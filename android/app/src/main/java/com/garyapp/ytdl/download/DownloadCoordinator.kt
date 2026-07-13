package com.garyapp.ytdl.download

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File

data class DownloadLaunch(
    val request: DownloadRequest,
    val outputDirectory: File,
)

sealed interface IdleDownloadActionResult<out T> {
    data object ActiveDownload : IdleDownloadActionResult<Nothing>
    data class Executed<T>(val value: T) : IdleDownloadActionResult<T>
}

object DownloadCoordinator {
    private val lock = Any()
    private val listeners = linkedSetOf<(DownloadTaskState) -> Unit>()
    private var pendingLaunch: DownloadLaunch? = null
    private var currentState: DownloadTaskState? = null
    private var activeCancellation: MutableDownloadCancellation? = null
    private var cancellationRequested = false

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
            synchronized(lock) {
                outputDirectory.mkdirs()
                val waiting = DownloadTaskState.waiting(request)
                pendingLaunch = DownloadLaunch(request, outputDirectory)
                cancellationRequested = false
                currentState = waiting
                waiting
            }
                .also(::publish)
        }
    }

    fun <T> runWhenIdle(action: () -> T): IdleDownloadActionResult<T> {
        return synchronized(lock) {
            val hasActiveDownload = pendingLaunch != null ||
                currentState?.stage?.let { it !in TerminalStages } == true
            if (hasActiveDownload) {
                IdleDownloadActionResult.ActiveDownload
            } else {
                IdleDownloadActionResult.Executed(action())
            }
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
        val shouldCancel = synchronized(lock) {
            activeCancellation = cancellation
            cancellationRequested
        }
        if (shouldCancel) {
            cancellation.cancel()
        }
    }

    internal fun clearCancellation(cancellation: MutableDownloadCancellation) {
        synchronized(lock) {
            if (activeCancellation === cancellation) {
                activeCancellation = null
                cancellationRequested = false
            }
        }
    }

    fun cancelActive() {
        val cancellation = synchronized(lock) {
            if (currentState?.stage?.let { it !in TerminalStages } == true) {
                cancellationRequested = true
            }
            activeCancellation
        }
        cancellation?.cancel()
    }

    internal fun resetForTests() {
        synchronized(lock) {
            listeners.clear()
            pendingLaunch = null
            currentState = null
            activeCancellation = null
            cancellationRequested = false
        }
    }

    private val TerminalStages = setOf(
        DownloadStage.Completed,
        DownloadStage.Failed,
        DownloadStage.Canceled,
        DownloadStage.Idle,
    )
}
