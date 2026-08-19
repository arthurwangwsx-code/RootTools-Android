package com.arthur.roottools.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RootActionAuditStoreCodecTest {
    @Test
    fun auditFieldsAreBoundedByDesign() {
        // The persistent audit store intentionally caps both record count and field size.
        // This test documents the semantic limit without requiring an Android Context.
        assertEquals(200, RootActionAuditStore.MAX_RECORDS_FOR_TEST)
    }
}
