package com.arthur.roottools.privilege

import com.arthur.roottools.model.PrivilegeBackendType
import com.arthur.roottools.model.PrivilegeCapability
import com.arthur.roottools.model.PrivilegeRouteBackend
import com.arthur.roottools.model.ShizukuBridgeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegeRoutingPolicyTest {
    @Test
    fun classifyBackend_distinguishesAdbRootAndSui() {
        assertEquals(PrivilegeBackendType.SHIZUKU_ADB, PrivilegeRoutingPolicy.classifyShizukuBackend(2000, false))
        assertEquals(PrivilegeBackendType.SHIZUKU_ROOT, PrivilegeRoutingPolicy.classifyShizukuBackend(0, false))
        assertEquals(PrivilegeBackendType.SUI_ROOT, PrivilegeRoutingPolicy.classifyShizukuBackend(0, true))
        assertEquals(PrivilegeBackendType.NONE, PrivilegeRoutingPolicy.classifyShizukuBackend(1000, false))
        assertEquals(PrivilegeBackendType.NONE, PrivilegeRoutingPolicy.classifyShizukuBackend(null, true))
    }

    @Test
    fun frameworkOperation_prefersShizuku_thenRootFallback() {
        val bridge = readyBridge(PrivilegeBackendType.SHIZUKU_ROOT)
        assertEquals(
            listOf(PrivilegeRouteBackend.SHIZUKU_ROOT, PrivilegeRouteBackend.ROOT_SHELL),
            PrivilegeRoutingPolicy.routesFor(PrivilegeCapability.PACKAGE_CONTROL, bridge, rootAvailable = true),
        )
    }

    @Test
    fun frameworkOperation_canRunWithShizukuAdbWithoutRoot() {
        val bridge = readyBridge(PrivilegeBackendType.SHIZUKU_ADB)
        assertEquals(
            listOf(PrivilegeRouteBackend.SHIZUKU_ADB),
            PrivilegeRoutingPolicy.routesFor(PrivilegeCapability.APP_OPS, bridge, rootAvailable = false),
        )
        assertEquals(
            PrivilegeRouteBackend.SHIZUKU_ADB,
            PrivilegeRoutingPolicy.primaryBackend(bridge, rootAvailable = false),
        )
    }

    @Test
    fun deniedShizuku_isNeverSelected() {
        val bridge = readyBridge(PrivilegeBackendType.SHIZUKU_ROOT).copy(permissionGranted = false)
        assertEquals(
            listOf(PrivilegeRouteBackend.ROOT_SHELL),
            PrivilegeRoutingPolicy.routesFor(PrivilegeCapability.COMPONENT_CONTROL, bridge, rootAvailable = true),
        )
    }

    @Test
    fun rootOnlyCapability_staysOnRootShell_evenWithSui() {
        val bridge = readyBridge(PrivilegeBackendType.SUI_ROOT)
        assertEquals(
            listOf(PrivilegeRouteBackend.ROOT_SHELL),
            PrivilegeRoutingPolicy.routesFor(PrivilegeCapability.SYSFS_WRITE, bridge, rootAvailable = true),
        )
        assertTrue(PrivilegeRoutingPolicy.supports(PrivilegeRouteBackend.ROOT_SHELL, PrivilegeCapability.SYSFS_WRITE))
        assertFalse(PrivilegeRoutingPolicy.supports(PrivilegeRouteBackend.SUI_ROOT, PrivilegeCapability.SYSFS_WRITE))

        assertEquals(
            listOf(PrivilegeRouteBackend.ROOT_SHELL),
            PrivilegeRoutingPolicy.routesFor(PrivilegeCapability.VIRTUAL_DISPLAY_CONTROL, bridge, rootAvailable = true),
        )
        assertFalse(PrivilegeRoutingPolicy.supports(PrivilegeRouteBackend.SUI_ROOT, PrivilegeCapability.VIRTUAL_DISPLAY_CONTROL))
    }

    @Test
    fun noPrivilege_returnsNoRoute() {
        assertEquals(
            emptyList<PrivilegeRouteBackend>(),
            PrivilegeRoutingPolicy.routesFor(
                PrivilegeCapability.PACKAGE_CONTROL,
                ShizukuBridgeState(),
                rootAvailable = false,
            ),
        )
        assertEquals(
            PrivilegeRouteBackend.NONE,
            PrivilegeRoutingPolicy.primaryBackend(ShizukuBridgeState(), rootAvailable = false),
        )
    }

    private fun readyBridge(backend: PrivilegeBackendType) = ShizukuBridgeState(
        binderAlive = true,
        permissionGranted = true,
        backend = backend,
        uid = when (backend) {
            PrivilegeBackendType.SHIZUKU_ADB -> 2000
            PrivilegeBackendType.SHIZUKU_ROOT, PrivilegeBackendType.SUI_ROOT -> 0
            PrivilegeBackendType.NONE -> null
        },
    )
}
