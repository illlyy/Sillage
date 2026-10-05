package com.termux.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers which requests the loopback proxy answers locally as the Claude CLI's startup probe.
 *
 * A miss here is not cosmetic: [ClaudeAgentBridge] uses the observed probe as the readiness signal
 * for a proxy-routed CLI, and readiness gates the composer (`RikkaChatInput(enabled = state.ready)`
 * plus the early returns in `NativeChatTurnActions.sendTurn`). Because the CLI also only echoes its
 * session id with the first user message, a probe that is never recognized deadlocks the whole
 * backend — the input stays disabled and nothing can ever be sent. The matcher therefore accepts
 * every spelling observed so far instead of the narrowest one.
 */
class LocalApiProxyHealthProbeTest {

    @Test
    fun matchesTheDocumentedProbePath() {
        assertTrue(LocalApiProxy.isHealthProbePath("/api/hello"))
    }

    @Test
    fun matchesWhenTheBaseUrlCarriesAPathPrefix() {
        assertTrue(LocalApiProxy.isHealthProbePath("/anthropic/api/hello"))
    }

    @Test
    fun toleratesTrailingSlashes() {
        assertTrue(LocalApiProxy.isHealthProbePath("/api/hello/"))
        assertTrue(LocalApiProxy.isHealthProbePath("/api/hello//"))
        assertTrue(LocalApiProxy.isHealthProbePath("/anthropic/api/hello/"))
    }

    @Test
    fun doesNotMatchRealApiTraffic() {
        assertFalse(LocalApiProxy.isHealthProbePath("/v1/messages"))
        assertFalse(LocalApiProxy.isHealthProbePath("/v1/messages/count_tokens"))
        assertFalse(LocalApiProxy.isHealthProbePath("/v1/responses"))
        assertFalse(LocalApiProxy.isHealthProbePath("/hello"))
        assertFalse(LocalApiProxy.isHealthProbePath("/api/helloworld"))
    }

    @Test
    fun rejectsMissingPaths() {
        assertFalse(LocalApiProxy.isHealthProbePath(null))
        assertFalse(LocalApiProxy.isHealthProbePath(""))
        assertFalse(LocalApiProxy.isHealthProbePath("/"))
    }
}
