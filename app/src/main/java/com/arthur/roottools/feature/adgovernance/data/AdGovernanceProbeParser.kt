package com.arthur.roottools.feature.adgovernance.data

import com.arthur.roottools.feature.adgovernance.model.AdActionEvent
import com.arthur.roottools.feature.adgovernance.model.AdAwayGovernanceState
import com.arthur.roottools.feature.adgovernance.model.AdGovernanceSnapshot
import com.arthur.roottools.feature.adgovernance.model.GkdGovernanceState
import com.arthur.roottools.feature.adgovernance.model.HostsGovernanceState
import com.arthur.roottools.feature.adgovernance.model.HyperOsAdsState
import com.arthur.roottools.feature.adgovernance.model.TailscaleGovernanceState

internal object AdGovernanceProbeParser {
    private val keyValuePattern = Regex("^([A-Z][A-Z0-9_]+)=(.*)$")
    private val appPattern = Regex("TopActivity\\(appId=([^,\\s)]+)")
    private val actionStartPattern = Regex("^(\\d{2}:\\d{2}:\\d{2}\\.\\d+).*addActionLog")
    private val groupPattern = Regex("gName:([^,]+)")
    private val automatorEnabledPattern = Regex("\"enableAutomator\"\\s*:\\s*true")
    private val automatorModePattern = Regex("\"automatorMode\"\\s*:\\s*(\\d+)")

    fun parse(raw: String): AdGovernanceSnapshot {
        val values = raw.lineSequence().mapNotNull { line ->
            keyValuePattern.matchEntire(line.trim())?.destructured?.let { (key, value) -> key to value.trim() }
        }.toMap()
        val store = section(raw, "__GKD_STORE_BEGIN__", "__GKD_STORE_END__")
        val log = section(raw, "__GKD_LOG_BEGIN__", "__GKD_LOG_END__")

        val gkdInstalled = values.flag("PKG_GKD")
        val gkdRunning = values.flag("GKD_RUNNING")
        val userServiceRunning = values.flag("GKD_USER_SERVICE")
        val shizukuServerRunning = values.flag("SHIZUKU_SERVER")
        val automatorEnabled = automatorEnabledPattern.containsMatchIn(store)
        val automatorMode = automatorModePattern.find(store)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val engineReady = when (automatorMode) {
            1 -> gkdInstalled && gkdRunning && automatorEnabled
            2 -> gkdInstalled && gkdRunning && automatorEnabled && userServiceRunning && shizukuServerRunning
            else -> false
        }

        return AdGovernanceSnapshot(
            rootAvailable = values["ROOT_UID"] == "0",
            gkd = GkdGovernanceState(
                installed = gkdInstalled,
                running = gkdRunning,
                userServiceRunning = userServiceRunning,
                shizukuServerRunning = shizukuServerRunning,
                automatorEnabled = automatorEnabled,
                automatorMode = automatorMode,
                engineReady = engineReady,
                subscriptionCount = values.int("GKD_SUBSCRIPTIONS"),
            ),
            adAway = AdAwayGovernanceState(
                installed = values.flag("PKG_ADAWAY"),
                running = values.flag("ADAWAY_RUNNING"),
            ),
            hosts = HostsGovernanceState(
                lineCount = values.int("HOSTS_LINES"),
                systemless = values.flag("HOSTS_SYSTEMLESS"),
            ),
            tailscale = TailscaleGovernanceState(
                active = values.flag("TAILSCALE_ACTIVE"),
                ipv4 = values["TAILSCALE_IPV4"]?.takeIf { it.isNotBlank() },
            ),
            hyperOs = HyperOsAdsState(
                systemAdInstalled = values.flag("PKG_HYPER_ADS"),
                systemAdEnabled = values.flag("HYPER_ADS_ENABLED"),
                analyticsInstalled = values.flag("PKG_MIUI_ANALYTICS"),
                analyticsEnabled = values.flag("MIUI_ANALYTICS_ENABLED"),
            ),
            recentActions = parseActions(log),
        )
    }

    private fun parseActions(log: String): List<AdActionEvent> {
        var currentAppId: String? = null
        var pendingTime: String? = null
        val result = mutableListOf<AdActionEvent>()

        log.lineSequence().forEach { line ->
            appPattern.find(line)?.groupValues?.getOrNull(1)?.let { currentAppId = it }
            actionStartPattern.find(line)?.groupValues?.getOrNull(1)?.let { pendingTime = it }
            val time = pendingTime
            if (time != null) {
                val groupName = groupPattern.find(line)?.groupValues?.getOrNull(1)?.trim()
                val appId = currentAppId
                if (!groupName.isNullOrEmpty() && !appId.isNullOrEmpty()) {
                    result += AdActionEvent(time = time, appId = appId, groupName = groupName)
                    pendingTime = null
                }
            }
        }
        return result.takeLast(MAX_ACTIONS)
    }

    private fun section(raw: String, start: String, end: String): String {
        val startIndex = raw.indexOf(start)
        if (startIndex < 0) return ""
        val contentStart = startIndex + start.length
        val endIndex = raw.indexOf(end, startIndex = contentStart)
        if (endIndex < 0) return ""
        return raw.substring(contentStart, endIndex).trim()
    }

    private fun Map<String, String>.flag(key: String): Boolean = get(key) == "1"
    private fun Map<String, String>.int(key: String): Int = get(key)?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    private const val MAX_ACTIONS = 50
}
