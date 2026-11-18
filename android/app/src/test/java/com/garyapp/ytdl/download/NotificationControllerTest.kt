package com.garyapp.ytdl.download

import android.app.Application
import android.app.Notification
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationControllerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val controller = NotificationController(application)

    @Test
    fun notificationUsesOwnMonochromeSmallIconAndImmutableMainActivityContentIntent() {
        val stages = DownloadStage.values()

        stages.forEach { stage ->
            val notification = controller.buildForegroundNotification(DownloadTaskState(stage = stage))
            val smallIconId = notification.smallIcon.resId

            assertEquals(application.packageName, application.resources.getResourcePackageName(smallIconId))
            assertNotNull("$stage 缺少通知正文点击动作", notification.contentIntent)
            assertTrue("$stage 的通知正文 PendingIntent 必须 immutable", notification.contentIntent.isImmutable)

            notification.contentIntent.send()
            val launchIntent = shadowOf(application).nextStartedActivity
            assertEquals(MainActivity::class.java.name, launchIntent.component?.className)
            assertTrue(launchIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
            assertTrue(launchIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        }
    }

    @Test
    fun terminalNotificationsAutoCancelWhileRunningNotificationKeepsCancelAction() {
        listOf(DownloadStage.Completed, DownloadStage.Failed, DownloadStage.Canceled, DownloadStage.Idle).forEach { stage ->
            val notification = controller.buildForegroundNotification(DownloadTaskState(stage = stage))
            assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
            assertTrue(notification.actions.isNullOrEmpty())
        }

        val running = controller.buildForegroundNotification(
            DownloadTaskState(stage = DownloadStage.DownloadingVideo),
        )
        assertFalse(running.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(listOf("取消", "暂停"), running.actions.map { it.title.toString() })
        assertTrue(running.actions.all { it.actionIntent.isImmutable })
    }

    @Test fun taskNotificationActionsKeepOriginalIdentityAfterNextTaskNotification() {
        val firstRequest = DownloadRequest("https://example.test/first", "一", DownloadRoute.DirectSingleFile("18"))
        val secondRequest = firstRequest.copy(taskId = "b".repeat(32))
        val first = controller.buildForegroundNotification(DownloadTaskState.waiting(firstRequest)).actions.first().actionIntent
        val second = controller.buildForegroundNotification(DownloadTaskState.waiting(secondRequest)).actions.first().actionIntent
        assertFalse(first == second)
        assertEquals(firstRequest.taskId, shadowOf(first).savedIntent.getStringExtra(DownloadService.ExtraTaskId))
        assertEquals(secondRequest.taskId, shadowOf(second).savedIntent.getStringExtra(DownloadService.ExtraTaskId))
    }
}
