package com.arthur.roottools.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import android.os.Build
import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.ComponentKind
import com.arthur.roottools.model.ComponentSnapshot

class ComponentRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun read(packageName: String): ComponentSnapshot? {
        if (!PACKAGE_REGEX.matches(packageName)) return null
        val info = runCatching { getPackageInfo(packageName) }.getOrNull() ?: return null
        val app = info.applicationInfo ?: return null
        val bootReceivers = queryBootReceivers(packageName)
        val launcherActivities = queryLauncherActivities(packageName)
        val components = buildList {
            info.activities.orEmpty().forEach { activity ->
                val flattened = ComponentName(activity.packageName, activity.name).flattenToString()
                add(
                    activity.toRecord(
                        ComponentKind.ACTIVITY,
                        protectedReason = if (flattened in launcherActivities) "Launcher entry" else null,
                    )
                )
            }
            info.services.orEmpty().forEach { service ->
                add(
                    service.toRecord(
                        ComponentKind.SERVICE,
                        foregroundService = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && service.foregroundServiceType != 0,
                    )
                )
            }
            info.receivers.orEmpty().forEach { receiver ->
                val flattened = ComponentName(receiver.packageName, receiver.name).flattenToString()
                add(receiver.toRecord(ComponentKind.RECEIVER, bootReceiver = flattened in bootReceivers))
            }
            info.providers.orEmpty().forEach { add(it.toRecord(ComponentKind.PROVIDER)) }
        }.sortedWith(compareBy<AppComponentRecord> { it.kind.ordinal }.thenBy { it.className })

        return ComponentSnapshot(
            packageName = packageName,
            label = runCatching { app.loadLabel(packageManager).toString() }.getOrDefault(packageName),
            appEnabled = app.enabled,
            systemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0 || app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            components = components,
            loadedAtMs = System.currentTimeMillis(),
        )
    }

    fun installedUserPackages(limit: Int = 80): List<Pair<String, String>> = runCatching {
        val apps = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        apps.asSequence()
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { app -> app.packageName to runCatching { app.loadLabel(packageManager).toString() }.getOrDefault(app.packageName) }
            .sortedBy { it.second.lowercase() }
            .take(limit)
            .toList()
    }.getOrDefault(emptyList())

    private fun getPackageInfo(packageName: String): PackageInfo {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, flags)
        }
    }

    private fun queryBootReceivers(packageName: String): Set<String> {
        val intents = listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED)
        return intents.flatMap { action ->
            val intent = Intent(action).setPackage(packageName)
            val resolved = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
            resolved.mapNotNull { item -> item.activityInfo?.let { ComponentName(it.packageName, it.name).flattenToString() } }
        }.toSet()
    }

    private fun queryLauncherActivities(packageName: String): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        return resolved.mapNotNull { item ->
            item.activityInfo?.let { ComponentName(it.packageName, it.name).flattenToString() }
        }.toSet()
    }

    private fun ComponentInfo.toRecord(
        kind: ComponentKind,
        bootReceiver: Boolean = false,
        foregroundService: Boolean = false,
        protectedReason: String? = null,
    ): AppComponentRecord {
        val componentName = ComponentName(packageName, name)
        val overrideState = runCatching { packageManager.getComponentEnabledSetting(componentName) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        val effectiveEnabled = when (overrideState) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> enabled && applicationInfo.enabled
        }
        return AppComponentRecord(
        componentName = componentName.flattenToString(),
        className = name,
        kind = kind,
        enabled = effectiveEnabled,
        exported = exported,
        permission = when (this) {
            is ActivityInfo -> permission
            is ServiceInfo -> permission
            is ProviderInfo -> readPermission ?: writePermission
            else -> null
        },
        directBootAware = directBootAware,
        bootReceiver = bootReceiver,
        foregroundService = foregroundService,
        protectedReason = protectedReason,
    )
    }

    private companion object {
        val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    }
}
