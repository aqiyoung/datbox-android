package com.datbox.app.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 只在本机留日志：Logcat + app 外部目录 logs/local.log，不上传任何数据 */
object TelemetryLog {
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
        write("I", tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        val fullMsg = if (t != null) "$msg: ${t.javaClass.simpleName}: ${t.message}" else msg
        Log.w(tag, fullMsg, t)
        write("W", tag, fullMsg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        val fullMsg = if (t != null) "$msg: ${t.javaClass.simpleName}: ${t.message}" else msg
        Log.e(tag, fullMsg, t)
        write("E", tag, fullMsg)
    }

    private fun write(level: String, tag: String, msg: String) {
        if (logFile == null) return
        scope.launch {
            try {
                val f = logFile ?: return@launch
                val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)
                f.appendText("${timeFmt.format(Date())} $level/$tag: $msg\n")
                if (f.length() > 500_000) {
                    val lines = f.readLines().takeLast(2000)
                    f.writeText(lines.joinToString("\n") + "\n")
                }
            } catch (_: Throwable) {
            }
        }
    }
}
