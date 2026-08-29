package com.arthur.nfclab.nfc

data class TagSnapshot(
    val timestampMs: Long,
    val idHex: String,
    val technologies: List<String>,
    val details: Map<String, String>,
    val ndefRecords: List<String>,
    val warning: String? = null,
)

fun ByteArray?.toHex(): String {
    if (this == null) return ""
    return joinToString(separator = "") { "%02X".format(it) }
}

