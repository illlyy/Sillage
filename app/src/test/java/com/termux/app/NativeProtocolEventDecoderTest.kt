package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProtocolEventDecoderTest {
    @Test
    fun decodesCamelAndSnakeCaseLifecycleFields() {
        val event = NativeProtocolEventDecoder.decodeLifecycle(
            """
            {"kind":"contextCompactionCompleted","thread_id":"thread-a","turn_id":"turn-1","item_id":"compact-1","sequence_number":9,"timestamp_ms":42,"item":{"status":"failed","error":"full"}}
            """.trimIndent(),
        ) as NativeProtocolEvent.CompactionFailed
        assertEquals("thread-a", event.threadId)
        assertEquals("turn-1", event.turnId)
        assertEquals("compact-1", event.itemId)
        assertEquals(9L, event.sequence)
        assertEquals("full", event.error)
    }

    @Test
    fun replaysInterleavedDeltaLinesWithStableIdentity() {
        val events = NativeProtocolEventDecoder.decodeDeltas(
            """
            {"kind":"reasoningDelta","threadId":"t","turnId":"u","itemId":"r","sequence":1,"delta":"think"}
            {"kind":"assistantDelta","thread_id":"t","turn_id":"u","item_id":"a","sequence":2,"delta":"answer"}
            {"kind":"planDelta","threadId":"t","turnId":"u","itemId":"p","sequence":3,"delta":"step"}
            """.trimIndent(),
        )
        assertEquals(listOf("r", "a", "p"), events.map { it.itemId })
        assertTrue(events[0] is NativeProtocolEvent.ReasoningDelta)
        assertTrue(events[1] is NativeProtocolEvent.AssistantDelta)
        assertTrue(events[2] is NativeProtocolEvent.PlanDelta)
    }

    @Test
    fun tokenUsageKeepsServerThresholdAndReliability() {
        val event = NativeProtocolEventDecoder.decodeLifecycle(
            """
            {"kind":"tokenUsageUpdated","threadId":"t","turnId":"u","sequence":4,"item":{"contextWindow":1000,"input_tokens":810,"auto_compact_token_limit":800,"context_tokens":810,"total":{"total_tokens":810}}}
            """.trimIndent(),
        ) as NativeProtocolEvent.TokenUsageUpdated
        assertEquals(1000L, event.contextWindow)
        assertEquals(800L, event.autoCompactTokenLimit)
        assertEquals(810L, event.inputTokens)
        assertTrue(event.contextUsageReliable)
    }

    @Test
    fun snakeCaseKindsAreAcceptedInReplay() {
        val event = NativeProtocolEventDecoder.decodeLifecycle(
            "{\"kind\":\"context_compaction_started\",\"thread_id\":\"t\",\"item_id\":\"c\",\"sequence\":1,\"item\":{\"source\":\"automatic\"}}",
        ) as NativeProtocolEvent.CompactionStarted
        assertEquals(NativeCompactionSource.AUTOMATIC, event.source)
        assertEquals("c", event.itemId)
    }

    @Test
    fun methodOnlyItemLifecycleIsNormalizedDuringReplay() {
        val event = NativeProtocolEventDecoder.decodeLifecycle(
            """{"method":"item/started","thread_id":"t","turn_id":"u","item":{"id":"c","type":"context_compaction"}}""",
        ) as NativeProtocolEvent.CompactionStarted
        assertEquals("c", event.itemId)
        assertEquals("u", event.turnId)
    }

    @Test
    fun legacyContextCompactedKindMapsToCompletedLifecycle() {
        val event = NativeProtocolEventDecoder.decodeLifecycle(
            """{"kind":"contextCompacted","threadId":"t","itemId":"c"}""",
        ) as NativeProtocolEvent.CompactionCompleted
        assertEquals("c", event.itemId)
    }

    @Test
    fun replaysFullAppServerTokenUsageEnvelope() {
        val event = NativeProtocolEventDecoder.decodeLifecycle(
            """{"method":"thread/tokenUsage/updated","params":{"thread_id":"t","turn_id":"u","token_usage":{"last_token_usage":{"input_tokens":12,"output_tokens":3},"total_token_usage":{"total_tokens":91003},"model_context_window":100000,"model_auto_compact_token_limit":90000}}}""",
        ) as NativeProtocolEvent.TokenUsageUpdated
        assertEquals("t", event.threadId)
        assertEquals("u", event.turnId)
        assertEquals(91003L, event.currentContextTokens)
        assertEquals(100000L, event.contextWindow)
        assertEquals(90000L, event.autoCompactTokenLimit)
        assertTrue(event.contextUsageReliable)
    }
}
