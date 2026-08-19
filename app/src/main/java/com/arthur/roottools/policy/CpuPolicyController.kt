package com.arthur.roottools.policy

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.model.AppliedCpuPolicy
import com.arthur.roottools.model.CpuCluster
import com.arthur.roottools.model.CpuPolicyEventType
import com.arthur.roottools.model.DeviceSnapshot
import com.arthur.roottools.model.PerformanceMode
import com.arthur.roottools.model.ThermalStage
import com.arthur.roottools.root.RootShell
import kotlin.math.roundToLong

class CpuPolicyController(
    private val shell: RootShell,
    private val store: PolicyStore,
    private val eventStore: CpuPolicyEventStore? = null,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "cpu-policy",
) {
    suspend fun apply(
        mode: PerformanceMode,
        snapshot: DeviceSnapshot,
        requestedStage: ThermalStage = snapshot.thermalStage(),
    ): AppliedCpuPolicy? {
        if (!snapshot.rootAvailable || snapshot.cpuClusters.isEmpty()) return null
        // Fresh installs start directly on the ownership-aware schema. Legacy installs are
        // identified by baseline_min_* keys that existed before this schema was introduced.
        if (store.policySchemaVersion == 0 && !store.hasLegacyBaseline()) {
            store.policySchemaVersion = POLICY_SCHEMA_VERSION
        }
        store.ensureBaseline(snapshot.cpuClusters)

        val stage = when (mode) {
            PerformanceMode.AUTO -> requestedStage
            PerformanceMode.COOL -> maxOf(requestedStage, ThermalStage.WARM)
            PerformanceMode.PERFORMANCE -> if (requestedStage >= ThermalStage.MODERATE) requestedStage else ThermalStage.NORMAL
        }
        val desiredByPolicy = linkedMapOf<Int, Long>()
        val writes = linkedMapOf<Int, Long>()

        snapshot.cpuClusters.forEachIndexed { index, cluster ->
            val desired = targetFor(cluster, index, snapshot.cpuClusters.lastIndex, mode, stage)
            desiredByPolicy[cluster.policyId] = desired
            val decision = CpuCapOwnershipDecider.decide(
                currentKHz = cluster.scalingMaxKHz,
                desiredKHz = desired,
                hardwareMaxKHz = cluster.hardwareMaxKHz,
                thermalStatus = snapshot.thermalStatus,
                ownedKHz = store.ownedMax(cluster.policyId),
            )
            if (decision.clearPreviousOwnership) store.clearOwnedMax(cluster.policyId)
            decision.writeTargetKHz?.let { writes[cluster.policyId] = it }
        }

        if (writes.isEmpty()) return AppliedCpuPolicy(mode, stage, desiredByPolicy)

        val clusterById = snapshot.cpuClusters.associateBy { it.policyId }
        val script = buildString {
            append("set -e\n")
            writes.forEach { (policyId, target) ->
                append("echo ").append(target)
                    .append(" > /sys/devices/system/cpu/cpufreq/policy")
                    .append(policyId).append("/scaling_max_freq\n")
            }
        }
        val result = shell.execute(script, timeoutSeconds = 4)
        if (!result.success) return null

        val changes = writes.mapNotNull { (policyId, target) ->
            val cluster = clusterById[policyId] ?: return@mapNotNull null
            "p$policyId ${cluster.scalingMaxKHz}→$target"
        }
        writes.forEach { (policyId, target) ->
            val hardwareMax = clusterById[policyId]?.hardwareMaxKHz ?: Long.MAX_VALUE
            if (target >= hardwareMax) store.clearOwnedMax(policyId)
            else store.setOwnedMax(policyId, target)
        }
        if (changes.isNotEmpty()) {
            val releaseOnly = writes.all { (policyId, target) ->
                target >= (clusterById[policyId]?.hardwareMaxKHz ?: Long.MAX_VALUE)
            }
            eventStore?.append(
                if (releaseOnly) CpuPolicyEventType.CAP_RELEASE else CpuPolicyEventType.CAP_WRITE,
                "${mode.name}/${stage.name}: ${changes.joinToString()}",
            )
            auditStore?.record(
                source = auditSource,
                feature = "performance",
                action = if (releaseOnly) "release_cap" else "apply_cap",
                target = changes.joinToString(),
                before = snapshot.cpuClusters.joinToString { "p${it.policyId}=${it.scalingMaxKHz}" },
                after = writes.entries.joinToString { "p${it.key}=${it.value}" },
                success = true,
                rollbackHint = "在性能页使用“释放 Root Tools cap”或切换 Auto",
            )
        }
        return AppliedCpuPolicy(mode, stage, desiredByPolicy)
    }

    suspend fun releaseOwnedCaps(snapshot: DeviceSnapshot): Boolean {
        if (!snapshot.rootAvailable || snapshot.cpuClusters.isEmpty()) return false
        val safeToRaise = snapshot.thermalStatus == 0 && snapshot.thermalStage() == ThermalStage.NORMAL
        val writes = linkedMapOf<Int, Long>()
        var ownershipTouched = false
        snapshot.cpuClusters.forEach { cluster ->
            val owned = store.ownedMax(cluster.policyId)
            if (owned <= 0L) return@forEach
            if (cluster.scalingMaxKHz != owned) {
                store.clearOwnedMax(cluster.policyId)
                ownershipTouched = true
                return@forEach
            }
            if (safeToRaise) writes[cluster.policyId] = cluster.hardwareMaxKHz
        }
        if (writes.isEmpty()) {
            if (ownershipTouched) eventStore?.append(CpuPolicyEventType.OWNERSHIP, "清理失效 owned cap，不抬频")
            return safeToRaise
        }
        val script = buildString {
            append("set -e\n")
            writes.forEach { (policyId, target) ->
                append("echo $target > /sys/devices/system/cpu/cpufreq/policy$policyId/scaling_max_freq\n")
            }
        }
        val result = shell.execute(script, timeoutSeconds = 4)
        if (!result.success) return false
        writes.keys.forEach(store::clearOwnedMax)
        val summary = writes.entries.joinToString { "p${it.key}→${it.value}" }
        eventStore?.append(CpuPolicyEventType.CAP_RELEASE, "手动释放：$summary")
        auditStore?.record(
            source = auditSource,
            feature = "performance",
            action = "manual_release_owned_caps",
            target = summary,
            before = snapshot.cpuClusters.joinToString { "p${it.policyId}=${it.scalingMaxKHz}" },
            after = summary,
            success = true,
            rollbackHint = "重新选择 Cool/Auto 热阶段时会按策略重新限峰",
        )
        return true
    }

    /**
     * One-time migration for builds that predate owned-cap tracking.
     *
     * The migration never raises frequency by itself. It only seeds ownership when a cool,
     * Thermal=0 device is still sitting exactly on one of the old Root Tools cap frequencies.
     * The normal ownership decision can then release that exact cap safely.
     */
    suspend fun migrateLegacyCapsIfNeeded(snapshot: DeviceSnapshot): Boolean {
        if (store.policySchemaVersion >= POLICY_SCHEMA_VERSION) return false
        if (!store.hasLegacyBaseline()) {
            store.policySchemaVersion = POLICY_SCHEMA_VERSION
            return false
        }

        val safeToInspect = snapshot.rootAvailable &&
            snapshot.thermalStatus == 0 &&
            (snapshot.skinTempC ?: Float.MAX_VALUE) < LEGACY_RESTORE_SKIN_C &&
            snapshot.cpuClusters.isNotEmpty()
        if (!safeToInspect) return false

        var seeded = false
        snapshot.cpuClusters.forEachIndexed { index, cluster ->
            val legacyTargets = listOf(ThermalStage.WARM, ThermalStage.MODERATE, ThermalStage.SEVERE)
                .map { stage -> targetFor(cluster, index, snapshot.cpuClusters.lastIndex, PerformanceMode.AUTO, stage) }
                .filter { it in 1 until cluster.hardwareMaxKHz }
                .toSet()
            if (cluster.scalingMaxKHz in legacyTargets) {
                store.setOwnedMax(cluster.policyId, cluster.scalingMaxKHz)
                eventStore?.append(
                    CpuPolicyEventType.OWNERSHIP,
                    "迁移旧 cap：p${cluster.policyId}=${cluster.scalingMaxKHz}",
                )
                seeded = true
            }
        }
        store.policySchemaVersion = POLICY_SCHEMA_VERSION
        return seeded
    }

    private fun targetFor(
        cluster: CpuCluster,
        index: Int,
        lastIndex: Int,
        mode: PerformanceMode,
        stage: ThermalStage,
    ): Long {
        val isLittle = index == 0
        val isPrime = index == lastIndex && lastIndex >= 2
        val ratio = when (stage) {
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
        val effectiveRatio = if (mode == PerformanceMode.PERFORMANCE && stage == ThermalStage.NORMAL) 1.0 else ratio
        return nearestFrequency(cluster, cluster.hardwareMaxKHz * effectiveRatio)
    }

    private fun nearestFrequency(cluster: CpuCluster, target: Double): Long {
        val candidates = (cluster.availableKHz + cluster.hardwareMaxKHz)
            .filter { it > 0L }
            .distinct()
            .sorted()
        if (candidates.isEmpty()) return target.roundToLong()
        return candidates.lastOrNull { it <= target.roundToLong() } ?: candidates.first()
    }

    private companion object {
        const val POLICY_SCHEMA_VERSION = 2
        const val LEGACY_RESTORE_SKIN_C = 35.5f
    }
}

