package com.arthur.roottools.feature.integrity.policy

import com.arthur.roottools.feature.integrity.model.AttestationRecord
import com.arthur.roottools.feature.integrity.model.AttestationSecurityLevel
import com.arthur.roottools.feature.integrity.model.AttestationSignals
import com.arthur.roottools.feature.integrity.model.AttestationTrustAnchor
import com.arthur.roottools.feature.integrity.model.BootOsSignals
import com.arthur.roottools.feature.integrity.model.IntegrityDisposition
import com.arthur.roottools.feature.integrity.model.IntegrityFindingCode
import com.arthur.roottools.feature.integrity.model.IntegritySignals
import com.arthur.roottools.feature.integrity.model.NativeIntegritySignals
import com.arthur.roottools.feature.integrity.model.RootRuntimeProvider
import com.arthur.roottools.feature.integrity.model.RootRuntimeSignals
import com.arthur.roottools.feature.integrity.model.RuntimeIntegritySignals
import com.arthur.roottools.feature.integrity.model.SelfIdentitySignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityRiskEngineTest {
    @Test
    fun rootedUnlockedDeviceBecomesExpectedAfterTrustedBaseline() {
        val signals = baseSignals()
        val baseline = IntegrityBaselineMatcher.capture("daily", signals, 1L)

        val findings = IntegrityRiskEngine.evaluate(signals, baseline)

        assertEquals(IntegrityDisposition.EXPECTED, findings.first { it.code == IntegrityFindingCode.ROOT_AVAILABLE }.disposition)
        assertEquals(IntegrityDisposition.EXPECTED, findings.first { it.code == IntegrityFindingCode.BOOTLOADER_UNLOCKED }.disposition)
        assertFalse(findings.any { it.disposition == IntegrityDisposition.CRITICAL })
    }

    @Test
    fun signingMismatchIsCritical() {
        val baselineSignals = baseSignals()
        val baseline = IntegrityBaselineMatcher.capture("daily", baselineSignals, 1L)
        val changed = baselineSignals.copy(self = baselineSignals.self.copy(signingSha256 = "other"))

        val finding = IntegrityRiskEngine.evaluate(changed, baseline)
            .first { it.code == IntegrityFindingCode.SELF_SIGNING_BASELINE_MISMATCH }

        assertEquals(IntegrityDisposition.CRITICAL, finding.disposition)
    }

    @Test
    fun apkHashMismatchSameVersionIsCriticalButVersionUpgradeIsNot() {
        val baselineSignals = baseSignals()
        val baseline = IntegrityBaselineMatcher.capture("daily", baselineSignals, 1L)
        val tampered = baselineSignals.copy(self = baselineSignals.self.copy(apkSha256 = "changed"))
        assertEquals(
            IntegrityDisposition.CRITICAL,
            IntegrityRiskEngine.evaluate(tampered, baseline)
                .first { it.code == IntegrityFindingCode.SELF_APK_BASELINE_MISMATCH }.disposition,
        )

        val upgraded = baselineSignals.copy(
            self = baselineSignals.self.copy(
                versionCode = 4,
                compiledVersionCode = 4,
                versionName = "0.4.0",
                compiledVersionName = "0.4.0",
                apkSha256 = "new-build",
            )
        )
        val upgradedFindings = IntegrityRiskEngine.evaluate(upgraded, baseline)
        assertTrue(upgradedFindings.any { it.code == IntegrityFindingCode.SELF_VERSION_CHANGED && it.disposition == IntegrityDisposition.INFO })
        assertFalse(upgradedFindings.any { it.code == IntegrityFindingCode.SELF_APK_BASELINE_MISMATCH })
    }

    @Test
    fun tracerAndRwxAreWarningsWhileFridaMarkerIsCritical() {
        val signals = baseSignals().copy(
            runtime = RuntimeIntegritySignals(
                tracerPid = 123,
                writableExecutableMappings = listOf("rwxp /memfd:test"),
                strongMarkers = setOf("frida-agent-64.so"),
            )
        )
        val findings = IntegrityRiskEngine.evaluate(signals, null)

        assertEquals(IntegrityDisposition.WARN, findings.first { it.code == IntegrityFindingCode.RUNTIME_TRACED }.disposition)
        assertEquals(IntegrityDisposition.WARN, findings.first { it.code == IntegrityFindingCode.RUNTIME_WRITABLE_EXECUTABLE }.disposition)
        assertEquals(IntegrityDisposition.CRITICAL, findings.first { it.code == IntegrityFindingCode.RUNTIME_STRONG_MARKER }.disposition)
    }

    @Test
    fun rootProviderOrModuleDriftIsWarning() {
        val signals = baseSignals()
        val baseline = IntegrityBaselineMatcher.capture("daily", signals, 1L)
        val changed = signals.copy(
            rootRuntime = signals.rootRuntime.copy(
                provider = RootRuntimeProvider.KERNEL_SU,
                modules = setOf("new-module"),
            )
        )

        val findings = IntegrityRiskEngine.evaluate(changed, baseline)

        assertEquals(IntegrityDisposition.WARN, findings.first { it.code == IntegrityFindingCode.ROOT_PROVIDER_CHANGED }.disposition)
        assertEquals(IntegrityDisposition.WARN, findings.first { it.code == IntegrityFindingCode.ROOT_MODULE_SET_CHANGED }.disposition)
    }

    @Test
    fun nativeRuntimeFileMismatchIsCritical() {
        val signals = baseSignals().copy(
            native = NativeIntegritySignals(
                available = true,
                selfExecutableSegments = 2,
                selfExecutableSegmentMismatches = 1,
            )
        )

        val findings = IntegrityRiskEngine.evaluate(signals, null)

        assertEquals(IntegrityDisposition.CRITICAL, findings.first { it.code == IntegrityFindingCode.NATIVE_RUNTIME_FILE_MISMATCH }.disposition)
    }

    @Test
    fun validHardwareAttestationIsPassAndRevocationIsCritical() {
        val good = AttestationRecord(
            available = true,
            securityLevel = AttestationSecurityLevel.TRUSTED_ENVIRONMENT,
            challengeMatched = true,
            chainSignatureValid = true,
            chainValidityValid = true,
            trustAnchor = AttestationTrustAnchor.GOOGLE,
            onlineVerificationAvailable = true,
            revocationCheckedOnline = true,
            deviceLocked = false,
        )
        val goodSignals = baseSignals().copy(attestation = AttestationSignals(standard = good))
        assertTrue(IntegrityRiskEngine.evaluate(goodSignals, null).any { it.code == IntegrityFindingCode.ATTESTATION_READY && it.disposition == IntegrityDisposition.PASS })

        val revokedSignals = baseSignals().copy(attestation = AttestationSignals(standard = good.copy(revokedSerials = setOf("deadbeef"))))
        assertEquals(
            IntegrityDisposition.CRITICAL,
            IntegrityRiskEngine.evaluate(revokedSignals, null)
                .first { it.code == IntegrityFindingCode.ATTESTATION_CERT_REVOKED }.disposition,
        )
    }

    private fun baseSignals() = IntegritySignals(
        self = SelfIdentitySignals(
            packageName = "com.arthur.roottools",
            compiledPackageName = "com.arthur.roottools",
            versionName = "0.3.0",
            compiledVersionName = "0.3.0",
            versionCode = 3,
            compiledVersionCode = 3,
            signingSha256 = "sign",
            apkSha256 = "apk",
        ),
        boot = BootOsSignals(
            frameworkModel = "SM-S908E",
            rootModel = "SM-S908E",
            buildFingerprint = "samsung/fingerprint",
            securityPatch = "2024-11-01",
            verifiedBootState = "orange",
            vbmetaDeviceState = "unlocked",
            flashLocked = false,
            selinuxMode = "Enforcing",
        ),
        rootRuntime = RootRuntimeSignals(
            rootAvailable = true,
            provider = RootRuntimeProvider.MAGISK,
            modules = setOf("zygisk_lsposed"),
            hookFrameworkPackages = setOf("org.lsposed.manager"),
        ),
    )
}
