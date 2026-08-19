package com.arthur.roottools.data

import android.content.Context
import com.arthur.roottools.model.AppPolicyCategory
import com.arthur.roottools.model.StartupAnalysis
import com.arthur.roottools.model.StartupAppRecord
import com.arthur.roottools.model.StartupBucketSummary
import com.arthur.roottools.root.RootShell

class StartupRepository(
    private val context: Context,
    private val shell: RootShell,
) {
    suspend fun analyzeCurrentBoot(): StartupAnalysis {
        val result = shell.execute(COMMAND, timeoutSeconds = 15)
        if (!result.success) return StartupAnalysis()
        val sections = splitSections(result.output)
        val uptime = sections["META"]?.firstOrNull { it.startsWith("UPTIME=") }
            ?.substringAfter('=')?.toLongOrNull() ?: 0L
        val nowEpoch = sections["META"]?.firstOrNull { it.startsWith("NOW=") }
            ?.substringAfter('=')?.toLongOrNull() ?: 0L
        val bootEpoch = nowEpoch - uptime

        val thirdParty = sections["THIRD"]
            .orEmpty().mapNotNull { it.removePrefix("package:").trim().takeIf(String::isNotBlank) }.toSet()
        val disabled = sections["DISABLED"]
            .orEmpty().mapNotNull { it.removePrefix("package:").trim().takeIf(String::isNotBlank) }.toSet()
        val running = sections["RUNNING"]
            .orEmpty().map { it.trim().substringBefore(':') }.filter { it in thirdParty }.toSet()
        val buckets = sections["BUCKETS"].orEmpty().mapNotNull { line ->
            val pkg = line.substringBefore(':').trim()
            val bucket = line.substringAfter(':', "").trim().toIntOrNull()
            if (pkg.isNotBlank() && bucket != null) pkg to bucket else null
        }.toMap()

        val bootReceivers = mutableMapOf<String, Int>()
        sections["BOOT_RECEIVERS"].orEmpty().forEach { line ->
            val trimmed = line.trim()
            if (!trimmed.contains('/')) return@forEach
            val pkg = trimmed.substringBefore('/').trim()
            if (pkg in thirdParty) bootReceivers[pkg] = (bootReceivers[pkg] ?: 0) + 1
        }

        data class MutableEvent(val times: MutableList<Long> = mutableListOf(), val reasons: MutableSet<String> = linkedSetOf())
        val eventMap = mutableMapOf<String, MutableEvent>()
        sections["EVENTS"].orEmpty().forEach { line ->
            val match = EVENT_REGEX.find(line) ?: return@forEach
            val epoch = match.groupValues[1].substringBefore('.').toLongOrNull() ?: return@forEach
            if (epoch < bootEpoch) return@forEach
            val process = match.groupValues[2]
            val pkg = process.substringBefore(':')
            if (pkg !in thirdParty) return@forEach
            val reason = match.groupValues[3].trim()
            eventMap.getOrPut(pkg) { MutableEvent() }.apply {
                times += (epoch - bootEpoch).coerceAtLeast(0)
                reasons += reason
            }
        }

        val allRelevant = (thirdParty + bootReceivers.keys + eventMap.keys + disabled + running)
        val appRecords = allRelevant.map { pkg ->
            val event = eventMap[pkg]
            StartupAppRecord(
                packageName = pkg,
                label = labelFor(pkg),
                firstStartSeconds = event?.times?.minOrNull(),
                startCount = event?.times?.size ?: 0,
                startReasons = event?.reasons.orEmpty(),
                bootReceiverCount = bootReceivers[pkg] ?: 0,
                running = pkg in running,
                disabled = pkg in disabled,
                standbyBucket = buckets[pkg],
                category = categoryFor(pkg),
            )
        }.sortedWith(compareByDescending<StartupAppRecord> { it.startupRiskScore }.thenBy { it.firstStartSeconds ?: Long.MAX_VALUE })

        val timeline = listOf(
            0L to 30L to "0~30s",
            30L to 60L to "30~60s",
            60L to 180L to "1~3m",
            180L to 300L to "3~5m",
            300L to Long.MAX_VALUE to "5m+",
        ).map { pair ->
            val range = pair.first
            val label = pair.second
            val matching = appRecords.filter { record -> record.firstStartSeconds?.let { it >= range.first && it < range.second } == true }
            StartupBucketSummary(label, matching.size, matching.sumOf { it.startCount })
        }

        val listeners = sections["APP_MODE"]?.firstOrNull { it.startsWith("LISTENERS=") }.orEmpty()
        val whitelist = sections["APP_MODE"]?.filter { it.startsWith("WHITELIST=") }.orEmpty().joinToString("\n")
        val appiumMode = listeners.contains(APP_IUM_COMPONENT) && whitelist.contains(APP_IUM_PACKAGE)

        return StartupAnalysis(
            bootUptimeSeconds = uptime,
            apps = appRecords,
            buckets = timeline,
            appiumTestMode = appiumMode,
        )
    }

    private fun labelFor(packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName.substringAfterLast('.'))

    private fun categoryFor(packageName: String): AppPolicyCategory = when (packageName) {
        in PROTECTED -> AppPolicyCategory.PROTECTED
        in FREEZE -> AppPolicyCategory.FREEZE
        in ON_DEMAND -> AppPolicyCategory.ON_DEMAND
        in RARE -> AppPolicyCategory.RARE
        else -> AppPolicyCategory.NORMAL
    }

    private fun splitSections(raw: String): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        raw.lineSequence().forEach { line ->
            if (line.startsWith("__") && line.endsWith("__")) {
                val section = line.removePrefix("__").removeSuffix("__")
                current = section
                result.getOrPut(section) { mutableListOf() }
            } else current?.let { result.getOrPut(it) { mutableListOf() }.add(line) }
        }
        return result
    }

    private companion object {
        const val APP_IUM_PACKAGE = "io.appium.settings"
        const val APP_IUM_COMPONENT = "io.appium.settings/io.appium.settings.NLService"
        val EVENT_REGEX = Regex("^\\s*([0-9]+\\.[0-9]+).*am_proc_start: \\[0,[0-9]+,[0-9]+,([^,]+),([^,]+),")
        val PROTECTED = setOf(
            "com.arthur.roottools",
            "com.tailscale.ipn",
            "com.arthur.aibox.android.rootlab",
            "com.arlosoft.macrodroid",
            "li.songe.gkd",
        )
        val FREEZE = setOf("com.tencent.android.qqdownloader")
        val ON_DEMAND = setOf("com.apextuner.app.debug", "io.appium.settings", "com.omarea.vtools", "net.dinglisch.android.taskerm")
        val RARE = setOf("com.bilibili.app.in", "com.facebook.katana", "com.esuper.file.explorer", "com.google.android.apps.photos")

        val COMMAND = """
            echo '__META__'
            echo NOW=${'$'}(date +%s)
            echo UPTIME=${'$'}(cut -d. -f1 /proc/uptime)
            echo '__THIRD__'
            pm list packages -3
            echo '__DISABLED__'
            pm list packages -3 -d
            echo '__BUCKETS__'
            am get-standby-bucket 2>/dev/null
            echo '__RUNNING__'
            ps -A -o ARGS 2>/dev/null
            echo '__BOOT_RECEIVERS__'
            cmd package query-receivers --brief -a android.intent.action.BOOT_COMPLETED 2>/dev/null
            echo '__EVENTS__'
            logcat -b events -d -v epoch -s am_proc_start:I 2>/dev/null
            echo '__APP_MODE__'
            echo LISTENERS=${'$'}(settings get secure enabled_notification_listeners 2>/dev/null)
            dumpsys deviceidle whitelist 2>/dev/null | sed 's/^/WHITELIST=/'
        """.trimIndent()
    }
}
