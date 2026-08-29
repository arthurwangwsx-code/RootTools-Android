package com.aibox.backgroundserver.platform.network

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRoutingCommandPolicyTest {
    @Test
    fun `tunnel lookup only accepts a literal IPv4 address`() {
        assertNotNull(RootRoutingCommandPolicy.findTunnelInterface("10.77.0.1"))
        assertNull(RootRoutingCommandPolicy.findTunnelInterface("10.77.0.1; reboot"))
        assertNull(RootRoutingCommandPolicy.findTunnelInterface("999.77.0.1"))
    }

    @Test
    fun `NAT rules validate interfaces and subnet`() {
        val rules = RootRoutingCommandPolicy.natRules("tun0", "wlan0", "10.77.0.0/24")
        assertNotNull(rules)
        assertTrue(requireNotNull(rules).apply.contains("-i tun0 -o wlan0"))
        assertTrue(rules.remove.contains("-D FORWARD"))
        assertNull(RootRoutingCommandPolicy.natRules("tun0; reboot", "wlan0", "10.77.0.0/24"))
        assertNull(RootRoutingCommandPolicy.natRules("tun0", "wlan0", "10.77.0.0/99"))
    }

    @Test
    fun `interface discovery output is validated before reuse`() {
        assertNotNull(RootRoutingCommandPolicy.validInterface("wg-roottools_1"))
        assertNull(RootRoutingCommandPolicy.validInterface("tun0\nreboot"))
        assertNull(RootRoutingCommandPolicy.validInterface(""))
    }
}
