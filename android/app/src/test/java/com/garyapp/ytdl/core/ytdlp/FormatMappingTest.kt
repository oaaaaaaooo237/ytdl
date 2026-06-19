package com.garyapp.ytdl.core.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FormatMappingTest {
    @Test
    fun mapsProgressive360pAsDirectAudioVideoFormat() {
        val format = YtdlpBridge.mapFormat(
            mapOf(
                "format_id" to "18",
                "ext" to "mp4",
                "height" to 360,
                "vcodec" to "avc1.42001E",
                "acodec" to "mp4a.40.2",
                "filesize" to 12_345_678,
            ),
        )

        assertEquals("18", format.id)
        assertEquals(360, format.height)
        assertEquals("360p", format.label)
        assertFalse(format.mergeRequired)
        assertTrue(format.isSupported)
        assertTrue(format.hasVideo)
        assertTrue(format.hasAudio)
    }

    @Test
    fun mapsVideoOnly1080pAsMergeRequiredWhenAudioFormatsExist() {
        val format = YtdlpBridge.mapFormat(
            raw = mapOf(
                "format_id" to "137",
                "ext" to "mp4",
                "height" to 1080,
                "vcodec" to "avc1.640028",
                "acodec" to "none",
            ),
            hasStandaloneAudio = true,
        )

        assertEquals("1080p 需合并音频", format.label)
        assertTrue(format.mergeRequired)
        assertTrue(format.isSupported)
        assertTrue(format.hasVideo)
        assertFalse(format.hasAudio)
    }

    @Test
    fun marksMissingHeightFormatAsUnsupported() {
        val format = YtdlpBridge.mapFormat(
            mapOf(
                "format_id" to "audio-only",
                "ext" to "m4a",
                "vcodec" to "none",
                "acodec" to "mp4a.40.2",
            ),
        )

        assertEquals(null, format.height)
        assertEquals("不支持", format.label)
        assertFalse(format.isSupported)
        assertFalse(format.mergeRequired)
    }

    @Test
    fun mapsSubtitleLanguageAndExtension() {
        val subtitles = YtdlpBridge.mapSubtitles(
            mapOf(
                "zh-Hans" to listOf(mapOf("ext" to "vtt"), mapOf("ext" to "json3")),
                "en" to listOf(mapOf("ext" to "srv3")),
            ),
        )

        assertEquals(
            listOf(
                SubtitleInfo(language = "zh-Hans", ext = "vtt"),
                SubtitleInfo(language = "zh-Hans", ext = "json3"),
                SubtitleInfo(language = "en", ext = "srv3"),
            ),
            subtitles,
        )
    }

    @Test
    fun mapsSafeErrorCategories() {
        assertEquals(AnalysisErrorCategory.Network, YtdlpBridge.mapErrorCategory("network"))
        assertEquals(AnalysisErrorCategory.Unsupported, YtdlpBridge.mapErrorCategory("unsupported"))
        assertEquals(AnalysisErrorCategory.Permission, YtdlpBridge.mapErrorCategory("permission"))
        assertEquals(AnalysisErrorCategory.Parser, YtdlpBridge.mapErrorCategory("parser"))
        assertEquals(AnalysisErrorCategory.Unknown, YtdlpBridge.mapErrorCategory("anything_else"))
    }

    @Test
    fun failureParsingRedactsSensitiveUrlCookiesAndHeaders() {
        val result = YtdlpBridge.parseAnalysisJson(
            """
            {
              "ok": false,
              "errorCategory": "permission",
              "errorMessage": "failed url=https://example.com/watch?v=abc&token=secret --cookies C:/Users/me/cookies.txt Authorization: Bearer abc Cookie: SID=secret"
            }
            """.trimIndent(),
        )

        val error = result.exceptionOrNull() as YtdlpAnalysisException
        assertEquals(AnalysisErrorCategory.Permission, error.category)
        assertFalse(error.safeMessage.contains("token=secret"))
        assertFalse(error.safeMessage.contains("cookies.txt"))
        assertFalse(error.safeMessage.contains("Bearer abc"))
        assertFalse(error.safeMessage.contains("SID=secret"))
        assertTrue(error.safeMessage.contains("[已隐藏]"))
    }

    @Test
    fun analyzeRejectsBlockedAdultDomainBeforeStartingPython() {
        val bridge = YtdlpBridge(
            pythonProvider = {
                throw AssertionError("Python provider must not be called for policy-rejected URLs")
            },
        )

        val result = bridge.analyze("https://video.pornhub.com/watch?v=private&token=secret")

        val error = result.exceptionOrNull() as YtdlpAnalysisException
        assertEquals(AnalysisErrorCategory.Unsupported, error.category)
        assertTrue(error.safeMessage.contains("Google Play"))
        assertFalse(error.safeMessage.contains("pornhub", ignoreCase = true))
        assertFalse(error.safeMessage.contains("token=secret", ignoreCase = true))
    }

    @Test
    fun analyzeWrapsRuntimeFailuresAsSafeFailures() {
        val bridge = YtdlpBridge(
            pythonProvider = {
                throw RuntimeException("missing module --cookies C:/Users/me/cookies.txt Authorization: Bearer secret")
            },
        )

        val result = bridge.analyze("https://example.com/watch?v=public")

        val error = result.exceptionOrNull() as YtdlpAnalysisException
        assertEquals(AnalysisErrorCategory.Parser, error.category)
        assertFalse(error.safeMessage.contains("cookies.txt"))
        assertFalse(error.safeMessage.contains("Bearer secret"))
        assertTrue(error.safeMessage.contains("[已隐藏]"))
    }
}
