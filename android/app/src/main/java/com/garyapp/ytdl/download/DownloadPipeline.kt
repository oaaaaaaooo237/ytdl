package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.DownloadFormatRole
import com.garyapp.ytdl.core.ytdlp.DownloadProgressListener
import com.garyapp.ytdl.core.ytdlp.DownloadResult
import com.garyapp.ytdl.core.ytdlp.SubtitleDownloadResult
import com.garyapp.ytdl.core.ytdlp.SubtitleSource
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.core.ytdlp.AnalysisErrorCategory
import com.garyapp.ytdl.core.ytdlp.YtdlpDownloadException
import com.garyapp.ytdl.media.MediaMergeRequest
import com.garyapp.ytdl.media.MediaOutputContainer
import com.garyapp.ytdl.media.MediaProcessor
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

interface DownloadEngine {
    fun downloadFormat(
        url: String,
        outputDirectory: File,
        formatId: String,
        role: DownloadFormatRole,
        cookiesPath: String? = null,
        listener: DownloadProgressListener? = null,
    ): Result<DownloadResult>

    fun downloadSubtitle(
        url: String,
        outputDirectory: File,
        language: String,
        ext: String,
        source: SubtitleSource,
        cookiesPath: String? = null,
        listener: DownloadProgressListener? = null,
    ): Result<SubtitleDownloadResult>
}

class YtdlpDownloadEngine(
    private val bridge: YtdlpBridge = YtdlpBridge(),
) : DownloadEngine {
    override fun downloadFormat(
        url: String,
        outputDirectory: File,
        formatId: String,
        role: DownloadFormatRole,
        cookiesPath: String?,
        listener: DownloadProgressListener?,
    ): Result<DownloadResult> {
        return bridge.downloadFormat(url, outputDirectory, formatId, role, cookiesPath, listener)
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
        return bridge.downloadSubtitle(url, outputDirectory, language, ext, source, cookiesPath, listener)
    }
}

interface DownloadCancellation {
    val isCancellationRequested: Boolean
}

object NeverDownloadCancellation : DownloadCancellation {
    override val isCancellationRequested: Boolean = false
}

class MutableDownloadCancellation : DownloadCancellation {
    private val canceled = AtomicBoolean(false)

    override val isCancellationRequested: Boolean
        get() = canceled.get()

    fun cancel() {
        canceled.set(true)
    }
}

data class DownloadPipelineResult(
    val state: DownloadTaskState,
    val outputs: List<DownloadOutputFile>,
)

class DownloadPipeline(
    private val engine: DownloadEngine,
    private val mediaProcessor: MediaProcessor,
) {
    fun run(
        request: DownloadRequest,
        outputDirectory: File,
        cancellation: DownloadCancellation = NeverDownloadCancellation,
        onStateChanged: (DownloadTaskState) -> Unit = {},
    ): DownloadPipelineResult {
        outputDirectory.mkdirs()
        var state = DownloadTaskState.waiting(request)
        var currentStage = state.stage
        val finalOutputs = mutableListOf<DownloadOutputFile>()

        fun emit(nextState: DownloadTaskState) {
            state = nextState
            currentStage = nextState.stage
            onStateChanged(nextState)
        }

        fun transition(stage: DownloadStage) {
            emit(state.atStage(stage))
        }

        fun ensureActive() {
            if (cancellation.isCancellationRequested) {
                throw DownloadPipelineCanceledException()
            }
        }

        fun progressListener(): DownloadProgressListener {
            return DownloadProgressListener { progress ->
                ensureActive()
                emit(state.copy(stage = currentStage).withProgress(progress))
                ensureActive()
            }
        }

        emit(state)

        return try {
            ensureActive()
            when (val route = request.route) {
                is DownloadRoute.DirectSingleFile -> {
                    transition(DownloadStage.DownloadingVideo)
                    val download = downloadFormatChecked(
                        request = request,
                        outputDirectory = outputDirectory,
                        formatId = route.formatId,
                        role = DownloadFormatRole.Media,
                        listener = progressListener(),
                    )
                    finalOutputs += download.toOutputFile(DownloadOutputKind.Media, "媒体")
                    ensureActive()
                }
                is DownloadRoute.VideoOnly -> {
                    transition(DownloadStage.DownloadingVideo)
                    val download = downloadFormatChecked(
                        request = request,
                        outputDirectory = outputDirectory,
                        formatId = route.videoFormatId,
                        role = DownloadFormatRole.Video,
                        listener = progressListener(),
                    )
                    finalOutputs += download.toOutputFile(DownloadOutputKind.Media, "视频")
                    ensureActive()
                }
                is DownloadRoute.AudioOnly -> {
                    transition(DownloadStage.DownloadingAudio)
                    val download = downloadFormatChecked(
                        request = request,
                        outputDirectory = outputDirectory,
                        formatId = route.audioFormatId,
                        role = DownloadFormatRole.Audio,
                        listener = progressListener(),
                    )
                    finalOutputs += download.toOutputFile(DownloadOutputKind.Media, "音频")
                    ensureActive()
                }
                is DownloadRoute.MergeRequired -> {
                    val video = downloadFormatPart(request, outputDirectory, route.videoFormatId, DownloadFormatRole.Video, ::transition, ::progressListener)
                    ensureActive()
                    val audio = downloadFormatPart(request, outputDirectory, route.audioFormatId, DownloadFormatRole.Audio, ::transition, ::progressListener)
                    ensureActive()
                    transition(DownloadStage.Merging)
                    val mergedOutput = File(
                        outputDirectory,
                        "merged-${route.videoFormatId.safeFileToken()}-${route.audioFormatId.safeFileToken()}.${MediaOutputContainer.Mp4.extension}",
                    )
                    val merged = mediaProcessor.mergeVideoAndAudio(
                        MediaMergeRequest(
                            videoInput = video,
                            audioInput = audio,
                            outputFile = mergedOutput,
                            outputContainer = MediaOutputContainer.Mp4,
                            expectedVideoFormatId = route.videoFormatId,
                            expectedAudioFormatId = route.audioFormatId,
                        ),
                    ).getOrThrow()
                    finalOutputs += DownloadOutputFile(
                        kind = DownloadOutputKind.Media,
                        path = merged.outputFile.absolutePath,
                        bytesWritten = merged.bytesWritten,
                    )
                    ensureActive()
                }
            }

            request.selectedSubtitles.forEach { subtitle ->
                ensureActive()
                transition(DownloadStage.DownloadingSubtitles)
                val download = engine.downloadSubtitle(
                    url = request.url,
                    outputDirectory = outputDirectory,
                    language = subtitle.language,
                    ext = subtitle.ext,
                    source = subtitle.source,
                    cookiesPath = request.cookiesPath,
                    listener = progressListener(),
                ).getOrThrow()
                finalOutputs += download.toOutputFile()
            }

            ensureActive()
            val completed = state.completeWith(finalOutputs).getOrThrow()
            emit(completed)
            DownloadPipelineResult(state = state, outputs = finalOutputs)
        } catch (_: DownloadPipelineCanceledException) {
            emit(state.canceled())
            DownloadPipelineResult(state = state, outputs = finalOutputs)
        } catch (exc: Exception) {
            if (exc is YtdlpDownloadException && exc.category == AnalysisErrorCategory.Canceled) {
                emit(state.canceled())
            } else {
                emit(state.failed(exc.message.orEmpty().ifBlank { "下载失败。" }))
            }
            DownloadPipelineResult(state = state, outputs = finalOutputs)
        }
    }

    private fun downloadFormatPart(
        request: DownloadRequest,
        outputDirectory: File,
        formatId: String,
        role: DownloadFormatRole,
        transition: (DownloadStage) -> Unit,
        progressListener: () -> DownloadProgressListener,
    ): File {
        transition(
            when (role) {
                DownloadFormatRole.Media -> DownloadStage.DownloadingVideo
                DownloadFormatRole.Video -> DownloadStage.DownloadingVideo
                DownloadFormatRole.Audio -> DownloadStage.DownloadingAudio
            },
        )
        val download = downloadFormatChecked(
            request = request,
            outputDirectory = outputDirectory,
            formatId = formatId,
            role = role,
            listener = progressListener(),
        )
        return download.requireExistingFile("${role.pythonValue} 输出")
    }

    private fun downloadFormatChecked(
        request: DownloadRequest,
        outputDirectory: File,
        formatId: String,
        role: DownloadFormatRole,
        listener: DownloadProgressListener,
    ): DownloadResult {
        val download = engine.downloadFormat(
            url = request.url,
            outputDirectory = outputDirectory,
            formatId = formatId,
            role = role,
            cookiesPath = request.cookiesPath,
            listener = listener,
        ).getOrThrow()
        if (download.formatId != formatId) {
            throw DownloadStateException("下载结果 formatId 与请求不一致。")
        }
        if (download.role != role) {
            throw DownloadStateException("下载结果 role 与请求不一致。")
        }
        return download
    }

    private fun DownloadResult.toOutputFile(
        kind: DownloadOutputKind,
        label: String,
    ): DownloadOutputFile {
        val file = requireExistingFile(label)
        return DownloadOutputFile(
            kind = kind,
            path = file.absolutePath,
            bytesWritten = bytesWritten,
        )
    }

    private fun SubtitleDownloadResult.toOutputFile(): DownloadOutputFile {
        val file = File(outputPath)
        if (!file.isFile || file.length() <= 0L || bytesWritten <= 0L) {
            throw DownloadStateException("字幕输出不存在或为空。")
        }
        return DownloadOutputFile(
            kind = DownloadOutputKind.Subtitle,
            path = file.absolutePath,
            bytesWritten = bytesWritten,
        )
    }

    private fun DownloadResult.requireExistingFile(label: String): File {
        val file = File(outputPath)
        if (!file.isFile || file.length() <= 0L || bytesWritten <= 0L) {
            throw DownloadStateException("$label 不存在或为空。")
        }
        return file
    }

    private fun String.safeFileToken(): String {
        return replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            .ifBlank { "format" }
    }
}

private class DownloadPipelineCanceledException : RuntimeException()
