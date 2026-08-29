package com.arthur.nfclab.platform.samsung

import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.ProvisioningReadiness
import com.arthur.nfclab.domain.ProvisioningRequirement
import com.arthur.nfclab.domain.ProvisioningRequirementState
import com.arthur.nfclab.domain.ProvisioningRoute
import com.arthur.nfclab.domain.ProvisioningRouteStatus
import com.arthur.nfclab.platform.provisioning.NfcProvisioningProvider

/** Conservative Samsung provisioning mapper; it only exposes the official wallet path. */
class SamsungProvisioningProvider : NfcProvisioningProvider {
    override val id: String = "samsung.provisioning"
    override val priority: Int = 110

    override fun supports(profile: NfcDeviceProfile): Boolean =
        profile.identity.manufacturer.equals("samsung", ignoreCase = true)

    override fun collect(profile: NfcDeviceProfile): List<ProvisioningRouteStatus> {
        val wallet = profile.wallets.firstOrNull { it.providerId == SamsungNfcProfileProvider.PROVIDER_ID }
            ?: return emptyList()
        return listOf(
            ProvisioningRouteStatus(
                route = ProvisioningRoute.OEM_WALLET,
                readiness = ProvisioningReadiness.MANAGED_EXTERNALLY,
                title = "Samsung Wallet / OEM Provisioning",
                detail = "Samsung Wallet 已识别为官方卡片管理路径；具体安全卡类型和 off-host 能力仍按机型实测，不从钱包安装状态自动推断。",
                providerId = wallet.providerId,
                requirements = listOf(
                    ProvisioningRequirement(
                        id = "wallet",
                        title = "官方钱包",
                        state = ProvisioningRequirementState.SATISFIED,
                        detail = "${wallet.label} 已安装。",
                        actionProviderId = wallet.providerId,
                    ),
                    ProvisioningRequirement(
                        id = "card-product-support",
                        title = "目标卡产品支持",
                        state = ProvisioningRequirementState.UNKNOWN,
                        detail = "需要按具体 Samsung 机型、地区和 Wallet 卡产品继续验证。",
                    ),
                ),
            ),
        )
    }
}
