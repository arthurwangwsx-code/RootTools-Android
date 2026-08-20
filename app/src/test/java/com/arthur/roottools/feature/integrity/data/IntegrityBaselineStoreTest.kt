package com.arthur.roottools.feature.integrity.data

import com.arthur.roottools.feature.integrity.model.IntegrityBaseline
import com.arthur.roottools.feature.integrity.model.RootRuntimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityBaselineStoreTest {
    @Test
    fun codecRoundTripsAllSafetyRelevantFields() {
        val baseline = IntegrityBaseline(
            profileName = "Samsung daily rooted",
            trustedAtEpochMs = 42L,
            packageName = "com.arthur.roottools",
            versionCode = 3,
            signingSha256 = "sign",
            apkSha256 = "apk",
            buildFingerprint = "fingerprint",
            securityPatch = "2024-11-01",
            verifiedBootState = "orange",
            vbmetaDeviceState = "unlocked",
            flashLocked = false,
            selinuxMode = "Enforcing",
            rootExpected = true,
            rootProvider = RootRuntimeProvider.MAGISK,
            rootModules = setOf("magisk:a", "vector:b"),
            hookFrameworkPackages = setOf("org.lsposed.manager"),
            expectedEnvironmentPackages = setOf("org.autojs.autojs"),
            adbExpected = true,
            vpnExpected = true,
            deviceSurfaceHashes = mapOf("cpu" to "abc", "sensor" to "def"),
        )

        val encoded = IntegrityBaselineCodec.encode(baseline)

        assertTrue(encoded.startsWith("ROOTTOOLS_INTEGRITY_BASELINE_V1\n"))
        assertEquals(baseline, IntegrityBaselineCodec.decode(encoded))
    }

    @Test
    fun codecPreservesUnicodeDelimitersAndNullableFlashLock() {
        val baseline = IntegrityBaseline(
            profileName = "三星 | 日常=rooted, 设备",
            trustedAtEpochMs = 7L,
            packageName = "com.arthur.roottools",
            versionCode = 3,
            signingSha256 = "aa:bb=cc",
            apkSha256 = "apk/hash+value",
            buildFingerprint = "samsung/b0q:14/user=release-keys",
            securityPatch = "2026-08-01",
            verifiedBootState = "orange",
            vbmetaDeviceState = "unlocked",
            flashLocked = null,
            selinuxMode = "Enforcing",
            rootExpected = true,
            rootProvider = RootRuntimeProvider.MAGISK,
            rootModules = setOf("module:a,b", "模块=二"),
            hookFrameworkPackages = setOf("org.lsposed.manager"),
            expectedEnvironmentPackages = setOf("io.example.env=one,two"),
            adbExpected = true,
            vpnExpected = false,
            deviceSurfaceHashes = mapOf("cpu:policy0" to "abc=123", "传感器,1" to "def/456"),
        )

        assertEquals(baseline, IntegrityBaselineCodec.decode(IntegrityBaselineCodec.encode(baseline)))
    }

    @Test
    fun codecRejectsUnknownOrMalformedPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            IntegrityBaselineCodec.decode("unknown-format")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IntegrityBaselineCodec.decode("ROOTTOOLS_INTEGRITY_BASELINE_V1\nprofileName=not-base64!\n")
        }
    }
}
