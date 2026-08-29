package com.arthur.nfclab.platform.simulation

import com.arthur.nfclab.domain.NfcCapability
import com.arthur.nfclab.domain.NfcDeviceProfile
import com.arthur.nfclab.domain.ProvisioningCapabilityReport
import com.arthur.nfclab.domain.ProvisioningReadiness
import com.arthur.nfclab.domain.ProvisioningRoute
import com.arthur.nfclab.domain.SimulationCapabilityReport
import com.arthur.nfclab.domain.SimulationLayer
import com.arthur.nfclab.domain.SimulationLayerStatus
import com.arthur.nfclab.domain.SimulationRoute
import com.arthur.nfclab.domain.SimulationRouteStatus
import com.arthur.nfclab.domain.SimulationSupport
import com.arthur.nfclab.domain.NfcCardRoute
import com.arthur.nfclab.nfc.TagSnapshot

object SimulationCapabilityAnalyzer {
    fun analyze(
        snapshot: TagSnapshot?,
        profile: NfcDeviceProfile?,
        supportsHostHce: Boolean,
        provisioning: ProvisioningCapabilityReport? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): SimulationCapabilityReport {
        val product = snapshot?.details?.get("NXP product")
        val technologies = snapshot?.technologies.orEmpty().map { it.substringAfterLast('.') }
        val isoDep = "IsoDep" in technologies
        val desfire = product?.contains("DESFire", ignoreCase = true) == true
        val root = profile?.rootAvailable == true
        val vendorApi = profile?.has(NfcCapability.VENDOR_NFC_API) == true
        val ese = profile?.primaryEse?.let { it.connected ?: it.available } == true
        val oemOffHost = profile?.has(NfcCapability.MIFARE_OFF_HOST) == true
        val eseAccessPrivileged = profile?.vendor?.extras?.get("eseAccessPermissionPrivileged") == "true"
        val walletEseAccess = profile?.vendor?.extras?.get("walletEseAccessGranted") == "true"
        val extendedFieldDetect = profile?.vendor?.extras?.get("extendedFieldDetect") == "true"
        val t4tNfcee = profile?.vendor?.extras?.get("t4tNfcee") == "true"
        val nativeCeControl = profile?.vendor?.extras?.get("nativeCardEmulationControl")
        val desfireHostRouteModel = profile?.vendor?.extras?.get("desfireHostRouteModel")
        val desfireHostRouteVerified = profile?.vendor?.extras?.get("desfireHostRouteVerified") == "true"
        val dtaConfigPath = profile?.vendor?.extras?.get("nfccDtaConfigPath")
        val dtaConfigPathVerified = profile?.vendor?.extras?.get("nfccDtaConfigPathVerified") == "true"
        val arbitraryRfIdentityVerified = profile?.vendor?.extras?.get("rfIdentityArbitraryOverrideVerified") == "true"
        val offHostCards = profile?.cards.orEmpty().filter { card -> card.route == NfcCardRoute.ESE }
        val primaryWallet = profile?.primaryWallet
        val partnerProvisioning = provisioning?.route(ProvisioningRoute.PARTNER_TSM)
        val directEseProvisioning = provisioning?.route(ProvisioningRoute.DIRECT_ESE)

        val rfSupport = when {
            root && vendorApi -> SimulationSupport.PARTIAL
            else -> SimulationSupport.UNSUPPORTED
        }
        val rfEvidence = buildList {
            snapshot?.details?.get("NFC-A ATQA")?.let { add("实体卡 ATQA=$it") }
            snapshot?.details?.get("NFC-A SAK")?.let { add("实体卡 SAK=$it") }
            if (root) add("Root 可用")
            if (vendorApi) add("厂商 NFC API 可用")
            if (extendedFieldDetect) add("NXP Extended Field Detect 已启用")
            if (nativeCeControl == "listen-mode-only") add("native startCardEmulation 仅恢复 Listen/CE 接收态")
            if (dtaConfigPathVerified && dtaConfigPath != null) add("NFCC DTA=$dtaConfigPath（认证/产测路径）")
            if (dtaConfigPathVerified && !arbitraryRfIdentityVerified) add("未验证 DTA 可稳定覆盖任意 NFCID1/ATQA/SAK")
        }

        val layers = listOf(
            SimulationLayerStatus(
                layer = SimulationLayer.RF_IDENTITY,
                support = rfSupport,
                title = "RF / ISO14443-A 身份",
                detail = if (rfSupport == SimulationSupport.PARTIAL) {
                    if (dtaConfigPathVerified) {
                        "已具备 Root、厂商 NFC 控制面和 NXP DTA 认证配置路径；DTA 可向 NFCC 应用测试 TLV，但尚未验证存在生产可用、可稳定设置任意 NFCID1 / ATQA / SAK 的接口。标准 HostApduService 不控制这一层。"
                    } else {
                        "已具备 Root 与厂商 NFC 控制面，但尚未验证存在可安全设置任意 UID / ATQA / SAK 的接口；标准 HostApduService 不控制这一层。"
                    }
                } else {
                    "标准 Android HCE 不允许应用自由控制 UID / ATQA / SAK。"
                },
                evidence = rfEvidence,
            ),
            SimulationLayerStatus(
                layer = SimulationLayer.ISO_DEP_TRANSPORT,
                support = when {
                    isoDep && supportsHostHce && desfire -> SimulationSupport.PARTIAL
                    isoDep && supportsHostHce -> SimulationSupport.SUPPORTED
                    else -> SimulationSupport.UNKNOWN
                },
                title = "ISO-DEP / APDU Transport",
                detail = if (isoDep && supportsHostHce && desfire) {
                    "实体卡和手机都支持 ISO-DEP，但 Android HostEmulationManager 在一个新的 Host HCE session 中必须先解析到 ISO SELECT AID（00 A4 04 00 ...）才会绑定 HostApduService。原生 DESFire Reader 如果直接从 native/wrapped DESFire command 开始，首帧不会进入应用 HCE 服务。"
                } else if (isoDep && supportsHostHce) {
                    "实体卡和手机都支持 ISO-DEP；可以用 HostApduService 做受控 APDU 状态机与 Reader 兼容性实验。"
                } else {
                    "当前证据不足以确认 Host HCE 能覆盖目标卡的 transport。"
                },
                evidence = buildList {
                    if (isoDep) add("实体卡暴露 IsoDep")
                    if (supportsHostHce) add("设备支持 Host Card Emulation")
                    if (desfire && supportsHostHce) add("Android Host HCE 首帧需要 ISO SELECT AID；非 SELECT 首帧会被 HostEmulationManager 丢弃")
                    if (desfireHostRouteVerified && desfireHostRouteModel != null) {
                        add("DESFire Host route=$desfireHostRouteModel")
                    }
                },
            ),
            SimulationLayerStatus(
                layer = SimulationLayer.APPLICATION_PROTOCOL,
                support = when {
                    desfire && supportsHostHce -> SimulationSupport.PARTIAL
                    supportsHostHce -> SimulationSupport.SUPPORTED
                    else -> SimulationSupport.UNKNOWN
                },
                title = "应用协议状态机",
                detail = if (desfire) {
                    if (desfireHostRouteVerified) {
                        "已确认厂商 DESFire Host route 只是把 protocol traffic 送入 Android HostEmulationManager / HostApduService；它不会额外提供 DESFire Application/File/密钥状态机。可以实现 synthetic DESFire-like / ISO-DEP 测试状态机，但公开 GetVersion 信息不足以还原真实卡的受保护状态。"
                    } else {
                        "可以实现 synthetic DESFire-like / ISO-DEP 测试状态机，但公开 GetVersion 信息不足以还原真实卡的 Application、File、权限与认证状态。"
                    }
                } else {
                    "可为自有 AID / 自有协议实现完整 Host HCE 状态机。"
                },
                evidence = buildList {
                    product?.let { add("目标产品=$it") }
                    if (desfireHostRouteVerified) add("未发现 NXP 专用 Host DESFire emulator；Host 数据进入 HostApduService")
                },
            ),
            SimulationLayerStatus(
                layer = SimulationLayer.SECURE_CREDENTIAL,
                support = if (desfire) SimulationSupport.REQUIRES_PROVISIONING else SimulationSupport.UNKNOWN,
                title = "受保护凭证 / Secure Messaging",
                detail = if (desfire) {
                    "真实 DESFire 等价模拟需要合法的应用定义、测试密钥和安全状态；这些材料不能由公开扫描结果推导。"
                } else {
                    "是否需要受保护凭证取决于目标协议。"
                },
                evidence = if (desfire) listOf("GetVersion 只提供产品/版本/容量等公开信息") else emptyList(),
            ),
            SimulationLayerStatus(
                layer = SimulationLayer.OFF_HOST_SE,
                support = when {
                    ese && vendorApi -> SimulationSupport.REQUIRES_PROVISIONING
                    ese -> SimulationSupport.PARTIAL
                    else -> SimulationSupport.UNSUPPORTED
                },
                title = "eSE / Off-host",
                detail = when {
                    ese && vendorApi && oemOffHost && eseAccessPrivileged -> "设备已证明存在 OEM off-host 路由与 eSE；同时 OEM eSE 访问受 signature|privileged 权限保护。目前没有验证到第三方可直接 provision 任意 DESFire 测试 applet/credential 的接口。"
                    ese && vendorApi -> "eSE 与厂商 NFC API 可用；下一步应继续只读逆向官方/工程测试 provisioning 边界。"
                    ese -> "eSE 可用，但缺少已验证的厂商 provisioning 控制面。"
                    else -> "当前设备没有确认到可用 eSE。"
                },
                evidence = buildList {
                    if (ese) add("eSE 可用")
                    if (vendorApi) add("Vendor NFC API 可用")
                    if (oemOffHost) add("已有 OEM off-host 卡链路")
                    if (t4tNfcee) add("NXP T4T NFCEE 已启用（Type-4 NDEF 能力）")
                    if (eseAccessPrivileged) add("ACCESS_ESE=signature|privileged")
                    if (walletEseAccess) add("官方钱包持有 ACCESS_ESE")
                    partnerProvisioning?.evidence.orEmpty().forEach(::add)
                    directEseProvisioning?.evidence.orEmpty().forEach(::add)
                },
            ),
        )

        val routes = listOf(
            SimulationRouteStatus(
                route = SimulationRoute.HOST_HCE,
                support = when {
                    !supportsHostHce -> SimulationSupport.UNSUPPORTED
                    desfire -> SimulationSupport.PARTIAL
                    else -> SimulationSupport.SUPPORTED
                },
                title = "Android Host HCE",
                detail = when {
                    !supportsHostHce -> "当前设备没有 Host Card Emulation 能力。"
                    desfire -> "可以验证 ISO-DEP / APDU 与自有状态机，但不能自动获得实体 DESFire 卡的 RF 身份或安全凭证。"
                    else -> "可用于自有 AID、自有 ISO-DEP/APDU 协议的 HostEmulation 测试。"
                },
            ),
            SimulationRouteStatus(
                route = SimulationRoute.OEM_OFF_HOST,
                support = when {
                    offHostCards.isNotEmpty() && oemOffHost -> SimulationSupport.SUPPORTED
                    ese && vendorApi -> SimulationSupport.REQUIRES_PROVISIONING
                    ese -> SimulationSupport.PARTIAL
                    else -> SimulationSupport.UNSUPPORTED
                },
                title = "OEM / eSE Off-host",
                detail = when {
                    offHostCards.isNotEmpty() && oemOffHost -> "设备已经存在由官方钱包 provision 的 off-host 卡，可通过系统钱包管理和切换；这条链路不经过本应用 HostApduService。"
                    ese && vendorApi -> "设备具备 eSE 与厂商 NFC 控制面，但当前没有可由本应用直接创建的 off-host 测试卡；需要官方/合作方 Provisioning。"
                    ese -> "eSE 可用，但尚未验证厂商 off-host 管理入口。"
                    else -> "当前没有确认到可用 eSE/off-host 路径。"
                },
                sourceLabel = primaryWallet?.label,
                cardTitles = offHostCards.map { card ->
                    "${card.title}${if (card.active) "（当前使用）" else ""}"
                },
                managementProviderId = primaryWallet?.providerId?.takeIf { offHostCards.isNotEmpty() },
            ),
            SimulationRouteStatus(
                route = SimulationRoute.CUSTOM_ESE_APPLET,
                support = when {
                    partnerProvisioning?.readiness == ProvisioningReadiness.READY -> SimulationSupport.SUPPORTED
                    partnerProvisioning != null -> SimulationSupport.REQUIRES_PROVISIONING
                    directEseProvisioning?.readiness == ProvisioningReadiness.READY -> SimulationSupport.SUPPORTED
                    directEseProvisioning != null && ese -> SimulationSupport.REQUIRES_PROVISIONING
                    ese -> SimulationSupport.REQUIRES_PROVISIONING
                    else -> SimulationSupport.UNSUPPORTED
                },
                title = "自定义安全卡 / eSE Applet",
                detail = when {
                    partnerProvisioning != null -> partnerProvisioning.detail
                    directEseProvisioning != null -> directEseProvisioning.detail
                    ese -> "eSE 可用，但没有验证到第三方可直接部署自定义安全卡 applet 的接口。"
                    else -> "当前没有确认到可用 eSE。"
                },
            ),
        )

        return SimulationCapabilityReport(
            targetProduct = product,
            targetTechnology = technologies.joinToString(" / ").ifBlank { "未扫描实体卡" },
            routes = routes,
            layers = layers,
            recommendedPath = buildList {
                add("先用 Host HCE synthetic profile 验证目标 Reader 是否会进入 ISO-DEP / APDU。")
                if (desfire && supportsHostHce) add("先用自有 Reader 验证“ISO SELECT AID → HostApduService”路径；若目标门禁直接发送 native DESFire 首帧，普通 Android Host HCE 无法接管该会话。")
                if (desfireHostRouteVerified) add("DESFire route-to-Host 已确认等价于 Android Host HCE 数据路径，不再把 route API 作为独立模拟引擎继续投入。")
                if (desfire) add("对自有 Reader 使用合法测试 AID / 测试密钥实现 DESFire-like 状态机，验证协议兼容性。")
                provisioning?.nextSteps.orEmpty().forEach(::add)
                add("把 RF、APDU、eSE 三层证据分别记录，避免把“能被手机读到”误判为“可完整模拟”。")
            },
            blockers = buildList {
                if (rfSupport != SimulationSupport.SUPPORTED) add("RF 身份层：尚无已验证的任意 UID / ATQA / SAK 控制接口")
                if (dtaConfigPathVerified && !arbitraryRfIdentityVerified) add("DTA 仅确认到认证/产测 NFCC TLV 配置路径，不能视为生产 RF 身份模拟接口")
                if (desfire && supportsHostHce) add("Host HCE 分派层：首次 Host APDU 必须是 ISO SELECT AID，原生 DESFire 首帧可能在 HostApduService 之前被丢弃")
                if (desfire) add("DESFire 安全层：缺少合法 provisioning 的应用定义 / 测试密钥 / 安全状态")
                if (ese) add("eSE 层：尚无已验证的第三方任意 DESFire provisioning 接口")
                if (eseAccessPrivileged) add("权限层：OEM eSE API 受 signature|privileged 保护")
                listOfNotNull(partnerProvisioning, directEseProvisioning)
                    .flatMap { it.unresolvedRequirements }
                    .distinctBy { it.id }
                    .forEach { requirement -> add("Provisioning：${requirement.title}（${requirement.state.name}）") }
            },
            collectedAtMs = nowMs,
        )
    }
}
