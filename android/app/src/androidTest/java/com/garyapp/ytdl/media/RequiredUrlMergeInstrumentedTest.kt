package com.garyapp.ytdl.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.garyapp.ytdl.core.ytdlp.DownloadFormatRole
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequiredUrlMergeInstrumentedTest {
    @Test
    fun analyzesDownloadsSplitStreamsAndMergesRequiredUrlIntoMp4() {
        assertEquals("T8D must run on API37.", 37, Build.VERSION.SDK_INT)
        startPythonIfNeeded()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = File(context.cacheDir, "required-url-merge-t8d").apply {
            deleteRecursively()
            mkdirs()
        }
        val downloadDir = File(workspace, "downloads").apply { mkdirs() }
        val outputRoot = File(workspace, "outputs").apply { mkdirs() }
        val outputFile = File(outputRoot, "tkxzMEfp49Q-merged.mp4")

        val bridge = YtdlpBridge()
        val analysisResult = bridge.analyze(RequiredUrl)
        assertTrue(analysisResult.exceptionOrNull()?.message.orEmpty(), analysisResult.isSuccess)
        val analysis = analysisResult.getOrThrow()
        val videoFormat = chooseRequiredVideoFormat(analysis.formats)
        val audioFormat = chooseRequiredAudioFormat(analysis.formats)

        val videoResult = bridge.downloadFormat(
            url = RequiredUrl,
            outputDirectory = downloadDir,
            formatId = videoFormat.id,
            role = DownloadFormatRole.Video,
        )
        assertTrue(videoResult.exceptionOrNull()?.message.orEmpty(), videoResult.isSuccess)

        val audioResult = bridge.downloadFormat(
            url = RequiredUrl,
            outputDirectory = downloadDir,
            formatId = audioFormat.id,
            role = DownloadFormatRole.Audio,
        )
        assertTrue(audioResult.exceptionOrNull()?.message.orEmpty(), audioResult.isSuccess)

        val videoDownload = videoResult.getOrThrow()
        val audioDownload = audioResult.getOrThrow()
        val videoFile = File(videoDownload.outputPath)
        val audioFile = File(audioDownload.outputPath)
        assertTrue("Video output missing: ${videoFile.absolutePath}", videoFile.isFile)
        assertTrue("Audio output missing: ${audioFile.absolutePath}", audioFile.isFile)
        assertTrue("Video output is empty.", videoDownload.bytesWritten > 0L)
        assertTrue("Audio output is empty.", audioDownload.bytesWritten > 0L)
        assertEquals(videoFormat.id, videoDownload.formatId)
        assertEquals(audioFormat.id, audioDownload.formatId)
        assertEquals(DownloadFormatRole.Video, videoDownload.role)
        assertEquals(DownloadFormatRole.Audio, audioDownload.role)

        val mergeResult = NativeMuxerMediaProcessor(outputRoot).mergeVideoAndAudio(
            MediaMergeRequest(
                videoInput = videoFile,
                audioInput = audioFile,
                outputFile = outputFile,
                outputContainer = MediaOutputContainer.Mp4,
                expectedVideoFormatId = videoFormat.id,
                expectedAudioFormatId = audioFormat.id,
            ),
        )
        assertTrue(mergeResult.exceptionOrNull()?.message.orEmpty(), mergeResult.isSuccess)

        val merged = mergeResult.getOrThrow()
        val trackCounts = countTracks(merged.outputFile)
        val evidence = "YTDL_REQUIRED_MERGE_SMOKE " +
            "sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL} " +
            "title=${analysis.title.toEvidenceValue()} " +
            "videoFormat=${videoDownload.formatId} videoCodec=${videoFormat.videoCodec.orEmpty()} " +
            "audioFormat=${audioDownload.formatId} audioCodec=${audioFormat.audioCodec.orEmpty()} " +
            "videoBytes=${videoDownload.bytesWritten} audioBytes=${audioDownload.bytesWritten} " +
            "videoPath=${videoFile.absolutePath} audioPath=${audioFile.absolutePath} " +
            "output=${merged.outputFile.absolutePath} mergedBytes=${merged.bytesWritten} " +
            "videoTracks=${trackCounts.video} audioTracks=${trackCounts.audio}"
        println(evidence)
        Log.i("RequiredUrlMergeT8D", evidence)

        assertTrue("Merged output missing: ${merged.outputFile.absolutePath}", merged.outputFile.isFile)
        assertTrue("Merged output is empty.", merged.bytesWritten > 0L)
        assertEquals(1, trackCounts.video)
        assertEquals(1, trackCounts.audio)
    }

    private fun startPythonIfNeeded() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(ApplicationProvider.getApplicationContext()))
        }
    }

    private fun chooseRequiredVideoFormat(formats: List<VideoFormat>): VideoFormat {
        return formats
            .filter { it.id.isNotBlank() && it.hasVideo && !it.hasAudio }
            .sortedWith(
                compareBy<VideoFormat>(
                    { if (it.ext.equals("mp4", ignoreCase = true) && it.videoCodec.hasH264Codec()) 0 else 1 },
                    { if (it.ext.equals("mp4", ignoreCase = true)) 0 else 1 },
                    { it.filesizeBytes ?: Long.MAX_VALUE },
                    { it.height ?: Int.MAX_VALUE },
                    { it.id },
                ),
            )
            .firstOrNull()
            ?: error("No video-only format found in analysis result.")
    }

    private fun chooseRequiredAudioFormat(formats: List<VideoFormat>): VideoFormat {
        return formats
            .filter { it.id.isNotBlank() && !it.hasVideo && it.hasAudio }
            .sortedWith(
                compareBy<VideoFormat>(
                    { if (it.ext.equals("m4a", ignoreCase = true) || it.audioCodec.hasAacCodec()) 0 else 1 },
                    { it.filesizeBytes ?: Long.MAX_VALUE },
                    { it.id },
                ),
            )
            .firstOrNull()
            ?: error("No audio-only format found in analysis result.")
    }

    private fun countTracks(file: File): TrackCounts {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var videoTracks = 0
            var audioTracks = 0
            for (trackIndex in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(trackIndex).getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    mime.startsWith("video/") -> videoTracks += 1
                    mime.startsWith("audio/") -> audioTracks += 1
                }
            }
            return TrackCounts(video = videoTracks, audio = audioTracks)
        } finally {
            extractor.release()
        }
    }

    private fun String?.hasH264Codec(): Boolean {
        val normalized = orEmpty().lowercase()
        return normalized.startsWith("avc1") || normalized.startsWith("h264")
    }

    private fun String?.hasAacCodec(): Boolean {
        val normalized = orEmpty().lowercase()
        return normalized.startsWith("mp4a") || normalized.startsWith("aac")
    }

    private fun String.toEvidenceValue(): String {
        return '"' + replace(Regex("""\s+"""), " ")
            .replace('"', '\'')
            .take(120) + '"'
    }

    private data class TrackCounts(
        val video: Int,
        val audio: Int,
    )

    private companion object {
        private const val RequiredUrl = "https://www.youtube.com/watch?v=tkxzMEfp49Q"
    }
}
