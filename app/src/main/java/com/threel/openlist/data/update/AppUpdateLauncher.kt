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
import com.threel.openlist.R
import com.threel.openlist.util.AppConfig
import kotlinx.coroutines.launch

/**
 * 启动后弹窗检查更新 (Activity 启动后 0.5s 调用, 避免挡住启动)
 */
object AppUpdateLauncher {

    fun maybeShow(context: Context, lifecycleOwner: LifecycleOwner) {
        // 仅在 STARTED 之后才弹窗, 避免主屏黑屏
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val mgr = (context.applicationContext as? android.app.Application)
                        ?.let { (it as? dagger.hilt.android.HiltAndroidApp)?.let { _ -> } }
                    // 通过 Hilt 拿 manager
                    val manager = dagger.hilt.android.EntryPoints.get(
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

/** Hilt Entry Point: 从 application context 拿 AppUpdateManager */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AppUpdateEntryPoint {
    fun appUpdateManager(): AppUpdateManager
}
