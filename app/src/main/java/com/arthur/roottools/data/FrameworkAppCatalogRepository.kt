package com.arthur.roottools.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.arthur.roottools.model.AppPolicyCategory
import com.arthur.roottools.model.StartupAnalysis
import com.arthur.roottools.model.StartupAppRecord
import com.arthur.roottools.model.StartupDataSource
import com.arthur.roottools.policy.PackagePolicyController

/**
 * Low-cost package catalog used when Root boot-trace data is unavailable but Shizuku is ready.
 * It uses normal PackageManager reads only; privileged mutations still go through PrivilegeRouter.
 */
class FrameworkAppCatalogRepository(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun read(): StartupAnalysis {
        val bootCounts = bootReceiverCounts()
        val apps = installedApplications()
            .asSequence()
            .filter { app -> !isSystemApp(app) || app.packageName in PackagePolicyController.PROTECTED_PACKAGES }
            .map { app ->
                val disabled = !app.enabled
                StartupAppRecord(
                    packageName = app.packageName,
                    label = runCatching { packageManager.getApplicationLabel(app).toString() }.getOrDefault(app.packageName),
                    bootReceiverCount = bootCounts[app.packageName] ?: 0,
                    running = false,
                    disabled = disabled,
                    standbyBucket = null,
                    category = when {
                        app.packageName in PackagePolicyController.PROTECTED_PACKAGES -> AppPolicyCategory.PROTECTED
                        disabled -> AppPolicyCategory.FREEZE
                        else -> AppPolicyCategory.NORMAL
                    },
                )
            }
            .sortedWith(compareByDescending<StartupAppRecord> { it.bootReceiverCount }.thenBy { it.label.lowercase() })
            .toList()
        return StartupAnalysis(
            apps = apps,
            dataSource = StartupDataSource.FRAMEWORK_CATALOG,
        )
    }

    private fun installedApplications(): List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
    }

    private fun bootReceiverCounts(): Map<String, Int> {
        val receivers = listOf(
            Intent(Intent.ACTION_BOOT_COMPLETED),
            Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED),
        ).flatMap(::queryReceivers)
        return receivers.mapNotNull { it.activityInfo?.packageName }.groupingBy { it }.eachCount()
    }

    private fun queryReceivers(intent: Intent) = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
    }

    private fun isSystemApp(app: ApplicationInfo): Boolean =
        app.flags and ApplicationInfo.FLAG_SYSTEM != 0 || app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
}
