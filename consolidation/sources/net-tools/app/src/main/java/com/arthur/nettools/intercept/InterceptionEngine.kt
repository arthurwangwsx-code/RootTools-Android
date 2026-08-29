package com.arthur.nettools.intercept

import android.content.Context
import android.os.ParcelFileDescriptor
import com.arthur.nettools.capture.AppTarget
import com.arthur.nettools.capture.RootShell
import com.arthur.nettools.security.CertificateManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class InterceptionEngine(private val context: Context) {
    companion object {
        const val PROXY_PORT = 7780
        private const val NAT_CHAIN = "NETTOOLS_MITM"
        private const val QUIC_CHAIN = "NETTOOLS_QUIC"

        fun cleanupNetworkRules(): String {
            if (!RootShell.hasRoot()) return "Root unavailable"
            val script = """
                iptables -t nat -D OUTPUT -j $NAT_CHAIN 2>/dev/null || true
                iptables -t nat -F $NAT_CHAIN 2>/dev/null || true
                iptables -t nat -X $NAT_CHAIN 2>/dev/null || true
                iptables -D OUTPUT -j $QUIC_CHAIN 2>/dev/null || true
                iptables -F $QUIC_CHAIN 2>/dev/null || true
                iptables -X $QUIC_CHAIN 2>/dev/null || true
                if command -v ip6tables >/dev/null 2>&1; then
                  ip6tables -t nat -D OUTPUT -j $NAT_CHAIN 2>/dev/null || true
                  ip6tables -t nat -F $NAT_CHAIN 2>/dev/null || true
                  ip6tables -t nat -X $NAT_CHAIN 2>/dev/null || true
                  ip6tables -D OUTPUT -j $QUIC_CHAIN 2>/dev/null || true
                  ip6tables -F $QUIC_CHAIN 2>/dev/null || true
                  ip6tables -X $QUIC_CHAIN 2>/dev/null || true
                fi
            """.trimIndent()
            return RootShell.exec(script).output
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val caManager = CertificateManager(context)
    private var client: MitmAddonClient? = null
    private var socket: ParcelFileDescriptor? = null
    private var readerJob: Job? = null
    private var runningSignal: CompletableDeferred<Unit>? = null
    private var sessionDir: File? = null
    private var eventsFile: File? = null
    private var currentTarget: AppTarget? = null
    private var currentOptions = InterceptionOptions()
    @Volatile private var expectedSocketClose = false
    private val eventSeq = AtomicLong(0)
    private val pendingHttp = mutableMapOf<Int, ArrayDeque<Long>>()

    suspend fun start(target: AppTarget, options: InterceptionOptions) = withContext(Dispatchers.IO) {
        if (InterceptionRuntime.state.value.phase != InterceptionPhase.IDLE && InterceptionRuntime.state.value.phase != InterceptionPhase.ERROR) return@withContext
        InterceptionRuntime.update {
            it.copy(phase = InterceptionPhase.STARTING, target = target, options = options, lastError = null, message = "Starting transparent TLS interception")
        }
        try {
            check(RootShell.hasRoot()) { "Root is required for transparent interception" }
            check(MitmAddonManager(context).installedStatus().installed) { "MITM add-on is not installed" }
            val ca = caManager.status()
            check(ca.generated) { "Import the MITM add-on CA first" }
            check(ca.systemTrusted) { if (ca.requiresReboot) "System CA is staged but not active; reboot the device" else "MITM CA is not trusted by the system" }

            cleanupNetworkRules()
            currentTarget = target
            currentOptions = options
            eventSeq.set(0)
            pendingHttp.clear()
            val id = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(java.util.Date())
            val rootDir = File(context.getExternalFilesDir(null), "intercepts/$id-${target.packageName}").apply { mkdirs() }
            File(rootDir, "payloads").mkdirs()
            sessionDir = rootDir
            eventsFile = File(rootDir, "events.jsonl")
            val session = InterceptionSession(id, target, System.currentTimeMillis(), rootDir.absolutePath)
            InterceptionRuntime.update { it.copy(session = session, recentEvents = emptyList(), httpExchanges = emptyList()) }

            val addon = MitmAddonClient(context)
            client = addon
            check(addon.connect(onDisconnect = {
                InterceptionRuntime.update { old -> old.copy(phase = InterceptionPhase.ERROR, lastError = "MITM add-on disconnected", message = "MITM add-on disconnected") }
            })) { "Unable to bind MITM add-on service" }
            InterceptionRuntime.update { it.copy(message = "Verifying mitmproxy interception CA") }
            val runtimePem = addon.requestCertificate(30_000)
            val runtimeCert = CertificateFactory.getInstance("X.509")
                .generateCertificate(runtimePem.byteInputStream()) as X509Certificate
            val runtimeFingerprint = MessageDigest.getInstance("SHA-256").digest(runtimeCert.encoded)
                .joinToString(":") { "%02X".format(it.toInt() and 0xff) }
            check(runtimeFingerprint == ca.fingerprint) {
                "MITM add-on CA changed. Refresh the interception CA and system trust before decrypting."
            }
            if (!MitmAddonManager(context).isBatteryOptimizationIgnored()) addon.disableDoze()
            runningSignal = CompletableDeferred()
            expectedSocketClose = false
            InterceptionRuntime.update { it.copy(message = "Starting mitmproxy runtime") }
            socket = addon.startProxy(PROXY_PORT, options.fullPayload, options.sslInsecureUpstream)
            readerJob = scope.launch { readLoop(socket!!) }
            withTimeout(60_000) { runningSignal?.await() }

            installNetworkRules(target.uid, options.blockQuic)
            if (options.restartTarget) restartTarget(target.packageName)
            InterceptionRuntime.update { it.copy(phase = InterceptionPhase.RUNNING, message = "Decrypting ${target.label} on port $PROXY_PORT") }
        } catch (t: Throwable) {
            runCatching { stopInternal(false) }
            InterceptionRuntime.update { it.copy(phase = InterceptionPhase.ERROR, lastError = t.message ?: t.javaClass.simpleName, message = "Interception failed") }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        InterceptionRuntime.update { it.copy(phase = InterceptionPhase.STOPPING, message = "Stopping interception") }
        stopInternal(true)
    }

    private suspend fun stopInternal(markComplete: Boolean) {
        expectedSocketClose = true
        cleanupNetworkRules()
        client?.stopProxy()
        socket?.close()
        socket = null
        readerJob?.cancelAndJoin()
        readerJob = null
        client?.disconnect()
        client = null
        val old = InterceptionRuntime.state.value
        val completed = old.session?.copy(stoppedAt = System.currentTimeMillis())
        if (completed != null) writeSessionSummary(completed, old)
        currentTarget = null
        if (markComplete) {
            InterceptionRuntime.update { it.copy(phase = InterceptionPhase.IDLE, session = completed, target = null, message = "Interception stopped") }
        }
    }

    private fun installNetworkRules(uid: Int, blockQuic: Boolean) {
        val base = """
            iptables -t nat -N $NAT_CHAIN 2>/dev/null || true
            iptables -t nat -F $NAT_CHAIN
            iptables -t nat -C OUTPUT -j $NAT_CHAIN 2>/dev/null || iptables -t nat -I OUTPUT 1 -j $NAT_CHAIN
            iptables -t nat -A $NAT_CHAIN -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $PROXY_PORT
        """.trimIndent()
        val quic = if (blockQuic) """
            iptables -N $QUIC_CHAIN 2>/dev/null || true
            iptables -F $QUIC_CHAIN
            iptables -C OUTPUT -j $QUIC_CHAIN 2>/dev/null || iptables -I OUTPUT 1 -j $QUIC_CHAIN
            iptables -A $QUIC_CHAIN -p udp --dport 443 -m owner --uid-owner $uid -j REJECT
        """.trimIndent() else ""
        val ipv6 = """
            if command -v ip6tables >/dev/null 2>&1; then
              ip6tables -t nat -N $NAT_CHAIN 2>/dev/null || true
              ip6tables -t nat -F $NAT_CHAIN 2>/dev/null || true
              ip6tables -t nat -C OUTPUT -j $NAT_CHAIN 2>/dev/null || ip6tables -t nat -I OUTPUT 1 -j $NAT_CHAIN 2>/dev/null || true
              ip6tables -t nat -A $NAT_CHAIN -p tcp -m owner --uid-owner $uid -j REDIRECT --to-ports $PROXY_PORT 2>/dev/null || true
              ${if (blockQuic) "ip6tables -N $QUIC_CHAIN 2>/dev/null || true; ip6tables -F $QUIC_CHAIN 2>/dev/null || true; ip6tables -C OUTPUT -j $QUIC_CHAIN 2>/dev/null || ip6tables -I OUTPUT 1 -j $QUIC_CHAIN; ip6tables -A $QUIC_CHAIN -p udp --dport 443 -m owner --uid-owner $uid -j REJECT" else "true"}
            fi
        """.trimIndent()
        val result = RootShell.exec("$base\n$quic\n$ipv6")
        check(result.code == 0) { result.output.ifBlank { "Failed to install transparent proxy rules" } }
    }

    private fun restartTarget(packageName: String) {
        RootShell.exec("am force-stop '$packageName'; sleep 0.3; monkey -p '$packageName' -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true")
    }

    @Suppress("DEPRECATION")
    private fun readLoop(fd: ParcelFileDescriptor) {
        try {
            DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(fd)).use { input ->
                while (true) {
                    val header = input.readLine() ?: break
                    val parts = header.split(':', limit = 6)
                    if (parts.size != 6) continue
                    val timestamp = parts[0].toLongOrNull() ?: continue
                    val ipVersion = parts[1].toIntOrNull() ?: 0
                    val ipProtocol = parts[2].toIntOrNull() ?: 0
                    val port = parts[3].toIntOrNull() ?: 0
                    val kind = DecryptedKind.fromWire(parts[4])
                    val length = parts[5].toIntOrNull() ?: continue
                    if (length < 0 || length > 64 * 1024 * 1024) break
                    val payload = ByteArray(length)
                    input.readFully(payload)
                    if (kind == DecryptedKind.RUNNING) runningSignal?.complete(Unit)
                    processEvent(timestamp, ipVersion, ipProtocol, port, kind, payload)
                }
            }
        } catch (e: IOException) {
            if (!expectedSocketClose) {
                InterceptionRuntime.update { old ->
                    old.copy(
                        phase = InterceptionPhase.ERROR,
                        lastError = "Plaintext stream interrupted: ${e.message ?: e.javaClass.simpleName}",
                        message = "MITM plaintext stream stopped",
                    )
                }
            }
        }
    }

    private fun processEvent(timestamp: Long, ipVersion: Int, ipProtocol: Int, port: Int, kind: DecryptedKind, payload: ByteArray) {
        val id = eventSeq.incrementAndGet()
        val dir = sessionDir ?: return
        val payloadPath = if (payload.isNotEmpty()) {
            File(dir, "payloads/${"%06d".format(id)}-${kind.wireName}.bin").apply { writeBytes(payload) }.absolutePath
        } else null
        val parsed = PlaintextParser.preview(kind, payload)
        val event = DecryptedEvent(id, timestamp, ipVersion, ipProtocol, port, kind, payload.size, parsed.first, parsed.second, payloadPath)
        appendEvent(event)
        updateHttp(event, payload)
        InterceptionRuntime.update { old ->
            val s = old.session
            val nextSession = if (s != null) s.copy(
                decryptedEvents = s.decryptedEvents + if (kind == DecryptedKind.RUNNING || kind == DecryptedKind.LOG) 0 else 1,
                httpRequests = s.httpRequests + if (kind == DecryptedKind.HTTP_REQUEST) 1 else 0,
                httpResponses = s.httpResponses + if (kind == DecryptedKind.HTTP_RESPONSE) 1 else 0,
                tlsErrors = s.tlsErrors + if (kind == DecryptedKind.TLS_ERROR) 1 else 0,
            ) else null
            old.copy(session = nextSession, recentEvents = (listOf(event) + old.recentEvents).take(200))
        }
    }

    private fun updateHttp(event: DecryptedEvent, payload: ByteArray) {
        val parsed = PlaintextParser.parseHttp(event.kind, payload) ?: return
        if (event.kind == DecryptedKind.HTTP_REQUEST) {
            val exchange = HttpExchangeSummary(
                id = event.id, timestamp = event.timestamp, port = event.port,
                requestLine = parsed.firstLine, host = parsed.host, method = parsed.method,
                contentType = parsed.contentType, requestBytes = payload.size,
            )
            pendingHttp.getOrPut(event.port) { ArrayDeque() }.addLast(event.id)
            InterceptionRuntime.update { it.copy(httpExchanges = (listOf(exchange) + it.httpExchanges).take(200)) }
        } else {
            val requestId = pendingHttp[event.port]?.pollFirst()
            InterceptionRuntime.update { old ->
                val list = old.httpExchanges.toMutableList()
                val idx = requestId?.let { id -> list.indexOfFirst { it.id == id } } ?: -1
                if (idx >= 0) list[idx] = list[idx].copy(responseLine = parsed.firstLine, statusCode = parsed.statusCode, responseBytes = payload.size, contentType = parsed.contentType ?: list[idx].contentType)
                else list.add(0, HttpExchangeSummary(event.id, event.timestamp, event.port, responseLine = parsed.firstLine, statusCode = parsed.statusCode, contentType = parsed.contentType, responseBytes = payload.size))
                old.copy(httpExchanges = list.take(200))
            }
        }
    }

    private fun appendEvent(event: DecryptedEvent) {
        val json = JSONObject().apply {
            put("id", event.id); put("timestamp", event.timestamp); put("ipVersion", event.ipVersion); put("ipProtocol", event.ipProtocol)
            put("port", event.port); put("kind", event.kind.wireName); put("size", event.size); put("title", event.title); put("preview", event.preview)
            put("payloadPath", event.payloadPath ?: JSONObject.NULL)
        }
        eventsFile?.appendText(json.toString() + "\n")
    }

    private fun writeSessionSummary(session: InterceptionSession, state: InterceptionState) {
        val dir = File(session.directory)
        val json = JSONObject().apply {
            put("id", session.id); put("packageName", session.target.packageName); put("appLabel", session.target.label); put("uid", session.target.uid)
            put("startedAt", session.startedAt); put("stoppedAt", session.stoppedAt ?: JSONObject.NULL); put("decryptedEvents", state.session?.decryptedEvents ?: 0)
            put("httpRequests", state.session?.httpRequests ?: 0); put("httpResponses", state.session?.httpResponses ?: 0); put("tlsErrors", state.session?.tlsErrors ?: 0)
            put("blockQuic", currentOptions.blockQuic); put("fullPayload", currentOptions.fullPayload); put("restartTarget", currentOptions.restartTarget)
        }
        File(dir, "session.json").writeText(json.toString(2))
    }
}
