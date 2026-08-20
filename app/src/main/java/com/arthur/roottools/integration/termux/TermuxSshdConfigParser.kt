package com.arthur.roottools.integration.termux

data class TermuxSshdConfigSnapshot(
    val port: Int? = null,
    val listenAddresses: List<String> = emptyList(),
    val passwordAuthentication: Boolean? = null,
    val publicKeyAuthentication: Boolean? = null,
) {
    val wildcardListenerConfigured: Boolean
        get() = listenAddresses.any { address ->
            val normalized = address.lowercase()
            normalized == "0.0.0.0" || normalized == "::" ||
                normalized.startsWith("0.0.0.0:") || normalized.startsWith("[::]:")
        }
}

object TermuxSshdConfigParser {
    fun parse(raw: String): TermuxSshdConfigSnapshot {
        var port: Int? = null
        val addresses = linkedSetOf<String>()
        var passwordAuthentication: Boolean? = null
        var publicKeyAuthentication: Boolean? = null

        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val key = trimmed.substringBefore(' ', "").lowercase()
            val value = trimmed.substringAfter(' ', "").trim()
            when (key) {
                "port" -> value.toIntOrNull()?.takeIf { it in 1..65535 }?.let { port = it }
                "listenaddress" -> if (value.length in 1..160) addresses += value
                "passwordauthentication" -> passwordAuthentication = parseBoolean(value)
                "pubkeyauthentication" -> publicKeyAuthentication = parseBoolean(value)
            }
        }

        return TermuxSshdConfigSnapshot(
            port = port,
            listenAddresses = addresses.toList(),
            passwordAuthentication = passwordAuthentication,
            publicKeyAuthentication = publicKeyAuthentication,
        )
    }

    private fun parseBoolean(value: String): Boolean? = when (value.lowercase()) {
        "yes" -> true
        "no" -> false
        else -> null
    }
}

