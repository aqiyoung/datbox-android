package com.threel.openlist.data.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.threel.openlist.util.AppConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/**
 * 启动后弹窗检查更新 (Activity 启动后 0.5s 调用, 避免挡住启动)
 *
 * 简化: 用 EntryPointAccessors.fromApplication() 拿 AppUpdateManager
 * (Hilt 2.51.1 推荐用法, 不用 EntryPoints.get)
 */
object AppUpdateLauncher {

    fun maybeShow(context: Context, lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val manager = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        AppUpdateEntryPoint::class.java
                    ).appUpdateManager()
                    val info = manager.checkForUpdate() ?: return@repeatOnLifecycle
                    showUpdateDialog(context, info)
                } catch (e: Exception) {
                    Log.w("AppUpdate", "launch failed: ${e.message}")
                }
            }
        }
    }

    private fun showUpdateDialog(context: Context, info: AppUpdateInfo) {
        val title = "发现新版本 ${info.version}"
        val message = buildString {
            append("当前: ${AppConfig.fullVersionString(context)}\n")
            append("最新: ${info.version} (build ${info.versionCode})\n")
            if (info.force_update) append("\n⚠️ 强制更新\n")
            append("\n是否前往下载？")
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("下载") { _, _ ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(info.apk_url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            .setNegativeButton("稍后", null)
            .setCancelable(!info.force_update)
            .show()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUpdateEntryPoint {
    fun appUpdateManager(): AppUpdateManager
}
