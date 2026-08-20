package com.arthur.roottools.feature.integrity.data

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.arthur.roottools.BuildConfig
import com.arthur.roottools.data.DeviceIntegrityRepository
import com.arthur.roottools.data.ModuleCenterRepository
import com.arthur.roottools.feature.integrity.model.AttestationRecord
import com.arthur.roottools.feature.integrity.model.AttestationSecurityLevel
import com.arthur.roottools.feature.integrity.model.AttestationSignals
import com.arthur.roottools.feature.integrity.model.AttestationTrustAnchor
import com.arthur.roottools.feature.integrity.model.BootOsSignals
import com.arthur.roottools.feature.integrity.model.DeviceSurfaceSignals
import com.arthur.roottools.feature.integrity.model.EnvironmentContextSignals
import com.arthur.roottools.feature.integrity.model.EnvironmentPackageCategory
import com.arthur.roottools.feature.integrity.model.EnvironmentPackageSignal
import com.arthur.roottools.feature.integrity.model.IntegrityBaseline
import com.arthur.roottools.feature.integrity.model.IntegrityScanMode
import com.arthur.roottools.feature.integrity.model.IntegritySignals
import com.arthur.roottools.feature.integrity.model.IntegritySnapshot
import com.arthur.roottools.feature.integrity.model.NativeIntegritySignals
import com.arthur.roottools.feature.integrity.model.RootRuntimeProvider
import com.arthur.roottools.feature.integrity.model.RootRuntimeSignals
import com.arthur.roottools.feature.integrity.model.RuntimeIntegritySignals
import com.arthur.roottools.feature.integrity.model.SandboxSignals
import com.arthur.roottools.feature.integrity.model.SelfIdentitySignals
import com.arthur.roottools.feature.integrity.nativebridge.NativeIntegrityBridge
import com.arthur.roottools.feature.integrity.policy.IntegrityRiskEngine
import com.arthur.roottools.model.AttestationRootAuthority
import com.arthur.roottools.model.KeyAttestationResult
import com.arthur.roottools.root.RootShell
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs

class IntegrityRepository(
    private val context: Context,
    private val shell: RootShell,
) {
    private val moduleRepository = ModuleCenterRepository(shell)
    private val attestationRepository = DeviceIntegrityRepository(context, shell)

    suspend fun scan(
        mode: IntegrityScanMode,
        baseline: IntegrityBaseline?,
        previous: IntegritySnapshot? = null,
    ): IntegritySnapshot {
        val started = System.currentTimeMillis()
        val unavailable = mutableSetOf<String>()
        val self = runCatching { collectSelfIdentity() }
            .getOrElse {
                unavailable += "self_identity"
                SelfIdentitySignals()
            }
        val boot = runCatching { collectBootOs() }
            .getOrElse {
                unavailable += "boot_os"
                BootOsSignals(frameworkModel = Build.MODEL, buildFingerprint = Build.FINGERPRINT, securityPatch = Build.VERSION.SECURITY_PATCH)
            }
        val rootRuntime = runCatching { collectRootRuntime() }
            .getOrElse {
                unavailable += "root_runtime"
                RootRuntimeSignals()
            }
        val runtime = runCatching { collectRuntimeIntegrity() }
            .getOrElse {
                unavailable += "runtime"
                RuntimeIntegritySignals()
            }
        val environment = runCatching { collectEnvironmentContext() }
            .getOrElse {
                unavailable += "environment"
                EnvironmentContextSignals()
            }

        val deviceSurface = when (mode) {
            IntegrityScanMode.DEEP -> runCatching { collectDeviceSurface() }
                .getOrElse {
                    unavailable += "device_surface"
                    null
                }
            else -> previous?.signals?.deviceSurface
        }
        val sandbox = when (mode) {
            IntegrityScanMode.DEEP -> runCatching { collectSandbox() }
                .getOrElse {
                    unavailable += "sandbox"
                    null
                }
            else -> previous?.signals?.sandbox
        }
        val native = when (mode) {
            IntegrityScanMode.NATIVE -> NativeIntegrityBridge.collect().also {
                if (!it.available) unavailable += "native"
            }
            else -> previous?.signals?.native
        }
        val attestation = when (mode) {
            IntegrityScanMode.ATTESTATION -> runCatching { collectAttestation() }
                .getOrElse {
                    unavailable += "attestation"
                    AttestationSignals(
                        standard = AttestationRecord(available = false, error = it.message ?: it.javaClass.simpleName),
                    )
                }
            else -> previous?.signals?.attestation
        }

        val signals = IntegritySignals(
            self = self,
            boot = boot,
            rootRuntime = rootRuntime,
            runtime = runtime,
            environment = environment,
            deviceSurface = deviceSurface,
            sandbox = sandbox,
            native = native,
            attestation = attestation,
        )
        return IntegritySnapshot(
            scannedAtEpochMs = System.currentTimeMillis(),
            mode = mode,
            durationMs = System.currentTimeMillis() - started,
            signals = signals,
            findings = IntegrityRiskEngine.evaluate(signals, baseline),
            unavailableProbes = unavailable,
            baselineProfileName = baseline?.profileName,
        )
    }

    suspend fun exportAttestationPem(): File? = attestationRepository.exportLastCertificateChains()

    private suspend fun collectSelfIdentity(): SelfIdentitySignals {
        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        val appInfo = packageInfo.applicationInfo ?: context.applicationInfo
        val signer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.firstOrNull()?.toByteArray()
        }
        val baseApk = appInfo.sourceDir.orEmpty()
        val rootPaths = if (shell.isAvailable(timeoutSeconds = 2)) {
            shell.execute("pm path ${BuildConfig.APPLICATION_ID} 2>/dev/null", timeoutSeconds = 3)
                .output
                .lineSequence()
                .map { it.removePrefix("package:").trim() }
                .filter(String::isNotBlank)
                .toList()
        } else emptyList()
        val rootContext = if (shell.isAvailable(timeoutSeconds = 2)) {
            shell.execute("cat /proc/${Process.myPid()}/attr/current 2>/dev/null", timeoutSeconds = 2).output.trim()
        } else ""
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(context.packageName)
        }
        return SelfIdentitySignals(
            packageName = packageInfo.packageName,
            compiledPackageName = BuildConfig.APPLICATION_ID,
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = packageInfo.longVersionCode,
            compiledVersionName = BuildConfig.VERSION_NAME,
            compiledVersionCode = BuildConfig.VERSION_CODE.toLong(),
            signingSha256 = signer?.let(::sha256).orEmpty(),
            apkSha256 = baseApk.takeIf(String::isNotBlank)?.let { sha256(File(it)) }.orEmpty(),
            installerPackage = installer,
            baseApkPath = baseApk,
            rootPackagePaths = rootPaths,
            packagePathConsistent = rootPaths.isEmpty() || baseApk in rootPaths,
            debuggable = appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            uid = Process.myUid(),
            selinuxContext = rootContext,
        )
    }

    private suspend fun collectBootOs(): BootOsSignals {
        val values = if (shell.isAvailable(timeoutSeconds = 2)) {
            shell.execute(BOOT_COMMAND, timeoutSeconds = 4).output
                .lineSequence()
                .mapNotNull(::parseKeyValueLine)
                .toMap()
        } else emptyMap()
        return BootOsSignals(
            frameworkModel = Build.MODEL,
            rootModel = values["model"].orEmpty(),
            buildFingerprint = Build.FINGERPRINT,
            productFingerprint = values["productFingerprint"].orEmpty(),
            vendorFingerprint = values["vendorFingerprint"].orEmpty(),
            systemFingerprint = values["systemFingerprint"].orEmpty(),
            securityPatch = Build.VERSION.SECURITY_PATCH,
            verifiedBootState = values["verifiedBootState"].orEmpty(),
            vbmetaDeviceState = values["vbmetaDeviceState"].orEmpty(),
            flashLocked = values["flashLocked"].toBooleanFlag(),
            buildType = values["buildType"] ?: Build.TYPE,
            buildTags = values["buildTags"] ?: Build.TAGS.orEmpty(),
            roDebuggable = values["roDebuggable"].toBooleanFlag(),
            selinuxMode = values["selinux"].orEmpty(),
        )
    }

    private suspend fun collectRootRuntime(): RootRuntimeSignals {
        val rootAvailable = shell.isAvailable(timeoutSeconds = 3)
        if (!rootAvailable) return RootRuntimeSignals()
        val runtime = shell.execute(ROOT_RUNTIME_COMMAND, timeoutSeconds = 5).output
            .lineSequence()
            .mapNotNull(::parseKeyValueLine)
            .toMap()
        val provider = when {
            runtime["magisk"] == "1" -> RootRuntimeProvider.MAGISK
            runtime["kernelsu"] == "1" -> RootRuntimeProvider.KERNEL_SU
            runtime["apatch"] == "1" -> RootRuntimeProvider.APATCH
            else -> RootRuntimeProvider.UNKNOWN
        }
        val modules = runCatching { moduleRepository.read() }.getOrNull()
        val installedPackages = installedPackageNames()
        val hooks = HOOK_FRAMEWORK_PACKAGES.keys.filterTo(mutableSetOf()) { it in installedPackages }
        val moduleIds = buildSet {
            modules?.magiskModules?.forEach { add("magisk:${it.id}") }
            modules?.vectorModules?.forEach { add("vector:${it.packageName}:${if (it.enabled) "on" else "off"}") }
        }
        return RootRuntimeSignals(
            rootAvailable = true,
            provider = provider,
            suVersion = runtime["suVersion"].orEmpty(),
            zygiskEnabled = runtime["zygisk"].toBooleanFlag(),
            modules = moduleIds,
            hookFrameworkPackages = hooks,
            vectorActive = modules?.vectorActive == true,
        )
    }

    private suspend fun collectRuntimeIntegrity(): RuntimeIntegritySignals {
        val localMaps = runCatching { File("/proc/self/maps").readLines() }.getOrDefault(emptyList())
        val tracerPid = runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("TracerPid:") }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.toIntOrNull()
                    ?: 0
            }
        }.getOrDefault(0)
        val executableAnonymous = localMaps.count { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 6)
            val perms = parts.getOrNull(1).orEmpty()
            val path = parts.getOrNull(5).orEmpty()
            'x' in perms && (path.isBlank() || path.startsWith("[anon") || path.startsWith("/memfd:"))
        }
        val rwx = localMaps.filter { line ->
            val perms = line.trim().split(Regex("\\s+"), limit = 3).getOrNull(1).orEmpty()
            'r' in perms && 'w' in perms && 'x' in perms
        }.take(20)
        val deleted = localMaps.filter { line ->
            val perms = line.trim().split(Regex("\\s+"), limit = 3).getOrNull(1).orEmpty()
            'x' in perms && "(deleted)" in line
        }.take(20)
        val strongMarkers = buildSet {
            localMaps.forEach { line ->
                val lower = line.lowercase()
                RUNTIME_MARKERS.forEach { marker -> if (marker in lower) add(marker) }
            }
        }
        val rootCount = if (shell.isAvailable(timeoutSeconds = 2)) {
            shell.execute("wc -l < /proc/${Process.myPid()}/maps 2>/dev/null", timeoutSeconds = 2)
                .output.trim().toIntOrNull()
        } else null
        return RuntimeIntegritySignals(
            tracerPid = tracerPid,
            mappingCount = localMaps.size,
            rootMappingCount = rootCount,
            executableAnonymousCount = executableAnonymous,
            writableExecutableMappings = rwx,
            deletedExecutableMappings = deleted,
            strongMarkers = strongMarkers,
            mapsCrossCheckConsistent = rootCount == null || abs(rootCount - localMaps.size) <= 6,
        )
    }

    private suspend fun collectEnvironmentContext(): EnvironmentContextSignals {
        val resolver = context.contentResolver
        val installed = installedPackageNames()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val vpnPackages = buildSet {
            connectivity?.allNetworks.orEmpty().forEach { network ->
                val capabilities = connectivity?.getNetworkCapabilities(network) ?: return@forEach
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@forEach
                val uid = runCatching { capabilities.ownerUid }.getOrNull() ?: return@forEach
                context.packageManager.getPackagesForUid(uid)?.forEach(::add)
            }
        }
        val proxyHost = Settings.Global.getString(resolver, Settings.Global.HTTP_PROXY)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val mockLocationPackages = if (shell.isAvailable(timeoutSeconds = 2)) {
            shell.execute("cmd appops query-op android:mock_location allow 2>/dev/null", timeoutSeconds = 3)
                .output
                .lineSequence()
                .map { it.trim().substringBefore(':').trim() }
                .filter { PACKAGE_NAME_REGEX.matches(it) }
                .toSet()
        } else emptySet()
        val packages = PACKAGE_TAXONOMY.mapNotNull { (packageName, category) ->
            packageName.takeIf { it in installed }?.let { EnvironmentPackageSignal(it, category) }
        }
        return EnvironmentContextSignals(
            developerOptionsEnabled = Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
            adbEnabled = Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1,
            wirelessAdbEnabled = Settings.Global.getInt(resolver, "adb_wifi_enabled", 0) == 1,
            vpnActive = vpnPackages.isNotEmpty(),
            vpnPackages = vpnPackages,
            globalProxy = proxyHost,
            mockLocationPackages = mockLocationPackages,
            accessibilityPackages = secureComponentPackages(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            notificationListenerPackages = secureComponentPackages("enabled_notification_listeners"),
            packages = packages,
        )
    }

    private suspend fun collectDeviceSurface(): DeviceSurfaceSignals {
        val rootAvailable = shell.isAvailable(timeoutSeconds = 2)
        val cpu = if (rootAvailable) shell.execute(CPU_SURFACE_COMMAND, timeoutSeconds = 4).output else ""
        val thermal = if (rootAvailable) shell.execute(THERMAL_SURFACE_COMMAND, timeoutSeconds = 4).output else ""
        val gpu = if (rootAvailable) shell.execute(GPU_SURFACE_COMMAND, timeoutSeconds = 6).output else ""
        val vulkan = if (rootAvailable) shell.execute(VULKAN_SURFACE_COMMAND, timeoutSeconds = 5).output else ""
        val battery = if (rootAvailable) shell.execute(BATTERY_SURFACE_COMMAND, timeoutSeconds = 4).output else ""
        val sensors = context.getSystemService(SensorManager::class.java)
            ?.getSensorList(Sensor.TYPE_ALL)
            .orEmpty()
            .map { sensor ->
                listOf(
                    sensor.type,
                    sensor.name,
                    sensor.vendor,
                    sensor.version,
                    sensor.isWakeUpSensor,
                    sensor.isDynamicSensor,
                    sensor.minDelay,
                    sensor.maxDelay,
                ).joinToString("|")
            }
            .sorted()
        val audioManager = context.getSystemService(AudioManager::class.java)
        val audio = buildList {
            addAll(audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS).orEmpty())
            addAll(audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty())
        }
            .distinctBy { it.id }
            .map { device -> "${device.type}|${device.productName}" }
            .sorted()
        val thermalCount = thermal.lineSequence().count { it.startsWith("zone=") }
        return DeviceSurfaceSignals(
            cpuTopologyHash = cpu.takeIf(String::isNotBlank)?.let(::sha256),
            thermalZoneHash = thermal.takeIf(String::isNotBlank)?.let(::sha256),
            gpuRendererHash = gpu.takeIf(String::isNotBlank)?.let(::sha256),
            vulkanCapabilityHash = vulkan.takeIf(String::isNotBlank)?.let(::sha256),
            sensorShapeHash = sensors.takeIf(List<String>::isNotEmpty)?.joinToString("\n")?.let(::sha256),
            audioCapabilityHash = audio.takeIf(List<String>::isNotEmpty)?.joinToString("\n")?.let(::sha256),
            batterySupplyHash = battery.takeIf(String::isNotBlank)?.let(::sha256),
            sensorCount = sensors.size,
            audioDeviceCount = audio.size,
            thermalZoneCount = thermalCount,
            gpuSummary = gpu.lineSequence().filter(String::isNotBlank).take(3).joinToString(" · ").take(240),
        )
    }

    private suspend fun collectSandbox(): SandboxSignals {
        val dataDir = context.applicationInfo.dataDir.orEmpty()
        val deviceProtected = context.createDeviceProtectedStorageContext().applicationInfo.dataDir.orEmpty()
        val canonical = runCatching { File(dataDir).canonicalPath }.getOrDefault(dataDir)
        val localStat = runCatching { android.system.Os.stat(dataDir) }.getOrNull()
            ?.let { "${it.st_dev}:${it.st_ino}:${it.st_mode}:${it.st_uid}:${it.st_gid}" }
        val rootStat = if (shell.isAvailable(timeoutSeconds = 2)) {
            shell.execute("stat -c '%d:%i:%a:%u:%g' /data/user/0/${BuildConfig.APPLICATION_ID} 2>/dev/null", timeoutSeconds = 3)
                .output.trim().takeIf(String::isNotBlank)
        } else null

        val sentinel = File(context.noBackupFilesDir, SENTINEL_FILE)
        val nonce = UUID.randomUUID().toString()
        val rootSentinel = if (shell.isAvailable(timeoutSeconds = 2)) {
            try {
                sentinel.writeText(nonce)
                shell.execute("cat /data/user/0/${BuildConfig.APPLICATION_ID}/no_backup/$SENTINEL_FILE 2>/dev/null", timeoutSeconds = 3)
                    .output.trim() == nonce
            } finally {
                sentinel.delete()
            }
        } else null
        val evidence = buildList {
            if (canonical != dataDir) add("canonical=$canonical")
            if (localStat != null && rootStat != null && !statEquivalent(localStat, rootStat)) add("stat local=$localStat root=$rootStat")
            if (rootSentinel == false) add("sentinel cross-read mismatch")
        }
        return SandboxSignals(
            available = dataDir.isNotBlank(),
            coherent = canonical == dataDir && (localStat == null || rootStat == null || statEquivalent(localStat, rootStat)) && rootSentinel != false,
            dataDir = dataDir,
            deviceProtectedDir = deviceProtected,
            canonicalDataDir = canonical,
            appDataStat = localStat,
            rootDataStat = rootStat,
            sentinelVerified = rootSentinel,
            evidence = evidence,
        )
    }

    private suspend fun collectAttestation(): AttestationSignals {
        val snapshot = attestationRepository.scan()
        return AttestationSignals(
            standard = snapshot.standard.toIntegrityRecord(snapshot.onlineVerificationError),
            strongBox = snapshot.strongBox.toIntegrityRecord(snapshot.onlineVerificationError),
        )
    }

    private fun KeyAttestationResult.toIntegrityRecord(onlineError: String?): AttestationRecord = AttestationRecord(
        requestedStrongBox = requestedStrongBox,
        available = available,
        securityLevel = attestationSecurityLevel.toIntegrityLevel(),
        keyMintSecurityLevel = keyMintSecurityLevel.toIntegrityLevel(),
        attestationVersion = attestationVersion,
        keyMintVersion = keyMintVersion,
        challengeMatched = challengeMatches == true,
        chainSignatureValid = chainSignatureValid,
        chainValidityValid = chainValidityValid,
        chainLength = certificates.size,
        trustAnchor = rootAuthority.toIntegrityTrustAnchor(),
        rootSubject = certificates.lastOrNull()?.subject.orEmpty(),
        rootSpkiSha256 = rootSpkiSha256.orEmpty(),
        revokedSerials = certificates.filter { it.revoked }.mapTo(mutableSetOf()) { it.serialHex },
        revocationCheckedOnline = revocationChecked,
        onlineVerificationAvailable = onlineError == null && (onlineTrustAnchorChecked || revocationChecked),
        rkpProvisioningPresent = remoteProvisioned,
        deviceLocked = deviceLocked,
        verifiedBootState = verifiedBootState.displayName,
        verifiedBootKeySha256 = verifiedBootKeySha256,
        verifiedBootHashSha256 = verifiedBootHashSha256,
        osVersion = osVersion?.filter(Char::isDigit)?.toLongOrNull(),
        osPatchLevel = osPatchLevel?.filter(Char::isDigit)?.toLongOrNull(),
        vendorPatchLevel = vendorPatchLevel?.filter(Char::isDigit)?.toLongOrNull(),
        bootPatchLevel = bootPatchLevel?.filter(Char::isDigit)?.toLongOrNull(),
        error = error,
    )

    private fun com.arthur.roottools.model.AttestationSecurityLevel.toIntegrityLevel(): AttestationSecurityLevel = when (this) {
        com.arthur.roottools.model.AttestationSecurityLevel.SOFTWARE -> AttestationSecurityLevel.SOFTWARE
        com.arthur.roottools.model.AttestationSecurityLevel.TRUSTED_ENVIRONMENT -> AttestationSecurityLevel.TRUSTED_ENVIRONMENT
        com.arthur.roottools.model.AttestationSecurityLevel.STRONGBOX -> AttestationSecurityLevel.STRONGBOX
        com.arthur.roottools.model.AttestationSecurityLevel.UNKNOWN -> AttestationSecurityLevel.UNKNOWN
    }

    private fun AttestationRootAuthority.toIntegrityTrustAnchor(): AttestationTrustAnchor = when (this) {
        AttestationRootAuthority.GOOGLE -> AttestationTrustAnchor.GOOGLE
        AttestationRootAuthority.GOOGLE_RKP -> AttestationTrustAnchor.GOOGLE_RKP
        AttestationRootAuthority.OEM, AttestationRootAuthority.KNOX -> AttestationTrustAnchor.OEM
        AttestationRootAuthority.AOSP -> AttestationTrustAnchor.AOSP_SOFTWARE
        AttestationRootAuthority.UNKNOWN -> AttestationTrustAnchor.UNKNOWN
    }

    private fun secureComponentPackages(key: String): Set<String> = Settings.Secure.getString(context.contentResolver, key)
        .orEmpty()
        .split(':')
        .map { it.substringBefore('/').trim() }
        .filter { PACKAGE_NAME_REGEX.matches(it) }
        .toSet()

    @Suppress("DEPRECATION")
    private fun installedPackageNames(): Set<String> = runCatching {
        context.packageManager
            .getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .mapTo(mutableSetOf()) { it.packageName }
    }.getOrDefault(emptySet())

    private fun parseKeyValueLine(line: String): Pair<String, String>? {
        val index = line.indexOf('=')
        if (index <= 0) return null
        return line.substring(0, index).trim() to line.substring(index + 1).trim()
    }

    private fun String?.toBooleanFlag(): Boolean? = when (this?.trim()?.lowercase()) {
        "1", "true", "locked", "on", "enabled" -> true
        "0", "false", "unlocked", "off", "disabled" -> false
        else -> null
    }

    private fun statEquivalent(local: String, root: String): Boolean {
        val localParts = local.split(':')
        val rootParts = root.split(':')
        if (localParts.size < 2 || rootParts.size < 2) return true
        return localParts[0] == rootParts[0] && localParts[1] == rootParts[1]
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun sha256(text: String): String = sha256(text.toByteArray())
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val SENTINEL_FILE = "roottools_integrity_sentinel"
        val PACKAGE_NAME_REGEX = Regex("[A-Za-z0-9._]+")
        val RUNTIME_MARKERS = setOf("frida", "gadget", "substrate", "xposed", "lsposed", "riru", "zygisk", "edxposed")

        val HOOK_FRAMEWORK_PACKAGES = mapOf(
            "org.lsposed.manager" to EnvironmentPackageCategory.HOOK_FRAMEWORK,
            "org.meowcat.edxposed.manager" to EnvironmentPackageCategory.HOOK_FRAMEWORK,
            "de.robv.android.xposed.installer" to EnvironmentPackageCategory.HOOK_FRAMEWORK,
        )

        val PACKAGE_TAXONOMY = linkedMapOf(
            "com.topjohnwu.magisk" to EnvironmentPackageCategory.ROOT_RUNTIME,
            "io.github.vvb2060.magisk" to EnvironmentPackageCategory.ROOT_RUNTIME,
            "me.weishu.kernelsu" to EnvironmentPackageCategory.ROOT_RUNTIME,
            "com.rifsxd.ksunext" to EnvironmentPackageCategory.ROOT_RUNTIME,
            "me.bmax.apatch" to EnvironmentPackageCategory.ROOT_RUNTIME,
            "me.garfieldhan.apatch.next" to EnvironmentPackageCategory.ROOT_RUNTIME,
            "org.lsposed.manager" to EnvironmentPackageCategory.HOOK_FRAMEWORK,
            "org.meowcat.edxposed.manager" to EnvironmentPackageCategory.HOOK_FRAMEWORK,
            "de.robv.android.xposed.installer" to EnvironmentPackageCategory.HOOK_FRAMEWORK,
            "com.tsng.hidemyapplist" to EnvironmentPackageCategory.ROOT_HIDING,
            "com.tsng.hidemyroot" to EnvironmentPackageCategory.ROOT_HIDING,
            "io.virtualapp" to EnvironmentPackageCategory.VIRTUALIZATION,
            "io.virtualapp.sandvxposed64" to EnvironmentPackageCategory.VIRTUALIZATION,
            "org.autojs.autojs" to EnvironmentPackageCategory.AUTOMATION,
            "org.autojs.autojspro" to EnvironmentPackageCategory.AUTOMATION,
            "com.github.uiautomator" to EnvironmentPackageCategory.AUTOMATION,
            "com.github.uiautomator2" to EnvironmentPackageCategory.AUTOMATION,
            "com.genymobile.scrcpy" to EnvironmentPackageCategory.REMOTE_CONTROL,
            "com.sigma_rt.totalcontrol" to EnvironmentPackageCategory.REMOTE_CONTROL,
            "com.android1500.androidfaker" to EnvironmentPackageCategory.DEVICE_SPOOFING,
            "com.lerist.fakeloction" to EnvironmentPackageCategory.DEVICE_SPOOFING,
        )

        val BOOT_COMMAND = """
            echo model=${'$'}(getprop ro.product.model)
            echo verifiedBootState=${'$'}(getprop ro.boot.verifiedbootstate)
            echo vbmetaDeviceState=${'$'}(getprop ro.boot.vbmeta.device_state)
            echo flashLocked=${'$'}(getprop ro.boot.flash.locked)
            echo buildType=${'$'}(getprop ro.build.type)
            echo buildTags=${'$'}(getprop ro.build.tags)
            echo roDebuggable=${'$'}(getprop ro.debuggable)
            echo productFingerprint=${'$'}(getprop ro.product.build.fingerprint)
            echo vendorFingerprint=${'$'}(getprop ro.vendor.build.fingerprint)
            echo systemFingerprint=${'$'}(getprop ro.system.build.fingerprint)
            echo selinux=${'$'}(getenforce 2>/dev/null)
        """.trimIndent()

        val ROOT_RUNTIME_COMMAND = """
            command -v magisk >/dev/null 2>&1 && echo magisk=1 || echo magisk=0
            [ -d /data/adb/ksu ] || command -v ksud >/dev/null 2>&1; [ ${'$'}? -eq 0 ] && echo kernelsu=1 || echo kernelsu=0
            [ -d /data/adb/ap ] || command -v apd >/dev/null 2>&1; [ ${'$'}? -eq 0 ] && echo apatch=1 || echo apatch=0
            v=${'$'}(magisk -v 2>/dev/null | head -n 1); [ -n "${'$'}v" ] || v=${'$'}(ksud --version 2>/dev/null | head -n 1); [ -n "${'$'}v" ] || v=${'$'}(apd --version 2>/dev/null | head -n 1); echo suVersion=${'$'}v
            z=${'$'}(magisk --sqlite "SELECT value FROM settings WHERE key='zygisk';" 2>/dev/null | tail -n 1)
            case "${'$'}z" in *1*) echo zygisk=1 ;; *0*) echo zygisk=0 ;; *) echo zygisk= ;; esac
        """.trimIndent()

        val CPU_SURFACE_COMMAND = """
            for d in /sys/devices/system/cpu/cpu[0-9]*; do
              [ -d "${'$'}d" ] || continue
              cpu=${'$'}{d##*cpu}
              pkg=${'$'}(cat "${'$'}d/topology/physical_package_id" 2>/dev/null)
              core=${'$'}(cat "${'$'}d/topology/core_id" 2>/dev/null)
              sib=${'$'}(cat "${'$'}d/topology/thread_siblings_list" 2>/dev/null)
              max=${'$'}(cat "${'$'}d/cpufreq/cpuinfo_max_freq" 2>/dev/null)
              echo "cpu=${'$'}cpu|pkg=${'$'}pkg|core=${'$'}core|sib=${'$'}sib|max=${'$'}max"
            done
        """.trimIndent()

        val THERMAL_SURFACE_COMMAND = """
            for d in /sys/class/thermal/thermal_zone*; do
              [ -d "${'$'}d" ] || continue
              echo "zone=${'$'}{d##*thermal_zone}|type=${'$'}(cat "${'$'}d/type" 2>/dev/null)"
            done
        """.trimIndent()

        val GPU_SURFACE_COMMAND = """
            getprop | grep -Ei 'gpu|egl|gles' | sort | head -n 50
            dumpsys SurfaceFlinger 2>/dev/null | grep -Ei 'GLES|GPU|Vulkan' | head -n 30
        """.trimIndent()

        val VULKAN_SURFACE_COMMAND = """
            echo ro.hardware.vulkan=${'$'}(getprop ro.hardware.vulkan)
            echo ro.opengles.version=${'$'}(getprop ro.opengles.version)
            pm list features 2>/dev/null | grep -i vulkan | sort
        """.trimIndent()

        val BATTERY_SURFACE_COMMAND = """
            for d in /sys/class/power_supply/*; do
              [ -d "${'$'}d" ] || continue
              echo "supply=${'$'}{d##*/}|type=${'$'}(cat "${'$'}d/type" 2>/dev/null)"
            done
        """.trimIndent()
    }
}
