package com.arthur.roottools.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuCapOwnershipDeciderTest {
    @Test
    fun addsStricterCapAndOwnsWrite() {
        val decision = CpuCapOwnershipDecider.decide(
            currentKHz = 2_995_200,
            desiredKHz = 2_515_200,
            hardwareMaxKHz = 2_995_200,
            thermalStatus = 0,
            ownedKHz = 0,
        )
        assertEquals(2_515_200L, decision.writeTargetKHz)
        assertFalse(decision.clearPreviousOwnership)
    }

    @Test
    fun doesNotRaiseVendorCapWithoutOwnership() {
        val decision = CpuCapOwnershipDecider.decide(
            currentKHz = 1_843_200,
            desiredKHz = 2_995_200,
            hardwareMaxKHz = 2_995_200,
            thermalStatus = 0,
            ownedKHz = 0,
        )
        assertNull(decision.writeTargetKHz)
    }

    @Test
    fun doesNotRaiseWhileThermalIsActiveEvenIfWeOwnCap() {
        val decision = CpuCapOwnershipDecider.decide(
            currentKHz = 2_284_800,
            desiredKHz = 2_995_200,
            hardwareMaxKHz = 2_995_200,
            thermalStatus = 1,
            ownedKHz = 2_284_800,
        )
        assertNull(decision.writeTargetKHz)
        assertFalse(decision.clearPreviousOwnership)
    }

    @Test
    fun restoresOnlyExactOwnedCapWhenThermalClears() {
        val decision = CpuCapOwnershipDecider.decide(
            currentKHz = 2_284_800,
            desiredKHz = 2_995_200,
            hardwareMaxKHz = 2_995_200,
            thermalStatus = 0,
            ownedKHz = 2_284_800,
        )
        assertEquals(2_995_200L, decision.writeTargetKHz)
    }

    @Test
    fun preservesOwnershipWhenVendorIsEvenStricter() {
        val decision = CpuCapOwnershipDecider.decide(
            currentKHz = 1_843_200,
            desiredKHz = 2_995_200,
            hardwareMaxKHz = 2_995_200,
            thermalStatus = 1,
            ownedKHz = 2_284_800,
        )
        assertNull(decision.writeTargetKHz)
        assertFalse(decision.clearPreviousOwnership)
    }

    @Test
    fun clearsOwnershipIfAnotherOwnerLiftsOurCap() {
        val decision = CpuCapOwnershipDecider.decide(
            currentKHz = 2_995_200,
            desiredKHz = 2_995_200,
            hardwareMaxKHz = 2_995_200,
            thermalStatus = 0,
            ownedKHz = 2_284_800,
        )
        assertNull(decision.writeTargetKHz)
        assertTrue(decision.clearPreviousOwnership)
    }
}
