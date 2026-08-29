package com.arthur.nfclab.storage

import android.content.Context
import com.arthur.nfclab.domain.AccessDiagnosticReport
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.ProvisioningCapabilityReport
import com.arthur.nfclab.domain.SimulationCapabilityReport
import com.arthur.nfclab.nfc.TagSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScanAutomationStore(context: Context) {
    private val filesDir = context.filesDir
    private val statusFile = File(filesDir, "scan_status.json")
    private val lastScanFile = File(filesDir, "last_scan.json")
    private val rootStatusFile = File(filesDir, "root_status.json")
    private val deviceProfileFile = File(filesDir, "device_nfc_profile.json")
    private val xiaomiProfileFile = File(filesDir, "xiaomi_nfc_profile.json")
    private val accessDiagnosticFile = File(filesDir, "access_diagnostic.json")
    private val simulationCapabilityFile = File(filesDir, "simulation_capability.json")
    private val provisioningCapabilityFile = File(filesDir, "provisioning_capability.json")

    @Synchronized
    fun writeWaiting(
        sessionId: String,
        startedAtMs: Long,
        timeoutMs: Long,
        nfcAvailable: Boolean,
        nfcEnabled: Boolean,
    ) {
        writeStatus(
            JSONObject()
                .put("sessionId", sessionId)
                .put("status", "waiting")
                .put("startedAtMs", startedAtMs)
                .put("timeoutMs", timeoutMs)
                .put("nfcAvailable", nfcAvailable)
                .put("nfcEnabled", nfcEnabled)
                .put("message", "Reader Mode armed; waiting for RF tag activation"),
        )
    }

    @Synchronized
    fun writeDetected(
        sessionId: String?,
        startedAtMs: Long,
        snapshot: TagSnapshot,
    ) {
        val payload = snapshot.toJson()
            .put("sessionId", sessionId ?: JSONObject.NULL)
            .put("status", "detected")
            .put("startedAtMs", startedAtMs)
            .put("detectedAtMs", System.currentTimeMillis())
        lastScanFile.writeText(payload.toString(2))
        writeStatus(payload)
    }

    @Synchronized
    fun writeTimeout(sessionId: String, startedAtMs: Long, timeoutMs: Long) {
        writeStatus(
            JSONObject()
                .put("sessionId", sessionId)
                .put("status", "timeout")
                .put("startedAtMs", startedAtMs)
                .put("finishedAtMs", System.currentTimeMillis())
                .put("timeoutMs", timeoutMs)
                .put("noTagActivation", true)
                .put(
                    "message",
                    "No RF tag activation observed. Possible causes: card outside antenna coupling zone, non-13.56MHz access card, or unsupported RF technology.",
                ),
        )
    }

    @Synchronized
    fun writeHceReady(
        sessionId: String,
        startedAtMs: Long,
        aid: String,
        payload: String,
        nfcAvailable: Boolean,
        nfcEnabled: Boolean,
        supportsHce: Boolean,
    ) {
        writeStatus(
            JSONObject()
                .put("sessionId", sessionId)
                .put("status", "hce_ready")
                .put("startedAtMs", startedAtMs)
                .put("finishedAtMs", System.currentTimeMillis())
                .put("nfcAvailable", nfcAvailable)
                .put("nfcEnabled", nfcEnabled)
                .put("supportsHce", supportsHce)
                .put("aid", aid)
                .put("payload", payload)
                .put("message", "Reader Mode disabled; custom ISO-DEP HCE service is ready for host routing."),
        )
    }

    @Synchronized
    fun writeRootDiagnostics(sessionId: String, report: String) {
        rootStatusFile.writeText(
            JSONObject()
                .put("sessionId", sessionId)
                .put("status", "root_diagnostics_complete")
                .put("finishedAtMs", System.currentTimeMillis())
                .put("report", report)
                .toString(2),
        )
    }

    @Synchronized
    fun writeDeviceProfile(sessionId: String, profile: NfcDeviceProfile) {
        deviceProfileFile.writeText(
            profile.toJson()
                .put("sessionId", sessionId)
                .put("status", "device_profile_complete")
                .toString(2),
        )
    }

    @Synchronized
    fun writeLegacyXiaomiProfile(sessionId: String, profile: NfcDeviceProfile) {
        xiaomiProfileFile.writeText(
            profile.toJson()
                .put("sessionId", sessionId)
                .put("status", "xiaomi_profile_complete")
                .toString(2),
        )
    }

    @Synchronized
    fun writeSimulationCapability(sessionId: String, report: SimulationCapabilityReport) {
        simulationCapabilityFile.writeText(
            report.toJson()
                .put("sessionId", sessionId)
                .put("status", "simulation_capability_complete")
                .toString(2),
        )
    }

    @Synchronized
    fun writeProvisioningCapability(sessionId: String, report: ProvisioningCapabilityReport) {
        provisioningCapabilityFile.writeText(
            report.toJson()
                .put("sessionId", sessionId)
                .put("status", "provisioning_capability_complete")
                .toString(2),
        )
    }

    @Synchronized
    fun writeAccessDiagnosticRunning(sessionId: String, cardTitle: String?) {
        accessDiagnosticFile.writeText(
            JSONObject()
                .put("sessionId", sessionId)
                .put("status", "access_diagnostic_running")
                .put("startedAtMs", System.currentTimeMillis())
                .put("cardTitle", cardTitle ?: JSONObject.NULL)
                .toString(2),
        )
    }

    @Synchronized
    fun writeAccessDiagnosticReport(sessionId: String, report: AccessDiagnosticReport) {
        accessDiagnosticFile.writeText(
            report.toJson()
                .put("automationSessionId", sessionId)
                .put("status", "access_diagnostic_complete")
                .toString(2),
        )
    }

    @Synchronized
    fun writeAccessDiagnosticError(sessionId: String, message: String) {
        accessDiagnosticFile.writeText(
            JSONObject()
                .put("sessionId", sessionId)
                .put("status", "access_diagnostic_error")
                .put("finishedAtMs", System.currentTimeMillis())
                .put("message", message)
                .toString(2),
        )
    }

    @Synchronized
    fun isWaiting(sessionId: String): Boolean {
        if (!statusFile.exists()) return false
        return runCatching {
            val json = JSONObject(statusFile.readText())
            json.optString("sessionId") == sessionId && json.optString("status") == "waiting"
        }.getOrDefault(false)
    }

    private fun writeStatus(json: JSONObject) {
        statusFile.writeText(json.toString(2))
    }

    private fun TagSnapshot.toJson(): JSONObject = JSONObject().apply {
        put("timestampMs", timestampMs)
        put("idHex", idHex)
        put("technologies", JSONArray(technologies))
        put("details", JSONObject(details))
        put("ndefRecords", JSONArray(ndefRecords))
        put("warning", warning ?: JSONObject.NULL)
    }
}
