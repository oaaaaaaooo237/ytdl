package com.garyapp.ytdl.download

import com.garyapp.ytdl.cookies.TemporaryCookiesFile
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
import com.garyapp.ytdl.storage.ExportController
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
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
    private val deleteIntermediateFile: (File) -> Boolean = { it.delete() },
) {
    fun run(
        request: DownloadRequest,
        outputDirectory: File,
        cancellation: DownloadCancellation = NeverDownloadCancellation,
        onStateChanged: (DownloadTaskState) -> Unit = {},
    ): DownloadPipelineResult {
        outputDirectory.mkdirs()
        val appPrivateRoot = outputDirectory.canonicalFile
        val taskOutputDirectory = createTaskOutputDirectory(appPrivateRoot)
        ExportController.markPrivateTaskStarted(taskOutputDirectory).getOrThrow()
        var state = DownloadTaskState.waiting(request)
        var currentStage = state.stage
        var currentSubtitleLanguage: String? = null
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

        val cookiesValidationError = validateCookiesPath(request.cookiesPath)
        if (cookiesValidationError != null) {
            emit(state.failed(cookiesValidationError))
            return DownloadPipelineResult(state = state, outputs = finalOutputs)
        }

        val result = try {
            ensureActive()
            when (val route = request.route) {
                is DownloadRoute.DirectSingleFile -> {
                    transition(DownloadStage.DownloadingVideo)
                    val download = downloadFormatChecked(
                        request = request,
                        outputDirectory = taskOutputDirectory,
                        formatId = route.formatId,
                        role = DownloadFormatRole.Media,
                        listener = progressListener(),
                    )
                    finalOutputs += download.toTitledOutputFile(
                        kind = DownloadOutputKind.Media,
                        label = "媒体",
                        title = request.title,
                        appPrivateRoot = appPrivateRoot,
                    )
                    ensureActive()
                }
                is DownloadRoute.VideoOnly -> {
                    transition(DownloadStage.DownloadingVideo)
                    val download = downloadFormatChecked(
                        request = request,
                        outputDirectory = taskOutputDirectory,
                        formatId = route.videoFormatId,
                        role = DownloadFormatRole.Video,
                        listener = progressListener(),
                    )
                    finalOutputs += download.toTitledOutputFile(
                        kind = DownloadOutputKind.Media,
                        label = "视频",
                        title = request.title,
                        appPrivateRoot = appPrivateRoot,
                    )
                    ensureActive()
                }
                is DownloadRoute.AudioOnly -> {
                    transition(DownloadStage.DownloadingAudio)
                    val download = downloadFormatChecked(
                        request = request,
                        outputDirectory = taskOutputDirectory,
                        formatId = route.audioFormatId,
                        role = DownloadFormatRole.Audio,
                        listener = progressListener(),
                    )
                    finalOutputs += download.toTitledOutputFile(
                        kind = DownloadOutputKind.Media,
                        label = "音频",
                        title = request.title,
                        appPrivateRoot = appPrivateRoot,
                    )
                    ensureActive()
                }
                is DownloadRoute.MergeRequired -> {
                    val intermediateDirectory = File(taskOutputDirectory, ".parts").apply { mkdirs() }
                    val video = downloadFormatPart(request, intermediateDirectory, route.videoFormatId, DownloadFormatRole.Video, ::transition, ::progressListener)
                    ensureActive()
                    val audio = downloadFormatPart(request, intermediateDirectory, route.audioFormatId, DownloadFormatRole.Audio, ::transition, ::progressListener)
                    ensureActive()
                    transition(DownloadStage.Merging)
                    val mergedOutput = File(
                        taskOutputDirectory,
                        "${mediaFileBaseName(request.title)}.${MediaOutputContainer.Mp4.extension}",
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
                        appPrivateRootPath = appPrivateRoot.absolutePath,
                    )
                    deleteIntermediateStream(video)
                    deleteIntermediateStream(audio)
                    ensureActive()
                }
            }

            request.selectedSubtitles.forEach { subtitle ->
                ensureActive()
                currentSubtitleLanguage = subtitle.language
                transition(DownloadStage.DownloadingSubtitles)
                val download = engine.downloadSubtitle(
                    url = request.url,
                    outputDirectory = taskOutputDirectory,
                    language = subtitle.language,
                    ext = subtitle.ext,
                    source = subtitle.source,
                    cookiesPath = request.cookiesPath,
                    listener = progressListener(),
                ).getOrThrow()
                finalOutputs += download.toOutputFile(appPrivateRoot)
            }
            currentSubtitleLanguage = null

            ensureActive()
            cleanupUntrackedTaskFiles(
                taskOutputDirectory = taskOutputDirectory,
                finalOutputs = finalOutputs,
                preserveLifecycleMarker = true,
            )
            val completed = state.completeWith(finalOutputs).getOrThrow()
            emit(completed)
            DownloadPipelineResult(state = state, outputs = finalOutputs)
        } catch (_: DownloadPipelineCanceledException) {
            cleanupUntrackedTaskFiles(taskOutputDirectory, finalOutputs)
            emit(state.canceled())
            DownloadPipelineResult(state = state, outputs = finalOutputs)
        } catch (exc: Exception) {
            cleanupUntrackedTaskFiles(taskOutputDirectory, finalOutputs)
            if (exc is YtdlpDownloadException && exc.category == AnalysisErrorCategory.Canceled) {
                emit(state.canceled())
            } else {
                val message = if (currentSubtitleLanguage != null) {
                    DownloadFailureMessages.missingSubtitle(currentSubtitleLanguage)
                } else {
                    DownloadFailureMessages.fromException(exc)
                }
                emit(state.failed(message, finalOutputs.toList()))
            }
            DownloadPipelineResult(state = state, outputs = finalOutputs)
        }

        val cleanupError = cleanupTemporaryCookies(request.cookiesPath)
        if (cleanupError != null) {
            emit(state.failed(cleanupError))
            return DownloadPipelineResult(state = state, outputs = finalOutputs)
        }

        return result
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
        var lastFailure: Exception? = null
        for (attempt in 1..MaxFormatDownloadAttempts) {
            try {
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
            } catch (exc: Exception) {
                lastFailure = exc
                if (attempt >= MaxFormatDownloadAttempts || !exc.isRetryableFormatDownloadFailure()) {
                    throw exc
                }
            }
        }
        throw lastFailure ?: DownloadStateException("下载失败。")
    }

    private fun Exception.isRetryableFormatDownloadFailure(): Boolean {
        return this is YtdlpDownloadException && category == AnalysisErrorCategory.Network
    }

    private fun validateCookiesPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (TemporaryCookiesFile.isManagedTemporaryPath(path)) {
            null
        } else {
            DownloadFailureMessages.unmanagedCookiesReference()
        }
    }

    private fun cleanupTemporaryCookies(path: String?): String? {
        if (path.isNullOrBlank() || !TemporaryCookiesFile.isManagedTemporaryPath(path)) {
            return null
        }
        return if (TemporaryCookiesFile.deleteIfManagedPath(path)) {
            null
        } else {
            DownloadFailureMessages.cookiesCleanupFailed()
        }
    }

    private fun DownloadResult.toTitledOutputFile(
        kind: DownloadOutputKind,
        label: String,
        title: String,
        appPrivateRoot: File,
    ): DownloadOutputFile {
        val downloadedFile = requireExistingFile(label)
        val extension = downloadedFile.extension.takeIf { it.matches(Regex("""[A-Za-z0-9]{1,10}""")) }
        val targetName = buildString {
            append(mediaFileBaseName(title))
            if (extension != null) {
                append('.')
                append(extension)
            }
        }
        val file = File(downloadedFile.parentFile, targetName)
        if (file != downloadedFile) {
            check(!file.exists()) { "主文件命名冲突。" }
            check(downloadedFile.renameTo(file) && file.isFile) { "无法按媒体标题命名主文件。" }
        }
        return DownloadOutputFile(
            kind = kind,
            path = file.absolutePath,
            bytesWritten = bytesWritten,
            appPrivateRootPath = appPrivateRoot.absolutePath,
        )
    }

    private fun SubtitleDownloadResult.toOutputFile(appPrivateRoot: File): DownloadOutputFile {
        val file = File(outputPath)
        if (!file.isFile || file.length() <= 0L || bytesWritten <= 0L) {
            throw DownloadStateException("字幕输出不存在或为空。")
        }
        return DownloadOutputFile(
            kind = DownloadOutputKind.Subtitle,
            path = file.absolutePath,
            bytesWritten = bytesWritten,
            appPrivateRootPath = appPrivateRoot.absolutePath,
        )
    }

    private fun DownloadResult.requireExistingFile(label: String): File {
        val file = File(outputPath)
        if (!file.isFile || file.length() <= 0L || bytesWritten <= 0L) {
            throw DownloadStateException("$label 不存在或为空。")
        }
        return file
    }

    private fun deleteIntermediateStream(file: File) {
        if (file.isFile && !deleteIntermediateFile(file) && file.exists()) {
            throw DownloadStateException("合并完成后无法清理中间流文件。")
        }
    }

    private fun cleanupUntrackedTaskFiles(
        taskOutputDirectory: File,
        finalOutputs: List<DownloadOutputFile>,
        preserveLifecycleMarker: Boolean = false,
    ) {
        val retainedOutputs = finalOutputs.mapNotNull { output ->
            runCatching { File(output.path).canonicalFile }.getOrNull()
        }
        val canonicalTaskDirectory = taskOutputDirectory.canonicalFile
        taskOutputDirectory.listFiles()
            ?.filterNot(ExportController::isTaskLifecycleMarker)
            ?.forEach { candidate ->
            val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrNull() ?: return@forEach
            val containsRetainedOutput = retainedOutputs.any { output ->
                output == canonicalCandidate || output.path.startsWith(canonicalCandidate.path + File.separator)
            }
            if (!containsRetainedOutput) {
                deleteTaskEntry(candidate, canonicalTaskDirectory)
            }
        }
        val remainingPayloads = taskOutputDirectory.listFiles().orEmpty()
            .filterNot(ExportController::isTaskLifecycleMarker)
        val hasUntrackedPayload = remainingPayloads.any { candidate ->
            val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrNull() ?: return@any true
            retainedOutputs.none { output ->
                output == canonicalCandidate || output.path.startsWith(canonicalCandidate.path + File.separator)
            }
        }
        if (preserveLifecycleMarker && hasUntrackedPayload) {
            throw DownloadStateException("无法清理下载过程文件。")
        }
        if (!preserveLifecycleMarker && !hasUntrackedPayload) {
            taskOutputDirectory.listFiles().orEmpty()
                .filter(ExportController::isTaskLifecycleMarker)
                .forEach(File::delete)
        }
        if (taskOutputDirectory.listFiles().isNullOrEmpty()) {
            taskOutputDirectory.delete()
        }
    }

    private fun deleteTaskEntry(candidate: File, canonicalTaskDirectory: File): Boolean {
        val path = candidate.toPath()
        if (Files.isSymbolicLink(path)) {
            return runCatching { Files.deleteIfExists(path) }.getOrDefault(false)
        }
        val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrNull() ?: return false
        if (!canonicalCandidate.path.startsWith(canonicalTaskDirectory.path + File.separator)) return false
        if (candidate.isDirectory) {
            val children = candidate.listFiles() ?: return false
            if (!children.all { deleteTaskEntry(it, canonicalTaskDirectory) }) return false
        }
        return candidate.delete() || !candidate.exists()
    }

    private fun createTaskOutputDirectory(appPrivateRoot: File): File {
        val dir = File(appPrivateRoot, "task-${System.currentTimeMillis()}-${TaskSequence.incrementAndGet().toString(36)}")
        dir.mkdirs()
        return dir
    }

    private companion object {
        const val MaxFormatDownloadAttempts = 2
        private val TaskSequence = AtomicLong()
    }
}

internal fun mediaFileBaseNameForTest(title: String): String = mediaFileBaseName(title)

private fun mediaFileBaseName(title: String): String {
    val cleaned = title
        .replace(Regex("""[\u0000-\u001F\u007F<>:"/\\|?*]+"""), "_")
        .trim()
        .trim('.', '_', ' ')
        .ifBlank { "未命名媒体" }
    val safeReservedName = if (cleaned.substringBefore('.').uppercase(Locale.ROOT) in WindowsReservedFileNames) {
        "_$cleaned"
    } else {
        cleaned
    }
    return safeReservedName.takeUtf8Prefix(180).ifBlank { "未命名媒体" }
}

private fun String.takeUtf8Prefix(maxBytes: Int): String {
    var endIndex = 0
    var byteCount = 0
    while (endIndex < length) {
        val codePoint = codePointAt(endIndex)
        val codePointText = String(Character.toChars(codePoint))
        val codePointBytes = codePointText.toByteArray(Charsets.UTF_8).size
        if (byteCount + codePointBytes > maxBytes) break
        byteCount += codePointBytes
        endIndex += Character.charCount(codePoint)
    }
    return substring(0, endIndex).trimEnd('.', ' ')
}

private val WindowsReservedFileNames = buildSet {
    addAll(listOf("CON", "PRN", "AUX", "NUL"))
    (1..9).forEach { index ->
        add("COM$index")
        add("LPT$index")
    }
}

private class DownloadPipelineCanceledException : RuntimeException()
