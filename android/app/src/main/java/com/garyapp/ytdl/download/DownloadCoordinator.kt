package com.garyapp.ytdl.download

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File

data class DownloadLaunch(
    val request: DownloadRequest,
    val outputDirectory: File,
)

object DownloadCoordinator {
    private val lock = Any()
    private val listeners = linkedSetOf<(DownloadTaskState) -> Unit>()
    private var pendingLaunch: DownloadLaunch? = null
    private var currentState: DownloadTaskState? = null
    private var activeCancellation: MutableDownloadCancellation? = null

    fun startForegroundDownload(
        context: Context,
        request: DownloadRequest,
        outputDirectory: File,
    ): Result<DownloadTaskState> {
        val waiting = enqueueForServiceStart(request, outputDirectory)
            .getOrElse { return Result.failure(it) }
        return try {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(DownloadService.ActionStart)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Result.success(waiting)
        } catch (exc: Exception) {
            clearPendingLaunch(request)
            publish(DownloadTaskState.idle())
            Result.failure(exc)
        }
    }

    fun addListener(listener: (DownloadTaskState) -> Unit): AutoCloseable {
        val state = synchronized(lock) {
            listeners += listener
            currentState
        }
        state?.let(listener)
        return AutoCloseable {
            synchronized(lock) {
                listeners -= listener
            }
        }
    }

    fun publish(state: DownloadTaskState) {
        val snapshot = synchronized(lock) {
            currentState = state
            listeners.toList()
        }
        snapshot.forEach { listener -> listener(state) }
    }

    internal fun enqueueForServiceStart(
        request: DownloadRequest,
        outputDirectory: File,
    ): Result<DownloadTaskState> {
        return runCatching {
            outputDirectory.mkdirs()
            val waiting = DownloadTaskState.waiting(request)
            synchronized(lock) {
                pendingLaunch = DownloadLaunch(request, outputDirectory)
                currentState = waiting
            }
            publish(waiting)
            waiting
        }
    }

    internal fun consumePendingLaunch(): DownloadLaunch? {
        return synchronized(lock) {
            pendingLaunch.also {
                pendingLaunch = null
            }
        }
    }

    private fun clearPendingLaunch(request: DownloadRequest) {
        synchronized(lock) {
            if (pendingLaunch?.request === request) {
                pendingLaunch = null
                currentState = null
            }
        }
    }

    internal fun attachCancellation(cancellation: MutableDownloadCancellation) {
        synchronized(lock) {
            activeCancellation = cancellation
        }
    }

    internal fun clearCancellation(cancellation: MutableDownloadCancellation) {
        synchronized(lock) {
            if (activeCancellation === cancellation) {
                activeCancellation = null
            }
        }
    }

    fun cancelActive() {
        synchronized(lock) {
            activeCancellation
        }?.cancel()
    }

    internal fun resetForTests() {
        synchronized(lock) {
            listeners.clear()
            pendingLaunch = null
            currentState = null
            activeCancellation = null
        }
    }
}
