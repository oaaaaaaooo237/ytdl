package com.garyapp.ytdl.download

import android.content.Context
import java.io.File

internal class TorrentDownloadPipeline(context: Context) {
    fun run(request: DownloadRequest, outputDirectory: File, cancellation: DownloadCancellation,
            onStateChanged: (DownloadTaskState) -> Unit): DownloadPipelineResult {
        val state = DownloadTaskState.waiting(request).failed("请安装 Torrent 版以下载种子。")
        onStateChanged(state)
        return DownloadPipelineResult(state, emptyList())
    }
}

internal fun restoreLegacyTorrentDownloads(context: Context) = Unit
