package com.garyapp.ytdl.core.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParserUpdateCheckerTest {
    @Test
    fun officialEndpointAndTimeoutsStayBounded() {
        assertEquals("https://pypi.org/pypi/yt-dlp/json", ParserUpdateHttpClient.Endpoint)
        assertTrue(ParserUpdateHttpClient.ConnectTimeoutMillis in 1..5_000)
        assertTrue(ParserUpdateHttpClient.ReadTimeoutMillis in 1..5_000)
        assertTrue(ParserUpdateHttpClient.MetadataTotalTimeoutMillis in 5_000..30_000)
        assertTrue(ParserUpdateHttpClient.WheelTotalTimeoutMillis >= 5 * 60_000)
        assertTrue(ParserUpdateHttpClient.MaxMetadataBytes in 1_048_576..16_777_216)
    }

    @Test
    fun metadataReadEnforcesTotalDeadlineAndDisconnects() {
        var nowMillis = 0L
        val connection = FakeParserHttpConnection(
            inputStreamProvider = {
                ClockAdvancingInputStream("{}".toByteArray()) { nowMillis = 11L }
            },
        )

        val result = runCatching {
            ParserUpdateHttpClient.fetchJson(
                connectionFactory = { connection },
                nowMillis = { nowMillis },
                totalTimeoutMillis = 10L,
            )
        }

        assertTrue(result.isFailure)
        assertTrue(connection.disconnected)
    }

    @Test
    fun oversizedMetadataIsRejectedAndDisconnected() {
        val connection = FakeParserHttpConnection(
            inputStreamProvider = {
                ByteArrayInputStream(ByteArray(ParserUpdateHttpClient.MaxMetadataBytes + 1))
            },
        )

        val result = runCatching {
            ParserUpdateHttpClient.fetchJson(
                connectionFactory = { connection },
                nowMillis = { 0L },
                totalTimeoutMillis = 10_000L,
            )
        }

        assertTrue(result.isFailure)
        assertTrue(connection.disconnected)
    }

    @Test
    fun wheelStreamEnforcesTotalDeadlineAndDisconnects() {
        var nowMillis = 0L
        val connection = FakeParserHttpConnection(
            inputStreamProvider = {
                ClockAdvancingInputStream("wheel".toByteArray()) { nowMillis = 11L }
            },
        )

        val result = runCatching {
            ParserUpdateHttpClient.openWheel(
                uri = URI("https://files.pythonhosted.org/packages/yt_dlp.whl"),
                connectionFactory = { connection },
                nowMillis = { nowMillis },
                totalTimeoutMillis = 10L,
            ).use { it.readBytes() }
        }

        assertTrue(result.isFailure)
        assertTrue(connection.disconnected)
    }

    @Test
    fun wheelResponseAndInputStreamExceptionsAlwaysDisconnect() {
        val responseFailure = FakeParserHttpConnection(
            responseCodeProvider = { throw IOException("response failed") },
        )
        val inputFailure = FakeParserHttpConnection(
            inputStreamProvider = { throw IOException("input failed") },
        )

        listOf(responseFailure, inputFailure).forEach { connection ->
            val result = runCatching {
                ParserUpdateHttpClient.openWheel(
                    uri = URI("https://files.pythonhosted.org/packages/yt_dlp.whl"),
                    connectionFactory = { connection },
                    nowMillis = { 0L },
                    totalTimeoutMillis = 10_000L,
                )
            }
            assertTrue(result.isFailure)
            assertTrue(connection.disconnected)
        }
    }

    @Test
    fun newerPyPiInfoVersionProducesUpdateAvailable() {
        val checker = ParserUpdateChecker("2026.3.17") {
            """{"info":{"version":"2026.4.1"}}"""
        }

        assertEquals(
            ParserUpdateResult.UpdateAvailable("2026.4.1"),
            checker.check(),
        )
    }

    @Test
    fun sameOrOlderVersionIsAlreadyLatest() {
        assertEquals(
            ParserUpdateResult.AlreadyLatest,
            ParserUpdateChecker("2026.3.17") {
                """{"info":{"version":"2026.3.17"}}"""
            }.check(),
        )
        assertEquals(
            ParserUpdateResult.AlreadyLatest,
            ParserUpdateChecker("2026.3.17") {
                """{"info":{"version":"2026.2.28"}}"""
            }.check(),
        )
    }

    @Test
    fun selectedRuntimeVersionPreventsRepeatedStartupPromptForSameDownload() {
        val checker = ParserUpdateChecker.forCurrentVersion(
            currentVersionProvider = { "2026.4.1" },
            fetchJson = { """{"info":{"version":"2026.4.1"}}""" },
        )

        assertEquals(ParserUpdateResult.AlreadyLatest, checker.check())
    }

    @Test
    fun offlineInvalidJsonAndInvalidVersionAreSilentFailures() {
        val cases = listOf<() -> String>(
            { throw IllegalStateException("offline") },
            { "not-json" },
            { """{"info":{}}""" },
            { """{"info":{"version":"latest"}}""" },
        )

        cases.forEach { fetchJson ->
            assertEquals(
                ParserUpdateResult.Failed,
                ParserUpdateChecker("2026.3.17", fetchJson).check(),
            )
        }
    }

    @Test
    fun asyncCheckReturnsBeforeNetworkWorkCompletes() {
        val releaseFetch = CountDownLatch(1)
        val callback = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val checker = ParserUpdateChecker("2026.3.17") {
                releaseFetch.await(2, TimeUnit.SECONDS)
                """{"info":{"version":"2026.3.17"}}"""
            }

            checker.checkAsync(executor) { callback.countDown() }

            assertFalse(callback.await(100, TimeUnit.MILLISECONDS))
            releaseFetch.countDown()
            assertTrue(callback.await(2, TimeUnit.SECONDS))
        } finally {
            releaseFetch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun promptRegistryPromptsOnlyOnceForSameLatestVersion() {
        val registry = ParserUpdatePromptRegistry()

        assertTrue(registry.shouldPrompt("2026.4.1"))
        assertFalse(registry.shouldPrompt("2026.4.1"))
        assertTrue(registry.shouldPrompt("2026.4.2"))
    }

    @Test
    fun inFlightResultSurvivesListenerRecreationAndPromptsOnce() {
        val fetchStarted = CountDownLatch(1)
        val releaseFetch = CountDownLatch(1)
        val recreatedResult = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val coordinator = ParserUpdateCoordinator(
                checker = ParserUpdateChecker("2026.3.17") {
                    fetchStarted.countDown()
                    assertTrue(releaseFetch.await(2, TimeUnit.SECONDS))
                    """{"info":{"version":"2026.4.1"}}"""
                },
                executor = executor,
            )
            val firstStates = CopyOnWriteArrayList<ParserUpdateState>()
            val firstSubscription = coordinator.addListener(firstStates::add)

            coordinator.requestStartupCheck()
            assertTrue(fetchStarted.await(2, TimeUnit.SECONDS))
            assertTrue(firstStates.last().isRunning)
            firstSubscription.close()

            val recreatedStates = CopyOnWriteArrayList<ParserUpdateState>()
            val recreatedSubscription = coordinator.addListener { state ->
                recreatedStates += state
                if (state.result == ParserUpdateResult.UpdateAvailable("2026.4.1")) {
                    recreatedResult.countDown()
                }
            }
            assertTrue(recreatedStates.last().isRunning)

            releaseFetch.countDown()
            assertTrue(recreatedResult.await(2, TimeUnit.SECONDS))
            assertEquals(
                ParserUpdateResult.UpdateAvailable("2026.4.1"),
                recreatedStates.last().result,
            )
            assertEquals("2026.4.1", recreatedStates.last().promptVersion)
            coordinator.dismissUpdatePrompt("2026.4.1")
            recreatedSubscription.close()

            var restoredState: ParserUpdateState? = null
            coordinator.addListener { restoredState = it }.close()
            assertEquals(ParserUpdateResult.UpdateAvailable("2026.4.1"), restoredState?.result)
            assertEquals(null, restoredState?.promptVersion)
        } finally {
            releaseFetch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun staleCapturedSnapshotCannotFollowNewerStateDelivery() {
        val stateDeliveryExecutor = ReorderingExecutor()
        val latestDelivered = CountDownLatch(1)
        val observedStates = CopyOnWriteArrayList<ParserUpdateState>()
        val coordinator = ParserUpdateCoordinator(
            checker = ParserUpdateChecker("2026.3.17") {
                """{"info":{"version":"2026.4.1"}}"""
            },
            executor = Executor(Runnable::run),
            stateDeliveryExecutor = stateDeliveryExecutor,
        )
        val subscription = coordinator.addListener { state ->
            observedStates += state
            if (state.promptVersion == "2026.4.1") {
                latestDelivered.countDown()
            }
        }
        try {
            coordinator.requestStartupCheck()
            assertEquals(3, stateDeliveryExecutor.pendingCount)

            stateDeliveryExecutor.runLast()
            assertTrue(latestDelivered.await(0, TimeUnit.SECONDS))
            stateDeliveryExecutor.runAllFromFront()

            assertEquals(1, observedStates.size)
            assertEquals(
                ParserUpdateResult.UpdateAvailable("2026.4.1"),
                observedStates.single().result,
            )
            assertEquals("2026.4.1", observedStates.single().promptVersion)
        } finally {
            subscription.close()
        }
    }
}

private class FakeParserHttpConnection(
    private val responseCodeProvider: () -> Int = { 200 },
    private val inputStreamProvider: () -> InputStream = { ByteArrayInputStream(byteArrayOf(1)) },
) : HttpURLConnection(URL("https://files.pythonhosted.org/test")) {
    var disconnected = false
        private set

    override fun disconnect() {
        disconnected = true
    }

    override fun usingProxy(): Boolean = false

    override fun connect() = Unit

    override fun getResponseCode(): Int = responseCodeProvider()

    override fun getInputStream(): InputStream = inputStreamProvider()
}

private class ClockAdvancingInputStream(
    bytes: ByteArray,
    private val onRead: () -> Unit,
) : ByteArrayInputStream(bytes) {
    override fun read(): Int {
        onRead()
        return super.read()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        onRead()
        return super.read(buffer, offset, length)
    }
}

private class ReorderingExecutor : Executor {
    private val commands = ArrayDeque<Runnable>()

    val pendingCount: Int
        get() = commands.size

    override fun execute(command: Runnable) {
        commands.addLast(command)
    }

    fun runLast() {
        commands.removeLast().run()
    }

    fun runAllFromFront() {
        while (commands.isNotEmpty()) {
            commands.removeFirst().run()
        }
    }
}
