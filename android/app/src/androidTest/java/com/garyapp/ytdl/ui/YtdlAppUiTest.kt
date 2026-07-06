package com.garyapp.ytdl.ui

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.data.YtdlDatabaseProvider
import com.garyapp.ytdl.download.DownloadCoordinator
import com.garyapp.ytdl.storage.ExportController
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YtdlAppUiTest {
    private lateinit var device: UiDevice
    private val packageName = "com.garyapp.ytdl"

    @Before
    fun launchApp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull("找不到 $packageName 的启动入口", context.packageManager.getLaunchIntentForPackage(packageName))
        cancelActiveDownload()
        DownloadCoordinator.resetForTests()
        openAppToDownloadPage()
    }

    private fun openAppToDownloadPage() {
        device.pressHome()
        device.executeShellCommand("am start -W -n $packageName/.MainActivity")
        if (findTag("ytdl-screen-download", timeoutMs = 5_000) == null) {
            findTag("ytdl-tab-download", timeoutMs = 5_000)?.click()
        }
        assertTrue(
            "应用未进入前台下载页：$packageName",
            findTag("ytdl-screen-download", timeoutMs = 5_000) != null,
        )
    }

    @After
    fun cleanupAppState() {
        cancelActiveDownload()
        DownloadCoordinator.resetForTests()
    }

    @Test
    fun bottomTabsNavigateAcrossFiveReferenceScreens() {
        assertTagVisible("ytdl-screen-download")
        assertTagVisible("ytdl-download-start")

        tapTag("ytdl-tab-formats")
        assertTagVisible("ytdl-screen-formats")
        assertTagVisible("ytdl-format-empty-card")
        assertTextContains("请先分析视频", timeoutMs = 1_000)

        tapTag("ytdl-tab-queue")
        assertTagVisible("ytdl-screen-queue")
        assertTagVisible("ytdl-queue-active-card")
        assertTagNotVisible("ytdl-queue-scroll-indicator")

        tapTag("ytdl-tab-history")
        assertTagVisible("ytdl-screen-history")
        assertTrue(
            "历史页应显示空状态或真实历史卡片",
            findTag("ytdl-history-empty-card", timeoutMs = 1_000) != null ||
                findTag("ytdl-history-real-card", timeoutMs = 1_000) != null,
        )

        tapTag("ytdl-tab-settings")
        assertTagVisible("ytdl-screen-settings")
        assertTagVisible("ytdl-settings-media-processor")
        assertTagVisible("ytdl-settings-notification-permission")
    }

    @Test
    fun settingsAppearanceColorPresetsAreVisibleAndSummaryUpdates() {
        tapTag("ytdl-tab-settings")
        scrollUntilTag("ytdl-settings-color-preset-1")
        assertTextContains("基准图配色", timeoutMs = 1_000)
        assertTextContains("Codex 风格", timeoutMs = 1_000)

        try {
            tapTag("ytdl-settings-color-preset-0")
            assertTextContains("基准图配色 · 跟随系统", timeoutMs = 2_000)
            tapTag("ytdl-settings-color-preset-1")
            assertTextContains("Codex 风格 · 跟随系统", timeoutMs = 2_000)
        } finally {
            findTag("ytdl-settings-color-preset-0", timeoutMs = 1_000)?.click()
            device.waitForIdle()
        }
    }

    @Test
    fun queueScreenShowsEmptyStateWithoutDemoFailureCard() {
        tapTag("ytdl-tab-queue")
        assertTagVisible("ytdl-queue-active-card")
        assertTagNotVisible("ytdl-queue-scroll-indicator")
        assertTextContains("暂无真实下载任务", timeoutMs = 1_000)
    }

    @Test
    fun downloadPageRunsRealAnalyzeAndDownloadFlow() {
        runRealAnalyzePreviewAndDownloadFlow(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            screenshotPrefix = "",
            expectedTitleText = "Jalen Brunson",
            completeDownloadAndHistory = true,
        )
    }

    @Test
    fun downloadPageCanCancelRunningForegroundTask() {
        startRealDownloadFromDownloadPage(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            expectedTitleText = "Jalen Brunson",
        )

        tapTag("ytdl-tab-queue")
        assertTagVisible("ytdl-real-queue-card", timeoutMs = 30_000)
        val cancelRequestedAt = System.currentTimeMillis()
        tapTag("ytdl-queue-cancel-action")
        assertAnyTextContains(
            texts = listOf("已请求取消当前下载", "下载已取消", "最近任务已取消"),
            timeoutMs = 120_000,
        )
        assertLatestHistoryEventuallyCanceled(cancelRequestedAt)
        saveScreen("08-queue-canceled.png")
    }

    @Test
    fun notificationActionCanCancelRunningForegroundTask() {
        grantNotificationPermissionIfRuntimeRequired()
        startRealDownloadFromDownloadPage(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            expectedTitleText = "Jalen Brunson",
        )

        val cancelRequestedAt = System.currentTimeMillis()
        openNotificationShadeAndTapCancel()
        assertLatestHistoryEventuallyCanceled(cancelRequestedAt)
        saveScreen("09-notification-canceled.png")
    }

    @Test
    fun notificationPermissionDeniedStillShowsInAppProgress() {
        revokeNotificationPermissionIfRuntimeRequired()
        openAppToDownloadPage()

        try {
            tapTag("ytdl-tab-settings")
            assertTextContains("未授权 · 下载仍在应用内显示进度", timeoutMs = 5_000)
            assertTextContains("请求", timeoutMs = 1_000)

            tapTag("ytdl-tab-download")
            startRealDownloadFromDownloadPage(
                url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
                expectedTitleText = "Jalen Brunson",
            )

            tapTag("ytdl-tab-queue")
            assertTagVisible("ytdl-real-queue-card", timeoutMs = 30_000)
            assertTagVisible("ytdl-queue-stage-strip", timeoutMs = 60_000)
            assertTextContains("下载视频", timeoutMs = 5_000)
            assertTextContains("下载音频", timeoutMs = 5_000)
            assertTextContains("原生合并", timeoutMs = 5_000)
            saveScreen("10-notification-denied-in-app-progress.png")

            val cancelRequestedAt = System.currentTimeMillis()
            tapTag("ytdl-queue-cancel-action")
            assertAnyTextContains(
                texts = listOf("已请求取消当前下载", "下载已取消", "最近任务已取消"),
                timeoutMs = 120_000,
            )
            assertLatestHistoryEventuallyCanceled(cancelRequestedAt)
        } finally {
            grantNotificationPermissionIfRuntimeRequired()
        }
    }

    @Test
    fun shortsUrlRunsRealAnalyzePreviewAndDownloadFlow() {
        runRealAnalyzePreviewAndDownloadFlow(
            url = "https://www.youtube.com/shorts/QBwpO9f0oAw",
            screenshotPrefix = "shorts-",
            expectedTitleText = "Luka and Jalen",
            completeDownloadAndHistory = false,
        )
    }

    @Test
    fun historyDeleteRequiresConfirmationForInsertedTestRecord() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        val title = "UITEST_DELETE_CONFIRM_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        val id = historyDao.insert(
            HistoryItemEntity.createSafe(
                title,
                8,
                "https",
                "test-host",
                "video",
                "app-private://outputs/task-uitest-delete/merged-test.mp4",
                "视频+音频 · 测试记录",
                HistoryItemEntity.STATUS_COMPLETED,
                100,
                "",
                "",
                "",
                now,
                now,
                now,
            ),
        )

        try {
            tapTag("ytdl-tab-history")
            assertTextContains(title, timeoutMs = 5_000)

            tapTag("ytdl-history-action-$id-删除")
            assertTagVisible("ytdl-history-delete-confirm")
            assertTextContains(title, timeoutMs = 1_000)
            assertTrue("点击删除后不应直接删除测试记录", historyContains(id))

            tapTag("ytdl-history-delete-cancel")
            assertTrue("取消删除后测试记录必须保留", historyContains(id))

            tapTag("ytdl-history-action-$id-删除")
            assertTagVisible("ytdl-history-delete-confirm")
            assertTextContains(title, timeoutMs = 1_000)
            tapTag("ytdl-history-delete-confirm")
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline && historyContains(id)) {
                Thread.sleep(200)
            }
            assertTrue("确认删除后必须删除测试记录", !historyContains(id))
        } finally {
            historyDao.deleteById(id)
        }
    }

    @Test
    fun historyMissingOutputActionsShowVisibleRecovery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        val title = "UITEST_MISSING_OUTPUT_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        val id = historyDao.insert(
            HistoryItemEntity.createSafe(
                title,
                8,
                "https",
                "test-host",
                "video",
                "app-private://outputs/task-missing-output/merged-test.mp4",
                "视频+音频 · 缺失输出测试",
                HistoryItemEntity.STATUS_COMPLETED,
                100,
                "",
                "",
                "",
                now,
                now,
                now,
            ),
        )

        try {
            tapTag("ytdl-tab-history")
            assertTextContains(title, timeoutMs = 5_000)

            tapTag("ytdl-history-action-$id-打开")
            assertTextContains("本地文件不存在或为空", timeoutMs = 2_000)
            assertTextContains("重新下载", timeoutMs = 1_000)

            tapTag("ytdl-history-action-$id-分享")
            assertTextContains("本地文件不存在或为空", timeoutMs = 2_000)

            tapTag("ytdl-history-action-$id-导出")
            assertTextContains("本地文件不存在或为空", timeoutMs = 2_000)
            saveScreen("12-history-missing-output-recovery.png")
        } finally {
            historyDao.deleteById(id)
        }
    }

    @Test
    fun seedForegroundDeleteRecordWhenExplicitlyRequested() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("seedForegroundDelete") != "true") {
            return
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        val now = System.currentTimeMillis()
        val title = "UITEST_FOREGROUND_DELETE_M9_4_$now"
        val id = historyDao.insert(
            HistoryItemEntity.createSafe(
                title,
                8,
                "https",
                "test-host",
                "video",
                "app-private://outputs/task-uitest-foreground-delete/merged-test.mp4",
                "视频+音频 · 前台删除测试",
                HistoryItemEntity.STATUS_COMPLETED,
                100,
                "",
                "",
                "",
                now,
                now,
                now,
            ),
        )

        assertTrue("必须插入前台删除测试记录", historyContains(id))
    }

    @Test
    fun seedForegroundMissingOutputRecordWhenExplicitlyRequested() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("seedForegroundMissingOutput") != "true") {
            return
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        val now = System.currentTimeMillis()
        val title = "UITEST_MISSING_OUTPUT_M9_5_$now"
        val id = historyDao.insert(
            HistoryItemEntity.createSafe(
                title,
                8,
                "https",
                "test-host",
                "video",
                "app-private://outputs/task-missing-output-m9-5/merged-test.mp4",
                "视频+音频 · 缺失输出前台测试",
                HistoryItemEntity.STATUS_COMPLETED,
                100,
                "",
                "",
                "",
                now,
                now,
                now,
            ),
        )

        assertTrue("必须插入前台缺失输出测试记录", historyContains(id))
    }

    @Test
    fun cleanupForegroundMissingOutputRecordsWhenExplicitlyRequested() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("cleanupForegroundMissingOutput") != "true") {
            return
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        historyDao.deleteByTitlePrefix("UITEST_MISSING_OUTPUT_M9_5_")

        val remaining = historyDao.listRecent(100)
            .filter { it.title.orEmpty().startsWith("UITEST_MISSING_OUTPUT_M9_5_") }
        assertTrue("前台缺失输出测试记录必须被精确清理", remaining.isEmpty())
    }

    @Test
    fun seedForegroundExportCancelRecordWhenExplicitlyRequested() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("seedForegroundExportCancel") != "true") {
            return
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        val root = File(context.filesDir, "gui-downloads")
        val taskDir = File(root, "task-export-cancel-m9-6").apply { mkdirs() }
        val output = File(taskDir, "export-cancel-test.mp4").apply {
            writeBytes(ByteArray(4096) { index -> (index % 251).toByte() })
        }
        val now = System.currentTimeMillis()
        val title = "UITEST_EXPORT_CANCEL_M9_6_$now"
        val id = historyDao.insert(
            HistoryItemEntity.createSafe(
                title,
                8,
                "https",
                "test-host",
                "video",
                ExportController.appPrivateOutputUri(output.absolutePath, root.absolutePath),
                "视频+音频 · 导出取消前台测试",
                HistoryItemEntity.STATUS_COMPLETED,
                100,
                "",
                "",
                "",
                now,
                now,
                now,
            ),
        )

        assertTrue("必须插入前台导出取消测试记录", historyContains(id))
        assertTrue("必须创建小型 app-private 测试输出", output.isFile && output.length() == 4096L)
    }

    @Test
    fun cleanupForegroundExportCancelRecordsWhenExplicitlyRequested() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("cleanupForegroundExportCancel") != "true") {
            return
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        historyDao.deleteByTitlePrefix("UITEST_EXPORT_CANCEL_M9_6_")
        File(context.filesDir, "gui-downloads/task-export-cancel-m9-6").deleteRecursively()

        val remaining = historyDao.listRecent(100)
            .filter { it.title.orEmpty().startsWith("UITEST_EXPORT_CANCEL_M9_6_") }
        assertTrue("前台导出取消测试记录必须被精确清理", remaining.isEmpty())
        assertTrue("前台导出取消测试文件夹必须被清理", !File(context.filesDir, "gui-downloads/task-export-cancel-m9-6").exists())
    }

    @Test
    fun seedForegroundSubtitleOutputRecordWhenExplicitlyRequested() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("seedForegroundSubtitleOutput") != "true") {
            return
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        val root = File(context.filesDir, "gui-downloads")
        val taskDir = File(root, "task-subtitle-output-m9-8").apply { mkdirs() }
        val mediaOutput = File(taskDir, "subtitle-output-media.mp4").apply {
            writeBytes(ByteArray(4096) { index -> (index % 251).toByte() })
        }
        val subtitleOutput = File(taskDir, "subtitle-output.zh-Hans.vtt").apply {
            writeText(
                "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nM9.8 subtitle output foreground smoke\n",
                Charsets.UTF_8,
            )
        }
        val now = System.currentTimeMillis()
        val title = "UITEST_SUBTITLE_OUTPUT_M9_8_$now"
        val id = historyDao.insert(
            HistoryItemEntity.createSafe(
                title,
                8,
                "https",
                "test-host",
                "video",
                ExportController.appPrivateOutputUri(mediaOutput.absolutePath, root.absolutePath),
                ExportController.appPrivateOutputUri(subtitleOutput.absolutePath, root.absolutePath),
                "720p MP4 · 独立字幕前台测试",
                null,
                HistoryItemEntity.STATUS_COMPLETED,
                100,
                "",
                "",
                "",
                now,
                now,
                now,
            ),
        )

        assertTrue("必须插入前台独立字幕测试记录", historyContains(id))
        assertTrue("必须创建小型 app-private 媒体测试输出", mediaOutput.isFile && mediaOutput.length() == 4096L)
        assertTrue("必须创建小型 app-private 字幕测试输出", subtitleOutput.isFile && subtitleOutput.length() > 0L)
    }

    @Test
    fun cleanupForegroundSubtitleOutputRecordsWhenExplicitlyRequested() {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("cleanupForegroundSubtitleOutput") != "true") {
            return
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        historyDao.deleteByTitlePrefix("UITEST_SUBTITLE_OUTPUT_M9_8_")
        File(context.filesDir, "gui-downloads/task-subtitle-output-m9-8").deleteRecursively()

        val remaining = historyDao.listRecent(100)
            .filter { it.title.orEmpty().startsWith("UITEST_SUBTITLE_OUTPUT_M9_8_") }
        assertTrue("前台独立字幕测试记录必须被精确清理", remaining.isEmpty())
        assertTrue("前台独立字幕测试文件夹必须被清理", !File(context.filesDir, "gui-downloads/task-subtitle-output-m9-8").exists())
    }

    @Test
    fun historyCardLoadsThumbnailForInsertedTestRecord() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        val title = "UITEST_THUMBNAIL_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        val id = historyDao.insert(
            HistoryItemEntity.createSafe(
                title,
                8,
                "https",
                "test-host",
                "video",
                "",
                "视频+音频 · 缩略图测试",
                "https://i.ytimg.com/vi/tkxzMEfp49Q/hqdefault.jpg?token=secret",
                HistoryItemEntity.STATUS_COMPLETED,
                100,
                "",
                "",
                "",
                now,
                now,
                now,
            ),
        )

        try {
            tapTag("ytdl-tab-history")
            assertTextContains(title, timeoutMs = 5_000)
            assertTagVisible("ytdl-history-thumbnail-image", timeoutMs = 20_000)
            saveScreen("11-history-thumbnail.png")
        } finally {
            historyDao.deleteById(id)
        }
    }

    private fun startRealDownloadFromDownloadPage(
        url: String,
        expectedTitleText: String?,
    ) {
        setTextTag("ytdl-url-input", url)
        tapTag("ytdl-analyze-button")
        assertTextContains("分析完成", timeoutMs = 45_000)
        expectedTitleText?.let { assertTextContains(it, timeoutMs = 1_000) }

        tapTag("ytdl-tab-formats")
        scrollUntilTag("ytdl-format-row-1080")
        tapTag("ytdl-format-row-1080")
        scrollUntilTag("ytdl-format-apply")
        tapTag("ytdl-format-apply")
        tapTag("ytdl-download-authorized-checkbox")
        tapTag("ytdl-download-start")
    }

    private fun runRealAnalyzePreviewAndDownloadFlow(
        url: String,
        screenshotPrefix: String,
        expectedTitleText: String?,
        completeDownloadAndHistory: Boolean,
    ) {
        setTextTag("ytdl-url-input", url)
        saveScreen("${screenshotPrefix}02-url.png")

        tapTag("ytdl-analyze-button")
        assertTextContains("分析完成", timeoutMs = 45_000)
        expectedTitleText?.let { assertTextContains(it, timeoutMs = 1_000) }
        assertTagVisible("ytdl-thumbnail-image", timeoutMs = 15_000)
        saveScreen("${screenshotPrefix}03-analysis.png")

        tapTag("ytdl-tab-formats")
        scrollUntilTag("ytdl-format-row-1080")
        assertTextContains("1080p", timeoutMs = 1_000)
        assertTextContains("需原生合并", timeoutMs = 1_000)
        tapTag("ytdl-format-row-1080")
        scrollUntilTag("ytdl-format-apply")
        tapTag("ytdl-format-apply")
        assertTagVisible("ytdl-screen-download")
        assertTextContains("1080p", timeoutMs = 1_000)
        tapTag("ytdl-download-authorized-checkbox")
        saveScreen("${screenshotPrefix}03-format-applied.png")

        if (!completeDownloadAndHistory) {
            return
        }

        val downloadStartedAt = System.currentTimeMillis()
        tapTag("ytdl-download-start")
        saveScreen("${screenshotPrefix}04-download-started.png")

        tapTag("ytdl-tab-queue")
        assertTagVisible("ytdl-real-queue-card", timeoutMs = 30_000)
        assertAnyTextContains(
            texts = listOf("下载视频", "下载音频", "原生合并", "正在下载", "下载进行中"),
            timeoutMs = 60_000,
        )
        saveScreen("${screenshotPrefix}05-queue-active.png")

        assertAnyTextContains(
            texts = listOf("下载完成", "最近任务已完成"),
            timeoutMs = 600_000,
        )
        assertTagVisible("ytdl-real-queue-card", timeoutMs = 5_000)
        saveScreen("${screenshotPrefix}06-queue-complete.png")
        assertLatestHistoryBelongsToCurrentDownload(downloadStartedAt, expectedTitleText)

        tapTag("ytdl-tab-history")
        assertTagVisible("ytdl-history-real-card", timeoutMs = 30_000)
        expectedTitleText?.let { assertTextContains(it, timeoutMs = 5_000) }
        assertTextContains("完成", timeoutMs = 5_000)
        saveScreen("${screenshotPrefix}07-history-real-card.png")
    }

    private fun assertLatestHistoryBelongsToCurrentDownload(startedAt: Long, expectedTitleText: String?) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val latest = YtdlDatabaseProvider.get(context).historyDao().listRecent(1).firstOrNull()
        assertNotNull("本轮下载完成后未写入最新历史记录", latest)
        latest!!
        assertEquals("最新历史记录不是完成态", HistoryItemEntity.STATUS_COMPLETED, latest.status)
        assertTrue(
            "最新历史记录不是本轮下载写入：completedAt=${latest.completedAt}, startedAt=$startedAt",
            latest.completedAt >= startedAt,
        )
        expectedTitleText?.let { title ->
            assertTrue("最新历史标题不属于本轮测试视频：${latest.title}", latest.title.contains(title))
        }
        assertTrue(
            "最新历史缺少 app-private 输出 URI：${latest.outputUri}",
            latest.outputUri.startsWith("app-private://outputs/task-"),
        )
        assertTrue(
            "最新历史没有指向合并媒体文件：${latest.outputUri}",
            latest.outputUri.contains("/merged-"),
        )
        assertTrue(
            "最新历史格式摘要未证明视频+音频合并：${latest.formatSummary}",
            latest.formatSummary.contains("视频") && latest.formatSummary.contains("音频"),
        )
    }

    private fun assertLatestHistoryEventuallyCanceled(cancelRequestedAt: Long) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        val deadline = System.currentTimeMillis() + 120_000
        var latestRecent: HistoryItemEntity? = null
        while (System.currentTimeMillis() < deadline) {
            val recentRows = historyDao.listRecent(20).filter { it.completedAt >= cancelRequestedAt }
            latestRecent = recentRows.firstOrNull()
            if (recentRows.any { it.status == HistoryItemEntity.STATUS_CANCELED }) {
                return
            }
            assertTrue(
                "取消流程不应写成完成历史：status=${latestRecent?.status}, outputUri=${latestRecent?.outputUri}",
                recentRows.none { it.status == HistoryItemEntity.STATUS_COMPLETED },
            )
            Thread.sleep(1_000)
        }
        assertEquals("取消流程必须写入 canceled 历史", HistoryItemEntity.STATUS_CANCELED, latestRecent?.status)
    }

    private fun historyContains(id: Long): Boolean {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return YtdlDatabaseProvider.get(context).historyDao().listRecent(100).any { it.id == id }
    }

    private fun cancelActiveDownload() {
        DownloadCoordinator.cancelActive()
        device.executeShellCommand(
            "am startservice -a com.garyapp.ytdl.download.CANCEL -n $packageName/.download.DownloadService",
        )
        device.waitForIdle()
    }

    private fun grantNotificationPermissionIfRuntimeRequired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
            clearNotificationPermissionFlagsIfRuntimeRequired()
        }
    }

    private fun revokeNotificationPermissionIfRuntimeRequired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device.executeShellCommand("pm revoke $packageName android.permission.POST_NOTIFICATIONS")
            device.executeShellCommand("pm set-permission-flags $packageName android.permission.POST_NOTIFICATIONS user-set")
            clearNotificationPermissionFlagsIfRuntimeRequired(clearUserSet = false)
        }
    }

    private fun clearNotificationPermissionFlagsIfRuntimeRequired(clearUserSet: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (clearUserSet) {
                device.executeShellCommand("pm clear-permission-flags $packageName android.permission.POST_NOTIFICATIONS user-set")
            }
            device.executeShellCommand("pm clear-permission-flags $packageName android.permission.POST_NOTIFICATIONS user-fixed")
        }
    }

    private fun openNotificationShadeAndTapCancel() {
        device.openNotification()
        val notificationTitle = device.wait(Until.findObject(By.textContains("YTDL 下载任务")), 10_000)
        assertNotNull("通知栏未显示 YTDL 下载任务", notificationTitle)
        assertAnyTextContains(
            texts = listOf("正在下载视频", "正在下载音频", "正在原生合并", "等待下载"),
            timeoutMs = 10_000,
        )
        saveScreen("09-notification-before-cancel.png")

        val titleBounds = notificationTitle!!.visibleBounds
        device.click(device.displayWidth - 120, titleBounds.centerY())
        device.waitForIdle()
        saveScreen("09-notification-expanded.png")

        val cancelAction = waitForSystemNotificationCancelAction(titleBounds.top)
        assertNotNull("通知栏未显示下载取消 action", cancelAction)
        cancelAction!!.click()
        device.waitForIdle()
        device.pressBack()
    }

    private fun waitForSystemNotificationCancelAction(minTop: Int): UiObject2? {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val candidates = device.findObjects(By.pkg("com.android.systemui").text("取消"))
                .filter { it.visibleBounds.top >= minTop }
                .sortedBy { it.visibleBounds.top }
            if (candidates.isNotEmpty()) {
                return candidates.first()
            }
            device.waitForIdle()
            Thread.sleep(250)
        }
        return null
    }

    private fun tapTag(tag: String) {
        val node = findTag(tag)
        assertNotNull("未找到可点击 UI 节点：$tag", node)
        node!!.click()
        device.waitForIdle()
    }

    private fun assertTagVisible(tag: String, timeoutMs: Long = 5_000) {
        val node = findTag(tag, timeoutMs = timeoutMs)
        assertNotNull("未看到页面 UI 节点：$tag", node)
    }

    private fun assertTagNotVisible(tag: String, timeoutMs: Long = 800) {
        val node = findTag(tag, timeoutMs = timeoutMs)
        assertTrue("不应看到页面 UI 节点：$tag", node == null)
    }

    private fun scrollUntilTag(tag: String) {
        repeat(4) {
            if (findTag(tag, timeoutMs = 500) != null) return
            swipeContentUp()
        }
        assertTagVisible(tag)
    }

    private fun swipeContentUp() {
        device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.76f).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.28f).toInt(),
            24,
        )
        device.waitForIdle()
    }

    private fun setTextTag(tag: String, value: String) {
        val node = findTag(if (tag == "ytdl-url-input") "ytdl_url_input" else tag)
        assertNotNull("未找到可输入 UI 节点：$tag", node)
        node!!.text = value
        device.waitForIdle()
    }

    private fun assertTextContains(text: String, timeoutMs: Long) {
        assertTrue(
            "未看到文本：$text",
            device.wait(Until.hasObject(By.textContains(text)), timeoutMs),
        )
    }

    private fun assertAnyTextContains(texts: List<String>, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val matched = texts.any { text ->
                device.hasObject(By.textContains(text))
            }
            if (matched) return
            device.waitForIdle()
            Thread.sleep(500)
        }
        assertTrue("未看到任一文本：${texts.joinToString(" / ")}", false)
    }

    private fun saveScreen(name: String) {
        device.executeShellCommand("mkdir -p /sdcard/Download/ytdl-visible-real-flow")
        val remotePath = "/sdcard/Download/ytdl-visible-real-flow/$name"
        device.executeShellCommand("screencap -p $remotePath")
        val listing = device.executeShellCommand("ls -l $remotePath")
        assertTrue("截图失败：$name", listing.contains(name))
    }

    private fun findTag(tag: String, timeoutMs: Long = 5_000): UiObject2? {
        val candidates = if (tag == "ytdl-url-input") listOf(tag, "ytdl_url_input") else listOf(tag)
        for (candidate in candidates) {
            device.wait(Until.findObject(By.res(packageName, candidate)), timeoutMs)?.let { return it }
            device.wait(Until.findObject(By.res(candidate)), 500)?.let { return it }
        }
        return null
    }
}
