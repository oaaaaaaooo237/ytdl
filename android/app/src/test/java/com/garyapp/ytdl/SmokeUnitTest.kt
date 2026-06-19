package com.garyapp.ytdl

import com.garyapp.ytdl.ui.ytdlNavigationDestinations
import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeUnitTest {
    @Test
    fun navigationLabelsExposeFiveAndroidTabs() {
        assertEquals(
            listOf("下载", "格式", "队列", "历史", "设置"),
            ytdlNavigationDestinations().map { it.label },
        )
    }
}
