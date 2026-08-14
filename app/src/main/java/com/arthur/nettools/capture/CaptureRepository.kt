package com.arthur.nettools.capture

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
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

class CaptureRepository(private val context: Context) {
    private val capturesDir = File(context.getExternalFilesDir(null), "captures").apply { mkdirs() }
    private val pcapd = PcapdBridge(context)
    private val stateMutable = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = stateMutable.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val root = RootShell.hasRoot()
        val tcpdump = if (root) RootShell.commandPath("tcpdump") else null
        val sessions = loadSessions()
        val active = sessions.firstOrNull { it.stoppedAt == null && (pcapd.isRunning() || isCaptureAlive(it.id)) }
        if (root) recoverAbandonedSessions(sessions.filter { it.stoppedAt == null && it.id != active?.id })
        val refreshedSessions = loadSessions()
        stateMutable.value = CaptureState(
            rootAvailable = root,
            tcpdumpPath = tcpdump,
            active = active,
            sessions = refreshedSessions.filter { it.stoppedAt != null },
            message = when {
                !root -> "Root permission is required"
                pcapd.binaryPath == null && tcpdump == null -> "No root capture backend found"
                active != null -> "Recovered active capture: ${active.appLabel}"
                pcapd.binaryPath != null -> "Root capture ready · pcapd UID filtering"
                else -> "Root capture ready · tcpdump fallback"
            },
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
                stateMutable.value = current.copy(message = "pcapd capture failed: ${it.message}")
                return@withContext
            }
            log.writeText("backend=pcapd\nuid=${target?.uid ?: -1}\npackage=$packageName\n")
        } else {
            if (tcpdump == null) {
                stateMutable.value = current.copy(message = "No capture backend found")
                return@withContext
            }
            val pid = File(capturesDir, "$id.pid")
            val cmd = "$tcpdump -i any -s 0 -U -w '${pcap.absolutePath}' >'${log.absolutePath}' 2>&1 & echo \$! > '${pid.absolutePath}'"
            val result = RootShell.exec(cmd)
            if (result.code != 0) {
                stateMutable.value = current.copy(message = "Capture failed: ${result.output}")
                return@withContext
            }
        }
        val session = CaptureSession(id, label, packageName, uid, System.currentTimeMillis(), pcapPath = pcap.absolutePath)
        writeMetadata(session)
        stateMutable.value = current.copy(active = session, message = "Capturing $label")
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        val current = stateMutable.value
        val active = current.active ?: return@withContext
        val pidFile = File(capturesDir, "${active.id}.pid")
        if (pcapd.isRunning()) {
            pcapd.stop()
            Thread.sleep(180)
        }
        val pid = pidFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
        if (pid != null) {
            RootShell.exec("kill -2 $pid")
            repeat(10) {
                if (!processAlive(pid)) return@repeat
                Thread.sleep(100)
            }
            if (processAlive(pid)) RootShell.exec("kill $pid")
        }
        val analysis = PcapParser.parse(File(active.pcapPath))
        val completed = active.copy(stoppedAt = System.currentTimeMillis(), analysis = analysis)
        writeMetadata(completed)
        pidFile.delete()
        stateMutable.value = current.copy(
            active = null,
            sessions = listOf(completed) + current.sessions.filterNot { it.id == completed.id },
            message = "Captured ${analysis.packetCount} packets / ${formatBytes(analysis.byteCount)}",
        )
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        stateMutable.value = stateMutable.value.copy(sessions = loadSessions())
    }

    private fun loadSessions(): List<CaptureSession> = capturesDir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { runCatching { readMetadata(it) }.getOrNull() }
        ?.sortedByDescending { it.startedAt }
        ?: emptyList()

    private fun isCaptureAlive(id: String): Boolean {
        val pid = File(capturesDir, "$id.pid").takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: return false
        return processAlive(pid)
    }

    private fun processAlive(pid: Long): Boolean = RootShell.exec("kill -0 $pid 2>/dev/null").code == 0

    private fun recoverAbandonedSessions(sessions: List<CaptureSession>) {
        sessions.forEach { session ->
            val pidFile = File(capturesDir, "${session.id}.pid")
            val pid = pidFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
            if (pid != null && processAlive(pid)) RootShell.exec("kill -2 $pid")
            Thread.sleep(120)
            val analysis = PcapParser.parse(File(session.pcapPath))
            writeMetadata(session.copy(stoppedAt = System.currentTimeMillis(), analysis = analysis))
            pidFile.delete()
        }
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
            })
        }
        File(capturesDir, "${s.id}.json").writeText(json.toString(2))
    }

    private fun readMetadata(file: File): CaptureSession {
        val j = JSONObject(file.readText())
        val a = j.optJSONObject("analysis")?.let { aj ->
            val protocols = aj.optJSONArray("protocols") ?: JSONArray()
            val flows = aj.optJSONArray("flows") ?: JSONArray()
            CaptureAnalysis(
                aj.optInt("packetCount"), aj.optLong("byteCount"),
                (0 until protocols.length()).map { protocols.getJSONObject(it).let { p -> ProtocolCount(p.getString("protocol"), p.getInt("packets")) } },
                (0 until flows.length()).map { flows.getJSONObject(it).let { f -> FlowSummary(f.getString("protocol"), f.getString("source"), f.getString("destination"), f.optString("host").takeIf { it.isNotBlank() && it != "null" }, f.optString("hint").takeIf { it.isNotBlank() && it != "null" }, f.optInt("packets", 1)) } },
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
