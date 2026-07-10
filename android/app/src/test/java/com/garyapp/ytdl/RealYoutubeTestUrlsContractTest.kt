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

    private fun sourceFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull(File::isFile)
            ?: error("找不到源码文件：${candidates.joinToString()}")
    }
}
