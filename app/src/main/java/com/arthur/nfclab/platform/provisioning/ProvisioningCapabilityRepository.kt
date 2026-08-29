package com.arthur.nfclab.platform.provisioning

import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.ProvisioningCapabilityReport
import com.arthur.nfclab.domain.ProvisioningRoute
import com.arthur.nfclab.domain.ProvisioningRouteStatus

class ProvisioningCapabilityRepository(
    private val providers: List<NfcProvisioningProvider> = NfcProvisioningProviderRegistry.defaults(),
) {
    fun collect(profile: NfcDeviceProfile, nowMs: Long = System.currentTimeMillis()): ProvisioningCapabilityReport {
        val routes = linkedMapOf<ProvisioningRoute, ProvisioningRouteStatus>()
        providers
            .asSequence()
            .filter { provider -> runCatching { provider.supports(profile) }.getOrDefault(false) }
            .sortedBy { it.priority }
            .forEach { provider ->
                runCatching { provider.collect(profile) }.getOrDefault(emptyList()).forEach { route ->
                    routes.putIfAbsent(route.route, route)
                }
            }

        val routeList = routes.values.toList()
        val nextSteps = buildList {
            routeList.firstOrNull { it.route == ProvisioningRoute.OEM_WALLET }?.let { route ->
                if (route.providerId != null) add("已有 OEM 钱包 Provisioning 路径；优先使用官方入口管理已授权卡片。")
            }
            routeList.firstOrNull { it.route == ProvisioningRoute.PARTNER_TSM }?.let { route ->
                if (route.unresolvedRequirements.isNotEmpty()) {
                    add("合作方 TSM 链路已识别；下一步补齐调用方签名/合作身份与服务端 Provisioning 授权。")
                }
            }
            routeList.firstOrNull { it.route == ProvisioningRoute.DIRECT_ESE }?.let { route ->
                if (route.unresolvedRequirements.isNotEmpty()) {
                    add("Direct eSE 路径不要把 Root 等价为 Provisioning 权限；先确认安全域/Applet 管理授权。")
                }
            }
            if (isEmpty()) add("当前没有可执行的安全卡 Provisioning 路径。")
        }

        return ProvisioningCapabilityReport(
            routes = routeList,
            nextSteps = nextSteps,
            collectedAtMs = nowMs,
        )
    }

}
