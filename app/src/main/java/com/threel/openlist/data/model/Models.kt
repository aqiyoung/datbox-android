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

/** OpenList /api/me 响应 */
@Serializable
data class UserInfo(
    val id: Int = 0,
    val username: String = "",
    val password: String = "",
    @SerialName("base_path") val basePath: String = "/",
    val role: Int = 2,  // 0=普通用户 1=游客 2=管理员 (OpenList internal/model/user.go: GENERAL/GUEST/ADMIN)
    val permission: Int = 0,
    val disabled: Boolean = false,
    @SerialName("sso_id") val ssoId: String = ""
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

/** /api/fs/search 结果节点 (OpenList internal/model/search.go: parent=所在目录) */
@Serializable
data class FsSearchNode(
    val parent: String = "/",
    val name: String,
    val size: Long = 0,
    @SerialName("is_dir") val isDir: Boolean = false,
    @SerialName("type") val type: Int = 0,
)

@Serializable
data class FsSearchData(
    val content: List<FsSearchNode> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class FsSearchResponse(
    val code: Int,
    val message: String,
    val data: FsSearchData? = null,
)

@Serializable
data class FsItem(
    val name: String,
    val size: Long = 0,
    @SerialName("is_dir") val isDir: Boolean = false,
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
