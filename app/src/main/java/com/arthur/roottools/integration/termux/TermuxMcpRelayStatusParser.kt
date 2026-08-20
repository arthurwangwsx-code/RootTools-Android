package com.arthur.roottools.integration.termux

data class TermuxMcpRelayStatus(
    val running: Boolean? = null,
    val pid: Int? = null,
    val bindMode: String? = null,
)

object TermuxMcpRelayStatusParser {
    fun parse(raw: String): TermuxMcpRelayStatus {
        val values = raw.lineSequence().mapNotNull { line ->
            val key = line.substringBefore('=', "").trim()
            val value = line.substringAfter('=', "").trim()
            if (key.matches(Regex("[a-z_]{1,32}")) && value.length <= 128) key to value else null
        }.toMap()
        return TermuxMcpRelayStatus(
            running = when (values["running"]) {
                "1" -> true
                "0" -> false
                else -> null
            },
            pid = values["pid"]?.toIntOrNull()?.takeIf { it > 0 },
            bindMode = values["bind"]?.takeIf { it in setOf("loopback", "tailscale") },
        )
    }
}

