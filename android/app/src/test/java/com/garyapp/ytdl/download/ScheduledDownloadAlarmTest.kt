package com.garyapp.ytdl.download

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ScheduledDownloadAlarmTest {
    @Test
    fun recoveryOnlyArmsAnEligibleQueuedSchedule() {
        val request = DownloadRequest(
            url = "https://example.org/video",
            title = "Scheduled video",
            route = DownloadRoute.DirectSingleFile("18"),
            scheduledAtMillis = 2_000L,
        )
        val state = DownloadTaskState.waiting(request)
        val queued = ResumeTaskItem(request.taskId, request.title, state, wasQueued = true)

        assertEquals(2_000L, queued.queuedScheduleTime())
        assertEquals(null, queued.copy(wasQueued = false).queuedScheduleTime())
        assertEquals(null, queued.copy(recoveryError = "Missing metadata").queuedScheduleTime())
        assertEquals(null, queued.copy(state = state.copy(stopReason = DownloadStopReason.UserPause)).queuedScheduleTime())
        assertEquals(null, queued.copy(state = state.copy(fileRecoveryAction = FileRecoveryAction.ChooseFileName)).queuedScheduleTime())
        assertEquals(null, queued.copy(state = state.copy(request = request.copy(scheduledAtMillis = null))).queuedScheduleTime())
    }

    @Test
    fun replacementKeepsOneAlarmAndCancelRemovesIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val later = System.currentTimeMillis() + 120_000L
        assertTrue(ScheduledDownloadAlarm.arm(context, later))
        assertTrue(ScheduledDownloadAlarm.arm(context, later - 60_000L))
        val scheduled = alarms.scheduledAlarms.single()
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled.type)
        assertEquals(later - 60_000L, scheduled.triggerAtTime)

        ScheduledDownloadAlarm.cancel(context)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }
}
