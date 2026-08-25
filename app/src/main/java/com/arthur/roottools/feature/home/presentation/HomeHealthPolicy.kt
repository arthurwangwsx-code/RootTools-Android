package com.arthur.roottools.feature.home.presentation

import com.arthur.roottools.model.CpuPolicyEvent
import com.arthur.roottools.model.MemoryPressureStatus
import com.arthur.roottools.model.RootActionAuditRecord

enum class HomeHealthVerdict {
    LOADING,
    GOOD,
    SETUP,
    BUSY,
    WARM,
    CRITICAL,
}

enum class HomeAttentionType {
    ROOT_SHELL,
    THERMAL,
    CPU,
    MEMORY,
    ROOT,
}

data class HomeHealthInput(
    val rootAvailable: Boolean,
    val metricsAvailable: Boolean = true,
    val cpuUsagePercent: Float,
    val thermalStatus: Int,
    val skinC: Float?,
    val abnormalRootShells: Int,
    val memoryStatus: MemoryPressureStatus,
)

data class HomeHealthDecision(
    val verdict: HomeHealthVerdict,
    val attention: List<HomeAttentionType>,
)

enum class HomeTimelineKind {
    PERFORMANCE,
    ROOT_ACTION,
}

data class HomeTimelineEntry(
    val timestampMs: Long,
    val kind: HomeTimelineKind,
    val summary: String,
    val success: Boolean,
)

object HomeHealthPolicy {
    private const val HOT_SKIN_C = 39f
    private const val HIGH_CPU_PERCENT = 80f

    fun decide(input: HomeHealthInput): HomeHealthDecision {
        val attention = buildList {
            if (input.abnormalRootShells > 0) add(HomeAttentionType.ROOT_SHELL)
            if (!input.rootAvailable) add(HomeAttentionType.ROOT)
            if (input.metricsAvailable) {
                if (input.thermalStatus >= 2 || (input.skinC ?: 0f) >= HOT_SKIN_C) add(HomeAttentionType.THERMAL)
                if (input.cpuUsagePercent >= HIGH_CPU_PERCENT) add(HomeAttentionType.CPU)
                if (input.memoryStatus == MemoryPressureStatus.PRESSURE) add(HomeAttentionType.MEMORY)
            }
        }

        val verdict = when {
            HomeAttentionType.ROOT_SHELL in attention -> HomeHealthVerdict.CRITICAL
            HomeAttentionType.ROOT in attention -> HomeHealthVerdict.SETUP
            !input.metricsAvailable -> HomeHealthVerdict.LOADING
            HomeAttentionType.THERMAL in attention -> HomeHealthVerdict.WARM
            HomeAttentionType.CPU in attention || HomeAttentionType.MEMORY in attention -> HomeHealthVerdict.BUSY
            else -> HomeHealthVerdict.GOOD
        }
        return HomeHealthDecision(verdict, attention)
    }

    fun mergeTimeline(
        cpuEvents: List<CpuPolicyEvent>,
        auditRecords: List<RootActionAuditRecord>,
        limit: Int = 5,
    ): List<HomeTimelineEntry> {
        if (limit <= 0) return emptyList()
        val performance = cpuEvents.map {
            HomeTimelineEntry(
                timestampMs = it.timestampMs,
                kind = HomeTimelineKind.PERFORMANCE,
                summary = it.message,
                success = true,
            )
        }
        val audit = auditRecords.map {
            HomeTimelineEntry(
                timestampMs = it.timestampMs,
                kind = HomeTimelineKind.ROOT_ACTION,
                summary = listOf(it.feature, it.action).filter(String::isNotBlank).joinToString(" · "),
                success = it.success,
            )
        }
        return (performance + audit)
            .sortedByDescending(HomeTimelineEntry::timestampMs)
            .take(limit)
    }
}
