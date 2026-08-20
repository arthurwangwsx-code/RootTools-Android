package com.arthur.roottools.policy

import com.arthur.roottools.model.RuntimePermissionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePermissionMutationPolicyTest {
    private val camera = RuntimePermissionRecord(
        name = "android.permission.CAMERA",
        granted = true,
        protection = "dangerous",
    )

    @Test
    fun dangerousPermission_onNormalPackage_canChange() {
        assertTrue(
            RuntimePermissionMutationPolicy.evaluate(
                "com.example.app",
                camera,
                granted = false,
                protectedPackages = emptySet(),
            ).allowed,
        )
    }

    @Test
    fun nonDangerousPermission_isRejected() {
        val result = RuntimePermissionMutationPolicy.evaluate(
            "com.example.app",
            camera.copy(protection = "signature"),
            granted = true,
            protectedPackages = emptySet(),
        )
        assertFalse(result.allowed)
        assertEquals(RuntimePermissionMutationRejection.NOT_RUNTIME_DANGEROUS, result.rejection)
    }

    @Test
    fun malformedPermission_isRejected() {
        val result = RuntimePermissionMutationPolicy.evaluate(
            "com.example.app",
            camera.copy(name = "android.permission.CAMERA;id"),
            granted = true,
            protectedPackages = emptySet(),
        )
        assertEquals(RuntimePermissionMutationRejection.INVALID_PERMISSION, result.rejection)
    }

    @Test
    fun protectedPackage_cannotRevokeButCanGrant() {
        val protected = setOf("com.example.app")
        val revoke = RuntimePermissionMutationPolicy.evaluate("com.example.app", camera, granted = false, protectedPackages = protected)
        assertFalse(revoke.allowed)
        assertEquals(RuntimePermissionMutationRejection.PROTECTED_PACKAGE_REVOKE, revoke.rejection)
        assertTrue(RuntimePermissionMutationPolicy.evaluate("com.example.app", camera, granted = true, protectedPackages = protected).allowed)
    }
}
