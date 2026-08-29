package com.arthur.nfclab.platform.xiaomi

import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.ProvisioningReadiness
import com.arthur.nfclab.domain.ProvisioningRequirement
import com.arthur.nfclab.domain.ProvisioningRequirementState
import com.arthur.nfclab.domain.ProvisioningRoute
import com.arthur.nfclab.domain.ProvisioningRouteStatus
import com.arthur.nfclab.platform.provisioning.NfcProvisioningProvider

class XiaomiProvisioningProvider : NfcProvisioningProvider {
    override val id: String = "xiaomi.provisioning"
    override val priority: Int = 100

    override fun supports(profile: NfcDeviceProfile): Boolean {
        val manufacturer = profile.identity.manufacturer.lowercase()
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
    }

    override fun collect(profile: NfcDeviceProfile): List<ProvisioningRouteStatus> {
        val extras = profile.vendor?.extras.orEmpty()
        val eseAvailable = profile.primaryEse?.let { it.connected ?: it.available } == true
        val wallet = profile.wallets.firstOrNull { it.providerId == XiaomiNfcProfileProvider.PROVIDER_ID }
        val walletCards = profile.cards.filter { it.sourceId == XiaomiNfcProfileProvider.PROVIDER_ID }
        val openSe = extras["openSeService"] == "true"
        val miSe = extras["miSeOpenService"] == "true"
        val privilegedEse = extras["eseAccessPermissionPrivileged"] == "true"
        val walletEse = extras["walletEseAccessGranted"] == "true"

        return buildList {
            if (wallet != null) {
                add(
                    ProvisioningRouteStatus(
                        route = ProvisioningRoute.OEM_WALLET,
                        readiness = ProvisioningReadiness.MANAGED_EXTERNALLY,
                        title = "Xiaomi Wallet / OEM Provisioning",
                        detail = if (walletCards.isNotEmpty()) {
                            "官方钱包已完成至少一条 eSE/off-host 卡 Provisioning；NFC Tools 只管理入口和状态，不复制钱包凭证。"
                        } else {
                            "官方钱包入口可用；新增安全卡仍由 Xiaomi Wallet/TSM 完成。"
                        },
                        providerId = wallet.providerId,
                        requirements = listOf(
                            requirement("wallet", "官方钱包", ProvisioningRequirementState.SATISFIED, "${wallet.label} 已安装。"),
                            requirement(
                                "ese",
                                "eSE",
                                if (eseAvailable) ProvisioningRequirementState.SATISFIED else ProvisioningRequirementState.MISSING,
                                if (eseAvailable) "eSE1 可用。" else "未确认 eSE 可用。",
                            ),
                            ProvisioningRequirement(
                                id = "wallet-management",
                                title = "官方 Provisioning 入口",
                                state = if (wallet.managementAction != null) ProvisioningRequirementState.ACTION_AVAILABLE else ProvisioningRequirementState.UNKNOWN,
                                detail = if (wallet.managementAction != null) "可跳转到官方门卡管理入口。" else "当前只确认钱包应用存在。",
                                actionProviderId = wallet.providerId,
                            ),
                        ),
                        evidence = buildList {
                            if (walletCards.isNotEmpty()) add("已发现 ${walletCards.size} 张官方钱包卡")
                            if (walletEse) add("官方钱包持有 OEM eSE 访问授权")
                        },
                    ),
                )
            }

            if (openSe || miSe) {
                add(
                    ProvisioningRouteStatus(
                        route = ProvisioningRoute.PARTNER_TSM,
                        readiness = ProvisioningReadiness.PARTNER_REQUIRED,
                        title = "Xiaomi Partner TSM",
                        detail = "设备侧已发现 OEM OpenSE/MiSE 服务，但执行模型依赖调用方身份校验与服务端 APDU task；不是 Root 后即可直接调用的本地 Provisioning API。",
                        requirements = listOf(
                            requirement("ese", "eSE", if (eseAvailable) ProvisioningRequirementState.SATISFIED else ProvisioningRequirementState.MISSING, if (eseAvailable) "eSE1 可用。" else "未确认 eSE。"),
                            requirement("partner-service", "OEM TSM 服务", ProvisioningRequirementState.SATISFIED, buildList {
                                if (openSe) add("OpenSeService")
                                if (miSe) add("MiSeOpenService")
                            }.joinToString(" + ") + " 已发现。"),
                            requirement("caller-identity", "合作方调用身份", ProvisioningRequirementState.PARTNER_REQUIRED, "需要受 OEM/TSM 接受的包签名、spId 或合作方身份。"),
                            requirement("backend-authorization", "TSM 服务端授权", ProvisioningRequirementState.PARTNER_REQUIRED, "需要服务端下发合法 Provisioning/APDU task。"),
                            requirement("test-applet", "自有测试 Applet / 安全域", ProvisioningRequirementState.MISSING, "当前工程未配置可被 OEM TSM 合法下发的自有测试 applet。"),
                        ),
                        evidence = buildList {
                            if (openSe) add("OpenSeService: caller-signature + server-apdu-task")
                            if (miSe) add("MiSeOpenService: spId/package/signature + TSM RPC")
                        },
                    ),
                )
            }

            add(
                ProvisioningRouteStatus(
                    route = ProvisioningRoute.DIRECT_ESE,
                    readiness = when {
                        !eseAvailable -> ProvisioningReadiness.BLOCKED
                        privilegedEse -> ProvisioningReadiness.PRIVILEGED_ONLY
                        else -> ProvisioningReadiness.UNKNOWN
                    },
                    title = "Direct eSE / OMAPI",
                    detail = when {
                        !eseAvailable -> "当前没有确认到可用 eSE。"
                        privilegedEse -> "eSE 存在，但 OEM ACCESS_ESE 为 signature|privileged；普通第三方应用不能把 Root 直接等价为安全域/Applet Provisioning 权限。"
                        else -> "eSE 存在，但还没有验证当前应用拥有安全域/Applet Provisioning 权限。"
                    },
                    requirements = listOf(
                        requirement("ese", "eSE", if (eseAvailable) ProvisioningRequirementState.SATISFIED else ProvisioningRequirementState.MISSING, if (eseAvailable) "eSE1 可用。" else "未确认 eSE。"),
                        requirement(
                            "ese-permission",
                            "OEM eSE 权限",
                            if (privilegedEse) ProvisioningRequirementState.PRIVILEGED_ONLY else ProvisioningRequirementState.UNKNOWN,
                            if (privilegedEse) "ACCESS_ESE 受 signature|privileged 保护。" else "权限模型尚未确认。",
                        ),
                        requirement("security-domain", "安全域/Applet 管理权", ProvisioningRequirementState.MISSING, "需要合法安全域、Applet 安装与生命周期管理授权。"),
                    ),
                    evidence = buildList {
                        if (privilegedEse) add("ACCESS_ESE=signature|privileged")
                        if (walletEse) add("OEM Wallet 是已授权调用方")
                    },
                ),
            )
        }
    }

    private fun requirement(
        id: String,
        title: String,
        state: ProvisioningRequirementState,
        detail: String,
    ) = ProvisioningRequirement(id, title, state, detail)
}
