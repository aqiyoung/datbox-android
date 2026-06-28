package com.threel.openlist.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.threel.openlist.MainActivity
import com.threel.openlist.data.api.TokenStore
import com.threel.openlist.util.AppConfig
import com.threel.openlist.util.TelemetryLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadTask(
    val id: String,
    val remotePath: String,
    val fileName: String,
    val localPath: String,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val error: String? = null
)

enum class DownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }

@Singleton
class AppDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
) {
    companion object {
        const val TAG = "AppDownload"
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1001
    }

    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads = _downloads.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    init {
        createNotificationChannel()
    }

    fun enqueue(remotePath: String, fileName: String) {
        val id = "${remotePath.hashCode()}_${System.currentTimeMillis()}"
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Downloads").also { it.mkdirs() }
        val localPath = File(downloadsDir, fileName).absolutePath

        val task = DownloadTask(
            id = id,
            remotePath = remotePath,
            fileName = fileName,
            localPath = localPath,
        )

        _downloads.value = _downloads.value + task
        startDownload(id)
    }

    fun cancel(id: String) {
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(status = DownloadStatus.CANCELLED) else it
        }
    }

    fun retry(id: String) {
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(status = DownloadStatus.PENDING, error = null) else it
        }
        startDownload(id)
    }

    fun remove(id: String) {
        _downloads.value = _downloads.value.filter { it.id != id }
    }

    private fun startDownload(id: String) {
        scope.launch {
            val task = _downloads.value.find { it.id == id } ?: return@launch
            updateStatus(id, DownloadStatus.DOWNLOADING)

            try {
                // 1. 拿 sign
                val serverUrl = AppConfig.PUBLIC_BASE_URL.trimEnd('/')
                val token = tokenStore.tokenSync()
                val getBody = """{"path":"${task.remotePath}"}"""
                val getRequest = Request.Builder()
                    .url("$serverUrl/api/fs/get")
                    .addHeader("Authorization", token)
                    .post(getBody.toRequestBody())
                    .build()

                val sign = client.newCall(getRequest).execute().use { resp ->
                    if (!resp.isSuccessful) error("fs/get HTTP ${resp.code}")
                    val body = resp.body?.string() ?: error("empty body")
                    val match = Regex("\"sign\"\\s*:\\s*\"([^\"]+)\"").find(body)
                        ?: error("no sign in response")
                    match.groupValues[1]
                }

                // 2. 下载文件
                val downloadUrl = "$serverUrl/d${task.remotePath}?sign=$sign"
                val req = Request.Builder().url(downloadUrl).get().build()
                val dir = File(task.localPath).parentFile
                dir?.mkdirs()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("empty body")
                    val total = body.contentLength()
                    var downloaded = 0L

                    body.byteStream().use { input ->
                        File(task.localPath).outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read

                                if (downloaded % (100 * 1024) < 8192) {
                                    updateProgress(id, downloaded, total)
                                    showProgressNotification(task.fileName, downloaded, total)
                                }
                            }
                        }
                    }
                }

                updateStatus(id, DownloadStatus.COMPLETED, totalBytes = task.totalBytes)
                showCompleteNotification(task.fileName)

            } catch (e: CancellationException) {
                updateStatus(id, DownloadStatus.CANCELLED)
            } catch (e: Exception) {
                TelemetryLog.e(TAG, "download FAIL: ${task.remotePath}", e)
                updateStatus(id, DownloadStatus.FAILED, error = e.message)
            }
        }
    }

    private fun updateStatus(id: String, status: DownloadStatus, totalBytes: Long = 0, error: String? = null) {
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(status = status, totalBytes = totalBytes, error = error) else it
        }
    }

    private fun updateProgress(id: String, downloaded: Long, total: Long) {
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(downloadedBytes = downloaded, totalBytes = total) else it
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "文件下载", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示文件下载进度"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(fileName: String, current: Long, total: Long) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val progress = if (total > 0) ((current * 100) / total).toInt() else 0

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("下载中: $fileName")
            .setContentText("${current / 1024}KB / ${total / 1024}KB ($progress%)")
            .setProgress(100, progress, total <= 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification(fileName: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText(fileName)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}

private fun String.toRequestBody(): okhttp3.RequestBody {
    return this.toRequestBody("application/json; charset=utf-8".toMediaType())
}

private fun String.toRequestBody(mediaType: okhttp3.MediaType): okhttp3.RequestBody {
    return okhttp3.RequestBody.create(mediaType, this.toByteArray())
}

private fun String.toMediaType() = okhttp3.MediaType.parse(this)
