package com.arthur.roottools.feature.integrity.model

enum class IntegrityScanMode {
    FAST,
    DEEP,
    NATIVE,
    ATTESTATION,
}

enum class IntegrityCategory {
    APP_INTEGRITY,
    RUNTIME,
    BOOT_OS,
    ROOT_RUNTIME,
    VIRTUALIZATION,
    DEVICE_SURFACE,
    AUTOMATION,
    NETWORK_LOCATION,
    ATTESTATION,
}

enum class IntegrityDisposition(val priority: Int) {
    PASS(0),
    UNAVAILABLE(1),
    INFO(2),
    EXPECTED(3),
    WARN(4),
    CRITICAL(5),
}

enum class IntegrityConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

enum class IntegritySource {
    ANDROID_API,
    ROOT,
    PROCFS,
    SYSFS,
    NATIVE,
    ATTESTATION,
    ONLINE_GOOGLE,
    BASELINE,
}

enum class IntegrityFindingCode {
    NO_TRUSTED_BASELINE,
    SELF_IDENTITY_OK,
    SELF_COMPILED_IDENTITY_MISMATCH,
    SELF_SIGNING_BASELINE_MISMATCH,
    SELF_APK_BASELINE_MISMATCH,
    SELF_VERSION_CHANGED,
    SELF_PACKAGE_PATH_MISMATCH,
    DEBUGGABLE_BUILD,
    BOOTLOADER_UNLOCKED,
    VERIFIED_BOOT_STATE_CHANGED,
    SELINUX_NOT_ENFORCING,
    SECURITY_PATCH_CHANGED,
    ROOT_AVAILABLE,
    ROOT_PROVIDER_CHANGED,
    ROOT_MODULE_SET_CHANGED,
    HOOK_FRAMEWORK_PRESENT,
    RUNTIME_TRACED,
    RUNTIME_STRONG_MARKER,
    RUNTIME_WRITABLE_EXECUTABLE,
    RUNTIME_DELETED_EXECUTABLE,
    RUNTIME_MAPS_CROSSCHECK_MISMATCH,
    DEVELOPER_OPTIONS_ENABLED,
    ADB_ENABLED,
    VPN_ACTIVE,
    GLOBAL_PROXY_CONFIGURED,
    AUTOMATION_TOOL_PRESENT,
    VIRTUALIZATION_TOOL_PRESENT,
    DEVICE_SPOOFING_TOOL_PRESENT,
    ROOT_HIDING_TOOL_PRESENT,
    DEVICE_MODEL_CROSSCHECK_MISMATCH,
    DEVICE_SURFACE_DRIFT,
    SANDBOX_INCOHERENT,
    NATIVE_PROBE_UNAVAILABLE,
    NATIVE_RUNTIME_FILE_MISMATCH,
    NATIVE_STRONG_MARKER,
    ATTESTATION_UNAVAILABLE,
    ATTESTATION_CHALLENGE_MISMATCH,
    ATTESTATION_CHAIN_INVALID,
    ATTESTATION_CERT_REVOKED,
    ATTESTATION_ROOT_UNTRUSTED,
    ATTESTATION_SOFTWARE_ONLY,
    ATTESTATION_READY,
    STRONGBOX_UNAVAILABLE,
    STRONGBOX_READY,
    ATTESTATION_BOOT_CROSSCHECK_MISMATCH,
    ONLINE_ATTESTATION_STATUS_UNAVAILABLE,
    RKP_PROVISIONING_PRESENT,
}

enum class RootRuntimeProvider {
    NONE,
    MAGISK,
    KERNEL_SU,
    APATCH,
    UNKNOWN,
}

enum class EnvironmentPackageCategory {
    ROOT_RUNTIME,
    HOOK_FRAMEWORK,
    VIRTUALIZATION,
    AUTOMATION,
    REMOTE_CONTROL,
    DEVICE_SPOOFING,
    ROOT_HIDING,
}

enum class AttestationSecurityLevel {
    SOFTWARE,
    TRUSTED_ENVIRONMENT,
    STRONGBOX,
    UNKNOWN,
}

enum class AttestationTrustAnchor {
    GOOGLE,
    GOOGLE_RKP,
    OEM,
    AOSP_SOFTWARE,
    UNKNOWN,
}

data class EnvironmentPackageSignal(
    val packageName: String,
    val category: EnvironmentPackageCategory,
)

data class SelfIdentitySignals(
    val packageName: String = "",
    val compiledPackageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val compiledVersionName: String = "",
    val compiledVersionCode: Long = 0,
    val signingSha256: String = "",
    val apkSha256: String = "",
    val installerPackage: String? = null,
    val baseApkPath: String = "",
    val rootPackagePaths: List<String> = emptyList(),
    val packagePathConsistent: Boolean = true,
    val debuggable: Boolean = false,
    val uid: Int = -1,
    val selinuxContext: String = "",
)

data class BootOsSignals(
    val frameworkModel: String = "",
    val rootModel: String = "",
    val buildFingerprint: String = "",
    val productFingerprint: String = "",
    val vendorFingerprint: String = "",
    val systemFingerprint: String = "",
    val securityPatch: String = "",
    val verifiedBootState: String = "",
    val vbmetaDeviceState: String = "",
    val flashLocked: Boolean? = null,
    val buildType: String = "",
    val buildTags: String = "",
    val roDebuggable: Boolean? = null,
    val selinuxMode: String = "",
)

data class RootRuntimeSignals(
    val rootAvailable: Boolean = false,
    val provider: RootRuntimeProvider = RootRuntimeProvider.NONE,
    val suVersion: String = "",
    val zygiskEnabled: Boolean? = null,
    val modules: Set<String> = emptySet(),
    val hookFrameworkPackages: Set<String> = emptySet(),
    val vectorActive: Boolean = false,
)

data class RuntimeIntegritySignals(
    val tracerPid: Int = 0,
    val mappingCount: Int = 0,
    val rootMappingCount: Int? = null,
    val executableAnonymousCount: Int = 0,
    val writableExecutableMappings: List<String> = emptyList(),
    val deletedExecutableMappings: List<String> = emptyList(),
    val strongMarkers: Set<String> = emptySet(),
    val mapsCrossCheckConsistent: Boolean = true,
)

data class EnvironmentContextSignals(
    val developerOptionsEnabled: Boolean = false,
    val adbEnabled: Boolean = false,
    val wirelessAdbEnabled: Boolean = false,
    val vpnActive: Boolean = false,
    val vpnPackages: Set<String> = emptySet(),
    val globalProxy: String? = null,
    val mockLocationPackages: Set<String> = emptySet(),
    val accessibilityPackages: Set<String> = emptySet(),
    val notificationListenerPackages: Set<String> = emptySet(),
    val packages: List<EnvironmentPackageSignal> = emptyList(),
)

data class DeviceSurfaceSignals(
    val cpuTopologyHash: String? = null,
    val thermalZoneHash: String? = null,
    val gpuRendererHash: String? = null,
    val vulkanCapabilityHash: String? = null,
    val sensorShapeHash: String? = null,
    val audioCapabilityHash: String? = null,
    val batterySupplyHash: String? = null,
    val sensorCount: Int = 0,
    val audioDeviceCount: Int = 0,
    val thermalZoneCount: Int = 0,
    val gpuSummary: String = "",
) {
    fun hashes(): Map<String, String> = buildMap {
        cpuTopologyHash?.let { put("cpu", it) }
        thermalZoneHash?.let { put("thermal", it) }
        gpuRendererHash?.let { put("gpu", it) }
        vulkanCapabilityHash?.let { put("vulkan", it) }
        sensorShapeHash?.let { put("sensor", it) }
        audioCapabilityHash?.let { put("audio", it) }
        batterySupplyHash?.let { put("battery", it) }
    }
}

data class SandboxSignals(
    val available: Boolean = false,
    val coherent: Boolean = true,
    val dataDir: String = "",
    val deviceProtectedDir: String = "",
    val canonicalDataDir: String = "",
    val appDataStat: String? = null,
    val rootDataStat: String? = null,
    val sentinelVerified: Boolean? = null,
    val evidence: List<String> = emptyList(),
)

data class NativeIntegritySignals(
    val available: Boolean = false,
    val tracerPid: Int? = null,
    val mappingCount: Int = 0,
    val writableExecutableCount: Int = 0,
    val deletedExecutableCount: Int = 0,
    val strongMarkers: Set<String> = emptySet(),
    val loadedElfCount: Int = 0,
    val selfExecutableSegments: Int = 0,
    val selfExecutableSegmentMismatches: Int = 0,
    val selfLibraryPath: String? = null,
    val error: String? = null,
)

data class AttestationRecord(
    val requestedStrongBox: Boolean = false,
    val available: Boolean = false,
    val securityLevel: AttestationSecurityLevel = AttestationSecurityLevel.UNKNOWN,
    val keyMintSecurityLevel: AttestationSecurityLevel = AttestationSecurityLevel.UNKNOWN,
    val attestationVersion: Int? = null,
    val keyMintVersion: Int? = null,
    val challengeMatched: Boolean = false,
    val chainSignatureValid: Boolean = false,
    val chainValidityValid: Boolean = false,
    val chainLength: Int = 0,
    val trustAnchor: AttestationTrustAnchor = AttestationTrustAnchor.UNKNOWN,
    val rootSubject: String = "",
    val rootSpkiSha256: String = "",
    val revokedSerials: Set<String> = emptySet(),
    val revocationCheckedOnline: Boolean = false,
    val onlineVerificationAvailable: Boolean = false,
    val rkpProvisioningPresent: Boolean = false,
    val rkpCertificatesIssued: Long? = null,
    val rkpValidatedEntity: String? = null,
    val deviceLocked: Boolean? = null,
    val verifiedBootState: String? = null,
    val verifiedBootKeySha256: String? = null,
    val verifiedBootHashSha256: String? = null,
    val osVersion: Long? = null,
    val osPatchLevel: Long? = null,
    val vendorPatchLevel: Long? = null,
    val bootPatchLevel: Long? = null,
    val certificateChainPem: String = "",
    val error: String? = null,
)

data class AttestationSignals(
    val standard: AttestationRecord? = null,
    val strongBox: AttestationRecord? = null,
)

data class IntegritySignals(
    val self: SelfIdentitySignals = SelfIdentitySignals(),
    val boot: BootOsSignals = BootOsSignals(),
    val rootRuntime: RootRuntimeSignals = RootRuntimeSignals(),
    val runtime: RuntimeIntegritySignals = RuntimeIntegritySignals(),
    val environment: EnvironmentContextSignals = EnvironmentContextSignals(),
    val deviceSurface: DeviceSurfaceSignals? = null,
    val sandbox: SandboxSignals? = null,
    val native: NativeIntegritySignals? = null,
    val attestation: AttestationSignals? = null,
)

data class IntegrityFinding(
    val code: IntegrityFindingCode,
    val category: IntegrityCategory,
    val disposition: IntegrityDisposition,
    val confidence: IntegrityConfidence,
    val sources: Set<IntegritySource>,
    val observed: String? = null,
    val expected: String? = null,
    val evidence: List<String> = emptyList(),
)

data class IntegrityBaseline(
    val schemaVersion: Int = 1,
    val profileName: String,
    val trustedAtEpochMs: Long,
    val packageName: String,
    val versionCode: Long,
    val signingSha256: String,
    val apkSha256: String,
    val buildFingerprint: String,
    val securityPatch: String,
    val verifiedBootState: String,
    val vbmetaDeviceState: String,
    val flashLocked: Boolean?,
    val selinuxMode: String,
    val rootExpected: Boolean,
    val rootProvider: RootRuntimeProvider,
    val rootModules: Set<String>,
    val hookFrameworkPackages: Set<String>,
    val expectedEnvironmentPackages: Set<String>,
    val adbExpected: Boolean,
    val vpnExpected: Boolean,
    val deviceSurfaceHashes: Map<String, String>,
)

data class IntegritySnapshot(
    val scannedAtEpochMs: Long = 0,
    val mode: IntegrityScanMode = IntegrityScanMode.FAST,
    val durationMs: Long = 0,
    val signals: IntegritySignals = IntegritySignals(),
    val findings: List<IntegrityFinding> = emptyList(),
    val unavailableProbes: Set<String> = emptySet(),
    val baselineProfileName: String? = null,
) {
    val primaryDisposition: IntegrityDisposition
        get() = findings.maxByOrNull { it.disposition.priority }?.disposition ?: IntegrityDisposition.PASS

    val criticalCount: Int get() = findings.count { it.disposition == IntegrityDisposition.CRITICAL }
    val warningCount: Int get() = findings.count { it.disposition == IntegrityDisposition.WARN }
    val expectedCount: Int get() = findings.count { it.disposition == IntegrityDisposition.EXPECTED }
    val baselineDriftCount: Int
        get() = findings.count { IntegritySource.BASELINE in it.sources && it.disposition.priority >= IntegrityDisposition.WARN.priority }
}

data class IntegrityProbeExecution(
    val id: String,
    val mode: IntegrityScanMode,
    val durationMs: Long,
    val available: Boolean,
    val evidence: List<String> = emptyList(),
    val error: String? = null,
)

enum class IntegrityReportFormat {
    TEXT,
    JSON,
    PEM,
}
