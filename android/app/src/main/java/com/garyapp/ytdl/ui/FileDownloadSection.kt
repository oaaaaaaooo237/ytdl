package com.garyapp.ytdl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garyapp.ytdl.core.files.FileKind
import com.garyapp.ytdl.core.files.FileMetadata
import com.garyapp.ytdl.core.files.ResumeSupport
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.HttpFileConflictPolicy
import com.garyapp.ytdl.ui.theme.LocalYtdlAppPalette
import java.util.Locale

internal data class FileDownloadSelection(
    val metadata: FileMetadata,
    val fileName: String = metadata.fileName,
    val conflictPolicy: HttpFileConflictPolicy = HttpFileConflictPolicy.AutoRename,
) {
    val route: DownloadRoute.HttpFile?
        get() = runCatching { DownloadRoute.HttpFile(metadata, fileName, conflictPolicy) }.getOrNull()

    val extensionChanged: Boolean
        get() = !fileName.extension().equals(metadata.fileName.extension(), ignoreCase = true)

    val formatLabel: String
        get() = metadata.fileName.extension().uppercase(Locale.ROOT).ifBlank { metadata.mimeType.orEmpty() }

    fun request(target: StorageTarget): DownloadRequest? = route?.let {
        DownloadRequest(
            url = metadata.sourceUrl,
            title = fileName,
            route = it,
            formatSummary = formatLabel,
            storageTarget = target,
        )
    }

    private fun String.extension(): String = if (lastIndexOf('.') > 0) substringAfterLast('.') else ""
}

internal fun FileMetadata.isStreamManifest(): Boolean = when (mimeType) {
    "application/vnd.apple.mpegurl", "application/mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl",
    "application/dash+xml" -> true
    "application/xml", "text/xml" -> fileName.endsWith(".mpd", ignoreCase = true)
    "application/octet-stream", "text/plain", null ->
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("m3u8", "mpd")
    else -> false
}

internal fun FileKind.identificationLabel(): String = when (this) {
    FileKind.Video -> "媒体 · 视频直链"
    FileKind.Audio -> "媒体 · 音频直链"
    FileKind.Other -> "普通文件"
}

@Composable
internal fun FileDownloadSection(
    selection: FileDownloadSelection,
    onSelectionChange: (FileDownloadSelection) -> Unit,
) {
    val palette = LocalYtdlAppPalette.current
    val metadata = selection.metadata
    val size = metadata.totalBytes?.let(::formatBytes) ?: appText("大小未知", "Unknown size")
    val resume = when (metadata.resumeSupport) {
        ResumeSupport.Supported -> appText("支持断点续传", "Resume supported")
        ResumeSupport.Unknown -> appText("继续下载时验证服务器是否支持续传", "Resume support will be checked when you resume")
        ResumeSupport.Unavailable -> appText("服务器未提供可用于续传的验证信息", "This server did not provide the information needed to resume")
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = palette.cardBackground, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(metadata.fileName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(listOf(selection.formatLabel, size).filter { it.isNotBlank() }.joinToString(" · "),
                    color = palette.softText, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (metadata.kind == FileKind.Other) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(appText("保存文件名", "File name"), color = palette.softText, style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = selection.fileName,
                    onValueChange = { onSelectionChange(selection.copy(fileName = it)) },
                    modifier = Modifier.fillMaxWidth().testTag("ytdl-download-filename"),
                    singleLine = true,
                    shape = RectangleShape,
                    isError = selection.route == null || selection.extensionChanged,
                )
                when {
                    selection.route == null -> Text(appText("请输入有效的文件名。", "Enter a valid file name."), color = MaterialTheme.colorScheme.error)
                    selection.extensionChanged -> Text(
                        appText("扩展名已更改，可能影响打开方式。", "Changing the extension can affect how the file opens."),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("ytdl-download-extension-warning"),
                    )
                }
            }
        }
        Surface(color = palette.segmentedBackground, modifier = Modifier.fillMaxWidth()) {
            Text(resume, color = palette.titleText, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun FileConflictChoice(
    selection: FileDownloadSelection,
    onSelectionChange: (FileDownloadSelection) -> Unit,
) {
    val labels = listOf(appText("自动编号，保留两个文件", "Auto-rename and keep both files"), appText("每次询问", "Ask each time"))
    val policies = HttpFileConflictPolicy.entries
    DownloadChoice(appText("重名处理", "If the file already exists"), labels[policies.indexOf(selection.conflictPolicy)], "ytdl-download-conflict") { dismiss ->
        policies.forEachIndexed { index, policy ->
            ChoiceItem(labels[index], "ytdl-file-conflict-${policy.name}") {
                dismiss()
                onSelectionChange(selection.copy(conflictPolicy = policy))
            }
        }
    }
}

@Composable
internal fun DownloadControl(
    label: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    actionLabel: String = "",
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalYtdlAppPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = palette.softText, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
            shape = RectangleShape,
            border = BorderStroke(1.dp, palette.borderColor),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selected) palette.downloadAccent else palette.cardBackground,
                contentColor = if (selected) palette.primaryActionText else palette.titleText,
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (actionLabel.isNotBlank()) Text(actionLabel, color = if (selected) palette.primaryActionText else palette.formatAccent, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun DownloadChoice(
    label: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    items: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        DownloadControl(label, value, tag, actionLabel = "▾", selected = expanded, onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RectangleShape,
            containerColor = LocalYtdlAppPalette.current.cardBackground,
        ) {
            items { expanded = false }
        }
    }
}

@Composable
internal fun ChoiceItem(label: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(tag),
    )
}
