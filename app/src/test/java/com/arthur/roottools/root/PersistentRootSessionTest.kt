package com.arthur.roottools.root

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentRootSessionTest {
    @Test
    fun reusesOneShellAcrossCommands() = runBlocking {
        val session = PersistentRootSession(listOf("sh"))

        val first = session.execute("printf first", timeoutSeconds = 2)
        val second = session.execute("printf second", timeoutSeconds = 2)

        assertTrue(first.success)
        assertEquals("first", first.output.trim())
        assertTrue(second.success)
        assertEquals("second", second.output.trim())
        assertEquals(1, session.processLaunchCount)
    }

    @Test
    fun preservesExitCodeWithoutKillingSharedShell() = runBlocking {
        val session = PersistentRootSession(listOf("sh"))

        val failed = session.execute("printf nope; false", timeoutSeconds = 2)
        val recovered = session.execute("printf ok", timeoutSeconds = 2)

        assertFalse(failed.success)
        assertEquals(1, failed.exitCode)
        assertEquals("nope", failed.output.trim())
        assertTrue(recovered.success)
        assertEquals("ok", recovered.output.trim())
        assertEquals(1, session.processLaunchCount)
    }

    @Test
    fun supportsQuotesAndMultilineCommandsInsideIsolatedChildShell() = runBlocking {
        val session = PersistentRootSession(listOf("sh"))

        val result = session.execute(
            """
                printf '%s\n' "it's safe"
                printf '%s' 'second line'
            """.trimIndent(),
            timeoutSeconds = 2,
        )

        assertTrue(result.success)
        assertEquals("it's safe\nsecond line", result.output.trim())
        assertEquals(1, session.processLaunchCount)
    }

    @Test
    fun timeoutKillsTermIgnoringCommandAndKeepsSharedShellReusable() = runBlocking {
        val session = PersistentRootSession(listOf("sh"))

        val timedOut = session.execute(
            "trap '' TERM; while :; do :; done",
            timeoutSeconds = 1,
        )
        val recovered = session.execute("printf recovered", timeoutSeconds = 2)

        assertTrue(timedOut.timedOut)
        assertFalse(timedOut.success)
        assertTrue(recovered.success)
        assertEquals("recovered", recovered.output.trim())
        assertEquals(1, session.processLaunchCount)
    }

    @Test
    fun reportsShellLaunchFailureWithoutClaimingSuccess() = runBlocking {
        val session = PersistentRootSession(listOf("/definitely/not/a/roottools-shell"))

        val result = session.execute("printf unreachable", timeoutSeconds = 2)

        assertFalse(result.success)
        assertFalse(result.timedOut)
        assertEquals(-1, result.exitCode)
        assertTrue(result.output.isNotBlank())
        assertEquals(0, session.processLaunchCount)
    }

    @Test
    fun reportsShellClosingBeforeCommandCompletes() = runBlocking {
        val session = PersistentRootSession(listOf("sh", "-c", "sleep 0.1"))

        val result = session.execute("sleep 1; printf unreachable", timeoutSeconds = 2)

        assertFalse(result.success)
        assertFalse(result.timedOut)
        assertEquals(-1, result.exitCode)
        assertTrue(result.output.isNotBlank())
        assertEquals(1, session.processLaunchCount)
    }
}
