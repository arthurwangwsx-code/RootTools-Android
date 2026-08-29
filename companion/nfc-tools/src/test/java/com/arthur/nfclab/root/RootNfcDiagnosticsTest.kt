package com.arthur.nfclab.root

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootNfcDiagnosticsTest {
    @Test
    fun redactCredentialIdentifiers_hidesWalletCredentialIdentifiers() {
        val input = """
            Cards:2
            AID:A0000003964D344D10045920D5951E00 STATUS:ACTIVATED UID:0123456789abcdef TYPE:M1
            door_card_vc_uid=abcdef0123456789
            door_card_cid:12345678-1234-5678-9abc-def012345678
        """.trimIndent()

        val output = RootNfcDiagnostics.redactCredentialIdentifiers(input)

        assertTrue(output.contains("AID:A0000003964D344D10045920D5951E00"))
        assertTrue(output.contains("STATUS:ACTIVATED"))
        assertTrue(output.contains("TYPE:M1"))
        assertTrue(output.contains("UID:<redacted>"))
        assertFalse(output.contains("0123456789abcdef"))
        assertFalse(output.contains("abcdef0123456789"))
        assertFalse(output.contains("12345678-1234-5678-9abc-def012345678"))
    }
}
