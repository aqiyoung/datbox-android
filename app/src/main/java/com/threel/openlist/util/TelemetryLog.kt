package com.threel.openlist.util

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 老板 6/14 11:35 拍: APP 端实时日志 -> Telegram
 *
 * - W/E 等级: 立即推 (老板能直接看到)
 * - I 等级: 写本地缓存 (避免噪声)
 * - 失败静默: 不影响 APP 功能
 *
 * 端点: https://<your-server>/api/openlist-android/log
 * server: ~/dev-projects/ricoui-astro-starter/dist/api/openlist-android/log_server.py
 */
object TelemetryLog {
    private const val TAG = "Telemetry"
    private const val ENDPOINT = "https://<your-server>/api/openlist-android/log"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO)

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
                val safeMsg = msg.replace("\"", "\\\"").replace("\n", " ").take(800)
                val body = """{"level":"$level","tag":"${tag.take(40)}","msg":"$safeMsg"}"""
                    .toRequestBody(JSON)
                val req = Request.Builder().url(ENDPOINT).post(body).build()
                client.newCall(req).execute().close()
            } catch (_: Throwable) {
                // 静默: telemetry 失败不影响 APP
            }
        }
    }
}
