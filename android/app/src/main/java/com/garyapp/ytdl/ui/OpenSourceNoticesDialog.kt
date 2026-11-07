package com.garyapp.ytdl.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal val CaptainFlashOpenSourceNotices = """
FFmpeg 8.0.1
LGPL 2.1 或更新版本 · HLS 无重编码封装
https://ffmpeg.org/releases/ffmpeg-8.0.1.tar.xz

yt-dlp
Unlicense；部分附带代码遵循各自许可证
https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE

Chaquopy
MIT License
https://github.com/chaquo/chaquopy/blob/master/LICENSE.txt

Python
Python Software Foundation License
https://docs.python.org/3/license.html

AndroidX（Compose、Room、Activity、Core、Lifecycle）
Apache License 2.0
https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt

Kotlin 标准库
Apache License 2.0
https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt
""".trimIndent()

@Composable
internal fun OpenSourceNoticesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("captainflash-open-source-dialog"),
        onDismissRequest = onDismiss,
        title = { Text(appText("第三方开源许可", "Open-source licenses")) },
        text = {
            SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                Text(appText(CaptainFlashOpenSourceNotices,
                    CaptainFlashOpenSourceNotices
                        .replace("LGPL 2.1 或更新版本 · HLS 无重编码封装", "LGPL 2.1 or later · HLS remuxing")
                        .replace("Unlicense；部分附带代码遵循各自许可证", "Unlicense. Some bundled code has separate licenses.")
                        .replace("AndroidX（Compose、Room、Activity、Core、Lifecycle）", "AndroidX (Compose, Room, Activity, Core, Lifecycle)")
                        .replace("Kotlin 标准库", "Kotlin standard library")))
            }
        },
        confirmButton = { SettingsActionButton(onClick = onDismiss) { Text(appText("关闭", "Close")) } },
    )
}
