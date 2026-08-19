package com.arthur.roottools.integration.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxMcpRelayScriptBuilderTest {
    private val deviceId = "123e4567-e89b-42d3-a456-426614174000"
    private val rootToolsToken = "a".repeat(64)
    private val bearer = "b".repeat(64)

    @Test
    fun `relay exposes only semantic RootTools tools`() {
        val script = TermuxMcpRelayScriptBuilder.build(deviceId, rootToolsToken, bearer)
        assertTrue(script.contains("get_device_identity"))
        assertTrue(script.contains("get_device_status"))
        assertTrue(script.contains("set_performance_mode"))
        assertTrue(script.contains("ensure_root_adb"))
        assertTrue(script.contains("run_diagnostic"))
        assertTrue(script.contains("freeze_app"))
        assertTrue(script.contains("enable_app"))
        assertTrue(script.contains("run_workflow"))
        assertFalse(script.contains("execute_shell"))
        assertFalse(script.contains("shell=True"))
        assertFalse(script.contains("su -c"))
        assertFalse(script.contains("REBOOT"))
    }

    @Test
    fun `relay implements current stateless mcp metadata and safe binds`() {
        val script = TermuxMcpRelayScriptBuilder.build(deviceId, rootToolsToken, bearer)
        assertTrue(script.contains("2026-07-28"))
        assertTrue(script.contains("server/discover"))
        assertTrue(script.contains("tools/list"))
        assertTrue(script.contains("tools/call"))
        assertTrue(script.contains("MCP-Protocol-Version"))
        assertTrue(script.contains("Mcp-Method"))
        assertTrue(script.contains("Mcp-Name"))
        assertTrue(script.contains("notifications/progress"))
        assertTrue(script.contains("X-Accel-Buffering"))
        assertTrue(script.contains("127.0.0.1"))
        assertTrue(script.contains("100.64.0.0/10"))
        assertFalse(script.contains("\"0.0.0.0\""))
    }
}

