package com.termux.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTurnStabilityTest {
    @Test
    fun silentLocalTurnIsAborted() {
        assertTrue(
            shouldAbortSilentNativeTurn(
                busy = true,
                sameThread = true,
                sameTurnStart = true,
                producedLiveActivity = false,
            ),
        )
    }

    @Test
    fun anyLiveActivityKeepsTheTurnAlive() {
        assertFalse(
            shouldAbortSilentNativeTurn(
                busy = true,
                sameThread = true,
                sameTurnStart = true,
                producedLiveActivity = true,
            ),
        )
    }

    @Test
    fun staleWatchdogDoesNotTouchAnotherRouteOrTurn() {
        assertFalse(shouldAbortSilentNativeTurn(true, false, true, false))
        assertFalse(shouldAbortSilentNativeTurn(true, true, false, false))
        assertFalse(shouldAbortSilentNativeTurn(false, true, true, false))
    }
}
