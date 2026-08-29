package com.arthur.roottools.feature.network.inspection.intercept

data class ParsedHttpMessage(
    val firstLine: String,
    val headers: Map<String, String>,
    val method: String? = null,
    val statusCode: Int? = null,
    val host: String? = null,
    val contentType: String? = null,
)

object PlaintextParser {
    fun preview(kind: DecryptedKind, payload: ByteArray): Pair<String, String> {
        if (payload.isEmpty()) return kind.name to ""
        val text = payload.toString(Charsets.UTF_8).replace('\u0000', ' ').trim()
        val safe = redactSensitiveText(text).take(6_000)
        return when (kind) {
            DecryptedKind.HTTP_REQUEST -> "HTTP request" to safe
            DecryptedKind.HTTP_RESPONSE -> "HTTP response" to safe
            DecryptedKind.WS_CLIENT -> "WebSocket → server" to safe
            DecryptedKind.WS_SERVER -> "WebSocket ← server" to safe
            DecryptedKind.TCP_CLIENT -> "TCP → server" to printableOrHex(payload)
            DecryptedKind.TCP_SERVER -> "TCP ← server" to printableOrHex(payload)
            DecryptedKind.TLS_ERROR, DecryptedKind.HTTP_ERROR, DecryptedKind.TCP_ERROR -> "Decryption error" to safe
            DecryptedKind.LOG -> "MITM log" to safe
            else -> kind.name.replace('_', ' ') to printableOrHex(payload)
        }
    }

    fun parseHttp(kind: DecryptedKind, payload: ByteArray): ParsedHttpMessage? {
        if (kind != DecryptedKind.HTTP_REQUEST && kind != DecryptedKind.HTTP_RESPONSE) return null
        val headerText = payload.toString(Charsets.ISO_8859_1).substringBefore("\r\n\r\n")
        val lines = headerText.split("\r\n")
        val first = lines.firstOrNull()?.trim().orEmpty()
        if (first.isBlank()) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx > 0) line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim() else null
        }.toMap()
        val method = if (kind == DecryptedKind.HTTP_REQUEST) first.substringBefore(' ').takeIf { it.isNotBlank() } else null
        val status = if (kind == DecryptedKind.HTTP_RESPONSE) first.split(' ').getOrNull(1)?.toIntOrNull() else null
        return ParsedHttpMessage(
            firstLine = redactSensitiveText(first),
            headers = headers.mapValues { (name, value) ->
                if (name in sensitiveHeaderNames) "<redacted>" else redactQuerySecrets(value)
            },
            method = method,
            statusCode = status,
            host = headers["host"],
            contentType = headers["content-type"],
        )
    }

    fun redactSensitiveText(value: String): String {
        var redacted = value
        sensitiveHeaderNames.forEach { name ->
            redacted = redacted.replace(
                Regex("(?im)^(${Regex.escape(name)}\\s*:)\\s*.*$"),
                "$1 <redacted>",
            )
        }
        return redactQuerySecrets(redacted)
    }

    fun printableOrHex(bytes: ByteArray): String {
        val sample = bytes.take(512).toByteArray()
        val printable = sample.count {
            it == 9.toByte() || it == 10.toByte() || it == 13.toByte() || (it.toInt() and 0xff) in 32..126
        }
        if (sample.isNotEmpty() && printable * 100 / sample.size > 75) {
            return redactSensitiveText(sample.toString(Charsets.UTF_8))
        }
        return sample.take(96).joinToString(" ") { "%02x".format(it.toInt() and 0xff) } + if (bytes.size > 96) " …" else ""
    }

    private fun redactQuerySecrets(value: String): String = value.replace(
        Regex("(?i)([?&](?:access_token|auth_token|refresh_token|token|api_key|apikey|key)=)[^&\\s]+"),
        "$1<redacted>",
    )

    private val sensitiveHeaderNames = setOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "x-auth-token",
    )
}
