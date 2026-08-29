package com.arthur.nfclab.platform.xiaomi

import com.arthur.nfclab.domain.AccessDiagnosticConclusion
import com.arthur.nfclab.domain.AccessReaderOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiAccessDiagnosticAnalyzerTest {
    @Test
    fun noField_isClassifiedAsNoRfField() {
        val report = XiaomiAccessDiagnosticAnalyzer.analyze(
            sessionId = "s1",
            startedAtMs = 1_000L,
            finishedAtMs = 2_000L,
            cardTitle = "小区",
            cardSourceLabel = "小米钱包",
            activeCardId = "A00001",
            outcome = AccessReaderOutcome.NO_REACTION,
            baselineReaderDump = "Counts:{TOTAL:1,OK:1}",
            finalReaderDump = "Counts:{TOTAL:1,OK:1}",
            logLines = emptyList(),
        )

        assertEquals(AccessDiagnosticConclusion.NO_RF_FIELD, report.conclusion)
        assertFalse(report.signals.rfFieldSeen)
        assertFalse(report.signals.cardInteractionSeen)
    }

    @Test
    fun fieldWithoutCardInteraction_isClassifiedAsProtocolCompatibility() {
        val report = XiaomiAccessDiagnosticAnalyzer.analyze(
            sessionId = "s2",
            startedAtMs = 10_000L,
            finishedAtMs = 12_000L,
            cardTitle = "小区",
            cardSourceLabel = "小米钱包",
            activeCardId = "A00001",
            outcome = AccessReaderOutcome.NO_REACTION,
            baselineReaderDump = "",
            finalReaderDump = "",
            logLines = listOf("123.0 I/NfcRDDT: Here we go!"),
        )

        assertEquals(AccessDiagnosticConclusion.RF_FIELD_NO_CARD_INTERACTION, report.conclusion)
        assertTrue(report.signals.rfFieldSeen)
        assertEquals(1, report.signals.fieldSessionCount)
        assertFalse(report.signals.cardInteractionSeen)
    }

    @Test
    fun nfceeAndLayer4Failure_isClassifiedAfterCardInteraction() {
        val aid = "A0000003964D344D10045920D5951E01"
        val finalReader = "ID:2 TA:true TB:false TF:false HDL:4 MCount:1 SAid:$aid NAids:[$aid,] HAids:[] NDS:[] TM:20000"
        val report = XiaomiAccessDiagnosticAnalyzer.analyze(
            sessionId = "s3",
            startedAtMs = 19_000L,
            finishedAtMs = 22_000L,
            cardTitle = "小区",
            cardSourceLabel = "小米钱包",
            activeCardId = aid,
            outcome = AccessReaderOutcome.REACTED_BUT_FAILED,
            baselineReaderDump = "",
            finalReaderDump = finalReader,
            logLines = listOf(
                "123.0 I/NfcRDDT: Here we go!",
                "123.1 E/NfcService: onNfceeActionNotification nfceeId = 192 ,triger = 1",
                "123.2 W/NfcRDDT: Hightest:4 Layer:4 Count:[L2:1,L3:1,L4:1]",
                "123.3 I/NfcRDDT: card has been activated:$aid",
            ),
        )

        assertEquals(AccessDiagnosticConclusion.CARD_INTERACTION_AUTH_FAILED, report.conclusion)
        assertTrue(report.signals.rfFieldSeen)
        assertTrue(report.signals.cardInteractionSeen)
        assertEquals(4, report.signals.highestProtocolLayer)
        assertTrue(report.signals.nfceeActionCount > 0)
        assertEquals(true, report.signals.activeCardMatched)
        assertTrue("A" in report.signals.detectedTechnologies)
        assertTrue(report.evidence.none { it.contains(aid) })
    }
}
