package com.datbox.app.util

import android.content.Context
import android.util.Log
import com.datbox.app.data.api.toJsonRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
private data class TelemetryLogBody(
    val level: String,
    val tag: String,
    val msg: String,
)

object TelemetryLog {
    private const val TAG = "Telemetry"
    private const val ENDPOINT = "https://<your-server>/api/openlist-android/log"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile private var logFile: File? = null

    fun init(context: Context) {
        try {
            val dir = File(context.getExternalFilesDir(null), "logs")
            if (!dir.exists()) dir.mkdirs()
            logFile = File(dir, "local.log")
        } catch (_: Throwable) {
        }
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        sendAsync("I", tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        val fullMsg = if (t != null) "$msg: ${t.javaClass.simpleName}: ${t.message}" else msg
        Log.w(tag, fullMsg, t)
        sendAsync("W", tag, fullMsg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        val fullMsg = if (t != null) "$msg: ${t.javaClass.simpleName}: ${t.message}" else msg
        Log.e(tag, fullMsg, t)
        sendAsync("E", tag, fullMsg)
    }

    private fun sendAsync(level: String, tag: String, msg: String) {
        scope.launch {
            try {
                logFile?.let { f ->
                    val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)
                    val line = "${timeFmt.format(Date())} ${level}/${tag}: ${msg}\n"
                    f.appendText(line)
                    if (f.length() > 500_000) {
                        val lines = f.readLines().takeLast(2000)
                        f.writeText(lines.joinToString("\n") + "\n")
                    }
                }
            } catch (_: Throwable) {
            }
            try {
                val body = TelemetryLogBody(level, tag.take(40), msg.take(800)).toJsonRequestBody()
                val req = Request.Builder().url(ENDPOINT).post(body).build()
                client.newCall(req).execute().close()
            } catch (_: Throwable) {
            }
        }
    }
}
