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
    fun missingCodecsRemainUnknownAndDoNotMeanStreamsAreAbsent() {
        val format = YtdlpBridge.mapFormat(
            mapOf(
                "format_id" to "single-file",
                "ext" to "mp4",
                "height" to 1080,
                "width" to 1920,
            ),
        )

        assertEquals(null, format.videoCodec)
        assertEquals(null, format.audioCodec)
        assertTrue(format.hasVideo)
        assertTrue(format.hasAudio)
        assertTrue(format.isSupported)
        assertEquals(1080, format.height)
        assertEquals("1080p", format.label)
    }

    @Test
    fun blankCodecsRemainUnknownWhileExplicitNoneMeansAbsent() {
        val unknown = YtdlpBridge.mapFormat(
            mapOf(
                "format_id" to "blank-codecs",
                "ext" to "mp4",
                "height" to 720,
                "vcodec" to "",
                "acodec" to "   ",
            ),
        )
        val videoOnly = YtdlpBridge.mapFormat(
            mapOf(
                "format_id" to "video-only",
                "ext" to "mp4",
                "height" to 1080,
                "vcodec" to "avc1.640028",
                "acodec" to "none",
            ),
        )

        assertEquals(null, unknown.videoCodec)
        assertEquals(null, unknown.audioCodec)
        assertTrue(unknown.hasVideo)
        assertTrue(unknown.hasAudio)
        assertTrue(videoOnly.hasVideo)
        assertFalse(videoOnly.hasAudio)
    }

    @Test
    fun jsonNullCodecsRemainUnknownAfterJsonObjectMapping() {
        val analysis = YtdlpBridge.parseAnalysisJson(
            """
            {
              "ok": true,
              "title": "单文件媒体",
              "duration": 60,
              "thumbnail": "",
              "formats": [
                {
                  "format_id": "single-file",
                  "ext": "mp4",
                  "height": 1080,
                  "width": 1920,
                  "vcodec": null,
                  "acodec": null
                }
              ],
              "subtitles": {},
              "automatic_captions": {}
            }
            """.trimIndent(),
        ).getOrThrow()

        val format = analysis.formats.single()
        assertEquals(null, format.videoCodec)
        assertEquals(null, format.audioCodec)
        assertTrue(format.hasVideo)
        assertTrue(format.hasAudio)
        assertEquals(1080, format.height)
        assertEquals("1080p", format.label)
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
    fun failureParsingUsesSharedSensitiveRedactorForCookieArgumentsAndQuerySecrets() {
        val result = YtdlpBridge.parseAnalysisJson(
            """
            {
              "ok": false,
              "errorCategory": "parser",
              "errorMessage": "failed https://example.com/watch?v=abc&cookies=SID-secret&jwt=raw-jwt --cookies=C:/Users/me/private/cookies.txt --cookies-from-browser chrome session=browser-session"
            }
            """.trimIndent(),
        )

        val error = result.exceptionOrNull() as YtdlpAnalysisException
        assertEquals(AnalysisErrorCategory.Parser, error.category)
        listOf(
            "SID-secret",
            "raw-jwt",
            "cookies.txt",
            "private",
            "--cookies-from-browser chrome",
            "browser-session",
            "session=browser-session",
            "watch?v=abc",
        ).forEach {
            assertFalse("safeMessage leaked $it", error.safeMessage.contains(it, ignoreCase = true))
        }
        assertTrue(error.safeMessage.contains("[已隐藏]"))
    }

    @Test
    fun analyzeDoesNotPolicyBlockHttpDomainBeforeStartingPython() {
        var pythonProviderCalled = false
        val bridge = YtdlpBridge(
            pythonProvider = {
                pythonProviderCalled = true
                throw RuntimeException("python reached")
            },
        )

        val result = bridge.analyze("https://video.example.com/watch?v=private&token=secret")

        val error = result.exceptionOrNull() as YtdlpAnalysisException
        assertTrue(pythonProviderCalled)
        assertEquals(AnalysisErrorCategory.Parser, error.category)
        assertTrue(error.safeMessage.contains("python reached"))
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
