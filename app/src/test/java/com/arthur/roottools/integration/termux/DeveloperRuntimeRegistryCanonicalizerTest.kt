package com.arthur.roottools.integration.termux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperRuntimeRegistryCanonicalizerTest {
    @Test
    fun `registry ordering is deterministic and excludes credentials`() {
        val payload = DeveloperRuntimeRegistryPayload(
            deviceId = "123e4567-e89b-42d3-a456-426614174000",
            generatedAtEpochMs = 1_800_000_000_000L,
            artifacts = listOf(
                DeveloperRuntimeArtifactDescriptor("termux-mcp-relay", 2, "b".repeat(64), "roottools_mcp.py"),
                DeveloperRuntimeArtifactDescriptor("roottools-cli", 2, "a".repeat(64), "roottools"),
            ),
        )
        val first = DeveloperRuntimeRegistryCanonicalizer.canonicalPayload(payload)
        val second = DeveloperRuntimeRegistryCanonicalizer.canonicalPayload(payload.copy(artifacts = payload.artifacts.reversed()))
        assertEquals(first, second)
        assertTrue(first.indexOf("roottools-cli") < first.indexOf("termux-mcp-relay"))
        assertTrue(first.contains("\"workflows\""))
        assertTrue(!first.contains("bearer"))
        assertTrue(!first.contains("token"))
    }
}

