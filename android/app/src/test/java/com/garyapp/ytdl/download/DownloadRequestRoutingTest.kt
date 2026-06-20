package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.AnalysisErrorCategory
import com.garyapp.ytdl.core.ytdlp.DownloadFormatRole
import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import com.garyapp.ytdl.core.ytdlp.DownloadProgressListener
import com.garyapp.ytdl.core.ytdlp.DownloadResult
import com.garyapp.ytdl.core.ytdlp.SubtitleDownloadResult
import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.SubtitleSource
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.core.ytdlp.YtdlpDownloadException
import com.garyapp.ytdl.media.MediaMergeRequest
import com.garyapp.ytdl.media.MediaOutputContainer
import com.garyapp.ytdl.media.MediaProcessingResult
import com.garyapp.ytdl.media.MediaProcessor
import com.garyapp.ytdl.ui.FormatMode
import com.garyapp.ytdl.ui.FormatSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadRequestRoutingTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun directSingleFileRouteDownloadsChosenProgressiveFormatWithoutSingleFileFallback() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedHeight = 360,
                selectedVideoFormatId = "18",
                mergeRequired = false,
            ),
        ).getOrThrow()
        assertEquals(DownloadRoute.DirectSingleFile(formatId = "18"), request.route)

        val engine = RecordingDownloadEngine(temp.root)
        val mediaProcessor = RecordingMediaProcessor()
        val stages = mutableListOf<DownloadStage>()
        val result = DownloadPipeline(engine, mediaProcessor).run(request, temp.root) { stages += it.stage }

        assertEquals(DownloadStage.Completed, result.state.stage)
        assertEquals(listOf("format:media:18"), engine.calls)
        assertTrue(result.outputs.single { it.kind == DownloadOutputKind.Media }.path.endsWith("18-media.mp4"))
        assertTrue(mediaProcessor.mergeRequests.isEmpty())
        assertFalse(stages.contains(DownloadStage.Exporting))
    }

    @Test
    fun videoAndAudioSelectionRejectsVideoOnlyFormatWithoutExplicitAudio() {
        val result = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(videoOnlyFormat(id = "137", height = 1080)),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedHeight = 1080,
                selectedVideoFormatId = "137",
                mergeRequired = false,
            ),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("音频"))
    }

    @Test
    fun videoOnlyRouteRejectsProgressiveFormatWithAudio() {
        val result = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
            selection = FormatSelection(
                mode = FormatMode.VideoOnly,
                selectedHeight = 360,
                selectedVideoFormatId = "18",
            ),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("独立视频流"))
    }

    @Test
    fun audioOnlyRouteRejectsProgressiveFormatWithVideo() {
        val result = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
            selection = FormatSelection(
                mode = FormatMode.AudioOnly,
                selectedAudioFormatId = "18",
            ),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("独立音频流"))
    }

    @Test
    fun mergeRequiredRouteDownloadsExplicitVideoAndAudioBeforeNativeMerge() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(
                videoOnlyFormat(id = "137", height = 1080),
                audioOnlyFormat(id = "140"),
            ),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedHeight = 1080,
                selectedVideoFormatId = "137",
                selectedAudioFormatId = "140",
                mergeRequired = true,
            ),
        ).getOrThrow()
        assertEquals(DownloadRoute.MergeRequired(videoFormatId = "137", audioFormatId = "140"), request.route)

        val engine = RecordingDownloadEngine(temp.root)
        val mediaProcessor = RecordingMediaProcessor()
        val stages = mutableListOf<DownloadStage>()
        val result = DownloadPipeline(engine, mediaProcessor).run(request, temp.root) { stages += it.stage }

        assertEquals(DownloadStage.Completed, result.state.stage)
        assertEquals(listOf("format:video:137", "format:audio:140"), engine.calls)
        assertEquals(1, mediaProcessor.mergeRequests.size)
        assertEquals("137", mediaProcessor.mergeRequests.single().expectedVideoFormatId)
        assertEquals("140", mediaProcessor.mergeRequests.single().expectedAudioFormatId)
        assertFalse(engine.calls.contains("single"))
        assertTrue(stages.indexOf(DownloadStage.Merging) < stages.indexOf(DownloadStage.Completed))
        assertFalse(stages.contains(DownloadStage.Exporting))
    }

    @Test
    fun videoOnlyRouteUsesExplicitVideoFormatDownload() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(videoOnlyFormat(id = "137", height = 1080)),
            selection = FormatSelection(
                mode = FormatMode.VideoOnly,
                selectedHeight = 1080,
                selectedVideoFormatId = "137",
            ),
        ).getOrThrow()

        val engine = RecordingDownloadEngine(temp.root)
        val result = DownloadPipeline(engine, RecordingMediaProcessor()).run(request, temp.root)

        assertEquals(DownloadRoute.VideoOnly(videoFormatId = "137"), request.route)
        assertEquals(DownloadStage.Completed, result.state.stage)
        assertEquals(listOf("format:video:137"), engine.calls)
    }

    @Test
    fun audioOnlyRouteUsesExplicitAudioFormatDownload() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(audioOnlyFormat(id = "140")),
            selection = FormatSelection(
                mode = FormatMode.AudioOnly,
                selectedAudioFormatId = "140",
            ),
        ).getOrThrow()

        val engine = RecordingDownloadEngine(temp.root)
        val result = DownloadPipeline(engine, RecordingMediaProcessor()).run(request, temp.root)

        assertEquals(DownloadRoute.AudioOnly(audioFormatId = "140"), request.route)
        assertEquals(DownloadStage.Completed, result.state.stage)
        assertEquals(listOf("format:audio:140"), engine.calls)
    }

    @Test
    fun subtitleChoiceDownloadsSeparateSubtitleFileBeforeCompletion() {
        val subtitle = SubtitleInfo(language = "en", ext = "vtt", source = SubtitleSource.Automatic)
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(
                progressiveFormat(id = "18", height = 360),
                subtitles = listOf(subtitle),
            ),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedHeight = 360,
                selectedVideoFormatId = "18",
            ),
            selectedSubtitles = listOf(subtitle),
        ).getOrThrow()

        val engine = RecordingDownloadEngine(temp.root)
        val stages = mutableListOf<DownloadStage>()
        val result = DownloadPipeline(engine, RecordingMediaProcessor()).run(request, temp.root) { stages += it.stage }

        assertEquals(DownloadStage.Completed, result.state.stage)
        assertEquals(listOf("format:media:18", "subtitle:automatic:en:vtt"), engine.calls)
        assertEquals(1, result.outputs.count { it.kind == DownloadOutputKind.Subtitle })
        assertTrue(stages.indexOf(DownloadStage.DownloadingSubtitles) < stages.indexOf(DownloadStage.Completed))
    }

    @Test
    fun emptySubtitleSelectionCompletesWithOnlyMediaOutputAndNoSubtitleStage() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedHeight = 360,
                selectedVideoFormatId = "18",
            ),
            selectedSubtitles = emptyList(),
        ).getOrThrow()

        val engine = RecordingDownloadEngine(temp.root)
        val stages = mutableListOf<DownloadStage>()
        val result = DownloadPipeline(engine, RecordingMediaProcessor()).run(request, temp.root) { stages += it.stage }

        assertEquals(DownloadStage.Completed, result.state.stage)
        assertEquals(listOf("format:media:18"), engine.calls)
        assertEquals(1, result.outputs.size)
        assertEquals(DownloadOutputKind.Media, result.outputs.single().kind)
        assertFalse(stages.contains(DownloadStage.DownloadingSubtitles))
    }

    @Test
    fun cancellationStopsBeforeNextRouteAndDoesNotComplete() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(
                videoOnlyFormat(id = "137", height = 1080),
                audioOnlyFormat(id = "140"),
            ),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedVideoFormatId = "137",
                selectedAudioFormatId = "140",
                mergeRequired = true,
            ),
        ).getOrThrow()
        val cancellation = MutableDownloadCancellation()
        val engine = RecordingDownloadEngine(temp.root).apply {
            onVideoDownload = { cancellation.cancel() }
        }
        val stages = mutableListOf<DownloadStage>()

        val result = DownloadPipeline(engine, RecordingMediaProcessor()).run(
            request = request,
            outputDirectory = temp.root,
            cancellation = cancellation,
        ) { stages += it.stage }

        assertEquals(DownloadStage.Canceled, result.state.stage)
        assertEquals(listOf("format:video:137"), engine.calls)
        assertFalse(stages.contains(DownloadStage.Completed))
    }

    @Test
    fun cancellationDuringProgressEventStopsBeforeDownloadContinuesAndDoesNotComplete() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedVideoFormatId = "18",
            ),
        ).getOrThrow()
        val cancellation = MutableDownloadCancellation()
        val engine = RecordingDownloadEngine(temp.root).apply {
            beforeProgress = { cancellation.cancel() }
        }
        val stages = mutableListOf<DownloadStage>()

        val result = DownloadPipeline(engine, RecordingMediaProcessor()).run(
            request = request,
            outputDirectory = temp.root,
            cancellation = cancellation,
        ) { stages += it.stage }

        assertEquals(DownloadStage.Canceled, result.state.stage)
        assertFalse(engine.continuedAfterProgress)
        assertFalse(stages.contains(DownloadStage.Completed))
    }

    @Test
    fun canceledYtdlpDownloadFailureMapsToCanceledState() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedVideoFormatId = "18",
            ),
        ).getOrThrow()
        val engine = RecordingDownloadEngine(temp.root).apply {
            downloadFailure = YtdlpDownloadException(
                category = AnalysisErrorCategory.Canceled,
                safeMessage = "下载已取消。",
            )
        }
        val stages = mutableListOf<DownloadStage>()

        val result = DownloadPipeline(engine, RecordingMediaProcessor()).run(request, temp.root) { stages += it.stage }

        assertEquals(DownloadStage.Canceled, result.state.stage)
        assertFalse(stages.contains(DownloadStage.Completed))
    }

    @Test
    fun fatalErrorsFromEngineAreNotConvertedToFailedState() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedVideoFormatId = "18",
            ),
        ).getOrThrow()
        val engine = RecordingDownloadEngine(temp.root).apply {
            throwableToThrow = AssertionError("fatal invariant")
        }

        assertThrows(AssertionError::class.java) {
            DownloadPipeline(engine, RecordingMediaProcessor()).run(request, temp.root)
        }
    }

    @Test
    fun failureRoutingDoesNotMergeOrComplete() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(
                videoOnlyFormat(id = "137", height = 1080),
                audioOnlyFormat(id = "140"),
            ),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedVideoFormatId = "137",
                selectedAudioFormatId = "140",
                mergeRequired = true,
            ),
        ).getOrThrow()
        val engine = RecordingDownloadEngine(temp.root).apply {
            failingRole = DownloadFormatRole.Audio
        }
        val mediaProcessor = RecordingMediaProcessor()
        val stages = mutableListOf<DownloadStage>()

        val result = DownloadPipeline(engine, mediaProcessor).run(request, temp.root) { stages += it.stage }

        assertEquals(DownloadStage.Failed, result.state.stage)
        assertEquals(listOf("format:video:137", "format:audio:140"), engine.calls)
        assertTrue(mediaProcessor.mergeRequests.isEmpty())
        assertFalse(stages.contains(DownloadStage.Completed))
    }

    @Test
    fun directMediaDownloadFailsWhenReturnedFormatIdDoesNotMatchRequest() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedVideoFormatId = "18",
            ),
        ).getOrThrow()
        val engine = RecordingDownloadEngine(temp.root).apply {
            returnedFormatId = "22"
        }
        val stages = mutableListOf<DownloadStage>()

        val result = DownloadPipeline(engine, RecordingMediaProcessor()).run(request, temp.root) { stages += it.stage }

        assertEquals(DownloadStage.Failed, result.state.stage)
        assertFalse(stages.contains(DownloadStage.Completed))
    }

    @Test
    fun videoOnlyDownloadFailsWhenReturnedRoleDoesNotMatchRequest() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(videoOnlyFormat(id = "137", height = 1080)),
            selection = FormatSelection(
                mode = FormatMode.VideoOnly,
                selectedVideoFormatId = "137",
            ),
        ).getOrThrow()
        val engine = RecordingDownloadEngine(temp.root).apply {
            returnedRole = DownloadFormatRole.Media
        }
        val stages = mutableListOf<DownloadStage>()

        val result = DownloadPipeline(engine, RecordingMediaProcessor()).run(request, temp.root) { stages += it.stage }

        assertEquals(DownloadStage.Failed, result.state.stage)
        assertFalse(stages.contains(DownloadStage.Completed))
    }

    @Test
    fun audioOnlyDownloadFailsWhenReturnedFormatIdDoesNotMatchRequest() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(audioOnlyFormat(id = "140")),
            selection = FormatSelection(
                mode = FormatMode.AudioOnly,
                selectedAudioFormatId = "140",
            ),
        ).getOrThrow()
        val engine = RecordingDownloadEngine(temp.root).apply {
            returnedFormatId = "141"
        }
        val stages = mutableListOf<DownloadStage>()

        val result = DownloadPipeline(engine, RecordingMediaProcessor()).run(request, temp.root) { stages += it.stage }

        assertEquals(DownloadStage.Failed, result.state.stage)
        assertFalse(stages.contains(DownloadStage.Completed))
    }

    @Test
    fun mergeRequiredDoesNotMergeWhenAudioDownloadReturnsWrongRole() {
        val request = DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysisWith(
                videoOnlyFormat(id = "137", height = 1080),
                audioOnlyFormat(id = "140"),
            ),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedVideoFormatId = "137",
                selectedAudioFormatId = "140",
                mergeRequired = true,
            ),
        ).getOrThrow()
        val engine = RecordingDownloadEngine(temp.root).apply {
            returnedRoleByRequestRole[DownloadFormatRole.Audio] = DownloadFormatRole.Video
        }
        val mediaProcessor = RecordingMediaProcessor()
        val stages = mutableListOf<DownloadStage>()

        val result = DownloadPipeline(engine, mediaProcessor).run(request, temp.root) { stages += it.stage }

        assertEquals(DownloadStage.Failed, result.state.stage)
        assertTrue(mediaProcessor.mergeRequests.isEmpty())
        assertFalse(stages.contains(DownloadStage.Completed))
    }

    private class RecordingDownloadEngine(
        private val root: File,
    ) : DownloadEngine {
        val calls = mutableListOf<String>()
        var failingRole: DownloadFormatRole? = null
        var onVideoDownload: () -> Unit = {}
        var beforeProgress: () -> Unit = {}
        var continuedAfterProgress = false
        var throwableToThrow: Throwable? = null
        var returnedFormatId: String? = null
        var returnedRole: DownloadFormatRole? = null
        var downloadFailure: YtdlpDownloadException? = null
        val returnedRoleByRequestRole = mutableMapOf<DownloadFormatRole, DownloadFormatRole>()

        override fun downloadFormat(
            url: String,
            outputDirectory: File,
            formatId: String,
            role: DownloadFormatRole,
            cookiesPath: String?,
            listener: DownloadProgressListener?,
        ): Result<DownloadResult> {
            throwableToThrow?.let { throw it }
            calls += "format:${role.pythonValue}:$formatId"
            downloadFailure?.let { return Result.failure(it) }
            if (failingRole == role) {
                return Result.failure(IllegalStateException("${role.pythonValue} failed"))
            }
            beforeProgress()
            listener?.onProgress(
                DownloadProgress(
                    status = "downloading",
                    percent = 5.0,
                    downloadedBytes = 5L,
                    totalBytes = 100L,
                    speedBytesPerSecond = null,
                    etaSeconds = null,
                    filename = null,
                ),
            )
            continuedAfterProgress = true
            val file = writeFile("$formatId-${role.pythonValue}.${if (role == DownloadFormatRole.Audio) "m4a" else "mp4"}")
            if (role == DownloadFormatRole.Video) {
                onVideoDownload()
            }
            return Result.success(
                DownloadResult(
                    outputPath = file.absolutePath,
                    bytesWritten = file.length(),
                    title = formatId,
                    formatId = returnedFormatId ?: formatId,
                    role = returnedRoleByRequestRole[role] ?: returnedRole ?: role,
                ),
            )
        }

        override fun downloadSubtitle(
            url: String,
            outputDirectory: File,
            language: String,
            ext: String,
            source: SubtitleSource,
            cookiesPath: String?,
            listener: DownloadProgressListener?,
        ): Result<SubtitleDownloadResult> {
            calls += "subtitle:${source.pythonValue}:$language:$ext"
            val file = writeFile("subtitle-${source.pythonValue}.$language.$ext")
            return Result.success(
                SubtitleDownloadResult(
                    outputPath = file.absolutePath,
                    bytesWritten = file.length(),
                    language = language,
                    ext = ext,
                    source = source,
                    title = "subtitle",
                ),
            )
        }

        private fun writeFile(name: String): File {
            return File(root, name).apply {
                parentFile?.mkdirs()
                writeText("download-$name")
            }
        }
    }

    private class RecordingMediaProcessor : MediaProcessor {
        override val processorName: String = "recording"
        val mergeRequests = mutableListOf<MediaMergeRequest>()

        override fun mergeVideoAndAudio(request: MediaMergeRequest): Result<MediaProcessingResult> {
            mergeRequests += request
            request.outputFile.parentFile?.mkdirs()
            request.outputFile.writeText("merged")
            return Result.success(
                MediaProcessingResult(
                    outputFile = request.outputFile,
                    bytesWritten = request.outputFile.length(),
                    videoTrackCount = 1,
                    audioTrackCount = 1,
                    processorName = processorName,
                ),
            )
        }
    }

    private fun analysisWith(
        vararg formats: VideoFormat,
        subtitles: List<SubtitleInfo> = emptyList(),
    ) = VideoAnalysis(
        title = "测试视频",
        durationSeconds = 60,
        thumbnailUrl = null,
        formats = formats.toList(),
        subtitles = subtitles,
    )

    private fun progressiveFormat(id: String, height: Int) = VideoFormat(
        id = id,
        ext = "mp4",
        height = height,
        label = "${height}p",
        hasVideo = true,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = "avc1",
        audioCodec = "mp4a",
    )

    private fun videoOnlyFormat(id: String, height: Int) = VideoFormat(
        id = id,
        ext = "mp4",
        height = height,
        label = "${height}p 需合并音频",
        hasVideo = true,
        hasAudio = false,
        mergeRequired = true,
        isSupported = true,
        videoCodec = "avc1",
        audioCodec = "none",
    )

    private fun audioOnlyFormat(id: String) = VideoFormat(
        id = id,
        ext = "m4a",
        height = null,
        label = "音频",
        hasVideo = false,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = "none",
        audioCodec = "mp4a",
    )

    private companion object {
        const val TestUrl = "https://www.youtube.com/watch?v=tkxzMEfp49Q"
    }
}
