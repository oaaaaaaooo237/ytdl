package com.garyapp.ytdl.ui

import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.download.ResumeTaskItem
import com.garyapp.ytdl.download.TaskOutputType
import com.garyapp.ytdl.download.outputType
import com.garyapp.ytdl.download.taskOutputTypeFromMime
import com.garyapp.ytdl.download.taskOutputTypeFromStored
import com.garyapp.ytdl.storage.ExportController
import java.net.URI

enum class TaskStatus { InProgress, Waiting, Paused, Completed, Failed, Canceled }

data class UnifiedTask(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val type: TaskOutputType,
    val state: DownloadTaskState? = null,
    val recovery: ResumeTaskItem? = null,
    val history: HistoryUiItem? = null,
)

fun matchesTaskFilter(task: UnifiedTask, statuses: Set<TaskStatus>, types: Set<TaskOutputType>): Boolean =
    (statuses.isEmpty() || task.status in statuses) && (types.isEmpty() || task.type in types)

/** Live state wins while actionable. Persisted terminal rows carry the history actions. */
fun unifiedTasks(
    liveStates: List<DownloadTaskState>,
    recovered: List<ResumeTaskItem>,
    history: List<HistoryItemEntity>,
): List<UnifiedTask> = unifiedUiTasks(liveStates, recovered, historyUiItemsFromRows(history))

internal fun unifiedUiTasks(
    liveStates: List<DownloadTaskState>,
    recovered: List<ResumeTaskItem>,
    history: List<HistoryUiItem>,
): List<UnifiedTask> {
    val rows = linkedMapOf<String, UnifiedTask>()
    for (state in liveStates) {
        val request = state.request ?: continue
        val status = state.stage.taskStatus() ?: continue
        rows[request.taskId] = UnifiedTask(request.taskId, request.title, status, state.outputType(), state = state)
    }
    for (row in recovered) {
        val status = when {
            row.recoveryError != null || row.state.request == null -> TaskStatus.Failed
            row.wasQueued && row.state.stage == DownloadStage.Paused -> TaskStatus.Waiting
            else -> row.state.stage.taskStatus() ?: continue
        }
        val live = rows[row.taskId]?.state
        if (live != null && live.stage !in TerminalStages && live.stage != DownloadStage.Paused) continue
        rows[row.taskId] = UnifiedTask(
            row.taskId, row.title, status, row.state.request?.let { row.state.outputType() } ?: row.outputType,
            state = row.state, recovery = row,
        )
    }
    for (row in history.sortedWith(compareByDescending<HistoryUiItem> { it.completedAt }.thenByDescending { it.id })) {
        val id = row.taskId ?: "history:${row.id}"
        val current = rows[id]
        if (current?.history != null) continue
        if (current != null && (current.recovery != null || current.state?.fileRecoveryAction != null ||
            current.state?.stage !in TerminalStages)) {
            rows[id] = current.copy(history = row)
        } else {
            rows[id] = UnifiedTask(id, row.title, row.taskStatus(), row.outputType, history = row)
        }
    }
    return rows.values.toList()
}

private val TerminalStages = setOf(DownloadStage.Completed, DownloadStage.Failed, DownloadStage.Canceled)

private fun DownloadStage.taskStatus(): TaskStatus? = when (this) {
    DownloadStage.Idle, DownloadStage.Analyzing -> null
    DownloadStage.Waiting, DownloadStage.WaitingForNetwork -> TaskStatus.Waiting
    DownloadStage.Paused -> TaskStatus.Paused
    DownloadStage.Pausing, DownloadStage.Resuming, DownloadStage.DownloadingFile,
    DownloadStage.DownloadingVideo, DownloadStage.DownloadingAudio, DownloadStage.DownloadingSubtitles,
    DownloadStage.Merging, DownloadStage.Exporting -> TaskStatus.InProgress
    DownloadStage.Completed -> TaskStatus.Completed
    DownloadStage.Failed -> TaskStatus.Failed
    DownloadStage.Canceled -> TaskStatus.Canceled
}

private fun HistoryUiItem.taskStatus(): TaskStatus = when (status) {
    HistoryItemEntity.STATUS_COMPLETED -> TaskStatus.Completed
    HistoryItemEntity.STATUS_CANCELED -> TaskStatus.Canceled
    else -> TaskStatus.Failed
}

internal fun HistoryItemEntity.taskOutputType(): TaskOutputType =
    taskOutputTypeFromStored(outputType)
        ?: outputMimeType?.let(::taskOutputTypeFromMime)
        ?: legacyTaskOutputType(outputUri, formatSummary)

internal fun legacyTaskOutputType(outputUri: String?, formatSummary: String?): TaskOutputType {
    val label = historyTypeLabel(formatSummary.orEmpty())
    if (label == "仅音频") return TaskOutputType.Audio
    val outputPath = runCatching { URI(outputUri.orEmpty()).path }.getOrNull().orEmpty()
    val type = taskOutputTypeFromMime(ExportController.mimeTypeFor(outputPath))
    return if (type == TaskOutputType.Other && label.isNotBlank()) TaskOutputType.Video else type
}
