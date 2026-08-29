package com.arthur.nfclab.platform.xiaomi

import com.arthur.nfclab.domain.DeviceIdentity
import com.arthur.nfclab.domain.NfcCapability
import com.arthur.nfclab.domain.NfcCardRoute
import com.arthur.nfclab.domain.NfcDeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiNfcProfileProviderTest {
    @Test
    fun mapProfile_convertsXiaomiDetailsIntoGenericCapabilitiesAndCards() {
        val base = NfcDeviceProfile(
            identity = DeviceIdentity("Xiaomi", "test", "test", "15"),
            capabilities = setOf(NfcCapability.NFC_READER, NfcCapability.HCE_ISO_DEP),
            rootAvailable = false,
            selinuxEnforcing = true,
            secureElements = emptyList(),
            collectedAtMs = 1L,
        )
        val xiaomi = XiaomiNfcProfile(
            manufacturer = "Xiaomi",
            model = "23127PN0CC",
            device = "houji",
            androidRelease = "15",
            hyperOsVersion = "OS2.0",
            rootAvailable = true,
            selinuxEnforcing = true,
            nfcFirmware = "01.01.38",
            nfcChipId = "0xc1",
            nfcPort = "I2C",
            walletVersion = "26.06.16.1.f",
            eSeConnected = true,
            mifareReaderEnabled = true,
            defaultMifareRoute = "0x01",
            hostListenTechMask = "0x07",
            miNfcServiceAvailable = true,
            nxpVendorServiceAvailable = true,
            miNfcApiVersion = 1,
            seRouting = 1,
            listenTechMask = 1,
            pollingTechMask = 15,
            cards = listOf(
                XiaomiVirtualCard(
                    title = "测试门卡",
                    aid = "A0000003964D344D10045920D5951E00",
                    active = true,
                    productName = "实体门卡",
                    mifareCardType = 0,
                    sectorOverwritten = true,
                ),
            ),
            collectedAtMs = 2L,
        )

        val result = XiaomiNfcProfileProvider().mapProfile(base, xiaomi)

        assertTrue(result.has(NfcCapability.ROOT))
        assertTrue(result.has(NfcCapability.ESE))
        assertTrue(result.has(NfcCapability.MIFARE_READER))
        assertTrue(result.has(NfcCapability.MIFARE_OFF_HOST))
        assertTrue(result.has(NfcCapability.VENDOR_NFC_API))
        assertEquals("Xiaomi / NXP NFC", result.vendor?.displayName)
        assertEquals(1, result.wallets.size)
        assertEquals("小米钱包", result.primaryWallet?.label)
        assertEquals(XiaomiNfcProfileProvider.XIAOMI_DOOR_CARD_SELECT_ACTION, result.primaryWallet?.managementAction)
        assertEquals(1, result.cards.size)
        assertEquals(NfcCardRoute.ESE, result.cards.single().route)
        assertEquals("测试门卡", result.activeCard?.title)
    }
}
