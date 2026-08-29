package com.arthur.nfclab.platform.diagnostics

import com.arthur.nfclab.platform.samsung.SamsungRootDiagnosticsContributor
import com.arthur.nfclab.platform.xiaomi.XiaomiRootDiagnosticsContributor

object RootDiagnosticsContributorRegistry {
    fun defaults(): List<RootDiagnosticsContributor> = listOf(
        XiaomiRootDiagnosticsContributor(),
        SamsungRootDiagnosticsContributor(),
    )
}
