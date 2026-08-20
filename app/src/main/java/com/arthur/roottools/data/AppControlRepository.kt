package com.arthur.roottools.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.arthur.roottools.model.AppControlDetail
import com.arthur.roottools.model.AppInventoryItem
import com.arthur.roottools.model.AppInventorySnapshot
import com.arthur.roottools.model.AppSourceStatus
import com.arthur.roottools.root.RootShell
import java.security.MessageDigest
import java.io.File

class AppControlRepository(
    context: Context,
    private val rootShell: RootShell,
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)

    suspend fun readInventory(allowRootRunningProbe: Boolean = true): AppInventorySnapshot {
        val packages = runCatching { installedPackages() }.getOrDefault(emptyList())
        val runningProbe = if (allowRootRunningProbe) rootShell.execute("ps -A -o NAME 2>/dev/null") else null
        val runningProcessNames = if (runningProbe?.success == true) {
            runningProbe.output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "NAME" }
                .toSet()
        } else {
            activityManager.runningAppProcesses.orEmpty().flatMap { process ->
                buildList {
                    add(process.processName)
                    process.pkgList?.let(::addAll)
                }
            }.toSet()
        }
        val runningPackages = runningProcessNames.mapTo(mutableSetOf()) { it.substringBefore(':') }

        val apps = packages.mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            val packageName = info.packageName
            AppInventoryItem(
                packageName = packageName,
                label = runCatching { app.loadLabel(packageManager).toString() }.getOrDefault(packageName),
                versionName = info.versionName.orEmpty(),
                versionCode = longVersionCode(info),
                uid = app.uid,
                targetSdk = app.targetSdkVersion,
                minSdk = app.minSdkVersion,
                firstInstallTimeMs = info.firstInstallTime,
                lastUpdateTimeMs = info.lastUpdateTime,
                systemApp = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                updatedSystemApp = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                debuggable = app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                persistent = app.flags and ApplicationInfo.FLAG_PERSISTENT != 0,
                // Keep inventory loading to one PackageManager snapshot. Exact enabled state
                // (disabled-user/default/etc.) is resolved only after opening App Detail.
                enabled = app.enabled,
                enabledState = if (app.enabled) "enabled" else "disabled",
                stopped = app.flags and ApplicationInfo.FLAG_STOPPED != 0,
                running = packageName in runningPackages,
                hasSplits = !app.splitSourceDirs.isNullOrEmpty(),
                splitCount = app.splitSourceDirs?.size ?: 0,
                // Installer is a per-package Binder call and is detail-only metadata.
                installerPackage = null,
            )
        }.sortedWith(compareBy<AppInventoryItem> { it.label.lowercase() }.thenBy { it.packageName })

        return AppInventorySnapshot(
            apps = apps,
            loadedAtMs = System.currentTimeMillis(),
            runningProbeAvailable = runningProbe?.success == true || activityManager.runningAppProcesses != null,
        )
    }

    fun readDetail(packageName: String): AppControlDetail? {
        if (!PACKAGE_REGEX.matches(packageName)) return null
        val info = runCatching { packageInfo(packageName) }.getOrNull() ?: return null
        val app = info.applicationInfo ?: return null
        val enabledState = enabledState(packageName, app)
        val installer = installSource(packageName)
        val sourceFile = File(app.sourceDir.orEmpty())
        val splitFiles = app.splitSourceDirs?.map(::File).orEmpty()
        return AppControlDetail(
            packageName = packageName,
            label = runCatching { app.loadLabel(packageManager).toString() }.getOrDefault(packageName),
            versionName = info.versionName.orEmpty(),
            versionCode = longVersionCode(info),
            uid = app.uid,
            targetSdk = app.targetSdkVersion,
            minSdk = app.minSdkVersion,
            compileSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) app.compileSdkVersion.takeIf { it > 0 } else null,
            firstInstallTimeMs = info.firstInstallTime,
            lastUpdateTimeMs = info.lastUpdateTime,
            installerPackage = installer,
            sourceStatus = sourceStatus(app, installer),
            systemApp = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            updatedSystemApp = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            debuggable = app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            persistent = app.flags and ApplicationInfo.FLAG_PERSISTENT != 0,
            processName = app.processName.orEmpty(),
            enabled = enabledState.first,
            enabledState = enabledState.second,
            stopped = app.flags and ApplicationInfo.FLAG_STOPPED != 0,
            largeHeap = app.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0,
            allowBackup = app.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0,
            usesCleartextTraffic = app.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0,
            launchable = packageManager.getLaunchIntentForPackage(packageName) != null,
            sourceDir = app.sourceDir.orEmpty(),
            splitSourceDirs = app.splitSourceDirs?.toList().orEmpty(),
            sourceReadable = sourceFile.isFile && sourceFile.canRead(),
            baseApkBytes = sourceFile.takeIf { it.isFile }?.length() ?: 0L,
            splitApkBytes = splitFiles.filter { it.isFile }.sumOf { it.length() },
            dataDir = app.dataDir.orEmpty(),
            deviceProtectedDataDir = app.deviceProtectedDataDir.orEmpty(),
            // ApplicationInfo.dataDir is the credential-protected app data directory for the
            // normal user context. The SDK does not expose credentialProtectedDataDir on all
            // compile SDK stubs, so keep this portable instead of relying on a hidden field.
            credentialProtectedDataDir = app.dataDir.orEmpty(),
            nativeLibraryDir = app.nativeLibraryDir.orEmpty(),
            sharedLibraryFiles = app.sharedLibraryFiles?.toList().orEmpty(),
            signingSha256 = signingSha256(info),
            activityCount = info.activities?.size ?: 0,
            serviceCount = info.services?.size ?: 0,
            receiverCount = info.receivers?.size ?: 0,
            providerCount = info.providers?.size ?: 0,
            requestedPermissionCount = info.requestedPermissions?.size ?: 0,
            loadedAtMs = System.currentTimeMillis(),
        )
    }

    private fun installedPackages(): List<PackageInfo> {
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }
    }

    private fun packageInfo(packageName: String): PackageInfo {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.MATCH_DISABLED_COMPONENTS
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, flags)
        }
    }

    private fun enabledState(packageName: String, app: ApplicationInfo): Pair<Boolean, String> {
        val state = runCatching { packageManager.getApplicationEnabledSetting(packageName) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true to "enabled"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false to "disabled"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> false to "disabled-user"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false to "disabled-until-used"
            else -> app.enabled to "default"
        }
    }

    private fun installSource(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val source = packageManager.getInstallSourceInfo(packageName)
            source.installingPackageName ?: source.initiatingPackageName ?: source.originatingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    private fun sourceStatus(app: ApplicationInfo, installer: String?): AppSourceStatus = when {
        app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 -> AppSourceStatus.UPDATED_SYSTEM
        app.flags and ApplicationInfo.FLAG_SYSTEM != 0 -> AppSourceStatus.SYSTEM
        installer == "com.android.vending" -> AppSourceStatus.GOOGLE_PLAY
        installer == "com.sec.android.app.samsungapps" -> AppSourceStatus.GALAXY_STORE
        installer in setOf("com.google.android.packageinstaller", "com.android.packageinstaller", "com.samsung.android.packageinstaller") -> AppSourceStatus.PACKAGE_INSTALLER
        installer.isNullOrBlank() -> AppSourceStatus.SIDELOAD
        installer.startsWith("com.android.shell") -> AppSourceStatus.SIDELOAD
        installer.isNotBlank() -> AppSourceStatus.OTHER_STORE
        else -> AppSourceStatus.UNKNOWN
    }

    private fun longVersionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

    private fun signingSha256(info: PackageInfo): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString(":") { byte -> "%02X".format(byte) }
        }
    }

    private companion object {
        val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    }
}
