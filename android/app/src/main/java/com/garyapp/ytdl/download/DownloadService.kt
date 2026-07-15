package com.garyapp.ytdl.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.data.YtdlDatabaseProvider
import com.garyapp.ytdl.media.NativeMuxerMediaProcessor
import com.garyapp.ytdl.storage.ExportController
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DownloadService : Service() {
    private lateinit var notificationController: NotificationController
    private lateinit var historyRecorder: DownloadHistoryRecorder
    private lateinit var retryDraftStore: RetryDraftStore
    private lateinit var launchExecutor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scheduledLaunchCount = 0
    private var latestStartId = 0

    override fun onCreate() {
        super.onCreate()
        notificationController = NotificationController(this)
        notificationController.ensureChannel()
        historyRecorder = DownloadHistoryRecorder(
            historyDao = YtdlDatabaseProvider.get(this).historyDao(),
        )
        retryDraftStore = FileRetryDraftStore.fromContext(this)
        launchExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ActionCancel) {
            DownloadCoordinator.cancelActive()
            if (scheduledLaunchCount == 0) {
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        val launch = DownloadCoordinator.claimPendingLaunch()
        if (launch == null) {
            if (scheduledLaunchCount == 0) {
                val idle = DownloadTaskState.idle()
                startTypedForeground(idle)
                DownloadCoordinator.publish(idle)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        val state = DownloadTaskState.waiting(launch.request)
        scheduledLaunchCount += 1
        if (scheduledLaunchCount == 1) {
            startTypedForeground(state)
        }
        launchExecutor.execute {
            DownloadCoordinator.activateLaunch(launch)
            publishForegroundState(state)
            val storageTarget = SettingsRepository.fromContext(this).getSettings().defaultStorageTarget
            try {
                runLaunch(launch, storageTarget)
            } finally {
                DownloadCoordinator.finishActiveLaunch(launch)
                mainHandler.post(::onScheduledLaunchFinished)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        DownloadCoordinator.cancelActive()
        launchExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun runLaunch(
        launch: DownloadLaunch,
        storageTarget: StorageTarget,
    ) {
        val cancellation = MutableDownloadCancellation()
        DownloadCoordinator.attachCancellation(cancellation)
        try {
            val pipeline = DownloadPipeline(
                engine = YtdlpDownloadEngine(YtdlpBridge()),
                mediaProcessor = NativeMuxerMediaProcessor(launch.outputDirectory),
            )
            val result = pipeline.run(
                request = launch.request,
                outputDirectory = launch.outputDirectory,
                cancellation = cancellation,
            ) { state ->
                if (state.stage != DownloadStage.Completed || storageTarget !is StorageTarget.SafTree) {
                    publishForegroundState(state)
                }
            }
            val terminalState = exportCompletedOutputs(result, storageTarget, cancellation)
            val historyRecordResult = historyRecorder.recordTerminal(terminalState)
            updateRetryDraftForHistory(
                state = terminalState,
                historyRecordResult = historyRecordResult,
                retryStore = retryDraftStore,
            ).onFailure {
                Log.w(LogTag, "Retry draft persistence did not complete.", it)
            }
            val finalState = applyHistoryRecordingResult(
                state = terminalState,
                recordResult = historyRecordResult,
            )
            publishForegroundState(finalState)
        } finally {
            DownloadCoordinator.clearCancellation(cancellation)
        }
    }

    private fun onScheduledLaunchFinished() {
        scheduledLaunchCount = (scheduledLaunchCount - 1).coerceAtLeast(0)
        if (scheduledLaunchCount == 0) {
            stopForegroundAfterTerminalState()
            stopSelf(latestStartId)
        }
    }

    private fun exportCompletedOutputs(
        result: DownloadPipelineResult,
        storageTarget: StorageTarget,
        cancellation: DownloadCancellation,
    ): DownloadTaskState {
        if (result.state.stage != DownloadStage.Completed) {
            return result.state
        }

        val outputs = result.outputs.map { output ->
            val root = output.appPrivateRootPath?.let(::File)
                ?: return result.state.failed("缺少私有输出目录。")
            ExportController.discoverAppPrivateOutput(File(output.path), root).getOrElse {
                return result.state.failed("无法确认 App 私有输出文件。")
            }
        }
        if (storageTarget !is StorageTarget.SafTree) {
            return ExportController.markAppPrivateTaskCompleted(outputs).fold(
                onSuccess = { result.state },
                onFailure = { result.state.failed("无法保护 App 私有完成文件。") },
            )
        }

        publishForegroundState(result.state.atStage(DownloadStage.Exporting))
        val exportResult = runCatching {
            val safExport = ExportController.copyToSafTreeWithDocuments(
                contentResolver = contentResolver,
                treeUri = storageTarget.treeUri,
                outputs = outputs,
                isCancellationRequested = { cancellation.isCancellationRequested },
            ).getOrThrow()
            val exportedState = result.state.withExternalDocumentUris(safExport.documentUris).getOrThrow()
            ExportController.cleanupExportedPrivateTask(outputs).onFailure {
                Log.w(LogTag, "App private staging cleanup did not complete.")
            }
            exportedState
        }
        return exportResult.fold(
            onSuccess = { it },
            onFailure = { error ->
                when (error) {
                    is CancellationException -> result.state.canceled()
                    else -> result.state.failed(
                        message = ExportController.treeExportFailureMessage(),
                        outputs = result.outputs,
                    )
                }
            },
        )
    }

    private fun publishForegroundState(state: DownloadTaskState) {
        DownloadCoordinator.publish(state)
        notificationController.notifyForegroundState(state)
    }

    private fun startTypedForeground(state: DownloadTaskState) {
        val notification = notificationController.buildForegroundNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationController.NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST,
            )
        } else {
            startForeground(NotificationController.NotificationId, notification)
        }
    }

    private fun stopForegroundAfterTerminalState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    companion object {
        private const val LogTag = "YtdlDownloadService"
        const val ActionStart = "com.garyapp.ytdl.download.START"
        const val ActionCancel = "com.garyapp.ytdl.download.CANCEL"
    }
}
