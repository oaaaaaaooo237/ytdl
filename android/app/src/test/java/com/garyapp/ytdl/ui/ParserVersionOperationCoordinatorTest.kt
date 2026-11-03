package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.ytdlp.ParserVersionControl
import com.garyapp.ytdl.core.ytdlp.ParserVersionInfo
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class ParserVersionOperationCoordinatorTest {
    @Test
    fun lateInitialRunningSnapshotCannotOverrideCompletion() {
        val executor = QueuedOperationExecutor()
        val coordinator = ParserVersionOperationCoordinator(FakeVersionControl(), executor)
        coordinator.download("2026.4.1")
        assertTrue(coordinator.currentState().isRunning)
        val observedRunningStates = CopyOnWriteArrayList<Boolean>()
        val initialRunningDeliveryStarted = CountDownLatch(1)
        val releaseInitialRunningDelivery = CountDownLatch(1)
        val completionDelivered = CountDownLatch(1)
        val registrationThread = Thread {
            coordinator.addListener { state ->
                if (state.isRunning) {
                    initialRunningDeliveryStarted.countDown()
                    check(releaseInitialRunningDelivery.await(2, TimeUnit.SECONDS))
                } else {
                    completionDelivered.countDown()
                }
                observedRunningStates += state.isRunning
            }
        }
        registrationThread.start()
        assertTrue(initialRunningDeliveryStarted.await(2, TimeUnit.SECONDS))
        val operationThread = Thread(executor::runNext)
        operationThread.start()
        val interleavingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (
            completionDelivered.count > 0L &&
            operationThread.state != Thread.State.BLOCKED &&
            System.nanoTime() < interleavingDeadline
        ) {
            Thread.yield()
        }
        assertTrue(
            "完成态应已并发投递，或正等待同一 listener 的初始投递结束",
            completionDelivered.count == 0L || operationThread.state == Thread.State.BLOCKED,
        )

        releaseInitialRunningDelivery.countDown()
        registrationThread.join(2_000)
        operationThread.join(2_000)

        assertFalse(registrationThread.isAlive)
        assertFalse(operationThread.isAlive)
        assertFalse("listener 最终不能停留在旧 running 态", observedRunningStates.last())
    }
}

private class QueuedOperationExecutor : Executor {
    private val commands = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        commands.addLast(command)
    }

    fun runNext() {
        commands.removeFirst().run()
    }
}

private class FakeVersionControl : ParserVersionControl {
    private var selectedVersion = YtdlpBridge.PINNED_YTDLP_VERSION
    private val downloadedVersions = mutableListOf<String>()

    override fun listVersions(): List<ParserVersionInfo> {
        return listOf(
            ParserVersionInfo(
                version = YtdlpBridge.PINNED_YTDLP_VERSION,
                isBuiltIn = true,
                isSelected = selectedVersion == YtdlpBridge.PINNED_YTDLP_VERSION,
            ),
        ) + downloadedVersions.map { version ->
            ParserVersionInfo(
                version = version,
                isBuiltIn = false,
                isSelected = selectedVersion == version,
            )
        }
    }

    override fun downloadLatest(expectedVersion: String?): Result<ParserVersionInfo> {
        val version = requireNotNull(expectedVersion)
        downloadedVersions += version
        return Result.success(ParserVersionInfo(version, isBuiltIn = false, isSelected = false))
    }

    override fun select(version: String): Result<Unit> {
        selectedVersion = version
        return Result.success(Unit)
    }

    override fun delete(version: String): Result<Unit> = error("unused")
}
