package com.arthur.roottools.feature.network.inspection.intercept

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InterceptionStopPolicyTest {
    @Test
    fun `successful cleanup is the only stopped state`() {
        val outcome = InterceptionStopPolicy.outcome(cleanupSucceeded = true, technicalDetail = "ignored")

        assertEquals(InterceptionPhase.IDLE, outcome.phase)
        assertEquals(InterceptionStatus.STOPPED, outcome.status)
        assertNull(outcome.lastError)
    }

    @Test
    fun `failed cleanup remains visible as an error`() {
        val outcome = InterceptionStopPolicy.outcome(cleanupSucceeded = false, technicalDetail = "iptables failed")

        assertEquals(InterceptionPhase.ERROR, outcome.phase)
        assertEquals(InterceptionStatus.RULE_CLEANUP_FAILED, outcome.status)
        assertEquals("iptables failed", outcome.lastError)
    }

    @Test
    fun `blank shell output gets an actionable fallback`() {
        val outcome = InterceptionStopPolicy.outcome(cleanupSucceeded = false, technicalDetail = "  ")

        assertEquals(
            "Interception stopped, but RootTools could not verify that redirect rules were removed",
            outcome.lastError,
        )
    }
}
