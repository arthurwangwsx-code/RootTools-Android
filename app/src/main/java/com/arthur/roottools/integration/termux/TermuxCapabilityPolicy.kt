package com.arthur.roottools.integration.termux

import com.arthur.roottools.model.TermuxBridgeMode
import com.arthur.roottools.model.TermuxDistribution

data class TermuxCapabilityInput(
    val installed: Boolean,
    val versionName: String?,
    val installerPackageName: String?,
    val runCommandServiceAvailable: Boolean,
    val runCommandPermissionAvailable: Boolean,
    val runCommandPermissionGranted: Boolean,
)

data class TermuxCapabilityDecision(
    val distribution: TermuxDistribution,
    val bridgeMode: TermuxBridgeMode,
)

object TermuxCapabilityPolicy {
    fun decide(input: TermuxCapabilityInput): TermuxCapabilityDecision {
        if (!input.installed) {
            return TermuxCapabilityDecision(
                distribution = TermuxDistribution.UNKNOWN,
                bridgeMode = TermuxBridgeMode.UNAVAILABLE,
            )
        }

        val distribution = detectDistribution(input.versionName, input.installerPackageName)
        val bridgeMode = when {
            input.runCommandServiceAvailable &&
                input.runCommandPermissionAvailable &&
                input.runCommandPermissionGranted -> TermuxBridgeMode.OFFICIAL_RUN_COMMAND

            input.runCommandServiceAvailable && input.runCommandPermissionAvailable ->
                TermuxBridgeMode.OFFICIAL_RUN_COMMAND_PERMISSION_REQUIRED

            else -> TermuxBridgeMode.REVERSE_INTENT_ONLY
        }

        return TermuxCapabilityDecision(distribution, bridgeMode)
    }

    fun detectDistribution(versionName: String?, installerPackageName: String?): TermuxDistribution {
        val version = versionName.orEmpty().lowercase()
        val installer = installerPackageName.orEmpty().lowercase()
        return when {
            version.startsWith("googleplay.") || installer == GOOGLE_PLAY_INSTALLER -> TermuxDistribution.GOOGLE_PLAY
            installer == FDROID_INSTALLER || version.contains("fdroid") -> TermuxDistribution.FDROID
            version.contains("github") -> TermuxDistribution.GITHUB
            else -> TermuxDistribution.UNKNOWN
        }
    }

    private const val GOOGLE_PLAY_INSTALLER = "com.android.vending"
    private const val FDROID_INSTALLER = "org.fdroid.fdroid"
}

