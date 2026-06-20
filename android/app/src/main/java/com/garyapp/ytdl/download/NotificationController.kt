package com.garyapp.ytdl.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class NotificationController(
    private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            "下载任务",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示正在进行的下载、合并和导出状态。"
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun buildForegroundNotification(state: DownloadTaskState): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ChannelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        return builder
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("YTDL 下载任务")
            .setContentText(state.stage.notificationText())
            .setOngoing(state.stage !in TerminalStages)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun notifyForegroundState(state: DownloadTaskState) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NotificationId, buildForegroundNotification(state))
    }

    private fun DownloadStage.notificationText(): String {
        return when (this) {
            DownloadStage.Idle -> "空闲"
            DownloadStage.Analyzing -> "正在分析"
            DownloadStage.Waiting -> "等待下载"
            DownloadStage.DownloadingVideo -> "正在下载视频"
            DownloadStage.DownloadingAudio -> "正在下载音频"
            DownloadStage.DownloadingSubtitles -> "正在下载字幕文件"
            DownloadStage.Merging -> "正在原生合并"
            DownloadStage.Exporting -> "导出待接入"
            DownloadStage.Completed -> "下载完成"
            DownloadStage.Failed -> "下载失败"
            DownloadStage.Canceled -> "已取消"
        }
    }

    companion object {
        const val ChannelId = "download_foreground"
        const val NotificationId = 1001

        private val TerminalStages = setOf(
            DownloadStage.Completed,
            DownloadStage.Failed,
            DownloadStage.Canceled,
            DownloadStage.Idle,
        )
    }
}
