package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import java.io.File
import java.net.URI

enum class DownloadStage {
    Idle,
    Analyzing,
    Waiting,
    Pausing,
    Paused,
    WaitingForNetwork,
    Resuming,
    DownloadingFile,
    DownloadingVideo,
    DownloadingAudio,
    DownloadingSubtitles,
    Merging,
    Exporting,
    Completed,
    Failed,
    Canceled,
}

enum class FileRecoveryAction { Retry, ConfirmRestart, ChooseFileName }

enum class DownloadOutputKind {
    File,
    Media,
    Subtitle,
}

data class DownloadOutputFile(
    val kind: DownloadOutputKind,
    val path: String,
    val bytesWritten: Long,
    val appPrivateRootPath: String? = null,
    val externalDocumentUri: String? = null,
    val mimeType: String? = null,
)

data class DownloadTaskState(
    val stage: DownloadStage,
    val request: DownloadRequest? = null,
    val outputs: List<DownloadOutputFile> = emptyList(),
    val progress: DownloadProgress? = null,
    val errorMessage: String? = null,
    val stopReason: DownloadStopReason? = null,
    val statusMessage: String? = null,
    val fileRecoveryAction: FileRecoveryAction? = null,
) {
    fun atStage(nextStage: DownloadStage): DownloadTaskState {
        return copy(stage = nextStage, progress = null, errorMessage = null, fileRecoveryAction = null)
    }

    fun withProgress(progress: DownloadProgress): DownloadTaskState {
        return copy(progress = progress)
    }

    fun failed(
        message: String,
        outputs: List<DownloadOutputFile> = this.outputs,
    ): DownloadTaskState {
        return copy(stage = DownloadStage.Failed, outputs = outputs, progress = null, errorMessage = message)
    }

    fun canceled(): DownloadTaskState {
        return copy(stage = DownloadStage.Canceled, progress = null, errorMessage = null, stopReason = DownloadStopReason.UserCancel, fileRecoveryAction = null)
    }

    fun completeWith(outputs: List<DownloadOutputFile>): Result<DownloadTaskState> {
        return runCatching {
            val currentRequest = request
                ?: throw DownloadStateException("缺少下载请求，不能标记完成。")
            validateFinalOutputs(currentRequest, outputs)
            copy(
                stage = DownloadStage.Completed,
                fileRecoveryAction = null,
                outputs = outputs,
                progress = null,
                errorMessage = null,
            )
        }
    }

    fun withExternalDocumentUris(documentUris: List<String>): Result<DownloadTaskState> {
        return runCatching {
            require(stage == DownloadStage.Completed) { "只有完成任务可以标记外部输出。" }
            require(documentUris.size == outputs.size) { "外部输出数量与私有输出不一致。" }
            documentUris.forEach { rawUri ->
                val uri = URI(rawUri)
                require(uri.scheme.equals("content", ignoreCase = true) && !uri.rawAuthority.isNullOrBlank()) {
                    "外部输出必须是 content URI。"
                }
            }
            copy(
                outputs = outputs.zip(documentUris) { output, documentUri ->
                    output.copy(externalDocumentUri = documentUri)
                },
            )
        }
    }

    private fun validateFinalOutputs(
        request: DownloadRequest,
        outputs: List<DownloadOutputFile>,
    ) {
        if (request.route is DownloadRoute.HttpFile) {
            val expectedKind = if (request.route.metadata.kind == com.garyapp.ytdl.core.files.FileKind.Other)
                DownloadOutputKind.File else DownloadOutputKind.Media
            require(request.selectedSubtitles.isEmpty() && outputs.size == 1 && outputs.single().kind == expectedKind) {
                "文件输出尚未生成。"
            }
            val output = outputs.single()
            val file = File(output.path)
            require(file.isFile && file.length() == output.bytesWritten &&
                (output.bytesWritten > 0 || expectedKind == DownloadOutputKind.File && output.bytesWritten == 0L)) {
                "文件输出不存在或长度不正确。"
            }
            return
        }
        require(outputs.none { it.kind == DownloadOutputKind.File }) { "媒体任务输出类型不可用。" }
        val mediaOutputs = outputs.filter { it.kind == DownloadOutputKind.Media }
        if (mediaOutputs.size != 1) {
            throw DownloadStateException("媒体输出尚未生成，不能标记完成。")
        }

        val subtitleOutputs = outputs.filter { it.kind == DownloadOutputKind.Subtitle }
        if (subtitleOutputs.size != request.selectedSubtitles.size) {
            throw DownloadStateException("字幕输出尚未全部生成，不能标记完成。")
        }

        outputs.forEach { output ->
            val file = File(output.path)
            if (!file.isFile || file.length() <= 0L || output.bytesWritten <= 0L) {
                throw DownloadStateException("输出文件不存在或为空，不能标记完成。")
            }
        }
    }

    companion object {
        fun idle(): DownloadTaskState = DownloadTaskState(stage = DownloadStage.Idle)

        fun analyzing(): DownloadTaskState = DownloadTaskState(stage = DownloadStage.Analyzing)

        fun waiting(request: DownloadRequest): DownloadTaskState {
            return DownloadTaskState(
                stage = DownloadStage.Waiting,
                request = request,
            )
        }
    }
}

class DownloadStateException(
    message: String,
) : IllegalStateException(message)
