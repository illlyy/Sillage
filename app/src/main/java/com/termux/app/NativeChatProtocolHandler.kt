package com.termux.app

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import android.view.Choreographer
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.termux.R
import com.termux.app.update.AppUpdateManager
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject


    internal fun CodexChatActivity.resetProtocolTurnState(preserveSequenceWatermarks: Boolean = true) {
        drainPendingNativeUiEvents()
        legacyPendingStreamScope.advanceEpoch()
        if (preserveSequenceWatermarks) protocolEventQueue.clearPendingPreservingWatermarks()
        else protocolEventQueue.clear()
        currentThreadId?.let { threadId ->
            NativeChatLifecycleHandoff.remove(threadId)
            if (lifecycleHandoffRouteThreadId == threadId) lifecycleHandoffRouteThreadId = ""
        }
        currentCompactionPolicy().reset()
        compactionLifecycleTimeouts.values.forEach(streamHandler::removeCallbacks)
        compactionLifecycleTimeouts.clear()
        resetProtocolFeatureSeenForTurn()
        lastProtocolCompletedTurnId = ""
        lastReliableUsage = null
    }

    internal fun CodexChatActivity.resetProtocolFeatureSeenForTurn() {
        protocolReasoningSeen = false
        protocolAssistantSeen = false
        protocolCommandSeen = false
        protocolPlanSeen = false
        protocolToolSeen = false
        protocolSubagentSeen = false
        protocolUsageSeen = false
        protocolTurnCompletedSeen = false
    }

    internal fun CodexChatActivity.claimTurnCompletion(threadId: String, turnId: String): Boolean {
        val stableTurn = turnId.ifBlank { "legacy:${chatState.turnStartedAt}" }
        val key = "${threadId.ifBlank { "thread" }}:$stableTurn"
        if (!handledTurnCompletionKeys.add(key)) return false
        while (handledTurnCompletionKeys.size > 24) {
            val iterator = handledTurnCompletionKeys.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
        return true
    }

    internal fun CodexChatActivity.hasNativeContinuationPending(): Boolean {
        val activeGoal = !suppressGoalRetryUntilNewTurn &&
            chatState.activeGoalObjective.isNotBlank() && chatState.activeGoalStatus == "active"
        val activeSubagent = chatState.subagentStatuses.values.any { it == "working" || it == "waiting" }
        return chatState.queuedFollowUps.isNotEmpty() || goalRetryWaitingForCompletion ||
            activeGoal || activeSubagent
    }

    internal fun CodexChatActivity.syncNativeContinuationHint() {
        val threadId = currentThreadId?.takeIf { it.isNotBlank() } ?: return
        val activeBridge = bridge ?: return
        continuationHintRouter.sync(threadId, hasNativeContinuationPending()) { routedThreadId, pending ->
            activeBridge.setNativeContinuationPending(routedThreadId, pending)
        }
    }

    internal fun CodexChatActivity.detachNativeContinuationHintBeforeRouteChange(nextThreadId: String?) {
        val previousThreadId = currentThreadId?.takeIf { it.isNotBlank() } ?: return
        if (previousThreadId == nextThreadId?.trim()) return
        continuationHintRouter.leave(previousThreadId)
    }

    internal fun CodexChatActivity.protocolString(item: JSONObject?, vararg keys: String): String {
        if (item == null) return ""
        keys.forEach { key ->
            val value = item.optString(key, "").trim()
            if (value.isNotBlank() && !value.equals("null", true)) return value
        }
        return ""
    }

    internal fun CodexChatActivity.protocolLong(payload: JSONObject, key: String): Long {
        val snake = key.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
        return payload.optLong(key, payload.optLong(snake, payload.optLong("${key}Number", 0L)))
    }

    internal fun CodexChatActivity.protocolEventThread(payload: JSONObject): String {
        val direct = protocolString(payload, "threadId", "thread_id")
        if (direct.isNotBlank()) return direct
        val turn = payload.optJSONObject("turn")
        val item = payload.optJSONObject("item") ?: payload.optJSONObject("details")
        return protocolString(turn, "threadId", "thread_id")
            .ifBlank { protocolString(item, "threadId", "thread_id", "senderThreadId", "sender_thread_id") }
    }

    internal fun CodexChatActivity.protocolEventTurn(payload: JSONObject): String? {
        val direct = protocolString(payload, "turnId", "turn_id")
        if (direct.isNotBlank()) return direct
        val turn = payload.optJSONObject("turn")
        val item = payload.optJSONObject("item") ?: payload.optJSONObject("details")
        return protocolString(turn, "id", "turnId", "turn_id")
            .ifBlank { protocolString(item, "turnId", "turn_id") }
            .takeIf { it.isNotBlank() }
    }

    internal fun CodexChatActivity.protocolEventLocalEpoch(payload: JSONObject?): Long? {
        if (payload == null) return null
        val containers = listOfNotNull(payload, payload.optJSONObject("details"), payload.optJSONObject("turn"))
        val keys = arrayOf("nativeTurnEpoch", "native_turn_epoch", "localTurnEpoch", "local_turn_epoch")
        containers.forEach { item ->
            keys.forEach { key ->
                if (item.has(key) && !item.isNull(key)) return item.optLong(key)
            }
        }
        return null
    }

    internal fun CodexChatActivity.protocolCompactionSource(payload: JSONObject?, item: JSONObject?): NativeCompactionSource? {
        val raw = protocolString(item, "source", "origin", "trigger")
            .ifBlank { protocolString(payload, "source", "origin", "trigger") }
            .lowercase()
        return when (raw) {
            "manual", "user", "interactive" -> NativeCompactionSource.MANUAL
            "automatic", "auto", "server", "fallback" -> NativeCompactionSource.AUTOMATIC
            "legacy" -> NativeCompactionSource.LEGACY
            else -> null
        }
    }

    internal fun CodexChatActivity.isFinalAssistantItem(item: JSONObject?): Boolean {
        if (item == null) return false
        val phase = protocolString(item, "phase", "itemPhase", "item_phase")
            .replace("-", "_").replace(" ", "_").lowercase(Locale.ROOT)
        return phase in setOf("final_answer", "finalanswer", "final", "answer") ||
            item.optBoolean("final", false) || item.optBoolean("isFinal", false)
    }

    internal fun CodexChatActivity.enqueueProtocolEvent(event: NativeProtocolEvent, immediate: Boolean = false) {
        // Legacy callbacks do not carry a sequence.  Flush any normalized deltas already waiting
        // for the same UI batch before accepting the unsequenced barrier, otherwise a later queue
        // drain can move an older reasoning/command chunk after its completion callback.
        if (event.sequence <= 0L) flushProtocolEventQueue()
        val ready = protocolEventQueue.offer(event)
        if (ready.isNotEmpty()) ready.forEach(chatState::acceptNormalizedProtocolEvent)
        if (immediate) {
            streamHandler.removeCallbacks(flushProtocolEventQueueRunnable)
            flushProtocolEventQueue()
        } else if (ready.isEmpty()) {
            streamHandler.removeCallbacks(flushProtocolEventQueueRunnable)
            streamHandler.postDelayed(flushProtocolEventQueueRunnable, 12L)
        }
    }

    internal fun CodexChatActivity.flushProtocolEventQueue() {
        protocolEventQueue.drain().forEach(chatState::acceptNormalizedProtocolEvent)
    }

    /** Accept lifecycle recordings produced by retained/older bridges without duplicating their
     * protocol-specific parsing in the Compose state. */

    internal fun CodexChatActivity.handleDecodedLifecycleEvent(event: NativeProtocolEvent) {
        if (event.threadId.isNotBlank() && currentThreadId != null && event.threadId != currentThreadId) return
        val acceptedTurnStart = event is NativeProtocolEvent.TurnStarted &&
            chatState.shouldAcceptProtocolTurnStart(event.turnId)
        when (event) {
            is NativeProtocolEvent.CompactionStarted -> {
                enqueueProtocolEvent(event, immediate = true)
                chatState.compactionItems.firstOrNull { it.serverItemId == event.itemId || it.id == event.itemId }
                    ?.let { item ->
                        cancelCompactionRequestTimeout(item.requestId)
                        compactionJournalStore.record(item)
                    }
                currentCompactionPolicy().markStarted(event.threadId, event.turnId)
                scheduleCompactionLifecycleTimeout(event)
            }
            is NativeProtocolEvent.CompactionCompleted,
            is NativeProtocolEvent.CompactionFailed -> {
                enqueueProtocolEvent(event, immediate = true)
                chatState.compactionItems.lastOrNull { event.itemId.isNullOrBlank() || it.serverItemId == event.itemId || it.id == event.itemId }
                    ?.let { item ->
                        cancelCompactionRequestTimeout(item.requestId)
                        compactionJournalStore.record(item)
                        if (item.status == NativeCompactionStatus.COMPLETED) {
                            // Completion items do not carry post-compaction usage. Keep the latch
                            // until the next reliable token update proves the context actually fell.
                            currentCompactionPolicy().markCompleted(event.threadId, event.turnId)
                        } else currentCompactionPolicy().markFailed(event.threadId, event.turnId)
                    }
                cancelCompactionLifecycleTimeout(event)
            }
            is NativeProtocolEvent.TokenUsageUpdated -> {
                enqueueProtocolEvent(event, immediate = true)
                evaluateAutomaticCompaction(
                    NativeTurnUsage(
                        inputTokens = event.inputTokens,
                        cachedInputTokens = event.cachedInputTokens,
                        outputTokens = event.outputTokens,
                        reasoningOutputTokens = event.reasoningTokens,
                        currentContextTokens = event.currentContextTokens,
                        contextWindow = event.contextWindow,
                        estimated = event.estimated,
                        contextUsageReliable = event.contextUsageReliable,
                        autoCompactTokenLimit = event.autoCompactTokenLimit,
                    ),
                )
            }
            else -> {
                when (event) {
                    is NativeProtocolEvent.TurnStarted -> {
                        if (acceptedTurnStart) resetProtocolFeatureSeenForTurn()
                    }
                    is NativeProtocolEvent.ReasoningDelta,
                    is NativeProtocolEvent.ReasoningCompleted -> protocolReasoningSeen = true
                    is NativeProtocolEvent.AssistantDelta,
                    is NativeProtocolEvent.AssistantCompleted -> protocolAssistantSeen = true
                    is NativeProtocolEvent.CommandStarted,
                    is NativeProtocolEvent.CommandCompleted -> protocolCommandSeen = true
                    is NativeProtocolEvent.PlanStarted,
                    is NativeProtocolEvent.PlanDelta,
                    is NativeProtocolEvent.PlanCompleted -> protocolPlanSeen = true
                    is NativeProtocolEvent.ToolCompleted -> protocolToolSeen = true
                    is NativeProtocolEvent.SubagentUpdated -> protocolSubagentSeen = true
                    is NativeProtocolEvent.TurnCompleted -> {
                        val completedTurn = event.turnId.orEmpty()
                        if (completedTurn.isBlank() || chatState.currentTurnId.isBlank() || completedTurn == chatState.currentTurnId) {
                            protocolTurnCompletedSeen = true
                            lastProtocolCompletedTurnId = completedTurn.ifBlank { chatState.currentTurnId }
                        }
                    }
                    else -> Unit
                }
                enqueueProtocolEvent(event, immediate = true)
                if (event is NativeProtocolEvent.SubagentUpdated) syncNativeContinuationHint()
                if (event is NativeProtocolEvent.PlanCompleted) {
                    val planText = chatState.messages.lastOrNull {
                        it.role == NativeChatRole.ACTIVITY && it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX)
                    }?.let { decodeNativeProposedPlan(it.content) }.orEmpty()
                    currentThreadId?.takeIf { planText.isNotBlank() }
                        ?.let { persistPendingPlanImplementation(it, planText) }
                }
            }
        }
    }

    internal fun CodexChatActivity.scheduleCompactionLifecycleTimeout(event: NativeProtocolEvent.CompactionStarted) {
        val timeoutKey = event.itemId ?: "${event.threadId}:${event.turnId.orEmpty()}"
        compactionLifecycleTimeouts.remove(timeoutKey)?.let(streamHandler::removeCallbacks)
        val timeout = Runnable {
            if (chatState.compactionItems.any {
                    (event.itemId.isNullOrBlank() || it.serverItemId == event.itemId || it.id == event.itemId) &&
                        it.status == NativeCompactionStatus.RUNNING
                }) {
                NativeChatDiagnostics.record(this, "compaction_lifecycle_timeout", JSONObject()
                    .put("thread", event.threadId.take(8)).put("itemId", event.itemId.orEmpty()))
            }
        }
        compactionLifecycleTimeouts[timeoutKey] = timeout
        streamHandler.postDelayed(timeout, 60_000L)
    }

    internal fun CodexChatActivity.cancelCompactionLifecycleTimeout(event: NativeProtocolEvent) {
        val timeoutKey = event.itemId ?: "${event.threadId}:${event.turnId.orEmpty()}"
        compactionLifecycleTimeouts.remove(timeoutKey)?.let(streamHandler::removeCallbacks)
    }

    internal fun CodexChatActivity.canonicalProtocolKind(raw: String): String {
        val normalized = raw.replace("_", "").replace("-", "").replace("/", "").replace(".", "").lowercase(Locale.ROOT)
        return when (normalized) {
            "itemstarted", "itemstart" -> "itemStarted"
            "itemcompleted", "itemcomplete" -> "itemCompleted"
            "contextcompacted", "contextcompaction" -> "contextCompactionCompleted"
            "contextcompactionstarted", "contextcompactionstart" -> "contextCompactionStarted"
            "contextcompactioncompleted", "contextcompactioncomplete" -> "contextCompactionCompleted"
            "contextcompactionfailed" -> "contextCompactionFailed"
            "contextcompactioncancelled", "contextcompactioncanceled" -> "contextCompactionCancelled"
            "reasoningdelta" -> "reasoningDelta"
            "reasoningcompleted", "reasoningcomplete" -> "reasoningCompleted"
            "assistantdelta" -> "assistantDelta"
            "assistantstarted", "assistantstart" -> "assistantStarted"
            "assistantcompleted", "assistantcomplete" -> "assistantCompleted"
            "commandstarted", "commandstart" -> "commandStarted"
            "commandoutput", "commanddelta" -> "commandOutput"
            "commandcompleted", "commandcomplete" -> "commandCompleted"
            "toolcompleted", "toolcomplete" -> "toolCompleted"
            "planstarted", "planstart" -> "planStarted"
            "plandelta" -> "planDelta"
            "plancompleted", "plancomplete" -> "planCompleted"
            "subagentupdated", "subagentupdate" -> "subagentUpdated"
            "tokenusageupdated", "tokenusageupdate" -> "tokenUsageUpdated"
            "turnstarted", "turnstart" -> "turnStarted"
            "turncompleted", "turncomplete" -> "turnCompleted"
            "error" -> "error"
            else -> raw
        }
    }

    internal fun CodexChatActivity.handleProtocolEvent(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val kind = canonicalProtocolKind(
            payload.optString("kind").ifBlank {
                payload.optString("event_kind").ifBlank {
                    payload.optString("method").ifBlank { payload.optString("event", payload.optString("type")) }
                }
            },
        )
        val threadId = protocolEventThread(payload).ifBlank { currentThreadId.orEmpty() }
        if (currentThreadId != null && threadId.isNotBlank() && threadId != currentThreadId) return
        val turnId = protocolEventTurn(payload)
        val acceptedTurnStart = kind == "turnStarted" && chatState.shouldAcceptProtocolTurnStart(turnId)
        val item = payload.optJSONObject("item") ?: payload.optJSONObject("details")
        val itemId = protocolString(payload, "itemId", "item_id")
            .ifBlank { protocolString(item, "id", "itemId", "item_id") }
            .takeIf { it.isNotBlank() }
        val sequence = protocolLong(payload, "sequence")
        val timestamp = payload.optLong("timestampMs", payload.optLong("timestamp_ms", System.currentTimeMillis()))
        if (kind in setOf("itemStarted", "itemCompleted")) {
            val decoded = NativeProtocolEventDecoder.decodeLifecycle(raw, threadId)
            if (decoded != null) {
                handleDecodedLifecycleEvent(decoded)
                return
            }
        }
        when (kind) {
            "contextCompactionStarted" -> {
                val event = NativeProtocolEvent.CompactionStarted(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    source = protocolCompactionSource(payload, item),
                    requestId = protocolString(item, "requestId", "request_id")
                        .ifBlank { protocolString(payload, "requestId", "request_id") }
                        .takeIf { it.isNotBlank() },
                    sequence = sequence,
                    timestampMs = timestamp,
                )
                enqueueProtocolEvent(event, immediate = true)
                val visual = chatState.compactionItems.firstOrNull { it.serverItemId == itemId || it.id == itemId }
                visual?.let {
                    cancelCompactionRequestTimeout(it.requestId)
                    compactionJournalStore.record(it)
                }
                currentCompactionPolicy().markStarted(threadId, turnId)
                val timeoutKey = itemId ?: "${threadId}:${turnId.orEmpty()}"
                compactionLifecycleTimeouts.remove(timeoutKey)?.let(streamHandler::removeCallbacks)
                val timeout = Runnable {
                    if (chatState.compactionItems.any { (it.serverItemId == itemId || it.id == itemId || itemId.isNullOrBlank()) && it.status == NativeCompactionStatus.RUNNING }) {
                        NativeChatDiagnostics.record(this, "compaction_lifecycle_timeout", JSONObject()
                            .put("thread", threadId.take(8)).put("itemId", itemId.orEmpty()))
                    }
                }
                compactionLifecycleTimeouts[timeoutKey] = timeout
                streamHandler.postDelayed(timeout, 60_000L)
            }
            "contextCompactionCompleted", "contextCompactionFailed", "contextCompactionCancelled" -> {
                val error = protocolString(item, "error", "message").takeIf { it.isNotBlank() }
                val status = protocolString(item, "status", "state").lowercase()
                val cancelled = kind == "contextCompactionCancelled" || status in setOf("cancelled", "canceled")
                val failed = kind == "contextCompactionFailed" || status in setOf("failed", "error", "failure")
                val event: NativeProtocolEvent = if (failed || cancelled) {
                    NativeProtocolEvent.CompactionFailed(
                        threadId = threadId,
                        turnId = turnId,
                        itemId = itemId,
                        error = error.orEmpty(),
                        cancelled = cancelled,
                        source = protocolCompactionSource(payload, item),
                        requestId = protocolString(item, "requestId", "request_id")
                            .ifBlank { protocolString(payload, "requestId", "request_id") }
                            .takeIf { it.isNotBlank() },
                        sequence = sequence,
                        timestampMs = timestamp,
                    )
                } else NativeProtocolEvent.CompactionCompleted(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    error = error,
                    cancelled = false,
                    source = protocolCompactionSource(payload, item),
                    requestId = protocolString(item, "requestId", "request_id")
                        .ifBlank { protocolString(payload, "requestId", "request_id") }
                        .takeIf { it.isNotBlank() },
                    sequence = sequence,
                    timestampMs = timestamp,
                )
                enqueueProtocolEvent(event, immediate = true)
                val visual = chatState.compactionItems.lastOrNull { itemId.isNullOrBlank() || it.serverItemId == itemId || it.id == itemId }
                visual?.let {
                    cancelCompactionRequestTimeout(it.requestId)
                    compactionJournalStore.record(it)
                    if (it.status == NativeCompactionStatus.COMPLETED) {
                        currentCompactionPolicy().markCompleted(threadId, turnId)
                    } else currentCompactionPolicy().markFailed(threadId, turnId)
                }
                val timeoutKey = itemId ?: "${threadId}:${turnId.orEmpty()}"
                compactionLifecycleTimeouts.remove(timeoutKey)?.let(streamHandler::removeCallbacks)
            }
            "commandStarted" -> enqueueProtocolEvent(
                NativeProtocolEvent.CommandStarted(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    command = protocolString(item, "command", "cmd"),
                    cwd = protocolString(item, "cwd", "workingDirectory", "working_directory"),
                    payload = item?.toString().orEmpty(),
                    sequence = sequence,
                    timestampMs = timestamp,
                ),
                immediate = true,
            )
            "commandCompleted" -> enqueueProtocolEvent(
                NativeProtocolEvent.CommandCompleted(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    command = protocolString(item, "command", "cmd"),
                    outputRef = protocolString(item, NativeCommandOutputStore.OUTPUT_REF),
                    status = protocolString(item, "status").ifBlank { "completed" },
                    exitCode = item?.optInt("exitCode")?.takeIf { item.has("exitCode") },
                    payload = item?.toString().orEmpty(),
                    sequence = sequence,
                    timestampMs = timestamp,
                ),
                immediate = true,
            )
            "reasoningCompleted" -> enqueueProtocolEvent(
                NativeProtocolEvent.ReasoningCompleted(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    text = protocolString(item, "text", "summary"),
                    sequence = sequence,
                    timestampMs = timestamp,
                ),
                immediate = true,
            )
            "assistantStarted", "assistantCompleted" -> enqueueProtocolEvent(
                if (kind == "assistantCompleted") NativeProtocolEvent.AssistantCompleted(
                    threadId, turnId, itemId, text = protocolString(item, "text", "content"),
                    finalAnswer = isFinalAssistantItem(item), sequence = sequence, timestampMs = timestamp,
                ) else NativeProtocolEvent.AssistantDelta(threadId, turnId, itemId, delta = "", sequence = sequence, timestampMs = timestamp),
                immediate = true,
            )
            "planStarted" -> enqueueProtocolEvent(NativeProtocolEvent.PlanStarted(threadId, turnId, itemId, sequence = sequence, timestampMs = timestamp), immediate = true)
            "planCompleted" -> {
                enqueueProtocolEvent(
                    NativeProtocolEvent.PlanCompleted(
                        threadId, turnId, itemId, text = protocolString(item, "text", "content"),
                        sequence = sequence, timestampMs = timestamp,
                    ),
                    immediate = true,
                )
                val planText = chatState.messages.lastOrNull {
                    it.role == NativeChatRole.ACTIVITY && it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX)
                }?.let { decodeNativeProposedPlan(it.content) }.orEmpty()
                currentThreadId?.takeIf { planText.isNotBlank() }
                    ?.let { persistPendingPlanImplementation(it, planText) }
            }
            "toolCompleted" -> enqueueProtocolEvent(
                NativeProtocolEvent.ToolCompleted(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    type = payload.optString("itemType", item?.optString("type", "tool").orEmpty()),
                    title = protocolString(item, "tool", "name", "query", "agentName"),
                    payloadRef = protocolString(item, NativeLargePayloadStore.PAYLOAD_REF),
                    status = protocolString(item, "status").ifBlank { "completed" },
                    payload = item?.toString().orEmpty(),
                    sequence = sequence,
                    timestampMs = timestamp,
                ),
                immediate = true,
            )
            "subagentUpdated" -> enqueueProtocolEvent(
                NativeProtocolEvent.SubagentUpdated(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    agentThreadId = item?.let(::subagentThreadId).orEmpty(),
                    callId = protocolString(item, "callId", "call_id"),
                    name = protocolString(item, "agentName", "agentNickname", "nickname"),
                    status = protocolString(item, "status").ifBlank { "working" },
                    sequence = sequence,
                    timestampMs = timestamp,
                ),
                immediate = true,
            )
            "tokenUsageUpdated" -> {
                val usage = item ?: JSONObject()
                val parsed = NativeTokenUsageParser.parse(usage.toString(), 0L)
                if (parsed != null) {
                    enqueueProtocolEvent(
                        NativeProtocolEvent.TokenUsageUpdated(
                            threadId = threadId,
                            turnId = turnId,
                            itemId = itemId,
                            inputTokens = parsed.inputTokens,
                            cachedInputTokens = parsed.cachedInputTokens,
                            outputTokens = parsed.outputTokens,
                            reasoningTokens = parsed.reasoningOutputTokens,
                            currentContextTokens = parsed.currentContextTokens,
                            contextWindow = parsed.contextWindow,
                            estimated = parsed.estimated,
                            contextUsageReliable = parsed.contextUsageReliable,
                            autoCompactTokenLimit = parsed.autoCompactTokenLimit,
                            sequence = sequence,
                            timestampMs = timestamp,
                        ),
                        immediate = true,
                    )
                    evaluateAutomaticCompaction(parsed)
                }
            }
            "turnStarted" -> enqueueProtocolEvent(
                NativeProtocolEvent.TurnStarted(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    sequence = sequence,
                    timestampMs = timestamp,
                ),
                immediate = true,
            )
            "turnCompleted" -> enqueueProtocolEvent(
                NativeProtocolEvent.TurnCompleted(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    failed = nativeTurnLifecycleFailed(
                        item,
                        payload.optJSONObject("turn"),
                        payload.optJSONObject("details"),
                        payload,
                    ),
                    sequence = sequence,
                    timestampMs = timestamp,
                ),
                immediate = true,
            )
            "error" -> enqueueProtocolEvent(
                NativeProtocolEvent.Error(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    message = protocolString(item, "message", "error").ifBlank { payload.optString("message") },
                    sequence = sequence,
                    timestampMs = timestamp,
                ),
                immediate = true,
            )
        }
        if (kind.startsWith("reasoning")) protocolReasoningSeen = true
        if (kind.startsWith("assistant")) protocolAssistantSeen = true
        if (kind.startsWith("command")) protocolCommandSeen = true
        if (kind.startsWith("plan")) protocolPlanSeen = true
        if (kind.startsWith("tool")) protocolToolSeen = true
        if (kind.startsWith("subagent")) protocolSubagentSeen = true
        if (kind.startsWith("tokenUsage")) protocolUsageSeen = true
        if (acceptedTurnStart) resetProtocolFeatureSeenForTurn()
        if (kind == "turnCompleted") {
            val completedTurn = turnId.orEmpty()
            if (completedTurn.isBlank() || chatState.currentTurnId.isBlank() || completedTurn == chatState.currentTurnId) {
                protocolTurnCompletedSeen = true
                lastProtocolCompletedTurnId = completedTurn.ifBlank { chatState.currentTurnId }
            }
        }
        if (kind.startsWith("subagent")) syncNativeContinuationHint()
    }

    internal fun CodexChatActivity.handleProtocolDelta(raw: String) {
        NativeProtocolEventDecoder.decodeDeltas(raw, currentThreadId.orEmpty()).forEach { event ->
            if (currentThreadId != null && event.threadId.isNotBlank() && event.threadId != currentThreadId) return@forEach
            when (event) {
                is NativeProtocolEvent.ReasoningDelta -> protocolReasoningSeen = true
                is NativeProtocolEvent.AssistantDelta -> protocolAssistantSeen = true
                is NativeProtocolEvent.PlanDelta -> protocolPlanSeen = true
                else -> Unit
            }
            enqueueProtocolEvent(event)
        }
    }

    internal fun CodexChatActivity.handleCommandDeltaV2(raw: String) {
        NativeProtocolEventDecoder.decodeCommandOutputs(raw, currentThreadId.orEmpty()).forEach { event ->
            if (currentThreadId != null && event.threadId.isNotBlank() && event.threadId != currentThreadId) return@forEach
            protocolCommandSeen = true
            enqueueProtocolEvent(event)
        }
    }

    internal fun CodexChatActivity.handleCompactionRpcResult(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val requestId = protocolString(payload, "requestId", "request_id").takeIf { it.isNotBlank() }
        val status = protocolString(payload, "status", "state").lowercase(Locale.ROOT)
        val success = if (payload.has("success")) payload.optBoolean("success", false)
        else status in setOf("completed", "complete", "success", "succeeded", "ok")
        val cancelled = status in setOf("cancelled", "canceled", "cancel", "aborted")
        val threadId = protocolString(payload, "threadId", "thread_id").ifBlank { currentThreadId.orEmpty() }
        if (success && !cancelled) {
            // thread/compact/start returns {} as an acknowledgement. WebUI waits for the actual
            // contextCompaction item/completed lifecycle before presenting success; do the same.
            NativeChatDiagnostics.record(this, "compaction_rpc_acknowledged", JSONObject()
                .put("thread", threadId.take(8)).put("requestId", requestId.orEmpty().takeLast(24)))
            return
        }
        cancelCompactionRequestTimeout(requestId)
        val updated = chatState.completeManualCompactionRpc(
            requestId,
            success = false,
            cancelled = cancelled,
            error = protocolString(payload, "error", "message"),
            threadId = threadId,
        )
        updated?.let(compactionJournalStore::record)
        currentCompactionPolicy().markFailed(threadId, updated?.turnId ?: chatState.currentTurnId)
    }

    internal fun CodexChatActivity.handleTurnError(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val error = payload.optJSONObject("error")
        val message = error?.optString("message").orEmpty().ifBlank { payload.optString("message") }
        val willRetry = payload.optBoolean("willRetry", false)
        val automaticGoal = canAutoRetryGoal()
        if (willRetry) {
            // app-server is still inside the same turn; keep the UI in a waiting state and update
            // the existing card in place. The retry limit is an internal provider retry, not a
            // reason to show five visually identical error cards.
            chatState.updateRetryStatus(message, automaticGoal, terminal = false)
            return
        }
        drainPendingNativeUiEvents()
        if (automaticGoal) {
            goalRetryCycleActive = true
            goalRetryWaitingForCompletion = true
            syncNativeContinuationHint()
            chatState.updateRetryStatus(message, automaticGoal = true, terminal = true)
            // Normally turn/completed arrives first. This fallback also recovers from older
            // app-server builds that only emit the final error notification.
            scheduleGoalRetry()
        } else {
            syncNativeContinuationHint()
            chatState.updateRetryStatus(message, automaticGoal = false, terminal = true)
            stopFrameDiagnostics()
            currentThreadId?.let { threadId ->
                NativeTaskNotificationManager.notifyEvent(this, threadId, NativeTaskNotificationPolicy.FAILED, message.hashCode().toString(), "")
            }
        }
    }

