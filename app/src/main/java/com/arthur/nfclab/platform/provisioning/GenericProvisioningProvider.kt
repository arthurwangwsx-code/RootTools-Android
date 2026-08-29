package com.arthur.nfclab.platform.provisioning

import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.ProvisioningReadiness
import com.arthur.nfclab.domain.ProvisioningRequirement
import com.arthur.nfclab.domain.ProvisioningRequirementState
import com.arthur.nfclab.domain.ProvisioningRoute
import com.arthur.nfclab.domain.ProvisioningRouteStatus

class GenericProvisioningProvider : NfcProvisioningProvider {
    override val id: String = "android.generic.provisioning"
    override val priority: Int = 1000

    override fun supports(profile: NfcDeviceProfile): Boolean = true

    override fun collect(profile: NfcDeviceProfile): List<ProvisioningRouteStatus> {
        val eseAvailable = profile.primaryEse?.let { it.connected ?: it.available } == true
        return listOf(
            ProvisioningRouteStatus(
                route = ProvisioningRoute.DIRECT_ESE,
                readiness = if (eseAvailable) ProvisioningReadiness.UNKNOWN else ProvisioningReadiness.BLOCKED,
                title = "Direct eSE / OMAPI",
                detail = if (eseAvailable) {
                    "设备存在 eSE，但通用 Android feature 不能证明当前应用拥有 applet 安装或任意安全域 Provisioning 权限。"
                } else {
                    "当前没有确认到可用 eSE。"
                },
                requirements = listOf(
                    ProvisioningRequirement(
                        id = "ese-present",
                        title = "eSE 可用",
                        state = if (eseAvailable) ProvisioningRequirementState.SATISFIED else ProvisioningRequirementState.MISSING,
                        detail = if (eseAvailable) "系统报告存在可用 eSE。" else "需要带 eSE 的设备或其他受支持安全元件。",
                    ),
                    ProvisioningRequirement(
                        id = "applet-provisioning-authority",
                        title = "Applet Provisioning 授权",
                        state = ProvisioningRequirementState.UNKNOWN,
                        detail = "需要厂商、TSM 或安全域管理方明确授权；仅有 OMAPI 读写能力并不等于可安装 applet。",
                    ),
                ),
            ),
        )
    }
}
