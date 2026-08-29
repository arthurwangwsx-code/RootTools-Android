package com.arthur.nfclab.platform.xiaomi

import android.content.Context
import android.os.Build
import com.arthur.nfclab.domain.DeviceIdentity
import com.arthur.nfclab.domain.NfcCapability
import com.arthur.nfclab.domain.NfcCard
import com.arthur.nfclab.domain.NfcCardKind
import com.arthur.nfclab.domain.NfcCardRoute
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.NfcWalletInfo
import com.arthur.nfclab.domain.SecureElementInfo
import com.arthur.nfclab.domain.SecureElementType
import com.arthur.nfclab.domain.VendorNfcDetails
import com.arthur.nfclab.platform.NfcProfileProvider

class XiaomiNfcProfileProvider : NfcProfileProvider {
    override val id: String = PROVIDER_ID
    override val priority: Int = 100

    override fun supports(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco")
    }

    override fun enrich(context: Context, base: NfcDeviceProfile): NfcDeviceProfile {
        return mapProfile(base, XiaomiWalletInspector.collect(context))
    }

    internal fun mapProfile(base: NfcDeviceProfile, xiaomi: XiaomiNfcProfile): NfcDeviceProfile {
        val capabilities = base.capabilities.toMutableSet().apply {
            if (xiaomi.rootAvailable) add(NfcCapability.ROOT)
            if (xiaomi.eSeConnected) add(NfcCapability.ESE)
            if (xiaomi.mifareReaderEnabled) add(NfcCapability.MIFARE_READER)
            if (xiaomi.miNfcServiceAvailable || xiaomi.nxpVendorServiceAvailable) add(NfcCapability.VENDOR_NFC_API)
            if (xiaomi.officialM1OffHostReady) add(NfcCapability.MIFARE_OFF_HOST)
        }

        val secureElements = base.secureElements
            .filterNot { it.type == SecureElementType.ESE }
            .toMutableList()
            .apply {
                add(
                    SecureElementInfo(
                        type = SecureElementType.ESE,
                        name = "eSE1",
                        available = xiaomi.eSeConnected || NfcCapability.ESE in capabilities,
                        connected = xiaomi.eSeConnected,
                    ),
                )
            }

        return base.copy(
            identity = DeviceIdentity(
                manufacturer = xiaomi.manufacturer,
                model = xiaomi.model,
                device = xiaomi.device,
                androidRelease = xiaomi.androidRelease,
                osLabel = xiaomi.hyperOsVersion?.let { "HyperOS $it" },
            ),
            capabilities = capabilities,
            rootAvailable = xiaomi.rootAvailable || base.rootAvailable,
            selinuxEnforcing = xiaomi.selinuxEnforcing,
            secureElements = secureElements,
            vendor = VendorNfcDetails(
                providerId = PROVIDER_ID,
                displayName = "Xiaomi / NXP NFC",
                firmware = xiaomi.nfcFirmware,
                chipId = xiaomi.nfcChipId,
                port = xiaomi.nfcPort,
                apiVersion = xiaomi.miNfcApiVersion,
                seRouting = xiaomi.seRouting,
                listenTechMask = xiaomi.listenTechMask,
                pollingTechMask = xiaomi.pollingTechMask,
                extras = buildMap {
                    xiaomi.defaultMifareRoute?.let { put("mifareRoute", it) }
                    xiaomi.hostListenTechMask?.let { put("hostListenTechMask", it) }
                    put("miNfcService", xiaomi.miNfcServiceAvailable.toString())
                    put("nxpVendorService", xiaomi.nxpVendorServiceAvailable.toString())
                    put("mifareReader", xiaomi.mifareReaderEnabled.toString())
                    put("extendedFieldDetect", xiaomi.extendedFieldDetectEnabled.toString())
                    put("t4tNfcee", xiaomi.t4tNfceeEnabled.toString())
                    put("nativeCardEmulationControl", "listen-mode-only")
                    put("eseAccessPermissionPrivileged", xiaomi.eseAccessPermissionPrivileged.toString())
                    put("walletEseAccessGranted", xiaomi.walletEseAccessGranted.toString())
                    put("openSeService", xiaomi.openSeServiceAvailable.toString())
                    put("openSeServiceExported", xiaomi.openSeServiceAvailable.toString())
                    put("openSeAuthorizationModel", "caller-signature+tsm-server")
                    put("openSeOperationModel", "server-apdu-task")
                    put("miSeOpenService", xiaomi.miSeOpenServiceAvailable.toString())
                    if (xiaomi.miSeOpenServiceAvailable) {
                        put("miSeAuthorizationModel", "spid-caller-package+signature+tsm-server")
                        put("miSeOperationModel", "tsm-rpc-server-apdu-task")
                        put("miSeCapabilities", "executeSeOperation,getOperationResult,login,getSeid")
                    }
                    put("publicTsmFeaturePermissionPrivileged", xiaomi.publicTsmFeaturePermissionPrivileged.toString())
                    if (xiaomi.device.equals("houji", ignoreCase = true) && xiaomi.nxpVendorServiceAvailable) {
                        // Verified on Xiaomi 14 / SN100-compatible NXP framework by tracing
                        // MifareDesfireRouteSet -> setRoutingEntry(PROTOCOL, DESFIRE, ...)
                        // -> NFA_EeSetDefaultProtoRouting -> HostEmulationManager -> HostApduService.
                        put("desfireHostRouteModel", "protocol-routing-to-host-apdu-service")
                        put("desfireHostRouteVerified", "true")
                        // NXP DTA is a certification/engineering path. On this ROM its Binder
                        // entry requires WRITE_SECURE_SETTINGS and native code consumes
                        // nfc.dta.configTLV before applying NFCC configuration through NFA_SetConfig.
                        // This is deliberately not exposed as a production RF identity editor.
                        put("nfccDtaConfigPath", "write-secure-settings+nfc.dta.configTLV")
                        put("nfccDtaConfigPathVerified", "true")
                        put("rfIdentityArbitraryOverrideVerified", "false")
                    }
                },
            ),
            wallets = base.wallets
                .filterNot { it.providerId == PROVIDER_ID } + NfcWalletInfo(
                    providerId = PROVIDER_ID,
                    label = "小米钱包",
                    packageName = XIAOMI_WALLET_PACKAGE,
                    version = xiaomi.walletVersion,
                    managementAction = XIAOMI_DOOR_CARD_SELECT_ACTION,
                ),
            cards = xiaomi.cards.map(::mapCard),
            error = listOfNotNull(base.error, xiaomi.error).joinToString("; ").ifBlank { null },
        )
    }

    private fun mapCard(card: XiaomiVirtualCard): NfcCard = NfcCard(
        id = card.aid,
        title = card.title,
        kind = when (card.mifareCardType) {
            7 -> NfcCardKind.CPU
            else -> NfcCardKind.MIFARE_CLASSIC
        },
        technologyLabel = card.typeLabel,
        active = card.active,
        route = NfcCardRoute.ESE,
        sourceId = PROVIDER_ID,
        sourceLabel = "小米钱包",
        productName = card.productName,
        metadata = mapOf(
            "mifareCardType" to card.mifareCardType.toString(),
            "sectorOverwritten" to card.sectorOverwritten.toString(),
        ),
    )

    companion object {
        const val PROVIDER_ID = "xiaomi.wallet"
        const val XIAOMI_WALLET_PACKAGE = "com.miui.tsmclient"
        const val XIAOMI_DOOR_CARD_SELECT_ACTION = "com.miui.tsmclient.action.DOOR_CARD_SELECT"
    }
}
