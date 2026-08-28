package com.arthur.roottools.model

enum class CpuCapSource(val displayName: String) {
    FULL("Full"),
    ROOT_TOOLS("Root Tools"),
    SAMSUNG_THERMAL("Samsung Thermal"),
    OTHER_SYSTEM("Other System"),
}

data class CpuCapState(
    val policyId: Int,
    val source: CpuCapSource,
    val currentMaxKHz: Long,
    val hardwareMaxKHz: Long,
    val ownedMaxKHz: Long = 0L,
)

enum class CpuPolicyEventType {
    MODE,
    CAP_WRITE,
    CAP_RELEASE,
    OWNERSHIP,
    COMPATIBILITY,
}

data class CpuPolicyEvent(
    val timestampMs: Long,
    val type: CpuPolicyEventType,
    val message: String,
)
