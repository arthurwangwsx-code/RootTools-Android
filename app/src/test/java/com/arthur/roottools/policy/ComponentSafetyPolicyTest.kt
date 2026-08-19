package com.arthur.roottools.policy

import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.ComponentKind
import com.arthur.roottools.model.ComponentSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentSafetyPolicyTest {
    private val receiver = AppComponentRecord(
        componentName = "com.example.app/.BootReceiver",
        className = "com.example.app.BootReceiver",
        kind = ComponentKind.RECEIVER,
        enabled = true,
        exported = false,
        bootReceiver = true,
    )

    @Test
    fun knownUserComponent_isAllowed() {
        val decision = ComponentSafetyPolicy.evaluate(
            snapshot(components = listOf(receiver)),
            receiver,
            protectedPackages = emptySet(),
        )
        assertTrue(decision.allowed)
        assertEquals(receiver.componentName, decision.componentName)
    }

    @Test
    fun systemAndProtectedApps_areReadOnly() {
        assertEquals(
            ComponentMutationRejection.SYSTEM_APP,
            ComponentSafetyPolicy.evaluate(snapshot(systemApp = true, components = listOf(receiver)), receiver, emptySet()).rejection,
        )
        assertEquals(
            ComponentMutationRejection.PROTECTED_PACKAGE,
            ComponentSafetyPolicy.evaluate(snapshot(components = listOf(receiver)), receiver, setOf("com.example.app")).rejection,
        )
    }

    @Test
    fun crossPackageStaleAndHostileComponents_areRejected() {
        val foreign = receiver.copy(componentName = "com.other.app/.Receiver")
        assertFalse(ComponentSafetyPolicy.evaluate(snapshot(components = listOf(foreign)), foreign, emptySet()).allowed)
        assertEquals(
            ComponentMutationRejection.STALE_COMPONENT,
            ComponentSafetyPolicy.evaluate(snapshot(components = emptyList()), receiver, emptySet()).rejection,
        )
        val hostile = receiver.copy(componentName = "com.example.app/.BootReceiver;id")
        assertEquals(
            ComponentMutationRejection.INVALID_COMPONENT,
            ComponentSafetyPolicy.evaluate(snapshot(components = listOf(hostile)), hostile, emptySet()).rejection,
        )
    }

    @Test
    fun protectedLauncherComponent_isRejected() {
        val protectedComponent = receiver.copy(protectedReason = "Launcher activity")
        assertEquals(
            ComponentMutationRejection.PROTECTED_COMPONENT,
            ComponentSafetyPolicy.evaluate(snapshot(components = listOf(protectedComponent)), protectedComponent, emptySet()).rejection,
        )
    }

    private fun snapshot(
        systemApp: Boolean = false,
        components: List<AppComponentRecord>,
    ) = ComponentSnapshot(
        packageName = "com.example.app",
        label = "Example",
        systemApp = systemApp,
        components = components,
    )
}
