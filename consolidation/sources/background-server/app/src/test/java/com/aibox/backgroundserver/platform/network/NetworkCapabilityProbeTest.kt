package com.aibox.backgroundserver.platform.network

import com.aibox.backgroundserver.platform.root.RootCommandGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkCapabilityProbeTest {
    private val probe = NetworkCapabilityProbe(RootCommandGateway())

    @Test
    fun `userspace backend selected when tun exists without kernel wireguard`() {
        val result = probe.parse(
            """
            tun=1
            iptables=1
            wgtools=0
            wgkernel=0
            ipforward=0
            """.trimIndent(),
        )

        assertEquals("Userspace GoBackend", result.recommendedBackend)
        assertTrue(result.tunAvailable)
        assertTrue(result.iptablesAvailable)
        assertFalse(result.kernelWireGuardAvailable)
        assertFalse(result.ipv4ForwardingEnabled)
    }

    @Test
    fun `kernel backend selected only when kernel and tools both exist`() {
        val result = probe.parse(
            """
            tun=1
            iptables=1
            wgtools=1
            wgkernel=1
            ipforward=1
            """.trimIndent(),
        )

        assertEquals("Kernel + wg-quick", result.recommendedBackend)
        assertTrue(result.kernelWireGuardAvailable)
        assertTrue(result.wireGuardToolsAvailable)
        assertTrue(result.ipv4ForwardingEnabled)
    }

    @Test
    fun `missing tun marks wireguard unsupported`() {
        val result = probe.parse(
            """
            tun=0
            iptables=1
            wgtools=0
            wgkernel=0
            ipforward=0
            """.trimIndent(),
        )

        assertEquals("不可用", result.recommendedBackend)
        assertFalse(result.tunAvailable)
    }
}
