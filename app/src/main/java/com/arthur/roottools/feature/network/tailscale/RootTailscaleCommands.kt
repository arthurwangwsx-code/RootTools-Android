package com.arthur.roottools.feature.network.tailscale

import com.arthur.roottools.feature.network.tailscale.data.RootTailscaleRuntimeInstaller
import com.arthur.roottools.feature.network.tailscale.data.RootTailscaleRuntimeSpec
import com.arthur.roottools.feature.network.tailscale.model.RootTailscaleMode

internal object RootTailscaleCommands {
    fun install(payload: RootTailscaleRuntimeInstaller.RuntimePayload): String {
        val tailscale = shellQuote(payload.tailscale.absolutePath)
        val tailscaled = shellQuote(payload.tailscaled.absolutePath)
        return """
            set -e
            BASE=${RootTailscaleRuntimeSpec.BASE_DIR}
            BIN=${'$'}BASE/bin
            RUN=${'$'}BASE/run
            STATE=${'$'}BASE/state
            ${secureRuntimeDirectories()}
            cp $tailscale "${'$'}BIN/tailscale.new"
            cp $tailscaled "${'$'}BIN/tailscaled.new"
            chmod 0755 "${'$'}BIN/tailscale.new" "${'$'}BIN/tailscaled.new"
            test "${'$'}("${'$'}BIN/tailscale.new" version 2>/dev/null | head -n 1)" = "${RootTailscaleRuntimeSpec.VERSION}"
            if [ -x "${'$'}BIN/tailscale" ]; then cp -f "${'$'}BIN/tailscale" "${'$'}BIN/tailscale.previous"; fi
            if [ -x "${'$'}BIN/tailscaled" ]; then cp -f "${'$'}BIN/tailscaled" "${'$'}BIN/tailscaled.previous"; fi
            mv -f "${'$'}BIN/tailscale.new" "${'$'}BIN/tailscale"
            mv -f "${'$'}BIN/tailscaled.new" "${'$'}BIN/tailscaled"
            chmod 0755 "${'$'}BIN/tailscale" "${'$'}BIN/tailscaled"
            "${'$'}BIN/tailscale" version | head -n 1
        """.trimIndent()
    }

    fun beginAuthentication(hostname: String): String = """
        ${runtimeVariables()}
        ${secureRuntimeDirectories()}
        ${stopManagedDaemon()}
        rm -f "${'$'}SOCK" "${'$'}RUN/auth.out"
        nohup "${'$'}TSD" --tun=userspace-networking --state="${'$'}STATE/tailscaled.state" --statedir="${'$'}STATE" --socket="${'$'}SOCK" > "${'$'}RUN/userspace.log" 2>&1 </dev/null &
        echo ${'$'}! > "${'$'}RUN/tailscaled.pid"
        ${waitForSocket(15, 20)}
        nohup "${'$'}TS" --socket="${'$'}SOCK" up --accept-dns=false --hostname=${shellQuote(hostname)} > "${'$'}RUN/auth.out" 2>&1 </dev/null &
        echo ${'$'}! > "${'$'}RUN/auth.pid"
        n=0
        while [ "${'$'}n" -lt 20 ]; do
          ip="${'$'}("${'$'}TS" --socket="${'$'}SOCK" ip -4 2>/dev/null | head -n 1)"
          backend="${'$'}("${'$'}TS" --socket="${'$'}SOCK" status --peers=false --json 2>/dev/null | sed -n 's/.*"BackendState"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
          url="${'$'}("${'$'}TS" --socket="${'$'}SOCK" status 2>/dev/null | sed -n 's/^Log in at: //p' | head -n 1)"
          [ -n "${'$'}url" ] || url="${'$'}(sed -n 's/.*\(https:\/\/login\.tailscale\.com\/a\/[A-Za-z0-9_-]*\).*/\1/p' "${'$'}RUN/auth.out" 2>/dev/null | head -n 1)"
          [ -n "${'$'}ip" ] && [ "${'$'}backend" = "Running" ] && { touch "${'$'}MARKER"; echo "TAILNET_IP=${'$'}ip"; exit 0; }
          if [ -n "${'$'}url" ]; then
            (
              w=0
              while [ "${'$'}w" -lt 300 ]; do
                saved_ip="${'$'}("${'$'}TS" --socket="${'$'}SOCK" ip -4 2>/dev/null | head -n 1)"
                saved_backend="${'$'}("${'$'}TS" --socket="${'$'}SOCK" status --peers=false --json 2>/dev/null | sed -n 's/.*"BackendState"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
                [ -n "${'$'}saved_ip" ] && [ "${'$'}saved_backend" = "Running" ] && { touch "${'$'}MARKER"; exit 0; }
                sleep 2
                w=${'$'}((w+1))
              done
              exit 1
            ) > "${'$'}RUN/auth-watch.log" 2>&1 &
            echo ${'$'}! > "${'$'}RUN/auth-watch.pid"
            echo "AUTH_URL=${'$'}url"
            exit 0
          fi
          sleep 1
          n=${'$'}((n+1))
        done
        tail -n 20 "${'$'}RUN/auth.out" 2>/dev/null
        exit 21
    """.trimIndent()

    fun enableUserspaceServe(hostname: String): String = """
        ${runtimeVariables()}
        ${secureRuntimeDirectories()}
        ${stopManagedDaemon()}
        ${cleanupLegacyRoutes()}
        rm -f "${'$'}SOCK"
        nohup "${'$'}TSD" --tun=userspace-networking --state="${'$'}STATE/tailscaled.state" --statedir="${'$'}STATE" --socket="${'$'}SOCK" > "${'$'}RUN/userspace.log" 2>&1 </dev/null &
        echo ${'$'}! > "${'$'}RUN/tailscaled.pid"
        ${waitForSocket(25, 22)}
        "${'$'}TS" --socket="${'$'}SOCK" set --accept-dns=false --hostname=${shellQuote(hostname)} >/dev/null 2>&1 || true
        ${waitForBackend(30, 23)}
        touch "${'$'}MARKER"
        ss -ltnp 2>/dev/null | grep 'adbd' | awk '{print ${'$'}4}' | grep -Eq '(^|:|\])5555${'$'}' || exit 24
        "${'$'}TS" --socket="${'$'}SOCK" serve --bg --tcp=5555 tcp://127.0.0.1:5555 > "${'$'}RUN/serve-adb.log" 2>&1 || { tail -n 20 "${'$'}RUN/serve-adb.log"; exit 25; }
        if ss -ltnp 2>/dev/null | awk '{print ${'$'}4}' | grep -Eq '(^|:|\])8765${'$'}'; then
          "${'$'}TS" --socket="${'$'}SOCK" serve --bg --tcp=8765 tcp://127.0.0.1:8765 > "${'$'}RUN/serve-mcp.log" 2>&1 || true
        fi
        serve_out="${'$'}("${'$'}TS" --socket="${'$'}SOCK" serve status 2>/dev/null || true)"
        printf '%s\n' "${'$'}serve_out" | grep -Fq 'tcp://127.0.0.1:5555' || exit 26
        echo "TAILNET_IP=${'$'}ip"
        echo SERVE_ADB=1
    """.trimIndent()

    fun enableKernel(hostname: String): String = """
        ${runtimeVariables()}
        ${secureRuntimeDirectories()}
        if [ -S "${'$'}SOCK" ]; then
          "${'$'}TS" --socket="${'$'}SOCK" serve --tcp=5555 off >/dev/null 2>&1 || true
          "${'$'}TS" --socket="${'$'}SOCK" serve --tcp=8765 off >/dev/null 2>&1 || true
        fi
        ${stopManagedDaemon()}
        ${cleanupLegacyRoutes()}
        rm -f "${'$'}SOCK"
        nohup "${'$'}TSD" --tun=tailscale0 --state="${'$'}STATE/tailscaled.state" --statedir="${'$'}STATE" --socket="${'$'}SOCK" > "${'$'}RUN/kernel.log" 2>&1 </dev/null &
        echo ${'$'}! > "${'$'}RUN/tailscaled.pid"
        ${waitForSocket(25, 30)}
        "${'$'}TS" --socket="${'$'}SOCK" set --accept-dns=false --hostname=${shellQuote(hostname)} >/dev/null 2>&1 || true
        ${waitForBackend(30, 31)}
        touch "${'$'}MARKER"
        n=0
        while [ "${'$'}n" -lt 20 ]; do
          ip link show tailscale0 >/dev/null 2>&1 && ip route get 100.100.100.100 2>/dev/null | grep -q 'dev tailscale0' && break
          sleep 1
          n=${'$'}((n+1))
        done
        ip link show tailscale0 >/dev/null 2>&1 || exit 32
        ip route get 100.100.100.100 2>/dev/null | grep -q 'dev tailscale0' || exit 33
        echo "TAILNET_IP=${'$'}ip"
        echo ROUTE_READY=1
    """.trimIndent()

    fun disable(): String = """
        ${runtimeVariables()}
        if [ -S "${'$'}SOCK" ]; then
          saved_ip="${'$'}("${'$'}TS" --socket="${'$'}SOCK" ip -4 2>/dev/null | head -n 1)"
          saved_backend="${'$'}("${'$'}TS" --socket="${'$'}SOCK" status --peers=false --json 2>/dev/null | sed -n 's/.*"BackendState"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
          [ -n "${'$'}saved_ip" ] && [ "${'$'}saved_backend" = "Running" ] && touch "${'$'}MARKER"
          "${'$'}TS" --socket="${'$'}SOCK" serve --tcp=5555 off >/dev/null 2>&1 || true
          "${'$'}TS" --socket="${'$'}SOCK" serve --tcp=8765 off >/dev/null 2>&1 || true
        fi
        ${stopManagedDaemon()}
        "${'$'}TSD" --cleanup --statedir="${'$'}STATE" >/dev/null 2>&1 || true
        ${cleanupLegacyRoutes()}
        ip link delete tailscale0 >/dev/null 2>&1 || true
        rm -f "${'$'}SOCK" "${'$'}RUN/tailscaled.pid" "${'$'}RUN/auth.pid" "${'$'}RUN/auth-watch.pid"
    """.trimIndent()

    fun enableBoot(mode: RootTailscaleMode, hostname: String): String {
        require(mode == RootTailscaleMode.USERSPACE_SERVE || mode == RootTailscaleMode.KERNEL_TUN)
        val script = bootScript(mode, hostname)
        return """
            mkdir -p /data/adb/service.d
            cat > ${RootTailscaleRuntimeSpec.BOOT_SCRIPT} <<'ROOTTOOLS_TAILSCALE_BOOT'
$script
ROOTTOOLS_TAILSCALE_BOOT
            chmod 0755 ${RootTailscaleRuntimeSpec.BOOT_SCRIPT}
        """.trimIndent()
    }

    fun disableBoot(): String = "rm -f ${RootTailscaleRuntimeSpec.BOOT_SCRIPT}"

    private fun bootScript(mode: RootTailscaleMode, hostname: String): String {
        val tun = if (mode == RootTailscaleMode.USERSPACE_SERVE) "userspace-networking" else "tailscale0"
        val postStart = if (mode == RootTailscaleMode.USERSPACE_SERVE) {
            """
              n=0
              while [ "${'$'}n" -lt 60 ]; do
                ss -ltnp 2>/dev/null | grep 'adbd' | awk '{print ${'$'}4}' | grep -Eq '(^|:|\])5555${'$'}' && break
                sleep 1
                n=${'$'}((n+1))
              done
              ss -ltnp 2>/dev/null | grep 'adbd' | awk '{print ${'$'}4}' | grep -Eq '(^|:|\])5555${'$'}' || exit 44
              "${'$'}TS" --socket="${'$'}SOCK" serve --bg --tcp=5555 tcp://127.0.0.1:5555 >/dev/null 2>&1 || exit 45
              if ss -ltnp 2>/dev/null | awk '{print ${'$'}4}' | grep -Eq '(^|:|\])8765${'$'}'; then
                "${'$'}TS" --socket="${'$'}SOCK" serve --bg --tcp=8765 tcp://127.0.0.1:8765 >/dev/null 2>&1 || true
              fi
            """.trimIndent()
        } else {
            """
              n=0
              while [ "${'$'}n" -lt 30 ]; do
                ip link show tailscale0 >/dev/null 2>&1 && ip route get 100.100.100.100 2>/dev/null | grep -q 'dev tailscale0' && break
                sleep 1
                n=${'$'}((n+1))
              done
              ip route get 100.100.100.100 2>/dev/null | grep -q 'dev tailscale0' || exit 46
            """.trimIndent()
        }
        return """
            #!/system/bin/sh
            ROOTTOOLS_MODE=${mode.name}
            ${runtimeVariables()}
            (
              n=0
              while [ "${'$'}(getprop sys.boot_completed)" != "1" ] && [ "${'$'}n" -lt 60 ]; do sleep 2; n=${'$'}((n+1)); done
              [ "${'$'}(getprop sys.boot_completed)" = "1" ] || exit 40
              ${secureRuntimeDirectories()}
              ${stopManagedDaemon()}
              ${cleanupLegacyRoutes()}
              rm -f "${'$'}SOCK"
              nohup "${'$'}TSD" --tun=$tun --state="${'$'}STATE/tailscaled.state" --statedir="${'$'}STATE" --socket="${'$'}SOCK" > "${'$'}RUN/boot-runtime.log" 2>&1 </dev/null &
              echo ${'$'}! > "${'$'}RUN/tailscaled.pid"
              ${waitForSocket(30, 41)}
              "${'$'}TS" --socket="${'$'}SOCK" set --accept-dns=false --hostname=${shellQuote(hostname)} >/dev/null 2>&1 || true
              ${waitForBackend(30, 42)}
              touch "${'$'}MARKER"
              $postStart
              echo "ready mode=${mode.name} ip=${'$'}ip"
            ) >> "${'$'}RUN/boot.log" 2>&1 &
        """.trimIndent()
    }

    private fun runtimeVariables(): String = """
        BASE=${RootTailscaleRuntimeSpec.BASE_DIR}
        BIN=${'$'}BASE/bin
        RUN=${'$'}BASE/run
        STATE=${'$'}BASE/state
        SOCK=${'$'}RUN/tailscaled.sock
        TS=${'$'}BIN/tailscale
        TSD=${'$'}BIN/tailscaled
        MARKER=${RootTailscaleRuntimeSpec.IDENTITY_MARKER}
    """.trimIndent()

    private fun secureRuntimeDirectories(): String = """
        umask 077
        mkdir -p "${'$'}BASE" "${'$'}BIN" "${'$'}RUN" "${'$'}STATE"
        chmod 0700 "${'$'}BASE" "${'$'}BIN" "${'$'}RUN" "${'$'}STATE"
    """.trimIndent()

    private fun stopManagedDaemon(): String = """
        for pid_file in "${'$'}RUN/auth.pid" "${'$'}RUN/auth-watch.pid"; do
          if [ -f "${'$'}pid_file" ]; then
            candidate_pid="${'$'}(cat "${'$'}pid_file" 2>/dev/null)"
            case "${'$'}candidate_pid" in
              ''|*[!0-9]*) ;;
              *) kill "${'$'}candidate_pid" >/dev/null 2>&1 || true ;;
            esac
          fi
        done
        managed_pids=""
        for old_pid in ${'$'}(pidof tailscaled 2>/dev/null); do
          if tr '\0' ' ' < "/proc/${'$'}old_pid/cmdline" 2>/dev/null | grep -Fq "${'$'}TSD"; then
            managed_pids="${'$'}managed_pids ${'$'}old_pid"
            kill "${'$'}old_pid" >/dev/null 2>&1 || true
          fi
        done
        n=0
        while [ "${'$'}n" -lt 5 ]; do
          alive=0
          for old_pid in ${'$'}managed_pids; do kill -0 "${'$'}old_pid" >/dev/null 2>&1 && alive=1; done
          [ "${'$'}alive" = 0 ] && break
          sleep 1
          n=${'$'}((n+1))
        done
        for old_pid in ${'$'}managed_pids; do kill -0 "${'$'}old_pid" >/dev/null 2>&1 && kill -9 "${'$'}old_pid" >/dev/null 2>&1 || true; done
    """.trimIndent()

    private fun cleanupLegacyRoutes(): String = """
        while iptables -t mangle -C OUTPUT -d 100.64.0.0/10 -j MARK --set-mark 1099 >/dev/null 2>&1; do
          iptables -t mangle -D OUTPUT -d 100.64.0.0/10 -j MARK --set-mark 1099 >/dev/null 2>&1 || break
        done
        while ip rule del fwmark 1099 lookup 1099 >/dev/null 2>&1; do :; done
        ip route del table 1099 100.64.0.0/10 dev tailscale0 >/dev/null 2>&1 || true
        ip route del 100.64.0.0/10 dev tailscale0 >/dev/null 2>&1 || true
    """.trimIndent()

    private fun waitForSocket(maxSeconds: Int, exitCode: Int): String = """
        n=0
        while [ "${'$'}n" -lt $maxSeconds ] && [ ! -S "${'$'}SOCK" ]; do sleep 1; n=${'$'}((n+1)); done
        [ -S "${'$'}SOCK" ] || exit $exitCode
    """.trimIndent()

    private fun waitForBackend(maxSeconds: Int, exitCode: Int): String = """
        n=0
        ip=""
        while [ "${'$'}n" -lt $maxSeconds ]; do
          ip="${'$'}("${'$'}TS" --socket="${'$'}SOCK" ip -4 2>/dev/null | head -n 1)"
          backend="${'$'}("${'$'}TS" --socket="${'$'}SOCK" status --peers=false --json 2>/dev/null | sed -n 's/.*"BackendState"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
          [ -n "${'$'}ip" ] && [ "${'$'}backend" = "Running" ] && break
          sleep 1
          n=${'$'}((n+1))
        done
        [ -n "${'$'}ip" ] && [ "${'$'}backend" = "Running" ] || exit $exitCode
    """.trimIndent()

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
