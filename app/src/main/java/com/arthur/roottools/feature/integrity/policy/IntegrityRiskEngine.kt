package com.arthur.roottools.feature.integrity.policy

import com.arthur.roottools.feature.integrity.model.AttestationRecord
import com.arthur.roottools.feature.integrity.model.AttestationSecurityLevel
import com.arthur.roottools.feature.integrity.model.AttestationTrustAnchor
import com.arthur.roottools.feature.integrity.model.EnvironmentPackageCategory
import com.arthur.roottools.feature.integrity.model.IntegrityBaseline
import com.arthur.roottools.feature.integrity.model.IntegrityCategory
import com.arthur.roottools.feature.integrity.model.IntegrityConfidence
import com.arthur.roottools.feature.integrity.model.IntegrityDisposition
import com.arthur.roottools.feature.integrity.model.IntegrityFinding
import com.arthur.roottools.feature.integrity.model.IntegrityFindingCode
import com.arthur.roottools.feature.integrity.model.IntegritySignals
import com.arthur.roottools.feature.integrity.model.IntegritySource

object IntegrityRiskEngine {
    fun evaluate(signals: IntegritySignals, baseline: IntegrityBaseline?): List<IntegrityFinding> = buildList {
        if (baseline == null) {
            add(finding(IntegrityFindingCode.NO_TRUSTED_BASELINE, IntegrityCategory.APP_INTEGRITY, IntegrityDisposition.INFO, IntegrityConfidence.HIGH, IntegritySource.BASELINE))
        }

        evaluateSelf(signals, baseline, this)
        evaluateBoot(signals, baseline, this)
        evaluateRootRuntime(signals, baseline, this)
        evaluateRuntime(signals, baseline, this)
        evaluateEnvironment(signals, baseline, this)
        evaluateDeviceSurface(signals, baseline, this)
        evaluateNative(signals, baseline, this)
        evaluateAttestation(signals, this)
    }.sortedWith(compareByDescending<IntegrityFinding> { it.disposition.priority }.thenBy { it.category.name }.thenBy { it.code.name })

    private fun evaluateSelf(signals: IntegritySignals, baseline: IntegrityBaseline?, out: MutableList<IntegrityFinding>) {
        val self = signals.self
        if (self.packageName != self.compiledPackageName || self.versionCode != self.compiledVersionCode || self.versionName != self.compiledVersionName) {
            out += finding(
                IntegrityFindingCode.SELF_COMPILED_IDENTITY_MISMATCH,
                IntegrityCategory.APP_INTEGRITY,
                IntegrityDisposition.CRITICAL,
                IntegrityConfidence.HIGH,
                IntegritySource.ANDROID_API,
                observed = "${self.packageName}@${self.versionCode}/${self.versionName}",
                expected = "${self.compiledPackageName}@${self.compiledVersionCode}/${self.compiledVersionName}",
            )
        }
        if (!self.packagePathConsistent) {
            out += finding(
                IntegrityFindingCode.SELF_PACKAGE_PATH_MISMATCH,
                IntegrityCategory.APP_INTEGRITY,
                IntegrityDisposition.WARN,
                IntegrityConfidence.HIGH,
                IntegritySource.ANDROID_API,
                IntegritySource.ROOT,
                observed = self.baseApkPath,
                expected = self.rootPackagePaths.firstOrNull(),
            )
        }
        if (self.debuggable) {
            out += finding(IntegrityFindingCode.DEBUGGABLE_BUILD, IntegrityCategory.APP_INTEGRITY, IntegrityDisposition.INFO, IntegrityConfidence.HIGH, IntegritySource.ANDROID_API)
        }
        if (baseline != null) {
            if (self.signingSha256.isNotBlank() && baseline.signingSha256.isNotBlank() && self.signingSha256 != baseline.signingSha256) {
                out += finding(
                    IntegrityFindingCode.SELF_SIGNING_BASELINE_MISMATCH,
                    IntegrityCategory.APP_INTEGRITY,
                    IntegrityDisposition.CRITICAL,
                    IntegrityConfidence.HIGH,
                    IntegritySource.ANDROID_API,
                    IntegritySource.BASELINE,
                    observed = self.signingSha256,
                    expected = baseline.signingSha256,
                )
            }
            if (self.versionCode == baseline.versionCode && self.apkSha256.isNotBlank() && baseline.apkSha256.isNotBlank() && self.apkSha256 != baseline.apkSha256) {
                out += finding(
                    IntegrityFindingCode.SELF_APK_BASELINE_MISMATCH,
                    IntegrityCategory.APP_INTEGRITY,
                    IntegrityDisposition.CRITICAL,
                    IntegrityConfidence.HIGH,
                    IntegritySource.ANDROID_API,
                    IntegritySource.BASELINE,
                    observed = self.apkSha256,
                    expected = baseline.apkSha256,
                )
            } else if (self.versionCode != baseline.versionCode) {
                out += finding(
                    IntegrityFindingCode.SELF_VERSION_CHANGED,
                    IntegrityCategory.APP_INTEGRITY,
                    IntegrityDisposition.INFO,
                    IntegrityConfidence.HIGH,
                    IntegritySource.BASELINE,
                    observed = self.versionCode.toString(),
                    expected = baseline.versionCode.toString(),
                )
            }
        }
        if (out.none { it.category == IntegrityCategory.APP_INTEGRITY && it.disposition.priority >= IntegrityDisposition.WARN.priority }) {
            out += finding(IntegrityFindingCode.SELF_IDENTITY_OK, IntegrityCategory.APP_INTEGRITY, IntegrityDisposition.PASS, IntegrityConfidence.HIGH, IntegritySource.ANDROID_API)
        }
    }

    private fun evaluateBoot(signals: IntegritySignals, baseline: IntegrityBaseline?, out: MutableList<IntegrityFinding>) {
        val boot = signals.boot
        val unlocked = boot.flashLocked == false || boot.vbmetaDeviceState.equals("unlocked", true)
        if (unlocked) {
            val expected = baseline?.let { it.flashLocked == false || it.vbmetaDeviceState.equals("unlocked", true) } == true
            out += finding(
                IntegrityFindingCode.BOOTLOADER_UNLOCKED,
                IntegrityCategory.BOOT_OS,
                if (expected) IntegrityDisposition.EXPECTED else IntegrityDisposition.INFO,
                IntegrityConfidence.HIGH,
                IntegritySource.ROOT,
                observed = "${boot.vbmetaDeviceState}/${boot.verifiedBootState}",
                expected = baseline?.let { "${it.vbmetaDeviceState}/${it.verifiedBootState}" },
            )
        }
        if (!boot.selinuxMode.equals("Enforcing", true) && boot.selinuxMode.isNotBlank()) {
            val expected = baseline?.selinuxMode?.equals(boot.selinuxMode, true) == true
            out += finding(
                IntegrityFindingCode.SELINUX_NOT_ENFORCING,
                IntegrityCategory.BOOT_OS,
                if (expected) IntegrityDisposition.EXPECTED else IntegrityDisposition.WARN,
                IntegrityConfidence.HIGH,
                IntegritySource.ROOT,
                observed = boot.selinuxMode,
                expected = baseline?.selinuxMode,
            )
        }
        if (baseline != null && boot.verifiedBootState != baseline.verifiedBootState) {
            out += finding(
                IntegrityFindingCode.VERIFIED_BOOT_STATE_CHANGED,
                IntegrityCategory.BOOT_OS,
                IntegrityDisposition.WARN,
                IntegrityConfidence.HIGH,
                IntegritySource.ROOT,
                IntegritySource.BASELINE,
                observed = boot.verifiedBootState,
                expected = baseline.verifiedBootState,
            )
        }
        if (baseline != null && boot.securityPatch.isNotBlank() && boot.securityPatch != baseline.securityPatch) {
            out += finding(
                IntegrityFindingCode.SECURITY_PATCH_CHANGED,
                IntegrityCategory.BOOT_OS,
                IntegrityDisposition.INFO,
                IntegrityConfidence.HIGH,
                IntegritySource.ANDROID_API,
                IntegritySource.BASELINE,
                observed = boot.securityPatch,
                expected = baseline.securityPatch,
            )
        }
        if (boot.frameworkModel.isNotBlank() && boot.rootModel.isNotBlank() && !boot.frameworkModel.equals(boot.rootModel, true)) {
            out += finding(
                IntegrityFindingCode.DEVICE_MODEL_CROSSCHECK_MISMATCH,
                IntegrityCategory.DEVICE_SURFACE,
                IntegrityDisposition.WARN,
                IntegrityConfidence.HIGH,
                IntegritySource.ANDROID_API,
                IntegritySource.ROOT,
                observed = boot.frameworkModel,
                expected = boot.rootModel,
            )
        }
    }

    private fun evaluateRootRuntime(signals: IntegritySignals, baseline: IntegrityBaseline?, out: MutableList<IntegrityFinding>) {
        val root = signals.rootRuntime
        if (root.rootAvailable) {
            out += finding(
                IntegrityFindingCode.ROOT_AVAILABLE,
                IntegrityCategory.ROOT_RUNTIME,
                if (baseline?.rootExpected == true) IntegrityDisposition.EXPECTED else IntegrityDisposition.INFO,
                IntegrityConfidence.HIGH,
                IntegritySource.ROOT,
                observed = root.provider.name,
                expected = baseline?.rootProvider?.name,
            )
        }
        if (baseline != null && root.provider != baseline.rootProvider) {
            out += finding(
                IntegrityFindingCode.ROOT_PROVIDER_CHANGED,
                IntegrityCategory.ROOT_RUNTIME,
                IntegrityDisposition.WARN,
                IntegrityConfidence.HIGH,
                IntegritySource.ROOT,
                IntegritySource.BASELINE,
                observed = root.provider.name,
                expected = baseline.rootProvider.name,
            )
        }
        if (baseline != null && root.modules != baseline.rootModules) {
            out += finding(
                IntegrityFindingCode.ROOT_MODULE_SET_CHANGED,
                IntegrityCategory.ROOT_RUNTIME,
                IntegrityDisposition.WARN,
                IntegrityConfidence.HIGH,
                IntegritySource.ROOT,
                IntegritySource.BASELINE,
                observed = root.modules.sorted().joinToString(","),
                expected = baseline.rootModules.sorted().joinToString(","),
            )
        }
        root.hookFrameworkPackages.forEach { packageName ->
            val expected = baseline?.hookFrameworkPackages?.contains(packageName) == true
            out += finding(
                IntegrityFindingCode.HOOK_FRAMEWORK_PRESENT,
                IntegrityCategory.ROOT_RUNTIME,
                if (expected) IntegrityDisposition.EXPECTED else IntegrityDisposition.INFO,
                IntegrityConfidence.HIGH,
                IntegritySource.ANDROID_API,
                observed = packageName,
            )
        }
    }

    private fun evaluateRuntime(
        signals: IntegritySignals,
        baseline: IntegrityBaseline?,
        out: MutableList<IntegrityFinding>,
    ) {
        val runtime = signals.runtime
        if (runtime.tracerPid > 0) {
            out += finding(
                IntegrityFindingCode.RUNTIME_TRACED,
                IntegrityCategory.RUNTIME,
                IntegrityDisposition.WARN,
                IntegrityConfidence.HIGH,
                IntegritySource.PROCFS,
                observed = runtime.tracerPid.toString(),
                expected = "0",
            )
        }
        runtime.strongMarkers.forEach { marker ->
            val critical = STRONG_CRITICAL_MARKERS.any { marker.contains(it, ignoreCase = true) }
            val expectedFrameworkMarker = !critical && baseline != null && markerExpectedByBaseline(marker, baseline)
            out += finding(
                IntegrityFindingCode.RUNTIME_STRONG_MARKER,
                IntegrityCategory.RUNTIME,
                when {
                    critical -> IntegrityDisposition.CRITICAL
                    expectedFrameworkMarker -> IntegrityDisposition.EXPECTED
                    else -> IntegrityDisposition.WARN
                },
                IntegrityConfidence.HIGH,
                IntegritySource.PROCFS,
                IntegritySource.BASELINE.takeIf { expectedFrameworkMarker } ?: IntegritySource.PROCFS,
                observed = marker,
            )
        }
        if (runtime.writableExecutableMappings.isNotEmpty()) {
            out += finding(
                IntegrityFindingCode.RUNTIME_WRITABLE_EXECUTABLE,
                IntegrityCategory.RUNTIME,
                IntegrityDisposition.WARN,
                IntegrityConfidence.MEDIUM,
                IntegritySource.PROCFS,
                observed = runtime.writableExecutableMappings.size.toString(),
                evidence = runtime.writableExecutableMappings.take(5),
            )
        }
        if (runtime.deletedExecutableMappings.isNotEmpty()) {
            out += finding(
                IntegrityFindingCode.RUNTIME_DELETED_EXECUTABLE,
                IntegrityCategory.RUNTIME,
                IntegrityDisposition.WARN,
                IntegrityConfidence.MEDIUM,
                IntegritySource.PROCFS,
                observed = runtime.deletedExecutableMappings.size.toString(),
                evidence = runtime.deletedExecutableMappings.take(5),
            )
        }
        if (!runtime.mapsCrossCheckConsistent) {
            out += finding(
                IntegrityFindingCode.RUNTIME_MAPS_CROSSCHECK_MISMATCH,
                IntegrityCategory.RUNTIME,
                IntegrityDisposition.WARN,
                IntegrityConfidence.MEDIUM,
                IntegritySource.PROCFS,
                IntegritySource.ROOT,
                observed = runtime.mappingCount.toString(),
                expected = runtime.rootMappingCount?.toString(),
            )
        }
    }

    private fun evaluateEnvironment(signals: IntegritySignals, baseline: IntegrityBaseline?, out: MutableList<IntegrityFinding>) {
        val env = signals.environment
        if (env.developerOptionsEnabled) {
            out += finding(IntegrityFindingCode.DEVELOPER_OPTIONS_ENABLED, IntegrityCategory.AUTOMATION, IntegrityDisposition.INFO, IntegrityConfidence.HIGH, IntegritySource.ANDROID_API)
        }
        if (env.adbEnabled) {
            out += finding(
                IntegrityFindingCode.ADB_ENABLED,
                IntegrityCategory.AUTOMATION,
                if (baseline?.adbExpected == true) IntegrityDisposition.EXPECTED else IntegrityDisposition.INFO,
                IntegrityConfidence.HIGH,
                IntegritySource.ANDROID_API,
            )
        }
        if (env.vpnActive) {
            out += finding(
                IntegrityFindingCode.VPN_ACTIVE,
                IntegrityCategory.NETWORK_LOCATION,
                if (baseline?.vpnExpected == true) IntegrityDisposition.EXPECTED else IntegrityDisposition.INFO,
                IntegrityConfidence.HIGH,
                IntegritySource.ANDROID_API,
                observed = env.vpnPackages.sorted().joinToString(",").ifBlank { "active" },
            )
        }
        if (!env.globalProxy.isNullOrBlank()) {
            out += finding(
                IntegrityFindingCode.GLOBAL_PROXY_CONFIGURED,
                IntegrityCategory.NETWORK_LOCATION,
                IntegrityDisposition.INFO,
                IntegrityConfidence.HIGH,
                IntegritySource.ANDROID_API,
                observed = env.globalProxy,
            )
        }
        env.packages.forEach { packageSignal ->
            val expected = baseline?.expectedEnvironmentPackages?.contains(packageSignal.packageName) == true
            val (code, category) = when (packageSignal.category) {
                EnvironmentPackageCategory.VIRTUALIZATION -> IntegrityFindingCode.VIRTUALIZATION_TOOL_PRESENT to IntegrityCategory.VIRTUALIZATION
                EnvironmentPackageCategory.DEVICE_SPOOFING -> IntegrityFindingCode.DEVICE_SPOOFING_TOOL_PRESENT to IntegrityCategory.VIRTUALIZATION
                EnvironmentPackageCategory.ROOT_HIDING -> IntegrityFindingCode.ROOT_HIDING_TOOL_PRESENT to IntegrityCategory.ROOT_RUNTIME
                EnvironmentPackageCategory.AUTOMATION, EnvironmentPackageCategory.REMOTE_CONTROL -> IntegrityFindingCode.AUTOMATION_TOOL_PRESENT to IntegrityCategory.AUTOMATION
                EnvironmentPackageCategory.ROOT_RUNTIME, EnvironmentPackageCategory.HOOK_FRAMEWORK -> return@forEach
            }
            out += finding(
                code,
                category,
                if (expected) IntegrityDisposition.EXPECTED else IntegrityDisposition.INFO,
                IntegrityConfidence.HIGH,
                IntegritySource.ANDROID_API,
                observed = packageSignal.packageName,
            )
        }
    }

    private fun evaluateDeviceSurface(signals: IntegritySignals, baseline: IntegrityBaseline?, out: MutableList<IntegrityFinding>) {
        val surface = signals.deviceSurface ?: return
        if (baseline != null) {
            val changed = surface.hashes().mapNotNull { (key, observed) ->
                baseline.deviceSurfaceHashes[key]?.takeIf { it != observed }?.let { key }
            }
            if (changed.isNotEmpty()) {
                out += finding(
                    IntegrityFindingCode.DEVICE_SURFACE_DRIFT,
                    IntegrityCategory.DEVICE_SURFACE,
                    IntegrityDisposition.WARN,
                    IntegrityConfidence.MEDIUM,
                    IntegritySource.SYSFS,
                    IntegritySource.ANDROID_API,
                    IntegritySource.BASELINE,
                    observed = changed.joinToString(","),
                )
            }
        }
        signals.sandbox?.let { sandbox ->
            if (sandbox.available && !sandbox.coherent) {
                out += finding(
                    IntegrityFindingCode.SANDBOX_INCOHERENT,
                    IntegrityCategory.VIRTUALIZATION,
                    IntegrityDisposition.WARN,
                    IntegrityConfidence.HIGH,
                    IntegritySource.ANDROID_API,
                    IntegritySource.ROOT,
                    evidence = sandbox.evidence,
                )
            }
        }
    }

    private fun evaluateNative(
        signals: IntegritySignals,
        baseline: IntegrityBaseline?,
        out: MutableList<IntegrityFinding>,
    ) {
        val native = signals.native ?: return
        if (!native.available) {
            out += finding(
                IntegrityFindingCode.NATIVE_PROBE_UNAVAILABLE,
                IntegrityCategory.RUNTIME,
                IntegrityDisposition.UNAVAILABLE,
                IntegrityConfidence.HIGH,
                IntegritySource.NATIVE,
                observed = native.error,
            )
            return
        }
        if (native.selfExecutableSegmentMismatches > 0) {
            out += finding(
                IntegrityFindingCode.NATIVE_RUNTIME_FILE_MISMATCH,
                IntegrityCategory.RUNTIME,
                IntegrityDisposition.CRITICAL,
                IntegrityConfidence.HIGH,
                IntegritySource.NATIVE,
                observed = native.selfExecutableSegmentMismatches.toString(),
                expected = "0",
            )
        }
        native.strongMarkers.forEach { marker ->
            val critical = STRONG_CRITICAL_MARKERS.any { marker.contains(it, true) }
            val expectedFrameworkMarker = !critical && baseline != null && markerExpectedByBaseline(marker, baseline)
            out += finding(
                IntegrityFindingCode.NATIVE_STRONG_MARKER,
                IntegrityCategory.RUNTIME,
                when {
                    critical -> IntegrityDisposition.CRITICAL
                    expectedFrameworkMarker -> IntegrityDisposition.EXPECTED
                    else -> IntegrityDisposition.WARN
                },
                IntegrityConfidence.HIGH,
                IntegritySource.NATIVE,
                IntegritySource.BASELINE.takeIf { expectedFrameworkMarker } ?: IntegritySource.NATIVE,
                observed = marker,
            )
        }
    }

    private fun evaluateAttestation(signals: IntegritySignals, out: MutableList<IntegrityFinding>) {
        val attestation = signals.attestation ?: return
        attestation.standard?.let { evaluateAttestationRecord(it, false, signals, out) }
        attestation.strongBox?.let { evaluateAttestationRecord(it, true, signals, out) }
    }

    private fun evaluateAttestationRecord(
        record: AttestationRecord,
        strongBox: Boolean,
        signals: IntegritySignals,
        out: MutableList<IntegrityFinding>,
    ) {
        if (!record.available) {
            out += finding(
                if (strongBox) IntegrityFindingCode.STRONGBOX_UNAVAILABLE else IntegrityFindingCode.ATTESTATION_UNAVAILABLE,
                IntegrityCategory.ATTESTATION,
                IntegrityDisposition.UNAVAILABLE,
                IntegrityConfidence.HIGH,
                IntegritySource.ATTESTATION,
                observed = record.error,
            )
            return
        }
        if (!record.challengeMatched) {
            out += finding(IntegrityFindingCode.ATTESTATION_CHALLENGE_MISMATCH, IntegrityCategory.ATTESTATION, IntegrityDisposition.CRITICAL, IntegrityConfidence.HIGH, IntegritySource.ATTESTATION)
        }
        if (!record.chainSignatureValid || !record.chainValidityValid) {
            out += finding(
                IntegrityFindingCode.ATTESTATION_CHAIN_INVALID,
                IntegrityCategory.ATTESTATION,
                IntegrityDisposition.CRITICAL,
                IntegrityConfidence.HIGH,
                IntegritySource.ATTESTATION,
                observed = "signature=${record.chainSignatureValid},validity=${record.chainValidityValid}",
            )
        }
        if (record.revokedSerials.isNotEmpty()) {
            out += finding(
                IntegrityFindingCode.ATTESTATION_CERT_REVOKED,
                IntegrityCategory.ATTESTATION,
                IntegrityDisposition.CRITICAL,
                IntegrityConfidence.HIGH,
                IntegritySource.ATTESTATION,
                IntegritySource.ONLINE_GOOGLE,
                observed = record.revokedSerials.sorted().joinToString(","),
            )
        }
        if (record.trustAnchor == AttestationTrustAnchor.UNKNOWN) {
            out += finding(
                IntegrityFindingCode.ATTESTATION_ROOT_UNTRUSTED,
                IntegrityCategory.ATTESTATION,
                IntegrityDisposition.WARN,
                IntegrityConfidence.HIGH,
                IntegritySource.ATTESTATION,
                observed = record.rootSubject,
            )
        }
        if (record.securityLevel == AttestationSecurityLevel.SOFTWARE) {
            out += finding(
                IntegrityFindingCode.ATTESTATION_SOFTWARE_ONLY,
                IntegrityCategory.ATTESTATION,
                IntegrityDisposition.WARN,
                IntegrityConfidence.HIGH,
                IntegritySource.ATTESTATION,
            )
        }
        if (!record.onlineVerificationAvailable) {
            out += finding(
                IntegrityFindingCode.ONLINE_ATTESTATION_STATUS_UNAVAILABLE,
                IntegrityCategory.ATTESTATION,
                IntegrityDisposition.UNAVAILABLE,
                IntegrityConfidence.HIGH,
                IntegritySource.ONLINE_GOOGLE,
            )
        }
        if (record.rkpProvisioningPresent) {
            out += finding(
                IntegrityFindingCode.RKP_PROVISIONING_PRESENT,
                IntegrityCategory.ATTESTATION,
                IntegrityDisposition.INFO,
                IntegrityConfidence.HIGH,
                IntegritySource.ATTESTATION,
                observed = record.rkpValidatedEntity ?: record.rkpCertificatesIssued?.toString(),
            )
        }
        val rootUnlocked = signals.boot.flashLocked == false || signals.boot.vbmetaDeviceState.equals("unlocked", true)
        if (record.deviceLocked != null && record.deviceLocked == rootUnlocked) {
            out += finding(
                IntegrityFindingCode.ATTESTATION_BOOT_CROSSCHECK_MISMATCH,
                IntegrityCategory.ATTESTATION,
                IntegrityDisposition.WARN,
                IntegrityConfidence.HIGH,
                IntegritySource.ATTESTATION,
                IntegritySource.ROOT,
                observed = "attestedLocked=${record.deviceLocked}",
                expected = "rootUnlocked=$rootUnlocked",
            )
        }
        val hasCriticalForRecord = out.any {
            it.category == IntegrityCategory.ATTESTATION && it.disposition == IntegrityDisposition.CRITICAL
        }
        if (!hasCriticalForRecord) {
            out += finding(
                if (strongBox) IntegrityFindingCode.STRONGBOX_READY else IntegrityFindingCode.ATTESTATION_READY,
                IntegrityCategory.ATTESTATION,
                IntegrityDisposition.PASS,
                IntegrityConfidence.HIGH,
                IntegritySource.ATTESTATION,
                observed = record.securityLevel.name,
            )
        }
    }

    private fun finding(
        code: IntegrityFindingCode,
        category: IntegrityCategory,
        disposition: IntegrityDisposition,
        confidence: IntegrityConfidence,
        vararg sources: IntegritySource,
        observed: String? = null,
        expected: String? = null,
        evidence: List<String> = emptyList(),
    ) = IntegrityFinding(
        code = code,
        category = category,
        disposition = disposition,
        confidence = confidence,
        sources = sources.toSet(),
        observed = observed,
        expected = expected,
        evidence = evidence,
    )

    private fun markerExpectedByBaseline(marker: String, baseline: IntegrityBaseline): Boolean {
        val normalized = marker.lowercase()
        return when {
            "lsposed" in normalized || "xposed" in normalized || "edxposed" in normalized ->
                baseline.hookFrameworkPackages.isNotEmpty() || baseline.rootModules.any { "lsposed" in it.lowercase() || "xposed" in it.lowercase() }
            "zygisk" in normalized || "riru" in normalized ->
                baseline.rootProvider.name == "MAGISK" || baseline.rootModules.any { "zygisk" in it.lowercase() || "riru" in it.lowercase() }
            else -> false
        }
    }

    private val STRONG_CRITICAL_MARKERS = setOf("frida", "gadget", "substrate")
}
