package com.garyapp.ytdl.download

import com.garyapp.ytdl.data.HistoryDao

class DownloadHistoryRecorder(
    private val historyDao: HistoryDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun recordTerminal(
        state: DownloadTaskState,
        formatSummary: String = state.request?.historyFormatSummary().orEmpty(),
    ): Result<Long> {
        return runCatching {
            when (state.stage) {
                DownloadStage.Completed,
                DownloadStage.Failed,
                DownloadStage.Canceled,
                -> historyDao.insertFromTaskState(
                    state,
                    formatSummary.ifBlank { "下载任务" },
                    clock(),
                )
                else -> throw DownloadStateException("只有终态任务可以写入历史。")
            }
        }
    }
}

fun applyHistoryRecordingResult(
    state: DownloadTaskState,
    recordResult: Result<Long>,
): DownloadTaskState {
    if (recordResult.isSuccess) {
        return state
    }
    return state.failed(DownloadFailureMessages.historyWriteFailed())
}

private fun DownloadRequest.historyFormatSummary(): String {
    val routeSummary = when (val currentRoute = route) {
        is DownloadRoute.DirectSingleFile -> "格式 ${currentRoute.formatId}"
        is DownloadRoute.VideoOnly -> "仅视频 ${currentRoute.videoFormatId}"
        is DownloadRoute.AudioOnly -> "仅音频 ${currentRoute.audioFormatId}"
        is DownloadRoute.MergeRequired -> "视频 ${currentRoute.videoFormatId} + 音频 ${currentRoute.audioFormatId}"
    }
    val subtitleSummary = if (selectedSubtitles.isEmpty()) {
        ""
    } else {
        " + 字幕 ${selectedSubtitles.joinToString { "${it.language}.${it.ext}" }}"
    }
    return routeSummary + subtitleSummary
}
