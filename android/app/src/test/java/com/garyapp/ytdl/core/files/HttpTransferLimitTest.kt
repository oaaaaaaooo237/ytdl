package com.garyapp.ytdl.core.files

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpTransferLimitTest {
    @After
    fun resetLimit() {
        HttpTransferLimit.update(0)
    }

    @Test
    fun changingLimitUpdatesTheNextRead() {
        HttpTransferLimit.update(256)
        assertEquals(26_214, HttpTransferLimit.acquire(128 * 1024) { false })

        HttpTransferLimit.update(512)
        assertEquals(52_428, HttpTransferLimit.acquire(128 * 1024) { false })

        HttpTransferLimit.update(0)
        assertEquals(128 * 1024, HttpTransferLimit.acquire(128 * 1024) { false })
    }

    @Test
    fun stoppedTransferDoesNotReadOrConsumeTheSharedSlot() {
        HttpTransferLimit.update(256)
        assertEquals(0, HttpTransferLimit.acquire(128 * 1024) { true })
        assertEquals(26_214, HttpTransferLimit.acquire(128 * 1024) { false })
    }

    @Test
    fun twoReadsUseTheSameBudget() {
        HttpTransferLimit.update(256)
        val first = HttpTransferLimit.acquire(128 * 1024) { false }
        val started = System.nanoTime()
        val second = HttpTransferLimit.acquire(128 * 1024) { false }
        assertEquals(first, second)
        org.junit.Assert.assertTrue(System.nanoTime() - started >= 75_000_000L)
    }
}
