package com.arthur.roottools.feature.assistant.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSelectionPolicyTest {
    @Test
    fun `eligible different package switches`() {
        val decision = AssistantSelectionPolicy.decide(
            targetPackage = "com.openai.chatgpt",
            currentPackage = "com.miui.voiceassist",
            eligiblePackages = setOf("com.miui.voiceassist", "com.openai.chatgpt"),
        )

        assertEquals(AssistantSelectionDecision.Switch("com.openai.chatgpt"), decision)
    }

    @Test
    fun `current holder is a no-op`() {
        val decision = AssistantSelectionPolicy.decide(
            targetPackage = "com.openai.chatgpt",
            currentPackage = "com.openai.chatgpt",
            eligiblePackages = setOf("com.openai.chatgpt"),
        )

        assertEquals(AssistantSelectionDecision.NoOp, decision)
    }

    @Test
    fun `non candidate package is rejected`() {
        val decision = AssistantSelectionPolicy.decide(
            targetPackage = "com.example.notes",
            currentPackage = null,
            eligiblePackages = setOf("com.openai.chatgpt"),
        )

        assertEquals(
            AssistantSelectionDecision.Reject(AssistantSelectionRejectReason.NOT_ELIGIBLE),
            decision,
        )
    }

    @Test
    fun `hostile package values are rejected`() {
        val values = listOf(
            "com.openai.chatgpt;reboot",
            "com.openai.chatgpt && id",
            "com.openai.chatgpt\nreboot",
            "com openai chatgpt",
            "",
        )

        values.forEach { value ->
            val decision = AssistantSelectionPolicy.decide(
                targetPackage = value,
                currentPackage = null,
                eligiblePackages = setOf(value),
            )
            assertTrue(decision is AssistantSelectionDecision.Reject)
            assertEquals(
                AssistantSelectionRejectReason.INVALID_PACKAGE,
                (decision as AssistantSelectionDecision.Reject).reason,
            )
        }
    }
}
