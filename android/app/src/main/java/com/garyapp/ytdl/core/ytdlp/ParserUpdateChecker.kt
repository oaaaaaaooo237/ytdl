package com.garyapp.ytdl.core.ytdlp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor

sealed interface ParserUpdateResult {
    data object AlreadyLatest : ParserUpdateResult
    data class UpdateAvailable(val latestVersion: String) : ParserUpdateResult
    data object Failed : ParserUpdateResult
}

data class ParserUpdateState(
    val isRunning: Boolean = false,
    val result: ParserUpdateResult? = null,
    val manualResultRequested: Boolean = false,
    val promptVersion: String? = null,
)

class ParserUpdateChecker(
    private val bundledVersion: String = YtdlpBridge.PINNED_YTDLP_VERSION,
    private val fetchJson: () -> String = ParserUpdateHttpClient::fetchJson,
) {
    fun check(): ParserUpdateResult {
        return runCatching {
            val latestVersion = JSONObject(fetchJson())
                .optJSONObject("info")
                ?.optString("version")
                .orEmpty()
            val comparison = compareVersions(latestVersion, bundledVersion)
                ?: return ParserUpdateResult.Failed
            if (comparison > 0) {
                ParserUpdateResult.UpdateAvailable(latestVersion)
            } else {
                ParserUpdateResult.AlreadyLatest
            }
        }.getOrElse { ParserUpdateResult.Failed }
    }

    fun checkAsync(executor: Executor, onResult: (ParserUpdateResult) -> Unit) {
        executor.execute { onResult(check()) }
    }

    private fun compareVersions(left: String, right: String): Int? {
        val leftParts = left.toVersionParts() ?: return null
        val rightParts = right.toVersionParts() ?: return null
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val comparison = (leftParts.getOrNull(index) ?: 0)
                .compareTo(rightParts.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun String.toVersionParts(): List<Int>? {
        if (isBlank()) return null
        return split('.').map { part ->
            if (part.isEmpty() || part.any { !it.isDigit() }) return null
            part.toIntOrNull() ?: return null
        }
    }
}

object ParserUpdateHttpClient {
    const val Endpoint = "https://pypi.org/pypi/yt-dlp/json"
    const val ReleasePage = "https://github.com/oaaaaaaooo237/ytdl/releases/latest"
    const val ConnectTimeoutMillis = 3_000
    const val ReadTimeoutMillis = 3_000

    fun fetchJson(): String {
        val connection = URL(Endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = ConnectTimeoutMillis
            connection.readTimeout = ReadTimeoutMillis
            connection.instanceFollowRedirects = true
            val responseCode = connection.responseCode
            require(responseCode in 200..299) { "PyPI response $responseCode" }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class ParserUpdatePromptRegistry {
    private val promptedVersions = mutableSetOf<String>()

    @Synchronized
    fun shouldPrompt(latestVersion: String): Boolean = promptedVersions.add(latestVersion)
}

class ParserUpdateCoordinator(
    private val checker: ParserUpdateChecker = ParserUpdateChecker(),
    private val executor: Executor,
    private val promptRegistry: ParserUpdatePromptRegistry = ParserUpdatePromptRegistry(),
    private val stateDeliveryExecutor: Executor = Executor(Runnable::run),
) {
    private val lock = Any()
    private val listeners = linkedSetOf<ParserUpdateListener>()
    private var state = ParserUpdateState()
    private var revision = 0L
    private var startupRequested = false

    fun addListener(listener: (ParserUpdateState) -> Unit): AutoCloseable {
        return addVersionedListener { snapshot -> listener(snapshot.state) }
    }

    internal fun addVersionedListener(listener: (VersionedParserUpdateState) -> Unit): AutoCloseable {
        val registration = ParserUpdateListener(listener)
        val snapshot = synchronized(lock) {
            listeners += registration
            currentSnapshot()
        }
        deliver(registration, snapshot)
        return AutoCloseable {
            synchronized(lock) {
                listeners -= registration
            }
            registration.close()
        }
    }

    fun requestStartupCheck() {
        val shouldRequest = synchronized(lock) {
            if (startupRequested) {
                false
            } else {
                startupRequested = true
                true
            }
        }
        if (shouldRequest) requestCheck(manual = false)
    }

    fun requestManualCheck() {
        requestCheck(manual = true)
    }

    fun dismissUpdatePrompt(latestVersion: String) {
        val notification = synchronized(lock) {
            if (state.promptVersion != latestVersion) {
                null
            } else {
                state = state.copy(promptVersion = null)
                revision += 1
                currentSnapshot() to listeners.toList()
            }
        }
        notification?.let(::notifyListeners)
    }

    private fun requestCheck(manual: Boolean) {
        var shouldRun = false
        val notification = synchronized(lock) {
            state = if (state.isRunning) {
                state.copy(manualResultRequested = state.manualResultRequested || manual)
            } else {
                shouldRun = true
                ParserUpdateState(
                    isRunning = true,
                    manualResultRequested = manual,
                    promptVersion = state.promptVersion,
                )
            }
            revision += 1
            currentSnapshot() to listeners.toList()
        }
        notifyListeners(notification)
        if (shouldRun) {
            checker.checkAsync(executor, ::completeCheck)
        }
    }

    private fun completeCheck(result: ParserUpdateResult) {
        val notification = synchronized(lock) {
            val promptVersion = (result as? ParserUpdateResult.UpdateAvailable)
                ?.latestVersion
                ?.takeIf(promptRegistry::shouldPrompt)
                ?: state.promptVersion
            state = state.copy(
                isRunning = false,
                result = result,
                promptVersion = promptVersion,
            )
            revision += 1
            currentSnapshot() to listeners.toList()
        }
        notifyListeners(notification)
    }

    private fun currentSnapshot(): VersionedParserUpdateState = VersionedParserUpdateState(revision, state)

    private fun notifyListeners(notification: Pair<VersionedParserUpdateState, List<ParserUpdateListener>>) {
        val (snapshot, snapshotListeners) = notification
        snapshotListeners.forEach { listener -> deliver(listener, snapshot) }
    }

    private fun deliver(listener: ParserUpdateListener, snapshot: VersionedParserUpdateState) {
        stateDeliveryExecutor.execute { listener.deliver(snapshot) }
    }
}

internal data class VersionedParserUpdateState(
    val revision: Long,
    val state: ParserUpdateState,
)

private class ParserUpdateListener(
    private val callback: (VersionedParserUpdateState) -> Unit,
) {
    private var latestRevision = -1L
    private var active = true

    @Synchronized
    fun deliver(snapshot: VersionedParserUpdateState) {
        if (!active || snapshot.revision <= latestRevision) return
        latestRevision = snapshot.revision
        callback(snapshot)
    }

    @Synchronized
    fun close() {
        active = false
    }
}
