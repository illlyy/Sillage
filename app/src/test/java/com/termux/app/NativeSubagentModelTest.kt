package com.termux.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSubagentModelTest {
    @Test
    fun threadIdUsesDirectValueBeforeReceiverFallback() {
        val direct = JSONObject()
            .put("agentThreadId", "direct")
            .put("receiverThreadIds", JSONArray().put("fallback"))
        val fallback = JSONObject().put("receiverThreadIds", JSONArray().put(JSONObject.NULL).put("receiver"))

        assertEquals("direct", subagentThreadId(direct))
        assertEquals("receiver", subagentThreadId(fallback))
    }

    @Test
    fun historicalAndLiveItemsMergeByCallOrThreadAndLiveStateWins() {
        val historicalSpawn = JSONObject()
            .put("type", "collabAgentToolCall")
            .put("tool", "spawn_agent")
            .put("callId", "call-1")
            .put("status", "queued")
            .put("agentName", "researcher")
        val historicalThread = JSONObject()
            .put("type", "subAgentActivity")
            .put("agentThreadId", "thread-2")
            .put("status", "working")
        val ignoredNoThread = JSONObject()
            .put("type", "collabAgentToolCall")
            .put("tool", "send_input")
            .put("callId", "call-ignored")
        val ignoredStringEntry = JSONObject()
            .put("type", "subAgentActivity")
            .put("agentThreadId", "string-history")
        val history = processMessage(
            JSONArray()
                .put(historicalSpawn)
                .put(historicalThread)
                .put(ignoredNoThread)
                .put(ignoredStringEntry.toString()),
        )
        val liveSpawn = JSONObject()
            .put("type", "subAgentActivity")
            .put("callId", "call-1")
            .put("agentThreadId", "thread-1")
            .put("status", "running")
        val liveThread = JSONObject()
            .put("type", "subAgentActivity")
            .put("agentThreadId", "thread-2")
            .put("status", "completed")

        val result = collectAllSubagentItems(
            messages = listOf(history),
            liveSubagents = listOf(liveSpawn.toString(), liveThread.toString()),
        ).associateBy(::subagentThreadId)

        assertEquals(setOf("thread-1", "thread-2"), result.keys)
        assertEquals("running", jsonText(result.getValue("thread-1"), "status"))
        assertEquals("researcher", subagentName(result.getValue("thread-1")))
        assertEquals("completed", jsonText(result.getValue("thread-2"), "status"))
        assertFalse(result.containsKey("string-history"))
    }

    @Test
    fun currentDrawerItemMergesIntoMatchingCollectedAgent() {
        val live = JSONObject()
            .put("type", "subAgentActivity")
            .put("agentThreadId", "thread")
            .put("status", "working")
            .put("output", "old")
        val current = JSONObject()
            .put("type", "subAgentActivity")
            .put("agentThreadId", "thread")
            .put("status", "done")
            .put("output", "new")

        val result = collectSubagentItems(emptyList(), listOf(live.toString()), current)

        assertEquals(1, result.size)
        assertEquals("done", jsonText(result.single(), "status"))
        assertEquals("new", jsonText(result.single(), "output"))
    }

    @Test
    fun candidateAndStatusNormalizationPreserveProtocolRules() {
        assertTrue(isSubagentCandidate(JSONObject().put("type", "collabAgentToolCall").put("tool", "spawn_agent")))
        assertTrue(isSubagentCandidate(JSONObject().put("type", "subAgentActivity").put("agentThreadId", "thread")))
        assertFalse(isSubagentCandidate(JSONObject().put("type", "collabAgentToolCall").put("tool", "send_input")))
        assertFalse(isSubagentCandidate(JSONObject().put("type", "commandExecution").put("agentThreadId", "thread")))

        val cases = mapOf(
            "running" to "working",
            "in_progress" to "working",
            "queued" to "waiting",
            "error" to "failed",
            "cancelled" to "stopped",
            "success" to "done",
            "unknown" to "waiting",
        )
        cases.forEach { (raw, expected) -> assertEquals(expected, normalizedSubagentStatus(raw)) }
    }

    private fun processMessage(tools: JSONArray): NativeChatMessage {
        val payload = JSONObject().put("tools", tools).toString().toByteArray(Charsets.UTF_8)
        return NativeChatMessage(
            id = "history",
            role = NativeChatRole.ACTIVITY,
            content = "PROCESS2|${NativeBase64.encode(payload)}",
        )
    }
}
