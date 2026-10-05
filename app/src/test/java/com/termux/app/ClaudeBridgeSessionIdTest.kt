package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the thread-id decision for a fresh Claude session. The bridge adopts this id before
 * the CLI echoes its authoritative session id (which, for a fresh stream-json session, only
 * arrives with the first user-message line), so goals/history route correctly from the start.
 */
class ClaudeBridgeSessionIdTest {

    @Test
    fun resumeIdWinsWhenResuming() {
        assertEquals("019fc2a1-dead-beef", ClaudeAgentBridge.chooseSessionId("019fc2a1-dead-beef", "fresh-uuid"))
    }

    @Test
    fun freshUuidUsedWhenNotResuming() {
        assertEquals("fresh-uuid", ClaudeAgentBridge.chooseSessionId("", "fresh-uuid"))
    }

    @Test
    fun nullResumeIdFallsBackToFreshUuid() {
        assertEquals("fresh-uuid", ClaudeAgentBridge.chooseSessionId(null, "fresh-uuid"))
    }

    @Test
    fun blankResumeIdFallsBackToFreshUuid() {
        assertEquals("fresh-uuid", ClaudeAgentBridge.chooseSessionId("   ", "fresh-uuid"))
    }

    /**
     * The CLI only echoes its authoritative `session_id` with the first user message of a spawn,
     * so at send time the bridge still only knows the provisional id. Keying goal state off the
     * authoritative id alone made the very first turn after (re)attaching a conversation drop the
     * goal prefix — i.e. a user who set a goal and immediately sent a message got no goal at all.
     */
    @Test
    fun goalUsesAuthoritativeSessionIdOnceTheCliEchoesIt() {
        assertEquals("echoed-id", ClaudeAgentBridge.goalThreadId("echoed-id", "provisional-id"))
    }

    @Test
    fun goalFallsBackToProvisionalIdBeforeTheCliEchoesOne() {
        assertEquals("provisional-id", ClaudeAgentBridge.goalThreadId(null, "provisional-id"))
        assertEquals("provisional-id", ClaudeAgentBridge.goalThreadId("", "provisional-id"))
    }

    @Test
    fun goalIsEmptyWhenNeitherIdIsKnown() {
        assertEquals("", ClaudeAgentBridge.goalThreadId(null, null))
        assertEquals("", ClaudeAgentBridge.goalThreadId("", ""))
    }

    /**
     * Readiness must never depend on the CLI's undocumented health probe alone. When routed
     * through the loopback proxy the probe used to be the *only* readiness signal, and readiness
     * gates the composer — so a CLI that never sent the probe left the input permanently disabled
     * and the backend unusable, with no error logged anywhere (F-CLAUDE-33).
     */
    @Test
    fun readinessAlwaysHasABootGrace() {
        assertTrue(ClaudeAgentBridge.readyFallbackDelayMs(true) > 0L)
        assertTrue(ClaudeAgentBridge.readyFallbackDelayMs(false) > 0L)
    }

    /** The probe is the precise signal, so the proxy path waits longer before assuming ready. */
    @Test
    fun proxyPathWaitsLongerForTheProbeThanTheDirectPath() {
        assertTrue(
            ClaudeAgentBridge.readyFallbackDelayMs(true) > ClaudeAgentBridge.readyFallbackDelayMs(false)
        )
    }
}
