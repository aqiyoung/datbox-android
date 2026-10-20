package com.datbox.app.data.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import java.io.FileOutputStream
import java.io.OutputStream
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
        /** 系统「下载」目录下的子目录, 用户拆开文件管理器就能看到 */
        const val SUBDIR = "Datbox"
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

    /** @return 任务 id, 调用方可据此订阅完成状态 */
    fun enqueue(remotePath: String, fileName: String): String {
        val id = "${remotePath.hashCode()}_${System.currentTimeMillis()}"
        val task = DownloadTask(
            id = id,
            remotePath = remotePath,
            fileName = fileName,
            localPath = "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR/$fileName",
        )

        _downloads.value = _downloads.value + task
        startDownload(id)
        return id
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
            var dest: Dest? = null
            var written = 0L

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

                val target = createDest(task.fileName)
                dest = target
                updateLocalPath(id, target.displayPath)

                val call = client.newCall(req)
                activeCalls[id] = call
                call.execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("empty body")
                    val total = body.contentLength()

                    val output = target.openOutput() ?: error("无法写入 ${target.displayPath}")
                    output.use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                                written += read

                                if (written % (100 * 1024) < 8192) {
                                    updateProgress(id, written, total)
                                    showProgressNotification(id, task.fileName, written, total)
                                }
                            }
                        }
                    }
                }

                completed = true
                target.finish()
                updateProgress(id, written, written)
                updateStatus(id, DownloadStatus.COMPLETED, totalBytes = written)
                showCompleteNotification(id, task.fileName, target)

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
                    dest?.discard()
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

    private fun updateLocalPath(id: String, path: String) {
        _downloads.value = _downloads.value.map {
            if (it.id == id) it.copy(localPath = path) else it
        }
    }

    /** 下载落点: Android 10+ 走 MediaStore 写进系统「下载/Datbox」, 8/9 写同目录的公共路径。 */
    private inner class Dest(
        val displayPath: String,
        val uri: Uri,
        val mime: String,
        private val mediaRow: Boolean,
        private val file: File?,
    ) {
        fun openOutput(): OutputStream? =
            if (file != null) FileOutputStream(file) else context.contentResolver.openOutputStream(uri)

        /** MediaStore 的 IS_PENDING 要清掉, 否则图库/文件管理器看不到 */
        fun finish() {
            if (!mediaRow) return
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            runCatching { context.contentResolver.update(uri, values, null, null) }
        }

        fun discard() {
            if (file != null) file.delete() else runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private fun createDest(fileName: String): Dest {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法在系统下载目录创建文件")
            return Dest("${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR/$fileName", uri, mime, true, null)
        }

        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBDIR)
        val canWritePublic =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        val (dir, label) = if (canWritePublic && (publicDir.exists() || publicDir.mkdirs())) {
            publicDir to "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR"
        } else {
            val fallback = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "Downloads")
            fallback.mkdirs()
            fallback to "Android/data/${context.packageName}/files/Download"
        }
        val file = File(dir, fileName)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Dest("$label/$fileName", uri, mime, false, file)
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

    private fun showCompleteNotification(taskId: String, fileName: String, dest: Dest) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(dest.uri, dest.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPending = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(fileName)
            .setContentText("已保存到 ${dest.displayPath}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("已保存到 ${dest.displayPath}"))
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(0, "打开", openPending)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationIdFor(taskId), notification)
    }
}
