package com.arthur.roottools.feature.integrity.data

import android.content.Context
import com.arthur.roottools.feature.integrity.model.IntegrityReportFormat
import com.arthur.roottools.feature.integrity.model.IntegritySnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IntegrityReportStore(private val context: Context) {
    fun write(snapshot: IntegritySnapshot, format: IntegrityReportFormat): File {
        require(format != IntegrityReportFormat.PEM) { "PEM export is provided by the attestation repository" }
        val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(snapshot.scannedAtEpochMs))
        val extension = if (format == IntegrityReportFormat.JSON) "json" else "txt"
        val file = File(directory, "roottools-integrity-$timestamp.$extension")
        file.writeText(if (format == IntegrityReportFormat.JSON) toJson(snapshot).toString(2) else toText(snapshot))
        return file
    }

    fun latestReport(): File? = File(context.filesDir, "diagnostics")
        .listFiles { file -> file.isFile && file.name.startsWith("roottools-integrity-") && (file.extension == "txt" || file.extension == "json") }
        ?.maxByOrNull(File::lastModified)

    internal fun toText(snapshot: IntegritySnapshot): String = buildString {
        appendLine("RootTools Environment Integrity Report")
        appendLine("schema=1")
        appendLine("scanMode=${snapshot.mode.name}")
        appendLine("scannedAt=${snapshot.scannedAtEpochMs}")
        appendLine("durationMs=${snapshot.durationMs}")
        appendLine("baseline=${snapshot.baselineProfileName.orEmpty()}")
        appendLine("primary=${snapshot.primaryDisposition.name}")
        appendLine("critical=${snapshot.criticalCount} warn=${snapshot.warningCount} expected=${snapshot.expectedCount} drift=${snapshot.baselineDriftCount}")
        appendLine()
        appendLine("[SELF]")
        appendLine("package=${snapshot.signals.self.packageName}")
        appendLine("version=${snapshot.signals.self.versionName} (${snapshot.signals.self.versionCode})")
        appendLine("signingSha256=${snapshot.signals.self.signingSha256}")
        appendLine("apkSha256=${snapshot.signals.self.apkSha256}")
        appendLine("debuggable=${snapshot.signals.self.debuggable}")
        appendLine("selinuxContext=${snapshot.signals.self.selinuxContext}")
        appendLine()
        appendLine("[BOOT_OS]")
        snapshot.signals.boot.let { boot ->
            appendLine("model=${boot.frameworkModel} rootModel=${boot.rootModel}")
            appendLine("verifiedBootState=${boot.verifiedBootState}")
            appendLine("vbmetaDeviceState=${boot.vbmetaDeviceState}")
            appendLine("flashLocked=${boot.flashLocked}")
            appendLine("securityPatch=${boot.securityPatch}")
            appendLine("selinux=${boot.selinuxMode}")
        }
        appendLine()
        appendLine("[ROOT_RUNTIME]")
        snapshot.signals.rootRuntime.let { root ->
            appendLine("rootAvailable=${root.rootAvailable}")
            appendLine("provider=${root.provider.name}")
            appendLine("zygisk=${root.zygiskEnabled}")
            appendLine("modules=${root.modules.sorted().joinToString(",")}")
            appendLine("hookFramework=${root.hookFrameworkPackages.sorted().joinToString(",")}")
        }
        appendLine()
        appendLine("[RUNTIME]")
        snapshot.signals.runtime.let { runtime ->
            appendLine("tracerPid=${runtime.tracerPid}")
            appendLine("mappingCount=${runtime.mappingCount} rootMappingCount=${runtime.rootMappingCount}")
            appendLine("anonymousExecutable=${runtime.executableAnonymousCount}")
            appendLine("rwx=${runtime.writableExecutableMappings.size} deletedExec=${runtime.deletedExecutableMappings.size}")
            appendLine("markers=${runtime.strongMarkers.sorted().joinToString(",")}")
        }
        snapshot.signals.native?.let { native ->
            appendLine("nativeAvailable=${native.available}")
            appendLine("nativeLoadedElf=${native.loadedElfCount}")
            appendLine("nativeSelfSegments=${native.selfExecutableSegments} mismatches=${native.selfExecutableSegmentMismatches}")
            appendLine("nativeMarkers=${native.strongMarkers.sorted().joinToString(",")}")
        }
        appendLine()
        appendLine("[ENVIRONMENT]")
        snapshot.signals.environment.let { env ->
            appendLine("developerOptions=${env.developerOptionsEnabled} adb=${env.adbEnabled} wirelessAdb=${env.wirelessAdbEnabled}")
            appendLine("vpn=${env.vpnActive} proxyConfigured=${!env.globalProxy.isNullOrBlank()}")
            appendLine("packages=${env.packages.joinToString(",") { "${it.category.name}:${it.packageName}" }}")
            appendLine("mockLocationPackages=${env.mockLocationPackages.sorted().joinToString(",")}")
        }
        snapshot.signals.deviceSurface?.let { surface ->
            appendLine()
            appendLine("[DEVICE_SURFACE]")
            surface.hashes().toSortedMap().forEach { (key, value) -> appendLine("$key=$value") }
            appendLine("sensorCount=${surface.sensorCount} audioDeviceCount=${surface.audioDeviceCount} thermalZoneCount=${surface.thermalZoneCount}")
        }
        snapshot.signals.sandbox?.let { sandbox ->
            appendLine()
            appendLine("[SANDBOX]")
            appendLine("available=${sandbox.available} coherent=${sandbox.coherent} sentinel=${sandbox.sentinelVerified}")
            sandbox.evidence.forEach { appendLine("evidence=$it") }
        }
        snapshot.signals.attestation?.let { attestation ->
            appendLine()
            appendLine("[ATTESTATION]")
            listOf("standard" to attestation.standard, "strongBox" to attestation.strongBox).forEach { (name, record) ->
                if (record == null) return@forEach
                appendLine("$name.available=${record.available}")
                appendLine("$name.security=${record.securityLevel.name}")
                appendLine("$name.keyMintSecurity=${record.keyMintSecurityLevel.name}")
                appendLine("$name.challenge=${record.challengeMatched}")
                appendLine("$name.chainSignature=${record.chainSignatureValid} validity=${record.chainValidityValid} length=${record.chainLength}")
                appendLine("$name.trustAnchor=${record.trustAnchor.name} rkp=${record.rkpProvisioningPresent} revocationOnline=${record.revocationCheckedOnline}")
                appendLine("$name.deviceLocked=${record.deviceLocked} verifiedBootState=${record.verifiedBootState}")
            }
        }
        appendLine()
        appendLine("[FINDINGS]")
        snapshot.findings.forEach { finding ->
            appendLine("${finding.disposition.name}|${finding.category.name}|${finding.code.name}|confidence=${finding.confidence.name}|sources=${finding.sources.joinToString(",") { it.name }}")
            finding.observed?.let { appendLine("  observed=$it") }
            finding.expected?.let { appendLine("  expected=$it") }
            finding.evidence.forEach { appendLine("  evidence=$it") }
        }
        if (snapshot.unavailableProbes.isNotEmpty()) {
            appendLine()
            appendLine("[UNAVAILABLE_PROBES]")
            appendLine(snapshot.unavailableProbes.sorted().joinToString(","))
        }
    }

    internal fun toJson(snapshot: IntegritySnapshot): JSONObject = JSONObject().apply {
        put("schema", 1)
        put("scanMode", snapshot.mode.name)
        put("scannedAt", snapshot.scannedAtEpochMs)
        put("durationMs", snapshot.durationMs)
        put("baseline", snapshot.baselineProfileName)
        put("primary", snapshot.primaryDisposition.name)
        put("counts", JSONObject().apply {
            put("critical", snapshot.criticalCount)
            put("warn", snapshot.warningCount)
            put("expected", snapshot.expectedCount)
            put("drift", snapshot.baselineDriftCount)
        })
        put("self", JSONObject().apply {
            put("package", snapshot.signals.self.packageName)
            put("versionName", snapshot.signals.self.versionName)
            put("versionCode", snapshot.signals.self.versionCode)
            put("signingSha256", snapshot.signals.self.signingSha256)
            put("apkSha256", snapshot.signals.self.apkSha256)
            put("debuggable", snapshot.signals.self.debuggable)
        })
        put("boot", JSONObject().apply {
            put("model", snapshot.signals.boot.frameworkModel)
            put("rootModel", snapshot.signals.boot.rootModel)
            put("verifiedBootState", snapshot.signals.boot.verifiedBootState)
            put("vbmetaDeviceState", snapshot.signals.boot.vbmetaDeviceState)
            put("flashLocked", snapshot.signals.boot.flashLocked)
            put("securityPatch", snapshot.signals.boot.securityPatch)
            put("selinux", snapshot.signals.boot.selinuxMode)
        })
        put("rootRuntime", JSONObject().apply {
            put("rootAvailable", snapshot.signals.rootRuntime.rootAvailable)
            put("provider", snapshot.signals.rootRuntime.provider.name)
            put("zygisk", snapshot.signals.rootRuntime.zygiskEnabled)
            put("modules", JSONArray(snapshot.signals.rootRuntime.modules.sorted()))
            put("hookFramework", JSONArray(snapshot.signals.rootRuntime.hookFrameworkPackages.sorted()))
        })
        put("runtime", JSONObject().apply {
            put("tracerPid", snapshot.signals.runtime.tracerPid)
            put("mappingCount", snapshot.signals.runtime.mappingCount)
            put("rootMappingCount", snapshot.signals.runtime.rootMappingCount)
            put("anonymousExecutable", snapshot.signals.runtime.executableAnonymousCount)
            put("rwx", snapshot.signals.runtime.writableExecutableMappings.size)
            put("deletedExecutable", snapshot.signals.runtime.deletedExecutableMappings.size)
            put("markers", JSONArray(snapshot.signals.runtime.strongMarkers.sorted()))
        })
        snapshot.signals.native?.let { native ->
            put("native", JSONObject().apply {
                put("available", native.available)
                put("tracerPid", native.tracerPid)
                put("mappingCount", native.mappingCount)
                put("loadedElfCount", native.loadedElfCount)
                put("writableExecutableCount", native.writableExecutableCount)
                put("deletedExecutableCount", native.deletedExecutableCount)
                put("selfExecutableSegments", native.selfExecutableSegments)
                put("selfExecutableSegmentMismatches", native.selfExecutableSegmentMismatches)
                put("markers", JSONArray(native.strongMarkers.sorted()))
            })
        }
        put("environment", JSONObject().apply {
            val env = snapshot.signals.environment
            put("developerOptions", env.developerOptionsEnabled)
            put("adb", env.adbEnabled)
            put("wirelessAdb", env.wirelessAdbEnabled)
            put("vpn", env.vpnActive)
            put("proxyConfigured", !env.globalProxy.isNullOrBlank())
            put("packages", JSONArray(env.packages.map { "${it.category.name}:${it.packageName}" }))
        })
        snapshot.signals.deviceSurface?.let { surface ->
            put("deviceSurface", JSONObject(surface.hashes()).apply {
                put("sensorCount", surface.sensorCount)
                put("audioDeviceCount", surface.audioDeviceCount)
                put("thermalZoneCount", surface.thermalZoneCount)
            })
        }
        snapshot.signals.sandbox?.let { sandbox ->
            put("sandbox", JSONObject().apply {
                put("available", sandbox.available)
                put("coherent", sandbox.coherent)
                put("sentinelVerified", sandbox.sentinelVerified)
                put("evidence", JSONArray(sandbox.evidence))
            })
        }
        snapshot.signals.attestation?.let { attestation ->
            put("attestation", JSONObject().apply {
                attestation.standard?.let { put("standard", attestationJson(it)) }
                attestation.strongBox?.let { put("strongBox", attestationJson(it)) }
            })
        }
        put("findings", JSONArray(snapshot.findings.map { finding ->
            JSONObject().apply {
                put("code", finding.code.name)
                put("category", finding.category.name)
                put("disposition", finding.disposition.name)
                put("confidence", finding.confidence.name)
                put("sources", JSONArray(finding.sources.map { it.name }))
                put("observed", finding.observed)
                put("expected", finding.expected)
                put("evidence", JSONArray(finding.evidence))
            }
        }))
        put("unavailableProbes", JSONArray(snapshot.unavailableProbes.sorted()))
    }

    private fun attestationJson(record: com.arthur.roottools.feature.integrity.model.AttestationRecord): JSONObject = JSONObject().apply {
        put("available", record.available)
        put("securityLevel", record.securityLevel.name)
        put("keyMintSecurityLevel", record.keyMintSecurityLevel.name)
        put("challengeMatched", record.challengeMatched)
        put("chainSignatureValid", record.chainSignatureValid)
        put("chainValidityValid", record.chainValidityValid)
        put("chainLength", record.chainLength)
        put("trustAnchor", record.trustAnchor.name)
        put("rootSpkiSha256", record.rootSpkiSha256)
        put("revokedSerials", JSONArray(record.revokedSerials.sorted()))
        put("revocationCheckedOnline", record.revocationCheckedOnline)
        put("rkpProvisioningPresent", record.rkpProvisioningPresent)
        put("deviceLocked", record.deviceLocked)
        put("verifiedBootState", record.verifiedBootState)
        put("verifiedBootKeySha256", record.verifiedBootKeySha256)
        put("verifiedBootHashSha256", record.verifiedBootHashSha256)
        put("error", record.error)
    }
}
