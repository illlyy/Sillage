package com.termux.app

import android.app.ActivityOptions
import android.content.Context.MODE_PRIVATE
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


internal data class PendingNativeSteer(
    val messageId: String,
    val followUp: NativeQueuedFollowUp,
)


    internal fun CodexChatActivity.editMessage(messageId: String, text: String) {
        val value = text.trim()
        if (value.isEmpty() || chatState.busy || compactionInProgress() || !chatState.ready) return
        currentThreadId?.let(NativeHistorySnapshotCache::remove)
        val userIndex = chatState.messages.indexOfFirst { it.id == messageId && it.role == NativeChatRole.USER }
        if (userIndex < 0) return
        val rollbackTurns = chatState.messages.drop(userIndex).count { it.role == NativeChatRole.USER }.coerceAtLeast(1)
        chatState.messages[userIndex] = chatState.messages[userIndex].copy(content = value)
        while (chatState.messages.size > userIndex + 1) chatState.messages.removeAt(chatState.messages.lastIndex)
        chatState.messages.add(NativeChatMessage(role = NativeChatRole.ACTIVITY, content = "NOTICE|已从此处重新生成"))
        resetProtocolTurnState()
        chatState.prepareReplacementTurn()
        startFrameDiagnostics()
        bridge?.editTurn(value, chatState.selectedModel, chatState.selectedEffort, rollbackTurns)
    }

    internal fun CodexChatActivity.retryMessage(text: String, preserveRetryStatus: Boolean = false) {
        val displayValue = text.trim()
        if (displayValue.isEmpty() || chatState.busy || compactionInProgress() || !chatState.ready) return
        if (!preserveRetryStatus) {
            cancelGoalAutoRetry()
            suppressGoalRetryUntilNewTurn = false
        }
        val implementsPlan = displayValue.startsWith(NATIVE_IMPLEMENT_PLAN_DISPLAY_PREFIX)
        val value = if (implementsPlan) {
            "${CodexAppServerBridge.IMPLEMENT_PLAN_PROMPT_PREFIX}\n${decodeNativeImplementPlan(displayValue)}"
        } else displayValue
        currentThreadId?.let(NativeHistorySnapshotCache::remove)
        chatState.prepareForRetry(preserveRetryStatus)
        resetProtocolTurnState()
        chatState.prepareReplacementTurn()
        startFrameDiagnostics()
        bridge?.sendMessage(value, chatState.selectedModel, chatState.selectedEffort, "[]", if (implementsPlan) "default" else chatState.selectedMode)
    }

    internal fun CodexChatActivity.persistDraft(value: String) {
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString("native_chat_draft_v1", value).apply()
    }

    internal fun CodexChatActivity.updateDraft(value: String) {
        chatState.input = value
        persistDraft(value)
    }

    internal fun CodexChatActivity.captureFollowUp(value: String): NativeQueuedFollowUp = NativeQueuedFollowUp(
        text = value,
        attachments = chatState.attachments.toList(),
        skills = chatState.selectedSkills.toList(),
        model = chatState.selectedModel,
        effort = chatState.selectedEffort,
        mode = chatState.selectedMode,
    )

    internal fun CodexChatActivity.attachmentsJson(followUp: NativeQueuedFollowUp): String = JSONArray().also { array ->
        followUp.attachments.forEach { attachment ->
            array.put(JSONObject().put("name", attachment.name).put("path", attachment.path).put("image", attachment.image))
        }
    }.toString()

    internal fun CodexChatActivity.skillsJson(followUp: NativeQueuedFollowUp): String = JSONArray().also { array ->
        followUp.skills.forEach { skill ->
            array.put(JSONObject().put("name", skill.name).put("path", skill.path))
        }
    }.toString()

    internal fun CodexChatActivity.clearComposerAfterSubmit() {
        updateDraft("")
        chatState.attachments.clear()
        chatState.selectedSkills.clear()
    }

    /** Main-thread lifecycle/terminal barrier: publish every callback already accepted by us. */

    internal fun CodexChatActivity.drainPendingNativeUiEvents() {
        streamHandler.removeCallbacks(flushProtocolEventQueueRunnable)
        streamHandler.removeCallbacks(flushReasoningRunnable)
        streamHandler.removeCallbacks(flushAnswerRunnable)
        streamHandler.removeCallbacks(flushPlanRunnable)
        streamHandler.removeCallbacks(flushCommandRunnable)
        NativePendingUiEventDrain.run(
            protocol = ::flushProtocolEventQueue,
            reasoning = { flushReasoningDeltas(force = true) },
            assistant = { flushAnswerDeltas(force = true) },
            plan = ::flushPlanDeltas,
            command = ::flushCommandDeltas,
        )
    }

    internal fun CodexChatActivity.queueFollowUp(followUp: NativeQueuedFollowUp, clearComposer: Boolean = true): NativeSubmitResult {
        if (chatState.queuedFollowUps.none { it.id == followUp.id }) chatState.queuedFollowUps.add(followUp)
        persistQueuedFollowUps()
        syncNativeContinuationHint()
        if (clearComposer) clearComposerAfterSubmit()
        return NativeSubmitResult(accepted = true, queued = true)
    }

    /** Mirrors the in-memory queue to the durable claim-ticket store for the current thread. */
    internal fun CodexChatActivity.persistQueuedFollowUps() {
        val threadId = currentThreadId ?: return
        NativeQueuedMessageStore(getSharedPreferences("codex_mobile", MODE_PRIVATE))
            .write(threadId, chatState.queuedFollowUps)
    }

    internal fun CodexChatActivity.startNewTurn(followUp: NativeQueuedFollowUp, clearComposer: Boolean = true): NativeSubmitResult {
        cancelGoalAutoRetry()
        goalRetryCycleActive = false
        goalRetryWaitingForCompletion = false
        suppressGoalRetryUntilNewTurn = false
        currentThreadId?.let { NativeTaskNotificationManager.reset(this, it) }
        currentThreadId?.takeIf { chatState.pendingPlanImplementation.isNotBlank() }
            ?.let(::clearPendingPlanImplementation)
        currentThreadId?.let(NativeHistorySnapshotCache::remove)
        // A queued item was cleared when it entered the queue. Do not erase a newer draft that
        // the user typed while the previous turn was finishing.
        if (clearComposer) clearComposerAfterSubmit()
        if (chatState.conversationTitle == "新对话" && followUp.text.isNotBlank()) {
            chatState.conversationTitle = followUp.text.lineSequence().firstOrNull().orEmpty().trim()
                .let { if (it.length > 28) it.take(27) + "…" else it }
                .ifBlank { "新对话" }
        }
        resetProtocolTurnState()
        val userMessage = chatState.addUser(followUp.text, followUp.skills, followUp.attachments)
        currentThreadId?.let { threadId ->
            CodexTaskStore.assignProject(this, threadId, chatState.projectPath)
            conversationProjectCache[threadId] = chatState.projectPath
        }
        startFrameDiagnostics()
        bridge?.sendMessage(
            followUp.text,
            followUp.model,
            followUp.effort,
            attachmentsJson(followUp),
            followUp.mode,
            skillsJson(followUp),
        )
        syncNativeContinuationHint()
        return NativeSubmitResult(accepted = true, messageId = userMessage.id)
    }

    internal fun CodexChatActivity.submitSteer(followUp: NativeQueuedFollowUp): NativeSubmitResult {
        cancelGoalAutoRetry()
        goalRetryWaitingForCompletion = false
        currentThreadId?.let(NativeHistorySnapshotCache::remove)
        val requestId = bridge?.steerMessage(
            followUp.text,
            attachmentsJson(followUp),
            skillsJson(followUp),
        ) ?: -1
        if (requestId < 0) {
            Toast.makeText(
                this,
                nativeText(nativeLanguage, "当前回答尚未可引导，已加入发送队列", "The active turn is not steerable yet; queued instead"),
                Toast.LENGTH_SHORT,
            ).show()
            return queueFollowUp(followUp)
        }
        val userMessage = chatState.addFollowUpUser(followUp.text, followUp.skills, followUp.attachments)
        pendingNativeSteers[requestId] = PendingNativeSteer(userMessage.id, followUp)
        clearComposerAfterSubmit()
        return NativeSubmitResult(accepted = true, messageId = userMessage.id)
    }

    internal fun CodexChatActivity.sendMessage(text: String): NativeSubmitResult? {
        val value = text.trim()
        if ((value.isEmpty() && chatState.attachments.isEmpty()) || !chatState.ready) return null
        if (compactionInProgress()) {
            Toast.makeText(
                this,
                nativeText(nativeLanguage, "上下文正在压缩，请稍候", "Context compaction is still running"),
                Toast.LENGTH_SHORT,
            ).show()
            return null
        }
        val followUp = captureFollowUp(value)
        if (!chatState.busy) return startNewTurn(followUp)
        return when (chatState.followUpSubmitAction) {
            NativeFollowUpSubmitAction.STEER -> submitSteer(followUp)
            NativeFollowUpSubmitAction.QUEUE -> queueFollowUp(followUp)
        }
    }

    internal fun CodexChatActivity.setFollowUpSubmitAction(action: NativeFollowUpSubmitAction) {
        chatState.followUpSubmitAction = action
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(NATIVE_FOLLOW_UP_ACTION_PREFERENCE, action.name.lowercase(Locale.ROOT))
            .apply()
    }

    internal fun CodexChatActivity.removeQueuedFollowUp(id: String) {
        chatState.queuedFollowUps.removeAll { it.id == id }
        persistQueuedFollowUps()
        syncNativeContinuationHint()
    }

    /**
     * Claim-ticket dispatch: the durable store is taken (read + cleared) before anything is
     * sent, so a completion-time flush and a session-restore flush can never double-send.
     * Persisted items (restart recovery) are merged into the in-memory queue by id.
     */
    internal fun CodexChatActivity.sendNextQueuedFollowUp() {
        if (chatState.busy || compactionInProgress() || !chatState.ready) return
        val threadId = currentThreadId ?: return
        val store = NativeQueuedMessageStore(getSharedPreferences("codex_mobile", MODE_PRIVATE))
        val persisted = store.take(threadId)
        if (persisted.isNotEmpty()) {
            val persistedIds = persisted.map { it.id }.toSet()
            chatState.queuedFollowUps.removeAll { it.id in persistedIds }
            chatState.queuedFollowUps.addAll(persisted)
        }
        val followUp = if (chatState.queuedFollowUps.isEmpty()) return else chatState.queuedFollowUps.removeAt(0)
        persistQueuedFollowUps()
        startNewTurn(followUp, clearComposer = false)
    }

    /**
     * Session-restore flush: dispatches one persisted queued message when the resumed thread
     * is idle. The claim-ticket take inside [sendNextQueuedFollowUp] guarantees no double-send
     * with the completion-time flush.
     */
    internal fun CodexChatActivity.flushRestoredQueuedMessages() {
        val threadId = currentThreadId ?: return
        if (chatState.busy || !chatState.ready || chatState.phase.active) return
        val store = NativeQueuedMessageStore(getSharedPreferences("codex_mobile", MODE_PRIVATE))
        if (store.count(threadId) == 0 && chatState.queuedFollowUps.isEmpty()) return
        sendNextQueuedFollowUp()
    }

    internal fun CodexChatActivity.setChatMode(mode: String) {
        chatState.selectedMode = if (mode == "plan") "plan" else "default"
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString("native_chat_mode_v1", chatState.selectedMode)
            .apply { currentThreadId?.let { putString(modePreferenceKey(it), chatState.selectedMode) } }
            .apply()
    }

    internal fun CodexChatActivity.setPermissionMode(mode: String) {
        val normalized = NativePermissionMode.normalize(mode)
        chatState.permissionMode = normalized
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(NativePermissionMode.PREFERENCE_KEY, normalized)
            .apply()
    }

    internal fun CodexChatActivity.modePreferenceKey(threadId: String): String = "native_thread_mode_v1_$threadId"

    internal fun CodexChatActivity.stopCurrentTurn() {
        if (!chatState.busy) return
        cancelGoalAutoRetry()
        goalRetryWaitingForCompletion = false
        suppressGoalRetryUntilNewTurn = true
        chatState.connectionLabel = "正在停止…"
        syncNativeContinuationHint()
        bridge?.interruptCurrentTurn()
    }

