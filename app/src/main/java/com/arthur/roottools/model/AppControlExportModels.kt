package com.arthur.roottools.model

data class AppControlExportResult(
    val markdownPath: String,
    val jsonPath: String,
    val generatedAtMs: Long,
)
