package com.garyapp.ytdl.download

import android.content.Context
import com.garyapp.ytdl.core.privacy.SensitiveText
import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import com.garyapp.ytdl.data.QueueItemEntity
import com.garyapp.ytdl.data.YtdlDatabase
import com.garyapp.ytdl.data.YtdlDatabaseProvider
import java.io.File

data class ResumeTaskItem(
    val taskId: String,
    val title: String,
    val state: DownloadTaskState,
    val recoveryError: String? = null,
    val wasQueued: Boolean = false,
)

/** Room is the commit marker: no runnable item is exposed before all three stores exist. */
internal class ResumeTaskRepository(
    private val database: YtdlDatabase,
    private val requests: ResumeRequestStore,
    val workspace: TaskWorkspace,
) {
    private val queue get() = database.queueDao()

    fun create(request: DownloadRequest): Result<Unit> = synchronized(MutationLock) {
        runCatching {
            check(queue.getByTaskId(request.taskId) == null) { "任务已存在。" }
            check(!workspace.exists(request.taskId)) { "任务目录已存在。" }
            check(requests.load(request.taskId).isFailure) { "恢复信息已存在。" }
            requests.saveNew(request.copy(cookiesPath = null)).getOrThrow()
            try {
                workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, DownloadStage.Waiting.name)).getOrThrow()
                val now = System.currentTimeMillis()
                val row = QueueItemEntity.createSafe(request.title, 0, "", "", "", "", request.formatSummary,
                    QueueItemEntity.STATUS_QUEUED, 0, "", "", null, now, now).apply {
                    taskId = request.taskId
                    queueOrder = (queue.listAll().maxOfOrNull { it.queueOrder } ?: 0) + 1
                    phase = DownloadStage.Waiting.name
                    checkpointAt = now
                }
                queue.insert(row)
                Unit
            } catch (error: Exception) {
                workspace.deleteTask(request.taskId)
                requests.delete(request.taskId)
                throw error
            }
        }
    }

    fun recover(): List<ResumeTaskItem> = synchronized(MutationLock) {
        queue.listNonTerminal().sortedBy {
            if (it.status in setOf("running", "waiting_network") || it.stopReason == DownloadStopReason.LifecycleEnd.name && it.phase != DownloadStage.Waiting.name) 0 else 1
        }.map { row ->
            val restored = runCatching {
                check(row.stopReason != DownloadStopReason.UserCancel.name)
                val request = requests.load(row.taskId).getOrThrow()
                val previous = workspace.loadCheckpoint(row.taskId).getOrThrow()
                val checkpoint = PipelineCheckpoint.open(workspace, request)
                // A completed private file can still be waiting for export.
                val reason = previous.stopReason?.let { DownloadStopReason.valueOf(it) } ?: DownloadStopReason.LifecycleEnd
                ResumeTaskItem(row.taskId, safeTitle(row.title), DownloadTaskState(
                    DownloadStage.Paused, request, progress = checkpoint.progress(), stopReason = reason,
                    statusMessage = if (reason == DownloadStopReason.UserPause) "点击继续下载。" else "应用曾退出，点击继续。",
                ), wasQueued = row.status == QueueItemEntity.STATUS_QUEUED)
            }.getOrElse {
                ResumeTaskItem(row.taskId, safeTitle(row.title), DownloadTaskState(
                    DownloadStage.Paused, progress = DownloadProgress("paused", null, row.downloadedBytes,
                        row.totalBytes, 0.0, null, null),
                ), "恢复信息不可用，请取消任务后重新分析。")
            }
            restored
        }
    }

    fun saveState(state: DownloadTaskState) = synchronized(MutationLock) {
        val request = state.request ?: return@synchronized
        val row = queue.getByTaskId(request.taskId) ?: return@synchronized
        if (row.status in TerminalStatuses) return@synchronized
        row.status = when (state.stage) {
            DownloadStage.Waiting, DownloadStage.Resuming -> "queued"
            DownloadStage.Paused -> "paused"
            DownloadStage.WaitingForNetwork -> "waiting_network"
            else -> "running"
        }
        if (state.stage !in setOf(DownloadStage.Paused, DownloadStage.Pausing, DownloadStage.Resuming, DownloadStage.WaitingForNetwork)) {
            row.phase = state.stage.name
        }
        row.stopReason = state.stopReason?.name
        state.progress?.let {
            row.downloadedBytes = it.downloadedBytes ?: row.downloadedBytes
            row.totalBytes = it.totalBytes ?: row.totalBytes
            row.progress = it.percent?.toInt()?.coerceIn(0, 100) ?: row.progress
        }
        row.updatedAt = System.currentTimeMillis()
        row.checkpointAt = row.updatedAt
        queue.update(row)
    }

    fun prioritize(taskId: String) = synchronized(MutationLock) {
        val row = checkNotNull(queue.getByTaskId(taskId))
        row.queueOrder = (queue.listNonTerminal().minOfOrNull { it.queueOrder } ?: 0) - 1
        queue.update(row)
    }

    /** History and its terminal marker commit together, so a late callback cannot duplicate history. */
    fun recordTerminal(state: DownloadTaskState, recorder: DownloadHistoryRecorder = DownloadHistoryRecorder(database.historyDao())): Result<Long?> = synchronized(MutationLock) {
        runCatching {
            require(state.stage in TerminalStages)
            val request = requireNotNull(state.request)
            var historyId: Long? = null
            database.runInTransaction {
                val row = checkNotNull(queue.getByTaskId(request.taskId))
                if (row.status !in TerminalStatuses) {
                    historyId = recorder.recordTerminal(state).getOrThrow()
                    row.status = state.stage.name.lowercase()
                    row.phase = state.stage.name
                    row.stopReason = state.stopReason?.name
                    queue.update(row)
                }
            }
            requests.delete(request.taskId)
            historyId
        }
    }

    fun cancelUnavailable(taskId: String): Result<Unit> = synchronized(MutationLock) {
        runCatching {
            workspace.deleteTask(taskId).getOrThrow()
            requests.delete(taskId).getOrThrow()
            queue.deleteByTaskId(taskId)
            Unit
        }
    }

    fun cancelStopped(state: DownloadTaskState): Result<Unit> = synchronized(MutationLock) {
        runCatching {
            val request = requireNotNull(state.request)
            val row = checkNotNull(queue.getByTaskId(request.taskId))
            row.status = "cancel_pending"
            row.stopReason = DownloadStopReason.UserCancel.name
            queue.update(row)
            workspace.deleteTask(request.taskId).getOrThrow()
            recordTerminal(state.canceled().copy(outputs = emptyList()), DownloadHistoryRecorder(database.historyDao())).getOrThrow()
            Unit
        }
    }

    companion object {
        private val MutationLock = Any()
        val TerminalStages = setOf(DownloadStage.Completed, DownloadStage.Failed, DownloadStage.Canceled)
        private val TerminalStatuses = setOf("completed", "failed", "canceled", "network_failed", "permission_failed")
        private fun safeTitle(value: String?) = SensitiveText.redact(value.orEmpty()).ifBlank { "下载任务" }
        fun fromContext(context: Context) = ResumeTaskRepository(YtdlDatabaseProvider.get(context),
            FileResumeRequestStore.fromContext(context), TaskWorkspace(File(context.filesDir, "gui-downloads")))
    }
}
