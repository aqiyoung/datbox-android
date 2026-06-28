package com.threel.openlist.data.api

import android.content.Context
import com.threel.openlist.data.model.LoginResponse
import com.threel.openlist.data.model.UserInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun login(username: String, password: String, serverUrl: String = tokenStore.serverUrlSync()): Result<String> = runCatching {
        val resp = api.login(LoginRequest(username, password))
        if (resp.code != 200 || resp.data == null) {
            error("登录失败: ${resp.message}")
        }
        tokenStore.saveToken(resp.data.token)
        resp.data.token
    }

    /**
     * 测试服务器连通性
     *
     * 发送空用户名登录, 如果返回 401 (而非连接失败/超时/OPTIONS 405),
     * 说明服务器可达而且是 OpenList 服务。
     */
    suspend fun testConnection(serverUrl: String): Boolean {
        return try {
            val url = serverUrl.trimEnd('/')
            val body = """{"username":"","password":""}"""
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("$url/api/auth/login")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                // 401 = OpenList 返回 "用户名或密码错误" = 服务器可达
                // 200 也行 (空密码刚好登录成功, 虽然不太可能)
                resp.code == 401 || resp.code == 200
            }
        } catch (e: Exception) {
            false
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
        com.threel.openlist.util.TelemetryLog.i("Repo", "download START (IO): $remotePath")
        runCatching {
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')

        // 1) 拿 sign (fs/get 需要 path 在 body)
        val getReq = Request.Builder()
            .url("$serverUrl/api/fs/get")
            .header("Authorization", token)
            .post("""{"path":"$remotePath"}""".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val sign = client.newCall(getReq).execute().use { resp ->
            com.threel.openlist.util.TelemetryLog.i("Repo", "fs/get resp code=${resp.code}")
            if (!resp.isSuccessful) error("fs/get HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("fs/get empty body")
            // 简单正则拿 sign (避引入 Json parser 到这里)
            val match = Regex("\"sign\"\\s*:\\s*\"([^\"]+)\"").find(body)
                ?: error("fs/get 响应里没 sign 字段: ${body.take(200)}")
            match.groupValues[1]
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
        com.threel.openlist.util.TelemetryLog.i("Repo", "GET /d path: ${req.url.encodedPath} sign=${sign.take(20)}...")
        client.newCall(req).execute().use { resp ->
            com.threel.openlist.util.TelemetryLog.i("Repo", "GET /d resp code=${resp.code} size=${resp.body?.contentLength()}")
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            val body = resp.body ?: error("empty body")
            outFile.outputStream().use { out ->
                body.byteStream().use { input -> input.copyTo(out) }
            }
        }
        com.threel.openlist.util.TelemetryLog.i("Repo", "download DONE: $fileName ${outFile.length()}B -> ${outFile.absolutePath}")
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
        com.threel.openlist.util.TelemetryLog.i("Repo", "upload START (IO): ${file.absolutePath} -> $remoteDir")
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
        val codeMatch = Regex("\"code\"\\s*:\\s*(\\d+)").find(respBody)
        val code = codeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val message = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(respBody)?.groupValues?.get(1) ?: ""
        FsUploadResponse(code = code, message = message)
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
     *   例: https://<your-server>/d/天翼云盘/电影/动作片/蝙蝠侠.mkv?sign=xxx
     *   拿链接的人能看完整目录树
     *
     * v0.3.29 修法: OpenList 4.x 官方短链 /sd/<id>/<filename>
     *   链接: https://<your-server>/sd/<share_id>/蝙蝠侠.mkv
     *   拿到链接的人只看到 ID + 文件名, 不知道原路径
     *   限同一个文件可以匿名下载 (限原本权限的 'anyone_with_link')
     *
     * 实现: POST /api/share/create -> 拿 share_id -> 拼 PUBLIC_BASE_URL/sd/<id>/<name>
     */
    suspend fun buildShortShareUrl(remotePath: String): String = withContext(Dispatchers.IO) {
        com.threel.openlist.util.TelemetryLog.i("Repo", "buildShortShareUrl START: $remotePath")
        val token = tokenStore.tokenSync()
        val fileName = remotePath.substringAfterLast('/')
        // v0.3.30 修: OpenList 4.x /api/share/create 的 files[] 实际是 "作为子资源进入"
        // 坑: 单文件 share 里, OpenList 会把 files[0] 当成 dir 拼 /sd/<id>/<filename>
        //     拼出来是 "/本地存储/hello.txt/hello.txt" 路径, 服务端 stat 报 not a directory 500
        // 修法: 分享父目录而不是文件本身, URL 仍拼 /sd/<id>/<filename> (parent 是 dir 才是合法路径)
        var parentPath = remotePath.substringBeforeLast('/')
        if (parentPath.isEmpty()) parentPath = "/"
        val body = """{"files":["$parentPath"],"expires":"2099-12-31T23:59:59Z","password":""}"""
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("${com.threel.openlist.util.AppConfig.PUBLIC_BASE_URL}/api/share/create")
            .header("Authorization", token)
            .post(body)
            .build()
        val shareId = client.newCall(req).execute().use { resp ->
            val responseBody = resp.body?.string() ?: error("share/create empty body")
            if (!resp.isSuccessful) error("share/create HTTP ${resp.code}: ${responseBody.take(200)}")
            // 提取 "id" 字段 (手工 regex, 跟项目其它 API 一致)
            Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)?.groupValues?.get(1)
                ?: error("share/create 响应里没 id 字段: ${responseBody.take(200)}")
        }
        // 中文文件名 URL encode (OpenList 服务端也接受 raw, 但浏览器分享出去可能被截断)
        val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8")
        "${com.threel.openlist.util.AppConfig.PUBLIC_BASE_URL}/sd/$shareId/$encodedName"
    }
}
