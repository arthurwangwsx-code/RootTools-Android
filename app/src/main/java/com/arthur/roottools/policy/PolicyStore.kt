package com.arthur.roottools.policy

import android.content.Context
import androidx.core.content.edit
import com.arthur.roottools.model.CpuCluster
import com.arthur.roottools.model.PerformanceMode

class PolicyStore(context: Context) {
    private val prefs = context.getSharedPreferences("root_tools_policy", Context.MODE_PRIVATE)

    var mode: PerformanceMode
        get() = runCatching {
            PerformanceMode.valueOf(prefs.getString(KEY_MODE, PerformanceMode.AUTO.name)!!)
        }.getOrDefault(PerformanceMode.AUTO)
        set(value) = prefs.edit { putString(KEY_MODE, value.name) }

    var performanceUntilMs: Long
        get() = prefs.getLong(KEY_PERF_UNTIL, 0L)
        set(value) = prefs.edit { putLong(KEY_PERF_UNTIL, value) }

    fun ensureBaseline(clusters: List<CpuCluster>) {
        prefs.edit {
            clusters.forEach { cluster ->
                val key = baselineMinKey(cluster.policyId)
                if (!prefs.contains(key)) {
                    putLong(key, cluster.scalingMinKHz)
                }
            }
        }
    }

    fun baselineMin(policyId: Int, fallback: Long): Long =
        prefs.getLong(baselineMinKey(policyId), fallback)

    var policySchemaVersion: Int
        get() = prefs.getInt(KEY_POLICY_SCHEMA, 0)
        set(value) = prefs.edit { putInt(KEY_POLICY_SCHEMA, value) }

    var buildFingerprint: String
        get() = prefs.getString(KEY_BUILD_FINGERPRINT, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_BUILD_FINGERPRINT, value) }

    fun hasLegacyBaseline(): Boolean = prefs.all.keys.any { it.startsWith("baseline_min_") }

    fun ownedMax(policyId: Int): Long = prefs.getLong(ownedMaxKey(policyId), 0L)

    fun setOwnedMax(policyId: Int, value: Long) {
        prefs.edit { putLong(ownedMaxKey(policyId), value) }
    }

    fun clearOwnedMax(policyId: Int) {
        prefs.edit { remove(ownedMaxKey(policyId)) }
    }

    fun clearAllOwnedMax() {
        prefs.edit {
            prefs.all.keys.filter { it.startsWith("owned_max_") }.forEach(::remove)
        }
    }

    fun clearBaselines() {
        prefs.edit {
            prefs.all.keys.filter { it.startsWith("baseline_min_") }.forEach(::remove)
        }
    }

    private fun baselineMinKey(policyId: Int) = "baseline_min_$policyId"
    private fun ownedMaxKey(policyId: Int) = "owned_max_$policyId"

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_PERF_UNTIL = "performance_until_ms"
        const val KEY_POLICY_SCHEMA = "policy_schema_version"
        const val KEY_BUILD_FINGERPRINT = "build_fingerprint"
    }
}

