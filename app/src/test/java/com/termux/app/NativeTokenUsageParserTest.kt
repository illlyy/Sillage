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
}
