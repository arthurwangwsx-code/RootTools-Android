package com.arthur.roottools.feature.home.presentation

import com.arthur.roottools.model.CpuPolicyEvent
import com.arthur.roottools.model.CpuPolicyEventType
import com.arthur.roottools.model.MemoryPressureStatus
import com.arthur.roottools.model.RootActionAuditRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHealthPolicyTest {
    private fun input(
        root: Boolean = true,
        metricsAvailable: Boolean = true,
        cpu: Float = 15f,
        thermal: Int = 0,
        skin: Float? = 34f,
        abnormalRootShells: Int = 0,
        memory: MemoryPressureStatus = MemoryPressureStatus.HEALTHY,
    ) = HomeHealthInput(root, metricsAvailable, cpu, thermal, skin, abnormalRootShells, memory)

    @Test
    fun unavailableLiveMetricsShowLoadingInsteadOfFalsePressure() {
        val decision = HomeHealthPolicy.decide(
            input(metricsAvailable = false, memory = MemoryPressureStatus.PRESSURE),
        )

        assertEquals(HomeHealthVerdict.LOADING, decision.verdict)
        assertTrue(decision.attention.isEmpty())
    }

    @Test
    fun healthySnapshotIsGood() {
        val decision = HomeHealthPolicy.decide(input())

        assertEquals(HomeHealthVerdict.GOOD, decision.verdict)
        assertTrue(decision.attention.isEmpty())
    }

    @Test
    fun abnormalRootShellHasHighestPriority() {
        val decision = HomeHealthPolicy.decide(
            input(cpu = 95f, thermal = 2, skin = 41f, abnormalRootShells = 1),
        )

        assertEquals(HomeHealthVerdict.CRITICAL, decision.verdict)
        assertEquals(HomeAttentionType.ROOT_SHELL, decision.attention.first())
    }

    @Test
    fun thermalWarningBeatsBusyCpu() {
        val decision = HomeHealthPolicy.decide(input(cpu = 90f, skin = 39.5f))

        assertEquals(HomeHealthVerdict.WARM, decision.verdict)
        assertTrue(HomeAttentionType.THERMAL in decision.attention)
        assertTrue(HomeAttentionType.CPU in decision.attention)
    }

    @Test
    fun missingRootProducesSetupVerdict() {
        val decision = HomeHealthPolicy.decide(input(root = false))

        assertEquals(HomeHealthVerdict.SETUP, decision.verdict)
        assertEquals(listOf(HomeAttentionType.ROOT), decision.attention)
    }

    @Test
    fun timelineMergesAndSortsBothSources() {
        val cpu = listOf(CpuPolicyEvent(200, CpuPolicyEventType.MODE, "AUTO→COOL"))
        val audit = listOf(
            RootActionAuditRecord(
                timestampMs = 300,
                source = "UI",
                feature = "adb",
                action = "enable_root_tcp",
                target = "5555",
                before = "off",
                after = "on",
                success = true,
                rollbackHint = "",
            ),
            RootActionAuditRecord(
                timestampMs = 100,
                source = "UI",
                feature = "app",
                action = "freeze",
                target = "example",
                before = "enabled",
                after = "disabled",
                success = false,
                rollbackHint = "",
            ),
        )

        val timeline = HomeHealthPolicy.mergeTimeline(cpu, audit, limit = 2)

        assertEquals(listOf(300L, 200L), timeline.map { it.timestampMs })
        assertEquals(HomeTimelineKind.ROOT_ACTION, timeline.first().kind)
        assertEquals(HomeTimelineKind.PERFORMANCE, timeline.last().kind)
    }
}
