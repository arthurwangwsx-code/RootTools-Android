package com.arthur.roottools.policy

import com.arthur.roottools.model.CpuCluster
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage
import kotlin.math.roundToLong

/**
 * Pure CPU peak-selection policy.
 *
 * Cool is intentionally a "responsive cool" profile rather than a low-frequency profile:
 * efficiency cores retain their full peak for UI/background coordination while performance and
 * prime clusters lose only the least-efficient high-frequency tail. Vendor Thermal remains free
 * to impose a stricter cap; ownership/reconciliation is handled separately by
 * [CpuCapOwnershipDecider].
 */
object CpuFrequencyTargetPolicy {
    fun target(
        cluster: CpuCluster,
        index: Int,
        lastIndex: Int,
        mode: PerformanceMode,
        stage: ThermalStage,
    ): Long {
        val isLittle = index == 0
        val isPrime = index == lastIndex && lastIndex >= 2
        val effectiveStage = if (mode == PerformanceMode.COOL) {
            maxOf(stage, ThermalStage.WARM)
        } else {
            stage
        }

        val ratio = when {
            mode == PerformanceMode.PERFORMANCE && effectiveStage == ThermalStage.NORMAL -> 1.00
            mode == PerformanceMode.COOL && effectiveStage == ThermalStage.WARM -> when {
                isLittle -> 1.00
                isPrime -> 0.78
                else -> 0.86
            }
            else -> when (effectiveStage) {
                ThermalStage.NORMAL -> 1.00
                ThermalStage.WARM -> when {
                    isLittle -> 1.00
                    isPrime -> 0.84
                    else -> 0.89
                }
                ThermalStage.MODERATE -> when {
                    isLittle -> 0.95
                    isPrime -> 0.76
                    else -> 0.80
                }
                ThermalStage.SEVERE -> when {
                    isLittle -> 0.90
                    isPrime -> 0.68
                    else -> 0.72
                }
            }
        }
        return nearestFrequency(cluster, cluster.hardwareMaxKHz * ratio)
    }

    private fun nearestFrequency(cluster: CpuCluster, target: Double): Long {
        val candidates = (cluster.availableKHz + cluster.hardwareMaxKHz)
            .filter { it > 0L }
            .distinct()
            .sorted()
        if (candidates.isEmpty()) return target.roundToLong()
        return candidates.lastOrNull { it <= target.roundToLong() } ?: candidates.first()
    }
}
