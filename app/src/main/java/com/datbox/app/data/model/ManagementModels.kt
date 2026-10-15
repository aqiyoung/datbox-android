package com.datbox.app.data.model

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
    @SerialName("sso_id") val ssoId: String = "",
    // 2FA/passkey 绑定。上游 /api/admin/user/update 是整结构体覆盖, 不回传就等于清空
    val authn: String = "[]"
)

@Serializable
data class Mount(
    val id: Int = 0,
    @SerialName("mount_path") val mountPath: String = "",
    val driver: String = "Local",
    val status: String = "work",
    val order: Int = 0,
    val remark: String = "",
    val disabled: Boolean = false,
    // —— 以下字段编辑时必须原样回传 ——
    // 上游 UpdateStorage 是全字段覆盖(op.UpdateStorage → db.UpdateStorage)，
    // body 里缺哪个字段哪个就被清零：addition 是驱动配置(token/密钥/根目录)，
    // 丢了整个挂载就废了；disabled/order/remark 等同理。客户端就 POST 整个 Mount。
    @SerialName("cache_expiration") val cacheExpiration: Int = 0,
    @SerialName("custom_cache_policies") val customCachePolicies: String = "",
    val addition: String = "",
    @SerialName("disable_index") val disableIndex: Boolean = false,
    @SerialName("enable_sign") val enableSign: Boolean = false,
    @SerialName("order_by") val orderBy: String = "",
    @SerialName("order_direction") val orderDirection: String = "",
    @SerialName("extract_folder") val extractFolder: String = "",
    @SerialName("web_proxy") val webProxy: Boolean = false,
    @SerialName("webdav_policy") val webdavPolicy: String = "",
    @SerialName("proxy_range") val proxyRange: Boolean = false,
    @SerialName("down_proxy_url") val downProxyUrl: String = "",
    @SerialName("disable_proxy_sign") val disableProxySign: Boolean = false,
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
