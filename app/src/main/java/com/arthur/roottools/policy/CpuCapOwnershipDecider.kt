package com.arthur.roottools.policy

data class CpuCapDecision(
    val writeTargetKHz: Long? = null,
    val clearPreviousOwnership: Boolean = false,
)

/** Pure ownership decision for one cpufreq policy. */
object CpuCapOwnershipDecider {
    fun decide(
        currentKHz: Long,
        desiredKHz: Long,
        hardwareMaxKHz: Long,
        thermalStatus: Int,
        ownedKHz: Long,
    ): CpuCapDecision {
        val externalLifted = ownedKHz > 0L && currentKHz > ownedKHz
        val effectiveOwned = if (externalLifted) 0L else ownedKHz

        return when {
            desiredKHz < currentKHz -> CpuCapDecision(
                writeTargetKHz = desiredKHz,
                clearPreviousOwnership = externalLifted,
            )

            desiredKHz > currentKHz &&
                thermalStatus == 0 &&
                effectiveOwned > 0L &&
                currentKHz == effectiveOwned -> CpuCapDecision(writeTargetKHz = desiredKHz)

            desiredKHz == currentKHz &&
                desiredKHz >= hardwareMaxKHz &&
                effectiveOwned > 0L -> CpuCapDecision(clearPreviousOwnership = true)

            else -> CpuCapDecision(clearPreviousOwnership = externalLifted)
        }
    }
}
