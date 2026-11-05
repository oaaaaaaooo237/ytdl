package com.garyapp.ytdl.download

import android.content.Context
import android.content.Intent
import android.os.Build
import com.garyapp.ytdl.core.settings.SettingsRepository
import java.io.File
import java.util.ArrayDeque

data class DownloadLaunch(val request: DownloadRequest, val outputDirectory: File)

sealed interface IdleDownloadActionResult<out T> {
    data object ActiveDownload : IdleDownloadActionResult<Nothing>
    data class Executed<T>(val value: T) : IdleDownloadActionResult<T>
}

object DownloadCoordinator {
    private val lock = Any()
    private val listeners = linkedSetOf<(DownloadTaskState) -> Unit>()
    private val pendingListeners = linkedSetOf<(List<DownloadRequest>) -> Unit>()
    private val savedListeners = linkedSetOf<(List<ResumeTaskItem>) -> Unit>()
    private val workerReleasedListeners = linkedSetOf<() -> Unit>()
    private val queuedLaunches = ArrayDeque<DownloadLaunch>()
    private val saved = linkedMapOf<String, ResumeTaskItem>()
    private var claimedLaunch: DownloadLaunch? = null
    private var activeLaunch: DownloadLaunch? = null
    private var currentState: DownloadTaskState? = null
    private var activeCancellation: MutableDownloadCancellation? = null
    private var requestedStop: DownloadStopReason? = null
    private var repository: ResumeTaskRepository? = null
    private var restored = false
    private var lastSavedAt = 0L
    private var lastSavedStage: DownloadStage? = null

    // Called on a background executor. Restoration never starts a service or requests media.
    fun restore(context: Context) {
        synchronized(lock) { if (restored) return }
        restore(ResumeTaskRepository.fromContext(context))
    }
    internal fun restore(repo: ResumeTaskRepository) {
        synchronized(lock) {
            if (restored) return
            repository = repo
            repo.recover().forEach { saved[it.taskId] = it }
            restored = true
        }
        notifySavedListeners()
    }

    fun startForegroundDownload(context: Context, request: DownloadRequest, outputDirectory: File): Result<DownloadTaskState> = runCatching {
        restore(context)
        val captured = request.copy(storageTarget = request.storageTarget ?: SettingsRepository.fromContext(context).getSettings().defaultStorageTarget)
        val state = synchronized(lock) {
            requireNotNull(repository).create(captured).getOrThrow()
            enqueueForServiceStart(captured, outputDirectory).getOrThrow()
        }
        try { startService(context) } catch (error: Exception) {
            parkPending(captured.taskId, "启动下载失败，点击继续重试。")
            throw error
        }
        state
    }

    fun resume(context: Context, taskId: String): Result<Unit> = runCatching {
        synchronized(lock) {
            val item = checkNotNull(saved[taskId])
            check(item.recoveryError == null)
            check(item.state.fileRecoveryAction !in setOf(FileRecoveryAction.ConfirmRestart, FileRecoveryAction.ChooseFileName)) { "请先确认重新下载。" }
            val request = checkNotNull(item.state.request).copy(cookiesPath = null)
            repository?.prioritize(taskId)
            val state = item.state.copy(stage = DownloadStage.Resuming, stopReason = null, statusMessage = null, fileRecoveryAction = null)
            repository?.saveState(state)
            saved.remove(taskId)
            queuedLaunches.addFirst(DownloadLaunch(request, File(context.filesDir, "gui-downloads")))
            val waiting = saved.values.filter { it.wasQueued && it.recoveryError == null }
            waiting.forEach {
                queuedLaunches.addLast(DownloadLaunch(requireNotNull(it.state.request), File(context.filesDir, "gui-downloads")))
                saved.remove(it.taskId)
            }
        }
        notifySavedListeners(); notifyPendingListeners()
        try { startService(context) } catch (error: Exception) {
            parkPending(taskId, "启动下载失败，点击继续重试。")
            throw error
        }
    }

    /** Called on a background executor after explicit user confirmation and a fresh file probe. */
    fun confirmFileRestart(context: Context, taskId: String, metadata: com.garyapp.ytdl.core.files.FileMetadata): Result<Unit> = runCatching {
        synchronized(lock) {
            val item = checkNotNull(saved[taskId])
            check(item.state.fileRecoveryAction == FileRecoveryAction.ConfirmRestart)
            check(activeLaunch?.request?.taskId != taskId && claimedLaunch?.request?.taskId != taskId && queuedLaunches.none { it.request.taskId == taskId })
            val request = requireNotNull(repository).confirmFileRestart(requireNotNull(item.state.request), metadata).getOrThrow()
            saved[taskId] = item.copy(state = DownloadTaskState(DownloadStage.Paused, request))
        }
        resume(context, taskId).getOrThrow()
    }

    /** Resolves an Ask conflict without changing source metadata or deleting its partial. */
    fun chooseFileName(context: Context, taskId: String, fileName: String): Result<Unit> = runCatching {
        synchronized(lock) {
            val item = checkNotNull(saved[taskId])
            check(item.state.fileRecoveryAction == FileRecoveryAction.ChooseFileName)
            check(activeLaunch?.request?.taskId != taskId && claimedLaunch?.request?.taskId != taskId && queuedLaunches.none { it.request.taskId == taskId })
            val request = requireNotNull(repository).chooseFileName(requireNotNull(item.state.request), fileName).getOrThrow()
            saved[taskId] = item.copy(state = DownloadTaskState(DownloadStage.Paused, request))
        }
        resume(context, taskId).getOrThrow()
    }

    internal fun cancelPending(context: Context, taskId: String): Result<Boolean> {
        val result = synchronized(lock) {
            val launch = queuedLaunches.firstOrNull { it.request.taskId == taskId }
                ?: return Result.success(false)
            queuedLaunches.remove(launch)
            saved[taskId] = ResumeTaskItem(taskId, launch.request.title, DownloadTaskState.waiting(launch.request))
            cancelSaved(context, taskId).map { true }
        }
        notifySavedListeners(); notifyPendingListeners()
        return result
    }

    fun cancelSaved(context: Context, taskId: String): Result<Unit> = runCatching {
        synchronized(lock) {
            val item = checkNotNull(saved[taskId])
            check(activeLaunch?.request?.taskId != taskId && claimedLaunch?.request?.taskId != taskId && queuedLaunches.none { it.request.taskId == taskId })
            if (item.state.request == null) {
                requireNotNull(repository).cancelUnavailable(taskId).getOrThrow()
            } else {
                val result = requireNotNull(repository).cancelStopped(item.state)
                if (result.isFailure) {
                    saved[taskId] = item.copy(recoveryError = "取消尚未完成，文件待清理，请重试。", state = item.state.copy(request = null))
                    updateStoppedNotification(context, taskId, item.state.copy(stage = DownloadStage.Paused,
                        stopReason = DownloadStopReason.UserCancel, statusMessage = "取消尚未完成，文件待清理，请重试。"))
                    notifySavedListeners()
                    result.getOrThrow()
                }
            }
            saved.remove(taskId)
            updateStoppedNotification(context, taskId, item.state.canceled().copy(outputs = emptyList()))
        }
        notifySavedListeners(); notifyPendingListeners()
    }

    private fun startService(context: Context) {
        val intent = Intent(context, DownloadService::class.java).setAction(DownloadService.ActionStart)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
    }

    private fun updateStoppedNotification(context: Context, taskId: String, state: DownloadTaskState) {
        val notifications = NotificationController(context)
        if (activeLaunch == null && claimedLaunch == null &&
            (currentState?.request?.taskId == taskId || currentState == null && notifications.showsTask(taskId))) {
            val updated = state.copy(request = state.request ?: currentState?.request)
            publish(updated)
            notifications.notifyForegroundState(updated)
        }
    }

    fun addListener(listener: (DownloadTaskState) -> Unit): AutoCloseable {
        synchronized(lock) { listeners += listener; currentState }?.let(listener)
        return AutoCloseable { synchronized(lock) { listeners -= listener } }
    }
    fun addPendingListener(listener: (List<DownloadRequest>) -> Unit): AutoCloseable {
        listener(synchronized(lock) { pendingListeners += listener; pendingRequestsLocked() })
        return AutoCloseable { synchronized(lock) { pendingListeners -= listener } }
    }
    fun addSavedListener(listener: (List<ResumeTaskItem>) -> Unit): AutoCloseable {
        listener(synchronized(lock) { savedListeners += listener; saved.values.toList() })
        return AutoCloseable { synchronized(lock) { savedListeners -= listener } }
    }
    fun publish(state: DownloadTaskState) {
        val snapshot = synchronized(lock) {
            if (activeLaunch != null && state.request?.taskId != activeLaunch?.request?.taskId) return
            val now = System.currentTimeMillis()
            if (lastSavedStage != state.stage || now - lastSavedAt >= 1_000) {
                try { repository?.saveState(state) } catch (error: Exception) { throw PipelineCheckpointException(error) }
                lastSavedAt = now; lastSavedStage = state.stage
            }
            currentState = state
            listeners.toList()
        }
        snapshot.forEach { it(state) }
    }
    internal fun enqueueForServiceStart(request: DownloadRequest, outputDirectory: File): Result<DownloadTaskState> = runCatching {
        val waiting = DownloadTaskState.waiting(request)
        val shouldPublish = synchronized(lock) {
            check(queuedLaunches.none { it.request.taskId == request.taskId } && activeLaunch?.request?.taskId != request.taskId)
            outputDirectory.mkdirs()
            queuedLaunches.addLast(DownloadLaunch(request, outputDirectory))
            activeLaunch == null && claimedLaunch == null && (currentState == null || currentState?.stage in TerminalStages)
        }
        notifyPendingListeners()
        if (shouldPublish) publish(waiting)
        waiting
    }
    internal fun <T> withTaskFilesLocked(action: () -> T): T = synchronized(lock) { action() }

    fun <T> runWhenIdle(action: () -> T): IdleDownloadActionResult<T> = synchronized(lock) {
        if (queuedLaunches.isNotEmpty() || claimedLaunch != null || activeLaunch != null ||
            currentState?.stage?.let { it !in TerminalStages && it != DownloadStage.Paused } == true) {
            IdleDownloadActionResult.ActiveDownload
        } else IdleDownloadActionResult.Executed(action())
    }
    internal fun claimPendingLaunch(): DownloadLaunch? = synchronized(lock) {
        if (claimedLaunch != null || activeLaunch != null) return@synchronized null
        queuedLaunches.pollFirst()?.also { claimedLaunch = it }
    }
    internal fun activateLaunch(launch: DownloadLaunch) {
        synchronized(lock) { check(claimedLaunch === launch); claimedLaunch = null; activeLaunch = launch }
        notifyPendingListeners()
    }
    internal fun finishActiveLaunch(launch: DownloadLaunch) {
        val callbacks = synchronized(lock) {
            if (activeLaunch === launch) { activeLaunch = null; requestedStop = null }
            workerReleasedListeners.toList()
        }
        callbacks.forEach { it() }
    }
    internal fun addWorkerReleasedListener(listener: () -> Unit): AutoCloseable {
        synchronized(lock) { workerReleasedListeners += listener }
        return AutoCloseable { synchronized(lock) { workerReleasedListeners -= listener } }
    }
    internal fun hasWorker(): Boolean = synchronized(lock) { activeLaunch != null || claimedLaunch != null }
    internal fun park(launch: DownloadLaunch, state: DownloadTaskState) {
        val snapshot = synchronized(lock) {
            repository?.saveState(state)
            saved[launch.request.taskId] = ResumeTaskItem(launch.request.taskId, launch.request.title, state.copy(request = launch.request.copy(cookiesPath = null)),
                recoveryError = if (state.stopReason == DownloadStopReason.UserCancel) "取消尚未完成，请重试取消。" else null)
            if (activeLaunch === launch || activeLaunch == null && currentState?.request?.taskId == launch.request.taskId) {
                currentState = state
                listeners.toList()
            } else emptyList()
        }
        notifySavedListeners()
        snapshot.forEach { it(state) }
    }
    private fun parkPending(taskId: String, message: String) {
        val launch = synchronized(lock) {
            queuedLaunches.firstOrNull { it.request.taskId == taskId }?.also { queuedLaunches.remove(it) }
        } ?: return
        val state = DownloadTaskState(DownloadStage.Paused, launch.request.copy(cookiesPath = null), statusMessage = message)
        park(launch, state)
        publish(state)
        notifyPendingListeners()
    }
    internal fun attachCancellation(cancellation: MutableDownloadCancellation) {
        synchronized(lock) {
            activeCancellation = cancellation
            when (requestedStop) {
                DownloadStopReason.UserCancel -> cancellation.cancel()
                DownloadStopReason.UserPause -> cancellation.pause()
                DownloadStopReason.LifecycleEnd -> cancellation.stopForLifecycle()
                DownloadStopReason.NetworkUnavailable -> cancellation.waitForNetwork()
                null -> Unit
            }
        }
    }
    internal fun clearCancellation(cancellation: MutableDownloadCancellation) {
        synchronized(lock) { if (activeCancellation === cancellation) activeCancellation = null }
    }
    fun cancelActive(taskId: String? = null): Boolean = synchronized(lock) {
        val runnableId = activeLaunch?.request?.taskId ?: claimedLaunch?.request?.taskId ?: queuedLaunches.peekFirst()?.request?.taskId
        if (taskId != null && runnableId != taskId) return@synchronized false
        stopActive(DownloadStopReason.UserCancel)
        true
    }
    fun pauseActive(taskId: String? = null) = synchronized(lock) {
        if (taskId != null && activeLaunch?.request?.taskId != taskId) return@synchronized
        stopActive(DownloadStopReason.UserPause)
        val state = currentState?.takeIf { activeLaunch != null && requestedStop == DownloadStopReason.UserPause }
        state?.let { publish(it.copy(stage = DownloadStage.Pausing, stopReason = DownloadStopReason.UserPause)) }
    }
    internal fun stopForLifecycle(launch: DownloadLaunch?) {
        synchronized(lock) { if (launch != null && activeLaunch === launch) stopActive(DownloadStopReason.LifecycleEnd) }
    }
    internal fun waitForNetwork() { stopActive(DownloadStopReason.NetworkUnavailable) }
    private fun stopActive(reason: DownloadStopReason) {
        synchronized(lock) {
            if (requestedStop == DownloadStopReason.UserCancel) return
            if (reason in setOf(DownloadStopReason.NetworkUnavailable, DownloadStopReason.LifecycleEnd) && requestedStop != null) return
            val runnable = activeLaunch ?: claimedLaunch ?: queuedLaunches.peekFirst()
            val runnableId = runnable?.request?.taskId
            if (currentState?.stage in TerminalStages && currentState?.request?.taskId == runnableId) return
            if (reason == DownloadStopReason.UserCancel) {
                val targetState = currentState?.takeIf { it.request?.taskId == runnableId }
                    ?: runnable?.let { DownloadTaskState.waiting(it.request) }
                targetState?.let { repository?.saveState(it.copy(stage = DownloadStage.Pausing, stopReason = reason)) }
            }
            requestedStop = reason
            activeCancellation?.let {
                when (reason) {
                    DownloadStopReason.UserCancel -> it.cancel()
                    DownloadStopReason.UserPause -> it.pause()
                    DownloadStopReason.NetworkUnavailable -> it.waitForNetwork()
                    DownloadStopReason.LifecycleEnd -> it.stopForLifecycle()
                }
            }
        }
    }
    internal fun currentState(): DownloadTaskState? = synchronized(lock) { currentState }
    internal fun <T> withCurrentState(action: (DownloadTaskState?) -> T): T = synchronized(lock) { action(currentState) }
    internal fun hasPendingLaunch(): Boolean = synchronized(lock) { queuedLaunches.isNotEmpty() && activeLaunch == null && claimedLaunch == null }
    internal fun clearNetworkStop() { synchronized(lock) { if (requestedStop == DownloadStopReason.NetworkUnavailable) requestedStop = null } }
    internal fun resetForTests() {
        synchronized(lock) {
            listeners.clear(); pendingListeners.clear(); savedListeners.clear(); workerReleasedListeners.clear(); queuedLaunches.clear(); saved.clear()
            claimedLaunch = null; activeLaunch = null; currentState = null; activeCancellation = null
            requestedStop = null; repository = null; restored = false; lastSavedAt = 0; lastSavedStage = null
        }
    }
    private val TerminalStages = ResumeTaskRepository.TerminalStages + DownloadStage.Idle
    private fun notifyPendingListeners() {
        val snapshot = synchronized(lock) { pendingRequestsLocked() to pendingListeners.toList() }
        snapshot.second.forEach { it(snapshot.first) }
    }
    private fun notifySavedListeners() {
        val snapshot = synchronized(lock) { saved.values.toList() to savedListeners.toList() }
        snapshot.second.forEach { it(snapshot.first) }
    }
    private fun pendingRequestsLocked() = listOfNotNull(claimedLaunch?.request) + queuedLaunches.map { it.request }
}
