package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTokenUsageParserTest {
    @Test
    fun parsesAppServerLastUsageShape() {
        val usage = NativeTokenUsageParser.parse(
            """{"last":{"inputTokens":120,"cachedInputTokens":40,"outputTokens":60,"reasoningOutputTokens":10,"totalTokens":190},"modelContextWindow":200000}""",
            durationMs = 2_000,
        )!!
        assertEquals(120, usage.inputTokens)
        assertEquals(40, usage.cachedInputTokens)
        assertEquals(60, usage.outputTokens)
        assertEquals(190, usage.totalTokens)
        assertEquals(30.0, usage.outputTokensPerSecond, 0.001)
        assertFalse(usage.estimated)
    }

    @Test
    fun supportsResponsesAndChatUsageNamesAndFallbackEstimate() {
        val actual = NativeTokenUsageParser.parse("""{"usage":{"prompt_tokens":7,"completion_tokens":5,"total_tokens":12}}""")!!
        assertEquals(12, actual.totalTokens)
        val estimate = NativeTokenUsageParser.estimate("a".repeat(40), 1_000)
        assertEquals(10, estimate.outputTokens)
        assertTrue(estimate.estimated)
    }

    @Test
    fun preservesSnakeCaseContextAndCurrentUsageWithoutInputBreakdown() {
        val usage = NativeTokenUsageParser.parse(
            """{"context_window":100000,"context_tokens":91000,"auto_compact_token_limit":90000}""",
        )!!
        assertEquals(100000L, usage.contextWindow)
        assertEquals(91000L, usage.currentContextTokens)
        assertEquals(90000L, usage.autoCompactTokenLimit)
        assertTrue(usage.contextUsageReliable)
    }

    @Test
    fun explicitUnreliableFlagWinsOverCumulativeShape() {
        val usage = NativeTokenUsageParser.parse(
            """{"contextWindow":100000,"contextUsageReliable":false,"total":{"totalTokens":95000}}""",
        )!!
        assertEquals(95000L, usage.currentContextTokens)
        assertFalse(usage.contextUsageReliable)
    }

    @Test
    fun parsesSnakeCaseLastAndCumulativeUsageShapes() {
        val usage = NativeTokenUsageParser.parse(
            """{"last_usage":{"input_tokens":10},"cumulative_usage":{"total_tokens":920},"context_window":1000}""",
        )!!
        assertEquals(10L, usage.inputTokens)
        assertEquals(920L, usage.currentContextTokens)
        assertTrue(usage.contextUsageReliable)
    }

    @Test
    fun parsesCodexModelAutoCompactAndTotalTokenUsageAliases() {
        val usage = NativeTokenUsageParser.parse(
            """{"last_token_usage":{"input_tokens":12,"output_tokens":3},"total_token_usage":{"input_tokens":91000,"total_tokens":91003},"model_context_window":100000,"model_auto_compact_token_limit":90000}""",
        )!!
        assertEquals(12L, usage.inputTokens)
        assertEquals(91003L, usage.currentContextTokens)
        assertEquals(100000L, usage.contextWindow)
        assertEquals(90000L, usage.autoCompactTokenLimit)
        assertTrue(usage.contextUsageReliable)
    }
}
