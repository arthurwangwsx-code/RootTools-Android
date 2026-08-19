package com.arthur.roottools.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageMutationPolicyTest {
    private val protected = setOf("com.arthur.roottools")

    @Test
    fun normalPackage_allowsIdempotentStateChanges() {
        val pkg = "com.example.app"
        assertTrue(PackageMutationPolicy.evaluate(pkg, PackageMutationKind.FREEZE, protectedPackages = protected).allowed)
        assertTrue(PackageMutationPolicy.evaluate(pkg, PackageMutationKind.ENABLE, protectedPackages = protected).allowed)
        assertTrue(PackageMutationPolicy.evaluate(pkg, PackageMutationKind.FORCE_STOP, protectedPackages = protected).allowed)
        assertTrue(PackageMutationPolicy.evaluate(pkg, PackageMutationKind.SET_STANDBY_BUCKET, 40, protectedPackages = protected).allowed)
        assertTrue(PackageMutationPolicy.evaluate(pkg, PackageMutationKind.SET_BACKGROUND_ALLOWED, backgroundAllowed = false, protectedPackages = protected).allowed)
    }

    @Test
    fun hostilePackage_isRejectedBeforeBackend() {
        val decision = PackageMutationPolicy.evaluate(
            "com.example.app;reboot",
            PackageMutationKind.FREEZE,
            protectedPackages = protected,
        )
        assertFalse(decision.allowed)
        assertEquals(PackageMutationRejection.INVALID_PACKAGE, decision.rejection)
    }

    @Test
    fun invalidStandbyBucket_isRejected() {
        val decision = PackageMutationPolicy.evaluate(
            "com.example.app",
            PackageMutationKind.SET_STANDBY_BUCKET,
            bucket = 99,
            protectedPackages = protected,
        )
        assertFalse(decision.allowed)
        assertEquals(PackageMutationRejection.INVALID_BUCKET, decision.rejection)
    }

    @Test
    fun protectedPackage_cannotBeFrozenStoppedOrRestricted() {
        assertFalse(PackageMutationPolicy.evaluate("com.arthur.roottools", PackageMutationKind.FREEZE, protectedPackages = protected).allowed)
        assertFalse(PackageMutationPolicy.evaluate("com.arthur.roottools", PackageMutationKind.FORCE_STOP, protectedPackages = protected).allowed)
        assertFalse(PackageMutationPolicy.evaluate("com.arthur.roottools", PackageMutationKind.SET_STANDBY_BUCKET, 40, protectedPackages = protected).allowed)
        assertFalse(PackageMutationPolicy.evaluate("com.arthur.roottools", PackageMutationKind.SET_BACKGROUND_ALLOWED, backgroundAllowed = false, protectedPackages = protected).allowed)
        assertTrue(PackageMutationPolicy.evaluate("com.arthur.roottools", PackageMutationKind.ENABLE, protectedPackages = protected).allowed)
        assertTrue(PackageMutationPolicy.evaluate("com.arthur.roottools", PackageMutationKind.SET_STANDBY_BUCKET, 10, protectedPackages = protected).allowed)
    }
}
