package com.arthur.roottools.feature.network.tailscale

data class RootTailscaleAuditRecord(
    val action: String,
    val target: String = "",
    val before: String = "",
    val after: String = "",
    val success: Boolean,
    val rollbackHint: String = "",
)

fun interface RootTailscaleAuditSink {
    fun record(record: RootTailscaleAuditRecord)
}
