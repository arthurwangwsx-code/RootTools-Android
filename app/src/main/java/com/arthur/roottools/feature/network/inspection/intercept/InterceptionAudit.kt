package com.arthur.roottools.feature.network.inspection.intercept

data class InterceptionAuditRecord(
    val action: String,
    val target: String = "",
    val before: String = "",
    val after: String = "",
    val success: Boolean,
    val rollbackHint: String = "",
)

fun interface InterceptionAuditSink {
    fun record(record: InterceptionAuditRecord)
}
