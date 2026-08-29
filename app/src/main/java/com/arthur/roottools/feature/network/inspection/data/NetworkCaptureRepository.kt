package com.arthur.roottools.feature.network.inspection.data

import android.content.Context
import android.content.pm.ApplicationInfo
import com.arthur.roottools.feature.network.inspection.capture.AppTarget
import com.arthur.roottools.feature.network.inspection.capture.CaptureAnalysis
import com.arthur.roottools.feature.network.inspection.capture.CaptureBinary
import com.arthur.roottools.feature.network.inspection.capture.CaptureReadinessPolicy
import com.arthur.roottools.feature.network.inspection.capture.CaptureSession
import com.arthur.roottools.feature.network.inspection.capture.CaptureSignal
import com.arthur.roottools.feature.network.inspection.capture.CaptureState
import com.arthur.roottools.feature.network.inspection.capture.CaptureStatus
import com.arthur.roottools.feature.network.inspection.capture.FlowSummary
import com.arthur.roottools.feature.network.inspection.capture.NetworkCaptureCommandPolicy
import com.arthur.roottools.feature.network.inspection.capture.PacketField
import com.arthur.roottools.feature.network.inspection.capture.PacketSummary
import com.arthur.roottools.feature.network.inspection.capture.PcapParser
import com.arthur.roottools.feature.network.inspection.capture.ProtocolCount
import com.arthur.roottools.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NetworkCaptureRepository(
    private val context: Context,
    private val shell: RootShell,
    private val rootAvailable: () -> Boolean,
) {
    private val capturesDir = File(context.getExternalFilesDir(null), "captures").apply { mkdirs() }
    private val pcapd = PcapdBridge(context, shell)
    private val stateMutable = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = stateMutable.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val root = rootAvailable()
        val tcpdump = if (root) findCommandPath(CaptureBinary.TCPDUMP) else null
        val sessions = loadSessions()
        val pending = sessions.firstOrNull { it.stoppedAt == null }
        val pcapdRunning = root && pcapd.isRunning()
        val active = pending?.takeIf { pcapdRunning || isCaptureAlive(it.id) }
        if (root) recoverAbandonedSessions(sessions.filter { it.stoppedAt == null && it.id != active?.id })
        val refreshedSessions = loadSessions()
        stateMutable.value = CaptureState(
            rootAvailable = root,
            tcpdumpPath = tcpdump,
            active = active,
            sessions = refreshedSessions.filter { it.stoppedAt != null },
            status = CaptureReadinessPolicy.status(
                rootAvailable = root,
                pcapdAvailable = pcapd.binaryPath != null,
                tcpdumpAvailable = tcpdump != null,
                activeSessionRecovered = active != null,
            ),
        )
    }

    suspend fun installedApps(): List<AppTarget> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.uid >= 10_000 }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { AppTarget(pm.getApplicationLabel(it).toString(), it.packageName, it.uid) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    suspend fun start(target: AppTarget?) = withContext(Dispatchers.IO) {
        val current = stateMutable.value
        if (current.active != null) return@withContext
        val tcpdump = current.tcpdumpPath
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val packageName = target?.packageName ?: "__all__"
        val label = target?.label ?: "Whole device"
        val uid = target?.uid ?: -1
        val pcap = File(capturesDir, "$id-$packageName.pcap")
        val log = File(capturesDir, "$id.log")
        if (pcapd.binaryPath != null) {
            pcapd.start(pcap, target?.uid).onFailure {
                stateMutable.value = current.copy(status = CaptureStatus.ERROR, technicalDetail = it.message)
                return@withContext
            }
            log.writeText("backend=pcapd\nuid=${target?.uid ?: -1}\npackage=$packageName\n")
        } else {
            if (tcpdump == null) {
                stateMutable.value = current.copy(status = CaptureStatus.BACKEND_UNAVAILABLE, technicalDetail = null)
                return@withContext
            }
            val pid = File(capturesDir, "$id.pid")
            val command = NetworkCaptureCommandPolicy.tcpdumpLaunch(
                binaryPath = tcpdump,
                outputPcap = pcap.absolutePath,
                outputLog = log.absolutePath,
                pidFile = pid.absolutePath,
            )
            if (command == null) {
                stateMutable.value = current.copy(
                    status = CaptureStatus.ERROR,
                    technicalDetail = "capture paths failed validation",
                )
                return@withContext
            }
            val result = shell.execute(command)
            if (!result.success) {
                stateMutable.value = current.copy(status = CaptureStatus.ERROR, technicalDetail = result.output)
                return@withContext
            }
        }
        val session = CaptureSession(id, label, packageName, uid, System.currentTimeMillis(), pcapPath = pcap.absolutePath)
        writeMetadata(session)
        stateMutable.value = current.copy(
            active = session,
            status = CaptureStatus.CAPTURING,
            technicalDetail = null,
        )
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        val current = stateMutable.value
        val active = current.active ?: return@withContext
        val pidFile = File(capturesDir, "${active.id}.pid")
        if (pcapd.isRunning()) {
            pcapd.stop()
            delay(180)
        }
        val pid = pidFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
        if (pid != null) {
            signal(pid, CaptureSignal.INTERRUPT)
            for (attempt in 0 until 10) {
                if (!processAlive(pid)) break
                delay(100)
            }
            if (processAlive(pid)) signal(pid, CaptureSignal.TERMINATE)
        }
        val analysis = PcapParser.parse(File(active.pcapPath))
        val completed = active.copy(stoppedAt = System.currentTimeMillis(), analysis = analysis)
        writeMetadata(completed)
        pidFile.delete()
        stateMutable.value = current.copy(
            active = null,
            sessions = listOf(completed) + current.sessions.filterNot { it.id == completed.id },
            status = CaptureStatus.CAPTURE_COMPLETE,
            technicalDetail = null,
        )
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        stateMutable.value = stateMutable.value.copy(sessions = loadSessions())
    }

    suspend fun ensurePacketAnalysis(id: String) = withContext(Dispatchers.IO) {
        val current = stateMutable.value
        val session = current.sessions.firstOrNull { it.id == id } ?: return@withContext
        if (session.analysis?.packets?.isNotEmpty() == true) return@withContext
        val pcap = File(session.pcapPath)
        if (!pcap.exists()) return@withContext
        val updated = session.copy(analysis = PcapParser.parse(pcap))
        writeMetadata(updated)
        stateMutable.value = current.copy(sessions = current.sessions.map { if (it.id == id) updated else it })
    }

    private fun loadSessions(): List<CaptureSession> = capturesDir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { runCatching { readMetadata(it) }.getOrNull() }
        ?.sortedByDescending { it.startedAt }
        ?: emptyList()

    private suspend fun isCaptureAlive(id: String): Boolean {
        val pid = File(capturesDir, "$id.pid").takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: return false
        return processAlive(pid)
    }

    private suspend fun processAlive(pid: Long): Boolean {
        val command = NetworkCaptureCommandPolicy.processAlive(pid) ?: return false
        return shell.execute(command, timeoutSeconds = 3).success
    }

    private suspend fun recoverAbandonedSessions(sessions: List<CaptureSession>) {
        sessions.forEach { session ->
            val pidFile = File(capturesDir, "${session.id}.pid")
            val pid = pidFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
            if (pid != null && processAlive(pid)) signal(pid, CaptureSignal.INTERRUPT)
            delay(120)
            val analysis = PcapParser.parse(File(session.pcapPath))
            writeMetadata(session.copy(stoppedAt = System.currentTimeMillis(), analysis = analysis))
            pidFile.delete()
        }
    }

    private suspend fun signal(pid: Long, signal: CaptureSignal) {
        val command = NetworkCaptureCommandPolicy.signal(pid, signal) ?: return
        shell.execute(command, timeoutSeconds = 3)
    }

    private suspend fun findCommandPath(binary: CaptureBinary): String? {
        val result = shell.execute(NetworkCaptureCommandPolicy.commandPath(binary), timeoutSeconds = 3)
        return result.output.trim().takeIf { result.success && it.startsWith('/') && '\n' !in it && '\r' !in it }
    }

    private fun writeMetadata(s: CaptureSession) {
        val a = s.analysis
        val json = JSONObject().apply {
            put("id", s.id); put("appLabel", s.appLabel); put("packageName", s.packageName); put("uid", s.uid)
            put("startedAt", s.startedAt); put("stoppedAt", s.stoppedAt ?: JSONObject.NULL); put("pcapPath", s.pcapPath)
            if (a != null) put("analysis", JSONObject().apply {
                put("packetCount", a.packetCount); put("byteCount", a.byteCount)
                put("protocols", JSONArray(a.protocols.map { JSONObject().put("protocol", it.protocol).put("packets", it.packets) }))
                put("flows", JSONArray(a.flows.map { f -> JSONObject().put("protocol", f.protocol).put("source", f.source)
                    .put("destination", f.destination).put("host", f.host ?: JSONObject.NULL).put("hint", f.hint ?: JSONObject.NULL).put("packets", f.packets) }))
                put("packets", JSONArray(a.packets.map { p -> JSONObject()
                    .put("id", p.id).put("timestampMicros", p.timestampMicros).put("capturedLength", p.capturedLength).put("originalLength", p.originalLength)
                    .put("protocol", p.protocol).put("source", p.source).put("destination", p.destination).put("title", p.title)
                    .put("subtitle", p.subtitle ?: JSONObject.NULL).put("payloadText", p.payloadText ?: JSONObject.NULL).put("payloadHex", p.payloadHex ?: JSONObject.NULL)
                    .put("fields", JSONArray(p.fields.map { field -> JSONObject().put("label", field.label).put("value", field.value) }))
                }))
            })
        }
        File(capturesDir, "${s.id}.json").writeText(json.toString(2))
    }

    private fun readMetadata(file: File): CaptureSession {
        val j = JSONObject(file.readText())
        val a = j.optJSONObject("analysis")?.let { aj ->
            val protocols = aj.optJSONArray("protocols") ?: JSONArray()
            val flows = aj.optJSONArray("flows") ?: JSONArray()
            val packets = aj.optJSONArray("packets") ?: JSONArray()
            CaptureAnalysis(
                aj.optInt("packetCount"), aj.optLong("byteCount"),
                (0 until protocols.length()).map { protocols.getJSONObject(it).let { p -> ProtocolCount(p.getString("protocol"), p.getInt("packets")) } },
                (0 until flows.length()).map { flows.getJSONObject(it).let { f -> FlowSummary(f.getString("protocol"), f.getString("source"), f.getString("destination"), f.optString("host").takeIf { it.isNotBlank() && it != "null" }, f.optString("hint").takeIf { it.isNotBlank() && it != "null" }, f.optInt("packets", 1)) } },
                (0 until packets.length()).map { packets.getJSONObject(it).let { p ->
                    val fields = p.optJSONArray("fields") ?: JSONArray()
                    PacketSummary(
                        id = p.getInt("id"), timestampMicros = p.optLong("timestampMicros"), capturedLength = p.optInt("capturedLength"), originalLength = p.optInt("originalLength"),
                        protocol = p.getString("protocol"), source = p.getString("source"), destination = p.getString("destination"), title = p.getString("title"),
                        subtitle = p.optString("subtitle").takeIf { it.isNotBlank() && it != "null" },
                        fields = (0 until fields.length()).map { index -> fields.getJSONObject(index).let { field -> PacketField(field.getString("label"), field.getString("value")) } },
                        payloadText = p.optString("payloadText").takeIf { it.isNotBlank() && it != "null" },
                        payloadHex = p.optString("payloadHex").takeIf { it.isNotBlank() && it != "null" },
                    )
                } },
            )
        }
        return CaptureSession(j.getString("id"), j.getString("appLabel"), j.getString("packageName"), j.getInt("uid"), j.getLong("startedAt"), j.optLong("stoppedAt").takeIf { !j.isNull("stoppedAt") }, j.getString("pcapPath"), a)
    }

    companion object {
        fun formatBytes(value: Long): String = when {
            value >= 1024 * 1024 -> "%.1f MB".format(value / 1048576.0)
            value >= 1024 -> "%.1f KB".format(value / 1024.0)
            else -> "$value B"
        }
    }
}
