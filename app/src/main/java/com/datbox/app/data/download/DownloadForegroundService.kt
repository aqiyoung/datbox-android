package com.datbox.app.data.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * 下载前台服务：下载期间常驻一条"下载进行中"通知，让系统在 App 退后台时不回收进程，
 * 否则 OkHttp 下载协程会随进程一起死，下载中断且无任何提示。
 *
 * 生命周期由 [AppDownloadManager] 全权管理：有任务在跑时 startForegroundService（携带当前任务数），
 * 全部结束后 stopService。数量走 intent extra，服务本身无状态。
 */
class DownloadForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra(EXTRA_COUNT, 1) ?: 1
        val notification = NotificationCompat.Builder(this, AppDownloadManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 $count 个文件")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this,
            FGS_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        // 不要求被杀后重启: 下载协程本就随进程消亡, 空壳服务重启没有意义
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_COUNT = "count"
        const val FGS_NOTIFICATION_ID = 1000
    }
}
