package com.arthur.roottools.integration.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TermuxTaskControllerTest {
    @Test
    fun `key value parser ignores malformed and unbounded keys`() {
        val values = TermuxTaskController.parseKeyValue(
            """
            installed=1
            sha256=abc123
            bad key=value
            no-delimiter
            ${"x".repeat(80)}=bad
            """.trimIndent()
        )

        assertEquals("1", values["installed"])
        assertEquals("abc123", values["sha256"])
        assertFalse(values.containsKey("bad key"))
    }
}

