package com.arthur.roottools.integration.termux

import com.arthur.roottools.model.TermuxBridgeMode
import com.arthur.roottools.model.TermuxDistribution
import org.junit.Assert.assertEquals
import org.junit.Test

class TermuxCapabilityPolicyTest {
    @Test
    fun `missing package is unavailable`() {
        val decision = TermuxCapabilityPolicy.decide(
            TermuxCapabilityInput(
                installed = false,
                versionName = null,
                installerPackageName = null,
                runCommandServiceAvailable = false,
                runCommandPermissionAvailable = false,
                runCommandPermissionGranted = false,
            )
        )

        assertEquals(TermuxDistribution.UNKNOWN, decision.distribution)
        assertEquals(TermuxBridgeMode.UNAVAILABLE, decision.bridgeMode)
    }

    @Test
    fun `google play build without run command uses reverse bridge`() {
        val decision = TermuxCapabilityPolicy.decide(
            TermuxCapabilityInput(
                installed = true,
                versionName = "googleplay.2026.06.21",
                installerPackageName = "com.android.vending",
                runCommandServiceAvailable = false,
                runCommandPermissionAvailable = false,
                runCommandPermissionGranted = false,
            )
        )

        assertEquals(TermuxDistribution.GOOGLE_PLAY, decision.distribution)
        assertEquals(TermuxBridgeMode.REVERSE_INTENT_ONLY, decision.bridgeMode)
    }

    @Test
    fun `stable build with service but no grant requests permission`() {
        val decision = TermuxCapabilityPolicy.decide(
            TermuxCapabilityInput(
                installed = true,
                versionName = "0.118.3",
                installerPackageName = "org.fdroid.fdroid",
                runCommandServiceAvailable = true,
                runCommandPermissionAvailable = true,
                runCommandPermissionGranted = false,
            )
        )

        assertEquals(TermuxDistribution.FDROID, decision.distribution)
        assertEquals(TermuxBridgeMode.OFFICIAL_RUN_COMMAND_PERMISSION_REQUIRED, decision.bridgeMode)
    }

    @Test
    fun `stable build with permission uses official bridge`() {
        val decision = TermuxCapabilityPolicy.decide(
            TermuxCapabilityInput(
                installed = true,
                versionName = "0.118.3",
                installerPackageName = "org.fdroid.fdroid",
                runCommandServiceAvailable = true,
                runCommandPermissionAvailable = true,
                runCommandPermissionGranted = true,
            )
        )

        assertEquals(TermuxBridgeMode.OFFICIAL_RUN_COMMAND, decision.bridgeMode)
    }

    @Test
    fun `unknown sideload with service remains unknown distribution`() {
        val decision = TermuxCapabilityPolicy.decide(
            TermuxCapabilityInput(
                installed = true,
                versionName = "0.118.3",
                installerPackageName = null,
                runCommandServiceAvailable = true,
                runCommandPermissionAvailable = true,
                runCommandPermissionGranted = true,
            )
        )

        assertEquals(TermuxDistribution.UNKNOWN, decision.distribution)
        assertEquals(TermuxBridgeMode.OFFICIAL_RUN_COMMAND, decision.bridgeMode)
    }
}

