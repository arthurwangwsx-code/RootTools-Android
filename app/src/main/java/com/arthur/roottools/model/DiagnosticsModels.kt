package com.arthur.roottools.model

data class DiagnosticProcess(
    val pid: Int,
    val user: String,
    val ppid: Int,
    val cpuPercent: Float,
    val memoryPercent: Float,
    val rss: String,
    val processName: String,
)

data class RootShellRecord(
    val pid: Int,
    val ppid: Int,
    val elapsed: String,
    val command: String,
    val cpuPercent: Float,
)

data class RootShellAttribution(
    val pipe: String,
    val ownerPid: Int,
    val ownerFd: Int,
    val ownerCommand: String,
)

data class RootShellDetails(
    val pid: Int,
    val command: String = "",
    val fd0: String = "",
    val fd1: String = "",
    val fd2: String = "",
    val rchar: Long = 0,
    val syscr: Long = 0,
    val readBytes: Long = 0,
    val attributions: List<RootShellAttribution> = emptyList(),
)

data class WakeLockHealth(
    val activeCount: Int = 0,
    val activeLines: List<String> = emptyList(),
    val recentLines: List<String> = emptyList(),
)

data class ActiveServiceHealth(
    val packageName: String,
    val component: String,
    val foreground: Boolean,
)

data class DiagnosticsSnapshot(
    val topProcesses: List<DiagnosticProcess> = emptyList(),
    val rootShells: List<RootShellRecord> = emptyList(),
    val wakeLocks: WakeLockHealth = WakeLockHealth(),
    val services: List<ActiveServiceHealth> = emptyList(),
    val capturedAtMs: Long = System.currentTimeMillis(),
) {
    val abnormalRootShells: Int get() = rootShells.count { it.cpuPercent >= 50f }
    val foregroundServices: Int get() = services.count { it.foreground }
}
