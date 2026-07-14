package com.garyapp.ytdl.core.ytdlp

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.SocketTimeoutException
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

class ParserUpdateChecker private constructor(
    private val bundledVersion: String = YtdlpBridge.PINNED_YTDLP_VERSION,
    private val fetchJson: () -> String = ParserUpdateHttpClient::fetchJson,
    private val currentVersionProvider: () -> String = { bundledVersion },
) {
    constructor(
        bundledVersion: String = YtdlpBridge.PINNED_YTDLP_VERSION,
        fetchJson: () -> String = ParserUpdateHttpClient::fetchJson,
    ) : this(bundledVersion, fetchJson, { bundledVersion })

    fun check(): ParserUpdateResult {
        return runCatching {
            val latestVersion = ParserReleaseMetadata.latestVersionFromPyPiJson(fetchJson())
            val comparison = compareVersions(latestVersion, currentVersionProvider())
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

    companion object {
        fun forCurrentVersion(
            currentVersionProvider: () -> String,
            fetchJson: () -> String = ParserUpdateHttpClient::fetchJson,
        ): ParserUpdateChecker {
            return ParserUpdateChecker(
                bundledVersion = YtdlpBridge.PINNED_YTDLP_VERSION,
                fetchJson = fetchJson,
                currentVersionProvider = currentVersionProvider,
            )
        }
    }
}

object ParserUpdateHttpClient {
    const val Endpoint = "https://pypi.org/pypi/yt-dlp/json"
    const val ConnectTimeoutMillis = 3_000
    const val ReadTimeoutMillis = 3_000
    const val MetadataTotalTimeoutMillis = 15_000
    const val WheelTotalTimeoutMillis = 10 * 60_000
    const val MaxMetadataBytes = 8 * 1024 * 1024

    fun fetchJson(): String = fetchJson(
        connectionFactory = { url -> url.openConnection() as HttpURLConnection },
        nowMillis = ::monotonicMillis,
        totalTimeoutMillis = MetadataTotalTimeoutMillis.toLong(),
    )

    internal fun fetchJson(
        connectionFactory: (URL) -> HttpURLConnection,
        nowMillis: () -> Long,
        totalTimeoutMillis: Long,
    ): String {
        val startedAt = nowMillis()
        val connection = connectionFactory(URL(Endpoint))
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = ConnectTimeoutMillis
            connection.readTimeout = ReadTimeoutMillis
            connection.instanceFollowRedirects = false
            requireWithinDeadline(startedAt, nowMillis, totalTimeoutMillis)
            val responseCode = connection.responseCode
            requireWithinDeadline(startedAt, nowMillis, totalTimeoutMillis)
            require(responseCode in 200..299) { "PyPI response $responseCode" }
            connection.inputStream.use { input ->
                readBounded(
                    input = input,
                    maxBytes = MaxMetadataBytes,
                    startedAt = startedAt,
                    nowMillis = nowMillis,
                    totalTimeoutMillis = totalTimeoutMillis,
                ).toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    fun openWheel(uri: URI): InputStream = openWheel(
        uri = uri,
        connectionFactory = { url -> url.openConnection() as HttpURLConnection },
        nowMillis = ::monotonicMillis,
        totalTimeoutMillis = WheelTotalTimeoutMillis.toLong(),
    )

    internal fun openWheel(
        uri: URI,
        connectionFactory: (URL) -> HttpURLConnection,
        nowMillis: () -> Long,
        totalTimeoutMillis: Long,
    ): InputStream {
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("files.pythonhosted.org", ignoreCase = true) &&
                uri.port == -1 &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null,
        ) { "解析器下载地址无效。" }
        val startedAt = nowMillis()
        val connection = connectionFactory(uri.toURL())
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = ConnectTimeoutMillis
            connection.readTimeout = ReadTimeoutMillis
            connection.instanceFollowRedirects = false
            requireWithinDeadline(startedAt, nowMillis, totalTimeoutMillis)
            val responseCode = connection.responseCode
            requireWithinDeadline(startedAt, nowMillis, totalTimeoutMillis)
            require(responseCode in 200..299) { "解析器下载失败。" }
            val input = connection.inputStream
            requireWithinDeadline(startedAt, nowMillis, totalTimeoutMillis)
            return DeadlineConnectionInputStream(
                input = input,
                connection = connection,
                startedAt = startedAt,
                nowMillis = nowMillis,
                totalTimeoutMillis = totalTimeoutMillis,
            )
        } catch (failure: Throwable) {
            connection.disconnect()
            throw failure
        }
    }
}

private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L

private fun requireWithinDeadline(
    startedAt: Long,
    nowMillis: () -> Long,
    totalTimeoutMillis: Long,
) {
    if (nowMillis() - startedAt > totalTimeoutMillis) {
        throw SocketTimeoutException("解析器网络请求超时。")
    }
}

private fun readBounded(
    input: InputStream,
    maxBytes: Int,
    startedAt: Long,
    nowMillis: () -> Long,
    totalTimeoutMillis: Long,
): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        requireWithinDeadline(startedAt, nowMillis, totalTimeoutMillis)
        val count = input.read(buffer)
        requireWithinDeadline(startedAt, nowMillis, totalTimeoutMillis)
        if (count < 0) break
        if (count == 0) continue
        require(output.size() + count <= maxBytes) { "解析器元数据过大。" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private class DeadlineConnectionInputStream(
    input: InputStream,
    private val connection: HttpURLConnection,
    private val startedAt: Long,
    private val nowMillis: () -> Long,
    private val totalTimeoutMillis: Long,
) : FilterInputStream(input) {
    private var closed = false
    private var disconnected = false

    override fun read(): Int = checkedRead { super.read() }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return checkedRead { super.read(buffer, offset, length) }
    }

    private inline fun <T> checkedRead(block: () -> T): T {
        return try {
            requireWithinDeadline(startedAt, nowMillis, totalTimeoutMillis)
            block().also {
                requireWithinDeadline(startedAt, nowMillis, totalTimeoutMillis)
            }
        } catch (failure: Throwable) {
            disconnect()
            throw failure
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try {
            super.close()
        } finally {
            disconnect()
        }
    }

    @Synchronized
    private fun disconnect() {
        if (disconnected) return
        disconnected = true
        connection.disconnect()
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
