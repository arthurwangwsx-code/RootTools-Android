package com.arthur.roottools.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTransportPolicyTest {
    @Test
    fun `system marked shell broadcast can call only approved typed command families`() {
        val flags = AutomationTransportPolicy.FLAG_RECEIVER_FROM_SHELL
        val shadowCommands = AutomationCommand.entries.filter { it.requiredScope == AutomationScope.SHADOW_DISPLAY }
        val agentCommands = AutomationCommand.entries.filter { it.requiredScope == AutomationScope.AGENT_SESSION }

        assertTrue(shadowCommands.isNotEmpty())
        shadowCommands.forEach { command ->
            assertTrue(AutomationTransportPolicy.isTrustedAdbRequest(flags, command))
        }
        assertTrue(agentCommands.isNotEmpty())
        agentCommands.forEach { command ->
            assertTrue(AutomationTransportPolicy.isTrustedAdbRequest(flags, command))
        }
        assertFalse(AutomationTransportPolicy.isTrustedAdbRequest(flags, AutomationCommand.SET_ADB))
        assertFalse(AutomationTransportPolicy.isTrustedAdbRequest(flags, AutomationCommand.FREEZE))
        assertFalse(AutomationTransportPolicy.isTrustedAdbRequest(flags, AutomationCommand.RUN_WORKFLOW))
    }

    @Test
    fun `broadcast without system shell marker cannot claim adb transport`() {
        assertFalse(
            AutomationTransportPolicy.isTrustedAdbRequest(
                intentFlags = 0,
                command = AutomationCommand.SHADOW_START,
            )
        )
        assertFalse(
            AutomationTransportPolicy.isTrustedAdbRequest(
                intentFlags = 0,
                command = AutomationCommand.AGENT_STOP,
            )
        )
    }
}
