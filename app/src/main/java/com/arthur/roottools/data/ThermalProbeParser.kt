package com.arthur.roottools.data

/**
 * OEM-tolerant parser for `dumpsys thermalservice`.
 *
 * Samsung commonly exposes AP/BAT/SKIN names while Xiaomi/Qualcomm builds expose lower-case
 * battery/skin plus CPU0..CPU7 sensors. Prefer the HAL current-temperature block when present so
 * stale cached samples do not drive policy after a system upgrade or thermal-service restart.
 */
object ThermalProbeParser {
    data class Snapshot(
        val status: Int = 0,
        val apC: Float? = null,
        val skinC: Float? = null,
        val batteryC: Float? = null,
        val usbC: Float? = null,
        val pathmC: Float? = null,
    )

    private data class Temperature(
        val valueC: Float,
        val type: Int?,
        val name: String,
        val current: Boolean,
    )

    fun parse(lines: List<String>): Snapshot {
        var status = 0
        var inCurrentBlock = false
        val temperatures = mutableListOf<Temperature>()

        lines.forEach { line ->
            when {
                line.contains("Thermal Status:") -> {
                    status = line.substringAfter("Thermal Status:").trim().toIntOrNull() ?: status
                }
                line.contains("Current temperatures from HAL:", ignoreCase = true) -> {
                    inCurrentBlock = true
                }
                line.contains("Current cooling devices from HAL:", ignoreCase = true) -> {
                    inCurrentBlock = false
                }
            }

            if (!line.contains("Temperature{")) return@forEach
            val value = VALUE.find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: return@forEach
            val type = TYPE.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val name = NAME.find(line)?.groupValues?.getOrNull(1).orEmpty()
            temperatures += Temperature(value, type, name, inCurrentBlock)
        }

        val current = temperatures.filter { it.current }
        val pool = current.ifEmpty { temperatures }
        val battery = pool.firstNamed("BAT", "BATTERY") ?: pool.firstOfType(2)
        val skin = pool.firstNamed("SKIN") ?: pool.firstOfType(3)
        val ap = pool.firstNamed("AP")
            ?: pool.filter { it.type == 0 || it.name.uppercase().startsWith("CPU") }.maxByOrNull { it.valueC }
        val usb = pool.firstNamed("USB")
        val pathm = pool.firstNamed("PATHM")

        return Snapshot(
            status = status,
            apC = ap?.valueC,
            skinC = skin?.valueC,
            batteryC = battery?.valueC,
            usbC = usb?.valueC,
            pathmC = pathm?.valueC,
        )
    }

    private fun List<Temperature>.firstNamed(vararg names: String): Temperature? {
        val accepted = names.map(String::uppercase).toSet()
        return firstOrNull { it.name.uppercase() in accepted }
    }

    private fun List<Temperature>.firstOfType(type: Int): Temperature? = firstOrNull { it.type == type }

    private val VALUE = Regex("mValue=([-0-9.]+)")
    private val TYPE = Regex("mType=([0-9]+)")
    private val NAME = Regex("mName=([^,}]+)")
}
