package com.arthur.roottools.policy

import com.arthur.roottools.model.CpuCapSource
import com.arthur.roottools.model.CpuCapState
import com.arthur.roottools.model.DeviceSnapshot

class CpuPolicyInspector(private val store: PolicyStore) {
    fun inspect(snapshot: DeviceSnapshot): List<CpuCapState> = snapshot.cpuClusters.map { cluster ->
        val owned = store.ownedMax(cluster.policyId)
        val source = when {
            cluster.scalingMaxKHz >= cluster.hardwareMaxKHz -> CpuCapSource.FULL
            owned > 0L && cluster.scalingMaxKHz == owned -> CpuCapSource.ROOT_TOOLS
            snapshot.thermalStatus > 0 -> CpuCapSource.SAMSUNG_THERMAL
            else -> CpuCapSource.OTHER_SYSTEM
        }
        CpuCapState(
            policyId = cluster.policyId,
            source = source,
            currentMaxKHz = cluster.scalingMaxKHz,
            hardwareMaxKHz = cluster.hardwareMaxKHz,
            ownedMaxKHz = owned,
        )
    }
}
