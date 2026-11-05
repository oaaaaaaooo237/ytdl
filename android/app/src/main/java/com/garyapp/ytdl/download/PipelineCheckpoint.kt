package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import java.io.File

internal class PipelineCheckpointException(cause: Exception) : RuntimeException(cause)

/** Owns role identities; TaskWorkspace owns path and atomic snapshot validation. */
internal class PipelineCheckpoint private constructor(
    private val workspace: TaskWorkspace,
    private val request: DownloadRequest,
    val directory: File,
    private var checkpoint: TaskCheckpoint,
    val isResuming: Boolean,
) {
    val fileRecoveryAction: FileRecoveryAction? get() = checkpoint.fileRecoveryAction?.let(FileRecoveryAction::valueOf)
    val phase: DownloadStage get() = DownloadStage.valueOf(checkpoint.phase)
    var resumingPhase: String? = if (isResuming) checkpoint.phase else null
    private val identities = identities(request)
    private val completed = checkpoint.completedFiles.toMutableList()
    var pendingFilePublication = checkpoint.pendingFilePublication
        private set

    fun prepareFilePublication(target: File, bytes: Long) {
        check(pendingFilePublication == null)
        ControlledDirectory(directory).checkSafe(target)
        check(!target.exists())
        pendingFilePublication = PendingFilePublication(identities.getValue("file"), target.relativeTo(directory).invariantSeparatorsPath, bytes)
    }

    fun progress(): DownloadProgress? = if (checkpoint.downloadedBytes > 0 || checkpoint.totalBytes != null)
        DownloadProgress("downloading", checkpoint.totalBytes?.takeIf { it > 0 }?.let {
            checkpoint.downloadedBytes * 100.0 / it
        }, checkpoint.downloadedBytes, checkpoint.totalBytes, 0.0, null, null) else null

    fun file(role: String): File? = completed.singleOrNull { it.role == role }?.let { File(directory, it.relativePath) }

    fun streamDirectory(merge: Boolean): File = protect {
        val guard = ControlledDirectory(directory)
        val target = if (merge) guard.child(directory, ".parts") else directory
        check(target.mkdirs() || target.isDirectory)
        guard.checkSafe(target)
        target
    }

    fun record(role: String, file: File) = protect {
        ControlledDirectory(directory).checkSafe(file)
        check(file.path.startsWith(directory.path + File.separator) && file.isFile && (file.length() > 0 || role == "file" && request.route is DownloadRoute.HttpFile &&
            request.route.metadata.kind == com.garyapp.ytdl.core.files.FileKind.Other))
        val entry = CompletedTaskFile(role, identities.getValue(role), file.relativeTo(directory).invariantSeparatorsPath, file.length())
        completed.removeAll { it.role == role }
        completed += entry
        if (role == "file") pendingFilePublication = null
    }

    fun forget(vararg roles: String, onlyForMerged: Boolean = false) {
        if (!onlyForMerged || completed.any { it.role == "merged" }) completed.removeAll { it.role in roles }
    }

    fun save(stage: DownloadStage, progress: DownloadProgress?, reason: DownloadStopReason?, recoveryAction: FileRecoveryAction? = null) = protect {
        checkpoint = TaskCheckpoint(request.taskId, stage.name, reason?.name,
            progress?.downloadedBytes ?: 0, progress?.totalBytes, completedFiles = completed.toList(), fileRecoveryAction = recoveryAction?.name, pendingFilePublication = pendingFilePublication)
        workspace.saveCheckpoint(request.taskId, checkpoint).getOrThrow()
    }

    companion object {
        fun open(workspace: TaskWorkspace, request: DownloadRequest): PipelineCheckpoint = protect {
            val directory = workspace.directory(request.taskId, true)
            val snapshot = File(directory, "checkpoint.bin")
            val existing = snapshot.exists()
            val checkpoint = if (existing) workspace.loadCheckpoint(request.taskId).getOrThrow() else {
                // Never infer a completed or partial payload from arbitrary pre-existing files.
                check(directory.listFiles().orEmpty().isEmpty())
                TaskCheckpoint(request.taskId, DownloadStage.Waiting.name).also {
                    workspace.saveCheckpoint(request.taskId, it).getOrThrow()
                }
            }
            check(DownloadStage.entries.any { it.name == checkpoint.phase })
            check(checkpoint.stopReason == null || DownloadStopReason.entries.any { it.name == checkpoint.stopReason })
            val allowed = identities(request)
            checkpoint.pendingFilePublication?.let { check(it.identity == allowed["file"]) }
            check(checkpoint.completedFiles.map { it.role }.distinct().size == checkpoint.completedFiles.size)
            checkpoint.completedFiles.forEach {
                check(allowed[it.role] == it.formatId)
                if (it.sizeBytes == 0L) check(it.role == "file" && request.route is DownloadRoute.HttpFile &&
                    request.route.metadata.kind == com.garyapp.ytdl.core.files.FileKind.Other)
            }
            check(checkpoint.fileRecoveryAction == null || request.route is DownloadRoute.HttpFile)
            val roles = checkpoint.completedFiles.map { it.role }.toSet()
            val mainRole = if (request.route is DownloadRoute.MergeRequired) "merged" else allowed.keys.first()
            if (checkpoint.phase == DownloadStage.Completed.name) {
                check(mainRole in roles && allowed.keys.filter { it.startsWith("subtitle_") }.all { it in roles })
            }
            if (checkpoint.phase == DownloadStage.Merging.name && "merged" !in roles) {
                check(request.route is DownloadRoute.MergeRequired && "video" in roles && "audio" in roles)
            }
            val guard = ControlledDirectory(directory)
            fun inspect(file: File) {
                guard.checkSafe(file)
                if (file.isDirectory) checkNotNull(file.listFiles()).forEach(::inspect)
                else check(file.isFile)
            }
            inspect(directory)
            PipelineCheckpoint(workspace, request, directory, checkpoint, existing)
        }

        private fun httpFileIdentity(route: DownloadRoute.HttpFile): String {
            val m = route.metadata
            val fields = listOf(m.sourceUrl, m.finalUrl, m.fileName, m.mimeType, m.totalBytes?.toString(),
                m.etag, m.lastModified, m.kind.name, m.resumeSupport.name)
            val bytes = fields.joinToString("") { if (it == null) "-1:" else "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }

        private fun identities(request: DownloadRequest): Map<String, String> = buildMap {
            when (val route = request.route) {
                is DownloadRoute.HttpFile -> put("file", httpFileIdentity(route))
                is DownloadRoute.DirectSingleFile -> put("media", route.formatId)
                is DownloadRoute.VideoOnly -> put("video", route.videoFormatId)
                is DownloadRoute.AudioOnly -> put("audio", route.audioFormatId)
                is DownloadRoute.MergeRequired -> {
                    put("video", route.videoFormatId)
                    put("audio", route.audioFormatId)
                    put("merged", "${route.videoFormatId.length}-${route.videoFormatId}-${route.audioFormatId}")
                }
            }
            request.selectedSubtitles.forEachIndexed { index, subtitle ->
                put("subtitle_$index", "${subtitle.source.pythonValue}:${subtitle.language}:${subtitle.ext}")
            }
        }

        private inline fun <T> protect(action: () -> T): T = try { action() }
        catch (error: Exception) { throw PipelineCheckpointException(error) }
    }
}
