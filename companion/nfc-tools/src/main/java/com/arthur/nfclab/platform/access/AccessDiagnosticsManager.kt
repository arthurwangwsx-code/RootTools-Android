package com.arthur.nfclab.platform.access

import android.content.Context
import com.arthur.nfclab.domain.AccessDiagnosticReport
import com.arthur.nfclab.domain.AccessReaderOutcome
import com.arthur.nfclab.domain.NfcCard
import com.arthur.nfclab.domain.NfcDeviceProfile

class AccessDiagnosticsManager(
    context: Context,
    private val probes: List<AccessDiagnosticProbe> = AccessDiagnosticProbeRegistry.defaults(context),
) {
    private var activeProbe: AccessDiagnosticProbe? = null

    fun supportedProvider(profile: NfcDeviceProfile?): AccessDiagnosticProbe? {
        if (profile == null) return null
        return probes
            .asSequence()
            .filter { runCatching { it.supports(profile) }.getOrDefault(false) }
            .sortedBy { it.priority }
            .firstOrNull()
    }

    fun start(profile: NfcDeviceProfile, activeCard: NfcCard?): Result<Unit> {
        if (activeProbe != null) return Result.failure(IllegalStateException("已有门禁诊断正在运行"))
        val probe = supportedProvider(profile)
            ?: return Result.failure(UnsupportedOperationException("当前设备暂未提供底层门禁兼容性探针"))
        val result = probe.start(profile, activeCard)
        if (result.isSuccess) activeProbe = probe
        return result
    }

    fun stop(outcome: AccessReaderOutcome): Result<AccessDiagnosticReport> {
        val probe = activeProbe ?: return Result.failure(IllegalStateException("当前没有正在运行的门禁诊断"))
        return try {
            probe.stop(outcome)
        } finally {
            activeProbe = null
        }
    }

    fun cancel() {
        activeProbe?.cancel()
        activeProbe = null
    }
}
