package com.arthur.roottools.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootToolsNavigationPolicyTest {
    @Test
    fun everyToolIdHasExactlyOneDestination() {
        val destinations = ToolId.entries.map(RootToolsNavigationPolicy::destinationFor)

        assertEquals(ToolId.entries.size, destinations.size)
        assertEquals(ToolId.entries.size, destinations.distinct().size)
    }

    @Test
    fun toolsMapToCanonicalDomains() {
        assertEquals(RootToolsTab.APPS, RootToolsNavigationPolicy.destinationFor(ToolId.APPS).tab)
        assertEquals(RootToolsTab.APPS, RootToolsNavigationPolicy.destinationFor(ToolId.AD_GOVERNANCE).tab)
        assertEquals(RootToolsTab.DEVICE, RootToolsNavigationPolicy.destinationFor(ToolId.PERFORMANCE).tab)
        assertEquals(RootToolsTab.DEVICE, RootToolsNavigationPolicy.destinationFor(ToolId.AGENT_SESSION).tab)
        assertEquals(RootToolsTab.DEVICE, RootToolsNavigationPolicy.destinationFor(ToolId.ROOT_ADB).tab)
        assertEquals(RootToolsTab.DIAGNOSTICS, RootToolsNavigationPolicy.destinationFor(ToolId.INTEGRITY).tab)
        assertEquals(RootToolsTab.SYSTEM, RootToolsNavigationPolicy.destinationFor(ToolId.MODULES).tab)
    }

    @Test
    fun externalEntryMapsToTypedDestination() {
        assertEquals(RootToolsDestination.ADB, RootToolsNavigationPolicy.externalScreen("ADB"))
        assertEquals(RootToolsDestination.INTEGRITY, RootToolsNavigationPolicy.externalScreen("integrity"))
        assertEquals(RootToolsDestination.AGENT_SESSION, RootToolsNavigationPolicy.externalScreen("agent-session"))
        assertEquals(RootToolsDestination.SHADOW_DISPLAY, RootToolsNavigationPolicy.externalScreen("shadow-display"))
        assertNull(RootToolsNavigationPolicy.externalScreen("unknown"))
    }

    @Test
    fun routeSelectsExpectedBottomTab() {
        assertEquals(RootToolsTab.HOME, RootToolsNavigationPolicy.tabForRoute(null))
        assertEquals(RootToolsTab.APPS, RootToolsNavigationPolicy.tabForRoute("apps/control"))
        assertEquals(RootToolsTab.DEVICE, RootToolsNavigationPolicy.tabForRoute("device/performance"))
        assertEquals(RootToolsTab.DIAGNOSTICS, RootToolsNavigationPolicy.tabForRoute("diagnostics/integrity"))
        assertEquals(RootToolsTab.SYSTEM, RootToolsNavigationPolicy.tabForRoute("system/modules"))
    }
}
