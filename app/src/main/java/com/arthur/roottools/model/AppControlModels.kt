package com.arthur.roottools.model

enum class AppInventoryFilter {
    ALL,
    USER,
    SYSTEM,
    RUNNING,
    FROZEN,
    DEBUGGABLE,
}

enum class AppInventorySort {
    LABEL,
    PACKAGE,
    LAST_UPDATE,
    TARGET_SDK,
}

enum class AppSourceStatus(val displayName: String) {
    SYSTEM("System image"),
    UPDATED_SYSTEM("Updated system"),
    GOOGLE_PLAY("Google Play"),
    GALAXY_STORE("Galaxy Store"),
    PACKAGE_INSTALLER("Package Installer"),
    SIDELOAD("ADB / Sideload"),
    OTHER_STORE("Other installer"),
    UNKNOWN("Unknown"),
}

data class AppInventoryItem(
    val packageName: String,
    val label: String,
    val versionName: String = "",
    val versionCode: Long = 0L,
    val uid: Int = -1,
    val targetSdk: Int = 0,
    val minSdk: Int = 0,
    val firstInstallTimeMs: Long = 0L,
    val lastUpdateTimeMs: Long = 0L,
    val systemApp: Boolean = false,
    val updatedSystemApp: Boolean = false,
    val debuggable: Boolean = false,
    val persistent: Boolean = false,
    val processName: String = "",
    val enabled: Boolean = true,
    val enabledState: String = "default",
    val stopped: Boolean = false,
    val running: Boolean = false,
    val hasSplits: Boolean = false,
    val splitCount: Int = 0,
    val installerPackage: String? = null,
)

data class AppInventorySnapshot(
    val apps: List<AppInventoryItem> = emptyList(),
    val loadedAtMs: Long = 0L,
    val runningProbeAvailable: Boolean = false,
) {
    val userApps: Int get() = apps.count { !it.systemApp }
    val systemApps: Int get() = apps.count { it.systemApp }
    val runningApps: Int get() = apps.count { it.running }
    val frozenApps: Int get() = apps.count { !it.enabled }
}

data class AppControlDetail(
    val packageName: String = "",
    val label: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val uid: Int = -1,
    val targetSdk: Int = 0,
    val minSdk: Int = 0,
    val compileSdk: Int? = null,
    val firstInstallTimeMs: Long = 0L,
    val lastUpdateTimeMs: Long = 0L,
    val installerPackage: String? = null,
    val sourceStatus: AppSourceStatus = AppSourceStatus.UNKNOWN,
    val systemApp: Boolean = false,
    val updatedSystemApp: Boolean = false,
    val debuggable: Boolean = false,
    val persistent: Boolean = false,
    val processName: String = "",
    val enabled: Boolean = true,
    val enabledState: String = "default",
    val stopped: Boolean = false,
    val largeHeap: Boolean = false,
    val allowBackup: Boolean = false,
    val usesCleartextTraffic: Boolean = false,
    val launchable: Boolean = false,
    val sourceDir: String = "",
    val splitSourceDirs: List<String> = emptyList(),
    val sourceReadable: Boolean = false,
    val baseApkBytes: Long = 0L,
    val splitApkBytes: Long = 0L,
    val dataDir: String = "",
    val deviceProtectedDataDir: String = "",
    val credentialProtectedDataDir: String = "",
    val nativeLibraryDir: String = "",
    val sharedLibraryFiles: List<String> = emptyList(),
    val signingSha256: List<String> = emptyList(),
    val activityCount: Int = 0,
    val serviceCount: Int = 0,
    val receiverCount: Int = 0,
    val providerCount: Int = 0,
    val requestedPermissionCount: Int = 0,
    val loadedAtMs: Long = 0L,
) {
    val totalApkBytes: Long get() = baseApkBytes + splitApkBytes
}
