package com.arthur.roottools.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsMutationPolicyTest {
    private val supported = setOf("RUN_IN_BACKGROUND", "POST_NOTIFICATION")
    private val modes = setOf("allow", "ignore", "deny", "default", "foreground")

    @Test
    fun normalTarget_normalizesAndAllowsKnownOperation() {
        val result = AppOpsMutationPolicy.evaluate(
            packageName = "com.example.app",
            op = "run_in_background",
            mode = "ALLOW",
            protectedPackages = emptySet(),
            supportedOps = supported,
            writableModes = modes,
        )
        assertTrue(result.allowed)
        assertEquals("RUN_IN_BACKGROUND", result.op)
        assertEquals("allow", result.mode)
    }

    @Test
    fun hostilePackage_isRejectedBeforeCommandLayer() {
        val result = AppOpsMutationPolicy.evaluate(
            "com.example.app;id",
            "RUN_IN_BACKGROUND",
            "allow",
            emptySet(),
            supported,
            modes,
        )
        assertFalse(result.allowed)
        assertEquals(AppOpsMutationRejection.INVALID_PACKAGE, result.rejection)
    }

    @Test
    fun unknownOperationAndMode_areRejected() {
        assertEquals(
            AppOpsMutationRejection.UNSUPPORTED_OP,
            AppOpsMutationPolicy.evaluate("com.example.app", "CAMERA", "allow", emptySet(), supported, modes).rejection,
        )
        assertEquals(
            AppOpsMutationRejection.UNSUPPORTED_MODE,
            AppOpsMutationPolicy.evaluate("com.example.app", "POST_NOTIFICATION", "allow;id", emptySet(), supported, modes).rejection,
        )
    }

    @Test
    fun protectedPackage_cannotBeDeniedOrIgnored() {
        val protected = setOf("com.example.app")
        listOf("deny", "ignore").forEach { mode ->
            val result = AppOpsMutationPolicy.evaluate("com.example.app", "POST_NOTIFICATION", mode, protected, supported, modes)
            assertFalse(result.allowed)
            assertEquals(AppOpsMutationRejection.PROTECTED_PACKAGE_RESTRICTION, result.rejection)
        }
        assertTrue(AppOpsMutationPolicy.evaluate("com.example.app", "POST_NOTIFICATION", "allow", protected, supported, modes).allowed)
    }
}
