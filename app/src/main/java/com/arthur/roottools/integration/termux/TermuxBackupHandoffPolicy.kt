package com.arthur.roottools.integration.termux

data class TermuxBackupHandoffValidation(
    val valid: Boolean,
    val message: String,
)

object TermuxBackupHandoffPolicy {
    const val MAX_ARTIFACT_BYTES = 32L * 1024L * 1024L
    const val CHUNK_BYTES = 48 * 1024

    fun validateMetadata(artifactId: String, fileName: String, length: Long): TermuxBackupHandoffValidation {
        if (!ARTIFACT_ID_REGEX.matches(artifactId)) return invalid("Invalid backup artifact id")
        if (!FILE_NAME_REGEX.matches(fileName)) return invalid("Invalid backup file name")
        if (length !in 1..MAX_ARTIFACT_BYTES) return invalid("Backup artifact exceeds the supported handoff size")
        return TermuxBackupHandoffValidation(true, "OK")
    }

    fun isAppOwnedPath(path: String, roots: List<String>): Boolean {
        if (roots.isEmpty()) return false
        return roots.any { root ->
            path == root || path.startsWith(root.trimEnd('/') + "/")
        }
    }

    private fun invalid(message: String) = TermuxBackupHandoffValidation(false, message)

    private val ARTIFACT_ID_REGEX = Regex("^[a-z0-9][a-z0-9._-]{1,63}$")
    private val FILE_NAME_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$")
}

