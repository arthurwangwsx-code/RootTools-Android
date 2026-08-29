package com.arthur.nfclab.platform.samsung

import com.arthur.nfclab.domain.DeviceIdentity
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.NfcWalletInfo
import com.arthur.nfclab.domain.ProvisioningReadiness
import com.arthur.nfclab.domain.ProvisioningRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class SamsungProvisioningProviderTest {
    @Test
    fun collect_exposesOfficialWalletWithoutInferringOffHostCardType() {
        val profile = NfcDeviceProfile(
            identity = DeviceIdentity("Samsung", "test", "test", "15"),
            capabilities = emptySet(),
            rootAvailable = false,
            selinuxEnforcing = true,
            secureElements = emptyList(),
            wallets = listOf(
                NfcWalletInfo(
                    providerId = SamsungNfcProfileProvider.PROVIDER_ID,
                    label = "Samsung Wallet",
                    packageName = "com.samsung.android.spay",
                ),
            ),
            collectedAtMs = 1L,
        )

        val route = SamsungProvisioningProvider().collect(profile).single()

        assertEquals(ProvisioningRoute.OEM_WALLET, route.route)
        assertEquals(ProvisioningReadiness.MANAGED_EXTERNALLY, route.readiness)
        assertEquals(1, route.unresolvedRequirements.size)
    }
}
