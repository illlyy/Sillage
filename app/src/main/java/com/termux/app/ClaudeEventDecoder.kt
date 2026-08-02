package com.termux.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Decodes one Claude Code `stream-json` NDJSON line into the same normalized
 * {@code function}/{@code value} events that CodexChatActivity dispatches, so the
 * chat host, reducers and Compose UI are backend agnostic.
 *
 * The decoder keeps per-turn state (streaming tool arguments, active command items)
 * and emits plain-text deltas for reasoning/answer streams, JSON payloads for
 * commands/tools/plans/approvals — matching the codex bridge's event shapes.
 *
 * Thread-safety: used only by the ClaudeAgentBridge reader thread.
 */
internal class ClaudeEventDecoder(
    private val currentThread: () -> String,
    private val emit: (function: String, value: String) -> Unit,
) {
    /** tool_use_id -> accumulated input JSON (from input_json_delta) */
    private val pendingToolInputs = HashMap<String, StringBuilder>()

    /** tool_use_id -> command item id (Bash) for output correlation */
    private val commandItemIds = HashMap<String, String>()

    /** tool_use_id -> emitted tool detail id (non-command tools) */
    private val toolDetailIds = HashMap<String, String>()

    /** thinking block accumulation (for ReasoningCompleted) */
    private var thinkingBuffer: StringBuilder? = null

    /** content block index of the active thinking block, -1 when none */
    private var activeThinkingIndex = -1

    /** text block accumulation for final assistant message fallback */
    private var textBuffer: StringBuilder? = null

    /** ids of tool_use blocks already surfaced via stream events */
    private val surfacedToolUses = HashSet<String>()

    /** ids of tool_result blocks already surfaced */
    private val surfacedToolResults = HashSet<String>()

    private var activeTurnId = ""
    private var epoch = 0L

    fun reset(threadId: String) {
        pendingToolInputs.clear()
        commandItemIds.clear()
        toolDetailIds.clear()
        surfacedToolUses.clear()
        surfacedToolResults.clear()
        thinkingBuffer = null
        activeThinkingIndex = -1
        textBuffer = null
        activeTurnId = ""
    }

    fun observeSession(line: JSONObject): String {
        return line.optString("session_id").ifBlank { line.optString("sessionId") }
    }

    /** Decodes one NDJSON line; session id is captured by the caller for onReady routing. */
    fun decode(line: JSONObject, sessionId: String) {
        val type = line.optString("type")
        when (type) {
            "system" -> decodeSystem(line)
            "stream_event" -> decodeStreamEvent(line.optJSONObject("event"))
            "assistant" -> decodeAssistant(line)
            "user" -> decodeUser(line)
            "result" -> decodeResult(line, sessionId)
            "control_request" -> Unit // handled by the bridge
            else -> Unit
        }
    }

    private fun thread() = currentThread().ifBlank { "claude" }

    private fun decodeSystem(line: JSONObject) {
        when (line.optString("subtype")) {
            "init" -> Unit // capabilities negotiated by the bridge; no UI event needed
            "compact_boundary" -> {
                // Claude compaction is instantaneous at the boundary; emit the full
                // started -> completed pair so the UI never leaves a spinning capsule.
                emit("onCompactStatus", "started")
                emit("onCompactStatus", "completed")
            }
            "permission_denied" -> {
                val tool = line.optString("tool_name", line.optString("toolName"))
                val message = line.optString("message").ifBlank { "工具调用被拒绝：$tool" }
                emit("onTurnError", JSONObject()
                    .put("threadId", thread())
                    .put("message", message)
                    .put("tool", tool).toString())
            }
            "informational" -> {
                val message = line.optString("content").ifBlank { return }
                if (line.optString("level") == "warning") emit("onHistoryWarning", message)
            }
            else -> Unit
        }
    }

    private fun decodeStreamEvent(event: JSONObject?) {
        if (event == null) return
        when (event.optString("type")) {
            "content_block_start" -> {
                val block = event.optJSONObject("content_block") ?: return
                when (block.optString("type")) {
                    "thinking" -> {
                        thinkingBuffer = StringBuilder()
                        activeThinkingIndex = event.optInt("index", -1)
                    }
                    "text" -> textBuffer = StringBuilder()
                    "tool_use" -> observeToolBlock(event.optInt("index", -1), block)
                    else -> Unit
                }
            }
            "content_block_delta" -> {
                val delta = event.optJSONObject("delta") ?: return
                when (delta.optString("type")) {
                    "text_delta" -> {
                        textBuffer?.append(delta.optString("text"))
                        val text = delta.optString("text")
                        if (text.isNotEmpty()) emit("onDelta", text)
                    }
                    "thinking_delta" -> {
                        thinkingBuffer?.append(delta.optString("thinking"))
                        val text = delta.optString("thinking")
                        if (text.isNotEmpty()) emit("onReasoningDelta", text)
                    }
                    "input_json_delta" -> {
                        val index = event.optInt("index", -1)
                        val blockId = pendingToolIdForIndex(index) ?: return
                        pendingToolInputs.getOrPut(blockId) { StringBuilder() }
                            .append(delta.optString("partial_json"))
                    }
                    else -> Unit
                }
            }
            "content_block_stop" -> {
                val index = event.optInt("index", -1)
                // Thinking blocks seal the reasoning panel when their block ends.
                if (index == activeThinkingIndex) {
                    thinkingBuffer?.let { buffer ->
                        val full = buffer.toString()
                        if (full.isNotBlank()) emit("onReasoningComplete", full)
                    }
                    thinkingBuffer = null
                    activeThinkingIndex = -1
                }
                pendingToolIdForIndex(index)?.let { id ->
                    val input = runCatching {
                        JSONObject(pendingToolInputs.remove(id)?.toString().orEmpty())
                    }.getOrNull() ?: JSONObject()
                    val name = pendingToolNames.remove(id) ?: ""
                    surfaceToolUse(id, name, input)
                }
            }
            else -> Unit
        }
    }

    /** Maps a content block index to the tool_use id seen at content_block_start. */
    private val toolIndexToId = HashMap<Int, String>()

    private fun pendingToolIdForIndex(index: Int): String? {
        val id = toolIndexToId[index]
        if (id != null) return id
        // content_block_start for tool_use may carry the id in the event itself
        return null
    }

    private fun decodeAssistant(line: JSONObject) {
        val message = line.optJSONObject("message") ?: return
        val content = message.optJSONArray("content") ?: return
        val parentAgent = line.optString("parent_tool_use_id", "").ifBlank { "" }
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "tool_use" -> {
                    val id = block.optString("id")
                    if (id.isBlank() || surfacedToolUses.contains(id)) continue
                    surfaceToolUse(id, block.optString("name"), block.optJSONObject("input") ?: JSONObject())
                }
                else -> Unit
            }
        }
        // Final assistant text fallback when streaming was disabled: not emitted to avoid
        // duplicates; the stream path already delivered deltas.
        val thinking = message.optString("thinking", "")
        if (thinking.isNotEmpty() && thinkingBuffer == null) emit("onReasoningDelta", thinking)
    }

    private fun decodeUser(line: JSONObject) {
        val message = line.optJSONObject("message") ?: return
        val content = message.optJSONArray("content") ?: return
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") != "tool_result") continue
            val toolUseId = block.optString("tool_use_id")
            if (toolUseId.isBlank() || surfacedToolResults.contains(toolUseId)) continue
            surfacedToolResults.add(toolUseId)
            val output = block.opt("content")
            val outputText = when (output) {
                is String -> output
                is JSONArray -> output.toString()
                is JSONObject -> output.toString()
                else -> ""
            }
            val isError = block.optBoolean("is_error", false)
            commandItemIds.remove(toolUseId)?.let { command ->
                emit("onCommandComplete", JSONObject()
                    .put("id", toolUseId)
                    .put("command", command)
                    .put("status", if (isError) "failed" else "completed")
                    .put("exitCode", if (isError) 1 else 0)
                    .put("output", outputText)
                    .toString())
            }
            toolDetailIds.remove(toolUseId)?.let { detailId ->
                emit("onToolComplete", JSONObject()
                    .put("id", detailId)
                    .put("type", "tool")
                    .put("tool", detailId)
                    .put("status", if (isError) "failed" else "completed")
                    .put("payload", outputText)
                    .toString())
            }
        }
    }

    private fun surfaceToolUse(id: String, toolName: String, input: JSONObject) {
        if (surfacedToolUses.contains(id)) return
        surfacedToolUses.add(id)
        when (toolName) {
            "Bash", "Shell", "bash" -> {
                val command = input.optString("command", input.optString("cmd"))
                commandItemIds[id] = command
                emit("onCommandStarted", JSONObject()
                    .put("id", id)
                    .put("command", command)
                    .put("cwd", input.optString("cwd"))
                    .put("status", "inProgress")
                    .toString())
            }
            "AskUserQuestion" -> {
                val questions = input.optJSONArray("questions") ?: JSONArray()
                val first = questions.optJSONObject(0) ?: JSONObject()
                emit("onUserInputRequest", JSONObject()
                    .put("params", JSONObject()
                        .put("threadId", thread())
                        .put("requestId", id)
                        .put("toolUseId", id)
                        .put("prompt", first.optString("question", input.toString()))
                        .put("questions", questions))
                    .toString())
            }
            "ExitPlanMode" -> {
                val plan = input.optString("plan")
                emit("onPlanStarted", JSONObject().put("id", id).toString())
                emit("onPlanDelta", JSONObject()
                    .put("itemId", id)
                    .put("delta", plan).toString())
                emit("onPlanComplete", JSONObject().put("item", JSONObject().put("id", id).put("text", plan)).toString())
            }
            else -> {
                val detailId = id
                toolDetailIds[id] = detailId
                emit("onToolComplete", JSONObject()
                    .put("id", detailId)
                    .put("type", "tool")
                    .put("tool", toolName)
                    .put("status", "inProgress")
                    .toString())
            }
        }
    }

    /** tool_use_id -> tool name, captured from content_block_start events */
    internal val pendingToolNames = HashMap<String, String>()

    fun observeToolBlock(index: Int, block: JSONObject) {
        val id = block.optString("id")
        if (id.isNotBlank()) {
            toolIndexToId[index] = id
            pendingToolNames[id] = block.optString("name")
        }
    }

    private fun decodeResult(line: JSONObject, sessionId: String) {
        val isError = line.optBoolean("is_error", false) ||
            line.optString("subtype", "success").startsWith("error")
        val turnId = activeTurnId.ifBlank { sessionId }
        val failed = isError
        thinkingBuffer = null
        activeThinkingIndex = -1
        textBuffer = null
        emit("onTurnComplete", JSONObject()
            .put("threadId", thread())
            .put("turnId", turnId)
            .put("epoch", epoch)
            .put("turn", JSONObject().put("hasPendingContinuation", false))
            .put("details", JSONObject())
            .put("failed", failed)
            .toString())
        if (failed) {
            val error = line.optString("result").ifBlank { line.optString("subtype") }
            if (error.isNotBlank()) emit("onNativeError", error)
        }
        epoch++
        activeTurnId = ""
    }

    /** Call when a turn begins (first user message of a turn) to reset per-turn state. */
    fun beginTurn(turnId: String) {
        activeTurnId = turnId
        surfacedToolUses.clear()
        surfacedToolResults.clear()
        thinkingBuffer = null
        activeThinkingIndex = -1
        textBuffer = null
    }
}
