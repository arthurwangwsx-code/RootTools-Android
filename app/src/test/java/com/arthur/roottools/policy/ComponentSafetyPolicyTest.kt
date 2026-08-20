package com.arthur.roottools.policy

import com.arthur.roottools.model.AppComponentRecord
import com.arthur.roottools.model.ComponentKind
import com.arthur.roottools.model.ComponentSnapshot
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
    fun userAppKnownComponent_isAllowed() {
        val decision = ComponentSafetyPolicy.evaluate(
            snapshot("com.example.app", listOf(receiver)),
            receiver,
            protectedPackages = emptySet(),
        )
        assertTrue(decision.allowed)
    }

    @Test
    fun systemApp_isReadOnly() {
        val decision = ComponentSafetyPolicy.evaluate(
            snapshot("com.example.app", listOf(receiver)).copy(systemApp = true),
            receiver,
            protectedPackages = emptySet(),
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun protectedPackage_isRejected() {
        val protectedComponent = receiver.copy(
            componentName = "com.arthur.roottools/.BootReceiver",
            className = "com.arthur.roottools.BootReceiver",
        )
        val decision = ComponentSafetyPolicy.evaluate(
            snapshot("com.arthur.roottools", listOf(protectedComponent)),
            protectedComponent,
            protectedPackages = setOf("com.arthur.roottools"),
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun crossPackageComponent_isRejected() {
        val foreign = receiver.copy(componentName = "com.other.app/.Receiver")
        val decision = ComponentSafetyPolicy.evaluate(
            snapshot("com.example.app", listOf(foreign)),
            foreign,
            protectedPackages = emptySet(),
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun staleComponent_notInSnapshot_isRejected() {
        val decision = ComponentSafetyPolicy.evaluate(
            snapshot("com.example.app", emptyList()),
            receiver,
            protectedPackages = emptySet(),
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun injectionInComponentName_isRejected() {
        val unsafe = receiver.copy(componentName = "com.example.app/.BootReceiver;reboot")
        val decision = ComponentSafetyPolicy.evaluate(
            snapshot("com.example.app", listOf(unsafe)),
            unsafe,
            protectedPackages = emptySet(),
        )
        assertFalse(decision.allowed)
    }

    private fun snapshot(packageName: String, components: List<AppComponentRecord>) = ComponentSnapshot(
        packageName = packageName,
        label = "Example",
        components = components,
    )
}
