package com.arthur.nfclab.platform.xiaomi

import com.arthur.nfclab.domain.DeviceIdentity
import com.arthur.nfclab.domain.NfcCard
import com.arthur.nfclab.domain.NfcCardKind
import com.arthur.nfclab.domain.NfcCardRoute
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.NfcWalletInfo
import com.arthur.nfclab.domain.ProvisioningReadiness
import com.arthur.nfclab.domain.ProvisioningRequirementState
import com.arthur.nfclab.domain.ProvisioningRoute
import com.arthur.nfclab.domain.SecureElementInfo
import com.arthur.nfclab.domain.SecureElementType
import com.arthur.nfclab.domain.VendorNfcDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiProvisioningProviderTest {
    @Test
    fun collect_exposesWalletPartnerTsmAndPrivilegedEseBoundaries() {
        val profile = NfcDeviceProfile(
            identity = DeviceIdentity("Xiaomi", "Xiaomi 14", "houji", "15"),
            capabilities = emptySet(),
            rootAvailable = true,
            selinuxEnforcing = true,
            secureElements = listOf(SecureElementInfo(SecureElementType.ESE, "eSE1", true, true)),
            vendor = VendorNfcDetails(
                providerId = XiaomiNfcProfileProvider.PROVIDER_ID,
                displayName = "Xiaomi / NXP NFC",
                extras = mapOf(
                    "openSeService" to "true",
                    "miSeOpenService" to "true",
                    "eseAccessPermissionPrivileged" to "true",
                    "walletEseAccessGranted" to "true",
                ),
            ),
            wallets = listOf(
                NfcWalletInfo(
                    providerId = XiaomiNfcProfileProvider.PROVIDER_ID,
                    label = "小米钱包",
                    packageName = XiaomiNfcProfileProvider.XIAOMI_WALLET_PACKAGE,
                    managementAction = XiaomiNfcProfileProvider.XIAOMI_DOOR_CARD_SELECT_ACTION,
                ),
            ),
            cards = listOf(
                NfcCard(
                    id = "test-aid",
                    title = "测试门卡",
                    kind = NfcCardKind.MIFARE_CLASSIC,
                    technologyLabel = "M1 实体门卡",
                    active = true,
                    route = NfcCardRoute.ESE,
                    sourceId = XiaomiNfcProfileProvider.PROVIDER_ID,
                    sourceLabel = "小米钱包",
                ),
            ),
            collectedAtMs = 1L,
        )

        val routes = XiaomiProvisioningProvider().collect(profile).associateBy { it.route }

        assertEquals(ProvisioningReadiness.MANAGED_EXTERNALLY, routes[ProvisioningRoute.OEM_WALLET]?.readiness)
        assertEquals(ProvisioningReadiness.PARTNER_REQUIRED, routes[ProvisioningRoute.PARTNER_TSM]?.readiness)
        assertEquals(ProvisioningReadiness.PRIVILEGED_ONLY, routes[ProvisioningRoute.DIRECT_ESE]?.readiness)
        assertTrue(
            routes[ProvisioningRoute.PARTNER_TSM]
                ?.requirements
                ?.any { it.state == ProvisioningRequirementState.PARTNER_REQUIRED } == true,
        )
        assertTrue(
            routes[ProvisioningRoute.DIRECT_ESE]
                ?.requirements
                ?.any { it.state == ProvisioningRequirementState.PRIVILEGED_ONLY } == true,
        )
    }
}
