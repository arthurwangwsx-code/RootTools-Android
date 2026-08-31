package com.arthur.roottools.feature.network.tailscale.data

import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleSnapshot
import com.arthur.roottools.root.RootShell

class RootTailscaleRepository(
    private val shell: RootShell,
    private val rootAvailable: () -> Boolean = { true },
) {
    suspend fun read(): RootTailscaleSnapshot {
        if (!rootAvailable()) return RootTailscaleSnapshot(collectedAtMs = System.currentTimeMillis())
        val result = shell.execute(PROBE_COMMAND, timeoutSeconds = 10)
        if (!result.success && result.output.isBlank()) {
            return RootTailscaleSnapshot(rootAvailable = true, collectedAtMs = System.currentTimeMillis())
        }
        return RootTailscaleProbeParser.parse(
            raw = result.output,
            rootAvailable = true,
            collectedAtMs = System.currentTimeMillis(),
        )
    }

    internal companion object {
        val PROBE_COMMAND = """
            BASE=${RootTailscaleRuntimeSpec.BASE_DIR}
            BIN=${'$'}BASE/bin
            RUN=${'$'}BASE/run
            SOCK=${'$'}RUN/tailscaled.sock
            TS=${'$'}BIN/tailscale
            TSD=${'$'}BIN/tailscaled

            if [ -x "${'$'}TS" ] && [ -x "${'$'}TSD" ]; then echo RUNTIME_INSTALLED=1; else echo RUNTIME_INSTALLED=0; fi
            if [ -x "${'$'}TS" ]; then echo "VERSION=${'$'}("${'$'}TS" version 2>/dev/null | head -n 1 | tr -d '\r')"; else echo VERSION=; fi
            managed_daemon=0
            for pid in ${'$'}(pidof tailscaled 2>/dev/null); do
              if tr '\0' ' ' < "/proc/${'$'}pid/cmdline" 2>/dev/null | grep -Fq "${'$'}TSD"; then managed_daemon=1; break; fi
            done
            echo "DAEMON_RUNNING=${'$'}managed_daemon"
            if [ -S "${'$'}SOCK" ]; then echo SOCKET_READY=1; else echo SOCKET_READY=0; fi
            if [ -s "${'$'}BASE/state/tailscaled.state" ]; then echo STATE_PRESENT=1; else echo STATE_PRESENT=0; fi
            if [ -f ${RootTailscaleRuntimeSpec.IDENTITY_MARKER} ]; then echo IDENTITY_SAVED=1; else echo IDENTITY_SAVED=0; fi
            if ip link show tailscale0 >/dev/null 2>&1; then echo TAILSCALE0=1; else echo TAILSCALE0=0; fi
            if [ -S "${'$'}SOCK" ] && [ -x "${'$'}TS" ]; then
              echo "TAILNET_IP=${'$'}("${'$'}TS" --socket="${'$'}SOCK" ip -4 2>/dev/null | head -n 1 | tr -d '\r')"
              status_out="${'$'}("${'$'}TS" --socket="${'$'}SOCK" status 2>/dev/null || true)"
              echo "AUTH_URL=${'$'}(printf '%s\n' "${'$'}status_out" | sed -n 's/^Log in at: //p' | head -n 1)"
              status_json="${'$'}("${'$'}TS" --socket="${'$'}SOCK" status --peers=false --json 2>/dev/null || true)"
              backend_state="${'$'}(printf '%s\n' "${'$'}status_json" | sed -n 's/.*"BackendState"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
              echo "BACKEND_STATE=${'$'}backend_state"
              if [ "${'$'}backend_state" = "Running" ]; then echo BACKEND_ONLINE=1; else echo BACKEND_ONLINE=0; fi
              serve_out="${'$'}("${'$'}TS" --socket="${'$'}SOCK" serve status 2>/dev/null || true)"
              if printf '%s\n' "${'$'}serve_out" | grep -Fq 'tcp://127.0.0.1:5555'; then echo SERVE_ADB=1; else echo SERVE_ADB=0; fi
              if printf '%s\n' "${'$'}serve_out" | grep -Fq 'tcp://127.0.0.1:8765'; then echo SERVE_MCP=1; else echo SERVE_MCP=0; fi
            else
              echo TAILNET_IP=
              echo AUTH_URL=
              echo BACKEND_STATE=
              echo BACKEND_ONLINE=0
              echo SERVE_ADB=0
              echo SERVE_MCP=0
            fi
            if ip route get 100.100.100.100 2>/dev/null | grep -q 'dev tailscale0'; then
              echo ROUTE_READY=1
            else
              echo ROUTE_READY=0
            fi
            if [ -x ${RootTailscaleRuntimeSpec.BOOT_SCRIPT} ]; then echo BOOT_ENABLED=1; else echo BOOT_ENABLED=0; fi
            if ss -ltnp 2>/dev/null | grep 'adbd' | awk '{print ${'$'}4}' | grep -Eq '(^|:|\])5555${'$'}'; then echo ADB_5555=1; else echo ADB_5555=0; fi
            if ss -ltnp 2>/dev/null | awk '{print ${'$'}4}' | grep -Eq '(^|:|\])8765${'$'}'; then echo MCP_8765=1; else echo MCP_8765=0; fi
            connectivity_out="${'$'}(dumpsys connectivity 2>/dev/null || true)"
            if printf '%s\n' "${'$'}connectivity_out" | grep -Eq 'TRANSPORT_VPN|VPN CONNECTED|type: VPN'; then echo VPN_ACTIVE=1; else echo VPN_ACTIVE=0; fi
            vpn_owner="${'$'}(printf '%s\n' "${'$'}connectivity_out" | sed -n 's/.*extra: VPN:\([^} ]*\).*/\1/p' | tail -n 1)"
            echo "VPN_OWNER=${'$'}vpn_owner"
            if pm path ${RootTailscaleRuntimeSpec.OFFICIAL_PACKAGE} >/dev/null 2>&1; then echo OFFICIAL_APP_INSTALLED=1; else echo OFFICIAL_APP_INSTALLED=0; fi
            if pm path ${RootTailscaleRuntimeSpec.HIDDIFY_PACKAGE} >/dev/null 2>&1; then echo HIDDIFY_INSTALLED=1; else echo HIDDIFY_INSTALLED=0; fi
        """.trimIndent()
    }
}
