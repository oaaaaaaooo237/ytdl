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
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

interface DownloadEngine {
    fun downloadFormat(
        url: String,
        outputDirectory: File,
        formatId: String,
        role: DownloadFormatRole,
        cookiesPath: String? = null,
        listener: DownloadProgressListener? = null,
        analysisKey: String? = null,
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
        analysisKey: String?,
    ): Result<DownloadResult> {
        return bridge.downloadFormat(url, outputDirectory, formatId, role, cookiesPath, listener, analysisKey)
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

enum class DownloadStopReason { UserPause, NetworkUnavailable, UserCancel, LifecycleEnd }

interface DownloadCancellation {
    val isCancellationRequested: Boolean
    val stopReason: DownloadStopReason?
        get() = if (isCancellationRequested) DownloadStopReason.UserCancel else null
}

object NeverDownloadCancellation : DownloadCancellation {
    override val isCancellationRequested = false
}

class MutableDownloadCancellation : DownloadCancellation {
    private val reason = AtomicReference<DownloadStopReason?>(null)
    override val stopReason: DownloadStopReason? get() = reason.get()
    override val isCancellationRequested: Boolean get() = stopReason != null
    fun cancel() { reason.set(DownloadStopReason.UserCancel) }
    fun pause() { reason.updateAndGet { if (it == DownloadStopReason.UserCancel) it else DownloadStopReason.UserPause } }
    fun waitForNetwork() { reason.compareAndSet(null, DownloadStopReason.NetworkUnavailable) }
    fun stopForLifecycle() { reason.compareAndSet(null, DownloadStopReason.LifecycleEnd) }
}

data class DownloadPipelineResult(
    val state: DownloadTaskState,
    val outputs: List<DownloadOutputFile>,
)

class DownloadPipeline(
    private val engine: DownloadEngine,
    private val mediaProcessor: MediaProcessor,
    private val deleteIntermediateFile: (File) -> Boolean = { it.delete() },
    private val isNetworkAvailable: () -> Boolean = { true },
) {
    fun run(
        request: DownloadRequest,
        outputDirectory: File,
        cancellation: DownloadCancellation = NeverDownloadCancellation,
        onStateChanged: (DownloadTaskState) -> Unit = {},
    ): DownloadPipelineResult {
        if (request.route is DownloadRoute.HttpFile) {
            return HttpFilePipeline().run(request, outputDirectory, cancellation, onStateChanged)
        }
        var state = DownloadTaskState.waiting(request)
        val finalOutputs = mutableListOf<DownloadOutputFile>()
        var taskDirectory: File? = null
        var session: PipelineCheckpoint? = null
        var subtitleLanguage: String? = null
        var currentStage = DownloadStage.Waiting
        var lastProgressSave = 0L
        var checkpointFailed = false
        fun emit(next: DownloadTaskState) { state = next; onStateChanged(next) }
        fun save(reason: DownloadStopReason? = cancellation.stopReason) {
            try { session?.save(currentStage, state.progress, reason) }
            catch (error: PipelineCheckpointException) { checkpointFailed = true; throw error }
        }
        fun ensureActive() {
            if (cancellation.stopReason != null) throw DownloadPipelineStoppedException()
        }
        fun transition(stage: DownloadStage) {
            currentStage = stage
            state = state.atStage(stage)
            save()
            emit(state)
            ensureActive()
        }
        fun listener() = DownloadProgressListener { progress ->
            ensureActive()
            if (progress.status == "checking") return@DownloadProgressListener
            val previousBytes = state.progress?.downloadedBytes ?: 0
            val restarted = session?.resumingPhase == currentStage.name &&
                (progress.downloadedBytes ?: 0) < previousBytes
            state = state.withProgress(progress.copy(filename = null)).copy(
                statusMessage = if (restarted) "该网站无法接着下载，已重新下载当前文件。" else state.statusMessage,
            )
            session?.resumingPhase = null
            val now = System.nanoTime()
            if (now - lastProgressSave >= 1_000_000_000L) { save(); lastProgressSave = now }
            emit(state)
            ensureActive()
        }
        emit(state)
        try {
            validateCookiesPath(request.cookiesPath)?.let {
                emit(state.failed(it))
                return DownloadPipelineResult(state, emptyList())
            }
            val workspace = TaskWorkspace(outputDirectory)
            val checkpoint = PipelineCheckpoint.open(workspace, request)
            session = checkpoint
            currentStage = checkpoint.phase
            taskDirectory = checkpoint.directory
            val appPrivateRoot = requireNotNull(taskDirectory.parentFile).canonicalFile
            ExportController.markPrivateTaskStarted(taskDirectory).getOrThrow()
            state = state.copy(progress = checkpoint.progress())
            if (checkpoint.isResuming) emit(state.copy(stage = DownloadStage.Resuming))
            ensureActive()

            fun output(file: File, kind: DownloadOutputKind = DownloadOutputKind.Media) = DownloadOutputFile(
                kind, file.absolutePath, file.length(), appPrivateRoot.absolutePath,
            )
            fun stream(formatId: String, role: DownloadFormatRole, merge: Boolean): File {
                checkpoint.file(role.pythonValue)?.let { existing ->
                    if (merge) return existing
                    val titled = DownloadResult(existing.path, existing.length(), request.title, formatId, role)
                        .toTitledOutputFile(DownloadOutputKind.Media, "下载输出", request.title, appPrivateRoot, ::ensureActive)
                    val file = File(titled.path)
                    checkpoint.record(role.pythonValue, file)
                    save()
                    return file
                }
                val stage = if (role == DownloadFormatRole.Audio) DownloadStage.DownloadingAudio else DownloadStage.DownloadingVideo
                // The saved byte count belongs only to the interrupted stream.
                val restoredProgress = if (checkpoint.resumingPhase == stage.name) state.progress else null
                currentStage = stage
                state = state.atStage(stage).copy(progress = restoredProgress)
                save(); emit(state); ensureActive()
                val directory = checkpoint.streamDirectory(merge)
                val downloaded = downloadFormatChecked(request, directory, formatId, role, listener(), cancellation)
                val raw = downloaded.requireExistingFile("下载输出")
                if (raw.absoluteFile.parentFile != directory.absoluteFile) throw PipelineCheckpointException(IllegalStateException("任务输出路径不可用。"))
                checkpoint.record(role.pythonValue, raw)
                save()
                if (merge) return raw
                // Keep the recorded raw path until the titled copy is checkpointed.
                val titled = downloaded.toTitledOutputFile(DownloadOutputKind.Media, "下载输出", request.title, appPrivateRoot, ::ensureActive)
                val file = File(titled.path)
                checkpoint.record(role.pythonValue, file)
                save()
                return file
            }

            when (val route = request.route) {
                is DownloadRoute.HttpFile -> error("文件请求应由文件管线处理。")
                is DownloadRoute.DirectSingleFile -> finalOutputs += output(stream(route.formatId, DownloadFormatRole.Media, false))
                is DownloadRoute.VideoOnly -> finalOutputs += output(stream(route.videoFormatId, DownloadFormatRole.Video, false))
                is DownloadRoute.AudioOnly -> finalOutputs += output(stream(route.audioFormatId, DownloadFormatRole.Audio, false))
                is DownloadRoute.MergeRequired -> {
                    var merged = checkpoint.file("merged")
                    if (merged == null) {
                        val video = stream(route.videoFormatId, DownloadFormatRole.Video, true)
                        ensureActive()
                        val audio = stream(route.audioFormatId, DownloadFormatRole.Audio, true)
                        ensureActive()
                        transition(DownloadStage.Merging)
                        val target = File(taskDirectory, "${mediaFileBaseName(request.title)}.${MediaOutputContainer.Mp4.extension}")
                        // Only this deterministic incomplete merge output may be replaced.
                        if (target.exists()) {
                            ControlledDirectory(taskDirectory).checkSafe(target)
                            check(target.isFile && target.delete())
                        }
                        val result = mediaProcessor.mergeVideoAndAudio(MediaMergeRequest(
                            videoInput = video, audioInput = audio, outputFile = target,
                            outputContainer = MediaOutputContainer.Mp4,
                            expectedVideoFormatId = route.videoFormatId, expectedAudioFormatId = route.audioFormatId,
                        )).getOrThrow()
                        check(result.outputFile.canonicalFile == target.canonicalFile && target.length() > 0)
                        checkpoint.record("merged", target)
                        save()
                        merged = target
                        finalOutputs += output(target)
                        ensureActive()
                        checkpoint.forget("video", "audio")
                        save()
                        deleteIntermediateStream(video)
                        deleteIntermediateStream(audio)
                    } else finalOutputs += output(merged)
                }
            }
            ensureActive()
            request.selectedSubtitles.forEachIndexed { index, subtitle ->
                val role = "subtitle_$index"
                val existing = checkpoint.file(role)
                if (existing != null) finalOutputs += output(existing, DownloadOutputKind.Subtitle)
                else {
                    subtitleLanguage = subtitle.language
                    transition(DownloadStage.DownloadingSubtitles)
                    val download = engine.downloadSubtitle(request.url, taskDirectory, subtitle.language,
                        subtitle.ext, subtitle.source, request.cookiesPath, listener()).getOrThrow()
                    check(download.language == subtitle.language && download.ext == subtitle.ext && download.source == subtitle.source)
                    val file = download.toOutputFile(appPrivateRoot)
                    checkpoint.record(role, File(file.path))
                    save()
                    finalOutputs += file
                }
                ensureActive()
            }
            subtitleLanguage = null
            checkpoint.forget("video", "audio", onlyForMerged = true)
            save()
            cleanupUntrackedTaskFiles(taskDirectory, finalOutputs, preserveLifecycleMarker = true, preserveCheckpoint = true)
            currentStage = DownloadStage.Completed
            save()
            emit(state.completeWith(finalOutputs).getOrThrow())
        } catch (exc: Exception) {
            val permanentSiteError = exc is YtdlpDownloadException &&
                exc.category in setOf(AnalysisErrorCategory.Permission, AnalysisErrorCategory.Unsupported)
            val requestedReason = cancellation.stopReason.takeUnless { it == DownloadStopReason.NetworkUnavailable && permanentSiteError }
            val reason = requestedReason ?: if (
                exc is YtdlpDownloadException && exc.category == AnalysisErrorCategory.Network && !isNetworkAvailable()
            ) DownloadStopReason.NetworkUnavailable else null
            when {
                reason == DownloadStopReason.UserCancel -> {
                    val deletion = runCatching { TaskWorkspace(outputDirectory).deleteTask(request.taskId).getOrThrow() }
                    finalOutputs.clear()
                    emit(if (deletion.isSuccess) state.canceled().copy(outputs = emptyList())
                        else state.failed("任务文件待清理，已保留文件。"))
                }
                exc is PipelineCheckpointException || checkpointFailed -> emit(state.failed("任务恢复信息校验失败，已保留文件。"))
                reason != null && session != null -> {
                    try {
                        save(reason)
                        if (reason == DownloadStopReason.UserPause) emit(state.copy(stage = DownloadStage.Pausing))
                        emit(state.copy(
                            stage = if (reason == DownloadStopReason.NetworkUnavailable) DownloadStage.WaitingForNetwork else DownloadStage.Paused,
                            stopReason = reason, outputs = finalOutputs.toList(), errorMessage = null,
                            progress = state.progress?.copy(speedBytesPerSecond = 0.0, etaSeconds = null, filename = null),
                        ))
                    } catch (_: Exception) { emit(state.failed("暂停信息无法保存，已保留文件。")) }
                }
                else -> {
                    taskDirectory?.let { cleanupUntrackedTaskFiles(it, finalOutputs) }
                    val message = subtitleLanguage?.let(DownloadFailureMessages::missingSubtitle)
                        ?: if (exc is YtdlpDownloadException && exc.category == AnalysisErrorCategory.Canceled)
                            DownloadFailureMessages.processing() else DownloadFailureMessages.fromException(exc)
                    emit(state.failed(message, finalOutputs.toList()))
                }
            }
        } finally {
            cleanupTemporaryCookies(request.cookiesPath)?.let { emit(state.failed(it)) }
        }
        return DownloadPipelineResult(state, finalOutputs.toList())
    }

    private fun downloadFormatChecked(
        request: DownloadRequest,
        outputDirectory: File,
        formatId: String,
        role: DownloadFormatRole,
        listener: DownloadProgressListener,
        cancellation: DownloadCancellation,
    ): DownloadResult {
        var lastFailure: Exception? = null
        for (attempt in 1..MaxFormatDownloadAttempts) {
            if (cancellation.stopReason != null) throw DownloadPipelineStoppedException()
            try {
                val download = engine.downloadFormat(
                    url = request.url,
                    outputDirectory = outputDirectory,
                    formatId = formatId,
                    role = role,
                    cookiesPath = request.cookiesPath,
                    listener = listener,
                    analysisKey = request.analysisKey,
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
                if (cancellation.stopReason != null || attempt >= MaxFormatDownloadAttempts || !exc.isRetryableFormatDownloadFailure() || !isNetworkAvailable()) {
                    throw exc
                }
                repeat(attempt * 5) {
                    if (cancellation.stopReason != null || !isNetworkAvailable()) throw exc
                    Thread.sleep(50)
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
        ensureActive: () -> Unit,
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
            try {
                val guard = ControlledDirectory(requireNotNull(downloadedFile.parentFile))
                guard.checkSafe(downloadedFile)
                guard.checkSafe(file)
                if (!file.exists()) {
                    // Android SELinux disallows hard links for app data. Publish a fully
                    // synced copy while the prior checkpoint continues to reference raw.
                    val temporary = guard.child(requireNotNull(file.parentFile), ".title-${UUID.randomUUID()}.tmp")
                    check(temporary.createNewFile())
                    try {
                        downloadedFile.inputStream().use { input ->
                            FileOutputStream(temporary).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                                output.flush()
                                output.fd.sync()
                            }
                        }
                        listOf(guard.root, downloadedFile, temporary, file).forEach(guard::checkSafe)
                        check(temporary.length() == downloadedFile.length())
                        ensureActive()
                        check(!file.exists() && temporary.renameTo(file)) { "主文件命名冲突。" }
                    } finally {
                        if (temporary.exists()) runCatching { guard.checkSafe(temporary); temporary.delete() }
                    }
                } else {
                    check(file.isFile && file.length() == downloadedFile.length() &&
                        file.sha256(ensureActive).contentEquals(downloadedFile.sha256(ensureActive))) { "主文件命名冲突。" }
                }
            } catch (stopped: DownloadPipelineStoppedException) { throw stopped
            } catch (error: Exception) { throw PipelineCheckpointException(error) }
        }
        return DownloadOutputFile(
            kind = kind,
            path = file.absolutePath,
            bytesWritten = bytesWritten,
            appPrivateRootPath = appPrivateRoot.absolutePath,
        )
    }

    private fun File.sha256(ensureActive: () -> Unit): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
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
        preserveCheckpoint: Boolean = false,
    ) {
        val retainedOutputs = finalOutputs.mapNotNull { output ->
            runCatching { File(output.path).canonicalFile }.getOrNull()
        }
        val canonicalTaskDirectory = taskOutputDirectory.canonicalFile
        taskOutputDirectory.listFiles()
            ?.filterNot { ExportController.isTaskLifecycleMarker(it) || (preserveCheckpoint && it.name in setOf("checkpoint.bin", "exported.bin")) }
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
            .filterNot { ExportController.isTaskLifecycleMarker(it) || (preserveCheckpoint && it.name in setOf("checkpoint.bin", "exported.bin")) }
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
        if (Build.VERSION.SDK_INT >= 26) {
            val path = candidate.toPath()
            if (Files.isSymbolicLink(path)) return runCatching { Files.deleteIfExists(path) }.getOrDefault(false)
        }
        if (runCatching { ControlledDirectory(canonicalTaskDirectory).checkSafe(candidate) }.isFailure) return false
        val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrNull() ?: return false
        if (!canonicalCandidate.path.startsWith(canonicalTaskDirectory.path + File.separator)) return false
        if (candidate.isDirectory) {
            val children = candidate.listFiles() ?: return false
            if (!children.all { deleteTaskEntry(it, canonicalTaskDirectory) }) return false
        }
        return candidate.delete() || !candidate.exists()
    }

    private companion object {
        const val MaxFormatDownloadAttempts = 2
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

private class DownloadPipelineStoppedException : RuntimeException()
