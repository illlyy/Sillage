package com.termux.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the unexpected-exit reporting policy. A deliberate stop, a turn already closed by its
 * result, or an error already surfaced must stay silent; a startup failure and an idle exit report
 * without inventing a completion; only a live turn cut short gets a synthetic completion so the
 * partial answer seals without re-entering.
 */
class ClaudeAgentBridgeExitTest {

    private fun report(
        wasRunning: Boolean = true,
        stopRequested: Boolean = false,
        ready: Boolean = true,
        turnResultSeen: Boolean = false,
        errorAlreadyShown: Boolean = false,
        turnInProgress: Boolean = true,
    ): ClaudeAgentBridge.ExitReport? = ClaudeAgentBridge.decideExitReport(
        wasRunning, stopRequested, ready, turnResultSeen, errorAlreadyShown, turnInProgress,
    )

    @Test
    fun `deliberate stop stays silent`() {
        assertNull(report(stopRequested = true))
    }

    @Test
    fun `reader not running stays silent`() {
        assertNull(report(wasRunning = false))
    }

    @Test
    fun `turn already closed by result stays silent`() {
        assertNull(report(turnResultSeen = true))
    }

    @Test
    fun `error already surfaced stays silent`() {
        assertNull(report(errorAlreadyShown = true))
    }

    @Test
    fun `startup failure reports without synthetic completion`() {
        val result = report(ready = false)
        assertTrue(result!!.reportError)
        assertFalse(result.syntheticCompletion)
    }

    @Test
    fun `idle exit reports without synthetic completion`() {
        val result = report(turnInProgress = false)
        assertTrue(result!!.reportError)
        assertFalse(result.syntheticCompletion)
    }

    @Test
    fun `live turn cut short reports and synthesizes completion`() {
        val result = report()
        assertTrue(result!!.reportError)
        assertTrue(result.syntheticCompletion)
    }
}
