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
    private class BlockingEngine(val entered: CountDownLatch, val release: CountDownLatch) : DownloadEngine {
        override fun downloadFormat(url: String, outputDirectory: File, formatId: String, role: DownloadFormatRole, cookiesPath: String?, listener: DownloadProgressListener?, analysisKey: String?): Result<DownloadResult> = runCatching {
            File(outputDirectory, "video.part").writeText("partial")
            listener?.onProgress(DownloadProgress("downloading", 50.0, 7, 14, 1.0, null, null))
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
            listener?.onProgress(DownloadProgress("downloading", 50.0, 7, 14, 1.0, null, null))
            error("expected stop")
        }
        override fun downloadSubtitle(url: String, outputDirectory: File, language: String, ext: String, source: SubtitleSource, cookiesPath: String?, listener: DownloadProgressListener?): Result<SubtitleDownloadResult> = error("not used")
    }
}
