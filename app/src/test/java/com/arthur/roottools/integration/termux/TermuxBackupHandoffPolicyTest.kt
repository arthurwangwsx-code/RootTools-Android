package com.arthur.roottools.integration.termux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxBackupHandoffPolicyTest {
    @Test
    fun `backup metadata rejects path traversal and oversize artifacts`() {
        assertTrue(TermuxBackupHandoffPolicy.validateMetadata("diag-1", "report.txt", 1024).valid)
        assertFalse(TermuxBackupHandoffPolicy.validateMetadata("../diag", "report.txt", 1024).valid)
        assertFalse(TermuxBackupHandoffPolicy.validateMetadata("diag-1", "../report.txt", 1024).valid)
        assertFalse(
            TermuxBackupHandoffPolicy.validateMetadata(
                "diag-1",
                "report.txt",
                TermuxBackupHandoffPolicy.MAX_ARTIFACT_BYTES + 1,
            ).valid
        )
    }

    @Test
    fun `app owned path requires canonical root boundary`() {
        val roots = listOf("/data/user/0/com.arthur.roottools/files")
        assertTrue(
            TermuxBackupHandoffPolicy.isAppOwnedPath(
                "/data/user/0/com.arthur.roottools/files/diagnostics/a.txt",
                roots,
            )
        )
        assertFalse(
            TermuxBackupHandoffPolicy.isAppOwnedPath(
                "/data/user/0/com.arthur.roottools/files-evil/a.txt",
                roots,
            )
        )
    }
}

