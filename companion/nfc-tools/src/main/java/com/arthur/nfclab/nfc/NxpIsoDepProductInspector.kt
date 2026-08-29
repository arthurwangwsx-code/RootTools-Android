package com.arthur.nfclab.nfc

import android.nfc.tech.IsoDep

data class NxpIsoDepProductInfo(
    val family: String,
    val productLabel: String,
    val implementationLabel: String,
    val storageLabel: String?,
    val hardwareMajor: Int,
    val hardwareMinor: Int,
    val softwareMajor: Int?,
    val softwareMinor: Int?,
    val storageCode: Int,
    val protocolCode: Int,
)

/**
 * Read-only NXP product identification using the public MIFARE GetVersion flow.
 *
 * The returned manufacturing/serial frame is intentionally not persisted.
 */
object NxpIsoDepProductInspector {
    private val getVersion = byteArrayOf(0x90.toByte(), 0x60, 0x00, 0x00, 0x00)
    private val additionalFrame = byteArrayOf(0x90.toByte(), 0xAF.toByte(), 0x00, 0x00, 0x00)

    fun inspect(isoDep: IsoDep): NxpIsoDepProductInfo? {
        return runCatching {
            isoDep.connect()
            isoDep.timeout = 1_500

            val frames = mutableListOf<ByteArray>()
            var response = isoDep.transceive(getVersion)
            while (frames.size < 3) {
                val parsed = parseWrappedResponse(response) ?: return@runCatching null
                frames += parsed.data
                if (parsed.status == 0x9100) break
                response = isoDep.transceive(additionalFrame)
            }

            val first = frames.getOrNull(0) ?: return@runCatching null
            if (first.size < 7 || (first[0].toInt() and 0xFF) != 0x04) return@runCatching null

            val productType = first[1].toInt() and 0xFF
            val family = classifyProductType(productType)
            val hardwareMajor = first[3].toInt() and 0xFF
            val hardwareMinor = first[4].toInt() and 0xFF
            val storageCode = first[5].toInt() and 0xFF

            val second = frames.getOrNull(1)
            NxpIsoDepProductInfo(
                family = family,
                productLabel = classifyProduct(
                    productType = productType,
                    hardwareMajor = hardwareMajor,
                    hardwareMinor = hardwareMinor,
                    storageCode = storageCode,
                ),
                implementationLabel = classifyImplementation(productType),
                storageLabel = storageLabel(storageCode),
                hardwareMajor = hardwareMajor,
                hardwareMinor = hardwareMinor,
                softwareMajor = second?.getOrNull(3)?.toInt()?.and(0xFF),
                softwareMinor = second?.getOrNull(4)?.toInt()?.and(0xFF),
                storageCode = storageCode,
                protocolCode = first[6].toInt() and 0xFF,
            )
        }.getOrNull().also {
            runCatching { isoDep.close() }
        }
    }

    private data class WrappedResponse(val data: ByteArray, val status: Int)

    private fun parseWrappedResponse(response: ByteArray): WrappedResponse? {
        if (response.size < 2) return null
        val status = ((response[response.size - 2].toInt() and 0xFF) shl 8) or
            (response[response.size - 1].toInt() and 0xFF)
        if (status != 0x91AF && status != 0x9100) return null
        return WrappedResponse(response.copyOf(response.size - 2), status)
    }

    internal fun classifyProductType(productType: Int): String = when (productType and 0x0F) {
        0x01 -> "MIFARE DESFire / DUOX family"
        0x02 -> "MIFARE Plus family"
        0x03 -> "MIFARE Ultralight family"
        0x04 -> "NTAG family"
        0x08 -> "MIFARE DESFire Light family"
        else -> "NXP MIFARE/NTAG family (type 0x%02X)".format(productType)
    }

    internal fun classifyProduct(
        productType: Int,
        hardwareMajor: Int,
        hardwareMinor: Int,
        storageCode: Int,
    ): String {
        if ((productType and 0x0F) != 0x01) return classifyProductType(productType)
        val base = when {
            hardwareMajor == 0xA0 -> "MIFARE DUOX"
            (hardwareMajor and 0x0F) == 0x01 -> "MIFARE DESFire EV1"
            (hardwareMajor and 0x0F) == 0x02 -> "MIFARE DESFire EV2"
            (hardwareMajor and 0x0F) == 0x03 -> "MIFARE DESFire EV3"
            else -> "MIFARE DESFire"
        }
        return storageLabel(storageCode)?.let { "$base $it" } ?: base
    }

    internal fun classifyImplementation(productType: Int): String = when (productType and 0xF0) {
        0x00 -> "Native MIFARE IC"
        0x80 -> "MIFARE implementation"
        0x90 -> "Java Card applet"
        0xA0 -> "MIFARE 2GO"
        else -> "Unknown implementation"
    }

    internal fun storageLabel(storageCode: Int): String? = when (storageCode) {
        0x10 -> "256 B"
        0x16 -> "2K"
        0x18 -> "4K"
        0x1A -> "8K"
        0x1C -> "16K"
        0x1E -> "32K"
        else -> null
    }

    /**
     * Backfills product labels for older read-only snapshots that already contain
     * GetVersion family/version fields but were captured before product classification
     * was added. It never invents fields when the source evidence is missing.
     */
    fun enrichSnapshot(snapshot: TagSnapshot): TagSnapshot {
        if (snapshot.details.containsKey("NXP product")) return snapshot
        val family = snapshot.details["NXP product family"] ?: return snapshot
        if (family != "MIFARE DESFire / DUOX family") return snapshot

        val version = snapshot.details["NXP hardware version"]
            ?.split('.', limit = 2)
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()
        val hardwareMajor = version.getOrNull(0) ?: return snapshot
        val hardwareMinor = version.getOrNull(1) ?: 0
        val storageCode = snapshot.details["NXP storage code"]
            ?.removePrefix("0x")
            ?.removePrefix("0X")
            ?.toIntOrNull(16)
            ?: return snapshot

        val enriched = snapshot.details.toMutableMap().apply {
            put(
                "NXP product",
                classifyProduct(
                    productType = 0x01,
                    hardwareMajor = hardwareMajor,
                    hardwareMinor = hardwareMinor,
                    storageCode = storageCode,
                ),
            )
            storageLabel(storageCode)?.let { putIfAbsent("NXP storage", it) }
        }
        return snapshot.copy(details = enriched)
    }
}
