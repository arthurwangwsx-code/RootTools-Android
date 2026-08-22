package com.arthur.roottools.policy

import com.arthur.roottools.data.RootActionAuditStore
import com.arthur.roottools.core.shadow.ShadowDisplayPolicy
import com.arthur.roottools.core.shadow.ShadowDisplayStatusParser
import com.arthur.roottools.model.PrivilegeRouteBackend
import com.arthur.roottools.model.ShadowDisplayActionResult
import com.arthur.roottools.model.ShadowDisplayConfig
import com.arthur.roottools.model.ShadowDisplayStatus
import com.arthur.roottools.privilege.PrivilegeRouter

class ShadowDisplayController(
    private val privilegeRouter: PrivilegeRouter,
    private val auditStore: RootActionAuditStore,
    private val auditSource: String,
) {
    suspend fun status(): Result<ShadowDisplayStatus> {
        val result = privilegeRouter.shadowDisplayStatus()
        return if (result.success && result.value != null) {
            runCatching { ShadowDisplayStatusParser.parse(result.value) }
        } else {
            Result.failure(IllegalStateException(result.detail.ifBlank { "Unable to read shadow display status" }))
        }
    }

    suspend fun start(width: Int, height: Int, densityDpi: Int): ShadowDisplayActionResult {
        val config = ShadowDisplayPolicy.config(width, height, densityDpi)
            ?: return invalid("Invalid display size or density")
        val before = status().getOrNull()
        if (before?.running == true) return invalid("Shadow display is already running")
        val result = privilegeRouter.startShadowDisplay(config)
        val after = if (result.success && result.value != null) ShadowDisplayStatusParser.parse(result.value) else null
        val success = result.success && after?.running == true
        auditStore.record(
            source = auditSource,
            feature = FEATURE,
            action = "start",
            target = "${config.width}x${config.height}/${config.densityDpi}",
            before = describe(before),
            after = describe(after),
            success = success,
            rollbackHint = "Stop shadow display",
        )
        return ShadowDisplayActionResult(
            success = success,
            backend = if (success) result.backend else PrivilegeRouteBackend.NONE,
            detail = if (success) "" else result.detail.ifBlank { after?.error ?: "Shadow display did not become active" },
        )
    }

    suspend fun stop(): ShadowDisplayActionResult {
        val before = status().getOrNull()
        val result = privilegeRouter.stopShadowDisplay()
        val after = status().getOrNull()
        val success = result.success && after?.running != true
        auditStore.record(
            source = auditSource,
            feature = FEATURE,
            action = "stop",
            target = before?.displayId?.toString().orEmpty(),
            before = describe(before),
            after = describe(after),
            success = success,
            rollbackHint = before?.config?.let { "Start ${it.width}x${it.height}/${it.densityDpi}" }.orEmpty(),
        )
        return ShadowDisplayActionResult(success, if (success) result.backend else PrivilegeRouteBackend.NONE, result.detail)
    }

    suspend fun launchPackage(packageName: String): ShadowDisplayActionResult {
        val pkg = ShadowDisplayPolicy.packageName(packageName) ?: return invalid("Invalid package name")
        val before = status().getOrNull()
        if (before?.running != true) return invalid("Shadow display is not running")
        val result = privilegeRouter.launchPackageOnShadowDisplay(pkg)
        auditStore.record(
            source = auditSource,
            feature = FEATURE,
            action = "launch-package",
            target = pkg,
            before = "display=${before.displayId}",
            after = if (result.success) "launched" else result.detail,
            success = result.success,
        )
        return ShadowDisplayActionResult(result.success, result.backend, result.detail)
    }

    suspend fun tap(x: Int, y: Int): ShadowDisplayActionResult {
        val current = status().getOrNull() ?: return invalid("Unable to read shadow display")
        if (!current.running) return invalid("Shadow display is not running")
        val safeX = ShadowDisplayPolicy.coordinate(x, current.config.width) ?: return invalid("X coordinate is outside the display")
        val safeY = ShadowDisplayPolicy.coordinate(y, current.config.height) ?: return invalid("Y coordinate is outside the display")
        return privilegeRouter.tapShadowDisplay(safeX, safeY).toActionResult()
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): ShadowDisplayActionResult {
        val current = status().getOrNull() ?: return invalid("Unable to read shadow display")
        if (!current.running) return invalid("Shadow display is not running")
        val values = listOf(
            ShadowDisplayPolicy.coordinate(x1, current.config.width),
            ShadowDisplayPolicy.coordinate(y1, current.config.height),
            ShadowDisplayPolicy.coordinate(x2, current.config.width),
            ShadowDisplayPolicy.coordinate(y2, current.config.height),
        )
        val duration = ShadowDisplayPolicy.swipeDurationMs(durationMs)
        if (values.any { it == null } || duration == null) return invalid("Invalid swipe coordinates or duration")
        return privilegeRouter.swipeShadowDisplay(values[0]!!, values[1]!!, values[2]!!, values[3]!!, duration).toActionResult()
    }

    suspend fun typeText(text: String): ShadowDisplayActionResult {
        val current = status().getOrNull() ?: return invalid("Unable to read shadow display")
        if (!current.running) return invalid("Shadow display is not running")
        val safe = ShadowDisplayPolicy.text(text) ?: return invalid("Text is too long or contains unsupported control data")
        return privilegeRouter.typeTextOnShadowDisplay(safe).toActionResult()
    }

    suspend fun capturePreview(): Result<ByteArray> {
        val current = status().getOrNull()
        if (current?.running != true) return Result.failure(IllegalStateException("Shadow display is not running"))
        val result = privilegeRouter.captureShadowDisplayPreview()
        return if (result.success && result.value != null) Result.success(result.value)
        else Result.failure(IllegalStateException(result.detail.ifBlank { "Unable to capture shadow display" }))
    }

    private fun <T> com.arthur.roottools.privilege.PrivilegeResult<T>.toActionResult() =
        ShadowDisplayActionResult(success, backend, detail)

    private fun invalid(detail: String) = ShadowDisplayActionResult(false, PrivilegeRouteBackend.NONE, detail)

    private fun describe(status: ShadowDisplayStatus?): String = when {
        status == null -> "unknown"
        status.running -> "running display=${status.displayId} ${status.config.width}x${status.config.height}/${status.config.densityDpi}"
        else -> status.state.name.lowercase()
    }

    private companion object {
        const val FEATURE = "shadow-display"
    }
}
