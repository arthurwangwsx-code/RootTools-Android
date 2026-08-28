package com.arthur.roottools.feature.network.tailscale.data

import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleSnapshot
import com.arthur.roottools.feature.network.tailscale.policy.RootTailscalePolicy

object RootTailscaleProbeParser {
    fun parse(raw: String, rootAvailable: Boolean, collectedAtMs: Long): RootTailscaleSnapshot {
        val values = linkedMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            if (key.matches(KEY_PATTERN)) values[key] = value
        }
        val ip = values["TAILNET_IP"]?.takeIf(RootTailscalePolicy::isTailnetIpv4)
        val authUrl = values["AUTH_URL"]?.takeIf { it.matches(AUTH_URL_PATTERN) }
        return RootTailscaleSnapshot(
            rootAvailable = rootAvailable,
            runtimeInstalled = values.bool("RUNTIME_INSTALLED"),
            runtimeVersion = values["VERSION"]?.takeIf(String::isNotBlank),
            daemonRunning = values.bool("DAEMON_RUNNING"),
            socketReady = values.bool("SOCKET_READY"),
            statePresent = values.bool("STATE_PRESENT"),
            tailscale0Present = values.bool("TAILSCALE0"),
            tailnetIpv4 = ip,
            authUrl = authUrl,
            routeReady = values.bool("ROUTE_READY"),
            bootEnabled = values.bool("BOOT_ENABLED"),
            adb5555Listening = values.bool("ADB_5555"),
            androidVpnOwner = values["VPN_OWNER"]?.takeIf(String::isNotBlank),
            officialAppInstalled = values.bool("OFFICIAL_APP_INSTALLED"),
            hiddifyInstalled = values.bool("HIDDIFY_INSTALLED"),
            collectedAtMs = collectedAtMs,
        )
    }

    private fun Map<String, String>.bool(key: String): Boolean = this[key] == "1"

    private val KEY_PATTERN = Regex("[A-Z0-9_]+")
    private val AUTH_URL_PATTERN = Regex("https://login\\.tailscale\\.com/a/[A-Za-z0-9]+")
}

