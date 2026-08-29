package com.arthur.nfclab.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagCompatibilityComparatorTest {
    @Test
    fun compare_reportsSameProductWhenOnlyUidDiffers() {
        val left = snapshot("A1", "MIFARE DESFire EV1 2K")
        val right = snapshot("B2", "MIFARE DESFire EV1 2K")

        val comparison = TagCompatibilityComparator.compare(left, right)

        assertTrue(comparison.sameTechnologySet)
        assertEquals(true, comparison.sameProduct)
        assertTrue(comparison.differences.isEmpty())
    }

    @Test
    fun compare_reportsDifferentProductGeneration() {
        val left = snapshot("A1", "MIFARE DESFire EV1 2K")
        val right = snapshot("B2", "MIFARE DESFire EV3 8K")

        val comparison = TagCompatibilityComparator.compare(left, right)

        assertFalse(comparison.differences.isEmpty())
        assertEquals(false, comparison.sameProduct)
    }

    @Test
    fun latestDistinctPair_skipsRepeatedReadsOfSameTag() {
        val history = listOf(
            snapshot("A1", "MIFARE DESFire EV1 2K", 30),
            snapshot("A1", "MIFARE DESFire EV1 2K", 20),
            snapshot("B2", "MIFARE DESFire EV1 2K", 10),
        )

        val pair = TagCompatibilityComparator.latestDistinctPair(history)

        assertNotNull(pair)
        assertEquals("A1", pair!!.first.idHex)
        assertEquals("B2", pair.second.idHex)
    }

    private fun snapshot(id: String, product: String, time: Long = 1L) = TagSnapshot(
        timestampMs = time,
        idHex = id,
        technologies = listOf("IsoDep", "NfcA"),
        details = mapOf(
            "NFC-A ATQA" to "4403",
            "NFC-A SAK" to "0x20",
            "NXP product" to product,
            "NXP product family" to "MIFARE DESFire / DUOX family",
            "NXP storage code" to "0x16",
        ),
        ndefRecords = emptyList(),
    )
}
