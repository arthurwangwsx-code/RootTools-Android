package com.arthur.roottools.data

import com.arthur.roottools.model.AttestationCertificateSummary
import com.arthur.roottools.model.AttestationRootAuthority
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date

internal data class ChainVerificationResult(
    val rootAuthority: AttestationRootAuthority,
    val chainSignatureValid: Boolean,
    val chainValidityValid: Boolean,
    val knownTrustAnchor: Boolean,
    val remoteProvisioned: Boolean,
    val rootSpkiSha256: String?,
    val certificates: List<AttestationCertificateSummary>,
)

internal object AttestationChainVerifier {
    // Current Google Android Key Attestation trust anchors. The first is the long-lived RSA root;
    // the second is the 2026 EC Key Attestation CA1 root published by android/keyattestation.
    val builtInGoogleRootSpkiSha256: Set<String> = setOf(
        "feb2ea7551ee316ed4bb443c8293b884dbfdea40b603ee3e4f4a897e4580fbae",
        "3ee44512a1af2beb39c889490c60ea3f82e43f5d5a5532f5ab9419f676cd07ec",
    )

    private val aospRootSpkiSha256 = setOf(
        "d5100c7942ef2e8310dc30ef82729680cf48d690735c3f68179a33c7c370f286",
        "f2c4746f545946c100e72297f8f946344d7052f03a2f694221f9c893b0e6f711",
    )

    private val knoxRootSpkiSha256 = setOf(
        "b1ebc512fc4fa78f1b5485360a612c9bca058328831be8ce041181cfc6cc0927",
        "6b15a74d196a6453ecc359797e37e9f3385b6190f8ce7b28100aa881b96725d8",
        "a98d7466cdc535d4ec870837fe16e85f874a341ac257ce73913b0c987eaaa82d",
    )

    fun verify(
        certificates: List<X509Certificate>,
        oemRootSpkiSha256: Set<String> = emptySet(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ChainVerificationResult {
        if (certificates.isEmpty()) {
            return ChainVerificationResult(
                rootAuthority = AttestationRootAuthority.UNKNOWN,
                chainSignatureValid = false,
                chainValidityValid = false,
                knownTrustAnchor = false,
                remoteProvisioned = false,
                rootSpkiSha256 = null,
                certificates = emptyList(),
            )
        }

        val now = Date(nowEpochMs)
        val signatureStates = certificates.indices.map { index ->
            val cert = certificates[index]
            val issuerKey = certificates.getOrNull(index + 1)?.publicKey ?: cert.publicKey
            runCatching { cert.verify(issuerKey) }.isSuccess
        }
        val validityStates = certificates.map { cert -> runCatching { cert.checkValidity(now) }.isSuccess }
        val remoteProvisioned = certificates.any { cert ->
            cert.getExtensionValue(AttestationParser.PROVISIONING_INFO_OID) != null
        }
        val root = certificates.last()
        val rootSpki = sha256(root.publicKey.encoded)
        val baseAuthority = when (rootSpki) {
            in builtInGoogleRootSpkiSha256 -> AttestationRootAuthority.GOOGLE
            in knoxRootSpkiSha256 -> AttestationRootAuthority.KNOX
            in aospRootSpkiSha256 -> AttestationRootAuthority.AOSP
            in oemRootSpkiSha256 -> AttestationRootAuthority.OEM
            else -> AttestationRootAuthority.UNKNOWN
        }
        val authority = if (baseAuthority == AttestationRootAuthority.GOOGLE && remoteProvisioned) {
            AttestationRootAuthority.GOOGLE_RKP
        } else {
            baseAuthority
        }

        return ChainVerificationResult(
            rootAuthority = authority,
            chainSignatureValid = signatureStates.all { it },
            chainValidityValid = validityStates.all { it },
            knownTrustAnchor = authority != AttestationRootAuthority.UNKNOWN,
            remoteProvisioned = remoteProvisioned,
            rootSpkiSha256 = rootSpki,
            certificates = certificates.mapIndexed { index, cert ->
                AttestationCertificateSummary(
                    index = index,
                    subject = cert.subjectX500Principal.name,
                    issuer = cert.issuerX500Principal.name,
                    serialHex = cert.serialNumber.toString(16),
                    notBeforeEpochMs = cert.notBefore.time,
                    notAfterEpochMs = cert.notAfter.time,
                    signatureValid = signatureStates[index],
                    validityValid = validityStates[index],
                )
            },
        )
    }

    fun serialCandidates(serial: BigInteger): Set<String> = setOf(
        serial.toString(10).lowercase(),
        serial.toString(16).lowercase(),
    )

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
