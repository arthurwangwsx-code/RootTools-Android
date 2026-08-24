package com.arthur.roottools.feature.adgovernance.model

data class AdActionEvent(
    val time: String,
    val appId: String,
    val groupName: String,
)

data class GkdGovernanceState(
    val installed: Boolean = false,
    val running: Boolean = false,
    val userServiceRunning: Boolean = false,
    val shizukuServerRunning: Boolean = false,
    val automatorEnabled: Boolean = false,
    val automatorMode: Int = 0,
    val engineReady: Boolean = false,
    val subscriptionCount: Int = 0,
)

data class AdAwayGovernanceState(
    val installed: Boolean = false,
    val running: Boolean = false,
)

data class HostsGovernanceState(
    val lineCount: Int = 0,
    val systemless: Boolean = false,
) {
    val active: Boolean get() = lineCount > 2 || systemless
}

data class TailscaleGovernanceState(
    val active: Boolean = false,
    val ipv4: String? = null,
)

data class HyperOsAdsState(
    val systemAdInstalled: Boolean = false,
    val systemAdEnabled: Boolean = false,
    val analyticsInstalled: Boolean = false,
    val analyticsEnabled: Boolean = false,
)

data class AdGovernanceSnapshot(
    val rootAvailable: Boolean = false,
    val gkd: GkdGovernanceState = GkdGovernanceState(),
    val adAway: AdAwayGovernanceState = AdAwayGovernanceState(),
    val hosts: HostsGovernanceState = HostsGovernanceState(),
    val tailscale: TailscaleGovernanceState = TailscaleGovernanceState(),
    val hyperOs: HyperOsAdsState = HyperOsAdsState(),
    val recentActions: List<AdActionEvent> = emptyList(),
    val probeError: String? = null,
) {
    fun actionCountFor(appId: String): Int = recentActions.count { it.appId == appId }

    fun latestActionFor(appId: String): AdActionEvent? = recentActions.lastOrNull { it.appId == appId }
}
