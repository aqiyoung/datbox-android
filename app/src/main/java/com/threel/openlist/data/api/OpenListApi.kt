package com.threel.openlist.data.api

import com.threel.openlist.data.model.FsGetResponse
import com.threel.openlist.data.model.FsListResponse
import com.threel.openlist.data.model.LoginResponse
import com.threel.openlist.data.model.UserInfo
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
}
