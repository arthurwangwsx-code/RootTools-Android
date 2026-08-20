package com.arthur.roottools.model

data class RuntimePermissionRecord(
    val name: String,
    val granted: Boolean,
    val protection: String,
)

data class AppOpRecord(
    val name: String,
    val raw: String,
    val mode: String?,
    val supported: Boolean,
    val backend: PrivilegeRouteBackend = PrivilegeRouteBackend.NONE,
)

data class PermissionAppOpsSnapshot(
    val packageName: String = "",
    val label: String = "",
    val systemApp: Boolean = false,
    val permissions: List<RuntimePermissionRecord> = emptyList(),
    val appOps: List<AppOpRecord> = emptyList(),
    val appOpsBackendAvailable: Boolean = false,
    val loadedAtMs: Long = 0L,
) {
    val grantedPermissions: Int get() = permissions.count { it.granted }
    val deniedPermissions: Int get() = permissions.count { !it.granted }
}
