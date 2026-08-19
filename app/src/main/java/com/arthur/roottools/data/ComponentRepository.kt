package com.arthur.roottools.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.ComponentKind
import com.arthur.roottools.model.ComponentSnapshot
import com.arthur.roottools.model.PackageCatalogItem

/** On-demand PackageManager inventory. No polling and no privileged writes. */
class ComponentRepository(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    fun catalog(includeSystemApps: Boolean = false): List<PackageCatalogItem> = installedApplications()
        .asSequence()
        .filter { includeSystemApps || !isSystemApp(it) }
        .map { app ->
            PackageCatalogItem(
                packageName = app.packageName,
                label = runCatching { packageManager.getApplicationLabel(app).toString() }.getOrDefault(app.packageName),
                systemApp = isSystemApp(app),
                enabled = app.enabled,
            )
        }
        .sortedWith(compareBy<PackageCatalogItem> { it.label.lowercase() }.thenBy { it.packageName })
        .toList()

    fun read(packageName: String): ComponentSnapshot? {
        val info = runCatching { packageInfo(packageName) }.getOrNull() ?: return null
        val app = info.applicationInfo ?: return null
        val launcher = packageManager.getLaunchIntentForPackage(packageName)?.component?.flattenToShortString()
        val bootComponents = bootReceivers(packageName)
        val records = buildList {
            info.activities.orEmpty().forEach { component ->
                add(record(component, ComponentKind.ACTIVITY, launcher, bootComponents))
            }
            info.services.orEmpty().forEach { component ->
                add(record(component, ComponentKind.SERVICE, launcher, bootComponents))
            }
            info.receivers.orEmpty().forEach { component ->
                add(record(component, ComponentKind.RECEIVER, launcher, bootComponents))
            }
            info.providers.orEmpty().forEach { component ->
                add(record(component, ComponentKind.PROVIDER, launcher, bootComponents))
            }
        }.sortedWith(compareBy<AppComponentRecord> { it.kind.ordinal }.thenBy { it.className.lowercase() })
        return ComponentSnapshot(
            packageName = packageName,
            label = runCatching { packageManager.getApplicationLabel(app).toString() }.getOrDefault(packageName),
            systemApp = isSystemApp(app),
            components = records,
            loadedAtMs = System.currentTimeMillis(),
        )
    }

    private fun record(
        info: ComponentInfo,
        kind: ComponentKind,
        launcher: String?,
        bootComponents: Set<String>,
    ): AppComponentRecord {
        val name = ComponentName(info.packageName, info.name).flattenToShortString()
        return AppComponentRecord(
            componentName = name,
            className = info.name,
            kind = kind,
            enabled = effectiveEnabled(info),
            exported = info.exported,
            bootReceiver = name in bootComponents,
            foregroundService = info is ServiceInfo && info.foregroundServiceType != 0,
            directBootAware = info.directBootAware,
            permission = when (info) {
                is android.content.pm.ActivityInfo -> info.permission
                is ServiceInfo -> info.permission
                is android.content.pm.ProviderInfo -> info.readPermission ?: info.writePermission
                else -> null
            },
            protectedReason = if (name == launcher) "Launcher activity" else null,
        )
    }

    private fun effectiveEnabled(info: ComponentInfo): Boolean = when (
        packageManager.getComponentEnabledSetting(ComponentName(info.packageName, info.name))
    ) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
        -> false
        else -> info.enabled
    }

    private fun bootReceivers(packageName: String): Set<String> = listOf(
        Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(packageName),
        Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED).setPackage(packageName),
    ).flatMap(::queryReceivers)
        .mapNotNull { it.activityInfo }
        .map { ComponentName(it.packageName, it.name).flattenToShortString() }
        .toSet()

    private fun queryReceivers(intent: Intent) = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
    }

    private fun packageInfo(packageName: String): PackageInfo {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, flags)
        }
    }

    private fun installedApplications(): List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
    }

    private fun isSystemApp(app: ApplicationInfo): Boolean =
        app.flags and ApplicationInfo.FLAG_SYSTEM != 0 || app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
}
