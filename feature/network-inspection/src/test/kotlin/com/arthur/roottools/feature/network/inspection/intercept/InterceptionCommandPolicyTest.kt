package com.arthur.roottools.feature.network.inspection.intercept

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterceptionCommandPolicyTest {
    @Test
    fun `rules reject system UIDs and invalid proxy ports`() {
        assertNull(InterceptionCommandPolicy.rules(uid = 9999, proxyPort = 7780, blockQuic = true))
        assertNull(InterceptionCommandPolicy.rules(uid = 10000, proxyPort = 80, blockQuic = true))
        assertNull(InterceptionCommandPolicy.rules(uid = 10000, proxyPort = 70000, blockQuic = true))
    }

    @Test
    fun `rules are scoped to a single app UID and typed port`() {
        val commands = InterceptionCommandPolicy.rules(10123, 7780, blockQuic = true)
        assertNotNull(commands)
        val install = requireNotNull(commands).install
        assertTrue(install.contains("--uid-owner 10123"))
        assertTrue(install.contains("--to-ports 7780"))
        assertTrue(install.contains("ROOTTOOLS_MITM"))
        assertTrue(install.contains("ROOTTOOLS_QUIC"))
    }

    @Test
    fun `QUIC rules are omitted when user disables blocking`() {
        val install = requireNotNull(InterceptionCommandPolicy.rules(10123, 7780, blockQuic = false)).install
        assertFalse(install.contains("--dport 443"))
    }

    @Test
    fun `cleanup removes canonical and legacy chains for IPv4 and IPv6`() {
        val cleanup = InterceptionCommandPolicy.cleanupRules()
        assertTrue(cleanup.contains("iptables -t nat -D OUTPUT -j ROOTTOOLS_MITM"))
        assertTrue(cleanup.contains("ip6tables -t nat -D OUTPUT -j ROOTTOOLS_MITM"))
        assertTrue(cleanup.contains("NETTOOLS_MITM"))
        assertTrue(cleanup.contains("NETTOOLS_QUIC"))
    }

    @Test
    fun `force stop accepts only Android package names`() {
        assertTrue(requireNotNull(InterceptionCommandPolicy.forceStop("com.example.app")).contains("'com.example.app'"))
        assertNull(InterceptionCommandPolicy.forceStop("com.example.app; reboot"))
        assertNull(InterceptionCommandPolicy.forceStop("single"))
    }
}
