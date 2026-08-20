package com.arthur.roottools.data

import com.arthur.roottools.model.AttestationRootAuthority
import com.arthur.roottools.model.AttestationSecurityLevel
import com.arthur.roottools.model.IntegrityFindingLevel
import com.arthur.roottools.model.IntegritySystemSignals
import com.arthur.roottools.model.KeyAttestationResult
import com.arthur.roottools.model.VerifiedBootState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttestationIntegrityRiskEngineTest {
    private val healthyAttestation = KeyAttestationResult(
        attempted = true,
        available = true,
        attestationSecurityLevel = AttestationSecurityLevel.TRUSTED_ENVIRONMENT,
        keyMintSecurityLevel = AttestationSecurityLevel.TRUSTED_ENVIRONMENT,
        challengeMatches = true,
        deviceLocked = false,
        verifiedBootState = VerifiedBootState.UNVERIFIED,
        osPatchLevel = "2026-08",
        rootAuthority = AttestationRootAuthority.GOOGLE,
        chainSignatureValid = true,
        chainValidityValid = true,
        knownTrustAnchor = true,
    )

    @Test
    fun rootedUnlockedDeviceIsExpectedNotCritical() {
        val findings = AttestationIntegrityRiskEngine.evaluate(
            standard = healthyAttestation,
            strongBox = KeyAttestationResult(requestedStrongBox = true, error = "unsupported"),
            system = IntegritySystemSignals(
                rootAvailable = true,
                flashLockedProperty = false,
                verifiedBootStateProperty = "orange",
                selinuxEnforcing = true,
                securityPatch = "2026-08-01",
            ),
        )

        assertTrue(findings.any { it.id == "context.root.expected" && it.level == IntegrityFindingLevel.EXPECTED })
        assertTrue(findings.any { it.id == "context.bootloader.unlocked" && it.level == IntegrityFindingLevel.EXPECTED })
        assertFalse(findings.any { it.level == IntegrityFindingLevel.CRITICAL })
    }

    @Test
    fun strongBootStateMismatchIsWarning() {
        val findings = AttestationIntegrityRiskEngine.evaluate(
            standard = healthyAttestation.copy(deviceLocked = true, verifiedBootState = VerifiedBootState.VERIFIED),
            strongBox = KeyAttestationResult(requestedStrongBox = true),
            system = IntegritySystemSignals(
                rootAvailable = true,
                flashLockedProperty = false,
                verifiedBootStateProperty = "orange",
                selinuxEnforcing = true,
            ),
        )

        assertEquals(
            IntegrityFindingLevel.WARN,
            findings.first { it.id == "crosscheck.bootloader.mismatch" }.level,
        )
        assertEquals(
            IntegrityFindingLevel.WARN,
            findings.first { it.id == "crosscheck.verified_boot.mismatch" }.level,
        )
    }

    @Test
    fun revokedOrChallengeMismatchIsCritical() {
        val findings = AttestationIntegrityRiskEngine.evaluate(
            standard = healthyAttestation.copy(challengeMatches = false, revoked = true),
            strongBox = KeyAttestationResult(requestedStrongBox = true),
            system = IntegritySystemSignals(selinuxEnforcing = true),
        )

        assertTrue(findings.any { it.id.endsWith("challenge_mismatch") && it.level == IntegrityFindingLevel.CRITICAL })
        assertTrue(findings.any { it.id.endsWith("revoked") && it.level == IntegrityFindingLevel.CRITICAL })
    }
}
