package com.arthur.roottools.core.shadow

import com.arthur.roottools.model.ShadowDisplayRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowDisplayPolicyTest {
    @Test
    fun config_acceptsBalancedProfile_andRejectsBounds() {
        assertNotNull(ShadowDisplayPolicy.config(720, 1600, 320))
        assertNull(ShadowDisplayPolicy.config(359, 1600, 320))
        assertNull(ShadowDisplayPolicy.config(720, 3201, 320))
        assertNull(ShadowDisplayPolicy.config(720, 1600, 641))
    }

    @Test
    fun coordinate_isDisplayBounded() {
        assertEquals(0, ShadowDisplayPolicy.coordinate(0, 720))
        assertEquals(719, ShadowDisplayPolicy.coordinate(719, 720))
        assertNull(ShadowDisplayPolicy.coordinate(-1, 720))
        assertNull(ShadowDisplayPolicy.coordinate(720, 720))
    }

    @Test
    fun text_preservesUserContent_butRejectsControlAndHugePayload() {
        assertEquals("KLCC & TRX", ShadowDisplayPolicy.text("KLCC & TRX"))
        assertEquals("最近新手机", ShadowDisplayPolicy.text("最近新手机"))
        assertEquals(ShadowDisplayTextStrategy.KEY_EVENTS, ShadowDisplayPolicy.textStrategy("new phones 2026"))
        assertEquals(ShadowDisplayTextStrategy.CLIPBOARD_PASTE, ShadowDisplayPolicy.textStrategy("最近新手机"))
        assertNull(ShadowDisplayPolicy.text("bad\u0000text"))
        assertNull(ShadowDisplayPolicy.text("bad\ntext"))
        assertNull(ShadowDisplayPolicy.text("x".repeat(ShadowDisplayPolicy.MAX_TEXT_LENGTH + 1)))
        assertNull(ShadowDisplayPolicy.textStrategy("bad\ntext"))
    }

    @Test
    fun packageName_rejectsShellSyntax() {
        assertEquals("com.google.android.apps.maps", ShadowDisplayPolicy.packageName("com.google.android.apps.maps"))
        assertNull(ShadowDisplayPolicy.packageName("com.google.maps;reboot"))
        assertNull(ShadowDisplayPolicy.packageName("com.google.maps && id"))
        assertNull(ShadowDisplayPolicy.packageName("not-a-package"))
    }

    @Test
    fun statusParser_requiresProcessAndDisplayForRunning() {
        val status = ShadowDisplayStatusParser.parse(
            """
            state=running
            pid=1234
            displayId=43
            width=720
            height=1600
            densityDpi=320
            startedAtMs=100
            processAlive=1
            activeDisplays=0,43
            """.trimIndent(),
        )

        assertEquals(ShadowDisplayRuntimeState.RUNNING, status.state)
        assertEquals(43, status.displayId)
        assertTrue(status.running)
    }

    @Test
    fun statusParser_marksStaleDaemonNotRunning() {
        val status = ShadowDisplayStatusParser.parse(
            """
            state=running
            pid=1234
            displayId=43
            width=720
            height=1600
            densityDpi=320
            processAlive=0
            activeDisplays=0
            """.trimIndent(),
        )

        assertFalse(status.running)
        assertFalse(status.displayActive)
    }
}
