package com.garyapp.ytdl.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal val CaptainFlashOpenSourceNotices = """
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
        title = { Text("开源声明") },
        text = {
            SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                Text(CaptainFlashOpenSourceNotices)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
