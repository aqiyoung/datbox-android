package com.datbox.app.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.datbox.app.MainActivity
import com.datbox.app.data.api.FsGetRequest
import com.datbox.app.data.api.OpenListJson
import com.datbox.app.data.api.TokenStore
import com.datbox.app.data.api.toJsonRequestBody
import com.datbox.app.data.model.FsGetResponse
import com.datbox.app.util.TelemetryLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
        private const val FIRST_NOTIFICATION_ID = 1001
    }

    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads = _downloads.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val notificationIds = ConcurrentHashMap<String, Int>()
    private val nextNotificationId = AtomicInteger(FIRST_NOTIFICATION_ID)
    private fun notificationIdFor(taskId: String) =
        notificationIds.getOrPut(taskId) { nextNotificationId.getAndIncrement() }
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
        activeJobs.remove(id)?.cancel()
        activeCalls.remove(id)?.cancel()
    }

    fun retry(id: String) {
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(status = DownloadStatus.PENDING, error = null) else it
        }
        startDownload(id)
    }

    fun remove(id: String) {
        activeJobs.remove(id)?.cancel()
        activeCalls.remove(id)?.cancel()
        notificationIds.remove(id)?.let { cancelNotification(it) }
        _downloads.value = _downloads.value.filter { it.id != id }
        stopForegroundServiceIfIdle()
    }

    private fun startDownload(id: String) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val task = _downloads.value.find { it.id == id } ?: return@launch
            updateStatus(id, DownloadStatus.DOWNLOADING)
            var completed = false

            try {
                val serverUrl = tokenStore.serverUrlSync().trimEnd('/')
                val token = tokenStore.tokenSync()
                val getBody = FsGetRequest(task.remotePath).toJsonRequestBody()
                val getRequest = Request.Builder()
                    .url("$serverUrl/api/fs/get")
                    .addHeader("Authorization", token)
                    .post(getBody)
                    .build()

                val getCall = client.newCall(getRequest)
                activeCalls[id] = getCall
                val sign = getCall.execute().use { resp ->
                    if (!resp.isSuccessful) error("fs/get HTTP ${resp.code}")
                    val body = resp.body?.string() ?: error("empty body")
                    OpenListJson.decodeFromString<FsGetResponse>(body).data?.sign
                        ?: error("no sign in response")
                }

                val downloadUrl = "$serverUrl/d${task.remotePath}?sign=$sign"
                val req = Request.Builder().url(downloadUrl).get().build()
                val dir = File(task.localPath).parentFile
                dir?.mkdirs()

                val call = client.newCall(req)
                activeCalls[id] = call
                call.execute().use { resp ->
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
                                    showProgressNotification(id, task.fileName, downloaded, total)
                                }
                            }
                        }
                    }
                }

                completed = true
                updateStatus(id, DownloadStatus.COMPLETED, totalBytes = File(task.localPath).length())
                showCompleteNotification(id, task.fileName)

            } catch (e: CancellationException) {
                updateStatus(id, DownloadStatus.CANCELLED)
            } catch (e: Exception) {
                val cancelled = _downloads.value.firstOrNull { it.id == id }?.status == DownloadStatus.CANCELLED
                if (cancelled) {
                    updateStatus(id, DownloadStatus.CANCELLED)
                } else {
                    TelemetryLog.e(TAG, "download FAIL: ${task.remotePath.substringAfterLast('/')}", e)
                    updateStatus(id, DownloadStatus.FAILED, error = e.message)
                }
            } finally {
                activeCalls.remove(id)
                activeJobs.remove(id)
                if (!completed) {
                    File(task.localPath).delete()
                    cancelNotification(notificationIdFor(id))
                }
                if (activeJobs.isEmpty()) stopForegroundServiceIfIdle() else startForegroundService()
            }
        }
        activeJobs[id] = job
        startForegroundService()
        job.start()
    }

    private fun startForegroundService() {
        try {
            val intent = Intent(context, DownloadForegroundService::class.java)
                .putExtra(DownloadForegroundService.EXTRA_COUNT, activeJobs.size)
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            TelemetryLog.e(TAG, "start FGS fail", e)
        }
    }

    private fun stopForegroundServiceIfIdle() {
        if (activeJobs.isEmpty()) {
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }
    }

    private fun cancelNotification(id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
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

    private fun showProgressNotification(taskId: String, fileName: String, current: Long, total: Long) {
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
        manager.notify(notificationIdFor(taskId), notification)
    }

    private fun showCompleteNotification(taskId: String, fileName: String) {
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
        manager.notify(notificationIdFor(taskId), notification)
    }
}
