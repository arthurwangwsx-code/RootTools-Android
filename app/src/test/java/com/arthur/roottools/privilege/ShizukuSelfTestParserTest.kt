package com.arthur.roottools.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuSelfTestParserTest {
    @Test
    fun parsesSuccessfulRootService() {
        val result = ShizukuSelfTestParser.parse("uid=0;pm=ok;activity=ok;appops=ok")
        assertEquals(0, result.uid)
        assertTrue(result.packageControl)
        assertTrue(result.activityControl)
        assertTrue(result.appOps)
        assertTrue(result.allPassed)
    }

    @Test
    fun parsesAdbIdentityWithoutTreatingItAsRoot() {
        val result = ShizukuSelfTestParser.parse("uid=2000;pm=ok;activity=ok;appops=ok")
        assertEquals(2000, result.uid)
        assertTrue(result.allPassed)
    }

    @Test
    fun missingMalformedAndFailedFieldsAreConservative() {
        val result = ShizukuSelfTestParser.parse("uid=oops;pm=ok;activity=fail;garbage;appops=")
        assertEquals(null, result.uid)
        assertTrue(result.packageControl)
        assertFalse(result.activityControl)
        assertFalse(result.appOps)
        assertFalse(result.allPassed)
    }
}
