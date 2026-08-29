package com.arthur.nfclab.platform.access

import com.arthur.nfclab.domain.AccessDiagnosticReport
import com.arthur.nfclab.domain.AccessReaderOutcome
import com.arthur.nfclab.domain.NfcCard
import com.arthur.nfclab.domain.NfcDeviceProfile

interface AccessDiagnosticProbe {
    val id: String
    val priority: Int

    fun supports(profile: NfcDeviceProfile): Boolean

    fun start(profile: NfcDeviceProfile, activeCard: NfcCard?): Result<Unit>

    fun stop(outcome: AccessReaderOutcome): Result<AccessDiagnosticReport>

    fun cancel()
}
