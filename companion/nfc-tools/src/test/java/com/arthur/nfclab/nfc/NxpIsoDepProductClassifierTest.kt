package com.arthur.nfclab.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class NxpIsoDepProductClassifierTest {
    @Test
    fun classifyProductType_usesNxpFamilyNibble() {
        assertEquals("MIFARE DESFire / DUOX family", NxpIsoDepProductInspector.classifyProductType(0x01))
        assertEquals("MIFARE Plus family", NxpIsoDepProductInspector.classifyProductType(0x02))
        assertEquals("MIFARE DESFire Light family", NxpIsoDepProductInspector.classifyProductType(0x08))
    }

    @Test
    fun classifyProduct_recognizesDesfireEv1TwoKilobyteCard() {
        assertEquals(
            "MIFARE DESFire EV1 2K",
            NxpIsoDepProductInspector.classifyProduct(
                productType = 0x01,
                hardwareMajor = 0x01,
                hardwareMinor = 0x00,
                storageCode = 0x16,
            ),
        )
    }

    @Test
    fun classifyProduct_distinguishesDesfireGenerationsAndDuox() {
        assertEquals(
            "MIFARE DESFire EV2 4K",
            NxpIsoDepProductInspector.classifyProduct(0x01, 0x02, 0x00, 0x18),
        )
        assertEquals(
            "MIFARE DESFire EV3 8K",
            NxpIsoDepProductInspector.classifyProduct(0x01, 0x03, 0x00, 0x1A),
        )
        assertEquals(
            "MIFARE DUOX 2K",
            NxpIsoDepProductInspector.classifyProduct(0x01, 0xA0, 0x00, 0x16),
        )
    }

    @Test
    fun classifyImplementation_usesNxpProductTypeUpperNibble() {
        assertEquals("Native MIFARE IC", NxpIsoDepProductInspector.classifyImplementation(0x01))
        assertEquals("MIFARE implementation", NxpIsoDepProductInspector.classifyImplementation(0x81))
        assertEquals("Java Card applet", NxpIsoDepProductInspector.classifyImplementation(0x91))
        assertEquals("MIFARE 2GO", NxpIsoDepProductInspector.classifyImplementation(0xA1))
    }

    @Test
    fun enrichSnapshot_backfillsPreviouslyCapturedGetVersionFields() {
        val snapshot = TagSnapshot(
            timestampMs = 1L,
            idHex = "TEST",
            technologies = listOf("IsoDep", "NfcA"),
            details = mapOf(
                "NXP product family" to "MIFARE DESFire / DUOX family",
                "NXP hardware version" to "1.0",
                "NXP storage code" to "0x16",
            ),
            ndefRecords = emptyList(),
        )

        val enriched = NxpIsoDepProductInspector.enrichSnapshot(snapshot)

        assertEquals("MIFARE DESFire EV1 2K", enriched.details["NXP product"])
        assertEquals("2K", enriched.details["NXP storage"])
    }
}
