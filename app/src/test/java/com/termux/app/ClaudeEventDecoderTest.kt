package com.termux.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies Claude stream-json lines decode into the codex-compatible event namespace. */
class ClaudeEventDecoderTest {

    private fun decoder(events: MutableList<Pair<String, String>>): ClaudeEventDecoder =
        ClaudeEventDecoder(
            currentThread = { "thread-1" },
            emit = { function, value -> events.add(function to value) },
        )

    private fun line(json: String): JSONObject = JSONObject(json)

    private fun streamEvent(index: Int, block: String, extra: String = ""): JSONObject = line(
        """{"type":"stream_event","event":{"type":"content_block_start","index":$index,"content_block":$block$extra}}""",
    )

    private fun deltaEvent(index: Int, deltaType: String, text: String): JSONObject {
        val field = when (deltaType) {
            "text_delta" -> "text"
            "thinking_delta" -> "thinking"
            else -> deltaType
        }
        return line(
            """{"type":"stream_event","event":{"type":"content_block_delta","index":$index,"delta":{"type":"$deltaType","$field":"$text"}}}""",
        )
    }

    @Test
    fun `text delta maps to onDelta`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(deltaEvent(0, "text_delta", "Hello"), "s1")
        decoder.decode(deltaEvent(0, "text_delta", " world"), "s1")
        assertEquals(listOf("onDelta" to "Hello", "onDelta" to " world"), events)
    }

    @Test
    fun `thinking delta maps to onReasoningDelta`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(line("""{"type":"stream_event","event":{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"x"}}}"""), "s1")
        decoder.decode(deltaEvent(0, "thinking_delta", "Let me think"), "s1")
        assertTrue(events.any { it.first == "onReasoningDelta" && it.second == "Let me think" })
    }

    @Test
    fun `bash tool start and result map to command events`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        val toolUseId = "toolu_01"
        // start + streamed input + stop
        decoder.decode(
            line("""{"type":"stream_event","event":{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"$toolUseId","name":"Bash"}}}"""),
            "s1",
        )
        decoder.decode(
            line("""{"type":"stream_event","event":{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"command\":\"ls -la\"}"}}}"""),
            "s1",
        )
        decoder.decode(line("""{"type":"stream_event","event":{"type":"content_block_stop","index":1}}"""), "s1")
        val started = events.first { it.first == "onCommandStarted" }
        val startedPayload = JSONObject(started.second)
        assertEquals("ls -la", startedPayload.optString("command"))
        assertEquals("inProgress", startedPayload.optString("status"))
        // tool result
        decoder.decode(
            line("""{"type":"user","session_id":"s1","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"$toolUseId","content":"total 4","is_error":false}]}}"""),
            "s1",
        )
        val completed = events.first { it.first == "onCommandComplete" }
        val completedPayload = JSONObject(completed.second)
        assertEquals("completed", completedPayload.optString("status"))
        assertEquals("total 4", completedPayload.optString("output"))
    }

    @Test
    fun `ask user question maps to onUserInputRequest`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        val toolUseId = "toolu_ask"
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"$toolUseId","name":"AskUserQuestion","input":{"questions":[{"id":"q1","question":"Choose a language","options":["Kotlin","Java"]}]}}]}}"""),
            "s1",
        )
        val request = events.first { it.first == "onUserInputRequest" }
        val payload = JSONObject(request.second).optJSONObject("params")
        assertEquals(toolUseId, payload.optString("toolUseId"))
        assertEquals("Choose a language", payload.optString("prompt"))
        assertEquals(1, payload.optJSONArray("questions").length())
    }

    @Test
    fun `exit plan mode maps to plan events`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        val toolUseId = "toolu_plan"
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"$toolUseId","name":"ExitPlanMode","input":{"plan":"1. Step one\n2. Step two"}}]}}"""),
            "s1",
        )
        assertTrue(events.any { it.first == "onPlanStarted" })
        val delta = events.first { it.first == "onPlanDelta" }
        val deltaPayload = JSONObject(delta.second)
        assertEquals("1. Step one\n2. Step two", deltaPayload.optString("delta"))
        assertTrue(events.any { it.first == "onPlanComplete" })
    }

    @Test
    fun `blank exit plan mode emits nothing`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_empty","name":"ExitPlanMode","input":{}}]}}"""),
            "s1",
        )
        assertTrue(events.none { it.first == "onPlanStarted" || it.first == "onPlanDelta" || it.first == "onPlanComplete" })
    }

    @Test
    fun `result maps to onTurnComplete with failure flag`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"result","session_id":"s1","subtype":"success","is_error":false,"result":"done"}"""),
            "s1",
        )
        val complete = events.first { it.first == "onTurnComplete" }
        val payload = JSONObject(complete.second)
        assertEquals("thread-1", payload.optString("threadId"))
        assertEquals(false, payload.optBoolean("failed"))
        // failure path
        events.clear()
        decoder.decode(
            line("""{"type":"result","session_id":"s1","subtype":"error_during_execution","is_error":true,"result":"boom"}"""),
            "s1",
        )
        val failed = events.first { it.first == "onTurnComplete" }
        assertEquals(true, JSONObject(failed.second).optBoolean("failed"))
        assertTrue(events.any { it.first == "onNativeError" && it.second == "boom" })
    }

    @Test
    fun `non-bash tool maps to tool detail events`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        val toolUseId = "toolu_grep"
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"$toolUseId","name":"Grep","input":{"pattern":"fun"}}]}}"""),
            "s1",
        )
        val detail = events.first { it.first == "onToolComplete" }
        val payload = JSONObject(detail.second)
        assertEquals(toolUseId, payload.optString("id"))
        assertEquals("Grep", payload.optString("tool"))
        assertEquals("inProgress", payload.optString("status"))
        // result completes it
        decoder.decode(
            line("""{"type":"user","session_id":"s1","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"$toolUseId","content":"fun main()","is_error":false}]}}"""),
            "s1",
        )
        val completed = events.last { it.first == "onToolComplete" }
        assertEquals("completed", JSONObject(completed.second).optString("status"))
        assertEquals("fun main()", JSONObject(completed.second).optString("payload"))
    }

    @Test
    fun `permission denied maps to turn error`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"system","subtype":"permission_denied","session_id":"s1","tool_name":"Bash","message":"denied by policy"}"""),
            "s1",
        )
        val error = events.first { it.first == "onTurnError" }
        assertEquals("denied by policy", JSONObject(error.second).optString("message"))
    }

    @Test
    fun `system thinking object form maps to onReasoningDelta and seals on result`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"system","subtype":"thinking","session_id":"s1","thinking":{"kind":"thinking_block","thinking":"Let me think"}}"""),
            "s1",
        )
        assertTrue(events.any { it.first == "onReasoningDelta" && it.second == "Let me think" })
        events.clear()
        decoder.decode(
            line("""{"type":"result","session_id":"s1","subtype":"success","is_error":false,"result":"done"}"""),
            "s1",
        )
        assertTrue(events.any { it.first == "onReasoningComplete" && it.second == "Let me think" })
    }

    @Test
    fun `system thinking string form maps to onReasoningDelta`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"system","subtype":"thinking","session_id":"s1","thinking":"raw text"}"""),
            "s1",
        )
        assertTrue(events.any { it.first == "onReasoningDelta" && it.second == "raw text" })
    }

    @Test
    fun `task tool maps to subagent event working then done`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        val toolUseId = "toolu_task"
        decoder.decode(
            line("""{"type":"stream_event","event":{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"$toolUseId","name":"Task"}}}"""),
            "s1",
        )
        decoder.decode(
            line("""{"type":"stream_event","event":{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"description\":\"research X\"}"}}}"""),
            "s1",
        )
        decoder.decode(line("""{"type":"stream_event","event":{"type":"content_block_stop","index":1}}"""), "s1")
        val started = events.first { it.first == "onSubagentEvent" }
        val startedPayload = JSONObject(started.second)
        assertEquals(toolUseId, startedPayload.optString("callId"))
        assertEquals("working", startedPayload.optString("status"))
        assertEquals("subAgentActivity", startedPayload.optString("type"))
        // tool result closes the capsule as done
        events.clear()
        decoder.decode(
            line("""{"type":"user","session_id":"s1","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"$toolUseId","content":"done","is_error":false}]}}"""),
            "s1",
        )
        val done = events.first { it.first == "onSubagentEvent" }
        assertEquals("done", JSONObject(done.second).optString("status"))
    }

    @Test
    fun `result usage maps to onTokenUsage`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"result","session_id":"s1","subtype":"success","is_error":false,"result":"done","usage":{"input_tokens":10,"output_tokens":20,"cache_read_input_tokens":5}}"""),
            "s1",
        )
        val usage = events.first { it.first == "onTokenUsage" }
        assertTrue(usage.second.contains("input_tokens"))
    }

    @Test
    fun `assistant text block maps to onDelta when not streamed`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"text","text":"Hello world"}]}}"""),
            "s1",
        )
        assertTrue(events.any { it.first == "onDelta" && it.second == "Hello world" })
    }

    @Test
    fun `assistant text block does not duplicate streamed deltas`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        // content_block deltas streamed the text first
        decoder.decode(
            line("""{"type":"stream_event","event":{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}}"""),
            "s1",
        )
        events.clear()
        // the full assistant message must not re-emit the same text
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"text","text":"Hello world"}]}}"""),
            "s1",
        )
        assertTrue(events.none { it.first == "onDelta" })
    }

    @Test
    fun `thinking content array block maps to onReasoningDelta`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"thinking","thinking":"chain of thought"}]}}"""),
            "s1",
        )
        assertTrue(events.any { it.first == "onReasoningDelta" && it.second == "chain of thought" })
    }

    @Test
    fun `session echo adopt preserves the armed turn id`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        // The bridge arms the outbound turn before the CLI echoes its session id on the first
        // user-message line. The echo adopts the session WITHOUT losing the turn id, so the
        // result completion still carries the turnStarted id the host matched.
        decoder.beginTurn("turn-1")
        decoder.adoptSession("session-1")
        decoder.decode(
            line("""{"type":"result","session_id":"session-1","subtype":"success","is_error":false,"result":"done"}"""),
            "session-1",
        )
        val complete = events.first { it.first == "onTurnComplete" }
        val payload = JSONObject(complete.second)
        assertEquals("turn-1", payload.optString("turnId"))
    }

    @Test
    fun `plain reset still clears the armed turn id`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.beginTurn("turn-1")
        decoder.reset("")
        decoder.decode(
            line("""{"type":"result","session_id":"session-1","subtype":"success","is_error":false,"result":"done"}"""),
            "session-1",
        )
        val complete = events.first { it.first == "onTurnComplete" }
        // With no armed turn, the completion falls back to the session id.
        assertEquals("session-1", JSONObject(complete.second).optString("turnId"))
    }

    @Test
    fun `system thinking suppresses later stream thinking same turn`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.beginTurn("turn-1")
        // Legacy system path surfaces thinking first.
        decoder.decode(
            line("""{"type":"system","subtype":"thinking","session_id":"s1","thinking":"system thought"}"""),
            "s1",
        )
        events.clear()
        // stream_event thinking must not double the reasoning panel.
        decoder.decode(
            line("""{"type":"stream_event","event":{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"x"}}}"""),
            "s1",
        )
        decoder.decode(deltaEvent(0, "thinking_delta", "stream thought"), "s1")
        assertTrue(events.none { it.first == "onReasoningDelta" && it.second == "stream thought" })
    }

    @Test
    fun `stream thinking suppresses later system thinking same turn`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.beginTurn("turn-1")
        decoder.decode(
            line("""{"type":"stream_event","event":{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"x"}}}"""),
            "s1",
        )
        decoder.decode(deltaEvent(0, "thinking_delta", "stream thought"), "s1")
        events.clear()
        decoder.decode(
            line("""{"type":"system","subtype":"thinking","session_id":"s1","thinking":"system thought"}"""),
            "s1",
        )
        assertTrue(events.none { it.first == "onReasoningDelta" && it.second == "system thought" })
    }

    /**
     * Observed on device: an upstream 400 arrives as a synthetic assistant message, so it used to
     * stream into the reply bubble as if the model had answered with an error dump.
     */
    @Test
    fun `api error assistant message surfaces on the error channel not the answer`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.beginTurn("turn-1")
        decoder.decode(
            line(
                """{"type":"assistant","session_id":"s1","isApiErrorMessage":true,"apiErrorStatus":400,""" +
                    """"error":"unknown","message":{"model":"<synthetic>","role":"assistant","type":"message",""" +
                    """"content":[{"type":"text","text":"API Error: 400 {\"error\":{\"code\":\"InvalidSubscription\"}}"}]}}""",
            ),
            "s1",
        )
        assertTrue(events.none { it.first == "onDelta" })
        val error = events.first { it.first == "onNativeError" }
        assertTrue(error.second.contains("InvalidSubscription"))
    }

    /**
     * A gzip error body relayed without its Content-Encoding decodes to a long run of control
     * bytes. The card must collapse that run rather than mirror the garble.
     */
    @Test
    fun `undecodable bytes in an api error stay readable`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.beginTurn("turn-1")
        decoder.decode(
            line(
                """{"type":"assistant","session_id":"s1","isApiErrorMessage":true,"apiErrorStatus":400,""" +
                    """"message":{"role":"assistant","content":[{"type":"text","text":""" +
                    """"API Error: 400 \u001f\u008b\u0008\u0000\u0000\u0000AAAA\u0001\u0002BBBB"}]}}""",
            ),
            "s1",
        )
        val text = events.first { it.first == "onNativeError" }.second
        assertTrue(text.startsWith("API Error: 400"))
        assertTrue(text.contains('\uFFFD'))
        assertTrue(text.none { it.code < 0x20 && it != '\n' })
    }

    @Test
    fun `failed result does not repeat a surfaced api error`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.beginTurn("turn-1")
        decoder.decode(
            line(
                """{"type":"assistant","session_id":"s1","isApiErrorMessage":true,"apiErrorStatus":400,""" +
                    """"message":{"role":"assistant","content":[{"type":"text","text":"API Error: 400 bad request"}]}}""",
            ),
            "s1",
        )
        decoder.decode(
            line("""{"type":"result","session_id":"s1","subtype":"error_during_execution","is_error":true,"result":"API Error: 400 bad request"}"""),
            "s1",
        )
        assertEquals(1, events.count { it.first == "onNativeError" })
        assertEquals(true, JSONObject(events.first { it.first == "onTurnComplete" }.second).optBoolean("failed"))
    }

    @Test
    fun `api error surfaces again on the next turn`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        val apiError =
            """{"type":"assistant","session_id":"s1","isApiErrorMessage":true,"apiErrorStatus":400,""" +
                """"message":{"role":"assistant","content":[{"type":"text","text":"API Error: 400 boom"}]}}"""
        decoder.beginTurn("turn-1")
        decoder.decode(line(apiError), "s1")
        decoder.beginTurn("turn-2")
        decoder.decode(line(apiError), "s1")
        assertEquals(2, events.count { it.first == "onNativeError" })
    }

    /**
     * Claude Code 2.x renamed the delegated-work tool from `Task` to `Agent`. The decoder only knew
     * the old names, so subagent capsules silently stopped appearing even though the subagent ran.
     */
    @Test
    fun `agent tool maps to a subagent event`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_agent","name":"Agent","input":{"subagent_type":"Explore","description":"find X"}}]}}"""),
            "s1",
        )
        val subagent = events.first { it.first == "onSubagentEvent" }
        val payload = JSONObject(subagent.second)
        assertEquals("toolu_agent", payload.optString("callId"))
        assertEquals("working", payload.optString("status"))
        assertEquals("Explore", payload.optString("name"))
    }

    @Test
    fun `an unknown subagent tool name is recognised by its input shape`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_x","name":"SomeFutureName","input":{"subagent_type":"general-purpose"}}]}}"""),
            "s1",
        )
        assertTrue(events.any { it.first == "onSubagentEvent" })
    }

    /** Reading an image must reach the image card, not a generic tool row. */
    @Test
    fun `reading an image emits an image item carrying the path`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_img","name":"Read","input":{"file_path":"probe.png"}}]}}"""),
            "s1",
        )
        val item = events.first { it.first == "onToolComplete" }
        val payload = JSONObject(item.second)
        assertEquals("image", payload.optString("type"))
        assertEquals("probe.png", payload.optString("preview"))
        assertEquals("Read", payload.optString("tool"))
    }

    /** An edit must reach the diff card: type `fileChange` plus a renderable diff preview. */
    @Test
    fun `editing a file emits a file change with a diff preview`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_edit","name":"Edit","input":{"file_path":"Main.kt","old_string":"val a = 1","new_string":"val a = 2"}}]}}"""),
            "s1",
        )
        val payload = JSONObject(events.first { it.first == "onToolComplete" }.second)
        assertEquals("fileChange", payload.optString("type"))
        val preview = payload.optString("preview")
        assertTrue(preview.contains("-val a = 1"))
        assertTrue(preview.contains("+val a = 2"))
        assertEquals("Main.kt", payload.optString("subject"))
    }

    /**
     * The trailing tool_result must not downgrade the row back to a generic tool call, and must not
     * replace the diff with the raw result (for an image that result is a base64 blob).
     */
    @Test
    fun `a tool result keeps the classification and the diff`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_edit","name":"Edit","input":{"file_path":"Main.kt","old_string":"a","new_string":"b"}}]}}"""),
            "s1",
        )
        events.clear()
        decoder.decode(
            line("""{"type":"user","session_id":"s1","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_edit","content":"Applied 1 edit","is_error":false}]}}"""),
            "s1",
        )
        val payload = JSONObject(events.first { it.first == "onToolComplete" }.second)
        assertEquals("fileChange", payload.optString("type"))
        assertEquals("completed", payload.optString("status"))
        assertTrue(payload.optString("preview").contains("-a"))
    }

    @Test
    fun `a plain tool keeps its name and subject`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_g","name":"Grep","input":{"pattern":"fun main"}}]}}"""),
            "s1",
        )
        val payload = JSONObject(events.first { it.first == "onToolComplete" }.second)
        assertEquals("tool", payload.optString("type"))
        assertEquals("Grep", payload.optString("tool"))
        assertEquals("fun main", payload.optString("subject"))
    }

    /** A plain tool does adopt its result as the preview, so the row shows real output. */
    @Test
    fun `a plain tool adopts the result text as its preview`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_g2","name":"Glob","input":{"pattern":"*.kt"}}]}}"""),
            "s1",
        )
        events.clear()
        decoder.decode(
            line("""{"type":"user","session_id":"s1","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_g2","content":"Main.kt\nApp.kt","is_error":false}]}}"""),
            "s1",
        )
        val payload = JSONObject(events.first { it.first == "onToolComplete" }.second)
        assertEquals("tool", payload.optString("type"))
        assertTrue(payload.optString("preview").contains("Main.kt"))
    }

    @Test
    fun `web fetch maps to the web search type`() {
        val events = mutableListOf<Pair<String, String>>()
        val decoder = decoder(events)
        decoder.decode(
            line("""{"type":"assistant","session_id":"s1","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_w","name":"WebFetch","input":{"url":"https://x.dev"}}]}}"""),
            "s1",
        )
        val payload = JSONObject(events.first { it.first == "onToolComplete" }.second)
        assertEquals("webSearch", payload.optString("type"))
        assertEquals("https://x.dev", payload.optString("subject"))
    }
}
