package com.garyapp.ytdl

import android.content.Intent
import com.garyapp.ytdl.download.DownloadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationNavigationActivityTest {
    @Test fun notificationNavigationIsConsumedBeforeActivityRecreation() {
        val intent = Intent(Intent.ACTION_MAIN).setAction(MainActivity.ActionOpenTasks)
            .setData(android.net.Uri.parse("ytdl://tasks/first"))
            .putExtra(DownloadService.ExtraTaskId, "first")
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        try {
            assertNull(controller.get().intent.action)
            assertNull(controller.get().intent.data)
            assertNull(controller.get().intent.getStringExtra(DownloadService.ExtraTaskId))
            controller.recreate()
            assertNull(controller.get().intent.action)
            val field = MainActivity::class.java.getDeclaredField("openTasksRequest").apply { isAccessible = true }
            val request = field.get(controller.get()) as androidx.compose.runtime.MutableState<*>
            assertEquals(0, request.value)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
