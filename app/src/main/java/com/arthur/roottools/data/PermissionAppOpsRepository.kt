package com.arthur.roottools.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.arthur.roottools.model.AppOpRecord
import com.arthur.roottools.model.PermissionAppOpsSnapshot
import com.arthur.roottools.model.RuntimePermissionRecord
import com.arthur.roottools.privilege.PrivilegeRouter

class PermissionAppOpsRepository(
    context: Context,
    private val router: PrivilegeRouter,
) {
    private val packageManager = context.applicationContext.packageManager

    suspend fun read(packageName: String, includeAppOps: Boolean): PermissionAppOpsSnapshot? {
        if (!PACKAGE_REGEX.matches(packageName)) return null
        val info = runCatching { getPackageInfo(packageName) }.getOrNull() ?: return null
        val app = info.applicationInfo ?: return null
        val permissions = info.requestedPermissions.orEmpty().map { permission ->
            val granted = packageManager.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED
            RuntimePermissionRecord(
                name = permission,
                granted = granted,
                protection = permissionProtection(permission),
            )
        }.sortedWith(compareBy<RuntimePermissionRecord> { it.granted }.thenBy { it.name })

        val appOps = if (includeAppOps) {
            SUPPORTED_APP_OPS.map { op ->
                val result = router.getAppOp(packageName, op)
                val raw = result.value.orEmpty()
                AppOpRecord(
                    name = op,
                    raw = raw.take(420),
                    mode = parseMode(raw),
                    supported = result.success && !looksUnsupported(raw),
                    backend = result.backend,
                )
            }
        } else emptyList()

        return PermissionAppOpsSnapshot(
            packageName = packageName,
            label = runCatching { app.loadLabel(packageManager).toString() }.getOrDefault(packageName),
            systemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0 || app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            permissions = permissions,
            appOps = appOps,
            appOpsBackendAvailable = includeAppOps,
            loadedAtMs = System.currentTimeMillis(),
        )
    }

    private fun getPackageInfo(packageName: String) = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of((PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA).toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
    }

    private fun permissionProtection(permission: String): String = runCatching {
        @Suppress("DEPRECATION")
        val info = packageManager.getPermissionInfo(permission, 0)
        when (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) {
            PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
            PermissionInfo.PROTECTION_SIGNATURE -> "signature"
            PermissionInfo.PROTECTION_NORMAL -> "normal"
            else -> "other"
        }
    }.getOrDefault("unknown")

    private fun parseMode(raw: String): String? {
        val match = MODE_REGEX.find(raw.lowercase()) ?: return null
        return match.groupValues[1]
    }

    private fun looksUnsupported(raw: String): Boolean {
        val lower = raw.lowercase()
        return lower.contains("unknown operation") || lower.contains("invalid") || lower.contains("error:")
    }

    companion object {
        val SUPPORTED_APP_OPS = listOf(
            "RUN_IN_BACKGROUND",
            "RUN_ANY_IN_BACKGROUND",
            "WAKE_LOCK",
            "SYSTEM_ALERT_WINDOW",
            "POST_NOTIFICATION",
            "GET_USAGE_STATS",
            "SCHEDULE_EXACT_ALARM",
        )
        val WRITABLE_MODES = setOf("allow", "ignore", "default", "deny", "foreground")
        private val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
        private val MODE_REGEX = Regex("\\b(allow|ignore|deny|default|foreground)\\b")
    }
}
