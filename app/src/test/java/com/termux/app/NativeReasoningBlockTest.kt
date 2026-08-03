package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeReasoningBlockTest {

    @Test
    fun labelShowsThinkingWhileStreaming() {
        assertEquals("思考中…", reasoningDurationLabel(12, streaming = true, language = "zh"))
        assertEquals("Thinking…", reasoningDurationLabel(12, streaming = true, language = "en"))
    }

    @Test
    fun labelShowsThinkingWhenDurationUnknown() {
        assertEquals("思考中…", reasoningDurationLabel(null, streaming = false, language = "zh"))
        assertEquals("Thinking…", reasoningDurationLabel(null, streaming = false, language = "en"))
    }

    @Test
    fun labelShowsThinkingForZeroOrNegativeDuration() {
        assertEquals("思考中…", reasoningDurationLabel(0, streaming = false, language = "zh"))
        assertEquals("Thinking…", reasoningDurationLabel(-3, streaming = false, language = "en"))
    }

    @Test
    fun labelShowsThoughtDurationInBothLanguages() {
        assertEquals("思考了 42s", reasoningDurationLabel(42, streaming = false, language = "zh"))
        assertEquals("Thought for 42s", reasoningDurationLabel(42, streaming = false, language = "en"))
    }
}
