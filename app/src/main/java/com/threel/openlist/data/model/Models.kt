package com.threel.openlist.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** OpenList 登录响应 */
@Serializable
data class LoginResponse(
    val code: Int,
    val message: String,
    val data: TokenData? = null,
)

@Serializable
data class TokenData(
    val token: String,
)

/** OpenList /api/admin/user/info 响应 */
@Serializable
data class UserInfo(
    val id: Int,
    val username: String,
    @SerialName("base_path") val basePath: String = "/",
    val role: Int = 2,  // 0=admin 1=guest 2=normal
    val disabled: Boolean = false,
)

/** /api/fs/list 响应 */
@Serializable
data class FsListResponse(
    val code: Int,
    val message: String,
    val data: FsListData? = null,
)

@Serializable
data class FsListData(
    val content: List<FsItem> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class FsItem(
    val name: String,
    val size: Long = 0,
    val isDir: Boolean = false,
    val modified: String = "",
    val sign: String = "",  // 用于下载
    val thumb: String = "",
    @SerialName("type") val type: Int = 0,  // 0=file 1=folder
)

/** /api/fs/get 响应（单文件元数据） */
@Serializable
data class FsGetResponse(
    val code: Int,
    val message: String,
    val data: FsItem? = null,
)
