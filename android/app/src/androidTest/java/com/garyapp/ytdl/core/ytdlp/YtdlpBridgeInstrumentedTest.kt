package com.garyapp.ytdl.core.ytdlp

import android.content.Context
import android.os.Build
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YtdlpBridgeInstrumentedTest {
    @Test
    fun analyzesRequiredYoutubeSmokeUrl() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(androidx.test.core.app.ApplicationProvider.getApplicationContext()))
        }

        val result = YtdlpBridge().analyze("https://www.youtube.com/watch?v=tkxzMEfp49Q")

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        val analysis = result.getOrThrow()
        val highestHeight = analysis.formats.mapNotNull { it.height }.maxOrNull()
        val evidence = "YTDL_ANALYSIS_SMOKE sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL} title=${analysis.title.take(80)} formats=${analysis.formats.size} highest=${highestHeight ?: 0} subtitles=${analysis.subtitles.size}"
        println(evidence)
        Log.i("YtdlpBridgeSmoke", evidence)
        assertEquals(37, Build.VERSION.SDK_INT)
        assertTrue(analysis.title.isNotBlank())
        assertTrue(analysis.formats.isNotEmpty())
    }

    @Test
    fun downloadsRequiredYoutubeSmokeUrlWithRealProgress() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(ApplicationProvider.getApplicationContext()))
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val outputDir = File(context.cacheDir, "real-download-smoke").apply {
            deleteRecursively()
            mkdirs()
        }
        val progressValues = mutableListOf<Double>()

        val result = YtdlpBridge().downloadSingleFile(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            outputDirectory = outputDir,
            listener = object : DownloadProgressListener {
                override fun onProgress(progress: DownloadProgress) {
                    progress.percent?.let(progressValues::add)
                    val evidence = "YTDL_DOWNLOAD_PROGRESS status=${progress.status} percent=${progress.percent ?: -1.0} downloaded=${progress.downloadedBytes ?: -1} total=${progress.totalBytes ?: -1}"
                    println(evidence)
                    Log.i("YtdlpBridgeSmoke", evidence)
                }
            },
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        val download = result.getOrThrow()
        val outputFile = File(download.outputPath)
        val evidence = "YTDL_DOWNLOAD_SMOKE sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL} file=${outputFile.name} bytes=${download.bytesWritten} progressEvents=${progressValues.size}"
        println(evidence)
        Log.i("YtdlpBridgeSmoke", evidence)

        assertTrue("download output should exist: ${download.outputPath}", outputFile.isFile)
        assertTrue("download output should not be empty", download.bytesWritten > 0L)
        assertTrue("yt-dlp should emit progress more than once", progressValues.distinct().size >= 2)
        assertTrue("download should report completion", progressValues.any { it >= 100.0 })
    }

    @Test
    fun downloadsRequiredYoutubeSmokeUrlSplitFormats() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(ApplicationProvider.getApplicationContext()))
        }

        val bridge = YtdlpBridge()
        val analysisResult = bridge.analyze("https://www.youtube.com/watch?v=tkxzMEfp49Q")
        assertTrue(analysisResult.exceptionOrNull()?.message.orEmpty(), analysisResult.isSuccess)

        val analysis = analysisResult.getOrThrow()
        val videoFormat = chooseSmallSplitVideoFormat(analysis.formats)
        val audioFormat = chooseSmallSplitAudioFormat(analysis.formats)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val outputDir = File(context.cacheDir, "split-download-smoke").apply {
            deleteRecursively()
            mkdirs()
        }

        val videoResult = bridge.downloadFormat(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            outputDirectory = outputDir,
            formatId = videoFormat.id,
            role = DownloadFormatRole.Video,
        )
        assertTrue(videoResult.exceptionOrNull()?.message.orEmpty(), videoResult.isSuccess)

        val audioResult = bridge.downloadFormat(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            outputDirectory = outputDir,
            formatId = audioFormat.id,
            role = DownloadFormatRole.Audio,
        )
        assertTrue(audioResult.exceptionOrNull()?.message.orEmpty(), audioResult.isSuccess)

        val videoDownload = videoResult.getOrThrow()
        val audioDownload = audioResult.getOrThrow()
        val videoFile = File(videoDownload.outputPath)
        val audioFile = File(audioDownload.outputPath)

        val evidence = "YTDL_SPLIT_DOWNLOAD_SMOKE sdk=${Build.VERSION.SDK_INT} videoFormat=${videoDownload.formatId} audioFormat=${audioDownload.formatId} videoBytes=${videoDownload.bytesWritten} audioBytes=${audioDownload.bytesWritten} videoPath=${videoDownload.outputPath} audioPath=${audioDownload.outputPath}"
        println(evidence)
        Log.i("YtdlpBridgeSmoke", evidence)

        assertTrue("video-only output should exist: ${videoDownload.outputPath}", videoFile.isFile)
        assertTrue("audio-only output should exist: ${audioDownload.outputPath}", audioFile.isFile)
        assertTrue("video-only output should not be empty", videoDownload.bytesWritten > 0L)
        assertTrue("audio-only output should not be empty", audioDownload.bytesWritten > 0L)
        assertEquals(DownloadFormatRole.Video, videoDownload.role)
        assertEquals(DownloadFormatRole.Audio, audioDownload.role)
        assertEquals(videoFormat.id, videoDownload.formatId)
        assertEquals(audioFormat.id, audioDownload.formatId)
    }

    private fun chooseSmallSplitVideoFormat(formats: List<VideoFormat>): VideoFormat {
        return formats
            .filter { it.id.isNotBlank() && it.hasVideo && !it.hasAudio }
            .sortedWith(
                compareBy<VideoFormat>(
                    { if (it.ext == "mp4") 0 else 1 },
                    { it.filesizeBytes ?: Long.MAX_VALUE },
                    { it.height ?: Int.MAX_VALUE },
                ),
            )
            .firstOrNull()
            ?: error("No video-only format found in analysis result: ${formats.map { it.id to it.label }}")
    }

    private fun chooseSmallSplitAudioFormat(formats: List<VideoFormat>): VideoFormat {
        return formats
            .filter { it.id.isNotBlank() && !it.hasVideo && it.hasAudio }
            .sortedWith(
                compareBy<VideoFormat>(
                    { if (it.ext == "m4a") 0 else 1 },
                    { it.filesizeBytes ?: Long.MAX_VALUE },
                ),
            )
            .firstOrNull()
            ?: error("No audio-only format found in analysis result: ${formats.map { it.id to it.label }}")
    }
}
