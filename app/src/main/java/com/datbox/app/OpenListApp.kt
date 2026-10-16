package com.datbox.app

import android.app.Application
import com.datbox.app.util.AppConfig
import com.datbox.app.util.TelemetryLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OpenListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TelemetryLog.init(this)
        try {
            TelemetryLog.i(
                "AppStart",
                "APP_STARTED versionName=${AppConfig.versionName(this)} versionCode=${AppConfig.versionCode(this)}",
            )
        } catch (t: Throwable) {
            android.util.Log.e("OpenListApp", "telemetry init failed", t)
            TelemetryLog.e("AppStart", "telemetry init failed", t)
        }
    }
}
