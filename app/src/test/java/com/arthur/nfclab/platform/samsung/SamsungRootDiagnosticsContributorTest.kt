package com.arthur.nfclab.platform.samsung

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungRootDiagnosticsContributorTest {
    @Test
    fun manufacturerDetection_isSamsungOnly() {
        assertTrue(SamsungRootDiagnosticsContributor.isSamsungManufacturer("samsung"))
        assertTrue(SamsungRootDiagnosticsContributor.isSamsungManufacturer("SAMSUNG"))
        assertFalse(SamsungRootDiagnosticsContributor.isSamsungManufacturer("Xiaomi"))
        assertFalse(SamsungRootDiagnosticsContributor.isSamsungManufacturer("Google"))
    }
}
