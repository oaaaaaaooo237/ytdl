package com.garyapp.ytdl.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.garyapp.ytdl.MainActivity
import com.garyapp.ytdl.R

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
            description = "显示正在进行的下载、合并和保存状态。"
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
            .addExtras(android.os.Bundle().apply { putString(DownloadService.ExtraTaskId, state.request?.taskId) })
            .setSmallIcon(R.drawable.ic_stat_ytdl_download)
            .setContentTitle("YTDL 下载任务")
            .setContentText(state.stage.notificationText())
            .setContentIntent(contentPendingIntent())
            .setAutoCancel(state.stage in TerminalStages)
            .setOngoing(state.stage !in TerminalStages)
            .setOnlyAlertOnce(true)
            .apply {
                if (state.stage !in TerminalStages) {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                            "取消",
                            actionPendingIntent(DownloadService.ActionCancel, state.request?.taskId, 2),
                        ).build(),
                    )
                    if (state.stopReason != DownloadStopReason.UserCancel && (state.stage == DownloadStage.Paused || state.stage !in setOf(DownloadStage.Pausing, DownloadStage.WaitingForNetwork))) {
                        val resume = state.stage == DownloadStage.Paused
                        addAction(Notification.Action.Builder(
                            Icon.createWithResource(context, android.R.drawable.ic_media_play),
                            if (resume) "继续" else "暂停",
                            actionPendingIntent(if (resume) DownloadService.ActionResume else DownloadService.ActionPause, state.request?.taskId, 3),
                        ).build())
                    }
                }
            }
            .build()
    }

    fun notifyForegroundState(state: DownloadTaskState) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NotificationId, buildForegroundNotification(state))
    }

    internal fun showsTask(taskId: String): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any {
            it.id == NotificationId && it.notification.extras.getString(DownloadService.ExtraTaskId) == taskId
        }

    private fun DownloadStage.notificationText(): String {
        return when (this) {
            DownloadStage.Idle -> "空闲"
            DownloadStage.Analyzing -> "正在分析"
            DownloadStage.Pausing -> "正在暂停"
            DownloadStage.Paused -> "已暂停"
            DownloadStage.WaitingForNetwork -> "等待网络"
            DownloadStage.Resuming -> "正在继续"
            DownloadStage.Waiting -> "等待下载"
            DownloadStage.DownloadingVideo -> "正在下载视频"
            DownloadStage.DownloadingAudio -> "正在下载音频"
            DownloadStage.DownloadingSubtitles -> "正在处理附加文件"
            DownloadStage.Merging -> "正在原生合并"
            DownloadStage.Exporting -> "正在保存"
            DownloadStage.Completed -> "下载完成"
            DownloadStage.Failed -> "下载失败"
            DownloadStage.Canceled -> "已取消"
        }
    }

    private fun actionPendingIntent(action: String, taskId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, DownloadService::class.java)
            .setAction(action).putExtra(DownloadService.ExtraTaskId, taskId)
            .setData(android.net.Uri.parse("ytdl://task/${taskId ?: "active"}/$action"))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) PendingIntent.getForegroundService(context, requestCode, intent, flags)
        else PendingIntent.getService(context, requestCode, intent, flags)
    }

    private fun contentPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, 1, intent, flags)
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
