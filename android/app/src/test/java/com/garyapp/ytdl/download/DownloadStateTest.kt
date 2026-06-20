package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.SubtitleSource
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.ui.FormatMode
import com.garyapp.ytdl.ui.FormatSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadStateTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun stateSeparatesAllQueueStages() {
        assertEquals(
            listOf(
                DownloadStage.Idle,
                DownloadStage.Analyzing,
                DownloadStage.Waiting,
                DownloadStage.DownloadingVideo,
                DownloadStage.DownloadingAudio,
                DownloadStage.DownloadingSubtitles,
                DownloadStage.Merging,
                DownloadStage.Exporting,
                DownloadStage.Completed,
                DownloadStage.Failed,
                DownloadStage.Canceled,
            ),
            DownloadStage.entries,
        )
    }

    @Test
    fun completedStateRequiresEverySelectedOutputToExist() {
        val subtitle = SubtitleInfo(language = "en", ext = "vtt", source = SubtitleSource.Automatic)
        val request = DownloadRequest.fromAnalysis(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            analysis = analysisWith(subtitles = listOf(subtitle)),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedHeight = 360,
                selectedVideoFormatId = "18",
            ),
            selectedSubtitles = listOf(subtitle),
        ).getOrThrow()

        val media = writeOutput("media.mp4")
        val mediaOnly = DownloadTaskState.waiting(request).completeWith(
            listOf(DownloadOutputFile(DownloadOutputKind.Media, media.absolutePath, media.length())),
        )

        assertTrue(mediaOnly.isFailure)
        assertTrue(mediaOnly.exceptionOrNull()?.message.orEmpty().contains("字幕"))

        val subtitleFile = writeOutput("subtitle.en.vtt")
        val complete = DownloadTaskState.waiting(request).completeWith(
            listOf(
                DownloadOutputFile(DownloadOutputKind.Media, media.absolutePath, media.length()),
                DownloadOutputFile(DownloadOutputKind.Subtitle, subtitleFile.absolutePath, subtitleFile.length()),
            ),
        ).getOrThrow()

        assertEquals(DownloadStage.Completed, complete.stage)
        assertEquals(2, complete.outputs.size)
    }

    private fun analysisWith(
        subtitles: List<SubtitleInfo>,
    ) = VideoAnalysis(
        title = "测试视频",
        durationSeconds = 60,
        thumbnailUrl = null,
        formats = listOf(
            VideoFormat(
                id = "18",
                ext = "mp4",
                height = 360,
                label = "360p",
                hasVideo = true,
                hasAudio = true,
                mergeRequired = false,
                isSupported = true,
                videoCodec = "avc1",
                audioCodec = "mp4a",
            ),
        ),
        subtitles = subtitles,
    )

    private fun writeOutput(name: String): File {
        return File(temp.root, name).apply {
            writeText("output-$name")
        }
    }
}
