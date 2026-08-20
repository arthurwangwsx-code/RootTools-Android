package com.arthur.roottools.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class AttestationSupportParserTest {
    @Test
    fun parsesRootShellSignalLines() {
        val values = DeviceIntegrityRepository.parseKeyValueLines(
            """
            flash_locked=0
            vbmeta_state=unlocked
            verified_boot=orange
            selinux=Enforcing
            """.trimIndent()
        )

        assertEquals("0", values["flash_locked"])
        assertEquals("unlocked", values["vbmeta_state"])
        assertEquals("orange", values["verified_boot"])
        assertEquals("Enforcing", values["selinux"])
    }

    @Test
    fun revocationSerialCandidatesCoverDecimalAndHexKeys() {
        val value = BigInteger("12345678901234567890")
        val candidates = AttestationChainVerifier.serialCandidates(value)

        assertTrue(value.toString(10) in candidates)
        assertTrue(value.toString(16) in candidates)
        assertEquals("abcdef", GoogleAttestationStatusClient.normalizeSerial("0x00ABCDEF"))
    }
}
