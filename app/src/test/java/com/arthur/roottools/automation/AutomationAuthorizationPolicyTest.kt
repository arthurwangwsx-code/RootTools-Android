package com.arthur.roottools.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationAuthorizationPolicyTest {
    @Test
    fun `unknown command is rejected`() {
        assertNull(AutomationCommand.parse("SHELL"))
        assertNull(AutomationCommand.parse("REBOOT"))
    }

    @Test
    fun `scope must match command`() {
        assertTrue(
            AutomationAuthorizationPolicy.isAllowed(
                scopes = setOf(AutomationScope.SET_PERFORMANCE),
                command = AutomationCommand.SET_MODE,
            )
        )
        assertFalse(
            AutomationAuthorizationPolicy.isAllowed(
                scopes = setOf(AutomationScope.READ_STATUS),
                command = AutomationCommand.SET_MODE,
            )
        )
    }

    @Test
    fun `external automation cannot disable root tcp adb`() {
        assertTrue(
            AutomationAuthorizationPolicy.isAllowed(
                scopes = setOf(AutomationScope.SET_ADB_ENABLE),
                command = AutomationCommand.SET_ADB,
                enabled = true,
            )
        )
        assertFalse(
            AutomationAuthorizationPolicy.isAllowed(
                scopes = setOf(AutomationScope.SET_ADB_ENABLE),
                command = AutomationCommand.SET_ADB,
                enabled = false,
            )
        )
    }

    @Test
    fun `integrity scans require the typed integrity scope`() {
        assertTrue(
            AutomationAuthorizationPolicy.isAllowed(
                scopes = setOf(AutomationScope.INTEGRITY_SCAN),
                command = AutomationCommand.INTEGRITY_FAST_SCAN,
            )
        )
        assertTrue(
            AutomationAuthorizationPolicy.isAllowed(
                scopes = setOf(AutomationScope.INTEGRITY_SCAN),
                command = AutomationCommand.INTEGRITY_DEEP_SCAN,
            )
        )
        assertFalse(
            AutomationAuthorizationPolicy.isAllowed(
                scopes = setOf(AutomationScope.RUN_DIAGNOSTIC),
                command = AutomationCommand.INTEGRITY_DEEP_SCAN,
            )
        )
        assertTrue(AutomationCommand.parse("integrity_fast_scan") == AutomationCommand.INTEGRITY_FAST_SCAN)
    }

    @Test
    fun `termux defaults contain no destructive root scope`() {
        val defaults = AutomationAuthorizationPolicy.termuxDefaultScopes
        assertTrue(AutomationScope.READ_STATUS in defaults)
        assertTrue(AutomationScope.RUN_DIAGNOSTIC in defaults)
        assertTrue(AutomationScope.SET_PERFORMANCE in defaults)
        assertTrue(AutomationScope.APP_POLICY in defaults)
        assertTrue(AutomationScope.INTEGRITY_SCAN in defaults)
    }
}

