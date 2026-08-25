package com.arthur.roottools.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSessionPolicyTest {
    private val running = AgentSessionState(
        taskId = "task-1",
        title = "Task",
        currentStep = "Working",
        status = AgentSessionStatus.RUNNING,
    )

    @Test
    fun `active states keep foreground service alive`() {
        assertTrue(AgentSessionPolicy.shouldRunForeground(running))
        assertTrue(AgentSessionPolicy.shouldRunForeground(running.copy(status = AgentSessionStatus.PAUSED)))
        assertTrue(AgentSessionPolicy.shouldRunForeground(running.copy(status = AgentSessionStatus.WAITING_USER)))
        assertFalse(AgentSessionPolicy.shouldRunForeground(running.copy(status = AgentSessionStatus.COMPLETED)))
    }

    @Test
    fun `overlay degrades cleanly when permission is unavailable`() {
        assertFalse(AgentSessionPolicy.shouldShowOverlay(running, canDrawOverlays = false))
        assertTrue(AgentSessionPolicy.shouldShowOverlay(running, canDrawOverlays = true))
        assertFalse(
            AgentSessionPolicy.shouldShowOverlay(
                running.copy(overlayMode = AgentOverlayMode.HIDDEN),
                canDrawOverlays = true,
            )
        )
    }

    @Test
    fun `preview refresh happens only for expanded running overlay`() {
        assertFalse(AgentSessionPolicy.shouldRefreshPreview(running, canDrawOverlays = true))
        assertTrue(
            AgentSessionPolicy.shouldRefreshPreview(
                running.copy(overlayMode = AgentOverlayMode.EXPANDED),
                canDrawOverlays = true,
            )
        )
        assertFalse(
            AgentSessionPolicy.shouldRefreshPreview(
                running.copy(overlayMode = AgentOverlayMode.EXPANDED, status = AgentSessionStatus.PAUSED),
                canDrawOverlays = true,
            )
        )
    }

    @Test
    fun `waiting user uses attention channel`() {
        assertEquals(AgentNotificationChannelKind.RUNNING, AgentSessionPolicy.notificationChannel(running))
        assertEquals(
            AgentNotificationChannelKind.ATTENTION,
            AgentSessionPolicy.notificationChannel(running.copy(status = AgentSessionStatus.WAITING_USER)),
        )
    }

    @Test
    fun `progress rejects invalid values and clamps overflow`() {
        assertNull(AgentSessionPolicy.normalizedProgress(1, 0))
        assertNull(AgentSessionPolicy.normalizedProgress(-1, 10))
        assertEquals(10 to 10, AgentSessionPolicy.normalizedProgress(12, 10))
    }
}
