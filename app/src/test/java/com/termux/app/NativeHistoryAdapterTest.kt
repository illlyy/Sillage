package com.termux.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeHistoryAdapterTest {
    @Test
    fun historyParserProducesStableMessageKeysAcrossRepeatedParses() {
        val raw = JSONArray()
            .put(JSONObject().put("role", "user").put("content", "hello"))
            .put(JSONObject().put("role", "assistant").put("content", "before<plan>step</plan>after"))
            .toString()
        val first = NativeHistoryParser.parse(raw).messages.map { it.id }
        val second = NativeHistoryParser.parse(raw).messages.map { it.id }
        assertEquals(first, second)
    }

    @Test
    fun dedicatedPlanReplacesEquivalentTaggedFallbackRegardlessOfOrder() {
        val messages = listOf(
            NativeChatMessage(role = NativeChatRole.ASSISTANT, content = "<plan>same plan</plan>"),
            NativeChatMessage(role = NativeChatRole.ACTIVITY, content = encodeNativeProposedPlan("same plan")),
        )
        val plans = NativeHistoryAdapter.renderModel("thread", messages).plans
        assertEquals(1, plans.size)
        assertTrue(plans.single().dedicated)
    }

    @Test
    fun legacyCommandOutputGetsABoundedPreviewInUnifiedGroup() {
        val payload = JSONObject()
            .put("reasoning", "")
            .put(
                "tools",
                JSONArray().put(
                    JSONObject()
                        .put("id", "command")
                        .put("type", "commandExecution")
                        .put("command", "echo ok")
                        .put("aggregatedOutput", "ok\n"),
                ),
            )
        val group = NativeHistoryAdapter.processGroup("message", "thread", payload)
        assertEquals("ok\n", group.commands.single().outputPreview)
    }

    @Test
    fun legacyCompactionAndJournalRecordShareOneTimelineDivider() {
        val legacy = NativeCompactionItem(
            id = "history-compaction:thread:1000",
            threadId = "thread",
            source = NativeCompactionSource.LEGACY,
            status = NativeCompactionStatus.COMPLETED,
            createdAtMs = 1_000L,
            updatedAtMs = 1_000L,
        )
        val journal = legacy.copy(
            id = "manual-compaction:thread:req",
            serverItemId = "server-item",
            requestId = "req",
            source = NativeCompactionSource.MANUAL,
            updatedAtMs = 1_400L,
        )
        val merged = NativeHistoryAdapter.mergeCompactionTimeline(listOf(legacy, journal))
        assertEquals(1, merged.size)
        assertEquals("server-item", merged.single().serverItemId)
        assertEquals(NativeCompactionSource.MANUAL, merged.single().source)
    }

    @Test
    fun distinctIdentifiedCompactionsRemainSeparate() {
        val first = NativeCompactionItem(
            id = "first", threadId = "thread", serverItemId = "server-1",
            requestId = "request-1", createdAtMs = 1_000L, updatedAtMs = 1_000L,
        )
        val second = first.copy(
            id = "second", serverItemId = "server-2", requestId = "request-2",
            createdAtMs = 1_200L, updatedAtMs = 1_200L,
        )
        assertEquals(2, NativeHistoryAdapter.mergeCompactionTimeline(listOf(first, second)).size)
    }

    @Test
    fun distinctUnidentifiedTerminalCompactionsRemainSeparateInsideLegacyWindow() {
        val first = NativeCompactionItem(
            id = "legacy-first", threadId = "thread", source = NativeCompactionSource.LEGACY,
            status = NativeCompactionStatus.COMPLETED, createdAtMs = 1_000L, updatedAtMs = 1_000L,
        )
        val second = first.copy(
            id = "legacy-second", createdAtMs = 11_000L, updatedAtMs = 11_000L,
        )

        assertEquals(2, NativeHistoryAdapter.mergeCompactionTimeline(listOf(first, second)).size)
    }

    @Test
    fun unidentifiedRunningAndCompletionStillFormOneLegacyLifecycle() {
        val running = NativeCompactionItem(
            id = "legacy-running", threadId = "thread", source = NativeCompactionSource.LEGACY,
            status = NativeCompactionStatus.RUNNING, createdAtMs = 1_000L, updatedAtMs = 1_000L,
        )
        val completed = running.copy(
            id = "legacy-completed", status = NativeCompactionStatus.COMPLETED,
            createdAtMs = 1_500L, updatedAtMs = 1_500L,
        )

        val merged = NativeHistoryAdapter.mergeCompactionTimeline(listOf(running, completed))
        assertEquals(1, merged.size)
        assertEquals(NativeCompactionStatus.COMPLETED, merged.single().status)
    }

    @Test
    fun oldRequestOnlyAndServerOnlyManualLifecycleAreRepairedOnReload() {
        val pending = NativeCompactionItem(
            id = "manual", threadId = "thread", turnId = "turn-before",
            source = NativeCompactionSource.MANUAL, status = NativeCompactionStatus.COMPLETED,
            requestId = "request", createdAtMs = 1_000L, updatedAtMs = 1_900L,
        )
        val server = NativeCompactionItem(
            id = "server", threadId = "thread", turnId = "dedicated-turn",
            source = NativeCompactionSource.AUTOMATIC, status = NativeCompactionStatus.COMPLETED,
            serverItemId = "server-item", createdAtMs = 1_020L, updatedAtMs = 12_000L,
        )
        val merged = NativeHistoryAdapter.mergeCompactionTimeline(listOf(pending, server))
        assertEquals(1, merged.size)
        assertEquals("request", merged.single().requestId)
        assertEquals("server-item", merged.single().serverItemId)
        assertEquals(NativeCompactionSource.MANUAL, merged.single().source)
    }

    @Test
    fun legacyCompletionDoesNotSplitARequestAndServerPairRegardlessOfInputOrder() {
        val requestOnly = NativeCompactionItem(
            id = "manual", threadId = "thread", turnId = "old-turn",
            source = NativeCompactionSource.MANUAL, status = NativeCompactionStatus.COMPLETED,
            requestId = "request", createdAtMs = 1_000L, updatedAtMs = 1_900L,
        )
        val serverOnly = NativeCompactionItem(
            id = "server", threadId = "thread", turnId = "dedicated-turn",
            source = NativeCompactionSource.AUTOMATIC, status = NativeCompactionStatus.COMPLETED,
            serverItemId = "server-item", createdAtMs = 1_020L, updatedAtMs = 12_000L,
        )
        val legacyCompletion = NativeCompactionItem(
            id = "legacy", threadId = "thread", source = NativeCompactionSource.LEGACY,
            status = NativeCompactionStatus.COMPLETED, createdAtMs = 12_010L, updatedAtMs = 12_010L,
        )

        val merged = NativeHistoryAdapter.mergeCompactionTimeline(
            listOf(legacyCompletion, serverOnly, requestOnly),
        )
        assertEquals(1, merged.size)
        assertEquals("request", merged.single().requestId)
        assertEquals("server-item", merged.single().serverItemId)
        assertEquals(NativeCompactionSource.MANUAL, merged.single().source)
    }
}
