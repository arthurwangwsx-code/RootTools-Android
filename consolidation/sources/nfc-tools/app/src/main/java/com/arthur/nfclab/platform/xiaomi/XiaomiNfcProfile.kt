package com.arthur.nfclab.platform.xiaomi

import org.json.JSONArray
import org.json.JSONObject

data class XiaomiVirtualCard(
    val title: String,
    val aid: String,
    val active: Boolean,
    val productName: String,
    val mifareCardType: Int,
    val sectorOverwritten: Boolean,
) {
    val typeLabel: String
        get() = when (mifareCardType) {
            0 -> "M1 实体门卡"
            1 -> "M1 空白卡"
            3 -> "M1 在线卡"
            6 -> "M1 在线卡 V3"
            7 -> "在线 CPU 卡"
            8 -> "校园卡"
            else -> "门卡类型 $mifareCardType"
        }

    val shortAid: String
        get() = if (aid.length <= 10) aid else "…${aid.takeLast(10)}"
}

data class XiaomiNfcProfile(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val hyperOsVersion: String?,
    val rootAvailable: Boolean,
    val selinuxEnforcing: Boolean,
    val nfcFirmware: String?,
    val nfcChipId: String?,
    val nfcPort: String?,
    val walletVersion: String?,
    val eSeConnected: Boolean,
    val mifareReaderEnabled: Boolean,
    val defaultMifareRoute: String?,
    val hostListenTechMask: String?,
    val extendedFieldDetectEnabled: Boolean = false,
    val t4tNfceeEnabled: Boolean = false,
    val miNfcServiceAvailable: Boolean,
    val nxpVendorServiceAvailable: Boolean,
    val eseAccessPermissionPrivileged: Boolean = false,
    val walletEseAccessGranted: Boolean = false,
    val openSeServiceAvailable: Boolean = false,
    val miSeOpenServiceAvailable: Boolean = false,
    val publicTsmFeaturePermissionPrivileged: Boolean = false,
    val miNfcApiVersion: Int?,
    val seRouting: Int?,
    val listenTechMask: Int?,
    val pollingTechMask: Int?,
    val cards: List<XiaomiVirtualCard>,
    val collectedAtMs: Long,
    val error: String? = null,
) {
    val isXiaomiDevice: Boolean
        get() = manufacturer.equals("Xiaomi", ignoreCase = true) ||
            manufacturer.equals("Redmi", ignoreCase = true)

    val officialM1OffHostReady: Boolean
        get() = isXiaomiDevice &&
            eSeConnected &&
            defaultMifareRoute.equals("0x01", ignoreCase = true) &&
            cards.isNotEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("manufacturer", manufacturer)
        put("model", model)
        put("device", device)
        put("androidRelease", androidRelease)
        put("hyperOsVersion", hyperOsVersion ?: JSONObject.NULL)
        put("rootAvailable", rootAvailable)
        put("selinuxEnforcing", selinuxEnforcing)
        put("nfcFirmware", nfcFirmware ?: JSONObject.NULL)
        put("nfcChipId", nfcChipId ?: JSONObject.NULL)
        put("nfcPort", nfcPort ?: JSONObject.NULL)
        put("walletVersion", walletVersion ?: JSONObject.NULL)
        put("eSeConnected", eSeConnected)
        put("mifareReaderEnabled", mifareReaderEnabled)
        put("defaultMifareRoute", defaultMifareRoute ?: JSONObject.NULL)
        put("hostListenTechMask", hostListenTechMask ?: JSONObject.NULL)
        put("extendedFieldDetectEnabled", extendedFieldDetectEnabled)
        put("t4tNfceeEnabled", t4tNfceeEnabled)
        put("miNfcServiceAvailable", miNfcServiceAvailable)
        put("nxpVendorServiceAvailable", nxpVendorServiceAvailable)
        put("eseAccessPermissionPrivileged", eseAccessPermissionPrivileged)
        put("walletEseAccessGranted", walletEseAccessGranted)
        put("openSeServiceAvailable", openSeServiceAvailable)
        put("miSeOpenServiceAvailable", miSeOpenServiceAvailable)
        put("publicTsmFeaturePermissionPrivileged", publicTsmFeaturePermissionPrivileged)
        put("miNfcApiVersion", miNfcApiVersion ?: JSONObject.NULL)
        put("seRouting", seRouting ?: JSONObject.NULL)
        put("listenTechMask", listenTechMask ?: JSONObject.NULL)
        put("pollingTechMask", pollingTechMask ?: JSONObject.NULL)
        put("officialM1OffHostReady", officialM1OffHostReady)
        put("collectedAtMs", collectedAtMs)
        put("error", error ?: JSONObject.NULL)
        put(
            "cards",
            JSONArray().apply {
                cards.forEach { card ->
                    put(
                        JSONObject()
                            .put("title", card.title)
                            .put("aid", card.aid)
                            .put("active", card.active)
                            .put("productName", card.productName)
                            .put("mifareCardType", card.mifareCardType)
                            .put("typeLabel", card.typeLabel)
                            .put("sectorOverwritten", card.sectorOverwritten),
                    )
                }
            },
        )
    }
}
