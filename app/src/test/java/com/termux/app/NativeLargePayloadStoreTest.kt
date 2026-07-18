package com.termux.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class NativeLargePayloadStoreTest {
    @Before
    fun setUp() = NativeLargePayloadStore.clear()

    @After
    fun tearDown() = NativeLargePayloadStore.clear()

    @Test
    fun mcpOutputAndArgumentsStayOutsideCompactUiJson() {
        val output = "result-line\n".repeat(90_000)
        val arguments = JSONObject().put("document", "argument".repeat(40_000))
        val compact = JSONObject(NativeLargePayloadStore.compactToolItem(
            JSONObject()
                .put("id", "mcp-1")
                .put("type", "mcpToolCall")
                .put("tool", "read_resource")
                .put("arguments", arguments)
                .put("output", output)
                .put("status", "completed"),
        ))

        val reference = compact.getString(NativeLargePayloadStore.PAYLOAD_REF)
        assertFalse(compact.has("arguments"))
        assertFalse(compact.has("output"))
        assertTrue(compact.toString().length < 2_500)
        assertTrue(NativeLargePayloadStore.get(reference)!!.contains(output.take(1_000)))
    }

    @Test
    fun diffAndWebSearchUsePayloadReferences() {
        val diff = ("+added line\n-removed line\n").repeat(50_000)
        val compactDiff = JSONObject(NativeLargePayloadStore.compactToolItem(
            JSONObject().put("id", "diff-1").put("type", "fileChange").put("changes", diff),
        ))
        assertFalse(compactDiff.has("changes"))
        assertEquals(diff, NativeLargePayloadStore.get(compactDiff.getString(NativeLargePayloadStore.PAYLOAD_REF)))

        val results = "https://example.com/result\n".repeat(50_000)
        val compactSearch = JSONObject(NativeLargePayloadStore.compactToolItem(
            JSONObject().put("id", "search-1").put("type", "webSearch")
                .put("query", "android compose text performance").put("output", results),
        ))
        assertEquals("android compose text performance", compactSearch.getString("query"))
        assertFalse(compactSearch.has("output"))
        assertTrue(NativeLargePayloadStore.get(compactSearch.getString(NativeLargePayloadStore.PAYLOAD_REF))!!.endsWith(results))
    }

    @Test
    fun subagentHistoryCrossesMainThreadAsReferenceAndLoadsBoundedPages() {
        val messages = JSONArray()
        for (index in 0 until 150) {
            messages.put(JSONObject().put("id", "message-$index").put("role", "assistant").put("content", "answer-$index"))
        }
        val compact = JSONObject(NativeLargePayloadStore.compactSubagentHistoryResult(
            JSONObject().put("threadId", "agent-thread").put("generation", 7).put("messages", messages).put("status", "done"),
        ))

        assertFalse(compact.has("messages"))
        assertEquals(150, compact.getInt(NativeLargePayloadStore.MESSAGE_COUNT))
        assertTrue(compact.toString().length < 1_000)
        val reference = compact.getString(NativeLargePayloadStore.PAYLOAD_REF)
        val page = NativeLargePayloadStore.subagentPage(reference, Int.MAX_VALUE, 40)
        assertEquals(110, page.startIndex)
        assertEquals(150, page.totalCount)
        assertEquals(40, page.messages.size)
        assertEquals("message-110", JSONObject(page.messages.first()).getString("id"))
    }

    @Test
    fun repeatedSubagentSnapshotReusesReferenceUntilContentChanges() {
        fun payload(content: String) = JSONObject().put("threadId", "stable-agent").put("messages", JSONArray()
            .put(JSONObject().put("role", "assistant").put("content", content)))

        val first = JSONObject(NativeLargePayloadStore.compactSubagentHistoryResult(payload("same")))
        val second = JSONObject(NativeLargePayloadStore.compactSubagentHistoryResult(payload("same")))
        val third = JSONObject(NativeLargePayloadStore.compactSubagentHistoryResult(payload("changed")))

        assertEquals(first.getString(NativeLargePayloadStore.PAYLOAD_REF), second.getString(NativeLargePayloadStore.PAYLOAD_REF))
        assertNotEquals(second.getString(NativeLargePayloadStore.PAYLOAD_REF), third.getString(NativeLargePayloadStore.PAYLOAD_REF))
        assertEquals(1, NativeLargePayloadStore.cachedEntryCount())
    }

    @Test
    fun nestedSubagentActivityCompactsToolPayloadBeforePaging() {
        val toolOutput = "tool-output\n".repeat(20_000)
        val process = JSONObject().put("duration", 1).put("reasoning", "").put("tools", JSONArray().put(
            JSONObject().put("id", "nested-mcp").put("type", "mcpToolCall").put("tool", "resource")
                .put("arguments", JSONObject().put("uri", "resource://large")).put("output", toolOutput),
        ))
        val encoded = Base64.getEncoder().encodeToString(process.toString().toByteArray())
        val messages = JSONArray().put(JSONObject().put("role", "activity").put("content", "PROCESS2|$encoded"))
        val compact = JSONObject(NativeLargePayloadStore.compactSubagentHistoryResult(
            JSONObject().put("threadId", "nested-agent").put("messages", messages),
        ))

        val page = NativeLargePayloadStore.subagentPage(compact.getString(NativeLargePayloadStore.PAYLOAD_REF), Int.MAX_VALUE, 10)
        val compactActivity = JSONObject(page.messages.single()).getString("content")
        val decoded = JSONObject(String(Base64.getDecoder().decode(compactActivity.substringAfter('|'))))
        val compactTool = decoded.getJSONArray("tools").getJSONObject(0)
        assertFalse(compactTool.has("output"))
        val toolReference = compactTool.getString(NativeLargePayloadStore.PAYLOAD_REF)
        assertNotNull(NativeLargePayloadStore.get(toolReference))
        assertTrue(NativeLargePayloadStore.get(toolReference)!!.contains(toolOutput.take(1_000)))
    }

    @Test
    fun cacheRemainsWithinSharedMemoryBudget() {
        repeat(20) { index ->
            NativeLargePayloadStore.compactToolItem(
                JSONObject().put("id", "large-$index").put("type", "fileChange")
                    .put("changes", "x".repeat(1024 * 1024)),
            )
        }
        assertTrue(NativeLargePayloadStore.cachedCharacterCount() <= 16 * 1024 * 1024)
        assertTrue(NativeLargePayloadStore.cachedEntryCount() <= 16)
    }
}
