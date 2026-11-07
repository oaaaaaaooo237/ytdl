package com.garyapp.ytdl.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.garyapp.ytdl.core.settings.AppLanguage
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.FileRecoveryAction
import com.garyapp.ytdl.download.ResumeTaskItem
import com.garyapp.ytdl.download.TaskOutputType

internal data class TaskFilters(
    val statuses: Set<TaskStatus> = emptySet(),
    val types: Set<TaskOutputType> = emptySet(),
) {
    fun matches(status: TaskStatus, type: TaskOutputType, text: String, query: String): Boolean =
        (statuses.isEmpty() || status in statuses) && (types.isEmpty() || type in types) &&
            text.contains(query.trim(), ignoreCase = true)
}

internal val TaskStatus.label: String
    get() = when (this) {
        TaskStatus.InProgress -> "进行中"
        TaskStatus.Waiting -> "等待中"
        TaskStatus.Paused -> "已暂停"
        TaskStatus.Completed -> "已完成"
        TaskStatus.Failed -> "失败"
        TaskStatus.Canceled -> "已取消"
    }

private val TaskOutputType.label: String
    get() = when (this) {
        TaskOutputType.Video -> "视频"
        TaskOutputType.Audio -> "音频"
        TaskOutputType.Other -> "其他文件"
    }

internal fun TaskStatus.localized(language: AppLanguage): String = language.pick(label, when (this) {
    TaskStatus.InProgress -> "In progress"
    TaskStatus.Waiting -> "Queued"
    TaskStatus.Paused -> "Paused"
    TaskStatus.Completed -> "Completed"
    TaskStatus.Failed -> "Failed"
    TaskStatus.Canceled -> "Canceled"
})

private fun TaskOutputType.localized(language: AppLanguage): String = language.pick(label, when (this) {
    TaskOutputType.Video -> "Video"
    TaskOutputType.Audio -> "Audio"
    TaskOutputType.Other -> "Other files"
})

@Composable
internal fun TaskFilterBar(filters: TaskFilters, onChange: (TaskFilters) -> Unit) {
    val language = LocalAppLanguage.current
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()).testTag("ytdl-task-filters"),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskFilterChip(appText("全部", "All"), "all", filters == TaskFilters()) { onChange(TaskFilters()) }
            TaskStatus.entries.forEach { status ->
                TaskFilterChip(status.localized(language), status.name, status in filters.statuses) {
                    val selected = filters.statuses
                    onChange(filters.copy(statuses = if (status in selected) selected - status else selected + status))
                }
            }
            TaskOutputType.entries.forEach { type ->
                TaskFilterChip(type.localized(language), type.name, type in filters.types) {
                    val selected = filters.types
                    onChange(filters.copy(types = if (type in selected) selected - type else selected + type))
                }
            }
        }
        Text(
            filters.statuses.joinToString(language.pick("、", ", ")) { it.localized(language) }.ifEmpty { language.pick("全部状态", "All statuses") } + " · " +
                filters.types.joinToString(language.pick("、", ", ")) { it.localized(language) }.ifEmpty { language.pick("全部类型", "All types") } + language.pick(" · 左右滑动筛选", " · Swipe to see filters"),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TaskFilterChip(label: String, id: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) },
        shape = RectangleShape, modifier = Modifier.testTag("ytdl-task-filter-$id"))
}

@Composable
internal fun FileRecoveryDialog(task: ResumeTaskItem, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    val route = task.state.request?.route as? DownloadRoute.HttpFile ?: return
    val rename = task.state.fileRecoveryAction == FileRecoveryAction.ChooseFileName
    var name by remember(task.taskId) { mutableStateOf(route.fileName) }
    val valid = !rename || (name != route.fileName && runCatching { route.copy(fileName = name) }.isSuccess)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rename) appText("文件名已存在", "File name already exists") else appText("确认重新下载", "Download again?")) },
        text = {
            if (rename) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(appText("新的文件名", "New file name")) },
                    modifier = Modifier.width(300.dp).testTag("ytdl-recovery-filename"), singleLine = true,
                    shape = RectangleShape)
            } else {
                Text(appText("当前无法安全续传。确认后会重新识别源文件，识别成功后从头下载，原进度将被清除。", "This download cannot safely resume. The app will check the source again and restart the download if the check succeeds."))
            }
        },
        confirmButton = {
            SettingsActionButton(enabled = valid, onClick = { onConfirm(name.takeIf { rename }) },
                modifier = Modifier.testTag("ytdl-recovery-confirm")) { Text(if (rename) appText("保存并继续", "Save and resume") else appText("重新下载", "Download again")) }
        },
        dismissButton = { SettingsActionButton(onClick = onDismiss) { Text(appText("返回", "Back")) } },
    )
}


internal enum class HistoryFileAction(val label: String) {
    Open("打开"), Share("分享"), Export("导出"),
}

internal data class HistoryOutputReference(
    val uri: String,
    val label: String,
    val mimeType: String? = null,
    val isSubtitle: Boolean = false,
)
internal data class VerifiedHistoryOutput(val reference: HistoryOutputReference, val target: HistoryOutputTarget)

internal fun HistoryUiItem.outputReferences(): List<HistoryOutputReference> = buildList {
    if (hasOutput) add(HistoryOutputReference(outputUri, "主文件", outputMimeType))
    subtitleOutputUris.forEachIndexed { index, uri ->
        add(HistoryOutputReference(uri, if (isTorrent) "文件 ${index + 2}" else "字幕 ${index + 1}", isSubtitle = !isTorrent))
    }
}.distinctBy { it.uri }
