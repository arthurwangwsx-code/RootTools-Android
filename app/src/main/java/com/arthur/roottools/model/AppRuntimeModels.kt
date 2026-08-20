package com.arthur.roottools.model

data class AppRuntimeProcess(
    val pid: Int,
    val ppid: Int,
    val user: String,
    val cpuPercent: Float,
    val memoryPercent: Float,
    val rss: String,
    val elapsed: String,
    val processName: String,
)

data class AppRuntimeService(
    val component: String,
    val processName: String? = null,
    val foreground: Boolean = false,
)

data class AppRuntimeSnapshot(
    val packageName: String = "",
    val processes: List<AppRuntimeProcess> = emptyList(),
    val services: List<AppRuntimeService> = emptyList(),
    val standbyBucket: Int? = null,
    val dozeWhitelisted: Boolean = false,
    val dozeLines: List<String> = emptyList(),
    val wakeLockLines: List<String> = emptyList(),
    val backend: PrivilegeRouteBackend = PrivilegeRouteBackend.NONE,
    val loadedAtMs: Long = 0L,
) {
    val running: Boolean get() = processes.isNotEmpty()
    val foregroundServiceCount: Int get() = services.count { it.foreground }
}
