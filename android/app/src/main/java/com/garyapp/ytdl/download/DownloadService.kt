package com.garyapp.ytdl.download

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.data.YtdlDatabaseProvider
import com.garyapp.ytdl.media.NativeMuxerMediaProcessor

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
            startForeground(
                NotificationController.NotificationId,
                notificationController.buildForegroundNotification(idle),
            )
            DownloadCoordinator.publish(idle)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val state = DownloadTaskState.waiting(launch.request)
        startForeground(
            NotificationController.NotificationId,
            notificationController.buildForegroundNotification(state),
        )
        DownloadCoordinator.publish(state)

        Thread {
            runLaunch(launch, startId)
        }.start()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runLaunch(launch: DownloadLaunch, startId: Int) {
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
                publishForegroundState(state)
            }
            val finalState = applyHistoryRecordingResult(
                state = result.state,
                recordResult = historyRecorder.recordTerminal(result.state),
            )
            publishForegroundState(finalState)
        } finally {
            DownloadCoordinator.clearCancellation(cancellation)
            stopForegroundAfterTerminalState()
            stopSelf(startId)
        }
    }

    private fun publishForegroundState(state: DownloadTaskState) {
        DownloadCoordinator.publish(state)
        notificationController.notifyForegroundState(state)
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
