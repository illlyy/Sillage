package com.termux.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.LinkedHashMap
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

@Stable
internal class NativeChatState {
    private companion object {
        val PROTOCOL_MARKUP_REGEX = Regex(
            """</?(?:propose(?:d)?_plan(?:\s[^>]*)?|plan\s*|final\s*)>""",
            RegexOption.IGNORE_CASE,
        )
        val SUBAGENT_THREAD_ID_REGEX = Regex("[0-9a-fA-F-]{32,}")
    }

    val messages = mutableStateListOf<NativeChatMessage>()
    val conversations = mutableStateListOf<NativeConversation>()
    val modelOptions = mutableStateListOf<NativeModelOption>()
    val attachments = mutableStateListOf<NativeAttachment>()
    val skills = mutableStateListOf<NativeSkill>()
    val selectedSkills = mutableStateListOf<NativeSkill>()
    val toolDetails = mutableStateListOf<String>()
    val liveSubagents = mutableStateListOf<String>()
    val subagentHistoryRefs = mutableStateMapOf<String, String>()
    val subagentHistoryMessageCounts = mutableStateMapOf<String, Int>()
    val subagentStatuses = mutableStateMapOf<String, String>()
    val subagentHistoryErrors = mutableStateMapOf<String, String>()
    val loadingSubagentHistories = mutableStateListOf<String>()
    val queuedFollowUps = mutableStateListOf<NativeQueuedFollowUp>()
    /** Stable domain snapshots shared by live and historical renderers. */
    val activityGroups = mutableStateListOf<NativeActivityGroup>()
    val compactionItems = mutableStateListOf<NativeCompactionItem>()
    private val activityReducer = NativeActivityReducer()
    private val compactionReducer = NativeCompactionReducer()
    private val planStreamParser = NativePlanStreamParser()
    private val dedicatedPlanParser = NativePlanStreamParser()
    private var historicalActivityGroups: List<NativeActivityGroup> = emptyList()
    private var historicalCompactions: List<NativeCompactionItem> = emptyList()
    private var localProtocolSequence = 0L
    private var parsedAssistantText = ""
    /** Remains active even while a tag-only prefix has not produced visible assistant text. */
    private var assistantParserActive = false
    /**
     * Completion barriers are not guaranteed to be unique across app-server versions: a
     * normalized item/completed event may be followed by the retained legacy final-answer
     * callback.  Keep a per-turn terminal identity/text guard so that race cannot append a second
     * visually identical assistant bubble.  This is deliberately reset at turn boundaries, so
     * two intentional identical replies in different turns remain visible.
     */
    private val terminalAssistantItemIds = HashSet<String>()
    private var terminalAssistantText = ""
    /** Scope used for proposed-plan message keys during the current user turn. */
    private var activePlanScopeKey = ""
    /**
     * Reasoning may complete before the next command starts. That is not an assistant-message
     * boundary; only visible assistant body should split an exploration segment.
     */
    private var assistantBodySeenInPhase = false
    var conversationRenderModel by mutableStateOf(NativeConversationRenderModel())
        private set
    var followUpSubmitAction by mutableStateOf(NativeFollowUpSubmitAction.STEER)
    var input by mutableStateOf("")
    var connectionLabel by mutableStateOf("正在启动 Codex…")
    var ready by mutableStateOf(false)
    var historyLoading by mutableStateOf(false)
    var phase by mutableStateOf(NativeTurnPhase.IDLE)
    val busy: Boolean get() = phase.active
    var modelLabel by mutableStateOf("")
    var conversationTitle by mutableStateOf("新对话")
    var conversationAnimationKey by mutableStateOf("new-${UUID.randomUUID()}")
    var selectedModel by mutableStateOf("")
    var selectedEffort by mutableStateOf("high")
    var selectedMode by mutableStateOf("default")
    var activeGoalObjective by mutableStateOf("")
    var activeGoalStatus by mutableStateOf("active")
    var pendingUserInputRequest by mutableStateOf("")
    var pendingApprovalRequest by mutableStateOf("")
    var pendingPlanImplementation by mutableStateOf("")
    var projectPath by mutableStateOf("")
    var gitSnapshot by mutableStateOf("")
    var gitBusy by mutableStateOf(false)
    var gitError by mutableStateOf("")
    var gitNotice by mutableStateOf("")
    val gitDiffs = mutableStateMapOf<String, String>()
    val gitDiffLoading = mutableStateListOf<String>()
    val workspaceSnapshots = mutableStateListOf<NativeWorkspaceSnapshot>()
    var workspaceSnapshotBusy by mutableStateOf(false)
    var workspaceSnapshotError by mutableStateOf("")
    var workspaceSnapshotNotice by mutableStateOf("")
    var workspaceSnapshotPreview by mutableStateOf("")
    val workspaceSnapshotDiffs = mutableStateMapOf<String, String>()
    val workspaceSnapshotDiffLoading = mutableStateListOf<String>()
    val worktrees = mutableStateListOf<NativeWorktreeEntry>()
    var worktreeBusy by mutableStateOf(false)
    var worktreeError by mutableStateOf("")
    var worktreeNotice by mutableStateOf("")
    var worktreeMergePreview by mutableStateOf("")
    var worktreePrHandoff by mutableStateOf("")
    var permissionMode by mutableStateOf(NativePermissionMode.FULL_ACCESS)
    var planJson by mutableStateOf("[]")
    var planExplanation by mutableStateOf("")
    var planPanelAdded by mutableStateOf(false)
    private var activeProposedPlanItemId = ""
    private var activeProposedPlanDedicated = false
    var revision by mutableIntStateOf(0)
    // The active assistant answer is intentionally not stored in messages on every token batch.
    // Keeping the LazyColumn backing list structurally stable prevents all visible history items
    // from being reconsidered whenever the growing answer changes.
    var liveAssistantMessageId by mutableStateOf("")
        private set
    /** Protocol item identity for the currently streamed assistant item. */
    private var activeAssistantItemId = ""
    private val liveAssistantMarkdown = NativeStreamingMarkdownAccumulator()
    /** Full text is materialized only for completion/actions/tests, never by the live Composable. */
    val liveAssistantText: String
        get() {
            liveAssistantSnapshot
            val assistant = liveAssistantMarkdown.materialize()
            // Keep the historical testing/export contract (which exposed the raw stream as one
            // string) while the renderer uses the parser's separated assistant/plan messages.
            val plan = planStreamParser.planText
            return assistant + plan
        }
    var liveAssistantSnapshot by mutableStateOf(
        NativeStreamingMarkdownSnapshot(emptyList(), "", stableChars = 0, sourceChars = 0),
    )
        private set
    // Draw acknowledgement, not Compose state. It distinguishes a genuinely streamed answer from
    // deltas and completion barriers that were coalesced before the UI got a frame.
    private var liveAssistantPresentedChars = 0
    private var pendingTurnUsage: NativeTurnUsage? = null
    var processingLabel by mutableStateOf("")
    // Reasoning accumulates in a StringBuilder and the observable exposes an immutable
    // snapshot. This replaces `reasoningText += delta`, which reallocated the entire growing
    // String on every flush (O(n^2) over a long chain of thought).
    private val reasoningBuilder = StringBuilder()
    private val reasoningTextState = mutableStateOf("")
    val reasoningText: String get() = reasoningTextState.value
    var reasoningComplete by mutableStateOf(false)
    var reasoningCompletedAt by mutableStateOf(0L)
    // commandText is a bounded live preview. The complete stream never enters Compose
    // snapshot state; it is buffered privately and moved into NativeCommandOutputStore.
    var commandText by mutableStateOf("")
    var liveCommandJson by mutableStateOf("")
    private val commandOutputBuffer = StringBuilder()
    /**
     * App-server can run several command items in parallel. The legacy fields above remain as
     * the currently selected preview for old callbacks, while this map is the authoritative
     * private buffer used by normalized itemId-aware events. Keeping full output out of Compose
     * state preserves the bounded preview/reference contract even when commands overlap.
     */
    private data class LiveCommandBuffer(
        var raw: String,
        val output: StringBuilder = StringBuilder(),
    )
    private val liveCommandBuffers = LinkedHashMap<String, LiveCommandBuffer>()
    private var activeCommandKey = ""
    var currentThreadId by mutableStateOf("")
    var currentTurnId by mutableStateOf("")
    var turnStartedAt by mutableStateOf(0L)
    var turnMessageStartIndex by mutableIntStateOf(0)
    var phaseStartedAt by mutableStateOf(0L)
    var phaseMessageStartIndex by mutableIntStateOf(0)
    private var retryStatusMessageId = ""
    private var retryStatusAttempts = 0

    private fun nextProtocolSequence(): Long = ++localProtocolSequence

    private fun publishDomainSnapshot() {
        val liveActivities = activityReducer.groups()
        val activities = historicalActivityGroups + liveActivities
        val compactions = NativeHistoryAdapter.mergeCompactionTimeline(
            historicalCompactions + compactionReducer.items(currentThreadId.takeIf { it.isNotBlank() }),
        )
        activityGroups.clear()
        activityGroups.addAll(liveActivities)
        compactionItems.clear()
        compactionItems.addAll(compactions)
        val assistantMessages = messages.filter { it.role == NativeChatRole.ASSISTANT }
            .map { NativeConversationTextMessage(it.id, it.content, it.streaming, currentTurnId.takeIf { id -> id.isNotBlank() }) }
        val userMessages = messages.filter { it.role == NativeChatRole.USER }
            .map { NativeConversationTextMessage(it.id, it.content, it.streaming, null) }
        val plans = messages.asSequence()
            .filter { it.role == NativeChatRole.ACTIVITY && it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) }
            .map { NativePlanRenderItem(it.id, decodeNativeProposedPlan(it.content), it.streaming, dedicated = true) }
            .toList()
        val subagents = liveSubagents.mapNotNull { raw ->
            runCatching {
                val item = JSONObject(raw)
                NativeSubagentVisualFactory.create(
                    agentThreadId = item.optString("agentThreadId", item.optString("agent_thread_id")),
                    callId = item.optString("callId", item.optString("call_id", item.optString("id"))),
                    name = item.optString("agentName", item.optString("agentNickname", item.optString("name"))),
                    status = item.optString("status", "waiting"),
                )
            }.getOrNull()
        }
        conversationRenderModel = NativeConversationRenderModel(
            threadId = currentThreadId,
            activities = activities,
            compactions = compactions,
            plans = plans,
            subagents = NativeSubagentVisualFactory.mergeAll(subagents),
            assistantMessages = assistantMessages,
            userMessages = userMessages,
            errors = messages.filter { it.role == NativeChatRole.ERROR }.map { it.content },
            lastSequence = localProtocolSequence,
        )
    }

    /**
     * Completed exploration rows are already durable PROCESS/PROCESS2 messages. Rebuild that
     * small history projection before a replacement/new turn, then clear only the live reducer.
     * This prevents an early legacy event without turnId from attaching to the previous turn
     * while keeping the renderer-neutral conversation model complete.
     */
    private fun resetLiveActivityReducerForTurn() {
        historicalActivityGroups = NativeHistoryAdapter.renderModel(currentThreadId, messages).activities
        activityReducer.reset()
        activityGroups.clear()
    }

    /** Entry point for normalized bridge events. Legacy callbacks continue to call the same reducers. */
    fun acceptProtocolEvent(event: NativeProtocolEvent) {
        if (event.threadId.isNotBlank() && currentThreadId.isNotBlank() && event.threadId != currentThreadId) return
        if (!event.turnId.isNullOrBlank()) currentTurnId = event.turnId.orEmpty()
        if (event.sequence > localProtocolSequence) localProtocolSequence = event.sequence
        activityReducer.accept(event)
        val compaction = compactionReducer.accept(event)
        if (compaction != null) upsertCompactionMessage(compaction)
        publishDomainSnapshot()
    }

    /**
     * Applies a normalized event to the live stream as well as to the domain reducers.
     *
     * Older bridges still emit the legacy callbacks consumed by CodexChatActivity; that path
     * intentionally calls [acceptProtocolEvent] only (domain update). Newer normalized events use
     * this entry point, so a protocol replay or a bridge that omits legacy callbacks still paints
     * reasoning, assistant, plan, command and tool output correctly.
     */
    fun acceptNormalizedProtocolEvent(event: NativeProtocolEvent) {
        if (event.threadId.isNotBlank() && currentThreadId.isNotBlank() && event.threadId != currentThreadId) return
        // Plan start callbacks frequently arrive before their first delta. Capture the turn
        // identity before item-specific handlers create a message key so all deltas share one
        // scope.
        if (!event.turnId.isNullOrBlank()) currentTurnId = event.turnId.orEmpty()
        when (event) {
            is NativeProtocolEvent.ReasoningDelta -> {
                beginReasoningAfterAnswerIfNeeded()
                phase = NativeTurnPhase.REASONING
                processingLabel = "正在思考"
                appendReasoning(event.delta)
            }
            is NativeProtocolEvent.ReasoningCompleted -> {
                if (event.text.isNotBlank() && event.text.length > reasoningText.length) replaceReasoning(event.text)
                finishReasoning()
            }
            is NativeProtocolEvent.AssistantDelta -> {
                if (event.delta.isNotBlank()) {
                    finishReasoning()
                    appendAssistant(event.delta, event.itemId)
                }
            }
            is NativeProtocolEvent.AssistantCompleted -> {
                finishReasoning()
                if (event.finalAnswer) appendAssistantFinal(event.text, event.itemId)
                else completeAssistantItem(event.text, event.itemId)
            }
            is NativeProtocolEvent.CommandStarted -> {
                beginReasoningAfterAnswerIfNeeded()
                startCommand(event.payload.ifBlank {
                    JSONObject().put("id", event.itemId ?: JSONObject.NULL)
                        .put("command", event.command).put("cwd", event.cwd).toString()
                }, event.itemId)
            }
            is NativeProtocolEvent.CommandOutput -> appendCommandOutput(event.delta, event.itemId)
            is NativeProtocolEvent.CommandCompleted -> completeCommand(event.payload.ifBlank {
                JSONObject().put("id", event.itemId ?: JSONObject.NULL)
                    .put("command", event.command).put("status", event.status)
                    .apply { event.exitCode?.let { put("exitCode", it) } }
                    .put(NativeCommandOutputStore.OUTPUT_REF, event.outputRef)
                    .toString()
            }, event.itemId)
            is NativeProtocolEvent.ToolCompleted -> {
                beginReasoningAfterAnswerIfNeeded()
                val payload = event.payload.ifBlank {
                    JSONObject().put("id", event.itemId ?: JSONObject.NULL)
                        .put("type", event.type).put("tool", event.title)
                        .put("status", event.status).toString()
                }
                upsertToolDetail(payload)
            }
            is NativeProtocolEvent.PlanStarted -> {
                closeActivityBoundary()
                startProposedPlan(
                    JSONObject().put("itemId", event.itemId ?: JSONObject.NULL).toString(),
                )
                phaseMessageStartIndex = messages.size
            }
            is NativeProtocolEvent.PlanDelta -> {
                finishReasoning()
                appendProposedPlanDelta(
                    JSONObject().put("itemId", event.itemId ?: JSONObject.NULL).put("delta", event.delta).toString(),
                )
            }
            is NativeProtocolEvent.PlanCompleted -> completeProposedPlan(
                JSONObject().put("itemId", event.itemId ?: JSONObject.NULL).put("text", event.text).toString(),
            )
            is NativeProtocolEvent.SubagentUpdated -> {
                beginReasoningAfterAnswerIfNeeded()
                updateSubagent(
                    JSONObject().put("agentThreadId", event.agentThreadId)
                        .put("callId", event.callId).put("agentName", event.name).put("status", event.status)
                        .put("id", event.itemId ?: JSONObject.NULL).toString(),
                )
            }
            is NativeProtocolEvent.CompactionStarted -> closeActivityBoundary()
            is NativeProtocolEvent.TokenUsageUpdated -> updateTokenUsage(
                JSONObject().put("inputTokens", event.inputTokens)
                    .put("cachedInputTokens", event.cachedInputTokens)
                    .put("outputTokens", event.outputTokens)
                    .put("reasoningOutputTokens", event.reasoningTokens)
                    .put("contextWindow", event.contextWindow)
                    .put("contextTokens", event.currentContextTokens)
                    .put("autoCompactTokenLimit", event.autoCompactTokenLimit)
                    .put("contextUsageReliable", event.contextUsageReliable)
                    .put("estimated", event.estimated).toString(),
            )
            else -> Unit
        }
        acceptProtocolEvent(event)
        if (event is NativeProtocolEvent.CompactionStarted) {
            // The divider is appended by acceptProtocolEvent. Future activity snapshots must be
            // anchored after it so the next exploration cannot be inserted before the divider.
            phaseMessageStartIndex = messages.size
        }
    }

    fun beginManualCompaction(requestId: String? = null): NativeCompactionItem {
        if (phase.active) closeActivityBoundary()
        val item = compactionReducer.createManualPending(
            threadId = currentThreadId,
            turnId = currentTurnId.takeIf { it.isNotBlank() },
            requestId = requestId,
        )
        upsertCompactionMessage(item)
        if (phase.active) phaseMessageStartIndex = messages.size
        publishDomainSnapshot()
        return item
    }

    fun beginAutomaticCompaction(requestId: String? = null): NativeCompactionItem {
        if (phase.active) closeActivityBoundary()
        val item = compactionReducer.createAutomaticPending(
            threadId = currentThreadId,
            turnId = currentTurnId.takeIf { it.isNotBlank() },
            requestId = requestId,
        )
        upsertCompactionMessage(item)
        if (phase.active) phaseMessageStartIndex = messages.size
        publishDomainSnapshot()
        return item
    }

    fun completeManualCompactionRpc(
        requestId: String? = null,
        success: Boolean,
        cancelled: Boolean = false,
        error: String = "",
        threadId: String? = null,
    ): NativeCompactionItem? {
        val item = compactionReducer.onRpcResult(
            threadId = threadId?.takeIf { it.isNotBlank() } ?: currentThreadId,
            requestId = requestId,
            success = success,
            cancelled = cancelled,
            error = error,
        ) ?: return null
        upsertCompactionMessage(item)
        publishDomainSnapshot()
        return item
    }

    fun restoreCompactions(items: Iterable<NativeCompactionItem>) {
        items.forEach { item ->
            if (item.threadId.isNotBlank() && currentThreadId.isNotBlank() && item.threadId != currentThreadId) return@forEach
            val restored = compactionReducer.restore(item)
            historicalCompactions = NativeHistoryAdapter.mergeCompactionTimeline(historicalCompactions + restored)
            val canonical = historicalCompactions.firstOrNull { candidate ->
                candidate.id == restored.id ||
                    (!restored.serverItemId.isNullOrBlank() && candidate.serverItemId == restored.serverItemId) ||
                    (!restored.requestId.isNullOrBlank() && candidate.requestId == restored.requestId) ||
                    NativeHistoryAdapter.mergeCompactionTimeline(listOf(candidate, restored)).size == 1
            } ?: restored
            upsertCompactionMessage(canonical)
        }
        publishDomainSnapshot()
    }

    private fun upsertCompactionMessage(item: NativeCompactionItem) {
        val content = NativeHistoryAdapter.encodeCompaction(item)
        val index = messages.indexOfFirst { message ->
            if (message.id == item.id) return@indexOfFirst true
            val existing = NativeHistoryAdapter.decodeCompaction(message.content) ?: return@indexOfFirst false
            if (!item.serverItemId.isNullOrBlank() && existing.serverItemId == item.serverItemId) return@indexOfFirst true
            // A legacy history notification may have no server id; collapse it into the journal
            // record when the timeline merger identifies the same short-lived lifecycle.
            NativeHistoryAdapter.mergeCompactionTimeline(listOf(existing, item)).size == 1
        }
        val updated = NativeChatMessage(
            id = item.id,
            role = NativeChatRole.ACTIVITY,
            content = content,
            streaming = !item.isTerminal,
            revealStartedAt = item.createdAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        if (index >= 0) messages[index] = updated else messages.add(updated)
        revision++
    }

    fun resetConversation() {
        historyLoading = false
        messages.clear()
        planJson = "[]"
        planExplanation = ""
        planPanelAdded = false
        activeProposedPlanItemId = ""
        activeProposedPlanDedicated = false
        activePlanScopeKey = ""
        activeGoalObjective = ""
        activeGoalStatus = "active"
        retryStatusMessageId = ""
        retryStatusAttempts = 0
        pendingUserInputRequest = ""
        pendingApprovalRequest = ""
        pendingPlanImplementation = ""
        gitSnapshot = ""
        gitBusy = false
        gitError = ""
        gitNotice = ""
        gitDiffs.clear()
        gitDiffLoading.clear()
        workspaceSnapshots.clear()
        workspaceSnapshotBusy = false
        workspaceSnapshotError = ""
        workspaceSnapshotNotice = ""
        workspaceSnapshotPreview = ""
        workspaceSnapshotDiffs.clear()
        workspaceSnapshotDiffLoading.clear()
        worktrees.clear()
        worktreeBusy = false
        worktreeError = ""
        worktreeNotice = ""
        worktreeMergePreview = ""
        worktreePrHandoff = ""
        phase = NativeTurnPhase.IDLE
        processingLabel = ""
        clearReasoning()
        reasoningComplete = false
        reasoningCompletedAt = 0L
        assistantBodySeenInPhase = false
        clearLiveCommandBuffers()
        pendingTurnUsage = null
        clearLiveAssistantBuffer()
        toolDetails.clear()
        liveSubagents.clear()
        subagentHistoryRefs.clear()
        subagentHistoryMessageCounts.clear()
        subagentStatuses.clear()
        subagentHistoryErrors.clear()
        loadingSubagentHistories.clear()
        queuedFollowUps.clear()
        activityReducer.reset()
        compactionReducer.reset()
        planStreamParser.reset()
        dedicatedPlanParser.reset()
        historicalActivityGroups = emptyList()
        historicalCompactions = emptyList()
        parsedAssistantText = ""
        localProtocolSequence = 0L
        resetTerminalAssistantDeduplication()
        activityGroups.clear()
        compactionItems.clear()
        conversationRenderModel = NativeConversationRenderModel(threadId = currentThreadId)
        currentTurnId = ""
        turnStartedAt = 0L
        turnMessageStartIndex = 0
        phaseStartedAt = 0L
        phaseMessageStartIndex = 0
        revision++
    }

    fun addFollowUpUser(text: String, skills: List<NativeSkill>, attachments: List<NativeAttachment>): NativeChatMessage {
        val message = NativeChatMessage(
            role = NativeChatRole.USER,
            content = text,
            skills = skills.toList(),
            attachments = attachments.toList(),
        )
        messages.add(message)
        revision++
        return message
    }

    fun removeMessage(messageId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            messages.removeAt(index)
            revision++
        }
    }

    fun addUser(text: String, skills: List<NativeSkill> = emptyList(), attachments: List<NativeAttachment> = emptyList()): NativeChatMessage {
        // A new user turn starts a fresh retry presentation. Keep any old card in history, but
        // never let a later error update it instead of the current turn's card.
        retryStatusMessageId = ""
        retryStatusAttempts = 0
        val message = NativeChatMessage(role = NativeChatRole.USER, content = text, skills = skills.toList(), attachments = attachments.toList())
        messages.add(message)
        turnMessageStartIndex = messages.size
        resetLiveActivityReducerForTurn()
        phase = NativeTurnPhase.WAITING
        processingLabel = "处理中"
        clearReasoning()
        reasoningComplete = false
        reasoningCompletedAt = 0L
        assistantBodySeenInPhase = false
        clearLiveCommandBuffers()
        pendingTurnUsage = null
        clearLiveAssistantBuffer()
        toolDetails.clear()
        liveSubagents.clear()
        planStreamParser.reset()
        dedicatedPlanParser.reset()
        parsedAssistantText = ""
        resetTerminalAssistantDeduplication()
        activeProposedPlanItemId = ""
        activeProposedPlanDedicated = false
        activePlanScopeKey = ""
        // The new user message starts a fresh protocol turn.  Keeping the previous turn id here
        // can make an early legacy token-usage callback latch automatic compaction to the old
        // turn before app-server sends its first item/started event.
        currentTurnId = ""
        turnStartedAt = System.currentTimeMillis()
        phaseStartedAt = turnStartedAt
        phaseMessageStartIndex = turnMessageStartIndex
        revision++
        return message
    }

    /** Reset transient live state when editing/retrying without appending another user row. */
    fun prepareReplacementTurn() {
        turnMessageStartIndex = messages.size
        resetLiveActivityReducerForTurn()
        phase = NativeTurnPhase.WAITING
        processingLabel = "处理中"
        clearReasoning()
        reasoningComplete = false
        reasoningCompletedAt = 0L
        assistantBodySeenInPhase = false
        clearLiveCommandBuffers()
        pendingTurnUsage = null
        clearLiveAssistantBuffer()
        toolDetails.clear()
        liveSubagents.clear()
        activeProposedPlanItemId = ""
        activeProposedPlanDedicated = false
        activePlanScopeKey = ""
        resetTerminalAssistantDeduplication()
        currentTurnId = ""
        turnStartedAt = System.currentTimeMillis()
        phaseStartedAt = turnStartedAt
        phaseMessageStartIndex = turnMessageStartIndex
        publishDomainSnapshot()
        revision++
    }

    private fun resetTerminalAssistantDeduplication() {
        terminalAssistantItemIds.clear()
        terminalAssistantText = ""
    }

    private fun isDuplicateTerminalAssistant(text: String, itemId: String?): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) return false
        val identity = itemId?.trim()?.takeIf { it.isNotBlank() }
        if (identity != null && identity in terminalAssistantItemIds) return true
        // Legacy callbacks have no item id.  Only suppress an exact terminal match in the current
        // turn; a prefix/extension is still a legitimate completion update and must be merged.
        if (identity == null && normalized == terminalAssistantText) return true
        val existing = (messages.lastIndex downTo turnMessageStartIndex.coerceAtLeast(0))
            .firstOrNull { index ->
                val message = messages[index]
                message.role == NativeChatRole.ASSISTANT && !message.streaming &&
                    message.content.trim() == normalized
            }
        return existing != null && identity == null
    }

    private fun rememberTerminalAssistant(text: String, itemId: String?) {
        text.trim().takeIf { it.isNotBlank() }?.let { terminalAssistantText = it }
        itemId?.trim()?.takeIf { it.isNotBlank() }?.let(terminalAssistantItemIds::add)
    }

    private fun commandBufferKey(raw: String = "", itemId: String? = null): String {
        val explicit = itemId?.takeIf { it.isNotBlank() }
        if (explicit != null) return "item:$explicit"
        val parsed = runCatching { JSONObject(raw) }.getOrNull()
            ?.optString("id")?.takeIf { it.isNotBlank() }
        return parsed?.let { "item:$it" } ?: "legacy"
    }

    private fun syncActiveCommandPreview() {
        val active = liveCommandBuffers[activeCommandKey]
        if (active == null) {
            liveCommandJson = ""
            commandOutputBuffer.setLength(0)
            commandText = ""
            return
        }
        liveCommandJson = active.raw
        commandOutputBuffer.setLength(0)
        commandOutputBuffer.append(active.output)
        commandText = NativeCommandOutputStore.livePreview(active.output)
    }

    private fun clearLiveCommandBuffers() {
        liveCommandBuffers.clear()
        activeCommandKey = ""
        liveCommandJson = ""
        commandOutputBuffer.setLength(0)
        commandText = ""
    }

    /** Compatibility entry point for Activity flows that restart a turn without a new message. */
    fun resetLiveCommandState() {
        clearLiveCommandBuffers()
        revision++
    }

    private fun upsertToolDetail(raw: String) {
        if (raw.isBlank()) return
        val incoming = runCatching { JSONObject(raw) }.getOrNull()
        val incomingId = incoming?.optString("id")?.takeIf { it.isNotBlank() }
        val incomingType = incoming?.optString("type").orEmpty()
        val index = toolDetails.indexOfFirst { existingRaw ->
            if (existingRaw == raw) return@indexOfFirst true
            if (incomingId == null) return@indexOfFirst false
            val existing = runCatching { JSONObject(existingRaw) }.getOrNull() ?: return@indexOfFirst false
            existing.optString("id") == incomingId &&
                (incomingType.isBlank() || existing.optString("type").isBlank() || existing.optString("type") == incomingType)
        }
        if (index >= 0) toolDetails[index] = raw else toolDetails.add(raw)
    }

    fun addToolDetail(raw: String) {
        upsertToolDetail(raw)
        revision++
    }

    private fun processPayload(): String {
        val tools = JSONArray()
        toolDetails.forEach { raw -> tools.put(runCatching { JSONObject(raw) }.getOrElse { raw }) }
        liveCommandBuffers.values.forEach { live ->
            runCatching { JSONObject(live.raw) }.getOrNull()?.let { item ->
                item.put("type", "commandExecution")
                item.put("status", item.optString("status", "inProgress"))
                val compact = NativeCommandOutputStore.compactCommandItem(item, live.output.toString())
                tools.put(JSONObject(compact))
            }
        }
        liveSubagents.forEach { raw ->
            val item = runCatching { JSONObject(raw) }.getOrNull()
            if (item != null) tools.put(item)
        }
        val duration = if (reasoningText.isBlank()) 0L else {
            ((System.currentTimeMillis() - phaseStartedAt).coerceAtLeast(1L) / 1000L).coerceAtLeast(1L)
        }
        return JSONObject()
            .put("duration", duration)
            .put("reasoning", reasoningText.trim())
            .put("command", "")
            .put("tools", tools)
            .put("reasoningUnavailable", reasoningText.isBlank())
            .toString()
    }

    /**
     * Compatibility projection for legacy callbacks that update the retained state fields before
     * their normalized protocol event reaches the reducer. The UI still consumes the same domain
     * DTO and renderer; this adapter is not a second presentation path.
     */
    fun legacyLiveActivityGroup(): NativeActivityGroup? {
        val hasActivity = reasoningText.isNotBlank() || liveCommandBuffers.isNotEmpty() ||
            toolDetails.isNotEmpty() || liveSubagents.isNotEmpty()
        if (!hasActivity && !phase.active) return null
        val keyTurn = currentTurnId.ifBlank { "pending" }
        return NativeHistoryAdapter.processGroup(
            messageId = "live-fallback:$keyTurn",
            threadId = currentThreadId,
            payload = JSONObject(processPayload()),
            createdAtMs = phaseStartedAt.takeIf { it > 0L } ?: turnStartedAt,
        ).copy(
            key = "live-fallback:${currentThreadId.ifBlank { "thread" }}:$keyTurn",
            turnId = currentTurnId.takeIf { it.isNotBlank() },
            running = phase.active,
            expandedByDefault = phase.active,
            completedAtMs = if (phase.active) 0L else System.currentTimeMillis(),
        )
    }

    private fun sealCurrentPhase() {
        if (reasoningText.isBlank() && liveCommandBuffers.isEmpty() && toolDetails.isEmpty() && liveSubagents.isEmpty()) return
        val process = "PROCESS2|" + NativeBase64.encode(processPayload().toByteArray(Charsets.UTF_8))
        val insertAt = (phaseMessageStartIndex until messages.size)
            .firstOrNull {
                messages[it].role == NativeChatRole.ASSISTANT ||
                    messages[it].content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) ||
                    NativeHistoryAdapter.decodeCompaction(messages[it].content) != null
            }
            ?: messages.size
        messages.add(insertAt, NativeChatMessage(role = NativeChatRole.ACTIVITY, content = process, revealStartedAt = System.currentTimeMillis()))
    }

    fun beginReasoningAfterAnswerIfNeeded() {
        // A reasoning completion is only a stream phase hint. Commands and tools emitted before
        // the first assistant body still belong to the same exploration group; the visible
        // assistant message is the actual boundary.
        if (!assistantBodySeenInPhase) return
        sealCurrentPhase()
        val lastAssistant = messages.indexOfLast { it.role == NativeChatRole.ASSISTANT && it.streaming }
        if (lastAssistant >= 0) sealLiveAssistant(lastAssistant)
        clearReasoning()
        reasoningComplete = false
        reasoningCompletedAt = 0L
        clearLiveCommandBuffers()
        toolDetails.clear()
        liveSubagents.clear()
        assistantBodySeenInPhase = false
        processingLabel = "\u6b63\u5728\u601d\u8003"
        phaseStartedAt = System.currentTimeMillis()
        phaseMessageStartIndex = messages.size
        revision++
    }

    /** Close the current exploration before a dedicated plan or compaction lifecycle item. */
    fun closeActivityBoundary() {
        val liveAssistant = messages.indexOfLast {
            it.role == NativeChatRole.ASSISTANT && it.streaming && it.id == liveAssistantMessageId
        }
        if (liveAssistant >= 0) sealLiveAssistant(liveAssistant)
        sealCurrentPhase()
        clearReasoning()
        reasoningComplete = false
        reasoningCompletedAt = 0L
        clearLiveCommandBuffers()
        toolDetails.clear()
        liveSubagents.clear()
        assistantBodySeenInPhase = false
        phaseStartedAt = System.currentTimeMillis()
        phaseMessageStartIndex = messages.size
        revision++
    }

    fun appendReasoning(delta: String) {
        if (delta.isEmpty()) return
        reasoningBuilder.append(delta)
        reasoningTextState.value = reasoningBuilder.toString()
    }

    fun replaceReasoning(full: String) {
        reasoningBuilder.setLength(0)
        reasoningBuilder.append(full)
        reasoningTextState.value = full
    }

    fun clearReasoning() {
        reasoningBuilder.setLength(0)
        reasoningTextState.value = ""
    }

    fun finishReasoning() {
        if (reasoningComplete) return
        reasoningComplete = true
        reasoningCompletedAt = System.currentTimeMillis()
        revision++
    }

    private fun planScopeKey(): String {
        if (activePlanScopeKey.isBlank()) {
            activePlanScopeKey = currentTurnId.takeIf { it.isNotBlank() }
                ?: "local-${turnMessageStartIndex.coerceAtLeast(0)}"
        }
        return activePlanScopeKey
    }

    private fun proposedPlanMessageId(itemId: String): String =
        "proposed-plan:${planScopeKey()}:${itemId.ifBlank { "current" }}"

    private fun upsertProposedPlan(
        itemId: String,
        text: String,
        append: Boolean,
        streaming: Boolean,
        dedicated: Boolean = false,
    ) {
        val resolvedItemId = itemId.ifBlank { activeProposedPlanItemId.ifBlank { "current" } }
        activeProposedPlanItemId = resolvedItemId
        if (dedicated) activeProposedPlanDedicated = true
        val requestedMessageId = proposedPlanMessageId(resolvedItemId)
        val normalizedIncoming = text.trim().replace(Regex("\\s+"), " ")
        val currentTurnStart = turnMessageStartIndex.coerceIn(0, messages.size)
        val exactIndex = (currentTurnStart until messages.size).firstOrNull { index ->
            val candidate = messages[index]
            candidate.id == requestedMessageId && candidate.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX)
        } ?: -1
        val semanticIndex = if (exactIndex >= 0) exactIndex else (currentTurnStart until messages.size).lastOrNull { index ->
            val candidate = messages[index]
            if (!candidate.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX)) return@lastOrNull false
            val existing = decodeNativeProposedPlan(candidate.content).trim().replace(Regex("\\s+"), " ")
            existing.isNotBlank() && normalizedIncoming.isNotBlank() &&
                (existing == normalizedIncoming || existing.contains(normalizedIncoming) || normalizedIncoming.contains(existing))
        } ?: if (dedicated) {
            // The dedicated protocol item is authoritative. Reuse a tag-fallback row from the
            // same turn even when an early partial delta is not textually similar yet.
            (currentTurnStart until messages.size).lastOrNull { index ->
                messages[index].content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX)
            } ?: -1
        } else -1
        val existingIndex = semanticIndex
        // A dedicated plan item can start before its first delta. Keep the parser active, but do
        // not add an empty visual row to the transcript.
        if (existingIndex < 0 && normalizedIncoming.isBlank()) return
        if (!dedicated && activeProposedPlanDedicated && existingIndex >= 0) return
        val messageId = if (existingIndex >= 0) messages[existingIndex].id else requestedMessageId
        val previous = if (existingIndex >= 0) decodeNativeProposedPlan(messages[existingIndex].content) else ""
        val next = if (append) previous + text else when {
            text.isEmpty() -> previous
            previous.isEmpty() -> text
            text.startsWith(previous) -> text
            previous.startsWith(text) -> previous
            else -> text
        }
        val message = NativeChatMessage(
            id = messageId, role = NativeChatRole.ACTIVITY, content = encodeNativeProposedPlan(next), streaming = streaming,
            revealStartedAt = if (existingIndex >= 0) messages[existingIndex].revealStartedAt else System.currentTimeMillis(),
        )
        if (existingIndex >= 0) messages[existingIndex] = message else messages.add(message)
        phase = if (streaming) NativeTurnPhase.ANSWERING else phase
        revision++
    }

    fun startProposedPlan(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val item = payload.optJSONObject("item") ?: payload
        val itemId = item.optString("id", payload.optString("itemId"))
        dedicatedPlanParser.start(itemId, dedicated = true)
        val parsed = dedicatedPlanParser.append(item.optString("text"), itemId, dedicated = true)
        upsertProposedPlan(itemId, parsed.planText, false, true, dedicated = true)
    }

    fun appendProposedPlanDelta(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val itemId = payload.optString("itemId", payload.optString("item_id"))
        if (activeProposedPlanItemId.isBlank() ||
            (itemId.isNotBlank() && activeProposedPlanItemId != itemId)
        ) {
            // Several app-server versions omit item/started. The first dedicated delta is still
            // an independent boundary and must not leave the preceding reasoning/commands live
            // beside the plan card.
            closeActivityBoundary()
        }
        val parsed = dedicatedPlanParser.append(payload.optString("delta"), itemId, dedicated = true)
        upsertProposedPlan(itemId, parsed.planText, false, true, dedicated = true)
    }

    fun completeProposedPlan(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val item = payload.optJSONObject("item") ?: payload
        val itemId = item.optString("id", payload.optString("itemId"))
        if (activeProposedPlanItemId.isBlank() ||
            (itemId.isNotBlank() && activeProposedPlanItemId != itemId)
        ) {
            // A completion-only dedicated item is common in replayed/older histories. Close the
            // current exploration before adding it, just as an explicit PlanStarted would.
            closeActivityBoundary()
        }
        val parsed = dedicatedPlanParser.complete(item.optString("text"), itemId, dedicated = true)
        upsertProposedPlan(itemId, parsed.planText, false, false, dedicated = true)
    }

    private fun finishProposedPlanIfStreaming() {
        val index = messages.indexOfLast { it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) && it.streaming }
        if (index >= 0) messages[index] = messages[index].copy(streaming = false)
    }

    fun finishPlanPanel(stepCount: Int = 0) {
        val index = messages.indexOfFirst { it.role == NativeChatRole.ACTIVITY && it.content.startsWith("PLAN_PANEL|") }
        if (index >= 0) {
            messages[index] = messages[index].copy(content = "PLAN_PANEL|complete|$stepCount")
            revision++
        }
    }

    fun ensurePlanPanel() {
        if (messages.none { it.role == NativeChatRole.ACTIVITY && it.content.startsWith("PLAN_PANEL|") }) {
            val insertAt = messages.indexOfLast { it.role == NativeChatRole.ASSISTANT }.takeIf { it >= 0 } ?: messages.size
            messages.add(insertAt, NativeChatMessage(role = NativeChatRole.ACTIVITY, content = "PLAN_PANEL|"))
        }
        planPanelAdded = true
        revision++
    }

    private fun cleanProtocolMarkup(text: String): String =
        text.replace(PROTOCOL_MARKUP_REGEX, "")

    private fun clearLiveAssistantBuffer() {
        liveAssistantMarkdown.reset()
        liveAssistantMessageId = ""
        liveAssistantSnapshot = NativeStreamingMarkdownSnapshot(emptyList(), "", stableChars = 0, sourceChars = 0)
        liveAssistantPresentedChars = 0
        parsedAssistantText = ""
        activeAssistantItemId = ""
        planStreamParser.reset()
        dedicatedPlanParser.reset()
        assistantParserActive = false
    }

    fun markLiveAssistantPresented(messageId: String, sourceChars: Int) {
        if (messageId == liveAssistantMessageId && sourceChars > liveAssistantPresentedChars) {
            liveAssistantPresentedChars = sourceChars
        }
    }

    private fun sealLiveAssistant(index: Int, authoritativeText: String = ""): String {
        val buffered = liveAssistantMarkdown.materialize()
        val cleanedAuthoritative = cleanProtocolMarkup(authoritativeText)
        val full = when {
            cleanedAuthoritative.isBlank() -> buffered
            buffered.isBlank() -> cleanedAuthoritative
            cleanedAuthoritative.startsWith(buffered) -> cleanedAuthoritative
            buffered.startsWith(cleanedAuthoritative) -> buffered
            else -> cleanedAuthoritative
        }
        if (index in messages.indices) {
            val existing = messages[index]
            // Native stream events and their completion barrier can land in one main-loop task.
            // If Compose never presented a live snapshot, use the whole-answer reveal rather than
            // replacing the empty streaming shell with fully opaque Markdown in a single frame.
            val completionNeedsReveal = full.isNotBlank() && liveAssistantPresentedChars <= 0
            messages[index] = existing.copy(
                content = full,
                streaming = false,
                finalOnlyReveal = completionNeedsReveal,
                usage = pendingTurnUsage,
            )
        }
        clearLiveAssistantBuffer()
        return full
    }

    /**
     * The first visible assistant body is the WebUI boundary between exploration and response.
     * Seal reasoning, commands and tools immediately instead of waiting for turn/completed; this
     * keeps the live layout identical to the eventual history layout and prevents the fallback
     * panel from drawing the old reasoning inside the answer bubble.
     */
    private fun sealExplorationBeforeAssistantBody() {
        if (assistantBodySeenInPhase) return
        sealCurrentPhase()
        clearReasoning()
        reasoningComplete = false
        reasoningCompletedAt = 0L
        clearLiveCommandBuffers()
        toolDetails.clear()
        liveSubagents.clear()
        assistantBodySeenInPhase = true
        phaseStartedAt = System.currentTimeMillis()
        phaseMessageStartIndex = messages.size
    }

    fun appendAssistant(delta: String, itemId: String? = null) {
        if (delta.isEmpty()) return
        val normalizedItemId = itemId.orEmpty()
        if (!assistantParserActive ||
            (normalizedItemId.isNotBlank() && activeAssistantItemId.isNotBlank() && normalizedItemId != activeAssistantItemId)
        ) {
            planStreamParser.reset(normalizedItemId.takeIf { it.isNotBlank() })
            parsedAssistantText = ""
            assistantParserActive = true
        }
        if (normalizedItemId.isNotBlank()) activeAssistantItemId = normalizedItemId
        val parsed = planStreamParser.append(delta, normalizedItemId.takeIf { it.isNotBlank() })
        if (parsed.hasPlan) {
            upsertProposedPlan(activeProposedPlanItemId.ifBlank { "tagged-plan" }, parsed.planText, false, true)
        }
        val fullAssistant = parsed.assistantText
        val cleanedDelta = when {
            fullAssistant.startsWith(parsedAssistantText) -> fullAssistant.substring(parsedAssistantText.length)
            parsedAssistantText.startsWith(fullAssistant) -> ""
            else -> fullAssistant
        }
        if (cleanedDelta.isEmpty()) {
            parsedAssistantText = fullAssistant
            return
        }
        sealExplorationBeforeAssistantBody()
        phase = NativeTurnPhase.ANSWERING
        val last = messages.lastOrNull()
        if (last == null || last.role != NativeChatRole.ASSISTANT || !last.streaming || last.id != liveAssistantMessageId) {
            liveAssistantMarkdown.reset()
            liveAssistantSnapshot = NativeStreamingMarkdownSnapshot(emptyList(), "", stableChars = 0, sourceChars = 0)
            liveAssistantPresentedChars = 0
            val message = NativeChatMessage(
                role = NativeChatRole.ASSISTANT,
                content = "",
                streaming = true,
                revealStartedAt = System.currentTimeMillis(),
            )
            messages.add(message)
            liveAssistantMessageId = message.id
        }
        // Compose receives immutable blocks plus a bounded tail. The complete growing String stays
        // private, avoiding an O(total answer length) copy for every published token batch.
        if (!fullAssistant.startsWith(parsedAssistantText) && parsedAssistantText.isNotBlank()) {
            liveAssistantMarkdown.reset()
            liveAssistantSnapshot = liveAssistantMarkdown.append(fullAssistant)
        } else {
            liveAssistantSnapshot = liveAssistantMarkdown.append(cleanedDelta)
        }
        parsedAssistantText = fullAssistant
    }

    fun completeAssistantItem(text: String, itemId: String? = null) {
        val normalizedItemId = itemId.orEmpty().takeIf { it.isNotBlank() }
        val parsed = planStreamParser.complete(text, normalizedItemId)
        if (parsed.hasPlan) {
            upsertProposedPlan(activeProposedPlanItemId.ifBlank { "tagged-plan" }, parsed.planText, false, false)
        }
        val cleanedText = parsed.assistantText
        if (isDuplicateTerminalAssistant(cleanedText, normalizedItemId)) {
            assistantParserActive = false
            clearLiveAssistantBuffer()
            revision++
            return
        }
        if (cleanedText.isNotBlank()) sealExplorationBeforeAssistantBody()
        val existingIndex = (messages.lastIndex downTo turnMessageStartIndex.coerceAtLeast(0))
            .firstOrNull { messages[it].role == NativeChatRole.ASSISTANT && messages[it].streaming }
        if (existingIndex != null) {
            sealLiveAssistant(existingIndex, cleanedText)
        } else if (cleanedText.isNotBlank()) {
            messages.add(
                NativeChatMessage(
                    role = NativeChatRole.ASSISTANT,
                    content = cleanedText,
                    streaming = false,
                    revealStartedAt = System.currentTimeMillis(),
                    finalOnlyReveal = true,
                    usage = pendingTurnUsage,
                ),
            )
        }
        rememberTerminalAssistant(cleanedText, normalizedItemId)
        assistantParserActive = false
        revision++
    }

    fun appendAssistantFinal(text: String, itemId: String? = null) {
        val normalizedItemId = itemId.orEmpty().takeIf { it.isNotBlank() }
        val parsed = planStreamParser.complete(text, normalizedItemId)
        if (parsed.hasPlan) {
            upsertProposedPlan(activeProposedPlanItemId.ifBlank { "tagged-plan" }, parsed.planText, false, false)
        }
        val cleanedText = parsed.assistantText
        if (isDuplicateTerminalAssistant(cleanedText, normalizedItemId)) {
            phase = NativeTurnPhase.COMPLETED
            clearLiveAssistantBuffer()
            revision++
            return
        }
        if (cleanedText.isNotBlank()) sealExplorationBeforeAssistantBody()
        val streamingIndex = (messages.lastIndex downTo turnMessageStartIndex.coerceAtLeast(0))
            .firstOrNull { messages[it].role == NativeChatRole.ASSISTANT && messages[it].streaming }
        if (streamingIndex != null) {
            sealLiveAssistant(streamingIndex, cleanedText)
        } else if (cleanedText.isNotBlank()) {
            val existingIndex = (messages.lastIndex downTo turnMessageStartIndex.coerceAtLeast(0))
                .firstOrNull { messages[it].role == NativeChatRole.ASSISTANT }
            if (existingIndex != null) {
                val existing = messages[existingIndex]
                val merged = when {
                    cleanedText.startsWith(existing.content) -> cleanedText
                    existing.content.startsWith(cleanedText) -> existing.content
                    else -> cleanedText
                }
                messages[existingIndex] = existing.copy(content = merged, streaming = false, finalOnlyReveal = false, usage = pendingTurnUsage ?: existing.usage)
            } else {
                messages.add(
                    NativeChatMessage(
                        role = NativeChatRole.ASSISTANT,
                        content = cleanedText,
                        streaming = false,
                        revealStartedAt = System.currentTimeMillis(),
                        finalOnlyReveal = true,
                        usage = pendingTurnUsage,
                    ),
                )
            }
        }
        rememberTerminalAssistant(cleanedText, normalizedItemId)
        phase = NativeTurnPhase.COMPLETED
        clearLiveAssistantBuffer()
        revision++
    }

    fun replaceHistory(value: String) = applyHistorySnapshot(NativeHistoryParser.parse(value))

    fun applyHistorySnapshot(snapshot: NativeHistorySnapshot) {
        if (!busy) {
            activityReducer.reset()
            compactionReducer.reset()
            localProtocolSequence = 0L
        }
        clearLiveAssistantBuffer()
        pendingTurnUsage = null
        val cachedPlanJson = planJson
        val cachedPlanExplanation = planExplanation
        planJson = snapshot.planJson
        planExplanation = snapshot.planExplanation
        planPanelAdded = false
        activeProposedPlanItemId = ""
        activeProposedPlanDedicated = false
        activePlanScopeKey = ""
        val parsed = ArrayList<NativeChatMessage>(snapshot.messages.size + 1)
        parsed.addAll(snapshot.messages)
        var planPanelIndex = snapshot.planPanelIndex
        if (planJson == "[]" && cachedPlanJson != "[]") {
            planJson = cachedPlanJson
            planExplanation = cachedPlanExplanation
            planPanelIndex = parsed.size
        }
        if (planJson != "[]") {
            val count = runCatching { JSONArray(planJson).length() }.getOrDefault(0)
            parsed.add(
                planPanelIndex.coerceIn(0, parsed.size),
                NativeChatMessage(role = NativeChatRole.ACTIVITY, content = "PLAN_PANEL|complete|$count"),
            )
            planPanelAdded = true
        }
        // The parser already did JSON/Base64/DTO work off-main. Keep the UI boundary to one
        // snapshot-list replacement regardless of history size.
        messages.clear()
        messages.addAll(parsed)
        val historyModel = NativeHistoryAdapter.renderModel(currentThreadId, parsed)
        historicalActivityGroups = historyModel.activities
        historicalCompactions = historyModel.compactions
        historyModel.compactions.forEach(compactionReducer::restore)
        conversationRenderModel = historyModel
        publishDomainSnapshot()
        revision++
    }

    /** Java/legacy bridge compatibility overload. */
    fun startCommand(raw: String) = startCommand(raw, null)

    fun startCommand(raw: String, itemId: String?) {
        val key = commandBufferKey(raw, itemId)
        val existing = liveCommandBuffers[key]
        val buffer = existing ?: LiveCommandBuffer(raw)
        if (raw.isNotBlank()) buffer.raw = raw
        liveCommandBuffers[key] = buffer
        activeCommandKey = key
        syncActiveCommandPreview()
        phase = NativeTurnPhase.TOOL_RUNNING
        processingLabel = "\u6b63\u5728\u8fd0\u884c\u547d\u4ee4"
        revision++
    }

    /** Java/legacy bridge compatibility overload. */
    fun appendCommandOutput(delta: String) = appendCommandOutput(delta, null)

    fun appendCommandOutput(delta: String, itemId: String?) {
        if (delta.isEmpty()) return
        val key = commandBufferKey(liveCommandJson, itemId).let { candidate ->
            if (itemId.isNullOrBlank() && activeCommandKey.isNotBlank()) activeCommandKey else candidate
        }
        val seedRaw = if (!itemId.isNullOrBlank()) {
            JSONObject().put("id", itemId).put("type", "commandExecution").toString()
        } else liveCommandJson
        val buffer = liveCommandBuffers.getOrPut(key) { LiveCommandBuffer(seedRaw) }
        buffer.output.append(delta)
        activeCommandKey = key
        syncActiveCommandPreview()
        // commandText is independently observable; bumping the global revision here made the
        // work panel recompute its message scans on every terminal chunk.
    }

    fun commandOutputLength(): Int = liveCommandBuffers[activeCommandKey]?.output?.length
        ?: commandOutputBuffer.length

    /** Java/legacy bridge compatibility overload. */
    fun completeCommand(raw: String) = completeCommand(raw, null)

    fun completeCommand(raw: String, itemId: String?) {
        val item = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
        val key = commandBufferKey(raw, itemId).let { candidate ->
            if (itemId.isNullOrBlank() && item.optString("id").isBlank() && activeCommandKey.isNotBlank()) activeCommandKey else candidate
        }
        val live = liveCommandBuffers[key]
        val started = runCatching { JSONObject(live?.raw.orEmpty()) }.getOrNull()
        if (started != null) {
            started.keys().forEach { key ->
                val completedValue = item.opt(key)
                val missing = completedValue == null || completedValue == JSONObject.NULL ||
                    (completedValue is String && completedValue.isBlank())
                if (missing) item.put(key, started.opt(key))
            }
        }
        item.put("type", "commandExecution")
        if (item.optString("status").isBlank()) item.put("status", "completed")
        // The normal bridge payload already owns a cache ref. Do not materialize a second
        // full-size String from the live buffer on the main thread in that common path.
        val needsFallback = item.optString(NativeCommandOutputStore.OUTPUT_REF).isBlank() &&
            item.optString(NativeCommandOutputStore.STDERR_REF).isBlank()
        val fallback = if (needsFallback) live?.output?.toString().orEmpty() else ""
        upsertToolDetail(NativeCommandOutputStore.compactCommandItem(item, fallback))
        liveCommandBuffers.remove(key)
        activeCommandKey = liveCommandBuffers.keys.lastOrNull().orEmpty()
        syncActiveCommandPreview()
        processingLabel = "\u5df2\u6267\u884c\u547d\u4ee4"
        revision++
    }

    fun addActivity(type: String) {
        phase = if (type == "reasoning") NativeTurnPhase.REASONING else NativeTurnPhase.TOOL_RUNNING
        processingLabel = when (type) {
            "reasoning" -> "正在思考"
            "commandExecution" -> "正在运行命令"
            "fileChange" -> "正在修改文件"
            "mcpToolCall" -> "正在调用工具"
            "webSearch" -> "正在搜索"
            else -> "处理中"
        }
        revision++
    }

    private fun subagentAliases(item: JSONObject): Set<String> = buildSet {
        fun addSafe(value: String) {
            val normalized = value.trim()
            if (normalized.isNotEmpty() && !normalized.equals("null", true)) add(normalized)
        }
        addSafe(item.optString("agentThreadId", ""))
        addSafe(item.optString("id", ""))
        addSafe(item.optString("callId", ""))
        val receivers = item.optJSONArray("receiverThreadIds")
        if (receivers != null) for (index in 0 until receivers.length()) addSafe(receivers.optString(index, ""))
    }

    private fun normalizedSubagentStatus(value: String): String = when (value.trim().lowercase()) {
        "inprogress", "in_progress", "running", "started", "working" -> "working"
        "waiting", "pending", "queued" -> "waiting"
        "failed", "error", "cancelled", "canceled", "stopped", "interrupted" -> "failed"
        "done", "complete", "completed", "success" -> "done"
        else -> ""
    }

    fun updateSubagent(raw: String): String {
        val incoming = runCatching { JSONObject(raw) }.getOrNull() ?: return ""
        val incomingAliases = subagentAliases(incoming)
        val index = liveSubagents.indexOfFirst { existing ->
            runCatching { subagentAliases(JSONObject(existing)).any(incomingAliases::contains) }.getOrDefault(false)
        }
        val merged = if (index >= 0) {
            val existing = runCatching { JSONObject(liveSubagents[index]) }.getOrElse { JSONObject() }
            incoming.keys().forEach { key ->
                val value = incoming.opt(key)
                if (value != null && value != JSONObject.NULL && (!(value is String) || value.isNotBlank())) existing.put(key, value)
            }
            existing
        } else incoming
        if (index >= 0) liveSubagents[index] = merged.toString() else liveSubagents.add(merged.toString())
        val thread = subagentAliases(merged).firstOrNull { it.matches(SUBAGENT_THREAD_ID_REGEX) }
            ?: merged.optString("agentThreadId", "").takeUnless { it.equals("null", true) }.orEmpty()
        val status = normalizedSubagentStatus(merged.optString("status", ""))
        if (thread.isNotBlank() && status.isNotBlank()) subagentStatuses[thread] = status
        publishDomainSnapshot()
        revision++
        return thread
    }

    fun completeTurn() {
        finishReasoning()
        finishProposedPlanIfStreaming()
        if (liveCommandBuffers.isNotEmpty()) {
            // Seal every still-open command, not only the last legacy pointer. This matters when
            // parallel command items finish after the turn-level completion barrier.
            val openKeys = liveCommandBuffers.keys.toList()
            openKeys.forEach { key ->
                val unfinished = runCatching { JSONObject(liveCommandBuffers[key]?.raw.orEmpty()) }.getOrNull() ?: JSONObject()
                unfinished.put("status", "completed")
                completeCommand(unfinished.toString(), key.removePrefix("item:").takeIf { key.startsWith("item:") })
            }
        }
        sealCurrentPhase()
        clearReasoning()
        clearLiveCommandBuffers()
        toolDetails.clear()
        liveSubagents.clear()
        assistantBodySeenInPhase = false
        phaseMessageStartIndex = messages.size
        // A turn can contain commentary, a proposed plan and a final answer. Close every
        // live item created by this turn; only sealing the last assistant leaves plan/commentary
        // animations running forever when a provider omits an item phase.
        val liveAssistantIndex = messages.indexOfLast {
            it.role == NativeChatRole.ASSISTANT && it.streaming && it.id == liveAssistantMessageId
        }
        if (liveAssistantIndex >= 0) sealLiveAssistant(liveAssistantIndex)
        for (index in turnMessageStartIndex.coerceAtLeast(0) until messages.size) {
            if (messages[index].streaming) messages[index] = messages[index].copy(streaming = false)
        }
        finalizeTurnUsage()
        phase = NativeTurnPhase.COMPLETED
        connectionLabel = "已连接"
        revision++
    }

    fun updateTokenUsage(raw: String) {
        val duration = currentTurnDurationMs()
        val usage = NativeTokenUsageParser.parse(raw, duration) ?: return
        pendingTurnUsage = usage
        applyUsageToCurrentAssistant(usage)
        revision++
    }

    private fun finalizeTurnUsage() {
        val index = (messages.lastIndex downTo turnMessageStartIndex.coerceAtLeast(0))
            .firstOrNull { messages[it].role == NativeChatRole.ASSISTANT } ?: return
        val message = messages[index]
        val duration = currentTurnDurationMs().coerceAtLeast(1L)
        val usage = pendingTurnUsage?.copy(durationMs = duration)
            ?: NativeTokenUsageParser.estimate(message.content.ifBlank { liveAssistantMarkdown.materialize() }, duration)
        pendingTurnUsage = usage
        messages[index] = message.copy(usage = usage)
    }

    private fun applyUsageToCurrentAssistant(usage: NativeTurnUsage) {
        val index = (messages.lastIndex downTo turnMessageStartIndex.coerceAtLeast(0))
            .firstOrNull { messages[it].role == NativeChatRole.ASSISTANT } ?: return
        messages[index] = messages[index].copy(usage = usage)
    }

    private fun currentTurnDurationMs(): Long =
        if (turnStartedAt <= 0L) 0L else (System.currentTimeMillis() - turnStartedAt).coerceAtLeast(1L)

    fun addError(message: String) {
        messages.add(
            NativeChatMessage(
                role = NativeChatRole.ERROR,
                content = message.ifBlank { "Codex 后端发生未知错误" },
            ),
        )
        phase = NativeTurnPhase.FAILED
        connectionLabel = "连接异常"
        revision++
    }

    fun hasRetryStatus(): Boolean = retryStatusMessageId.isNotBlank()

    fun retryStatusAttempts(): Int = retryStatusAttempts

    /** Update one stable retry card instead of appending a new error for every provider retry. */
    fun updateRetryStatus(message: String, automaticGoal: Boolean, terminal: Boolean) {
        if (retryStatusMessageId.isBlank()) retryStatusMessageId = UUID.randomUUID().toString()
        retryStatusAttempts++
        val prefix = when {
            automaticGoal -> "目标自动重试 · 第 ${retryStatusAttempts} 次"
            terminal -> "重试失败 · 共 ${retryStatusAttempts} 次"
            else -> "正在重试 · 第 ${retryStatusAttempts} 次"
        }
        val content = "$prefix\n${message.ifBlank { "Codex 后端暂时没有返回结果" }}"
        val index = messages.indexOfFirst { it.id == retryStatusMessageId }
        val updated = NativeChatMessage(
            id = retryStatusMessageId,
            role = NativeChatRole.ERROR,
            content = content,
        )
        if (index >= 0) messages[index] = updated else messages.add(updated)
        phase = if (terminal) NativeTurnPhase.FAILED else NativeTurnPhase.WAITING
        processingLabel = if (automaticGoal) "目标仍在执行，准备重试…" else "正在重试…"
        connectionLabel = "连接异常"
        revision++
    }

    /** Replace the merged error card with a compact success notice after a retry recovers. */
    fun completeRetryStatus() {
        val index = messages.indexOfFirst { it.id == retryStatusMessageId }
        if (index >= 0) {
            messages[index] = messages[index].copy(
                role = NativeChatRole.ACTIVITY,
                content = "NOTICE|重试后已恢复（共 ${retryStatusAttempts} 次）",
            )
            revision++
        }
        retryStatusMessageId = ""
        retryStatusAttempts = 0
    }

    /** Keep the same card at the end of the current user turn when a goal starts another try. */
    fun prepareForRetry(preserveRetryStatus: Boolean) {
        val retryCard = if (preserveRetryStatus) messages.firstOrNull { it.id == retryStatusMessageId } else null
        if (retryCard != null) messages.removeAll { it.id == retryCard.id }
        while (messages.isNotEmpty() && messages.last().role != NativeChatRole.USER) {
            messages.removeAt(messages.lastIndex)
        }
        if (retryCard != null) messages.add(retryCard)
        if (!preserveRetryStatus) {
            retryStatusMessageId = ""
            retryStatusAttempts = 0
        }
        revision++
    }
}
