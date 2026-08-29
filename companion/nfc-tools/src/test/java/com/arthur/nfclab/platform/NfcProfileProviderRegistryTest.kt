package com.arthur.nfclab.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcProfileProviderRegistryTest {
    @Test
    fun defaults_haveUniqueStableProviderIds() {
        val providers = NfcProfileProviderRegistry.defaults()
        val ids = providers.map { it.id }

        assertTrue(providers.isNotEmpty())
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun defaults_haveDeterministicPriorities() {
        val providers = NfcProfileProviderRegistry.defaults()
        val ordered = providers.sortedBy { it.priority }

        assertEquals(ordered.map { it.id }, providers.sortedBy { it.priority }.map { it.id })
        assertEquals(providers.size, providers.map { it.priority to it.id }.toSet().size)
    }
}
