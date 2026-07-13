package com.garyapp.ytdl

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealYoutubeTestUrlsContractTest {
    @Test
    fun realYoutubeSupportUsesTheCurrentThreeTestUrls() {
        val source = sourceFile(
            "app/src/androidTest/java/com/garyapp/ytdl/RealYoutubeTestSupport.kt",
            "src/androidTest/java/com/garyapp/ytdl/RealYoutubeTestSupport.kt",
        ).readText()

        val expectedUrls = listOf(
            "https://www.youtube.com/watch?v=PqQNXB6hhUs",
            "https://www.youtube.com/watch?v=svoD582Pas4",
            "https://www.youtube.com/shorts/oXFad1nt6v0",
        )
        val urls = Regex("\\\"(https://[^\\\"]+)\\\"")
            .findAll(source)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(expectedUrls, urls)
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
            "## 2026-07-10 后续执行边界",
        )
    }

    private fun assertCurrentUrlPolicy(document: String, start: String, end: String) {
        val startIndex = document.indexOf(start)
        val endIndex = document.indexOf(end, startIndex + start.length)
        assertTrue("当前策略起止标记缺失或顺序错误：$start -> $end", startIndex >= 0 && endIndex > startIndex)
        val policy = document.substring(startIndex, endIndex)
        val expectedUrls = setOf(
            "https://www.youtube.com/watch?v=PqQNXB6hhUs",
            "https://www.youtube.com/watch?v=svoD582Pas4",
            "https://www.youtube.com/shorts/oXFad1nt6v0",
        )
        val urls = Regex("https://[^\\s`，。；）)]+")
            .findAll(policy)
            .map { it.value.trimEnd('`', '，', '。', '；', '）', ')') }
            .filter { it.startsWith("https://www.youtube.com/") }
            .toList()

        assertEquals(expectedUrls.sorted(), urls.sorted())
        expectedUrls.forEach { url ->
            assertEquals("当前策略中的完整地址必须只出现一次：$url", 1, urls.count { it == url })
        }
    }

    private fun assertHistoricalBlock(document: String, start: String, end: String) {
        val startIndex = document.indexOf(start)
        val endIndex = document.indexOf(end, startIndex + start.length)
        assertTrue("历史块起止标记缺失或顺序错误：$start -> $end", startIndex >= 0 && endIndex > startIndex)
        val block = document.substring(startIndex, endIndex)
        val historicalUrls = listOf(
            "https://youtu.be/lcFR2mFSmSs?si=FqJ3ZTdKRq6NAt6G",
        )
        historicalUrls.forEach { url ->
            assertTrue("历史块缺少历史完整地址：$url", block.contains(url))
        }
    }

    private fun sourceFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull(File::isFile)
            ?: error("找不到源码文件：${candidates.joinToString()}")
    }
}
