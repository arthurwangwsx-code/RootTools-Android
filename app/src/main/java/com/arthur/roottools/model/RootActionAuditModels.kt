package com.arthur.roottools.model

data class RootActionAuditRecord(
    val timestampMs: Long,
    val source: String,
    val feature: String,
    val action: String,
    val target: String,
    val before: String,
    val after: String,
    val success: Boolean,
    val rollbackHint: String,
)
