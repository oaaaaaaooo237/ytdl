package com.garyapp.ytdl

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

object RealYoutubeTestUrls {
    const val PRIMARY_VIDEO = "https://www.youtube.com/watch?v=PqQNXB6hhUs"
    const val PRIMARY_VIDEO_ID = "PqQNXB6hhUs"
    const val SECONDARY_VIDEO = "https://www.youtube.com/watch?v=svoD582Pas4"
    const val SECONDARY_VIDEO_ID = "svoD582Pas4"
    const val SHORTS = "https://www.youtube.com/shorts/oXFad1nt6v0"
    const val SHORTS_ID = "oXFad1nt6v0"
}

object RealYoutubeTestGate {
    fun assumeRealYoutubeEnabled() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "真实 YouTube connected 测试默认跳过；按 QA 间隔规则单项运行时传入 realYoutube=true。",
            args.getString("realYoutube") == "true",
        )
    }

    fun assumeRealSubtitleDownloadEnabled() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "真实字幕下载测试已暂停；只有用户恢复后才传入 realYoutubeSubtitle=true。",
            args.getString("realYoutubeSubtitle") == "true",
        )
    }
}
