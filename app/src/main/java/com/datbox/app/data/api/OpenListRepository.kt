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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
        if (resp.code != 200) error(resp.message)
        resp.data?.content ?: emptyList()
    }

    /**
     * 本地扫描式搜索：不依赖服务端搜索索引。
     *
     * OpenList 的 /api/fs/search 只有在服务端把 search_index 从默认的 "none" 改成 bleve/db
     * 并建好索引后才可用，否则中间件直接返回 500 search not available。
     * 这里改用 /api/fs/list 逐层遍历，边扫边回报，代价是慢，但任何能正常浏览的服务器都能用。
     *
     * @param onUpdate 每扫完一个目录回报一次 (当前累计命中, 已扫描目录数)
     * @return 命中列表 + 是否因目录数/命中数/时间上限而截断
     */
    suspend fun searchLocal(
        keywords: String,
        root: String = "/",
        maxDirs: Int = 400,
        maxHits: Int = 300,
        timeBudgetMs: Long = 25_000,
        onUpdate: (List<com.datbox.app.data.model.FsSearchNode>, Int) -> Unit = { _, _ -> },
    ): Result<Pair<List<com.datbox.app.data.model.FsSearchNode>, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            val kw = keywords.trim()
            if (kw.isEmpty()) return@runCatching emptyList<com.datbox.app.data.model.FsSearchNode>() to false

            val hits = mutableListOf<com.datbox.app.data.model.FsSearchNode>()
            val lock = Mutex()
            val sem = Semaphore(6)
            val deadline = System.nanoTime() + timeBudgetMs * 1_000_000
            var scanned = 0
            var truncated = false

            suspend fun shouldStop(): Boolean = lock.withLock {
                scanned >= maxDirs || hits.size >= maxHits || System.nanoTime() > deadline
            }

            suspend fun scan(dir: String): List<String> {
                if (shouldStop()) return emptyList()
                val entries = sem.withPermit {
                    runCatching { listAll(dir) }.getOrDefault(emptyList())
                }
                val found = mutableListOf<com.datbox.app.data.model.FsSearchNode>()
                val subdirs = mutableListOf<String>()
                for (e in entries) {
                    if (e.name.contains(kw, ignoreCase = true)) {
                        found += com.datbox.app.data.model.FsSearchNode(
                            parent = dir,
                            name = e.name,
                            size = e.size,
                            isDir = e.isDir,
                            type = e.type,
                        )
                    }
                    if (e.isDir) subdirs += if (dir == "/") "/${e.name}" else "$dir/${e.name}"
                }
                // 在锁内回调，避免并发目录把新快照覆盖成旧快照
                lock.withLock {
                    hits += found
                    scanned += 1
                    onUpdate(hits.toList(), scanned)
                }
                return subdirs
            }

            var frontier = listOf(root)
            var depth = 0
            while (frontier.isNotEmpty() && depth <= 12 && !shouldStop()) {
                val next = mutableListOf<String>()
                coroutineScope {
                    frontier.take(2000).map { dir -> async { scan(dir) } }.awaitAll()
                }.forEach { next += it }
                frontier = next.distinct()
                depth++
            }

            val done = hits.toList()
            truncated = truncated || frontier.isNotEmpty()
            done to truncated
        }
    }

    /** list 的一页默认只有 50 条，扫描时必须翻页，否则大目录会被截掉一半。 */
    private suspend fun listAll(path: String): List<com.datbox.app.data.model.FsItem> {
        val out = mutableListOf<com.datbox.app.data.model.FsItem>()
        var page = 1
        while (page <= 10) {
            val resp = api.list(FsListRequest(path = path, page = page, perPage = 200))
            if (resp.code != 200) break
            val content = resp.data?.content ?: break
            out += content
            if (content.size < 200) break
            page++
        }
        return out
    }

    suspend fun get(path: String): Result<com.datbox.app.data.model.FsItem?> = runCatching {
        val resp = api.get(FsListRequest(path = path))
        if (resp.code != 200) error("获取失败: ${resp.message}")
        resp.data
    }

    fun isLoggedIn(): Boolean = tokenStore.tokenSync().isNotEmpty()

    /** 预览/播放用的带签名下载地址。路径按段编码，文件名里的空格、#、% 不会破坏 URL。 */
    suspend fun previewUrl(remotePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenStore.tokenSync()
            val serverUrl = tokenStore.serverUrlSync().trimEnd('/')

            val getReq = Request.Builder()
                .url("$serverUrl/api/fs/get")
                .header("Authorization", token)
                .post(FsGetRequest(remotePath).toJsonRequestBody())
                .build()
            val sign = client.newCall(getReq).execute().use { resp ->
                if (!resp.isSuccessful) error("fs/get HTTP ${resp.code}")
                val body = resp.body?.string() ?: error("fs/get 响应为空")
                OpenListJson.decodeFromString<FsGetResponse>(body).data?.sign
                    ?: error("fs/get 响应里没有 sign 字段")
            }

            val base = serverUrl.toHttpUrlOrNull() ?: error("服务器地址无效: $serverUrl")
            val builder = base.newBuilder().addPathSegment("d")
            remotePath.split('/').forEach { segment ->
                if (segment.isNotEmpty()) builder.addPathSegment(segment)
            }
            builder.addQueryParameter("sign", sign).build().toString()
        }
    }

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
