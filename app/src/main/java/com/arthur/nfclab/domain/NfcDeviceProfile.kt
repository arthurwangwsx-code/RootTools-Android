package com.arthur.nfclab.domain

import org.json.JSONArray
import org.json.JSONObject

enum class NfcCapability {
    NFC_READER,
    HCE_ISO_DEP,
    HCE_NFC_F,
    TYPE4_NDEF_EMULATION,
    ROOT,
    ESE,
    UICC,
    MIFARE_READER,
    MIFARE_OFF_HOST,
    VENDOR_NFC_API,
}

enum class SecureElementType { ESE, UICC }

enum class NfcCardKind { MIFARE_CLASSIC, CPU, TRANSIT, ACCESS, UNKNOWN }

enum class NfcCardRoute { HOST, ESE, UICC, UNKNOWN }

data class DeviceIdentity(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val osLabel: String? = null,
)

data class SecureElementInfo(
    val type: SecureElementType,
    val name: String,
    val available: Boolean,
    val connected: Boolean? = null,
)

data class NfcWalletInfo(
    val providerId: String,
    val label: String,
    val packageName: String,
    val version: String? = null,
    val managementAction: String? = null,
)

data class VendorNfcDetails(
    val providerId: String,
    val displayName: String,
    val firmware: String? = null,
    val chipId: String? = null,
    val port: String? = null,
    val apiVersion: Int? = null,
    val seRouting: Int? = null,
    val listenTechMask: Int? = null,
    val pollingTechMask: Int? = null,
    val extras: Map<String, String> = emptyMap(),
)

data class NfcCard(
    val id: String,
    val title: String,
    val kind: NfcCardKind,
    val technologyLabel: String,
    val active: Boolean,
    val route: NfcCardRoute,
    val sourceId: String,
    val sourceLabel: String,
    val productName: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    val shortId: String
        get() = if (id.length <= 10) id else "…${id.takeLast(10)}"
}

data class NfcDeviceProfile(
    val identity: DeviceIdentity,
    val capabilities: Set<NfcCapability>,
    val rootAvailable: Boolean,
    val selinuxEnforcing: Boolean?,
    val secureElements: List<SecureElementInfo>,
    val vendor: VendorNfcDetails? = null,
    val wallets: List<NfcWalletInfo> = emptyList(),
    val cards: List<NfcCard> = emptyList(),
    val collectedAtMs: Long,
    val error: String? = null,
) {
    val primaryEse: SecureElementInfo?
        get() = secureElements.firstOrNull { it.type == SecureElementType.ESE }

    val activeCard: NfcCard?
        get() = cards.firstOrNull { it.active }

    /**
     * Primary wallet is only a UI convenience. Domain storage remains multi-source so a device
     * can expose OEM Wallet + Google Wallet + another authorized card source at the same time.
     */
    val primaryWallet: NfcWalletInfo?
        get() = wallets.firstOrNull()

    /** Legacy source compatibility for callers that have not migrated to wallets yet. */
    val wallet: NfcWalletInfo?
        get() = primaryWallet

    fun has(capability: NfcCapability): Boolean = capability in capabilities

    fun toJson(): JSONObject = JSONObject().apply {
        put("manufacturer", identity.manufacturer)
        put("model", identity.model)
        put("device", identity.device)
        put("androidRelease", identity.androidRelease)
        put("osLabel", identity.osLabel ?: JSONObject.NULL)
        put("rootAvailable", rootAvailable)
        put("selinuxEnforcing", selinuxEnforcing ?: JSONObject.NULL)
        put("capabilities", JSONArray(capabilities.map { it.name }.sorted()))
        put("collectedAtMs", collectedAtMs)
        put("error", error ?: JSONObject.NULL)
        put(
            "secureElements",
            JSONArray().apply {
                secureElements.forEach { se ->
                    put(
                        JSONObject()
                            .put("type", se.type.name)
                            .put("name", se.name)
                            .put("available", se.available)
                            .put("connected", se.connected ?: JSONObject.NULL),
                    )
                }
            },
        )
        put(
            "vendor",
            vendor?.let { details ->
                JSONObject()
                    .put("providerId", details.providerId)
                    .put("displayName", details.displayName)
                    .put("firmware", details.firmware ?: JSONObject.NULL)
                    .put("chipId", details.chipId ?: JSONObject.NULL)
                    .put("port", details.port ?: JSONObject.NULL)
                    .put("apiVersion", details.apiVersion ?: JSONObject.NULL)
                    .put("seRouting", details.seRouting ?: JSONObject.NULL)
                    .put("listenTechMask", details.listenTechMask ?: JSONObject.NULL)
                    .put("pollingTechMask", details.pollingTechMask ?: JSONObject.NULL)
                    .put("extras", JSONObject(details.extras))
            } ?: JSONObject.NULL,
        )
        put("wallet", primaryWallet?.toJson() ?: JSONObject.NULL)
        put("wallets", JSONArray().apply { wallets.forEach { put(it.toJson()) } })
        put(
            "cards",
            JSONArray().apply {
                cards.forEach { card ->
                    put(
                        JSONObject()
                            .put("id", card.id)
                            .put("title", card.title)
                            .put("kind", card.kind.name)
                            .put("technologyLabel", card.technologyLabel)
                            .put("active", card.active)
                            .put("route", card.route.name)
                            .put("sourceId", card.sourceId)
                            .put("sourceLabel", card.sourceLabel)
                            .put("productName", card.productName ?: JSONObject.NULL)
                            .put("metadata", JSONObject(card.metadata)),
                    )
                }
            },
        )
    }

    private fun NfcWalletInfo.toJson(): JSONObject = JSONObject()
        .put("providerId", providerId)
        .put("label", label)
        .put("packageName", packageName)
        .put("version", version ?: JSONObject.NULL)
        .put("managementAction", managementAction ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject): NfcDeviceProfile? = runCatching {
            val capabilities = buildSet {
                val values = json.optJSONArray("capabilities") ?: JSONArray()
                for (index in 0 until values.length()) {
                    val name = values.optString(index)
                    runCatching { NfcCapability.valueOf(name) }.getOrNull()?.let(::add)
                }
            }

            val secureElements = buildList {
                val values = json.optJSONArray("secureElements") ?: JSONArray()
                for (index in 0 until values.length()) {
                    val item = values.optJSONObject(index) ?: continue
                    val type = runCatching {
                        SecureElementType.valueOf(item.optString("type"))
                    }.getOrNull() ?: continue
                    add(
                        SecureElementInfo(
                            type = type,
                            name = item.optString("name"),
                            available = item.optBoolean("available"),
                            connected = item.optNullableBoolean("connected"),
                        ),
                    )
                }
            }

            val vendor = json.optJSONObject("vendor")?.let { item ->
                VendorNfcDetails(
                    providerId = item.optString("providerId"),
                    displayName = item.optString("displayName"),
                    firmware = item.optNullableString("firmware"),
                    chipId = item.optNullableString("chipId"),
                    port = item.optNullableString("port"),
                    apiVersion = item.optNullableInt("apiVersion"),
                    seRouting = item.optNullableInt("seRouting"),
                    listenTechMask = item.optNullableInt("listenTechMask"),
                    pollingTechMask = item.optNullableInt("pollingTechMask"),
                    extras = item.optJSONObject("extras").toStringMap(),
                )
            }

            val wallets = buildList {
                val values = json.optJSONArray("wallets")
                if (values != null) {
                    for (index in 0 until values.length()) {
                        values.optJSONObject(index)?.toWalletInfo()?.let(::add)
                    }
                } else {
                    json.optJSONObject("wallet")?.toWalletInfo()?.let(::add)
                }
            }

            val cards = buildList {
                val values = json.optJSONArray("cards") ?: JSONArray()
                for (index in 0 until values.length()) {
                    val item = values.optJSONObject(index) ?: continue
                    val kind = runCatching { NfcCardKind.valueOf(item.optString("kind")) }
                        .getOrDefault(NfcCardKind.UNKNOWN)
                    val route = runCatching { NfcCardRoute.valueOf(item.optString("route")) }
                        .getOrDefault(NfcCardRoute.UNKNOWN)
                    add(
                        NfcCard(
                            id = item.optString("id"),
                            title = item.optString("title"),
                            kind = kind,
                            technologyLabel = item.optString("technologyLabel"),
                            active = item.optBoolean("active"),
                            route = route,
                            sourceId = item.optString("sourceId"),
                            sourceLabel = item.optString("sourceLabel"),
                            productName = item.optNullableString("productName"),
                            metadata = item.optJSONObject("metadata").toStringMap(),
                        ),
                    )
                }
            }

            NfcDeviceProfile(
                identity = DeviceIdentity(
                    manufacturer = json.getString("manufacturer"),
                    model = json.getString("model"),
                    device = json.getString("device"),
                    androidRelease = json.getString("androidRelease"),
                    osLabel = json.optNullableString("osLabel"),
                ),
                capabilities = capabilities,
                rootAvailable = json.optBoolean("rootAvailable"),
                selinuxEnforcing = json.optNullableBoolean("selinuxEnforcing"),
                secureElements = secureElements,
                vendor = vendor,
                wallets = wallets,
                cards = cards,
                collectedAtMs = json.optLong("collectedAtMs"),
                error = json.optNullableString("error"),
            )
        }.getOrNull()

        private fun JSONObject.toWalletInfo(): NfcWalletInfo? {
            val packageName = optString("packageName")
            if (packageName.isBlank()) return null
            return NfcWalletInfo(
                providerId = optString("providerId"),
                label = optString("label"),
                packageName = packageName,
                version = optNullableString("version"),
                managementAction = optNullableString("managementAction"),
            )
        }

        private fun JSONObject?.toStringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            return buildMap {
                keys().forEach { key -> put(key, optString(key)) }
            }
        }

        private fun JSONObject.optNullableString(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun JSONObject.optNullableInt(key: String): Int? =
            if (isNull(key) || !has(key)) null else optInt(key)

        private fun JSONObject.optNullableBoolean(key: String): Boolean? =
            if (isNull(key) || !has(key)) null else optBoolean(key)
    }
}
