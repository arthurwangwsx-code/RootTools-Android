package com.arthur.roottools.data

import com.arthur.roottools.model.AttestationSecurityLevel
import com.arthur.roottools.model.VerifiedBootState
import org.bouncycastle.asn1.ASN1Boolean
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1TaggedObject
import java.security.MessageDigest

internal data class ParsedKeyAttestation(
    val attestationVersion: Int,
    val attestationSecurityLevel: AttestationSecurityLevel,
    val keyMintVersion: Int,
    val keyMintSecurityLevel: AttestationSecurityLevel,
    val challenge: ByteArray,
    val uniqueIdPresent: Boolean,
    val deviceLocked: Boolean?,
    val verifiedBootState: VerifiedBootState,
    val verifiedBootKeySha256: String?,
    val verifiedBootHashSha256: String?,
    val osVersion: String?,
    val osPatchLevel: String?,
    val vendorPatchLevel: String?,
    val bootPatchLevel: String?,
)

/** Pure parser for Android's KeyDescription certificate extension. */
internal object AttestationParser {
    const val KEY_ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17"
    const val PROVISIONING_INFO_OID = "1.3.6.1.4.1.11129.2.1.30"

    private const val TAG_ROOT_OF_TRUST = 704
    private const val TAG_OS_VERSION = 705
    private const val TAG_OS_PATCH_LEVEL = 706
    private const val TAG_VENDOR_PATCH_LEVEL = 718
    private const val TAG_BOOT_PATCH_LEVEL = 719

    fun parse(extensionValue: ByteArray): ParsedKeyAttestation {
        val wrapped = ASN1OctetString.getInstance(ASN1Primitive.fromByteArray(extensionValue))
        val sequence = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(wrapped.octets))
        require(sequence.size() >= 8) { "KeyDescription has ${sequence.size()} fields" }

        val attestationVersion = ASN1Integer.getInstance(sequence.getObjectAt(0)).value.toInt()
        val attestationSecurityLevel = securityLevel(ASN1Enumerated.getInstance(sequence.getObjectAt(1)).value.toInt())
        val keyMintVersion = ASN1Integer.getInstance(sequence.getObjectAt(2)).value.toInt()
        val keyMintSecurityLevel = securityLevel(ASN1Enumerated.getInstance(sequence.getObjectAt(3)).value.toInt())
        val challenge = ASN1OctetString.getInstance(sequence.getObjectAt(4)).octets
        val uniqueId = ASN1OctetString.getInstance(sequence.getObjectAt(5)).octets
        val softwareEnforced = ASN1Sequence.getInstance(sequence.getObjectAt(6))
        val hardwareEnforced = ASN1Sequence.getInstance(sequence.getObjectAt(7))

        val rootOfTrust = findTagged(hardwareEnforced, TAG_ROOT_OF_TRUST)
            ?: findTagged(softwareEnforced, TAG_ROOT_OF_TRUST)
        val parsedRoot = rootOfTrust?.let(::parseRootOfTrust)

        return ParsedKeyAttestation(
            attestationVersion = attestationVersion,
            attestationSecurityLevel = attestationSecurityLevel,
            keyMintVersion = keyMintVersion,
            keyMintSecurityLevel = keyMintSecurityLevel,
            challenge = challenge,
            uniqueIdPresent = uniqueId.isNotEmpty(),
            deviceLocked = parsedRoot?.deviceLocked,
            verifiedBootState = parsedRoot?.verifiedBootState ?: VerifiedBootState.UNKNOWN,
            verifiedBootKeySha256 = parsedRoot?.verifiedBootKey?.takeIf { it.isNotEmpty() }?.let(::sha256),
            verifiedBootHashSha256 = parsedRoot?.verifiedBootHash?.takeIf { it.isNotEmpty() }?.let(::sha256),
            osVersion = readTaggedInteger(hardwareEnforced, softwareEnforced, TAG_OS_VERSION)?.let(::formatOsVersion),
            osPatchLevel = readTaggedInteger(hardwareEnforced, softwareEnforced, TAG_OS_PATCH_LEVEL)?.let(::formatOsPatch),
            vendorPatchLevel = readTaggedInteger(hardwareEnforced, softwareEnforced, TAG_VENDOR_PATCH_LEVEL)?.let(::formatDatePatch),
            bootPatchLevel = readTaggedInteger(hardwareEnforced, softwareEnforced, TAG_BOOT_PATCH_LEVEL)?.let(::formatDatePatch),
        )
    }

    private data class ParsedRootOfTrust(
        val verifiedBootKey: ByteArray,
        val deviceLocked: Boolean,
        val verifiedBootState: VerifiedBootState,
        val verifiedBootHash: ByteArray,
    )

    private fun parseRootOfTrust(tagged: ASN1TaggedObject): ParsedRootOfTrust {
        val sequence = ASN1Sequence.getInstance(tagged, true)
        require(sequence.size() >= 3) { "RootOfTrust has ${sequence.size()} fields" }
        return ParsedRootOfTrust(
            verifiedBootKey = ASN1OctetString.getInstance(sequence.getObjectAt(0)).octets,
            deviceLocked = ASN1Boolean.getInstance(sequence.getObjectAt(1)).isTrue,
            verifiedBootState = verifiedBootState(ASN1Enumerated.getInstance(sequence.getObjectAt(2)).value.toInt()),
            verifiedBootHash = if (sequence.size() >= 4) ASN1OctetString.getInstance(sequence.getObjectAt(3)).octets else byteArrayOf(),
        )
    }

    private fun readTaggedInteger(
        hardware: ASN1Sequence,
        software: ASN1Sequence,
        tag: Int,
    ): Int? {
        val value = findTagged(hardware, tag) ?: findTagged(software, tag) ?: return null
        return ASN1Integer.getInstance(value, true).value.toInt()
    }

    private fun findTagged(sequence: ASN1Sequence, tag: Int): ASN1TaggedObject? {
        for (index in 0 until sequence.size()) {
            val current = runCatching { ASN1TaggedObject.getInstance(sequence.getObjectAt(index)) }.getOrNull() ?: continue
            if (current.tagNo == tag) return current
        }
        return null
    }

    private fun securityLevel(value: Int): AttestationSecurityLevel = when (value) {
        0 -> AttestationSecurityLevel.SOFTWARE
        1 -> AttestationSecurityLevel.TRUSTED_ENVIRONMENT
        2 -> AttestationSecurityLevel.STRONGBOX
        else -> AttestationSecurityLevel.UNKNOWN
    }

    private fun verifiedBootState(value: Int): VerifiedBootState = when (value) {
        0 -> VerifiedBootState.VERIFIED
        1 -> VerifiedBootState.SELF_SIGNED
        2 -> VerifiedBootState.UNVERIFIED
        3 -> VerifiedBootState.FAILED
        else -> VerifiedBootState.UNKNOWN
    }

    private fun formatOsVersion(value: Int): String {
        if (value <= 0) return value.toString()
        val major = value / 10_000
        val minor = (value / 100) % 100
        val patch = value % 100
        return "$major.$minor.$patch"
    }

    private fun formatOsPatch(value: Int): String {
        if (value <= 0) return value.toString()
        val year = value / 100
        val month = value % 100
        return if (year in 2000..2999 && month in 1..12) "%04d-%02d".format(year, month) else value.toString()
    }

    private fun formatDatePatch(value: Int): String {
        if (value <= 0) return value.toString()
        val year = value / 10_000
        val month = (value / 100) % 100
        val day = value % 100
        return if (year in 2000..2999 && month in 1..12 && day in 1..31) {
            "%04d-%02d-%02d".format(year, month, day)
        } else {
            formatOsPatch(value)
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
