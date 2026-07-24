package com.termux.app

import android.util.Base64
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    var revision by mutableIntStateOf(0)
    // The active assistant answer is intentionally not stored in messages on every token batch.
    // Keeping the LazyColumn backing list structurally stable prevents all visible history items
    // from being reconsidered whenever the growing answer changes.
    var liveAssistantMessageId by mutableStateOf("")
        private set
    private val liveAssistantMarkdown = NativeStreamingMarkdownAccumulator()
    /** Full text is materialized only for completion/actions/tests, never by the live Composable. */
    val liveAssistantText: String
        get() {
            liveAssistantSnapshot
            return liveAssistantMarkdown.materialize()
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
    var currentThreadId by mutableStateOf("")
    var turnStartedAt by mutableStateOf(0L)
    var turnMessageStartIndex by mutableIntStateOf(0)
    var phaseStartedAt by mutableStateOf(0L)
    var phaseMessageStartIndex by mutableIntStateOf(0)
    private var retryStatusMessageId = ""
    private var retryStatusAttempts = 0

    fun resetConversation() {
        historyLoading = false
        messages.clear()
        planJson = "[]"
        planExplanation = ""
        planPanelAdded = false
        activeProposedPlanItemId = ""
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
        commandText = ""
        liveCommandJson = ""
        commandOutputBuffer.setLength(0)
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
        phase = NativeTurnPhase.WAITING
        processingLabel = "处理中"
        clearReasoning()
        reasoningComplete = false
        reasoningCompletedAt = 0L
        commandText = ""
        liveCommandJson = ""
        commandOutputBuffer.setLength(0)
        pendingTurnUsage = null
        clearLiveAssistantBuffer()
        toolDetails.clear()
        liveSubagents.clear()
        turnStartedAt = System.currentTimeMillis()
        phaseStartedAt = turnStartedAt
        phaseMessageStartIndex = turnMessageStartIndex
        revision++
        return message
    }

    private fun processPayload(): String {
        val tools = JSONArray()
        toolDetails.forEach { raw -> tools.put(runCatching { JSONObject(raw) }.getOrElse { raw }) }
        if (liveCommandJson.isNotBlank()) {
            runCatching { JSONObject(liveCommandJson) }.getOrNull()?.let { item ->
                item.put("type", "commandExecution")
                item.put("status", item.optString("status", "inProgress"))
                val compact = NativeCommandOutputStore.compactCommandItem(item, commandOutputBuffer.toString())
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

    private fun sealCurrentPhase() {
        if (reasoningText.isBlank() && commandText.isBlank() && liveCommandJson.isBlank() && toolDetails.isEmpty() && liveSubagents.isEmpty()) return
        val process = "PROCESS2|" + Base64.encodeToString(processPayload().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val insertAt = (phaseMessageStartIndex until messages.size)
            .firstOrNull { messages[it].role == NativeChatRole.ASSISTANT || messages[it].content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) }
            ?: messages.size
        messages.add(insertAt, NativeChatMessage(role = NativeChatRole.ACTIVITY, content = process, revealStartedAt = System.currentTimeMillis()))
    }

    fun beginReasoningAfterAnswerIfNeeded() {
        if (!reasoningComplete) return
        sealCurrentPhase()
        val lastAssistant = messages.indexOfLast { it.role == NativeChatRole.ASSISTANT && it.streaming }
        if (lastAssistant >= 0) sealLiveAssistant(lastAssistant)
        clearReasoning()
        reasoningComplete = false
        reasoningCompletedAt = 0L
        commandText = ""
        liveCommandJson = ""
        commandOutputBuffer.setLength(0)
        toolDetails.clear()
        liveSubagents.clear()
        processingLabel = "\u6b63\u5728\u601d\u8003"
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

    private fun proposedPlanMessageId(itemId: String): String = "proposed-plan:${itemId.ifBlank { "current" }}"

    private fun upsertProposedPlan(itemId: String, text: String, append: Boolean, streaming: Boolean) {
        val resolvedItemId = itemId.ifBlank { activeProposedPlanItemId.ifBlank { "current" } }
        activeProposedPlanItemId = resolvedItemId
        val messageId = proposedPlanMessageId(resolvedItemId)
        val existingIndex = messages.indexOfFirst { it.id == messageId && it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) }
        val previous = if (existingIndex >= 0) decodeNativeProposedPlan(messages[existingIndex].content) else ""
        val next = if (append) previous + text else text.ifEmpty { previous }
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
        upsertProposedPlan(item.optString("id", payload.optString("itemId")), item.optString("text"), false, true)
    }

    fun appendProposedPlanDelta(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        upsertProposedPlan(payload.optString("itemId", payload.optString("item_id")), payload.optString("delta"), true, true)
    }

    fun completeProposedPlan(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val item = payload.optJSONObject("item") ?: payload
        upsertProposedPlan(item.optString("id", payload.optString("itemId")), item.optString("text"), false, false)
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

    fun appendAssistant(delta: String) {
        if (delta.isEmpty()) return
        if (!planPanelAdded && delta.contains("<propose_plan", ignoreCase = true)) {
            messages.add(NativeChatMessage(role = NativeChatRole.ACTIVITY, content = "PLAN_PANEL|"))
            planPanelAdded = true
        }
        val cleanedDelta = cleanProtocolMarkup(delta)
        if (cleanedDelta.isEmpty()) return
        phase = NativeTurnPhase.ANSWERING
        val last = messages.lastOrNull()
        if (last == null || last.role != NativeChatRole.ASSISTANT || !last.streaming || last.id != liveAssistantMessageId) {
            clearLiveAssistantBuffer()
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
        liveAssistantSnapshot = liveAssistantMarkdown.append(cleanedDelta)
    }

    fun completeAssistantItem(text: String) {
        val cleanedText = cleanProtocolMarkup(text)
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
        revision++
    }

    fun appendAssistantFinal(text: String) {
        val cleanedText = cleanProtocolMarkup(text)
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
        phase = NativeTurnPhase.COMPLETED
        clearLiveAssistantBuffer()
        revision++
    }

    fun replaceHistory(value: String) = applyHistorySnapshot(NativeHistoryParser.parse(value))

    fun applyHistorySnapshot(snapshot: NativeHistorySnapshot) {
        clearLiveAssistantBuffer()
        pendingTurnUsage = null
        val cachedPlanJson = planJson
        val cachedPlanExplanation = planExplanation
        planJson = snapshot.planJson
        planExplanation = snapshot.planExplanation
        planPanelAdded = false
        activeProposedPlanItemId = ""
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
        revision++
    }

    fun startCommand(raw: String) {
        liveCommandJson = raw
        commandOutputBuffer.setLength(0)
        commandText = ""
        phase = NativeTurnPhase.TOOL_RUNNING
        processingLabel = "\u6b63\u5728\u8fd0\u884c\u547d\u4ee4"
        revision++
    }

    fun appendCommandOutput(delta: String) {
        if (delta.isEmpty()) return
        commandOutputBuffer.append(delta)
        commandText = NativeCommandOutputStore.livePreview(commandOutputBuffer)
        // commandText is independently observable; bumping the global revision here made the
        // work panel recompute its message scans on every terminal chunk.
    }

    fun commandOutputLength(): Int = commandOutputBuffer.length

    fun completeCommand(raw: String) {
        val item = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
        val started = runCatching { JSONObject(liveCommandJson) }.getOrNull()
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
        val fallback = if (needsFallback) commandOutputBuffer.toString() else ""
        toolDetails.add(NativeCommandOutputStore.compactCommandItem(item, fallback))
        liveCommandJson = ""
        commandOutputBuffer.setLength(0)
        commandText = ""
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
        revision++
        return thread
    }

    fun completeTurn() {
        finishReasoning()
        finishProposedPlanIfStreaming()
        if (liveCommandJson.isNotBlank()) {
            val unfinished = runCatching { JSONObject(liveCommandJson) }.getOrNull() ?: JSONObject()
            unfinished.put("status", "completed")
            completeCommand(unfinished.toString())
        }
        sealCurrentPhase()
        clearReasoning()
        commandText = ""
        liveCommandJson = ""
        commandOutputBuffer.setLength(0)
        toolDetails.clear()
        liveSubagents.clear()
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
