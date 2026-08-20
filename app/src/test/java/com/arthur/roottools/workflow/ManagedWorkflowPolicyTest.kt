package com.arthur.roottools.workflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedWorkflowPolicyTest {
    @Test
    fun `app test workflow requires a safe shaped package name`() {
        assertTrue(
            ManagedWorkflowPolicy.validate(
                ManagedWorkflowRequest(ManagedWorkflowId.APP_TEST_READY, "com.example.app")
            ).valid
        )
        assertFalse(
            ManagedWorkflowPolicy.validate(
                ManagedWorkflowRequest(ManagedWorkflowId.APP_TEST_READY, "com.example;reboot")
            ).valid
        )
        assertFalse(
            ManagedWorkflowPolicy.validate(ManagedWorkflowRequest(ManagedWorkflowId.APP_TEST_READY)).valid
        )
    }

    @Test
    fun `workflows without inputs reject surprise package payloads`() {
        assertFalse(
            ManagedWorkflowPolicy.validate(
                ManagedWorkflowRequest(ManagedWorkflowId.TEST_DEVICE_READY, "com.example.app")
            ).valid
        )
    }
}

