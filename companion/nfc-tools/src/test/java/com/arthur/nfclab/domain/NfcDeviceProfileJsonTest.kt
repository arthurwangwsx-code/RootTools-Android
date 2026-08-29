package com.arthur.nfclab.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcDeviceProfileJsonTest {
    @Test
    fun jsonRoundTrip_preservesProductFacingProfile() {
        val original = NfcDeviceProfile(
            identity = DeviceIdentity("Example", "Phone", "device", "15", "ExampleOS"),
            capabilities = setOf(NfcCapability.NFC_READER, NfcCapability.ESE, NfcCapability.MIFARE_OFF_HOST),
            rootAvailable = true,
            selinuxEnforcing = true,
            secureElements = listOf(SecureElementInfo(SecureElementType.ESE, "eSE1", true, true)),
            vendor = VendorNfcDetails(
                providerId = "example.vendor",
                displayName = "Example NFC",
                firmware = "1.2.3",
                extras = mapOf("route" to "ese"),
            ),
            wallets = listOf(
                NfcWalletInfo("example.wallet", "Example Wallet", "com.example.wallet", "2.0", "example.MANAGE"),
            ),
            cards = listOf(
                NfcCard(
                    id = "A00001",
                    title = "Office",
                    kind = NfcCardKind.MIFARE_CLASSIC,
                    technologyLabel = "M1",
                    active = true,
                    route = NfcCardRoute.ESE,
                    sourceId = "example.wallet",
                    sourceLabel = "Example Wallet",
                    metadata = mapOf("sectorOverwritten" to "true"),
                ),
            ),
            collectedAtMs = 1234L,
        )

        val restored = NfcDeviceProfile.fromJson(original.toJson())

        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun parser_ignoresUnknownFutureCapability() {
        val json = JSONObject(
            NfcDeviceProfile(
                identity = DeviceIdentity("Example", "Phone", "device", "15"),
                capabilities = setOf(NfcCapability.NFC_READER),
                rootAvailable = false,
                selinuxEnforcing = null,
                secureElements = emptyList(),
                collectedAtMs = 1L,
            ).toJson().toString(),
        )
        json.getJSONArray("capabilities").put("FUTURE_VENDOR_CAPABILITY")

        val restored = NfcDeviceProfile.fromJson(json)

        assertNotNull(restored)
        assertTrue(NfcCapability.NFC_READER in restored!!.capabilities)
    }
}
