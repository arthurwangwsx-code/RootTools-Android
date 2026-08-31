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
                IDENTITY_SAVED=1
                TAILSCALE0=1
                TAILNET_IP=100.110.6.9
                AUTH_URL=
                BACKEND_STATE=Running
                BACKEND_ONLINE=1
                ROUTE_READY=1
                SERVE_ADB=0
                SERVE_MCP=0
                BOOT_ENABLED=1
                ADB_5555=1
                MCP_8765=0
                VPN_ACTIVE=1
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
        assertTrue(snapshot.backendOnline)
        assertEquals("Running", snapshot.backendState)
        assertTrue(snapshot.androidVpnActive)
        assertTrue(snapshot.hasSavedIdentity)
        assertTrue(snapshot.bootEnabled)
        assertTrue(snapshot.adb5555Listening)
        assertTrue(snapshot.hiddifyVpnActive)
        assertEquals(1234L, snapshot.collectedAtMs)
    }

    @Test
    fun preAuthenticationStateFileIsNotASavedIdentity() {
        val snapshot = RootTailscaleProbeParser.parse(
            raw = """
                RUNTIME_INSTALLED=1
                DAEMON_RUNNING=1
                SOCKET_READY=1
                STATE_PRESENT=1
                IDENTITY_SAVED=0
                BACKEND_STATE=NeedsLogin
                BACKEND_ONLINE=0
            """.trimIndent(),
            rootAvailable = true,
            collectedAtMs = 5L,
        )

        assertTrue(snapshot.statePresent)
        assertFalse(snapshot.hasSavedIdentity)
        assertFalse(snapshot.authenticated)
    }

    @Test
    fun cachedIpWithoutRunningBackendIsNotAuthenticated() {
        val snapshot = RootTailscaleProbeParser.parse(
            raw = """
                RUNTIME_INSTALLED=1
                DAEMON_RUNNING=1
                SOCKET_READY=1
                STATE_PRESENT=1
                IDENTITY_SAVED=0
                TAILNET_IP=100.83.208.27
                BACKEND_STATE=Stopped
                BACKEND_ONLINE=0
            """.trimIndent(),
            rootAvailable = true,
            collectedAtMs = 6L,
        )

        assertEquals("100.83.208.27", snapshot.tailnetIpv4)
        assertFalse(snapshot.authenticated)
        assertFalse(snapshot.hasSavedIdentity)
    }

    @Test
    fun parsesUserspaceServeWithoutKernelRoute() {
        val snapshot = RootTailscaleProbeParser.parse(
            raw = """
                RUNTIME_INSTALLED=1
                VERSION=1.102.3
                DAEMON_RUNNING=1
                SOCKET_READY=1
                STATE_PRESENT=1
                TAILSCALE0=0
                TAILNET_IP=100.71.22.9
                BACKEND_STATE=Running
                BACKEND_ONLINE=1
                ROUTE_READY=0
                SERVE_ADB=1
                SERVE_MCP=1
                ADB_5555=1
                MCP_8765=1
                VPN_ACTIVE=1
                VPN_OWNER=com.getsurfboard
            """.trimIndent(),
            rootAvailable = true,
            collectedAtMs = 7L,
        )

        assertEquals(RootTailscaleMode.USERSPACE_SERVE, snapshot.mode)
        assertTrue(snapshot.userspaceServeReady)
        assertTrue(snapshot.serveMcpReady)
        assertEquals("com.getsurfboard", snapshot.androidVpnOwner)
    }

    @Test
    fun rejectsHostileIpAndAuthUrl() {
        val snapshot = RootTailscaleProbeParser.parse(
            raw = """
                RUNTIME_INSTALLED=1
                DAEMON_RUNNING=1
                TAILNET_IP=100.110.5.86;reboot
                AUTH_URL=https://evil.example/a/abc
                BACKEND_STATE=Running;reboot
            """.trimIndent(),
            rootAvailable = true,
            collectedAtMs = 1L,
        )

        assertNull(snapshot.tailnetIpv4)
        assertNull(snapshot.authUrl)
        assertNull(snapshot.backendState)
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
