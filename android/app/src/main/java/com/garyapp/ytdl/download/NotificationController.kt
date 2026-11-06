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
import com.garyapp.ytdl.core.settings.AppLanguage
import com.garyapp.ytdl.core.settings.AppSettings
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.privacy.SensitiveText

class NotificationController(
    private val context: Context,
) {
    private fun currentLanguage(): AppLanguage = SettingsRepository.fromContext(context).getSettings().language
        .resolve(context.resources.configuration.locales[0])

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val language = currentLanguage()
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            language.pick("下载任务", "Downloads"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = language.pick("显示正在进行的下载、合并和保存状态。", "Shows active downloads, merging, and saving.")
        }
        manager.createNotificationChannel(channel)
        manager.createNotificationChannel(NotificationChannel(ResultChannelId, language.pick("下载结果", "Download results"), NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(PausedChannelId, language.pick("已暂停的下载", "Paused downloads"), NotificationManager.IMPORTANCE_LOW))
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

        val language = currentLanguage()
        val title = state.request?.title?.let { SensitiveText.redact(it) }?.takeIf { it.isNotBlank() }
        val taskStatus = if (state.fileRecoveryAction == FileRecoveryAction.ConfirmRestart) language.pick("需要确认重新下载", "Confirm redownload") else state.stage.notificationText(language)
        return builder
            .addExtras(android.os.Bundle().apply { putString(DownloadService.ExtraTaskId, state.request?.taskId) })
            .setSmallIcon(R.drawable.ic_stat_ytdl_download)
            .setContentTitle(title ?: language.pick("CaptainFlash 下载任务", "CaptainFlash download"))
            .setContentText(if (activeCount > 1) language.pick("$activeCount 项任务进行中 · $taskStatus", "$activeCount active tasks · $taskStatus") else taskStatus)
            .setContentIntent(contentPendingIntent())
            .setAutoCancel(state.stage in TerminalStages)
            .setOngoing(state.stage !in TerminalStages && (channelId != PausedChannelId || state.stage != DownloadStage.Paused))
            .setOnlyAlertOnce(true)
            .apply {
                if (state.stage !in TerminalStages) {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                            language.pick("取消", "Cancel"),
                            actionPendingIntent(DownloadService.ActionCancel, state.request?.taskId, 2),
                        ).build(),
                    )
                    if (state.stopReason != DownloadStopReason.UserCancel && state.stage != DownloadStage.Pausing) {
                        val resume = state.stage == DownloadStage.Paused
                        addAction(Notification.Action.Builder(
                            Icon.createWithResource(context, android.R.drawable.ic_media_play),
                            if (resume) language.pick("继续", "Resume") else language.pick("暂停", "Pause"),
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

    internal fun notifyPaused(state: DownloadTaskState) {
        val taskId = state.request?.taskId ?: return
        if (canPostNotifications()) {
            context.getSystemService(NotificationManager::class.java)
                .notify(taskId, PausedNotificationId, buildNotification(state, PausedChannelId))
        }
    }

    internal fun clearPaused(taskId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(taskId, PausedNotificationId)
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

    private fun DownloadStage.notificationText(language: AppLanguage): String {
        return when (this) {
            DownloadStage.Idle -> language.pick("空闲", "Idle")
            DownloadStage.Analyzing -> language.pick("正在分析", "Analyzing")
            DownloadStage.Pausing -> language.pick("正在暂停", "Pausing")
            DownloadStage.Paused -> language.pick("已暂停", "Paused")
            DownloadStage.WaitingForNetwork -> language.pick("等待网络", "Waiting for network")
            DownloadStage.Resuming -> language.pick("正在继续", "Resuming")
            DownloadStage.Waiting -> language.pick("等待下载", "Queued")
            DownloadStage.DownloadingFile -> language.pick("正在下载文件", "Downloading file")
            DownloadStage.DownloadingVideo -> language.pick("正在下载视频", "Downloading video")
            DownloadStage.DownloadingAudio -> language.pick("正在下载音频", "Downloading audio")
            DownloadStage.DownloadingSubtitles -> language.pick("正在处理附加文件", "Processing attachments")
            DownloadStage.Merging -> language.pick("正在原生合并", "Merging media")
            DownloadStage.Exporting -> language.pick("正在保存", "Saving")
            DownloadStage.Completed -> language.pick("下载完成", "Download completed")
            DownloadStage.Failed -> language.pick("下载失败", "Download failed")
            DownloadStage.Canceled -> language.pick("已取消", "Canceled")
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
        private const val PausedChannelId = "download_paused"
        private const val PausedNotificationId = 1003

        private val TerminalStages = setOf(
            DownloadStage.Completed,
            DownloadStage.Failed,
            DownloadStage.Canceled,
            DownloadStage.Idle,
        )
    }
}
