package com.arthur.roottools.feature.network.inspection.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureReadinessPolicyTest {
    @Test
    fun `root denial wins over packaged backend`() {
        assertEquals(
            CaptureStatus.ROOT_REQUIRED,
            CaptureReadinessPolicy.status(
                rootAvailable = false,
                pcapdAvailable = true,
                tcpdumpAvailable = true,
                activeSessionRecovered = false,
            ),
        )
    }

    @Test
    fun `recovered active session wins over ready status`() {
        assertEquals(
            CaptureStatus.ACTIVE_RECOVERED,
            CaptureReadinessPolicy.status(
                rootAvailable = true,
                pcapdAvailable = true,
                tcpdumpAvailable = false,
                activeSessionRecovered = true,
            ),
        )
    }

    @Test
    fun `pcapd is preferred and tcpdump remains a fallback`() {
        assertEquals(
            CaptureStatus.READY_PCAPD,
            CaptureReadinessPolicy.status(true, true, true, false),
        )
        assertEquals(
            CaptureStatus.READY_TCPDUMP,
            CaptureReadinessPolicy.status(true, false, true, false),
        )
    }

    @Test
    fun `missing root backends is explicit`() {
        assertEquals(
            CaptureStatus.BACKEND_UNAVAILABLE,
            CaptureReadinessPolicy.status(true, false, false, false),
        )
    }
}
