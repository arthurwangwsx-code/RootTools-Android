package com.arthur.roottools.data

import com.arthur.roottools.model.HealthHistoryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthHistoryCodecTest {
    @Test
    fun roundTripKeepsLightweightHistoryFields() {
        val source = HealthHistoryPoint(
            timestampMs = 123456789L,
            cpuUsagePercent = 42.5f,
            memoryAvailableKb = 3_900_000L,
            apTempC = 41.2f,
            skinTempC = 35.6f,
            batteryLevel = 79,
            thermalStatus = 1,
            clusterCurrentKHz = mapOf(0 to 614400L, 4 to 1555200L, 7 to 806400L),
        )

        assertEquals(source, HealthHistoryCodec.decode(HealthHistoryCodec.encode(source)))
    }

    @Test
    fun malformedLineIsIgnored() {
        assertNull(HealthHistoryCodec.decode("broken"))
    }
}
