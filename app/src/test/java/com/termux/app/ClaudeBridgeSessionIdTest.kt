package com.termux.app

import org.junit.Assert.assertEquals
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
}
