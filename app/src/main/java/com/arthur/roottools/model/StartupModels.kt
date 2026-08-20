package com.arthur.roottools.model

enum class AppPolicyCategory(val displayName: String) {
    PROTECTED("Keep Alive"),
    FREEZE("Freeze"),
    ON_DEMAND("On Demand"),
    RARE("Rare"),
    NORMAL("Normal"),
}

data class StartupAppRecord(
    val packageName: String,
    val label: String = packageName,
    val firstStartSeconds: Long? = null,
    val startCount: Int = 0,
    val startReasons: Set<String> = emptySet(),
    val bootReceiverCount: Int = 0,
    val running: Boolean = false,
    val disabled: Boolean = false,
    val standbyBucket: Int? = null,
    val category: AppPolicyCategory = AppPolicyCategory.NORMAL,
) {
    val startupRiskScore: Int
        get() = startCount * 3 + bootReceiverCount * 2 + if (running) 2 else 0 + when (category) {
            AppPolicyCategory.FREEZE -> 8
            AppPolicyCategory.ON_DEMAND -> 4
            AppPolicyCategory.RARE -> 2
            else -> 0
        }
}

data class StartupBucketSummary(
    val label: String,
    val appCount: Int,
    val processStarts: Int,
)

data class StartupAnalysis(
    val bootUptimeSeconds: Long = 0,
    val apps: List<StartupAppRecord> = emptyList(),
    val buckets: List<StartupBucketSummary> = emptyList(),
    val appiumTestMode: Boolean = false,
    val degradedMode: Boolean = false,
    val source: String = "Root event trace",
    val analyzedAtMs: Long = System.currentTimeMillis(),
) {
    val startedApps: Int get() = apps.count { it.startCount > 0 }
    val bootCapableApps: Int get() = apps.count { it.bootReceiverCount > 0 }
    val runningApps: Int get() = apps.count { it.running }
    val frozenApps: Int get() = apps.count { it.disabled }
}

data class PackageActionResult(
    val success: Boolean,
    val message: String,
)
