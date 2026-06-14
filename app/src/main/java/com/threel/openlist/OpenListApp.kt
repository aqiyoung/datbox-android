package com.threel.openlist

import android.app.Application
import com.threel.openlist.util.TelemetryLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OpenListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 老板 6/14 11:56 反馈: 装 v0.3.10 后说 '失败' 但 log 收不到
        // TelemetryLog.init 先初始化落盘路径
        TelemetryLog.init(this)
        // 加这个 log 验证: 老板手机 APP 启动时, server 一定能收到 APP_STARTED
        try {
            val pkgInfo = packageManager.getPackageInfo(packageName, 0)
            val verName = pkgInfo.versionName ?: "?"
            val verCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            TelemetryLog.i("AppStart", "APP_STARTED versionName=$verName versionCode=$verCode")
        } catch (t: Throwable) {
            android.util.Log.e("OpenListApp", "telemetry init failed", t)
            TelemetryLog.e("AppStart", "telemetry init failed", t)
        }
    }
}
