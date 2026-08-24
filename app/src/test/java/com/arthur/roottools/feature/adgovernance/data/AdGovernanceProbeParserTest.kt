package com.arthur.roottools.feature.adgovernance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdGovernanceProbeParserTest {
    @Test
    fun `automation mode two requires gkd user service and shizuku server`() {
        val snapshot = AdGovernanceProbeParser.parse(
            """
            ROOT_UID=0
            PKG_GKD=1
            GKD_RUNNING=1
            GKD_USER_SERVICE=1
            SHIZUKU_SERVER=1
            PKG_ADAWAY=1
            ADAWAY_RUNNING=0
            PKG_HYPER_ADS=1
            HYPER_ADS_ENABLED=1
            PKG_MIUI_ANALYTICS=1
            MIUI_ANALYTICS_ENABLED=1
            HOSTS_LINES=2
            HOSTS_SYSTEMLESS=0
            TAILSCALE_ACTIVE=1
            TAILSCALE_IPV4=100.110.5.86
            GKD_SUBSCRIPTIONS=1
            __GKD_STORE_BEGIN__
            {"enableAutomator":true,"automatorMode":2,"enableMatch":true}
            __GKD_STORE_END__
            __GKD_LOG_BEGIN__
            11:01:17.497 A11yFeat, worker, initRuleChangedLog
            [0]: TopActivity(appId=com.zhihu.android, activityId=com.zhihu.android.app.ui.activity.LauncherActivity, number=0)
            11:01:19.774 A11yState, worker, a11y.addActionLog(A11yState.kt:308)
            [0]: id:667, v:572, type:app, gKey=-1, gName:开屏广告, index:0, key:0, status:达到最大执行次数
            11:05:00.558 A11yFeat, worker, initRuleChangedLog
            [0]: TopActivity(appId=com.jingdong.app.mall, activityId=com.jingdong.app.mall.MainFrameActivity, number=0)
            11:05:02.133 A11yState, worker, a11y.addActionLog(A11yState.kt:308)
            [0]: id:667, v:572, type:app, gKey=6, gName:局部广告-横幅广告, index:0, key:0, status:处于冷却时间
            __GKD_LOG_END__
            """.trimIndent()
        )

        assertTrue(snapshot.rootAvailable)
        assertTrue(snapshot.gkd.installed)
        assertTrue(snapshot.gkd.running)
        assertEquals(2, snapshot.gkd.automatorMode)
        assertTrue(snapshot.gkd.automatorEnabled)
        assertTrue(snapshot.gkd.engineReady)
        assertEquals(1, snapshot.gkd.subscriptionCount)
        assertEquals(2, snapshot.recentActions.size)
        assertEquals(1, snapshot.actionCountFor("com.zhihu.android"))
        assertEquals(1, snapshot.actionCountFor("com.jingdong.app.mall"))
        assertEquals("局部广告-横幅广告", snapshot.latestActionFor("com.jingdong.app.mall")?.groupName)
        assertEquals(2, snapshot.hosts.lineCount)
        assertFalse(snapshot.hosts.active)
        assertTrue(snapshot.tailscale.active)
        assertEquals("100.110.5.86", snapshot.tailscale.ipv4)
    }

    @Test
    fun `automation mode two is not ready when privileged worker disappeared`() {
        val snapshot = AdGovernanceProbeParser.parse(
            """
            ROOT_UID=0
            PKG_GKD=1
            GKD_RUNNING=1
            GKD_USER_SERVICE=0
            SHIZUKU_SERVER=1
            GKD_SUBSCRIPTIONS=1
            __GKD_STORE_BEGIN__
            {"enableAutomator":true,"automatorMode":2}
            __GKD_STORE_END__
            """.trimIndent()
        )

        assertFalse(snapshot.gkd.engineReady)
    }

    @Test
    fun `accessibility mode only needs enabled gkd process`() {
        val snapshot = AdGovernanceProbeParser.parse(
            """
            ROOT_UID=0
            PKG_GKD=1
            GKD_RUNNING=1
            GKD_USER_SERVICE=0
            SHIZUKU_SERVER=0
            __GKD_STORE_BEGIN__
            {"enableAutomator":true,"automatorMode":1}
            __GKD_STORE_END__
            """.trimIndent()
        )

        assertTrue(snapshot.gkd.engineReady)
    }

    @Test
    fun `malformed or partial payload stays conservative`() {
        val snapshot = AdGovernanceProbeParser.parse(
            """
            ROOT_UID=not-a-number
            PKG_GKD=wat
            HOSTS_LINES=nope
            __GKD_STORE_BEGIN__
            {broken
            __GKD_STORE_END__
            __GKD_LOG_BEGIN__
            random text
            __GKD_LOG_END__
            """.trimIndent()
        )

        assertFalse(snapshot.rootAvailable)
        assertFalse(snapshot.gkd.installed)
        assertFalse(snapshot.gkd.engineReady)
        assertEquals(0, snapshot.hosts.lineCount)
        assertTrue(snapshot.recentActions.isEmpty())
    }
}
