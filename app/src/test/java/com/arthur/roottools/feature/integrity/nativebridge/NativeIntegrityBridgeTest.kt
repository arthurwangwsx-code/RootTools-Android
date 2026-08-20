package com.arthur.roottools.feature.integrity.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeIntegrityBridgeTest {
    @Test
    fun parsesNativeSummary() {
        val result = NativeIntegrityBridge.parse(
            """
            available=1
            tracerPid=0
            mappingCount=321
            writableExecutableCount=1
            deletedExecutableCount=2
            strongMarkers=frida,zygisk
            loadedElfCount=56
            selfExecutableSegments=1
            selfExecutableSegmentMismatches=0
            selfLibraryPath=/data/app/libroottools_integrity.so
            """.trimIndent()
        )
        assertTrue(result.available)
        assertEquals(321, result.mappingCount)
        assertEquals(setOf("frida", "zygisk"), result.strongMarkers)
        assertEquals(0, result.selfExecutableSegmentMismatches)
    }
}
