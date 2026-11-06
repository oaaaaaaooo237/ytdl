package com.garyapp.ytdl.ui

import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.download.ResumeTaskItem
import com.garyapp.ytdl.download.TaskOutputType
import org.junit.Assert.*
import org.junit.Test

class UnifiedTasksTest {
    private val request = DownloadRequest("https://example.com", "任务", DownloadRoute.AudioOnly("audio"))

    @Test fun activeAndRecoveredAndHistoryShareIdentityAndKeepLegacyRows() {
        val active = DownloadTaskState(DownloadStage.DownloadingAudio, request)
        val restored = ResumeTaskItem(request.taskId, "任务", active.copy(stage = DownloadStage.Paused))
        val queuedRequest = request.copy(taskId = "a".repeat(32))
        val queued = ResumeTaskItem(queuedRequest.taskId, "等待", DownloadTaskState(DownloadStage.Paused, queuedRequest), wasQueued = true)
        val recent = HistoryItemEntity().apply { id = 7; taskId = request.taskId; status = "completed"; outputType = "Audio" }
        val old = HistoryItemEntity().apply { id = 3; title = "标题.mp3"; status = "failed" }
        val rows = unifiedTasks(listOf(active), listOf(restored, queued), listOf(recent, old))
        assertEquals(listOf(request.taskId, queuedRequest.taskId, "history:3"), rows.map { it.id })
        assertEquals(listOf(TaskStatus.InProgress, TaskStatus.Waiting, TaskStatus.Failed), rows.map { it.status })
        assertEquals(TaskOutputType.Audio, rows.first().type)
        assertEquals(TaskOutputType.Other, rows.last().type)
        assertEquals(7L, unifiedTasks(emptyList(), listOf(restored), listOf(recent)).single().history!!.id)
    }

    @Test fun recoveryActionWinsOverSameIdHistoryAndDeduplicatesLegacyDuplicates() {
        val state = DownloadTaskState(DownloadStage.Failed, request,
            fileRecoveryAction = com.garyapp.ytdl.download.FileRecoveryAction.ConfirmRestart)
        val recovered = ResumeTaskItem(request.taskId, "待确认", state)
        val history = listOf(7L, 8L).map { historyId ->
            HistoryItemEntity().apply { id = historyId; taskId = request.taskId; status = "failed" }
        }
        val task = unifiedTasks(listOf(state), listOf(recovered), history).single()
        assertEquals(recovered, task.recovery)
        assertEquals(8L, task.history?.id)
        assertEquals(8L, unifiedTasks(emptyList(), emptyList(), history).single().history?.id)
    }

    @Test fun corruptedQueueEntryIsFailedAndKeepsItsRecoveryIdentity() {
        val recovered = ResumeTaskItem(
            request.taskId, "损坏任务", DownloadTaskState(DownloadStage.Paused),
            recoveryError = "恢复信息不可用", wasQueued = true, outputType = TaskOutputType.Audio,
        )
        val row = unifiedTasks(emptyList(), listOf(recovered), emptyList()).single()
        assertEquals(TaskStatus.Failed, row.status)
        assertEquals(request.taskId, row.id)
        assertEquals(TaskOutputType.Audio, row.type)
        assertEquals(recovered, row.recovery)
        assertNull(row.state?.request)
    }

    @Test fun filterUsesOrWithinGroupsAndAndAcrossGroups() {
        val task = unifiedTasks(listOf(DownloadTaskState(DownloadStage.Paused, request)), emptyList(), emptyList()).single()
        assertTrue(matchesTaskFilter(task, setOf(TaskStatus.Paused, TaskStatus.Completed), setOf(TaskOutputType.Video, TaskOutputType.Audio)))
        assertFalse(matchesTaskFilter(task, setOf(TaskStatus.Completed), setOf(TaskOutputType.Audio)))
        assertFalse(matchesTaskFilter(task, setOf(TaskStatus.Paused), setOf(TaskOutputType.Video)))
        assertTrue(matchesTaskFilter(task, emptySet(), setOf(TaskOutputType.Audio)))
        assertTrue(matchesTaskFilter(task, setOf(TaskStatus.Paused), emptySet()))
        assertTrue(matchesTaskFilter(task, emptySet(), emptySet()))
    }

    @Test fun terminalHistoryKeepsActualOutputMimeAndUsesRouteForFailedMedia() {
        val video = request.copy(route = DownloadRoute.MergeRequired("video", "audio"))
        val failed = HistoryItemEntity.fromTaskState(DownloadTaskState(DownloadStage.Failed, video), "任务", 3)
        assertEquals(video.taskId, failed.taskId)
        assertEquals("Video", failed.outputType)
        val audio = com.garyapp.ytdl.download.DownloadOutputFile(
            com.garyapp.ytdl.download.DownloadOutputKind.Media, "/private/result.webm", 10,
            externalDocumentUri = "content://example/document/opaque",
        )
        val complete = HistoryItemEntity.fromTaskState(
            DownloadTaskState(DownloadStage.Completed, request, listOf(audio)), "任务", 4,
        )
        assertEquals("audio/webm", complete.outputMimeType)
        assertEquals("Audio", complete.outputType)
    }
}
