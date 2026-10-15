package com.datbox.app.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.datbox.app.BuildConfig

/**
 * App 全局配置 / 版本信息
 *
 * - versionName / versionCode 真实值 (从 PackageManager)
 * - buildType (debug/beta/release) 从 applicationIdSuffix 反推
 * - 更新检查已统一走 GitHub Release（见 data/update/GitHubUpdateEngine）
 *
 * 用法: AppConfig.currentVersionName(context) / AppConfig.buildType
 */
object AppConfig {

    const val BRAND = "数匣"
    const val BRAND_EN = "DATBOX"
    const val BRAND_SUBTITLE = "数匣 · 云盘聚合"
    // 老板 6/14 拍: 在 About 页面标注 "基于官方 OpenList 开发" + 给上游仓库加链接
    const val UPSTREAM_NAME = "OpenList"
    const val UPSTREAM_REPO = "https://github.com/OpenListTeam/OpenList"
    const val UPSTREAM_NOTE = "基于官方 OpenList 开发"

    fun versionName(context: Context): String =
        try {
            val pi: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pi.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }

    fun versionCode(context: Context): Long =
        try {
            val pi: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
            else pi.versionCode.toLong()
        } catch (e: PackageManager.NameNotFoundException) {
            0L
        }

    /**
     * Build type: 从 applicationId 后缀反推
     * - com.datbox.app          → release
     * - com.datbox.app.debug    → debug
     * - com.datbox.app.beta     → beta
     */
    val buildType: String
        get() = when {
            "com.datbox.app.beta" in BuildConfig.APPLICATION_ID -> "beta"
            "com.datbox.app.debug" in BuildConfig.APPLICATION_ID -> "debug"
            else -> "release"
        }

    fun fullVersionString(context: Context): String {
        val v = versionName(context)
        val c = versionCode(context)
        return "v$v (build $c · $buildType)"
    }
}
