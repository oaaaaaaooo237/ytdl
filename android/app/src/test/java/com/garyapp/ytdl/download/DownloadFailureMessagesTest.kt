package com.garyapp.ytdl.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFailureMessagesTest {
    @Test fun emptySiteFormatsAreNotReportedAsFileProcessingFailure() {
        val message = "网站本次未返回可下载的视频格式，请稍后重新分析。"
        assertEquals(message, DownloadFailureMessages.fromException(
            com.garyapp.ytdl.core.ytdlp.YtdlpDownloadException(
                com.garyapp.ytdl.core.ytdlp.AnalysisErrorCategory.Unsupported, message)))
    }

    @Test
    fun storageFailureUsesActionableStorageMessage() {
        assertEquals(
            "设备存储空间不足，请清理空间后重试。",
            DownloadFailureMessages.fromErrorText("设备存储空间不足，无法合并视频和音频。"),
        )
    }
}
