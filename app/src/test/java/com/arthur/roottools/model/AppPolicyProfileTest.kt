package com.arthur.roottools.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppPolicyProfileTest {
    @Test
    fun builtInProfilesAreUniqueAndBounded() {
        assertEquals(AppPolicyProfileId.entries.size, BuiltInAppPolicyProfiles.all.map { it.id }.distinct().size)
        assertFalse(BuiltInAppPolicyProfiles.all.any { it.standbyBucket != null && it.standbyBucket !in setOf(5, 10, 20, 30, 40, 45) })
    }
}
