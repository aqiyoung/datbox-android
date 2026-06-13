package com.threel.openlist.data.api

import com.threel.openlist.data.model.FsGetResponse
import com.threel.openlist.data.model.FsListResponse
import com.threel.openlist.data.model.LoginResponse
import com.threel.openlist.data.model.UserInfo
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
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

    @POST("api/fs/get")
    suspend fun get(@Body req: FsListRequest): FsGetResponse

    /** 下载用（不是 Retrofit @Streaming，是后端直接返的 URL，前端用 OkHttp 下载） */
    @GET("d/{path}")
    suspend fun downloadUrl(@Path("path", encoded = true) path: String): String

    /** 老板 6/13 v0.3.0: 表单上传 (multipart/form-data) */
    @Multipart
    @PUT("api/fs/form")
    suspend fun upload(
        @Query("path") path: String,
        @Query("override") override: Boolean = true,
        @Part file: MultipartBody.Part,
    ): FsUploadResponse
}
