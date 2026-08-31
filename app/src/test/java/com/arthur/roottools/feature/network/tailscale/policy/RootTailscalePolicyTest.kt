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
                backendState = "Running",
                backendOnline = true,
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
                identitySaved = true,
                adb5555Listening = true,
            ),
        )

        assertEquals(RootTailscaleHealth.STOPPED, decision.health)
        assertTrue(decision.canEnableRootOverlay)
        assertFalse(decision.canBeginAuthentication)
    }

    @Test
    fun savedIdentityStillRequiresLocalAdbBeforeUserspaceServe() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                statePresent = true,
                identitySaved = true,
                adb5555Listening = false,
            ),
        )

        assertFalse(decision.canEnableUserspaceServe)
        assertTrue(decision.canEnableRootOverlay)
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
                backendState = "Running",
                backendOnline = true,
                routeReady = true,
                androidVpnActive = true,
                androidVpnOwner = RootTailscaleSnapshot.HIDDIFY_PACKAGE,
            ),
        )

        assertEquals(RootTailscaleHealth.READY, decision.health)
        assertTrue(decision.coexistenceReady)
        assertFalse(decision.canStopOfficialApp)
    }

    @Test
    fun cachedTailnetIpWithoutRunningBackendIsDegraded() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                daemonRunning = true,
                socketReady = true,
                tailscale0Present = true,
                tailnetIpv4 = "100.100.20.30",
                backendState = "Stopped",
                backendOnline = false,
                routeReady = true,
                androidVpnOwner = RootTailscaleSnapshot.OFFICIAL_TAILSCALE_PACKAGE,
            ),
        )

        assertEquals(RootTailscaleHealth.DEGRADED, decision.health)
        assertFalse(decision.canStopOfficialApp)
        assertFalse(decision.coexistenceReady)
    }

    @Test
    fun savedIdentityWithOfflineBackendOffersRepair() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                daemonRunning = true,
                socketReady = true,
                identitySaved = true,
                backendState = "Stopped",
                backendOnline = false,
            ),
        )

        assertEquals(RootTailscaleHealth.DEGRADED, decision.health)
        assertTrue(decision.canRepair)
        assertFalse(decision.canBeginAuthentication)
    }

    @Test
    fun revokedSavedIdentityOffersReauthenticationInsteadOfRepairOrEnable() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                daemonRunning = true,
                socketReady = true,
                statePresent = true,
                identitySaved = true,
                backendState = "NeedsLogin",
                adb5555Listening = true,
            ),
        )

        assertEquals(RootTailscaleHealth.NEEDS_LOGIN, decision.health)
        assertTrue(decision.canBeginAuthentication)
        assertFalse(decision.canEnableUserspaceServe)
        assertFalse(decision.canEnableRootOverlay)
        assertFalse(decision.canRepair)
    }

    @Test
    fun userspaceServeIsReadyWithoutKernelRoute() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                daemonRunning = true,
                socketReady = true,
                tailnetIpv4 = "100.100.20.30",
                backendState = "Running",
                backendOnline = true,
                serveAdbReady = true,
                adb5555Listening = true,
                androidVpnActive = true,
                androidVpnOwner = "com.getsurfboard",
            ),
        )

        assertEquals(RootTailscaleHealth.READY, decision.health)
        assertTrue(decision.coexistenceReady)
        assertTrue(decision.canEnableBoot)
        assertTrue(decision.canEnableRootOverlay)
        assertFalse(decision.canEnableUserspaceServe)
    }

    @Test
    fun readyRootModeWithoutAndroidVpnIsNotCoexistenceReady() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                daemonRunning = true,
                socketReady = true,
                tailnetIpv4 = "100.83.208.27",
                backendState = "Running",
                backendOnline = true,
                serveAdbReady = true,
                adb5555Listening = true,
                androidVpnActive = false,
            ),
        )

        assertEquals(RootTailscaleHealth.READY, decision.health)
        assertFalse(decision.coexistenceReady)
    }

    @Test
    fun authenticatedStoppedRuntimeCanChooseUserspaceOrKernel() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                statePresent = true,
                identitySaved = true,
                adb5555Listening = true,
            ),
        )

        assertTrue(decision.canEnableUserspaceServe)
        assertTrue(decision.canEnableRootOverlay)
        assertFalse(decision.canEnableBoot)
    }

    @Test
    fun unauthenticatedStateFileKeepsLoginRetryAvailable() {
        val decision = RootTailscalePolicy.decide(
            RootTailscaleSnapshot(
                rootAvailable = true,
                runtimeInstalled = true,
                daemonRunning = true,
                socketReady = true,
                statePresent = true,
                identitySaved = false,
                backendState = "NeedsLogin",
            ),
        )

        assertEquals(RootTailscaleHealth.NEEDS_LOGIN, decision.health)
        assertTrue(decision.canBeginAuthentication)
        assertFalse(decision.canEnableUserspaceServe)
        assertFalse(decision.canEnableRootOverlay)
    }
}
