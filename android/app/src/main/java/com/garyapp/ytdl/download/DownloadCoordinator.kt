package com.garyapp.ytdl.download

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.util.ArrayDeque

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
    private val pendingListeners = linkedSetOf<(List<DownloadRequest>) -> Unit>()
    private val queuedLaunches = ArrayDeque<DownloadLaunch>()
    private val scheduledLaunches = mutableListOf<DownloadLaunch>()
    private var activeLaunch: DownloadLaunch? = null
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
            if (clearPendingLaunch(request)) {
                publish(DownloadTaskState.idle())
            }
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

    fun addPendingListener(listener: (List<DownloadRequest>) -> Unit): AutoCloseable {
        val requests = synchronized(lock) {
            pendingListeners += listener
            pendingRequestsLocked()
        }
        listener(requests)
        return AutoCloseable {
            synchronized(lock) {
                pendingListeners -= listener
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
            val waiting = DownloadTaskState.waiting(request)
            val publishWaiting = synchronized(lock) {
                outputDirectory.mkdirs()
                queuedLaunches.addLast(DownloadLaunch(request, outputDirectory))
                val hasActiveState = activeLaunch != null ||
                    currentState?.stage?.let { it !in TerminalStages } == true
                if (!hasActiveState) {
                    currentState = waiting
                }
                !hasActiveState
            }
            notifyPendingListeners()
            if (publishWaiting) {
                notifyStateListeners(waiting)
            }
            waiting
        }
    }

    fun <T> runWhenIdle(action: () -> T): IdleDownloadActionResult<T> {
        return synchronized(lock) {
            val hasActiveDownload = queuedLaunches.isNotEmpty() ||
                scheduledLaunches.isNotEmpty() ||
                activeLaunch != null ||
                currentState?.stage?.let { it !in TerminalStages } == true
            if (hasActiveDownload) {
                IdleDownloadActionResult.ActiveDownload
            } else {
                IdleDownloadActionResult.Executed(action())
            }
        }
    }

    internal fun claimPendingLaunch(): DownloadLaunch? {
        val launch = synchronized(lock) {
            queuedLaunches.pollFirst()?.also(scheduledLaunches::add)
        }
        if (launch != null) {
            notifyPendingListeners()
        }
        return launch
    }

    internal fun activateLaunch(launch: DownloadLaunch) {
        synchronized(lock) {
            scheduledLaunches.removeIdentity(launch)
            activeLaunch = launch
        }
        notifyPendingListeners()
    }

    internal fun finishActiveLaunch(launch: DownloadLaunch) {
        synchronized(lock) {
            if (activeLaunch === launch) {
                activeLaunch = null
            }
        }
    }

    private fun clearPendingLaunch(request: DownloadRequest): Boolean {
        val (removed, becameIdle) = synchronized(lock) {
            val queuedRemoved = queuedLaunches.removeFirstMatching { it.request === request }
            val scheduledIndex = scheduledLaunches.indexOfFirst { it.request === request }
            val scheduledRemoved = if (scheduledIndex >= 0) {
                scheduledLaunches.removeAt(scheduledIndex)
                true
            } else {
                false
            }
            val didRemove = queuedRemoved || scheduledRemoved
            val isIdle = didRemove && activeLaunch == null && queuedLaunches.isEmpty() && scheduledLaunches.isEmpty()
            if (isIdle && currentState?.request === request) {
                currentState = null
            }
            didRemove to isIdle
        }
        if (removed) {
            notifyPendingListeners()
        }
        return becameIdle
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
            pendingListeners.clear()
            queuedLaunches.clear()
            scheduledLaunches.clear()
            activeLaunch = null
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

    private fun notifyStateListeners(state: DownloadTaskState) {
        val snapshot = synchronized(lock) { listeners.toList() }
        snapshot.forEach { listener -> listener(state) }
    }

    private fun notifyPendingListeners() {
        val (requests, snapshot) = synchronized(lock) {
            pendingRequestsLocked() to pendingListeners.toList()
        }
        snapshot.forEach { listener -> listener(requests) }
    }

    private fun pendingRequestsLocked(): List<DownloadRequest> {
        return buildList {
            scheduledLaunches.forEach { add(it.request) }
            queuedLaunches.forEach { add(it.request) }
        }
    }

    private fun MutableList<DownloadLaunch>.removeIdentity(launch: DownloadLaunch): Boolean {
        val index = indexOfFirst { it === launch }
        if (index < 0) return false
        removeAt(index)
        return true
    }

    private fun ArrayDeque<DownloadLaunch>.removeFirstMatching(
        predicate: (DownloadLaunch) -> Boolean,
    ): Boolean {
        val iterator = iterator()
        while (iterator.hasNext()) {
            if (predicate(iterator.next())) {
                iterator.remove()
                return true
            }
        }
        return false
    }
}
