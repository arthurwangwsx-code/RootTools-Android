package com.arthur.roottools.policy

object BuildCompatibilityPolicy {
    enum class Result {
        INITIALIZE,
        COMPATIBLE,
        SYSTEM_BUILD_CHANGED,
    }

    fun evaluate(previousFingerprint: String, currentFingerprint: String): Result = when {
        currentFingerprint.isBlank() -> Result.COMPATIBLE
        previousFingerprint.isBlank() -> Result.INITIALIZE
        previousFingerprint == currentFingerprint -> Result.COMPATIBLE
        else -> Result.SYSTEM_BUILD_CHANGED
    }
}
