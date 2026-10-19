package com.datbox.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.datbox.app.util.AppConfig
import com.datbox.app.util.TelemetryLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OpenListApp : Application(), ImageLoaderFactory {
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

    /** Coil 默认解码器不含 SVG，预览 .svg 会直接失败；这里补上，全局生效。 */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true)
            .build()
}
