package com.arthur.roottools.feature.network.tailscale.policy

import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleHealth
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootTailscalePolicyTest {
    @Test
    fun tailnetIpv4HonorsCgnatBoundary() {
        assertTrue(RootTailscalePolicy.isTailnetIpv4("100.64.0.1"))
        assertTrue(RootTailscalePolicy.isTailnetIpv4("100.127.255.254"))
        assertFalse(RootTailscalePolicy.isTailnetIpv4("100.63.255.255"))
        assertFalse(RootTailscalePolicy.isTailnetIpv4("100.128.0.1"))
        assertFalse(RootTailscalePolicy.isTailnetIpv4("10.0.0.1"))
        assertFalse(RootTailscalePolicy.isTailnetIpv4("100.110.5.999"))
        assertFalse(RootTailscalePolicy.isTailnetIpv4("100.110.5.86;reboot"))
    }

    @Test
    fun hostnameIsCanonicalAndBounded() {
        assertEquals("xiaomi-14-root", RootTailscalePolicy.normalizeHostname("Xiaomi 14 / Root"))
        assertEquals("roottools-android", RootTailscalePolicy.normalizeHostname("***"))
        assertTrue(RootTailscalePolicy.normalizeHostname("A".repeat(100)).length <= 63)
    }

    @Test
    fun missingRuntimeOffersInstallOnly() {
        val decision = RootTailscalePolicy.decide(RootTailscaleSnapshot(rootAvailable = true))

        assertEquals(RootTailscaleHealth.RUNTIME_MISSING, decision.health)
        assertTrue(decision.canInstallRuntime)
        assertFalse(decision.canBeginAuthentication)
        assertFalse(decision.canEnableRootOverlay)
    }

    @Test
    fun unauthenticatedDaemonRequestsLogin() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                daemonRunning = true,
                socketReady = true,
                authUrl = "https://login.tailscale.com/a/abc123",
            ),
        )

        assertEquals(RootTailscaleHealth.NEEDS_LOGIN, decision.health)
        assertTrue(decision.canBeginAuthentication)
        assertFalse(decision.canEnableRootOverlay)
    }

    @Test
    fun verifiedKernelTunnelAllowsOfficialAppStop() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                daemonRunning = true,
                socketReady = true,
                tailscale0Present = true,
                tailnetIpv4 = "100.100.20.30",
                routeReady = true,
                androidVpnOwner = RootTailscaleSnapshot.OFFICIAL_TAILSCALE_PACKAGE,
            ),
        )

        assertEquals(RootTailscaleHealth.READY, decision.health)
        assertTrue(decision.canStopOfficialApp)
        assertFalse(decision.coexistenceReady)
    }

    @Test
    fun stoppedRuntimeWithSavedIdentityCanEnableWithoutNewLogin() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                statePresent = true,
            ),
        )

        assertEquals(RootTailscaleHealth.STOPPED, decision.health)
        assertTrue(decision.canEnableRootOverlay)
        assertFalse(decision.canBeginAuthentication)
    }

    @Test
    fun hiddifyOwnerAndKernelTunnelIsCoexistenceReady() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                daemonRunning = true,
                socketReady = true,
                tailscale0Present = true,
                tailnetIpv4 = "100.100.20.30",
                routeReady = true,
                androidVpnOwner = RootTailscaleSnapshot.HIDDIFY_PACKAGE,
            ),
        )

        assertEquals(RootTailscaleHealth.READY, decision.health)
        assertTrue(decision.coexistenceReady)
        assertFalse(decision.canStopOfficialApp)
    }
}

