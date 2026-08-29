package com.arthur.roottools.feature.companions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSuitePolicyTest {
    private val spec = CompanionSuiteRegistry.tools.first()

    @Test
    fun `installed enabled package is launchable only when launcher exists`() {
        val state = CompanionSuitePolicy.resolve(
            spec,
            CompanionPackageObservation(true, enabled = true, launchable = true, versionName = "1.2"),
        )

        assertEquals(CompanionAvailability.INSTALLED, state.availability)
        assertTrue(state.launchable)
        assertEquals("1.2", state.versionName)
    }

    @Test
    fun `disabled package stays visible but cannot launch`() {
        val state = CompanionSuitePolicy.resolve(
            spec,
            CompanionPackageObservation(true, enabled = false, launchable = true),
        )

        assertEquals(CompanionAvailability.DISABLED, state.availability)
        assertFalse(state.launchable)
    }

    @Test
    fun `missing package does not retain stale version or launch state`() {
        val state = CompanionSuitePolicy.resolve(
            spec,
            CompanionPackageObservation(false, enabled = true, launchable = true, versionName = "stale"),
        )

        assertEquals(CompanionAvailability.MISSING, state.availability)
        assertFalse(state.launchable)
        assertEquals(null, state.versionName)
    }

    @Test
    fun `registry has unique package module and artifact ownership`() {
        val tools = CompanionSuiteRegistry.tools

        assertEquals(tools.size, tools.map { it.packageName }.distinct().size)
        assertEquals(tools.size, tools.map { it.gradleModule }.distinct().size)
        assertEquals(tools.size, tools.map { it.artifactName }.distinct().size)
        assertEquals(CompanionRole.OPTIONAL, tools.single { it.id == CompanionToolId.NFC_LAB }.role)
    }
}
