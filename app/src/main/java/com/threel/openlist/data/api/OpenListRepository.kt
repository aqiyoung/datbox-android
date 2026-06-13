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

    /** 老板 6/13 v0.3.0: 下载文件 (OkHttp 直拉, 写到 Downloads 目录)
     *
     * OpenList 4.x bug: 客户端不能传 'Bearer ' 前缀! (中间件 cache key 不一致)
     * 跟 Retrofit AuthInterceptor 一致 - 传 raw token 即可
     */
    suspend fun download(remotePath: String, fileName: String): Result<File> = runCatching {
        val token = tokenStore.tokenSync()
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')
        // 路径用 /d/{path} 形式 + Authorization header (raw token, 不加 'Bearer ')
        val encoded = remotePath.removePrefix("/")
        val url = "$serverUrl/d/$encoded"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", token)
            .get()
            .build()
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

    /** 老板 6/13 v0.3.0: 上传文件 (Retrofit multipart) */
    suspend fun upload(remoteDir: String, file: File): Result<FsUploadResponse> = runCatching {
        val mime = "application/octet-stream".toMediaType()
        val body = file.asRequestBody(mime)
        val part = MultipartBody.Part.createFormData("file", file.name, body)
        // 老板 6/14: 不能上传到根 / (storage 都在 /天翼云盘 下)
        // 如果 remoteDir == "/", 提示用户先进 storage
        val targetPath = if (remoteDir == "/") {
            error("请先进 storage 目录再上传 (如 /天翼云盘)")
        } else {
            "$remoteDir/${file.name}"
        }
        api.upload(path = targetPath, override = true, file = part)
    }

    /** 老板 6/13 v0.3.0: 公开分享链接 - 直接拼 URL (OpenList 短链) */
    fun buildShareUrl(remotePath: String): String {
        val serverUrl = tokenStore.serverUrlSync().trimEnd('/')
        return "$serverUrl/d$remotePath"
    }
}
