package com.arthur.roottools.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedWorkflowCanonicalizerTest {
    @Test
    fun `canonical workflow payload is stable`() {
        val request = ManagedWorkflowRequest(ManagedWorkflowId.APP_TEST_READY, "com.example.app")
        val first = ManagedWorkflowCanonicalizer.canonicalPayload(
            request,
            "123e4567-e89b-42d3-a456-426614174000",
            1_800_000_000_000L,
        )
        val second = ManagedWorkflowCanonicalizer.canonicalPayload(
            request,
            "123e4567-e89b-42d3-a456-426614174000",
            1_800_000_000_000L,
        )
        assertEquals(first, second)
        assertTrue(first.contains("\"workflowId\":\"APP_TEST_READY\""))
        assertTrue(first.contains("\"packageName\":\"com.example.app\""))
        assertTrue(first.contains("\"type\":\"ENSURE_ROOT_ADB\""))
    }

    @Test
    fun `invalid device identity cannot be signed canonically`() {
        assertThrows(IllegalArgumentException::class.java) {
            ManagedWorkflowCanonicalizer.canonicalPayload(
                ManagedWorkflowRequest(ManagedWorkflowId.TEST_DEVICE_READY),
                "../../device",
                1L,
            )
        }
    }
}

