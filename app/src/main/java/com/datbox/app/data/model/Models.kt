package com.datbox.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
data class UserInfo(
    val id: Int = 0,
    val username: String = "",
    val password: String = "",
    @SerialName("base_path") val basePath: String = "/",
    val role: Int = 2,
    val permission: Int = 0,
    val disabled: Boolean = false,
    @SerialName("sso_id") val ssoId: String = ""
)

/** GET /api/public/settings 的响应。data 是 OpenList 公开设置的键值表, version 即服务端版本。 */
@Serializable
data class PublicSettingsResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Map<String, String>? = null,
)

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
    val sign: String = "",
    val thumb: String = "",
    @SerialName("type") val type: Int = 0,
)

@Serializable
data class FsGetResponse(
    val code: Int,
    val message: String,
    val data: FsItem? = null,
)
