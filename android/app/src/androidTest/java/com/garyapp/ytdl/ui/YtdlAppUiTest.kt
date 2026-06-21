package com.garyapp.ytdl.ui

import android.content.Context
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
        clearHistoryRows(context)
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
        assertTagVisible("ytdl-history-empty-card")

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
    fun shortsUrlRunsRealAnalyzePreviewAndDownloadFlow() {
        runRealAnalyzePreviewAndDownloadFlow(
            url = "https://www.youtube.com/shorts/QBwpO9f0oAw",
            screenshotPrefix = "shorts-",
            expectedTitleText = "Luka and Jalen",
            completeDownloadAndHistory = false,
        )
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

    private fun clearHistoryRows(context: Context) {
        val historyDao = YtdlDatabaseProvider.get(context).historyDao()
        repeat(50) {
            val rows = historyDao.listRecent(100)
            if (rows.isEmpty()) {
                return
            }
            rows.forEach { item ->
                historyDao.deleteById(item.id)
            }
        }
        assertTrue("历史清理后仍有残留记录", historyDao.listRecent(1).isEmpty())
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

    private fun cancelActiveDownload() {
        DownloadCoordinator.cancelActive()
        device.executeShellCommand(
            "am startservice -a com.garyapp.ytdl.download.CANCEL -n $packageName/.download.DownloadService",
        )
        device.waitForIdle()
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
        val node = findTag(tag)
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
        return device.wait(Until.findObject(By.res(packageName, tag)), timeoutMs)
            ?: device.wait(Until.findObject(By.res(tag)), 500)
    }
}
