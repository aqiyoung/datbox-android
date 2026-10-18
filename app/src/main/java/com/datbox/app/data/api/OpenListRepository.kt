package com.datbox.app.data.api

import android.content.Context
import com.datbox.app.data.model.FsGetResponse
import com.datbox.app.data.model.Share
import com.datbox.app.data.model.UserInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Dns
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ServerProbe(
    val reachable: Boolean,
    val ipv4Ms: Long? = null,
    val ipv6Ms: Long? = null,
    val bestMs: Long? = null
)

private class FamilyDns(private val preferV6: Boolean) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = try {
            InetAddress.getAllByName(hostname).toList()
        } catch (e: UnknownHostException) {
            return emptyList()
        }
        return all.filter { (it is Inet6Address) == preferV6 }
    }
}

@Singleton
class OpenListRepository @Inject constructor(
    private val api: OpenListApi,
    private val tokenStore: TokenStore,
    @ApplicationContext private val context: Context,
) {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val probeClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun login(username: String, password: String): Result<String> = runCatching {
        val resp = api.login(LoginRequest(username, password))
        if (resp.code != 200 || resp.data == null) {
            error("登录失败: ${resp.message}")
        }
        tokenStore.saveToken(resp.data.token)
        resp.data.token
    }

    suspend fun testConnection(serverUrl: String): ServerProbe {
        val baseUrl = serverUrl.trimEnd('/')
        return withContext(Dispatchers.IO) {
            coroutineScope {
                val v4 = async { probeFamily(false, baseUrl) }
                val v6 = async { probeFamily(true, baseUrl) }
                val ipv4Ms = v4.await()
                val ipv6Ms = v6.await()
                val best = listOfNotNull(ipv4Ms, ipv6Ms).minOrNull()
                ServerProbe(
                    reachable = best != null,
                    ipv4Ms = ipv4Ms,
                    ipv6Ms = ipv6Ms,
                    bestMs = best
                )
            }
        }
    }

    private suspend fun probeFamily(preferV6: Boolean, baseUrl: String): Long? =
        withContext(Dispatchers.IO) {
            try {
                val client = probeClient.newBuilder().dns(FamilyDns(preferV6)).build()
                val req = Request.Builder()
                    .url("$baseUrl/api/public/info")
                    .get()
                    .build()
                val start = System.nanoTime()
                val ok = client.newCall(req).execute().use { resp ->
                    resp.code in 200..599
                }
                if (!ok) null else ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1L)
            } catch (e: Exception) {
                null
            }
        }

    suspend fun userInfo(): Result<UserInfo> = runCatching {
        val info = api.userInfo()
        if (info.disabled) error("账号已停用")
        info
    }

    /** 服务端 OpenList 版本 (形如 "v4.2.2 (Commit: ...) - Frontend: ... - Build at: ..."), 取不到返回 null。 */
    suspend fun serverVersion(): String? = runCatching {
        api.publicSettings().data?.get("version")?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    suspend fun list(path: String = "/"): Result<List<com.datbox.app.data.model.FsItem>> = runCatching {
        val resp = api.list(FsListRequest(path = path))
        if (resp.code != 200) error("列表失败: ${resp.message}")
        resp.data?.content ?: emptyList()
    }

    suspend fun search(keywords: String, parent: String = "/", scope: Int = 0): Result<List<com.datbox.app.data.model.FsSearchNode>> = runCatching {
        val resp = api.search(FsSearchRequest(parent = parent, keywords = keywords, scope = scope))
        if (resp.code != 200) error("搜索失败: ${resp.message}")
        resp.data?.content ?: emptyList()
    }

    suspend fun get(path: String): Result<com.datbox.app.data.model.FsItem?> = runCatching {
        val resp = api.get(FsListRequest(path = path))
        if (resp.code != 200) error("获取失败: ${resp.message}")
        resp.data
    }

    fun isLoggedIn(): Boolean = tokenStore.tokenSync().isNotEmpty()

    suspend fun download(remotePath: String, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        com.datbox.app.util.TelemetryLog.i("Repo", "download START (IO): ${remotePath.substringAfterLast('/')}")
        runCatching {
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')

        val getReq = Request.Builder()
            .url("$serverUrl/api/fs/get")
            .header("Authorization", token)
            .post(FsGetRequest(remotePath).toJsonRequestBody())
            .build()
        val sign = client.newCall(getReq).execute().use { resp ->
            com.datbox.app.util.TelemetryLog.i("Repo", "fs/get resp code=${resp.code}")
            if (!resp.isSuccessful) error("fs/get HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("fs/get empty body")
            OpenListJson.decodeFromString<FsGetResponse>(body).data?.sign
                ?: error("fs/get 响应里没 sign 字段: ${body.take(200)}")
        }

        val url = "$serverUrl/d$remotePath?sign=$sign"
        val req = Request.Builder().url(url).get().build()
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: java.io.File(context.filesDir, "Downloads").also { it.mkdirs() }
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val outFile = File(downloadsDir, fileName)
        com.datbox.app.util.TelemetryLog.i("Repo", "GET /d -> ${remotePath.substringAfterLast('/')}")
        client.newCall(req).execute().use { resp ->
            com.datbox.app.util.TelemetryLog.i("Repo", "GET /d resp code=${resp.code} size=${resp.body?.contentLength()}")
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            val body = resp.body ?: error("empty body")
            outFile.outputStream().use { out ->
                body.byteStream().use { input -> input.copyTo(out) }
            }
        }
        com.datbox.app.util.TelemetryLog.i("Repo", "download DONE: $fileName ${outFile.length()}B")
        outFile
        }
    }

    suspend fun upload(remoteDir: String, file: File): Result<FsUploadResponse> = withContext(Dispatchers.IO) {
        com.datbox.app.util.TelemetryLog.i("Repo", "upload START (IO): ${remoteDir.substringAfterLast('/')}")
        runCatching {
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')

        if (remoteDir == "/") {
            com.datbox.app.util.TelemetryLog.w("Repo", "upload rejected: remoteDir=/")
            error("请先进 storage 目录再上传 (如 /天翼云盘)")
        }
        val targetPath = "$remoteDir/${file.name}"

        val mime = "application/octet-stream".toMediaType()
        val requestBody = file.asRequestBody(mime)
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, requestBody)
            .build()

        val encodedPath = java.net.URLEncoder.encode(targetPath, "UTF-8")
        com.datbox.app.util.TelemetryLog.i("Repo", "upload File-Path encoded: $encodedPath")
        val req = Request.Builder()
            .url("$serverUrl/api/fs/form")
            .header("Authorization", token)
            .header("File-Path", encodedPath)
            .header("As-Task", "true")
            .header("Overwrite", "true")
            .put(multipart)
            .build()
        val respBody = client.newCall(req).execute().use { resp ->
            com.datbox.app.util.TelemetryLog.i("Repo", "PUT /api/fs/form resp code=${resp.code}")
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            resp.body?.string() ?: error("empty body")
        }
        OpenListJson.decodeFromString<FsUploadResponse>(respBody)
        }
    }

    suspend fun buildShortShareUrl(remotePath: String): String = withContext(Dispatchers.IO) {
        com.datbox.app.util.TelemetryLog.i("Repo", "buildShortShareUrl START: ${remotePath.substringAfterLast('/')}")
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')
        val fileName = remotePath.substringAfterLast('/')
        var parentPath = remotePath.substringBeforeLast('/')
        if (parentPath.isEmpty()) parentPath = "/"
        val body = ShareCreateRequest(
            files = listOf(parentPath),
            expires = "2099-12-31T23:59:59Z",
            password = "",
        ).toJsonRequestBody()
        val req = Request.Builder()
            .url("$serverUrl/api/share/create")
            .header("Authorization", token)
            .post(body)
            .build()
        val shareId = client.newCall(req).execute().use { resp ->
            val responseBody = resp.body?.string() ?: error("share/create empty body")
            if (!resp.isSuccessful) error("share/create HTTP ${resp.code}: ${responseBody.take(200)}")
            OpenListJson.decodeFromString<ObjResponse<Share>>(responseBody).data?.id
                ?: error("share/create 响应里没 id 字段: ${responseBody.take(200)}")
        }
        val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8")
        "$serverUrl/sd/$shareId/$encodedName"
    }
}
