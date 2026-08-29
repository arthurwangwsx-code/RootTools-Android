package com.arthur.roottools.root

import com.arthur.roottools.core.privilege.RootCommandResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SerializedRootExecutorTest {
    @Test
    fun `uses the shared isolated transport with grace timeout`() = runBlocking {
        var observedCommand = emptyList<String>()
        var observedTimeoutMillis = 0L
        val executor = SerializedRootExecutor { command, timeoutMillis ->
            observedCommand = command
            observedTimeoutMillis = timeoutMillis
            RootCommandResult(0, "0")
        }

        val result = executor.execute("id -u", timeoutSeconds = 5)

        assertTrue(result.success)
        assertEquals(listOf("su", "-c"), observedCommand.take(2))
        assertTrue(observedCommand.last().contains("timeout -k 0.2s 5s"))
        assertEquals(7_000L, observedTimeoutMillis)
    }

    @Test
    fun `rejects invalid input before starting a process`() = runBlocking {
        var calls = 0
        val executor = SerializedRootExecutor { _, _ ->
            calls += 1
            RootCommandResult(0, "unexpected")
        }

        val result = executor.execute("", timeoutSeconds = 5)

        assertFalse(result.success)
        assertEquals(2, result.exitCode)
        assertEquals(0, calls)
    }

    @Test
    fun `serializes concurrent privileged commands`() = runBlocking {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val executor = SerializedRootExecutor { _, _ ->
            val active = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { current -> maxOf(current, active) }
            Thread.sleep(60)
            inFlight.decrementAndGet()
            RootCommandResult(0, "ok")
        }

        val results = listOf(
            async { executor.execute("printf one", 2) },
            async { executor.execute("printf two", 2) },
        ).awaitAll()

        assertTrue(results.all { it.success })
        assertEquals(1, maxInFlight.get())
    }
}
