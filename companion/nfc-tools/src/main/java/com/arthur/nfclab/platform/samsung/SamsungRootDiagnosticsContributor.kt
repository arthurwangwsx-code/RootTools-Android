package com.arthur.nfclab.platform.samsung

import android.content.Context
import android.os.Build
import com.arthur.nfclab.platform.diagnostics.RootDiagnosticsContributor
import java.util.concurrent.TimeUnit

/** Read-only Samsung/NXP capability probe; it never changes routing or NFC configuration. */
class SamsungRootDiagnosticsContributor : RootDiagnosticsContributor {
    override val id: String = "samsung.root-nfc"
    override val priority: Int = 110

    override fun supports(context: Context): Boolean = isSamsungManufacturer(Build.MANUFACTURER)

    override fun collect(context: Context): String {
        val script = """
            echo '=== samsung nfc extension probe ==='
            pm list features 2>/dev/null | grep -E 'com\.samsung\.android\.nfc|com\.nxp\.mifare|android\.hardware\.nfc' || true
            if [ -f /system/framework/com.samsung.android.nfc.t4t.jar ]; then
              echo samsung_t4t_framework=true
            else
              echo samsung_t4t_framework=false
            fi
            for f in /odm/etc/libnfc-nxp.conf /vendor/etc/libnfc-nxp.conf /product/etc/libnfc-nxp.conf /system_ext/etc/libnfc-nxp.conf; do
              if [ -r "${'$'}f" ]; then
                echo "nfc_vendor_config=${'$'}f"
                grep -E '^(MIFARE_READER_ENABLE|LEGACY_MIFARE_READER|DEFAULT_AID_ROUTE|DEFAULT_ISODEP_ROUTE|DEFAULT_MIFARE_CLT_ROUTE|HOST_LISTEN_TECH_MASK|NFA_PROPRIETARY_CFG)=' "${'$'}f" || true
                break
              fi
            done
            echo '--- nfc related services ---'
            service list 2>/dev/null | grep -i nfc || true
        """.trimIndent()

        return runCatching {
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(8, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            when {
                !finished -> "=== samsung nfc extension probe ===\nprobe timeout"
                output.isBlank() -> "=== samsung nfc extension probe ===\nprobe unavailable"
                else -> output.trim()
            }
        }.getOrElse { error ->
            "=== samsung nfc extension probe ===\n${error.javaClass.simpleName}: ${error.message}"
        }
    }

    companion object {
        internal fun isSamsungManufacturer(manufacturer: String): Boolean =
            manufacturer.equals("samsung", ignoreCase = true)
    }
}
