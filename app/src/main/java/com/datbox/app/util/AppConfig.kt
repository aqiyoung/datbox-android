package com.datbox.app.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

object AppConfig {

    const val BRAND = "Datbox"
    const val BRAND_SUBTITLE = "Datbox · 云盘聚合"
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

    val buildType: String = "release"

    fun fullVersionString(context: Context): String {
        val v = versionName(context)
        val c = versionCode(context)
        return "v$v (build $c · $buildType)"
    }
}
