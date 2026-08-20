package com.arthur.roottools.automation

import android.os.SystemClock
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/** Process-local backstop for scoped external automation clients. */
object AutomationRateLimiter {
    private const val WINDOW_MS = 60_000L
    private const val MAX_REQUESTS_PER_WINDOW = 60
    private val windows = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun tryAcquire(clientId: String, nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (!CLIENT_ID_REGEX.matches(clientId)) return false
        val queue = windows.computeIfAbsent(clientId) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && nowMs - queue.first() >= WINDOW_MS) queue.removeFirst()
            if (queue.size >= MAX_REQUESTS_PER_WINDOW) return false
            queue.addLast(nowMs)
            return true
        }
    }

    internal fun clearForTest() = windows.clear()

    private val CLIENT_ID_REGEX = Regex("^[a-z0-9][a-z0-9._-]{1,39}$")
}

