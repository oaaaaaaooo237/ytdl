package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadStage

fun buildAppliedDownloadRequest(
    url: String,
    analysis: VideoAnalysis?,
    appliedSelection: FormatSelection,
): Result<DownloadRequest> {
    return runCatching {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            throw IllegalArgumentException("请先输入公开视频页面地址。")
        }
        val currentAnalysis = analysis
            ?: throw IllegalStateException("请先分析视频。")

        DownloadRequest.fromAnalysis(
            url = normalizedUrl,
            analysis = currentAnalysis,
            selection = appliedSelection,
            selectedSubtitles = emptyList(),
        ).getOrThrow()
    }
}

fun userVisibleDownloadStatus(stage: DownloadStage): String {
    return when (stage) {
        DownloadStage.Idle -> "空闲"
        DownloadStage.Analyzing -> "分析中"
        DownloadStage.Waiting -> "等待中"
        DownloadStage.DownloadingVideo -> "下载视频"
        DownloadStage.DownloadingAudio -> "下载音频"
        DownloadStage.DownloadingSubtitles -> "下载字幕"
        DownloadStage.Merging -> "原生合并"
        DownloadStage.Exporting -> "导出待接入"
        DownloadStage.Completed -> "下载完成"
        DownloadStage.Failed -> "下载失败"
        DownloadStage.Canceled -> "已取消"
    }
}
