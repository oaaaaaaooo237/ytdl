package com.garyapp.ytdl.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
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
        assertTagVisible("ytdl-queue-scroll-indicator")

        tapTag("ytdl-tab-history")
        assertTagVisible("ytdl-screen-history")
        assertTagVisible("ytdl-history-first-card")

        tapTag("ytdl-tab-settings")
        assertTagVisible("ytdl-screen-settings")
        assertTagVisible("ytdl-settings-media-processor")
    }

    @Test
    fun queueScreenCanScrollToFailureCard() {
        tapTag("ytdl-tab-queue")
        assertTagVisible("ytdl-queue-active-card")
        assertTagVisible("ytdl-queue-scroll-indicator")

        repeat(4) {
            if (findTag("ytdl-queue-failed-card", timeoutMs = 500) != null) return@repeat
            swipeContentUp()
        }

        assertTagVisible("ytdl-queue-failed-card")
    }

    @Test
    fun downloadPageRunsRealAnalyzeAndDownloadFlow() {
        runRealAnalyzePreviewAndDownloadFlow(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            screenshotPrefix = "",
            expectedTitleText = "Jalen Brunson",
        )
    }

    @Test
    fun shortsUrlRunsRealAnalyzePreviewAndDownloadFlow() {
        runRealAnalyzePreviewAndDownloadFlow(
            url = "https://www.youtube.com/shorts/QBwpO9f0oAw",
            screenshotPrefix = "shorts-",
            expectedTitleText = "Luka and Jalen",
        )
    }

    private fun runRealAnalyzePreviewAndDownloadFlow(
        url: String,
        screenshotPrefix: String,
        expectedTitleText: String?,
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
        saveScreen("${screenshotPrefix}03-format-applied.png")
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
