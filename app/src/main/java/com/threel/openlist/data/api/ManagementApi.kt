package com.threel.openlist.data.api

import com.threel.openlist.data.model.ManagementResponse
import com.threel.openlist.data.model.Mount
import com.threel.openlist.data.model.Option
import com.threel.openlist.data.model.Overview
import com.threel.openlist.data.model.Share
import com.threel.openlist.data.model.User
import com.threel.openlist.data.model.UserInfo
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.Serializable

@Serializable
data class PageReq(
    val page: Int = 1,
    val perPage: Int = 50
)

@Serializable
data class UserCreateRequest(
    val username: String,
    val password: String,
    val role: Int = 1,
    val permission: Int = 0,
    val base_path: String = "/"
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
    val driver: String = "Local",
    val mount_path: String,
    val order: Int = 0,
    val remark: String = "",
    val cache_expiration: Int = 30,
    val web_proxy: Boolean = false,
    val webdav_policy: String = "native_proxy",
    val down_proxy_url: String = ""
)

@Serializable
data class MountUpdateRequest(
    val id: Int,
    val driver: String = "Local",
    val mount_path: String = "",
    val order: Int = -1,
    val remark: String = "",
    val cache_expiration: Int = -1,
    val web_proxy: Boolean = false,
    val webdav_policy: String = "",
    val down_proxy_url: String = ""
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
data class IdListResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: List<T> = emptyList()
)

@Serializable
data class ObjResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
)

@Serializable
data class OverviewResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Overview? = null
)

interface ManagementApi {
    // ===== 当前用户 (Auth) =====
    @GET("api/me")
    suspend fun currentUser(): ObjResponse<UserInfo>

    // ===== 用户管理 (Admin) =====
    @GET("api/admin/user/list")
    suspend fun userList(): IdListResponse<User>

    @POST("api/admin/user/create")
    suspend fun userCreate(@Body req: UserCreateRequest): ObjResponse<User>

    @POST("api/admin/user/update")
    suspend fun userUpdate(@Body req: UserUpdateRequest): ManagementResponse

    @POST("api/admin/user/delete")
    suspend fun userDelete(@Query("id") id: Int): ManagementResponse

    // ===== 挂载管理 (Admin) =====
    @GET("api/admin/storage/list")
    suspend fun mountList(): IdListResponse<Mount>

    @POST("api/admin/storage/create")
    suspend fun mountCreate(@Body req: MountCreateRequest): ObjResponse<Mount>

    @POST("api/admin/storage/update")
    suspend fun mountUpdate(@Body req: MountUpdateRequest): ManagementResponse

    @POST("api/admin/storage/delete")
    suspend fun mountDelete(@Query("id") id: Int): ManagementResponse

    @POST("api/admin/storage/enable")
    suspend fun mountEnable(@Query("id") id: Int): ManagementResponse

    @POST("api/admin/storage/disable")
    suspend fun mountDisable(@Query("id") id: Int): ManagementResponse

    // ===== 分享管理 (Admin) =====
    @POST("api/admin/share/list")
    suspend fun shareList(@Body req: PageReq = PageReq()): IdListResponse<Share>

    @POST("api/admin/share/add")
    suspend fun shareCreate(@Body req: ShareCreateRequest): ObjResponse<Share>

    @POST("api/admin/share/delete")
    suspend fun shareDelete(@Query("id") id: String): ManagementResponse

    // ===== 系统设置 (Admin) =====
    @GET("api/admin/setting/list")
    suspend fun optionList(): IdListResponse<Option>

    @POST("api/admin/setting/save")
    suspend fun optionSave(@Body req: OptionUpdateRequest): ManagementResponse

    @POST("api/admin/setting/delete")
    suspend fun optionDelete(@Query("key") key: String): ManagementResponse

    // ===== 仪表盘 (Public) =====
    @GET("api/public/info")
    suspend fun publicInfo(): ObjResponse<Map<String, String>>

    // ===== 文件操作 (FS) =====
    @POST("api/fs/mkdir")
    suspend fun mkdir(@Body req: Map<String, String>): ManagementResponse

    @POST("api/fs/rename")
    suspend fun rename(@Body req: Map<String, String>): ManagementResponse

    @POST("api/fs/move")
    suspend fun move(@Body req: Map<String, String>): ManagementResponse

    @POST("api/fs/remove")
    suspend fun delete(@Body req: Map<String, String>): ManagementResponse
}

