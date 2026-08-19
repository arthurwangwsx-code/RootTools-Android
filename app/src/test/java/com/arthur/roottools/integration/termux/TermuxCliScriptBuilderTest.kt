package com.arthur.roottools.integration.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxCliScriptBuilderTest {
    private val token = "a".repeat(64)

    @Test
    fun `script exposes only typed roottools actions`() {
        val script = TermuxCliScriptBuilder.build(token)

        assertTrue(script.contains("--es command GET_STATUS"))
        assertTrue(script.contains("--es command SET_MODE"))
        assertTrue(script.contains("--es command SET_ADB"))
        assertTrue(script.contains("--es command SET_NATIVE_ADB"))
        assertTrue(script.contains("--es command RUN_DIAGNOSTIC"))
        assertTrue(script.contains("--es command FREEZE"))
        assertTrue(script.contains("--es command UNFREEZE"))
        assertTrue(script.contains("--es command RUN_WORKFLOW"))
        assertFalse(script.contains("--es command SHELL"))
        assertFalse(script.contains("--es command REBOOT"))
        assertFalse(script.contains("su -c"))
    }

    @Test
    fun `root tcp off is not emitted`() {
        val script = TermuxCliScriptBuilder.build(token)
        assertTrue(script.contains("Root TCP ADB can only be enabled remotely."))
        assertFalse(script.contains("SET_ADB --ez enabled false"))
    }

    @Test
    fun `invalid token is rejected before script generation`() {
        assertThrows(IllegalArgumentException::class.java) {
            TermuxCliScriptBuilder.build("bad token; rm -rf /")
        }
    }
}

