package com.datbox.app.data.api

import com.datbox.app.data.model.FsGetResponse
import com.datbox.app.data.model.FsListResponse
import com.datbox.app.data.model.FsSearchResponse
import com.datbox.app.data.model.LoginResponse
import com.datbox.app.data.model.UserInfo
import retrofit2.http.Body
import retrofit2.http.POST
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class FsListRequest(
    val path: String = "/",
    val password: String? = null,
    val page: Int = 1,
    @kotlinx.serialization.SerialName("per_page") val perPage: Int = 50,
    val refresh: Boolean = false,
)

/** POST /api/fs/get 请求体（下载/预览取 sign 用）。path 来自服务端文件名，必须交给序列化器转义。 */
@Serializable
data class FsGetRequest(
    val path: String,
)

/** POST /api/fs/search 请求体 (parent="/" 为全盘; scope: 0=全部, 1=仅目录, 2=仅文件; 需服务端开启搜索索引) */
@Serializable
data class FsSearchRequest(
    val parent: String = "/",
    val keywords: String,
    val scope: Int = 0,
    val page: Int = 1,
    @kotlinx.serialization.SerialName("per_page") val perPage: Int = 50,
)

/** 老板 6/13 v0.3.0: 上传响应 */
@Serializable
data class FsUploadResponse(
    val code: Int,
    val message: String,
    val data: FsUploadData? = null,
)

@Serializable
data class FsUploadData(
    val task: FsUploadTask? = null,
)

@Serializable
data class FsUploadTask(
    val id: String = "",
    val name: String = "",
    val state: Int = 0,  // 0=pending 1=running 2=success 3=failed 4=canceled
    val status: String = "",
    val progress: Double = 0.0,
    val error: String = "",
)

interface OpenListApi {
    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @GET("api/admin/user/info")
    suspend fun userInfo(): UserInfo

    @POST("api/fs/list")
    suspend fun list(@Body req: FsListRequest): FsListResponse

    @POST("api/fs/search")
    suspend fun search(@Body req: FsSearchRequest): FsSearchResponse

    @POST("api/fs/get")
    suspend fun get(@Body req: FsListRequest): FsGetResponse

    // 老板 6/14: 上传改用 OkHttp 直发 (File-Path header), 不走 Retrofit
    // 详见 OpenListRepository.upload
}
