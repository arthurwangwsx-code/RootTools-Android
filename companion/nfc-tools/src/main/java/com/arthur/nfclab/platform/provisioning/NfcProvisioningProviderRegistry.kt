package com.arthur.nfclab.platform.provisioning

import com.arthur.nfclab.platform.samsung.SamsungProvisioningProvider
import com.arthur.nfclab.platform.xiaomi.XiaomiProvisioningProvider

object NfcProvisioningProviderRegistry {
    fun defaults(): List<NfcProvisioningProvider> = listOf(
        XiaomiProvisioningProvider(),
        SamsungProvisioningProvider(),
        GenericProvisioningProvider(),
    )
}
