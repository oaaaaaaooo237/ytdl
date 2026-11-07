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
@Config(sdk = [35], qualifiers = "zh-rCN")
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

    @Test fun resultsRespectIndependentPreferencesAndLeaveForegroundNotificationInPlace() {
        shadowOf(application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        controller.ensureChannel()
        val first = DownloadRequest("https://example.test/first", "一", DownloadRoute.DirectSingleFile("18"))
        val second = first.copy(taskId = "b".repeat(32))
        val third = first.copy(taskId = "c".repeat(32))
        val manager = application.getSystemService(android.app.NotificationManager::class.java)
        controller.notifyForegroundState(DownloadTaskState(DownloadStage.DownloadingVideo, third), 2)
        val completed = DownloadTaskState(DownloadStage.Completed, first)
        val failed = DownloadTaskState(DownloadStage.Failed, second)
        val disabled = com.garyapp.ytdl.core.settings.AppSettings(notifyDownloadCompleted = false, notifyDownloadFailed = false)
        controller.notifyResult(completed, disabled)
        controller.notifyResult(failed, disabled)
        assertEquals(1, manager.activeNotifications.size)

        controller.notifyResult(completed, disabled.copy(notifyDownloadCompleted = true))
        controller.notifyResult(failed, disabled.copy(notifyDownloadCompleted = true))
        assertEquals(setOf(null, first.taskId), manager.activeNotifications.map { it.tag }.toSet())
        controller.notifyResult(failed, disabled.copy(notifyDownloadFailed = true))
        controller.notifyResult(DownloadTaskState(DownloadStage.Failed, third), disabled.copy(notifyDownloadFailed = true))
        assertEquals(setOf(null, first.taskId, second.taskId, third.taskId), manager.activeNotifications.map { it.tag }.toSet())
        val foreground = manager.activeNotifications.single { it.id == NotificationController.NotificationId }.notification
        assertEquals("2 项任务进行中 · 正在下载视频", foreground.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(third.taskId, foreground.extras.getString(DownloadService.ExtraTaskId))
        assertTrue(foreground.flags and Notification.FLAG_ONGOING_EVENT != 0)
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
