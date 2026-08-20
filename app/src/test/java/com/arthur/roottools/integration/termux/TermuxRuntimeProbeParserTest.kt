package com.arthur.roottools.integration.termux

import com.arthur.roottools.model.RuntimeToolState
import org.junit.Assert.assertEquals
import org.junit.Test

class TermuxRuntimeProbeParserTest {
    @Test
    fun `parser maps known runtime tools`() {
        val result = TermuxRuntimeProbeParser.parse(
            """
            git=1
            python=1
            node=0
            ssh=1
            sshd=0
            sv=1
            """.trimIndent()
        )

        assertEquals(RuntimeToolState.INSTALLED, result.git)
        assertEquals(RuntimeToolState.INSTALLED, result.python)
        assertEquals(RuntimeToolState.NOT_INSTALLED, result.node)
        assertEquals(RuntimeToolState.INSTALLED, result.ssh)
        assertEquals(RuntimeToolState.NOT_INSTALLED, result.sshd)
        assertEquals(RuntimeToolState.INSTALLED, result.serviceManager)
    }

    @Test
    fun `hostile or malformed output is ignored`() {
        val result = TermuxRuntimeProbeParser.parse("git=2\nnode=$(reboot)\nunknown=1")
        assertEquals(RuntimeToolState.UNKNOWN, result.git)
        assertEquals(RuntimeToolState.UNKNOWN, result.node)
    }
}

