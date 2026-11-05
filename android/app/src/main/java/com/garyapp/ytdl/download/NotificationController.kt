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
import com.garyapp.ytdl.core.settings.AppSettings
import com.garyapp.ytdl.core.privacy.SensitiveText

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
        manager.createNotificationChannel(NotificationChannel(ResultChannelId, "下载结果", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun buildForegroundNotification(state: DownloadTaskState, activeCount: Int = 0): Notification =
        buildNotification(state, ChannelId, activeCount)

    private fun buildNotification(state: DownloadTaskState, channelId: String, activeCount: Int = 0): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val title = state.request?.title?.let { SensitiveText.redact(it) }?.takeIf { it.isNotBlank() }
        val taskStatus = if (state.fileRecoveryAction == FileRecoveryAction.ConfirmRestart) "需要确认重新下载" else state.stage.notificationText()
        return builder
            .addExtras(android.os.Bundle().apply { putString(DownloadService.ExtraTaskId, state.request?.taskId) })
            .setSmallIcon(R.drawable.ic_stat_ytdl_download)
            .setContentTitle(title ?: "CaptainFlash 下载任务")
            .setContentText(if (activeCount > 1) "$activeCount 项任务进行中 · $taskStatus" else taskStatus)
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
                    if (state.stopReason != DownloadStopReason.UserCancel && state.stage != DownloadStage.Pausing) {
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

    fun notifyForegroundState(state: DownloadTaskState, activeCount: Int = 0) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NotificationId, buildForegroundNotification(state, activeCount))
    }

    internal fun notifyResult(state: DownloadTaskState, settings: AppSettings) {
        val enabled = when (state.stage) {
            DownloadStage.Completed -> settings.notifyDownloadCompleted
            DownloadStage.Failed -> settings.notifyDownloadFailed
            else -> false
        }
        val taskId = state.request?.taskId ?: return
        if (enabled && canPostNotifications()) {
            context.getSystemService(NotificationManager::class.java)
                .notify(taskId, ResultNotificationId, buildNotification(state, ResultChannelId))
        }
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
            DownloadStage.DownloadingFile -> "正在下载文件"
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
        private const val ResultChannelId = "download_results"
        private const val ResultNotificationId = 1002

        private val TerminalStages = setOf(
            DownloadStage.Completed,
            DownloadStage.Failed,
            DownloadStage.Canceled,
            DownloadStage.Idle,
        )
    }
}
