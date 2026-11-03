package com.garyapp.ytdl.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.media.NativeMuxerMediaProcessor
import com.garyapp.ytdl.storage.ExportController
import com.garyapp.ytdl.ui.prepareTemporaryCookiesForDownload
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DownloadService : Service() {
    private lateinit var notificationController: NotificationController
    private lateinit var retryDraftStore: RetryDraftStore
    internal lateinit var repository: ResumeTaskRepository
    internal lateinit var network: DownloadNetwork
    internal var engineFactory: () -> DownloadEngine = { YtdlpDownloadEngine(YtdlpBridge()) }
    internal var exportToTree: (String, List<ExportController.AppPrivateOutput>, DownloadCancellation) -> List<String> = { uri, outputs, token ->
        ExportController.copyToSafTreeWithDocuments(contentResolver, uri, outputs,
            isCancellationRequested = { token.isCancellationRequested }).getOrThrow().documentUris
    }
    private val launchExecutor = Executors.newSingleThreadExecutor()
    private val controlExecutor = Executors.newSingleThreadExecutor()
    private val draining = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var destroyed = false
    @Volatile private var running: DownloadLaunch? = null
    @Volatile private var cancellation: MutableDownloadCancellation? = null
    private var latestStartId = 0
    private var workerSubscription: AutoCloseable? = null

    override fun onCreate() {
        super.onCreate()
        notificationController = NotificationController(this)
        notificationController.ensureChannel()
        retryDraftStore = FileRetryDraftStore.fromContext(this)
        repository = ResumeTaskRepository.fromContext(this)
        network = NetworkMonitor(this) { cancellation?.waitForNetwork() }
        workerSubscription = DownloadCoordinator.addWorkerReleasedListener { mainHandler.post { if (!destroyed) drain() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        DownloadCoordinator.withCurrentState { startTypedForeground(it ?: DownloadTaskState.idle()) }
        if (intent?.action == ActionCancel) {
            val taskId = intent.getStringExtra(ExtraTaskId)
            controlExecutor.execute {
                DownloadCoordinator.restore(repository)
                if (taskId == null) DownloadCoordinator.cancelActive()
                else if (DownloadCoordinator.cancelPending(this, taskId).getOrNull() == false &&
                    !DownloadCoordinator.cancelActive(taskId)) DownloadCoordinator.cancelSaved(this, taskId)
            }
            watchStop()
        } else if (intent?.action == ActionPause) {
            val taskId = intent.getStringExtra(ExtraTaskId)
            controlExecutor.execute { DownloadCoordinator.pauseActive(taskId) }
            watchStop()
        } else if (intent?.action == ActionResume) {
            intent.getStringExtra(ExtraTaskId)?.let { id -> controlExecutor.execute {
                DownloadCoordinator.restore(repository)
                DownloadCoordinator.resume(this, id)
            } }
        }
        drain()
        return START_NOT_STICKY
    }

    private fun drain() {
        if (destroyed || !draining.compareAndSet(false, true)) return
        launchExecutor.execute {
            try {
                while (!destroyed) {
                    val launch = DownloadCoordinator.claimPendingLaunch() ?: break
                    running = launch
                    DownloadCoordinator.activateLaunch(launch)
                    try {
                        runLaunch(launch)
                    } catch (_: Exception) {
                        val state = (DownloadCoordinator.currentState() ?: DownloadTaskState.waiting(launch.request))
                            .copy(stage = DownloadStage.Paused, statusMessage = "任务信息保存失败，已保留文件，请重试。")
                        runCatching { DownloadCoordinator.park(launch, state) }
                        if (!destroyed) runCatching { publishForegroundState(state) }
                    } finally {
                        DownloadCoordinator.finishActiveLaunch(launch)
                        running = null
                    }
                }
            } finally {
                draining.set(false)
                mainHandler.post {
                    if (!destroyed) {
                        // Recheck after releasing the worker flag: a start command may have arrived during exit.
                        if (DownloadCoordinator.hasPendingLaunch()) drain()
                        else if (!DownloadCoordinator.hasWorker()) { stopForeground(STOP_FOREGROUND_DETACH); stopSelf(latestStartId) }
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        destroyed = true
        cancellation?.stopForLifecycle()
        DownloadCoordinator.stopForLifecycle(running)
        network.close()
        workerSubscription?.close()
        mainHandler.removeCallbacksAndMessages(null)
        // No interrupt, cancel, deletion, or new worker while native work still owns files.
        launchExecutor.shutdown()
        controlExecutor.shutdown()
        super.onDestroy()
    }

    internal fun watchStop() {
        val expected = running ?: return
        mainHandler.postDelayed({
            if (!destroyed && running === expected) controlExecutor.execute {
                val state = DownloadCoordinator.currentState() ?: return@execute
                if (state.request?.taskId == expected.request.taskId && cancellation?.stopReason != null) {
                    publishForegroundState(state.copy(stage = DownloadStage.Pausing,
                        statusMessage = "下载仍在安全停止中，请稍候；断点文件已保留。"))
                }
            }
        }, 30_000)
    }

    internal fun runLaunch(launch: DownloadLaunch) {
        running = launch
        launch.request.cookiesPath?.let { check(com.garyapp.ytdl.cookies.TemporaryCookiesFile.deleteIfManagedPath(it)) }
        var state = DownloadTaskState.waiting(launch.request)
        while (true) {
            val token = MutableDownloadCancellation()
            cancellation = token
            DownloadCoordinator.attachCancellation(token)
            if (destroyed) token.stopForLifecycle()
            try {
                if (!network.isAvailable() && token.stopReason == null) token.waitForNetwork()
                val temporaryCookies = if (launch.request.usesCookies && token.stopReason == null) {
                    prepareTemporaryCookiesForDownload(SettingsRepository.fromContext(this).getSettings().cookiesReference,
                        this, "resume-${launch.request.taskId}").getOrNull()
                        ?: run {
                            state = restoredState(launch).copy(stage = DownloadStage.Paused,
                                statusMessage = "授权文件读取失败，请在设置中重新选择后继续。")
                            DownloadCoordinator.park(launch, state)
                            publishForegroundState(state)
                            return
                        }
                } else null
                val request = launch.request.copy(cookiesPath = temporaryCookies?.file?.absolutePath)
                val pipeline = DownloadPipeline(engineFactory(),
                    NativeMuxerMediaProcessor(launch.outputDirectory), isNetworkAvailable = network::isAvailable)
                val result = pipeline.run(request, launch.outputDirectory, token) { next ->
                    if (next.stage != DownloadStage.Completed) publishForegroundState(next)
                }
                state = exportCompletedOutputs(result, request.storageTarget ?: StorageTarget.AppPrivate, token)
                if (token.stopReason == DownloadStopReason.UserCancel) state = state.copy(stopReason = DownloadStopReason.UserCancel)
                if (state.stage == DownloadStage.Failed && state.errorMessage?.contains("已保留文件") == true) {
                    state = state.copy(stage = DownloadStage.Paused, statusMessage = state.errorMessage)
                }
                if (state.stage == DownloadStage.WaitingForNetwork) {
                    publishForegroundState(state)
                    // This launch retains the sole slot and queue head. Polls are bounded and stoppable.
                    while (!destroyed && token.stopReason in setOf(null, DownloadStopReason.NetworkUnavailable) && !network.isStable()) {
                        Thread.sleep(200)
                    }
                    if (destroyed) token.stopForLifecycle()
                    if (token.stopReason in setOf(null, DownloadStopReason.NetworkUnavailable) && !destroyed) {
                        DownloadCoordinator.clearNetworkStop()
                        continue
                    }
                    state = stopRetained(launch, state, if (destroyed) DownloadStopReason.LifecycleEnd else token.stopReason ?: DownloadStopReason.LifecycleEnd)
                }
                if (state.stage == DownloadStage.Paused) {
                    DownloadCoordinator.park(launch, state)
                } else if (state.stage in ResumeTaskRepository.TerminalStages) {
                    val recorded = repository.recordTerminal(state)
                    val historyId = recorded.getOrNull()
                    if (historyId != null) updateRetryDraftForHistory(state, Result.success(historyId), retryDraftStore)
                    if (recorded.isFailure) {
                        state = state.copy(stage = DownloadStage.Paused, statusMessage = "历史保存失败，任务文件已保留，请继续重试。")
                        DownloadCoordinator.park(launch, state)
                    } else if (state.stage == DownloadStage.Completed && state.outputs.any { it.externalDocumentUri != null }) {
                        repository.workspace.deleteTask(launch.request.taskId)
                    }
                }
                publishForegroundState(state)
                return
            } finally {
                DownloadCoordinator.clearCancellation(token)
                if (cancellation === token) cancellation = null
            }
        }
    }

    private fun restoredState(launch: DownloadLaunch): DownloadTaskState {
        val checkpoint = PipelineCheckpoint.open(repository.workspace, launch.request)
        return DownloadTaskState(DownloadStage.Paused, launch.request.copy(cookiesPath = null), progress = checkpoint.progress())
    }

    private fun stopRetained(launch: DownloadLaunch, state: DownloadTaskState, reason: DownloadStopReason): DownloadTaskState {
        if (reason == DownloadStopReason.UserCancel) {
            return if (repository.workspace.deleteTask(launch.request.taskId).isSuccess) state.canceled().copy(outputs = emptyList())
            else state.copy(stage = DownloadStage.Paused, stopReason = DownloadStopReason.UserCancel, statusMessage = "任务文件待清理，已保留文件。")
        }
        val checkpoint = repository.workspace.loadCheckpoint(launch.request.taskId).getOrThrow()
        repository.workspace.saveCheckpoint(launch.request.taskId, checkpoint.copy(stopReason = reason.name)).getOrThrow()
        return state.copy(stage = if (reason == DownloadStopReason.NetworkUnavailable) DownloadStage.WaitingForNetwork else DownloadStage.Paused, stopReason = reason,
            progress = state.progress?.copy(speedBytesPerSecond = 0.0, etaSeconds = null))
    }

    internal fun exportCompletedOutputs(result: DownloadPipelineResult, target: StorageTarget, token: DownloadCancellation): DownloadTaskState {
        if (result.state.stage != DownloadStage.Completed) return result.state
        val request = requireNotNull(result.state.request)
        val outputRoot = requireNotNull(repository.workspace.directory(request.taskId).parentFile)
        val outputs = result.outputs.map { output ->
            ExportController.discoverAppPrivateOutput(File(output.path), outputRoot).getOrThrow()
        }
        if (token.stopReason != null) return stopRetained(requireNotNull(running), result.state, requireNotNull(token.stopReason))
        if (target !is StorageTarget.SafTree) {
            ExportController.markAppPrivateTaskCompleted(outputs).getOrThrow()
            return result.state
        }
        repository.workspace.loadExportedDocuments(request.taskId).getOrThrow()?.let { uris ->
            return result.state.withExternalDocumentUris(uris).getOrThrow()
        }
        publishForegroundState(result.state.copy(stage = DownloadStage.Exporting))
        return runCatching {
            val documentUris = exportToTree(target.treeUri, outputs, token)
            repository.workspace.saveExportedDocuments(request.taskId, documentUris).onFailure {
                documentUris.forEach { uri ->
                    runCatching { android.provider.DocumentsContract.deleteDocument(contentResolver, android.net.Uri.parse(uri)) }
                }
            }.getOrThrow()
            val state = result.state.withExternalDocumentUris(documentUris).getOrThrow()
            state
        }.getOrElse { error ->
            if (error is CancellationException && token.stopReason != null) {
                stopRetained(requireNotNull(running), result.state, requireNotNull(token.stopReason))
            } else result.state.copy(stage = DownloadStage.Paused,
                statusMessage = "保存失败，成品已保留；检查原保存目录权限后继续。")
        }
    }

    private fun publishForegroundState(state: DownloadTaskState) {
        if (destroyed) return
        DownloadCoordinator.publish(state)
        notificationController.notifyForegroundState(state)
    }
    private fun startTypedForeground(state: DownloadTaskState) {
        val notification = notificationController.buildForegroundNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NotificationController.NotificationId,
            notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
        else startForeground(NotificationController.NotificationId, notification)
    }
    companion object {
        const val ActionStart = "com.garyapp.ytdl.download.START"
        const val ActionCancel = "com.garyapp.ytdl.download.CANCEL"
        const val ActionPause = "com.garyapp.ytdl.download.PAUSE"
        const val ActionResume = "com.garyapp.ytdl.download.RESUME"
        const val ExtraTaskId = "taskId"
    }
}
