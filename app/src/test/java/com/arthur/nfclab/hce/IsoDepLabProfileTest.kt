package com.arthur.nfclab.hce

import org.junit.Assert.assertEquals
import org.junit.Test

class IsoDepLabProfileTest {
    @Test
    fun defaultProfileUsesSyntheticProprietaryAidAndTestKey() {
        val profile = IsoDepLabProfiles.default
        assertEquals("F0", profile.aid.take(2))
        assertEquals(16, profile.testKey.size)
    }
}
