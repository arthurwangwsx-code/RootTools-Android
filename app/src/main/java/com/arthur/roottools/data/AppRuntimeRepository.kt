package com.arthur.roottools.data

import com.arthur.roottools.model.AppRuntimeProcess
import com.arthur.roottools.model.AppRuntimeService
import com.arthur.roottools.model.AppRuntimeSnapshot
import com.arthur.roottools.privilege.PrivilegeRouter

class AppRuntimeRepository(private val router: PrivilegeRouter) {
    suspend fun read(packageName: String): AppRuntimeSnapshot? {
        val result = router.appRuntimeSnapshot(packageName)
        if (!result.success) return null
        val sections = splitSections(result.value.orEmpty())
        return AppRuntimeSnapshot(
            packageName = packageName,
            processes = parseProcesses(sections["PROCESSES"].orEmpty()),
            services = parseServices(sections["SERVICES"].orEmpty()),
            standbyBucket = parseBucket(sections["BUCKET"].orEmpty()),
            dozeWhitelisted = sections["DOZE"].orEmpty().isNotEmpty(),
            dozeLines = sections["DOZE"].orEmpty(),
            wakeLockLines = sections["WAKELOCK"].orEmpty(),
            backend = result.backend,
            loadedAtMs = System.currentTimeMillis(),
        )
    }

    private fun parseBucket(lines: List<String>): Int? {
        val raw = lines.firstOrNull()?.trim().orEmpty()
        return raw.substringAfter(':', raw).trim().toIntOrNull()
    }

    private fun parseProcesses(lines: List<String>): List<AppRuntimeProcess> = lines.mapNotNull { line ->
        val parts = line.split('|', limit = 8)
        if (parts.size < 8) return@mapNotNull null
        AppRuntimeProcess(
            pid = parts[0].toIntOrNull() ?: return@mapNotNull null,
            ppid = parts[1].toIntOrNull() ?: -1,
            user = parts[2],
            cpuPercent = parts[3].toFloatOrNull() ?: 0f,
            memoryPercent = parts[4].toFloatOrNull() ?: 0f,
            rss = parts[5],
            elapsed = parts[6],
            processName = parts[7],
        )
    }

    private fun parseServices(lines: List<String>): List<AppRuntimeService> {
        val result = mutableListOf<AppRuntimeService>()
        var current: AppRuntimeService? = null
        lines.forEach { line ->
            val match = SERVICE_REGEX.find(line)
            if (match != null) {
                current?.let(result::add)
                current = AppRuntimeService(component = match.groupValues[1])
            } else if (current != null && line.contains("processName=")) {
                current = current?.copy(processName = line.substringAfter("processName=").substringBefore(' ').trim())
            } else if (current != null && line.contains("isForeground=true")) {
                current = current?.copy(foreground = true)
            }
        }
        current?.let(result::add)
        return result.distinctBy { it.component }
    }

    private fun splitSections(raw: String): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        raw.lineSequence().forEach { line ->
            if (line.startsWith("__") && line.endsWith("__")) {
                val section = line.removePrefix("__").removeSuffix("__")
                current = section
                result.getOrPut(section) { mutableListOf() }
            } else current?.let { result.getOrPut(it) { mutableListOf() }.add(line) }
        }
        return result
    }

    private companion object {
        val SERVICE_REGEX = Regex("ServiceRecord\\{[^ ]+ u\\d+ ([^ }]+)")
    }
}
