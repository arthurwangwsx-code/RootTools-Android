package com.arthur.nfclab.hce

/**
 * Synthetic ISO-DEP lab profile. This is intentionally not a real access credential.
 */
data class IsoDepLabProfile(
    val aid: String,
    val displayName: String,
    val applicationData: ByteArray,
    val testKey: ByteArray,
) {
    init {
        require(aid.length >= 12 && aid.length % 2 == 0)
        require(testKey.size in setOf(16, 24, 32))
    }
}

