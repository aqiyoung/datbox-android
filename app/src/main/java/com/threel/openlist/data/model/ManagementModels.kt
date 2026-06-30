package com.threel.openlist.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val username: String,
    val password: String = "",
    @SerialName("base_path") val basePath: String = "/",
    val role: Int = 0,  // 0=普通用户, 1=游客, 2=管理员
    val disabled: Boolean = false,
    val permission: Int = 0,
    @SerialName("sso_id") val ssoId: String = ""
)

@Serializable
data class Mount(
    val id: Int = 0,
    @SerialName("mount_path") val mountPath: String = "",
    val driver: String = "Local",
    val status: String = "work",
    val order: Int = 0,
    val remark: String = "",
    val disabled: Boolean = false
) {
    val path: String get() = mountPath
}

@Serializable
data class Share(
    val id: String = "",
    val path: String = "",
    val name: String = "",
    val password: String = "",
    val expires: String = "",
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("viewer_id") val viewerId: Int? = null,
    @SerialName("creator_id") val creatorId: Int? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class Option(
    val key: String = "",
    val value: String = "",
    val type: String = "string",
    val description: String = ""
)

@Serializable
data class Overview(
    @SerialName("user_count") val userCount: Int = 0,
    @SerialName("mount_count") val mountCount: Int = 0,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("storage_used") val storageUsed: Long = 0,
    @SerialName("storage_total") val storageTotal: Long = 0,
    @SerialName("schema_migrations") val schemaMigrations: Int = 0
)

@Serializable
data class ManagementResponse(val code: Int = 0, val message: String = "")
