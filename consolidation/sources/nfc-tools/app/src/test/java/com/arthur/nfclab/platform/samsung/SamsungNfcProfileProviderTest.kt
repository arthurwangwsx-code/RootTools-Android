package com.arthur.nfclab.platform.samsung

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungNfcProfileProviderTest {
    @Test
    fun manufacturerDetection_isCaseInsensitiveAndStrict() {
        assertTrue(SamsungNfcProfileProvider.isSamsungManufacturer("samsung"))
        assertTrue(SamsungNfcProfileProvider.isSamsungManufacturer("SAMSUNG"))
        assertFalse(SamsungNfcProfileProvider.isSamsungManufacturer("Xiaomi"))
        assertFalse(SamsungNfcProfileProvider.isSamsungManufacturer("samsung-like"))
    }
}
