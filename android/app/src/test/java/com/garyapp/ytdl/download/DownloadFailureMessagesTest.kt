package com.garyapp.ytdl.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFailureMessagesTest {
    @Test
    fun storageFailureUsesActionableStorageMessage() {
        assertEquals(
            "设备存储空间不足，请清理空间后重试。",
            DownloadFailureMessages.fromErrorText("设备存储空间不足，无法合并视频和音频。"),
        )
    }
}
