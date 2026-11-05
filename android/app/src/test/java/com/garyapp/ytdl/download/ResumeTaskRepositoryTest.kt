package com.garyapp.ytdl.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.data.QueueItemEntity
import com.garyapp.ytdl.data.YtdlDatabase
import com.garyapp.ytdl.testing.ProjectTestPaths
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResumeTaskRepositoryTest {
    private lateinit var database: YtdlDatabase
    private lateinit var root: File
    private lateinit var workspace: TaskWorkspace
    private lateinit var store: MemoryRequests
    private lateinit var repository: ResumeTaskRepository
    @Before fun setup() {
        DownloadCoordinator.resetForTests()
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), YtdlDatabase::class.java)
            .allowMainThreadQueries().build()
        root = ProjectTestPaths.createTempDirectory("resume-repository-").toFile()
        workspace = TaskWorkspace(File(root, "downloads"))
        store = MemoryRequests()
        repository = ResumeTaskRepository(database, store, workspace)
    }
    @After fun cleanup() { DownloadCoordinator.resetForTests(); database.close(); root.deleteRecursively() }
    private fun fileRequest(): DownloadRequest {
        val metadata = com.garyapp.ytdl.core.files.FileMetadata("http://example/report", "https://cdn/report", "report.pdf",
            "application/pdf", 10L, "\"one\"", null, com.garyapp.ytdl.core.files.FileKind.Other,
            com.garyapp.ytdl.core.files.ResumeSupport.Supported)
        return DownloadRequest(metadata.sourceUrl, "文件", DownloadRoute.HttpFile(metadata, "用户.pdf"))
    }
    @Test fun fileFailureRetainsEncryptedRequestAndRestoresDistinctRecoveryAction() {
        val request = fileRequest(); repository.create(request).getOrThrow()
        for (action in listOf(FileRecoveryAction.Retry, FileRecoveryAction.ConfirmRestart)) {
            val state = DownloadTaskState(DownloadStage.Failed, request, fileRecoveryAction = action)
            PipelineCheckpoint.open(workspace, request).save(DownloadStage.Failed, null, null, action)
            assertNull(repository.recordTerminal(state).getOrThrow())
            assertEquals(request, store.load(request.taskId).getOrThrow())
            val recovered = repository.recover().single().state
            assertEquals(DownloadStage.Failed, recovered.stage); assertEquals(action, recovered.fileRecoveryAction)
            assertTrue(database.historyDao().listAll().isEmpty())
        }
        repository.cancelStopped(repository.recover().single().state).getOrThrow()
        assertTrue(store.load(request.taskId).isFailure)
        assertEquals("canceled", database.historyDao().listAll().single().status)
    }
    @Test fun retryableFileResumeClearsFailureActionBeforeQueueing() {
        val request = fileRequest(); repository.create(request).getOrThrow()
        PipelineCheckpoint.open(workspace, request).save(DownloadStage.Failed, null, null, FileRecoveryAction.Retry)
        repository.saveState(DownloadTaskState(DownloadStage.Failed, request, fileRecoveryAction = FileRecoveryAction.Retry))
        DownloadCoordinator.restore(repository)
        DownloadCoordinator.resume(ApplicationProvider.getApplicationContext<Context>(), request.taskId).getOrThrow()
        assertEquals("queued", database.queueDao().getByTaskId(request.taskId).status)
        assertEquals(request.taskId, DownloadCoordinator.claimPendingLaunch()!!.request.taskId)
    }

    @Test fun failedConfirmedRestartSnapshotKeepsOldPartialAndRecoverableTask() {
        var failSnapshot = false
        val safeWorkspace = TaskWorkspace(File(root, "restart-failure"), AtomicSnapshotWriter { source, target ->
            if (failSnapshot) error("injected snapshot failure")
            java.nio.file.Files.move(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            Unit
        })
        val repo = ResumeTaskRepository(database, store, safeWorkspace)
        val request = fileRequest(); repo.create(request).getOrThrow()
        val task = safeWorkspace.directory(request.taskId)
        val http = File(task, "http").apply { mkdirs() }
        val partial = File(http, "body.part").apply { writeText("old") }
        File(http, "resume.bin").writeText("old checkpoint")
        PipelineCheckpoint.open(safeWorkspace, request).save(DownloadStage.Failed, null, null, FileRecoveryAction.ConfirmRestart)
        failSnapshot = true
        val metadata = (request.route as DownloadRoute.HttpFile).metadata.copy(etag = "\"two\"")
        assertTrue(repo.confirmFileRestart(request, metadata).isFailure)
        assertEquals("old", partial.readText())
        assertEquals(FileRecoveryAction.ConfirmRestart.name, safeWorkspace.loadCheckpoint(request.taskId).getOrThrow().fileRecoveryAction)
        assertNull(repo.recover().single().recoveryError)
    }

    @Test fun explicitFileRestartUpdatesEncryptedMetadataAndClearsOnlyConfirmedWorkspace() {
        val request = fileRequest(); repository.create(request).getOrThrow()
        val task = workspace.directory(request.taskId)
        File(task, "http").mkdirs()
        File(task, "http/body.part").writeText("old")
        PipelineCheckpoint.open(workspace, request).save(DownloadStage.Failed, null, null, FileRecoveryAction.ConfirmRestart)
        val metadata = (request.route as DownloadRoute.HttpFile).metadata.copy(
            etag = "\"two\"", mimeType = "audio/mpeg", kind = TaskOutputType.Audio,
        )
        val updated = repository.confirmFileRestart(request, metadata).getOrThrow()
        assertEquals(request.taskId, updated.taskId)
        assertEquals("用户.pdf", (updated.route as DownloadRoute.HttpFile).fileName)
        assertEquals(metadata, updated.route.metadata)
        assertEquals(updated, store.load(request.taskId).getOrThrow())
        assertFalse(File(workspace.directory(request.taskId), "http/body.part").exists())
        assertNull(workspace.loadCheckpoint(request.taskId).getOrThrow().fileRecoveryAction)
        assertEquals("Audio", database.queueDao().getByTaskId(request.taskId).outputType)
        store.delete(request.taskId).getOrThrow()
        val unavailable = repository.recover().single()
        assertNull(unavailable.state.request)
        assertNotNull(unavailable.recoveryError)
        assertEquals(TaskOutputType.Audio, unavailable.outputType)
        repository.cancelUnavailable(unavailable.taskId).getOrThrow()
        assertTrue(repository.recover().isEmpty())
    }

    @Test fun pendingCancellationCleanupFailureCannotReturnTheTaskToTheRunnableQueue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DownloadCoordinator.restore(repository)
        val active = request("当前任务")
        val pending = request("等待任务")
        listOf(active, pending).forEach {
            repository.create(it).getOrThrow()
            DownloadCoordinator.enqueueForServiceStart(it, workspace.directory(it.taskId).parentFile!!).getOrThrow()
        }
        val launch = DownloadCoordinator.claimPendingLaunch()!!
        DownloadCoordinator.activateLaunch(launch)
        val saved = mutableListOf<ResumeTaskItem>()
        val subscription = DownloadCoordinator.addSavedListener { saved.clear(); saved.addAll(it) }
        try {
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_pending_cancel BEFORE INSERT ON history_items BEGIN SELECT RAISE(ABORT, 'blocked'); END")
            assertTrue(DownloadCoordinator.cancelPending(context, pending.taskId).isFailure)
            assertEquals(pending.taskId, saved.single().taskId)
            assertNull(saved.single().state.request)
            assertNotNull(saved.single().recoveryError)
            DownloadCoordinator.finishActiveLaunch(launch)
            assertNull(DownloadCoordinator.claimPendingLaunch())
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_pending_cancel")
            DownloadCoordinator.cancelSaved(context, pending.taskId).getOrThrow()
            assertTrue(saved.isEmpty())
            assertTrue(workspace.exists(active.taskId))
        } finally { subscription.close() }
    }

    @Test fun cancelPendingDoesNotDeleteATaskAlreadyClaimedByTheWorker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DownloadCoordinator.restore(repository)
        val request = request()
        repository.create(request).getOrThrow()
        DownloadCoordinator.enqueueForServiceStart(request, workspace.directory(request.taskId).parentFile!!).getOrThrow()
        val launch = DownloadCoordinator.claimPendingLaunch()!!
        assertFalse(DownloadCoordinator.cancelPending(context, request.taskId).getOrThrow())
        assertTrue(workspace.exists(request.taskId))
        assertTrue(DownloadCoordinator.cancelActive(request.taskId))
        DownloadCoordinator.activateLaunch(launch)
        val token = MutableDownloadCancellation()
        DownloadCoordinator.attachCancellation(token)
        assertEquals(DownloadStopReason.UserCancel, token.stopReason)
    }

    private fun request(title: String = "测试任务") = DownloadRequest("https://example.test/page?token=secret", title,
        DownloadRoute.DirectSingleFile("18"), storageTarget = StorageTarget.SafTree("content://test/tree/original", "原位置"))

    @Test fun recoveryKeepsProgressDestinationAndNeverExecutesNetwork() {
        val request = request().copy(usesCookies = true, cookiesPath = "temporary-secret")
        repository.create(request).getOrThrow()
        workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, DownloadStage.DownloadingVideo.name,
            downloadedBytes = 82, totalBytes = 100)).getOrThrow()
        val part = File(workspace.directory(request.taskId), "video.part").apply { writeBytes(ByteArray(82)) }
        val restored = ResumeTaskRepository(database, store, workspace).recover().single()
        assertEquals(DownloadStage.Paused, restored.state.stage)
        assertEquals(82L, restored.state.progress!!.downloadedBytes)
        assertEquals(request.storageTarget, restored.state.request!!.storageTarget)
        assertNull(restored.state.request!!.cookiesPath)
        assertTrue(restored.state.request!!.usesCookies)
        assertEquals(82L, part.length())
        assertEquals(0, database.historyDao().listAll().size)
    }
    @Test fun missingAndCorruptRecordsStayVisibleAndMissingCheckpointIsNotRecreated() {
        val old = QueueItemEntity().apply { title = "旧任务"; status = "queued" }
        database.queueDao().insert(old)
        val current = request()
        repository.create(current).getOrThrow()
        File(workspace.directory(current.taskId), "checkpoint.bin").delete()
        val items = repository.recover()
        assertEquals(2, items.size)
        assertTrue(items.all { it.recoveryError != null && it.state.request == null })
        assertFalse(File(workspace.directory(current.taskId), "checkpoint.bin").exists())
    }
    @Test fun duplicateTaskDoesNotOverwriteAnyRecoveryState() {
        val request = request()
        repository.create(request).getOrThrow()
        assertTrue(repository.create(request.copy(title = "不能覆盖")).isFailure)
        assertEquals("测试任务", store.load(request.taskId).getOrThrow().title)
        assertEquals(1, database.queueDao().listAll().size)
    }
    @Test fun persistenceFailureDoesNotPublishCommittedQueueOrCreateWorkspace() {
        store.failSave = true
        val request = request()
        assertTrue(repository.create(request).isFailure)
        assertTrue(database.queueDao().listAll().isEmpty())
        assertFalse(workspace.exists(request.taskId))
    }
    @Test fun activeTaskRecoversBeforeResumedQueueHeadAndWaitingOrderRemainsStable() {
        val a = request("A"); val b = request("B"); val c = request("C")
        listOf(a,b,c).forEach { repository.create(it).getOrThrow() }
        repository.saveState(DownloadTaskState(DownloadStage.DownloadingVideo, a))
        repository.prioritize(b.taskId)
        val restored = repository.recover()
        assertEquals(listOf("A", "B", "C"), restored.map { it.title })
        assertFalse(restored.first().wasQueued)
        assertTrue(restored.drop(1).all { it.wasQueued })
        val secondOpen = repository.recover()
        assertEquals(restored.map { it.taskId to it.wasQueued }, secondOpen.map { it.taskId to it.wasQueued })
    }
    @Test fun restoreDoesNotScheduleWorkUntilContinueAndResumeGoesBeforeWaitingWithoutInterruptingActive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val a = request("A"); val b = request("B"); val c = request("C")
        listOf(a,b,c).forEach { repository.create(it).getOrThrow() }
        repository.saveState(DownloadTaskState(DownloadStage.Paused, a, stopReason = DownloadStopReason.UserPause))
        repository.saveState(DownloadTaskState(DownloadStage.Paused, c, stopReason = DownloadStopReason.UserPause))
        DownloadCoordinator.restore(repository)
        assertNull(DownloadCoordinator.claimPendingLaunch())
        DownloadCoordinator.resume(context, a.taskId).getOrThrow()
        val first = DownloadCoordinator.claimPendingLaunch()!!
        assertEquals(a.taskId, first.request.taskId)
        DownloadCoordinator.activateLaunch(first)
        DownloadCoordinator.publish(DownloadTaskState(DownloadStage.DownloadingVideo, a))
        DownloadCoordinator.resume(context, c.taskId).getOrThrow()
        assertNull(DownloadCoordinator.claimPendingLaunch())
        assertEquals(a.taskId, DownloadCoordinator.currentState()!!.request!!.taskId)
        DownloadCoordinator.finishActiveLaunch(first)
        val next = DownloadCoordinator.claimPendingLaunch()!!
        assertEquals(c.taskId, next.request.taskId)
        DownloadCoordinator.activateLaunch(next); DownloadCoordinator.finishActiveLaunch(next)
        assertEquals(b.taskId, DownloadCoordinator.claimPendingLaunch()!!.request.taskId)
    }
    @Test fun onlyTerminalResultsWriteHistoryAndRepeatedTerminalIsIdempotent() {
        val request = request()
        repository.create(request).getOrThrow()
        val recorder = DownloadHistoryRecorder(database.historyDao())
        val paused = DownloadTaskState(DownloadStage.Paused, request)
        assertTrue(repository.recordTerminal(paused, recorder).isFailure)
        val failed = paused.failed("下载失败，请重新分析。")
        assertNotNull(repository.recordTerminal(failed, recorder).getOrThrow())
        assertNull(repository.recordTerminal(failed, recorder).getOrThrow())
        assertEquals(1, database.historyDao().listAll().size)
        assertTrue(repository.recover().isEmpty())
    }
    @Test fun cancelClaimedNextTaskPersistsItsIntentInsteadOfPreviousTerminalRequest() {
        val a = request("A"); val b = request("B")
        DownloadCoordinator.restore(repository)
        listOf(a, b).forEach {
            repository.create(it).getOrThrow()
            DownloadCoordinator.enqueueForServiceStart(it, root).getOrThrow()
        }
        val first = DownloadCoordinator.claimPendingLaunch()!!
        DownloadCoordinator.activateLaunch(first)
        val failed = DownloadTaskState(DownloadStage.Failed, a, errorMessage = "下载失败。")
        repository.recordTerminal(failed).getOrThrow()
        DownloadCoordinator.publish(failed)
        DownloadCoordinator.finishActiveLaunch(first)
        assertEquals(b.taskId, DownloadCoordinator.claimPendingLaunch()!!.request.taskId)
        assertTrue(DownloadCoordinator.cancelActive(b.taskId))
        assertEquals("UserCancel", database.queueDao().getByTaskId(b.taskId).stopReason)
        assertEquals("failed", database.queueDao().getByTaskId(a.taskId).status)
    }
    @Test fun encryptedRequestCleanupFailureDoesNotUndoCommittedTerminal() {
        val request = request()
        repository.create(request).getOrThrow()
        store.failDelete = true
        val state = DownloadTaskState(DownloadStage.Failed, request, errorMessage = "下载失败。")
        assertNotNull(repository.recordTerminal(state).getOrThrow())
        assertNull(repository.recordTerminal(state).getOrThrow())
        assertEquals(1, database.historyDao().listAll().size)
        assertTrue(repository.recover().isEmpty())
    }
    @Test fun canceledTaskCannotBeResumedAfterHistoryTransactionFailure() {
        val request = request()
        repository.create(request).getOrThrow()
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_history BEFORE INSERT ON history_items BEGIN SELECT RAISE(ABORT, 'blocked'); END")
        assertTrue(repository.cancelStopped(DownloadTaskState(DownloadStage.Paused, request)).isFailure)
        assertEquals("UserCancel", database.queueDao().getByTaskId(request.taskId).stopReason)
        assertNotNull(repository.recover().single().recoveryError)
        assertTrue(database.historyDao().listAll().isEmpty())
    }
    @Test fun canceledStoppedTaskDeletesOnlyItsWorkspaceAndWritesOneHistory() {
        val a = request("A"); val b = request("B")
        repository.create(a).getOrThrow(); repository.create(b).getOrThrow()
        val sentinel = File(workspace.directory(b.taskId), "video.part").apply { writeText("other") }
        repository.cancelStopped(DownloadTaskState(DownloadStage.Paused, a)).getOrThrow()
        assertFalse(workspace.exists(a.taskId))
        assertTrue(sentinel.exists())
        assertEquals(1, database.historyDao().listAll().size)
        assertEquals(listOf(b.taskId), database.queueDao().listNonTerminal().map { it.taskId })
    }
    @Test fun cancelSavedCurrentTaskClearsPausedStateAndNotificationActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val request = request()
        repository.create(request).getOrThrow()
        repository.saveState(DownloadTaskState(DownloadStage.Paused, request))
        DownloadCoordinator.restore(repository)
        val paused = DownloadTaskState(DownloadStage.Paused, request)
        DownloadCoordinator.publish(paused)
        val notifications = NotificationController(context)
        notifications.ensureChannel()
        notifications.notifyForegroundState(paused)
        DownloadCoordinator.cancelSaved(context, request.taskId).getOrThrow()
        assertEquals(DownloadStage.Canceled, DownloadCoordinator.currentState()!!.stage)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val notification = manager.activeNotifications.singleOrNull()?.notification
        assertTrue(notification == null || notification.actions.isNullOrEmpty())
        assertFalse(workspace.exists(request.taskId))
        assertTrue(repository.recover().isEmpty())
    }

    @Test fun cancelSavedOldTaskPreservesOtherActiveTaskAndItsNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val a = request("A"); val b = request("B")
        repository.create(a).getOrThrow()
        repository.saveState(DownloadTaskState(DownloadStage.Paused, a))
        DownloadCoordinator.restore(repository)
        repository.create(b).getOrThrow()
        DownloadCoordinator.enqueueForServiceStart(b, root).getOrThrow()
        val launch = DownloadCoordinator.claimPendingLaunch()!!
        DownloadCoordinator.activateLaunch(launch)
        val running = DownloadTaskState(DownloadStage.DownloadingVideo, b)
        DownloadCoordinator.publish(running)
        NotificationController(context).apply { ensureChannel(); notifyForegroundState(running) }
        DownloadCoordinator.cancelSaved(context, a.taskId).getOrThrow()
        assertEquals(running, DownloadCoordinator.currentState())
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val notification = manager.activeNotifications.single().notification
        assertEquals(listOf("取消", "暂停"), notification.actions.map { it.title.toString() })
        assertEquals(b.taskId, org.robolectric.Shadows.shadowOf(notification.actions.first().actionIntent)
            .savedIntent.getStringExtra(DownloadService.ExtraTaskId))
        assertTrue(workspace.exists(b.taskId))
    }

    @Test fun cancelRestoredTaskUpdatesOnlyItsDetachedNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val request = request()
        repository.create(request).getOrThrow()
        val paused = DownloadTaskState(DownloadStage.Paused, request)
        repository.saveState(paused)
        NotificationController(context).apply { ensureChannel(); notifyForegroundState(paused) }
        DownloadCoordinator.resetForTests()
        DownloadCoordinator.restore(repository)
        assertNull(DownloadCoordinator.currentState())
        DownloadCoordinator.cancelSaved(context, request.taskId).getOrThrow()
        assertEquals(DownloadStage.Canceled, DownloadCoordinator.currentState()!!.stage)
        val notification = context.getSystemService(android.app.NotificationManager::class.java)
            .activeNotifications.single().notification
        assertTrue(notification.actions.isNullOrEmpty())
        assertEquals(0, notification.flags and android.app.Notification.FLAG_ONGOING_EVENT)
    }

    @Test fun failedCancelKeepsRecoveryRecordAndOnlyCancellationAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val request = request()
        repository.create(request).getOrThrow()
        val paused = DownloadTaskState(DownloadStage.Paused, request)
        repository.saveState(paused)
        DownloadCoordinator.restore(repository)
        DownloadCoordinator.publish(paused)
        NotificationController(context).apply { ensureChannel(); notifyForegroundState(paused) }
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_history BEFORE INSERT ON history_items BEGIN SELECT RAISE(ABORT, 'blocked'); END")
        assertTrue(DownloadCoordinator.cancelSaved(context, request.taskId).isFailure)
        assertEquals(DownloadStage.Paused, DownloadCoordinator.currentState()!!.stage)
        assertEquals(DownloadStopReason.UserCancel, DownloadCoordinator.currentState()!!.stopReason)
        val notification = context.getSystemService(android.app.NotificationManager::class.java)
            .activeNotifications.single().notification
        assertEquals(listOf("取消"), notification.actions.map { it.title.toString() })
        assertEquals(request.taskId, org.robolectric.Shadows.shadowOf(notification.actions.single().actionIntent)
            .savedIntent.getStringExtra(DownloadService.ExtraTaskId))
        assertEquals(1, repository.recover().size)
    }
    private class MemoryRequests : ResumeRequestStore {
        val values = mutableMapOf<String, DownloadRequest>()
        var failSave = false
        var failDelete = false
        override fun save(request: DownloadRequest) = runCatching { check(!failSave); values[request.taskId] = request.copy(cookiesPath = null); Unit }
        override fun load(taskId: String) = runCatching { checkNotNull(values[taskId]) }
        override fun delete(taskId: String) = runCatching { check(!failDelete); values.remove(taskId); Unit }
    }
}
