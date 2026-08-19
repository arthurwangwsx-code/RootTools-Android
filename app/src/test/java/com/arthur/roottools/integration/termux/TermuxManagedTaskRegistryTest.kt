package com.arthur.roottools.integration.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxManagedTaskRegistryTest {
    @Test
    fun `every managed task uses termux prefix or home`() {
        TermuxManagedTaskId.entries.forEach { id ->
            val spec = TermuxManagedTaskRegistry.spec(id)
            assertTrue(spec.executable.startsWith("${'$'}PREFIX/"))
            assertTrue(spec.workDir.startsWith("/data/data/com.termux/files/"))
            assertTrue(spec.timeoutMs in 1_000L..10 * 60_000L)
            assertTrue(spec.maxOutputChars in 1_000..64_000)
        }
    }

    @Test
    fun `managed tasks do not expose root or reboot`() {
        val serialized = TermuxManagedTaskId.entries.joinToString("\n") { id ->
            val spec = TermuxManagedTaskRegistry.spec(id)
            buildString {
                append(spec.executable)
                append(' ')
                append(spec.arguments.joinToString(" "))
            }
        }.lowercase()

        assertFalse(serialized.contains("su -c"))
        assertFalse(serialized.contains(" reboot"))
        assertFalse(serialized.contains("setprop"))
    }

    @Test
    fun `only generated artifact installers accept roottools owned stdin`() {
        val stdinTasks = TermuxManagedTaskId.entries.filter {
            TermuxManagedTaskRegistry.spec(it).acceptsRootToolsStdin
        }
        assertEquals(
            listOf(
                TermuxManagedTaskId.INSTALL_ROOTTOOLS_CLI,
                TermuxManagedTaskId.INSTALL_MCP_RELAY,
                TermuxManagedTaskId.POST_PROCESS_DIAGNOSTIC,
            ),
            stdinTasks,
        )
    }

    @Test
    fun `relay start tasks cannot bind wildcard interfaces`() {
        val serialized = listOf(
            TermuxManagedTaskId.MCP_RELAY_START_LOOPBACK,
            TermuxManagedTaskId.MCP_RELAY_START_TAILSCALE,
        ).joinToString("\n") { TermuxManagedTaskRegistry.spec(it).arguments.joinToString(" ") }
        assertFalse(serialized.contains("--bind 0.0.0.0"))
        assertTrue(serialized.contains("--bind loopback"))
        assertTrue(serialized.contains("--bind tailscale"))
    }
}

