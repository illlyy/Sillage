package com.termux.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claude's result line carries the turn's tokens but says nothing about how full the context is, so
 * the bridge folds a separate `get_context_usage` answer into the same payload. These cover that
 * merge, including the cases where the answer is missing pieces.
 */
class ClaudeContextUsageMergeTest {

    private val contextReply = JSONObject(
        """
        {"totalTokens":21249,"maxTokens":786432,"rawMaxTokens":786432,"percentage":3,
         "categories":[
           {"name":"System prompt","tokens":1838},
           {"name":"System tools","tokens":17885},
           {"name":"Messages","tokens":31},
           {"name":"Autocompact buffer","tokens":33000},
           {"name":"Free space","tokens":732183}
         ]}
        """.trimIndent(),
    )

    private fun merged(usage: String = """{"input_tokens":12,"output_tokens":40}""") =
        JSONObject(ClaudeAgentBridge.mergeContextIntoUsage(usage, contextReply))

    @Test
    fun foldsWindowAndCurrentTotalIntoTheTurnUsage() {
        val payload = merged()
        assertEquals(21_249L, payload.optLong("contextTokens"))
        assertEquals(786_432L, payload.optLong("contextWindow"))
        assertTrue(payload.optBoolean("contextUsageReliable"))
        // The turn's own numbers must survive: the host handler replaces rather than merges.
        assertEquals(12L, payload.optLong("input_tokens"))
        assertEquals(40L, payload.optLong("output_tokens"))
    }

    @Test
    fun subtractsTheAutocompactBufferToGetTheThreshold() {
        // 786432 - 33000: the point where the CLI compacts on its own.
        assertEquals(753_432L, merged().optLong("autoCompactTokenLimit"))
    }

    @Test
    fun exposesTheBreakdownTheDialogRenders() {
        val rows = merged().optJSONArray("contextCategories")!!
        assertEquals(5, rows.length())
        assertEquals("System prompt", rows.optJSONObject(0).optString("name"))
        assertEquals(1838L, rows.optJSONObject(0).optLong("tokens"))
    }

    @Test
    fun leavesTheUsageAloneWhenTheProbeFailed() {
        val payload = JSONObject(ClaudeAgentBridge.mergeContextIntoUsage("""{"input_tokens":12}""", null))
        assertEquals(12L, payload.optLong("input_tokens"))
        assertFalse(payload.has("contextWindow"))
        assertFalse(payload.has("contextUsageReliable"))
    }

    @Test
    fun doesNotInventAWindowWhenTheReplyHasNone() {
        // Without a window the UI must hide the metric rather than divide by zero or print a guess.
        val payload = JSONObject(
            ClaudeAgentBridge.mergeContextIntoUsage(
                """{"input_tokens":12}""",
                JSONObject("""{"totalTokens":5000,"categories":[]}"""),
            ),
        )
        assertFalse(payload.has("contextWindow"))
        assertFalse(payload.has("contextUsageReliable"))
        assertFalse(payload.has("autoCompactTokenLimit"))
    }

    @Test
    fun survivesAnInputThatIsNotJson() {
        assertEquals("not json", ClaudeAgentBridge.mergeContextIntoUsage("not json", contextReply))
    }
}
