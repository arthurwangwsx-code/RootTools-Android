package com.arthur.roottools.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildCompatibilityPolicyTest {
    @Test
    fun firstSeenBuild_initializesWithoutTreatingInstallAsUpgrade() {
        assertEquals(
            BuildCompatibilityPolicy.Result.INITIALIZE,
            BuildCompatibilityPolicy.evaluate("", "xiaomi/houji/os3"),
        )
    }

    @Test
    fun sameBuild_keepsExistingOwnershipState() {
        assertEquals(
            BuildCompatibilityPolicy.Result.COMPATIBLE,
            BuildCompatibilityPolicy.evaluate("xiaomi/houji/os3", "xiaomi/houji/os3"),
        )
    }

    @Test
    fun changedBuild_requiresFreshOwnershipProbe() {
        assertEquals(
            BuildCompatibilityPolicy.Result.SYSTEM_BUILD_CHANGED,
            BuildCompatibilityPolicy.evaluate("xiaomi/houji/os2", "xiaomi/houji/os3"),
        )
    }
}
