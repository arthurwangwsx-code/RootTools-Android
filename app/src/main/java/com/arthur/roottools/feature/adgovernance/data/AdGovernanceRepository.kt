package com.arthur.roottools.feature.adgovernance.data

import com.arthur.roottools.feature.adgovernance.model.AdGovernanceSnapshot
import com.arthur.roottools.root.RootShell

class AdGovernanceRepository(
    private val shell: RootShell,
) {
    suspend fun read(): AdGovernanceSnapshot {
        val result = shell.execute(PROBE_COMMAND, timeoutSeconds = 10)
        val snapshot = AdGovernanceProbeParser.parse(result.output)
        return if (result.success) snapshot else snapshot.copy(
            probeError = result.output.ifBlank { "exit=${result.exitCode}" }.take(240),
        )
    }

    private companion object {
        val PROBE_COMMAND = """
            probe_pkg() {
              if pm path "$1" >/dev/null 2>&1; then echo 1; else echo 0; fi
            }
            probe_disabled() {
              pm list packages -d 2>/dev/null | grep -Fqx "package:$1"
            }

            ROOT_UID=$(id -u 2>/dev/null || echo -1)
            echo "ROOT_UID=${'$'}ROOT_UID"

            PKG_GKD=$(probe_pkg li.songe.gkd)
            PKG_ADAWAY=$(probe_pkg org.adaway)
            PKG_HYPER_ADS=$(probe_pkg com.miui.systemAdSolution)
            PKG_MIUI_ANALYTICS=$(probe_pkg com.miui.analytics)
            echo "PKG_GKD=${'$'}PKG_GKD"
            echo "PKG_ADAWAY=${'$'}PKG_ADAWAY"
            echo "PKG_HYPER_ADS=${'$'}PKG_HYPER_ADS"
            echo "PKG_MIUI_ANALYTICS=${'$'}PKG_MIUI_ANALYTICS"

            if pidof li.songe.gkd >/dev/null 2>&1; then echo 'GKD_RUNNING=1'; else echo 'GKD_RUNNING=0'; fi
            if pidof 'li.songe.gkd:shizuku-user-service' >/dev/null 2>&1; then echo 'GKD_USER_SERVICE=1'; else echo 'GKD_USER_SERVICE=0'; fi
            if pidof shizuku_server >/dev/null 2>&1; then echo 'SHIZUKU_SERVER=1'; else echo 'SHIZUKU_SERVER=0'; fi
            if pidof org.adaway >/dev/null 2>&1; then echo 'ADAWAY_RUNNING=1'; else echo 'ADAWAY_RUNNING=0'; fi

            if [ "${'$'}PKG_HYPER_ADS" = 1 ] && ! probe_disabled com.miui.systemAdSolution; then echo 'HYPER_ADS_ENABLED=1'; else echo 'HYPER_ADS_ENABLED=0'; fi
            if [ "${'$'}PKG_MIUI_ANALYTICS" = 1 ] && ! probe_disabled com.miui.analytics; then echo 'MIUI_ANALYTICS_ENABLED=1'; else echo 'MIUI_ANALYTICS_ENABLED=0'; fi

            HOSTS_LINES=$(wc -l </system/etc/hosts 2>/dev/null | tr -d ' ')
            echo "HOSTS_LINES=${'$'}{HOSTS_LINES:-0}"
            if grep -E ' /system/etc/hosts ' /proc/mounts 2>/dev/null | grep -q '/data/adb'; then echo 'HOSTS_SYSTEMLESS=1'; else echo 'HOSTS_SYSTEMLESS=0'; fi

            # Carrier mobile interfaces also commonly use 100.64.0.0/10 CGNAT addresses.
            # Only accept a 100.x address from a tun* interface for the Tailscale endpoint.
            TAILSCALE_IPV4=$(ip -4 -o addr show 2>/dev/null | awk '${'$'}2 ~ /^tun[0-9]+${'$'}/ && ${'$'}4 ~ /^100\./ { split(${'$'}4,a,"/"); print a[1]; exit }')
            echo "TAILSCALE_IPV4=${'$'}TAILSCALE_IPV4"
            if [ -n "${'$'}TAILSCALE_IPV4" ] || pidof com.tailscale.ipn >/dev/null 2>&1; then echo 'TAILSCALE_ACTIVE=1'; else echo 'TAILSCALE_ACTIVE=0'; fi

            GKD_BASE=/storage/emulated/0/Android/data/li.songe.gkd/files
            GKD_STORE="${'$'}GKD_BASE/store/store.json"
            GKD_SUBS_DIR="${'$'}GKD_BASE/subscription"
            GKD_SUBSCRIPTIONS=$(find "${'$'}GKD_SUBS_DIR" -maxdepth 1 -type f -name '[0-9]*.json' 2>/dev/null | wc -l | tr -d ' ')
            echo "GKD_SUBSCRIPTIONS=${'$'}{GKD_SUBSCRIPTIONS:-0}"
            echo '__GKD_STORE_BEGIN__'
            [ -r "${'$'}GKD_STORE" ] && cat "${'$'}GKD_STORE"
            echo '__GKD_STORE_END__'

            GKD_LOG=$(ls -1t "${'$'}GKD_BASE"/log/*.log 2>/dev/null | head -n 1)
            echo '__GKD_LOG_BEGIN__'
            [ -n "${'$'}GKD_LOG" ] && tail -n 1200 "${'$'}GKD_LOG"
            echo '__GKD_LOG_END__'
        """.trimIndent()
    }
}
