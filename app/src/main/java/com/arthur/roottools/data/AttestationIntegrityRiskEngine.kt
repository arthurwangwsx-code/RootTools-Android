package com.arthur.roottools.data

import com.arthur.roottools.model.AttestationRootAuthority
import com.arthur.roottools.model.DeviceIntegritySnapshot
import com.arthur.roottools.model.IntegrityFinding
import com.arthur.roottools.model.IntegrityFindingLevel
import com.arthur.roottools.model.IntegritySystemSignals
import com.arthur.roottools.model.KeyAttestationResult
import com.arthur.roottools.model.VerifiedBootState

internal object AttestationIntegrityRiskEngine {
    fun evaluate(
        standard: KeyAttestationResult,
        strongBox: KeyAttestationResult,
        system: IntegritySystemSignals,
        onlineVerificationError: String? = null,
    ): List<IntegrityFinding> = buildList {
        if (!standard.available) {
            add(
                IntegrityFinding(
                    id = "attestation.standard.unavailable",
                    title = "Android Key Attestation 不可用",
                    level = IntegrityFindingLevel.UNAVAILABLE,
                    summary = standard.error ?: "设备或当前系统环境没有返回可解析的 attestation certificate chain。",
                )
            )
        } else {
            addStandardFindings(standard)
        }

        if (strongBox.available) {
            add(
                IntegrityFinding(
                    id = "attestation.strongbox.available",
                    title = "StrongBox 可用",
                    level = IntegrityFindingLevel.PASS,
                    summary = "已获得 ${strongBox.attestationSecurityLevel.displayName} 级硬件认证证书链。",
                )
            )
            if (strongBox.challengeMatches == false || !strongBox.chainSignatureValid || strongBox.revoked) {
                addStandardFindings(strongBox, prefix = "strongbox")
            }
        } else {
            add(
                IntegrityFinding(
                    id = "attestation.strongbox.unavailable",
                    title = "StrongBox 未完成认证",
                    level = IntegrityFindingLevel.INFO,
                    summary = strongBox.error ?: "当前设备没有声明 StrongBox，或 StrongBox 暂时不可用。",
                )
            )
        }

        if (system.rootAvailable) {
            add(
                IntegrityFinding(
                    id = "context.root.expected",
                    title = "Root 环境",
                    level = IntegrityFindingLevel.EXPECTED,
                    summary = "RootTools 运行在 Root 测试设备上；该状态作为预期环境信息保留，不直接视为完整性故障。",
                )
            )
        }

        val propertyLocked = system.bootloaderLockedByProperties
        if (propertyLocked == false) {
            add(
                IntegrityFinding(
                    id = "context.bootloader.unlocked",
                    title = "Bootloader 已解锁",
                    level = if (system.rootAvailable) IntegrityFindingLevel.EXPECTED else IntegrityFindingLevel.INFO,
                    summary = "启动属性报告设备处于 unlocked 状态。",
                )
            )
        }

        val attestedLocked = standard.deviceLocked
        if (propertyLocked != null && attestedLocked != null && propertyLocked != attestedLocked) {
            add(
                IntegrityFinding(
                    id = "crosscheck.bootloader.mismatch",
                    title = "Bootloader 状态不一致",
                    level = IntegrityFindingLevel.WARN,
                    summary = "Android 启动属性与硬件 Attestation 对 deviceLocked 的观测不一致。",
                    evidence = "propertyLocked=$propertyLocked, attestedDeviceLocked=$attestedLocked",
                )
            )
        }

        propertyVerifiedBootState(system.verifiedBootStateProperty)?.let { propertyState ->
            if (standard.verifiedBootState != VerifiedBootState.UNKNOWN && propertyState != standard.verifiedBootState) {
                add(
                    IntegrityFinding(
                        id = "crosscheck.verified_boot.mismatch",
                        title = "Verified Boot 状态不一致",
                        level = IntegrityFindingLevel.WARN,
                        summary = "启动属性与硬件 Attestation 的 Verified Boot 状态不一致。",
                        evidence = "property=${propertyState.displayName}, attested=${standard.verifiedBootState.displayName}",
                    )
                )
            }
        }

        val systemPatch = system.securityPatch?.takeIf { it.isNotBlank() }
        val attestedPatch = standard.osPatchLevel?.takeIf { it.isNotBlank() }
        if (systemPatch != null && attestedPatch != null && !systemPatch.startsWith(attestedPatch)) {
            add(
                IntegrityFinding(
                    id = "crosscheck.security_patch.mismatch",
                    title = "系统补丁级别不一致",
                    level = IntegrityFindingLevel.WARN,
                    summary = "Framework security patch 与 Key Attestation 中的 OS patch level 不一致。",
                    evidence = "framework=$systemPatch, attested=$attestedPatch",
                )
            )
        }

        if (system.selinuxEnforcing == false) {
            add(
                IntegrityFinding(
                    id = "context.selinux.permissive",
                    title = "SELinux 非 Enforcing",
                    level = IntegrityFindingLevel.WARN,
                    summary = "当前 SELinux 不是 Enforcing；这会显著改变 Android 安全边界。",
                )
            )
        }

        onlineVerificationError?.takeIf { it.isNotBlank() }?.let { error ->
            add(
                IntegrityFinding(
                    id = "attestation.online.unavailable",
                    title = "在线信任元数据不可用",
                    level = IntegrityFindingLevel.INFO,
                    summary = "本地证书链仍已解析；Google roots / revocation 的在线刷新未完整完成。",
                    evidence = error,
                )
            )
        }
    }

    private fun MutableList<IntegrityFinding>.addStandardFindings(
        result: KeyAttestationResult,
        prefix: String = "standard",
    ) {
        if (result.challengeMatches == false) {
            add(
                IntegrityFinding(
                    id = "attestation.$prefix.challenge_mismatch",
                    title = "Attestation challenge 不匹配",
                    level = IntegrityFindingLevel.CRITICAL,
                    summary = "证书中的 challenge 与本次随机 challenge 不一致，不能把这条证书链视为本次请求的证明。",
                )
            )
        }
        if (!result.chainSignatureValid) {
            add(
                IntegrityFinding(
                    id = "attestation.$prefix.chain_signature_invalid",
                    title = "证书链签名验证失败",
                    level = IntegrityFindingLevel.CRITICAL,
                    summary = "至少一个证书无法由链中父证书公钥验证。",
                )
            )
        }
        if (!result.chainValidityValid) {
            add(
                IntegrityFinding(
                    id = "attestation.$prefix.chain_expired",
                    title = "证书链有效期异常",
                    level = IntegrityFindingLevel.WARN,
                    summary = "至少一个证书当前不在有效期内。",
                )
            )
        }
        if (result.revoked) {
            add(
                IntegrityFinding(
                    id = "attestation.$prefix.revoked",
                    title = "证书已在 Google 撤销列表中",
                    level = IntegrityFindingLevel.CRITICAL,
                    summary = "当前证书链命中官方 attestation revocation status list。",
                )
            )
        }

        when (result.rootAuthority) {
            AttestationRootAuthority.GOOGLE,
            AttestationRootAuthority.GOOGLE_RKP,
            AttestationRootAuthority.KNOX,
            AttestationRootAuthority.OEM -> add(
                IntegrityFinding(
                    id = "attestation.$prefix.trust_anchor",
                    title = "已识别认证根",
                    level = IntegrityFindingLevel.PASS,
                    summary = "根证书：${result.rootAuthority.displayName}${if (result.remoteProvisioned) " · RKP" else ""}。",
                )
            )

            AttestationRootAuthority.AOSP -> add(
                IntegrityFinding(
                    id = "attestation.$prefix.aosp_root",
                    title = "AOSP 软件认证根",
                    level = IntegrityFindingLevel.WARN,
                    summary = "证书链使用公开 AOSP 测试根，不具备生产设备硬件认证根的信任属性。",
                )
            )

            AttestationRootAuthority.UNKNOWN -> add(
                IntegrityFinding(
                    id = "attestation.$prefix.unknown_root",
                    title = "未知认证根",
                    level = IntegrityFindingLevel.WARN,
                    summary = "当前本地/在线 trust anchors 都没有识别该根证书。",
                    evidence = result.rootSpkiSha256,
                )
            )
        }

        add(
            IntegrityFinding(
                id = "attestation.$prefix.security_level",
                title = "认证安全等级",
                level = if (result.hardwareBacked) IntegrityFindingLevel.PASS else IntegrityFindingLevel.WARN,
                summary = "Attestation=${result.attestationSecurityLevel.displayName}, KeyMint=${result.keyMintSecurityLevel.displayName}。",
            )
        )
    }

    internal fun propertyVerifiedBootState(raw: String?): VerifiedBootState? = when (raw?.trim()?.lowercase()) {
        "green", "verified" -> VerifiedBootState.VERIFIED
        "yellow", "self_signed", "self-signed" -> VerifiedBootState.SELF_SIGNED
        "orange", "unverified" -> VerifiedBootState.UNVERIFIED
        "red", "failed" -> VerifiedBootState.FAILED
        else -> null
    }

    fun snapshotWithFindings(snapshot: DeviceIntegritySnapshot): DeviceIntegritySnapshot = snapshot.copy(
        findings = evaluate(
            standard = snapshot.standard,
            strongBox = snapshot.strongBox,
            system = snapshot.system,
            onlineVerificationError = snapshot.onlineVerificationError,
        )
    )
}
