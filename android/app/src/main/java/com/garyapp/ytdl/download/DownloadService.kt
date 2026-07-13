package com.garyapp.ytdl.download

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.data.YtdlDatabaseProvider
import com.garyapp.ytdl.media.NativeMuxerMediaProcessor
import com.garyapp.ytdl.storage.ExportController
import java.io.File
import java.util.concurrent.CancellationException

class DownloadService : Service() {
    private lateinit var notificationController: NotificationController
    private lateinit var historyRecorder: DownloadHistoryRecorder

    override fun onCreate() {
        super.onCreate()
        notificationController = NotificationController(this)
        notificationController.ensureChannel()
        historyRecorder = DownloadHistoryRecorder(
            historyDao = YtdlDatabaseProvider.get(this).historyDao(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionCancel) {
            DownloadCoordinator.cancelActive()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val launch = DownloadCoordinator.consumePendingLaunch()
        if (launch == null) {
            val idle = DownloadTaskState.idle()
            startTypedForeground(idle)
            DownloadCoordinator.publish(idle)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val state = DownloadTaskState.waiting(launch.request)
        startTypedForeground(state)
        DownloadCoordinator.publish(state)
        val storageTarget = SettingsRepository.fromContext(this).getSettings().defaultStorageTarget

        Thread {
            runLaunch(launch, storageTarget, startId)
        }.start()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runLaunch(
        launch: DownloadLaunch,
        storageTarget: StorageTarget,
        startId: Int,
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
            val finalState = applyHistoryRecordingResult(
                state = terminalState,
                recordResult = historyRecorder.recordTerminal(terminalState),
            )
            publishForegroundState(finalState)
        } finally {
            DownloadCoordinator.clearCancellation(cancellation)
            stopForegroundAfterTerminalState()
            stopSelf(startId)
        }
    }

    private fun exportCompletedOutputs(
        result: DownloadPipelineResult,
        storageTarget: StorageTarget,
        cancellation: DownloadCancellation,
    ): DownloadTaskState {
        if (result.state.stage != DownloadStage.Completed || storageTarget !is StorageTarget.SafTree) {
            return result.state
        }

        publishForegroundState(result.state.atStage(DownloadStage.Exporting))
        val exportResult = runCatching {
            val outputs = result.outputs.map { output ->
                val root = output.appPrivateRootPath?.let(::File)
                    ?: throw IllegalStateException("缺少私有输出目录。")
                ExportController.discoverAppPrivateOutput(File(output.path), root).getOrThrow()
            }
            ExportController.copyToSafTree(
                contentResolver = contentResolver,
                treeUri = storageTarget.treeUri,
                outputs = outputs,
                isCancellationRequested = { cancellation.isCancellationRequested },
            ).getOrThrow()
        }
        return exportResult.fold(
            onSuccess = { result.state },
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
        const val ActionStart = "com.garyapp.ytdl.download.START"
        const val ActionCancel = "com.garyapp.ytdl.download.CANCEL"
    }
}
