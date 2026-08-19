package com.arthur.roottools.model

enum class PrivilegeBackendType(val displayName: String) {
    NONE("Off"),
    SHIZUKU_ADB("Shizuku ADB"),
    SHIZUKU_ROOT("Shizuku Root"),
    SUI_ROOT("Sui Root"),
}

enum class PrivilegeCapability {
    ROOT_LINUX,
    ROOT_FS,
    SYSFS_WRITE,
    PACKAGE_CONTROL,
    COMPONENT_CONTROL,
    ACTIVITY_CONTROL,
    APP_OPS,
    FRAMEWORK_DIAGNOSTICS,
    MAGISK_CONTROL,
    ADBD_CONTROL,
}

data class ShizukuBridgeState(
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val permissionDeniedPermanently: Boolean = false,
    val backend: PrivilegeBackendType = PrivilegeBackendType.NONE,
    val uid: Int? = null,
    val serverVersion: Int? = null,
    val serverPatchVersion: Int? = null,
    val selinuxContext: String? = null,
    val managerInstalled: Boolean = false,
    val suiAvailable: Boolean = false,
    val lastBinderDeathAt: Long? = null,
    val error: String? = null,
) {
    val ready: Boolean get() = binderAlive && permissionGranted
}

data class CapabilityProbeResult(
    val capability: PrivilegeCapability,
    val available: Boolean,
    val backend: PrivilegeBackendType,
    val detail: String,
    val latencyMs: Double? = null,
)
