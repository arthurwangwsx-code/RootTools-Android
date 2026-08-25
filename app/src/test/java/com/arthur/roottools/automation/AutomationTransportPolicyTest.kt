package com.arthur.roottools.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTransportPolicyTest {
    @Test
    fun `system marked shell broadcast can call only shadow display command family`() {
        val flags = AutomationTransportPolicy.FLAG_RECEIVER_FROM_SHELL
        val shadowCommands = AutomationCommand.entries.filter { it.requiredScope == AutomationScope.SHADOW_DISPLAY }

        assertTrue(shadowCommands.isNotEmpty())
        shadowCommands.forEach { command ->
            assertTrue(AutomationTransportPolicy.isTrustedAdbShadowRequest(flags, command))
        }
        assertFalse(AutomationTransportPolicy.isTrustedAdbShadowRequest(flags, AutomationCommand.SET_ADB))
        assertFalse(AutomationTransportPolicy.isTrustedAdbShadowRequest(flags, AutomationCommand.FREEZE))
        assertFalse(AutomationTransportPolicy.isTrustedAdbShadowRequest(flags, AutomationCommand.RUN_WORKFLOW))
    }

    @Test
    fun `broadcast without system shell marker cannot claim adb shadow transport`() {
        assertFalse(
            AutomationTransportPolicy.isTrustedAdbShadowRequest(
                intentFlags = 0,
                command = AutomationCommand.SHADOW_START,
            )
        )
    }
}
