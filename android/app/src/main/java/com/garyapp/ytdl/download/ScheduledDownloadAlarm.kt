package com.garyapp.ytdl.download

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

internal fun ResumeTaskItem.queuedScheduleTime(): Long? =
    state.request?.scheduledAtMillis?.takeIf {
        wasQueued && recoveryError == null && state.fileRecoveryAction == null &&
            state.stopReason != DownloadStopReason.UserPause
    }

internal object ScheduledDownloadAlarm {
    fun arm(context: Context, timeMillis: Long): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) return false
        return runCatching {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, trigger(context))
        }.isSuccess
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(trigger(context))
    }

    private fun trigger(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, ScheduledDownloadReceiver::class.java).apply { action = Action },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    const val Action = "com.garyapp.ytdl.SCHEDULED_DOWNLOAD"
}

class ScheduledDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ScheduledDownloadAlarm.Action && action != Intent.ACTION_BOOT_COMPLETED &&
            action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) return
        val result = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                if (action == ScheduledDownloadAlarm.Action) {
                    DownloadCoordinator.wakeScheduledDownloads(app)
                } else {
                    ResumeTaskRepository.fromContext(app).recover().asSequence()
                        .mapNotNull(ResumeTaskItem::queuedScheduleTime)
                        .minOrNull()
                        ?.let { ScheduledDownloadAlarm.arm(app, it) }
                }
            } catch (_: Exception) {
                // The persisted request remains available for recovery when the app opens.
            } finally {
                result.finish()
            }
        }.start()
    }
}
