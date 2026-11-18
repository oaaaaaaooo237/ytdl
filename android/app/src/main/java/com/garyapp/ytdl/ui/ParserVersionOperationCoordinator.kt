package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.ytdlp.ParserVersionControl
import com.garyapp.ytdl.core.ytdlp.ParserVersionInfo
import java.util.concurrent.Executor

data class ParserVersionOperationState(
    val versions: List<ParserVersionInfo>,
    val isRunning: Boolean = false,
    val message: String? = null,
    val explanationTitle: String? = null,
)

class ParserVersionOperationCoordinator(
    private val control: ParserVersionControl,
    private val executor: Executor,
) {
    private val listeners = linkedSetOf<ParserVersionOperationListener>()
    private var state = ParserVersionOperationState(
        versions = runCatching(control::listVersions).getOrDefault(emptyList()),
    )
    private var revision = 0L

    @Synchronized
    fun currentState(): ParserVersionOperationState = state

    fun addListener(listener: (ParserVersionOperationState) -> Unit): AutoCloseable {
        val registration = ParserVersionOperationListener(listener)
        val snapshot = synchronized(this) {
            listeners += registration
            currentSnapshot()
        }
        registration.deliver(snapshot)
        return AutoCloseable {
            synchronized(this) {
                listeners -= registration
            }
            registration.close()
        }
    }

    fun select(version: String) {
        submit("无法选择解析器版本，请重新检查或下载。") {
            val result = control.select(version)
            val refreshed = refreshedVersions()
            val message = if (
                result.isSuccess && refreshed.any { it.version == version && it.isSelected }
            ) {
                "已选择 $version，重启应用后生效。"
            } else {
                "无法选择解析器版本，请重新检查或下载。"
            }
            ParserVersionOperationState(refreshed, message = message)
        }
    }

    fun download(expectedVersion: String?) {
        submit("解析器更新失败，请稍后重试。", "解析器更新失败") {
            val result = runCatching {
                val downloaded = control.downloadLatest(expectedVersion).getOrThrow()
                val selectionChanged = !downloaded.isSelected
                if (selectionChanged) {
                    control.select(downloaded.version).getOrThrow()
                }
                downloaded to selectionChanged
            }
            val refreshed = refreshedVersions()
            result.fold(
                onSuccess = { (downloaded, selectionChanged) ->
                    val message = when {
                        refreshed.none { it.version == downloaded.version && it.isSelected } -> {
                            "解析器更新失败，请稍后重试。"
                        }
                        downloaded.isBuiltIn && selectionChanged -> "已选择内置最新版，重启后生效。"
                        downloaded.isBuiltIn -> "最新版已内置，当前无需下载。"
                        selectionChanged -> "解析器 ${downloaded.version} 已下载并选择，重启应用后生效。"
                        else -> "解析器 ${downloaded.version} 已下载，当前已选择。"
                    }
                    val title = if (message.startsWith("解析器更新失败")) {
                        "解析器更新失败"
                    } else {
                        "解析器更新完成"
                    }
                    ParserVersionOperationState(refreshed, message = message, explanationTitle = title)
                },
                onFailure = {
                    ParserVersionOperationState(
                        refreshed,
                        message = "解析器更新失败，请稍后重试。",
                        explanationTitle = "解析器更新失败",
                    )
                },
            )
        }
    }

    fun delete(version: String) {
        val wasSelected = currentState().versions.any { it.version == version && it.isSelected }
        submit("无法删除解析器版本，请稍后重试。") {
            val result = control.delete(version)
            val refreshed = refreshedVersions()
            val message = when {
                result.isFailure || refreshed.any { it.version == version } -> {
                    "无法删除解析器版本，请稍后重试。"
                }
                wasSelected && refreshed.any { it.isBuiltIn && it.isSelected } -> {
                    "已删除解析器 $version。下次启动将使用内置版本。"
                }
                else -> "已删除解析器 $version。"
            }
            ParserVersionOperationState(refreshed, message = message)
        }
    }

    private fun submit(
        rejectionMessage: String,
        rejectionTitle: String? = null,
        operation: () -> ParserVersionOperationState,
    ) {
        val notification = synchronized(this) {
            if (state.isRunning) return
            state = state.copy(isRunning = true, message = null, explanationTitle = null)
            revision += 1
            currentSnapshot() to listeners.toList()
        }
        notifyListeners(notification)
        try {
            executor.execute {
                val completed = runCatching(operation).getOrElse {
                    ParserVersionOperationState(
                        versions = currentState().versions,
                        message = rejectionMessage,
                        explanationTitle = rejectionTitle,
                    )
                }
                publish(completed.copy(isRunning = false))
            }
        } catch (_: RuntimeException) {
            publish(
                ParserVersionOperationState(
                    versions = currentState().versions,
                    message = rejectionMessage,
                    explanationTitle = rejectionTitle,
                ),
            )
        }
    }

    private fun refreshedVersions(): List<ParserVersionInfo> =
        runCatching(control::listVersions).getOrElse { currentState().versions }

    private fun publish(updated: ParserVersionOperationState) {
        val notification = synchronized(this) {
            state = updated
            revision += 1
            currentSnapshot() to listeners.toList()
        }
        notifyListeners(notification)
    }

    private fun currentSnapshot() = VersionedParserVersionOperationState(revision, state)

    private fun notifyListeners(
        notification: Pair<VersionedParserVersionOperationState, List<ParserVersionOperationListener>>,
    ) {
        val (snapshot, snapshotListeners) = notification
        snapshotListeners.forEach { listener -> listener.deliver(snapshot) }
    }
}

private data class VersionedParserVersionOperationState(
    val revision: Long,
    val state: ParserVersionOperationState,
)

private class ParserVersionOperationListener(
    private val callback: (ParserVersionOperationState) -> Unit,
) {
    private var latestRevision = -1L
    private var active = true

    @Synchronized
    fun deliver(snapshot: VersionedParserVersionOperationState) {
        if (!active || snapshot.revision <= latestRevision) return
        latestRevision = snapshot.revision
        callback(snapshot.state)
    }

    @Synchronized
    fun close() {
        active = false
    }
}
