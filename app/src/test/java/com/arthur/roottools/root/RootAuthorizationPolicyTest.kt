package com.arthur.roottools.root

import com.arthur.roottools.core.privilege.RootCommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootAuthorizationPolicyTest {
    @Test
    fun uidZeroIsGranted() {
        val result = RootAuthorizationPolicy.fromProbe(RootCommandResult(0, "0"))

        assertEquals(RootAuthorizationStatus.GRANTED, result.status)
        assertTrue(result.granted)
    }

    @Test
    fun timeoutIsNotReportedAsMissingRoot() {
        val result = RootAuthorizationPolicy.fromProbe(RootCommandResult(-1, "", timedOut = true))

        assertEquals(RootAuthorizationStatus.DENIED_OR_TIMEOUT, result.status)
        assertTrue(result.detail.contains("timed out"))
    }

    @Test
    fun nonRootUidIsDenied() {
        val result = RootAuthorizationPolicy.fromProbe(RootCommandResult(0, "2000"))

        assertEquals(RootAuthorizationStatus.DENIED_OR_TIMEOUT, result.status)
    }
}
