package com.termux.app

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInteractionProtocolTest {
    @Test
    fun encodesAllUserInputAnswers() {
        val payload = JSONObject(
            encodeNativeUserInputAnswers(
                linkedMapOf(
                    "scope" to "Current module",
                    "tests" to "Run unit and instrumentation tests",
                ),
            ),
        )

        assertEquals("Current module", payload.getJSONObject("scope").getJSONArray("answers").getString(0))
        assertEquals(
            "Run unit and instrumentation tests",
            payload.getJSONObject("tests").getJSONArray("answers").getString(0),
        )
    }

    @Test
    fun implementPlanDisplayPayloadRoundTripsMarkdown() {
        val plan = "# Plan\n\n1. Inspect\n2. Implement"

        assertEquals(plan, decodeNativeImplementPlan(encodeNativeImplementPlan(plan)))
    }

    @Test
    fun persistedImplementPlanPromptBecomesSyntheticDisplayMessage() {
        val plan = "## Steps\n\n- Add tests\n- Implement"
        val normalized = CodexAppServerBridge.normalizeHistoricalUserText(
            "${CodexAppServerBridge.IMPLEMENT_PLAN_PROMPT_PREFIX}\n$plan",
        )

        assertTrue(normalized.startsWith(NATIVE_IMPLEMENT_PLAN_DISPLAY_PREFIX))
        val decoded = String(
            Base64.getDecoder().decode(normalized.substringAfter('|')),
            StandardCharsets.UTF_8,
        )
        assertEquals(plan, decoded)
    }
}
