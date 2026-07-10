package com.garyapp.ytdl

import java.io.File
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

        assertCurrentUrlPolicy(plan)
        assertCurrentUrlPolicy(visualPlan)
        assertCurrentUrlPolicy(smokeLedger)

        assertTrue(plan.contains("### Historical Record M9:"))
        val m9History = plan
            .substringAfter("### Historical Record M9:")
            .substringBefore("### Continuation Task M10:")
        assertTrue(m9History.contains("历史记录，已由 2026-07-10 地址集替代"))
    }

    private fun assertCurrentUrlPolicy(document: String) {
        assertTrue(document.contains("PqQNXB6hhUs"))
        assertTrue(document.contains("svoD582Pas4"))
        assertTrue(document.contains("oXFad1nt6v0"))
    }

    private fun sourceFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull(File::isFile)
            ?: error("找不到源码文件：${candidates.joinToString()}")
    }
}
