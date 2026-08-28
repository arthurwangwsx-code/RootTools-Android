package com.arthur.roottools.feature.network.tailscale.data

import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootTailscaleProbeParserTest {
    @Test
    fun parsesKernelCoexistenceSnapshot() {
        val snapshot = RootTailscaleProbeParser.parse(
            raw = """
                RUNTIME_INSTALLED=1
                VERSION=1.102.3
                DAEMON_RUNNING=1
                SOCKET_READY=1
                STATE_PRESENT=1
                TAILSCALE0=1
                TAILNET_IP=100.110.6.9
                AUTH_URL=
                ROUTE_READY=1
                BOOT_ENABLED=1
                ADB_5555=1
                VPN_OWNER=app.hiddify.com
                OFFICIAL_APP_INSTALLED=1
                HIDDIFY_INSTALLED=1
            """.trimIndent(),
            rootAvailable = true,
            collectedAtMs = 1234L,
        )

        assertEquals(RootTailscaleMode.KERNEL_TUN, snapshot.mode)
        assertEquals("100.110.6.9", snapshot.tailnetIpv4)
        assertEquals("1.102.3", snapshot.runtimeVersion)
        assertTrue(snapshot.routeReady)
        assertTrue(snapshot.hasSavedIdentity)
        assertTrue(snapshot.bootEnabled)
        assertTrue(snapshot.adb5555Listening)
        assertTrue(snapshot.hiddifyVpnActive)
        assertEquals(1234L, snapshot.collectedAtMs)
    }

    @Test
    fun rejectsHostileIpAndAuthUrl() {
        val snapshot = RootTailscaleProbeParser.parse(
            raw = """
                RUNTIME_INSTALLED=1
                DAEMON_RUNNING=1
                TAILNET_IP=100.110.5.86;reboot
                AUTH_URL=https://evil.example/a/abc
            """.trimIndent(),
            rootAvailable = true,
            collectedAtMs = 1L,
        )

        assertNull(snapshot.tailnetIpv4)
        assertNull(snapshot.authUrl)
        assertFalse(snapshot.authenticated)
    }

    @Test
    fun duplicateKeysUseLastObservedValue() {
        val snapshot = RootTailscaleProbeParser.parse(
            raw = """
                RUNTIME_INSTALLED=0
                RUNTIME_INSTALLED=1
                TAILNET_IP=100.64.0.2
            """.trimIndent(),
            rootAvailable = true,
            collectedAtMs = 1L,
        )

        assertTrue(snapshot.runtimeInstalled)
        assertEquals("100.64.0.2", snapshot.tailnetIpv4)
    }
}

