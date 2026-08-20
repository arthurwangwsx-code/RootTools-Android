package com.arthur.roottools.integration.termux

import com.arthur.roottools.model.RuntimeToolState

data class TermuxRuntimeToolSnapshot(
    val git: RuntimeToolState = RuntimeToolState.UNKNOWN,
    val python: RuntimeToolState = RuntimeToolState.UNKNOWN,
    val node: RuntimeToolState = RuntimeToolState.UNKNOWN,
    val ssh: RuntimeToolState = RuntimeToolState.UNKNOWN,
    val sshd: RuntimeToolState = RuntimeToolState.UNKNOWN,
    val serviceManager: RuntimeToolState = RuntimeToolState.UNKNOWN,
)

object TermuxRuntimeProbeParser {
    fun parse(raw: String): TermuxRuntimeToolSnapshot {
        val states = raw.lineSequence()
            .mapNotNull { line ->
                val key = line.substringBefore('=', missingDelimiterValue = "").trim()
                val value = line.substringAfter('=', missingDelimiterValue = "").trim()
                if (key.isBlank() || value !in setOf("0", "1")) null else key to value
            }
            .toMap()

        fun state(key: String): RuntimeToolState = when (states[key]) {
            "1" -> RuntimeToolState.INSTALLED
            "0" -> RuntimeToolState.NOT_INSTALLED
            else -> RuntimeToolState.UNKNOWN
        }

        return TermuxRuntimeToolSnapshot(
            git = state("git"),
            python = state("python"),
            node = state("node"),
            ssh = state("ssh"),
            sshd = state("sshd"),
            serviceManager = state("sv"),
        )
    }
}

