package com.termux.app

import android.content.Intent
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.util.UUID
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class NativeChatRole {
    USER,
    ASSISTANT,
    ACTIVITY,
    ERROR,
}

internal enum class NativeTurnPhase(val active: Boolean) {
    IDLE(false), WAITING(true), REASONING(true), TOOL_RUNNING(true), ANSWERING(true), STOPPING(true), COMPLETED(false), FAILED(false)
}

internal const val NATIVE_PROPOSED_PLAN_PREFIX = "PROPOSED_PLAN|"
private const val NATIVE_SHOW_RESPONSE_STATS_PREFERENCE = "native_show_response_stats_v1"
internal fun encodeNativeProposedPlan(text: String): String = NATIVE_PROPOSED_PLAN_PREFIX + text
internal fun decodeNativeProposedPlan(content: String): String = content.substringAfter('|')

@Immutable
data class NativeAttachment(val name: String, val path: String, val image: Boolean)

@Immutable
data class NativeSkill(val name: String, val description: String, val path: String)

@Immutable
internal data class NativeModelOption(val id: String, val name: String, val efforts: List<String>, val defaultEffort: String)

@Immutable
internal data class NativeConversation(val threadId: String, val title: String, val state: String, val projectPath: String, val favorite: Boolean) { val projectName: String get() = projectPath.trimEnd('/').substringAfterLast('/').ifBlank { "无项目" } }

@Immutable
data class NativeChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: NativeChatRole,
    val content: String,
    val streaming: Boolean = false,
    val revealStartedAt: Long = 0L,
    val finalOnlyReveal: Boolean = false,
    val usage: NativeTurnUsage? = null,
    val skills: List<NativeSkill> = emptyList(),
    val attachments: List<NativeAttachment> = emptyList(),
)

@Stable
internal class NativeChatState {
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
    var reasoningText by mutableStateOf("")
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

    fun resetConversation() {
        historyLoading = false
        messages.clear()
        planJson = "[]"
        planExplanation = ""
        planPanelAdded = false
        activeProposedPlanItemId = ""
        activeGoalObjective = ""
        activeGoalStatus = "active"
        pendingUserInputRequest = ""
        pendingApprovalRequest = ""
        phase = NativeTurnPhase.IDLE
        processingLabel = ""
        reasoningText = ""
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
        turnStartedAt = 0L
        turnMessageStartIndex = 0
        phaseStartedAt = 0L
        phaseMessageStartIndex = 0
        revision++
    }

    fun addUser(text: String, skills: List<NativeSkill> = emptyList(), attachments: List<NativeAttachment> = emptyList()) {
        messages.add(NativeChatMessage(role = NativeChatRole.USER, content = text, skills = skills.toList(), attachments = attachments.toList()))
        turnMessageStartIndex = messages.size
        phase = NativeTurnPhase.WAITING
        processingLabel = "处理中"
        reasoningText = ""
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
        reasoningText = ""
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

    private fun cleanProtocolMarkup(text: String): String = text
        .replace(Regex("""</?propose(?:d)?_plan(?:\s[^>]*)?>""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""</?plan\s*>""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""</?final\s*>""", RegexOption.IGNORE_CASE), "")

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
        revision++
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
        val thread = subagentAliases(merged).firstOrNull { it.matches(Regex("[0-9a-fA-F-]{32,}")) }
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
        reasoningText = ""
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
}

class CodexChatActivity : ComponentActivity(), CodexAppServerBridge.EventListener {
    companion object {
        private val subagentRouteCounter = java.util.concurrent.atomic.AtomicInteger(0)
        private const val STREAM_CATCH_UP_CHUNK_CHARS = 1_200
        private const val STREAM_CATCH_UP_DELAY_MS = 24L
    }

    private val chatState = NativeChatState()
    private var bridge: CodexAppServerBridge? = null
    private var activeProfileId: String = ""
    private var appliedProviderFingerprint: String? = null
    private var appliedProviderDefaultModel: String = ""
    private var backendConfigurationLoaded = false
    private var pendingBackendConfigurationReload = false
    private var pendingCachedHistoryThreadId: String? = null
    private var pendingCachedHistorySnapshot: NativeHistorySnapshot? = null
    private var conversationRefreshGeneration = 0
    private var subagentRouteGeneration = subagentRouteCounter.incrementAndGet()
    private val subagentHistoryAttempts = HashMap<String, Int>()
    private val conversationProjectCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val conversationTitleCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val taskPreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "overlay_tasks_v1") {
            android.util.Log.d("IlyopCodexTasks", "preference changed")
            refreshConversations()
        }
    }
    private val streamHandler = Handler(Looper.getMainLooper())
    private val pendingReasoning = StringBuilder()
    private val pendingAnswer = StringBuilder()
    private val pendingPlan = StringBuilder()
    private val pendingCommand = StringBuilder()
    private var pendingPlanItemId = ""
    private var reasoningFlushScheduled = false
    private var answerFlushScheduled = false
    private var planFlushScheduled = false
    private var commandFlushScheduled = false
    private var reasoningPendingSince = 0L
    private var answerPendingSince = 0L
    /**
     * Direct-manipulation and navigation motion owns the UI thread. Stream deltas keep buffering
     * while those animations run, but no growing text snapshot is published into Compose until
     * the motion settles. This prevents answer measurement from stealing drawer frames.
     */
    private var uiMotionActive = false
    private var uiMotionCatchUpGeneration = 0
    private val flushReasoningRunnable = Runnable {
        reasoningFlushScheduled = false
        flushReasoningDeltas()
    }
    private val flushAnswerRunnable = Runnable {
        answerFlushScheduled = false
        flushAnswerDeltas()
    }
    private val flushPlanRunnable = Runnable {
        planFlushScheduled = false
        flushPlanDeltas()
    }
    private val flushCommandRunnable = Runnable {
        commandFlushScheduled = false
        flushCommandDeltas()
    }
    private var lastFrameNanos = 0L
    private var frameWindowStartedAt = 0L
    private var frameCount = 0
    private var slowFrameCount = 0
    private var worstFrameMs = 0L
    private val frameDiagnostics = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (chatState.busy && lastFrameNanos > 0L) {
                val frameMs = ((frameTimeNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000L)
                frameCount++
                if (frameMs > 24L) slowFrameCount++
                if (frameMs > worstFrameMs) worstFrameMs = frameMs
                val now = android.os.SystemClock.uptimeMillis()
                if (frameWindowStartedAt == 0L) frameWindowStartedAt = now
                if (now - frameWindowStartedAt >= 2_000L) {
                    NativeChatDiagnostics.record(this@CodexChatActivity, "frame_window", JSONObject()
                        .put("frames", frameCount).put("slowFrames", slowFrameCount)
                        .put("worstMs", worstFrameMs).put("reasoningChars", chatState.reasoningText.length)
                        .put("messages", chatState.messages.size))
                    frameWindowStartedAt = now
                    frameCount = 0
                    slowFrameCount = 0
                    worstFrameMs = 0L
                }
            }
            lastFrameNanos = frameTimeNanos
            if (chatState.busy) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun startFrameDiagnostics() {
        lastFrameNanos = 0L
        frameWindowStartedAt = android.os.SystemClock.uptimeMillis()
        frameCount = 0
        slowFrameCount = 0
        worstFrameMs = 0L
        Choreographer.getInstance().removeFrameCallback(frameDiagnostics)
        Choreographer.getInstance().postFrameCallback(frameDiagnostics)
    }

    private fun stopFrameDiagnostics() {
        Choreographer.getInstance().removeFrameCallback(frameDiagnostics)
        lastFrameNanos = 0L
    }
    private var currentThreadId: String? = null
    private var expectedHistoryGeneration = -1
    private var displayedHistorySnapshot: NativeHistorySnapshot? = null
    private var nativeThemeMode by mutableStateOf("system")
    private var nativeColorPalette by mutableStateOf(FcodeColorPalette.ROSE.value)
    private var nativeChatBackground by mutableStateOf(FcodeChatBackgroundStyle.THEME.value)
    private var nativeChatBackgroundImage by mutableStateOf("")
    private var nativeChatBackgroundDim by mutableStateOf(0.32f)
    private var nativeLanguage by mutableStateOf("zh")
    private var streamAnimationsEnabled by mutableStateOf(true)
    private var fixedStreamingViewportEnabled by mutableStateOf(true)
    private var showReasoning by mutableStateOf(true)
    private var autoFollowOutput by mutableStateOf(true)
    private var showResponseStats by mutableStateOf(true)
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { cacheAttachment(it, true) }
    }
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { cacheAttachment(it, false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeChatDiagnostics.record(this, "activity_create", JSONObject()
            .put("diagnostics", NativeChatDiagnostics.file(this).absolutePath))
        val nativePrefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        chatState.input = nativePrefs.getString("native_chat_draft_v1", "").orEmpty()
        chatState.selectedMode = nativePrefs.getString("native_chat_mode_v1", "default").orEmpty().takeIf { it == "plan" } ?: "default"
        chatState.permissionMode = NativePermissionMode.normalize(nativePrefs.getString(NativePermissionMode.PREFERENCE_KEY, NativePermissionMode.FULL_ACCESS))
        nativeThemeMode = FcodeAppearancePreferences.normalizeColorMode(nativePrefs.getString(FcodeAppearancePreferences.COLOR_MODE, "system"))
        nativeColorPalette = FcodeColorPalette.from(nativePrefs.getString(FcodeAppearancePreferences.COLOR_PALETTE, FcodeColorPalette.ROSE.value)).value
        nativeChatBackground = FcodeChatBackgroundStyle.from(nativePrefs.getString(FcodeAppearancePreferences.CHAT_BACKGROUND, FcodeChatBackgroundStyle.THEME.value)).value
        nativeChatBackgroundImage = nativePrefs.getString(FcodeAppearancePreferences.CHAT_BACKGROUND_IMAGE, "").orEmpty()
        nativeChatBackgroundDim = nativePrefs.getFloat(FcodeAppearancePreferences.CHAT_BACKGROUND_DIM, 0.32f).coerceIn(0f, 0.72f)
        nativeLanguage = nativePrefs.getString("native_language_v1", "system").orEmpty().let { if (it == "en") "en" else if (it == "zh") "zh" else if (Locale.getDefault().language == "en") "en" else "zh" }
        streamAnimationsEnabled = nativePrefs.getBoolean("native_stream_animations_v1", true)
        fixedStreamingViewportEnabled = nativePrefs.getBoolean("native_stream_fixed_viewport_v1", true)
        showReasoning = nativePrefs.getBoolean("native_show_reasoning_v1", true)
        autoFollowOutput = nativePrefs.getBoolean("native_auto_follow_v1", true)
        showResponseStats = nativePrefs.getBoolean(NATIVE_SHOW_RESPONSE_STATS_PREFERENCE, true)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )

        setContent {
            FcodeChatTheme(
                nativeThemeMode,
                nativeLanguage,
                streamAnimationsEnabled,
                showReasoning,
                autoFollowOutput,
                colorPalette = nativeColorPalette,
                chatBackground = nativeChatBackground,
                chatBackgroundImage = nativeChatBackgroundImage,
                chatBackgroundDim = nativeChatBackgroundDim,
                fixedStreamingViewport = fixedStreamingViewportEnabled,
                showResponseStats = showResponseStats,
            ) {
                NativeChatScreen(
                    state = chatState,
                    onSend = ::sendMessage,
                    // TextFieldState owns live IME edits. Persist drafts without writing
                    // Compose screen state on every key press.
                    onInputChange = ::persistDraft,
                    onRetry = ::retryMessage,
                    onEditMessage = ::editMessage,
                    onStop = ::stopCurrentTurn,
                    onNewConversation = ::newConversation,
                    onResumeConversation = ::resumeConversation,
                    onUiMotionChanged = ::setUiMotionActive,
                    onLoadSubagentHistory = ::loadSubagentHistory,
                    onModelSelected = ::selectNativeModel,
                    onEffortSelected = ::selectNativeEffort,
                    onModeChange = ::setChatMode,
                    onPermissionModeChange = ::setPermissionMode,
                    onSetGoal = ::setGoal,
                    onClearGoal = ::clearGoal,
                    onToggleGoalPause = ::toggleGoalPause,
                    onCompact = { bridge?.compactThread() },
                    onAnswerUserInput = ::answerUserInput,
                    onAnswerApproval = ::answerApproval,
                    onPickImages = { imagePicker.launch("image/*") },
                    onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                    onRemoveAttachment = { chatState.attachments.remove(it) },
                    onRenameConversation = ::renameConversation,
                    onDeleteConversation = ::deleteConversation,
                    onToggleFavorite = ::toggleFavorite,
                    onBackHome = ::openHomeSettings,
                    onOpenLegacyWebUi = ::openLegacyWebUi,
                    onToggleTheme = {
                        nativeThemeMode = when (nativeThemeMode) { "system" -> "light"; "light" -> "dark"; else -> "system" }
                        nativePrefs.edit().putString("native_theme_mode_v1", nativeThemeMode).apply()
                    },
                )
            }
        }

        getSharedPreferences("codex_mobile", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(taskPreferenceListener)
        startRuntimeAfterFirstDraw()
    }

    private fun startRuntimeAfterFirstDraw() {
        val decor = window.decorView
        var scheduled = false
        val listener = object : android.view.ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (scheduled) return
                scheduled = true
                decor.post {
                    if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnDrawListener(this)
                    if (isFinishing || isDestroyed) return@post
                    refreshConversations()
                    startBackend()
                }
            }
        }
        decor.viewTreeObserver.addOnDrawListener(listener)
    }

    private fun startBackend(preferConfiguredDefault: Boolean = false) {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val profile = CodexProviderStore(prefs).active()
        val routeThroughMihomo = prefs.getBoolean("mihomo_route_api", false) ||
            (profile?.let { it.proxyEnabled && it.proxyWebUi } == true)
        val providerFingerprint = NativeProviderSync.configurationFingerprint(profile, routeThroughMihomo) +
            "|mcp=${prefs.getLong(NativeMcpConfigStore.REVISION_KEY, 0L)}:${NativeMcpConfigStore.fileFingerprint()}"

        backendConfigurationLoaded = true
        pendingBackendConfigurationReload = false
        appliedProviderFingerprint = providerFingerprint
        appliedProviderDefaultModel = profile?.model.orEmpty()
        activeProfileId = profile?.id.orEmpty()

        chatState.modelOptions.clear()
        if (profile != null) chatState.modelOptions.addAll(NativeProviderSync.modelOptions(profile))
        if (profile != null) {
            restoreNativeSelection(prefs, profile.id, profile.model, preferConfiguredDefault)
        } else {
            chatState.selectedModel = ""
            chatState.modelLabel = "默认模型"
            chatState.selectedEffort = "high"
        }

        if (profile == null || profile.baseUrl.isBlank() || profile.apiKey.isBlank()) {
            if (CodexNativeRuntime.exists()) CodexNativeRuntime.shutdown()
            bridge = null
            chatState.ready = false
            chatState.addError("没有可用的 API 配置，请先返回首页创建并启用配置。")
            return
        }

        val binary = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex")
        if (!binary.canExecute()) {
            if (CodexNativeRuntime.exists()) CodexNativeRuntime.shutdown()
            bridge = null
            chatState.ready = false
            chatState.addError("Codex CLI 尚未安装，请先返回首页完成运行环境安装。")
            return
        }

        chatState.connectionLabel = "正在连接 ${chatState.modelLabel}…"

        // Native chat owns a dedicated reasoning panel, so request summaries from
        // capable models even if the shared WebUI profile defaults them to off.
        profile.models.forEach { model ->
            if (model.reasoningSummaries && model.defaultReasoningSummary.equals("none", ignoreCase = true)) {
                model.defaultReasoningSummary = "detailed"
            }
        }
        CodexModelCatalog.writeAtomic(
            File(File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "ilyop-model-catalog.json"),
            profile.models,
        )

        val retainedRuntime = CodexNativeRuntime.exists()
        val retainedThread = CodexNativeRuntime.currentThreadId()?.takeIf { it.isNotBlank() }
            ?: currentThreadId?.takeIf { it.isNotBlank() }
        chatState.ready = false
        bridge = CodexNativeRuntime.attach(
            this,
            this,
            providerFingerprint,
            profile.baseUrl,
            profile.apiKey,
            profile.model,
            profile.apiFormat.takeUnless { it.isBlank() || it == "auto" } ?: "openai_responses",
            routeThroughMihomo,
            profile.forwardReasoningContext,
            profile.ultraSubagentLimit,
            profile.normalSubagentLimit,
            profile.ultraTransportEfforts(),
            profile.customSubagentStability && profile.hasCustomV2Models(),
        )
        if (!retainedThread.isNullOrBlank()) {
            val bridgeWasRecreated = CodexNativeRuntime.lastAttachRecreatedBridge()
            resumeConversation(
                retainedThread,
                retainedRuntime = retainedRuntime && !bridgeWasRecreated,
            )
        }
    }

    private fun reloadProviderConfigurationIfChanged() {
        if (!backendConfigurationLoaded) return
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val profile = CodexProviderStore(prefs).active()
        val routeThroughMihomo = prefs.getBoolean("mihomo_route_api", false) ||
            (profile?.let { it.proxyEnabled && it.proxyWebUi } == true)
        val nextFingerprint = NativeProviderSync.configurationFingerprint(profile, routeThroughMihomo) +
            "|mcp=${prefs.getLong(NativeMcpConfigStore.REVISION_KEY, 0L)}:${NativeMcpConfigStore.fileFingerprint()}"
        if (nextFingerprint == appliedProviderFingerprint) {
            pendingBackendConfigurationReload = false
            return
        }
        if (chatState.busy) {
            pendingBackendConfigurationReload = true
            return
        }
        val preferConfiguredDefault = NativeProviderSync.shouldPreferConfiguredDefault(
            activeProfileId,
            appliedProviderDefaultModel,
            profile?.id,
            profile?.model,
        )
        NativeChatDiagnostics.record(this, "provider_configuration_reload", JSONObject()
            .put("previousProfileId", activeProfileId)
            .put("nextProfileId", profile?.id.orEmpty())
            .put("preferConfiguredDefault", preferConfiguredDefault)
            .put("models", profile?.models?.size ?: 0))
        startBackend(preferConfiguredDefault)
    }

    private fun applyPendingProviderConfiguration() {
        if (!pendingBackendConfigurationReload) return
        streamHandler.post { reloadProviderConfigurationIfChanged() }
    }

    private fun preferencePart(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun modelPreferenceKey(profileId: String): String =
        "native_chat_model_v2_${preferencePart(profileId)}"

    private fun effortPreferenceKey(profileId: String, modelId: String): String =
        "native_chat_effort_v2_${preferencePart(profileId)}_${preferencePart(modelId.lowercase())}"

    private fun restoreNativeSelection(
        prefs: android.content.SharedPreferences,
        profileId: String,
        profileDefaultModel: String,
        preferConfiguredDefault: Boolean = false,
    ) {
        val storedModelId = prefs.getString(modelPreferenceKey(profileId), null).orEmpty()
        val selectedModelId = NativeProviderSync.resolveModelId(
            chatState.modelOptions.map { it.id },
            storedModelId,
            profileDefaultModel,
            preferConfiguredDefault,
        )
        val selectedOption = chatState.modelOptions.firstOrNull {
            it.id.equals(selectedModelId, ignoreCase = true)
        }

        if (selectedOption == null) {
            chatState.selectedModel = profileDefaultModel
            chatState.modelLabel = profileDefaultModel.ifBlank { "默认模型" }
            chatState.selectedEffort = "high"
            return
        }

        chatState.selectedModel = selectedOption.id
        chatState.modelLabel = selectedOption.name.ifBlank { selectedOption.id }
        val storedEffort = prefs.getString(effortPreferenceKey(profileId, selectedOption.id), null)
            ?.trim()
            ?.lowercase()
            .orEmpty()
        chatState.selectedEffort = storedEffort.takeIf { it in selectedOption.efforts }
            ?: selectedOption.defaultEffort

        prefs.edit()
            .putString(modelPreferenceKey(profileId), selectedOption.id)
            .putString(effortPreferenceKey(profileId, selectedOption.id), chatState.selectedEffort)
            .apply()
        NativeChatDiagnostics.record(this, "native_selection_restored", JSONObject()
            .put("profileId", profileId)
            .put("model", chatState.selectedModel)
            .put("effort", chatState.selectedEffort)
            .put("hadStoredModel", storedModelId.isNotBlank())
            .put("hadValidStoredEffort", storedEffort in selectedOption.efforts))
    }

    private fun selectNativeModel(modelId: String) {
        val option = chatState.modelOptions.firstOrNull { it.id.equals(modelId, ignoreCase = true) } ?: return
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val profileId = activeProfileId
        val storedEffort = if (profileId.isBlank()) "" else {
            prefs.getString(effortPreferenceKey(profileId, option.id), null)
                ?.trim()
                ?.lowercase()
                .orEmpty()
        }
        chatState.selectedModel = option.id
        chatState.modelLabel = option.name.ifBlank { option.id }
        chatState.selectedEffort = storedEffort.takeIf { it in option.efforts } ?: option.defaultEffort
        if (profileId.isNotBlank()) {
            prefs.edit()
                .putString(modelPreferenceKey(profileId), option.id)
                .putString(effortPreferenceKey(profileId, option.id), chatState.selectedEffort)
                .apply()
        }
        NativeChatDiagnostics.record(this, "native_model_selected", JSONObject()
            .put("profileId", profileId)
            .put("model", chatState.selectedModel)
            .put("effort", chatState.selectedEffort))
    }

    private fun selectNativeEffort(effort: String) {
        val option = chatState.modelOptions.firstOrNull {
            it.id.equals(chatState.selectedModel, ignoreCase = true)
        } ?: return
        val normalized = effort.trim().lowercase()
        if (normalized !in option.efforts) {
            NativeChatDiagnostics.record(this, "native_effort_rejected", JSONObject()
                .put("model", option.id)
                .put("effort", normalized))
            return
        }
        chatState.selectedEffort = normalized
        if (activeProfileId.isNotBlank()) {
            getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
                .putString(modelPreferenceKey(activeProfileId), option.id)
                .putString(effortPreferenceKey(activeProfileId, option.id), normalized)
                .apply()
        }
        NativeChatDiagnostics.record(this, "native_effort_selected", JSONObject()
            .put("profileId", activeProfileId)
            .put("model", option.id)
            .put("effort", normalized))
    }

    private fun editMessage(messageId: String, text: String) {
        val value = text.trim()
        if (value.isEmpty() || chatState.busy || !chatState.ready) return
        currentThreadId?.let(NativeHistorySnapshotCache::remove)
        val userIndex = chatState.messages.indexOfFirst { it.id == messageId && it.role == NativeChatRole.USER }
        if (userIndex < 0) return
        val rollbackTurns = chatState.messages.drop(userIndex).count { it.role == NativeChatRole.USER }.coerceAtLeast(1)
        chatState.messages[userIndex] = chatState.messages[userIndex].copy(content = value)
        while (chatState.messages.size > userIndex + 1) chatState.messages.removeAt(chatState.messages.lastIndex)
        chatState.messages.add(NativeChatMessage(role = NativeChatRole.ACTIVITY, content = "NOTICE|已从此处重新生成"))
        chatState.phase = NativeTurnPhase.WAITING
        startFrameDiagnostics()
        chatState.processingLabel = "处理中"
        chatState.reasoningText = ""
        chatState.reasoningComplete = false
        chatState.reasoningCompletedAt = 0L
        chatState.commandText = ""
        chatState.liveCommandJson = ""
        chatState.toolDetails.clear()
        chatState.liveSubagents.clear()
        chatState.turnStartedAt = System.currentTimeMillis()
        chatState.turnMessageStartIndex = chatState.messages.size
        chatState.phaseStartedAt = chatState.turnStartedAt
        chatState.phaseMessageStartIndex = chatState.turnMessageStartIndex
        bridge?.editTurn(value, chatState.selectedModel, chatState.selectedEffort, rollbackTurns)
    }

    private fun retryMessage(text: String) {
        val value = text.trim()
        if (value.isEmpty() || chatState.busy || !chatState.ready) return
        currentThreadId?.let(NativeHistorySnapshotCache::remove)
        while (chatState.messages.isNotEmpty() && chatState.messages.last().role != NativeChatRole.USER) {
            chatState.messages.removeAt(chatState.messages.lastIndex)
        }
        chatState.phase = NativeTurnPhase.WAITING
        startFrameDiagnostics()
        chatState.processingLabel = "处理中"
        chatState.reasoningText = ""
        chatState.reasoningComplete = false
        chatState.reasoningCompletedAt = 0L
        chatState.commandText = ""
        chatState.liveCommandJson = ""
        chatState.toolDetails.clear()
        chatState.liveSubagents.clear()
        chatState.turnStartedAt = System.currentTimeMillis()
        chatState.turnMessageStartIndex = chatState.messages.size
        chatState.phaseStartedAt = chatState.turnStartedAt
        chatState.phaseMessageStartIndex = chatState.turnMessageStartIndex
        bridge?.sendMessage(value, chatState.selectedModel, chatState.selectedEffort, "[]", chatState.selectedMode)
    }

    private fun persistDraft(value: String) {
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString("native_chat_draft_v1", value).apply()
    }

    private fun updateDraft(value: String) {
        chatState.input = value
        persistDraft(value)
    }

    private fun sendMessage(text: String) {
        val value = text.trim()
        if ((value.isEmpty() && chatState.attachments.isEmpty()) || chatState.busy || !chatState.ready) return
        currentThreadId?.let(NativeHistorySnapshotCache::remove)
        updateDraft("")
        if (chatState.conversationTitle == "新对话" && value.isNotBlank()) {
            chatState.conversationTitle = value.lineSequence().firstOrNull().orEmpty().trim().let { if (it.length > 28) it.take(27) + "…" else it }.ifBlank { "新对话" }
        }
        val referencedSkills = chatState.selectedSkills.toList()
        val referencedAttachments = chatState.attachments.toList()
        chatState.addUser(value, referencedSkills, referencedAttachments)
        startFrameDiagnostics()
        val attachments = JSONArray().also { array ->
            chatState.attachments.forEach { attachment ->
                array.put(JSONObject().put("name", attachment.name).put("path", attachment.path).put("image", attachment.image))
            }
        }
        val skills = JSONArray().also { array ->
            chatState.selectedSkills.forEach { skill -> array.put(JSONObject().put("name", skill.name).put("path", skill.path)) }
        }
        bridge?.sendMessage(value, chatState.selectedModel, chatState.selectedEffort, attachments.toString(), chatState.selectedMode, skills.toString())
        chatState.attachments.clear()
        chatState.selectedSkills.clear()
    }

    private fun setChatMode(mode: String) {
        chatState.selectedMode = if (mode == "plan") "plan" else "default"
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString("native_chat_mode_v1", chatState.selectedMode)
            .apply { currentThreadId?.let { putString(modePreferenceKey(it), chatState.selectedMode) } }
            .apply()
    }

    private fun goalPreferenceKey(threadId: String): String = "native_thread_goal_v1_$threadId"
    private fun setPermissionMode(mode: String) {
        val normalized = NativePermissionMode.normalize(mode)
        chatState.permissionMode = normalized
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(NativePermissionMode.PREFERENCE_KEY, normalized)
            .apply()
    }

    private fun modePreferenceKey(threadId: String): String = "native_thread_mode_v1_$threadId"
    private fun goalStatusPreferenceKey(threadId: String): String = "native_thread_goal_status_v1_$threadId"
    private fun planPreferenceKey(threadId: String): String = "native_thread_plan_v1_$threadId"
    private fun planExplanationPreferenceKey(threadId: String): String = "native_thread_plan_explanation_v1_$threadId"

    private fun restoreGoalForThread(threadId: String) {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        chatState.activeGoalObjective = prefs.getString(goalPreferenceKey(threadId), "").orEmpty()
        chatState.activeGoalStatus = prefs.getString(goalStatusPreferenceKey(threadId), "active").orEmpty().takeIf { it == "paused" } ?: "active"
        chatState.selectedMode = prefs.getString(modePreferenceKey(threadId), "default").orEmpty().takeIf { it == "plan" } ?: "default"
        chatState.planJson = prefs.getString(planPreferenceKey(threadId), "[]").orEmpty().ifBlank { "[]" }
        chatState.planExplanation = prefs.getString(planExplanationPreferenceKey(threadId), "").orEmpty()
    }

    private fun setGoal(objective: String) {
        val value = objective.trim()
        val threadId = currentThreadId
        if (value.isEmpty() || !chatState.ready || threadId.isNullOrBlank()) return
        chatState.activeGoalObjective = value
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(goalPreferenceKey(threadId), value).putString(goalStatusPreferenceKey(threadId), "active").apply()
        chatState.activeGoalStatus = "active"
        bridge?.setThreadGoal(value)
    }

    private fun answerUserInput(requestId: Int, questionId: String, answer: String) {
        val answers = JSONObject().put(questionId, JSONObject().put("answers", JSONArray().put(answer)))
        bridge?.respondUserInput(requestId, answers.toString())
        chatState.pendingUserInputRequest = ""
    }

    private fun answerApproval(rawRequest: String, decision: String) {
        if (rawRequest.isBlank()) return
        bridge?.respondApprovalRequest(rawRequest, decision)
        if (chatState.pendingApprovalRequest == rawRequest) chatState.pendingApprovalRequest = ""
        if (chatState.phase == NativeTurnPhase.WAITING) {
            chatState.phase = NativeTurnPhase.TOOL_RUNNING
            chatState.processingLabel = nativeText(nativeLanguage, "\u6b63\u5728\u7ee7\u7eed\u6267\u884c", "Continuing")
        }
    }

    private fun cancelPendingApproval() {
        val raw = chatState.pendingApprovalRequest
        if (raw.isNotBlank()) bridge?.respondApprovalRequest(raw, "cancel")
        chatState.pendingApprovalRequest = ""
    }

    private fun toggleGoalPause() {
        val threadId = currentThreadId ?: return
        if (chatState.activeGoalObjective.isBlank() || chatState.phase.active) return
        val next = if (chatState.activeGoalStatus == "paused") "active" else "paused"
        chatState.activeGoalStatus = next
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString(goalStatusPreferenceKey(threadId), next).apply()
        bridge?.setThreadGoalStatus(next)
    }

    private fun clearGoal() {
        currentThreadId?.let { threadId ->
            getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
                .remove(goalPreferenceKey(threadId)).remove(goalStatusPreferenceKey(threadId)).apply()
        }
        chatState.activeGoalObjective = ""
        bridge?.clearThreadGoal()
    }

    private fun cacheAttachment(uri: Uri, image: Boolean) {
        try {
            var name = "attachment-${System.currentTimeMillis()}"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
            }
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val directory = File(cacheDir, "native-chat-attachments").apply { mkdirs() }
            val target = File(directory, "${System.currentTimeMillis()}-$safeName")
            contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            chatState.attachments.add(NativeAttachment(name, target.absolutePath, image))
        } catch (error: Exception) {
            Toast.makeText(this, "附件读取失败：${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopCurrentTurn() {
        if (!chatState.busy) return
        chatState.connectionLabel = "正在停止…"
        bridge?.interruptCurrentTurn()
    }

    private fun refreshConversations() {
        val generation = ++conversationRefreshGeneration
        android.util.Log.d("IlyopCodexTasks", "refresh generation=$generation")
        val favorites = favoriteThreadIds()
        val snapshot = CodexTaskStore.current(this)

        // Task status and stored titles are cheap SharedPreferences data. Publish them
        // immediately so a newly running/completed task appears without waiting for a
        // recursive session-file scan.
        val immediate = snapshot.map { task ->
            NativeConversation(
                task.threadId,
                conversationTitleCache[task.threadId] ?: task.title,
                task.state,
                conversationProjectCache[task.threadId].orEmpty(),
                task.threadId in favorites,
            )
        }.sortedByDescending { it.state == CodexTaskStore.RUNNING }
        applyConversationSnapshot(generation, immediate)

        Thread {
            val missingProjectIds = snapshot.asSequence().map { it.threadId }.filter { !conversationProjectCache.containsKey(it) }.toList()
            if (missingProjectIds.isNotEmpty()) {
                CodexAppServerBridge.resolveConversationProjects(missingProjectIds).forEach { (threadId, project) ->
                    if (project.isNotBlank()) conversationProjectCache[threadId] = project
                }
            }
            val enriched = snapshot.mapNotNull { task ->
                var title = conversationTitleCache[task.threadId] ?: task.title
                val fallbackTitle = title.startsWith("Codex 任务")
                if (fallbackTitle) {
                    val resolvedTitle = CodexAppServerBridge.resolveConversationTitle(task.threadId)
                    if (resolvedTitle.isNotBlank()) {
                        title = resolvedTitle
                        conversationTitleCache[task.threadId] = resolvedTitle
                    }
                }
                val project = conversationProjectCache[task.threadId].orEmpty()
                if (fallbackTitle && title.startsWith("Codex 任务")) null
                else NativeConversation(task.threadId, title, task.state, project, task.threadId in favorites)
            }.sortedByDescending { it.state == CodexTaskStore.RUNNING }
            applyConversationSnapshot(generation, enriched)
        }.apply { name = "CodexConversationMetadata" }.start()
    }

    private fun applyConversationSnapshot(generation: Int, conversations: List<NativeConversation>) {
        runOnUiThread {
            if (generation != conversationRefreshGeneration || isFinishing || isDestroyed) return@runOnUiThread
            android.util.Log.d("IlyopCodexTasks", "apply generation=$generation count=${conversations.size} first=${conversations.firstOrNull()?.title}")
            chatState.conversations.clear()
            chatState.conversations.addAll(conversations)
        }
    }

    private fun favoriteThreadIds(): Set<String> {
        val raw = getSharedPreferences("codex_mobile", MODE_PRIVATE).getString("native_favorite_threads_v1", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildSet { for (index in 0 until array.length()) add(array.optString(index)) }
    }

    private fun toggleFavorite(conversation: NativeConversation) {
        val favorites = favoriteThreadIds().toMutableSet()
        if (!favorites.add(conversation.threadId)) favorites.remove(conversation.threadId)
        val array = JSONArray().also { value -> favorites.forEach { value.put(it) } }
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString("native_favorite_threads_v1", array.toString()).apply()
        refreshConversations()
    }

    private fun renameConversation(conversation: NativeConversation, title: String) {
        CodexTaskStore.updateTitle(this, conversation.threadId, title)
        refreshConversations()
    }

    private fun deleteConversation(conversation: NativeConversation) {
        NativeHistorySnapshotCache.remove(conversation.threadId)
        CodexTaskStore.delete(this, conversation.threadId)
        refreshConversations()
    }

    private fun subagentMessageCount(threadId: String): Int =
        chatState.subagentHistoryMessageCounts[threadId] ?: 0

    private fun loadSubagentHistory(threadId: String) {
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

    private fun handleSubagentHistory(value: String) {
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

    private fun setUiMotionActive(active: Boolean) {
        if (uiMotionActive == active) return
        uiMotionActive = active
        val generation = ++uiMotionCatchUpGeneration
        NativeChatDiagnostics.record(this, "ui_motion_priority", JSONObject()
            .put("active", active)
            .put("pendingReasoning", pendingReasoning.length)
            .put("pendingAnswer", pendingAnswer.length))
        if (active) return

        // Wait until the first frame after the gesture/navigation animation. Publishing in the
        // same frame as drawer settlement can still turn the final animation frame into a hitch.
        Choreographer.getInstance().postFrameCallback {
            if (uiMotionActive || generation != uiMotionCatchUpGeneration) return@postFrameCallback
            streamHandler.post {
                if (uiMotionActive || generation != uiMotionCatchUpGeneration) return@post
                flushReasoningDeltas()
                flushAnswerDeltas()
            }
        }
    }

    private fun discardPendingStreamEvents(reason: String) {
        streamHandler.removeCallbacks(flushReasoningRunnable)
        streamHandler.removeCallbacks(flushAnswerRunnable)
        streamHandler.removeCallbacks(flushPlanRunnable)
        streamHandler.removeCallbacks(flushCommandRunnable)
        reasoningFlushScheduled = false
        answerFlushScheduled = false
        planFlushScheduled = false
        commandFlushScheduled = false
        val droppedReasoning = pendingReasoning.length
        val droppedAnswer = pendingAnswer.length
        val droppedPlan = pendingPlan.length
        val droppedCommand = pendingCommand.length
        pendingReasoning.setLength(0)
        pendingAnswer.setLength(0)
        pendingPlan.setLength(0)
        pendingCommand.setLength(0)
        pendingPlanItemId = ""
        // Invalidate a catch-up callback posted by the route we just left.
        uiMotionCatchUpGeneration++
        stopFrameDiagnostics()
        NativeChatDiagnostics.record(this, "stream_route_reset", JSONObject()
            .put("reason", reason).put("droppedReasoning", droppedReasoning).put("droppedAnswer", droppedAnswer)
            .put("droppedPlan", droppedPlan).put("droppedCommand", droppedCommand))
    }

    private fun resumeConversation(threadId: String, retainedRuntime: Boolean = false) {
        cancelPendingApproval()
        discardPendingStreamEvents("resume")
        subagentRouteGeneration = subagentRouteCounter.incrementAndGet()
        subagentHistoryAttempts.clear()
        currentThreadId = threadId
        val selectedConversation = chatState.conversations.firstOrNull { it.threadId == threadId }
        val cachedHistory = NativeHistorySnapshotCache.get(threadId)
        pendingCachedHistoryThreadId = threadId.takeIf { cachedHistory != null }
        pendingCachedHistorySnapshot = cachedHistory
        displayedHistorySnapshot = null
        chatState.resetConversation()
        chatState.currentThreadId = threadId
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
        }
        chatState.ready = false
        chatState.connectionLabel = "\u6b63\u5728\u6062\u590d\u5bf9\u8bdd\u2026"
        expectedHistoryGeneration = if (retainedRuntime) {
            bridge?.restoreRetainedConversation(threadId) ?: -1
        } else {
            bridge?.resumeConversation(threadId) ?: -1
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

    private fun newConversation() {
        cancelPendingApproval()
        discardPendingStreamEvents("new")
        chatState.selectedMode = "default"
        subagentRouteGeneration = subagentRouteCounter.incrementAndGet()
        subagentHistoryAttempts.clear()
        pendingCachedHistoryThreadId = null
        pendingCachedHistorySnapshot = null
        currentThreadId = null
        expectedHistoryGeneration = -1
        displayedHistorySnapshot = null
        chatState.currentThreadId = ""
        chatState.conversationAnimationKey = "new-${UUID.randomUUID()}"
        chatState.resetConversation()
        chatState.conversationTitle = "新对话"
        chatState.ready = false
        chatState.connectionLabel = "正在创建新对话…"
        bridge?.newConversation()
    }

    private fun openHomeSettings() {
        startActivity(Intent(this, NativeSettingsActivity::class.java))
    }

    private fun openLegacyWebUi() {
        CodexNativeRuntime.shutdown()
        startActivity(
            Intent(this, CodexHomeActivity::class.java)
                .setAction(CodexHomeActivity.ACTION_OPEN_WEBUI)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    /**
     * A drawer can stay open long enough to accumulate thousands of characters. Once direct
     * manipulation ends, replay that backlog in bounded pieces instead of attaching one very
     * large document update to the first free frame. Terminal/phase-boundary flushes remain exact.
     */
    private fun consumeStreamChunk(buffer: StringBuilder, force: Boolean): String {
        var end = if (force) buffer.length else minOf(buffer.length, STREAM_CATCH_UP_CHUNK_CHARS)
        if (end in 1 until buffer.length && Character.isHighSurrogate(buffer[end - 1])) end--
        val chunk = buffer.substring(0, end)
        buffer.delete(0, end)
        return chunk
    }

    private fun flushReasoningDeltas(force: Boolean = false) {
        streamHandler.removeCallbacks(flushReasoningRunnable)
        reasoningFlushScheduled = false
        if (pendingReasoning.isEmpty()) { reasoningPendingSince = 0L; return }
        if (NativeUiRenderSafety.shouldDeferStreamFlushForUiMotion(uiMotionActive, force)) return
        val now = android.os.SystemClock.uptimeMillis()
        val boundary = pendingReasoning.lastOrNull()?.let { it in charArrayOf('\n', '.', '!', '?', '?', '?', '?') } == true
        if (NativeUiRenderSafety.shouldDeferStreamFlush(pendingReasoning.length, boundary, now - reasoningPendingSince, force)) {
            reasoningFlushScheduled = true
            streamHandler.postDelayed(flushReasoningRunnable, 32L)
            return
        }
        val value = consumeStreamChunk(pendingReasoning, force)
        if (pendingReasoning.isEmpty()) {
            reasoningPendingSince = 0L
        } else {
            reasoningFlushScheduled = true
            streamHandler.postDelayed(flushReasoningRunnable, STREAM_CATCH_UP_DELAY_MS)
        }
        val continuedAfterAnswer = chatState.reasoningComplete
        chatState.beginReasoningAfterAnswerIfNeeded()
        if (continuedAfterAnswer) NativeChatDiagnostics.record(this, "reasoning_segment_started", JSONObject()
            .put("messageIndex", chatState.messages.size))
        chatState.reasoningText += value
        chatState.revision++
    }

    private fun answerFlushDelayMs(): Long {
        val liveChars = chatState.liveAssistantSnapshot.sourceChars
        return NativeUiRenderSafety.streamFlushDelayMs(liveChars, pendingAnswer.length, 88L)
    }

    private fun reasoningFlushDelayMs(): Long =
        NativeUiRenderSafety.streamFlushDelayMs(chatState.reasoningText.length, pendingReasoning.length, 88L)

    private fun flushAnswerDeltas(force: Boolean = false) {
        streamHandler.removeCallbacks(flushAnswerRunnable)
        answerFlushScheduled = false
        if (pendingAnswer.isEmpty()) { answerPendingSince = 0L; return }
        if (NativeUiRenderSafety.shouldDeferStreamFlushForUiMotion(uiMotionActive, force)) return
        val now = android.os.SystemClock.uptimeMillis()
        val boundary = pendingAnswer.lastOrNull()?.let { it in charArrayOf('\n', '.', '!', '?', '?', '?', '?') } == true
        if (NativeUiRenderSafety.shouldDeferStreamFlush(pendingAnswer.length, boundary, now - answerPendingSince, force)) {
            answerFlushScheduled = true
            streamHandler.postDelayed(flushAnswerRunnable, 32L)
            return
        }
        val value = consumeStreamChunk(pendingAnswer, force)
        if (pendingAnswer.isEmpty()) {
            answerPendingSince = 0L
        } else {
            answerFlushScheduled = true
            streamHandler.postDelayed(flushAnswerRunnable, STREAM_CATCH_UP_DELAY_MS)
        }
        chatState.finishReasoning()
        chatState.appendAssistant(value)
    }

    private fun commandFlushDelayMs(): Long =
        NativeUiRenderSafety.streamFlushDelayMs(chatState.commandOutputLength(), pendingCommand.length, 72L)

    private fun flushCommandDeltas() {
        streamHandler.removeCallbacks(flushCommandRunnable)
        commandFlushScheduled = false
        if (pendingCommand.isEmpty()) return
        val delta = pendingCommand.toString()
        pendingCommand.setLength(0)
        chatState.appendCommandOutput(delta)
    }

    private fun flushPlanDeltas() {
        streamHandler.removeCallbacks(flushPlanRunnable)
        planFlushScheduled = false
        if (pendingPlan.isEmpty()) return
        val delta = pendingPlan.toString()
        pendingPlan.setLength(0)
        chatState.appendProposedPlanDelta(JSONObject().put("itemId", pendingPlanItemId).put("delta", delta).toString())
    }

    private fun consumePendingCachedHistory(threadId: String): NativeHistorySnapshot? {
        if (pendingCachedHistoryThreadId != threadId) return null
        val snapshot = pendingCachedHistorySnapshot
        pendingCachedHistoryThreadId = null
        pendingCachedHistorySnapshot = null
        return snapshot
    }

    private fun applyPreparedHistory(threadId: String, snapshot: NativeHistorySnapshot, fresh: Boolean) {
        if (currentThreadId != threadId) return
        // A very fast empty disk result may beat the two-frame cached-history commit. Preserve the
        // known cached conversation rather than flashing/settling on an empty thread.
        if (fresh) {
            val pendingCache = consumePendingCachedHistory(threadId)
            if (snapshot.messages.isEmpty() && pendingCache?.messages?.isNotEmpty() == true && chatState.messages.isEmpty()) {
                chatState.applyHistorySnapshot(pendingCache)
                displayedHistorySnapshot = pendingCache
            }
        }
        val preserveCachedSnapshot = fresh && snapshot.messages.isEmpty() && chatState.messages.isNotEmpty()
        val unchangedFreshSnapshot = fresh && displayedHistorySnapshot?.hasSameContent(snapshot) == true
        if (!preserveCachedSnapshot && !unchangedFreshSnapshot) {
            chatState.applyHistorySnapshot(snapshot)
            displayedHistorySnapshot = snapshot
        }
        if (fresh) {
            if (!preserveCachedSnapshot && !unchangedFreshSnapshot) NativeHistorySnapshotCache.put(threadId, snapshot)
            chatState.historyLoading = false
        }
        if (chatState.busy) {
            // A resumed running turn starts a fresh live phase after the persisted snapshot.
            chatState.turnMessageStartIndex = chatState.messages.size
            chatState.phaseMessageStartIndex = chatState.messages.size
            if (chatState.phaseStartedAt <= 0L) chatState.phaseStartedAt = System.currentTimeMillis()
        }
        NativeChatDiagnostics.record(this, "history_snapshot_applied", JSONObject()
            .put("thread", threadId.take(8)).put("fresh", fresh)
            .put("messages", chatState.messages.size).put("estimatedChars", snapshot.estimatedChars)
            .put("preservedCache", preserveCachedSnapshot)
            .put("skippedUnchanged", unchangedFreshSnapshot))
    }

    override fun onHistoryPrepared(threadId: String, generation: Int, snapshot: NativeHistorySnapshot) {
        if (!NativeHistoryRouteGuard.shouldApply(expectedHistoryGeneration, currentThreadId, generation, threadId)) return
        applyPreparedHistory(threadId, snapshot, fresh = true)
    }

    override fun onEvent(function: String, value: String) {
        when (function) {
            "onReady" -> {
                // A late onReady from the previous conversation must not overwrite the
                // goal/mode of the conversation the user has already selected.
                if (currentThreadId != null && currentThreadId != value) return
                currentThreadId = value
                chatState.currentThreadId = value
                restoreGoalForThread(value)
                chatState.ready = true
                bridge?.loadSkills()
                chatState.connectionLabel = "已连接"
            }
            "onHistory" -> {
                // Compatibility path for an older retained Bridge. Never parse its large JSON
                // payload on the main thread.
                val routeThread = currentThreadId ?: return
                val routeGeneration = expectedHistoryGeneration
                Thread({
                    val snapshot = runCatching { NativeHistoryParser.parse(value) }.getOrNull() ?: return@Thread
                    runOnUiThread {
                        if (!NativeHistoryRouteGuard.shouldApply(expectedHistoryGeneration, currentThreadId, routeGeneration, routeThread)) return@runOnUiThread
                        applyPreparedHistory(routeThread, snapshot, fresh = true)
                    }
                }, "CodexHistoryParser").start()
            }
            "onDelta" -> {
                flushReasoningDeltas(force = true)
                flushCommandDeltas()
                if (pendingAnswer.isEmpty()) answerPendingSince = android.os.SystemClock.uptimeMillis()
                pendingAnswer.append(value)
                if (!answerFlushScheduled) {
                    answerFlushScheduled = true
                    streamHandler.postDelayed(flushAnswerRunnable, answerFlushDelayMs())
                }
            }
            "onAssistantItemComplete" -> {
                flushReasoningDeltas(force = true)
                flushAnswerDeltas(force = true)
                flushCommandDeltas()
                chatState.finishReasoning()
                chatState.completeAssistantItem(value)
            }
            "onFinalAnswer" -> {
                flushReasoningDeltas(force = true)
                flushAnswerDeltas(force = true)
                flushCommandDeltas()
                chatState.finishReasoning()
                chatState.appendAssistantFinal(value)
            }
            "onReasoningDelta" -> {
                flushAnswerDeltas(force = true)
                flushCommandDeltas()
                if (pendingReasoning.isEmpty()) reasoningPendingSince = android.os.SystemClock.uptimeMillis()
                pendingReasoning.append(value)
                if (!reasoningFlushScheduled) {
                    reasoningFlushScheduled = true
                    streamHandler.postDelayed(flushReasoningRunnable, reasoningFlushDelayMs())
                }
            }
            "onReasoningComplete" -> {
                flushReasoningDeltas(force = true)
                flushCommandDeltas()
                if (value.length > chatState.reasoningText.length) chatState.reasoningText = value
                chatState.revision++
            }
            "onCommandStarted" -> {
                flushCommandDeltas()
                chatState.startCommand(value)
            }
            "onCommandDelta" -> {
                pendingCommand.append(value)
                if (!commandFlushScheduled) {
                    commandFlushScheduled = true
                    streamHandler.postDelayed(flushCommandRunnable, commandFlushDelayMs())
                }
            }
            "onCommandComplete" -> {
                flushCommandDeltas()
                chatState.completeCommand(value)
            }
            "onToolComplete" -> { chatState.toolDetails.add(value); chatState.revision++ }
            "onSkills" -> {
                val array = runCatching { JSONArray(value) }.getOrNull() ?: JSONArray()
                val parsed = buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val name = item.optString("name")
                        val path = item.optString("path")
                        if (name.isNotBlank() && path.isNotBlank()) add(NativeSkill(name, item.optString("description"), path))
                    }
                }.distinctBy { it.path }.sortedBy { it.name.lowercase() }
                chatState.skills.clear()
                chatState.skills.addAll(parsed)
            }
            "onTokenUsage" -> chatState.updateTokenUsage(value)
            "onPlanStarted" -> {
                flushAnswerDeltas(force = true); flushPlanDeltas(); chatState.finishReasoning(); chatState.startProposedPlan(value)
            }
            "onPlanDelta" -> {
                flushAnswerDeltas(force = true); chatState.finishReasoning()
                val payload = runCatching { JSONObject(value) }.getOrNull()
                val itemId = payload?.optString("itemId", payload.optString("item_id")).orEmpty()
                val delta = payload?.optString("delta").orEmpty()
                if (itemId.isNotBlank() && pendingPlanItemId.isNotBlank() && itemId != pendingPlanItemId) flushPlanDeltas()
                if (itemId.isNotBlank()) pendingPlanItemId = itemId
                if (delta.isNotEmpty()) pendingPlan.append(delta)
                if (!planFlushScheduled && pendingPlan.isNotEmpty()) { planFlushScheduled = true; streamHandler.postDelayed(flushPlanRunnable, 56L) }
            }
            "onPlanComplete" -> {
                flushAnswerDeltas(force = true); flushPlanDeltas(); chatState.finishReasoning(); chatState.completeProposedPlan(value); pendingPlanItemId = ""
            }
            "onPlanUpdated" -> {
                // App-server versions have emitted the plan at params.plan, turn.plan,
                // or inside a nested payload. Normalize all of them so Plan mode is not
                // rendered as an empty panel on older/newer CLI builds.
                val payload = runCatching { JSONObject(value) }.getOrNull()
                val turn = payload?.optJSONObject("turn")
                val nested = payload?.optJSONObject("payload")
                val plan = payload?.optJSONArray("plan")
                    ?: turn?.optJSONArray("plan")
                    ?: nested?.optJSONArray("plan")
                val explanation = payload?.optString("explanation")?.takeIf { it.isNotBlank() }
                    ?: turn?.optString("explanation")?.takeIf { it.isNotBlank() }
                    ?: nested?.optString("explanation")?.takeIf { it.isNotBlank() }
                    ?: ""
                if (plan != null) {
                    chatState.ensurePlanPanel()
                    chatState.planJson = plan.toString()
                    chatState.finishPlanPanel(plan.length())
                }
                chatState.planExplanation = explanation
                currentThreadId?.let { threadId ->
                    getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
                        .putString(planPreferenceKey(threadId), chatState.planJson)
                        .putString(planExplanationPreferenceKey(threadId), chatState.planExplanation)
                        .apply()
                }
                chatState.revision++
            }
            "onCompactStatus" -> {
                val text = when (value) {
                    "started" -> nativeText(nativeLanguage, "\u6b63\u5728\u538b\u7f29\u4e0a\u4e0b\u6587\u2026", "Compacting context?")
                    "completed" -> nativeText(nativeLanguage, "\u4e0a\u4e0b\u6587\u5df2\u538b\u7f29", "Context compacted")
                    else -> nativeText(nativeLanguage, "\u4e0a\u4e0b\u6587\u538b\u7f29\u5931\u8d25", "Context compaction failed")
                }
                chatState.messages.add(NativeChatMessage(role = NativeChatRole.ACTIVITY, content = "NOTICE|$text"))
                chatState.revision++
            }
            "onUserInputRequest" -> {
                chatState.pendingUserInputRequest = value
                chatState.phase = NativeTurnPhase.WAITING
                chatState.processingLabel = "\u7b49\u5f85\u4f60\u7684\u56de\u7b54"
            }
            "onApprovalRequest" -> {
                chatState.pendingApprovalRequest = value
                chatState.phase = NativeTurnPhase.WAITING
                chatState.processingLabel = nativeText(nativeLanguage, "\u7b49\u5f85\u6743\u9650\u786e\u8ba4", "Waiting for approval")
            }
            "onSubagentEvent" -> {
                val thread = chatState.updateSubagent(value)
                if (thread.isNotBlank()) loadSubagentHistory(thread)
            }
            "onSubagentHistory" -> handleSubagentHistory(value)
            "onItem" -> chatState.addActivity(value)
            "onTurnComplete" -> {
                currentThreadId?.let(NativeHistorySnapshotCache::remove)
                flushReasoningDeltas(force = true)
                flushAnswerDeltas(force = true)
                flushPlanDeltas()
                flushCommandDeltas()
                chatState.pendingApprovalRequest = ""
                chatState.completeTurn()
                pendingPlanItemId = ""
                if (chatState.planJson != "[]") chatState.finishPlanPanel(runCatching { JSONArray(chatState.planJson).length() }.getOrDefault(0))
                stopFrameDiagnostics()
                refreshConversations()
                applyPendingProviderConfiguration()
            }
            "onNativeError" -> {
                currentThreadId?.let(NativeHistorySnapshotCache::remove)
                flushReasoningDeltas(force = true)
                flushAnswerDeltas(force = true)
                flushPlanDeltas()
                flushCommandDeltas()
                chatState.pendingApprovalRequest = ""
                NativeChatDiagnostics.record(this, "native_error", JSONObject()
                    .put("thread", currentThreadId.orEmpty().take(8))
                    .put("model", chatState.selectedModel)
                    .put("effort", chatState.selectedEffort)
                    .put("message", value.take(600)))
                chatState.addError(value)
                stopFrameDiagnostics()
                applyPendingProviderConfiguration()
            }
            "onLog" -> Unit
        }
    }

    override fun onResume() {
        super.onResume()
        // Settings is a separate native Activity. Refresh preferences here so a theme
        // or language change is visible immediately when returning to the chat.
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        nativeThemeMode = FcodeAppearancePreferences.normalizeColorMode(prefs.getString(FcodeAppearancePreferences.COLOR_MODE, "system"))
        nativeColorPalette = FcodeColorPalette.from(prefs.getString(FcodeAppearancePreferences.COLOR_PALETTE, FcodeColorPalette.ROSE.value)).value
        nativeChatBackground = FcodeChatBackgroundStyle.from(prefs.getString(FcodeAppearancePreferences.CHAT_BACKGROUND, FcodeChatBackgroundStyle.THEME.value)).value
        nativeChatBackgroundImage = prefs.getString(FcodeAppearancePreferences.CHAT_BACKGROUND_IMAGE, "").orEmpty()
        nativeChatBackgroundDim = prefs.getFloat(FcodeAppearancePreferences.CHAT_BACKGROUND_DIM, 0.32f).coerceIn(0f, 0.72f)
        nativeLanguage = prefs.getString("native_language_v1", "system").orEmpty().let {
            if (it == "en") "en" else if (it == "zh") "zh" else if (Locale.getDefault().language == "en") "en" else "zh"
        }
        streamAnimationsEnabled = prefs.getBoolean("native_stream_animations_v1", true)
        fixedStreamingViewportEnabled = prefs.getBoolean("native_stream_fixed_viewport_v1", true)
        showReasoning = prefs.getBoolean("native_show_reasoning_v1", true)
        autoFollowOutput = prefs.getBoolean("native_auto_follow_v1", true)
        showResponseStats = prefs.getBoolean(NATIVE_SHOW_RESPONSE_STATS_PREFERENCE, true)
        chatState.permissionMode = NativePermissionMode.normalize(prefs.getString(NativePermissionMode.PREFERENCE_KEY, NativePermissionMode.FULL_ACCESS))
        bridge?.loadSkills()
        reloadProviderConfigurationIfChanged()
        if (chatState.busy) startFrameDiagnostics()
    }

    override fun onPause() {
        stopFrameDiagnostics()
        super.onPause()
    }

    override fun onDestroy() {
        streamHandler.removeCallbacksAndMessages(null)
        getSharedPreferences("codex_mobile", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(taskPreferenceListener)
        CodexNativeRuntime.detach(this)
        bridge = null
        super.onDestroy()
    }
}
