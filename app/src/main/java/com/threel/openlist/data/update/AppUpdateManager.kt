package com.threel.openlist.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.threel.openlist.util.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App 启动检查更新 + 弹窗提示
 *
 * - latest.json 包含 version / versionCode / apk_url / force_update / min_supported_version
 * - 启动时 (Application onCreate) 异步 fire-and-forget 调用
 * - 如果发现新版本, 回调 onUpdateAvailable, UI 弹窗
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val TAG = "AppUpdate"
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 检查更新
     * @return AppUpdateInfo if new version, null if up-to-date or error
     */
    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = AppConfig.UPDATE_CHECK_URL
            Log.i(TAG, "checkForUpdate: $url")
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "HTTP ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string() ?: return@withContext null
                val info = parseUpdateInfo(body) ?: return@withContext null
                val currentCode = AppConfig.versionCode(context)
                Log.i(TAG, "current=$currentCode, latest=${info.versionCode} (${info.version})")
                if (info.versionCode > currentCode) info else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkForUpdate failed: ${e.message}")
            null
        }
    }

    private fun parseUpdateInfo(json: String): AppUpdateInfo? = try {
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }.decodeFromString(AppUpdateInfo.serializer(), json)
    } catch (e: Exception) {
        Log.w(TAG, "parse failed: ${e.message}")
        null
    }

    /**
     * 下载 APK 到 cache 目录, 安装 (需要 FileProvider + manifest provider 授权)
     * 简化: 直接返回 Intent.ACTION_VIEW 让系统浏览器/下载器处理
     */
    fun apkInstallIntent(apkUrl: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
