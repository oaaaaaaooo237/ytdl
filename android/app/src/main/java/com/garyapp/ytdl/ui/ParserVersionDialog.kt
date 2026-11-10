package com.garyapp.ytdl.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.garyapp.ytdl.core.ytdlp.ParserVersionInfo

@Composable
internal fun ParserVersionDialog(
    versions: List<ParserVersionInfo>,
    runningVersion: String,
    latestVersion: String?,
    busy: Boolean,
    checking: Boolean,
    status: String,
    message: String?,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val availableVersion = latestVersion?.takeUnless { latest -> versions.any { it.version == latest } }
    var candidate by remember(versions, availableVersion) {
        mutableStateOf(versions.firstOrNull { it.isSelected }?.version)
    }
    val chosen = versions.firstOrNull { it.version == candidate }
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("ytdl-parser-status-dialog"),
        onDismissRequest = onClose,
        title = { Text(appText("解析器版本", "Parser versions")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(appText("本进程实际版本：yt-dlp $runningVersion", "Running: yt-dlp $runningVersion"),
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(appText("版本", "Version"), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text(appText("来源", "Source"), Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
                    Text(appText("选择", "Select"), Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium)
                }
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                    versions.forEach { version ->
                        ParserVersionRow(version.version,
                            if (version.isBuiltIn) appText("内置", "Built-in") else appText("已下载", "Downloaded"),
                            candidate == version.version, !busy, onSelect = { candidate = version.version })
                    }
                    availableVersion?.let { version ->
                        ParserVersionRow(version, appText("未下载", "Available"), false, !busy,
                            onSelect = {}, onDownload = { onDownload(version) })
                    }
                }
                Text(if (busy) appText("正在处理解析器…", "Updating parser…") else status,
                    style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParserActionButton("select", appText("选择解析器", "Select parser"), ParserConfirmIcon,
                        !busy && candidate != null && chosen?.isSelected != true, Modifier.weight(1f)) {
                        chosen?.let { onSelect(it.version) }
                    }
                    ParserActionButton("delete", appText("删除解析器", "Delete parser"), ParserDeleteIcon,
                        !busy && chosen?.canDelete == true, Modifier.weight(1f), true) {
                        chosen?.let { onDelete(it.version) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ParserActionButton("refresh", appText("重新检查", "Check again"), ParserRefreshIcon,
                        !busy && !checking, Modifier.weight(1f), onClick = onRefresh)
                    ParserActionButton("close", appText("关闭", "Close"), ParserExitIcon,
                        true, Modifier.weight(1f), onClick = onClose)
                }
            }
        },
    )
}

@Composable
private fun ParserVersionRow(version: String, source: String, selected: Boolean, enabled: Boolean,
    onSelect: () -> Unit, onDownload: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .then(if (onDownload == null) Modifier.selectable(selected = selected, enabled = enabled,
            role = Role.RadioButton, onClick = onSelect) else Modifier)
        .testTag("ytdl-parser-row-$version"), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onDownload != null) Text(appText("新", "New"), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall)
            Text(version, style = MaterialTheme.typography.bodyMedium)
        }
        Text(source, Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
        if (onDownload == null) RadioButton(selected, null, Modifier.size(48.dp), enabled)
        else IconButton(onDownload, Modifier.size(48.dp).testTag("ytdl-parser-download-$version"), enabled) {
            Icon(ParserDownloadIcon, appText("下载解析器 $version", "Download parser $version"), Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ParserActionButton(tag: String, label: String, icon: ImageVector, enabled: Boolean,
    modifier: Modifier, destructive: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier.height(48.dp).testTag("ytdl-parser-$tag"), enabled,
        shape = RectangleShape, contentPadding = PaddingValues(12.dp)) {
        Icon(icon, label, Modifier.size(24.dp),
            tint = if (enabled && destructive) MaterialTheme.colorScheme.error else LocalContentColor.current)
    }
}

private fun parserIcon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
    }.build()

private val ParserConfirmIcon = parserIcon("ParserConfirm") {
    moveTo(5f, 12f); lineTo(10f, 17f); lineTo(19f, 7f)
}
private val ParserDeleteIcon = parserIcon("ParserDelete") {
    moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f)
}
private val ParserRefreshIcon = parserIcon("ParserRefresh") {
    moveTo(20f, 10f); curveTo(19f, 5f, 13f, 2f, 8f, 5f)
    curveTo(3f, 8f, 3f, 15f, 7f, 18f); curveTo(11f, 21f, 17f, 20f, 19f, 16f)
    moveTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f)
}
private val ParserExitIcon = parserIcon("ParserExit") {
    moveTo(10f, 4f); lineTo(4f, 4f); lineTo(4f, 20f); lineTo(10f, 20f)
    moveTo(10f, 12f); lineTo(21f, 12f); moveTo(17f, 8f); lineTo(21f, 12f); lineTo(17f, 16f)
}
private val ParserDownloadIcon = parserIcon("ParserDownload") {
    moveTo(12f, 3f); lineTo(12f, 16f); moveTo(7f, 11f); lineTo(12f, 16f); lineTo(17f, 11f)
    moveTo(5f, 18f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 18f)
}
