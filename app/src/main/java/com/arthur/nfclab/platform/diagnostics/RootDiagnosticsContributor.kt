package com.arthur.nfclab.platform.diagnostics

import android.content.Context

interface RootDiagnosticsContributor {
    val id: String
    val priority: Int

    fun supports(context: Context): Boolean

    /**
     * Returns an already human-readable diagnostic section. Contributors are read-only probes.
     */
    fun collect(context: Context): String
}
