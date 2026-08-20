package com.arthur.roottools.feature.integrity.policy

import com.arthur.roottools.feature.integrity.model.DeviceSurfaceSignals
import com.arthur.roottools.feature.integrity.model.EnvironmentContextSignals
import com.arthur.roottools.feature.integrity.model.EnvironmentPackageCategory
import com.arthur.roottools.feature.integrity.model.EnvironmentPackageSignal
import com.arthur.roottools.feature.integrity.model.IntegritySignals
import com.arthur.roottools.feature.integrity.model.RootRuntimeProvider
import com.arthur.roottools.feature.integrity.model.RootRuntimeSignals
import com.arthur.roottools.feature.integrity.model.RuntimeIntegritySignals
import com.arthur.roottools.feature.integrity.model.SelfIdentitySignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityBaselineMatcherTest {
    @Test
    fun captureStoresExpectedEnvironmentButNeverStoresRuntimeInjectionMarkers() {
        val signals = IntegritySignals(
            self = SelfIdentitySignals(packageName = "com.arthur.roottools", signingSha256 = "sign", apkSha256 = "apk", versionCode = 3),
            rootRuntime = RootRuntimeSignals(rootAvailable = true, provider = RootRuntimeProvider.MAGISK, modules = setOf("module-a")),
            runtime = RuntimeIntegritySignals(strongMarkers = setOf("frida-agent.so")),
            environment = EnvironmentContextSignals(
                packages = listOf(EnvironmentPackageSignal("org.autojs.autojs", EnvironmentPackageCategory.AUTOMATION)),
                adbEnabled = true,
                vpnActive = true,
            ),
            deviceSurface = DeviceSurfaceSignals(cpuTopologyHash = "cpu-hash"),
        )

        val baseline = IntegrityBaselineMatcher.capture("daily", signals, 42L)

        assertEquals(setOf("org.autojs.autojs"), baseline.expectedEnvironmentPackages)
        assertEquals(setOf("module-a"), baseline.rootModules)
        assertTrue(baseline.adbExpected)
        assertTrue(baseline.vpnExpected)
        assertEquals("cpu-hash", baseline.deviceSurfaceHashes["cpu"])
        assertFalse(baseline.expectedEnvironmentPackages.contains("frida-agent.so"))
    }

    @Test
    fun diffIgnoresApkHashAcrossVersionUpgrade() {
        val baselineSignals = IntegritySignals(
            self = SelfIdentitySignals(packageName = "com.arthur.roottools", versionCode = 3, signingSha256 = "sign", apkSha256 = "old"),
        )
        val baseline = IntegrityBaselineMatcher.capture("daily", baselineSignals, 1L)
        val upgraded = baselineSignals.copy(self = baselineSignals.self.copy(versionCode = 4, apkSha256 = "new"))

        val diffs = IntegrityBaselineMatcher.diff(baseline, upgraded)

        assertFalse(diffs.any { it.field == "apk" })
    }
}
