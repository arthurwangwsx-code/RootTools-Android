package com.arthur.roottools.privilege

import com.arthur.roottools.model.PrivilegeBackendType
import com.arthur.roottools.model.PrivilegeCapability
import com.arthur.roottools.model.PrivilegeRouteBackend
import com.arthur.roottools.model.FrameworkPrivilegePreference
import com.arthur.roottools.model.ShizukuBridgeState

/**
 * Pure routing policy for privileged operations.
 *
 * Keep this Android-free so backend selection can be exhaustively unit tested. Runtime adapters
 * (RootShell / Shizuku UserService) are responsible only for executing the selected route.
 */
object PrivilegeRoutingPolicy {
    private val frameworkCapabilities = setOf(
        PrivilegeCapability.PACKAGE_CONTROL,
        PrivilegeCapability.COMPONENT_CONTROL,
        PrivilegeCapability.ACTIVITY_CONTROL,
        PrivilegeCapability.ROLE_CONTROL,
        PrivilegeCapability.APP_OPS,
        PrivilegeCapability.FRAMEWORK_DIAGNOSTICS,
    )

    fun classifyShizukuBackend(uid: Int?, suiAvailable: Boolean): PrivilegeBackendType = when {
        uid == 0 && suiAvailable -> PrivilegeBackendType.SUI_ROOT
        uid == 0 -> PrivilegeBackendType.SHIZUKU_ROOT
        uid == 2000 -> PrivilegeBackendType.SHIZUKU_ADB
        else -> PrivilegeBackendType.NONE
    }

    fun primaryBackend(bridge: ShizukuBridgeState, rootAvailable: Boolean): PrivilegeRouteBackend =
        routesFor(
            PrivilegeCapability.FRAMEWORK_DIAGNOSTICS,
            bridge,
            rootAvailable,
            FrameworkPrivilegePreference.AUTO,
        ).firstOrNull()
            ?: PrivilegeRouteBackend.NONE

    /**
     * Returns routes in execution priority order.
     *
     * Root-only Linux/sysfs/Magisk/adbd capabilities intentionally stay on RootShell even when a
     * root Shizuku/Sui server exists. Framework capabilities prefer Shizuku and may fall back to
     * RootShell when root is available.
     */
    fun routesFor(
        capability: PrivilegeCapability,
        bridge: ShizukuBridgeState,
        rootAvailable: Boolean,
        preference: FrameworkPrivilegePreference = FrameworkPrivilegePreference.AUTO,
    ): List<PrivilegeRouteBackend> {
        val result = mutableListOf<PrivilegeRouteBackend>()
        if (capability !in frameworkCapabilities) {
            if (rootAvailable) result += PrivilegeRouteBackend.ROOT_SHELL
            return result
        }

        val shizukuBackend = bridge.backend.toRouteBackend()
            .takeIf { bridge.ready && it != PrivilegeRouteBackend.NONE }
        when (preference) {
            FrameworkPrivilegePreference.AUTO -> {
                shizukuBackend?.let(result::add)
                if (rootAvailable) result += PrivilegeRouteBackend.ROOT_SHELL
            }
            FrameworkPrivilegePreference.SHIZUKU_ONLY -> {
                shizukuBackend?.let(result::add)
            }
            FrameworkPrivilegePreference.ROOT_FIRST -> {
                if (rootAvailable) result += PrivilegeRouteBackend.ROOT_SHELL
                shizukuBackend?.let(result::add)
            }
        }
        return result.distinct()
    }

    fun supports(backend: PrivilegeRouteBackend, capability: PrivilegeCapability): Boolean = when (backend) {
        PrivilegeRouteBackend.NONE -> false
        PrivilegeRouteBackend.ROOT_SHELL -> true
        PrivilegeRouteBackend.SHIZUKU_ADB,
        PrivilegeRouteBackend.SHIZUKU_ROOT,
        PrivilegeRouteBackend.SUI_ROOT,
        -> capability in frameworkCapabilities
    }

    fun PrivilegeBackendType.toRouteBackend(): PrivilegeRouteBackend = when (this) {
        PrivilegeBackendType.NONE -> PrivilegeRouteBackend.NONE
        PrivilegeBackendType.SHIZUKU_ADB -> PrivilegeRouteBackend.SHIZUKU_ADB
        PrivilegeBackendType.SHIZUKU_ROOT -> PrivilegeRouteBackend.SHIZUKU_ROOT
        PrivilegeBackendType.SUI_ROOT -> PrivilegeRouteBackend.SUI_ROOT
    }
}
