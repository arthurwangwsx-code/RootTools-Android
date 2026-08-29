package com.arthur.nfclab.platform.samsung

import android.content.Context
import android.os.Build
import com.arthur.nfclab.domain.NfcCapability
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.NfcWalletInfo
import com.arthur.nfclab.domain.VendorNfcDetails
import com.arthur.nfclab.platform.NfcProfileProvider

/**
 * Samsung provider intentionally starts as a conservative, read-only capability mapper.
 * It does not infer MIFARE Classic card emulation from generic NXP support.
 */
class SamsungNfcProfileProvider : NfcProfileProvider {
    override val id: String = PROVIDER_ID
    override val priority: Int = 110

    override fun supports(context: Context): Boolean = isSamsungManufacturer(Build.MANUFACTURER)

    override fun enrich(context: Context, base: NfcDeviceProfile): NfcDeviceProfile {
        val pm = context.packageManager
        val hasT4t = pm.hasSystemFeature(FEATURE_T4T_EMULATION)
        val walletPackage = SAMSUNG_WALLET_PACKAGES.firstOrNull { packageName ->
            runCatching { pm.getPackageInfo(packageName, 0) }.isSuccess
        }
        val walletVersion = walletPackage?.let { packageName ->
            runCatching { pm.getPackageInfo(packageName, 0).versionName }.getOrNull()
        }
        val capabilities = base.capabilities.toMutableSet().apply {
            if (hasT4t) {
                add(NfcCapability.TYPE4_NDEF_EMULATION)
                add(NfcCapability.VENDOR_NFC_API)
            }
        }

        return base.copy(
            capabilities = capabilities,
            vendor = VendorNfcDetails(
                providerId = PROVIDER_ID,
                displayName = "Samsung NFC",
                extras = mapOf(
                    "type4NdefEmulation" to hasT4t.toString(),
                    "implementationStage" to "read-only-capability-probe",
                ),
            ),
            wallets = walletPackage?.let { packageName ->
                base.wallets.filterNot { it.providerId == PROVIDER_ID } + NfcWalletInfo(
                    providerId = PROVIDER_ID,
                    label = "Samsung Wallet",
                    packageName = packageName,
                    version = walletVersion,
                )
            } ?: base.wallets,
        )
    }

    companion object {
        const val PROVIDER_ID = "samsung.nfc"
        const val FEATURE_T4T_EMULATION = "com.samsung.android.nfc.t4temul"
        internal val SAMSUNG_WALLET_PACKAGES = listOf(
            "com.samsung.android.spay",
            "com.samsung.android.spayfw",
        )

        internal fun isSamsungManufacturer(value: String): Boolean =
            value.equals("samsung", ignoreCase = true)
    }
}
