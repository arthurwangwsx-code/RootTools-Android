package com.arthur.nfclab.platform.provisioning

import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.ProvisioningRouteStatus

interface NfcProvisioningProvider {
    val id: String
    val priority: Int

    fun supports(profile: NfcDeviceProfile): Boolean

    fun collect(profile: NfcDeviceProfile): List<ProvisioningRouteStatus>
}
