package com.arthur.nettools.intercept

import com.arthur.nettools.capture.AppTarget

enum class InterceptionPhase { IDLE, STARTING, RUNNING, STOPPING, ERROR }

enum class DecryptedKind(val wireName: String) {
    RUNNING("running"),
    TLS_ERROR("tls_err"),
    HTTP_ERROR("http_err"),
    HTTP_REQUEST("http_req"),
    HTTP_RESPONSE("http_rep"),
    TCP_CLIENT("tcp_climsg"),
    TCP_SERVER("tcp_srvmsg"),
    TCP_ERROR("tcp_err"),
    WS_CLIENT("ws_climsg"),
    WS_SERVER("ws_srvmsg"),
    TRUNCATED("trunc"),
    MASTER_SECRET("secret"),
    LOG("log"),
    JS_INJECTED("js_inject"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String) = entries.firstOrNull { it.wireName == value } ?: UNKNOWN
    }
}

data class DecryptedEvent(
    val id: Long,
    val timestamp: Long,
    val ipVersion: Int,
    val ipProtocol: Int,
    val port: Int,
    val kind: DecryptedKind,
    val size: Int,
    val title: String,
    val preview: String,
    val payloadPath: String? = null,
)

data class HttpExchangeSummary(
    val id: Long,
    val timestamp: Long,
    val port: Int,
    val requestLine: String? = null,
    val responseLine: String? = null,
    val host: String? = null,
    val method: String? = null,
    val statusCode: Int? = null,
    val contentType: String? = null,
    val requestBytes: Int = 0,
    val responseBytes: Int = 0,
)

data class InterceptionSession(
    val id: String,
    val target: AppTarget,
    val startedAt: Long,
    val directory: String,
    val stoppedAt: Long? = null,
    val decryptedEvents: Int = 0,
    val httpRequests: Int = 0,
    val httpResponses: Int = 0,
    val tlsErrors: Int = 0,
)

data class AddonStatus(
    val installed: Boolean = false,
    val versionName: String? = null,
    val versionCode: Long = -1,
    val supportedAbi: String? = null,
    val latestVersion: String? = null,
    val latestAssetUrl: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

data class InterceptionOptions(
    val blockQuic: Boolean = true,
    val restartTarget: Boolean = true,
    val fullPayload: Boolean = true,
    val sslInsecureUpstream: Boolean = true,
)

data class InterceptionState(
    val phase: InterceptionPhase = InterceptionPhase.IDLE,
    val rootAvailable: Boolean = false,
    val addon: AddonStatus = AddonStatus(),
    val target: AppTarget? = null,
    val options: InterceptionOptions = InterceptionOptions(),
    val session: InterceptionSession? = null,
    val recentEvents: List<DecryptedEvent> = emptyList(),
    val httpExchanges: List<HttpExchangeSummary> = emptyList(),
    val proxyPort: Int = 7780,
    val lastError: String? = null,
    val message: String = "Interception idle",
)
