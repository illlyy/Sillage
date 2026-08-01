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


    internal fun CodexChatActivity.goalPreferenceKey(threadId: String): String = "native_thread_goal_v1_$threadId"

    internal fun CodexChatActivity.goalStatusPreferenceKey(threadId: String): String = "native_thread_goal_status_v1_$threadId"

    internal fun CodexChatActivity.planPreferenceKey(threadId: String): String = "native_thread_plan_v1_$threadId"

    internal fun CodexChatActivity.planExplanationPreferenceKey(threadId: String): String = "native_thread_plan_explanation_v1_$threadId"

    internal fun CodexChatActivity.pendingUserInputPreferenceKey(threadId: String): String = "native_thread_pending_user_input_v1_$threadId"

    internal fun CodexChatActivity.pendingApprovalPreferenceKey(threadId: String): String = "native_thread_pending_approval_v1_$threadId"

    internal fun CodexChatActivity.pendingPlanImplementationPreferenceKey(threadId: String): String = "native_thread_pending_plan_v1_$threadId"

    internal fun CodexChatActivity.threadAttention(threadId: String): String {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val pendingInput = prefs.getString(pendingUserInputPreferenceKey(threadId), "").orEmpty()
        val deadline = runCatching { JSONObject(pendingInput).optLong("_nativeDeadlineMs", 0L) }.getOrDefault(0L)
        if (pendingInput.isNotBlank() && deadline > 0L && deadline <= System.currentTimeMillis()) {
            prefs.edit().remove(pendingUserInputPreferenceKey(threadId)).apply()
        }
        return when {
            pendingInput.isNotBlank() && (deadline <= 0L || deadline > System.currentTimeMillis()) -> "answer"
            prefs.getString(pendingApprovalPreferenceKey(threadId), "").orEmpty().isNotBlank() -> "approval"
            prefs.getString(pendingPlanImplementationPreferenceKey(threadId), "").orEmpty().isNotBlank() -> "plan"
            else -> ""
        }
    }

    internal fun CodexChatActivity.attentionPriority(value: String): Int = when (value) {
        "answer" -> 3
        "approval" -> 2
        "plan", "resume" -> 1
        else -> 0
    }

    internal fun CodexChatActivity.taskAttention(threadId: String, state: String): String = threadAttention(threadId).ifBlank {
        if (state == CodexTaskStore.FAILED) "resume" else ""
    }

    internal fun CodexChatActivity.syncPendingNotification(threadId: String) {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val input = prefs.getString(pendingUserInputPreferenceKey(threadId), "").orEmpty()
        val approval = prefs.getString(pendingApprovalPreferenceKey(threadId), "").orEmpty()
        val plan = prefs.getString(pendingPlanImplementationPreferenceKey(threadId), "").orEmpty()
        when {
            input.isNotBlank() -> NativeTaskNotificationManager.notifyEvent(this, threadId, NativeTaskNotificationPolicy.ANSWER, requestIdentity(input), "")
            approval.isNotBlank() -> NativeTaskNotificationManager.notifyEvent(this, threadId, NativeTaskNotificationPolicy.APPROVAL, requestIdentity(approval), "")
            plan.isNotBlank() -> NativeTaskNotificationManager.notifyEvent(this, threadId, NativeTaskNotificationPolicy.PLAN, plan.hashCode().toString(), "")
            CodexTaskStore.current(this).any { it.threadId == threadId && it.state == CodexTaskStore.FAILED } ->
                NativeTaskNotificationManager.notifyEvent(this, threadId, NativeTaskNotificationPolicy.RESUME, "failed", "")
            else -> NativeTaskNotificationManager.markSeen(this, threadId)
        }
    }

    internal fun CodexChatActivity.restoreGoalForThread(threadId: String) {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        chatState.activeGoalObjective = prefs.getString(goalPreferenceKey(threadId), "").orEmpty()
        chatState.activeGoalStatus = prefs.getString(goalStatusPreferenceKey(threadId), "active").orEmpty()
            .takeIf { it == "active" || it == "paused" || it == "budgetLimited" } ?: "active"
        chatState.selectedMode = prefs.getString(modePreferenceKey(threadId), "default").orEmpty().takeIf { it == "plan" } ?: "default"
        chatState.planJson = prefs.getString(planPreferenceKey(threadId), "[]").orEmpty().ifBlank { "[]" }
        chatState.planExplanation = prefs.getString(planExplanationPreferenceKey(threadId), "").orEmpty()
        chatState.pendingUserInputRequest = prefs.getString(pendingUserInputPreferenceKey(threadId), "").orEmpty()
        chatState.pendingApprovalRequest = prefs.getString(pendingApprovalPreferenceKey(threadId), "").orEmpty()
        chatState.pendingPlanImplementation = prefs.getString(pendingPlanImplementationPreferenceKey(threadId), "").orEmpty()
        if (chatState.pendingUserInputRequest.isNotBlank()) scheduleUserInputTimeout(threadId, chatState.pendingUserInputRequest)
        chatState.workspaceSnapshots.clear()
        chatState.workspaceSnapshots.addAll(NativeWorkspaceSnapshotStore.load(this, threadId))
        syncNativeContinuationHint()
    }

    internal fun CodexChatActivity.clearLocalGoal(threadId: String) {
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .remove(goalPreferenceKey(threadId))
            .remove(goalStatusPreferenceKey(threadId))
            .apply()
        if (currentThreadId == threadId) {
            cancelGoalAutoRetry()
            goalRetryCycleActive = false
            goalRetryWaitingForCompletion = false
            chatState.activeGoalObjective = ""
            chatState.activeGoalStatus = "active"
            syncNativeContinuationHint()
        }
    }

    internal fun CodexChatActivity.applyGoalState(value: String) {
        val payload = runCatching { JSONObject(value) }.getOrNull() ?: return
        val threadId = payload.optString("threadId")
        if (threadId.isBlank() || threadId != currentThreadId) return
        val goal = payload.optJSONObject("goal")
        val objective = goal?.optString("objective").orEmpty().trim()
        val status = goal?.optString("status", "active").orEmpty()
        if (goal == null || objective.isBlank() || status == "complete") {
            clearLocalGoal(threadId)
            return
        }
        val normalizedStatus = status.takeIf { it == "active" || it == "paused" || it == "budgetLimited" } ?: "active"
        chatState.activeGoalObjective = objective
        chatState.activeGoalStatus = normalizedStatus
        if (normalizedStatus != "active") cancelGoalAutoRetry()
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(goalPreferenceKey(threadId), objective)
            .putString(goalStatusPreferenceKey(threadId), normalizedStatus)
            .apply()
        syncNativeContinuationHint()
    }

    internal fun CodexChatActivity.setGoal(objective: String) {
        val value = objective.trim()
        val threadId = currentThreadId
        if (value.isEmpty() || !chatState.ready || threadId.isNullOrBlank()) return
        chatState.activeGoalObjective = value
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(goalPreferenceKey(threadId), value).putString(goalStatusPreferenceKey(threadId), "active").apply()
        chatState.activeGoalStatus = "active"
        suppressGoalRetryUntilNewTurn = false
        bridge?.setThreadGoal(value)
        syncNativeContinuationHint()
    }

    internal fun CodexChatActivity.requestIdentity(raw: String): String = runCatching {
        JSONObject(raw).opt("requestId")?.toString().orEmpty()
    }.getOrDefault("")

    internal fun CodexChatActivity.storePendingUserInput(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val params = payload.optJSONObject("params")
        val threadId = params?.optString("threadId").orEmpty().ifBlank { currentThreadId.orEmpty() }
        if (threadId.isBlank()) return
        if (payload.optLong("_nativeDeadlineMs", 0L) <= 0L) {
            payload.put("_nativeDeadlineMs", System.currentTimeMillis() + NATIVE_USER_INPUT_TIMEOUT_MS)
        }
        val stored = payload.toString()
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(pendingUserInputPreferenceKey(threadId), stored)
            .apply()
        if (currentThreadId == threadId) chatState.pendingUserInputRequest = stored
        scheduleUserInputTimeout(threadId, stored)
        refreshConversations()
    }

    internal fun CodexChatActivity.clearPendingUserInput(threadId: String, expectedRequestId: String = "") {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val stored = prefs.getString(pendingUserInputPreferenceKey(threadId), "").orEmpty()
        if (expectedRequestId.isNotBlank() && requestIdentity(stored) != expectedRequestId) return
        prefs.edit().remove(pendingUserInputPreferenceKey(threadId)).apply()
        syncPendingNotification(threadId)
        if (currentThreadId == threadId && (expectedRequestId.isBlank() || requestIdentity(chatState.pendingUserInputRequest) == expectedRequestId)) {
            chatState.pendingUserInputRequest = ""
        }
        refreshConversations()
    }

    internal fun CodexChatActivity.scheduleUserInputTimeout(threadId: String, raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val requestId = requestIdentity(raw)
        val deadline = payload.optLong("_nativeDeadlineMs", System.currentTimeMillis() + NATIVE_USER_INPUT_TIMEOUT_MS)
        streamHandler.postDelayed({
            val stored = getSharedPreferences("codex_mobile", MODE_PRIVATE)
                .getString(pendingUserInputPreferenceKey(threadId), "").orEmpty()
            if (requestIdentity(stored) != requestId || System.currentTimeMillis() < deadline) return@postDelayed
            bridge?.respondUserInput(stored, "{}")
            clearPendingUserInput(threadId, requestId)
            if (currentThreadId == threadId && chatState.phase == NativeTurnPhase.WAITING) {
                chatState.phase = NativeTurnPhase.TOOL_RUNNING
                chatState.processingLabel = nativeText(nativeLanguage, "\u6b63\u5728\u7ee7\u7eed\u6267\u884c", "Continuing")
            }
        }, (deadline - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    internal fun CodexChatActivity.answerUserInput(answersJson: String) {
        val raw = chatState.pendingUserInputRequest
        val threadId = currentThreadId ?: return
        if (raw.isBlank()) return
        bridge?.respondUserInput(raw, answersJson)
        clearPendingUserInput(threadId, requestIdentity(raw))
        if (chatState.phase == NativeTurnPhase.WAITING) {
            chatState.phase = NativeTurnPhase.TOOL_RUNNING
            chatState.processingLabel = nativeText(nativeLanguage, "\u6b63\u5728\u7ee7\u7eed\u6267\u884c", "Continuing")
        }
    }

    internal fun CodexChatActivity.handleUserInputResolved(value: String) {
        val payload = runCatching { JSONObject(value) }.getOrNull() ?: return
        val threadId = payload.optString("threadId")
        val requestId = payload.opt("requestId")?.toString().orEmpty()
        if (threadId.isNotBlank()) {
            clearPendingUserInput(threadId, requestId)
            if (currentThreadId == threadId && chatState.phase == NativeTurnPhase.WAITING) {
                chatState.phase = NativeTurnPhase.TOOL_RUNNING
                chatState.processingLabel = nativeText(nativeLanguage, "\u6b63\u5728\u7ee7\u7eed\u6267\u884c", "Continuing")
            }
        }
    }

    internal fun CodexChatActivity.persistPendingPlanImplementation(threadId: String, plan: String) {
        val value = plan.trim()
        if (value.isBlank()) return
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(pendingPlanImplementationPreferenceKey(threadId), value)
            .apply()
        if (currentThreadId == threadId) chatState.pendingPlanImplementation = value
        NativeTaskNotificationManager.notifyEvent(this, threadId, NativeTaskNotificationPolicy.PLAN, value.hashCode().toString(), "")
        refreshConversations()
    }

    internal fun CodexChatActivity.clearPendingPlanImplementation(threadId: String) {
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .remove(pendingPlanImplementationPreferenceKey(threadId))
            .apply()
        if (currentThreadId == threadId) chatState.pendingPlanImplementation = ""
        syncPendingNotification(threadId)
        refreshConversations()
    }

    internal fun CodexChatActivity.executePendingPlan() {
        val threadId = currentThreadId ?: return
        val plan = chatState.pendingPlanImplementation.trim()
        if (plan.isBlank() || chatState.busy || !chatState.ready) return
        clearPendingPlanImplementation(threadId)
        setChatMode("default")
        currentThreadId?.let(NativeHistorySnapshotCache::remove)
        updateDraft("")
        chatState.addUser(encodeNativeImplementPlan(plan))
        startFrameDiagnostics()
        bridge?.sendMessage(
            "${CodexAppServerBridge.IMPLEMENT_PLAN_PROMPT_PREFIX}\n$plan",
            chatState.selectedModel,
            chatState.selectedEffort,
            "[]",
            "default",
            "[]",
        )
    }

    internal fun CodexChatActivity.revisePendingPlan(feedback: String) {
        val threadId = currentThreadId ?: return
        val value = feedback.trim()
        if (value.isBlank()) return
        clearPendingPlanImplementation(threadId)
        setChatMode("plan")
        sendMessage(value)
    }

    internal fun CodexChatActivity.cancelPendingPlan() {
        currentThreadId?.let(::clearPendingPlanImplementation)
    }

    internal fun CodexChatActivity.answerApproval(rawRequest: String, decision: String) {
        if (rawRequest.isBlank()) return
        bridge?.respondApprovalRequest(rawRequest, decision)
        approvalThreadId(rawRequest)?.let { clearPendingApproval(it, rawRequest) }
        if (chatState.phase == NativeTurnPhase.WAITING) {
            chatState.phase = NativeTurnPhase.TOOL_RUNNING
            chatState.processingLabel = nativeText(nativeLanguage, "\u6b63\u5728\u7ee7\u7eed\u6267\u884c", "Continuing")
        }
    }

    internal fun CodexChatActivity.cancelPendingApproval() {
        val raw = chatState.pendingApprovalRequest
        if (raw.isNotBlank()) bridge?.respondApprovalRequest(raw, "cancel")
        approvalThreadId(raw)?.let { clearPendingApproval(it, raw) }
    }

    internal fun CodexChatActivity.approvalThreadId(raw: String): String? = runCatching {
        JSONObject(raw).optJSONObject("params")?.optString("threadId").orEmpty()
    }.getOrDefault("").ifBlank { currentThreadId.orEmpty() }.takeIf { it.isNotBlank() }

    internal fun CodexChatActivity.storePendingApproval(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val threadId = payload.optJSONObject("params")?.optString("threadId").orEmpty()
            .ifBlank { currentThreadId.orEmpty() }
        if (threadId.isBlank()) return
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(pendingApprovalPreferenceKey(threadId), raw)
            .apply()
        if (currentThreadId == threadId) chatState.pendingApprovalRequest = raw
        refreshConversations()
    }

    internal fun CodexChatActivity.clearPendingApproval(threadId: String, expectedRaw: String = "") {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val stored = prefs.getString(pendingApprovalPreferenceKey(threadId), "").orEmpty()
        if (expectedRaw.isNotBlank() && stored.isNotBlank() && requestIdentity(stored) != requestIdentity(expectedRaw)) return
        prefs.edit().remove(pendingApprovalPreferenceKey(threadId)).apply()
        syncPendingNotification(threadId)
        if (currentThreadId == threadId) chatState.pendingApprovalRequest = ""
        refreshConversations()
    }

    internal fun CodexChatActivity.toggleGoalPause() {
        val threadId = currentThreadId ?: return
        if (chatState.activeGoalObjective.isBlank() || chatState.phase.active) return
        val next = if (chatState.activeGoalStatus == "active") "paused" else "active"
        chatState.activeGoalStatus = next
        if (next != "active") cancelGoalAutoRetry()
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString(goalStatusPreferenceKey(threadId), next).apply()
        bridge?.setThreadGoalStatus(next)
        syncNativeContinuationHint()
        if (next == "active" && goalRetryCycleActive) scheduleGoalRetry(300L)
    }

    internal fun CodexChatActivity.clearGoal() {
        cancelGoalAutoRetry()
        goalRetryCycleActive = false
        goalRetryWaitingForCompletion = false
        currentThreadId?.let(::clearLocalGoal)
        bridge?.clearThreadGoal()
    }

    internal fun CodexChatActivity.canAutoRetryGoal(): Boolean =
        !suppressGoalRetryUntilNewTurn &&
            currentThreadId?.isNotBlank() == true &&
            chatState.activeGoalObjective.isNotBlank() &&
            chatState.activeGoalStatus == "active" &&
            chatState.ready

    internal fun CodexChatActivity.scheduleGoalRetry(delayMs: Long = 1_800L) {
        if (!canAutoRetryGoal() || goalRetryScheduled) return
        goalRetryScheduled = true
        streamHandler.postDelayed(goalRetryRunnable, delayMs)
    }

    internal fun CodexChatActivity.cancelGoalAutoRetry() {
        goalRetryScheduled = false
        streamHandler.removeCallbacks(goalRetryRunnable)
    }

