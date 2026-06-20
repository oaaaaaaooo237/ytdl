package com.garyapp.ytdl.core.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
    fun parsesProgressiveMediaDownloadJsonWithRoleFormatIdBytesAndPath() {
        val result = YtdlpBridge.parseDownloadJson(
            """
            {
              "ok": true,
              "title": "sample",
              "formatId": "18",
              "role": "media",
              "outputPath": "/data/user/0/com.garyapp.ytdl/cache/download-tkxzMEfp49Q-18-media.mp4",
              "bytesWritten": 44556988
            }
            """.trimIndent(),
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        val download = result.getOrThrow()
        assertEquals("18", download.formatId)
        assertEquals(DownloadFormatRole.Media, download.role)
        assertEquals(
            "/data/user/0/com.garyapp.ytdl/cache/download-tkxzMEfp49Q-18-media.mp4",
            download.outputPath,
        )
        assertEquals(44556988L, download.bytesWritten)
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
    fun downloadFormatDoesNotConvertFatalPythonProviderErrorsToResultFailure() {
        val bridge = YtdlpBridge(
            pythonProvider = {
                throw AssertionError("fatal python bridge invariant")
            },
        )

        assertThrows(AssertionError::class.java) {
            bridge.downloadFormat(
                url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
                outputDirectory = java.io.File("build/split-download-test"),
                formatId = "18",
                role = DownloadFormatRole.Media,
            )
        }
    }

    @Test
    fun parseDownloadJsonMapsCanceledCategory() {
        val result = YtdlpBridge.parseDownloadJson(
            """
            {
              "ok": false,
              "errorCategory": "canceled",
              "errorMessage": "用户已取消下载。"
            }
            """.trimIndent(),
        )

        val error = result.exceptionOrNull() as YtdlpDownloadException
        assertEquals(AnalysisErrorCategory.Canceled, error.category)
        assertTrue(error.safeMessage.contains("取消"))
    }

    @Test
    fun pythonProgressBridgeDoesNotSilentlySwallowListenerExceptions() {
        val source = sourceFile(
            "app/src/main/python/ytdl_bridge.py",
            "src/main/python/ytdl_bridge.py",
        ).readText()

        assertTrue(source.contains("class ProgressListenerException"))
        assertTrue(source.contains("raise ProgressListenerException"))
        assertFalse(source.contains("except Exception:\n        pass"))
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
        assertEquals("media", DownloadFormatRole.Media.pythonValue)
        assertEquals("video", DownloadFormatRole.Video.pythonValue)
        assertEquals("audio", DownloadFormatRole.Audio.pythonValue)
        assertFalse(DownloadFormatRole.values().any { it.pythonValue == "18/worst" })
    }

    private fun sourceFile(vararg candidates: String): java.io.File {
        return candidates
            .map { java.io.File(it) }
            .firstOrNull { it.isFile }
            ?: error("source file not found: ${candidates.joinToString()}")
    }
}
