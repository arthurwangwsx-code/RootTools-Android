package com.arthur.roottools.data

import com.arthur.roottools.model.AttestationSecurityLevel
import com.arthur.roottools.model.VerifiedBootState
import org.bouncycastle.asn1.ASN1Boolean
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERTaggedObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttestationParserTest {
    @Test
    fun parsesRootOfTrustAndPatchLevels() {
        val challenge = byteArrayOf(1, 2, 3, 4, 5)
        val verifiedBootKey = byteArrayOf(9, 8, 7, 6)
        val verifiedBootHash = byteArrayOf(4, 3, 2, 1)
        val rootOfTrust = DERSequence(
            arrayOf(
                DEROctetString(verifiedBootKey),
                ASN1Boolean.FALSE,
                ASN1Enumerated(2),
                DEROctetString(verifiedBootHash),
            )
        )
        val hardware = DERSequence(
            ASN1EncodableVector().apply {
                add(DERTaggedObject(true, 704, rootOfTrust))
                add(DERTaggedObject(true, 705, ASN1Integer(140000)))
                add(DERTaggedObject(true, 706, ASN1Integer(202608)))
                add(DERTaggedObject(true, 718, ASN1Integer(20260801)))
                add(DERTaggedObject(true, 719, ASN1Integer(20260805)))
            }
        )
        val keyDescription = DERSequence(
            arrayOf(
                ASN1Integer(400),
                ASN1Enumerated(1),
                ASN1Integer(400),
                ASN1Enumerated(2),
                DEROctetString(challenge),
                DEROctetString(byteArrayOf()),
                DERSequence(),
                hardware,
            )
        )
        val extensionValue = DEROctetString(keyDescription.encoded).encoded

        val parsed = AttestationParser.parse(extensionValue)

        assertEquals(400, parsed.attestationVersion)
        assertEquals(AttestationSecurityLevel.TRUSTED_ENVIRONMENT, parsed.attestationSecurityLevel)
        assertEquals(400, parsed.keyMintVersion)
        assertEquals(AttestationSecurityLevel.STRONGBOX, parsed.keyMintSecurityLevel)
        assertTrue(parsed.challenge.contentEquals(challenge))
        assertFalse(parsed.uniqueIdPresent)
        assertEquals(false, parsed.deviceLocked)
        assertEquals(VerifiedBootState.UNVERIFIED, parsed.verifiedBootState)
        assertEquals("14.0.0", parsed.osVersion)
        assertEquals("2026-08", parsed.osPatchLevel)
        assertEquals("2026-08-01", parsed.vendorPatchLevel)
        assertEquals("2026-08-05", parsed.bootPatchLevel)
        assertEquals(64, parsed.verifiedBootKeySha256?.length)
        assertEquals(64, parsed.verifiedBootHashSha256?.length)
    }
}
