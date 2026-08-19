package com.arthur.roottools.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutomationRateLimiterTest {
    @Before
    fun reset() = AutomationRateLimiter.clearForTest()

    @Test
    fun `allows sixty requests then rejects within one minute`() {
        repeat(60) { index ->
            assertTrue(AutomationRateLimiter.tryAcquire("termux-mcp", nowMs = index.toLong()))
        }
        assertFalse(AutomationRateLimiter.tryAcquire("termux-mcp", nowMs = 59L))
    }

    @Test
    fun `old requests expire from window`() {
        repeat(60) { assertTrue(AutomationRateLimiter.tryAcquire("termux-mcp", nowMs = 0L)) }
        assertTrue(AutomationRateLimiter.tryAcquire("termux-mcp", nowMs = 60_000L))
    }
}
