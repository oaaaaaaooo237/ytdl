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
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {
    private lateinit var notificationController: NotificationController
    private lateinit var retryDraftStore: RetryDraftStore
    internal lateinit var repository: ResumeTaskRepository
    internal lateinit var network: DownloadNetwork
    internal var engineFactory: () -> DownloadEngine = { YtdlpDownloadEngine(YtdlpBridge()) }
    internal var exportToTree: (String, List<ExportController.AppPrivateOutput>, DownloadCancellation) -> List<String> = { uri, outputs, token ->
        synchronized(SafExportLock) {
            ExportController.copyToSafTreeWithDocuments(contentResolver, uri, outputs,
                isCancellationRequested = { token.isCancellationRequested }).getOrThrow().documentUris
        }
    }
    private val launchExecutor = Executors.newFixedThreadPool(3)
    private val controlExecutor = Executors.newSingleThreadExecutor()
    private val running = ConcurrentHashMap.newKeySet<DownloadLaunch>()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var destroyed = false
    private var latestStartId = 0
    private var foregroundStarted = false
    private var pendingControls = 0
    private var workerSubscription: AutoCloseable? = null
    private var stateSubscription: AutoCloseable? = null
    private var settingsSubscription: AutoCloseable? = null

    override fun onCreate() {
        super.onCreate()
        notificationController = NotificationController(this)
        notificationController.ensureChannel()
        retryDraftStore = FileRetryDraftStore.fromContext(this)
        repository = ResumeTaskRepository.fromContext(this)
        network = NetworkMonitor(this) {
            running.forEach(DownloadCoordinator::waitForNetwork)
        }
        workerSubscription = DownloadCoordinator.addWorkerReleasedListener { mainHandler.post { drain() } }
        stateSubscription = DownloadCoordinator.addActiveListener {
            mainHandler.post { if (!destroyed && foregroundStarted) refreshForeground() }
        }
        settingsSubscription = SettingsRepository.observeConcurrency(this) { mainHandler.post { drain() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val active = DownloadCoordinator.activeStates()
        startTypedForeground(foregroundState(active), active.size)
        foregroundStarted = true
        val taskId = intent?.getStringExtra(ExtraTaskId)
        when (intent?.action) {
            ActionCancel -> runControl {
                DownloadCoordinator.restore(repository)
                if (taskId == null) DownloadCoordinator.cancelActive()
                else if (DownloadCoordinator.cancelPending(this, taskId).getOrNull() == false &&
                    !DownloadCoordinator.cancelActive(taskId)) DownloadCoordinator.cancelSaved(this, taskId)
            }
            ActionPause -> runControl { DownloadCoordinator.pauseActive(taskId, notificationController::notifyPaused) }
            ActionResume -> if (taskId == null) drain() else runControl {
                DownloadCoordinator.restore(repository)
                DownloadCoordinator.resume(this, taskId)
            }
            else -> drain()
        }
        if (intent?.action in setOf(ActionPause, ActionCancel)) watchStop(taskId)
        return START_NOT_STICKY
    }

    private fun runControl(action: () -> Unit) {
        pendingControls++
        controlExecutor.execute {
            try {
                if (!destroyed) action()
            } finally {
                mainHandler.post { pendingControls--; drain() }
            }
        }
    }

    // All claims and shutdown are serialized on the service's main thread.
    private fun drain() {
        if (destroyed || !foregroundStarted || pendingControls != 0) return
        val limit = SettingsRepository.fromContext(this).getSettings().maxConcurrentDownloads
        while (true) {
            val launch = DownloadCoordinator.claimPendingLaunch(limit) ?: break
            running += launch
            DownloadCoordinator.activateLaunch(launch)
            launchExecutor.execute {
                try {
                    runLaunch(launch)
                } catch (_: Exception) {
                    val state = (DownloadCoordinator.activeStates()[launch.request.taskId] ?: DownloadTaskState.waiting(launch.request))
                        .copy(stage = DownloadStage.Paused, statusMessage = "任务信息保存失败，已保留文件，请重试。")
                    runCatching { finalizeLaunch(launch, state) }
                } finally {
                    running -= launch
                    DownloadCoordinator.finishActiveLaunch(launch)
                }
            }
        }
        refreshForeground()
        if (!DownloadCoordinator.hasWorker() && !DownloadCoordinator.hasPendingLaunch()) {
            foregroundStarted = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(latestStartId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        destroyed = true
        running.forEach(DownloadCoordinator::stopForLifecycle)
        network.close()
        workerSubscription?.close()
        stateSubscription?.close()
        settingsSubscription?.close()
        mainHandler.removeCallbacksAndMessages(null)
        // Workers keep their slots until the pipelines stop writing and persist their checkpoints.
        launchExecutor.shutdown()
        controlExecutor.shutdown()
        super.onDestroy()
    }

    internal fun watchStop(taskId: String? = null) {
        val expected = running.firstOrNull { taskId == null || it.request.taskId == taskId } ?: return
        mainHandler.postDelayed({
            if (!destroyed && expected in running) controlExecutor.execute {
                DownloadCoordinator.warnIfStopping(expected)
            }
        }, 30_000)
    }

    internal fun runLaunch(launch: DownloadLaunch) {
        val addedHere = running.add(launch)
        try {
            executeLaunch(launch)
        } finally {
            if (addedHere) running -= launch
        }
    }

    private fun executeLaunch(launch: DownloadLaunch) {
        val taskId = launch.request.taskId
        launch.request.cookiesPath?.let { check(com.garyapp.ytdl.cookies.TemporaryCookiesFile.deleteIfManagedPath(it)) }
        var state = DownloadTaskState.waiting(launch.request)
        while (true) {
            val token = MutableDownloadCancellation()
            DownloadCoordinator.attachCancellation(token, taskId)
            if (destroyed) token.stopForLifecycle()
            try {
                if (!network.isAvailable() && token.stopReason == null) token.waitForNetwork()
                val temporaryCookies = if (launch.request.usesCookies && launch.request.route !is DownloadRoute.HttpFile && token.stopReason == null) {
                    prepareTemporaryCookiesForDownload(launch.request.cookiesReferenceFor(SettingsRepository.fromContext(this).getSettings()),
                        this, "resume-${launch.request.taskId}").getOrNull()
                        ?: run {
                            state = restoredState(launch).copy(stage = DownloadStage.Paused,
                                statusMessage = "授权文件读取失败，请在设置中重新选择后继续。")
                            finalizeLaunch(launch, state)
                            return
                        }
                } else null
                val request = launch.request.copy(cookiesPath = temporaryCookies?.file?.absolutePath)
                val onState: (DownloadTaskState) -> Unit = { next ->
                    if (next.stage !in ResumeTaskRepository.TerminalStages) publishForegroundState(next)
                }
                val result = if (request.route is DownloadRoute.HttpFile) {
                    HttpFilePipeline().run(request, launch.outputDirectory, token, onState)
                } else {
                    DownloadPipeline(engineFactory(), NativeMuxerMediaProcessor(launch.outputDirectory),
                        isNetworkAvailable = network::isAvailable).run(request, launch.outputDirectory, token, onState)
                }
                state = exportCompletedOutputs(result, request.storageTarget ?: StorageTarget.AppPrivate, token)
                if (state.stage == DownloadStage.Failed && state.fileRecoveryAction == null && state.errorMessage?.contains("已保留文件") == true) {
                    state = state.copy(stage = DownloadStage.Paused, statusMessage = state.errorMessage)
                }
                if (state.stage == DownloadStage.WaitingForNetwork) {
                    publishForegroundState(state)
                    // A network-waiting launch retains its own slot until retry or stop.
                    while (!destroyed && token.stopReason in setOf(null, DownloadStopReason.NetworkUnavailable) && !network.isStable()) {
                        Thread.sleep(200)
                    }
                    if (destroyed) token.stopForLifecycle()
                    if (token.stopReason in setOf(null, DownloadStopReason.NetworkUnavailable) && !destroyed) {
                        DownloadCoordinator.clearNetworkStop(taskId)
                        continue
                    }
                    state = stopRetained(launch, state, token.stopReason?.takeUnless { destroyed && it == DownloadStopReason.NetworkUnavailable } ?: DownloadStopReason.LifecycleEnd)
                }
                finalizeLaunch(launch, state)
                return
            } finally {
                DownloadCoordinator.clearCancellation(token, taskId)
            }
        }
    }

    private fun finalizeLaunch(launch: DownloadLaunch, state: DownloadTaskState) {
        DownloadCoordinator.finalizeLaunch(launch, complete = { cancelRequested ->
            val finalState = if (cancelRequested && state.stage != DownloadStage.Canceled) {
                stopRetained(launch, state, DownloadStopReason.UserCancel)
            } else state
            if (finalState.stage in ResumeTaskRepository.TerminalStages && finalState.fileRecoveryAction == null) {
                val recorded = repository.recordTerminal(finalState)
                val historyId = recorded.getOrNull()
                if (historyId != null) updateRetryDraftForHistory(finalState, Result.success(historyId), retryDraftStore)
                if (recorded.isFailure) {
                    return@finalizeLaunch finalState.copy(stage = DownloadStage.Paused,
                        statusMessage = "历史保存失败，任务文件已保留，请继续重试。")
                }
                if (finalState.stage == DownloadStage.Completed && finalState.outputs.any { it.externalDocumentUri != null }) {
                    repository.workspace.deleteTask(launch.request.taskId)
                }
            }
            finalState
        }, onFinalized = { finalState ->
            if (!destroyed) runCatching {
                if (finalState.stage == DownloadStage.Paused) notificationController.notifyPaused(finalState)
                else notificationController.notifyResult(finalState, SettingsRepository.fromContext(this).getSettings())
            }
        })
    }

    private fun restoredState(launch: DownloadLaunch): DownloadTaskState {
        val checkpoint = PipelineCheckpoint.open(repository.workspace, launch.request)
        return DownloadTaskState(DownloadStage.Paused, launch.request.copy(cookiesPath = null), progress = checkpoint.progress(), fileRecoveryAction = checkpoint.fileRecoveryAction)
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
            ExportController.discoverAppPrivateOutput(File(output.path), outputRoot,
                allowEmptyFile = output.kind == DownloadOutputKind.File,
                mimeType = output.resolvedMimeType(request),
                askOnConflict = (request.route as? DownloadRoute.HttpFile)?.conflictPolicy == HttpFileConflictPolicy.Ask,
                displayName = (request.route as? DownloadRoute.HttpFile)?.fileName).getOrThrow()
        }
        if (token.stopReason != null) return stopRetained(DownloadLaunch(request, outputRoot), result.state, requireNotNull(token.stopReason))
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
                stopRetained(DownloadLaunch(request, outputRoot), result.state, requireNotNull(token.stopReason))
            } else if (error is ExportController.NameConflictException && request.route is DownloadRoute.HttpFile) {
                val conflict = result.state.failed("文件名已存在，请选择其他名称。").copy(fileRecoveryAction = FileRecoveryAction.ChooseFileName)
                PipelineCheckpoint.open(repository.workspace, request).save(conflict.stage, conflict.progress, conflict.stopReason, conflict.fileRecoveryAction)
                conflict
            } else result.state.copy(stage = DownloadStage.Paused,
                statusMessage = "保存失败，成品已保留；检查原保存目录权限后继续。")
        }
    }

    private fun publishForegroundState(state: DownloadTaskState) {
        DownloadCoordinator.publish(state)
    }

    private fun foregroundState(active: Map<String, DownloadTaskState> = DownloadCoordinator.activeStates()): DownloadTaskState =
        active.values.firstOrNull { it.stage !in ResumeTaskRepository.TerminalStages && it.stage != DownloadStage.Paused }
            ?: active.values.firstOrNull { it.stage == DownloadStage.Paused }
            ?: DownloadCoordinator.currentState()?.takeUnless { it.stage in ResumeTaskRepository.TerminalStages }
            ?: DownloadTaskState.idle()

    private fun refreshForeground() {
        val active = DownloadCoordinator.activeStates()
        val state = foregroundState(active)
        if (state.stage != DownloadStage.Idle) notificationController.notifyForegroundState(state, active.size)
    }

    private fun startTypedForeground(state: DownloadTaskState, activeCount: Int) {
        val notification = notificationController.buildForegroundNotification(state, activeCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NotificationController.NotificationId,
            notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
        else startForeground(NotificationController.NotificationId, notification)
    }
    companion object {
        // Serialize SAF name checks and creation across transfers to preserve the Ask policy.
        private val SafExportLock = Any()
        const val ActionStart = "com.garyapp.ytdl.download.START"
        const val ActionCancel = "com.garyapp.ytdl.download.CANCEL"
        const val ActionPause = "com.garyapp.ytdl.download.PAUSE"
        const val ActionResume = "com.garyapp.ytdl.download.RESUME"
        const val ExtraTaskId = "taskId"
    }
}
