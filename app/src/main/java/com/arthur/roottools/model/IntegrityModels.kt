package com.arthur.roottools.model

enum class AttestationSecurityLevel(val displayName: String) {
    SOFTWARE("Software"),
    TRUSTED_ENVIRONMENT("TEE"),
    STRONGBOX("StrongBox"),
    UNKNOWN("Unknown"),
}

enum class VerifiedBootState(val displayName: String) {
    VERIFIED("Verified"),
    SELF_SIGNED("Self-signed"),
    UNVERIFIED("Unverified"),
    FAILED("Failed"),
    UNKNOWN("Unknown"),
}

enum class AttestationRootAuthority(val displayName: String) {
    GOOGLE("Google"),
    GOOGLE_RKP("Google RKP"),
    KNOX("Samsung Knox"),
    OEM("OEM"),
    AOSP("AOSP"),
    UNKNOWN("Unknown"),
}

enum class IntegrityFindingLevel(val displayName: String) {
    PASS("Pass"),
    INFO("Info"),
    EXPECTED("Expected"),
    WARN("Warning"),
    CRITICAL("Critical"),
    UNAVAILABLE("Unavailable"),
}

data class IntegrityFinding(
    val id: String,
    val title: String,
    val level: IntegrityFindingLevel,
    val summary: String,
    val evidence: String? = null,
)

data class AttestationCertificateSummary(
    val index: Int,
    val subject: String,
    val issuer: String,
    val serialHex: String,
    val notBeforeEpochMs: Long,
    val notAfterEpochMs: Long,
    val signatureValid: Boolean,
    val validityValid: Boolean,
    val revoked: Boolean = false,
    val revocationReason: String? = null,
)

data class KeyAttestationResult(
    val requestedStrongBox: Boolean = false,
    val attempted: Boolean = false,
    val available: Boolean = false,
    val devicePropertiesIncluded: Boolean = false,
    val attestationVersion: Int? = null,
    val attestationSecurityLevel: AttestationSecurityLevel = AttestationSecurityLevel.UNKNOWN,
    val keyMintVersion: Int? = null,
    val keyMintSecurityLevel: AttestationSecurityLevel = AttestationSecurityLevel.UNKNOWN,
    val challengeMatches: Boolean? = null,
    val deviceLocked: Boolean? = null,
    val verifiedBootState: VerifiedBootState = VerifiedBootState.UNKNOWN,
    val verifiedBootKeySha256: String? = null,
    val verifiedBootHashSha256: String? = null,
    val osVersion: String? = null,
    val osPatchLevel: String? = null,
    val vendorPatchLevel: String? = null,
    val bootPatchLevel: String? = null,
    val rootAuthority: AttestationRootAuthority = AttestationRootAuthority.UNKNOWN,
    val rootSpkiSha256: String? = null,
    val remoteProvisioned: Boolean = false,
    val chainSignatureValid: Boolean = false,
    val chainValidityValid: Boolean = false,
    val knownTrustAnchor: Boolean = false,
    val onlineTrustAnchorChecked: Boolean = false,
    val revocationChecked: Boolean = false,
    val revoked: Boolean = false,
    val certificates: List<AttestationCertificateSummary> = emptyList(),
    val error: String? = null,
) {
    val hardwareBacked: Boolean
        get() = attestationSecurityLevel == AttestationSecurityLevel.TRUSTED_ENVIRONMENT ||
            attestationSecurityLevel == AttestationSecurityLevel.STRONGBOX

    val locallyValid: Boolean
        get() = available && challengeMatches == true && chainSignatureValid && chainValidityValid
}

data class IntegritySystemSignals(
    val rootAvailable: Boolean = false,
    val flashLockedProperty: Boolean? = null,
    val vbmetaDeviceState: String? = null,
    val verifiedBootStateProperty: String? = null,
    val verityMode: String? = null,
    val selinuxEnforcing: Boolean? = null,
    val buildType: String? = null,
    val securityPatch: String? = null,
) {
    val bootloaderLockedByProperties: Boolean?
        get() = when {
            flashLockedProperty != null -> flashLockedProperty
            vbmetaDeviceState.equals("locked", ignoreCase = true) -> true
            vbmetaDeviceState.equals("unlocked", ignoreCase = true) -> false
            else -> null
        }
}

data class DeviceIntegritySnapshot(
    val standard: KeyAttestationResult = KeyAttestationResult(),
    val strongBox: KeyAttestationResult = KeyAttestationResult(requestedStrongBox = true),
    val system: IntegritySystemSignals = IntegritySystemSignals(),
    val findings: List<IntegrityFinding> = emptyList(),
    val checkedAtEpochMs: Long = 0L,
    val onlineVerificationError: String? = null,
) {
    val criticalCount: Int get() = findings.count { it.level == IntegrityFindingLevel.CRITICAL }
    val warningCount: Int get() = findings.count { it.level == IntegrityFindingLevel.WARN }
    val expectedCount: Int get() = findings.count { it.level == IntegrityFindingLevel.EXPECTED }
    val passedCount: Int get() = findings.count { it.level == IntegrityFindingLevel.PASS }
}
