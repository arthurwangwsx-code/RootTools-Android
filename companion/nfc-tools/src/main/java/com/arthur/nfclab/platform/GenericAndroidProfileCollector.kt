package com.arthur.nfclab.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.arthur.nfclab.domain.DeviceIdentity
import com.arthur.nfclab.domain.NfcCapability
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.SecureElementInfo
import com.arthur.nfclab.domain.SecureElementType
import java.util.concurrent.TimeUnit

object GenericAndroidProfileCollector {
    fun collect(context: Context): NfcDeviceProfile {
        val pm = context.packageManager
        val capabilities = buildSet {
            if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) add(NfcCapability.NFC_READER)
            if (pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) add(NfcCapability.HCE_ISO_DEP)
            if (pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF)) add(NfcCapability.HCE_NFC_F)
            if (pm.hasSystemFeature(FEATURE_NFC_ESE)) add(NfcCapability.ESE)
            if (pm.hasSystemFeature(FEATURE_NFC_UICC)) add(NfcCapability.UICC)
            if (pm.hasSystemFeature(FEATURE_NXP_MIFARE)) add(NfcCapability.MIFARE_READER)
        }.toMutableSet()

        val rootAvailable = probeRoot()
        if (rootAvailable) capabilities += NfcCapability.ROOT

        return NfcDeviceProfile(
            identity = DeviceIdentity(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                device = Build.DEVICE,
                androidRelease = Build.VERSION.RELEASE,
            ),
            capabilities = capabilities,
            rootAvailable = rootAvailable,
            selinuxEnforcing = probeSelinux(),
            secureElements = buildList {
                if (NfcCapability.ESE in capabilities) {
                    add(SecureElementInfo(SecureElementType.ESE, "eSE", available = true))
                }
                if (NfcCapability.UICC in capabilities) {
                    add(SecureElementInfo(SecureElementType.UICC, "UICC", available = true))
                }
            },
            collectedAtMs = System.currentTimeMillis(),
        )
    }

    private fun probeRoot(): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(1500, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        finished && process.exitValue() == 0 && text.contains("uid=0")
    }.getOrDefault(false)

    private fun probeSelinux(): Boolean? = runCatching {
        val process = ProcessBuilder("getenforce").redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText().trim() }
        val finished = process.waitFor(800, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        if (!finished) null else text.equals("Enforcing", ignoreCase = true)
    }.getOrNull()

    private const val FEATURE_NFC_ESE = "android.hardware.nfc.ese"
    private const val FEATURE_NFC_UICC = "android.hardware.nfc.uicc"
    private const val FEATURE_NXP_MIFARE = "com.nxp.mifare"
}
