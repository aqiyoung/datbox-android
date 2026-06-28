package com.threel.openlist.data.api

import com.threel.openlist.data.model.ManagementResponse
import com.threel.openlist.data.model.Mount
import com.threel.openlist.data.model.Option
import com.threel.openlist.data.model.Overview
import com.threel.openlist.data.model.Share
import com.threel.openlist.data.model.User
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.Serializable

@Serializable
data class UserCreateRequest(
    val username: String,
    val password: String,
    val role: Int = 1,
    val permission: Int = 0
)

@Serializable
data class UserUpdateRequest(
    val id: Int,
    val username: String = "",
    val password: String = "",
    val role: Int = -1,
    val permission: Int = -1
)

@Serializable
data class MountCreateRequest(
    val name: String,
    val driver: String = "Local",
    val path: String = "",
    val status: Int = 1
)

@Serializable
data class MountUpdateRequest(
    val id: Int,
    val name: String = "",
    val driver: String = "",
    val path: String = "",
    val status: Int = -1
)

@Serializable
data class ShareCreateRequest(
    val files: List<String>,
    val expires: String = "",
    val password: String = ""
)

@Serializable
data class OptionUpdateRequest(
    val key: String,
    val value: String
)

@Serializable
data class UserListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<User> = emptyList()
)

@Serializable
data class MountListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<Mount> = emptyList()
)

@Serializable
data class ShareListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<Share> = emptyList()
)

@Serializable
data class OptionListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<Option> = emptyList()
)

@Serializable
data class OverviewResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Overview? = null
)

@Serializable
data class UserResponse(
    val code: Int = 0,
    val message: String = "",
    val data: User? = null
)

@Serializable
data class MountResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Mount? = null
)

@Serializable
data class ShareResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Share? = null
)

interface ManagementApi {
    // ===== 用户管理 =====
    @GET("api/user/list")
    suspend fun userList(): UserListResponse

    @POST("api/user/create")
    suspend fun userCreate(@Body req: UserCreateRequest): UserResponse

    @POST("api/user/update")
    suspend fun userUpdate(@Body req: UserUpdateRequest): ManagementResponse

    @DELETE("api/user/delete")
    suspend fun userDelete(@Query("id") id: Int): ManagementResponse

    // ===== 挂载管理 =====
    @GET("api/storage/list")
    suspend fun mountList(): MountListResponse

    @POST("api/storage/add")
    suspend fun mountCreate(@Body req: MountCreateRequest): MountResponse

    @POST("api/storage/update")
    suspend fun mountUpdate(@Body req: MountUpdateRequest): ManagementResponse

    @DELETE("api/storage/remove")
    suspend fun mountDelete(@Query("id") id: Int): ManagementResponse

    // ===== 分享管理 =====
    @GET("api/share/list")
    suspend fun shareList(): ShareListResponse

    @POST("api/share/create")
    suspend fun shareCreate(@Body req: ShareCreateRequest): ShareResponse

    @DELETE("api/share/delete")
    suspend fun shareDelete(@Query("id") id: String): ManagementResponse

    // ===== 系统设置 =====
    @GET("api/overview")
    suspend fun overview(): OverviewResponse

    @GET("api/option/list")
    suspend fun optionList(): OptionListResponse

    @POST("api/option/update")
    suspend fun optionUpdate(@Body req: OptionUpdateRequest): ManagementResponse

    // ===== 文件操作 =====
    @POST("api/fs/mkdir")
    suspend fun mkdir(@Body req: Map<String, String>): ManagementResponse

    @POST("api/fs/rename")
    suspend fun rename(@Body req: Map<String, String>): ManagementResponse

    @POST("api/fs/move")
    suspend fun move(@Body req: Map<String, String>): ManagementResponse

    @POST("api/fs/delete")
    suspend fun delete(@Body req: Map<String, String>): ManagementResponse
}
