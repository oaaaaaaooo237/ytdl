package com.garyapp.ytdl

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue

object RealYoutubeTestUrls {
    const val PRIMARY_VIDEO = "https://youtu.be/lcFR2mFSmSs?si=FqJ3ZTdKRq6NAt6G"
    const val PRIMARY_VIDEO_ID = "lcFR2mFSmSs"
    const val SECONDARY_VIDEO = "https://youtu.be/auNezUzwCZg?si=wBLppn7aAimNzXTW"
    const val SECONDARY_VIDEO_ID = "auNezUzwCZg"
    const val SHORTS = "https://youtube.com/shorts/jWTrleK2_MU?si=1hOoGpC7JM__M4Sf"
    const val SHORTS_ID = "jWTrleK2_MU"
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
