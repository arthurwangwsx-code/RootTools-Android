package com.arthur.nfclab.platform.simulation

import com.arthur.nfclab.domain.DeviceIdentity
import com.arthur.nfclab.domain.NfcCapability
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.NfcCard
import com.arthur.nfclab.domain.NfcCardKind
import com.arthur.nfclab.domain.NfcCardRoute
import com.arthur.nfclab.domain.NfcWalletInfo
import com.arthur.nfclab.domain.SecureElementInfo
import com.arthur.nfclab.domain.SecureElementType
import com.arthur.nfclab.domain.SimulationLayer
import com.arthur.nfclab.domain.SimulationRoute
import com.arthur.nfclab.domain.SimulationSupport
import com.arthur.nfclab.domain.VendorNfcDetails
import com.arthur.nfclab.nfc.TagSnapshot
import com.arthur.nfclab.platform.provisioning.ProvisioningCapabilityRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationCapabilityAnalyzerTest {
    @Test
    fun desfireOnRootedXiaomi_reportsHostAndProvisioningBoundaries() {
        val snapshot = TagSnapshot(
            timestampMs = 1L,
            idHex = "04TEST",
            technologies = listOf("IsoDep", "NfcA"),
            details = mapOf(
                "NXP product" to "MIFARE DESFire EV1 2K",
                "NFC-A ATQA" to "4403",
                "NFC-A SAK" to "0x20",
            ),
            ndefRecords = emptyList(),
        )
        val profile = NfcDeviceProfile(
            identity = DeviceIdentity("Xiaomi", "test", "houji", "15"),
            capabilities = setOf(
                NfcCapability.HCE_ISO_DEP,
                NfcCapability.ROOT,
                NfcCapability.ESE,
                NfcCapability.VENDOR_NFC_API,
                NfcCapability.MIFARE_OFF_HOST,
            ),
            rootAvailable = true,
            selinuxEnforcing = true,
            secureElements = listOf(SecureElementInfo(SecureElementType.ESE, "eSE1", true, true)),
            vendor = VendorNfcDetails(
                providerId = "xiaomi.wallet",
                displayName = "Xiaomi / NXP NFC",
                extras = mapOf(
                    "extendedFieldDetect" to "true",
                    "t4tNfcee" to "true",
                    "nativeCardEmulationControl" to "listen-mode-only",
                    "eseAccessPermissionPrivileged" to "true",
                    "walletEseAccessGranted" to "true",
                    "openSeService" to "true",
                    "openSeAuthorizationModel" to "caller-signature+tsm-server",
                    "openSeOperationModel" to "server-apdu-task",
                    "miSeOpenService" to "true",
                    "miSeAuthorizationModel" to "spid-caller-package+signature+tsm-server",
                    "miSeOperationModel" to "tsm-rpc-server-apdu-task",
                    "miSeCapabilities" to "executeSeOperation,getOperationResult,login,getSeid",
                    "publicTsmFeaturePermissionPrivileged" to "true",
                    "desfireHostRouteModel" to "protocol-routing-to-host-apdu-service",
                    "desfireHostRouteVerified" to "true",
                    "nfccDtaConfigPath" to "write-secure-settings+nfc.dta.configTLV",
                    "nfccDtaConfigPathVerified" to "true",
                    "rfIdentityArbitraryOverrideVerified" to "false",
                ),
            ),
            wallets = listOf(
                NfcWalletInfo(
                    providerId = "xiaomi.wallet",
                    label = "小米钱包",
                    packageName = "com.miui.tsmclient",
                ),
            ),
            cards = listOf(
                NfcCard(
                    id = "test-card",
                    title = "公司",
                    kind = NfcCardKind.MIFARE_CLASSIC,
                    technologyLabel = "M1 实体门卡",
                    active = true,
                    route = NfcCardRoute.ESE,
                    sourceId = "xiaomi.wallet",
                    sourceLabel = "小米钱包",
                ),
            ),
            collectedAtMs = 2L,
        )

        val provisioning = ProvisioningCapabilityRepository().collect(profile, nowMs = 2L)
        val report = SimulationCapabilityAnalyzer.analyze(
            snapshot,
            profile,
            supportsHostHce = true,
            provisioning = provisioning,
            nowMs = 3L,
        )
        val byLayer = report.layers.associateBy { it.layer }
        val byRoute = report.routes.associateBy { it.route }

        assertEquals(SimulationSupport.PARTIAL, byLayer[SimulationLayer.RF_IDENTITY]?.support)
        assertTrue(byLayer[SimulationLayer.RF_IDENTITY]?.evidence?.any { it.contains("DTA") } == true)
        assertEquals(SimulationSupport.PARTIAL, byLayer[SimulationLayer.ISO_DEP_TRANSPORT]?.support)
        assertTrue(byLayer[SimulationLayer.ISO_DEP_TRANSPORT]?.evidence?.any { it.contains("SELECT AID") } == true)
        assertEquals(SimulationSupport.PARTIAL, byLayer[SimulationLayer.APPLICATION_PROTOCOL]?.support)
        assertEquals(SimulationSupport.REQUIRES_PROVISIONING, byLayer[SimulationLayer.SECURE_CREDENTIAL]?.support)
        assertEquals(SimulationSupport.REQUIRES_PROVISIONING, byLayer[SimulationLayer.OFF_HOST_SE]?.support)
        assertTrue(report.blockers.isNotEmpty())
        assertTrue(report.recommendedPath.any { it.contains("合作方 TSM") })
        assertTrue(report.recommendedPath.any { it.contains("route-to-Host") })
        assertTrue(report.blockers.any { it.contains("首次 Host APDU") })
        assertTrue(report.blockers.any { it.contains("Provisioning") })
        assertTrue(byLayer[SimulationLayer.OFF_HOST_SE]?.evidence?.any { it.contains("server-apdu-task") } == true)
        assertTrue(byLayer[SimulationLayer.OFF_HOST_SE]?.evidence?.any { it.contains("TSM RPC") } == true)
        assertTrue(byLayer[SimulationLayer.APPLICATION_PROTOCOL]?.evidence?.any { it.contains("HostApduService") } == true)
        assertEquals(SimulationSupport.PARTIAL, byRoute[SimulationRoute.HOST_HCE]?.support)
        assertEquals(SimulationSupport.SUPPORTED, byRoute[SimulationRoute.OEM_OFF_HOST]?.support)
        assertEquals(listOf("公司（当前使用）"), byRoute[SimulationRoute.OEM_OFF_HOST]?.cardTitles)
        assertEquals("xiaomi.wallet", byRoute[SimulationRoute.OEM_OFF_HOST]?.managementProviderId)
        assertEquals(SimulationSupport.REQUIRES_PROVISIONING, byRoute[SimulationRoute.CUSTOM_ESE_APPLET]?.support)
    }
}
