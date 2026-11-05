package com.garyapp.ytdl.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.core.ytdlp.*
import com.garyapp.ytdl.data.YtdlDatabase
import com.garyapp.ytdl.testing.ProjectTestPaths
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadServiceResumeTest {
    private lateinit var database: YtdlDatabase
    private lateinit var service: DownloadService
    private lateinit var root: File
    private lateinit var repository: ResumeTaskRepository
    private val values = mutableMapOf<String, DownloadRequest>()
    private val store = object : ResumeRequestStore {
        override fun save(request: DownloadRequest) = runCatching { values[request.taskId] = request.copy(cookiesPath = null); Unit }
        override fun load(taskId: String) = runCatching { checkNotNull(values[taskId]) }
        override fun delete(taskId: String) = runCatching { values.remove(taskId); Unit }
    }
    @Before fun setup() {
        DownloadCoordinator.resetForTests()
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), YtdlDatabase::class.java).allowMainThreadQueries().build()
        root = ProjectTestPaths.createTempDirectory("service-resume-").toFile()
        repository = ResumeTaskRepository(database, store, TaskWorkspace(root))
        service = Robolectric.buildService(DownloadService::class.java).create().get()
        service.network.close()
        service.network = object : DownloadNetwork {
            override fun isAvailable() = true
            override fun isStable() = true
            override fun close() {}
        }
        service.repository = repository
        DownloadCoordinator.restore(repository)
    }
    @After fun cleanup() { service.onDestroy(); DownloadCoordinator.resetForTests(); database.close(); root.deleteRecursively() }
    private fun request() = DownloadRequest("https://example.test/page", "测试", DownloadRoute.DirectSingleFile("18"))
    private fun enqueue(request: DownloadRequest): DownloadLaunch {
        repository.create(request).getOrThrow()
        DownloadCoordinator.enqueueForServiceStart(request, root).getOrThrow()
        return DownloadCoordinator.claimPendingLaunch()!!.also { DownloadCoordinator.activateLaunch(it) }
    }
    @Test fun serviceRunsTwoDefaultWorkersAndPauseTargetsOnlyOne() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(2)
        val tasks = List(3) { request() }
        tasks.forEach {
            repository.create(it).getOrThrow()
            DownloadCoordinator.enqueueForServiceStart(it, root).getOrThrow()
        }
        service.engineFactory = { BlockingEngine(entered, release) }
        val subscription = DownloadCoordinator.addWorkerReleasedListener { exited.countDown() }
        try {
            service.onStartCommand(null, 0, 1)
            assertTrue("默认应并发启动两项", entered.await(5, TimeUnit.SECONDS))
            DownloadCoordinator.pauseActive(tasks.first().taskId)
            release.countDown()
            assertTrue(exited.await(5, TimeUnit.SECONDS))
            assertEquals("paused", database.queueDao().getByTaskId(tasks[0].taskId).status)
            assertEquals("failed", database.queueDao().getByTaskId(tasks[1].taskId).status)
            assertEquals("queued", database.queueDao().getByTaskId(tasks[2].taskId).status)
        } finally {
            release.countDown()
            subscription.close()
        }
    }
    @Test fun raisingLimitStartsWaitingTasksAndLoweringItPreservesRunningTasks() {
        val settings = com.garyapp.ytdl.core.settings.SettingsRepository.fromContext(service)
        settings.update { it.copy(maxConcurrentDownloads = 1) }
        val entered = List(4) { CountDownLatch(1) }
        val releases = List(4) { CountDownLatch(1) }
        val tasks = List(4) { request() }
        tasks.forEach {
            repository.create(it).getOrThrow()
            DownloadCoordinator.enqueueForServiceStart(it, root).getOrThrow()
        }
        val factories = java.util.concurrent.atomic.AtomicInteger()
        service.engineFactory = {
            val index = factories.getAndIncrement()
            BlockingEngine(entered[index], releases[index])
        }
        val released = java.util.concurrent.Semaphore(0)
        val subscription = DownloadCoordinator.addWorkerReleasedListener { released.release() }
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        try {
            service.onStartCommand(null, 0, 1)
            assertTrue(entered[0].await(5, TimeUnit.SECONDS))
            assertEquals(1, DownloadCoordinator.activeStates().size)
            settings.update { it.copy(maxConcurrentDownloads = 3) }
            looper.idle()
            assertTrue(entered[1].await(5, TimeUnit.SECONDS))
            assertTrue(entered[2].await(5, TimeUnit.SECONDS))
            assertEquals(tasks.take(3).map { it.taskId }, DownloadCoordinator.activeStates().keys.toList())
            settings.update { it.copy(maxConcurrentDownloads = 1) }
            looper.idle()
            assertTrue(DownloadCoordinator.activeStates().values.all { it.stopReason == null })
            DownloadCoordinator.cancelActive(tasks[0].taskId)
            releases[0].countDown()
            assertTrue(released.tryAcquire(5, TimeUnit.SECONDS))
            looper.idle()
            assertEquals("canceled", database.queueDao().getByTaskId(tasks[0].taskId).status)
            assertEquals(2, DownloadCoordinator.activeStates().size)
            assertEquals(1L, entered[3].count)
            releases[1].countDown(); releases[2].countDown()
            assertTrue(released.tryAcquire(2, 5, TimeUnit.SECONDS))
            looper.idle()
            assertTrue(entered[3].await(5, TimeUnit.SECONDS))
            assertEquals(setOf(tasks[3].taskId), DownloadCoordinator.activeStates().keys)
            releases[3].countDown()
            assertTrue(released.tryAcquire(5, TimeUnit.SECONDS))
        } finally {
            releases.forEach { it.countDown() }
            subscription.close()
        }
    }

    @Test fun destroyingServiceKeepsBothWorkingDirectoriesUntilWorkersExitAndRestoresBoth() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(2)
        val tasks = List(2) { request() }
        tasks.forEach {
            repository.create(it).getOrThrow()
            DownloadCoordinator.enqueueForServiceStart(it, root).getOrThrow()
        }
        service.engineFactory = { BlockingEngine(entered, release) }
        val subscription = DownloadCoordinator.addWorkerReleasedListener { exited.countDown() }
        try {
            service.onStartCommand(null, 0, 1)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            service.onDestroy()
            assertEquals(2, DownloadCoordinator.activeStates().size)
            tasks.forEach { task ->
                assertTrue(repository.workspace.directory(task.taskId).walk().any { it.name == "video.part" })
            }
            release.countDown()
            assertTrue(exited.await(5, TimeUnit.SECONDS))
            assertEquals(tasks.map { it.taskId }.toSet(), repository.recover().map { it.taskId }.toSet())
            assertTrue(repository.recover().all { it.state.stopReason == DownloadStopReason.LifecycleEnd })
            assertTrue(database.historyDao().listAll().isEmpty())
        } finally {
            release.countDown()
            subscription.close()
        }
    }

    @Test fun completedNeighborLeavesPausedNotificationWithOriginalResumeAction() {
        org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val paused = enqueue(request().copy(usesCookies = true))
        val complete = request()
        repository.create(complete).getOrThrow()
        DownloadCoordinator.enqueueForServiceStart(complete, root).getOrThrow()
        val neighbor = DownloadCoordinator.claimPendingLaunch(2)!!
        DownloadCoordinator.activateLaunch(neighbor)
        service.engineFactory = { BlockingEngine(CountDownLatch(1), CountDownLatch(0), succeeds = true) }
        service.runLaunch(paused)
        service.runLaunch(neighbor)
        val manager = service.getSystemService(android.app.NotificationManager::class.java)
        val resumeCard = manager.activeNotifications.single { it.tag == paused.request.taskId }.notification
        val resume = resumeCard.actions.single { it.title.toString() == "继续" }.actionIntent
        assertEquals(paused.request.taskId, org.robolectric.Shadows.shadowOf(resume).savedIntent.getStringExtra(DownloadService.ExtraTaskId))
        assertEquals(DownloadService.ActionResume, org.robolectric.Shadows.shadowOf(resume).savedIntent.action)
        assertTrue(manager.activeNotifications.any { it.tag == complete.taskId })
        assertEquals("completed", database.queueDao().getByTaskId(complete.taskId).status)
    }

    @Test fun queuedPauseRetainsCookiesReferenceUntilManagedFileCanBeDeleted() {
        val temporary = com.garyapp.ytdl.cookies.TemporaryCookiesFile.materializeFromStream(
            "test cookie".byteInputStream(), root, "queued-pause",
        ).getOrThrow()
        val path = temporary.file
        assertTrue(path.delete())
        assertTrue(path.mkdir())
        val child = File(path, "prevents-delete").apply { writeText("test") }
        val task = request().copy(cookiesPath = path.absolutePath)
        repository.create(task).getOrThrow()
        DownloadCoordinator.enqueueForServiceStart(task, root).getOrThrow()
        var pending = emptyList<DownloadRequest>()
        val subscription = DownloadCoordinator.addPendingListener { pending = it }
        try {
            DownloadCoordinator.pauseActive(task.taskId)
            assertEquals("queued", database.queueDao().getByTaskId(task.taskId).status)
            assertEquals(path.absolutePath, pending.single().cookiesPath)
            assertTrue(child.delete())
            DownloadCoordinator.pauseActive(task.taskId)
            assertFalse(path.exists())
            assertTrue(pending.isEmpty())
            assertEquals("paused", database.queueDao().getByTaskId(task.taskId).status)
            assertNull(repository.recover().single().state.request!!.cookiesPath)
        } finally {
            child.delete()
            temporary.delete()
            subscription.close()
        }
    }

    @Test fun cancelAfterSafCopyStillRecordsExportedOutput() {
        val task = request().copy(storageTarget = com.garyapp.ytdl.core.storage.StorageTarget.SafTree("content://docs/tree/save"))
        val launch = enqueue(task)
        val documentUri = "content://docs/document/exported"
        service.engineFactory = { BlockingEngine(CountDownLatch(1), CountDownLatch(0), succeeds = true) }
        service.exportToTree = { _, _, token ->
            assertTrue(DownloadCoordinator.cancelActive(task.taskId))
            assertEquals(DownloadStopReason.UserCancel, token.stopReason)
            listOf(documentUri)
        }
        service.runLaunch(launch)
        assertEquals("completed", database.queueDao().getByTaskId(task.taskId).status)
        assertEquals("completed", database.historyDao().listAll().single().status)
        assertEquals(documentUri, DownloadCoordinator.currentState()!!.outputs.single().externalDocumentUri)
        assertFalse(repository.workspace.exists(task.taskId))
    }

    @Test fun durableSafReceiptSurvivesEarlierCancelMarkerAcrossRecovery() {
        val task = request().copy(storageTarget = com.garyapp.ytdl.core.storage.StorageTarget.SafTree("content://docs/tree/save"))
        enqueue(task)
        assertTrue(DownloadCoordinator.cancelActive(task.taskId))
        repository.workspace.saveExportedDocuments(task.taskId, listOf("content://docs/document/exported")).getOrThrow()
        val recovered = repository.recover().single()
        assertNull(recovered.recoveryError)
        assertEquals(task.taskId, recovered.state.request!!.taskId)
        DownloadCoordinator.resetForTests()
        DownloadCoordinator.restore(repository)
        assertTrue(DownloadCoordinator.cancelSaved(service, task.taskId).isFailure)
        assertEquals(listOf("content://docs/document/exported"),
            repository.workspace.loadExportedDocuments(task.taskId).getOrThrow())
    }

    @Test fun cancelDuringSafCopyStillHonorsExporterRollback() {
        val task = request().copy(storageTarget = com.garyapp.ytdl.core.storage.StorageTarget.SafTree("content://docs/tree/save"))
        val launch = enqueue(task)
        service.engineFactory = { BlockingEngine(CountDownLatch(1), CountDownLatch(0), succeeds = true) }
        service.exportToTree = { _, _, token ->
            assertTrue(DownloadCoordinator.cancelActive(task.taskId))
            assertEquals(DownloadStopReason.UserCancel, token.stopReason)
            throw java.util.concurrent.CancellationException()
        }
        service.runLaunch(launch)
        assertEquals("canceled", database.queueDao().getByTaskId(task.taskId).status)
        assertEquals("canceled", database.historyDao().listAll().single().status)
        assertFalse(repository.workspace.exists(task.taskId))
    }

    @Test fun pausingQueuedTaskPersistsImmediatelyAndPreventsAutomaticRestore() {
        val queued = List(3) { request() }
        queued.forEach {
            repository.create(it).getOrThrow()
            DownloadCoordinator.enqueueForServiceStart(it, root).getOrThrow()
        }
        val target = queued[1]
        DownloadCoordinator.pauseActive(target.taskId)
        assertEquals("paused", database.queueDao().getByTaskId(target.taskId).status)
        val restored = repository.recover().single { it.taskId == target.taskId }
        assertFalse(restored.wasQueued)
        assertEquals(DownloadStopReason.UserPause, restored.state.stopReason)
        val first = DownloadCoordinator.claimPendingLaunch()!!
        assertEquals(queued[0].taskId, first.request.taskId)
        DownloadCoordinator.finishActiveLaunch(first)
        assertEquals(queued[2].taskId, DownloadCoordinator.claimPendingLaunch()!!.request.taskId)
        DownloadCoordinator.resume(service, target.taskId).getOrThrow()
        assertEquals("queued", database.queueDao().getByTaskId(target.taskId).status)
    }

    @Test fun cancelAcceptedWhilePipelineEmitsPausedStillCleansWorkspaceAndRecordsCanceled() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val launch = enqueue(request())
        service.engineFactory = { BlockingEngine(entered, release) }
        var accepted = false
        val subscription = DownloadCoordinator.addListener { state ->
            if (state.stage == DownloadStage.Paused && !accepted) {
                accepted = DownloadCoordinator.cancelActive(launch.request.taskId)
            }
        }
        val worker = Thread { service.runLaunch(launch) }.apply { start() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            DownloadCoordinator.pauseActive(launch.request.taskId)
            release.countDown()
            worker.join(5_000)
            assertFalse(worker.isAlive)
            assertTrue(accepted)
            assertEquals("canceled", database.queueDao().getByTaskId(launch.request.taskId).status)
            assertEquals("canceled", database.historyDao().listAll().single().status)
            assertFalse(repository.workspace.exists(launch.request.taskId))
        } finally {
            release.countDown()
            subscription.close()
        }
    }

    @Test fun latePipelinePauseCannotOverwriteDurableCancelIntent() {
        val launch = enqueue(request())
        val id = launch.request.taskId
        assertTrue(DownloadCoordinator.cancelActive(id))
        DownloadCoordinator.publish(DownloadTaskState(DownloadStage.Paused, launch.request, stopReason = DownloadStopReason.UserPause))
        assertEquals(DownloadStopReason.UserCancel.name, database.queueDao().getByTaskId(id).stopReason)
        assertNotNull(repository.recover().single { it.taskId == id }.recoveryError)
    }

    @Test fun finalizedPausedTaskCanBeCanceledBeforeWorkerReleasesSlot() {
        val launch = enqueue(request().copy(usesCookies = true))
        service.runLaunch(launch)
        assertTrue(DownloadCoordinator.hasWorker())
        assertFalse(DownloadCoordinator.cancelActive(launch.request.taskId))
        DownloadCoordinator.cancelSaved(service, launch.request.taskId).getOrThrow()
        assertEquals("canceled", database.queueDao().getByTaskId(launch.request.taskId).status)
        assertFalse(repository.workspace.exists(launch.request.taskId))
    }

    @Test fun continuingOneSavedTaskDoesNotChangeOtherPausedOrActiveTasks() {
        val first = enqueue(request())
        DownloadCoordinator.park(first, DownloadTaskState(DownloadStage.Paused, first.request))
        DownloadCoordinator.finishActiveLaunch(first)
        val second = enqueue(request())
        DownloadCoordinator.park(second, DownloadTaskState(DownloadStage.Paused, second.request))
        DownloadCoordinator.finishActiveLaunch(second)
        val running = enqueue(request())
        val token = MutableDownloadCancellation()
        DownloadCoordinator.attachCancellation(token, running.request.taskId)
        val pending = request()
        repository.create(pending).getOrThrow()
        DownloadCoordinator.enqueueForServiceStart(pending, root).getOrThrow()
        var waiting = emptyList<DownloadRequest>()
        val subscription = DownloadCoordinator.addPendingListener { waiting = it }
        try {
            DownloadCoordinator.resume(service, second.request.taskId).getOrThrow()
            assertEquals(listOf(second.request.taskId, pending.taskId), waiting.map { it.taskId })
            assertEquals(setOf(running.request.taskId), DownloadCoordinator.activeStates().keys)
            assertNull(token.stopReason)
            assertEquals("paused", database.queueDao().getByTaskId(first.request.taskId).status)
            assertEquals("queued", database.queueDao().getByTaskId(second.request.taskId).status)
        } finally {
            subscription.close()
        }
    }

    @Test fun restoringNetworkContinuesOnlyTheTaskThatWasNotManuallyPaused() {
        val online = java.util.concurrent.atomic.AtomicBoolean(false)
        service.network = object : DownloadNetwork {
            override fun isAvailable() = online.get()
            override fun isStable() = online.get()
            override fun close() {}
        }
        val tasks = List(2) { request() }
        tasks.forEach {
            repository.create(it).getOrThrow()
            DownloadCoordinator.enqueueForServiceStart(it, root).getOrThrow()
        }
        val waiting = CountDownLatch(1)
        val paused = CountDownLatch(1)
        val resumed = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(2)
        service.engineFactory = { BlockingEngine(resumed, release) }
        val workers = DownloadCoordinator.addWorkerReleasedListener { exited.countDown() }
        val states = DownloadCoordinator.addActiveListener { active ->
            if (active.size == 2 && active.values.all { it.stage == DownloadStage.WaitingForNetwork }) waiting.countDown()
            if (active[tasks[0].taskId]?.stage == DownloadStage.Paused) paused.countDown()
        }
        try {
            service.onStartCommand(null, 0, 1)
            assertTrue(waiting.await(5, TimeUnit.SECONDS))
            DownloadCoordinator.pauseActive(tasks[0].taskId)
            assertTrue(paused.await(5, TimeUnit.SECONDS))
            online.set(true)
            assertTrue(resumed.await(5, TimeUnit.SECONDS))
            assertEquals("paused", database.queueDao().getByTaskId(tasks[0].taskId).status)
            assertEquals(DownloadStage.DownloadingVideo, DownloadCoordinator.activeStates()[tasks[1].taskId]?.stage)
            release.countDown()
            assertTrue(exited.await(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            states.close()
            workers.close()
        }
    }

    @Test fun httpFileUsesForegroundTaskWithoutCreatingMediaEngineAndExportsEmptyWithMetadataMime() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/download") { e ->
            e.responseHeaders.add("Content-Length", "0"); e.responseHeaders.add("Content-Type", "application/pdf")
            e.responseHeaders.add("ETag", "\"one\""); e.sendResponseHeaders(200, -1); e.close()
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/download"
            val metadata = com.garyapp.ytdl.core.files.FileMetadata(url, url, "download", "application/pdf", 0L, "\"one\"", null,
                com.garyapp.ytdl.core.files.FileKind.Other, com.garyapp.ytdl.core.files.ResumeSupport.Supported)
            val launch = enqueue(DownloadRequest(url, "文件", DownloadRoute.HttpFile(metadata),
                storageTarget = com.garyapp.ytdl.core.storage.StorageTarget.SafTree("content://docs/tree/save")))
            service.engineFactory = { error("文件任务不得创建媒体下载器") }
            var copies = 0
            service.exportToTree = { _, outputs, _ ->
                assertEquals(0L, outputs.single().bytesWritten)
                assertEquals("application/pdf", outputs.single().mimeType)
                copies++; listOf("content://docs/document/report")
            }
            service.runLaunch(launch)
            assertEquals(1, copies)
            assertEquals(DownloadStage.Completed, DownloadCoordinator.currentState()!!.stage)
            assertEquals("completed", database.historyDao().listAll().single().status)
            assertFalse(repository.workspace.exists(launch.request.taskId))
        } finally { server.stop(0) }
    }

    @Test fun safFileFailureAndNameConflictKeepCompletedEmptyOutputForRecovery() {
        val url = "https://example.test/download"
        val metadata = com.garyapp.ytdl.core.files.FileMetadata(url, url, "download", "application/pdf", 0L, "\"one\"", null,
            com.garyapp.ytdl.core.files.FileKind.Other, com.garyapp.ytdl.core.files.ResumeSupport.Supported)
        val request = DownloadRequest(url, "文件", DownloadRoute.HttpFile(metadata, conflictPolicy = HttpFileConflictPolicy.Ask))
        repository.create(request).getOrThrow()
        val checkpoint = PipelineCheckpoint.open(repository.workspace, request)
        val directory = File(checkpoint.directory, "outputs").apply { mkdirs() }
        val file = File(directory, "download").apply { writeBytes(byteArrayOf()) }
        checkpoint.record("file", file); checkpoint.save(DownloadStage.Completed, null, null)
        val output = DownloadOutputFile(DownloadOutputKind.File, file.path, 0L, root.path, mimeType = metadata.mimeType)
        val result = DownloadPipelineResult(DownloadTaskState(DownloadStage.Completed, request, listOf(output)), listOf(output))
        val target = com.garyapp.ytdl.core.storage.StorageTarget.SafTree("content://docs/tree/save")
        service.exportToTree = { _, _, _ -> error("permission lost") }
        assertEquals(DownloadStage.Paused, service.exportCompletedOutputs(result, target, NeverDownloadCancellation).stage)
        assertTrue(file.isFile)
        service.exportToTree = { _, _, _ -> throw com.garyapp.ytdl.storage.ExportController.NameConflictException() }
        val conflict = service.exportCompletedOutputs(result, target, NeverDownloadCancellation)
        assertEquals(FileRecoveryAction.ChooseFileName, conflict.fileRecoveryAction)
        assertEquals(DownloadStage.Failed, conflict.stage)
        val renamed = repository.chooseFileName(request, "other").getOrThrow()
        assertEquals(metadata, (renamed.route as DownloadRoute.HttpFile).metadata)
        val resumed = HttpFilePipeline().run(renamed, root)
        assertEquals(DownloadStage.Completed, resumed.state.stage)
        assertTrue(file.isFile)
        service.exportToTree = { _, outputs, _ ->
            assertEquals("other", outputs.single().displayName); assertEquals("application/pdf", outputs.single().mimeType)
            listOf("content://docs/document/other")
        }
        assertEquals("content://docs/document/other", service.exportCompletedOutputs(resumed, target, NeverDownloadCancellation).outputs.single().externalDocumentUri)
    }

    @Test fun cancelCommandRemovesOnlyQueuedTaskAndKeepsActiveDownloadAndRemainingOrder() {
        val active = enqueue(request())
        val token = MutableDownloadCancellation()
        DownloadCoordinator.attachCancellation(token)
        val marker = File(repository.workspace.directory(active.request.taskId), "keep.part").apply { writeText("active") }
        val pending = request().copy(title = "等待取消")
        val next = request().copy(title = "继续等待")
        listOf(pending, next).forEach {
            repository.create(it).getOrThrow()
            DownloadCoordinator.enqueueForServiceStart(it, root).getOrThrow()
        }
        val removed = CountDownLatch(1)
        val subscription = DownloadCoordinator.addPendingListener { requests ->
            if (requests.none { it.taskId == pending.taskId }) removed.countDown()
        }
        try {
            service.onStartCommand(android.content.Intent(service, DownloadService::class.java)
                .setAction(DownloadService.ActionCancel)
                .putExtra(DownloadService.ExtraTaskId, pending.taskId), 0, 1)
            assertTrue("等待任务应被单独取消", removed.await(5, TimeUnit.SECONDS))
            assertNull(token.stopReason)
            assertEquals("active", marker.readText())
            assertEquals("canceled", database.queueDao().getByTaskId(pending.taskId).status)
            assertFalse(repository.workspace.exists(pending.taskId))
            assertEquals(1, database.historyDao().listAll().count { it.status == "canceled" })
            DownloadCoordinator.finishActiveLaunch(active)
            assertEquals(next.taskId, DownloadCoordinator.claimPendingLaunch()!!.request.taskId)
        } finally { subscription.close() }
    }
    @Test fun notificationCancelForPausedTaskLeavesNoResumeAction() {
        val launch = enqueue(request())
        val paused = DownloadTaskState(DownloadStage.Paused, launch.request)
        DownloadCoordinator.park(launch, paused)
        DownloadCoordinator.finishActiveLaunch(launch)
        NotificationController(service).notifyForegroundState(paused)
        val canceled = CountDownLatch(1)
        val subscription = DownloadCoordinator.addListener {
            if (it.stage == DownloadStage.Canceled) canceled.countDown()
        }
        try {
            service.onStartCommand(android.content.Intent(service, DownloadService::class.java)
                .setAction(DownloadService.ActionCancel)
                .putExtra(DownloadService.ExtraTaskId, launch.request.taskId), 0, 1)
            assertTrue(canceled.await(5, TimeUnit.SECONDS))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(DownloadStage.Canceled, DownloadCoordinator.currentState()!!.stage)
            val notification = service.getSystemService(android.app.NotificationManager::class.java)
                .activeNotifications.singleOrNull()?.notification
            assertTrue(notification == null || notification.actions.isNullOrEmpty())
            assertFalse(repository.workspace.exists(launch.request.taskId))
        } finally { subscription.close() }
    }
    @Test fun missingCookieSourceParksWithoutAnonymousNetworkOrTerminalHistory() {
        val launch = enqueue(request().copy(usesCookies = true))
        var called = false
        service.engineFactory = { called = true; error("must not download") }
        service.runLaunch(launch)
        assertFalse(called)
        assertEquals(DownloadStage.Paused, DownloadCoordinator.currentState()!!.stage)
        assertTrue(DownloadCoordinator.currentState()!!.statusMessage!!.contains("授权文件"))
        assertEquals(0, database.historyDao().listAll().size)
        assertTrue(repository.workspace.exists(launch.request.taskId))
    }
    @Test fun savedExportReceiptPreventsSecondCopyAfterHistoryFailure() {
        val request = request().copy(storageTarget = com.garyapp.ytdl.core.storage.StorageTarget.SafTree("content://documents/tree/original"))
        repository.create(request).getOrThrow()
        val file = File(repository.workspace.directory(request.taskId), "output.mp4").apply { writeText("complete") }
        repository.workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, "Completed",
            completedFiles = listOf(CompletedTaskFile("media", "18", file.name, file.length())))).getOrThrow()
        val output = DownloadOutputFile(DownloadOutputKind.Media, file.path, file.length(), root.path)
        val result = DownloadPipelineResult(DownloadTaskState(DownloadStage.Completed, request, listOf(output)), listOf(output))
        var copies = 0
        service.exportToTree = { _, _, _ -> copies++; listOf("content://documents/document/exact-output") }
        val exported = service.exportCompletedOutputs(result, request.storageTarget!!, NeverDownloadCancellation)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_history BEFORE INSERT ON history_items BEGIN SELECT RAISE(ABORT, 'blocked'); END")
        assertTrue(repository.recordTerminal(exported).isFailure)
        assertTrue(file.exists())
        val resumed = DownloadPipeline(BlockingEngine(CountDownLatch(0), CountDownLatch(0)),
            com.garyapp.ytdl.media.NativeMuxerMediaProcessor(root)).run(request, root)
        assertEquals(DownloadStage.Completed, resumed.state.stage)
        val again = service.exportCompletedOutputs(resumed, request.storageTarget!!, NeverDownloadCancellation)
        assertEquals(exported.outputs.map { it.externalDocumentUri }, again.outputs.map { it.externalDocumentUri })
        assertEquals(1, copies)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_history")
        repository.recordTerminal(again).getOrThrow()
        assertEquals(1, database.historyDao().listAll().size)
    }
    @Test fun lifecycleStopRetainsPartAndDoesNotWriteCancellationHistory() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val launch = enqueue(request())
        service.engineFactory = { BlockingEngine(entered, release) }
        val worker = Thread { service.runLaunch(launch) }.apply { start() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        service.onDestroy()
        assertTrue(worker.isAlive)
        release.countDown(); worker.join(5_000)
        assertFalse(worker.isAlive)
        assertTrue(File(repository.workspace.directory(launch.request.taskId), "video.part").exists())
        assertEquals("LifecycleEnd", repository.workspace.loadCheckpoint(launch.request.taskId).getOrThrow().stopReason)
        assertEquals(DownloadStage.Paused, DownloadCoordinator.currentState()!!.stage)
        assertTrue(database.historyDao().listAll().isEmpty())
    }

    @Test fun networkLossDuringExportWaitsAndAutomaticallyReusesCompletedPrivateFile() {
        val online = java.util.concurrent.atomic.AtomicBoolean(true)
        service.network = object : DownloadNetwork {
            override fun isAvailable() = online.get()
            override fun isStable() = online.get()
            override fun close() {}
        }
        val request = request().copy(storageTarget = com.garyapp.ytdl.core.storage.StorageTarget.SafTree("content://documents/tree/original"))
        val launch = enqueue(request)
        val file = File(repository.workspace.directory(request.taskId), "测试.mp4").apply { writeText("complete") }
        repository.workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, "Completed",
            completedFiles = listOf(CompletedTaskFile("media", "18", file.name, file.length())))).getOrThrow()
        val downloadCalled = CountDownLatch(1)
        service.engineFactory = { BlockingEngine(downloadCalled, CountDownLatch(0)) }
        val exportAttempts = java.util.concurrent.atomic.AtomicInteger()
        service.exportToTree = { treeUri, outputs, token ->
            assertEquals("content://documents/tree/original", treeUri)
            assertEquals(8L, outputs.single().bytesWritten)
            if (exportAttempts.incrementAndGet() == 1) {
                online.set(false)
                (token as MutableDownloadCancellation).waitForNetwork()
                throw java.util.concurrent.CancellationException("network stopped export")
            }
            listOf("content://documents/document/exact-output")
        }
        val waiting = CountDownLatch(1)
        val subscription = DownloadCoordinator.addListener { if (it.stage == DownloadStage.WaitingForNetwork) waiting.countDown() }
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val worker = Thread { runCatching { service.runLaunch(launch) }.onFailure(failure::set) }.apply { start() }
        try {
            assertTrue(waiting.await(5, TimeUnit.SECONDS))
            assertEquals(DownloadStage.WaitingForNetwork, DownloadCoordinator.currentState()!!.stage)
            assertEquals("Completed", repository.workspace.loadCheckpoint(request.taskId).getOrThrow().phase)
            assertEquals("NetworkUnavailable", repository.workspace.loadCheckpoint(request.taskId).getOrThrow().stopReason)
            assertTrue(file.exists())
            assertTrue(database.historyDao().listAll().isEmpty())
            online.set(true)
            worker.join(5_000)
            assertFalse(worker.isAlive)
            assertNull(failure.get())
            assertEquals(DownloadStage.Completed, DownloadCoordinator.currentState()!!.stage)
            assertEquals(2, exportAttempts.get())
            assertEquals(1L, downloadCalled.count)
            assertEquals(1, database.historyDao().listAll().size)
            assertFalse(repository.workspace.exists(request.taskId))
        } finally {
            online.set(true)
            worker.join(5_000)
            subscription.close()
        }
    }
    @Test fun pauseKeepsSlotUntilWorkerExitsThenReleasesForNextLaunch() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val launch = enqueue(request())
        service.engineFactory = { BlockingEngine(entered, release) }
        val next = request(); DownloadCoordinator.enqueueForServiceStart(next, root).getOrThrow()
        val worker = Thread { service.runLaunch(launch) }.apply { start() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        DownloadCoordinator.pauseActive()
        assertEquals(DownloadStage.Pausing, DownloadCoordinator.currentState()!!.stage)
        val warned = CountDownLatch(1)
        val subscription = DownloadCoordinator.addListener { if (it.statusMessage?.contains("安全停止") == true) warned.countDown() }
        service.watchStop()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(31))
        assertTrue(warned.await(2, TimeUnit.SECONDS))
        subscription.close()
        assertEquals(DownloadStage.Pausing, DownloadCoordinator.currentState()!!.stage)
        assertNull(DownloadCoordinator.claimPendingLaunch())
        assertTrue(worker.isAlive)
        release.countDown(); worker.join(5_000)
        assertFalse(worker.isAlive)
        assertEquals(DownloadStage.Paused, DownloadCoordinator.currentState()!!.stage)
        assertNull(DownloadCoordinator.claimPendingLaunch())
        DownloadCoordinator.finishActiveLaunch(launch)
        assertEquals(next.taskId, DownloadCoordinator.claimPendingLaunch()!!.request.taskId)
        assertTrue(database.historyDao().listAll().isEmpty())
    }

    @Test fun waitingForNetworkKeepsHeadUntilStableAndThenContinuesSameTask() {
        val online = java.util.concurrent.atomic.AtomicBoolean(false)
        service.network = object : DownloadNetwork {
            override fun isAvailable() = online.get()
            override fun isStable() = online.get()
            override fun close() {}
        }
        val launch = enqueue(request())
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val waiting = CountDownLatch(1)
        service.engineFactory = { BlockingEngine(entered, release) }
        val subscription = DownloadCoordinator.addListener { if (it.stage == DownloadStage.WaitingForNetwork) waiting.countDown() }
        DownloadCoordinator.enqueueForServiceStart(request(), root).getOrThrow()
        val worker = Thread { service.runLaunch(launch) }.apply { start() }
        assertTrue(waiting.await(5, TimeUnit.SECONDS))
        assertEquals(1L, entered.count)
        assertNull(DownloadCoordinator.claimPendingLaunch())
        online.set(true)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        DownloadCoordinator.pauseActive(); release.countDown(); worker.join(5_000)
        assertFalse(worker.isAlive)
        assertEquals(DownloadStage.Paused, DownloadCoordinator.currentState()!!.stage)
        subscription.close()
    }
    private class BlockingEngine(
        val entered: CountDownLatch,
        val release: CountDownLatch,
        val succeeds: Boolean = false,
    ) : DownloadEngine {
        override fun downloadFormat(url: String, outputDirectory: File, formatId: String, role: DownloadFormatRole, cookiesPath: String?, listener: DownloadProgressListener?, analysisKey: String?): Result<DownloadResult> = runCatching {
            File(outputDirectory, "video.part").writeText("partial")
            listener?.onProgress(DownloadProgress("downloading", 50.0, 7, 14, 1.0, null, null))
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
            listener?.onProgress(DownloadProgress("downloading", 50.0, 7, 14, 1.0, null, null))
            if (!succeeds) error("expected stop")
            val file = File(outputDirectory, "video.mp4").apply { writeText("completed") }
            DownloadResult(file.path, file.length(), "测试", formatId, role)
        }
        override fun downloadSubtitle(url: String, outputDirectory: File, language: String, ext: String, source: SubtitleSource, cookiesPath: String?, listener: DownloadProgressListener?): Result<SubtitleDownloadResult> = error("not used")
    }
}
