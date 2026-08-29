package com.aibox.backgroundserver.runtime

import com.aibox.backgroundserver.engine.BackgroundEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineSupervisorTest {
    @Test
    fun `supervisor starts and stops registered engine`() {
        val engine = FakeEngine("wireguard")
        val supervisor = EngineSupervisor(listOf(engine))

        assertTrue(supervisor.start("wireguard").isSuccess)
        assertTrue(supervisor.isRunning("wireguard"))
        assertTrue(supervisor.runningEngineIds().contains("wireguard"))

        assertTrue(supervisor.stop("wireguard").isSuccess)
        assertFalse(supervisor.isRunning("wireguard"))
    }

    @Test
    fun `unknown engine returns failure without affecting registered engines`() {
        val engine = FakeEngine("wireguard")
        val supervisor = EngineSupervisor(listOf(engine))

        assertTrue(supervisor.start("openvpn").isFailure)
        assertFalse(supervisor.isRunning("wireguard"))
    }

    private class FakeEngine(override val engineId: String) : BackgroundEngine {
        private var running = false
        override fun isRunning(): Boolean = running
        override fun start(): Result<Unit> = Result.success(Unit).also { running = true }
        override fun stop(): Result<Unit> = Result.success(Unit).also { running = false }
    }
}
