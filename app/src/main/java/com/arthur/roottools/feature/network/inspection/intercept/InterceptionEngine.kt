package com.arthur.roottools.feature.network.inspection.intercept

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import com.arthur.roottools.feature.network.inspection.capture.AppTarget
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
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class InterceptionEngine(
    context: Context,
    private val rootAvailable: () -> Boolean,
    private val addonRepository: MitmAddonRepository,
    private val certificateManager: InterceptionCertificateManager,
    private val networkController: InterceptionNetworkController,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: MitmAddonClient? = null
    private var socket: ParcelFileDescriptor? = null
    private var readerJob: Job? = null
    private var runningSignal: CompletableDeferred<Unit>? = null
    private var sessionDirectory: File? = null
    private var eventsFile: File? = null
    private var currentOptions = InterceptionOptions()
    @Volatile private var expectedSocketClose = false
    private val eventSequence = AtomicLong(0)
    private val pendingHttp = mutableMapOf<Int, ArrayDeque<Long>>()

    suspend fun start(target: AppTarget, options: InterceptionOptions) = withContext(Dispatchers.IO) {
        val phase = InterceptionRuntime.state.value.phase
        if (phase != InterceptionPhase.IDLE && phase != InterceptionPhase.ERROR) return@withContext
        InterceptionRuntime.update {
            it.copy(
                phase = InterceptionPhase.STARTING,
                status = InterceptionStatus.STARTING,
                target = target,
                options = options,
                lastError = null,
            )
        }
        try {
            check(rootAvailable()) { "Root authorization is required for transparent interception" }
            check(addonRepository.installedStatus().installed) { "MITM add-on is not installed" }
            val certificate = certificateManager.status()
            check(certificate.available && certificate.source == CertificateSource.MITM_ADDON) {
                "Import the MITM add-on CA first"
            }
            check(certificate.systemTrusted) {
                if (certificate.requiresReboot) {
                    "System CA is staged but not active; reboot outside RootTools when ready"
                } else {
                    "MITM CA is not trusted by the system"
                }
            }

            val cleanup = networkController.cleanupRules()
            check(cleanup.success) { cleanup.technicalDetail ?: "Unable to clear stale interception rules" }
            currentOptions = options
            eventSequence.set(0)
            pendingHttp.clear()
            val id = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(java.util.Date())
            val rootDirectory = File(
                appContext.getExternalFilesDir(null),
                "intercepts/$id-${target.packageName}",
            ).apply { mkdirs() }
            File(rootDirectory, "payloads").mkdirs()
            sessionDirectory = rootDirectory
            eventsFile = File(rootDirectory, "events.jsonl")
            val session = InterceptionSession(id, target, System.currentTimeMillis(), rootDirectory.absolutePath)
            InterceptionRuntime.update {
                it.copy(session = session, recentEvents = emptyList(), httpExchanges = emptyList())
            }

            val addon = MitmAddonClient(appContext)
            client = addon
            check(addon.connect(onDisconnect = {
                InterceptionRuntime.update { old ->
                    old.copy(
                        phase = InterceptionPhase.ERROR,
                        status = InterceptionStatus.ADDON_DISCONNECTED,
                        lastError = "MITM add-on disconnected",
                    )
                }
            })) { "Unable to bind MITM add-on service" }
            InterceptionRuntime.update { it.copy(status = InterceptionStatus.VERIFYING_CA) }
            val runtimePem = addon.requestCertificate(CERTIFICATE_TIMEOUT_MS)
            check(certificateManager.fingerprintOfPem(runtimePem) == certificate.fingerprint) {
                "MITM add-on CA changed; refresh system trust before intercepting"
            }
            if (!addonRepository.isBatteryOptimizationIgnored()) addon.disableDoze()
            runningSignal = CompletableDeferred()
            expectedSocketClose = false
            InterceptionRuntime.update { it.copy(status = InterceptionStatus.STARTING_PROXY) }
            socket = addon.startProxy(PROXY_PORT, options.fullPayload, options.sslInsecureUpstream)
            readerJob = scope.launch { readLoop(requireNotNull(socket)) }
            withTimeout(PROXY_START_TIMEOUT_MS) { runningSignal?.await() }

            val rules = networkController.installRules(target.uid, PROXY_PORT, options.blockQuic)
            check(rules.success) { rules.technicalDetail ?: "Unable to install interception rules" }
            if (options.restartTarget) restartTarget(target.packageName)
            InterceptionRuntime.update {
                it.copy(phase = InterceptionPhase.RUNNING, status = InterceptionStatus.RUNNING)
            }
        } catch (error: Throwable) {
            runCatching { stopInternal(markComplete = false) }
            InterceptionRuntime.update {
                it.copy(
                    phase = InterceptionPhase.ERROR,
                    status = InterceptionStatus.ERROR,
                    lastError = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        InterceptionRuntime.update {
            it.copy(phase = InterceptionPhase.STOPPING, status = InterceptionStatus.STOPPING)
        }
        stopInternal(markComplete = true)
    }

    private suspend fun stopInternal(markComplete: Boolean) {
        expectedSocketClose = true
        networkController.cleanupRules()
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
        if (markComplete) {
            InterceptionRuntime.update {
                it.copy(
                    phase = InterceptionPhase.IDLE,
                    status = InterceptionStatus.STOPPED,
                    session = completed,
                    target = null,
                )
            }
        }
    }

    private suspend fun restartTarget(packageName: String) {
        val stopped = networkController.forceStop(packageName)
        check(stopped.success) { stopped.technicalDetail ?: "Unable to stop target app" }
        val launch = appContext.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) appContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Suppress("DEPRECATION")
    private fun readLoop(fileDescriptor: ParcelFileDescriptor) {
        try {
            DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(fileDescriptor)).use { input ->
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
                    if (length !in 0..MAX_EVENT_BYTES) break
                    val payload = ByteArray(length)
                    input.readFully(payload)
                    if (kind == DecryptedKind.RUNNING) runningSignal?.complete(Unit)
                    processEvent(timestamp, ipVersion, ipProtocol, port, kind, payload)
                }
            }
        } catch (error: IOException) {
            if (!expectedSocketClose) {
                InterceptionRuntime.update { old ->
                    old.copy(
                        phase = InterceptionPhase.ERROR,
                        status = InterceptionStatus.PLAINTEXT_STREAM_INTERRUPTED,
                        lastError = error.message ?: error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    private fun processEvent(
        timestamp: Long,
        ipVersion: Int,
        ipProtocol: Int,
        port: Int,
        kind: DecryptedKind,
        payload: ByteArray,
    ) {
        val id = eventSequence.incrementAndGet()
        val directory = sessionDirectory ?: return
        val payloadPath = if (payload.isNotEmpty()) {
            File(directory, "payloads/${"%06d".format(id)}-${kind.wireName}.bin")
                .apply { writeBytes(payload) }
                .absolutePath
        } else {
            null
        }
        val parsed = PlaintextParser.preview(kind, payload)
        val event = DecryptedEvent(
            id,
            timestamp,
            ipVersion,
            ipProtocol,
            port,
            kind,
            payload.size,
            parsed.first,
            parsed.second,
            payloadPath,
        )
        appendEvent(event)
        updateHttp(event, payload)
        InterceptionRuntime.update { old ->
            val session = old.session
            val next = session?.copy(
                decryptedEvents = session.decryptedEvents + if (kind == DecryptedKind.RUNNING || kind == DecryptedKind.LOG) 0 else 1,
                httpRequests = session.httpRequests + if (kind == DecryptedKind.HTTP_REQUEST) 1 else 0,
                httpResponses = session.httpResponses + if (kind == DecryptedKind.HTTP_RESPONSE) 1 else 0,
                tlsErrors = session.tlsErrors + if (kind == DecryptedKind.TLS_ERROR) 1 else 0,
            )
            old.copy(session = next, recentEvents = (listOf(event) + old.recentEvents).take(MAX_RECENT_EVENTS))
        }
    }

    private fun updateHttp(event: DecryptedEvent, payload: ByteArray) {
        val parsed = PlaintextParser.parseHttp(event.kind, payload) ?: return
        if (event.kind == DecryptedKind.HTTP_REQUEST) {
            val exchange = HttpExchangeSummary(
                id = event.id,
                timestamp = event.timestamp,
                port = event.port,
                requestLine = parsed.firstLine,
                host = parsed.host,
                method = parsed.method,
                contentType = parsed.contentType,
                requestBytes = payload.size,
            )
            pendingHttp.getOrPut(event.port) { ArrayDeque() }.addLast(event.id)
            InterceptionRuntime.update { old ->
                old.copy(httpExchanges = (listOf(exchange) + old.httpExchanges).take(MAX_RECENT_EVENTS))
            }
        } else {
            val requestId = pendingHttp[event.port]?.pollFirst()
            InterceptionRuntime.update { old ->
                val exchanges = old.httpExchanges.toMutableList()
                val index = requestId?.let { id -> exchanges.indexOfFirst { it.id == id } } ?: -1
                if (index >= 0) {
                    exchanges[index] = exchanges[index].copy(
                        responseLine = parsed.firstLine,
                        statusCode = parsed.statusCode,
                        responseBytes = payload.size,
                        contentType = parsed.contentType ?: exchanges[index].contentType,
                    )
                } else {
                    exchanges.add(
                        0,
                        HttpExchangeSummary(
                            event.id,
                            event.timestamp,
                            event.port,
                            responseLine = parsed.firstLine,
                            statusCode = parsed.statusCode,
                            contentType = parsed.contentType,
                            responseBytes = payload.size,
                        ),
                    )
                }
                old.copy(httpExchanges = exchanges.take(MAX_RECENT_EVENTS))
            }
        }
    }

    private fun appendEvent(event: DecryptedEvent) {
        val json = JSONObject().apply {
            put("id", event.id)
            put("timestamp", event.timestamp)
            put("ipVersion", event.ipVersion)
            put("ipProtocol", event.ipProtocol)
            put("port", event.port)
            put("kind", event.kind.wireName)
            put("size", event.size)
            put("title", event.title)
            put("preview", event.preview)
            put("payloadPath", event.payloadPath ?: JSONObject.NULL)
        }
        eventsFile?.appendText(json.toString() + "\n")
    }

    private fun writeSessionSummary(session: InterceptionSession, state: InterceptionState) {
        val json = JSONObject().apply {
            put("id", session.id)
            put("packageName", session.target.packageName)
            put("appLabel", session.target.label)
            put("uid", session.target.uid)
            put("startedAt", session.startedAt)
            put("stoppedAt", session.stoppedAt ?: JSONObject.NULL)
            put("decryptedEvents", state.session?.decryptedEvents ?: 0)
            put("httpRequests", state.session?.httpRequests ?: 0)
            put("httpResponses", state.session?.httpResponses ?: 0)
            put("tlsErrors", state.session?.tlsErrors ?: 0)
            put("blockQuic", currentOptions.blockQuic)
            put("fullPayload", currentOptions.fullPayload)
            put("restartTarget", currentOptions.restartTarget)
        }
        File(session.directory, "session.json").writeText(json.toString(2))
    }

    companion object {
        const val PROXY_PORT = 7780
        private const val CERTIFICATE_TIMEOUT_MS = 30_000L
        private const val PROXY_START_TIMEOUT_MS = 60_000L
        private const val MAX_EVENT_BYTES = 64 * 1024 * 1024
        private const val MAX_RECENT_EVENTS = 200
    }
}
