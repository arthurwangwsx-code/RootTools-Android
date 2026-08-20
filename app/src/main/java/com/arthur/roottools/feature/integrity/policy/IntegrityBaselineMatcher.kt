package com.arthur.roottools.feature.integrity.policy

import com.arthur.roottools.feature.integrity.model.IntegrityBaseline
import com.arthur.roottools.feature.integrity.model.IntegritySignals

data class IntegrityBaselineDiff(
    val field: String,
    val observed: String,
    val expected: String,
)

object IntegrityBaselineMatcher {
    fun capture(profileName: String, signals: IntegritySignals, nowEpochMs: Long): IntegrityBaseline {
        val environmentPackages = signals.environment.packages
            .map { it.packageName }
            .toSortedSet()
        return IntegrityBaseline(
            profileName = profileName,
            trustedAtEpochMs = nowEpochMs,
            packageName = signals.self.packageName,
            versionCode = signals.self.versionCode,
            signingSha256 = signals.self.signingSha256,
            apkSha256 = signals.self.apkSha256,
            buildFingerprint = signals.boot.buildFingerprint,
            securityPatch = signals.boot.securityPatch,
            verifiedBootState = signals.boot.verifiedBootState,
            vbmetaDeviceState = signals.boot.vbmetaDeviceState,
            flashLocked = signals.boot.flashLocked,
            selinuxMode = signals.boot.selinuxMode,
            rootExpected = signals.rootRuntime.rootAvailable,
            rootProvider = signals.rootRuntime.provider,
            rootModules = signals.rootRuntime.modules.toSortedSet(),
            hookFrameworkPackages = signals.rootRuntime.hookFrameworkPackages.toSortedSet(),
            expectedEnvironmentPackages = environmentPackages,
            adbExpected = signals.environment.adbEnabled,
            vpnExpected = signals.environment.vpnActive,
            deviceSurfaceHashes = signals.deviceSurface?.hashes().orEmpty().toSortedMap(),
        )
    }

    fun diff(baseline: IntegrityBaseline, signals: IntegritySignals): List<IntegrityBaselineDiff> = buildList {
        addIfChanged("signing", signals.self.signingSha256, baseline.signingSha256)
        if (signals.self.versionCode == baseline.versionCode) {
            addIfChanged("apk", signals.self.apkSha256, baseline.apkSha256)
        }
        addIfChanged("buildFingerprint", signals.boot.buildFingerprint, baseline.buildFingerprint)
        addIfChanged("securityPatch", signals.boot.securityPatch, baseline.securityPatch)
        addIfChanged("verifiedBootState", signals.boot.verifiedBootState, baseline.verifiedBootState)
        addIfChanged("vbmetaDeviceState", signals.boot.vbmetaDeviceState, baseline.vbmetaDeviceState)
        addIfChanged("flashLocked", signals.boot.flashLocked?.toString().orEmpty(), baseline.flashLocked?.toString().orEmpty())
        addIfChanged("selinux", signals.boot.selinuxMode, baseline.selinuxMode)
        addIfChanged("rootProvider", signals.rootRuntime.provider.name, baseline.rootProvider.name)
        addIfChanged("rootModules", signals.rootRuntime.modules.sorted().joinToString(","), baseline.rootModules.sorted().joinToString(","))
        addIfChanged(
            "environmentPackages",
            signals.environment.packages.map { it.packageName }.sorted().joinToString(","),
            baseline.expectedEnvironmentPackages.sorted().joinToString(","),
        )
        signals.deviceSurface?.hashes()?.forEach { (key, observed) ->
            baseline.deviceSurfaceHashes[key]?.let { expected -> addIfChanged("surface.$key", observed, expected) }
        }
    }

    private fun MutableList<IntegrityBaselineDiff>.addIfChanged(field: String, observed: String, expected: String) {
        if (observed != expected) add(IntegrityBaselineDiff(field, observed, expected))
    }
}
