package com.arthur.roottools.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import com.arthur.roottools.model.AttestationRootAuthority
import com.arthur.roottools.model.DeviceIntegritySnapshot
import com.arthur.roottools.model.IntegritySystemSignals
import com.arthur.roottools.model.KeyAttestationResult
import com.arthur.roottools.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec

class DeviceIntegrityRepository(
    private val context: Context,
    private val shell: RootShell,
    private val onlineClient: GoogleAttestationStatusClient = GoogleAttestationStatusClient(),
) {
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    }
    private var lastExportableChains: List<List<X509Certificate>> = emptyList()

    suspend fun scan(): DeviceIntegritySnapshot = withContext(Dispatchers.IO) {
        val system = readSystemSignals()
        val oemRootHashes = readOemRootSpkiSha256()
        val standardAttempt = generateAttestation(requestStrongBox = false, oemRootHashes = oemRootHashes)
        val strongBoxAttempt = if (supportsStrongBox()) {
            generateAttestation(requestStrongBox = true, oemRootHashes = oemRootHashes)
        } else {
            AttestationAttempt(
                result = KeyAttestationResult(
                    requestedStrongBox = true,
                    attempted = false,
                    available = false,
                    error = "设备未声明 FEATURE_STRONGBOX_KEYSTORE",
                )
            )
        }

        lastExportableChains = listOfNotNull(
            standardAttempt.certificates.takeIf { it.isNotEmpty() },
            strongBoxAttempt.certificates.takeIf { it.isNotEmpty() },
        )

        val online = onlineClient.fetch()
        val standard = enrichWithOnlineMetadata(standardAttempt.result, online)
        val strongBox = enrichWithOnlineMetadata(strongBoxAttempt.result, online)
        val onlineError = online.errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
        val snapshot = DeviceIntegritySnapshot(
            standard = standard,
            strongBox = strongBox,
            system = system,
            checkedAtEpochMs = System.currentTimeMillis(),
            onlineVerificationError = onlineError,
        )
        AttestationIntegrityRiskEngine.snapshotWithFindings(snapshot)
    }

    suspend fun exportLastCertificateChains(): File? = withContext(Dispatchers.IO) {
        val certs = lastExportableChains.flatten()
        if (certs.isEmpty()) return@withContext null
        val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val file = File(directory, "roottools-attestation-${System.currentTimeMillis()}.pem")
        file.writeText(
            certs.joinToString("\n") { certificate ->
                val body = Base64.encodeToString(certificate.encoded, Base64.NO_WRAP)
                    .chunked(64)
                    .joinToString("\n")
                "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----"
            }
        )
        file
    }

    private suspend fun readSystemSignals(): IntegritySystemSignals {
        val rootAvailable = shell.isAvailable(timeoutSeconds = 4)
        val values = if (rootAvailable) {
            val command = """
                printf 'flash_locked=%s\n' "$(getprop ro.boot.flash.locked)"
                printf 'vbmeta_state=%s\n' "$(getprop ro.boot.vbmeta.device_state)"
                printf 'verified_boot=%s\n' "$(getprop ro.boot.verifiedbootstate)"
                printf 'verity_mode=%s\n' "$(getprop ro.boot.veritymode)"
                printf 'build_type=%s\n' "$(getprop ro.build.type)"
                printf 'selinux=%s\n' "$(getenforce 2>/dev/null)"
            """.trimIndent()
            val result = shell.execute(command, timeoutSeconds = 5)
            if (result.success) parseKeyValueLines(result.output) else emptyMap()
        } else {
            emptyMap()
        }
        return IntegritySystemSignals(
            rootAvailable = rootAvailable,
            flashLockedProperty = values["flash_locked"]?.toBooleanFlag(),
            vbmetaDeviceState = values["vbmeta_state"].nullIfBlank(),
            verifiedBootStateProperty = values["verified_boot"].nullIfBlank(),
            verityMode = values["verity_mode"].nullIfBlank(),
            selinuxEnforcing = values["selinux"]?.let { it.equals("Enforcing", ignoreCase = true) },
            buildType = values["build_type"].nullIfBlank() ?: Build.TYPE,
            securityPatch = Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() },
        )
    }

    private fun generateAttestation(
        requestStrongBox: Boolean,
        oemRootHashes: Set<String>,
    ): AttestationAttempt {
        val alias = if (requestStrongBox) STRONGBOX_ALIAS else STANDARD_ALIAS
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        deleteAlias(alias)
        return try {
            generateKey(alias, challenge, requestStrongBox, includeDeviceProperties = true)
            readAttestation(alias, challenge, requestStrongBox, oemRootHashes, devicePropertiesIncluded = true)
        } catch (first: Throwable) {
            deleteAlias(alias)
            try {
                // Some OEMs expose Key Attestation but reject the optional model/property block.
                generateKey(alias, challenge, requestStrongBox, includeDeviceProperties = false)
                readAttestation(alias, challenge, requestStrongBox, oemRootHashes, devicePropertiesIncluded = false)
            } catch (second: Throwable) {
                AttestationAttempt(
                    result = KeyAttestationResult(
                        requestedStrongBox = requestStrongBox,
                        attempted = true,
                        available = false,
                        error = describeAttestationFailure(second, first),
                    )
                )
            }
        } finally {
            deleteAlias(alias)
        }
    }

    private fun generateKey(
        alias: String,
        challenge: ByteArray,
        requestStrongBox: Boolean,
        includeDeviceProperties: Boolean,
    ) {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && includeDeviceProperties) {
            builder.setDevicePropertiesAttestationIncluded(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && requestStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE).apply {
            initialize(builder.build())
            generateKeyPair()
        }
    }

    private fun readAttestation(
        alias: String,
        challenge: ByteArray,
        requestStrongBox: Boolean,
        oemRootHashes: Set<String>,
        devicePropertiesIncluded: Boolean,
    ): AttestationAttempt {
        val certificates = keyStore.getCertificateChain(alias)
            ?.mapNotNull { it as? X509Certificate }
            .orEmpty()
        require(certificates.isNotEmpty()) { "AndroidKeyStore returned an empty certificate chain" }
        val extension = certificates.first().getExtensionValue(AttestationParser.KEY_ATTESTATION_OID)
            ?: error("Leaf certificate has no Android Key Attestation extension")
        val parsed = AttestationParser.parse(extension)
        val verified = AttestationChainVerifier.verify(certificates, oemRootHashes)
        return AttestationAttempt(
            certificates = certificates,
            result = KeyAttestationResult(
                requestedStrongBox = requestStrongBox,
                attempted = true,
                available = true,
                devicePropertiesIncluded = devicePropertiesIncluded,
                attestationVersion = parsed.attestationVersion,
                attestationSecurityLevel = parsed.attestationSecurityLevel,
                keyMintVersion = parsed.keyMintVersion,
                keyMintSecurityLevel = parsed.keyMintSecurityLevel,
                challengeMatches = parsed.challenge.contentEquals(challenge),
                deviceLocked = parsed.deviceLocked,
                verifiedBootState = parsed.verifiedBootState,
                verifiedBootKeySha256 = parsed.verifiedBootKeySha256,
                verifiedBootHashSha256 = parsed.verifiedBootHashSha256,
                osVersion = parsed.osVersion,
                osPatchLevel = parsed.osPatchLevel,
                vendorPatchLevel = parsed.vendorPatchLevel,
                bootPatchLevel = parsed.bootPatchLevel,
                rootAuthority = verified.rootAuthority,
                rootSpkiSha256 = verified.rootSpkiSha256,
                remoteProvisioned = verified.remoteProvisioned,
                chainSignatureValid = verified.chainSignatureValid,
                chainValidityValid = verified.chainValidityValid,
                knownTrustAnchor = verified.knownTrustAnchor,
                certificates = verified.certificates,
            )
        )
    }

    private fun enrichWithOnlineMetadata(
        local: KeyAttestationResult,
        online: GoogleAttestationOnlineData,
    ): KeyAttestationResult {
        if (!local.available) return local.copy(
            onlineTrustAnchorChecked = online.rootsChecked,
            revocationChecked = online.revocationsChecked,
        )

        val onlineGoogleRoot = local.rootSpkiSha256 != null && local.rootSpkiSha256 in online.rootSpkiSha256
        val rootAuthority = when {
            onlineGoogleRoot && local.remoteProvisioned -> AttestationRootAuthority.GOOGLE_RKP
            onlineGoogleRoot -> AttestationRootAuthority.GOOGLE
            else -> local.rootAuthority
        }
        val certificates = local.certificates.map { cert ->
            val revocation = if (online.revocationsChecked) {
                val serial = runCatching { java.math.BigInteger(cert.serialHex, 16) }.getOrNull()
                serial?.let { number ->
                    AttestationChainVerifier.serialCandidates(number)
                        .asSequence()
                        .map(GoogleAttestationStatusClient::normalizeSerial)
                        .mapNotNull { online.revocations[it] }
                        .firstOrNull()
                }
            } else {
                null
            }
            cert.copy(
                revoked = revocation != null,
                revocationReason = revocation?.let { entry ->
                    listOfNotNull(entry.status, entry.reason).joinToString(" · ")
                },
            )
        }
        return local.copy(
            rootAuthority = rootAuthority,
            knownTrustAnchor = local.knownTrustAnchor || onlineGoogleRoot,
            onlineTrustAnchorChecked = online.rootsChecked,
            revocationChecked = online.revocationsChecked,
            revoked = certificates.any { it.revoked },
            certificates = certificates,
        )
    }

    private fun readOemRootSpkiSha256(): Set<String> {
        val id = context.resources.getIdentifier(
            "vendor_required_attestation_certificates",
            "array",
            "android",
        )
        if (id == 0) return emptySet()
        val factory = CertificateFactory.getInstance("X.509")
        return runCatching {
            context.resources.getStringArray(id).mapNotNullTo(mutableSetOf()) { raw ->
                runCatching {
                    val normalized = raw
                        .replace(Regex("\\s+"), "\n")
                        .replace("-BEGIN\nCERTIFICATE-", "-BEGIN CERTIFICATE-")
                        .replace("-END\nCERTIFICATE-", "-END CERTIFICATE-")
                    val cert = factory.generateCertificate(ByteArrayInputStream(normalized.toByteArray())) as X509Certificate
                    AttestationChainVerifier.sha256(cert.publicKey.encoded)
                }.getOrNull()
            }
        }.getOrDefault(emptySet())
    }

    private fun supportsStrongBox(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private fun deleteAlias(alias: String) {
        runCatching {
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
    }

    private fun describeAttestationFailure(current: Throwable, previous: Throwable): String {
        val currentText = when (current) {
            is StrongBoxUnavailableException -> "StrongBox unavailable"
            else -> current.message ?: current.javaClass.simpleName
        }
        val previousText = previous.message ?: previous.javaClass.simpleName
        return if (currentText == previousText) currentText else "$currentText · device properties retry: $previousText"
    }

    private data class AttestationAttempt(
        val result: KeyAttestationResult,
        val certificates: List<X509Certificate> = emptyList(),
    )

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val STANDARD_ALIAS = "roottools_integrity_attestation"
        private const val STRONGBOX_ALIAS = "roottools_integrity_attestation_strongbox"

        internal fun parseKeyValueLines(text: String): Map<String, String> = buildMap {
            text.lineSequence().forEach { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@forEach
                put(line.substring(0, index).trim(), line.substring(index + 1).trim())
            }
        }

        private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

        private fun String.toBooleanFlag(): Boolean? = when (trim().lowercase()) {
            "1", "true", "locked" -> true
            "0", "false", "unlocked" -> false
            else -> null
        }
    }
}
