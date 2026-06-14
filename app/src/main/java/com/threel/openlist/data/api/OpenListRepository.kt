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

    suspend fun login(username: String, password: String): Result<String> = runCatching {
        val resp = api.login(LoginRequest(username, password))
        if (resp.code != 200 || resp.data == null) {
            error("登录失败: ${resp.message}")
        }
        tokenStore.saveToken(resp.data.token)
        resp.data.token
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
    suspend fun download(remotePath: String, fileName: String): Result<File> = runCatching {
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')

        // 1) 拿 sign (fs/get 需要 path 在 body)
        val getReq = Request.Builder()
            .url("$serverUrl/api/fs/get")
            .header("Authorization", token)
            .post("""{"path":"$remotePath"}""".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val sign = client.newCall(getReq).execute().use { resp ->
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
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val outFile = File(downloadsDir, fileName)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            val body = resp.body ?: error("empty body")
            outFile.outputStream().use { out ->
                body.byteStream().use { input -> input.copyTo(out) }
            }
        }
        outFile
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
    suspend fun upload(remoteDir: String, file: File): Result<FsUploadResponse> = runCatching {
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')

        // 老板 6/14 拍: 不能上传到根 / (storage 都在 /天翼云盘 下)
        if (remoteDir == "/") {
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

        val req = Request.Builder()
            .url("$serverUrl/api/fs/form")
            .header("Authorization", token)
            .header("File-Path", targetPath)
            .header("As-Task", "true")
            .header("Overwrite", "true")
            .put(multipart)
            .build()
        val respBody = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.message}")
            resp.body?.string() ?: error("empty body")
        }
        // 老板 6/14: 改普通字符串 + 转义, 避免 raw string `"""` 被 KSP 词法分析误解析 (188:1 Unclosed comment)
        val codeMatch = Regex("\"code\"\\s*:\\s*(\\d+)").find(respBody)
        val code = codeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val message = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(respBody)?.groupValues?.get(1) ?: ""
        FsUploadResponse(code = code, message = message)
    }

    /** 老板 6/14 修: 公开分享链接 (需先调 fs/get 拿 sign)
     *
     * 之前 v0.3.0-v0.3.6 拼 serverUrl/d/remotePath 永久 401:
     *   /d/xxx 路由只查 ?sign=, 不查 Authorization
     *   没 sign 客户端打开就 401 (老板 10:32:41 反馈)
     *
     * 正确流程: fs/get 拿 sign, 拼 /d/xxx?sign=hmac
     */
    suspend fun buildShareUrl(remotePath: String): String {
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')
        val getReq = Request.Builder()
            .url("$serverUrl/api/fs/get")
            .header("Authorization", token)
            .post("""{"path":"$remotePath"}""".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val sign = client.newCall(getReq).execute().use { resp ->
            if (!resp.isSuccessful) error("fs/get HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("fs/get empty body")
            Regex("\"sign\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: error("fs/get 响应里没 sign 字段: ${body.take(200)}")
        }
        return "$serverUrl/d$remotePath?sign=$sign"
    }
}
