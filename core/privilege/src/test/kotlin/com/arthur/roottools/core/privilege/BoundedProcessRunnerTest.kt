package com.arthur.roottools.core.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedProcessRunnerTest {
    private val runner = BoundedProcessRunner(outputLimitBytes = 32)

    @Test
    fun `captures stdout stderr and exit code independently`() {
        val result = runner.run(
            listOf("/bin/sh", "-c", "printf out; printf err >&2; exit 7"),
            timeoutMillis = 2_000,
        )

        assertEquals(7, result.exitCode)
        assertEquals("out", result.stdout)
        assertEquals("err", result.stderr)
        assertFalse(result.success)
    }

    @Test
    fun `bounds output while draining the process streams`() {
        val result = runner.run(
            listOf("/bin/sh", "-c", "printf 1234567890123456789012345678901234567890"),
            timeoutMillis = 2_000,
        )

        assertTrue(result.success)
        assertEquals(32, result.output.length)
    }

    @Test
    fun `reports timeout without claiming success`() {
        val result = runner.run(listOf("/bin/sh", "-c", "sleep 2"), timeoutMillis = 100)

        assertTrue(result.timedOut)
        assertEquals(124, result.exitCode)
        assertFalse(result.success)
    }
}
