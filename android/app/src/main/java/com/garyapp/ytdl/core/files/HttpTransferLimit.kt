package com.garyapp.ytdl.core.files

/** Shared by all HTTP transfers in this process, including parallel segments. */
internal object HttpTransferLimit {
    private var bytesPerSecond = 0L
    private var nextSlot = 0L

    @Synchronized
    fun update(kibPerSecond: Int) {
        val newLimit = kibPerSecond.toLong() * 1024
        if (newLimit != bytesPerSecond) {
            bytesPerSecond = newLimit
            nextSlot = 0L
        }
    }

    fun account(bytes: Int, shouldStop: () -> Boolean): Boolean {
        var remaining = bytes
        while (remaining > 0) {
            val permitted = acquire(remaining, shouldStop)
            if (permitted == 0) return false
            remaining -= permitted
        }
        return true
    }

    fun acquire(maxBytes: Int, shouldStop: () -> Boolean): Int {
        while (!shouldStop()) {
            val waitNanos = synchronized(this) {
                if (bytesPerSecond == 0L) return maxBytes
                val now = System.nanoTime()
                if (nextSlot == 0L || now - nextSlot >= 0) {
                    val size = minOf(maxBytes.toLong(), maxOf(1L, bytesPerSecond / 10)).toInt()
                    nextSlot = now + size * 1_000_000_000L / bytesPerSecond
                    return size
                }
                (nextSlot - now).coerceAtMost(10_000_000L)
            }
            Thread.sleep(waitNanos / 1_000_000L, (waitNanos % 1_000_000L).toInt())
        }
        return 0
    }
}
