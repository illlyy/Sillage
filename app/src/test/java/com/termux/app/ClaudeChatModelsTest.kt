package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Claude chat model-option builder (tier aliases, resolved display names, dedupe)
 * and the bridge's `--model` argument construction.
 */
class ClaudeChatModelsTest {

    private fun profile(
        model: String = "claude-sonnet-5",
        haiku: String = "",
        sonnet: String = "",
        opus: String = "",
        fable: String = "",
    ) = ClaudeProfile(
        id = "p1", name = "Test", apiKey = "sk-test", model = model,
        haikuModel = haiku, sonnetModel = sonnet, opusModel = opus, fableModel = fable,
    )

    @Test
    fun `tier options use resolved ids and labeled names`() {
        val options = buildClaudeModelOptions(profile(model = "claude-sonnet-5", opus = "claude-opus-5"))
        val ids = options.map { it.id }
        assertTrue("claude-sonnet-5" in ids)
        assertTrue("claude-opus-5" in ids)
        assertEquals("Opus · claude-opus-5", options.first { it.id == "claude-opus-5" }.name)
        // A blank tier resolves to the primary and collapses into it (no duplicate id).
        assertEquals(2, options.size)
    }

    @Test
    fun `blank tiers all resolve to the primary and dedupe to one option`() {
        val ids = buildClaudeModelOptions(profile(model = "sonnet")).map { it.id }
        assertEquals(listOf("sonnet"), ids)
    }

    @Test
    fun `relay with identical tiers shows a single clean entry`() {
        val options = buildClaudeModelOptions(
            profile(model = "kimi-k2", haiku = "kimi-k2", sonnet = "kimi-k2", opus = "kimi-k2", fable = "kimi-k2"),
        )
        assertEquals(listOf("kimi-k2"), options.map { it.id })
    }

    @Test
    fun `configured tiers resolve to distinct concrete ids`() {
        val options = buildClaudeModelOptions(
            profile(model = "claude-sonnet-5", haiku = "claude-haiku-4-5", opus = "claude-opus-5", fable = "claude-fable-5"),
        )
        assertEquals(
            listOf("claude-sonnet-5", "claude-opus-5", "claude-haiku-4-5", "claude-fable-5"),
            options.map { it.id },
        )
    }

    @Test
    fun `model args helper emits flag only for a differing override`() {
        assertTrue(ClaudeAgentBridge.modelArgs("", "claude-sonnet-5").isEmpty())
        assertTrue(ClaudeAgentBridge.modelArgs(null, "claude-sonnet-5").isEmpty())
        // Same as the profile primary -> no flag (default spawn stays unchanged).
        assertTrue(ClaudeAgentBridge.modelArgs("claude-sonnet-5", "claude-sonnet-5").isEmpty())
        assertEquals(listOf("--model", "claude-opus-5"), ClaudeAgentBridge.modelArgs("claude-opus-5", "claude-sonnet-5"))
    }
}
