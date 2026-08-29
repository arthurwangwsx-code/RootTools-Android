package com.arthur.nfclab.platform

import com.arthur.nfclab.platform.samsung.SamsungNfcProfileProvider
import com.arthur.nfclab.platform.xiaomi.XiaomiNfcProfileProvider

object NfcProfileProviderRegistry {
    fun defaults(): List<NfcProfileProvider> = listOf(
        XiaomiNfcProfileProvider(),
        SamsungNfcProfileProvider(),
    )
}
