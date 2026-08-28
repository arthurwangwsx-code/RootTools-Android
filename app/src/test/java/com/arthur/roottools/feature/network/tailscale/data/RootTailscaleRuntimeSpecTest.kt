package com.arthur.roottools.feature.network.tailscale.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootTailscaleRuntimeSpecTest {
    @Test
    fun verifiedRuntimeMetadataIsPinned() {
        assertEquals("1.102.3", RootTailscaleRuntimeSpec.VERSION)
        assertEquals(64, RootTailscaleRuntimeSpec.SHA256.length)
        assertTrue(RootTailscaleRuntimeSpec.DOWNLOAD_URL.startsWith("https://pkgs.tailscale.com/stable/"))
        assertTrue(RootTailscaleRuntimeSpec.DOWNLOAD_URL.contains(RootTailscaleRuntimeSpec.VERSION))
    }
}

