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

internal enum class NativeChatRole {
    USER,
    ASSISTANT,
    ACTIVITY,
    ERROR,
}

internal enum class NativeTurnPhase(val active: Boolean) {
    IDLE(false), WAITING(true), REASONING(true), TOOL_RUNNING(true), ANSWERING(true), STOPPING(true), COMPLETED(false), FAILED(false)
}

internal data class NativeAttachment(val name: String, val path: String, val image: Boolean)

internal data class NativeSkill(val name: String, val description: String, val path: String)

internal data class NativeModelOption(val id: String, val name: String, val efforts: List<String>, val defaultEffort: String)

internal data class NativeConversation(val threadId: String, val title: String, val state: String, val projectPath: String, val favorite: Boolean) { val projectName: String get() = projectPath.trimEnd('/').substringAfterLast('/').ifBlank { "无项目" } }

internal data class NativeChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: NativeChatRole,
    val content: String,
    val streaming: Boolean = false,
    val revealStartedAt: Long = 0L,
    val finalOnlyReveal: Boolean = false,
    val skills: List<NativeSkill> = emptyList(),
    val attachments: List<NativeAttachment> = emptyList(),
)

internal class NativeChatState {
    val messages = mutableStateListOf<NativeChatMessage>()
    val conversations = mutableStateListOf<NativeConversation>()
    val modelOptions = mutableStateListOf<NativeModelOption>()
    val attachments = mutableStateListOf<NativeAttachment>()
    val skills = mutableStateListOf<NativeSkill>()
    val selectedSkills = mutableStateListOf<NativeSkill>()
    val toolDetails = mutableStateListOf<String>()
    val liveSubagents = mutableStateListOf<String>()
    val subagentHistories = mutableStateMapOf<String, String>()
    val subagentStatuses = mutableStateMapOf<String, String>()
    val subagentHistoryErrors = mutableStateMapOf<String, String>()
    val loadingSubagentHistories = mutableStateListOf<String>()
    var input by mutableStateOf("")
    var connectionLabel by mutableStateOf("正在启动 Codex…")
    var ready by mutableStateOf(false)
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
    var planJson by mutableStateOf("[]")
    var planExplanation by mutableStateOf("")
    var planPanelAdded by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
    var processingLabel by mutableStateOf("")
    var reasoningText by mutableStateOf("")
    var reasoningComplete by mutableStateOf(false)
    var reasoningCompletedAt by mutableStateOf(0L)
    var commandText by mutableStateOf("")
    var turnStartedAt by mutableStateOf(0L)
    var turnMessageStartIndex by mutableIntStateOf(0)
    var phaseStartedAt by mutableStateOf(0L)
    var phaseMessageStartIndex by mutableIntStateOf(0)

    fun resetConversation() {
        messages.clear()
        planJson = "[]"
        planExplanation = ""
        planPanelAdded = false
        activeGoalObjective = ""
        activeGoalStatus = "active"
        phase = NativeTurnPhase.IDLE
        processingLabel = ""
        reasoningText = ""
        reasoningComplete = false
        reasoningCompletedAt = 0L
        commandText = ""
        toolDetails.clear()
        liveSubagents.clear()
        subagentHistories.clear()
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
            .put("command", commandText.trim())
            .put("tools", tools)
            .put("reasoningUnavailable", reasoningText.isBlank())
            .toString()
    }

    private fun sealCurrentPhase() {
        if (reasoningText.isBlank() && commandText.isBlank() && toolDetails.isEmpty() && liveSubagents.isEmpty()) return
        val process = "PROCESS2|" + Base64.encodeToString(processPayload().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val insertAt = (phaseMessageStartIndex until messages.size)
            .firstOrNull { messages[it].role == NativeChatRole.ASSISTANT }
            ?: messages.size
        messages.add(insertAt, NativeChatMessage(role = NativeChatRole.ACTIVITY, content = process, revealStartedAt = System.currentTimeMillis()))
    }

    fun beginReasoningAfterAnswerIfNeeded() {
        if (!reasoningComplete) return
        sealCurrentPhase()
        val lastAssistant = messages.indexOfLast { it.role == NativeChatRole.ASSISTANT && it.streaming }
        if (lastAssistant >= 0) messages[lastAssistant] = messages[lastAssistant].copy(streaming = false)
        reasoningText = ""
        reasoningComplete = false
        reasoningCompletedAt = 0L
        commandText = ""
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

    fun finishPlanPanel(stepCount: Int = 0) {
        val index = messages.indexOfFirst { it.role == NativeChatRole.ACTIVITY && it.content.startsWith("PLAN_PANEL|") }
        if (index >= 0) {
            messages[index] = messages[index].copy(content = "PLAN_PANEL|complete|$stepCount")
            revision++
        }
    }

    private fun cleanProtocolMarkup(text: String): String = text
        .replace(Regex("""</?propose_plan\s*>""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""</?plan\s*>""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""</?final\s*>""", RegexOption.IGNORE_CASE), "")

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
        if (last != null && last.role == NativeChatRole.ASSISTANT && last.streaming) {
            messages[messages.lastIndex] = last.copy(content = last.content + cleanedDelta)
        } else {
            messages.add(
                NativeChatMessage(
                    role = NativeChatRole.ASSISTANT,
                    content = cleanedDelta,
                    streaming = true,
                    revealStartedAt = System.currentTimeMillis(),
                ),
            )
        }
        revision++
    }

    fun appendAssistantFinal(text: String) {
        val cleanedText = cleanProtocolMarkup(text)
        if (cleanedText.isBlank()) return
        val existingIndex = (messages.lastIndex downTo turnMessageStartIndex.coerceAtLeast(0))
            .firstOrNull { messages[it].role == NativeChatRole.ASSISTANT }
        if (existingIndex != null) {
            val existing = messages[existingIndex]
            // A few app-server/provider combinations deliver both deltas and the final
            // item. Keep the stable message id and merge the authoritative final text;
            // adding a second assistant item makes a whole paragraph flash on screen.
            val merged = when {
                cleanedText.startsWith(existing.content) -> text
                existing.content.startsWith(cleanedText) -> existing.content
                else -> cleanedText
            }
            messages[existingIndex] = existing.copy(
                content = merged,
                streaming = false,
                finalOnlyReveal = false,
            )
        } else {
            messages.add(
                NativeChatMessage(
                    role = NativeChatRole.ASSISTANT,
                    content = cleanedText,
                    streaming = false,
                    revealStartedAt = System.currentTimeMillis(),
                    finalOnlyReveal = true,
                ),
            )
        }
        phase = NativeTurnPhase.COMPLETED
        revision++
    }

    fun replaceHistory(value: String) {
        val parsed = ArrayList<NativeChatMessage>()
        planJson = "[]"
        planExplanation = ""
        val items = JSONArray(value)
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val role = when (item.optString("role")) {
                "user" -> NativeChatRole.USER
                "assistant" -> NativeChatRole.ASSISTANT
                "activity" -> NativeChatRole.ACTIVITY
                else -> continue
            }
            val content = item.optString("content").trim()
            val messageSkills = buildList {
                val skillArray = item.optJSONArray("skills") ?: JSONArray()
                for (skillIndex in 0 until skillArray.length()) {
                    val skill = skillArray.optJSONObject(skillIndex) ?: continue
                    val name = skill.optString("name").trim()
                    val path = skill.optString("path").trim()
                    if (name.isNotEmpty()) add(NativeSkill(name, skill.optString("description"), path))
                }
            }
            val messageAttachments = buildList {
                val attachmentArray = item.optJSONArray("attachments") ?: JSONArray()
                for (attachmentIndex in 0 until attachmentArray.length()) {
                    val attachment = attachmentArray.optJSONObject(attachmentIndex) ?: continue
                    val path = attachment.optString("path").trim()
                    if (path.isNotEmpty()) add(NativeAttachment(attachment.optString("name", File(path).name), path, attachment.optBoolean("image")))
                }
            }
            if (role == NativeChatRole.ACTIVITY && content.startsWith("PLAN|")) {
                runCatching {
                    val decoded = String(Base64.decode(content.substringAfter('|'), Base64.DEFAULT), Charsets.UTF_8)
                    val planPayload = JSONObject(decoded)
                    planJson = planPayload.optJSONArray("plan")?.toString() ?: "[]"
                    planExplanation = planPayload.optString("explanation")
                }
                continue
            }
            if (content.isNotEmpty()) parsed.add(NativeChatMessage(role = role, content = content, skills = messageSkills, attachments = messageAttachments))
        }
        // One snapshot mutation avoids recomposing the chat once for every historical item.
        messages.clear()
        messages.addAll(parsed)
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
        sealCurrentPhase()
        reasoningText = ""
        commandText = ""
        toolDetails.clear()
        liveSubagents.clear()
        phaseMessageStartIndex = messages.size
        val lastAssistant = messages.indexOfLast { it.role == NativeChatRole.ASSISTANT }
        if (lastAssistant >= 0) {
            messages[lastAssistant] = messages[lastAssistant].copy(streaming = false)
        }
        phase = NativeTurnPhase.COMPLETED
        connectionLabel = "已连接"
        revision++
    }

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
    }

    private val chatState = NativeChatState()
    private var bridge: CodexAppServerBridge? = null
    private var activeProfileId: String = ""
    private var pendingConversationAnimationKey: String? = null
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
    private var reasoningFlushScheduled = false
    private var answerFlushScheduled = false
    private var reasoningPendingSince = 0L
    private var answerPendingSince = 0L
    private val flushReasoningRunnable = Runnable {
        reasoningFlushScheduled = false
        flushReasoningDeltas()
    }
    private val flushAnswerRunnable = Runnable {
        answerFlushScheduled = false
        flushAnswerDeltas()
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
    private var nativeThemeMode by mutableStateOf("system")
    private var nativeLanguage by mutableStateOf("zh")
    private var streamAnimationsEnabled by mutableStateOf(true)
    private var showReasoning by mutableStateOf(true)
    private var autoFollowOutput by mutableStateOf(true)
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
        nativeThemeMode = nativePrefs.getString("native_theme_mode_v1", "system").orEmpty().takeIf { it in setOf("system", "light", "dark") } ?: "system"
        nativeLanguage = nativePrefs.getString("native_language_v1", "system").orEmpty().let { if (it == "en") "en" else if (it == "zh") "zh" else if (Locale.getDefault().language == "en") "en" else "zh" }
        streamAnimationsEnabled = nativePrefs.getBoolean("native_stream_animations_v1", true)
        showReasoning = nativePrefs.getBoolean("native_show_reasoning_v1", true)
        autoFollowOutput = nativePrefs.getBoolean("native_auto_follow_v1", true)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )

        setContent {
            FcodeChatTheme(nativeThemeMode, nativeLanguage, streamAnimationsEnabled, showReasoning, autoFollowOutput) {
                NativeChatScreen(
                    state = chatState,
                    onSend = ::sendMessage,
                    onInputChange = ::updateDraft,
                    onRetry = ::retryMessage,
                    onEditMessage = ::editMessage,
                    onStop = ::stopCurrentTurn,
                    onNewConversation = ::newConversation,
                    onResumeConversation = ::resumeConversation,
                    onLoadSubagentHistory = ::loadSubagentHistory,
                    onModelSelected = ::selectNativeModel,
                    onEffortSelected = ::selectNativeEffort,
                    onModeChange = ::setChatMode,
                    onSetGoal = ::setGoal,
                    onClearGoal = ::clearGoal,
                    onToggleGoalPause = ::toggleGoalPause,
                    onCompact = { bridge?.compactThread() },
                    onAnswerUserInput = ::answerUserInput,
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
        refreshConversations()
        startBackend()
    }

    private fun startBackend() {
        val binary = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex")
        if (!binary.canExecute()) {
            chatState.addError("Codex CLI 尚未安装，请先返回首页完成运行环境安装。")
            return
        }

        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val profile = CodexProviderStore(prefs).active()
        if (profile == null || profile.baseUrl.isBlank() || profile.apiKey.isBlank()) {
            chatState.addError("没有可用的 API 配置，请先返回首页创建并启用配置。")
            return
        }

        chatState.modelOptions.clear()
        profile.models.forEach { model ->
            val efforts = model.supportedReasoningEfforts.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
            val defaultEffort = model.defaultReasoningEffort.trim().lowercase()
                .takeIf { it in efforts }
                ?: efforts.firstOrNull { it == "high" }
                ?: efforts.firstOrNull()
                ?: "high"
            chatState.modelOptions.add(
                NativeModelOption(
                    id = model.id,
                    name = model.name.ifBlank { model.id },
                    efforts = efforts.ifEmpty { listOf(defaultEffort) },
                    defaultEffort = defaultEffort,
                ),
            )
        }
        activeProfileId = profile.id
        restoreNativeSelection(prefs, profile.id, profile.model)
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

        val routeThroughMihomo = prefs.getBoolean("mihomo_route_api", false) ||
            (profile.proxyEnabled && profile.proxyWebUi)
        val retainedRuntime = CodexNativeRuntime.exists()
        val retainedThread = CodexNativeRuntime.currentThreadId()
        bridge = CodexNativeRuntime.attach(
            this,
            this,
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
        if (retainedRuntime && !retainedThread.isNullOrBlank()) {
            streamHandler.postDelayed({ resumeConversation(retainedThread, retainedRuntime = true) }, 80L)
        }

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
    ) {
        val storedModelId = prefs.getString(modelPreferenceKey(profileId), null).orEmpty()
        val selectedOption = chatState.modelOptions.firstOrNull {
            it.id.equals(storedModelId, ignoreCase = true)
        } ?: chatState.modelOptions.firstOrNull {
            it.id.equals(profileDefaultModel, ignoreCase = true)
        } ?: chatState.modelOptions.firstOrNull()

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
        chatState.toolDetails.clear()
        chatState.liveSubagents.clear()
        chatState.turnStartedAt = System.currentTimeMillis()
        chatState.turnMessageStartIndex = chatState.messages.size
        chatState.phaseStartedAt = chatState.turnStartedAt
        chatState.phaseMessageStartIndex = chatState.turnMessageStartIndex
        bridge?.sendMessage(value, chatState.selectedModel, chatState.selectedEffort, "[]", chatState.selectedMode)
    }

    private fun updateDraft(value: String) {
        chatState.input = value
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString("native_chat_draft_v1", value).apply()
    }

    private fun sendMessage(text: String) {
        val value = text.trim()
        if ((value.isEmpty() && chatState.attachments.isEmpty()) || chatState.busy || !chatState.ready) return
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
                val project = conversationProjectCache[task.threadId] ?: CodexAppServerBridge
                    .resolveConversationProject(task.threadId)
                    .also { resolved -> if (resolved.isNotBlank()) conversationProjectCache[task.threadId] = resolved }
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
        CodexTaskStore.delete(this, conversation.threadId)
        refreshConversations()
    }

    private fun subagentMessageCount(raw: String?): Int = runCatching {
        if (raw.isNullOrBlank()) 0 else JSONArray(raw).length()
    }.getOrDefault(0)

    private fun loadSubagentHistory(threadId: String) {
        if (threadId.isBlank() || threadId in chatState.loadingSubagentHistories) return
        val status = chatState.subagentStatuses[threadId].orEmpty()
        if (status in setOf("done", "failed") && subagentMessageCount(chatState.subagentHistories[threadId]) > 0) return
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
        val messages = payload.optJSONArray("messages") ?: JSONArray()
        chatState.subagentHistories[thread] = messages.toString()
        val status = payload.optString("status", "").trim().lowercase()
        if (status in setOf("waiting", "working", "done", "failed")) chatState.subagentStatuses[thread] = status
        val error = payload.optString("error", "").trim()
        if (error.isBlank()) chatState.subagentHistoryErrors.remove(thread) else chatState.subagentHistoryErrors[thread] = error

        val attempt = (subagentHistoryAttempts[thread] ?: 0) + 1
        subagentHistoryAttempts[thread] = attempt
        val terminal = status == "done" || status == "failed"
        val shouldRetry = when {
            !terminal -> attempt < 120
            messages.length() == 0 -> attempt < 9
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

    private fun discardPendingStreamEvents(reason: String) {
        streamHandler.removeCallbacks(flushReasoningRunnable)
        streamHandler.removeCallbacks(flushAnswerRunnable)
        reasoningFlushScheduled = false
        answerFlushScheduled = false
        val droppedReasoning = pendingReasoning.length
        val droppedAnswer = pendingAnswer.length
        pendingReasoning.setLength(0)
        pendingAnswer.setLength(0)
        stopFrameDiagnostics()
        NativeChatDiagnostics.record(this, "stream_route_reset", JSONObject()
            .put("reason", reason).put("droppedReasoning", droppedReasoning).put("droppedAnswer", droppedAnswer))
    }

    private fun resumeConversation(threadId: String, retainedRuntime: Boolean = false) {
        discardPendingStreamEvents("resume")
        subagentRouteGeneration = subagentRouteCounter.incrementAndGet()
        subagentHistoryAttempts.clear()
        pendingConversationAnimationKey = threadId
        currentThreadId = threadId
        val selectedConversation = chatState.conversations.firstOrNull { it.threadId == threadId }
        chatState.resetConversation()
        chatState.conversationTitle = selectedConversation?.title ?: "对话"
        if (selectedConversation?.state == CodexTaskStore.RUNNING) {
            chatState.phase = NativeTurnPhase.WAITING
            chatState.processingLabel = "正在重新连接任务"
            chatState.turnStartedAt = System.currentTimeMillis()
            chatState.phaseStartedAt = chatState.turnStartedAt
            startFrameDiagnostics()
        }
        chatState.ready = false
        chatState.connectionLabel = "\u6b63\u5728\u6062\u590d\u5bf9\u8bdd\u2026"
        if (retainedRuntime) bridge?.restoreRetainedConversation(threadId)
        else bridge?.resumeConversation(threadId)
    }

    private fun newConversation() {
        discardPendingStreamEvents("new")
        chatState.selectedMode = "default"
        subagentRouteGeneration = subagentRouteCounter.incrementAndGet()
        subagentHistoryAttempts.clear()
        pendingConversationAnimationKey = null
        currentThreadId = null
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

    private fun flushReasoningDeltas() {
        streamHandler.removeCallbacks(flushReasoningRunnable)
        reasoningFlushScheduled = false
        if (pendingReasoning.isEmpty()) { reasoningPendingSince = 0L; return }
        val now = android.os.SystemClock.uptimeMillis()
        val boundary = pendingReasoning.lastOrNull()?.let { it in charArrayOf('\n', '.', '!', '?', '?', '?', '?') } == true
        if (pendingReasoning.length < 12 && !boundary && now - reasoningPendingSince < 110L) {
            reasoningFlushScheduled = true
            streamHandler.postDelayed(flushReasoningRunnable, 32L)
            return
        }
        val value = pendingReasoning.toString()
        pendingReasoning.setLength(0)
        reasoningPendingSince = 0L
        val continuedAfterAnswer = chatState.reasoningComplete
        chatState.beginReasoningAfterAnswerIfNeeded()
        if (continuedAfterAnswer) NativeChatDiagnostics.record(this, "reasoning_segment_started", JSONObject()
            .put("messageIndex", chatState.messages.size))
        chatState.reasoningText += value
        chatState.revision++
        NativeChatDiagnostics.record(this, "reasoning_flush", JSONObject()
            .put("chars", value.length).put("total", chatState.reasoningText.length))
    }

    private fun flushAnswerDeltas() {
        streamHandler.removeCallbacks(flushAnswerRunnable)
        answerFlushScheduled = false
        if (pendingAnswer.isEmpty()) { answerPendingSince = 0L; return }
        val now = android.os.SystemClock.uptimeMillis()
        val boundary = pendingAnswer.lastOrNull()?.let { it in charArrayOf('\n', '.', '!', '?', '?', '?', '?') } == true
        if (pendingAnswer.length < 12 && !boundary && now - answerPendingSince < 110L) {
            answerFlushScheduled = true
            streamHandler.postDelayed(flushAnswerRunnable, 32L)
            return
        }
        val value = pendingAnswer.toString()
        pendingAnswer.setLength(0)
        answerPendingSince = 0L
        chatState.finishReasoning()
        chatState.appendAssistant(value)
        NativeChatDiagnostics.record(this, "answer_flush", JSONObject()
            .put("chars", value.length).put("messages", chatState.messages.size))
    }

    override fun onEvent(function: String, value: String) {
        when (function) {
            "onReady" -> {
                // A late onReady from the previous conversation must not overwrite the
                // goal/mode of the conversation the user has already selected.
                if (currentThreadId != null && currentThreadId != value) return
                currentThreadId = value
                restoreGoalForThread(value)
                chatState.ready = true
                bridge?.loadSkills()
                chatState.connectionLabel = "已连接"
            }
            "onHistory" -> {
                chatState.replaceHistory(value)
                if (chatState.busy) {
                    // A resumed running turn starts a fresh live phase after the persisted
                    // snapshot. Never calculate duration from the reset value 0, and never
                    // merge the new final answer into an older assistant message.
                    chatState.turnMessageStartIndex = chatState.messages.size
                    chatState.phaseMessageStartIndex = chatState.messages.size
                    if (chatState.phaseStartedAt <= 0L) chatState.phaseStartedAt = System.currentTimeMillis()
                }
                pendingConversationAnimationKey?.let { key -> chatState.conversationAnimationKey = key }
                pendingConversationAnimationKey = null
            }
            "onDelta" -> {
                flushReasoningDeltas()
                if (pendingAnswer.isEmpty()) answerPendingSince = android.os.SystemClock.uptimeMillis()
                pendingAnswer.append(value)
                if (!answerFlushScheduled) {
                    answerFlushScheduled = true
                    streamHandler.postDelayed(flushAnswerRunnable, 56L)
                }
            }
            "onFinalAnswer" -> {
                flushReasoningDeltas()
                flushAnswerDeltas()
                chatState.finishReasoning()
                chatState.appendAssistantFinal(value)
            }
            "onReasoningDelta" -> {
                flushAnswerDeltas()
                if (pendingReasoning.isEmpty()) reasoningPendingSince = android.os.SystemClock.uptimeMillis()
                pendingReasoning.append(value)
                if (!reasoningFlushScheduled) {
                    reasoningFlushScheduled = true
                    streamHandler.postDelayed(flushReasoningRunnable, 56L)
                }
            }
            "onReasoningComplete" -> {
                flushReasoningDeltas()
                if (value.length > chatState.reasoningText.length) chatState.reasoningText = value
                chatState.revision++
            }
            "onCommandDelta" -> { chatState.commandText += value; chatState.revision++ }
            "onCommandComplete" -> {
                chatState.toolDetails.add(value)
                chatState.commandText = ""
                chatState.revision++
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
            "onSubagentEvent" -> {
                val thread = chatState.updateSubagent(value)
                if (thread.isNotBlank()) loadSubagentHistory(thread)
            }
            "onSubagentHistory" -> handleSubagentHistory(value)
            "onItem" -> chatState.addActivity(value)
            "onTurnComplete" -> {
                flushReasoningDeltas()
                flushAnswerDeltas()
                chatState.completeTurn()
                if (chatState.planJson != "[]") chatState.finishPlanPanel(runCatching { JSONArray(chatState.planJson).length() }.getOrDefault(0))
                stopFrameDiagnostics()
                refreshConversations()
            }
            "onNativeError" -> {
                NativeChatDiagnostics.record(this, "native_error", JSONObject()
                    .put("thread", currentThreadId.orEmpty().take(8))
                    .put("model", chatState.selectedModel)
                    .put("effort", chatState.selectedEffort)
                    .put("message", value.take(600)))
                chatState.addError(value)
                stopFrameDiagnostics()
            }
            "onLog" -> Unit
        }
    }

    override fun onResume() {
        super.onResume()
        // Settings is a separate native Activity. Refresh preferences here so a theme
        // or language change is visible immediately when returning to the chat.
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        nativeThemeMode = prefs.getString("native_theme_mode_v1", "system").orEmpty().takeIf { it in setOf("system", "light", "dark") } ?: "system"
        nativeLanguage = prefs.getString("native_language_v1", "system").orEmpty().let {
            if (it == "en") "en" else if (it == "zh") "zh" else if (Locale.getDefault().language == "en") "en" else "zh"
        }
        streamAnimationsEnabled = prefs.getBoolean("native_stream_animations_v1", true)
        showReasoning = prefs.getBoolean("native_show_reasoning_v1", true)
        autoFollowOutput = prefs.getBoolean("native_auto_follow_v1", true)
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
