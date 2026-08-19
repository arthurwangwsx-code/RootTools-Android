package com.arthur.roottools.model

enum class TermuxDistribution(val displayName: String) {
    GOOGLE_PLAY("Google Play"),
    FDROID("F-Droid"),
    GITHUB("GitHub"),
    UNKNOWN("Unknown"),
}

enum class TermuxBridgeMode(val displayName: String) {
    OFFICIAL_RUN_COMMAND("Official RUN_COMMAND"),
    OFFICIAL_RUN_COMMAND_PERMISSION_REQUIRED("RUN_COMMAND permission required"),
    REVERSE_INTENT_ONLY("Termux → RootTools"),
    LOCAL_SSH("Local SSH"),
    UNAVAILABLE("Unavailable"),
}

enum class RuntimeToolState {
    INSTALLED,
    NOT_INSTALLED,
    UNKNOWN,
}

data class TermuxRuntimeSnapshot(
    val installed: Boolean = false,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val installerPackageName: String? = null,
    val distribution: TermuxDistribution = TermuxDistribution.UNKNOWN,
    val runCommandServiceAvailable: Boolean = false,
    val runCommandPermissionAvailable: Boolean = false,
    val runCommandPermissionGranted: Boolean = false,
    val bridgeMode: TermuxBridgeMode = TermuxBridgeMode.UNAVAILABLE,
    val sshd: RuntimeToolState = RuntimeToolState.UNKNOWN,
    val git: RuntimeToolState = RuntimeToolState.UNKNOWN,
    val python: RuntimeToolState = RuntimeToolState.UNKNOWN,
    val node: RuntimeToolState = RuntimeToolState.UNKNOWN,
    val checkedAtEpochMs: Long = 0L,
)

