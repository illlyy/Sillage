package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FcodeDrawerPhysicsTest {
    @Test
    fun slowReleaseSettlesToNearestAnchor() {
        assertEquals(0f, FcodeDrawerPhysics.settleTarget(0.49f, 0f))
        assertEquals(1f, FcodeDrawerPhysics.settleTarget(0.50f, 0f))
    }

    @Test
    fun flingVelocityWinsOverPosition() {
        assertEquals(1f, FcodeDrawerPhysics.settleTarget(0.05f, 600f))
        assertEquals(0f, FcodeDrawerPhysics.settleTarget(0.95f, -600f))
    }
}
