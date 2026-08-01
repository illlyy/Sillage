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


    internal fun CodexChatActivity.currentCompactionPolicy(): NativeCompactionPolicy {
        val profile = providerStore.active()
        val profileId = profile?.id.orEmpty().ifBlank { activeProfileId.ifBlank { "default" } }
        val modelId = chatState.selectedModel.ifBlank { profile?.model.orEmpty() }.ifBlank { "default" }
        val key = NativeCompactionSettingsStore.key(profileId, modelId)
        val settings = compactionSettingsStore.read(profileId, modelId)
        if (key != compactionPolicyKey || settings != compactionSettingsSnapshot) {
            compactionPolicyKey = key
            compactionSettingsSnapshot = settings
            compactionPolicy = NativeCompactionPolicy(settings)
        }
        return compactionPolicy
    }

    internal fun CodexChatActivity.evaluateAutomaticCompaction(usage: NativeTurnUsage) {
        if (!usage.estimated && usage.contextUsageReliable && usage.currentContextTokens > 0L &&
            (usage.contextWindow > 0L || usage.autoCompactTokenLimit > 0L)
        ) {
            lastReliableUsage = usage
        }
        // auto_compact_token_limit belongs to Codex/app-server. Calling thread/compact/start here
        // starts a separate manual compaction turn, which terminates the active answer instead of
        // continuing it. Match WebUI: observe the server's contextCompaction lifecycle only.
    }

    internal fun CodexChatActivity.requestManualCompaction() {
        val continuationPending = chatState.queuedFollowUps.isNotEmpty() || goalRetryWaitingForCompletion
        if (chatState.busy || chatState.pendingUserInputRequest.isNotBlank() ||
            chatState.pendingApprovalRequest.isNotBlank() || continuationPending
        ) {
            NativeChatDiagnostics.record(this, "manual_compaction_blocked", JSONObject()
                .put("thread", currentThreadId.orEmpty().take(8))
                .put("busy", chatState.busy)
                .put("continuation", continuationPending))
            Toast.makeText(
                this,
                nativeText(nativeLanguage, "任务进行中，完成后才能压缩上下文", "Compact is disabled while a task is in progress"),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        requestCompaction(NativeCompactionSource.MANUAL, null)
    }

    internal fun CodexChatActivity.requestCompaction(source: NativeCompactionSource, decision: NativeCompactionDecision?) {
        val threadId = currentThreadId ?: return
        if (source != NativeCompactionSource.MANUAL || chatState.busy || threadId.isBlank() ||
            bridge == null || chatState.compactionItems.any { !it.isTerminal }
        ) {
            NativeChatDiagnostics.record(this, "compaction_duplicate_suppressed", JSONObject()
                .put("thread", threadId.take(8)).put("source", source.name.lowercase()))
            return
        }
        val requestId = "${source.name.lowercase()}:${threadId.takeLast(8)}:${System.currentTimeMillis()}"
        val item = chatState.beginManualCompaction(requestId)
        compactionJournalStore.record(item)
        currentCompactionPolicy().markRequested(
            threadId,
            chatState.currentTurnId.takeIf { it.isNotBlank() },
        )
        NativeChatDiagnostics.record(this, "compaction_trigger_source", JSONObject()
            .put("source", decision?.source?.name?.lowercase() ?: source.name.lowercase())
            .put("threshold", decision?.threshold ?: 0L)
            .put("thread", threadId.take(8)))
        scheduleCompactionRequestTimeout(item)
        bridge?.compactThread(requestId)
    }

    internal fun CodexChatActivity.scheduleCompactionRequestTimeout(item: NativeCompactionItem) {
        val requestId = item.requestId?.takeIf { it.isNotBlank() } ?: return
        val timeoutKey = "request:$requestId"
        compactionLifecycleTimeouts.remove(timeoutKey)?.let(streamHandler::removeCallbacks)
        val timeout = Runnable {
            val pending = chatState.compactionItems.firstOrNull {
                it.requestId == requestId && !it.isTerminal
            } ?: return@Runnable
            val updated = chatState.completeManualCompactionRpc(
                requestId = requestId,
                success = false,
                error = nativeText(nativeLanguage, "未收到 Codex 的真实压缩事件", "Codex did not return a compaction lifecycle event"),
                threadId = pending.threadId,
            )
            updated?.let(compactionJournalStore::record)
            currentCompactionPolicy().markFailed(pending.threadId, pending.turnId)
            compactionLifecycleTimeouts.remove(timeoutKey)
        }
        compactionLifecycleTimeouts[timeoutKey] = timeout
        streamHandler.postDelayed(timeout, 30_000L)
    }

    internal fun CodexChatActivity.cancelCompactionRequestTimeout(requestId: String?) {
        val value = requestId?.takeIf { it.isNotBlank() } ?: return
        compactionLifecycleTimeouts.remove("request:$value")?.let(streamHandler::removeCallbacks)
    }

    internal fun CodexChatActivity.compactionInProgress(): Boolean = chatState.compactionItems.any { !it.isTerminal }

