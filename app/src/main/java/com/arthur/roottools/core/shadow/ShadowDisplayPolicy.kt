package com.arthur.roottools.core.shadow

import com.arthur.roottools.model.ShadowDisplayConfig
import com.arthur.roottools.model.ShadowDisplayRuntimeState
import com.arthur.roottools.model.ShadowDisplayStatus

enum class ShadowDisplayTextStrategy {
    KEY_EVENTS,
    CLIPBOARD_PASTE,
}

object ShadowDisplayPolicy {
    const val MIN_WIDTH = 360
    const val MAX_WIDTH = 2560
    const val MIN_HEIGHT = 640
    const val MAX_HEIGHT = 3200
    const val MIN_DPI = 120
    const val MAX_DPI = 640
    const val MAX_TEXT_LENGTH = 500

    fun config(width: Int, height: Int, densityDpi: Int): ShadowDisplayConfig? =
        if (width in MIN_WIDTH..MAX_WIDTH &&
            height in MIN_HEIGHT..MAX_HEIGHT &&
            densityDpi in MIN_DPI..MAX_DPI
        ) {
            ShadowDisplayConfig(width, height, densityDpi)
        } else {
            null
        }

    fun coordinate(value: Int, maxExclusive: Int): Int? =
        value.takeIf { maxExclusive > 0 && it in 0 until maxExclusive }

    fun swipeDurationMs(value: Int): Int? = value.takeIf { it in 50..5_000 }

    fun text(value: String): String? = value
        .takeIf { candidate ->
            candidate.length <= MAX_TEXT_LENGTH && candidate.none(Char::isISOControl)
        }

    fun textStrategy(value: String): ShadowDisplayTextStrategy? {
        val safe = text(value) ?: return null
        return if (safe.all { it.code in 0x20..0x7E }) {
            ShadowDisplayTextStrategy.KEY_EVENTS
        } else {
            ShadowDisplayTextStrategy.CLIPBOARD_PASTE
        }
    }

    fun packageName(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.length !in 3..220 || '.' !in trimmed) return null
        if (!PACKAGE_REGEX.matches(trimmed)) return null
        return trimmed
    }

    private val PACKAGE_REGEX = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
}

object ShadowDisplayStatusParser {
    fun parse(raw: String): ShadowDisplayStatus {
        val values = raw.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && '=' in it }
            .mapNotNull { line ->
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim()
                key.takeIf { it.isNotEmpty() }?.let { it to value }
            }
            .toMap()

        val state = when (values["state"]?.lowercase()) {
            "starting" -> ShadowDisplayRuntimeState.STARTING
            "running" -> ShadowDisplayRuntimeState.RUNNING
            "error" -> ShadowDisplayRuntimeState.ERROR
            else -> ShadowDisplayRuntimeState.STOPPED
        }
        val displayId = values["displayId"]?.toIntOrNull()?.takeIf { it >= 0 }
        val pid = values["pid"]?.toIntOrNull()?.takeIf { it > 0 }
        val width = values["width"]?.toIntOrNull() ?: ShadowDisplayConfig().width
        val height = values["height"]?.toIntOrNull() ?: ShadowDisplayConfig().height
        val dpi = values["densityDpi"]?.toIntOrNull() ?: ShadowDisplayConfig().densityDpi
        val config = ShadowDisplayPolicy.config(width, height, dpi) ?: ShadowDisplayConfig()
        val processAlive = values["processAlive"] == "1"
        val activeDisplays = values["activeDisplays"]
            .orEmpty()
            .split(',', ' ', '\n')
            .mapNotNull(String::toIntOrNull)
            .toSet()
        val displayActive = displayId != null && displayId in activeDisplays
        return ShadowDisplayStatus(
            state = state,
            displayId = displayId,
            pid = pid,
            config = config,
            processAlive = processAlive,
            displayActive = displayActive,
            startedAtMs = values["startedAtMs"]?.toLongOrNull()?.takeIf { it > 0 },
            error = values["error"]?.takeIf { it.isNotBlank() },
        )
    }
}
