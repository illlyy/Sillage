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

    /** thinking accumulated from `system` subtype "thinking" messages (Claude Code CLI path) */
    private var systemThinkingBuffer: StringBuilder? = null
    private var surfacedSystemThinking = false

    /** True once any thinking reached the host this turn, so the final-message fallback never duplicates. */
    private var thinkingSurfacedThisTurn = false

    /** True once text streamed via content_block deltas, so the assistant-message fallback never duplicates. */
    private var textStreamedThisTurn = false

    /** content block index of the active thinking block, -1 when none */
    private var activeThinkingIndex = -1

    /**
     * Which live path delivered thinking this turn: 0 = none yet, 1 = `system` subtype
     * "thinking", 2 = `stream_event` content blocks. With `--include-partial-messages` the CLI can
     * emit thinking through BOTH paths; the first one to surface wins and the other is suppressed,
     * otherwise the reasoning panel would show every chunk twice.
     */
    private var thinkingSource = 0

    /** Task tool_use id -> subagent capsule key (agentThreadId) */
    private val subagentCapsules = HashMap<String, String>()

    /** Task tool_use id -> subagent display name */
    private val subagentNames = HashMap<String, String>()

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
        systemThinkingBuffer = null
        surfacedSystemThinking = false
        thinkingSurfacedThisTurn = false
        textStreamedThisTurn = false
        activeThinkingIndex = -1
        thinkingSource = 0
        textBuffer = null
        subagentCapsules.clear()
        subagentNames.clear()
        activeTurnId = ""
    }

    /**
     * Adopts the authoritative session id the CLI echoes (first user-message line on a fresh
     * session). The echo can arrive after [beginTurn] already armed the outbound turn, so a plain
     * [reset] would wipe `activeTurnId` and the subsequent `result` completion would fall back to
     * the session id — mismatching the `turnStarted` turn id the host recorded, which rejects the
     * completion (turn never seals). Re-assert the armed turn id so the completion matches.
     */
    fun adoptSession(sessionId: String) {
        val turn = activeTurnId
        reset(sessionId)
        if (turn.isNotBlank()) beginTurn(turn)
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
            "thinking" -> {
                // Claude Code streams its thinking panel as system messages, not content blocks.
                // Normalize both string and {kind, thinking} shapes so the host's reasoning panel
                // streams live exactly like the Codex backend does.
                val raw = line.opt("thinking")
                val text = when (raw) {
                    is String -> raw
                    is JSONObject -> raw.optString("thinking").ifBlank { raw.optString("text") }
                    else -> ""
                }
                if (text.isNotEmpty()) {
                    // Stream_event thinking already won this turn's source; don't double surface.
                    if (thinkingSource == 2) return
                    thinkingSource = 1
                    surfacedSystemThinking = true
                    thinkingSurfacedThisTurn = true
                    val buffer = systemThinkingBuffer ?: StringBuilder().also { systemThinkingBuffer = it }
                    buffer.append(text)
                    emit("onReasoningDelta", text)
                }
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
                        // Legacy system/thinking already won this turn's source; don't arm the
                        // content-block path (it would double the reasoning panel).
                        if (thinkingSource != 1) {
                            thinkingSource = 2
                            thinkingSurfacedThisTurn = true
                            thinkingBuffer = StringBuilder()
                            activeThinkingIndex = event.optInt("index", -1)
                        }
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
                        if (text.isNotEmpty()) {
                            textStreamedThisTurn = true
                            emit("onDelta", text)
                        }
                    }
                    "thinking_delta" -> {
                        thinkingBuffer?.append(delta.optString("thinking"))
                        val text = delta.optString("thinking")
                        if (text.isNotEmpty() && thinkingSource != 1) {
                            // A thinking_delta can arrive without a preceding content_block_start
                            // (or the legacy system path already surfaced thinking). Never mix
                            // sources for one turn's reasoning panel.
                            thinkingSource = 2
                            emit("onReasoningDelta", text)
                        }
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
                "text" -> {
                    // The CLI can deliver the answer text as a full assistant message instead of
                    // content_block deltas. Surface it as onDelta unless deltas already streamed it.
                    if (!textStreamedThisTurn) {
                        val text = block.optString("text")
                        if (text.isNotEmpty()) emit("onDelta", text)
                    }
                }
                else -> Unit
            }
        }
        // Fallback for thinking that reached only the final assistant message instead of content
        // deltas. The transcript carries it as a content block of type "thinking"; only surface it
        // once so the reasoning panel is not duplicated after a streamed chain of thought.
        if (!thinkingSurfacedThisTurn && thinkingBuffer == null) {
            val thinking = contentThinkingText(content).ifEmpty { message.optString("thinking") }
            if (thinking.isNotEmpty()) {
                thinkingSurfacedThisTurn = true
                surfacedSystemThinking = true
                val buffer = systemThinkingBuffer ?: StringBuilder().also { systemThinkingBuffer = it }
                buffer.append(thinking)
                emit("onReasoningDelta", thinking)
            }
        }
    }

    /** Concatenates `thinking` text blocks from an assistant content array. */
    private fun contentThinkingText(content: JSONArray): String {
        val out = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "thinking") {
                val text = block.optString("thinking")
                if (text.isNotEmpty()) {
                    if (out.isNotEmpty()) out.append("\n\n")
                    out.append(text)
                }
            }
        }
        return out.toString()
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
            subagentCapsules.remove(toolUseId)?.let { agentThreadId ->
                emit("onSubagentEvent", JSONObject()
                    .put("agentThreadId", agentThreadId)
                    .put("callId", toolUseId)
                    .put("name", subagentNames.remove(toolUseId) ?: "Task")
                    .put("status", "done")
                    .put("type", "subAgentActivity")
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
            "Task", "Task_simple", "subagent_tool_use", "subagent" -> {
                // Surface delegated subagent work as a live capsule (working -> done). The capsule
                // is keyed by the Task tool-use id; loadSubagentHistory resolves the transcript.
                val agentThreadId = input.optString("agentThreadId")
                    .ifBlank { input.optString("agent_thread_id") }
                    .ifBlank { id }
                val name = input.optString("agentName")
                    .ifBlank { input.optString("agentNickname") }
                    .ifBlank { input.optString("name") }
                    .ifBlank { "Task" }
                subagentCapsules[id] = agentThreadId
                subagentNames[id] = name
                emit("onSubagentEvent", JSONObject()
                    .put("agentThreadId", agentThreadId)
                    .put("callId", id)
                    .put("name", name)
                    .put("status", "working")
                    .put("type", "subAgentActivity")
                    .toString())
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
        // Seal system-message thinking and forward result usage (tokens) so the reasoning panel
        // closes exactly at turn end and the answer card shows stats.
        if (surfacedSystemThinking) {
            systemThinkingBuffer?.let { buffer ->
                val full = buffer.toString()
                if (full.isNotBlank()) emit("onReasoningComplete", full)
            }
            systemThinkingBuffer = null
            surfacedSystemThinking = false
        }
        val usage = line.optJSONObject("usage")
        if (usage != null) emit("onTokenUsage", JSONObject().put("usage", usage).toString())
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
        systemThinkingBuffer = null
        surfacedSystemThinking = false
        thinkingSurfacedThisTurn = false
        textStreamedThisTurn = false
        activeThinkingIndex = -1
        thinkingSource = 0
        textBuffer = null
        subagentCapsules.clear()
        subagentNames.clear()
    }
}
