package com.arthur.roottools.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowPreviewAccessPolicyTest {
    private val appUid = 10295

    @Test
    fun `shell root and app may read only latest`() {
        assertTrue(ShadowPreviewAccessPolicy.canRead(2000, appUid, "/latest", "r"))
        assertTrue(ShadowPreviewAccessPolicy.canRead(0, appUid, "/latest", "r"))
        assertTrue(ShadowPreviewAccessPolicy.canRead(appUid, appUid, "/latest", "r"))
        assertFalse(ShadowPreviewAccessPolicy.canRead(2000, appUid, "/other", "r"))
    }

    @Test
    fun `ordinary apps and write modes are rejected`() {
        assertFalse(ShadowPreviewAccessPolicy.canRead(10318, appUid, "/latest", "r"))
        assertFalse(ShadowPreviewAccessPolicy.canRead(2000, appUid, "/latest", "rw"))
        assertFalse(ShadowPreviewAccessPolicy.canRead(2000, appUid, null, "r"))
    }
}
