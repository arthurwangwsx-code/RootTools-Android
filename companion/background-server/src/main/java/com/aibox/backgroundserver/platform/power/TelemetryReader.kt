package com.aibox.backgroundserver.platform.power

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.TrafficStats
import java.io.File
import kotlin.math.abs

data class TelemetrySample(
    val watts: Double?,
    val cpuLoadPercent: Double?,
    val loadAverage1: Double?,
    val loadAverage5: Double?,
    val loadAverage15: Double?,
    val memoryUsedPercent: Double?,
    val totalRxBytes: Long?,
    val totalTxBytes: Long?,
    val temperatureCelsius: Double?,
    val batteryCharging: Boolean,
)

object TelemetryReader {
    private data class CpuSnapshot(val total: Long, val idle: Long)
    private var previousCpuSnapshot: CpuSnapshot? = null

    private fun readLong(path: String): Long? =
        runCatching { File(path).readText().trim().toLong() }.getOrNull()

    private fun batteryIntent(context: Context): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun batteryPowerWatts(context: Context, batteryIntent: Intent?): Double? {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val currentUa = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (currentUa == Long.MIN_VALUE) return null
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        if (voltageMv <= 0) return null
        return abs(currentUa.toDouble() * voltageMv.toDouble()) / 1_000_000_000.0
    }

    @Synchronized
    private fun cpuUsagePercent(): Double? {
        val fields = runCatching {
            File("/proc/stat").useLines { lines -> lines.first().trim().split(Regex("\\s+")).drop(1).map(String::toLong) }
        }.getOrNull() ?: return null
        if (fields.size < 5) return null
        val idle = fields[3] + fields.getOrElse(4) { 0L }
        val total = fields.sum()
        val current = CpuSnapshot(total, idle)
        val previous = previousCpuSnapshot
        previousCpuSnapshot = current
        if (previous == null) return null
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        if (totalDelta <= 0) return null
        return ((totalDelta - idleDelta).toDouble() / totalDelta * 100.0).coerceIn(0.0, 100.0)
    }

    private fun loadAverages(): Triple<Double?, Double?, Double?> {
        val parts = runCatching { File("/proc/loadavg").readText().trim().split(Regex("\\s+")) }.getOrNull().orEmpty()
        return Triple(parts.getOrNull(0)?.toDoubleOrNull(), parts.getOrNull(1)?.toDoubleOrNull(), parts.getOrNull(2)?.toDoubleOrNull())
    }

    private fun memoryUsedPercent(): Double? {
        val values = runCatching {
            File("/proc/meminfo").readLines().associate { line ->
                val key = line.substringBefore(':')
                val value = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L
                key to value
            }
        }.getOrNull() ?: return null
        val total = values["MemTotal"] ?: return null
        val available = values["MemAvailable"] ?: return null
        if (total <= 0L) return null
        return ((total - available).toDouble() / total * 100.0).coerceIn(0.0, 100.0)
    }

    private fun thermalCelsius(batteryIntent: Intent?): Double? {
        val root = File("/sys/class/thermal")
        val zones = root.listFiles { file -> file.name.startsWith("thermal_zone") }.orEmpty()
        val preferred = zones.firstOrNull { zone ->
            val type = runCatching { File(zone, "type").readText().trim().lowercase() }.getOrDefault("")
            type.contains("cpu") || type.contains("soc") || type.contains("skin")
        } ?: zones.firstOrNull()
        val raw = preferred?.let { readLong(File(it, "temp").absolutePath) }
        if (raw != null) {
            return when {
                raw > 10_000 -> raw / 1000.0
                raw > 1_000 -> raw / 100.0
                raw > 100 -> raw / 10.0
                else -> raw.toDouble()
            }
        }
        val batteryTenthsC = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        return batteryTenthsC.takeIf { it != Int.MIN_VALUE }?.div(10.0)
    }

    fun sample(context: Context): TelemetrySample {
        val batteryIntent = batteryIntent(context)
        val (load1, load5, load15) = loadAverages()
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        return TelemetrySample(
            watts = batteryPowerWatts(context, batteryIntent),
            cpuLoadPercent = cpuUsagePercent(),
            loadAverage1 = load1,
            loadAverage5 = load5,
            loadAverage15 = load15,
            memoryUsedPercent = memoryUsedPercent(),
            totalRxBytes = TrafficStats.getTotalRxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() },
            totalTxBytes = TrafficStats.getTotalTxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() },
            temperatureCelsius = thermalCelsius(batteryIntent),
            batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }
}
