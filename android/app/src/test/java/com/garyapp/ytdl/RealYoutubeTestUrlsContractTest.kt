package com.garyapp.ytdl

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealYoutubeTestUrlsContractTest {
    @Test
    fun realYoutubeSupportUsesTheCurrentThreeTestUrls() {
        val source = sourceFile(
            "app/src/androidTest/java/com/garyapp/ytdl/RealYoutubeTestSupport.kt",
            "src/androidTest/java/com/garyapp/ytdl/RealYoutubeTestSupport.kt",
        ).readText()

        assertTrue(source.contains("https://www.youtube.com/watch?v=PqQNXB6hhUs"))
        assertTrue(source.contains("https://www.youtube.com/shorts/oXFad1nt6v0"))
        assertTrue(source.contains("https://www.youtube.com/watch?v=svoD582Pas4"))
        assertFalse(source.contains("lcFR2mFSmSs"))
        assertFalse(source.contains("auNezUzwCZg"))
        assertFalse(source.contains("jWTrleK2_MU"))
    }

    @Test
    fun currentAndroidExecutionInstructionsUseTheCurrentThreeTestUrls() {
        val plan = sourceFile(
            "../docs/superpowers/plans/2026-06-19-ytdl-android-play-mvp.md",
            "../../docs/superpowers/plans/2026-06-19-ytdl-android-play-mvp.md",
        ).readText()
        val visualPlan = sourceFile(
            "../docs/qa/android-full-visual-test-plan.md",
            "../../docs/qa/android-full-visual-test-plan.md",
        ).readText()
        val smokeLedger = sourceFile(
            "../docs/qa/android-mvp-smoke.md",
            "../../docs/qa/android-mvp-smoke.md",
        ).readText()

        assertCurrentUrlPolicy(
            plan,
            "- Use current real test URLs for full-flow verification:",
            "- Real YouTube connected tests are skipped by default",
        )
        assertCurrentUrlPolicy(
            visualPlan,
            "- 固定真实测试地址：",
            "## 硬性验收规则",
        )
        assertCurrentUrlPolicy(
            smokeLedger,
            "2026-07-10 测试地址替换：",
            "## 本轮已确认",
        )

        assertTrue(plan.contains("### Historical Record M9:"))
        val m9History = plan
            .substringAfter("### Historical Record M9:")
            .substringBefore("### Continuation Task M10:")
        assertTrue(m9History.contains("历史记录，已由 2026-07-10 地址集替代"))

        assertHistoricalBlock(
            smokeLedger,
            "### 2026-07-10 M11 前台格式过滤与容量失败恢复复核（历史记录，旧地址证据）",
            "### 2026-07-10 容量预检目标修正",
        )
        assertHistoricalBlock(
            smokeLedger,
            "### 2026-07-10 容量修正前台复验与测试清理（历史记录，旧地址证据）",
            "### 2026-07-10 M11 格式行只显示当前视频提供的高度",
        )
    }

    private fun assertCurrentUrlPolicy(document: String, start: String, end: String) {
        val policy = document.substringAfter(start).substringBefore(end)
        val expectedUrls = setOf(
            "https://www.youtube.com/watch?v=PqQNXB6hhUs",
            "https://www.youtube.com/watch?v=svoD582Pas4",
            "https://www.youtube.com/shorts/oXFad1nt6v0",
        )
        expectedUrls.forEach { url ->
            assertTrue("当前策略缺少完整地址：$url", policy.contains(url))
        }
        val urls = Regex("https://www\\.youtube\\.com/(?:watch\\?v=|shorts/)[A-Za-z0-9_-]+")
            .findAll(policy)
            .map { it.value }
            .toSet()

        assertEquals(expectedUrls, urls)
    }

    private fun assertHistoricalBlock(document: String, start: String, end: String) {
        assertTrue(document.contains(start))
        val block = document.substringAfter(start).substringBefore(end)
        assertTrue(block.contains("https://youtu.be/lcFR2mFSmSs"))
    }

    private fun sourceFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull(File::isFile)
            ?: error("找不到源码文件：${candidates.joinToString()}")
    }
}
