package com.arthur.roottools.feature.network.tailscale.policy

import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleDecision
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleHealth
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleMode
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleSnapshot
import java.util.Locale

object RootTailscalePolicy {
    fun decide(snapshot: RootTailscaleSnapshot): RootTailscaleDecision {
        val health = when {
            !snapshot.rootAvailable || !snapshot.runtimeInstalled -> RootTailscaleHealth.RUNTIME_MISSING
            snapshot.mode == RootTailscaleMode.KERNEL_TUN && snapshot.authenticated && snapshot.routeReady -> RootTailscaleHealth.READY
            snapshot.mode == RootTailscaleMode.KERNEL_TUN && snapshot.authenticated -> RootTailscaleHealth.DEGRADED
            snapshot.daemonRunning && !snapshot.authenticated -> RootTailscaleHealth.NEEDS_LOGIN
            else -> RootTailscaleHealth.STOPPED
        }
        val rootOverlayReady = health == RootTailscaleHealth.READY
        return RootTailscaleDecision(
            health = health,
            canInstallRuntime = snapshot.rootAvailable,
            canBeginAuthentication = snapshot.rootAvailable && snapshot.runtimeInstalled && !snapshot.authenticated && !snapshot.hasSavedIdentity,
            canEnableRootOverlay = snapshot.rootAvailable && snapshot.runtimeInstalled && (snapshot.authenticated || snapshot.hasSavedIdentity) && !rootOverlayReady,
            canDisableRootOverlay = snapshot.daemonRunning,
            canRepair = snapshot.rootAvailable && snapshot.runtimeInstalled && snapshot.authenticated,
            canStopOfficialApp = rootOverlayReady && snapshot.officialVpnActive,
            coexistenceReady = rootOverlayReady && !snapshot.officialVpnActive,
        )
    }

    fun isTailnetIpv4(value: String?): Boolean {
        val parts = value?.trim()?.split('.') ?: return false
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return octets[0] == 100 && octets[1] in 64..127
    }

    fun normalizeHostname(raw: String): String {
        val normalized = raw
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9-]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(63)
            .trimEnd('-')
        return normalized.ifBlank { "roottools-android" }
    }
}

