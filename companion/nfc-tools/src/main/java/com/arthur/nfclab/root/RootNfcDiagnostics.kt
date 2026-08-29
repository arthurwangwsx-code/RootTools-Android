package com.arthur.nfclab.root

import android.content.Context
import com.arthur.nfclab.platform.diagnostics.RootDiagnosticsContributor
import com.arthur.nfclab.platform.diagnostics.RootDiagnosticsContributorRegistry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object RootNfcDiagnostics {
    fun collect(
        context: Context,
        contributors: List<RootDiagnosticsContributor> = RootDiagnosticsContributorRegistry.defaults(),
    ): String {
        val nativeStatus = RootNativeBridge(context).status()
        val script = """
            echo '=== identity ==='
            id
            echo '=== selinux ==='
            getenforce
            echo '=== boot ==='
            echo -n 'verifiedbootstate='; getprop ro.boot.verifiedbootstate
            echo -n 'flash_locked='; getprop ro.boot.flash.locked
            echo '=== nfc properties ==='
            getprop | grep -i '^\[[^]]*nfc[^]]*\]:' | head -n 40
            echo '=== nfc service summary ==='
            dumpsys nfc | grep -E '^(mState=|mScreenState=|mTechMask:|mEnableLPD:|mEnableReader:|mEnableHostRouting:|mEnableP2p:|mIsSecureNfcEnabled=|Routing table:|    Default route:|--- dumpRoutingTable:|[[:space:]]+(AID_|PROTOCOL_|TECHNOLOGY_|SYSTEMCODE_))' | head -n 140
            echo '=== nfc hal ==='
            service list | grep -i nfc
        """.trimIndent()

        val shellStatus = runCatching {
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()

            val text = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching "Root 诊断超时。请确认 Magisk 已给 NFC Lab 授权。\n\n$text"
            }
            if (text.isBlank()) {
                "su 未返回内容。请确认 Root 管理器是否弹出了授权请求。"
            } else {
                redactCredentialIdentifiers(text.trim())
            }
        }.getOrElse {
            "Root 诊断失败：${it.javaClass.simpleName}: ${it.message}"
        }

        val vendorSections = contributors
            .asSequence()
            .filter { contributor -> runCatching { contributor.supports(context) }.getOrDefault(false) }
            .sortedBy { it.priority }
            .map { contributor ->
                runCatching { contributor.collect(context) }
                    .getOrElse { error ->
                        "=== ${contributor.id} ===\n${error.javaClass.simpleName}: ${error.message}"
                    }
            }
            .filter { it.isNotBlank() }
            .toList()

        return redactCredentialIdentifiers(buildString {
            appendLine("=== native root bridge ===")
            appendLine(nativeStatus)
            appendLine()
            append(shellStatus)
            vendorSections.forEach { section ->
                appendLine()
                appendLine()
                append(section)
            }
        }.trim())
    }

    internal fun redactCredentialIdentifiers(value: String): String {
        return value
            .replace(Regex("(?i)(\\bUID:)\\S+"), "$1<redacted>")
            .replace(Regex("(?i)(\\bCID:)\\S+"), "$1<redacted>")
            .replace(Regex("(?i)(door_card_vc_uid[=:]\\s*)\\S+"), "$1<redacted>")
            .replace(Regex("(?i)(door_card_cid[=:]\\s*)\\S+"), "$1<redacted>")
            .replace(Regex("(?m)^User .+ :$"), "User <redacted> :")
    }
}

