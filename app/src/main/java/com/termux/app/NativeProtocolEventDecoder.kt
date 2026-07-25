package com.termux.app

import org.json.JSONObject

/**
 * Adapter for the already-normalized JSON emitted by CodexAppServerBridge.
 *
 * Keeping this decoder independent from Activity/Compose makes protocol replay tests useful and
 * gives retained/older bridges one place to normalize camelCase and snake_case metadata.  The
 * bridge remains responsible for understanding app-server method names; this class only decodes
 * the small native protocol envelope.
 */
internal object NativeProtocolEventDecoder {
    fun decodeLifecycle(raw: String, fallbackThreadId: String = ""): NativeProtocolEvent? =
        decodeLifecycleObject(runCatching { JSONObject(raw) }.getOrNull(), fallbackThreadId)

    fun decodeDeltas(raw: String, fallbackThreadId: String = ""): List<NativeProtocolEvent> =
        raw.lineSequence().mapNotNull { line ->
            val payload = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
            val body = payload.optJSONObject("params") ?: payload
            val kind = canonicalKind(
                text(payload, "kind", "event_kind").ifBlank {
                    text(body, "kind", "event_kind").ifBlank { text(payload, "method", "event", "type") }
                },
            )
            val common = common(body, fallbackThreadId, payload)
            val delta = text(body, "delta").ifBlank { text(payload, "delta") }
            when (kind) {
                "reasoningDelta" -> NativeProtocolEvent.ReasoningDelta(
                    threadId = common.threadId, turnId = common.turnId, itemId = common.itemId,
                    delta = delta, sequence = common.sequence, timestampMs = common.timestampMs,
                )
                "assistantDelta" -> NativeProtocolEvent.AssistantDelta(
                    threadId = common.threadId, turnId = common.turnId, itemId = common.itemId,
                    delta = delta, itemPhase = text(body, "itemPhase", "item_phase").ifBlank { text(payload, "itemPhase", "item_phase") }.ifBlank { null },
                    sequence = common.sequence, timestampMs = common.timestampMs,
                )
                "planDelta" -> NativeProtocolEvent.PlanDelta(
                    threadId = common.threadId, turnId = common.turnId, itemId = common.itemId,
                    delta = delta, dedicated = true,
                    sequence = common.sequence, timestampMs = common.timestampMs,
                )
                else -> null
            }
        }.toList()

    fun decodeCommandOutputs(raw: String, fallbackThreadId: String = ""): List<NativeProtocolEvent.CommandOutput> =
        raw.lineSequence().mapNotNull { line ->
            val payload = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
            val body = payload.optJSONObject("params") ?: payload
            val kind = canonicalKind(
                text(payload, "kind", "event_kind").ifBlank {
                    text(body, "kind", "event_kind").ifBlank { text(payload, "method", "event", "type") }
                },
            )
            if (kind !in setOf("commandOutput", "commandDelta")) return@mapNotNull null
            val common = common(body, fallbackThreadId, payload)
            NativeProtocolEvent.CommandOutput(
                threadId = common.threadId,
                turnId = common.turnId,
                itemId = common.itemId,
                delta = text(body, "delta").ifBlank { text(payload, "delta") },
                outputRef = text(body, "outputRef", "output_ref").ifBlank { text(payload, "outputRef", "output_ref") },
                sequence = common.sequence,
                timestampMs = common.timestampMs,
            )
        }.toList()

    private fun decodeLifecycleObject(payload: JSONObject?, fallbackThreadId: String): NativeProtocolEvent? {
        if (payload == null) return null
        // Replay fixtures sometimes contain the original app-server envelope
        // ({method, params}) while newer bridge snapshots already flatten params. Keep one
        // decoder for both shapes so lifecycle normalization does not depend on which bridge
        // version produced the recording.
        val body = payload.optJSONObject("params") ?: payload
        val rawKind = text(payload, "kind", "event_kind").ifBlank {
            text(body, "kind", "event_kind").ifBlank {
            // Older protocol recordings kept only the app-server method name. Treat it as an
            // envelope too so replay does not depend on the bridge version that produced it.
            text(payload, "method", "event", "type").ifBlank {
                text(body, "method", "event", "type")
            }
            }
        }
        val item = body.optJSONObject("item") ?: body.optJSONObject("details")
            ?: body.optJSONObject("tokenUsage") ?: body.optJSONObject("token_usage")
            ?: payload.optJSONObject("item") ?: payload.optJSONObject("details") ?: JSONObject()
        fun field(vararg keys: String): String = text(item, *keys)
            .ifBlank { text(body, *keys) }
            .ifBlank { text(payload, *keys) }
        val kind = when (val canonical = canonicalKind(rawKind)) {
            "itemStarted" -> lifecycleKind(item, started = true)
            "itemCompleted" -> lifecycleKind(item, started = false)
            else -> canonical
        }
        val common = common(body, fallbackThreadId, payload)
        return when (kind) {
            "contextCompactionStarted" -> NativeProtocolEvent.CompactionStarted(
                common.threadId, common.turnId, common.itemId,
                source = parseCompactionSource(field("source", "origin", "trigger")),
                requestId = field("requestId", "request_id").takeIf { it.isNotBlank() },
                sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "contextCompactionCompleted" -> {
                val status = field("status", "state").lowercase()
                when {
                    status in setOf("failed", "error", "failure") -> NativeProtocolEvent.CompactionFailed(
                        common.threadId, common.turnId, common.itemId,
                        error = field("error", "message"),
                        sequence = common.sequence, timestampMs = common.timestampMs,
                        source = parseCompactionSource(field("source", "origin", "trigger")),
                        requestId = field("requestId", "request_id").takeIf { it.isNotBlank() },
                    )
                    status in setOf("cancelled", "canceled") -> NativeProtocolEvent.CompactionFailed(
                        common.threadId, common.turnId, common.itemId,
                        error = field("error", "message"), cancelled = true,
                        sequence = common.sequence, timestampMs = common.timestampMs,
                        source = parseCompactionSource(field("source", "origin", "trigger")),
                        requestId = field("requestId", "request_id").takeIf { it.isNotBlank() },
                    )
                    else -> NativeProtocolEvent.CompactionCompleted(
                        common.threadId, common.turnId, common.itemId,
                        error = field("error", "message")
                            .takeIf { it.isNotBlank() },
                        sequence = common.sequence, timestampMs = common.timestampMs,
                        source = parseCompactionSource(field("source", "origin", "trigger")),
                        requestId = field("requestId", "request_id").takeIf { it.isNotBlank() },
                    )
                }
            }
            "contextCompactionFailed", "contextCompactionCancelled" -> NativeProtocolEvent.CompactionFailed(
                common.threadId, common.turnId, common.itemId,
                error = field("error", "message"),
                cancelled = kind.endsWith("Cancelled"),
                sequence = common.sequence, timestampMs = common.timestampMs,
                source = parseCompactionSource(field("source", "origin", "trigger")),
                requestId = field("requestId", "request_id").takeIf { it.isNotBlank() },
            )
            "commandStarted" -> NativeProtocolEvent.CommandStarted(
                common.threadId, common.turnId, common.itemId,
                command = text(item, "command", "cmd"), cwd = text(item, "cwd", "workingDirectory", "working_directory"),
                payload = item.toString(), sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "commandCompleted" -> NativeProtocolEvent.CommandCompleted(
                common.threadId, common.turnId, common.itemId,
                command = text(item, "command", "cmd"), outputRef = text(item, "outputRef", "output_ref"),
                status = text(item, "status", "state").ifBlank { "completed" },
                exitCode = item.optInt("exitCode", item.optInt("exit_code", Int.MIN_VALUE)).takeUnless { it == Int.MIN_VALUE },
                payload = item.toString(), sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "reasoningCompleted" -> NativeProtocolEvent.ReasoningCompleted(
                common.threadId, common.turnId, common.itemId,
                text(item, "text", "summary"), sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "assistantStarted" -> NativeProtocolEvent.AssistantDelta(
                common.threadId, common.turnId, common.itemId, delta = "",
                sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "assistantCompleted" -> NativeProtocolEvent.AssistantCompleted(
                common.threadId, common.turnId, common.itemId,
                text = text(item, "text", "content"), finalAnswer = isFinalAssistantItem(item),
                sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "planStarted" -> NativeProtocolEvent.PlanStarted(
                common.threadId, common.turnId, common.itemId, sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "planCompleted" -> NativeProtocolEvent.PlanCompleted(
                common.threadId, common.turnId, common.itemId,
                text(item, "text", "content"), sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "toolCompleted" -> NativeProtocolEvent.ToolCompleted(
                common.threadId, common.turnId, common.itemId,
                type = text(payload, "itemType", "item_type").ifBlank { text(item, "type") },
                title = text(item, "tool", "name", "query", "agentName"),
                payloadRef = text(item, "payloadRef", "payload_ref"),
                status = text(item, "status", "state").ifBlank { "completed" }, payload = item.toString(),
                sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "subagentUpdated" -> NativeProtocolEvent.SubagentUpdated(
                common.threadId, common.turnId, common.itemId,
                agentThreadId = text(item, "agentThreadId", "agent_thread_id"),
                callId = text(item, "callId", "call_id"),
                name = text(item, "agentName", "agentNickname", "nickname"),
                status = text(item, "status", "state").ifBlank { "working" },
                sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "tokenUsageUpdated" -> decodeUsage(common, item)
            "turnCompleted" -> NativeProtocolEvent.TurnCompleted(
                common.threadId, common.turnId, common.itemId,
                failed = item.has("error") && !item.isNull("error"),
                sequence = common.sequence, timestampMs = common.timestampMs,
            )
            "error" -> NativeProtocolEvent.Error(
                common.threadId, common.turnId, common.itemId,
                message = text(item, "message", "error").ifBlank { text(payload, "message", "error") },
                sequence = common.sequence, timestampMs = common.timestampMs,
            )
            else -> null
        }
    }

    private fun decodeUsage(common: Common, item: JSONObject): NativeProtocolEvent.TokenUsageUpdated {
        val usage = NativeTokenUsageParser.parse(item.toString(), 0L) ?: NativeTurnUsage()
        return NativeProtocolEvent.TokenUsageUpdated(
            threadId = common.threadId, turnId = common.turnId, itemId = common.itemId,
            inputTokens = usage.inputTokens, cachedInputTokens = usage.cachedInputTokens,
            outputTokens = usage.outputTokens, reasoningTokens = usage.reasoningOutputTokens,
            currentContextTokens = usage.currentContextTokens,
            contextWindow = usage.contextWindow, estimated = usage.estimated,
            contextUsageReliable = usage.contextUsageReliable,
            autoCompactTokenLimit = usage.autoCompactTokenLimit,
            sequence = common.sequence, timestampMs = common.timestampMs,
        )
    }

    private data class Common(
        val threadId: String,
        val turnId: String?,
        val itemId: String?,
        val sequence: Long,
        val timestampMs: Long,
    )

    private fun common(payload: JSONObject, fallbackThreadId: String, envelope: JSONObject? = null): Common {
        val item = payload.optJSONObject("item") ?: payload.optJSONObject("details")
        val turn = payload.optJSONObject("turn")
        fun envelopeText(vararg keys: String): String = text(payload, *keys)
            .ifBlank { text(envelope, *keys) }
        val thread = envelopeText("threadId", "thread_id")
            .ifBlank { text(turn, "threadId", "thread_id") }
            .ifBlank { text(item, "threadId", "thread_id", "senderThreadId", "sender_thread_id") }
            .ifBlank { fallbackThreadId }
        val turnId = envelopeText("turnId", "turn_id")
            .ifBlank { text(turn, "id", "turnId", "turn_id") }
            .ifBlank { text(item, "turnId", "turn_id") }
            .ifBlank { "" }
            .takeIf { it.isNotBlank() }
        val itemId = envelopeText("itemId", "item_id")
            .ifBlank { text(item, "id", "itemId", "item_id") }
            .takeIf { it.isNotBlank() }
        val sequence = payload.optLong(
            "sequence",
            payload.optLong("sequence_number", envelope?.optLong("sequence", envelope.optLong("sequence_number", 0L)) ?: 0L),
        )
        val timestamp = payload.optLong(
            "timestampMs",
            payload.optLong(
                "timestamp_ms",
                envelope?.optLong("timestampMs", envelope.optLong("timestamp_ms", System.currentTimeMillis()))
                    ?: System.currentTimeMillis(),
            ),
        )
        return Common(
            threadId = thread,
            turnId = turnId,
            itemId = itemId,
            sequence = sequence,
            timestampMs = timestamp,
        )
    }

    private fun text(value: JSONObject?, vararg keys: String): String {
        if (value == null) return ""
        keys.forEach { key ->
            val direct = value.optString(key, "").trim()
            if (direct.isNotBlank() && !direct.equals("null", true)) return direct
        }
        return ""
    }

    private fun parseCompactionSource(value: String): NativeCompactionSource? = when (value.lowercase()) {
        "manual", "user", "interactive" -> NativeCompactionSource.MANUAL
        "automatic", "auto", "server", "fallback" -> NativeCompactionSource.AUTOMATIC
        "legacy" -> NativeCompactionSource.LEGACY
        else -> null
    }

    private fun isFinalAssistantItem(item: JSONObject): Boolean {
        val phase = text(item, "phase", "itemPhase", "item_phase")
            .replace("-", "_").replace(" ", "_").lowercase()
        return phase in setOf("final_answer", "finalanswer", "final", "answer") ||
            item.optBoolean("final", false) || item.optBoolean("isFinal", false)
    }

    private fun lifecycleKind(item: JSONObject, started: Boolean): String {
        val type = text(item, "type", "itemType", "item_type")
            .replace("_", "").replace("-", "").lowercase()
        val suffix = if (started) "Started" else "Completed"
        return when {
            type == "contextcompaction" || type == "contextcompacted" -> "contextCompaction$suffix"
            type == "commandexecution" -> "command$suffix"
            type == "reasoning" -> "reasoning$suffix"
            type == "plan" -> "plan$suffix"
            type == "agentmessage" -> "assistant$suffix"
            type == "subagentactivity" || type == "collabagenttoolcall" -> "subagentUpdated"
            else -> "tool$suffix"
        }
    }

    /** Protocol recordings from older bridges used snake_case event kinds. */
    private fun canonicalKind(raw: String): String {
        val normalized = raw.replace("_", "").replace("-", "").replace("/", "").replace(".", "").lowercase()
        return when (normalized) {
            "reasoningdelta" -> "reasoningDelta"
            "reasoningcompleted", "reasoningcomplete" -> "reasoningCompleted"
            "assistantdelta", "itemagentmessagedelta", "itemagentmessagecontentdelta", "itemagentmessagetextdelta" -> "assistantDelta"
            "itemreasoningsummarytextdelta", "itemreasoningtextdelta", "reasoningtextdelta" -> "reasoningDelta"
            "itemstarted", "itemstart" -> "itemStarted"
            "itemcompleted", "itemcomplete" -> "itemCompleted"
            "assistantstarted", "assistantstart" -> "assistantStarted"
            "assistantcompleted", "assistantcomplete" -> "assistantCompleted"
            "commandstarted", "commandstart" -> "commandStarted"
            "commandoutput", "commanddelta", "itemcommandexecutionoutputdelta" -> "commandOutput"
            "commandcompleted", "commandcomplete" -> "commandCompleted"
            "toolcompleted", "toolcomplete" -> "toolCompleted"
            "planstarted", "planstart" -> "planStarted"
            "plandelta" -> "planDelta"
            "plancompleted", "plancomplete" -> "planCompleted"
            "subagentupdated", "subagentupdate" -> "subagentUpdated"
            "contextcompactionstarted", "contextcompactionstart" -> "contextCompactionStarted"
            "contextcompactioncompleted", "contextcompactioncomplete" -> "contextCompactionCompleted"
            "contextcompacted", "contextcompaction" -> "contextCompactionCompleted"
            "contextcompactionfailed" -> "contextCompactionFailed"
            "contextcompactioncancelled", "contextcompactioncanceled" -> "contextCompactionCancelled"
            "tokenusageupdated", "tokenusageupdate", "threadtokenusageupdated", "threadtokenusageupdate" -> "tokenUsageUpdated"
            "turncompleted", "turncomplete" -> "turnCompleted"
            "error" -> "error"
            else -> when {
                // Some app-server builds namespace lifecycle notifications with `thread/` or
                // `item/` prefixes. Normalize by suffix as a forward-compatible replay adapter.
                normalized.endsWith("contextcompactionstarted") || normalized.endsWith("contextcompactionstart") -> "contextCompactionStarted"
                normalized.endsWith("contextcompactioncompleted") || normalized.endsWith("contextcompactioncomplete") -> "contextCompactionCompleted"
                normalized.endsWith("contextcompactionfailed") -> "contextCompactionFailed"
                normalized.endsWith("contextcompactioncancelled") || normalized.endsWith("contextcompactioncanceled") -> "contextCompactionCancelled"
                normalized.endsWith("contextcompacted") -> "contextCompactionCompleted"
                normalized.endsWith("tokenusageupdated") || normalized.endsWith("tokenusageupdate") -> "tokenUsageUpdated"
                normalized.endsWith("turncompleted") || normalized.endsWith("turncomplete") -> "turnCompleted"
                else -> raw
            }
        }
    }
}
