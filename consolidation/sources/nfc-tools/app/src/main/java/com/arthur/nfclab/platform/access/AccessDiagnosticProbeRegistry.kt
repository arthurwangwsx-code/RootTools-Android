package com.arthur.nfclab.platform.access

import android.content.Context
import com.arthur.nfclab.platform.xiaomi.XiaomiAccessDiagnosticProbe

object AccessDiagnosticProbeRegistry {
    fun defaults(context: Context): List<AccessDiagnosticProbe> = listOf(
        XiaomiAccessDiagnosticProbe(context.applicationContext),
    )
}
