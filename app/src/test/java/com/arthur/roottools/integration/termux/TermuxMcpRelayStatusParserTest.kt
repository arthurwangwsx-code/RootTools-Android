package com.arthur.roottools.integration.termux

import org.junit.Assert.assertEquals
import org.junit.Test

class TermuxMcpRelayStatusParserTest {
    @Test
    fun `parses managed relay state`() {
        val status = TermuxMcpRelayStatusParser.parse("running=1\npid=1234\nbind=tailscale")
        assertEquals(true, status.running)
        assertEquals(1234, status.pid)
        assertEquals("tailscale", status.bindMode)
    }

    @Test
    fun `rejects hostile pid and arbitrary bind`() {
        val status = TermuxMcpRelayStatusParser.parse("running=1\npid=$(reboot)\nbind=0.0.0.0")
        assertEquals(true, status.running)
        assertEquals(null, status.pid)
        assertEquals(null, status.bindMode)
    }
}

