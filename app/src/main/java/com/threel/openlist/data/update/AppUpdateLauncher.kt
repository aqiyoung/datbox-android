package com.threel.openlist.data.update

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 启动后弹窗检查更新（Activity 启动后 0.5s 调用，避免挡住启动）。
 *
 * 对齐统一引擎纪律：
 * - 受"启动时自动检查"开关约束（设置可关）；手动检查（About 页）始终执行。
 * - checkForUpdate() 返回 null = 网络不可达 → 不弹窗（不谎报"已是最新"）。
 * - 仅在 hasUpdate 时弹窗；isCritical 时不可取消（强制更新）。
 * - 跳转发布页走 engine.openRelease（GitHub App 优先）。
 */
object AppUpdateLauncher {

    fun maybeShow(context: Context, lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val manager = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        AppUpdateEntryPoint::class.java,
                    ).appUpdateManager()
                    if (!manager.autoCheckEnabled.first()) return@repeatOnLifecycle
                    val result = manager.checkForUpdate() ?: return@repeatOnLifecycle
                    if (!result.hasUpdate) return@repeatOnLifecycle
                    showUpdateDialog(context, manager, result)
                } catch (e: Exception) {
                    Log.w("AppUpdate", "launch failed: ${e.message}")
                }
            }
        }
    }

    private fun showUpdateDialog(
        context: Context,
        manager: AppUpdateManager,
        result: GitHubUpdateResult,
    ) {
        val message = buildString {
            append("当前: ${manager.currentVersionName}\n")
            append("最新: ${result.latestVersion}\n")
            result.releaseNotes?.let { notes ->
                // 跳过首行 P0 / critical 标记，展示其余更新内容。
                val body = notes.lines().drop(1).joinToString("\n").trim()
                if (body.isNotEmpty()) {
                    append("\n更新内容:\n")
                    append(if (body.length > 400) body.take(400) + "…" else body)
                }
            }
            if (result.isCritical) append("\n\n⚠️ 关键更新，建议尽快升级")
        }
        AlertDialog.Builder(context)
            .setTitle("发现新版本 ${result.latestVersion}")
            .setMessage(message)
            .setPositiveButton("前往更新") { _, _ ->
                manager.openRelease(context, result.releaseUrl)
            }
            .setNegativeButton("稍后", null)
            .setCancelable(!result.isCritical)
            .show()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUpdateEntryPoint {
    fun appUpdateManager(): AppUpdateManager
}
