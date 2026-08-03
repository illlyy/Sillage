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


    internal fun CodexChatActivity.refreshConversations() {
        // The overlay_tasks_v1 preference listener fires synchronously on whichever thread wrote
        // the pref — that can be a bridge thread. Generation/lastConversationBackend/list mutation
        // must serialize on the UI thread to stay race-free with applyConversationSnapshot.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { refreshConversations() }
            return
        }
        val backend = NativeBackendType.current(getSharedPreferences("codex_mobile", MODE_PRIVATE))
        chatState.backend = backend
        val generation = ++conversationRefreshGeneration
        android.util.Log.d("IlyopCodexTasks", "refresh generation=$generation backend=$backend")
        if (backend != lastConversationBackend) {
            // Backend switched: drop the other backend's records immediately so the drawer never
            // flashes stale cross-backend data while the new backend's scan is in flight.
            lastConversationBackend = backend
            chatState.conversations.clear()
        }
        NativeConversationSourceRegistry.sourceFor(backend).refresh(this, backend, generation)
    }

    internal fun CodexChatActivity.applyConversationSnapshot(backend: NativeBackendType, generation: Int, conversations: List<NativeConversation>) {        runOnUiThread {
            if (generation != conversationRefreshGeneration ||
                backend != NativeBackendType.current(getSharedPreferences("codex_mobile", MODE_PRIVATE)) ||
                isFinishing || isDestroyed) return@runOnUiThread
            android.util.Log.d("IlyopCodexTasks", "apply generation=$generation backend=$backend count=${conversations.size} first=${conversations.firstOrNull()?.title}")
            // Skip pointless re-clears: background task-state writes fire refreshes repeatedly and
            // the list only changes when thread ids/titles differ. Re-clearing identical content is
            // what made the drawer flash empty between same-backend refreshes.
            val current = chatState.conversations
            if (current.size == conversations.size && current.indices.all { index ->
                    current[index].threadId == conversations[index].threadId && current[index].title == conversations[index].title
                }
            ) return@runOnUiThread
            chatState.conversations.clear()
            chatState.conversations.addAll(conversations)
            conversations.firstOrNull { it.threadId == currentThreadId }?.projectPath?.takeIf { it.isNotBlank() }?.let {
                chatState.projectPath = it
            }
        }
    }

    /**
     * Unified backend switch used by the drawer quick-switch and the onResume mismatch path.
     * Terminates the running turn, persists the pref, tears down the leaving runtime (so its
     * bridge stops writing task-state refreshes and can no longer publish records), spawns the
     * requested backend, resets to a fresh conversation and reloads the drawer. Main thread only.
     */
    internal fun CodexChatActivity.switchBackend(to: NativeBackendType) {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        if (NativeBackendType.current(prefs) == to) return
        if (chatState.busy) stopCurrentTurn()
        NativeBackendType.set(prefs, to)
        chatState.backend = to
        notificationTargetThreadId = null
        if (to == NativeBackendType.CLAUDE) {
            if (CodexNativeRuntime.exists()) CodexNativeRuntime.shutdown()
        } else {
            if (ClaudeNativeRuntime.exists()) ClaudeNativeRuntime.shutdown()
        }
        bridge = null
        startBackend()
        newConversationAtProject("")
        refreshConversations()
    }

    internal fun CodexChatActivity.favoriteThreadIds(): Set<String> {
        val raw = getSharedPreferences("codex_mobile", MODE_PRIVATE).getString("native_favorite_threads_v1", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildSet { for (index in 0 until array.length()) add(array.optString(index)) }
    }

    internal fun CodexChatActivity.toggleFavorite(conversation: NativeConversation) {
        val favorites = favoriteThreadIds().toMutableSet()
        if (!favorites.add(conversation.threadId)) favorites.remove(conversation.threadId)
        val array = JSONArray().also { value -> favorites.forEach { value.put(it) } }
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString("native_favorite_threads_v1", array.toString()).apply()
        refreshConversations()
    }

    internal fun CodexChatActivity.renameConversation(conversation: NativeConversation, title: String) {
        CodexTaskStore.updateTitle(this, conversation.threadId, title)
        refreshConversations()
    }

    internal fun CodexChatActivity.deleteConversation(conversation: NativeConversation) {
        NativeHistorySnapshotCache.remove(conversation.threadId)
        NativeTaskNotificationManager.reset(this, conversation.threadId)
        val snapshots = NativeWorkspaceSnapshotStore.load(this, conversation.threadId)
        NativeWorkspaceSnapshotStore.clear(this, conversation.threadId)
        if (snapshots.isNotEmpty()) Thread({
            snapshots.filter { it.ref.isNotBlank() && it.projectPath.isNotBlank() }.forEach { snapshot ->
                runGit(snapshot.projectPath, listOf("update-ref", "-d", snapshot.ref))
            }
        }, "NativeSnapshotCleanup").start()
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .remove(pendingUserInputPreferenceKey(conversation.threadId))
            .remove(pendingApprovalPreferenceKey(conversation.threadId))
            .remove(pendingPlanImplementationPreferenceKey(conversation.threadId))
            .apply()
        CodexTaskStore.delete(this, conversation.threadId)
        refreshConversations()
    }

    internal fun CodexChatActivity.subagentMessageCount(threadId: String): Int =
        chatState.subagentHistoryMessageCounts[threadId] ?: 0

    internal fun CodexChatActivity.loadSubagentHistory(threadId: String) {
        if (threadId.isBlank() || threadId in chatState.loadingSubagentHistories) return
        val status = chatState.subagentStatuses[threadId].orEmpty()
        val cachedReference = chatState.subagentHistoryRefs[threadId].orEmpty()
        if (status in setOf("done", "failed") && subagentMessageCount(threadId) > 0 &&
            NativeLargePayloadStore.contains(cachedReference)) return
        subagentHistoryAttempts[threadId] = 0
        chatState.subagentHistoryErrors.remove(threadId)
        chatState.loadingSubagentHistories.add(threadId)
        bridge?.loadSubagentHistory(threadId, subagentRouteGeneration)
            ?: chatState.loadingSubagentHistories.remove(threadId)
    }

    internal fun CodexChatActivity.handleSubagentHistory(value: String) {
        val payload = runCatching { JSONObject(value) }.getOrNull() ?: return
        val generation = payload.optInt("generation", -1)
        if (generation != subagentRouteGeneration) return
        val thread = payload.optString("threadId").trim()
        if (thread.isBlank()) return
        val historyRef = payload.optString(NativeLargePayloadStore.PAYLOAD_REF)
        val messageCount = payload.optInt(NativeLargePayloadStore.MESSAGE_COUNT, 0).coerceAtLeast(0)
        if (historyRef.isNotBlank()) chatState.subagentHistoryRefs[thread] = historyRef
        chatState.subagentHistoryMessageCounts[thread] = messageCount
        val status = payload.optString("status", "").trim().lowercase()
        if (status in setOf("waiting", "working", "done", "failed")) chatState.subagentStatuses[thread] = status
        val error = payload.optString("error", "").trim()
        if (error.isBlank()) chatState.subagentHistoryErrors.remove(thread) else chatState.subagentHistoryErrors[thread] = error

        val attempt = (subagentHistoryAttempts[thread] ?: 0) + 1
        subagentHistoryAttempts[thread] = attempt
        val terminal = status == "done" || status == "failed"
        val shouldRetry = when {
            !terminal -> attempt < 120
            messageCount == 0 -> attempt < 9
            else -> false
        }
        if (!shouldRetry) {
            chatState.loadingSubagentHistories.remove(thread)
            subagentHistoryAttempts.remove(thread)
            return
        }
        val delayMs = when {
            attempt <= 4 -> 650L
            attempt <= 20 -> 1_500L
            else -> 3_500L
        }
        if (thread !in chatState.loadingSubagentHistories) chatState.loadingSubagentHistories.add(thread)
        streamHandler.postDelayed({
            if (generation == subagentRouteGeneration && thread in chatState.loadingSubagentHistories) {
                bridge?.loadSubagentHistory(thread, generation)
                    ?: chatState.loadingSubagentHistories.remove(thread)
            }
        }, delayMs)
    }

    internal fun CodexChatActivity.resumeConversation(threadId: String, retainedRuntime: Boolean = false) {
        detachNativeContinuationHintBeforeRouteChange(threadId)
        pendingNativeSteers.clear()
        cancelGoalAutoRetry()
        goalRetryCycleActive = false
        goalRetryWaitingForCompletion = false
        suppressGoalRetryUntilNewTurn = false
        discardPendingStreamEvents("resume")
        resetProtocolTurnState(preserveSequenceWatermarks = false)
        subagentRouteGeneration = CodexChatActivity.subagentRouteCounter.incrementAndGet()
        subagentHistoryAttempts.clear()
        currentThreadId = threadId
        notificationTargetThreadId = ""
        NativeTaskNotificationManager.markSeen(this, threadId)
        NativeTaskNotificationManager.setForegroundThread(this, threadId)
        val selectedConversation = chatState.conversations.firstOrNull { it.threadId == threadId }
        val lifecycleHandoff = NativeChatLifecycleHandoff.peek(threadId)
        val restoreLifecycleHandoff = NativeChatLifecycleHandoffPolicy.canRestore(
            retainedRuntime,
            threadId,
            lifecycleHandoff,
        )
        if (!restoreLifecycleHandoff && lifecycleHandoff != null) {
            NativeChatLifecycleHandoff.remove(threadId)
            // The regular cache may point at the same in-memory-only snapshot. A recreated bridge
            // must start from its own disk history instead of importing a previous runtime tail.
            NativeHistorySnapshotCache.remove(threadId)
        }
        lifecycleHandoffRouteThreadId = threadId.takeIf { restoreLifecycleHandoff }.orEmpty()
        val cachedHistory = NativeHistorySnapshotCache.get(threadId)
        pendingCachedHistoryThreadId = threadId.takeIf { cachedHistory != null }
        pendingCachedHistorySnapshot = cachedHistory
        displayedHistorySnapshot = null
        chatState.resetConversation()
        chatState.currentThreadId = threadId
        chatState.projectPath = selectedConversation?.projectPath.orEmpty()
        chatState.historyLoading = true
        // Commit the lightweight route before any cached messages. The screen can render its
        // loading shell in one frame instead of attaching history and changing route together.
        chatState.conversationAnimationKey = threadId
        chatState.conversationTitle = selectedConversation?.title ?: "\u5bf9\u8bdd"
        if (selectedConversation?.state == CodexTaskStore.RUNNING) {
            chatState.phase = NativeTurnPhase.WAITING
            chatState.processingLabel = "\u6b63\u5728\u91cd\u65b0\u8fde\u63a5\u4efb\u52a1"
            chatState.turnStartedAt = System.currentTimeMillis()
            chatState.phaseStartedAt = chatState.turnStartedAt
            startFrameDiagnostics()
        } else if (selectedConversation?.state == CodexTaskStore.FAILED) {
            chatState.phase = NativeTurnPhase.FAILED
            chatState.processingLabel = nativeText(nativeLanguage, "\u4efb\u52a1\u672a\u5b8c\u6210", "Task incomplete")
        }
        chatState.ready = false
        chatState.connectionLabel = "正在恢复对话…"
        // A durable queue may survive a restart or a session switch: once this thread is idle
        // again, dispatch the first queued message. Busy/resuming turns are guarded inside.
        streamHandler.postDelayed(::flushRestoredQueuedMessages, 750L)
        val isClaude = NativeBackendType.current(getSharedPreferences("codex_mobile", MODE_PRIVATE)) == NativeBackendType.CLAUDE
        expectedHistoryGeneration = if (isClaude) {
            bridge?.resumeConversation(threadId) ?: -1
        } else if (retainedRuntime) {
            bridge?.restoreRetainedConversation(threadId) ?: -1
        } else {
            bridge?.resumeConversation(threadId) ?: -1
        }
        if (isClaude) {
            // Claude has no onHistoryPrepared; load a lightweight transcript snapshot instead.
            val routeGeneration = expectedHistoryGeneration
            Thread {
                val snapshot = ClaudeHistoryAdapter.loadSnapshot(this, threadId)
                if (snapshot == null) return@Thread
                runOnUiThread {
                    if (currentThreadId != threadId || expectedHistoryGeneration != routeGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    applyPreparedHistory(threadId, snapshot, fresh = true)
                }
            }.apply { name = "ClaudeHistoryLoad" }.start()
            return
        }
        if (cachedHistory != null) {
            val routeGeneration = expectedHistoryGeneration
            // Two frame boundaries guarantee that the lightweight loading route is visible before
            // cached Text/AndroidView nodes are attached and measured.
            Choreographer.getInstance().postFrameCallback {
                Choreographer.getInstance().postFrameCallback {
                    if (currentThreadId != threadId || expectedHistoryGeneration != routeGeneration || displayedHistorySnapshot != null) {
                        return@postFrameCallback
                    }
                    val pendingCache = consumePendingCachedHistory(threadId) ?: return@postFrameCallback
                    applyPreparedHistory(threadId, pendingCache, fresh = false)
                }
            }
        }
    }

    internal fun CodexChatActivity.newConversation() = newConversationAtProject("")

    internal fun CodexChatActivity.newConversationAtProject(requestedProjectPath: String) {
        // Project creation/registration enters through this callback. Re-evaluate cached legacy
        // cwd values now so old tasks migrate in the same process instead of waiting for restart.
        if (requestedProjectPath.isNotBlank()) refreshConversations()
        detachNativeContinuationHintBeforeRouteChange(null)
        pendingNativeSteers.clear()
        cancelGoalAutoRetry()
        goalRetryCycleActive = false
        goalRetryWaitingForCompletion = false
        suppressGoalRetryUntilNewTurn = false
        discardPendingStreamEvents("new")
        resetProtocolTurnState(preserveSequenceWatermarks = false)
        chatState.selectedMode = "default"
        subagentRouteGeneration = CodexChatActivity.subagentRouteCounter.incrementAndGet()
        subagentHistoryAttempts.clear()
        pendingCachedHistoryThreadId = null
        pendingCachedHistorySnapshot = null
        currentThreadId = null
        expectedHistoryGeneration = -1
        displayedHistorySnapshot = null
        chatState.currentThreadId = ""
        chatState.conversationAnimationKey = "new-${UUID.randomUUID()}"
        chatState.resetConversation()
        chatState.projectPath = requestedProjectPath
            .takeIf { it.isNotBlank() && File(it).isDirectory }
            ?.let { runCatching { File(it).canonicalPath }.getOrDefault(it) }
            .orEmpty()
        chatState.conversationTitle = "新对话"
        chatState.ready = false
        chatState.connectionLabel = "正在创建新对话…"
        bridge?.newConversationAtCwd(chatState.projectPath.ifBlank { TermuxConstants.TERMUX_HOME_DIR_PATH })
    }

    internal fun CodexChatActivity.consumePendingCachedHistory(threadId: String): NativeHistorySnapshot? {
        if (pendingCachedHistoryThreadId != threadId) return null
        val snapshot = pendingCachedHistorySnapshot
        pendingCachedHistoryThreadId = null
        pendingCachedHistorySnapshot = null
        return snapshot
    }

    internal fun CodexChatActivity.applyPreparedHistory(threadId: String, snapshot: NativeHistorySnapshot, fresh: Boolean) {
        if (currentThreadId != threadId) return
        val lifecycleHandoff = if (lifecycleHandoffRouteThreadId == threadId) {
            NativeChatLifecycleHandoff.peek(threadId)
        } else null
        val preparedSnapshot = if (fresh && lifecycleHandoff != null) {
            NativeChatLifecycleHandoff.mergeFreshHistory(snapshot, lifecycleHandoff)
        } else snapshot
        // A very fast empty disk result may beat the two-frame cached-history commit. Preserve the
        // known cached conversation rather than flashing/settling on an empty thread.
        if (fresh) {
            val pendingCache = consumePendingCachedHistory(threadId)
            if (lifecycleHandoff == null && preparedSnapshot.messages.isEmpty() &&
                pendingCache?.messages?.isNotEmpty() == true && chatState.messages.isEmpty()
            ) {
                chatState.applyHistorySnapshot(pendingCache)
                displayedHistorySnapshot = pendingCache
            }
        }
        val preserveCachedSnapshot = fresh && preparedSnapshot.messages.isEmpty() && chatState.messages.isNotEmpty()
        val unchangedFreshSnapshot = fresh && displayedHistorySnapshot?.hasSameContent(preparedSnapshot) == true
        var restoredLifecycleState = false
        if (!preserveCachedSnapshot && !unchangedFreshSnapshot) {
            chatState.applyHistorySnapshot(preparedSnapshot)
            displayedHistorySnapshot = preparedSnapshot
            if (lifecycleHandoff != null &&
                (!fresh || preparedSnapshot === lifecycleHandoff.history || !lifecycleHandoff.phase.active)
            ) {
                chatState.restoreLifecycleUiState(lifecycleHandoff)
                restoredLifecycleState = true
            }
        }
        if (fresh) {
            if (!preserveCachedSnapshot && !unchangedFreshSnapshot) {
                NativeHistorySnapshotCache.put(threadId, preparedSnapshot)
            }
            // Keep an uncovered live tail for another recreation; authoritative fresh history
            // releases the process-level handoff as soon as it semantically covers that tail.
            if (lifecycleHandoff != null && preparedSnapshot !== lifecycleHandoff.history) {
                NativeChatLifecycleHandoff.remove(threadId)
                lifecycleHandoffRouteThreadId = ""
            }
            chatState.historyLoading = false
        }
        // Older app-server histories may omit context-compaction items. Merge the bounded local
        // journal by stable server/id key; NativeChatState keeps one divider for duplicates.
        compactionJournalStore.load(threadId).takeIf { it.isNotEmpty() }?.let(chatState::restoreCompactions)
        if (chatState.busy && !restoredLifecycleState) {
            // A resumed running turn starts a fresh live phase after the persisted snapshot.
            chatState.turnMessageStartIndex = chatState.messages.size
            chatState.phaseMessageStartIndex = chatState.messages.size
            if (chatState.phaseStartedAt <= 0L) chatState.phaseStartedAt = System.currentTimeMillis()
        }
        NativeChatDiagnostics.record(this, "history_snapshot_applied", JSONObject()
            .put("thread", threadId.take(8)).put("fresh", fresh)
            .put("messages", chatState.messages.size).put("estimatedChars", preparedSnapshot.estimatedChars)
            .put("preservedCache", preserveCachedSnapshot)
            .put("skippedUnchanged", unchangedFreshSnapshot)
            .put("lifecycleHandoff", lifecycleHandoff != null)
            .put("lifecycleTailPreserved", lifecycleHandoff != null && preparedSnapshot === lifecycleHandoff.history))
    }

