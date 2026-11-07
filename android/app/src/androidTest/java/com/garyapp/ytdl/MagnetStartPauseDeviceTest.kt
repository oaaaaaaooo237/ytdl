package com.garyapp.ytdl

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.garyapp.ytdl.download.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Manual external-network probe. Always pauses the task, and never waits for completion. */
@RunWith(AndroidJUnit4::class)
class MagnetStartPauseDeviceTest {
    @Test fun pausedSampleSurvivesTargetProcessRestart() {
        org.junit.Assume.assumeTrue("Manual external probe only", InstrumentationRegistry.getArguments().getString("run_external_magnet_probe") == "true")
        org.junit.Assume.assumeTrue(BuildConfig.TORRENT_ENABLED)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = File(context.getExternalFilesDir(null), "magnet-start-pause-result.txt")
        org.junit.Assume.assumeTrue("Run the manual start/pause probe first", report.isFile)
        val id = report.readLines().first { it.startsWith("task=") }.substringAfter('=')
        val original = File(context.getExternalFilesDir(null), "user-magnet-test.txt").readText().trim()
        val task = ResumeTaskRepository.fromContext(context).recover().firstOrNull { it.taskId == id }
        assertNotNull(task)
        assertEquals(DownloadStage.Paused, task!!.state.stage)
        assertEquals(DownloadStopReason.UserPause, task.state.stopReason)
        assertTrue((task.state.progress?.downloadedBytes ?: 0) > 0)
        assertEquals(original, task.state.request?.url)
        report.appendText("cold_restart=${task.state.stage} bytes=${task.state.progress?.downloadedBytes}\n")
    }

    @Test fun suppliedMagnetReceivesDataAndRemainsPausedInTheSharedQueue() {
        org.junit.Assume.assumeTrue("Manual external probe only", InstrumentationRegistry.getArguments().getString("run_external_magnet_probe") == "true")
        org.junit.Assume.assumeTrue(BuildConfig.TORRENT_ENABLED)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.getExternalFilesDir(null), "user-magnet-test.txt")
        org.junit.Assume.assumeTrue("Requires the user-supplied sample", input.isFile)
        val link = input.readText().trim()
        val request = DownloadRequest(link, "Runner 下载启动及暂停验证", DownloadRoute.Torrent(link),
            usesCookies = false, formatSummary = "Torrent")
        val id = request.taskId
        val report = File(context.getExternalFilesDir(null), "magnet-start-pause-result.txt")
        report.writeText("task=$id\n")
        try {
            DownloadCoordinator.startForegroundDownload(context, request, File(context.filesDir, "gui-downloads")).getOrThrow()
            val deadline = System.nanoTime() + 150_000_000_000L
            var received = 0L
            while (System.nanoTime() < deadline) {
                val state = DownloadCoordinator.activeStates()[id]
                received = state?.progress?.downloadedBytes ?: received
                if (received > 0) break
                if (state?.stage in setOf(DownloadStage.Failed, DownloadStage.Canceled, DownloadStage.Completed)) {
                    report.appendText("terminal=${state?.stage} error=${state?.errorMessage}\n")
                    break
                }
                Thread.sleep(100)
            }
            report.appendText("received=$received\n")
            context.startService(Intent(context, DownloadService::class.java).setAction(DownloadService.ActionPause)
                .putExtra(DownloadService.ExtraTaskId, id))
            val pauseDeadline = System.nanoTime() + 15_000_000_000L
            while (System.nanoTime() < pauseDeadline && DownloadCoordinator.activeStates()[id]?.stage != DownloadStage.Paused) Thread.sleep(100)
            val restored = ResumeTaskRepository.fromContext(context).recover().firstOrNull { it.taskId == id }
            report.appendText("persisted=${restored?.state?.stage} bytes=${restored?.state?.progress?.downloadedBytes}\n")
            assertTrue("No payload received; see the probe report", received > 0)
            assertNotNull("Task must remain in the persistent queue", restored)
            assertEquals(DownloadStage.Paused, restored!!.state.stage)
            assertEquals(link, restored.state.request?.url)
        } finally {
            context.startService(Intent(context, DownloadService::class.java).setAction(DownloadService.ActionPause)
                .putExtra(DownloadService.ExtraTaskId, id))
        }
    }
}
