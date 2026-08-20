package com.arthur.roottools.feature.integrity.nativebridge

import com.arthur.roottools.feature.integrity.model.NativeIntegritySignals

object NativeIntegrityBridge {
    private val loadError: Throwable? = runCatching { System.loadLibrary("roottools_integrity") }.exceptionOrNull()

    fun collect(): NativeIntegritySignals {
        loadError?.let {
            return NativeIntegritySignals(available = false, error = it.message ?: it.javaClass.simpleName)
        }
        return runCatching { parse(nativeSummary()) }
            .getOrElse { NativeIntegritySignals(available = false, error = it.message ?: it.javaClass.simpleName) }
    }

    private external fun nativeSummary(): String

    internal fun parse(raw: String): NativeIntegritySignals {
        val values = raw.lineSequence().mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()
        if (values["available"] != "1") {
            return NativeIntegritySignals(available = false, error = values["error"] ?: "native probe unavailable")
        }
        return NativeIntegritySignals(
            available = true,
            tracerPid = values["tracerPid"]?.toIntOrNull(),
            mappingCount = values["mappingCount"]?.toIntOrNull() ?: 0,
            writableExecutableCount = values["writableExecutableCount"]?.toIntOrNull() ?: 0,
            deletedExecutableCount = values["deletedExecutableCount"]?.toIntOrNull() ?: 0,
            strongMarkers = values["strongMarkers"]
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSet(),
            loadedElfCount = values["loadedElfCount"]?.toIntOrNull() ?: 0,
            selfExecutableSegments = values["selfExecutableSegments"]?.toIntOrNull() ?: 0,
            selfExecutableSegmentMismatches = values["selfExecutableSegmentMismatches"]?.toIntOrNull() ?: 0,
            selfLibraryPath = values["selfLibraryPath"].takeUnless { it.isNullOrBlank() },
        )
    }
}
