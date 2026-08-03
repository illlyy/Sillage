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


internal const val NATIVE_USER_INPUT_TIMEOUT_MS = 240_000L
internal const val NATIVE_SHOW_RESPONSE_STATS_PREFERENCE = "native_show_response_stats_v1"
internal const val NATIVE_SHOW_MODEL_SUBTITLE_PREFERENCE = "native_show_model_subtitle_v1"
internal const val NATIVE_SHOW_REASONING_TITLES_PREFERENCE = "native_show_reasoning_titles_v1"
internal const val NATIVE_FOLLOW_UP_ACTION_PREFERENCE = "native_follow_up_submit_action_v1"
internal const val CODEX_INSTALL_PROMPT_VIEWED_PREFERENCE = "codex_install_prompt_viewed_v1"


internal data class NativeLegacyPendingStreamScope(
    val threadId: String,
    val turnId: String,
    val epoch: Long,
)

/** Keeps unsequenced compatibility callbacks inside the route/turn that first buffered them. */
internal class NativeLegacyPendingStreamScopeGate {
    internal var epoch = 0L
    internal var pendingScope: NativeLegacyPendingStreamScope? = null

    val currentEpoch: Long
        get() = epoch

    fun advanceEpoch() {
        epoch++
        pendingScope = null
    }

    fun capture(threadId: String, turnId: String?): Boolean {
        val candidate = NativeLegacyPendingStreamScope(threadId.trim(), turnId.orEmpty().trim(), epoch)
        val existing = pendingScope
        if (existing == null) {
            pendingScope = candidate
            return true
        }
        if (existing.epoch != candidate.epoch || existing.threadId != candidate.threadId) return false
        if (existing.turnId.isNotBlank() && candidate.turnId.isNotBlank() && existing.turnId != candidate.turnId) {
            return false
        }
        if (existing.turnId.isBlank() && candidate.turnId.isNotBlank()) pendingScope = candidate
        return true
    }

    fun canDrain(threadId: String, turnId: String?): Boolean {
        val existing = pendingScope ?: return true
        val candidateThread = threadId.trim()
        val candidateTurn = turnId.orEmpty().trim()
        if (existing.epoch != epoch || existing.threadId != candidateThread) return false
        return existing.turnId.isBlank() || candidateTurn.isBlank() || existing.turnId == candidateTurn
    }

    fun clearPending() {
        pendingScope = null
    }
}


/** Tracks only continuation facts emitted by this Activity; unknown/global facts are preserved. */
internal class NativeContinuationHintRouter {
    internal var routedThreadId = ""

    fun sync(threadId: String, pending: Boolean, emit: (String, Boolean) -> Unit) {
        val target = threadId.trim()
        if (target.isNotBlank()) emit(target, pending)
        routedThreadId = target
    }

    fun leave(fallbackThreadId: String): String {
        val target = routedThreadId.ifBlank { fallbackThreadId.trim() }
        routedThreadId = ""
        // Keep the last per-thread fact intact. Only a later explicit sync for this same thread may
        // replace true with false; route visibility alone says nothing about background work.
        return target
    }
}


internal object NativeTurnCompletionDrainGate {
    fun drainIfOwned(
        completionThreadId: String,
        currentThreadId: String?,
        completionTurnId: String,
        completionEpoch: Long?,
        currentEpoch: Long,
        currentTurnActive: Boolean,
        acceptTurn: (String) -> Boolean,
        drain: () -> Unit,
    ): Boolean {
        val completedThread = completionThreadId.trim()
        val currentThread = currentThreadId?.trim()?.takeIf { it.isNotBlank() } ?: return false
        if (completedThread.isNotBlank() && completedThread != currentThread) return false
        val completedTurn = completionTurnId.trim()
        if (completedTurn.isBlank() && currentTurnActive && completionEpoch != currentEpoch) return false
        if (!acceptTurn(completedTurn)) return false
        drain()
        return true
    }
}


class CodexChatActivity : ComponentActivity(), NativeBackendBridge.EventListener {


    companion object {
        internal val subagentRouteCounter = java.util.concurrent.atomic.AtomicInteger(0)
        internal val stalePendingStateCleaned = java.util.concurrent.atomic.AtomicBoolean(false)
        internal const val STREAM_CATCH_UP_CHUNK_CHARS = 1_200
        internal const val STREAM_CATCH_UP_DELAY_MS = 24L
        internal const val MOTION_DEFER_CAP_MS = 400L
        // Smooth stream reveal: a whole-answer dump is paced out at this rate instead of being
        // released in 1_200-char pages. Real per-token streaming stays unaffected (backlog small).
        internal const val STREAM_REVEAL_CHARS_PER_SEC = 2_400
        internal const val MIN_STREAM_REVEAL_PER_FLUSH = 12
    }


    internal val chatState = NativeChatState()

    internal lateinit var providerStore: CodexProviderStore

    internal lateinit var compactionSettingsStore: NativeCompactionSettingsStore

    internal lateinit var compactionJournalStore: NativeCompactionJournalStore

    internal var compactionPolicy = NativeCompactionPolicy()

    internal var compactionPolicyKey = ""

    internal var compactionSettingsSnapshot: NativeCompactionSettings? = null

    internal var lastReliableUsage: NativeTurnUsage? = null

    internal var protocolReasoningSeen = false

    internal var protocolAssistantSeen = false

    internal var protocolCommandSeen = false

    internal var protocolPlanSeen = false

    internal var protocolToolSeen = false

    internal var protocolSubagentSeen = false

    internal var protocolUsageSeen = false

    internal var protocolTurnCompletedSeen = false

    internal var lastProtocolCompletedTurnId = ""

    internal val handledTurnCompletionKeys = LinkedHashSet<String>()

    internal val protocolEventQueue = NativeOrderedProtocolEventQueue()

    internal val legacyPendingStreamScope = NativeLegacyPendingStreamScopeGate()

    internal val continuationHintRouter = NativeContinuationHintRouter()

    internal val flushProtocolEventQueueRunnable = Runnable { flushProtocolEventQueue() }

    internal val compactionLifecycleTimeouts = HashMap<String, Runnable>()

    internal var bridge: NativeBackendBridge? = null

    internal val pendingNativeSteers = mutableMapOf<Int, PendingNativeSteer>()

    internal var activeProfileId: String = ""

    internal var appliedProviderFingerprint: String? = null

    internal var appliedProviderDefaultModel: String = ""

    internal var backendConfigurationLoaded = false

    internal var pendingBackendConfigurationReload = false

    /** Claude-only: a model-picker switch requested mid-turn; applied when the turn completes. */
    internal var pendingClaudeModelSwitch = ""

    internal var runtimeStartAttempted = false

    internal var pendingCachedHistoryThreadId: String? = null

    internal var pendingCachedHistorySnapshot: NativeHistorySnapshot? = null

    internal var lifecycleHandoffRouteThreadId = ""

    internal var conversationRefreshGeneration = 0

    /** Backend whose records were last applied to the drawer; null until the first refresh. */
    internal var lastConversationBackend: NativeBackendType? = null

    /** Backend the currently-attached [bridge] belongs to; null when no bridge is attached.
     *  This is the source of truth for whether a backend switch actually restarted the runtime —
     *  distinct from the process-wide per-runtime singletons (which both stay alive after any
     *  toggle and therefore cannot tell you which backend the activity is routed to). */
    internal var attachedBackend: NativeBackendType? = null

    internal var subagentRouteGeneration = subagentRouteCounter.incrementAndGet()

    internal val subagentHistoryAttempts = HashMap<String, Int>()

    internal val conversationProjectCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    internal val conversationTitleCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    internal val taskPreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "overlay_tasks_v1") {
            android.util.Log.d("IlyopCodexTasks", "preference changed")
            refreshConversations()
        }
    }

    internal val streamHandler = Handler(Looper.getMainLooper())

    internal val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    internal val backendCatalogPreparer = NativeBackendCatalogPreparer(
        File(File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "ilyop-model-catalog.json"),
    )

    internal var backendStartGeneration = 0

    internal var goalRetryCycleActive = false

    internal var goalRetryWaitingForCompletion = false

    internal var goalRetryScheduled = false

    internal var suppressGoalRetryUntilNewTurn = false

    internal val goalRetryRunnable = Runnable {
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

    internal val pendingReasoning = StringBuilder()

    internal val pendingAnswer = StringBuilder()

    internal val pendingPlan = StringBuilder()

    internal val pendingCommand = StringBuilder()

    internal var pendingPlanItemId = ""

    internal var reasoningFlushScheduled = false

    internal var answerFlushScheduled = false

    internal var planFlushScheduled = false

    internal var commandFlushScheduled = false

    internal var reasoningPendingSince = 0L

    internal var reasoningLastFlushAtMs = 0L

    internal var answerPendingSince = 0L

    internal var answerLastFlushAtMs = 0L
    /**
     * Direct-manipulation and navigation motion owns the UI thread. Stream deltas keep buffering
     * while those animations run, but no growing text snapshot is published into Compose until
     * the motion settles. This prevents answer measurement from stealing drawer frames.
     */

    internal var uiMotionActive = false

    internal var uiMotionCatchUpGeneration = 0

    /** Uptime when this buffer's flush first deferred for UI motion (0 = not deferring). */
    internal var reasoningMotionDeferSince = 0L

    internal var answerMotionDeferSince = 0L

    internal val flushReasoningRunnable = Runnable {
        reasoningFlushScheduled = false
        flushReasoningDeltas()
    }

    internal val flushAnswerRunnable = Runnable {
        answerFlushScheduled = false
        flushAnswerDeltas()
    }

    internal val flushPlanRunnable = Runnable {
        planFlushScheduled = false
        flushPlanDeltas()
    }

    internal val flushCommandRunnable = Runnable {
        commandFlushScheduled = false
        flushCommandDeltas()
    }

    internal var lastFrameNanos = 0L

    internal var frameWindowStartedAt = 0L

    internal var frameCount = 0

    internal var slowFrameCount = 0

    internal var worstFrameMs = 0L

    internal val frameDiagnostics = object : Choreographer.FrameCallback {
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

    internal var currentThreadId: String? = null

    internal var notificationTargetThreadId: String = ""

    internal var expectedHistoryGeneration = -1

    internal var displayedHistorySnapshot: NativeHistorySnapshot? = null

    internal var nativeThemeMode by mutableStateOf("system")

    internal var nativeColorPalette by mutableStateOf(FcodeColorPalette.ROSE.value)

    internal var nativeInterfaceStyle by mutableStateOf(FcodeInterfaceStyle.MATERIAL.value)

    internal var nativeAppearanceRevision by mutableIntStateOf(0)

    internal var nativeChatBackground by mutableStateOf(FcodeChatBackgroundStyle.THEME.value)

    internal var nativeChatBackgroundImage by mutableStateOf("")

    internal var nativeChatBackgroundDim by mutableStateOf(0.32f)

    internal var nativeChatFontScale by mutableStateOf(FcodeAppearancePreferences.DEFAULT_CHAT_FONT_SCALE)

    internal var nativeMaterialTransparency by mutableStateOf(FcodeMaterialTransparencyConfig())

    internal var nativeLanguage by mutableStateOf("zh")

    internal var streamAnimationsEnabled by mutableStateOf(true)

    internal var fixedStreamingViewportEnabled by mutableStateOf(true)

    internal var showReasoning by mutableStateOf(true)

    internal var autoFollowOutput by mutableStateOf(true)

    internal var showResponseStats by mutableStateOf(true)

    internal var showModelSubtitle by mutableStateOf(true)

    internal var showReasoningTitles by mutableStateOf(true)

    internal var hideNativeStatusBar by mutableStateOf(false)

    internal val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { cacheAttachment(it, true) }
    }

    internal val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { cacheAttachment(it, false) }
    }

    internal fun startFrameDiagnostics() {
        lastFrameNanos = 0L
        frameWindowStartedAt = android.os.SystemClock.uptimeMillis()
        frameCount = 0
        slowFrameCount = 0
        worstFrameMs = 0L
        Choreographer.getInstance().removeFrameCallback(frameDiagnostics)
        Choreographer.getInstance().postFrameCallback(frameDiagnostics)
    }

    internal fun stopFrameDiagnostics() {
        Choreographer.getInstance().removeFrameCallback(frameDiagnostics)
        lastFrameNanos = 0L
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
        nativeChatFontScale = readFcodeChatFontScale(this)
        nativeMaterialTransparency = readFcodeMaterialTransparencyConfig(this)
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
                chatFontScale = nativeChatFontScale,
                materialTransparency = nativeMaterialTransparency,
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
                    onNewConversationAtProject = ::newConversationAtProject,
                    onResumeConversation = ::resumeConversation,
                    onUiMotionChanged = ::setUiMotionActive,
                    onRefreshConversations = ::refreshConversations,
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
                        nativeThemeMode = when (nativeThemeMode) {
                            "system" -> "light"
                            "light" -> "dark"
                            else -> "system"
                        }
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
                    runtimeStartAttempted = true
                    refreshConversations()
                    if (isBackendCliInstalled()) {
                        startBackend()
                    } else {
                        chatState.ready = false
                        val isClaude = NativeBackendType.current(getSharedPreferences("codex_mobile", MODE_PRIVATE)) == NativeBackendType.CLAUDE
                        chatState.connectionLabel = if (isClaude) {
                            nativeText(nativeLanguage, "Claude CLI 尚未安装", "Claude CLI is not installed")
                        } else {
                            nativeText(nativeLanguage, "Codex CLI 尚未安装", "Codex CLI is not installed")
                        }
                        if (!isClaude) maybeOfferCodexInstallOnFirstLaunch()
                    }
                }
            }
        }
        decor.viewTreeObserver.addOnDrawListener(listener)
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
                // goal/mode of the conversation the user has already selected. The Claude
                // bridge's session id is authoritative: a resume that failed on the CLI side
                // starts a fresh session and the host must adopt it or stay unready forever.
                val isClaudeBackend = NativeBackendType.current(getSharedPreferences("codex_mobile", MODE_PRIVATE)) == NativeBackendType.CLAUDE
                if (!isClaudeBackend && currentThreadId != null && currentThreadId != value) return
                currentThreadId = value
                chatState.currentThreadId = value
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
                    captureLegacyPendingStreamScope()
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
                    captureLegacyPendingStreamScope()
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
                    captureLegacyPendingStreamScope()
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
                    if (delta.isNotEmpty()) captureLegacyPendingStreamScope()
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
                    subagentThreadId(JSONObject(value))
                }.getOrDefault("")
                syncNativeContinuationHint()
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
                // Publish any tail still buffered by the chunked stream flush before the phase
                // seals: a deferred flush can otherwise leave the final tokens missing from the
                // sealed answer (visible only after re-entering).
                flushReasoningDeltas(force = true)
                flushAnswerDeltas(force = true)
                flushCommandDeltas()
                val completion = value.takeIf { it.isNotBlank() }
                    ?.let { runCatching { JSONObject(it) }.getOrNull() }
                val completionThreadId = completion?.let(::protocolEventThread).orEmpty()
                val routeThreadId = currentThreadId.orEmpty()
                val completionTurnId = completion?.let(::protocolEventTurn).orEmpty()
                    .ifBlank { lastProtocolCompletedTurnId }
                val completionEpoch = protocolEventLocalEpoch(completion)
                val activeTurnId = chatState.currentTurnId
                val ownsVisibleTurn = NativeTurnCompletionDrainGate.drainIfOwned(
                    completionThreadId = completionThreadId,
                    currentThreadId = currentThreadId,
                    completionTurnId = completionTurnId,
                    completionEpoch = completionEpoch,
                    currentEpoch = legacyPendingStreamScope.currentEpoch,
                    currentTurnActive = chatState.phase.active,
                    acceptTurn = { turnId ->
                        chatState.shouldAcceptProtocolTurnCompletion(
                            turnId,
                            localEpochMatches = completionEpoch == legacyPendingStreamScope.currentEpoch,
                        )
                    },
                    drain = ::drainPendingNativeUiEvents,
                )
                if (!ownsVisibleTurn) {
                    NativeChatDiagnostics.record(this, "stale_turn_completion_ignored", JSONObject()
                        .put("thread", currentThreadId.orEmpty().take(8))
                        .put("completedThread", completionThreadId.take(8))
                        .put("completedTurn", completionTurnId.take(12))
                        .put("activeTurn", activeTurnId.take(12)))
                    return
                }
                // Only the completion that owns the visible thread/turn may publish the global
                // compatibility StringBuilders. An old completion can otherwise flush a new turn.
                val completionTurn = completion?.optJSONObject("turn")
                val nestedContinuation = completionTurn?.let {
                    it.optBoolean(
                        "hasPendingContinuation",
                        it.optBoolean("has_pending_continuation", false),
                    )
                } ?: false
                val hasPendingContinuation = completion?.let {
                    it.optBoolean(
                        "hasPendingContinuation",
                        it.optBoolean("has_pending_continuation", nestedContinuation),
                    )
                } ?: nestedContinuation
                val wasFailed = chatState.phase == NativeTurnPhase.FAILED ||
                    nativeTurnLifecycleFailed(completionTurn, completion?.optJSONObject("details"), completion)
                val stableCompletionTurn = completionTurnId.ifBlank { activeTurnId }
                if (!claimTurnCompletion(routeThreadId, stableCompletionTurn)) return
                val waitingForGoalRetry = goalRetryWaitingForCompletion
                if (!protocolTurnCompletedSeen) {
                    chatState.acceptProtocolEvent(NativeProtocolEvent.TurnCompleted(
                        threadId = routeThreadId,
                        turnId = completionTurnId.takeIf { it.isNotBlank() }
                            ?: chatState.currentTurnId.takeIf { it.isNotBlank() },
                        failed = wasFailed,
                    ))
                    chatState.completeTurn()
                }
                currentThreadId?.let(NativeHistorySnapshotCache::remove)
                approvalThreadId(chatState.pendingApprovalRequest)?.let(::clearPendingApproval)
                currentThreadId?.takeIf { chatState.pendingUserInputRequest.isNotBlank() }
                    ?.let(::clearPendingUserInput)
                if (wasFailed) chatState.phase = NativeTurnPhase.FAILED
                pendingPlanItemId = ""
                if (chatState.planJson != "[]") chatState.finishPlanPanel(runCatching { JSONArray(chatState.planJson).length() }.getOrDefault(0))
                if (!hasPendingContinuation) stopFrameDiagnostics()
                refreshConversations()
                if (!hasPendingContinuation) applyPendingProviderConfiguration()
                if (!hasPendingContinuation) applyPendingClaudeModelSwitch()
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
                } else if (hasPendingContinuation && !hasQueuedFollowUp && !goalRetryWaitingForCompletion) {
                    chatState.phase = NativeTurnPhase.WAITING
                    chatState.processingLabel = nativeText(nativeLanguage, "继续处理中", "Continuing")
                } else if (!wasFailed && !goalRetryWaitingForCompletion) {
                    goalRetryCycleActive = false
                    chatState.completeRetryStatus()
                }
                syncNativeContinuationHint()
            }
            "onHistoryWarning" -> {
                chatState.historyLoading = false
                NativeChatDiagnostics.record(this, "history_warning", JSONObject()
                    .put("thread", currentThreadId.orEmpty().take(8))
                    .put("message", value.take(600)))
                chatState.addNotice(value)
            }
            "onNativeError" -> {
                // Terminal backend failures must leave the loading shell immediately; keeping this
                // true can make a useful error look like the same blank route that failed.
                chatState.historyLoading = false
                currentThreadId?.let(NativeHistorySnapshotCache::remove)
                drainPendingNativeUiEvents()
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
                applyPendingClaudeModelSwitch()
            }
            "onLog" -> Unit
        }
        publishSessionActivity()
    }

    /**
     * Single writer into [NativeSessionActivityStore] for the visible conversation. The store
     * feeds the drawer status dots and any cross-conversation UI from one source of truth.
     */
    private fun publishSessionActivity() {
        val threadId = currentThreadId ?: return
        if (chatState.phase.active) {
            NativeSessionActivityStore.markProcessing(
                threadId = threadId,
                statusText = chatState.processingLabel,
                canInterrupt = chatState.phase != NativeTurnPhase.STOPPING && chatState.busy,
            )
        } else {
            NativeSessionActivityStore.markIdle(threadId)
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
        nativeChatFontScale = readFcodeChatFontScale(this)
        nativeMaterialTransparency = readFcodeMaterialTransparencyConfig(this)
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
        // The WebUI intentionally takes exclusive ownership of the Codex backend. When its
        // host finishes, this Activity is still in the back stack with a reference to the old,
        // stopped bridge; rebuild it before accepting another native turn.
        val currentBackend = NativeBackendType.current(getSharedPreferences("codex_mobile", MODE_PRIVATE))
        when {
            runtimeStartAttempted && !backendConfigurationLoaded && isBackendCliInstalled() -> {
                startBackend(preferConfiguredDefault = true)
                refreshConversations()
            }
            // A settings-page switch only writes the pref. The activity's attached bridge is the
            // truth for routing: if it belongs to the other backend, run a full switchBackend
            // (tear down the leaving runtime, spawn the requested one, reset to a fresh chat).
            // backendRuntimeExists() alone cannot detect this — it probes the process-wide per-
            // backend singletons, which both stay alive after any toggle, so both bridges exist
            // once a user has switched back and forth and the old bridge would be kept forever.
            backendConfigurationLoaded && bridge != null && attachedBackend != null && attachedBackend != currentBackend ->
                switchBackend(currentBackend)
            backendConfigurationLoaded && bridge != null && !backendRuntimeExists() -> {
                bridge = null
                startBackend()
                refreshConversations()
            }
            else -> {
                bridge?.loadSkills()
                reloadProviderConfigurationIfChanged()
                refreshConversations()
            }
        }
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

    private fun persistLifecycleHandoffBeforeDetach() {
        val threadId = currentThreadId?.takeIf { it.isNotBlank() } ?: return
        if (!CodexNativeRuntime.exists() || Looper.myLooper() != Looper.getMainLooper()) return
        drainPendingNativeUiEvents()
        val handoff = chatState.lifecycleHandoffSnapshot()
        if (handoff.threadId != threadId) return
        NativeChatLifecycleHandoff.remember(handoff)
        NativeHistorySnapshotCache.put(threadId, handoff.history)
        NativeChatDiagnostics.record(this, "activity_lifecycle_handoff", JSONObject()
            .put("thread", threadId.take(8))
            .put("phase", handoff.phase.name.lowercase(Locale.ROOT))
            .put("messages", handoff.history.messages.size)
            .put("estimatedChars", handoff.history.estimatedChars))
    }

    override fun onDestroy() {
        backendStartGeneration++
        backendScope.cancel()
        persistLifecycleHandoffBeforeDetach()
        // The bridge keeps running when the activity detaches; the turn may still be live in
        // another host. Do not clear the activity map — just drop this activity's observation.
        streamHandler.removeCallbacksAndMessages(null)
        getSharedPreferences("codex_mobile", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(taskPreferenceListener)
        if (NativeBackendType.current(getSharedPreferences("codex_mobile", MODE_PRIVATE)) == NativeBackendType.CLAUDE) {
            ClaudeNativeRuntime.detach(this)
        } else {
            CodexNativeRuntime.detach(this)
        }
        bridge = null
        super.onDestroy()
    }
}

