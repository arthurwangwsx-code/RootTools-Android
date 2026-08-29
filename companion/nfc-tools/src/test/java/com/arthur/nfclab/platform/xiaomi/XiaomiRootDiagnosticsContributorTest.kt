package com.arthur.nfclab.platform.xiaomi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiRootDiagnosticsContributorTest {
    @Test
    fun manufacturerDetection_matchesXiaomiFamilyOnly() {
        assertTrue(XiaomiRootDiagnosticsContributor.isXiaomiFamily("Xiaomi"))
        assertTrue(XiaomiRootDiagnosticsContributor.isXiaomiFamily("Redmi"))
        assertTrue(XiaomiRootDiagnosticsContributor.isXiaomiFamily("POCO"))
        assertFalse(XiaomiRootDiagnosticsContributor.isXiaomiFamily("samsung"))
        assertFalse(XiaomiRootDiagnosticsContributor.isXiaomiFamily("Google"))
    }
}
