package com.arthur.roottools.data

import com.arthur.roottools.model.MagiskModuleInfo
import com.arthur.roottools.model.ModuleCenterSnapshot
import com.arthur.roottools.model.PackageActionResult
import com.arthur.roottools.model.VectorModuleInfo
import com.arthur.roottools.model.VectorScopeEntry
import com.arthur.roottools.root.RootShell
import org.json.JSONObject

class ModuleCenterRepository(
    private val shell: RootShell,
    private val auditStore: RootActionAuditStore? = null,
    private val auditSource: String = "internal",
) {
    suspend fun read(): ModuleCenterSnapshot {
        val magiskResult = shell.execute(MAGISK_READ_COMMAND, timeoutSeconds = 8)
        val vectorResult = shell.execute("/data/adb/lspd/cli modules --json ls 2>/dev/null", timeoutSeconds = 8)
        val vectorActive = shell.execute("pidof vectord >/dev/null 2>&1", timeoutSeconds = 3).success
        return ModuleCenterSnapshot(
            magiskModules = if (magiskResult.success) parseMagisk(magiskResult.output) else emptyList(),
            vectorModules = if (vectorResult.success) parseVector(vectorResult.output) else emptyList(),
            vectorActive = vectorActive,
        )
    }

    suspend fun readScope(packageName: String): List<VectorScopeEntry> {
        val safe = validatePackage(packageName) ?: return emptyList()
        val result = shell.execute("/data/adb/lspd/cli scope --json ls $safe 2>/dev/null", timeoutSeconds = 6)
        if (!result.success) return emptyList()
        return runCatching {
            val root = JSONObject(result.output)
            val data = root.optJSONArray("data") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until data.length()) {
                    val item = data.optJSONObject(index) ?: continue
                    add(VectorScopeEntry(item.optString("APP_PACKAGE"), item.optInt("USER_ID", 0)))
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun setMagiskEnabled(moduleId: String, enabled: Boolean): PackageActionResult {
        val safe = validateModuleId(moduleId) ?: return PackageActionResult(false, "非法 module id")
        if (!enabled && safe in PROTECTED_MAGISK) return PackageActionResult(false, "该框架模块受保护，不能在普通入口禁用")
        val before = shell.execute(
            "[ -f /data/adb/modules/$safe/disable ] && echo disabled || echo enabled",
            timeoutSeconds = 3,
        ).output.trim().ifBlank { "unknown" }
        val command = if (enabled) {
            "rm -f /data/adb/modules/$safe/disable"
        } else {
            "touch /data/adb/modules/$safe/disable"
        }
        val result = shell.execute(command, timeoutSeconds = 5)
        auditStore?.record(
            source = auditSource,
            feature = "modules",
            action = "magisk_module_state",
            target = safe,
            before = before,
            after = if (enabled) "enabled" else "disabled-next-boot",
            success = result.success,
            rollbackHint = if (before.startsWith("disabled")) "重新写入 disable marker" else "移除 disable marker",
        )
        return if (result.success) PackageActionResult(true, "模块状态已写入，下次重启生效") else PackageActionResult(false, "模块状态修改失败：${result.output.take(120)}")
    }

    suspend fun setVectorEnabled(packageName: String, enabled: Boolean): PackageActionResult {
        val safe = validatePackage(packageName) ?: return PackageActionResult(false, "非法 package name")
        val before = read().vectorModules.firstOrNull { it.packageName == safe }?.enabled?.let { if (it) "enabled" else "disabled" } ?: "unknown"
        val command = "/data/adb/lspd/cli modules ${if (enabled) "enable" else "disable"} $safe"
        val result = shell.execute(command, timeoutSeconds = 6)
        auditStore?.record(
            source = auditSource,
            feature = "modules",
            action = "vector_module_state",
            target = safe,
            before = before,
            after = if (enabled) "enabled" else "disabled",
            success = result.success,
            rollbackHint = if (before == "enabled") "重新启用 Vector 模块" else if (before == "disabled") "重新停用 Vector 模块" else "按需恢复之前状态",
        )
        return if (result.success) PackageActionResult(true, "Vector 模块已${if (enabled) "启用" else "停用"}") else PackageActionResult(false, "Vector 操作失败：${result.output.take(120)}")
    }

    private fun parseMagisk(raw: String): List<MagiskModuleInfo> {
        val blocks = raw.split("__MODULE__").drop(1)
        return blocks.mapNotNull { block ->
            val values = block.lineSequence().mapNotNull { line ->
                val key = line.substringBefore('=', "").trim()
                if (key.isBlank()) null else key to line.substringAfter('=', "").trim()
            }.toMap()
            val id = values["id"] ?: values["DIR"] ?: return@mapNotNull null
            MagiskModuleInfo(
                id = id,
                name = values["name"] ?: id,
                version = values["version"].orEmpty(),
                author = values["author"].orEmpty(),
                description = values["description"].orEmpty(),
                disabledMarker = values["DISABLED"] == "1",
                removeMarker = values["REMOVE"] == "1",
                protected = id in PROTECTED_MAGISK,
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseVector(raw: String): List<VectorModuleInfo> = runCatching {
        val root = JSONObject(raw)
        val data = root.optJSONArray("data") ?: return@runCatching emptyList()
        buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                add(
                    VectorModuleInfo(
                        packageName = item.optString("PACKAGE"),
                        uid = item.optInt("UID", 0),
                        enabled = item.optString("STATUS").equals("enabled", true),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun validateModuleId(value: String): String? = value.takeIf { MODULE_REGEX.matches(it) }
    private fun validatePackage(value: String): String? = value.takeIf { PACKAGE_REGEX.matches(it) }

    private companion object {
        val MODULE_REGEX = Regex("[A-Za-z0-9._-]+")
        val PACKAGE_REGEX = Regex("[A-Za-z0-9._]+")
        val PROTECTED_MAGISK = setOf("zygisk_vector")
        val MAGISK_READ_COMMAND = """
            for d in /data/adb/modules/*; do
              [ -f "${'$'}d/module.prop" ] || continue
              echo __MODULE__
              echo DIR=${'$'}{d##*/}
              cat "${'$'}d/module.prop"
              [ -f "${'$'}d/disable" ] && echo DISABLED=1 || echo DISABLED=0
              [ -f "${'$'}d/remove" ] && echo REMOVE=1 || echo REMOVE=0
            done
        """.trimIndent()
    }
}
