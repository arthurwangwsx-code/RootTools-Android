package com.arthur.roottools.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivilegeInputValidatorTest {
    @Test
    fun packageName_acceptsNormalAndroidPackage() {
        assertEquals("com.tencent.mm", PrivilegeInputValidator.packageName("com.tencent.mm"))
        assertEquals("io.appium.settings", PrivilegeInputValidator.packageName("io.appium.settings"))
    }

    @Test
    fun packageName_rejectsShellInjectionAndMalformedValues() {
        assertNull(PrivilegeInputValidator.packageName("com.tencent.mm; reboot"))
        assertNull(PrivilegeInputValidator.packageName("com.tencent.mm && id"))
        assertNull(PrivilegeInputValidator.packageName("single"))
        assertNull(PrivilegeInputValidator.packageName("com..broken"))
    }

    @Test
    fun componentName_acceptsFlattenedComponentOnly() {
        assertEquals(
            "com.tencent.mm/.ui.LauncherUI",
            PrivilegeInputValidator.componentName("com.tencent.mm/.ui.LauncherUI"),
        )
        assertEquals(
            "io.appium.settings/io.appium.settings.NLService",
            PrivilegeInputValidator.componentName("io.appium.settings/io.appium.settings.NLService"),
        )
        assertNull(PrivilegeInputValidator.componentName("com.tencent.mm/.ui.LauncherUI; id"))
    }

    @Test
    fun appOps_areAllowListedAndNormalized() {
        assertEquals("RUN_IN_BACKGROUND", PrivilegeInputValidator.appOpName("run_in_background"))
        assertEquals("foreground", PrivilegeInputValidator.appOpMode("FOREGROUND"))
        assertNull(PrivilegeInputValidator.appOpName("RUN;ID"))
        assertNull(PrivilegeInputValidator.appOpMode("allow;reboot"))
    }

    @Test
    fun standbyBucket_isExplicitlyAllowListed() {
        assertEquals(30, PrivilegeInputValidator.standbyBucket(30))
        assertNull(PrivilegeInputValidator.standbyBucket(99))
    }
}
