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

private const val NATIVE_USER_INPUT_TIMEOUT_MS = 240_000L
private const val NATIVE_SHOW_RESPONSE_STATS_PREFERENCE = "native_show_response_stats_v1"
private const val NATIVE_SHOW_MODEL_SUBTITLE_PREFERENCE = "native_show_model_subtitle_v1"
private const val NATIVE_SHOW_REASONING_TITLES_PREFERENCE = "native_show_reasoning_titles_v1"
private const val NATIVE_FOLLOW_UP_ACTION_PREFERENCE = "native_follow_up_submit_action_v1"
private data class PendingNativeSteer(
    val messageId: String,
    val followUp: NativeQueuedFollowUp,
)

/** Immutable hand-off between background catalog preparation and main-thread runtime attach. */
private data class NativeBackendStartRequest(
    val generation: Int,
    val profile: CodexProviderStore.Profile,
    val providerFingerprint: String,
    val routeThroughMihomo: Boolean,
)

class CodexChatActivity : ComponentActivity(), CodexAppServerBridge.EventListener {
    companion object {
        private val subagentRouteCounter = java.util.concurrent.atomic.AtomicInteger(0)
        private val stalePendingStateCleaned = java.util.concurrent.atomic.AtomicBoolean(false)
        private const val STREAM_CATCH_UP_CHUNK_CHARS = 1_200
        private const val STREAM_CATCH_UP_DELAY_MS = 24L
    }

    private val chatState = NativeChatState()
    private lateinit var providerStore: CodexProviderStore
    private lateinit var compactionSettingsStore: NativeCompactionSettingsStore
    private lateinit var compactionJournalStore: NativeCompactionJournalStore
    private var compactionPolicy = NativeCompactionPolicy()
    private var compactionPolicyKey = ""
    private var compactionSettingsSnapshot: NativeCompactionSettings? = null
    private var lastReliableUsage: NativeTurnUsage? = null
    private var protocolReasoningSeen = false
    private var protocolAssistantSeen = false
    private var protocolCommandSeen = false
    private var protocolPlanSeen = false
    private var protocolToolSeen = false
    private var protocolSubagentSeen = false
    private var protocolUsageSeen = false
    private var protocolTurnCompletedSeen = false
    private val protocolEventQueue = NativeOrderedProtocolEventQueue()
    private val flushProtocolEventQueueRunnable = Runnable { flushProtocolEventQueue() }
    private var pendingAutoCompactionTurn = ""
    private val compactionLifecycleTimeouts = HashMap<String, Runnable>()
    private var bridge: CodexAppServerBridge? = null
    private val pendingNativeSteers = mutableMapOf<Int, PendingNativeSteer>()
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
    private val autoCompactionRunnable = Runnable {
        val expectedTurn = pendingAutoCompactionTurn
        pendingAutoCompactionTurn = ""
        if (expectedTurn.isBlank() || expectedTurn != activeCompactionTurnKey()) return@Runnable
        val usage = lastReliableUsage ?: return@Runnable
        val decision = currentCompactionPolicy().evaluate(compactionPolicyInput(usage))
        if (!decision.shouldTrigger || chatState.compactionItems.any { !it.isTerminal }) return@Runnable
        requestCompaction(NativeCompactionSource.AUTOMATIC, decision)
    }
    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val backendCatalogPreparer = NativeBackendCatalogPreparer(
        File(File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "ilyop-model-catalog.json"),
    )
    private var backendStartGeneration = 0
    private var goalRetryCycleActive = false
    private var goalRetryWaitingForCompletion = false
    private var goalRetryScheduled = false
    private var suppressGoalRetryUntilNewTurn = false
    private val goalRetryRunnable = Runnable {
        goalRetryScheduled = false
        if (!canAutoRetryGoal()) return@Runnable
        if (chatState.busy) {
            scheduleGoalRetry(2_000L)
            return@Runnable
        }
        val prompt = chatState.messages.lastOrNull { it.role == NativeChatRole.USER }?.content.orEmpty()
        if (prompt.isBlank()) return@Runnable
        goalRetryWaitingForCompletion = false
        retryMessage(prompt, preserveRetryStatus = true)
    }
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
    private var notificationTargetThreadId: String = ""
    private var expectedHistoryGeneration = -1
    private var displayedHistorySnapshot: NativeHistorySnapshot? = null
    private var nativeThemeMode by mutableStateOf("system")
    private var nativeColorPalette by mutableStateOf(FcodeColorPalette.ROSE.value)
    private var nativeInterfaceStyle by mutableStateOf(FcodeInterfaceStyle.MATERIAL.value)
    private var nativeAppearanceRevision by mutableIntStateOf(0)
    private var nativeChatBackground by mutableStateOf(FcodeChatBackgroundStyle.THEME.value)
    private var nativeChatBackgroundImage by mutableStateOf("")
    private var nativeChatBackgroundDim by mutableStateOf(0.32f)
    private var nativeLanguage by mutableStateOf("zh")
    private var streamAnimationsEnabled by mutableStateOf(true)
    private var fixedStreamingViewportEnabled by mutableStateOf(true)
    private var showReasoning by mutableStateOf(true)
    private var autoFollowOutput by mutableStateOf(true)
    private var showResponseStats by mutableStateOf(true)
    private var showModelSubtitle by mutableStateOf(true)
    private var showReasoningTitles by mutableStateOf(true)
    private var hideNativeStatusBar by mutableStateOf(false)
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
        providerStore = CodexProviderStore(nativePrefs)
        compactionSettingsStore = NativeCompactionSettingsStore(nativePrefs)
        compactionJournalStore = NativeCompactionJournalStore(nativePrefs)
        notificationTargetThreadId = intent?.getStringExtra(NativeTaskNotificationManager.EXTRA_THREAD_ID).orEmpty()
        if (stalePendingStateCleaned.compareAndSet(false, true)) {
            val staleApprovalKeys = nativePrefs.all.keys.filter { it.startsWith("native_thread_pending_approval_v1_") }
            if (staleApprovalKeys.isNotEmpty()) nativePrefs.edit().apply {
                staleApprovalKeys.forEach(::remove)
            }.apply()
        }
        chatState.input = nativePrefs.getString("native_chat_draft_v1", "").orEmpty()
        chatState.followUpSubmitAction = NativeFollowUpSubmitAction.from(
            nativePrefs.getString(NATIVE_FOLLOW_UP_ACTION_PREFERENCE, "steer"),
        )
        chatState.selectedMode = nativePrefs.getString("native_chat_mode_v1", "default").orEmpty().takeIf { it == "plan" } ?: "default"
        chatState.permissionMode = NativePermissionMode.normalize(nativePrefs.getString(NativePermissionMode.PREFERENCE_KEY, NativePermissionMode.FULL_ACCESS))
        nativeThemeMode = FcodeAppearancePreferences.normalizeColorMode(nativePrefs.getString(FcodeAppearancePreferences.COLOR_MODE, "system"))
        nativeColorPalette = FcodeColorPalette.from(nativePrefs.getString(FcodeAppearancePreferences.COLOR_PALETTE, FcodeColorPalette.ROSE.value)).value
        nativeInterfaceStyle = FcodeInterfaceStyle.from(nativePrefs.getString(FcodeAppearancePreferences.INTERFACE_STYLE, FcodeInterfaceStyle.MATERIAL.value)).value
        nativeChatBackground = FcodeChatBackgroundStyle.from(nativePrefs.getString(FcodeAppearancePreferences.CHAT_BACKGROUND, FcodeChatBackgroundStyle.THEME.value)).value
        nativeChatBackgroundImage = nativePrefs.getString(FcodeAppearancePreferences.CHAT_BACKGROUND_IMAGE, "").orEmpty()
        nativeChatBackgroundDim = nativePrefs.getFloat(FcodeAppearancePreferences.CHAT_BACKGROUND_DIM, 0.32f).coerceIn(0f, 0.72f)
        nativeLanguage = nativePrefs.getString("native_language_v1", "system").orEmpty().let { if (it == "en") "en" else if (it == "zh") "zh" else if (Locale.getDefault().language == "en") "en" else "zh" }
        streamAnimationsEnabled = nativePrefs.getBoolean("native_stream_animations_v1", true)
        fixedStreamingViewportEnabled = nativePrefs.getBoolean("native_stream_fixed_viewport_v1", true)
        showReasoning = nativePrefs.getBoolean("native_show_reasoning_v1", true)
        autoFollowOutput = nativePrefs.getBoolean("native_auto_follow_v1", true)
        showResponseStats = nativePrefs.getBoolean(NATIVE_SHOW_RESPONSE_STATS_PREFERENCE, true)
        showModelSubtitle = nativePrefs.getBoolean(NATIVE_SHOW_MODEL_SUBTITLE_PREFERENCE, true)
        showReasoningTitles = nativePrefs.getBoolean(NATIVE_SHOW_REASONING_TITLES_PREFERENCE, true)
        hideNativeStatusBar = nativePrefs.getBoolean(NATIVE_HIDE_STATUS_BAR_PREFERENCE, false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        applyNativeStatusBarVisibility(hideNativeStatusBar)

        setContent {
            FcodeChatTheme(
                nativeThemeMode,
                nativeLanguage,
                streamAnimationsEnabled,
                showReasoning,
                autoFollowOutput,
                colorPalette = nativeColorPalette,
                interfaceStyle = nativeInterfaceStyle,
                appearanceRevision = nativeAppearanceRevision,
                chatBackground = nativeChatBackground,
                chatBackgroundImage = nativeChatBackgroundImage,
                chatBackgroundDim = nativeChatBackgroundDim,
                fixedStreamingViewport = fixedStreamingViewportEnabled,
                showResponseStats = showResponseStats,
                showModelSubtitle = showModelSubtitle,
                showReasoningTitles = showReasoningTitles,
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
                    onFollowUpActionChange = ::setFollowUpSubmitAction,
                    onRemoveQueuedFollowUp = ::removeQueuedFollowUp,
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
                    onCompact = ::requestManualCompaction,
                    onAnswerUserInput = ::answerUserInput,
                    onExecutePendingPlan = ::executePendingPlan,
                    onRevisePendingPlan = ::revisePendingPlan,
                    onCancelPendingPlan = ::cancelPendingPlan,
                    onAnswerApproval = ::answerApproval,
                    onGitAction = ::performGitAction,
                    onSnapshotAction = ::performSnapshotAction,
                    onWorktreeAction = ::performWorktreeAction,
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

    private fun applyNativeStatusBarVisibility(hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
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

    private fun currentBackendConfiguration(
        prefs: android.content.SharedPreferences,
    ): NativeBackendConfiguration = NativeBackendConfigurationResolver.resolve(
        profile = providerStore.active(),
        routeApiPreference = prefs.getBoolean("mihomo_route_api", false),
        mcpRevision = prefs.getLong(NativeMcpConfigStore.REVISION_KEY, 0L),
        mcpFileFingerprint = NativeMcpConfigStore.fileFingerprint(),
    )

    private fun selectedModelConfig(): CodexProviderStore.ModelConfig? {
        val profile = providerStore.active() ?: return null
        val selected = chatState.selectedModel.ifBlank { profile.model }
        return profile.models.firstOrNull { it.id.equals(selected, ignoreCase = true) }
    }

    private fun currentCompactionPolicy(): NativeCompactionPolicy {
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

    private fun activeCompactionTurnKey(): String = currentThreadId.orEmpty() + ":" +
        chatState.currentTurnId.ifBlank { chatState.turnStartedAt.toString() }

    private fun compactionPolicyInput(usage: NativeTurnUsage): NativeCompactionPolicyInput {
        val model = selectedModelConfig()
        val contextWindow = usage.contextWindow.takeIf { it > 0L } ?: model?.contextWindow ?: 0L
        val used = usage.currentContextTokens.takeIf { it > 0L } ?: 0L
        val serverLimit = usage.autoCompactTokenLimit.takeIf { it > 0L }
            ?: model?.autoCompactTokenLimit
            ?: 0L
        return NativeCompactionPolicyInput(
            threadId = currentThreadId.orEmpty(),
            turnId = chatState.currentTurnId.takeIf { it.isNotBlank() }
                ?: chatState.turnStartedAt.takeIf { it > 0L }?.toString(),
            contextWindow = contextWindow,
            usedTokens = used,
            serverAutoCompactTokenLimit = serverLimit,
            usageReliable = usage.contextUsageReliable && used > 0L,
            estimated = usage.estimated,
            turnActive = chatState.phase.active,
            compactionInProgress = chatState.compactionItems.any { !it.isTerminal },
            waitingForUserInput = chatState.pendingUserInputRequest.isNotBlank() || chatState.pendingApprovalRequest.isNotBlank(),
            stopping = chatState.phase == NativeTurnPhase.STOPPING,
        )
    }

    private fun evaluateAutomaticCompaction(usage: NativeTurnUsage) {
        val policyInput = compactionPolicyInput(usage)
        if (!usage.estimated &&
            (policyInput.contextWindow > 0L || policyInput.serverAutoCompactTokenLimit > 0L) &&
            policyInput.usedTokens > 0L && policyInput.usageReliable
        ) {
            lastReliableUsage = usage
        }
        val decision = currentCompactionPolicy().evaluate(policyInput)
        if (!decision.shouldTrigger) {
            decision.skipReason?.let { reason ->
                NativeChatDiagnostics.record(this, "compaction_fallback_skipped_reason", JSONObject()
                    .put("reason", reason.name.lowercase())
                    .put("thread", currentThreadId.orEmpty().take(8)))
            }
            return
        }
        val turnKey = activeCompactionTurnKey()
        if (pendingAutoCompactionTurn == turnKey) {
            NativeChatDiagnostics.record(this, "compaction_duplicate_suppressed", JSONObject().put("turn", turnKey.takeLast(16)))
            return
        }
        pendingAutoCompactionTurn = turnKey
        streamHandler.removeCallbacks(autoCompactionRunnable)
        // Give the server's native auto-compaction event a short lead. A started event cancels
        // this runnable, so the client never races a server request at the same threshold.
        streamHandler.postDelayed(autoCompactionRunnable, 450L)
    }

    private fun requestManualCompaction() {
        requestCompaction(NativeCompactionSource.MANUAL, null)
    }

    private fun requestCompaction(source: NativeCompactionSource, decision: NativeCompactionDecision?) {
        val threadId = currentThreadId ?: return
        if (threadId.isBlank() || bridge == null || chatState.compactionItems.any { !it.isTerminal }) {
            NativeChatDiagnostics.record(this, "compaction_duplicate_suppressed", JSONObject()
                .put("thread", threadId.take(8)).put("source", source.name.lowercase()))
            return
        }
        val requestId = "${source.name.lowercase()}:${threadId.takeLast(8)}:${System.currentTimeMillis()}"
        val item = if (source == NativeCompactionSource.MANUAL) chatState.beginManualCompaction(requestId)
            else chatState.beginAutomaticCompaction(requestId)
        compactionJournalStore.record(item)
        currentCompactionPolicy().markRequested(
            threadId,
            chatState.currentTurnId.takeIf { it.isNotBlank() },
        )
        NativeChatDiagnostics.record(this, "compaction_trigger_source", JSONObject()
            .put("source", decision?.source?.name?.lowercase() ?: source.name.lowercase())
            .put("threshold", decision?.threshold ?: 0L)
            .put("thread", threadId.take(8)))
        bridge?.compactThread(requestId)
    }

    private fun cancelPendingAutoCompaction() {
        pendingAutoCompactionTurn = ""
        streamHandler.removeCallbacks(autoCompactionRunnable)
    }

    private fun startBackend(
        preferConfiguredDefault: Boolean = false,
        preparedConfiguration: NativeBackendConfiguration? = null,
    ) {
        // Every request invalidates an older background catalog write/attach continuation.
        // Catalog writes themselves are serialized, so the newest valid request always wins.
        val generation = ++backendStartGeneration
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val configuration = preparedConfiguration ?: currentBackendConfiguration(prefs)
        val profile = configuration.profile

        backendConfigurationLoaded = true
        pendingBackendConfigurationReload = false
        appliedProviderFingerprint = configuration.fingerprint
        appliedProviderDefaultModel = configuration.defaultModel
        activeProfileId = configuration.profileId

        chatState.modelOptions.clear()
        chatState.modelOptions.addAll(configuration.modelOptions)
        if (profile != null) {
            restoreNativeSelection(prefs, profile.id, profile.model, preferConfiguredDefault)
        } else {
            chatState.selectedModel = ""
            chatState.modelLabel = "默认模型"
            chatState.selectedEffort = "high"
        }

        if (!configuration.hasRuntimeCredentials) {
            if (CodexNativeRuntime.exists()) CodexNativeRuntime.shutdown()
            bridge = null
            chatState.ready = false
            chatState.addError("没有可用的 API 配置，请先返回首页创建并启用配置。")
            return
        }
        checkNotNull(profile)

        val binary = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex")
        if (!binary.canExecute()) {
            if (CodexNativeRuntime.exists()) CodexNativeRuntime.shutdown()
            bridge = null
            chatState.ready = false
            chatState.addError("Codex CLI 尚未安装，请先返回首页完成运行环境安装。")
            return
        }

        chatState.connectionLabel = "正在连接 ${chatState.modelLabel}…"
        chatState.ready = false

        // Native chat owns a dedicated reasoning panel, so request summaries from
        // capable models even if the shared WebUI profile defaults them to off.
        profile.models.forEach { model ->
            if (model.reasoningSummaries && model.defaultReasoningSummary.equals("none", ignoreCase = true)) {
                model.defaultReasoningSummary = "detailed"
            }
        }
        val request = NativeBackendStartRequest(
            generation = generation,
            profile = profile,
            providerFingerprint = configuration.fingerprint,
            routeThroughMihomo = configuration.routeThroughMihomo,
        )
        backendScope.launch {
            // JSON construction, writing and FileDescriptor.sync() stay off the UI thread, but
            // complete before app-server startup reads the catalog.
            backendCatalogPreparer.prepare(request.profile.models)
            if (request.generation != backendStartGeneration || isFinishing || isDestroyed) return@launch
            attachPreparedBackend(request)
        }
    }

    private fun attachPreparedBackend(request: NativeBackendStartRequest) {
        val profile = request.profile
        val retainedRuntime = CodexNativeRuntime.exists()
        val retainedThread = notificationTargetThreadId.takeIf { it.isNotBlank() }
            ?: CodexNativeRuntime.currentThreadId()?.takeIf { it.isNotBlank() }
            ?: currentThreadId?.takeIf { it.isNotBlank() }
        bridge = CodexNativeRuntime.attach(
            this,
            this,
            request.providerFingerprint,
            profile.baseUrl,
            profile.apiKey,
            profile.model,
            profile.apiFormat.takeUnless { it.isBlank() || it == "auto" } ?: "openai_responses",
            request.routeThroughMihomo,
            profile.forwardReasoningContext,
            profile.ultraSubagentLimit,
            profile.normalSubagentLimit,
            profile.ultraTransportEfforts(),
            profile.hasV2Models(),
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
        val configuration = currentBackendConfiguration(prefs)
        if (configuration.fingerprint == appliedProviderFingerprint) {
            pendingBackendConfigurationReload = false
            return
        }
        if (chatState.busy) {
            pendingBackendConfigurationReload = true
            return
        }
        val profile = configuration.profile
        val preferConfiguredDefault = NativeProviderSync.shouldPreferConfiguredDefault(
            activeProfileId,
            appliedProviderDefaultModel,
            configuration.profileId,
            configuration.defaultModel,
        )
        NativeChatDiagnostics.record(this, "provider_configuration_reload", JSONObject()
            .put("previousProfileId", activeProfileId)
            .put("nextProfileId", configuration.profileId)
            .put("preferConfiguredDefault", preferConfiguredDefault)
            .put("models", profile?.models?.size ?: 0))
        startBackend(preferConfiguredDefault, configuration)
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
        resetProtocolTurnState()
        chatState.prepareReplacementTurn()
        startFrameDiagnostics()
        bridge?.editTurn(value, chatState.selectedModel, chatState.selectedEffort, rollbackTurns)
    }

    private fun retryMessage(text: String, preserveRetryStatus: Boolean = false) {
        val displayValue = text.trim()
        if (displayValue.isEmpty() || chatState.busy || !chatState.ready) return
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

    private fun persistDraft(value: String) {
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString("native_chat_draft_v1", value).apply()
    }

    private fun updateDraft(value: String) {
        chatState.input = value
        persistDraft(value)
    }

    private fun captureFollowUp(value: String): NativeQueuedFollowUp = NativeQueuedFollowUp(
        text = value,
        attachments = chatState.attachments.toList(),
        skills = chatState.selectedSkills.toList(),
        model = chatState.selectedModel,
        effort = chatState.selectedEffort,
        mode = chatState.selectedMode,
    )

    private fun attachmentsJson(followUp: NativeQueuedFollowUp): String = JSONArray().also { array ->
        followUp.attachments.forEach { attachment ->
            array.put(JSONObject().put("name", attachment.name).put("path", attachment.path).put("image", attachment.image))
        }
    }.toString()

    private fun skillsJson(followUp: NativeQueuedFollowUp): String = JSONArray().also { array ->
        followUp.skills.forEach { skill ->
            array.put(JSONObject().put("name", skill.name).put("path", skill.path))
        }
    }.toString()

    private fun clearComposerAfterSubmit() {
        updateDraft("")
        chatState.attachments.clear()
        chatState.selectedSkills.clear()
    }

    private fun resetProtocolTurnState() {
        streamHandler.removeCallbacks(flushProtocolEventQueueRunnable)
        flushProtocolEventQueue()
        protocolEventQueue.clear()
        currentCompactionPolicy().reset()
        compactionLifecycleTimeouts.values.forEach(streamHandler::removeCallbacks)
        compactionLifecycleTimeouts.clear()
        protocolReasoningSeen = false
        protocolAssistantSeen = false
        protocolCommandSeen = false
        protocolPlanSeen = false
        protocolToolSeen = false
        protocolSubagentSeen = false
        protocolUsageSeen = false
        protocolTurnCompletedSeen = false
        lastReliableUsage = null
        cancelPendingAutoCompaction()
    }

    private fun queueFollowUp(followUp: NativeQueuedFollowUp, clearComposer: Boolean = true): NativeSubmitResult {
        if (chatState.queuedFollowUps.none { it.id == followUp.id }) chatState.queuedFollowUps.add(followUp)
        if (clearComposer) clearComposerAfterSubmit()
        return NativeSubmitResult(accepted = true, queued = true)
    }

    private fun startNewTurn(followUp: NativeQueuedFollowUp, clearComposer: Boolean = true): NativeSubmitResult {
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
        startFrameDiagnostics()
        bridge?.sendMessage(
            followUp.text,
            followUp.model,
            followUp.effort,
            attachmentsJson(followUp),
            followUp.mode,
            skillsJson(followUp),
        )
        return NativeSubmitResult(accepted = true, messageId = userMessage.id)
    }

    private fun submitSteer(followUp: NativeQueuedFollowUp): NativeSubmitResult {
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

    private fun sendMessage(text: String): NativeSubmitResult? {
        val value = text.trim()
        if ((value.isEmpty() && chatState.attachments.isEmpty()) || !chatState.ready) return null
        val followUp = captureFollowUp(value)
        if (!chatState.busy) return startNewTurn(followUp)
        return when (chatState.followUpSubmitAction) {
            NativeFollowUpSubmitAction.STEER -> submitSteer(followUp)
            NativeFollowUpSubmitAction.QUEUE -> queueFollowUp(followUp)
        }
    }

    private fun setFollowUpSubmitAction(action: NativeFollowUpSubmitAction) {
        chatState.followUpSubmitAction = action
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(NATIVE_FOLLOW_UP_ACTION_PREFERENCE, action.name.lowercase(Locale.ROOT))
            .apply()
    }

    private fun removeQueuedFollowUp(id: String) {
        chatState.queuedFollowUps.removeAll { it.id == id }
    }

    private fun sendNextQueuedFollowUp() {
        if (chatState.busy || !chatState.ready || chatState.queuedFollowUps.isEmpty()) return
        val followUp = chatState.queuedFollowUps.removeAt(0)
        startNewTurn(followUp, clearComposer = false)
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
    private fun pendingUserInputPreferenceKey(threadId: String): String = "native_thread_pending_user_input_v1_$threadId"
    private fun pendingApprovalPreferenceKey(threadId: String): String = "native_thread_pending_approval_v1_$threadId"
    private fun pendingPlanImplementationPreferenceKey(threadId: String): String = "native_thread_pending_plan_v1_$threadId"

    private fun threadAttention(threadId: String): String {
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

    private fun attentionPriority(value: String): Int = when (value) {
        "answer" -> 3
        "approval" -> 2
        "plan", "resume" -> 1
        else -> 0
    }

    private fun taskAttention(threadId: String, state: String): String = threadAttention(threadId).ifBlank {
        if (state == CodexTaskStore.FAILED) "resume" else ""
    }

    private fun syncPendingNotification(threadId: String) {
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

    private fun restoreGoalForThread(threadId: String) {
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
    }

    private fun clearLocalGoal(threadId: String) {
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
        }
    }

    private fun applyGoalState(value: String) {
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
    }

    private fun setGoal(objective: String) {
        val value = objective.trim()
        val threadId = currentThreadId
        if (value.isEmpty() || !chatState.ready || threadId.isNullOrBlank()) return
        chatState.activeGoalObjective = value
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(goalPreferenceKey(threadId), value).putString(goalStatusPreferenceKey(threadId), "active").apply()
        chatState.activeGoalStatus = "active"
        suppressGoalRetryUntilNewTurn = false
        bridge?.setThreadGoal(value)
    }

    private fun requestIdentity(raw: String): String = runCatching {
        JSONObject(raw).opt("requestId")?.toString().orEmpty()
    }.getOrDefault("")

    private fun storePendingUserInput(raw: String) {
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

    private fun clearPendingUserInput(threadId: String, expectedRequestId: String = "") {
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

    private fun scheduleUserInputTimeout(threadId: String, raw: String) {
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

    private fun answerUserInput(answersJson: String) {
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

    private fun handleUserInputResolved(value: String) {
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

    private fun persistPendingPlanImplementation(threadId: String, plan: String) {
        val value = plan.trim()
        if (value.isBlank()) return
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .putString(pendingPlanImplementationPreferenceKey(threadId), value)
            .apply()
        if (currentThreadId == threadId) chatState.pendingPlanImplementation = value
        NativeTaskNotificationManager.notifyEvent(this, threadId, NativeTaskNotificationPolicy.PLAN, value.hashCode().toString(), "")
        refreshConversations()
    }

    private fun clearPendingPlanImplementation(threadId: String) {
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit()
            .remove(pendingPlanImplementationPreferenceKey(threadId))
            .apply()
        if (currentThreadId == threadId) chatState.pendingPlanImplementation = ""
        syncPendingNotification(threadId)
        refreshConversations()
    }

    private fun executePendingPlan() {
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

    private fun revisePendingPlan(feedback: String) {
        val threadId = currentThreadId ?: return
        val value = feedback.trim()
        if (value.isBlank()) return
        clearPendingPlanImplementation(threadId)
        setChatMode("plan")
        sendMessage(value)
    }

    private fun cancelPendingPlan() {
        currentThreadId?.let(::clearPendingPlanImplementation)
    }

    private fun answerApproval(rawRequest: String, decision: String) {
        if (rawRequest.isBlank()) return
        bridge?.respondApprovalRequest(rawRequest, decision)
        approvalThreadId(rawRequest)?.let { clearPendingApproval(it, rawRequest) }
        if (chatState.phase == NativeTurnPhase.WAITING) {
            chatState.phase = NativeTurnPhase.TOOL_RUNNING
            chatState.processingLabel = nativeText(nativeLanguage, "\u6b63\u5728\u7ee7\u7eed\u6267\u884c", "Continuing")
        }
    }

    private fun cancelPendingApproval() {
        val raw = chatState.pendingApprovalRequest
        if (raw.isNotBlank()) bridge?.respondApprovalRequest(raw, "cancel")
        approvalThreadId(raw)?.let { clearPendingApproval(it, raw) }
    }

    private fun approvalThreadId(raw: String): String? = runCatching {
        JSONObject(raw).optJSONObject("params")?.optString("threadId").orEmpty()
    }.getOrDefault("").ifBlank { currentThreadId.orEmpty() }.takeIf { it.isNotBlank() }

    private fun storePendingApproval(raw: String) {
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

    private fun clearPendingApproval(threadId: String, expectedRaw: String = "") {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val stored = prefs.getString(pendingApprovalPreferenceKey(threadId), "").orEmpty()
        if (expectedRaw.isNotBlank() && stored.isNotBlank() && requestIdentity(stored) != requestIdentity(expectedRaw)) return
        prefs.edit().remove(pendingApprovalPreferenceKey(threadId)).apply()
        syncPendingNotification(threadId)
        if (currentThreadId == threadId) chatState.pendingApprovalRequest = ""
        refreshConversations()
    }

    private fun protocolString(item: JSONObject?, vararg keys: String): String {
        if (item == null) return ""
        keys.forEach { key ->
            val value = item.optString(key, "").trim()
            if (value.isNotBlank() && !value.equals("null", true)) return value
        }
        return ""
    }

    private fun protocolLong(payload: JSONObject, key: String): Long {
        val snake = key.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
        return payload.optLong(key, payload.optLong(snake, payload.optLong("${key}Number", 0L)))
    }

    private fun protocolEventThread(payload: JSONObject): String {
        val direct = protocolString(payload, "threadId", "thread_id")
        if (direct.isNotBlank()) return direct
        val turn = payload.optJSONObject("turn")
        val item = payload.optJSONObject("item") ?: payload.optJSONObject("details")
        return protocolString(turn, "threadId", "thread_id")
            .ifBlank { protocolString(item, "threadId", "thread_id", "senderThreadId", "sender_thread_id") }
    }

    private fun protocolEventTurn(payload: JSONObject): String? {
        val direct = protocolString(payload, "turnId", "turn_id")
        if (direct.isNotBlank()) return direct
        val turn = payload.optJSONObject("turn")
        val item = payload.optJSONObject("item") ?: payload.optJSONObject("details")
        return protocolString(turn, "id", "turnId", "turn_id")
            .ifBlank { protocolString(item, "turnId", "turn_id") }
            .takeIf { it.isNotBlank() }
    }

    private fun protocolCompactionSource(payload: JSONObject?, item: JSONObject?): NativeCompactionSource? {
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

    private fun isFinalAssistantItem(item: JSONObject?): Boolean {
        if (item == null) return false
        val phase = protocolString(item, "phase", "itemPhase", "item_phase")
            .replace("-", "_").replace(" ", "_").lowercase(Locale.ROOT)
        return phase in setOf("final_answer", "finalanswer", "final", "answer") ||
            item.optBoolean("final", false) || item.optBoolean("isFinal", false)
    }

    private fun enqueueProtocolEvent(event: NativeProtocolEvent, immediate: Boolean = false) {
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

    private fun flushProtocolEventQueue() {
        protocolEventQueue.drain().forEach(chatState::acceptNormalizedProtocolEvent)
    }

    /** Accept lifecycle recordings produced by retained/older bridges without duplicating their
     * protocol-specific parsing in the Compose state. */
    private fun handleDecodedLifecycleEvent(event: NativeProtocolEvent) {
        if (event.threadId.isNotBlank() && currentThreadId != null && event.threadId != currentThreadId) return
        event.turnId?.takeIf { it.isNotBlank() }?.let { chatState.currentTurnId = it }
        when (event) {
            is NativeProtocolEvent.CompactionStarted -> {
                cancelPendingAutoCompaction()
                enqueueProtocolEvent(event, immediate = true)
                chatState.compactionItems.firstOrNull { it.serverItemId == event.itemId || it.id == event.itemId }
                    ?.let(compactionJournalStore::record)
                currentCompactionPolicy().markStarted(event.threadId, event.turnId)
                scheduleCompactionLifecycleTimeout(event)
            }
            is NativeProtocolEvent.CompactionCompleted,
            is NativeProtocolEvent.CompactionFailed -> {
                enqueueProtocolEvent(event, immediate = true)
                chatState.compactionItems.lastOrNull { event.itemId.isNullOrBlank() || it.serverItemId == event.itemId || it.id == event.itemId }
                    ?.let { item ->
                        compactionJournalStore.record(item)
                        if (item.status == NativeCompactionStatus.COMPLETED) {
                            currentCompactionPolicy().markCompleted(
                                event.threadId,
                                event.turnId,
                                lastReliableUsage?.currentContextTokens ?: 0L,
                                lastReliableUsage?.contextWindow ?: 0L,
                            )
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
                    is NativeProtocolEvent.TurnCompleted -> protocolTurnCompletedSeen = true
                    else -> Unit
                }
                enqueueProtocolEvent(event, immediate = true)
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

    private fun scheduleCompactionLifecycleTimeout(event: NativeProtocolEvent.CompactionStarted) {
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

    private fun cancelCompactionLifecycleTimeout(event: NativeProtocolEvent) {
        val timeoutKey = event.itemId ?: "${event.threadId}:${event.turnId.orEmpty()}"
        compactionLifecycleTimeouts.remove(timeoutKey)?.let(streamHandler::removeCallbacks)
    }

    private fun canonicalProtocolKind(raw: String): String {
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
            "turncompleted", "turncomplete" -> "turnCompleted"
            "error" -> "error"
            else -> raw
        }
    }

    private fun handleProtocolEvent(raw: String) {
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
        // Token-usage and lifecycle notifications can be the first event of a turn. Capture the
        // identity before evaluating automatic compaction, otherwise a synthetic marker is stored
        // without turnId and cannot be consumed when the server sends item/started later.
        if (!turnId.isNullOrBlank()) chatState.currentTurnId = turnId
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
                cancelPendingAutoCompaction()
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
                chatState.currentTurnId = turnId.orEmpty().ifBlank { chatState.currentTurnId }
                enqueueProtocolEvent(event, immediate = true)
                val visual = chatState.compactionItems.firstOrNull { it.serverItemId == itemId || it.id == itemId }
                visual?.let { compactionJournalStore.record(it) }
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
                    compactionJournalStore.record(it)
                    if (it.status == NativeCompactionStatus.COMPLETED) {
                        currentCompactionPolicy().markCompleted(threadId, turnId, lastReliableUsage?.currentContextTokens ?: 0L, lastReliableUsage?.contextWindow ?: 0L)
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
                    agentThreadId = protocolString(item, "agentThreadId", "agent_thread_id"),
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
            "turnCompleted" -> enqueueProtocolEvent(
                NativeProtocolEvent.TurnCompleted(
                    threadId = threadId,
                    turnId = turnId,
                    itemId = itemId,
                    failed = item?.has("error") == true && !item.isNull("error"),
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
        if (kind == "turnCompleted") protocolTurnCompletedSeen = true
    }

    private fun handleProtocolDelta(raw: String) {
        NativeProtocolEventDecoder.decodeDeltas(raw, currentThreadId.orEmpty()).forEach { event ->
            if (currentThreadId != null && event.threadId.isNotBlank() && event.threadId != currentThreadId) return@forEach
            event.turnId?.takeIf { it.isNotBlank() }?.let { chatState.currentTurnId = it }
            when (event) {
                is NativeProtocolEvent.ReasoningDelta -> protocolReasoningSeen = true
                is NativeProtocolEvent.AssistantDelta -> protocolAssistantSeen = true
                is NativeProtocolEvent.PlanDelta -> protocolPlanSeen = true
                else -> Unit
            }
            enqueueProtocolEvent(event)
        }
    }

    private fun handleCommandDeltaV2(raw: String) {
        NativeProtocolEventDecoder.decodeCommandOutputs(raw, currentThreadId.orEmpty()).forEach { event ->
            if (currentThreadId != null && event.threadId.isNotBlank() && event.threadId != currentThreadId) return@forEach
            event.turnId?.takeIf { it.isNotBlank() }?.let { chatState.currentTurnId = it }
            protocolCommandSeen = true
            enqueueProtocolEvent(event)
        }
    }

    private fun handleCompactionRpcResult(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val requestId = protocolString(payload, "requestId", "request_id").takeIf { it.isNotBlank() }
        val status = protocolString(payload, "status", "state").lowercase(Locale.ROOT)
        val success = if (payload.has("success")) payload.optBoolean("success", false)
        else status in setOf("completed", "complete", "success", "succeeded", "ok")
        val cancelled = status in setOf("cancelled", "canceled", "cancel", "aborted")
        val threadId = protocolString(payload, "threadId", "thread_id").ifBlank { currentThreadId.orEmpty() }
        val complete: () -> Unit = {
            val current = chatState.compactionItems.firstOrNull { it.requestId == requestId }
            if (!(success && current?.status == NativeCompactionStatus.RUNNING)) {
                val updated = chatState.completeManualCompactionRpc(
                    requestId,
                    success = success && !cancelled,
                    cancelled = cancelled,
                    error = protocolString(payload, "error", "message"),
                    threadId = threadId,
                )
                updated?.let(compactionJournalStore::record)
                if (success && !cancelled && updated != null) {
                    // Older servers return only the RPC result. Mark the policy as completed now
                    // so a later reliable low-usage update can release hysteresis even when no
                    // item/started or item/completed notification ever arrives.
                    currentCompactionPolicy().markCompleted(
                        threadId,
                        chatState.currentTurnId.takeIf { it.isNotBlank() },
                        lastReliableUsage?.currentContextTokens ?: 0L,
                        lastReliableUsage?.contextWindow ?: 0L,
                    )
                }
            }
        }
        if (!success || cancelled) complete() else streamHandler.postDelayed(complete, 900L)
        if (!success || cancelled) currentCompactionPolicy().markFailed(threadId, chatState.currentTurnId)
    }

    private fun configuredProjectPath(): String {
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        val configured = prefs.getString("custom_project_root", "").orEmpty()
        return configured.takeIf { prefs.getBoolean("custom_project_root_enabled", true) && File(it).isDirectory } ?: ""
    }

    private fun performGitAction(action: String, value: String) {
        val project = chatState.projectPath
        val routeThreadId = currentThreadId
        if (project.isBlank()) {
            chatState.gitError = nativeText(nativeLanguage, "当前对话没有绑定项目目录", "This conversation has no project directory")
            return
        }
        if (action == "diff") {
            if (value.isBlank() || value in chatState.gitDiffLoading) return
            chatState.gitDiffLoading.add(value)
            Thread({
                val unstaged = runGit(project, listOf("diff", "--no-ext-diff", "--unified=3", "--", value))
                val staged = runGit(project, listOf("diff", "--cached", "--no-ext-diff", "--unified=3", "--", value))
                var unstagedText = unstaged.second.takeIf { unstaged.first == 0 }.orEmpty()
                if (unstagedText.isBlank() && staged.second.isBlank()) {
                    val untracked = runGit(project, listOf("diff", "--no-index", "--unified=3", "--", "/dev/null", value))
                    if (untracked.first in setOf(0, 1)) unstagedText = untracked.second
                }
                val snapshot = NativeGitWorkflow.diffSnapshot(
                    unstagedText,
                    staged.second.takeIf { staged.first == 0 }.orEmpty(),
                    listOfNotNull(
                        unstaged.second.takeIf { unstaged.first != 0 },
                        staged.second.takeIf { staged.first != 0 },
                    ).joinToString("\n").trim(),
                )
                runOnUiThread {
                    if (currentThreadId != routeThreadId || chatState.projectPath != project) return@runOnUiThread
                    chatState.gitDiffs[value] = snapshot
                    chatState.gitDiffLoading.remove(value)
                }
            }, "NativeGitDiff").start()
            return
        }
        if (chatState.gitBusy) return
        chatState.gitBusy = true
        chatState.gitError = ""
        chatState.gitNotice = ""
        Thread({
            val result = when (action) {
                "refresh" -> 0 to ""
                "fetch" -> runGit(project, listOf("fetch", "--prune", "--all"), mapOf("GIT_TERMINAL_PROMPT" to "0"))
                "push" -> pushCurrentBranch(project)
                "stage" -> runGit(project, listOf("add", "--", value))
                "unstage" -> {
                    val restore = runGit(project, listOf("restore", "--staged", "--", value))
                    if (restore.first == 0) restore else runGit(project, listOf("reset", "HEAD", "--", value))
                }
                "commit" -> if (value.trim().isBlank()) -1 to nativeText(nativeLanguage, "请输入提交说明", "Enter a commit message")
                    else runGit(project, listOf("commit", "-m", value.trim()))
                else -> -1 to nativeText(nativeLanguage, "未知 Git 操作", "Unknown Git action")
            }
            val status = loadGitSnapshot(project)
            runOnUiThread {
                if (currentThreadId != routeThreadId || chatState.projectPath != project) return@runOnUiThread
                chatState.gitSnapshot = status
                chatState.gitBusy = false
                chatState.gitDiffs.clear()
                chatState.gitDiffLoading.clear()
                if (result.first == 0) {
                    chatState.gitError = ""
                    chatState.gitNotice = when (action) {
                        "stage" -> nativeText(nativeLanguage, "已暂存 $value", "Staged $value")
                        "unstage" -> nativeText(nativeLanguage, "已取消暂存 $value", "Unstaged $value")
                        "commit" -> result.second.lineSequence().firstOrNull().orEmpty().ifBlank { nativeText(nativeLanguage, "提交成功", "Commit created") }
                        "fetch" -> nativeText(nativeLanguage, "远程引用已更新", "Remote references updated")
                        "push" -> result.second.lineSequence().lastOrNull { it.isNotBlank() }.orEmpty().ifBlank { nativeText(nativeLanguage, "分支已推送", "Branch pushed") }
                        else -> ""
                    }
                } else {
                    chatState.gitError = result.second.trim()
                    chatState.gitNotice = ""
                }
            }
        }, "NativeGitWorkflow").start()
    }

    private fun loadGitSnapshot(project: String): String {
        val git = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "git")
        if (!git.isFile) return NativeGitWorkflow.unavailable(project, nativeText(nativeLanguage, "尚未安装 Git", "Git is not installed"))
        val result = runGit(project, listOf("-c", "core.quotepath=false", "status", "--porcelain=v1", "--branch", "--untracked-files=all"))
        if (result.first != 0) return NativeGitWorkflow.unavailable(project, result.second.trim().ifBlank { nativeText(nativeLanguage, "这里不是 Git 仓库", "This is not a Git repository") })
        val head = runGit(project, listOf("rev-parse", "--verify", "HEAD"))
        val remotes = runGit(project, listOf("remote", "-v"))
        val history = if (head.first == 0) runGit(project, listOf(
            "log", "-n", "30", "--date-order",
            "--pretty=format:%H%x1f%h%x1f%an%x1f%at%x1f%s%x1f%D%x1e",
        )) else 0 to ""
        return NativeGitRemoteProtocol.enrichSnapshot(
            NativeGitWorkflow.parse(project, result.second),
            NativeGitRemoteProtocol.parseRemotes(remotes.second.takeIf { remotes.first == 0 }.orEmpty()),
            NativeGitRemoteProtocol.parseHistory(history.second.takeIf { history.first == 0 }.orEmpty()),
            head.first == 0,
        )
    }

    private fun pushCurrentBranch(project: String): Pair<Int, String> {
        val branch = runGit(project, listOf("symbolic-ref", "--quiet", "--short", "HEAD"))
        val branchName = branch.second.trim()
        if (branch.first != 0 || branchName.isBlank()) return -1 to nativeText(nativeLanguage, "分离 HEAD 不能直接推送", "A detached HEAD cannot be pushed directly")
        val remotesResult = runGit(project, listOf("remote", "-v"))
        val remotes = NativeGitRemoteProtocol.parseRemotes(remotesResult.second.takeIf { remotesResult.first == 0 }.orEmpty())
        val upstream = runGit(project, listOf("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}"))
        val upstreamName = upstream.second.trim().takeIf { upstream.first == 0 }.orEmpty()
        val remoteName = upstreamName.substringBefore('/', "").ifBlank {
            remotes.firstOrNull { it.name == "origin" }?.name ?: remotes.firstOrNull()?.name.orEmpty()
        }
        if (remoteName.isBlank()) return -1 to nativeText(nativeLanguage, "仓库没有可推送的远程地址", "The repository has no remote to push to")
        val arguments = if (upstreamName.isBlank()) {
            listOf("push", "-u", remoteName, branchName)
        } else {
            val remoteBranch = upstreamName.substringAfter('/', branchName)
            listOf("push", remoteName, "HEAD:refs/heads/$remoteBranch")
        }
        return runGit(project, arguments, mapOf("GIT_TERMINAL_PROMPT" to "0"))
    }

    private fun runGit(project: String, arguments: List<String>): Pair<Int, String> = runCatching {
        runGit(project, arguments, emptyMap())
    }.getOrElse { -1 to (it.message ?: it.javaClass.simpleName) }

    private fun runGit(project: String, arguments: List<String>, environment: Map<String, String>): Pair<Int, String> = runCatching {
        val directory = File(project).canonicalFile
        require(directory.isDirectory) { "Project directory is unavailable" }
        val command = mutableListOf(File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "git").absolutePath)
        command.addAll(arguments)
        val process = ProcessBuilder(command)
            .directory(directory)
            .redirectErrorStream(true)
            .also { builder ->
                builder.environment()["PATH"] = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin:/system/xbin"
                builder.environment()["HOME"] = TermuxConstants.TERMUX_HOME_DIR_PATH
                builder.environment()["PREFIX"] = TermuxConstants.TERMUX_PREFIX_DIR_PATH
                builder.environment()["TMPDIR"] = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH
                builder.environment().putAll(environment)
            }
            .start()
        val collected = StringBuilder()
        val outputReader = Thread({
            process.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8_192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    synchronized(collected) {
                        val remaining = 240_000 - collected.length
                        if (remaining > 0) collected.append(buffer, 0, minOf(count, remaining))
                    }
                }
            }
        }, "NativeGitOutput").apply { isDaemon = true; start() }
        val completed = waitForProcess(process, 90_000L)
        if (!completed) {
            stopProcess(process)
            outputReader.join(2_000L)
            return@runCatching -1 to nativeText(nativeLanguage, "Git 操作超时", "Git operation timed out")
        }
        outputReader.join(2_000L)
        val output = synchronized(collected) { collected.toString() }
        process.exitValue() to output
    }.getOrElse { -1 to (it.message ?: it.javaClass.simpleName) }

    private fun waitForProcess(process: Process, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs.coerceAtLeast(0L) * 1_000_000L
        while (true) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                val remainingNanos = deadline - System.nanoTime()
                if (remainingNanos <= 0L) return false
                try {
                    Thread.sleep(minOf(50L, (remainingNanos / 1_000_000L).coerceAtLeast(1L)))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
    }

    private fun stopProcess(process: Process) {
        runCatching { process.destroy() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { process.destroyForcibly() }
        }
    }

    private fun captureWorkspaceCommit(project: String): Pair<String, String> {
        val checkpointDir = File(cacheDir, "native-workspace-checkpoints").apply { mkdirs() }
        val indexFile = File.createTempFile("index-", ".tmp", checkpointDir).apply { delete() }
        val environment = mapOf(
            "GIT_INDEX_FILE" to indexFile.absolutePath,
            "GIT_AUTHOR_NAME" to "Sillage Checkpoint",
            "GIT_AUTHOR_EMAIL" to "checkpoint@sillage.local",
            "GIT_COMMITTER_NAME" to "Sillage Checkpoint",
            "GIT_COMMITTER_EMAIL" to "checkpoint@sillage.local",
        )
        return try {
            val head = runGit(project, listOf("rev-parse", "--verify", "HEAD"))
            val headCommit = head.second.trim().takeIf { head.first == 0 && it.matches(Regex("[0-9a-fA-F]{40,64}")) }.orEmpty()
            val readTree = if (headCommit.isBlank()) runGit(project, listOf("read-tree", "--empty"), environment)
                else runGit(project, listOf("read-tree", headCommit), environment)
            if (readTree.first != 0) return "" to readTree.second
            val add = runGit(project, listOf("-c", "core.quotepath=false", "add", "-A", "--", "."), environment)
            if (add.first != 0) return "" to add.second
            val tree = runGit(project, listOf("write-tree"), environment)
            if (tree.first != 0) return "" to tree.second
            val commitArgs = mutableListOf("commit-tree", tree.second.trim(), "-m", "Sillage workspace checkpoint")
            if (headCommit.isNotBlank()) commitArgs.addAll(listOf("-p", headCommit))
            val commit = runGit(project, commitArgs, environment)
            if (commit.first != 0) "" to commit.second else commit.second.trim() to ""
        } finally {
            indexFile.delete()
            File(indexFile.absolutePath + ".lock").delete()
        }
    }

    private fun createWorkspaceSnapshot(
        threadId: String,
        project: String,
        label: String,
        automatic: Boolean,
    ): Pair<NativeWorkspaceSnapshot?, String> {
        val captured = captureWorkspaceCommit(project)
        if (captured.first.isBlank()) return null to captured.second.ifBlank { nativeText(nativeLanguage, "\u65e0\u6cd5\u521b\u5efa\u5de5\u4f5c\u533a\u5feb\u7167", "Unable to create workspace snapshot") }
        val id = UUID.randomUUID().toString()
        val safeThread = threadId.replace(Regex("[^A-Za-z0-9._-]"), "-")
        val ref = "refs/fcode/checkpoints/$safeThread/$id"
        val updateRef = runGit(project, listOf("update-ref", ref, captured.first))
        if (updateRef.first != 0) return null to updateRef.second
        val item = NativeWorkspaceSnapshot(id, threadId, project, captured.first, ref, label, System.currentTimeMillis(), automatic)
        val previous = NativeWorkspaceSnapshotStore.load(this, threadId)
        val updated = NativeWorkspaceSnapshotStore.add(this, item)
        previous.filter { old -> updated.none { it.id == old.id } && old.ref.isNotBlank() }
            .forEach { old -> runGit(project, listOf("update-ref", "-d", old.ref)) }
        return item to ""
    }

    private fun performSnapshotAction(action: String, rawValue: String) {
        val threadId = currentThreadId ?: return
        val project = chatState.projectPath
        if (project.isBlank()) {
            chatState.workspaceSnapshotError = nativeText(nativeLanguage, "\u5f53\u524d\u5bf9\u8bdd\u6ca1\u6709\u7ed1\u5b9a\u9879\u76ee\u76ee\u5f55", "This conversation has no project directory")
            return
        }
        if (action == "refresh") {
            chatState.workspaceSnapshots.clear()
            chatState.workspaceSnapshots.addAll(NativeWorkspaceSnapshotStore.load(this, threadId))
            return
        }
        if (action == "clearPreview") {
            chatState.workspaceSnapshotPreview = ""
            chatState.workspaceSnapshotDiffs.clear()
            chatState.workspaceSnapshotDiffLoading.clear()
            return
        }
        if (action == "diff") {
            val request = runCatching { JSONObject(rawValue) }.getOrNull() ?: return
            val snapshotId = request.optString("snapshotId")
            val path = request.optString("path")
            val preview = runCatching { JSONObject(chatState.workspaceSnapshotPreview) }.getOrNull() ?: return
            val snapshot = chatState.workspaceSnapshots.firstOrNull { it.id == snapshotId } ?: return
            val currentCommit = preview.optString("currentCommit")
            val key = "$snapshotId|$path"
            if (path.isBlank() || currentCommit.isBlank() || key in chatState.workspaceSnapshotDiffLoading) return
            chatState.workspaceSnapshotDiffLoading.add(key)
            Thread({
                val diff = runGit(project, listOf("diff", "--no-ext-diff", "--unified=3", snapshot.commit, currentCommit, "--", path))
                runOnUiThread {
                    if (currentThreadId != threadId || chatState.projectPath != project) return@runOnUiThread
                    chatState.workspaceSnapshotDiffs[key] = if (diff.first == 0) diff.second else diff.second.ifBlank { "Unable to load diff" }
                    chatState.workspaceSnapshotDiffLoading.remove(key)
                }
            }, "NativeSnapshotDiff").start()
            return
        }
        if (chatState.workspaceSnapshotBusy) return
        chatState.workspaceSnapshotBusy = true
        chatState.workspaceSnapshotError = ""
        chatState.workspaceSnapshotNotice = ""
        Thread({
            var error = ""
            var notice = ""
            var preview = chatState.workspaceSnapshotPreview
            val failure = runCatching { when (action) {
                "create" -> {
                    val label = rawValue.trim().ifBlank { nativeText(nativeLanguage, "\u624b\u52a8\u5feb\u7167", "Manual snapshot") }
                    val result = createWorkspaceSnapshot(threadId, project, label, false)
                    error = result.second
                    if (result.first != null) notice = nativeText(nativeLanguage, "\u5df2\u521b\u5efa\u975e\u7834\u574f\u6027\u5feb\u7167", "Non-destructive snapshot created")
                }
                "preview" -> {
                    val snapshot = NativeWorkspaceSnapshotStore.load(this, threadId).firstOrNull { it.id == rawValue }
                    if (snapshot == null) error = nativeText(nativeLanguage, "\u5feb\u7167\u5df2\u4e0d\u5b58\u5728", "Snapshot no longer exists")
                    else if (!runCatching { File(snapshot.projectPath).canonicalPath == File(project).canonicalPath }.getOrDefault(false)) {
                        error = nativeText(nativeLanguage, "\u5feb\u7167\u5c5e\u4e8e\u53e6\u4e00\u4e2a\u9879\u76ee\u76ee\u5f55", "This snapshot belongs to another project directory")
                    }
                    else {
                        val current = captureWorkspaceCommit(project)
                        if (current.first.isBlank()) error = current.second
                        else {
                            val names = runGit(project, listOf("diff", "--name-status", "--find-renames", snapshot.commit, current.first, "--"))
                            val stats = runGit(project, listOf("diff", "--numstat", snapshot.commit, current.first, "--"))
                            if (names.first != 0) error = names.second
                            else preview = NativeWorkspaceSnapshotPreview.build(snapshot.id, current.first, names.second, stats.second)
                        }
                    }
                }
                "delete" -> {
                    val snapshot = NativeWorkspaceSnapshotStore.load(this, threadId).firstOrNull { it.id == rawValue }
                    if (snapshot != null && snapshot.ref.isNotBlank()) {
                        val deleted = runGit(snapshot.projectPath.ifBlank { project }, listOf("update-ref", "-d", snapshot.ref))
                        if (deleted.first != 0) error = deleted.second
                    }
                    if (error.isBlank()) {
                        NativeWorkspaceSnapshotStore.remove(this, threadId, rawValue)
                        preview = ""
                        notice = nativeText(nativeLanguage, "\u5feb\u7167\u5df2\u5220\u9664", "Snapshot deleted")
                    }
                }
                "restore" -> {
                    val request = runCatching { JSONObject(rawValue) }.getOrNull()
                    val snapshot = request?.optString("snapshotId")?.let { id -> NativeWorkspaceSnapshotStore.load(this, threadId).firstOrNull { it.id == id } }
                    val path = request?.optString("path").orEmpty()
                    if (snapshot == null || path.isBlank()) error = nativeText(nativeLanguage, "\u65e0\u6548\u7684\u6062\u590d\u8bf7\u6c42", "Invalid restore request")
                    else if (!runCatching { File(snapshot.projectPath).canonicalPath == File(project).canonicalPath }.getOrDefault(false)) {
                        error = nativeText(nativeLanguage, "\u4e0d\u80fd\u5c06\u5176\u4ed6\u9879\u76ee\u7684\u5feb\u7167\u6062\u590d\u5230\u5f53\u524d\u5de5\u4f5c\u533a", "A snapshot from another project cannot be restored here")
                    }
                    else {
                        val safety = createWorkspaceSnapshot(threadId, project, nativeText(nativeLanguage, "\u6062\u590d $path \u524d\u7684\u81ea\u52a8\u5907\u4efd", "Automatic backup before restoring $path"), true)
                        if (safety.first == null) error = safety.second
                        else {
                            val restored = runGit(project, listOf("restore", "--source=${snapshot.commit}", "--worktree", "--", path))
                            if (restored.first != 0) error = restored.second
                            else {
                                preview = ""
                                notice = nativeText(nativeLanguage, "\u5df2\u6062\u590d $path\uff0c\u5e76\u521b\u5efa\u4e86\u6062\u590d\u524d\u5907\u4efd", "Restored $path and created a pre-restore backup")
                            }
                        }
                    }
                }
                else -> error = nativeText(nativeLanguage, "\u672a\u77e5\u5feb\u7167\u64cd\u4f5c", "Unknown snapshot action")
            } }.exceptionOrNull()
            if (failure != null) error = failure.message ?: failure.javaClass.simpleName
            val snapshots = NativeWorkspaceSnapshotStore.load(this, threadId)
            runOnUiThread {
                if (currentThreadId != threadId || chatState.projectPath != project) return@runOnUiThread
                chatState.workspaceSnapshotBusy = false
                chatState.workspaceSnapshotError = error.trim()
                chatState.workspaceSnapshotNotice = notice
                chatState.workspaceSnapshotPreview = preview
                chatState.workspaceSnapshotDiffs.clear()
                chatState.workspaceSnapshotDiffLoading.clear()
                chatState.workspaceSnapshots.clear()
                chatState.workspaceSnapshots.addAll(snapshots)
                if (action == "restore" && error.isBlank()) performGitAction("refresh", "")
            }
        }, "NativeWorkspaceSnapshot").start()
    }

    private fun loadWorktrees(project: String): Pair<List<NativeWorktreeEntry>, String> {
        val listed = runGit(project, listOf("worktree", "list", "--porcelain"))
        if (listed.first != 0) return emptyList<NativeWorktreeEntry>() to listed.second
        val entries = NativeWorktreeProtocol.parse(listed.second).map { entry ->
            val status = runGit(entry.path, listOf("status", "--porcelain=v1", "--untracked-files=all"))
            entry.copy(dirty = status.first != 0 || status.second.isNotBlank())
        }
        return entries to ""
    }

    private fun samePath(left: String, right: String): Boolean = runCatching {
        File(left).canonicalPath == File(right).canonicalPath
    }.getOrDefault(false)

    private fun pathInside(path: String, directory: String): Boolean = runCatching {
        val child = File(path).canonicalFile
        val parent = File(directory).canonicalFile
        child == parent || child.path.startsWith(parent.path.trimEnd(File.separatorChar) + File.separator)
    }.getOrDefault(false)

    private fun performWorktreeAction(action: String, rawValue: String) {
        val project = chatState.projectPath
        val routeThread = currentThreadId
        val referencedProjects = chatState.conversations.map { it.projectPath }.filter { it.isNotBlank() }
        if (project.isBlank()) {
            chatState.worktreeError = nativeText(nativeLanguage, "\u5f53\u524d\u5bf9\u8bdd\u6ca1\u6709\u7ed1\u5b9a\u9879\u76ee\u76ee\u5f55", "This conversation has no project directory")
            return
        }
        if (action == "clearPreview") {
            chatState.worktreeMergePreview = ""
            return
        }
        if (action == "clearPrHandoff") {
            chatState.worktreePrHandoff = ""
            return
        }
        if (action == "open") {
            val target = rawValue.trim()
            if (target.isNotBlank() && File(target).isDirectory) newConversationAtProject(target)
            return
        }
        if (chatState.worktreeBusy) return
        chatState.worktreeBusy = true
        chatState.worktreeError = ""
        chatState.worktreeNotice = ""
        Thread({
            var error = ""
            var notice = ""
            var preview = chatState.worktreeMergePreview
            var prHandoff = chatState.worktreePrHandoff
            var openProject = ""
            val failure = runCatching {
                val initial = loadWorktrees(project)
                if (initial.second.isNotBlank()) {
                    error = initial.second
                    return@runCatching
                }
                val entries = initial.first
                val main = entries.firstOrNull()
                when (action) {
                    "refresh" -> Unit
                    "create" -> {
                        if (main == null) { error = nativeText(nativeLanguage, "\u65e0\u6cd5\u786e\u5b9a Git \u4e3b\u5de5\u4f5c\u533a", "Unable to resolve the main Git worktree"); return@runCatching }
                        val branch = NativeWorktreeProtocol.normalizeBranch(rawValue, System.currentTimeMillis())
                        val validBranch = runGit(main.path, listOf("check-ref-format", "--branch", branch))
                        if (validBranch.first != 0) { error = validBranch.second; return@runCatching }
                        val repoName = File(main.path).name.ifBlank { "repository" }
                        val base = File(TermuxConstants.TERMUX_HOME_DIR, ".fcode/worktrees/$repoName").apply { mkdirs() }
                        var target = File(base, NativeWorktreeProtocol.pathSlug(branch))
                        if (target.exists()) target = File(base, NativeWorktreeProtocol.pathSlug(branch) + "-" + System.currentTimeMillis())
                        val exists = runGit(main.path, listOf("show-ref", "--verify", "--quiet", "refs/heads/$branch")).first == 0
                        val command = if (exists) listOf("worktree", "add", target.absolutePath, branch)
                            else listOf("worktree", "add", "-b", branch, target.absolutePath, "HEAD")
                        val created = runGit(main.path, command)
                        if (created.first != 0) error = created.second
                        else {
                            openProject = target.absolutePath
                            notice = nativeText(nativeLanguage, "\u5df2\u521b\u5efa\u9694\u79bb\u5de5\u4f5c\u533a $branch", "Created isolated worktree $branch")
                        }
                    }
                    "previewMerge" -> {
                        val source = entries.firstOrNull { samePath(it.path, rawValue) }
                        if (main == null || source == null || samePath(source.path, main.path) || source.branch.isBlank() || main.branch.isBlank()) {
                            error = nativeText(nativeLanguage, "\u65e0\u6cd5\u9884\u89c8\u8be5 worktree \u7684\u5408\u5e76", "Unable to preview merge for this worktree")
                        } else if (main.dirty || source.dirty) {
                            error = nativeText(nativeLanguage, "\u5408\u5e76\u524d\u4e3b\u5de5\u4f5c\u533a\u548c\u9694\u79bb\u5de5\u4f5c\u533a\u90fd\u5fc5\u987b\u5e72\u51c0", "Both the main and isolated worktrees must be clean before merging")
                        } else {
                            val commits = runGit(main.path, listOf("log", "--oneline", "${main.branch}..${source.branch}", "--"))
                            val stat = runGit(main.path, listOf("diff", "--stat", "${main.branch}...${source.branch}", "--"))
                            preview = JSONObject()
                                .put("sourcePath", source.path).put("sourceBranch", source.branch)
                                .put("targetPath", main.path).put("targetBranch", main.branch)
                                .put("commits", commits.second).put("stat", stat.second)
                                .put("canMerge", commits.first == 0 && commits.second.isNotBlank())
                                .toString()
                        }
                    }
                    "preparePr" -> {
                        val source = entries.firstOrNull { samePath(it.path, rawValue) }
                        if (main == null || source == null || samePath(source.path, main.path) || source.branch.isBlank() || main.branch.isBlank()) {
                            error = nativeText(nativeLanguage, "无法为该 worktree 生成 PR 交接信息", "Unable to generate a PR handoff for this worktree")
                        } else {
                            val delta = runGit(main.path, listOf("rev-list", "--left-right", "--count", "${main.branch}...${source.branch}"))
                            if (delta.first != 0) {
                                error = delta.second
                            } else {
                                val counts = delta.second.trim().split(Regex("\\s+")).mapNotNull(String::toIntOrNull)
                                val behind = counts.getOrElse(0) { 0 }
                                val ahead = counts.getOrElse(1) { 0 }
                                val commits = runGit(main.path, listOf("log", "--reverse", "--pretty=format:- %h %s", "${main.branch}..${source.branch}", "--"))
                                val stat = runGit(main.path, listOf("diff", "--stat", "${main.branch}...${source.branch}", "--"))
                                val files = runGit(main.path, listOf("-c", "core.quotepath=false", "diff", "--name-status", "${main.branch}...${source.branch}", "--"))
                                val remotesResult = runGit(source.path, listOf("remote", "-v"))
                                val remotes = NativeGitRemoteProtocol.parseRemotes(remotesResult.second.takeIf { remotesResult.first == 0 }.orEmpty())
                                val upstream = runGit(source.path, listOf("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}"))
                                val upstreamRemote = upstream.second.trim().takeIf { upstream.first == 0 }.orEmpty().substringBefore('/', "")
                                val remote = remotes.firstOrNull { it.name == upstreamRemote }
                                    ?: remotes.firstOrNull { it.name == "origin" }
                                    ?: remotes.firstOrNull()
                                prHandoff = NativeGitRemoteProtocol.buildHandoff(
                                    source.path, source.branch, main.branch,
                                    remote?.name.orEmpty(), remote?.pushUrl.orEmpty(),
                                    ahead, behind,
                                    commits.second.takeIf { commits.first == 0 }.orEmpty(),
                                    stat.second.takeIf { stat.first == 0 }.orEmpty(),
                                    files.second.takeIf { files.first == 0 }.orEmpty(),
                                    source.dirty,
                                )
                                preview = ""
                                notice = nativeText(nativeLanguage, "已生成 ${source.branch} 的 PR 交接信息", "PR handoff generated for ${source.branch}")
                            }
                        }
                    }
                    "merge" -> {
                        val request = runCatching { JSONObject(rawValue) }.getOrNull()
                        val sourcePath = request?.optString("sourcePath").orEmpty()
                        val targetPath = request?.optString("targetPath").orEmpty()
                        val source = entries.firstOrNull { samePath(it.path, sourcePath) }
                        val target = entries.firstOrNull { samePath(it.path, targetPath) }
                        if (source == null || target == null || source.branch.isBlank() || source.dirty || target.dirty) {
                            error = nativeText(nativeLanguage, "worktree \u72b6\u6001\u5df2\u53d8\u5316\uff0c\u8bf7\u5237\u65b0\u540e\u91cd\u8bd5", "Worktree state changed; refresh and try again")
                        } else {
                            val threadId = routeThread.orEmpty().ifBlank { "worktree-merge" }
                            val safety = createWorkspaceSnapshot(threadId, target.path, nativeText(nativeLanguage, "\u5408\u5e76 ${source.branch} \u524d\u7684\u81ea\u52a8\u5907\u4efd", "Automatic backup before merging ${source.branch}"), true)
                            if (safety.first == null) error = safety.second
                            else {
                                val merged = runGit(target.path, listOf("merge", "--no-ff", "--no-edit", source.branch))
                                if (merged.first != 0) {
                                    runGit(target.path, listOf("merge", "--abort"))
                                    error = merged.second + "\n" + nativeText(nativeLanguage, "\u5df2\u81ea\u52a8\u53d6\u6d88\u51b2\u7a81\u5408\u5e76\uff0c\u4e3b\u5de5\u4f5c\u533a\u5df2\u6062\u590d\u3002", "The conflicted merge was aborted and the main worktree was restored.")
                                } else {
                                    preview = ""
                                    notice = nativeText(nativeLanguage, "\u5df2\u5c06 ${source.branch} \u5408\u5e76\u5230 ${target.branch}", "Merged ${source.branch} into ${target.branch}")
                                }
                            }
                        }
                    }
                    "remove" -> {
                        val target = entries.firstOrNull { samePath(it.path, rawValue) }
                        if (main == null || target == null || samePath(target.path, main.path) || pathInside(project, target.path)) {
                            error = nativeText(nativeLanguage, "\u4e0d\u80fd\u79fb\u9664\u4e3b\u5de5\u4f5c\u533a\u6216\u5f53\u524d\u5bf9\u8bdd\u6b63\u5728\u4f7f\u7528\u7684 worktree", "The main or currently active worktree cannot be removed")
                        } else if (referencedProjects.any { pathInside(it, target.path) }) {
                            error = nativeText(nativeLanguage, "\u8fd8\u6709\u5bf9\u8bdd\u7ed1\u5b9a\u5230\u8be5 worktree\uff0c\u8bf7\u5148\u5220\u9664\u6216\u8fc1\u79fb\u8fd9\u4e9b\u5bf9\u8bdd", "Conversations still reference this worktree; remove or migrate them first")
                        } else if (target.dirty || target.locked) {
                            error = nativeText(nativeLanguage, "\u53ea\u80fd\u79fb\u9664\u5e72\u51c0\u4e14\u672a\u9501\u5b9a\u7684 worktree", "Only clean, unlocked worktrees can be removed")
                        } else {
                            val removed = runGit(main.path, listOf("worktree", "remove", target.path))
                            if (removed.first != 0) error = removed.second
                            else notice = nativeText(nativeLanguage, "worktree \u5df2\u79fb\u9664\uff0c\u5206\u652f ${target.branch} \u4ecd\u4fdd\u7559", "Worktree removed; branch ${target.branch} was kept")
                        }
                    }
                    else -> error = nativeText(nativeLanguage, "\u672a\u77e5 worktree \u64cd\u4f5c", "Unknown worktree action")
                }
            }.exceptionOrNull()
            if (failure != null) error = failure.message ?: failure.javaClass.simpleName
            val refreshed = loadWorktrees(project)
            runOnUiThread {
                if (currentThreadId != routeThread || chatState.projectPath != project) return@runOnUiThread
                chatState.worktreeBusy = false
                chatState.worktreeError = error.trim()
                chatState.worktreeNotice = notice
                chatState.worktreeMergePreview = preview
                chatState.worktreePrHandoff = prHandoff
                chatState.worktrees.clear()
                chatState.worktrees.addAll(refreshed.first)
                if (openProject.isNotBlank() && error.isBlank()) newConversationAtProject(openProject)
                if (action == "merge" && error.isBlank()) performGitAction("refresh", "")
            }
        }, "NativeWorktreeWorkflow").start()
    }

    private fun toggleGoalPause() {
        val threadId = currentThreadId ?: return
        if (chatState.activeGoalObjective.isBlank() || chatState.phase.active) return
        val next = if (chatState.activeGoalStatus == "active") "paused" else "active"
        chatState.activeGoalStatus = next
        if (next != "active") cancelGoalAutoRetry()
        getSharedPreferences("codex_mobile", MODE_PRIVATE).edit().putString(goalStatusPreferenceKey(threadId), next).apply()
        bridge?.setThreadGoalStatus(next)
        if (next == "active" && goalRetryCycleActive) scheduleGoalRetry(300L)
    }

    private fun clearGoal() {
        cancelGoalAutoRetry()
        goalRetryCycleActive = false
        goalRetryWaitingForCompletion = false
        currentThreadId?.let(::clearLocalGoal)
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
            val target = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}-$safeName")
            val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("无法读取附件")
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            if (!target.isFile) throw IllegalStateException("附件缓存失败")
            chatState.attachments.add(NativeAttachment(name, target.absolutePath, image))
        } catch (error: Exception) {
            Toast.makeText(this, "附件读取失败：${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopCurrentTurn() {
        if (!chatState.busy) return
        cancelGoalAutoRetry()
        goalRetryWaitingForCompletion = false
        suppressGoalRetryUntilNewTurn = true
        chatState.connectionLabel = "正在停止…"
        bridge?.interruptCurrentTurn()
    }

    private fun canAutoRetryGoal(): Boolean =
        !suppressGoalRetryUntilNewTurn &&
            currentThreadId?.isNotBlank() == true &&
            chatState.activeGoalObjective.isNotBlank() &&
            chatState.activeGoalStatus == "active" &&
            chatState.ready

    private fun scheduleGoalRetry(delayMs: Long = 1_800L) {
        if (!canAutoRetryGoal() || goalRetryScheduled) return
        goalRetryScheduled = true
        streamHandler.postDelayed(goalRetryRunnable, delayMs)
    }

    private fun cancelGoalAutoRetry() {
        goalRetryScheduled = false
        streamHandler.removeCallbacks(goalRetryRunnable)
    }

    private fun handleTurnError(raw: String) {
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
        flushReasoningDeltas(force = true)
        flushAnswerDeltas(force = true)
        flushPlanDeltas()
        flushCommandDeltas()
        if (automaticGoal) {
            goalRetryCycleActive = true
            goalRetryWaitingForCompletion = true
            chatState.updateRetryStatus(message, automaticGoal = true, terminal = true)
            // Normally turn/completed arrives first. This fallback also recovers from older
            // app-server builds that only emit the final error notification.
            scheduleGoalRetry()
        } else {
            chatState.updateRetryStatus(message, automaticGoal = false, terminal = true)
            stopFrameDiagnostics()
            currentThreadId?.let { threadId ->
                NativeTaskNotificationManager.notifyEvent(this, threadId, NativeTaskNotificationPolicy.FAILED, message.hashCode().toString(), "")
            }
        }
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
                taskAttention(task.threadId, task.state),
            )
        }.sortedWith(compareByDescending<NativeConversation> { it.state == CodexTaskStore.RUNNING }
            .thenByDescending { attentionPriority(it.attention) })
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
                else NativeConversation(task.threadId, title, task.state, project, task.threadId in favorites, taskAttention(task.threadId, task.state))
            }.sortedWith(compareByDescending<NativeConversation> { it.state == CodexTaskStore.RUNNING }
                .thenByDescending { attentionPriority(it.attention) })
            applyConversationSnapshot(generation, enriched)
        }.apply { name = "CodexConversationMetadata" }.start()
    }

    private fun applyConversationSnapshot(generation: Int, conversations: List<NativeConversation>) {
        runOnUiThread {
            if (generation != conversationRefreshGeneration || isFinishing || isDestroyed) return@runOnUiThread
            android.util.Log.d("IlyopCodexTasks", "apply generation=$generation count=${conversations.size} first=${conversations.firstOrNull()?.title}")
            chatState.conversations.clear()
            chatState.conversations.addAll(conversations)
            conversations.firstOrNull { it.threadId == currentThreadId }?.projectPath?.takeIf { it.isNotBlank() }?.let {
                chatState.projectPath = it
            }
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
        pendingNativeSteers.clear()
        cancelGoalAutoRetry()
        goalRetryCycleActive = false
        goalRetryWaitingForCompletion = false
        suppressGoalRetryUntilNewTurn = false
        discardPendingStreamEvents("resume")
        resetProtocolTurnState()
        subagentRouteGeneration = subagentRouteCounter.incrementAndGet()
        subagentHistoryAttempts.clear()
        currentThreadId = threadId
        notificationTargetThreadId = ""
        NativeTaskNotificationManager.markSeen(this, threadId)
        NativeTaskNotificationManager.setForegroundThread(this, threadId)
        val selectedConversation = chatState.conversations.firstOrNull { it.threadId == threadId }
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

    private fun newConversation() = newConversationAtProject("")

    private fun newConversationAtProject(requestedProjectPath: String) {
        pendingNativeSteers.clear()
        cancelGoalAutoRetry()
        goalRetryCycleActive = false
        goalRetryWaitingForCompletion = false
        suppressGoalRetryUntilNewTurn = false
        discardPendingStreamEvents("new")
        resetProtocolTurnState()
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
        chatState.projectPath = requestedProjectPath.takeIf { it.isNotBlank() && File(it).isDirectory } ?: configuredProjectPath()
        chatState.conversationTitle = "新对话"
        chatState.ready = false
        chatState.connectionLabel = "正在创建新对话…"
        if (requestedProjectPath.isNotBlank()) bridge?.newConversationAtCwd(chatState.projectPath)
        else bridge?.newConversation()
    }

    private fun openHomeSettings() {
        // Queue the back-preview capture before launching, but keep all bitmap allocation and
        // copying off the shared UI thread so it cannot stall the settings enter transition.
        NativeSettingsBackPreview.captureAsync(window)
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14's system transition supplies the live previous-Activity preview for
            // predictive back. A custom ActivityOptions animation replaces that preview.
            startActivity(Intent(this, NativeSettingsActivity::class.java))
        } else {
            val transition = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.codex_settings_enter,
                R.anim.codex_chat_hold,
            )
            startActivity(Intent(this, NativeSettingsActivity::class.java), transition.toBundle())
        }
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
        if (!protocolAssistantSeen) {
            chatState.finishReasoning()
            chatState.appendAssistant(value)
            chatState.acceptProtocolEvent(NativeProtocolEvent.AssistantDelta(
                threadId = currentThreadId.orEmpty(),
                turnId = chatState.currentTurnId.takeIf { it.isNotBlank() },
                delta = value,
            ))
        }
    }

    private fun commandFlushDelayMs(): Long =
        NativeUiRenderSafety.streamFlushDelayMs(chatState.commandOutputLength(), pendingCommand.length, 72L)

    private fun flushCommandDeltas() {
        streamHandler.removeCallbacks(flushCommandRunnable)
        commandFlushScheduled = false
        if (pendingCommand.isEmpty()) return
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
        // Older app-server histories may omit context-compaction items. Merge the bounded local
        // journal by stable server/id key; NativeChatState keeps one divider for duplicates.
        compactionJournalStore.load(threadId).takeIf { it.isNotEmpty() }?.let(chatState::restoreCompactions)
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
            "onProtocolEvent" -> handleProtocolEvent(value)
            "onProtocolDelta" -> handleProtocolDelta(value)
            "onCommandDeltaV2" -> handleCommandDeltaV2(value)
            "onCompactionRpcResult" -> handleCompactionRpcResult(value)
            "onReady" -> {
                // A late onReady from the previous conversation must not overwrite the
                // goal/mode of the conversation the user has already selected.
                if (currentThreadId != null && currentThreadId != value) return
                currentThreadId = value
                chatState.currentThreadId = value
                if (chatState.projectPath.isBlank() && chatState.conversationAnimationKey.startsWith("new-")) {
                    chatState.projectPath = configuredProjectPath()
                }
                restoreGoalForThread(value)
                chatState.ready = true
                bridge?.getThreadGoal()
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
                if (!protocolAssistantSeen) {
                    flushReasoningDeltas(force = true)
                    flushCommandDeltas()
                    if (pendingAnswer.isEmpty()) answerPendingSince = android.os.SystemClock.uptimeMillis()
                    pendingAnswer.append(value)
                    if (!answerFlushScheduled) {
                        answerFlushScheduled = true
                        streamHandler.postDelayed(flushAnswerRunnable, answerFlushDelayMs())
                    }
                }
            }
            "onAssistantItemComplete" -> {
                flushReasoningDeltas(force = true)
                flushAnswerDeltas(force = true)
                flushCommandDeltas()
                if (!protocolAssistantSeen) {
                    chatState.finishReasoning()
                    chatState.completeAssistantItem(value)
                }
            }
            "onFinalAnswer" -> {
                flushReasoningDeltas(force = true)
                flushAnswerDeltas(force = true)
                flushCommandDeltas()
                if (!protocolAssistantSeen) {
                    chatState.finishReasoning()
                    chatState.appendAssistantFinal(value)
                }
            }
            "onReasoningDelta" -> {
                if (!protocolReasoningSeen) {
                    flushAnswerDeltas(force = true)
                    flushCommandDeltas()
                    if (pendingReasoning.isEmpty()) reasoningPendingSince = android.os.SystemClock.uptimeMillis()
                    pendingReasoning.append(value)
                    if (!reasoningFlushScheduled) {
                        reasoningFlushScheduled = true
                        streamHandler.postDelayed(flushReasoningRunnable, reasoningFlushDelayMs())
                    }
                }
            }
            "onReasoningComplete" -> {
                flushReasoningDeltas(force = true)
                flushCommandDeltas()
                if (!protocolReasoningSeen) chatState.acceptProtocolEvent(
                    NativeProtocolEvent.ReasoningCompleted(
                        threadId = currentThreadId.orEmpty(),
                        turnId = chatState.currentTurnId.takeIf { it.isNotBlank() },
                        text = value,
                    ),
                )
                if (!protocolReasoningSeen) {
                    if (value.length > chatState.reasoningText.length) chatState.replaceReasoning(value)
                    chatState.finishReasoning()
                    chatState.revision++
                }
            }
            "onCommandStarted" -> {
                flushCommandDeltas()
                if (!protocolCommandSeen) {
                    chatState.beginReasoningAfterAnswerIfNeeded()
                    chatState.startCommand(value)
                    val item = runCatching { JSONObject(value) }.getOrNull()
                    chatState.acceptProtocolEvent(
                        NativeProtocolEvent.CommandStarted(
                            threadId = currentThreadId.orEmpty(),
                            turnId = chatState.currentTurnId.takeIf { it.isNotBlank() },
                            itemId = item?.optString("id")?.takeIf { it.isNotBlank() },
                            command = item?.optString("command", item.optString("cmd")).orEmpty(),
                            cwd = item?.optString("cwd", item.optString("workingDirectory")).orEmpty(),
                            payload = value,
                        ),
                    )
                }
            }
            "onCommandDelta" -> {
                if (!protocolCommandSeen) {
                    pendingCommand.append(value)
                    if (!commandFlushScheduled) {
                        commandFlushScheduled = true
                        streamHandler.postDelayed(flushCommandRunnable, commandFlushDelayMs())
                    }
                }
            }
            "onCommandComplete" -> {
                flushCommandDeltas()
                if (!protocolCommandSeen) {
                    chatState.completeCommand(value)
                    val item = runCatching { JSONObject(value) }.getOrNull()
                    chatState.acceptProtocolEvent(
                        NativeProtocolEvent.CommandCompleted(
                            threadId = currentThreadId.orEmpty(),
                            turnId = chatState.currentTurnId.takeIf { it.isNotBlank() },
                            itemId = item?.optString("id")?.takeIf { it.isNotBlank() },
                            command = item?.optString("command", item.optString("cmd")).orEmpty(),
                            outputRef = item?.optString(NativeCommandOutputStore.OUTPUT_REF).orEmpty(),
                            status = item?.optString("status", "completed").orEmpty(),
                            exitCode = item?.optInt("exitCode")?.takeIf { item.has("exitCode") },
                            payload = value,
                        ),
                    )
                }
            }
            "onToolComplete" -> {
                if (!protocolToolSeen) {
                    chatState.addToolDetail(value)
                    val item = runCatching { JSONObject(value) }.getOrNull()
                    chatState.acceptProtocolEvent(
                        NativeProtocolEvent.ToolCompleted(
                            threadId = currentThreadId.orEmpty(),
                            turnId = chatState.currentTurnId.takeIf { it.isNotBlank() },
                            itemId = item?.optString("id")?.takeIf { it.isNotBlank() },
                            type = item?.optString("type", "tool").orEmpty(),
                            title = item?.optString("tool", item.optString("name", item.optString("query"))).orEmpty(),
                            payloadRef = item?.optString(NativeLargePayloadStore.PAYLOAD_REF).orEmpty(),
                            status = item?.optString("status", "completed").orEmpty(),
                            payload = value,
                        ),
                    )
                }
            }
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
            "onTokenUsage" -> {
                if (!protocolUsageSeen) {
                    chatState.updateTokenUsage(value)
                    NativeTokenUsageParser.parse(value, 0L)?.let { usage -> evaluateAutomaticCompaction(usage) }
                }
            }
            "onPlanStarted" -> {
                if (!protocolPlanSeen) {
                    flushAnswerDeltas(force = true); flushPlanDeltas(); chatState.closeActivityBoundary(); chatState.startProposedPlan(value)
                    chatState.phaseMessageStartIndex = chatState.messages.size
                }
            }
            "onPlanDelta" -> {
                if (!protocolPlanSeen) {
                    flushAnswerDeltas(force = true); chatState.finishReasoning()
                    val payload = runCatching { JSONObject(value) }.getOrNull()
                    val itemId = payload?.optString("itemId", payload.optString("item_id")).orEmpty()
                    val delta = payload?.optString("delta").orEmpty()
                    if (itemId.isNotBlank() && pendingPlanItemId.isNotBlank() && itemId != pendingPlanItemId) flushPlanDeltas()
                    if (itemId.isNotBlank()) pendingPlanItemId = itemId
                    if (delta.isNotEmpty()) pendingPlan.append(delta)
                    if (!planFlushScheduled && pendingPlan.isNotEmpty()) { planFlushScheduled = true; streamHandler.postDelayed(flushPlanRunnable, 56L) }
                }
            }
            "onPlanComplete" -> {
                if (!protocolPlanSeen) {
                    flushAnswerDeltas(force = true)
                    flushPlanDeltas()
                    chatState.finishReasoning()
                    chatState.completeProposedPlan(value)
                    pendingPlanItemId = ""
                    val planText = chatState.messages.lastOrNull {
                        it.role == NativeChatRole.ACTIVITY && it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX)
                    }?.let { decodeNativeProposedPlan(it.content) }.orEmpty()
                    currentThreadId?.takeIf { planText.isNotBlank() }
                        ?.let { persistPendingPlanImplementation(it, planText) }
                }
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
            "onGoalUpdated" -> applyGoalState(value)
            "onGoalCleared" -> {
                val payload = runCatching { JSONObject(value) }.getOrNull() ?: return
                val threadId = payload.optString("threadId")
                if (threadId.isNotBlank() && threadId == currentThreadId) clearLocalGoal(threadId)
            }
            "onCompactStatus" -> {
                // Retained bridges used a string-only RPC status. Fold it into the same reducer
                // instead of appending a second NOTICE capsule.
                when (value.lowercase()) {
                    "started" -> if (chatState.compactionItems.none { !it.isTerminal }) {
                        chatState.closeActivityBoundary()
                        chatState.beginManualCompaction()
                    }
                    "completed" -> chatState.completeManualCompactionRpc(success = true)
                    "cancelled", "canceled" -> chatState.completeManualCompactionRpc(success = false, cancelled = true)
                    else -> chatState.completeManualCompactionRpc(success = false, error = value)
                }
            }
            "onUserInputRequest" -> {
                storePendingUserInput(value)
                val requestThread = runCatching { JSONObject(value).optJSONObject("params")?.optString("threadId") }.getOrNull().orEmpty()
                if (requestThread.isBlank() || requestThread == currentThreadId) {
                    chatState.phase = NativeTurnPhase.WAITING
                    chatState.processingLabel = "\u7b49\u5f85\u4f60\u7684\u56de\u7b54"
                }
            }
            "onUserInputResolved" -> handleUserInputResolved(value)
            "onApprovalRequest" -> {
                storePendingApproval(value)
                chatState.phase = NativeTurnPhase.WAITING
                chatState.processingLabel = nativeText(nativeLanguage, "\u7b49\u5f85\u6743\u9650\u786e\u8ba4", "Waiting for approval")
            }
            "onSubagentEvent" -> {
                val thread = if (!protocolSubagentSeen) chatState.updateSubagent(value) else runCatching {
                    val item = JSONObject(value)
                    item.optString("agentThreadId", item.optString("agent_thread_id"))
                }.getOrDefault("")
                if (thread.isNotBlank()) loadSubagentHistory(thread)
            }
            "onSubagentHistory" -> handleSubagentHistory(value)
            "onSteerResult" -> {
                val payload = runCatching { JSONObject(value) }.getOrNull() ?: return
                val pending = pendingNativeSteers.remove(payload.optInt("requestId", -1)) ?: return
                if (!payload.optBoolean("accepted", false)) {
                    chatState.removeMessage(pending.messageId)
                    queueFollowUp(pending.followUp, clearComposer = false)
                    Toast.makeText(
                        this,
                        nativeText(nativeLanguage, "当前任务不能接收引导，已自动排队", "This turn cannot be steered; the message was queued"),
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (!chatState.busy) streamHandler.post(::sendNextQueuedFollowUp)
                }
            }
            "onTurnError" -> handleTurnError(value)
            "onItem" -> {
                chatState.beginReasoningAfterAnswerIfNeeded()
                chatState.addActivity(value)
            }
            "onTurnComplete" -> {
                val wasFailed = chatState.phase == NativeTurnPhase.FAILED
                val waitingForGoalRetry = goalRetryWaitingForCompletion
                cancelPendingAutoCompaction()
                if (!protocolTurnCompletedSeen) chatState.acceptProtocolEvent(NativeProtocolEvent.TurnCompleted(
                    threadId = currentThreadId.orEmpty(),
                    turnId = chatState.currentTurnId.takeIf { it.isNotBlank() },
                    failed = wasFailed,
                ))
                currentThreadId?.let(NativeHistorySnapshotCache::remove)
                flushReasoningDeltas(force = true)
                flushAnswerDeltas(force = true)
                flushPlanDeltas()
                flushCommandDeltas()
                approvalThreadId(chatState.pendingApprovalRequest)?.let(::clearPendingApproval)
                currentThreadId?.takeIf { chatState.pendingUserInputRequest.isNotBlank() }
                    ?.let(::clearPendingUserInput)
                chatState.completeTurn()
                if (wasFailed) chatState.phase = NativeTurnPhase.FAILED
                pendingPlanItemId = ""
                if (chatState.planJson != "[]") chatState.finishPlanPanel(runCatching { JSONArray(chatState.planJson).length() }.getOrDefault(0))
                stopFrameDiagnostics()
                refreshConversations()
                applyPendingProviderConfiguration()
                val hasQueuedFollowUp = chatState.queuedFollowUps.isNotEmpty()
                if (hasQueuedFollowUp) {
                    // Keep the completion barrier observable for one frame, then start exactly one
                    // queued turn. Remaining follow-ups advance one-per-completion like WebUI.
                    goalRetryWaitingForCompletion = false
                    streamHandler.postDelayed(::sendNextQueuedFollowUp, 80L)
                }
                if (!hasQueuedFollowUp && waitingForGoalRetry && canAutoRetryGoal()) {
                    goalRetryWaitingForCompletion = false
                    cancelGoalAutoRetry()
                    scheduleGoalRetry(400L)
                } else if (!wasFailed && !goalRetryWaitingForCompletion) {
                    goalRetryCycleActive = false
                    chatState.completeRetryStatus()
                }
            }
            "onNativeError" -> {
                // History and backend failures must leave the loading shell immediately; keeping
                // this true can make a useful error look like the same blank route that failed.
                chatState.historyLoading = false
                currentThreadId?.let(NativeHistorySnapshotCache::remove)
                flushReasoningDeltas(force = true)
                flushAnswerDeltas(force = true)
                flushPlanDeltas()
                flushCommandDeltas()
                approvalThreadId(chatState.pendingApprovalRequest)?.let(::clearPendingApproval)
                NativeChatDiagnostics.record(this, "native_error", JSONObject()
                    .put("thread", currentThreadId.orEmpty().take(8))
                    .put("model", chatState.selectedModel)
                    .put("effort", chatState.selectedEffort)
                    .put("message", value.take(600)))
                currentThreadId?.let { threadId ->
                    NativeTaskNotificationManager.notifyEvent(this, threadId, NativeTaskNotificationPolicy.FAILED, value.hashCode().toString(), "")
                }
                chatState.addError(value)
                stopFrameDiagnostics()
                applyPendingProviderConfiguration()
            }
            "onLog" -> Unit
        }
    }

    override fun onResume() {
        super.onResume()
        NativeTaskNotificationManager.setForegroundThread(this, currentThreadId)
        // Settings is a separate native Activity. Refresh preferences here so a theme
        // or language change is visible immediately when returning to the chat.
        val prefs = getSharedPreferences("codex_mobile", MODE_PRIVATE)
        nativeAppearanceRevision++
        nativeThemeMode = FcodeAppearancePreferences.normalizeColorMode(prefs.getString(FcodeAppearancePreferences.COLOR_MODE, "system"))
        nativeColorPalette = FcodeColorPalette.from(prefs.getString(FcodeAppearancePreferences.COLOR_PALETTE, FcodeColorPalette.ROSE.value)).value
        nativeInterfaceStyle = FcodeInterfaceStyle.from(prefs.getString(FcodeAppearancePreferences.INTERFACE_STYLE, FcodeInterfaceStyle.MATERIAL.value)).value
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
        showModelSubtitle = prefs.getBoolean(NATIVE_SHOW_MODEL_SUBTITLE_PREFERENCE, true)
        showReasoningTitles = prefs.getBoolean(NATIVE_SHOW_REASONING_TITLES_PREFERENCE, true)
        hideNativeStatusBar = prefs.getBoolean(NATIVE_HIDE_STATUS_BAR_PREFERENCE, false)
        applyNativeStatusBarVisibility(hideNativeStatusBar)
        chatState.permissionMode = NativePermissionMode.normalize(prefs.getString(NativePermissionMode.PREFERENCE_KEY, NativePermissionMode.FULL_ACCESS))
        AppUpdateManager.clearLegacyDownloadState(this)
        AppUpdateManager.checkAutomatically(this, nativeLanguage)
        bridge?.loadSkills()
        reloadProviderConfigurationIfChanged()
        if (chatState.busy) startFrameDiagnostics()
    }

    override fun onPause() {
        stopFrameDiagnostics()
        NativeTaskNotificationManager.setForegroundThread(this, null)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val threadId = intent.getStringExtra(NativeTaskNotificationManager.EXTRA_THREAD_ID).orEmpty()
        if (threadId.isBlank()) return
        notificationTargetThreadId = threadId
        NativeTaskNotificationManager.markSeen(this, threadId)
        if (threadId != currentThreadId) resumeConversation(threadId)
        else NativeTaskNotificationManager.setForegroundThread(this, threadId)
    }

    override fun onDestroy() {
        backendStartGeneration++
        backendScope.cancel()
        streamHandler.removeCallbacksAndMessages(null)
        getSharedPreferences("codex_mobile", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(taskPreferenceListener)
        CodexNativeRuntime.detach(this)
        bridge = null
        super.onDestroy()
    }
}
