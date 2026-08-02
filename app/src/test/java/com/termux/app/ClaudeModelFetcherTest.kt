package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies model list parsing for the Claude editor fetch. */
class ClaudeModelFetcherTest {

    @Test
    fun `parses anthropic data array`() {
        val models = ClaudeModelFetcher.parseModels(
            """{"data":[{"type":"model","id":"claude-sonnet-4-5","display_name":"Sonnet 4.5"},{"type":"model","id":"claude-opus-4-1"}]}""",
        )
        assertEquals(listOf("claude-opus-4-1", "claude-sonnet-4-5"), models)
    }

    @Test
    fun `parses relay models array with plain ids`() {
        val models = ClaudeModelFetcher.parseModels(
            """{"models":["claude-3-7-sonnet","kimi-k2","deepseek-chat"]}""",
        )
        assertEquals(listOf("claude-3-7-sonnet", "deepseek-chat", "kimi-k2"), models)
    }

    @Test
    fun `empty body yields no models`() {
        assertTrue(ClaudeModelFetcher.parseModels("").isEmpty())
        assertTrue(ClaudeModelFetcher.parseModels("{}").isEmpty())
    }

    @Test
    fun `official fallback list is available offline`() {
        assertTrue(ClaudeModelFetcher.officialModels().contains("claude-sonnet-4-5"))
    }
}
