package com.garyapp.ytdl.core.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubtitleDownloadRequestTest {
    @Test
    fun parsesManualAndAutomaticSubtitlesFromAnalysisJson() {
        val result = YtdlpBridge.parseAnalysisJson(
            """
            {
              "ok": true,
              "title": "sample",
              "duration": 42,
              "thumbnail": "",
              "formats": [],
              "subtitles": {
                "en": [{"ext": "vtt"}],
                "zh-Hans": [{"ext": "json3"}]
              },
              "automatic_captions": {
                "ja": [{"ext": "srv3"}],
                "en": [{"ext": "vtt"}]
              }
            }
            """.trimIndent(),
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals(
            listOf(
                SubtitleInfo(language = "en", ext = "vtt", source = SubtitleSource.Manual),
                SubtitleInfo(language = "zh-Hans", ext = "json3", source = SubtitleSource.Manual),
                SubtitleInfo(language = "ja", ext = "srv3", source = SubtitleSource.Automatic),
                SubtitleInfo(language = "en", ext = "vtt", source = SubtitleSource.Automatic),
            ),
            result.getOrThrow().subtitles,
        )
    }

    @Test
    fun parsesSubtitleDownloadJsonWithPathBytesLanguageExtensionAndSource() {
        val result = YtdlpBridge.parseSubtitleDownloadJson(
            """
            {
              "ok": true,
              "title": "sample",
              "language": "en",
              "ext": "vtt",
              "source": "automatic",
              "outputPath": "/data/user/0/com.garyapp.ytdl/cache/download-tkxzMEfp49Q-subtitle-automatic.en.vtt",
              "bytesWritten": 2048
            }
            """.trimIndent(),
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        val download = result.getOrThrow()
        assertEquals("sample", download.title)
        assertEquals("en", download.language)
        assertEquals("vtt", download.ext)
        assertEquals(SubtitleSource.Automatic, download.source)
        assertEquals(
            "/data/user/0/com.garyapp.ytdl/cache/download-tkxzMEfp49Q-subtitle-automatic.en.vtt",
            download.outputPath,
        )
        assertEquals(2048L, download.bytesWritten)
    }

    @Test
    fun rejectsSubtitleSelectorExpressionsBeforeCallingPython() {
        val bridge = YtdlpBridge(
            pythonProvider = {
                throw AssertionError("Python provider must not be called for invalid subtitle selectors")
            },
        )

        listOf(
            "en/zh-Hans" to "vtt",
            "all" to "vtt",
            "en" to "vtt/srt",
            "en" to "best",
            "en.*" to "vtt",
            "en" to "vtt,ttml",
        ).forEach { (language, ext) ->
            val result = bridge.downloadSubtitle(
                url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
                outputDirectory = File("build/subtitle-download-test"),
                language = language,
                ext = ext,
                source = SubtitleSource.Manual,
            )

            val error = result.exceptionOrNull() as YtdlpDownloadException
            assertEquals("language=$language ext=$ext", AnalysisErrorCategory.Unsupported, error.category)
            assertTrue(
                "language=$language ext=$ext message=${error.safeMessage}",
                error.safeMessage.contains("字幕", ignoreCase = true),
            )
        }
    }

    @Test
    fun rejectsInvalidSubtitleDownloadJson() {
        val zeroBytes = YtdlpBridge.parseSubtitleDownloadJson(
            """
            {
              "ok": true,
              "title": "sample",
              "language": "en",
              "ext": "vtt",
              "source": "manual",
              "outputPath": "/tmp/download.en.vtt",
              "bytesWritten": 0
            }
            """.trimIndent(),
        )
        val zeroBytesError = zeroBytes.exceptionOrNull() as YtdlpDownloadException
        assertEquals(AnalysisErrorCategory.Unknown, zeroBytesError.category)
        assertTrue(zeroBytesError.safeMessage.contains("字幕文件", ignoreCase = true))

        val unknownSource = YtdlpBridge.parseSubtitleDownloadJson(
            """
            {
              "ok": true,
              "title": "sample",
              "language": "en",
              "ext": "vtt",
              "source": "translated",
              "outputPath": "/tmp/download.en.vtt",
              "bytesWritten": 42
            }
            """.trimIndent(),
        )
        val unknownSourceError = unknownSource.exceptionOrNull() as YtdlpDownloadException
        assertEquals(AnalysisErrorCategory.Unsupported, unknownSourceError.category)
        assertTrue(unknownSourceError.safeMessage.contains("source", ignoreCase = true))
    }

    @Test
    fun subtitleFailureParsingRedactsSensitiveText() {
        val result = YtdlpBridge.parseSubtitleDownloadJson(
            """
            {
              "ok": false,
              "errorCategory": "permission",
              "errorMessage": "failed url=https://example.com/watch?v=abc&token=secret --cookies \"C:/private path/cookies.txt\" Authorization: Bearer abc Cookie: SID=secret"
            }
            """.trimIndent(),
        )

        val error = result.exceptionOrNull() as YtdlpDownloadException
        assertEquals(AnalysisErrorCategory.Permission, error.category)
        assertFalse(error.safeMessage.contains("token=secret"))
        assertFalse(error.safeMessage.contains("cookies.txt"))
        assertFalse(error.safeMessage.contains("private path"))
        assertFalse(error.safeMessage.contains("Bearer abc"))
        assertFalse(error.safeMessage.contains("SID=secret"))
        assertTrue(error.safeMessage.contains("[已隐藏]"))
    }

    @Test
    fun subtitleSourceUsesStablePythonValues() {
        assertEquals("manual", SubtitleSource.Manual.pythonValue)
        assertEquals("automatic", SubtitleSource.Automatic.pythonValue)
        assertEquals(SubtitleSource.Manual, SubtitleSource.fromPythonValue("manual"))
        assertEquals(SubtitleSource.Automatic, SubtitleSource.fromPythonValue("automatic"))
    }
}
