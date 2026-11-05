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
    private class Worker(val launch: DownloadLaunch) {
        var claimed = true
        var state = DownloadTaskState.waiting(launch.request)
        var cancellation: MutableDownloadCancellation? = null
        var requestedStop: DownloadStopReason? = null
        var lastSavedAt = 0L
        var lastSavedStage: DownloadStage? = null
    }

    private val workers = linkedMapOf<String, Worker>()
    private val earlyStops = mutableMapOf<String, DownloadStopReason>()
    private val activeListeners = linkedSetOf<(Map<String, DownloadTaskState>) -> Unit>()
    private var currentState: DownloadTaskState? = null
    private var repository: ResumeTaskRepository? = null
    private var restored = false

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
            check(taskId !in workers)
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
            check(taskId !in workers && queuedLaunches.none { it.request.taskId == taskId })
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
            check(taskId !in workers && queuedLaunches.none { it.request.taskId == taskId })
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
            earlyStops.remove(taskId)
            saved[taskId] = ResumeTaskItem(taskId, launch.request.title, DownloadTaskState.waiting(launch.request))
            cancelSaved(context, taskId).map { true }
        }
        notifySavedListeners(); notifyPendingListeners()
        return result
    }

    fun cancelSaved(context: Context, taskId: String): Result<Unit> = runCatching {
        synchronized(lock) {
            val item = checkNotNull(saved[taskId])
            check(taskId !in workers && queuedLaunches.none { it.request.taskId == taskId })
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
        if (workers.isEmpty() &&
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
    fun activeStates(): Map<String, DownloadTaskState> = synchronized(lock) { activeStatesLocked() }

    fun addActiveListener(listener: (Map<String, DownloadTaskState>) -> Unit): AutoCloseable {
        synchronized(lock) { activeListeners += listener; listener(activeStatesLocked()) }
        return AutoCloseable { synchronized(lock) { activeListeners -= listener } }
    }

    fun publish(state: DownloadTaskState) = synchronized(lock) {
        val worker = workers[state.request?.taskId]
        val now = System.currentTimeMillis()
        if (worker == null || worker.lastSavedStage != state.stage || now - worker.lastSavedAt >= 1_000) {
            try { repository?.saveState(state) } catch (error: Exception) { throw PipelineCheckpointException(error) }
            worker?.let { it.lastSavedAt = now; it.lastSavedStage = state.stage }
        }
        updateStateLocked(state, worker)
    }

    private fun updateStateLocked(state: DownloadTaskState, worker: Worker?) {
        worker?.state = state
        currentState = state
        listeners.toList().forEach { it(state) }
        if (worker != null) notifyActiveListenersLocked()
    }

    private fun activeStatesLocked() = workers.filterValues { !it.claimed }.mapValues { it.value.state }
    private fun notifyActiveListenersLocked() {
        val snapshot = activeStatesLocked()
        activeListeners.toList().forEach { it(snapshot) }
    }

    internal fun enqueueForServiceStart(request: DownloadRequest, outputDirectory: File): Result<DownloadTaskState> = runCatching {
        val waiting = DownloadTaskState.waiting(request)
        val shouldPublish = synchronized(lock) {
            check(queuedLaunches.none { it.request.taskId == request.taskId } && request.taskId !in workers)
            outputDirectory.mkdirs()
            queuedLaunches.addLast(DownloadLaunch(request, outputDirectory))
            workers.isEmpty() && (currentState == null || currentState?.stage in TerminalStages)
        }
        notifyPendingListeners()
        if (shouldPublish) publish(waiting)
        waiting
    }
    internal fun <T> withTaskFilesLocked(action: () -> T): T = synchronized(lock) { action() }

    fun <T> runWhenIdle(action: () -> T): IdleDownloadActionResult<T> = synchronized(lock) {
        if (queuedLaunches.isNotEmpty() || workers.isNotEmpty() ||
            currentState?.stage?.let { it !in TerminalStages && it != DownloadStage.Paused } == true) {
            IdleDownloadActionResult.ActiveDownload
        } else IdleDownloadActionResult.Executed(action())
    }
    internal fun claimPendingLaunch(limit: Int = 1): DownloadLaunch? = synchronized(lock) {
        if (workers.size >= limit.coerceIn(1, 3)) return@synchronized null
        queuedLaunches.pollFirst()?.also { launch ->
            val id = launch.request.taskId
            workers[id] = Worker(launch).apply { requestedStop = earlyStops.remove(id) }
        }
    }

    internal fun activateLaunch(launch: DownloadLaunch) {
        synchronized(lock) {
            val worker = checkNotNull(workers[launch.request.taskId])
            check(worker.launch === launch && worker.claimed)
            worker.claimed = false
            notifyActiveListenersLocked()
        }
        notifyPendingListeners()
    }

    internal fun finishActiveLaunch(launch: DownloadLaunch) {
        val callbacks = synchronized(lock) {
            if (workers[launch.request.taskId]?.launch !== launch) return
            workers.remove(launch.request.taskId)
            notifyActiveListenersLocked()
            workerReleasedListeners.toList()
        }
        callbacks.forEach { it() }
    }

    internal fun addWorkerReleasedListener(listener: () -> Unit): AutoCloseable {
        synchronized(lock) { workerReleasedListeners += listener }
        return AutoCloseable { synchronized(lock) { workerReleasedListeners -= listener } }
    }
    internal fun hasWorker(): Boolean = synchronized(lock) { workers.isNotEmpty() }
    internal fun park(launch: DownloadLaunch, state: DownloadTaskState) {
        synchronized(lock) {
            repository?.saveState(state)
            saved[launch.request.taskId] = ResumeTaskItem(launch.request.taskId, launch.request.title,
                state.copy(request = launch.request.copy(cookiesPath = null)),
                recoveryError = if (state.stopReason == DownloadStopReason.UserCancel) "取消尚未完成，请重试取消。" else null,
                outputType = launch.request.outputType())
            val worker = workers[launch.request.taskId]
            if (worker != null || currentState?.request?.taskId == launch.request.taskId) updateStateLocked(state, worker)
        }
        notifySavedListeners()
    }

    private fun parkPending(taskId: String, message: String) {
        val launch = synchronized(lock) {
            queuedLaunches.firstOrNull { it.request.taskId == taskId }?.also { queuedLaunches.remove(it); earlyStops.remove(taskId) }
        } ?: return
        val state = DownloadTaskState(DownloadStage.Paused, launch.request.copy(cookiesPath = null), statusMessage = message)
        park(launch, state)
        publish(state)
        notifyPendingListeners()
    }
    internal fun attachCancellation(cancellation: MutableDownloadCancellation, taskId: String? = null) {
        synchronized(lock) {
            val worker = workers[taskId ?: workers.keys.firstOrNull()] ?: return
            worker.cancellation = cancellation
            worker.requestedStop?.let { cancellation.requestStop(it) }
        }
    }

    internal fun clearCancellation(cancellation: MutableDownloadCancellation, taskId: String) {
        synchronized(lock) {
            workers[taskId]?.let { if (it.cancellation === cancellation) it.cancellation = null }
        }
    }

    fun cancelActive(taskId: String? = null): Boolean = synchronized(lock) {
        val id = taskId ?: firstRunnableIdLocked() ?: return@synchronized false
        stopTaskLocked(id, DownloadStopReason.UserCancel)
    }

    fun pauseActive(taskId: String? = null) = synchronized(lock) {
        val id = taskId ?: firstRunnableIdLocked() ?: return@synchronized
        if (stopTaskLocked(id, DownloadStopReason.UserPause)) {
            val worker = workers[id]
            if (worker != null && !worker.claimed && worker.requestedStop == DownloadStopReason.UserPause) {
                publish(worker.state.copy(stage = DownloadStage.Pausing, stopReason = DownloadStopReason.UserPause))
            }
        }
    }

    internal fun stopForLifecycle(launch: DownloadLaunch?) {
        synchronized(lock) {
            val id = launch?.request?.taskId ?: return
            if (workers[id]?.launch === launch) stopTaskLocked(id, DownloadStopReason.LifecycleEnd)
        }
    }

    internal fun waitForNetwork(taskId: String? = null) {
        synchronized(lock) {
            val id = taskId ?: firstRunnableIdLocked() ?: return
            stopTaskLocked(id, DownloadStopReason.NetworkUnavailable)
        }
    }

    private fun firstRunnableIdLocked() = workers.keys.firstOrNull() ?: queuedLaunches.peekFirst()?.request?.taskId

    private fun stopTaskLocked(id: String, reason: DownloadStopReason): Boolean {
        val worker = workers[id]
        val launch = worker?.launch ?: queuedLaunches.firstOrNull { it.request.taskId == id } ?: return false
        val existing = worker?.requestedStop ?: earlyStops[id]
        if (worker?.state?.stage in TerminalStages) return false
        if (existing == DownloadStopReason.UserCancel) return true
        if (reason in setOf(DownloadStopReason.NetworkUnavailable, DownloadStopReason.LifecycleEnd) && existing != null) return true
        if (worker == null) {
            earlyStops[id] = reason
        } else {
            worker.requestedStop = reason
            worker.cancellation?.requestStop(reason)
        }
        if (reason == DownloadStopReason.UserCancel) {
            val state = worker?.state ?: DownloadTaskState.waiting(launch.request)
            repository?.saveState(state.copy(stage = DownloadStage.Pausing, stopReason = reason))
        }
        return true
    }

    private fun MutableDownloadCancellation.requestStop(reason: DownloadStopReason) {
        when (reason) {
            DownloadStopReason.UserCancel -> cancel()
            DownloadStopReason.UserPause -> pause()
            DownloadStopReason.NetworkUnavailable -> waitForNetwork()
            DownloadStopReason.LifecycleEnd -> stopForLifecycle()
        }
    }

    internal fun currentState(): DownloadTaskState? = synchronized(lock) { currentState }
    internal fun <T> withCurrentState(action: (DownloadTaskState?) -> T): T = synchronized(lock) { action(currentState) }
    internal fun hasPendingLaunch(): Boolean = synchronized(lock) { queuedLaunches.isNotEmpty() }
    internal fun clearNetworkStop(taskId: String) {
        synchronized(lock) {
            workers[taskId]?.let { if (it.requestedStop == DownloadStopReason.NetworkUnavailable) it.requestedStop = null }
        }
    }

    internal fun warnIfStopping(taskId: String) = synchronized(lock) {
        val worker = workers[taskId] ?: return@synchronized
        if (worker.cancellation?.stopReason in setOf(DownloadStopReason.UserPause, DownloadStopReason.UserCancel) &&
            worker.state.stage !in TerminalStages && worker.state.stage != DownloadStage.Paused) {
            publish(worker.state.copy(stage = DownloadStage.Pausing,
                stopReason = worker.cancellation?.stopReason, statusMessage = "下载仍在安全停止中，请稍候；断点文件已保留。"))
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            listeners.clear(); pendingListeners.clear(); savedListeners.clear(); workerReleasedListeners.clear(); activeListeners.clear()
            queuedLaunches.clear(); saved.clear(); workers.clear(); earlyStops.clear()
            currentState = null; repository = null; restored = false
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
    private fun pendingRequestsLocked() = workers.values.filter { it.claimed }.map { it.launch.request } + queuedLaunches.map { it.request }
}
