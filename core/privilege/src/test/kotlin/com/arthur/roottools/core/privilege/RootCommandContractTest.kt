package com.arthur.roottools.core.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCommandContractTest {
    @Test
    fun `result exposes one success contract across apps`() {
        val success = RootCommandResult(0, "uid=0")
        val timeout = RootCommandResult(124, "", timedOut = true)

        assertTrue(success.success)
        assertTrue(success.ok)
        assertEquals("uid=0", success.stdout)
        assertFalse(timeout.success)
    }

    @Test
    fun `shell quote keeps hostile text inside one argument`() {
        assertEquals("'a'\"'\"'b; reboot\nnext'", PosixShell.quote("a'b; reboot\nnext"))
    }

    @Test
    fun `root transport rejects invalid timeout blank and nul input`() {
        assertNull(RootExecutionPolicy.isolatedSuCommand("id", 0))
        assertNull(RootExecutionPolicy.isolatedSuCommand("id", 121))
        assertNull(RootExecutionPolicy.isolatedSuCommand("", 5))
        assertNull(RootExecutionPolicy.isolatedSuCommand("id\u0000reboot", 5))
    }

    @Test
    fun `root transport isolates script behind timeout and quoted shell`() {
        val command = requireNotNull(RootExecutionPolicy.isolatedSuCommand("printf '%s' ok", 5))

        assertEquals(listOf("su", "-c"), command.take(2))
        assertTrue(command.last().startsWith("timeout -k 0.2s 5s sh -c "))
        assertTrue(command.last().contains("printf"))
    }
}
