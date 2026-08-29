package com.arthur.nfclab.platform.xiaomi

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object XiaomiWalletInspector {
    private const val WALLET_PACKAGE = "com.miui.tsmclient"

    fun collect(context: Context): XiaomiNfcProfile {
        val system = collectSystemFacts()
        val statusByAid = parseCardStatus(system.cardSummary)
        val cardResult = runCatching { readDoorCards(context, statusByAid) }
        val cards = cardResult.getOrDefault(emptyList())
        val cardError = cardResult.exceptionOrNull()?.let { "wallet-db: ${it.javaClass.simpleName}: ${it.message}" }

        return XiaomiNfcProfile(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            androidRelease = Build.VERSION.RELEASE,
            hyperOsVersion = system.values["hyperos"].nullIfBlank(),
            rootAvailable = system.values["root"] == "true",
            selinuxEnforcing = system.values["selinux"].equals("Enforcing", ignoreCase = true),
            nfcFirmware = system.values["nfc_fw"].nullIfBlank(),
            nfcChipId = system.values["nfc_chip"].nullIfBlank(),
            nfcPort = system.values["nfc_port"].nullIfBlank(),
            walletVersion = system.values["wallet_version"].nullIfBlank(),
            eSeConnected = system.values["ese_connected"] == "true",
            mifareReaderEnabled = system.values["mifare_reader"] == "0x01" ||
                system.values["mifare_reader"] == "1",
            defaultMifareRoute = system.values["mifare_route"].nullIfBlank(),
            hostListenTechMask = system.values["host_listen"].nullIfBlank(),
            extendedFieldDetectEnabled = system.values["extended_field_detect"] == "0x01" ||
                system.values["extended_field_detect"] == "1",
            t4tNfceeEnabled = system.values["t4t_nfcee"] == "0x01" ||
                system.values["t4t_nfcee"] == "1",
            miNfcServiceAvailable = system.values["mi_nfc"] == "true",
            nxpVendorServiceAvailable = system.values["nxp_vendor"] == "true",
            eseAccessPermissionPrivileged = system.values["ese_access_privileged"] == "true",
            walletEseAccessGranted = system.values["wallet_ese_access"] == "true",
            openSeServiceAvailable = system.values["open_se_service"] == "true",
            miSeOpenServiceAvailable = system.values["mi_se_open_service"] == "true",
            publicTsmFeaturePermissionPrivileged = system.values["public_tsm_feature_privileged"] == "true",
            miNfcApiVersion = parseParcelInt(system.values["mi_nfc_version_raw"]),
            seRouting = parseParcelInt(system.values["mi_nfc_se_raw"]),
            listenTechMask = parseParcelInt(system.values["mi_nfc_listen_raw"]),
            pollingTechMask = parseParcelInt(system.values["mi_nfc_polling_raw"])?.ushr(8),
            cards = cards,
            collectedAtMs = System.currentTimeMillis(),
            error = listOfNotNull(system.error, cardError).joinToString("; ").ifBlank { null },
        )
    }

    private data class SystemFacts(
        val values: Map<String, String>,
        val cardSummary: String,
        val error: String? = null,
    )

    private fun collectSystemFacts(): SystemFacts {
        val script = """
            echo root=true
            echo selinux=$(getenforce 2>/dev/null)
            h=$(getprop ro.mi.os.version.name); [ -z "${'$'}h" ] && h=$(getprop ro.miui.ui.version.name); echo hyperos=${'$'}h
            echo nfc_fw=$(getprop vendor.qti.nfc.fwver)
            echo nfc_chip=$(getprop vendor.qti.nfc.chipid)
            echo nfc_port=$(getprop ro.nfc.port)
            echo wallet_version=$(dumpsys package $WALLET_PACKAGE 2>/dev/null | sed -n 's/^[[:space:]]*versionName=//p' | head -n 1)
            dumpsys secure_element 2>/dev/null | grep -q 'SECURE ELEMENT SERVICE TERMINAL: eSE1' && echo ese_connected=true || echo ese_connected=false
            service list 2>/dev/null | grep -q 'mi_nfc:' && echo mi_nfc=true || echo mi_nfc=false
            service list 2>/dev/null | grep -q 'vendor.nxp.nxpnfc' && echo nxp_vendor=true || echo nxp_vendor=false
            dumpsys package permissions 2>/dev/null | grep -A 4 'Permission \[com.miui.permission.ACCESS_ESE\]' | grep -q 'prot=signature|privileged' && echo ese_access_privileged=true || echo ese_access_privileged=false
            dumpsys package $WALLET_PACKAGE 2>/dev/null | grep -q 'com.miui.permission.ACCESS_ESE: granted=true' && echo wallet_ese_access=true || echo wallet_ese_access=false
            dumpsys package $WALLET_PACKAGE 2>/dev/null | grep -q 'com.miui.tsmclient.action.ESE2_OPEN_SERVICE' && echo open_se_service=true || echo open_se_service=false
            dumpsys package $WALLET_PACKAGE 2>/dev/null | grep -q 'com.miui.tsmclient.action.SE_OPEN_SERVICE' && echo mi_se_open_service=true || echo mi_se_open_service=false
            dumpsys package $WALLET_PACKAGE 2>/dev/null | grep -A 4 'Permission \[com.miui.tsmclient.permission.CALL_PUBLIC_TSM_FEATURE_SERVICE\]' | grep -q 'prot=signature|privileged' && echo public_tsm_feature_privileged=true || echo public_tsm_feature_privileged=false
            echo mi_nfc_version_raw=$(service call mi_nfc 10 2>/dev/null | head -n 1)
            echo mi_nfc_se_raw=$(service call mi_nfc 1 2>/dev/null | head -n 1)
            echo mi_nfc_listen_raw=$(service call mi_nfc 5 i32 1 2>/dev/null | head -n 1)
            echo mi_nfc_polling_raw=$(service call mi_nfc 5 i32 2 2>/dev/null | head -n 1)
            for f in /odm/etc/libnfc-nxp.conf /vendor/etc/libnfc-nxp.conf /product/etc/libnfc-nxp.conf /system_ext/etc/libnfc-nxp.conf; do
              if [ -r "${'$'}f" ]; then
                echo mifare_reader=$(grep -m 1 '^MIFARE_READER_ENABLE=' "${'$'}f" | cut -d= -f2)
                echo mifare_route=$(grep -m 1 '^DEFAULT_MIFARE_CLT_ROUTE=' "${'$'}f" | cut -d= -f2)
                echo host_listen=$(grep -m 1 '^HOST_LISTEN_TECH_MASK=' "${'$'}f" | cut -d= -f2)
                echo extended_field_detect=$(grep -m 1 '^NXP_EXTENDED_FIELD_DETECT_MODE=' "${'$'}f" | cut -d= -f2)
                echo t4t_nfcee=$(grep -m 1 '^NXP_T4T_NFCEE_ENABLE=' "${'$'}f" | cut -d= -f2)
                break
              fi
            done
            echo __CARDS_BEGIN__
            dumpsys nfc 2>/dev/null | awk '/^Cards:/{show=1} show{print} /^Guards:/{exit}'
            echo __CARDS_END__
        """.trimIndent()

        return runCatching {
            val process = ProcessBuilder("su", "-M", "-c", script)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching SystemFacts(emptyMap(), "", "Root NFC 概览读取超时")
            }
            if (process.exitValue() != 0 || output.isBlank()) {
                return@runCatching SystemFacts(emptyMap(), "", "Root NFC 概览不可用")
            }

            val cardStart = output.indexOf("__CARDS_BEGIN__")
            val cardEnd = output.indexOf("__CARDS_END__")
            val cardSummary = if (cardStart >= 0 && cardEnd > cardStart) {
                output.substring(cardStart + "__CARDS_BEGIN__".length, cardEnd).trim()
            } else {
                ""
            }
            val scalarPart = if (cardStart >= 0) output.substring(0, cardStart) else output
            val values = scalarPart.lineSequence()
                .mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
                }
                .toMap()
            SystemFacts(values, cardSummary)
        }.getOrElse {
            SystemFacts(emptyMap(), "", "${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun parseCardStatus(cardSummary: String): Map<String, Boolean> {
        val regex = Regex("AID:([0-9A-Fa-f]+)\\s+STATUS:(ACTIVATED|DEACTIVATED)")
        return regex.findAll(cardSummary).associate { match ->
            match.groupValues[1].uppercase() to (match.groupValues[2] == "ACTIVATED")
        }
    }

    private fun readDoorCards(context: Context, statusByAid: Map<String, Boolean>): List<XiaomiVirtualCard> {
        val snapshotDir = File(context.cacheDir, "xiaomi-wallet-probe").apply {
            deleteRecursively()
            mkdirs()
        }
        return try {
            val walletDataDir = context.packageManager
                .getApplicationInfo(WALLET_PACKAGE, 0)
                .dataDir
            val remoteBase = "$walletDataDir/databases/tsmclient.db"
            val localDb = File(snapshotDir, "tsmclient.db")
            if (!copyRootFile(remoteBase, localDb)) return emptyList()
            val localWal = File(snapshotDir, "tsmclient.db-wal")
            copyRootFile("$remoteBase-wal", localWal, optional = true)

            SQLiteDatabase.openDatabase(
                localDb.absolutePath,
                null,
                // The snapshot is already isolated in our private cache. OPEN_READWRITE lets SQLite
                // rebuild the WAL index locally; we still issue read-only SQL and delete the copy.
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            ).use { database ->
                val cards = mutableListOf<XiaomiVirtualCard>()
                database.rawQuery(
                    "SELECT value FROM cache WHERE value LIKE ?",
                    arrayOf("%MIFARE_ENTRANCE%"),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val value = cursor.getString(0) ?: continue
                        parseCards(value, statusByAid).forEach { card ->
                            if (cards.none { it.aid.equals(card.aid, ignoreCase = true) }) cards += card
                        }
                    }
                }
                cards.sortedWith(compareByDescending<XiaomiVirtualCard> { it.active }.thenBy { it.title })
            }
        } finally {
            snapshotDir.deleteRecursively()
        }
    }

    private fun parseCards(value: String, statusByAid: Map<String, Boolean>): List<XiaomiVirtualCard> {
        val array = runCatching { JSONArray(value) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optString("cardName") != "MIFARE_ENTRANCE") continue
                val aid = item.optString("aid").uppercase()
                if (aid.isBlank()) continue
                add(
                    XiaomiVirtualCard(
                        title = item.optString("title").ifBlank { "门卡" },
                        aid = aid,
                        active = statusByAid[aid] == true,
                        productName = item.optString("door_card_product_name").ifBlank { "实体门卡" },
                        mifareCardType = item.optInt("mifare_card_type", 0),
                        sectorOverwritten = item.optBoolean("is_sector_overwritten", false),
                    ),
                )
            }
        }
    }

    private fun copyRootFile(remotePath: String, localFile: File, optional: Boolean = false): Boolean {
        val process = ProcessBuilder("su", "-M", "-c", "cat ${shellQuote(remotePath)}")
            .start()
        return try {
            process.inputStream.use { input ->
                localFile.outputStream().use { output -> input.copyTo(output) }
            }
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            val success = finished && process.exitValue() == 0 && localFile.length() > 0
            if (!success) {
                val stderr = runCatching { process.errorStream.bufferedReader().readText().trim() }.getOrDefault("")
                Log.w(TAG, "root file copy failed path=$remotePath exit=${if (finished) process.exitValue() else "timeout"} stderr=$stderr")
                localFile.delete()
            }
            success || optional
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    private fun parseParcelInt(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val words = Regex("(?i)\\b[0-9a-f]{8}\\b").findAll(value).map { it.value }.toList()
        if (words.size < 2) return null
        return words[1].toLongOrNull(16)?.toInt()
    }

    private const val TAG = "XiaomiWalletInspector"
}
