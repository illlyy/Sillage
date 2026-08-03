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


    internal fun CodexChatActivity.discardPendingStreamEvents(reason: String) {
        streamHandler.removeCallbacks(flushReasoningRunnable)
        streamHandler.removeCallbacks(flushAnswerRunnable)
        streamHandler.removeCallbacks(flushPlanRunnable)
        streamHandler.removeCallbacks(flushCommandRunnable)
        reasoningFlushScheduled = false
        answerFlushScheduled = false
        planFlushScheduled = false
        commandFlushScheduled = false
        reasoningMotionDeferSince = 0L
        answerMotionDeferSince = 0L
        reasoningLastFlushAtMs = 0L
        answerLastFlushAtMs = 0L
        val droppedReasoning = pendingReasoning.length
        val droppedAnswer = pendingAnswer.length
        val droppedPlan = pendingPlan.length
        val droppedCommand = pendingCommand.length
        pendingReasoning.setLength(0)
        pendingAnswer.setLength(0)
        pendingPlan.setLength(0)
        pendingCommand.setLength(0)
        pendingPlanItemId = ""
        legacyPendingStreamScope.clearPending()
        // Invalidate a catch-up callback posted by the route we just left.
        uiMotionCatchUpGeneration++
        stopFrameDiagnostics()
        NativeChatDiagnostics.record(this, "stream_route_reset", JSONObject()
            .put("reason", reason).put("droppedReasoning", droppedReasoning).put("droppedAnswer", droppedAnswer)
            .put("droppedPlan", droppedPlan).put("droppedCommand", droppedCommand))
    }

    internal fun CodexChatActivity.hasPendingLegacyStreamEvents(): Boolean =
        pendingReasoning.isNotEmpty() || pendingAnswer.isNotEmpty() ||
            pendingPlan.isNotEmpty() || pendingCommand.isNotEmpty()

    internal fun CodexChatActivity.captureLegacyPendingStreamScope() {
        if (legacyPendingStreamScope.capture(currentThreadId.orEmpty(), chatState.currentTurnId)) return
        // A callback from another turn must never share a StringBuilder with the visible turn.
        discardPendingStreamEvents("legacy_scope_changed")
        legacyPendingStreamScope.capture(currentThreadId.orEmpty(), chatState.currentTurnId)
    }

    internal fun CodexChatActivity.releaseLegacyPendingStreamScopeIfIdle() {
        if (!hasPendingLegacyStreamEvents()) legacyPendingStreamScope.clearPending()
    }

    internal fun CodexChatActivity.rejectMismatchedLegacyStreamFlush(): Boolean {
        if (legacyPendingStreamScope.canDrain(currentThreadId.orEmpty(), chatState.currentTurnId)) return false
        discardPendingStreamEvents("legacy_flush_scope_mismatch")
        return true
    }

    internal fun CodexChatActivity.consumeStreamChunk(
        buffer: StringBuilder,
        force: Boolean,
        limit: Int = CodexChatActivity.STREAM_CATCH_UP_CHUNK_CHARS,
    ): String {
        var end = if (force) buffer.length else minOf(buffer.length, limit)
        if (end in 1 until buffer.length && Character.isHighSurrogate(buffer[end - 1])) end--
        val chunk = buffer.substring(0, end)
        buffer.delete(0, end)
        return chunk
    }

    /**
     * Smooth reveal cap: pace a large buffered dump (whole-answer from the Claude relay) out over
     * time instead of releasing it in 1_200-char pages. Real per-token streaming keeps a small
     * backlog so the cap never binds; completion (force) still drains everything.
     */
    internal fun CodexChatActivity.streamRevealLimit(force: Boolean, lastFlushAt: Long): Pair<Int, Long> {
        val now = android.os.SystemClock.uptimeMillis()
        val elapsedMs = if (lastFlushAt == 0L) 32L else (now - lastFlushAt).coerceIn(16L, 250L)
        val limit = if (force) Int.MAX_VALUE else maxOf(
            (CodexChatActivity.STREAM_REVEAL_CHARS_PER_SEC * elapsedMs / 1000).toInt(),
            CodexChatActivity.MIN_STREAM_REVEAL_PER_FLUSH,
        )
        return limit to now
    }

    internal fun CodexChatActivity.flushReasoningDeltas(force: Boolean = false) {
        streamHandler.removeCallbacks(flushReasoningRunnable)
        reasoningFlushScheduled = false
        if (pendingReasoning.isEmpty()) {
            reasoningPendingSince = 0L
            reasoningMotionDeferSince = 0L
            reasoningLastFlushAtMs = 0L
            releaseLegacyPendingStreamScopeIfIdle()
            return
        }
        if (rejectMismatchedLegacyStreamFlush()) {
            reasoningMotionDeferSince = 0L
            return
        }
        if (NativeUiRenderSafety.shouldDeferStreamFlushForUiMotion(uiMotionActive, force)) {
            val now = android.os.SystemClock.uptimeMillis()
            if (reasoningMotionDeferSince == 0L) {
                reasoningMotionDeferSince = now
            } else if (now - reasoningMotionDeferSince >= CodexChatActivity.MOTION_DEFER_CAP_MS) {
                // Motion has held publication for too long (e.g. a long auto-follow run). Break the
                // cap so a live turn never strands its tail behind a permanently active scroll.
                reasoningMotionDeferSince = 0L
            } else {
                reasoningFlushScheduled = true
                streamHandler.postDelayed(flushReasoningRunnable, 32L)
                return
            }
        }
        val now = android.os.SystemClock.uptimeMillis()
        val boundary = pendingReasoning.lastOrNull()?.let { it in charArrayOf('\n', '.', '!', '?', '?', '?', '?') } == true
        if (NativeUiRenderSafety.shouldDeferStreamFlush(pendingReasoning.length, boundary, now - reasoningPendingSince, force)) {
            reasoningFlushScheduled = true
            streamHandler.postDelayed(flushReasoningRunnable, 32L)
            return
        }
        val reveal = streamRevealLimit(force, reasoningLastFlushAtMs)
        reasoningLastFlushAtMs = reveal.second
        val value = consumeStreamChunk(pendingReasoning, force, reveal.first)
        reasoningMotionDeferSince = 0L
        if (pendingReasoning.isEmpty()) {
            reasoningPendingSince = 0L
        } else {
            reasoningFlushScheduled = true
            streamHandler.postDelayed(flushReasoningRunnable, CodexChatActivity.STREAM_CATCH_UP_DELAY_MS)
        }
        if (!protocolReasoningSeen) {
            val continuedAfterAnswer = chatState.reasoningComplete
            chatState.beginReasoningAfterAnswerIfNeeded()
            if (continuedAfterAnswer) NativeChatDiagnostics.record(this, "reasoning_segment_started", JSONObject()
                .put("messageIndex", chatState.messages.size))
            chatState.appendReasoning(value)
            chatState.acceptProtocolEvent(NativeProtocolEvent.ReasoningDelta(
                threadId = currentThreadId.orEmpty(),
                turnId = chatState.currentTurnId.takeIf { it.isNotBlank() },
                delta = value,
            ))
        }
        releaseLegacyPendingStreamScopeIfIdle()
    }

    internal fun CodexChatActivity.answerFlushDelayMs(): Long {
        val liveChars = chatState.liveAssistantSnapshot.sourceChars
        return NativeUiRenderSafety.streamFlushDelayMs(liveChars, pendingAnswer.length, 88L)
    }

    internal fun CodexChatActivity.reasoningFlushDelayMs(): Long =
        NativeUiRenderSafety.streamFlushDelayMs(chatState.reasoningText.length, pendingReasoning.length, 88L)

    internal fun CodexChatActivity.flushAnswerDeltas(force: Boolean = false) {
        streamHandler.removeCallbacks(flushAnswerRunnable)
        answerFlushScheduled = false
        if (pendingAnswer.isEmpty()) {
            answerPendingSince = 0L
            answerMotionDeferSince = 0L
            answerLastFlushAtMs = 0L
            releaseLegacyPendingStreamScopeIfIdle()
            return
        }
        if (rejectMismatchedLegacyStreamFlush()) {
            answerMotionDeferSince = 0L
            return
        }
        if (NativeUiRenderSafety.shouldDeferStreamFlushForUiMotion(uiMotionActive, force)) {
            val now = android.os.SystemClock.uptimeMillis()
            if (answerMotionDeferSince == 0L) {
                answerMotionDeferSince = now
            } else if (now - answerMotionDeferSince >= CodexChatActivity.MOTION_DEFER_CAP_MS) {
                // Motion has held publication for too long (e.g. a long auto-follow run). Break the
                // cap so a live turn never strands its tail behind a permanently active scroll.
                answerMotionDeferSince = 0L
            } else {
                answerFlushScheduled = true
                streamHandler.postDelayed(flushAnswerRunnable, 32L)
                return
            }
        }
        val now = android.os.SystemClock.uptimeMillis()
        val boundary = pendingAnswer.lastOrNull()?.let { it in charArrayOf('\n', '.', '!', '?', '?', '?', '?') } == true
        if (NativeUiRenderSafety.shouldDeferStreamFlush(pendingAnswer.length, boundary, now - answerPendingSince, force)) {
            answerFlushScheduled = true
            streamHandler.postDelayed(flushAnswerRunnable, 32L)
            return
        }
        val reveal = streamRevealLimit(force, answerLastFlushAtMs)
        answerLastFlushAtMs = reveal.second
        val value = consumeStreamChunk(pendingAnswer, force, reveal.first)
        answerMotionDeferSince = 0L
        if (pendingAnswer.isEmpty()) {
            answerPendingSince = 0L
        } else {
            answerFlushScheduled = true
            streamHandler.postDelayed(flushAnswerRunnable, CodexChatActivity.STREAM_CATCH_UP_DELAY_MS)
        }
        if (!protocolAssistantSeen) {
            chatState.finishReasoning()
            chatState.appendAssistant(value)
            chatState.acceptProtocolEvent(NativeProtocolEvent.AssistantDelta(
                threadId = currentThreadId.orEmpty(),
                turnId = chatState.currentTurnId.takeIf { it.isNotBlank() },
                delta = value,
            ))
        }
        releaseLegacyPendingStreamScopeIfIdle()
    }

    internal fun CodexChatActivity.commandFlushDelayMs(): Long =
        NativeUiRenderSafety.streamFlushDelayMs(chatState.commandOutputLength(), pendingCommand.length, 72L)

    internal fun CodexChatActivity.flushCommandDeltas() {
        streamHandler.removeCallbacks(flushCommandRunnable)
        commandFlushScheduled = false
        if (pendingCommand.isEmpty()) {
            releaseLegacyPendingStreamScopeIfIdle()
            return
        }
        if (rejectMismatchedLegacyStreamFlush()) return
        val delta = pendingCommand.toString()
        pendingCommand.setLength(0)
        if (!protocolCommandSeen) {
            chatState.appendCommandOutput(delta)
            val live = runCatching { JSONObject(chatState.liveCommandJson) }.getOrNull()
            chatState.acceptProtocolEvent(
                NativeProtocolEvent.CommandOutput(
                    threadId = currentThreadId.orEmpty(),
                    turnId = chatState.currentTurnId.takeIf { it.isNotBlank() },
                    itemId = live?.optString("id")?.takeIf { it.isNotBlank() },
                    delta = delta,
                ),
            )
        }
        releaseLegacyPendingStreamScopeIfIdle()
    }

    internal fun CodexChatActivity.flushPlanDeltas() {
        streamHandler.removeCallbacks(flushPlanRunnable)
        planFlushScheduled = false
        if (pendingPlan.isEmpty()) {
            releaseLegacyPendingStreamScopeIfIdle()
            return
        }
        if (rejectMismatchedLegacyStreamFlush()) return
        val delta = pendingPlan.toString()
        pendingPlan.setLength(0)
        chatState.appendProposedPlanDelta(JSONObject().put("itemId", pendingPlanItemId).put("delta", delta).toString())
        releaseLegacyPendingStreamScopeIfIdle()
    }

