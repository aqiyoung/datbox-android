package com.threel.openlist.data.update

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Url

@Serializable
data class AppUpdateInfo(
    val version: String,
    val versionCode: Int,
    val type: String = "beta",
    val applicationId: String = "",
    val apk_url: String,
    val release_url: String = "",
    val changelog_url: String = "",
    val force_update: Boolean = false,
    val min_supported_version: String = "0.0.0",
)

/**
 * 独立的更新检查 service - 用 @Url 直接打 latest.json
 * 不依赖 token (public 端点)
 */
interface AppUpdateService {
    @GET
    suspend fun check(@Url url: String): AppUpdateInfo
}
