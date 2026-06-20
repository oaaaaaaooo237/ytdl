package com.garyapp.ytdl.core.ytdlp

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubtitleDownloadInstrumentedTest {
    @Test
    fun analyzesAndDownloadsOneAvailableSubtitleOrReportsNone() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(ApplicationProvider.getApplicationContext()))
        }

        val bridge = YtdlpBridge()
        val candidates = listOf(
            "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            "https://www.youtube.com/shorts/oXFad1nt6v0",
        )

        val analyzed = candidates.map { url ->
            val result = bridge.analyze(url)
            assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
            url to result.getOrThrow()
        }
        val subtitleAttempts = analyzed.flatMap { (url, analysis) ->
            chooseSubtitleCandidates(analysis.subtitles).map { subtitle -> url to subtitle }
        }

        if (subtitleAttempts.isEmpty()) {
            val evidence = analyzed.joinToString(separator = " | ") { (url, analysis) ->
                "url=${url.substringAfterLast('/')} subtitles=${analysis.subtitles.size}"
            }
            val message = "YTDL_SUBTITLE_SMOKE_NO_SUBTITLES sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL} $evidence"
            println(message)
            Log.i("YtdlpSubtitleSmoke", message)
            assertTrue("fixture URLs returned no subtitle candidates: $evidence", analyzed.all { it.second.subtitles.isEmpty() })
            return
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val skipped = mutableListOf<String>()
        subtitleAttempts.forEachIndexed { index, (url, subtitle) ->
            val outputDir = File(context.cacheDir, "subtitle-download-smoke-$index").apply {
                deleteRecursively()
                mkdirs()
            }
            val result = bridge.downloadSubtitle(
                url = url,
                outputDirectory = outputDir,
                language = subtitle.language,
                ext = subtitle.ext,
                source = subtitle.source,
            )
            if (result.isFailure) {
                val message = "url=${url.substringAfterLast('/')} language=${subtitle.language} ext=${subtitle.ext} source=${subtitle.source.pythonValue} error=${result.exceptionOrNull()?.message.orEmpty()}"
                skipped += message
                println("YTDL_SUBTITLE_CANDIDATE_SKIPPED $message")
                Log.i("YtdlpSubtitleSmoke", "YTDL_SUBTITLE_CANDIDATE_SKIPPED $message")
                return@forEachIndexed
            }

            val download = result.getOrThrow()
            val outputFile = File(download.outputPath)
            val evidence = "YTDL_SUBTITLE_DOWNLOAD_SMOKE sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL} language=${download.language} ext=${download.ext} source=${download.source.pythonValue} bytes=${download.bytesWritten} path=${download.outputPath}"
            println(evidence)
            Log.i("YtdlpSubtitleSmoke", evidence)

            assertTrue("subtitle output should exist: ${download.outputPath}", outputFile.isFile)
            assertTrue("subtitle output should not be empty", download.bytesWritten > 0L)
            assertEquals(subtitle.language, download.language)
            assertEquals(subtitle.ext, download.ext)
            assertEquals(subtitle.source, download.source)
            assertTrue("subtitle output should stay in app cache", outputFile.absolutePath.startsWith(outputDir.absolutePath))
            return
        }

        val attempted = subtitleAttempts.joinToString { (_, subtitle) ->
            "${subtitle.language}/${subtitle.ext}/${subtitle.source.pythonValue}"
        }
        throw AssertionError("subtitle candidates found but none downloaded. attempted=$attempted skipped=$skipped")
    }

    private fun chooseSubtitleCandidates(subtitles: List<SubtitleInfo>): List<SubtitleInfo> {
        return subtitles
            .filter { it.language.isNotBlank() && it.ext.isNotBlank() }
            .sortedWith(
                compareBy<SubtitleInfo>(
                    { if (it.source == SubtitleSource.Manual) 0 else 1 },
                    { preferredLanguageRank(it.language) },
                    { preferredExtRank(it.ext) },
                    { it.language },
                ),
            )
            .take(8)
    }

    private fun preferredLanguageRank(language: String): Int {
        val preferred = listOf("en", "en-US", "en-orig", "zh-Hans", "zh-Hant", "zh-CN", "zh", "ja")
        val index = preferred.indexOf(language)
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun preferredExtRank(ext: String): Int {
        val preferred = listOf("vtt", "srt", "ttml", "srv3", "json3")
        val index = preferred.indexOf(ext)
        return if (index >= 0) index else Int.MAX_VALUE
    }
}
