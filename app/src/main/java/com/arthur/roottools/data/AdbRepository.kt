package com.arthur.roottools.data

import android.content.Context
import com.arthur.roottools.model.AdbSnapshot
import com.arthur.roottools.root.RootShell

class AdbRepository(
    context: Context,
    private val shell: RootShell,
) {
    private val preferences = AdbPreferenceStore(context)

    suspend fun read(): AdbSnapshot {
        val root = shell.isAvailable()
        if (!root) return AdbSnapshot(bootPolicy = preferences.bootPolicy())
        val result = shell.execute(COLLECT_COMMAND, timeoutSeconds = 8)
        if (!result.success) return AdbSnapshot(rootAvailable = true, bootPolicy = preferences.bootPolicy())
        val snapshot = AdbStateParser.parse(
            raw = result.output,
            rootAvailable = true,
            bootPolicy = preferences.bootPolicy(),
            collectedAtMs = System.currentTimeMillis(),
        )
        preferences.updateLastKnown(snapshot)
        return snapshot
    }

    private companion object {
        val COLLECT_COMMAND = """
            echo '__ROOT_TCP__'
            echo "PORT=${'$'}(getprop service.adb.tcp.port)"
            echo '__NATIVE__'
            echo "ENABLED=${'$'}(settings get global adb_wifi_enabled 2>/dev/null)"
            echo "SUPPORTED=${'$'}(cmd adb is-wifi-supported 2>/dev/null)"
            echo "QR=${'$'}(cmd adb is-wifi-qr-supported 2>/dev/null)"
            echo '__USB__'
            echo "ADB_ENABLED=${'$'}(settings get global adb_enabled 2>/dev/null)"
            case "${'$'}(getprop sys.usb.config)" in *adb*) echo 'ACTIVE=1' ;; *) echo 'ACTIVE=0' ;; esac
            echo '__ADBD_PORTS__'
            ss -ltnp 2>/dev/null | grep 'adbd' | awk '{print ${'$'}4}' | awk -F: '{print ${'$'}NF}' | tr -d '[]' | grep -E '^[0-9]+${'$'}' | sort -nu
            echo '__NETWORK__'
            echo "TAILSCALE=${'$'}(ip -4 -o addr show tun0 2>/dev/null | awk '{print ${'$'}4}' | cut -d/ -f1 | head -n 1)"
            iface=${'$'}(ip route show default 2>/dev/null | head -n 1 | sed -n 's/.* dev \([^ ]*\).*/\1/p')
            echo "IFACE=${'$'}iface"
            echo "LOCAL=${'$'}(ip -4 -o addr show "${'$'}iface" 2>/dev/null | awk '{print ${'$'}4}' | cut -d/ -f1 | head -n 1)"
            echo '__TRUSTED__'
            if [ -f /data/misc/adb/adb_keys ]; then cut -d' ' -f2- /data/misc/adb/adb_keys 2>/dev/null | tail -n 12; fi
        """.trimIndent()
    }
}

