package com.arthur.nfclab.platform.xiaomi

import android.content.Context
import android.os.Build
import com.arthur.nfclab.platform.diagnostics.RootDiagnosticsContributor
import java.util.concurrent.TimeUnit

class XiaomiRootDiagnosticsContributor : RootDiagnosticsContributor {
    override val id: String = "xiaomi.root-nfc"
    override val priority: Int = 100

    override fun supports(context: Context): Boolean = isXiaomiFamily(Build.MANUFACTURER)

    override fun collect(context: Context): String {
        val script = """
            echo '=== xiaomi off-host mifare probe ==='
            echo -n 'wallet_package='; pm path com.miui.tsmclient >/dev/null 2>&1 && echo com.miui.tsmclient || echo unavailable
            dumpsys package com.miui.tsmclient 2>/dev/null | grep -m 1 'versionName=' || true
            dumpsys secure_element 2>/dev/null | grep -m 1 'SECURE ELEMENT SERVICE TERMINAL: eSE1' || true
            for f in /odm/etc/libnfc-nxp.conf /vendor/etc/libnfc-nxp.conf /product/etc/libnfc-nxp.conf /system_ext/etc/libnfc-nxp.conf; do
              if [ -r "${'$'}f" ]; then
                echo "nfc_vendor_config=${'$'}f"
                grep -E '^(MIFARE_READER_ENABLE|LEGACY_MIFARE_READER|DEFAULT_AID_ROUTE|DEFAULT_ISODEP_ROUTE|DEFAULT_MIFARE_CLT_ROUTE|DEFAULT_MIFARE_CLT_PWR_STATE|NFA_PROPRIETARY_CFG|HOST_LISTEN_TECH_MASK|NXP_SPI_SE_TERMINAL_NUM|NXP_NFC_SE_TERMINAL_NUM)=' "${'$'}f" || true
                break
              fi
            done
            echo '--- provisioned card summary (credential identifiers redacted) ---'
            dumpsys nfc 2>/dev/null | awk '/^Cards:/{show=1} show{print} /^Guards:/{exit}' | sed 's/ UID:[^ ]*/ UID:<redacted>/g'
            echo 'interpretation=TYPE:M1 with MIFARE technology routed to an eSE is an OEM off-host card-emulation path, not Android HostApduService HCE.'
        """.trimIndent()

        return runCatching {
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(8, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            when {
                !finished -> "=== xiaomi off-host mifare probe ===\nprobe timeout"
                output.isBlank() -> "=== xiaomi off-host mifare probe ===\nprobe unavailable"
                else -> output.trim()
            }
        }.getOrElse { error ->
            "=== xiaomi off-host mifare probe ===\n${error.javaClass.simpleName}: ${error.message}"
        }
    }

    companion object {
        internal fun isXiaomiFamily(manufacturer: String): Boolean {
            val value = manufacturer.lowercase()
            return value.contains("xiaomi") || value.contains("redmi") || value.contains("poco")
        }
    }
}
