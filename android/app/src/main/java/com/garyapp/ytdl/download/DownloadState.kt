package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import java.io.File

enum class DownloadStage {
    Idle,
    Analyzing,
    Waiting,
    DownloadingVideo,
    DownloadingAudio,
    DownloadingSubtitles,
    Merging,
    Exporting,
    Completed,
    Failed,
    Canceled,
}

enum class DownloadOutputKind {
    Media,
    Subtitle,
}

data class DownloadOutputFile(
    val kind: DownloadOutputKind,
    val path: String,
    val bytesWritten: Long,
)

data class DownloadTaskState(
    val stage: DownloadStage,
    val request: DownloadRequest? = null,
    val outputs: List<DownloadOutputFile> = emptyList(),
    val progress: DownloadProgress? = null,
    val errorMessage: String? = null,
) {
    fun atStage(nextStage: DownloadStage): DownloadTaskState {
        return copy(stage = nextStage, progress = null, errorMessage = null)
    }

    fun withProgress(progress: DownloadProgress): DownloadTaskState {
        return copy(progress = progress)
    }

    fun failed(message: String): DownloadTaskState {
        return copy(stage = DownloadStage.Failed, progress = null, errorMessage = message)
    }

    fun canceled(): DownloadTaskState {
        return copy(stage = DownloadStage.Canceled, progress = null, errorMessage = null)
    }

    fun completeWith(outputs: List<DownloadOutputFile>): Result<DownloadTaskState> {
        return runCatching {
            val currentRequest = request
                ?: throw DownloadStateException("缺少下载请求，不能标记完成。")
            validateFinalOutputs(currentRequest, outputs)
            copy(
                stage = DownloadStage.Completed,
                outputs = outputs,
                progress = null,
                errorMessage = null,
            )
        }
    }

    private fun validateFinalOutputs(
        request: DownloadRequest,
        outputs: List<DownloadOutputFile>,
    ) {
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
