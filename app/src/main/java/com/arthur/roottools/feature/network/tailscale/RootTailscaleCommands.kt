package com.arthur.roottools.feature.network.tailscale

import com.arthur.roottools.feature.network.tailscale.data.RootTailscaleRuntimeInstaller
import com.arthur.roottools.feature.network.tailscale.data.RootTailscaleRuntimeSpec

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
            mkdir -p "${'$'}BIN" "${'$'}RUN" "${'$'}STATE"
            umask 077
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
        BASE=${RootTailscaleRuntimeSpec.BASE_DIR}
        BIN=${'$'}BASE/bin
        RUN=${'$'}BASE/run
        STATE=${'$'}BASE/state
        SOCK=${'$'}RUN/tailscaled.sock
        TS=${'$'}BIN/tailscale
        TSD=${'$'}BIN/tailscaled
        mkdir -p "${'$'}RUN" "${'$'}STATE"
        stopped=0
        for old_pid in ${'$'}(pidof tailscaled 2>/dev/null); do
          if tr '\0' ' ' < "/proc/${'$'}old_pid/cmdline" 2>/dev/null | grep -Fq "${'$'}TSD"; then
            kill "${'$'}old_pid" >/dev/null 2>&1 || true
            stopped=1
          fi
        done
        [ "${'$'}stopped" = 1 ] && sleep 1
        rm -f "${'$'}SOCK" "${'$'}RUN/auth.out"
        nohup "${'$'}TSD" --tun=userspace-networking --state="${'$'}STATE/tailscaled.state" --statedir="${'$'}STATE" --socket="${'$'}SOCK" > "${'$'}RUN/userspace.log" 2>&1 </dev/null &
        echo ${'$'}! > "${'$'}RUN/tailscaled.pid"
        n=0
        while [ "${'$'}n" -lt 15 ] && [ ! -S "${'$'}SOCK" ]; do sleep 1; n=${'$'}((n+1)); done
        [ -S "${'$'}SOCK" ] || exit 20
        nohup "${'$'}TS" --socket="${'$'}SOCK" up --accept-dns=false --hostname=${shellQuote(hostname)} > "${'$'}RUN/auth.out" 2>&1 </dev/null &
        echo ${'$'}! > "${'$'}RUN/auth.pid"
        n=0
        while [ "${'$'}n" -lt 20 ]; do
          ip="${'$'}("${'$'}TS" --socket="${'$'}SOCK" ip -4 2>/dev/null | head -n 1)"
          url="${'$'}("${'$'}TS" --socket="${'$'}SOCK" status 2>/dev/null | sed -n 's/^Log in at: //p' | head -n 1)"
          [ -n "${'$'}ip" ] && { echo "TAILNET_IP=${'$'}ip"; exit 0; }
          [ -n "${'$'}url" ] && { echo "AUTH_URL=${'$'}url"; exit 0; }
          sleep 1
          n=${'$'}((n+1))
        done
        cat "${'$'}RUN/auth.out" 2>/dev/null | tail -n 20
        exit 21
    """.trimIndent()

    fun enableKernel(hostname: String): String = """
        BASE=${RootTailscaleRuntimeSpec.BASE_DIR}
        BIN=${'$'}BASE/bin
        RUN=${'$'}BASE/run
        STATE=${'$'}BASE/state
        SOCK=${'$'}RUN/tailscaled.sock
        TS=${'$'}BIN/tailscale
        TSD=${'$'}BIN/tailscaled
        stopped=0
        for old_pid in ${'$'}(pidof tailscaled 2>/dev/null); do
          if tr '\0' ' ' < "/proc/${'$'}old_pid/cmdline" 2>/dev/null | grep -Fq "${'$'}TSD"; then
            kill "${'$'}old_pid" >/dev/null 2>&1 || true
            stopped=1
          fi
        done
        [ "${'$'}stopped" = 1 ] && sleep 1
        [ -f "${'$'}RUN/auth.pid" ] && kill ${'$'}(cat "${'$'}RUN/auth.pid") >/dev/null 2>&1 || true
        rm -f "${'$'}SOCK"
        nohup "${'$'}TSD" --tun=tailscale0 --state="${'$'}STATE/tailscaled.state" --statedir="${'$'}STATE" --socket="${'$'}SOCK" > "${'$'}RUN/kernel.log" 2>&1 </dev/null &
        echo ${'$'}! > "${'$'}RUN/tailscaled.pid"
        n=0
        while [ "${'$'}n" -lt 25 ] && [ ! -S "${'$'}SOCK" ]; do sleep 1; n=${'$'}((n+1)); done
        [ -S "${'$'}SOCK" ] || exit 30
        "${'$'}TS" --socket="${'$'}SOCK" set --accept-dns=false --hostname=${shellQuote(hostname)} >/dev/null 2>&1 || true
        n=0
        while [ "${'$'}n" -lt 30 ]; do
          ip link show tailscale0 >/dev/null 2>&1 && ip="${'$'}("${'$'}TS" --socket="${'$'}SOCK" ip -4 2>/dev/null | head -n 1)" && [ -n "${'$'}ip" ] && break
          sleep 1
          n=${'$'}((n+1))
        done
        ip link show tailscale0 >/dev/null 2>&1 || exit 31
        ip="${'$'}("${'$'}TS" --socket="${'$'}SOCK" ip -4 2>/dev/null | head -n 1)"
        [ -n "${'$'}ip" ] || exit 32
        ${repairRoutes()}
        echo "TAILNET_IP=${'$'}ip"
    """.trimIndent()

    fun repairRoutes(): String = """
        ip route replace 100.64.0.0/10 dev tailscale0 metric 1 >/dev/null 2>&1 || true
        ip route replace table 1099 100.64.0.0/10 dev tailscale0 >/dev/null 2>&1 || true
        iptables -t mangle -C OUTPUT -d 100.64.0.0/10 -j MARK --set-mark 1099 >/dev/null 2>&1 || iptables -t mangle -A OUTPUT -d 100.64.0.0/10 -j MARK --set-mark 1099 >/dev/null 2>&1 || true
        ip rule show 2>/dev/null | grep -q 'lookup 1099' || ip rule add fwmark 1099 lookup 1099 >/dev/null 2>&1 || true
    """.trimIndent()

    fun disable(): String = """
        BASE=${RootTailscaleRuntimeSpec.BASE_DIR}
        BIN=${'$'}BASE/bin
        RUN=${'$'}BASE/run
        if [ -f "${'$'}RUN/auth.pid" ]; then kill ${'$'}(cat "${'$'}RUN/auth.pid") >/dev/null 2>&1 || true; fi
        for old_pid in ${'$'}(pidof tailscaled 2>/dev/null); do
          if tr '\0' ' ' < "/proc/${'$'}old_pid/cmdline" 2>/dev/null | grep -Fq "${'$'}BIN/tailscaled"; then
            kill "${'$'}old_pid" >/dev/null 2>&1 || true
          fi
        done
        sleep 1
        while iptables -t mangle -C OUTPUT -d 100.64.0.0/10 -j MARK --set-mark 1099 >/dev/null 2>&1; do iptables -t mangle -D OUTPUT -d 100.64.0.0/10 -j MARK --set-mark 1099 >/dev/null 2>&1 || break; done
        while ip rule del fwmark 1099 lookup 1099 >/dev/null 2>&1; do :; done
        ip route del table 1099 100.64.0.0/10 dev tailscale0 >/dev/null 2>&1 || true
        ip route del 100.64.0.0/10 dev tailscale0 >/dev/null 2>&1 || true
        ip link delete tailscale0 >/dev/null 2>&1 || true
        rm -f "${'$'}RUN/tailscaled.sock" "${'$'}RUN/tailscaled.pid" "${'$'}RUN/auth.pid"
    """.trimIndent()

    fun enableBoot(hostname: String): String {
        val script = bootScript(hostname)
        return """
            mkdir -p /data/adb/service.d
            cat > ${RootTailscaleRuntimeSpec.BOOT_SCRIPT} <<'ROOTTOOLS_TAILSCALE_BOOT'
$script
ROOTTOOLS_TAILSCALE_BOOT
            chmod 0755 ${RootTailscaleRuntimeSpec.BOOT_SCRIPT}
        """.trimIndent()
    }

    fun disableBoot(): String = "rm -f ${RootTailscaleRuntimeSpec.BOOT_SCRIPT}"

    private fun bootScript(hostname: String): String = """
        #!/system/bin/sh
        BASE=${RootTailscaleRuntimeSpec.BASE_DIR}
        BIN=${'$'}BASE/bin
        RUN=${'$'}BASE/run
        STATE=${'$'}BASE/state
        SOCK=${'$'}RUN/tailscaled.sock
        TS=${'$'}BIN/tailscale
        TSD=${'$'}BIN/tailscaled
        (
          n=0
          while [ "${'$'}(getprop sys.boot_completed)" != "1" ] && [ "${'$'}n" -lt 60 ]; do sleep 2; n=${'$'}((n+1)); done
          mkdir -p "${'$'}RUN" "${'$'}STATE"
          stopped=0
          for old_pid in ${'$'}(pidof tailscaled 2>/dev/null); do
            if tr '\0' ' ' < "/proc/${'$'}old_pid/cmdline" 2>/dev/null | grep -Fq "${'$'}TSD"; then
              kill "${'$'}old_pid" >/dev/null 2>&1 || true
              stopped=1
            fi
          done
          [ "${'$'}stopped" = 1 ] && sleep 1
          rm -f "${'$'}SOCK"
          nohup "${'$'}TSD" --tun=tailscale0 --state="${'$'}STATE/tailscaled.state" --statedir="${'$'}STATE" --socket="${'$'}SOCK" > "${'$'}RUN/kernel.log" 2>&1 </dev/null &
          echo ${'$'}! > "${'$'}RUN/tailscaled.pid"
          n=0
          while [ "${'$'}n" -lt 30 ] && [ ! -S "${'$'}SOCK" ]; do sleep 1; n=${'$'}((n+1)); done
          [ -S "${'$'}SOCK" ] || exit 40
          "${'$'}TS" --socket="${'$'}SOCK" set --accept-dns=false --hostname=${shellQuote(hostname)} >/dev/null 2>&1 || true
          n=0
          ip=""
          while [ "${'$'}n" -lt 30 ]; do
            ip="${'$'}("${'$'}TS" --socket="${'$'}SOCK" ip -4 2>/dev/null | head -n 1)"
            ip link show tailscale0 >/dev/null 2>&1 && [ -n "${'$'}ip" ] && break
            sleep 1
            n=${'$'}((n+1))
          done
          [ -n "${'$'}ip" ] || exit 41
          ${repairRoutes()}
          am force-stop --user 0 ${RootTailscaleRuntimeSpec.OFFICIAL_PACKAGE} >/dev/null 2>&1 || true
        ) >> "${'$'}RUN/boot.log" 2>&1 &
    """.trimIndent()

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}

