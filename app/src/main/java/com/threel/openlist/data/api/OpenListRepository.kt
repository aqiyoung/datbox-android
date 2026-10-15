package com.threel.openlist.data.api

import android.content.Context
import com.threel.openlist.data.model.FsGetResponse
import com.threel.openlist.data.model.LoginResponse
import com.threel.openlist.data.model.Share
import com.threel.openlist.data.model.UserInfo
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

/** 双栈测速结果：分别测量 IPv4 / IPv6 可达性与延迟。只要任一栈可达即 reachable=true，bestMs 取可达栈中最小延迟。 */
data class ServerProbe(
    val reachable: Boolean,
    val ipv4Ms: Long? = null,
    val ipv6Ms: Long? = null,
    val bestMs: Long? = null
)

/** 自定义 DNS：只返回指定地址族(A=IPv4 / AAAA=IPv6)的解析结果，用于分栈测速 */
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

/**
 * 包装 OpenList API，处理认证头 + download/upload 底层。
 */
@Singleton
class OpenListRepository @Inject constructor(
    private val api: OpenListApi,
    private val tokenStore: TokenStore,
    @ApplicationContext private val context: Context,
) {
    /** 单独 OkHttp 客户端（不走 Retrofit converter，省字节） */
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // 老板 6/13: 上传下载给 2 分钟超时
            .build()
    }

    /** 测速专用客户端：较短超时，避免单次慢连接拖垮整体体验 */
    private val probeClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    // serverUrl 由 DynamicServerInterceptor 按 DataStore 动态改写, 这里不接地址参数
    suspend fun login(username: String, password: String): Result<String> = runCatching {
        val resp = api.login(LoginRequest(username, password))
        if (resp.code != 200 || resp.data == null) {
            error("登录失败: ${resp.message}")
        }
        tokenStore.saveToken(resp.data.token)
        resp.data.token
    }

    /**
     * 双栈测速：分别探测 IPv4 与 IPv6 地址族的可达性并返回真实 RTT。
     *
     * - 用自定义 Dns 强制只解析 A(IPv4) 或 AAAA(IPv6) 记录，对两族并发发
     *   GET /api/public/info 并测量耗时。
     * - 只要任一栈可达即 reachable=true；bestMs 取两族中最小的可达延迟。
     * - 某族无地址/超时/DNS 失败 -> 该族结果为 null，不会误判整体"不可达"。
     */
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

    /** 仅用指定地址族探测 /api/public/info，返回 RTT(ms) 或 null(不可达/无该族地址)。 */
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
                    // 任何 HTTP 响应都说明该地址族可达
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

    suspend fun list(path: String = "/"): Result<List<com.threel.openlist.data.model.FsItem>> = runCatching {
        val resp = api.list(FsListRequest(path = path))
        if (resp.code != 200) error("列表失败: ${resp.message}")
        resp.data?.content ?: emptyList()
    }

    suspend fun get(path: String): Result<com.threel.openlist.data.model.FsItem?> = runCatching {
        val resp = api.get(FsListRequest(path = path))
        if (resp.code != 200) error("获取失败: ${resp.message}")
        resp.data
    }

    fun isLoggedIn(): Boolean = tokenStore.tokenSync().isNotEmpty()

    /** 老板 6/14 修: 下载文件 (OkHttp 直拉, 写到 Downloads 目录)
     *
     * OpenList 4.x 真实流程:
     *   1) POST /api/fs/get 拿 sign 字段 (HMAC, fs/get 返 data.sign)
     *   2) GET /d/xxx?sign=hmac 跳 302 到 storage 真链
     *
     * 之前 v0.3.3 用 Authorization token 直接打 /d/xxx 永远是 401:
     *   路由 g.GET /d/star/path 加 signCheck 中间件, auth 前面
     *   没 sign 直接 401; token 在 /d/ 路由完全不检查 (只查 sign)
     */
    suspend fun download(remotePath: String, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        com.threel.openlist.util.TelemetryLog.i("Repo", "download START (IO): ${remotePath.substringAfterLast('/')}")
        runCatching {
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')

        // 1) 拿 sign (fs/get 需要 path 在 body)
        val getReq = Request.Builder()
            .url("$serverUrl/api/fs/get")
            .header("Authorization", token)
            .post(FsGetRequest(remotePath).toJsonRequestBody())
            .build()
        val sign = client.newCall(getReq).execute().use { resp ->
            com.threel.openlist.util.TelemetryLog.i("Repo", "fs/get resp code=${resp.code}")
            if (!resp.isSuccessful) error("fs/get HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("fs/get empty body")
            OpenListJson.decodeFromString<FsGetResponse>(body).data?.sign
                ?: error("fs/get 响应里没 sign 字段: ${body.take(200)}")
        }

        // 2) 用 sign 下载 (sign 在 query, 不要 Authorization)
        val url = "$serverUrl/d$remotePath?sign=$sign"
        val req = Request.Builder().url(url).get().build()
        // 老板 6/14 13:21 修: Android 11+ scoped storage 不让直接写公共 Download 目录 (EACCES Permission denied)
        // 改写 app 私有目录 getExternalFilesDir(DIRECTORY_DOWNLOADS) (不需 WRITE_EXTERNAL_STORAGE 权限)
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: java.io.File(context.filesDir, "Downloads").also { it.mkdirs() }
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val outFile = File(downloadsDir, fileName)
        com.threel.openlist.util.TelemetryLog.i("Repo", "GET /d -> ${remotePath.substringAfterLast('/')}")
        client.newCall(req).execute().use { resp ->
            com.threel.openlist.util.TelemetryLog.i("Repo", "GET /d resp code=${resp.code} size=${resp.body?.contentLength()}")
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            val body = resp.body ?: error("empty body")
            outFile.outputStream().use { out ->
                body.byteStream().use { input -> input.copyTo(out) }
            }
        }
        com.threel.openlist.util.TelemetryLog.i("Repo", "download DONE: $fileName ${outFile.length()}B")
        outFile
        }
    }

    /** 老板 6/14 修: 上传文件 (OkHttp 直发 multipart)
     *
     * OpenList 4.x 真实流程:
     *   PUT /api/fs/form
     *     - Authorization token            (跟其他 API 一致)
     *     - File-Path 完整含文件名           (path 在 header, 不用 query!)
     *     - As-Task true                    (走异步任务)
     *     - Overwrite true                  (可覆盖)
     *     - body multipart file 字段
     *
     * 之前 v0.3.3 用 Retrofit @Query("path") 和 @Query("override") 永远是 400 'storage not found':
     *   FsForm 源码第一行: path := c.GetHeader("File-Path")
     *   它根本不读 query, 只读 header
     */
    suspend fun upload(remoteDir: String, file: File): Result<FsUploadResponse> = withContext(Dispatchers.IO) {
        com.threel.openlist.util.TelemetryLog.i("Repo", "upload START (IO): ${remoteDir.substringAfterLast('/')}")
        runCatching {
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')

        // 老板 6/14 拍: 不能上传到根 / (storage 都在 /天翼云盘 下)
        if (remoteDir == "/") {
            com.threel.openlist.util.TelemetryLog.w("Repo", "upload rejected: remoteDir=/")
            error("请先进 storage 目录再上传 (如 /天翼云盘)")
        }
        val targetPath = "$remoteDir/${file.name}"

        // multipart body (OkHttp 直发, 不走 Retrofit)
        val mime = "application/octet-stream".toMediaType()
        val requestBody = file.asRequestBody(mime)
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, requestBody)
            .build()

        // 老板 6/14 12:20 修: HTTP header 严格 ISO-8859-1, 中文路径 (如 /本地存储/图片) 拋 IllegalArgumentException
        // 修法: File-Path 头走 URL 百分号编码 (RFC 3986 unreserved), server 端 c.GetHeader 后会 URL-decode
        val encodedPath = java.net.URLEncoder.encode(targetPath, "UTF-8")
        com.threel.openlist.util.TelemetryLog.i("Repo", "upload File-Path encoded: $encodedPath")
        val req = Request.Builder()
            .url("$serverUrl/api/fs/form")
            .header("Authorization", token)
            .header("File-Path", encodedPath)
            .header("As-Task", "true")
            .header("Overwrite", "true")
            .put(multipart)
            .build()
        val respBody = client.newCall(req).execute().use { resp ->
            com.threel.openlist.util.TelemetryLog.i("Repo", "PUT /api/fs/form resp code=${resp.code}")
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            resp.body?.string() ?: error("empty body")
        }
        // 老板 6/14: 改普通字符串 + 转义, 避免 raw string `"""` 被 KSP 词法分析误解析 (188:1 Unclosed comment)
        OpenListJson.decodeFromString<FsUploadResponse>(respBody)
        }
    }

    /** 老板 6/14 修: 公开分享链接 (需先调 fs/get 拿 sign)
     *
     * 之前 v0.3.0-v0.3.6 拼 serverUrl/d/remotePath 永久 401:
     *   /d/xxx 路由只查 ?sign=, 不查 Authorization
     *   没 sign 客户端打开就 401 (老板 10:32:41 反馈)
     *
     * 正确流程: fs/get 拿 sign, 拼 /d/xxx?sign=hmac
     */
    /**
     * 创建短链分享链接 (v0.3.29 老板 6/14 20:00 拍: 隐藏中间目录)
     *
     * 之前 v0.3.0-v0.3.6 拼 serverUrl/d/remotePath 永久 401:
     *   /d/xxx 路由只查 ?sign=, 不查 Authorization
     *   没 sign 客户端打开就 401 (老板 10:32:41 反馈)
     *
     * 之前 v0.3.0-v0.3.28 拼 /d/完整路径?sign=xxx 露原目录结构:
     *   例: https://fn.threel.site/d/天翼云盘/电影/动作片/蝙蝠侠.mkv?sign=xxx
     *   拿链接的人能看完整目录树
     *
     * v0.3.29 修法: OpenList 4.x 官方短链 /sd/<id>/<filename>
     *   链接: https://fn.threel.site/sd/<share_id>/蝙蝠侠.mkv
     *   拿到链接的人只看到 ID + 文件名, 不知道原路径
     *   限同一个文件可以匿名下载 (限原本权限的 'anyone_with_link')
     *
     * 实现: POST /api/share/create -> 拿 share_id -> 拼 serverUrl/sd/<id>/<name>
     */
    suspend fun buildShortShareUrl(remotePath: String): String = withContext(Dispatchers.IO) {
        com.threel.openlist.util.TelemetryLog.i("Repo", "buildShortShareUrl START: ${remotePath.substringAfterLast('/')}")
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')
        val fileName = remotePath.substringAfterLast('/')
        // v0.3.30 修: OpenList 4.x /api/share/create 的 files[] 实际是 "作为子资源进入"
        // 坑: 单文件 share 里, OpenList 会把 files[0] 当成 dir 拼 /sd/<id>/<filename>
        //     拼出来是 "/本地存储/hello.txt/hello.txt" 路径, 服务端 stat 报 not a directory 500
        // 修法: 分享父目录而不是文件本身, URL 仍拼 /sd/<id>/<filename> (parent 是 dir 才是合法路径)
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
            // 提取 "id" 字段 (手工 regex, 跟项目其它 API 一致)
            OpenListJson.decodeFromString<ObjResponse<Share>>(responseBody).data?.id
                ?: error("share/create 响应里没 id 字段: ${responseBody.take(200)}")
        }
        // 中文文件名 URL encode (OpenList 服务端也接受 raw, 但浏览器分享出去可能被截断)
        val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8")
        "$serverUrl/sd/$shareId/$encodedName"
    }
}
