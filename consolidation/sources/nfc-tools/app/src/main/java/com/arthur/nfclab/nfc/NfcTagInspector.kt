package com.arthur.nfclab.nfc

import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import java.nio.charset.Charset

object NfcTagInspector {
    fun inspect(tag: Tag): TagSnapshot {
        val details = linkedMapOf<String, String>()
        val records = mutableListOf<String>()
        var warning: String? = null

        NfcA.get(tag)?.let {
            details["NFC-A ATQA"] = it.atqa.toHex()
            details["NFC-A SAK"] = "0x%02X".format(it.sak.toInt() and 0xFF)
            details["NFC-A max transceive"] = it.maxTransceiveLength.toString()
        }

        NfcB.get(tag)?.let {
            details["NFC-B application data"] = it.applicationData.toHex()
            details["NFC-B protocol info"] = it.protocolInfo.toHex()
            details["NFC-B max transceive"] = it.maxTransceiveLength.toString()
        }

        NfcF.get(tag)?.let {
            details["NFC-F manufacturer"] = it.manufacturer.toHex()
            details["NFC-F system code"] = it.systemCode.toHex()
            details["NFC-F max transceive"] = it.maxTransceiveLength.toString()
        }

        NfcV.get(tag)?.let {
            details["NFC-V DSF ID"] = "0x%02X".format(it.dsfId.toInt() and 0xFF)
            details["NFC-V response flags"] = "0x%02X".format(it.responseFlags.toInt() and 0xFF)
            details["NFC-V max transceive"] = it.maxTransceiveLength.toString()
        }

        IsoDep.get(tag)?.let { isoDep ->
            details["ISO-DEP historical bytes"] = isoDep.historicalBytes.toHex().ifEmpty { "-" }
            details["ISO-DEP higher layer response"] = isoDep.hiLayerResponse.toHex().ifEmpty { "-" }
            details["ISO-DEP max transceive"] = isoDep.maxTransceiveLength.toString()
            details["ISO-DEP extended APDU"] = isoDep.isExtendedLengthApduSupported.toString()

            NxpIsoDepProductInspector.inspect(isoDep)?.let { product ->
                details["NXP product"] = product.productLabel
                details["NXP product family"] = product.family
                details["NXP implementation"] = product.implementationLabel
                details["NXP hardware version"] = "${product.hardwareMajor}.${product.hardwareMinor}"
                product.softwareMajor?.let { major ->
                    details["NXP software version"] = "$major.${product.softwareMinor ?: 0}"
                }
                product.storageLabel?.let { details["NXP storage"] = it }
                details["NXP storage code"] = "0x%02X".format(product.storageCode)
                details["NXP protocol code"] = "0x%02X".format(product.protocolCode)
            }
        }

        MifareClassic.get(tag)?.let {
            details["MIFARE Classic type"] = when (it.type) {
                MifareClassic.TYPE_CLASSIC -> "Classic"
                MifareClassic.TYPE_PLUS -> "Plus"
                MifareClassic.TYPE_PRO -> "Pro"
                else -> "Unknown"
            }
            details["MIFARE Classic size"] = "${it.size} bytes"
            details["MIFARE Classic sectors"] = it.sectorCount.toString()
            details["MIFARE Classic blocks"] = it.blockCount.toString()
            warning = "检测到 MIFARE Classic：本工具只记录公开技术参数，不尝试认证、密钥恢复或受保护扇区导出。"
        }

        MifareUltralight.get(tag)?.let {
            details["MIFARE Ultralight type"] = when (it.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
                MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
                else -> "Unknown"
            }
        }

        Ndef.get(tag)?.let { ndef ->
            try {
                ndef.connect()
                details["NDEF type"] = ndef.type ?: "-"
                details["NDEF writable"] = ndef.isWritable.toString()
                details["NDEF max size"] = ndef.maxSize.toString()
                ndef.ndefMessage?.records?.forEachIndexed { index, record ->
                    records += "#${index + 1} ${describeRecord(record)}"
                }
            } catch (t: Throwable) {
                details["NDEF read error"] = t.javaClass.simpleName + ": " + (t.message ?: "unknown")
            } finally {
                runCatching { ndef.close() }
            }
        }

        return TagSnapshot(
            timestampMs = System.currentTimeMillis(),
            idHex = tag.id.toHex().ifEmpty { "(no id exposed)" },
            technologies = tag.techList.map { it.substringAfterLast('.') }.sorted(),
            details = details,
            ndefRecords = records,
            warning = warning,
        )
    }

    private fun describeRecord(record: NdefRecord): String {
        val type = record.type.toHex().ifEmpty { "-" }
        val id = record.id.toHex().ifEmpty { "-" }
        val decoded = decodeKnownRecord(record)
        val payloadHex = record.payload.take(96).toByteArray().toHex()
        val suffix = if (record.payload.size > 96) "…" else ""
        return buildString {
            append("TNF=").append(record.tnf)
            append(" type=").append(type)
            append(" id=").append(id)
            decoded?.let { append(" value=\"").append(it).append('"') }
            append(" payload=").append(payloadHex).append(suffix)
        }
    }

    private fun decodeKnownRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN) return null
        return when {
            record.type.contentEquals(NdefRecord.RTD_TEXT) -> decodeText(record.payload)
            record.type.contentEquals(NdefRecord.RTD_URI) -> runCatching { record.toUri()?.toString() }.getOrNull()
            else -> null
        }
    }

    private fun decodeText(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt() and 0xFF
        val languageLength = status and 0x3F
        val utf16 = status and 0x80 != 0
        if (payload.size <= 1 + languageLength) return null
        val charset = if (utf16) Charset.forName("UTF-16") else Charsets.UTF_8
        return runCatching {
            String(payload, 1 + languageLength, payload.size - 1 - languageLength, charset)
        }.getOrNull()
    }
}

