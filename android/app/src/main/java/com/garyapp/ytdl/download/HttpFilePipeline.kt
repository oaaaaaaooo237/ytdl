package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.files.FileDownloadResult
import com.garyapp.ytdl.core.files.FileKind
import com.garyapp.ytdl.core.files.ResumableFileDownloader
import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import com.garyapp.ytdl.storage.ExportController
import java.io.File
import java.io.FileOutputStream

/** File transport shares task ownership and recovery with media, without invoking yt-dlp. */
class HttpFilePipeline {
    fun run(
        request: DownloadRequest,
        outputDirectory: File,
        cancellation: DownloadCancellation = NeverDownloadCancellation,
        onStateChanged: (DownloadTaskState) -> Unit = {},
    ): DownloadPipelineResult {
        val route = requireNotNull(request.route as? DownloadRoute.HttpFile)
        val workspace = TaskWorkspace(outputDirectory)
        var state = DownloadTaskState.waiting(request)
        if (request.usesCookies || !request.cookiesPath.isNullOrBlank()) {
            val path = request.cookiesPath
            val cleaned = path.isNullOrBlank() || !com.garyapp.ytdl.cookies.TemporaryCookiesFile.isManagedTemporaryPath(path) ||
                com.garyapp.ytdl.cookies.TemporaryCookiesFile.deleteIfManagedPath(path)
            state = state.failed(if (cleaned) "文件下载需要有效直链，请重新确认文件信息。" else "临时授权文件待清理，请重试。")
            onStateChanged(state)
            return DownloadPipelineResult(state, emptyList())
        }
        var checkpoint: PipelineCheckpoint? = null
        var outputs = emptyList<DownloadOutputFile>()
        fun emit(next: DownloadTaskState) { state = next; onStateChanged(next) }
        fun save() { checkpoint?.save(state.stage, state.progress, state.stopReason, state.fileRecoveryAction) }
        fun stopped() {
            val reason = cancellation.stopReason ?: DownloadStopReason.LifecycleEnd
            if (reason == DownloadStopReason.UserCancel) {
                workspace.deleteTask(request.taskId).getOrThrow()
                outputs = emptyList()
                emit(state.canceled().copy(outputs = outputs, fileRecoveryAction = null))
            } else {
                state = state.copy(stage = if (reason == DownloadStopReason.NetworkUnavailable)
                    DownloadStage.WaitingForNetwork else DownloadStage.Paused, stopReason = reason,
                    progress = state.progress?.copy(speedBytesPerSecond = 0.0, etaSeconds = null), fileRecoveryAction = null)
                save(); emit(state)
            }
        }
        try {
            if (cancellation.stopReason == DownloadStopReason.UserCancel) {
                stopped(); return DownloadPipelineResult(state, outputs)
            }
            require(request.url == route.metadata.sourceUrl && request.selectedSubtitles.isEmpty()) { "文件请求与分析信息不一致。" }
            val session = PipelineCheckpoint.open(workspace, request)
            checkpoint = session
            state = state.copy(progress = session.progress())
            val task = session.directory
            val guard = ControlledDirectory(task)
            ExportController.markPrivateTaskStarted(task).getOrThrow()
            if (session.fileRecoveryAction in setOf(FileRecoveryAction.ConfirmRestart, FileRecoveryAction.ChooseFileName)) {
                emit(state.copy(stage = DownloadStage.Failed, fileRecoveryAction = session.fileRecoveryAction,
                    errorMessage = if (session.fileRecoveryAction == FileRecoveryAction.ChooseFileName) "文件名已存在，请选择其他名称。" else "无法安全续传，请确认重新下载。"))
                return DownloadPipelineResult(state, outputs)
            }
            if (cancellation.stopReason != null) { stopped(); return DownloadPipelineResult(state, outputs) }
            val outputKind = if (route.metadata.kind == FileKind.Other) DownloadOutputKind.File else DownloadOutputKind.Media
            fun output(file: File) = DownloadOutputFile(outputKind, file.path, file.length(), outputDirectory.canonicalPath, mimeType = route.metadata.mimeType)
            val existing = session.file("file")
            if (existing != null) {
                outputs = listOf(output(existing)); emit(state.completeWith(outputs).getOrThrow())
                return DownloadPipelineResult(state, outputs)
            }
            val outputDirectoryForTask = guard.child(task, "outputs")
            fun requiresNameChoice(): Boolean {
                guard.checkSafe(outputDirectoryForTask)
                val target = guard.child(outputDirectoryForTask, route.fileName)
                if (route.conflictPolicy != HttpFileConflictPolicy.Ask || !target.exists()) return false
                state = state.failed("文件名已存在，请选择其他名称。").copy(fileRecoveryAction = FileRecoveryAction.ChooseFileName)
                save(); emit(state)
                return true
            }
            if (requiresNameChoice()) return DownloadPipelineResult(state, outputs)
            val http = guard.child(task, "http").also { check(it.mkdirs() || it.isDirectory); guard.checkSafe(it) }
            val partial = guard.child(http, "body.part")
            val transferCheckpoint = guard.child(http, "resume.bin")
            state = state.copy(stage = DownloadStage.DownloadingFile, stopReason = null, fileRecoveryAction = null)
            save(); emit(state)
            val started = System.nanoTime()
            val initialBytes = partial.takeIf { it.isFile }?.length() ?: 0L
            val result = ResumableFileDownloader().download(route.metadata, partial, transferCheckpoint,
                shouldStop = { cancellation.stopReason != null },
                onProgress = { downloaded, total ->
                    val seconds = (System.nanoTime() - started) / 1_000_000_000.0
                    val speed = if (seconds > 0) (downloaded - initialBytes).coerceAtLeast(0) / seconds else 0.0
                    val progress = DownloadProgress("downloading", total?.takeIf { it > 0 }?.let { downloaded * 100.0 / it },
                        downloaded, total, speed, if (total != null && speed > 0) ((total - downloaded).coerceAtLeast(0) / speed).toLong() else null, null)
                    state = state.withProgress(progress); save(); emit(state)
                })
            if (cancellation.stopReason != null || result is FileDownloadResult.Stopped) {
                stopped()
            } else when (result) {
                is FileDownloadResult.Completed -> {
                    check(result.bytes > 0 || outputKind == DownloadOutputKind.File) { "媒体输出为空。" }
                    val directory = guard.child(task, "outputs").also { check(it.mkdirs() || it.isDirectory); guard.checkSafe(it) }
                    if (requiresNameChoice()) return DownloadPipelineResult(state, outputs)
                    var target = guard.child(directory, route.fileName)
                    var index = 1
                    while (target.exists()) {
                        val extension = route.fileName.substringAfterLast('.', "")
                        val base = if (extension.isEmpty()) route.fileName else route.fileName.dropLast(extension.length + 1)
                        target = guard.child(directory, "$base (${index++})${if (extension.isEmpty()) "" else ".$extension"}")
                    }
                    check(target.createNewFile())
                    try {
                        partial.inputStream().use { input -> FileOutputStream(target).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                if (cancellation.stopReason != null) return@use
                                val count = input.read(buffer); if (count < 0) break
                                out.write(buffer, 0, count)
                            }
                            out.fd.sync()
                        } }
                        if (cancellation.stopReason != null) { stopped(); return DownloadPipelineResult(state, outputs) }
                        guard.checkSafe(target); check(target.length() == result.bytes)
                        session.record("file", target)
                        outputs = listOf(output(target))
                        state = state.completeWith(outputs).getOrThrow(); save(); emit(state)
                    } finally {
                        if (state.stage != DownloadStage.Completed && target.exists()) { guard.checkSafe(target); target.delete() }
                    }
                    // The final checkpoint is durable before removing transfer data.
                    listOf(partial, transferCheckpoint).forEach { guard.checkSafe(it); check(!it.exists() || it.delete()) }
                    check(http.delete())
                }
                is FileDownloadResult.RestartRequired -> {
                    state = state.failed(result.reason).copy(fileRecoveryAction = FileRecoveryAction.ConfirmRestart)
                    save(); emit(state)
                }
                is FileDownloadResult.Failed -> {
                    state = state.failed(if (result.reason.contains("（401）") || result.reason.contains("（403）"))
                        "链接需要登录或已失效，请获取有效直链后重新下载。" else result.reason).copy(fileRecoveryAction = FileRecoveryAction.Retry)
                    save(); emit(state)
                }
                is FileDownloadResult.Stopped -> error("停止结果已处理。")
            }
        } catch (_: Exception) {
            if (cancellation.stopReason != null) {
                runCatching { stopped() }.onFailure { emit(state.failed("任务文件待清理，已保留文件。").copy(fileRecoveryAction = FileRecoveryAction.Retry)) }
            } else {
                state = state.failed("文件下载未完成，请重试。").copy(fileRecoveryAction = FileRecoveryAction.Retry)
                runCatching { save() }
                emit(state)
            }
        }
        return DownloadPipelineResult(state, outputs)
    }

    companion object {
        /** Call only after the user confirms discarding the old partial. Keeps the stable task identity. */
        internal fun confirmRestart(workspace: TaskWorkspace, request: DownloadRequest): Result<Unit> = runCatching {
            require(request.route is DownloadRoute.HttpFile)
            val checkpoint = workspace.loadCheckpoint(request.taskId).getOrThrow()
            require(checkpoint.fileRecoveryAction == FileRecoveryAction.ConfirmRestart.name)
            workspace.deleteTask(request.taskId).getOrThrow()
            workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, DownloadStage.Waiting.name)).getOrThrow()
        }
    }
}
