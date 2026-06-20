package com.garyapp.ytdl.core.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplitDownloadRequestTest {
    @Test
    fun parsesSplitDownloadJsonWithRoleFormatIdBytesAndPath() {
        val result = YtdlpBridge.parseDownloadJson(
            """
            {
              "ok": true,
              "title": "sample",
              "formatId": "137",
              "role": "video",
              "outputPath": "/data/user/0/com.garyapp.ytdl/cache/download-tkxzMEfp49Q-137-video.mp4",
              "bytesWritten": 123456
            }
            """.trimIndent(),
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        val download = result.getOrThrow()
        assertEquals("sample", download.title)
        assertEquals("137", download.formatId)
        assertEquals(DownloadFormatRole.Video, download.role)
        assertEquals(
            "/data/user/0/com.garyapp.ytdl/cache/download-tkxzMEfp49Q-137-video.mp4",
            download.outputPath,
        )
        assertEquals(123456L, download.bytesWritten)
    }

    @Test
    fun rejectsBlankFormatIdBeforeCallingPython() {
        val bridge = YtdlpBridge(
            pythonProvider = {
                throw AssertionError("Python provider must not be called for invalid format ids")
            },
        )

        val result = bridge.downloadFormat(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            outputDirectory = java.io.File("build/split-download-test"),
            formatId = " ",
            role = DownloadFormatRole.Video,
        )

        val error = result.exceptionOrNull() as YtdlpDownloadException
        assertEquals(AnalysisErrorCategory.Unsupported, error.category)
        assertTrue(error.safeMessage.contains("format id", ignoreCase = true))
    }

    @Test
    fun rejectsFormatSelectorFallbackBeforeCallingPython() {
        val bridge = YtdlpBridge(
            pythonProvider = {
                throw AssertionError("Python provider must not be called for selector expressions")
            },
        )

        val result = bridge.downloadFormat(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            outputDirectory = java.io.File("build/split-download-test"),
            formatId = "394/18/worst",
            role = DownloadFormatRole.Video,
        )

        val error = result.exceptionOrNull() as YtdlpDownloadException
        assertEquals(AnalysisErrorCategory.Unsupported, error.category)
        assertTrue(error.safeMessage.contains("format id", ignoreCase = true))
    }

    @Test
    fun rejectsNamedYtDlpSelectorsBeforeCallingPython() {
        val bridge = YtdlpBridge(
            pythonProvider = {
                throw AssertionError("Python provider must not be called for selector aliases")
            },
        )

        listOf(
            "best",
            "worst",
            "bestvideo",
            "bestaudio",
            "worstvideo",
            "worstaudio",
            "bv",
            "ba",
            "wv",
            "wa",
            "b",
            "w",
            "all",
            "mergeall",
            "bv.2",
        ).forEach { selector ->
            val result = bridge.downloadFormat(
                url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
                outputDirectory = java.io.File("build/split-download-test"),
                formatId = selector,
                role = DownloadFormatRole.Video,
            )

            val error = result.exceptionOrNull() as YtdlpDownloadException
            assertEquals("selector $selector", AnalysisErrorCategory.Unsupported, error.category)
            assertTrue("selector $selector", error.safeMessage.contains("format id", ignoreCase = true))
        }
    }

    @Test
    fun rejectsSuccessfulDownloadJsonWithEmptyOutputPath() {
        val result = YtdlpBridge.parseDownloadJson(
            """
            {
              "ok": true,
              "title": "sample",
              "formatId": "140",
              "role": "audio",
              "outputPath": "",
              "bytesWritten": 42
            }
            """.trimIndent(),
        )

        val error = result.exceptionOrNull() as YtdlpDownloadException
        assertEquals(AnalysisErrorCategory.Unknown, error.category)
        assertTrue(error.safeMessage.contains("输出文件", ignoreCase = true))
    }

    @Test
    fun rejectsSuccessfulDownloadJsonWithZeroBytes() {
        val result = YtdlpBridge.parseDownloadJson(
            """
            {
              "ok": true,
              "title": "sample",
              "formatId": "140",
              "role": "audio",
              "outputPath": "/tmp/download.m4a",
              "bytesWritten": 0
            }
            """.trimIndent(),
        )

        val error = result.exceptionOrNull() as YtdlpDownloadException
        assertEquals(AnalysisErrorCategory.Unknown, error.category)
        assertTrue(error.safeMessage.contains("输出文件", ignoreCase = true))
    }

    @Test
    fun rejectsUnknownRoleFromDownloadJson() {
        val result = YtdlpBridge.parseDownloadJson(
            """
            {
              "ok": true,
              "title": "sample",
              "formatId": "140",
              "role": "subtitle",
              "outputPath": "/tmp/download.m4a",
              "bytesWritten": 42
            }
            """.trimIndent(),
        )

        val error = result.exceptionOrNull() as YtdlpDownloadException
        assertEquals(AnalysisErrorCategory.Unsupported, error.category)
        assertTrue(error.safeMessage.contains("role", ignoreCase = true))
    }

    @Test
    fun splitDownloadRoleUsesYtDlpBridgeValues() {
        assertEquals("video", DownloadFormatRole.Video.pythonValue)
        assertEquals("audio", DownloadFormatRole.Audio.pythonValue)
        assertFalse(DownloadFormatRole.values().any { it.pythonValue == "18/worst" })
    }
}
