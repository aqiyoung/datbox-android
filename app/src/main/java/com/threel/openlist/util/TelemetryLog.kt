package com.threel.openlist.util

import android.content.Context
import android.util.Log
import com.threel.openlist.data.api.toJsonRequestBody
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

/**
 * 老板 6/14 11:35 拍: APP 端实时日志 → Telegram
 *
 * 双路输出:
 * - HTTP POST https://<your-server>/api/openlist-android/log (W/E 等级 → Telegram)
 * - 落盘 /sdcard/Android/data/com.threel.openlist/files/logs/local.log (防网络不通)
 * - 失败静默: 不影响 APP 功能
 *
 * 端点: https://<your-server>/api/openlist-android/log
 * server: ~/dev-projects/ricoui-astro-starter/dist/api/openlist-android/log_server.py
 */
object TelemetryLog {
    private const val TAG = "Telemetry"
    private const val ENDPOINT = "https://<your-server>/api/openlist-android/log"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile private var logFile: File? = null

    /** 老板 6/14 11:56 反馈 '失败' 装 v0.3.10 收不到 log - 补落盘兜底 */
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
        // 全部挪进 IO 协程: 之前落盘在调用线程(常常是主线程), 触发裁剪时还会整文件重读重写
        scope.launch {
            // 1. 落盘 (网络不通时的兜底)
            try {
                logFile?.let { f ->
                    val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)  // 局部新建: IO 协程可能多线程并发
                    val line = "${timeFmt.format(Date())} ${level}/${tag}: ${msg}\n"
                    f.appendText(line)
                    // 保持只后 2000 行
                    if (f.length() > 500_000) {
                        val lines = f.readLines().takeLast(2000)
                        f.writeText(lines.joinToString("\n") + "\n")
                    }
                }
            } catch (_: Throwable) {
            }
            // 2. 推 server + Telegram (JSON 交给序列化器转义; 之前手拼, 消息里带 \ 或引号就产出非法 JSON)
            try {
                val body = TelemetryLogBody(level, tag.take(40), msg.take(800)).toJsonRequestBody()
                val req = Request.Builder().url(ENDPOINT).post(body).build()
                client.newCall(req).execute().close()
            } catch (_: Throwable) {
                // 静默: telemetry 失败不影响 APP
            }
        }
    }
}
