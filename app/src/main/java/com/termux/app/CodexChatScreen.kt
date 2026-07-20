@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.termux.app

import android.util.Base64
import android.app.Activity
import android.content.Context
import android.view.accessibility.AccessibilityManager

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import java.util.Locale
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.Code
import org.commonmark.node.BlockQuote
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.tanh
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Bug01
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Share08
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.Voice
import me.rerere.hugeicons.stroke.Zap

val LocalNativeLanguage = staticCompositionLocalOf { "zh" }
val LocalStreamAnimationsEnabled = staticCompositionLocalOf { true }
val LocalFixedStreamingViewportEnabled = staticCompositionLocalOf { true }
private val LocalInteractiveScrollInProgress = staticCompositionLocalOf { false }
private val LocalTextSelectionActivityChanged = staticCompositionLocalOf<(Boolean) -> Unit> { { } }
private val LocalOpenSubagentDrawer = staticCompositionLocalOf<(JSONObject) -> Unit> { { } }
private const val CHAT_HISTORY_PAGE_SIZE = 24
val LocalShowReasoning = staticCompositionLocalOf { true }
val LocalAutoFollowOutput = staticCompositionLocalOf { true }
val LocalShowResponseStats = staticCompositionLocalOf { true }
val LocalShowModelSubtitle = staticCompositionLocalOf { true }
val LocalShowReasoningTitles = staticCompositionLocalOf { true }

internal fun nativeText(language: String, zh: String, en: String): String = if (language == "en") en else zh

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FcodeChatTheme(
    themeMode: String = "system",
    language: String = "zh",
    streamAnimations: Boolean = true,
    showReasoning: Boolean = true,
    autoFollow: Boolean = true,
    colorPalette: String = FcodeColorPalette.ROSE.value,
    chatBackground: String = FcodeChatBackgroundStyle.THEME.value,
    chatBackgroundImage: String = "",
    chatBackgroundDim: Float = 0.32f,
    fixedStreamingViewport: Boolean = true,
    showResponseStats: Boolean = true,
    showModelSubtitle: Boolean = true,
    showReasoningTitles: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = currentFcodeDarkMode(themeMode)
    val palette = FcodeColorPalette.from(colorPalette)
    val background = FcodeChatBackgroundStyle.from(chatBackground)
    val colorScheme = fcodeColorScheme(palette, dark)
    val markdownColors = fcodeMarkdownColors(palette, dark, colorScheme)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalNativeLanguage provides language,
                LocalStreamAnimationsEnabled provides streamAnimations,
                LocalFixedStreamingViewportEnabled provides fixedStreamingViewport,
                LocalShowReasoning provides showReasoning,
                LocalAutoFollowOutput provides autoFollow,
                LocalShowResponseStats provides showResponseStats,
                LocalShowModelSubtitle provides showModelSubtitle,
                LocalShowReasoningTitles provides showReasoningTitles,
                LocalFcodeColorPalette provides palette,
                LocalFcodeChatBackground provides background,
                LocalFcodeChatBackgroundImage provides chatBackgroundImage,
                LocalFcodeChatBackgroundDim provides chatBackgroundDim.coerceIn(0f, 0.72f),
                LocalFcodeMarkdownColors provides markdownColors,
                content = content,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeChatScreen(
    state: NativeChatState,
    onSend: (String) -> Unit,
    onInputChange: (String) -> Unit,
    onRetry: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onStop: () -> Unit,
    onNewConversation: () -> Unit,
    onResumeConversation: (String) -> Unit,
    onUiMotionChanged: (Boolean) -> Unit,
    onLoadSubagentHistory: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onEffortSelected: (String) -> Unit,
    onModeChange: (String) -> Unit,
    onPermissionModeChange: (String) -> Unit,
    onSetGoal: (String) -> Unit,
    onClearGoal: () -> Unit,
    onToggleGoalPause: () -> Unit,
    onCompact: () -> Unit,
    onAnswerUserInput: (String) -> Unit,
    onExecutePendingPlan: () -> Unit,
    onRevisePendingPlan: (String) -> Unit,
    onCancelPendingPlan: () -> Unit,
    onAnswerApproval: (String, String) -> Unit,
    onGitAction: (String, String) -> Unit,
    onSnapshotAction: (String, String) -> Unit,
    onWorktreeAction: (String, String) -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onRemoveAttachment: (NativeAttachment) -> Unit,
    onRenameConversation: (NativeConversation, String) -> Unit,
    onDeleteConversation: (NativeConversation) -> Unit,
    onToggleFavorite: (NativeConversation) -> Unit,
    onBackHome: () -> Unit,
    onOpenLegacyWebUi: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val context = LocalContext.current
    val language = LocalNativeLanguage.current
    val autoFollowEnabled = LocalAutoFollowOutput.current
    val drawerState = rememberFcodeInteractiveDrawerState()
    val scope = rememberCoroutineScope()
    // Drawer content remains composed offscreen; opening is transform-only.
    var pendingConversationThreadId by remember { mutableStateOf<String?>(null) }
    var textSelectionActive by remember { mutableStateOf(false) }
    val onTextSelectionActivityChanged = remember { { active: Boolean -> textSelectionActive = active } }
    val conversationListKey = state.conversationAnimationKey
    var historyLimit by remember(conversationListKey) { mutableIntStateOf(CHAT_HISTORY_PAGE_SIZE) }
    val initialVisibleCount = minOf(historyLimit, state.messages.size)
    val initialLoaderOffset = if (state.messages.size > initialVisibleCount) 1 else 0
    val initialLastItem = (initialVisibleCount + initialLoaderOffset - 1).coerceAtLeast(0)
    // A conversation switch used to reuse the previous thread's index, compose that middle
    // slice, then jump to the new bottom 16ms later. Start the new LazyList at its bottom so
    // cached history needs one measure/layout pass instead of two.
    val listState = remember(conversationListKey) {
        androidx.compose.foundation.lazy.LazyListState(initialLastItem, Int.MAX_VALUE)
    }
    val conversationSurfaceStage = when {
        state.historyLoading && state.messages.isEmpty() && state.currentThreadId.isNotBlank() -> "loading"
        state.messages.isEmpty() -> "empty"
        else -> "messages"
    }
    val conversationPresentationKey = "$conversationListKey:$conversationSurfaceStage"
    var conversationSurfaceEntered by remember(conversationPresentationKey) { mutableStateOf(false) }
    LaunchedEffect(conversationPresentationKey) {
        // Present the lightweight route first, then animate only one parent layer. This avoids
        // per-message transitions and keeps a large Markdown history off the animation clock.
        withFrameNanos { }
        conversationSurfaceEntered = true
    }
    val conversationSurfaceProgress by animateFloatAsState(
        targetValue = if (conversationSurfaceEntered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 390f),
        label = "conversationSurfaceTransition",
    )
    val listDragged by listState.interactionSource.collectIsDraggedAsState()
    val listScrolling by remember(listState) { derivedStateOf { listState.isScrollInProgress } }
    val accessibilityManager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    var touchExplorationEnabled by remember(accessibilityManager) {
        mutableStateOf(accessibilityManager.isTouchExplorationEnabled)
    }
    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            touchExplorationEnabled = enabled
        }
        accessibilityManager.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibilityManager.removeTouchExplorationStateChangeListener(listener) }
    }
    var followOutput by remember(conversationListKey) { mutableStateOf(true) }
    var followPausedUntil by remember(conversationListKey) { mutableLongStateOf(0L) }
    var inputHeightPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inputBottomPadding = with(density) { inputHeightPx.toDp() } + 8.dp
    // Visibility is a stable boolean. Reading the animated IME bottom inset here used to
    // invalidate the entire chat composition on every keyboard frame.
    val imeVisible = WindowInsets.isImeVisible
    val floatingInsetModifier = if (imeVisible) Modifier.imePadding() else Modifier
    val showScrollToBottom by remember(listState) { derivedStateOf { state.messages.isNotEmpty() && listState.canScrollForward } }
    var showModelPicker by remember { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    var showConversationSearch by remember { mutableStateOf(false) }
    var showMessageSearch by remember { mutableStateOf(false) }
    var renameConversation by remember { mutableStateOf<NativeConversation?>(null) }
    var deleteConversation by remember { mutableStateOf<NativeConversation?>(null) }
    var editMessage by remember { mutableStateOf<NativeChatMessage?>(null) }
    var restoreCheckpointMessage by remember { mutableStateOf<NativeChatMessage?>(null) }
    var restoreSnapshotRequest by remember { mutableStateOf<String?>(null) }
    var mergeWorktreeRequest by remember { mutableStateOf<String?>(null) }
    var removeWorktreePath by remember { mutableStateOf<String?>(null) }
    var previewAttachment by remember { mutableStateOf<NativeAttachment?>(null) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showWorkPanel by remember { mutableStateOf(false) }
    var showUserInputDrawer by remember(conversationListKey) { mutableStateOf(false) }
    var showPlanDecision by remember(conversationListKey) { mutableStateOf(false) }
    var showSkillPicker by remember { mutableStateOf(false) }
    var drawerSubagentRaw by remember { mutableStateOf<String?>(null) }
    val openSubagentDrawer = remember { { item: JSONObject -> drawerSubagentRaw = item.toString() } }
    val latestOnRetry = rememberUpdatedState(onRetry)
    val latestOnInputChange = rememberUpdatedState(onInputChange)
    val latestOnLoadSubagentHistory = rememberUpdatedState(onLoadSubagentHistory)
    val editItem: (NativeChatMessage) -> Unit = remember { { message -> editMessage = message } }
    val retryItem: (String) -> Unit = remember { { prompt -> latestOnRetry.value(prompt) } }
    val loadSubagentHistory: (String) -> Unit = remember { { threadId -> latestOnLoadSubagentHistory.value(threadId) } }
    val quoteMessage: (String) -> Unit = remember(state) {
        { quoted ->
            val block = quoted.lineSequence().joinToString("\n") { "> $it" }
            val input = listOf(state.input.trimEnd(), block, "")
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")
            latestOnInputChange.value(input)
        }
    }
    val pauseFollowForReasoning: () -> Unit = remember(conversationListKey) {
        { followPausedUntil = android.os.SystemClock.uptimeMillis() + 560L }
    }
    val previewMessageAttachment: (NativeAttachment) -> Unit = remember {
        { attachment -> previewAttachment = attachment }
    }
    val noOp: () -> Unit = remember { {} }
    LaunchedEffect(conversationListKey) { textSelectionActive = false }
    LaunchedEffect(state.pendingUserInputRequest) {
        showUserInputDrawer = state.pendingUserInputRequest.isNotBlank()
    }
    LaunchedEffect(state.pendingPlanImplementation, state.phase.active) {
        if (state.pendingPlanImplementation.isNotBlank() && !state.phase.active) showPlanDecision = true
        if (state.pendingPlanImplementation.isBlank()) showPlanDecision = false
    }
    // Observe drawer/list motion from a coroutine instead of reading drawer state in the root
    // composition. Opening the drawer must not recompose the complete chat document.
    LaunchedEffect(drawerState, listState) {
        snapshotFlow { drawerState.motionActive || listState.isScrollInProgress }
            .collect { active -> onUiMotionChanged(active) }
    }
    DisposableEffect(Unit) {
        onDispose { onUiMotionChanged(false) }
    }

    LaunchedEffect(listDragged, autoFollowEnabled) {
        if (!autoFollowEnabled) { followOutput = false; return@LaunchedEffect }
        if (listDragged) {
            followOutput = false
        } else if (!listState.canScrollForward) {
            followOutput = true
        }
    }

    val latestMessageId = state.messages.lastOrNull()?.id
    LaunchedEffect(latestMessageId) {
        if (latestMessageId != null && state.phase.active && autoFollowEnabled && followOutput && !listDragged) {
            // A new plan/assistant item is a structural list change, not just text growth.
            // Move to the new item once; the frame-based follow motor handles later deltas.
            withFrameNanos { }
            listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0), Int.MAX_VALUE)
        }
    }

    LaunchedEffect(imeVisible) {
        if (autoFollowEnabled && followOutput && !listDragged && state.messages.isNotEmpty()) {
            // WindowInsets.ime changes on every keyboard-animation frame. Keying this effect
            // by the raw inset forced a full LazyColumn jump/re-layout every frame. Keep the
            // input animation, then align the list once after its geometry settles.
            if (imeVisible) delay(180L)
            withFrameNanos { }
            listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0), Int.MAX_VALUE)
        }
    }

    // RikkaHub-style follow motor. A new animateScrollBy for every streamed batch causes
    // animations to queue and then jump when text arrives faster than they finish. Instead,
    // consume the measured bottom overflow once per display frame with a bounded velocity.
    // Small wrapped lines glide over a few frames; a large final Markdown reflow catches up
    // faster without teleporting. Manual dragging turns followOutput off above.
    LaunchedEffect(listState, followOutput, listDragged, state.conversationAnimationKey) {
        var previousFrame = withFrameNanos { it }
        var followVelocity = 0f
        while (isActive) {
            // Do not keep a frame callback alive for an idle conversation. Poll slowly
            // until generation/layout growth needs the smooth 60/120 Hz follow motor.
            if (!autoFollowEnabled || !followOutput || listDragged || state.messages.isEmpty() ||
                android.os.SystemClock.uptimeMillis() < followPausedUntil ||
                (!state.busy && !listState.canScrollForward)
            ) {
                delay(72L)
                followVelocity = 0f
                previousFrame = withFrameNanos { it }
                continue
            }
            val frame = withFrameNanos { it }
            val elapsedSeconds = ((frame - previousFrame).coerceAtMost(50_000_000L)) / 1_000_000_000f
            previousFrame = frame

            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull() ?: continue
            // Composer clearance is represented by afterContentPadding, so it must be part
            // of the distance or following stops while the last line is still hidden.
            val measuredOverflow = (last.offset + last.size + layout.afterContentPadding - layout.viewportEndOffset)
                .coerceAtLeast(0)
                .toFloat()
            val unseenRunway = if (last.index < layout.totalItemsCount - 1) with(density) { 72.dp.toPx() } else 0f
            val overflow = maxOf(measuredOverflow, unseenRunway)
            if (!listState.canScrollForward || overflow < 0.5f) {
                // No scroll work exists yet. Avoid retaining a 60/120 Hz callback merely
                // because the model is busy; the next 24 ms poll still reacts within ~2 frames.
                delay(24L)
                followVelocity = 0f
                previousFrame = withFrameNanos { it }
                continue
            }

            // Follow with a damped velocity rather than issuing a new scroll animation for
            // every streamed batch. This keeps the viewport moving continuously while the
            // response grows, and prevents the staircase/jump effect on slow devices.
            val targetVelocity = (overflow * 10f).coerceIn(0f, with(density) { 900.dp.toPx() })
            val acceleration = (1f - kotlin.math.exp(-12.0f * elapsedSeconds)).coerceIn(0f, 1f)
            followVelocity += (targetVelocity - followVelocity) * acceleration
            val distance = (followVelocity * elapsedSeconds).coerceAtMost(overflow)
            if (distance > 0.25f) listState.scrollBy(distance)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalOpenSubagentDrawer provides openSubagentDrawer,
        LocalTextSelectionActivityChanged provides onTextSelectionActivityChanged,
    ) {
    FcodeInteractiveDrawer(
        state = drawerState,
        gesturesEnabled = !textSelectionActive,
        scrimColor = MaterialTheme.colorScheme.scrim,
        drawerContent = {
            RikkaDrawerV2(
                    currentThreadId = pendingConversationThreadId ?: state.currentThreadId,
                    modelLabel = state.modelLabel,
                    conversations = state.conversations,
                    onSearch = { showConversationSearch = true },
                    onRenameConversation = { renameConversation = it },
                    onDeleteConversation = { deleteConversation = it },
                    onToggleFavorite = onToggleFavorite,
                    onResumeConversation = { threadId ->
                        if (threadId == state.currentThreadId) {
                            scope.launch { drawerState.close() }
                        } else if (pendingConversationThreadId == null) {
                            pendingConversationThreadId = threadId
                            scope.launch {
                                try {
                                    // Let the close transition own the UI thread. History route
                                    // and Markdown attachment begin only after the sheet settles.
                                    drawerState.close()
                                    withFrameNanos { }
                                    onResumeConversation(threadId)
                                } finally {
                                    pendingConversationThreadId = null
                                }
                            }
                        }
                    },
                    onClose = { scope.launch { drawerState.close() } },
                    onNewConversation = {
                        if (pendingConversationThreadId == null) {
                            pendingConversationThreadId = ""
                            scope.launch {
                                try {
                                    drawerState.close()
                                    withFrameNanos { }
                                    onNewConversation()
                                } finally {
                                    pendingConversationThreadId = null
                                }
                            }
                        }
                    },
                    onBackHome = onBackHome,
                    onOpenLegacyWebUi = onOpenLegacyWebUi,
                )
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) {
                AssistantBackdrop()
                Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    RikkaTopBar(
                        title = state.conversationTitle,
                        modelLabel = state.modelLabel,
                        onOpenDrawer = {
                            if (!textSelectionActive) {
                                scope.launch {
                                    if (!drawerState.targetOpen) drawerState.open()
                                }
                            }
                        },
                        onOpenWorkPanel = { showWorkPanel = true },
                        onSearch = { showMessageSearch = true },
                        onExport = { shareConversation(context, state.conversationTitle, state.messages) },
                        canExport = state.messages.any { it.role == NativeChatRole.USER || it.role == NativeChatRole.ASSISTANT },
                        onNewConversation = onNewConversation,
                    )
                },
                bottomBar = {},
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            val progress = conversationSurfaceProgress
                            alpha = 0.28f + 0.72f * progress
                            translationY = (1f - progress) * 10.dp.toPx()
                            scaleX = 0.992f + 0.008f * progress
                            scaleY = 0.992f + 0.008f * progress
                            transformOrigin = TransformOrigin.Center
                        },
                    ) {
                    if (state.historyLoading && state.messages.isEmpty() && state.currentThreadId.isNotBlank()) {
                        ConversationHistoryLoading(state.conversationTitle)
                    } else if (state.messages.isEmpty()) {
                        EmptyChatState(
                            ready = state.ready,
                            onPrompt = onInputChange,
                        )
                    } else {
                        // Do not cache by list size: streamed deltas replace the current
                        // message without changing size. A size-keyed snapshot permanently
                        // retained the first tiny delta and its streaming=true flag.
                        val visibleMessages = state.messages.takeLast(historyLimit)
                        val hiddenMessageCount = state.messages.size - visibleMessages.size
                        LaunchedEffect(conversationListKey, hiddenMessageCount) {
                            if (hiddenMessageCount <= 0) return@LaunchedEffect
                            snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { firstVisible ->
                                if (firstVisible <= 1 && historyLimit < state.messages.size) {
                                    // Keep the current stable-key anchor while prepending one small page.
                                    delay(80L)
                                    historyLimit = (historyLimit + CHAT_HISTORY_PAGE_SIZE).coerceAtMost(state.messages.size)
                                }
                            }
                        }
                        val liveAssistantId = if (state.phase == NativeTurnPhase.ANSWERING) visibleMessages.lastOrNull { it.role == NativeChatRole.ASSISTANT && it.streaming }?.id else null
                        // Assistant content changes on every stream batch, but retry targets only
                        // depend on message identity/role and user prompts. Keep a cheap structural
                        // fingerprint so a growing answer does not rebuild this map dozens of times/s.
                        var retryStructureKey = 1
                        visibleMessages.forEach { message ->
                            retryStructureKey = 31 * retryStructureKey + message.id.hashCode()
                            retryStructureKey = 31 * retryStructureKey + message.role.hashCode()
                            if (message.role == NativeChatRole.USER) {
                                retryStructureKey = 31 * retryStructureKey + message.content.hashCode()
                            }
                        }
                        val retryPrompts = remember(retryStructureKey, state.conversationAnimationKey) {
                            buildMap<String, String> {
                                var lastUser: String? = null
                                visibleMessages.forEach { message ->
                                    if (message.role == NativeChatRole.USER) lastUser = message.content
                                    else if (message.role == NativeChatRole.ASSISTANT || message.role == NativeChatRole.ERROR) lastUser?.let { put(message.id, it) }
                                }
                            }
                        }
                        val assistantChromeText = remember(
                            retryStructureKey, state.conversationAnimationKey, state.phase.active,
                        ) {
                            NativeAssistantChromePolicy.terminalAssistantText(visibleMessages, state.phase.active)
                        }
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalInteractiveScrollInProgress provides listDragged,
                        ) {
                            LazyColumn(
                            state = listState,
                            userScrollEnabled = !textSelectionActive,
                            modifier = Modifier.fillMaxSize().imePadding(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = inputBottomPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (hiddenMessageCount > 0) {
                                item(key = "history-loader", contentType = "history-loader") {
                                    TextButton(
                                        onClick = { historyLimit = (historyLimit + CHAT_HISTORY_PAGE_SIZE).coerceAtMost(state.messages.size) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("\u52a0\u8f7d\u66f4\u65e9\u6d88\u606f\uff08\u8fd8\u6709 $hiddenMessageCount \u6761\uff09") }
                                }
                            }
                            itemsIndexed(
                                items = visibleMessages,
                                key = { _, message -> message.id },
                                contentType = { _, message -> message.role },
                            ) { _, message ->
                                val previousUser = retryPrompts[message.id]
                                val editCallback = remember(message) { { editItem(message) } }
                                val retryCallback = remember(message.id, previousUser) {
                                    previousUser?.let { prompt -> { retryItem(prompt) } }
                                }
                                // LazyColumn item scopes already isolate recomposition by stable key.
                                // Avoid a graphicsLayer per message: it adds RenderNodes and GPU
                                // composition work without providing a persistent bitmap cache.
                                val stableMessageModifier = if (listScrolling && !touchExplorationEnabled) {
                                    // Accessibility geometry is restored immediately when scrolling
                                    // settles. In touch-exploration/read-screen mode we preserve full
                                    // semantics, accepting the extra geometry work. Otherwise,
                                    // avoid rebuilding every descendant bound at 120Hz while the
                                    // complete item is only translating vertically.
                                    Modifier.clearAndSetSemantics { }
                                } else Modifier
                                Box(
                                    Modifier.fillMaxWidth().then(stableMessageModifier),
                                ) {
                                    RikkaMessageItem(
                                        message = message,
                                        assistantActionText = assistantChromeText[message.id],
                                        chatState = state,
                                        liveState = state.takeIf { message.id == liveAssistantId },
                                        onEdit = editCallback,
                                        onRetry = retryCallback,
                                        onLoadSubagentHistory = loadSubagentHistory,
                                        onQuote = quoteMessage,
                                        onReasoningAutoCollapse = pauseFollowForReasoning,
                                        onPreviewAttachment = previewMessageAttachment,
                                    )
                                }
                            }
                            if (state.phase in setOf(NativeTurnPhase.WAITING, NativeTurnPhase.REASONING, NativeTurnPhase.TOOL_RUNNING) && liveAssistantId == null) {
                                item("processing") { ActiveProcessingPanel(state, false, loadSubagentHistory, noOp) }
                            }
                            // A temporary runway lets streamed lines grow upward instead of
                            // being pinned under the composer. It remains part of LazyColumn,
                            // so manual scrolling and follow cancellation keep normal semantics.
                            if (state.phase.active) {
                                item(key = "stream-runway", contentType = "stream-runway") {
                                    Spacer(Modifier.height(88.dp))
                                }
                            }
                            }
                        }
                        AnimatedVisibility(
                            visible = state.historyLoading && state.messages.isNotEmpty(),
                            modifier = Modifier.align(Alignment.Center),
                            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.96f),
                            exit = fadeOut(tween(90)) + scaleOut(targetScale = 0.98f),
                        ) {
                            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(nativeText(language, "\u6b63\u5728\u52a0\u8f7d\u5bf9\u8bdd\u2026", "Loading conversation\u2026"), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = showScrollToBottom,
                            modifier = Modifier.align(Alignment.BottomEnd).then(floatingInsetModifier).padding(end = 16.dp, bottom = inputBottomPadding + 16.dp),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    followOutput = true
                                    scope.launch {
                                        listState.animateScrollToItem(
                                            (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                                            Int.MAX_VALUE,
                                        )
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = CircleShape,
                            ) {
                                Icon(HugeIcons.ArrowDown01, "回到底部", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    }
                    if (state.pendingUserInputRequest.isNotBlank()) {
                        NativeUserInputBanner(
                            raw = state.pendingUserInputRequest,
                            drawerOpen = showUserInputDrawer,
                            onToggle = { showUserInputDrawer = !showUserInputDrawer },
                        )
                    }
                    if (state.activeGoalObjective.isNotBlank()) {
                        NativeGoalBanner(
                            objective = state.activeGoalObjective,
                            paused = state.activeGoalStatus != "active",
                            enabled = state.ready && !state.phase.active,
                            onEdit = { showGoalDialog = true },
                            onTogglePause = onToggleGoalPause,
                            onClear = onClearGoal,
                        )
                    }
                    RikkaChatInput(
                        value = state.input,
                        enabled = state.ready && !state.phase.active,
                        loading = state.phase.active,
                        modelLabel = state.modelLabel,
                        onModelClick = { showModelPicker = true },
                        effortOptions = state.modelOptions.firstOrNull { it.id == state.selectedModel }?.efforts.orEmpty().ifEmpty { listOf("none", "low", "medium", "high", "xhigh") },
                        selectedEffort = state.selectedEffort,
                        onEffortSelected = onEffortSelected,
                        selectedMode = state.selectedMode,
                        permissionMode = state.permissionMode,
                        onPermissionModeSelected = onPermissionModeChange,
                        activeGoal = state.activeGoalObjective,
                        onModeSelected = onModeChange,
                        onRequestGoal = { showGoalDialog = true },
                        onClearGoal = onClearGoal,
                        selectedSkills = state.selectedSkills,
                        onRemoveSkill = { state.selectedSkills.remove(it) },
                        attachments = state.attachments,
                        onMoreClick = { showFilesSheet = true },
                        onCompact = onCompact,
                        onRetryLast = { state.messages.lastOrNull { it.role == NativeChatRole.USER }?.content?.let(onRetry) },
                        onEditLast = { editMessage = state.messages.lastOrNull { it.role == NativeChatRole.USER } },
                        onRemoveAttachment = onRemoveAttachment,
                        onPreviewAttachment = { previewAttachment = it },
                        onValueChange = onInputChange,
                        onSend = { inputText ->
                            if (state.phase.active) {
                                onStop()
                            } else {
                                followOutput = true
                                followPausedUntil = 0L
                                scope.launch {
                                    listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0), Int.MAX_VALUE)
                                }
                                // TextFieldState is local to the composer; do not wait for its
                                // debounced draft mirror before sending the authoritative text.
                                onSend(inputText)
                            }
                        },
                        onHeightChanged = { inputHeightPx = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            }
        }
    }
    }
    drawerSubagentRaw?.let { raw ->
        runCatching { JSONObject(raw) }.getOrNull()?.let { item ->
            SubagentDrawer(
                item = item,
                state = state,
                onLoadHistory = onLoadSubagentHistory,
                onDismiss = { drawerSubagentRaw = null },
            )
        }
    }
    if (showSkillPicker) {
        SkillPickerDialog(
            skills = state.skills,
            selected = state.selectedSkills,
            onDismiss = { showSkillPicker = false },
            onSelect = { skill ->
                if (state.selectedSkills.none { it.path == skill.path }) state.selectedSkills.add(skill)
                showSkillPicker = false
            },
        )
    }
    if (showWorkPanel) {
        WorkPanelDialog(
            state = state,
            onLoadSubagentHistory = onLoadSubagentHistory,
            onEditGoal = { showGoalDialog = true },
            onClearGoal = onClearGoal,
            onExecutePlan = {
                if (!state.phase.active && state.ready) {
                    if (state.pendingPlanImplementation.isNotBlank()) {
                        onExecutePendingPlan()
                    } else {
                        onModeChange("default")
                        onSend(nativeText(language, "\u8bf7\u6309\u7167\u5de5\u4f5c\u9762\u677f\u4e2d\u7684\u8ba1\u5212\u5f00\u59cb\u6267\u884c\u3002", "Execute the plan shown in the work panel."))
                    }
                    showWorkPanel = false
                }
            },
            onGitAction = onGitAction,
            onSnapshotAction = onSnapshotAction,
            onWorktreeAction = onWorktreeAction,
            onRequestMergeWorktree = { request -> mergeWorktreeRequest = request; showWorkPanel = false },
            onRequestRemoveWorktree = { path -> removeWorktreePath = path; showWorkPanel = false },
            onRequestRestoreFile = { request ->
                restoreSnapshotRequest = request
                showWorkPanel = false
            },
            onRestoreCheckpoint = { checkpoint ->
                restoreCheckpointMessage = state.messages.firstOrNull { it.id == checkpoint.messageId }
                showWorkPanel = false
            },
            onEditCheckpoint = { checkpoint ->
                editMessage = state.messages.firstOrNull { it.id == checkpoint.messageId }
                showWorkPanel = false
            },
            onContinueTask = {
                onSend(nativeText(language, "\u8bf7\u4ece\u521a\u624d\u5931\u8d25\u6216\u4e2d\u65ad\u7684\u4f4d\u7f6e\u7ee7\u7eed\u6267\u884c\uff0c\u5148\u68c0\u67e5\u5f53\u524d\u5de5\u4f5c\u533a\u72b6\u6001\uff0c\u4e0d\u8981\u91cd\u590d\u5df2\u5b8c\u6210\u7684\u6b65\u9aa4\u3002", "Continue from the failed or interrupted point. Inspect the current workspace first and do not repeat completed steps."))
                showWorkPanel = false
            },
            onDismiss = { showWorkPanel = false },
        )
    }
    if (showGoalDialog) {
        GoalEditorDialog(
            initialValue = state.activeGoalObjective,
            enabled = state.ready && !state.phase.active,
            onDismiss = { showGoalDialog = false },
            onConfirm = { objective ->
                onSetGoal(objective)
                showGoalDialog = false
            },
        )
    }
    if (showUserInputDrawer && state.pendingUserInputRequest.isNotBlank()) {
        NativeUserInputDrawer(
            raw = state.pendingUserInputRequest,
            onAnswer = onAnswerUserInput,
            onDismiss = { showUserInputDrawer = false },
        )
    }
    if (showPlanDecision && state.pendingPlanImplementation.isNotBlank()) {
        NativePlanImplementationDialog(
            plan = state.pendingPlanImplementation,
            onExecute = {
                showPlanDecision = false
                onExecutePendingPlan()
            },
            onRevise = { feedback ->
                showPlanDecision = false
                onRevisePendingPlan(feedback)
            },
            onCancel = {
                showPlanDecision = false
                onCancelPendingPlan()
            },
        )
    }
    if (state.pendingApprovalRequest.isNotBlank()) {
        NativeApprovalDialog(
            raw = state.pendingApprovalRequest,
            onDecision = { decision -> onAnswerApproval(state.pendingApprovalRequest, decision) },
        )
    }
    previewAttachment?.let { attachment ->
        AttachmentPreviewDialog(attachment = attachment, onDismiss = { previewAttachment = null })
    }
    if (showMessageSearch) {
        MessageSearchDialog(
            messages = state.messages,
            onDismiss = { showMessageSearch = false },
            onSelect = { index ->
                showMessageSearch = false
                scope.launch { listState.animateScrollToItem(index) }
            },
        )
    }
    editMessage?.let { message ->
        EditMessageDialog(
            initialText = message.content,
            onDismiss = { editMessage = null },
            onConfirm = { value -> editMessage = null; onEditMessage(message.id, value) },
        )
    }
    restoreCheckpointMessage?.let { message ->
        RestoreCheckpointDialog(
            prompt = message.content,
            onDismiss = { restoreCheckpointMessage = null },
            onConfirm = {
                restoreCheckpointMessage = null
                onEditMessage(message.id, message.content)
            },
        )
    }
    restoreSnapshotRequest?.let { request ->
        val payload = remember(request) { runCatching { JSONObject(request) }.getOrNull() }
        RestoreSnapshotFileDialog(
            path = payload?.optString("path").orEmpty(),
            onDismiss = { restoreSnapshotRequest = null },
            onConfirm = {
                restoreSnapshotRequest = null
                onSnapshotAction("restore", request)
            },
        )
    }
    mergeWorktreeRequest?.let { request ->
        val payload = remember(request) { runCatching { JSONObject(request) }.getOrNull() }
        MergeWorktreeDialog(
            sourceBranch = payload?.optString("sourceBranch").orEmpty(),
            targetBranch = payload?.optString("targetBranch").orEmpty(),
            commits = payload?.optString("commits").orEmpty(),
            stat = payload?.optString("stat").orEmpty(),
            onDismiss = { mergeWorktreeRequest = null },
            onConfirm = { mergeWorktreeRequest = null; onWorktreeAction("merge", request) },
        )
    }
    removeWorktreePath?.let { path ->
        RemoveWorktreeDialog(
            path = path,
            onDismiss = { removeWorktreePath = null },
            onConfirm = { removeWorktreePath = null; onWorktreeAction("remove", path) },
        )
    }
    if (showConversationSearch) {
        ConversationSearchDialog(
            conversations = state.conversations,
            onDismiss = { showConversationSearch = false },
            onSelect = { onResumeConversation(it.threadId); showConversationSearch = false },
        )
    }
    renameConversation?.let { conversation ->
        RenameConversationDialog(
            conversation = conversation,
            onDismiss = { renameConversation = null },
            onConfirm = { title -> onRenameConversation(conversation, title); renameConversation = null },
        )
    }
    deleteConversation?.let { conversation ->
        FlClashAnimatedDialog(
            onDismissRequest = { deleteConversation = null },
            title = { Text("搜索对话") },
            text = { Text("确定从列表中删除“${conversation.title}”吗？") },
            confirmButton = { TextButton(onClick = { onDeleteConversation(conversation); deleteConversation = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConversation = null }) { Text("取消") } },
        )
    }
    if (showFilesSheet) {
        ModalBottomSheet(onDismissRequest = { showFilesSheet = false }) {
            RikkaFilesPicker(
                onPickImage = { showFilesSheet = false; onPickImages() },
                onPickFile = { showFilesSheet = false; onPickFiles() },
                onPickSkill = { showFilesSheet = false; showSkillPicker = true },
            )
        }
    }
    if (showModelPicker) {
        ChoiceDialog(
            title = nativeText(LocalNativeLanguage.current, "\u9009\u62e9\u6a21\u578b", "Select model"),
            options = state.modelOptions.map { it.id to it.name },
            selected = state.selectedModel,
            onDismiss = { showModelPicker = false },
            onSelect = { id ->
                onModelSelected(id)
                showModelPicker = false
            },
        )
    }

}

@Composable
private fun FlClashAnimatedDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    var windowVisible by remember { mutableStateOf(true) }
    val visibility = remember { MutableTransitionState(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        delay(18)
        visibility.targetState = true
    }
    fun dismissAnimated() {
        visibility.targetState = false
        scope.launch {
            delay(210)
            windowVisible = false
            onDismissRequest()
        }
    }
    if (windowVisible) {
        Dialog(onDismissRequest = { dismissAnimated() }, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = fadeIn(tween(120, easing = LinearEasing)) + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)),
                exit = fadeOut(tween(95, easing = LinearEasing)) + scaleOut(targetScale = 0.96f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 520f)),
            ) {
                Surface(
                    modifier = modifier.fillMaxWidth(0.88f).widthIn(min = 280.dp, max = 560.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                ) {
                    Column(modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)) {
                        if (title != null) Box(Modifier.padding(horizontal = 24.dp)) { title() }
                        if (title != null && text != null) Spacer(Modifier.height(16.dp))
                        if (text != null) Box(Modifier.padding(horizontal = 24.dp)) { text() }
                        Spacer(Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            if (dismissButton != null) dismissButton()
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSearchDialog(
    messages: List<NativeChatMessage>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val language = LocalNativeLanguage.current
    val results = remember(query, messages.size) {
        if (query.isBlank()) emptyList()
        else messages.mapIndexedNotNull { index, message ->
            if (message.role != NativeChatRole.ACTIVITY && message.content.contains(query.trim(), ignoreCase = true)) index to message else null
        }
    }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u641c\u7d22\u5f53\u524d\u5bf9\u8bdd", "Search conversation")) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(HugeIcons.Search01, null) },
                    placeholder = { Text(nativeText(language, "\u641c\u7d22\u6d88\u606f", "Search messages")) },
                )
                    if (query.isNotBlank()) {
                    Text(nativeText(language, "\u627e\u5230 ${results.size} \u6761\u7ed3\u679c", "${results.size} results found"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results, key = { it.first }) { (index, message) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(index) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(if (message.role == NativeChatRole.USER) nativeText(language, "\u7528\u6237", "User") else nativeText(language, "\u52a9\u624b", "Assistant"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(message.content.replace('\n', ' '), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u5173\u95ed", "Close")) } },
    )
}

@Composable
private fun NativeUserInputBanner(raw: String, drawerOpen: Boolean, onToggle: () -> Unit) {
    val language = LocalNativeLanguage.current
    val payload = remember(raw) { runCatching { JSONObject(raw) }.getOrNull() }
    val params = payload?.optJSONObject("params")
    val questions = params?.optJSONArray("questions")
    val count = questions?.length()?.coerceAtLeast(1) ?: 1
    val firstQuestion = questions?.optJSONObject(0)?.optString("question").orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.84f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HugeIcons.MessageAdd01, null, Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    nativeText(language, "\u5f85\u56de\u7b54 \u00b7 $count \u4e2a\u95ee\u9898", "Answer needed \u00b7 $count question${if (count == 1) "" else "s"}"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (firstQuestion.isNotBlank()) Text(firstQuestion, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Icon(HugeIcons.ArrowDown01, null, Modifier.size(16.dp).graphicsLayer { rotationZ = if (drawerOpen) 180f else 0f })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeUserInputDrawer(raw: String, onAnswer: (String) -> Unit, onDismiss: () -> Unit) {
    val language = LocalNativeLanguage.current
    val payload = remember(raw) { runCatching { JSONObject(raw) }.getOrNull() }
    val params = payload?.optJSONObject("params")
    val questionArray = params?.optJSONArray("questions") ?: JSONArray().also { array -> if (params != null) array.put(params) }
    val questions = remember(raw) {
        buildList { for (index in 0 until questionArray.length()) questionArray.optJSONObject(index)?.let(::add) }
    }
    var answers by remember(raw) { mutableStateOf(emptyMap<String, String>()) }
    val complete = questions.isNotEmpty() && questions.all { question ->
        val id = question.optString("id", "answer").ifBlank { "answer" }
        answers[id].orEmpty().isNotBlank()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(nativeText(language, "\u9700\u8981\u4f60\u7684\u56de\u7b54", "Your input is needed"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(nativeText(language, "\u8bf7\u56de\u7b54\u4ee5\u4e0b ${questions.size} \u4e2a\u95ee\u9898", "Answer the ${questions.size} question${if (questions.size == 1) "" else "s"} below"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(HugeIcons.Cancel01, nativeText(language, "\u5173\u95ed", "Close")) }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(questions, key = { index, question -> question.optString("id").ifBlank { index.toString() } }) { index, question ->
                    val questionId = question.optString("id", "answer").ifBlank { "answer" }
                    val header = question.optString("header").ifBlank { nativeText(language, "\u95ee\u9898 ${index + 1}", "Question ${index + 1}") }
                    val prompt = question.optString("question", question.optString("prompt", "")).ifBlank { nativeText(language, "\u6a21\u578b\u9700\u8981\u4f60\u7684\u56de\u7b54", "The model needs your input") }
                    val options = question.optJSONArray("options")
                    val allowOther = question.optBoolean("isOther", options == null || options.length() == 0)
                    val secret = question.optBoolean("isSecret", false)
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(header, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(prompt, style = MaterialTheme.typography.bodyLarge, lineHeight = 23.sp)
                            if (options != null) for (optionIndex in 0 until options.length()) {
                                val option = options.optJSONObject(optionIndex)
                                val label = option?.optString("label", option.optString("value", "")) ?: options.optString(optionIndex)
                                val description = option?.optString("description").orEmpty()
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable { answers = answers + (questionId to label) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (answers[questionId] == label) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = if (answers[questionId] == label) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)) else null,
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (allowOther || options == null || options.length() == 0) {
                                OutlinedTextField(
                                    value = answers[questionId].orEmpty().takeUnless { value -> options != null && (0 until options.length()).any { optionIndex -> options.optJSONObject(optionIndex)?.optString("label") == value } }.orEmpty(),
                                    onValueChange = { answers = answers + (questionId to it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(nativeText(language, "\u5176\u4ed6\u56de\u7b54", "Other answer")) },
                                    placeholder = { Text(nativeText(language, "\u8f93\u5165\u56de\u7b54", "Type your answer")) },
                                    visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                                    minLines = 1,
                                    maxLines = 4,
                                )
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    onAnswer(encodeNativeUserInputAnswers(answers))
                },
                enabled = complete,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(nativeText(language, "\u63d0\u4ea4\u5168\u90e8\u56de\u7b54", "Submit answers")) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun NativePlanImplementationDialog(plan: String, onExecute: () -> Unit, onRevise: (String) -> Unit, onCancel: () -> Unit) {
    val language = LocalNativeLanguage.current
    var feedback by remember(plan) { mutableStateOf("") }
    FlClashAnimatedDialog(
        onDismissRequest = onCancel,
        title = { Text(nativeText(language, "\u6267\u884c\u8fd9\u4e2a\u8ba1\u5212\uff1f", "Implement this plan?")) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Box(Modifier.padding(14.dp)) { DeferredHistoricalRichText(plan) }
                }
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(nativeText(language, "\u9700\u8981\u66f4\u6539\u7684\u5185\u5bb9", "Changes to make")) },
                    placeholder = { Text(nativeText(language, "\u4f8b\u5982\uff1a\u5148\u8865\u5145\u6d4b\u8bd5\uff0c\u4e0d\u8981\u4fee\u6539 API", "For example: add tests first and keep the API unchanged")) },
                    minLines = 2,
                    maxLines = 5,
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) }
                TextButton(enabled = feedback.isNotBlank(), onClick = { onRevise(feedback.trim()) }) { Text(nativeText(language, "\u66f4\u6539\u8ba1\u5212", "Change plan")) }
            }
        },
        confirmButton = { Button(onClick = onExecute) { Text(nativeText(language, "\u6267\u884c\u8ba1\u5212", "Implement plan")) } },
    )
}

@Composable
private fun NativeApprovalDialog(raw: String, onDecision: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    val payload = remember(raw) { runCatching { JSONObject(raw) }.getOrNull() }
    val method = payload?.optString("method").orEmpty()
    val params = payload?.optJSONObject("params") ?: JSONObject()
    val isCommand = method.contains("commandExecution") || method == "execCommandApproval"
    val isPermission = method.contains("permissions/requestApproval")
    val command = NativeCommandPresentation.rawCommand(params)
    val action = NativeCommandPresentation.action(params)
    val actionLabel = NativeCommandPresentation.label(action, language != "en")
    val reason = params.optString("reason")
    val cwd = params.optString("cwd")
    val available = params.optJSONArray("availableDecisions")
    val allowForSession = available == null || (0 until available.length()).any { index ->
        available.opt(index)?.toString()?.contains("acceptForSession") == true
    }
    val title = when {
        isCommand -> nativeText(language, "\u5141\u8bb8\u6267\u884c\u547d\u4ee4\uff1f", "Allow command?")
        isPermission -> nativeText(language, "\u5141\u8bb8\u989d\u5916\u6743\u9650\uff1f", "Allow additional permissions?")
        else -> nativeText(language, "\u5141\u8bb8\u4fee\u6539\u6587\u4ef6\uff1f", "Allow file changes?")
    }
    val summary = when {
        isCommand -> actionLabel
        isPermission -> nativeText(language, "\u6a21\u578b\u8bf7\u6c42\u6269\u5927\u5f53\u524d\u8bbf\u95ee\u8303\u56f4", "The model requests additional access")
        else -> nativeText(language, "\u6a21\u578b\u8bf7\u6c42\u5199\u5165\u5de5\u4f5c\u533a\u4ee5\u5916\u7684\u4f4d\u7f6e", "The model requests writes outside the workspace")
    }
    FlClashAnimatedDialog(
        onDismissRequest = { onDecision("decline") },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(summary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (reason.isNotBlank()) Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (command.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        SelectionContainer {
                            Text(
                                command.take(4_000),
                                Modifier.fillMaxWidth().padding(12.dp),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (cwd.isNotBlank()) Text(cwd, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isPermission) {
                    Text(params.optJSONObject("permissions")?.toString(2).orEmpty().take(2_000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    nativeText(language, "\u4ec5\u5728\u4f60\u7406\u89e3\u8be5\u64cd\u4f5c\u65f6\u5141\u8bb8\u3002\u9009\u62e9\u201c\u672c\u4f1a\u8bdd\u5141\u8bb8\u201d\u540e\uff0c\u76f8\u4f3c\u64cd\u4f5c\u672c\u6b21\u5bf9\u8bdd\u4e0d\u518d\u8be2\u95ee\u3002", "Only allow operations you understand. Session approval skips similar prompts for this conversation."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onDecision("decline") }) { Text(nativeText(language, "\u62d2\u7edd", "Deny")) }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (allowForSession) TextButton(onClick = { onDecision("acceptForSession") }) {
                    Text(nativeText(language, "\u672c\u4f1a\u8bdd\u5141\u8bb8", "Allow session"))
                }
                TextButton(onClick = { onDecision("accept") }) { Text(nativeText(language, "\u5141\u8bb8\u4e00\u6b21", "Allow once")) }
            }
        },
    )
}

@Composable
private fun EditMessageDialog(initialText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initialText) { mutableStateOf(initialText) }
    val language = LocalNativeLanguage.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u7f16\u8f91\u6d88\u606f", "Edit message")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("保存后将从这条消息重新生成后续内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), minLines = 3, maxLines = 10)
            }
        },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) { Text(nativeText(language, "\u4fdd\u5b58\u5e76\u91cd\u65b0\u751f\u6210", "Save and regenerate")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
    )
}

@Composable
private fun RestoreCheckpointDialog(prompt: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u4ece\u6b64\u68c0\u67e5\u70b9\u91cd\u65b0\u6267\u884c\uff1f", "Rerun from this checkpoint?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    nativeText(language, "\u6b64\u68c0\u67e5\u70b9\u4e4b\u540e\u7684\u5bf9\u8bdd\u8f6e\u6b21\u4f1a\u88ab\u56de\u6eda\uff0c\u7136\u540e\u91cd\u65b0\u63d0\u4ea4\u8fd9\u6761\u4efb\u52a1\u3002Codex \u4f1a\u6839\u636e\u5f53\u524d\u5de5\u4f5c\u533a\u518d\u6b21\u6267\u884c\u3002", "Later conversation turns will be rolled back and this task will be submitted again. Codex will rerun it against the current workspace."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(prompt.take(2_000), Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall, maxLines = 10, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        confirmButton = { Button(onClick = onConfirm) { Text(nativeText(language, "\u56de\u6eda\u5e76\u91cd\u65b0\u6267\u884c", "Rollback and rerun")) } },
    )
}

@Composable
private fun RestoreSnapshotFileDialog(path: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u6062\u590d\u8fd9\u4e2a\u6587\u4ef6\uff1f", "Restore this file?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    nativeText(language, "\u5f53\u524d\u5de5\u4f5c\u533a\u4e2d\u7684\u8be5\u6587\u4ef6\u5c06\u66ff\u6362\u4e3a\u5feb\u7167\u7248\u672c\u3002\u6062\u590d\u524d\u4f1a\u81ea\u52a8\u518d\u521b\u5efa\u4e00\u4efd\u5b89\u5168\u5feb\u7167\u3002\u5f53\u524d Git \u6682\u5b58\u533a\u4e0d\u4f1a\u88ab\u4fee\u6539\u3002", "The working-tree file will be replaced by the snapshot version. A safety snapshot is created first, and the Git index is left unchanged."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(path, Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        confirmButton = { Button(onClick = onConfirm) { Text(nativeText(language, "\u521b\u5efa\u5907\u4efd\u5e76\u6062\u590d", "Back up and restore")) } },
    )
}

@Composable
private fun MergeWorktreeDialog(sourceBranch: String, targetBranch: String, commits: String, stat: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u5408\u5e76\u9694\u79bb\u4efb\u52a1\uff1f", "Merge isolated task?")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(nativeText(language, "\u5c06 $sourceBranch \u5408\u5e76\u5230 $targetBranch\u3002\u5408\u5e76\u524d\u4f1a\u521b\u5efa\u9690\u85cf\u5b89\u5168\u5feb\u7167\uff1b\u82e5\u51fa\u73b0\u51b2\u7a81\uff0c\u4f1a\u81ea\u52a8\u53d6\u6d88\u5408\u5e76\u3002", "Merge $sourceBranch into $targetBranch. A hidden safety snapshot is created first; conflicted merges are aborted automatically."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (commits.isNotBlank()) {
                    Text(nativeText(language, "\u5f85\u5408\u5e76\u63d0\u4ea4", "Commits"), fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        SelectionContainer { Text(commits.take(12_000), Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (stat.isNotBlank()) {
                    Text(nativeText(language, "\u53d8\u66f4\u7edf\u8ba1", "Change summary"), fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        SelectionContainer { Text(stat.take(12_000), Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        confirmButton = { Button(onClick = onConfirm) { Text(nativeText(language, "\u521b\u5efa\u5907\u4efd\u5e76\u5408\u5e76", "Back up and merge")) } },
    )
}

@Composable
private fun RemoveWorktreeDialog(path: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u79fb\u9664 worktree\uff1f", "Remove worktree?")) },
        text = { Text(nativeText(language, "\u53ea\u4f1a\u79fb\u9664\u8fd9\u4e2a\u5e72\u51c0\u7684 worktree \u76ee\u5f55\uff0c\u5176 Git \u5206\u652f\u4f1a\u4fdd\u7559\u3002\n\n$path", "Only the clean worktree directory will be removed. Its Git branch will be kept.\n\n$path")) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        confirmButton = { Button(onClick = onConfirm) { Text(nativeText(language, "\u79fb\u9664 worktree", "Remove worktree")) } },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(options, key = { it.first }) { option ->
                    NavigationDrawerItem(
                        label = { Text(option.second) },
                        selected = option.first == selected,
                        onClick = { onSelect(option.first) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Modifier.liquidPress(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 360f),
        label = "liquidCardScale",
    )
    val targetX = if (pressed && measuredSize.width > 0) {
        val normalized = (pointer.x - measuredSize.width / 2f) / measuredSize.width
        9f * tanh(normalized * 1.8f)
    } else 0f
    val targetY = if (pressed && measuredSize.height > 0) {
        val normalized = (pointer.y - measuredSize.height / 2f) / measuredSize.height
        7f * tanh(normalized * 1.8f)
    } else 0f
    val translationX by animateFloatAsState(targetX, spring(dampingRatio = 0.56f, stiffness = 430f), label = "liquidCardX")
    val translationY by animateFloatAsState(targetY, spring(dampingRatio = 0.56f, stiffness = 430f), label = "liquidCardY")
    return this
        .onSizeChanged { measuredSize = it }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.translationX = translationX
            this.translationY = translationY
        }
        .semantics {
            role = Role.Button
            onClick { if (enabled) onClick(); enabled }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                pointer = down.position
                val start = down.position
                var moved = false
                var canceled = false
                var active = true
                while (active) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    pointer = change.position
                    if ((change.position - start).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (change.isConsumed && moved) canceled = true
                    active = change.pressed
                }
                pressed = false
                if (!moved && !canceled) onClick()
            }
        }
}

private fun shareConversation(context: android.content.Context, title: String, messages: List<NativeChatMessage>) {
    val body = buildString {
        append("# Fcode Codex 对话\n\n")
        append("导出时间：").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())).append("\n\n")
        messages.forEach { message ->
            when (message.role) {
                NativeChatRole.USER -> append("## 你\n\n").append(message.content).append("\n\n")
                NativeChatRole.ASSISTANT -> append("## 助手\n\n").append(message.content).append("\n\n")
                NativeChatRole.ERROR -> append("## 错误\n\n").append(message.content).append("\n\n")
                NativeChatRole.ACTIVITY -> Unit
            }
        }
    }.trim()
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/markdown")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Fcode Codex 对话")
        .putExtra(android.content.Intent.EXTRA_TEXT, body)
    context.startActivity(android.content.Intent.createChooser(intent, "导出对话"))
}

private data class StarterPrompt(val icon: ImageVector, val title: String, val description: String, val prompt: String)

@Composable
private fun ConversationHistoryLoading(title: String) {
    val language = LocalNativeLanguage.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            nativeText(language, "\u6b63\u5728\u8f7d\u5165\u5bf9\u8bdd\u2026", "Loading conversation\u2026"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyChatState(ready: Boolean, onPrompt: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    val prompts = remember {
        listOf(
            StarterPrompt(HugeIcons.Code, nativeText(language, "\u7406\u89e3\u9879\u76ee", "Understand project"), nativeText(language, "\u5206\u6790\u5f53\u524d\u5de5\u4f5c\u533a\u7ed3\u6784\u548c\u6838\u5fc3\u903b\u8f91", "Analyze workspace structure and core logic"), nativeText(language, "\u8bf7\u5206\u6790\u5f53\u524d\u9879\u76ee\u7684\u76ee\u5f55\u7ed3\u6784\u3002", "Analyze the current project structure.")),
            StarterPrompt(HugeIcons.Bug01, nativeText(language, "\u68c0\u67e5\u95ee\u9898", "Find problems"), nativeText(language, "\u67e5\u627e\u6f5c\u5728 bug\u3001\u5d29\u6e83\u548c\u5f02\u5e38\u8fb9\u754c", "Find bugs, crashes and edge cases"), nativeText(language, "\u8bf7\u68c0\u67e5\u5f53\u524d\u9879\u76ee\u4e2d\u53ef\u80fd\u7684 bug\u3002", "Check the current project for possible bugs.")),
            StarterPrompt(HugeIcons.MagicWand01, nativeText(language, "\u6539\u8fdb\u4f53\u9a8c", "Improve experience"), nativeText(language, "\u4f18\u5316 UI\u3001\u6027\u80fd\u548c\u7528\u6237\u4ea4\u4e92", "Improve UI, performance and interaction"), nativeText(language, "\u8bf7\u5ba1\u67e5\u5f53\u524d\u9879\u76ee\u7684\u7528\u6237\u4f53\u9a8c\u3002", "Review and improve the current user experience.")),
            StarterPrompt(HugeIcons.Files02, nativeText(language, "\u7ee7\u7eed\u5f00\u53d1", "Continue development"), nativeText(language, "\u8bfb\u53d6\u53d8\u66f4\u5e76\u5efa\u8bae\u4e0b\u4e00\u6b65", "Review changes and continue"), nativeText(language, "\u8bf7\u68c0\u67e5 Git \u53d8\u66f4\u548c\u9879\u76ee\u72b6\u6001\uff0c\u7136\u540e\u7ee7\u7eed\u4e0b\u4e00\u6b65\u3002", "Review Git changes and continue with the next step.")),
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(HugeIcons.Sparkles, null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(nativeText(language, "\u4eca\u5929\u60f3\u505a\u4ec0\u4e48\uff1f", "What would you like to do today?"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (ready) nativeText(language, "Codex \u5df2\u51c6\u5907\u597d\uff0c\u53ef\u4ee5\u4e0e\u4f60\u4e00\u8d77\u5904\u7406\u5f53\u524d\u9879\u76ee", "Codex is ready to work on your project") else nativeText(language, "\u6b63\u5728\u8fde\u63a5 Codex\u2026", "Connecting to Codex\u2026"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            prompts.forEach { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth().liquidPress(enabled = ready) { onPrompt(item.prompt) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) { Icon(item.icon, null, modifier = Modifier.size(19.dp)) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(HugeIcons.ArrowRight01, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RikkaTopBar(
    title: String,
    modelLabel: String,
    onOpenDrawer: () -> Unit,
    onOpenWorkPanel: () -> Unit,
    onSearch: () -> Unit,
    onExport: () -> Unit,
    canExport: Boolean,
    onNewConversation: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    TopAppBar(
        modifier = Modifier.statusBarsPadding(),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(HugeIcons.Menu03, contentDescription = "对话列表")
            }
        },
        title = {
            Column {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (fadeIn(tween(140, easing = LinearOutSlowInEasing)) +
                            slideInHorizontally(tween(180, easing = FastOutSlowInEasing)) { it / 12 })
                            .togetherWith(
                                fadeOut(tween(90, easing = LinearEasing)) +
                                    slideOutHorizontally(tween(120, easing = FastOutSlowInEasing)) { -it / 14 },
                            )
                    },
                    label = "conversationTitleTransition",
                ) { value ->
                    Text(value, maxLines = 1, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis)
                }
                if (LocalShowModelSubtitle.current) Text(
                    text = modelLabel.ifBlank { nativeText(language, "\u9ed8\u8ba4\u6a21\u578b", "Default model") },
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenWorkPanel) {
                Icon(HugeIcons.LeftToRightListBullet, contentDescription = nativeText(language, "\u5de5\u4f5c\u9762\u677f", "Work panel"))
            }
            IconButton(onClick = onNewConversation) {
                Icon(HugeIcons.MessageAdd01, contentDescription = "新对话")
            }
        },
    )
}

@Composable
private fun AssistantBackdrop() {
    FcodeChatBackdrop(Modifier.fillMaxSize())
}

@Composable
private fun RikkaEmptyState(
    status: String,
    ready: Boolean,
    onSuggestion: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        HugeIcons.Sparkles,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("有什么可以帮你？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("检查当前项目", "运行测试", "解释这段代码", "修复一个问题")) { text ->
                Surface(
                    modifier = Modifier.clickable(enabled = ready) { onSuggestion(text) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RikkaMessageItem(message: NativeChatMessage, assistantActionText: String?, chatState: NativeChatState, liveState: NativeChatState?, onEdit: () -> Unit, onRetry: (() -> Unit)?, onLoadSubagentHistory: (String) -> Unit, onQuote: (String) -> Unit, onReasoningAutoCollapse: () -> Unit = {}, onPreviewAttachment: (NativeAttachment) -> Unit = {}) {
    when (message.role) {
        NativeChatRole.USER -> {
            val implementsPlan = message.content.startsWith(NATIVE_IMPLEMENT_PLAN_DISPLAY_PREFIX)
            val displayText = if (implementsPlan) nativeText(LocalNativeLanguage.current, "\u662f\uff0c\u6267\u884c\u6b64\u8ba1\u5212", "Yes, implement this plan") else message.content
            RikkaUserMessage(displayText, message.skills, message.attachments, onEdit, onPreviewAttachment, editable = !implementsPlan)
        }
        NativeChatRole.ASSISTANT -> {
            val liveSnapshot = if (message.streaming && chatState.liveAssistantMessageId == message.id) {
                chatState.liveAssistantSnapshot
            } else null
            val historicalFileChanges = remember(message.id, chatState.conversationAnimationKey, chatState.messages.size) {
                associatedFileChangeItems(chatState.messages, message.id)
            }
            val liveFileChanges = liveState?.toolDetails?.mapNotNull { raw ->
                runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.optString("type") == "fileChange" }
            }.orEmpty()
            RikkaAssistantMessage(
                message.id,
                message.content,
                message.streaming,
                message.revealStartedAt,
                message.finalOnlyReveal,
                message.usage,
                assistantActionText,
                liveState,
                liveSnapshot,
                historicalFileChanges + liveFileChanges,
                onRetry,
                onLoadSubagentHistory,
                onQuote,
                onReasoningAutoCollapse,
            )
        }
        NativeChatRole.ACTIVITY -> RikkaActivityMessage(message, chatState, onLoadSubagentHistory)
        NativeChatRole.ERROR -> RikkaErrorMessage(message.content, onRetry)
    }
}

@Composable
private fun RikkaUserMessage(text: String, skills: List<NativeSkill>, attachments: List<NativeAttachment>, onEdit: () -> Unit, onPreviewAttachment: (NativeAttachment) -> Unit, editable: Boolean = true) {
    val language = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (attachments.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                attachments.forEach { attachment ->
                    Surface(
                        modifier = Modifier.widthIn(max = 250.dp).clickable { onPreviewAttachment(attachment) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (attachment.image) {
                                AttachmentThumbnail(attachment.path, Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)))
                            } else {
                                Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Icon(HugeIcons.Files02, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f, fill = false)) {
                                Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(formatFileSize(java.io.File(attachment.path).length()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (skills.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                skills.forEach { skill ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.82f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.Sparkles, null, Modifier.size(13.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(skill.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (text.isNotBlank()) Box {
            Surface(
                modifier = Modifier.widthIn(max = 360.dp).combinedClickable(onClick = {}, onLongClick = { menuExpanded = true }),
                shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                SelectionContainer {
                    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, letterSpacing = 0.1.sp)
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(nativeText(language, "\u590d\u5236", "Copy")) },
                    leadingIcon = { Icon(HugeIcons.Copy01, null, modifier = Modifier.size(18.dp)) },
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)); menuExpanded = false },
                )
                if (editable) DropdownMenuItem(
                    text = { Text(nativeText(language, "\u7f16\u8f91\u5e76\u91cd\u65b0\u751f\u6210", "Edit and regenerate")) },
                    leadingIcon = { Icon(HugeIcons.PencilEdit01, null, modifier = Modifier.size(18.dp)) },
                    onClick = { menuExpanded = false; onEdit() },
                )
            }
        }
        MessageActions(text = text, onEdit = onEdit.takeIf { editable })
    }
}


@Composable
private fun FinalOnlyAnswerReveal(
    text: String,
    revealStartedAt: Long,
    onQuoteSelection: ((String) -> Unit)? = null,
) {
    // Never blur a text document: blur promotes the whole answer to an offscreen layer and can
    // steal frames from navigation. A short compositor-only fade/translation keeps the reveal.
    if (!NativeUiRenderSafety.canAnimateDocument(text)) {
        RichResponseText(text, onQuoteSelection)
        return
    }
    val shouldAnimate = revealStartedAt > 0L && System.currentTimeMillis() - revealStartedAt < 2_000L
    var revealed by remember(revealStartedAt) { mutableStateOf(!shouldAnimate) }
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(
            durationMillis = NativeStreamRevealPolicy.DURATION_MS,
            easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f),
        ),
        label = "finalAnswerReveal",
    )
    val offsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }
    LaunchedEffect(revealStartedAt) { revealed = true }
    Box(
        Modifier.fillMaxWidth().graphicsLayer {
            alpha = 0.28f + 0.72f * progress
            translationY = offsetPx * (1f - progress)
        },
    ) {
        RichResponseText(text, onQuoteSelection)
    }
}

@Composable
private fun StreamingResponseText(
    messageId: String,
    text: String,
    streaming: Boolean,
    revealStartedAt: Long,
    finalOnlyReveal: Boolean,
    liveSnapshot: NativeStreamingMarkdownSnapshot? = null,
    onQuoteSelection: ((String) -> Unit)? = null,
    onLiveSnapshotPresented: ((Int) -> Unit)? = null,
) {
    if (finalOnlyReveal) {
        FinalOnlyAnswerReveal(text, revealStartedAt, onQuoteSelection)
        return
    }
    val generation = liveSnapshot?.sourceChars ?: text.length
    // Start at zero for the first published batch. Initializing this to generation made the first
    // visible answer look pre-existing, so its reveal range was empty and it appeared instantly.
    val previousLength = remember(messageId) { intArrayOf(0) }
    val tailStart = previousLength[0].coerceAtMost(generation)
    SideEffect {
        // Plain holder on purpose: this is next-frame bookkeeping, not UI state. A mutableState
        // write here caused a redundant recomposition that immediately discarded the real range.
        previousLength[0] = generation
        if (streaming && generation > 0) onLiveSnapshotPresented?.invoke(generation)
    }
    val accumulator = remember(messageId) { NativeStreamingMarkdownAccumulator() }
    val snapshot = liveSnapshot ?: remember(messageId, text, streaming) {
        accumulator.update(text, finished = !streaming)
    }

    val content: @Composable () -> Unit = {
        StreamingMarkdownSnapshotContent(
            snapshot = snapshot,
            tailStart = tailStart,
            generation = generation,
            streaming = streaming,
            onQuoteSelection = onQuoteSelection,
        )
    }
    if (streaming) {
        LiveAnswerViewport(generation = generation, content = content)
    } else {
        content()
    }
}

@Composable
private fun LiveAnswerViewport(generation: Int, content: @Composable () -> Unit) {
    if (!LocalFixedStreamingViewportEnabled.current) {
        // Optional natural-growth mode: let the outer chat list own all answer height and follow.
        content()
        return
    }
    // Once the answer reaches this height, its outer LazyColumn item stops growing. New text is
    // measured and followed inside this viewport, preventing every delta from shifting and
    // remeasuring the history list. The viewport is removed when generation completes.
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    LaunchedEffect(generation) {
        withFrameNanos { }
        if (scrollState.value != scrollState.maxValue) scrollState.scrollTo(scrollState.maxValue)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            // Programmatic tail following remains active, but gestures pass to the chat list.
            .verticalScroll(scrollState, enabled = false),
    ) {
        content()
    }
}

@Composable
private fun StreamingMarkdownSnapshotContent(
    snapshot: NativeStreamingMarkdownSnapshot,
    tailStart: Int,
    generation: Int,
    streaming: Boolean,
    onQuoteSelection: ((String) -> Unit)? = null,
) {
    val streamingFirstVisibleBlock = remember(snapshot.blocks, snapshot.tail.length, streaming) {
        if (!streaming) 0 else NativeStreamingMarkdownWindow.firstVisibleBlock(
            snapshot.blocks, snapshot.tail.length,
        )
    }
    val completedInitialBlock = remember(snapshot.blocks, snapshot.tail.length) {
        val totalChars = snapshot.stableChars + snapshot.tail.length
        if (totalChars < 24_000) 0 else NativeStreamingMarkdownWindow.firstVisibleBlock(
            snapshot.blocks, snapshot.tail.length,
        )
    }
    var completedFirstVisibleBlock by remember(snapshot.blocks) {
        mutableIntStateOf(completedInitialBlock)
    }
    LaunchedEffect(streaming, snapshot.blocks) {
        if (streaming || completedFirstVisibleBlock <= 0) return@LaunchedEffect
        // Parsing is already off-main-thread, but attaching every completed Markdown block in
        // one composition can still monopolize a frame. Prepend bounded batches so layout work
        // is amortized across frames; action chrome continues to use the untouched full text.
        while (completedFirstVisibleBlock > 0) {
            withFrameNanos { }
            var next = completedFirstVisibleBlock
            var batchChars = 0
            while (next > 0 && batchChars < 12_000) {
                next--
                batchChars += snapshot.blocks[next].text.length
            }
            completedFirstVisibleBlock = next
        }
    }
    val firstVisibleBlock = if (streaming) streamingFirstVisibleBlock else completedFirstVisibleBlock
    Column(modifier = Modifier.fillMaxWidth()) {
        if (firstVisibleBlock > 0) {
            Text(
                nativeText(
                    LocalNativeLanguage.current,
                    if (streaming) "\u8f83\u65e9\u5185\u5bb9\u5df2\u7a33\u5b9a\uff0c\u5b8c\u6574\u56de\u7b54\u5c06\u5728\u751f\u6210\u7ed3\u675f\u540e\u663e\u793a" else "\u6b63\u5728\u6574\u7406\u8f83\u65e9\u5185\u5bb9\u2026",
                    if (streaming) "Earlier content is stable; the full answer appears when generation finishes" else "Restoring earlier content...",
                ),
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
        snapshot.blocks.subList(firstVisibleBlock, snapshot.blocks.size).forEach { block ->
            androidx.compose.runtime.key(block.start, block.end) {
                if (streaming) {
                    // Do not create Markwon AndroidViews while tokens are arriving. Even with
                    // background parsing, applying spans and measuring each new TextView runs on
                    // the UI thread. Plain stable Compose chunks keep generation frame-friendly;
                    // completed output is upgraded to rich Markdown in bounded batches above.
                    StableLiveTextChunk(block.text, reasoning = false)
                } else {
                    StableStreamingMarkdownBlock(block.text, onQuoteSelection)
                }
            }
        }
        if (snapshot.tail.isNotEmpty()) {
            ChunkedLiveText(
                text = snapshot.tail,
                tailStart = (tailStart - snapshot.stableChars).coerceIn(0, snapshot.tail.length),
                generation = generation,
                reasoning = false,
                animateTail = streaming,
            )
        }
    }
}

@Composable
private fun StableStreamingMarkdownBlock(text: String, onQuoteSelection: ((String) -> Unit)? = null) {
    // Restartable boundary: an immutable completed block is skipped on later deltas while
    // Markwon parses it once in the background. Only the unfinished tail keeps changing.
    RichResponseText(text, onQuoteSelection)
}

@Composable
private fun RikkaAssistantMessage(
    messageId: String,
    text: String,
    streaming: Boolean,
    revealStartedAt: Long,
    finalOnlyReveal: Boolean,
    usage: NativeTurnUsage?,
    assistantActionText: String?,
    liveState: NativeChatState?,
    liveSnapshot: NativeStreamingMarkdownSnapshot?,
    fileChangeItems: List<JSONObject>,
    onRetry: (() -> Unit)?,
    onLoadSubagentHistory: (String) -> Unit,
    onQuote: (String) -> Unit,
    onReasoningAutoCollapse: () -> Unit = {},
) {
    val language = LocalNativeLanguage.current
    val context = LocalContext.current
    val showChrome = assistantActionText != null
    val hasVisibleContent = (liveSnapshot?.sourceChars ?: text.length) > 0
    val actionText = assistantActionText ?: text
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    val markLiveSnapshotPresented: (Int) -> Unit = remember(messageId, liveState) {
        { chars: Int ->
            if (liveState != null) liveState.markLiveAssistantPresented(messageId, chars)
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).combinedClickable(onClick = {}, onLongClick = { if (showChrome) menuExpanded = true }),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (liveState != null) {
                    ActiveProcessingPanel(liveState, hasVisibleContent, onLoadSubagentHistory, onReasoningAutoCollapse)
                    Spacer(Modifier.height(6.dp))
                }
                StreamingResponseText(
                    messageId = messageId,
                    text = text,
                    streaming = streaming,
                    revealStartedAt = revealStartedAt,
                    finalOnlyReveal = finalOnlyReveal,
                    liveSnapshot = liveSnapshot,
                    onQuoteSelection = onQuote,
                    onLiveSnapshotPresented = markLiveSnapshotPresented,
                )
            }
            // Generating status and final actions share one fixed-height slot. Switching content
            // uses alpha only, never expand/shrink, so completion cannot change message height.
            if (streaming || showChrome) Box(Modifier.fillMaxWidth().height(40.dp)) {
                androidx.compose.animation.Crossfade(
                    targetState = showChrome && !streaming && text.isNotBlank(),
                    animationSpec = tween(120, easing = LinearEasing),
                    label = "assistantChromeSlot",
                ) { actionsVisible ->
                    if (actionsVisible) {
                        MessageActions(text = actionText, onRetry = onRetry, allowShare = true)
                    } else if (streaming) {
                        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(7.dp))
                            Text(nativeText(language, "\u6b63\u5728\u751f\u6210", "Generating"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (LocalShowResponseStats.current && (streaming || usage != null)) {
                Box(Modifier.fillMaxWidth().height(32.dp)) {
                    if (!streaming && showChrome && usage != null) {
                        ResponseUsageFooterCompact(usage)
                    }
                }
            }
            val changedFiles = remember(fileChangeItems.map { it.toString() }) { extractChangedFiles(fileChangeItems) }
            if (!streaming && changedFiles.isNotEmpty()) {
                FileChangesCapsule(changedFiles)
            }
        }
        DropdownMenu(expanded = showChrome && menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(nativeText(language, "\u590d\u5236\u56de\u7b54", "Copy answer")) },
                leadingIcon = { Icon(HugeIcons.Copy01, null, modifier = Modifier.size(18.dp)) },
                onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(actionText)); menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text(nativeText(language, "\u5f15\u7528\u56de\u7b54", "Quote answer")) },
                leadingIcon = { Icon(HugeIcons.LeftToRightListBullet, null, modifier = Modifier.size(18.dp)) },
                onClick = { menuExpanded = false; onQuote(actionText) },
            )
            if (onRetry != null) DropdownMenuItem(
                text = { Text(nativeText(language, "\u91cd\u65b0\u751f\u6210", "Regenerate")) },
                leadingIcon = { Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(18.dp)) },
                onClick = { menuExpanded = false; onRetry() },
            )
            DropdownMenuItem(
                text = { Text(nativeText(language, "\u5206\u4eab\u56de\u7b54", "Share answer")) },
                leadingIcon = { Icon(HugeIcons.Share08, null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    menuExpanded = false
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, actionText)
                    context.startActivity(android.content.Intent.createChooser(intent, "分享回答"))
                },
            )
        }
    }
}

private data class ChangedFileEntry(val path: String, val operation: String)

@Composable
private fun FileOperationCapsule(file: ChangedFileEntry) {
    val language = LocalNativeLanguage.current
    val name = file.path.substringAfterLast('/').substringAfterLast('\\').ifBlank { file.path }
    val operation = when (file.operation) {
        "add" -> nativeText(language, "\u65b0\u5efa", "Added")
        "delete" -> nativeText(language, "\u5220\u9664", "Deleted")
        else -> nativeText(language, "\u7f16\u8f91", "Edited")
    }
    Surface(
        modifier = Modifier.widthIn(max = 520.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HugeIcons.Files02, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(7.dp))
            Text("$operation $name", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private fun associatedFileChangeItems(messages: List<NativeChatMessage>, assistantId: String): List<JSONObject> {
    val assistantIndex = messages.indexOfFirst { it.id == assistantId }
    if (assistantIndex <= 0) return emptyList()
    val result = mutableListOf<JSONObject>()
    var index = assistantIndex - 1
    while (index >= 0 && messages[index].role == NativeChatRole.ACTIVITY) {
        val content = messages[index].content
        if (content.startsWith("PROCESS2|")) {
            val payload = runCatching {
                JSONObject(String(Base64.decode(content.substringAfter('|'), Base64.DEFAULT), Charsets.UTF_8))
            }.getOrNull()
            val tools = payload?.optJSONArray("tools")
            if (tools != null) for (toolIndex in 0 until tools.length()) {
                val item = tools.optJSONObject(toolIndex)
                    ?: runCatching { JSONObject(tools.optString(toolIndex)) }.getOrNull()
                    ?: continue
                if (item.optString("type") == "fileChange") result.add(item)
            }
        }
        index--
    }
    return result
}

private fun collectAllFileChangeItems(state: NativeChatState): List<JSONObject> {
    val result = mutableListOf<JSONObject>()
    state.messages.forEach { message ->
        if (message.role != NativeChatRole.ACTIVITY || !message.content.startsWith("PROCESS2|")) return@forEach
        val payload = runCatching {
            JSONObject(String(Base64.decode(message.content.substringAfter('|'), Base64.DEFAULT), Charsets.UTF_8))
        }.getOrNull() ?: return@forEach
        val tools = payload.optJSONArray("tools") ?: return@forEach
        for (index in 0 until tools.length()) {
            val item = tools.optJSONObject(index) ?: runCatching { JSONObject(tools.optString(index)) }.getOrNull() ?: continue
            if (item.optString("type") == "fileChange") result.add(item)
        }
    }
    state.toolDetails.forEach { raw ->
        runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.optString("type") == "fileChange" }?.let(result::add)
    }
    return result.distinctBy { it.optString("id", it.optString("itemId", it.toString())) }
}

private fun extractChangedFiles(items: List<JSONObject>): List<ChangedFileEntry> {
    val entries = linkedMapOf<String, ChangedFileEntry>()
    fun add(pathValue: String, operation: String) {
        val path = pathValue.trim().trim('"', '\'', '`').removePrefix("a/").removePrefix("b/")
        if (path.isBlank() || path == "/dev/null") return
        entries[path] = ChangedFileEntry(path, operation)
    }
    items.forEach { item ->
        val detail = item.optString("changes").ifBlank { item.optString(NativeLargePayloadStore.PAYLOAD_PREVIEW) }
        listOf("path", "filePath", "file", "name").forEach { key -> item.optString(key).takeIf(String::isNotBlank)?.let { add(it, "edit") } }
        val changes = item.optJSONArray("changes")
        if (changes != null) for (index in 0 until changes.length()) {
            val change = changes.optJSONObject(index) ?: continue
            val path = listOf("path", "filePath", "file", "name").firstNotNullOfOrNull { key -> change.optString(key).takeIf(String::isNotBlank) }
            if (path != null) add(path, change.optString("type", "edit").lowercase())
        }
        Regex("(?m)^\\*\\*\\*\\s+(Update|Add|Delete) File:\\s*(.+)$", RegexOption.IGNORE_CASE).findAll(detail).forEach { match ->
            add(match.groupValues[2], match.groupValues[1].lowercase())
        }
        Regex("(?m)^(?:\\+\\+\\+|---)\\s+(?:[ab]/)?(.+)$").findAll(detail).forEach { match -> add(match.groupValues[1], "edit") }
    }
    return entries.values.toList()
}

@Composable
private fun FileChangesCapsule(files: List<ChangedFileEntry>) {
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = 0.88f, stiffness = 320f),
        label = "fileChangesArrow",
    )
    Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp).clickable { expanded = !expanded },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Files02, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text(
                    nativeText(language, "\u66f4\u6539\u4e86 ${files.size} \u4e2a\u6587\u4ef6", "${files.size} files changed"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(6.dp))
                Icon(HugeIcons.ArrowDown01, null, Modifier.size(14.dp).graphicsLayer { rotationZ = arrowRotation }, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        ReasoningCapsuleExpand(expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.66f),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    files.forEach { file ->
                        val name = file.path.substringAfterLast('/').substringAfterLast('\\').ifBlank { file.path }
                        val operation = when (file.operation) {
                            "add" -> nativeText(language, "\u65b0\u5efa", "Added")
                            "delete" -> nativeText(language, "\u5220\u9664", "Deleted")
                            else -> nativeText(language, "\u7f16\u8f91", "Edited")
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.Files02, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$operation $name", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                                if (file.path != name) Text(file.path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseUsageFooter(usage: NativeTurnUsage) {
    val language = LocalNativeLanguage.current
    val parts = remember(usage, language) {
        buildList {
            val prefix = if (usage.estimated) "≈" else ""
            if (usage.totalTokens > 0) add("$prefix${usage.totalTokens} tokens")
            if (usage.inputTokens > 0) add(nativeText(language, "输入 ${usage.inputTokens}", "${usage.inputTokens} input"))
            if (usage.outputTokens > 0) add(nativeText(language, "输出 ${usage.outputTokens}", "${usage.outputTokens} output"))
            usage.outputTokensPerSecond.takeIf { it > 0.0 }?.let { rate ->
                add(String.format(Locale.US, "%.1f tok/s", rate))
            }
            if (usage.durationMs > 0) {
                val seconds = usage.durationMs / 1000.0
                add(if (seconds < 10.0) String.format(Locale.US, "%.1fs", seconds) else String.format(Locale.US, "%.0fs", seconds))
            }
            if (usage.cachedInputTokens > 0) add(nativeText(language, "缓存 ${usage.cachedInputTokens}", "${usage.cachedInputTokens} cached"))
            if (usage.reasoningOutputTokens > 0) add(nativeText(language, "推理 ${usage.reasoningOutputTokens}", "${usage.reasoningOutputTokens} reasoning"))
        }.joinToString("  ·  ")
    }
    if (parts.isEmpty()) return
    Text(
        text = parts,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
        maxLines = 1,
    )
}

@Composable
private fun ResponseUsageFooterCompact(usage: NativeTurnUsage) {
    val language = LocalNativeLanguage.current
    val metrics = remember(usage, language) {
        buildList {
            val prefix = if (usage.estimated) "≈" else ""
            usage.outputTokensPerSecond.takeIf { it > 0.0 }?.let { rate ->
                add(nativeText(language, "速度", "Speed") to String.format(Locale.US, "%.1f tok/s", rate))
            }
            if (usage.outputTokens > 0) add(nativeText(language, "输出", "Output") to "$prefix${usage.outputTokens}")
            if (usage.totalTokens > 0) add(nativeText(language, "总计", "Total") to "$prefix${usage.totalTokens}")
            if (usage.durationMs > 0) {
                val seconds = usage.durationMs / 1000.0
                val duration = if (seconds < 10.0) String.format(Locale.US, "%.1fs", seconds)
                    else String.format(Locale.US, "%.0fs", seconds)
                add(nativeText(language, "用时", "Time") to duration)
            }
            if (usage.inputTokens > 0) add(nativeText(language, "输入", "Input") to "$prefix${usage.inputTokens}")
            if (usage.cachedInputTokens > 0) add(nativeText(language, "缓存", "Cached") to usage.cachedInputTokens.toString())
            if (usage.reasoningOutputTokens > 0) add(nativeText(language, "推理", "Reasoning") to usage.reasoningOutputTokens.toString())
        }
    }
    if (metrics.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        metrics.forEach { (label, value) ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f))
                    Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f))
                }
            }
        }
    }
}

@Composable
private fun MessageActions(text: String, onEdit: (() -> Unit)? = null, onRetry: (() -> Unit)? = null, allowShare: Boolean = false) {
    val language = LocalNativeLanguage.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.Copy01, "复制", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onEdit != null) IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.PencilEdit01, "编辑", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onRetry != null) IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.Refresh03, "重新生成", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (allowShare) IconButton(onClick = {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
            context.startActivity(android.content.Intent.createChooser(intent, "分享回答"))
        }, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.Share08, "分享", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun QElasticExpand(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f), clip = true)
            + fadeIn(tween(120, easing = LinearEasing)),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f), clip = true)
            + fadeOut(tween(90, easing = LinearEasing)),
    ) { content() }
}

@Composable
private fun SafeExpandableViewport(maxHeight: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clipToBounds()
            .verticalScroll(rememberScrollState()),
    ) {
        content()
    }
}

private data class LiveTextChunk(val start: Int, val text: String)

private fun splitLiveText(text: String, targetSize: Int = 520, maxSize: Int = 760): List<LiveTextChunk> {
    if (text.isEmpty()) return emptyList()
    val chunks = ArrayList<LiveTextChunk>((text.length / targetSize) + 1)
    var start = 0
    while (text.length - start > maxSize) {
        val target = (start + targetSize).coerceAtMost(text.length)
        val ceiling = (start + maxSize).coerceAtMost(text.length)
        // Prefer Markdown block boundaries so completed paragraphs, lists and code
        // fences become stable chunks. Only the final unfinished block is re-rendered.
        val paragraphEnd = text.indexOf("\n\n", target).let { if (it in target until ceiling) it + 2 else -1 }
        val fenceEnd = text.indexOf("\n```", target).let { if (it in target until ceiling) it + 4 else -1 }
        var end = listOf(paragraphEnd, fenceEnd).filter { it > 0 }.minOrNull() ?: -1
        if (end < 0) {
            end = text.lastIndexOf('\n', ceiling - 1).let { if (it > start + targetSize / 2) it + 1 else ceiling }
        }
        // Never split between a UTF-16 surrogate pair.
        if (end < text.length && end > start && Character.isHighSurrogate(text[end - 1])) end--
        chunks.add(LiveTextChunk(start, text.substring(start, end)))
        start = end
    }
    if (start < text.length) chunks.add(LiveTextChunk(start, text.substring(start)))
    return chunks
}

@Composable
private fun StableLiveTextChunk(text: String, reasoning: Boolean) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = if (reasoning) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        lineHeight = if (reasoning) 22.sp else 24.sp,
        letterSpacing = if (reasoning) 0.sp else 0.1.sp,
        color = if (reasoning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun FadingTailText(text: String, tailStart: Int, generation: Int, reasoning: Boolean) {
    if (!LocalStreamAnimationsEnabled.current || LocalInteractiveScrollInProgress.current) {
        // Direct manipulation wins: while the list is moving, publish text without another layer.
        StableLiveTextChunk(text, reasoning)
        return
    }
    val safeTailStart = tailStart.coerceIn(0, text.length)
    // Animate a continuous character-space reveal boundary. Retargeting a spring preserves the
    // current presentation value and velocity, so rapid token batches join one calm motion rather
    // than restarting the opacity of the complete sentence on every update.
    val revealPosition = remember {
        androidx.compose.animation.core.Animatable(safeTailStart.toFloat())
    }
    LaunchedEffect(generation, text.length) {
        if (revealPosition.value > text.length) revealPosition.snapTo(safeTailStart.toFloat())
        revealPosition.animateTo(
            targetValue = text.length.toFloat(),
            animationSpec = spring(
                dampingRatio = NativeStreamRevealPolicy.STREAM_DAMPING_RATIO,
                stiffness = NativeStreamRevealPolicy.STREAM_STIFFNESS,
                visibilityThreshold = NativeStreamRevealPolicy.STREAM_VISIBILITY_THRESHOLD,
            ),
        )
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val featherPx = with(density) { NativeStreamRevealPolicy.STREAM_FEATHER_DP.dp.toPx() }
    val baseColor = if (reasoning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val layoutHolder = remember { arrayOfNulls<TextLayoutResult>(1) }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            // Only the sole active tail owns an offscreen layer. DstOut erases exclusively the
            // unrevealed suffix; already presented words never change alpha or flash again.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val layout = layoutHolder[0] ?: return@drawWithContent
                val boundary = revealPosition.value.coerceIn(0f, text.length.toFloat())
                if (boundary < text.length - 0.001f && text.isNotEmpty()) {
                    val offset = boundary.toInt().coerceIn(0, text.lastIndex)
                    val fraction = boundary - offset
                    val line = layout.getLineForOffset(offset)
                    val nextOffset = (offset + 1).coerceAtMost(text.length)
                    val startX = layout.getHorizontalPosition(offset, usePrimaryDirection = true)
                    val nextLine = if (nextOffset < text.length) layout.getLineForOffset(nextOffset) else line
                    val nextX = if (nextLine == line) {
                        layout.getHorizontalPosition(nextOffset, usePrimaryDirection = true)
                    } else startX
                    val boundaryX = startX + (nextX - startX) * fraction
                    val lineTop = layout.getLineTop(line)
                    val lineBottom = layout.getLineBottom(line)
                    val direction = layout.getParagraphDirection(offset)

                    // Keep the transition as a soft wash around the newest glyph. The old mask
                    // erased from the exact glyph boundary and then erased every lower line at
                    // once, which produced a visible L/diagonal hard edge on wrapped sentences.
                    val wideFeather = featherPx * 1.35f
                    val verticalFeather = featherPx * 0.72f
                    val featherStart = (boundaryX - wideFeather).coerceAtLeast(0f)
                    val featherEnd = (boundaryX + wideFeather).coerceAtMost(size.width)
                    if (direction == ResolvedTextDirection.Ltr) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(featherEnd, lineTop),
                            size = Size((size.width - featherEnd).coerceAtLeast(0f), lineBottom - lineTop),
                            blendMode = BlendMode.DstOut,
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.42f to Color.Black.copy(alpha = 0.08f),
                                    0.68f to Color.Black.copy(alpha = 0.38f),
                                    1f to Color.Black,
                                ),
                                startX = featherStart,
                                endX = featherEnd,
                            ),
                            topLeft = Offset(featherStart, lineTop),
                            size = Size((featherEnd - featherStart).coerceAtLeast(0f), lineBottom - lineTop),
                            blendMode = BlendMode.DstOut,
                        )
                    } else {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(0f, lineTop),
                            size = Size(featherStart, lineBottom - lineTop),
                            blendMode = BlendMode.DstOut,
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Black,
                                    0.32f to Color.Black.copy(alpha = 0.38f),
                                    0.58f to Color.Black.copy(alpha = 0.08f),
                                    1f to Color.Transparent,
                                ),
                                startX = featherStart,
                                endX = featherEnd,
                            ),
                            topLeft = Offset(featherStart, lineTop),
                            size = Size((featherEnd - featherStart).coerceAtLeast(0f), lineBottom - lineTop),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                    if (lineBottom < size.height) {
                        val fadeHeight = verticalFeather.coerceAtMost(size.height - lineBottom)
                        if (fadeHeight > 0f) drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Black.copy(alpha = 0.42f),
                                    0.48f to Color.Black.copy(alpha = 0.82f),
                                    1f to Color.Black,
                                ),
                                startY = lineBottom,
                                endY = lineBottom + fadeHeight,
                            ),
                            topLeft = Offset(0f, lineBottom),
                            size = Size(size.width, fadeHeight),
                            blendMode = BlendMode.DstOut,
                        )
                        val hiddenStart = lineBottom + fadeHeight
                        if (hiddenStart < size.height) drawRect(
                            color = Color.Black,
                            topLeft = Offset(0f, hiddenStart),
                            size = Size(size.width, size.height - hiddenStart),
                            blendMode = BlendMode.DstOut,
                        )
                    }
                }
            },
        onTextLayout = { layoutHolder[0] = it },
        style = if (reasoning) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        lineHeight = if (reasoning) 22.sp else 24.sp,
        letterSpacing = if (reasoning) 0.sp else 0.1.sp,
        color = baseColor,
    )
}

@Composable
private fun ChunkedLiveText(text: String, tailStart: Int, generation: Int, reasoning: Boolean, animateTail: Boolean) {
    val chunks = remember(text, animateTail) {
        if (animateTail) splitLiveText(text, targetSize = 300, maxSize = 420) else splitLiveText(text)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        chunks.forEachIndexed { index, chunk ->
            androidx.compose.runtime.key(chunk.start) {
                val isTail = index == chunks.lastIndex
                if (isTail && animateTail) {
                    FadingTailText(
                        text = chunk.text,
                        tailStart = (tailStart - chunk.start).coerceIn(0, chunk.text.length),
                        generation = generation,
                        reasoning = reasoning,
                    )
                } else {
                    // Extracted into a restartable composable: unchanged completed chunks
                    // are skipped by Compose while only the final chunk is remeasured.
                    StableLiveTextChunk(chunk.text, reasoning)
                }
            }
        }
    }
}

@Composable
private fun LiveReasoningText(text: String) {
    val previousLength = remember { intArrayOf(0) }
    val tailStart = previousLength[0].coerceAtMost(text.length)
    SideEffect { previousLength[0] = text.length }
    ChunkedLiveText(
        text = text,
        tailStart = tailStart,
        generation = text.length,
        reasoning = true,
        // Reveal only the newly appended tail; completed chunks remain stable.
        animateTail = true,
    )
}

@Composable
private fun DeferredHistoricalRichText(text: String) {
    // Historical reasoning is operational prose, not answer Markdown. Creating a Markwon
    // AndroidView for every chunk caused deterministic hitches when those views first entered
    // LazyColumn prefetch. Keep it as reusable Compose text chunks with bounded measurement.
    val chunks = remember(text) { splitLiveText(text, targetSize = 900, maxSize = 1_300) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        chunks.forEach { chunk ->
            androidx.compose.runtime.key(chunk.start) {
                StableLiveTextChunk(chunk.text, reasoning = true)
            }
        }
    }
}

@Composable
private fun ActiveProcessingPanel(
    state: NativeChatState,
    answerStarted: Boolean,
    onLoadSubagentHistory: (String) -> Unit,
    onAutomaticCollapse: () -> Unit,
) {
    var elapsedSeconds by remember(state.turnStartedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(state.busy, state.turnStartedAt) {
        while (state.busy) {
            elapsedSeconds = ((System.currentTimeMillis() - state.turnStartedAt)
                .coerceAtLeast(0L) / 1000L)
            delay(1_000L)
        }
        elapsedSeconds = 0L
    }
    ProcessingPanel(
        state = state,
        elapsedSeconds = elapsedSeconds,
        answerStarted = answerStarted,
        onLoadSubagentHistory = onLoadSubagentHistory,
        onAutomaticCollapse = onAutomaticCollapse,
    )
}

@Composable
private fun ProcessingPanel(
    state: NativeChatState,
    elapsedSeconds: Long,
    answerStarted: Boolean,
    onLoadSubagentHistory: (String) -> Unit,
    onAutomaticCollapse: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    val showReasoning = LocalShowReasoning.current
    val liveCommand = remember(state.liveCommandJson) {
        state.liveCommandJson.takeIf(String::isNotBlank)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }
    val toolDetailCount = state.toolDetails.size
    val latestToolDetail = state.toolDetails.lastOrNull()
    val completedCommands = remember(toolDetailCount, latestToolDetail) {
        state.toolDetails.mapNotNull { raw ->
            runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.optString("type") == "commandExecution" }
        }
    }
    val completedFileChanges = remember(toolDetailCount, latestToolDetail) {
        state.toolDetails.mapNotNull { raw ->
            runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.optString("type") == "fileChange" }
        }
    }
    val liveChangedFiles = remember(completedFileChanges.map { it.toString() }) { extractChangedFiles(completedFileChanges) }
    val commandRunning = liveCommand != null
    val reasoningSeconds = if (state.reasoningCompletedAt > state.turnStartedAt) {
        (state.reasoningCompletedAt - state.turnStartedAt).coerceAtLeast(0L) / 1000L
    } else elapsedSeconds
    val activityRunning = state.phase in setOf(NativeTurnPhase.WAITING, NativeTurnPhase.REASONING, NativeTurnPhase.TOOL_RUNNING)
    val fallbackTitle = when {
        state.phase == NativeTurnPhase.FAILED -> nativeText(language, "\u751f\u6210\u5931\u8d25", "Generation failed")
        state.phase == NativeTurnPhase.WAITING && elapsedSeconds >= 12L -> nativeText(language, "\u7b49\u5f85\u6a21\u578b ${elapsedSeconds}s", "Waiting for model ${elapsedSeconds}s")
        state.phase == NativeTurnPhase.REASONING -> nativeText(language, "\u6b63\u5728\u601d\u8003 ${elapsedSeconds}s", "Thinking ${elapsedSeconds}s")
        state.reasoningComplete || state.phase == NativeTurnPhase.ANSWERING || state.phase == NativeTurnPhase.COMPLETED -> nativeText(language, "\u601d\u8003\u4e86 ${reasoningSeconds}s", "Thought for ${reasoningSeconds}s")
        else -> nativeText(language, "\u5904\u7406\u4e2d ${elapsedSeconds}s", "Processing ${elapsedSeconds}s")
    }
    val (reasoningTitle, reasoningBody) = reasoningTitleAndBody(state.reasoningText, LocalShowReasoningTitles.current)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReasoningCapsule(
            title = reasoningTitle ?: fallbackTitle,
            body = if (showReasoning) reasoningBody else "",
            running = activityRunning && !commandRunning,
            initiallyExpanded = state.phase.active && reasoningBody.isNotBlank() && showReasoning,
            reasoningLive = state.phase == NativeTurnPhase.REASONING && !state.reasoningComplete,
        )
        completedCommands.forEach { command -> CommandExecutionCard(command, compact = true) }
        if (liveCommand != null) CommandExecutionCard(liveCommand, running = true, liveOutput = state.commandText, compact = true)
        liveChangedFiles.forEach { file -> FileOperationCapsule(file) }
        if (state.liveSubagents.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                state.liveSubagents.forEach { raw ->
                    runCatching { JSONObject(raw) }.getOrNull()?.let { item ->
                        CollabAgentCapsule(item = item, state = state, onLoadHistory = onLoadSubagentHistory)
                    }
                }
            }
        }
    }
}

private fun reasoningTitleAndBody(source: String, titlesEnabled: Boolean): Pair<String?, String> {
    val text = source.trim()
    if (text.isEmpty() || !titlesEnabled) return null to text
    val firstRaw = text.lineSequence().firstOrNull()?.trim().orEmpty()
    val first = firstRaw.removePrefix("#").trim().removePrefix("**").removeSuffix("**").trim()
    val title = first.takeIf { it.length in 2..110 && !it.contains("```") }
    if (title == null) return null to text
    val body = if (text == firstRaw) "" else text.substringAfter('\n', "").trim()
    return title to body
}

@Composable
private fun ReasoningCapsule(
    title: String,
    body: String,
    running: Boolean,
    initiallyExpanded: Boolean = false,
    reasoningLive: Boolean = false,
) {
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(initiallyExpanded) { if (initiallyExpanded) expanded = true }
    val hasBody = body.isNotBlank()
    val scrollState = rememberScrollState()
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = 0.88f, stiffness = 320f),
        label = "reasoningCapsuleArrow",
    )
    LaunchedEffect(reasoningLive, body.length) {
        if (reasoningLive && expanded) {
            withFrameNanos { }
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            modifier = Modifier.widthIn(max = 680.dp).then(
                if (hasBody) Modifier.clickable { expanded = !expanded } else Modifier
            ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.7.dp)
                else Icon(HugeIcons.Zap, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text(
                    title,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasBody) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        HugeIcons.ArrowDown01,
                        if (expanded) nativeText(language, "\u6536\u8d77\u601d\u8003", "Collapse reasoning") else nativeText(language, "\u5c55\u5f00\u601d\u8003", "Expand reasoning"),
                        Modifier.size(14.dp).graphicsLayer { rotationZ = arrowRotation },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ReasoningCapsuleExpand(expanded && hasBody) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
            ) {
                Box(
                    Modifier.fillMaxWidth().heightIn(max = if (reasoningLive) 190.dp else 460.dp)
                        .verticalScroll(scrollState).padding(horizontal = 13.dp, vertical = 11.dp),
                ) {
                    if (reasoningLive) LiveReasoningText(body) else DeferredHistoricalRichText(body)
                }
            }
        }
    }
}

@Composable
private fun ReasoningCapsuleExpand(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 285f),
            clip = true,
        ) + fadeIn(tween(150, easing = LinearOutSlowInEasing)) +
            androidx.compose.animation.slideInVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 18 },
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 330f),
            clip = true,
        ) + fadeOut(tween(110, easing = LinearEasing)),
    ) { content() }
}

@Composable
private fun LegacyProcessingPanel(
    state: NativeChatState,
    elapsedSeconds: Long,
    answerStarted: Boolean,
    onLoadSubagentHistory: (String) -> Unit,
    onAutomaticCollapse: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    var userControlledExpansion by remember { mutableStateOf(false) }
    val language = LocalNativeLanguage.current
    val showReasoning = LocalShowReasoning.current
    val reasoningScrollState = rememberScrollState()
    val reasoningLive = expanded && state.phase == NativeTurnPhase.REASONING && !state.reasoningComplete
    val liveCommand = remember(state.liveCommandJson) {
        state.liveCommandJson.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
    }
    // Snapshot list equality keeps expensive JSON parsing isolated from reasoning/answer
    // revisions. Tool details change rarely; stream deltas can update dozens of times a second.
    val toolDetailCount = state.toolDetails.size
    val latestToolDetail = state.toolDetails.lastOrNull()
    val completedCommands = remember(toolDetailCount, latestToolDetail) {
        state.toolDetails.mapNotNull { raw ->
            runCatching { JSONObject(raw) }.getOrNull()?.takeIf { it.optString("type") == "commandExecution" }
        }
    }
    val commandCount = completedCommands.size + if (liveCommand != null) 1 else 0
    val commandRunning = liveCommand != null

    // Keep live reasoning in a bounded inner viewport. Only this viewport follows new text,
    // so the outer LazyColumn does not remeasure thousands of lines during generation.
    LaunchedEffect(reasoningLive) {
        if (!reasoningLive) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame).coerceAtMost(50_000_000L)) / 1_000_000_000f
            previousFrame = frame
            val remaining = (reasoningScrollState.maxValue - reasoningScrollState.value).coerceAtLeast(0).toFloat()
            if (remaining > 0.5f) {
                val interpolation = (1f - kotlin.math.exp(-9f * seconds)).coerceIn(0f, 1f)
                reasoningScrollState.scrollBy((remaining * interpolation).coerceAtLeast(0.5f).coerceAtMost(remaining))
            } else delay(32L)
        }
    }
    // Never auto-collapse while the turn is changing. Shrinking content above the answer
    // changes the LazyColumn item's height and makes the viewport jump. Expansion is now only
    // changed by an explicit user tap, where the resulting movement is expected.

    val reasoningSeconds = if (state.reasoningCompletedAt > state.turnStartedAt) {
        (state.reasoningCompletedAt - state.turnStartedAt).coerceAtLeast(0L) / 1000L
    } else elapsedSeconds
    val statusText = when {
        state.phase == NativeTurnPhase.FAILED -> nativeText(language, "\u751f\u6210\u5931\u8d25", "Generation failed")
        commandRunning -> nativeText(language, "\u6b63\u5728\u8fd0\u884c\u547d\u4ee4", "Running command")
        state.phase == NativeTurnPhase.WAITING && elapsedSeconds >= 12L -> nativeText(language, "\u7b49\u5f85\u6a21\u578b\u54cd\u5e94 ${elapsedSeconds}s", "Waiting for model ${elapsedSeconds}s")
        state.phase == NativeTurnPhase.REASONING -> nativeText(language, "\u6b63\u5728\u601d\u8003 ${elapsedSeconds}s", "Thinking ${elapsedSeconds}s")
        state.phase == NativeTurnPhase.TOOL_RUNNING && completedCommands.isNotEmpty() -> nativeText(language, "\u5df2\u6267\u884c\u547d\u4ee4", "Command completed")
        state.phase == NativeTurnPhase.TOOL_RUNNING -> nativeText(language, "\u6b63\u5728\u8c03\u7528\u5de5\u5177 ${elapsedSeconds}s", "Running tools ${elapsedSeconds}s")
        state.phase == NativeTurnPhase.ANSWERING || state.phase == NativeTurnPhase.COMPLETED || state.reasoningComplete -> nativeText(language, "\u601d\u8003\u4e86 ${reasoningSeconds}s", "Thought for ${reasoningSeconds}s")
        else -> nativeText(language, "\u5904\u7406\u4e2d ${elapsedSeconds}s", "Processing ${elapsedSeconds}s")
    }
    val activityRunning = state.phase in setOf(NativeTurnPhase.WAITING, NativeTurnPhase.REASONING, NativeTurnPhase.TOOL_RUNNING) &&
        !(state.phase == NativeTurnPhase.TOOL_RUNNING && !commandRunning && completedCommands.isNotEmpty())
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        tween(160, easing = FastOutSlowInEasing),
        label = "reasoningArrow",
    )

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    userControlledExpansion = true
                    expanded = !expanded
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activityRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(HugeIcons.Tick02, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(statusText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (commandCount > 0) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
                        Text(
                            nativeText(language, "$commandCount \u6761\u547d\u4ee4", "$commandCount commands"),
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    HugeIcons.ArrowDown01,
                    if (expanded) nativeText(language, "\u6536\u8d77", "Collapse") else nativeText(language, "\u5c55\u5f00", "Expand"),
                    Modifier.size(16.dp).graphicsLayer { rotationZ = arrowRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QElasticExpand(expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    if (state.phase.active) {
                        ProcessingPanelBody(
                            state = state,
                            liveCommand = liveCommand,
                            completedCommands = completedCommands,
                            showReasoning = showReasoning,
                            reasoningScrollState = reasoningScrollState,
                            reasoningLive = reasoningLive,
                            onLoadSubagentHistory = onLoadSubagentHistory,
                        )
                    } else {
                        SafeExpandableViewport(maxHeight = 520.dp) {
                            ProcessingPanelBody(
                                state = state,
                                liveCommand = liveCommand,
                                completedCommands = completedCommands,
                                showReasoning = showReasoning,
                                reasoningScrollState = reasoningScrollState,
                                reasoningLive = false,
                                onLoadSubagentHistory = onLoadSubagentHistory,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingPanelBody(
    state: NativeChatState,
    liveCommand: JSONObject?,
    completedCommands: List<JSONObject>,
    showReasoning: Boolean,
    reasoningScrollState: androidx.compose.foundation.ScrollState,
    reasoningLive: Boolean,
    onLoadSubagentHistory: (String) -> Unit,
) {
    if (showReasoning && state.reasoningText.isNotBlank()) {
        val modifier = if (state.phase.active) {
            Modifier.fillMaxWidth().heightIn(max = 156.dp).verticalScroll(reasoningScrollState)
        } else Modifier.fillMaxWidth()
        Box(Modifier.padding(top = 10.dp).then(modifier)) {
            if (reasoningLive) LiveReasoningText(state.reasoningText)
            else DeferredHistoricalRichText(state.reasoningText)
        }
    }
    if (completedCommands.isNotEmpty() || liveCommand != null) {
        CommandExecutionGroup(
            completedCommands = completedCommands,
            liveCommand = liveCommand,
            liveOutput = state.commandText,
        )
    }
    if (state.liveSubagents.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            state.liveSubagents.forEach { raw ->
                runCatching { JSONObject(raw) }.getOrNull()?.let { item ->
                    val thread = subagentThreadId(item)
                    CollabAgentCapsule(
                        item = item,
                        state = state,
                        onLoadHistory = onLoadSubagentHistory,
                    )
                }
            }
        }
    }
}

private data class MarkdownBlock(val code: Boolean, val text: String, val language: String = "")

private fun markdownBlocks(source: String): List<MarkdownBlock> {
    if (!source.contains("```")) return listOf(MarkdownBlock(false, source))
    return source.split("```").mapIndexedNotNull { index, part ->
        if (part.isEmpty()) null
        else if (index % 2 == 1) {
            val firstLine = part.substringBefore('\n').trim()
            val hasLanguage = part.contains('\n') && firstLine.matches(Regex("[A-Za-z0-9_+.#-]{1,24}"))
            MarkdownBlock(true, if (hasLanguage) part.substringAfter('\n').trimEnd() else part.trimEnd(), if (hasLanguage) firstLine else "")
        } else MarkdownBlock(false, part)
    }
}

@Composable
private fun RichResponseText(
    text: String,
    onQuoteSelection: ((String) -> Unit)? = null,
) {
    val initialDocument = remember(text) { NativeMarkdownDocumentParser.cached(text) }
    val document by produceState<NativeMarkdownDocument?>(initialDocument, text) {
        if (value == null) {
            value = withContext(Dispatchers.Default) { NativeMarkdownDocumentParser.parseCached(text) }
        }
    }
    val parsed = document
    if (parsed == null) {
        // Keep content visible while block segmentation runs off-main-thread.
        StableLiveTextChunk(text, reasoning = false)
        return
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        parsed.blocks.forEachIndexed { index, block ->
            androidx.compose.runtime.key(index, block.type, block.text.hashCode()) {
                when (block.type) {
                    NativeMarkdownBlockType.CODE -> RikkaCodeBlock(block.language, block.text, onQuoteSelection)
                    NativeMarkdownBlockType.INLINE_CODE -> FcodeStandaloneInlineCode(block.text, onQuoteSelection)
                    NativeMarkdownBlockType.TABLE -> block.table?.let { table ->
                        FcodeMarkdownTable(table, block.text)
                    }
                    NativeMarkdownBlockType.PROSE -> if (block.text.isNotBlank()) {
                        RichMarkdownText(block.text, onQuoteSelection)
                    }
                }
            }
        }
    }
}

@Composable
private fun RikkaCodeBlock(
    language: String,
    code: String,
    onQuoteSelection: ((String) -> Unit)? = null,
) {
    val languageUi = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    val colors = LocalFcodeMarkdownColors.current
    val lines = remember(code) { code.lines() }
    val collapsible = lines.size > 18 || code.length > 1800
    var expanded by remember(code) { mutableStateOf(!collapsible) }
    val visibleCode = remember(code, expanded) {
        if (expanded || !collapsible) code else lines.take(16).joinToString("\n")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.codeBlockBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 5.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    language.ifBlank { nativeText(languageUi, "\u4ee3\u7801", "Code") },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(nativeText(languageUi, "${lines.size} \u884c", "${lines.size} lines"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                IconButton(
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(HugeIcons.Copy01, nativeText(languageUi, "\u590d\u5236\u4ee3\u7801", "Copy code"), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = if (collapsible) 6.dp else 14.dp),
            ) {
                FcodeSelectableCodeText(
                    text = visibleCode,
                    modifier = Modifier.fillMaxWidth(),
                    onQuoteSelection = onQuoteSelection,
                )
            }
            if (collapsible) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (expanded) nativeText(languageUi, "\u6536\u8d77\u4ee3\u7801", "Collapse code") else nativeText(languageUi, "\u5c55\u5f00\u5168\u90e8 ${lines.size} \u884c", "Expand all ${lines.size} lines"))
                }
            }
        }
    }
}

@Composable
private fun FcodeSelectableCodeText(
    text: String,
    modifier: Modifier = Modifier,
    onQuoteSelection: ((String) -> Unit)? = null,
    textColor: Color? = null,
) {
    val language = LocalNativeLanguage.current
    val colors = LocalFcodeMarkdownColors.current
    val onSelectionActivityChanged = LocalTextSelectionActivityChanged.current
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FcodeSelectableTextView(context).apply {
                includeFontPadding = false
                setHorizontallyScrolling(true)
                setSingleLine(false)
                restingMovementMethod = android.text.method.ScrollingMovementMethod.getInstance()
                movementMethod = restingMovementMethod
                isHorizontalScrollBarEnabled = true
                scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setLineSpacing(resources.displayMetrics.density * 2f, 1.04f)
            }
        },
        onReset = { view -> view.finishSelection() },
        update = { view ->
            view.copyLabel = nativeText(language, "\u590d\u5236", "Copy")
            view.quoteLabel = nativeText(language, "\u5f15\u7528", "Quote")
            view.selectAllLabel = nativeText(language, "\u5168\u9009", "Select all")
            view.onSelectionActivityChanged = onSelectionActivityChanged
            view.onQuoteSelection = onQuoteSelection
            view.setTextColor((textColor ?: colors.codeBlockText).toArgb())
            if (view.text.toString() != text) {
                view.finishSelection()
                view.text = text
            }
        },
    )
}

@Composable
private fun FcodeStandaloneInlineCode(
    code: String,
    onQuoteSelection: ((String) -> Unit)? = null,
) {
    val colors = LocalFcodeMarkdownColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = colors.inlineCodeBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
    ) {
        FcodeSelectableCodeText(
            text = code,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            onQuoteSelection = onQuoteSelection,
            textColor = colors.inlineCodeText,
        )
    }
}

@Composable
private fun FcodeMarkdownTable(table: NativeMarkdownTable, source: String) {
    val language = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    val widths = remember(table) {
        List(table.columnCount) { column -> markdownTableColumnWidth(table, column).dp }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 13.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    nativeText(language, "\u8868\u683c", "Table"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    nativeText(language, "${table.rows.size} \u884c", "${table.rows.size} rows"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                )
                IconButton(
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(source)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        HugeIcons.Copy01,
                        nativeText(language, "\u590d\u5236\u8868\u683c", "Copy table"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column {
                    FcodeMarkdownTableRow(table.header, widths, header = true, alternate = false)
                    table.rows.forEachIndexed { index, row ->
                        FcodeMarkdownTableRow(row, widths, header = false, alternate = index % 2 == 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun FcodeMarkdownTableRow(
    cells: List<NativeMarkdownTableCell>,
    widths: List<androidx.compose.ui.unit.Dp>,
    header: Boolean,
    alternate: Boolean,
) {
    val background = when {
        header -> MaterialTheme.colorScheme.surfaceContainerHigh
        alternate -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.46f)
        else -> Color.Transparent
    }
    Row(Modifier.background(background)) {
        widths.forEachIndexed { column, width ->
            val cell = cells.getOrElse(column) { NativeMarkdownTableCell("") }
            val annotated = fcodeTableInlineText(cell.text)
            Box(
                Modifier
                    .width(width)
                    .padding(horizontal = 11.dp, vertical = if (header) 9.dp else 8.dp),
            ) {
                Text(
                    text = annotated,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    lineHeight = 19.sp,
                    textAlign = when (cell.alignment) {
                        NativeMarkdownAlignment.START -> TextAlign.Start
                        NativeMarkdownAlignment.CENTER -> TextAlign.Center
                        NativeMarkdownAlignment.END -> TextAlign.End
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
private fun fcodeTableInlineText(source: String): androidx.compose.ui.text.AnnotatedString {
    val codeBackground = LocalFcodeMarkdownColors.current.inlineCodeBackground
    val codeText = LocalFcodeMarkdownColors.current.inlineCodeText
    val linkColor = LocalFcodeMarkdownColors.current.link
    return remember(source, codeBackground, codeText, linkColor) {
        androidx.compose.ui.text.buildAnnotatedString {
            val pattern = Regex("""(`+)(.+?)\1|\*\*(.+?)\*\*|__(.+?)__|\[([^]]+)]\(([^)]+)\)""")
            var cursor = 0
            pattern.findAll(source).forEach { match ->
                if (match.range.first > cursor) append(source.substring(cursor, match.range.first))
                when {
                    match.groupValues[2].isNotEmpty() -> withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeText,
                        ),
                    ) { append(match.groupValues[2]) }
                    match.groupValues[3].isNotEmpty() || match.groupValues[4].isNotEmpty() -> withStyle(
                        androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold),
                    ) { append(match.groupValues[3].ifEmpty { match.groupValues[4] }) }
                    match.groupValues[5].isNotEmpty() -> withStyle(
                        androidx.compose.ui.text.SpanStyle(color = linkColor),
                    ) { append(match.groupValues[5]) }
                }
                cursor = match.range.last + 1
            }
            if (cursor < source.length) append(source.substring(cursor))
        }
    }
}

private fun markdownTableColumnWidth(table: NativeMarkdownTable, column: Int): Float {
    fun visualUnits(text: String): Float = text.codePoints().toArray().sumOf { codePoint ->
        if (codePoint > 0xFF) 1.0 else 0.58
    }.toFloat()
    var units = visualUnits(table.header.getOrNull(column)?.text.orEmpty())
    table.rows.forEach { row -> units = maxOf(units, visualUnits(row.getOrNull(column)?.text.orEmpty())) }
    return (units.coerceAtMost(24f) * 13f + 28f).coerceIn(104f, 268f)
}

private fun normalizeMarkdownLists(source: String): String =
    NativeMarkdownDocumentParser.normalizeProse(source)

private data class MarkdownRenderCacheKey(val themeKey: String, val text: String)

private object NativeMarkdownRenderer {
    private val renderers = LinkedHashMap<String, Markwon>()
    private val renderedCache = object : LinkedHashMap<MarkdownRenderCacheKey, android.text.Spanned>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MarkdownRenderCacheKey, android.text.Spanned>?): Boolean = size > 48
    }

    @Synchronized
    fun get(context: android.content.Context, colors: FcodeMarkdownColors): Markwon {
        renderers[colors.cacheKey]?.let { return it }
        val density = context.resources.displayMetrics.density
        val renderer = Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .blockMargin((12f * density).roundToInt())
                        .codeTextSize((14f * density).roundToInt())
                        .headingTextSizeMultipliers(floatArrayOf(1.46f, 1.28f, 1.16f, 1.08f, 1.02f, 1.0f))
                        .headingBreakHeight(0)
                        .listItemColor(colors.listMarker.toArgb())
                        .bulletWidth((8f * density).roundToInt())
                        .bulletListItemStrokeWidth((2f * density).roundToInt())
                        .blockQuoteColor(colors.quote.toArgb())
                        .blockQuoteWidth((4f * density).roundToInt())
                        .linkColor(colors.link.toArgb())
                        .isLinkUnderlined(false)
                        .codeBackgroundColor(colors.inlineCodeBackground.toArgb())
                        .codeTextColor(colors.inlineCodeText.toArgb())
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.setFactory(Code::class.java) { _, _ ->
                        RoundedInlineCodeSpan(
                            horizontalPadding = 5f * density,
                            verticalPadding = 2f * density,
                            radius = 8f * density,
                            backgroundColor = colors.inlineCodeBackground.toArgb(),
                            textColor = colors.inlineCodeText.toArgb(),
                        )
                    }
                    builder.setFactory(BlockQuote::class.java) { _, _ ->
                        RoundedBlockQuoteSpan(
                            margin = 16f * density,
                            barWidth = 4f * density,
                            radius = 2f * density,
                            color = colors.quote.toArgb(),
                        )
                    }
                }
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(42f) { builder -> builder.inlinesEnabled(true) })
            .build()
        if (renderers.size >= 8) renderers.remove(renderers.keys.first())
        renderers[colors.cacheKey] = renderer
        return renderer
    }

    fun cached(themeKey: String, text: String): android.text.Spanned? =
        synchronized(renderedCache) { renderedCache[MarkdownRenderCacheKey(themeKey, text)] }

    fun render(markwon: Markwon, themeKey: String, text: String): android.text.Spanned {
        val key = MarkdownRenderCacheKey(themeKey, text)
        synchronized(renderedCache) { renderedCache[key] }?.let { return it }
        val rendered = markwon.toMarkdown(normalizeMarkdownLists(text))
        synchronized(renderedCache) { renderedCache[key] = rendered }
        return rendered
    }
}

private class RoundedInlineCodeSpan(
    private val horizontalPadding: Float,
    private val verticalPadding: Float,
    private val radius: Float,
    private val backgroundColor: Int,
    private val textColor: Int,
) : android.text.style.ReplacementSpan() {
    override fun getSize(
        paint: android.graphics.Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: android.graphics.Paint.FontMetricsInt?,
    ): Int {
        val oldTypeface = paint.typeface
        paint.typeface = android.graphics.Typeface.MONOSPACE
        if (fm != null) {
            val source = paint.fontMetricsInt
            fm.ascent = (source.ascent - verticalPadding).roundToInt()
            fm.descent = (source.descent + verticalPadding).roundToInt()
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        val width = (paint.measureText(text, start, end) + horizontalPadding * 2f).roundToInt()
        paint.typeface = oldTypeface
        return width
    }

    override fun draw(
        canvas: android.graphics.Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: android.graphics.Paint,
    ) {
        val oldColor = paint.color
        val oldTypeface = paint.typeface
        paint.typeface = android.graphics.Typeface.MONOSPACE
        val width = paint.measureText(text, start, end) + horizontalPadding * 2f
        paint.color = backgroundColor
        canvas.drawRoundRect(
            x,
            top + verticalPadding * 0.35f,
            x + width,
            bottom - verticalPadding * 0.35f,
            radius,
            radius,
            paint,
        )
        paint.color = textColor
        canvas.drawText(text, start, end, x + horizontalPadding, y.toFloat(), paint)
        paint.color = oldColor
        paint.typeface = oldTypeface
    }
}

private class RoundedBlockQuoteSpan(
    private val margin: Float,
    private val barWidth: Float,
    private val radius: Float,
    private val color: Int,
) : android.text.style.LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = margin.roundToInt()

    override fun drawLeadingMargin(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: android.text.Layout,
    ) {
        val oldColor = paint.color
        val left = if (dir > 0) x.toFloat() else x - barWidth
        paint.color = color
        canvas.drawRoundRect(left, top.toFloat(), left + barWidth, bottom.toFloat(), radius, radius, paint)
        paint.color = oldColor
    }
}

private data class MarkdownViewBindingTag(
    val themeKey: String,
    val sourceHash: Int,
    val sourceLength: Int,
    val rendered: Boolean,
)

@Composable
private fun RichMarkdownText(
    text: String,
    onQuoteSelection: ((String) -> Unit)? = null,
) {
    val needsRichRenderer = remember(text) { NativeUiRenderSafety.requiresRichMarkdown(text) }
    val context = LocalContext.current
    val language = LocalNativeLanguage.current
    val colors = LocalFcodeMarkdownColors.current
    val themeKey = colors.cacheKey
    val onSelectionActivityChanged = LocalTextSelectionActivityChanged.current
    val markwon: Markwon? = if (needsRichRenderer) {
        remember(context.applicationContext, themeKey) {
            NativeMarkdownRenderer.get(context.applicationContext, colors)
        }
    } else null
    val parsed: android.text.Spanned? = if (markwon != null) {
        val result by produceState<android.text.Spanned?>(
            initialValue = NativeMarkdownRenderer.cached(themeKey, text),
            key1 = MarkdownRenderCacheKey(themeKey, text),
            key2 = markwon,
        ) {
            if (value == null) {
                value = withContext(Dispatchers.Default) {
                    // Malformed model output must fall back to the visible plain source.
                    runCatching { NativeMarkdownRenderer.render(markwon, themeKey, text) }.getOrNull()
                }
            }
        }
        result
    } else null
    val renderedTag = MarkdownViewBindingTag(themeKey, text.hashCode(), text.length, rendered = true)
    val plainTag = renderedTag.copy(rendered = false)
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { viewContext ->
            FcodeSelectableTextView(viewContext).apply {
                includeFontPadding = false
                textSize = 15.5f
                letterSpacing = if (resources.configuration.locales[0].language == "zh") 0f else 0.0025f
                setLineSpacing(resources.displayMetrics.density * 2f, 1.12f)
                val horizontal = (3f * resources.displayMetrics.density).roundToInt()
                val vertical = (2f * resources.displayMetrics.density).roundToInt()
                setPadding(horizontal, vertical, horizontal, vertical)
                breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
                hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
                linksClickable = true
                restingMovementMethod = android.text.method.LinkMovementMethod.getInstance()
                movementMethod = restingMovementMethod
                setTextColor(colors.text.toArgb())
                this.text = text
                tag = plainTag
            }
        },
        onReset = { view ->
            view.finishSelection()
            view.text = ""
            view.tag = null
        },
        update = { view ->
            view.copyLabel = nativeText(language, "\u590d\u5236", "Copy")
            view.quoteLabel = nativeText(language, "\u5f15\u7528", "Quote")
            view.selectAllLabel = nativeText(language, "\u5168\u9009", "Select all")
            view.onSelectionActivityChanged = onSelectionActivityChanged
            view.onQuoteSelection = onQuoteSelection
            view.setTextColor(colors.text.toArgb())
            val rendered = parsed
            if (rendered != null && markwon != null && view.tag != renderedTag) {
                view.finishSelection()
                val applied = runCatching { markwon.setParsedMarkdown(view, rendered) }.isSuccess
                if (applied) view.tag = renderedTag else {
                    view.text = text
                    view.tag = plainTag
                }
            } else if (rendered == null && view.tag != plainTag) {
                view.finishSelection()
                view.text = text
                view.tag = plainTag
            }
        },
    )
}

@Composable
private fun MarkdownLikeText(text: String) {
    val colors = LocalFcodeMarkdownColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        markdownBlocks(text).forEach { block ->
            if (block.code) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = colors.codeBlockBackground) {
                    Text(
                        block.text,
                        modifier = Modifier.padding(14.dp),
                        color = colors.codeBlockText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            } else {
                Text(block.text, color = colors.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun RikkaActivityMessage(message: NativeChatMessage, state: NativeChatState, onLoadSubagentHistory: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    val text = message.content
    if (text.startsWith(NATIVE_PROPOSED_PLAN_PREFIX)) {
        val planText = remember(text) { decodeNativeProposedPlan(text) }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HugeIcons.LeftToRightListBullet, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nativeText(language, "\u5efa\u8bae\u8ba1\u5212", "Proposed plan"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (message.streaming) nativeText(language, "\u6b63\u5728\u6574\u7406\u8ba1\u5212\u2026", "Drafting the plan...")
                            else nativeText(language, "\u8ba1\u5212\u5df2\u51c6\u5907\u597d", "Plan ready"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        )
                    }
                    if (message.streaming) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(HugeIcons.Tick02, null, Modifier.size(18.dp), tint = Color(0xFF5E8B68))
                }
                if (planText.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        if (message.streaming) {
                            StreamingResponseText(message.id, planText, true, message.revealStartedAt, false)
                        } else {
                            // Plans are commonly longer than the generic answer renderer's
                            // 1200-character rich-text cutoff. Always finish them as chunked
                            // Markdown so history reloads cannot degrade to literal # / - text.
                            DeferredHistoricalRichText(planText)
                        }
                    }
                }
            }
        }
        return
    }
    if (text.startsWith("PLAN_PANEL|")) {
        val language = LocalNativeLanguage.current
        val parts = text.split('|')
        val completed = parts.getOrNull(1) == "complete"
        val count = parts.getOrNull(2)?.toIntOrNull() ?: 0
        var expanded by remember(message.id) { mutableStateOf(true) }
        val planSteps = remember(state.planJson) { parsePlanItems(state.planJson) }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { expanded = !expanded },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.LeftToRightListBullet, null, Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(nativeText(language, "\u8ba1\u5212", "Plan"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (completed) nativeText(language, "\u8ba1\u5212\u5df2\u51c6\u5907\u597d${if (count > 0) " \u00b7 $count \u6b65" else ""}", "Plan ready${if (count > 0) " \u00b7 $count steps" else ""}")
                        else nativeText(language, "\u6b63\u5728\u51c6\u5907\u8ba1\u5212\u2026", "Preparing the plan\u2026"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (completed) Icon(HugeIcons.Tick02, null, Modifier.size(18.dp))
                else CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            }
            AnimatedVisibility(
                visible = expanded && (planSteps.isNotEmpty() || state.planExplanation.isNotBlank()),
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(180)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
            ) {
                Column(Modifier.padding(start = 42.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.planExplanation.isNotBlank()) {
                        DeferredHistoricalRichText(state.planExplanation)
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f))
                    }
                    planSteps.forEachIndexed { index, step ->
                        val label = step.optString("step").ifBlank { step.optString("title") }.ifBlank { step.optString("description") }
                        val status = step.optString("status", "pending")
                        if (label.isNotBlank()) Row(verticalAlignment = Alignment.Top) {
                            val mark = if (status == "completed") "✓" else "${index + 1}."
                            Text(mark, modifier = Modifier.width(24.dp), color = if (status == "completed") Color(0xFF5E8B68) else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            Box(Modifier.weight(1f)) { DeferredHistoricalRichText(label) }
                        }
                    }
                }
            }
        }
        return
    }
    if (text.startsWith("NOTICE|")) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(text.substringAfter('|'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        return
    }
    val payload = if (text.startsWith("PROCESS2|")) runCatching {
        JSONObject(String(Base64.decode(text.substringAfter('|'), Base64.DEFAULT), Charsets.UTF_8))
    }.getOrNull() else null
    if (payload != null) {
        HistoricalProcessCapsules(payload, state, onLoadSubagentHistory)
        return
    }
    val duration = payload?.optLong("duration")?.toString() ?: text.substringAfter("PROCESS|", "0").substringBefore('|')
    val reasoning = payload?.optString("reasoning").orEmpty()
    val command = payload?.optString("command").orEmpty()
    val reasoningUnavailable = payload?.optBoolean("reasoningUnavailable", false) == true
    val tools = payload?.optJSONArray("tools")
    // The live panel owns the expanded reasoning view. When completion inserts this
    // durable activity row, enter collapsed so the panel is not opened a second time.
    var expanded by remember(message.id) { mutableStateOf(false) }
    val historicalCommandCount = remember(text) {
        if (tools == null) 0 else (0 until tools.length()).count { index ->
            runCatching { JSONObject(tools.optString(index)) }.getOrNull()?.optString("type") == "commandExecution"
        }
    }
    val historicalArrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        tween(150, easing = FastOutSlowInEasing),
        label = "historicalReasoningArrow",
    )
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HugeIcons.Zap, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(nativeText(language, "\u601d\u8003\u4e86 ${duration}s", "Thought for ${duration}s"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (historicalCommandCount > 0) {
                Text(nativeText(language, "$historicalCommandCount \u6761\u547d\u4ee4", "$historicalCommandCount commands"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
            }
            Icon(HugeIcons.ArrowDown01, null, Modifier.size(15.dp).graphicsLayer { rotationZ = historicalArrowRotation }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        QElasticExpand(expanded) {
            SafeExpandableViewport(maxHeight = 520.dp) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        if (reasoning.isNotBlank()) Box(modifier = Modifier.padding(top = 10.dp)) { DeferredHistoricalRichText(reasoning) }
        else if (reasoningUnavailable) Text(
            nativeText(language, "\u6a21\u578b\u672a\u8fd4\u56de\u53ef\u89c1\u7684\u601d\u8003\u6458\u8981", "The model did not return a visible reasoning summary"),
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (command.isNotBlank()) ToolTextCard(nativeText(language, "\u547d\u4ee4\u6267\u884c", "Command execution"), command, false)
        val hasThreadBackedAgents = tools != null && (0 until tools.length()).any { toolIndex ->
            runCatching { JSONObject(tools.optString(toolIndex)) }.getOrNull()?.let(::subagentThreadId).orEmpty().isNotBlank()
        }
        if (tools != null) for (index in 0 until tools.length()) {
            val item = runCatching { JSONObject(tools.optString(index)) }.getOrNull() ?: continue
            val type = item.optString("type")
            if (type in setOf("collabAgentToolCall", "subAgentActivity") && hasThreadBackedAgents && subagentThreadId(item).isBlank()) continue
            val title = when (type) { "fileChange" -> nativeText(language, "\u6587\u4ef6\u4fee\u6539", "File change"); "mcpToolCall" -> "MCP tool"; "webSearch" -> nativeText(language, "\u7f51\u9875\u641c\u7d22", "Web search"); "collabAgentToolCall" -> nativeText(language, "\u5b50\u4ee3\u7406", "Subagent"); else -> type }
            val payloadRef = item.optString(NativeLargePayloadStore.PAYLOAD_REF)
            val detail = if (payloadRef.isNotBlank()) item.optString(NativeLargePayloadStore.PAYLOAD_PREVIEW) else when (type) {
                "commandExecution" -> buildString {
                    val commandValue = item.optString("command", "")
                    val outputValue = item.optString("aggregatedOutput", item.optString("output", ""))
                    if (commandValue.isNotBlank()) append("$ ").append(commandValue).append('\n')
                    if (outputValue.isNotBlank()) append(outputValue.trimEnd()).append('\n')
                    val code = item.opt("exitCode")
                    if (code != null && code != JSONObject.NULL) append("exit ").append(code)
                }
                "fileChange" -> item.optString("changes", item.toString(2))
                "mcpToolCall" -> buildString {
                    append(item.optString("tool", item.optString("name", "MCP")))
                    val arguments = item.opt("arguments")
                    if (arguments != null && arguments != JSONObject.NULL) append("\n\n参数\n").append(arguments.toString())
                    val output = item.optString("output", "")
                    if (output.isNotBlank()) append("\n\n结果\n").append(output)
                }
                "webSearch" -> buildString {
                    append(item.optString("query", item.toString(2)))
                    val output = item.optString("output", "")
                    if (output.isNotBlank()) append("\n\n").append(output)
                }
                "collabAgentToolCall", "subAgentActivity" -> item.optString("detail", item.toString(2))
                else -> item.toString(2)
            }
            if (type == "commandExecution") CommandExecutionCard(item)
            else if (type == "collabAgentToolCall" || type == "subAgentActivity") {
                val thread = subagentThreadId(item)
                CollabAgentCapsule(
                    item = item,
                    state = state,
                    onLoadHistory = onLoadSubagentHistory,
                )
            }
            else ToolTextCard(title, detail, type == "fileChange", payloadRef)
        }
                }
            }
        }
    }
}

@Composable
private fun HistoricalProcessCapsules(
    payload: JSONObject,
    state: NativeChatState,
    onLoadSubagentHistory: (String) -> Unit,
) {
    val language = LocalNativeLanguage.current
    val duration = payload.optLong("duration", 0L)
    val reasoning = payload.optString("reasoning")
    val (reasoningTitle, reasoningBody) = reasoningTitleAndBody(reasoning, LocalShowReasoningTitles.current)
    val tools = payload.optJSONArray("tools")
    val items = remember(payload.toString()) {
        buildList {
            if (tools != null) for (index in 0 until tools.length()) {
                val item = tools.optJSONObject(index)
                    ?: runCatching { JSONObject(tools.optString(index)) }.getOrNull()
                    ?: continue
                add(item)
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (reasoning.isNotBlank() || duration > 0L) ReasoningCapsule(
            title = reasoningTitle ?: if (duration > 0) nativeText(language, "\u601d\u8003\u4e86 ${duration}s", "Thought for ${duration}s") else nativeText(language, "\u601d\u8003", "Reasoning"),
            body = if (LocalShowReasoning.current) reasoningBody else "",
            running = false,
            initiallyExpanded = false,
        )
        items.filter { it.optString("type") == "commandExecution" }.forEach { item ->
            CommandExecutionCard(item, compact = true)
        }
        extractChangedFiles(items.filter { it.optString("type") == "fileChange" }).forEach { file ->
            FileOperationCapsule(file)
        }
        items.filter { it.optString("type") !in setOf("commandExecution", "fileChange") }.forEach { item ->
            when (item.optString("type")) {
                "collabAgentToolCall", "subAgentActivity" -> CollabAgentCapsule(item, state, onLoadSubagentHistory)
                "mcpToolCall" -> ToolTextCard(
                    item.optString("tool", item.optString("name", "MCP")),
                    item.optString("output", item.optString("detail", "")),
                    false,
                    item.optString(NativeLargePayloadStore.PAYLOAD_REF),
                )
                "webSearch" -> ToolTextCard(
                    item.optString("query", nativeText(language, "\u7f51\u9875\u641c\u7d22", "Web search")),
                    item.optString("output", ""),
                    false,
                    item.optString(NativeLargePayloadStore.PAYLOAD_REF),
                )
            }
        }
    }
}

private fun jsonText(item: JSONObject, vararg keys: String): String {
    keys.forEach { key ->
        if (item.has(key) && !item.isNull(key)) {
            val value = item.optString(key, "").trim()
            if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) return value
        }
    }
    return ""
}

private fun subagentThreadId(item: JSONObject): String {
    jsonText(item, "agentThreadId").takeIf { it.isNotBlank() }?.let { return it }
    val receivers = item.optJSONArray("receiverThreadIds") ?: return ""
    for (index in 0 until receivers.length()) {
        if (!receivers.isNull(index)) {
            val value = receivers.optString(index, "").trim()
            if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) return value
        }
    }
    return ""
}

@Composable
private fun CollabAgentCapsule(
    item: JSONObject,
    state: NativeChatState,
    onLoadHistory: (String) -> Unit,
) {
    // Details are hosted once at screen level. Keeping a Dialog and navigation state inside
    // every historical message retained a large amount of dormant composition state.
    val openDrawer = LocalOpenSubagentDrawer.current
    val name = subagentName(item)
    val status = subagentStatusLabel(resolvedSubagentStatus(state, item), LocalNativeLanguage.current)
    Surface(
        modifier = Modifier.padding(top = 8.dp).clickable {
            openDrawer(item)
            val thread = subagentThreadId(item)
            if (thread.isNotBlank()) onLoadHistory(thread)
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HugeIcons.Sparkles, null, Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.68f))
            Spacer(Modifier.width(5.dp))
            Icon(HugeIcons.ArrowRight01, null, Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubagentDrawer(
    item: JSONObject,
    state: NativeChatState,
    onLoadHistory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialId = subagentKey(item)
    val agents = remember(state.liveSubagents.toList(), item.toString()) { collectSubagentItems(state, item) }
    var selectedId by remember(initialId) { mutableStateOf<String?>(initialId) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedId != null) {
                    IconButton(onClick = { selectedId = null }) {
                        Icon(HugeIcons.ArrowRight01, "\u8fd4\u56de\u5b50\u4ee3\u7406\u5217\u8868", Modifier.graphicsLayer { rotationZ = 180f })
                    }
                } else Spacer(Modifier.width(48.dp))
                Text(
                    if (selectedId == null) "\u5b50\u4ee3\u7406" else subagentName(agents.firstOrNull { subagentKey(it) == selectedId } ?: item),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) { Icon(HugeIcons.Cancel01, "\u5173\u95ed") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                val selected = selectedId
                if (selected == null) {
                    SubagentOverview(state, agents) { chosen ->
                        selectedId = subagentKey(chosen)
                        subagentThreadId(chosen).takeIf { it.isNotBlank() }?.let(onLoadHistory)
                    }
                } else {
                    val chosen = agents.firstOrNull { subagentKey(it) == selected } ?: item
                    val thread = subagentThreadId(chosen)
                    SubagentDetail(
                        item = chosen,
                        history = state.subagentHistoryRefs[thread],
                        historyLoading = thread in state.loadingSubagentHistories,
                        historyError = state.subagentHistoryErrors[thread],
                        status = resolvedSubagentStatus(state, chosen),
                        onRetryHistory = { if (thread.isNotBlank()) onLoadHistory(thread) },
                    )
                }
            }
        }
    }
}

private fun subagentKey(item: JSONObject): String = subagentThreadId(item).ifBlank {
    jsonText(item, "id", "callId", "tool").ifBlank { item.toString().hashCode().toString() }
}

private fun subagentAliases(item: JSONObject): Set<String> = buildSet {
    subagentThreadId(item).takeIf { it.isNotBlank() }?.let(::add)
    listOf("id", "callId", "eventId").forEach { key -> jsonText(item, key).takeIf { it.isNotBlank() }?.let(::add) }
}

private fun mergeSubagentItems(existing: JSONObject, incoming: JSONObject): JSONObject {
    val merged = runCatching { JSONObject(existing.toString()) }.getOrElse { JSONObject() }
    incoming.keys().forEach { key ->
        val value = incoming.opt(key)
        if (value != null && value != JSONObject.NULL && (!(value is String) || value.isNotBlank())) merged.put(key, value)
    }
    return merged
}

private fun subagentName(item: JSONObject): String {
    val id = subagentThreadId(item)
    val fallback = if (id.isNotBlank()) "\u5b50\u4ee3\u7406 ${id.take(6)}" else "\u5b50\u4ee3\u7406"
    val explicit = jsonText(item, "agentName", "agentNickname", "nickname", "agent")
    if (explicit.isNotBlank() && !explicit.equals("subAgentActivity", true)) return explicit.substringAfterLast('/')
    val pathName = jsonText(item, "agentPath").substringAfterLast('/').trim()
    return pathName.ifBlank { fallback }
}

private fun normalizedSubagentStatus(value: String): String = when (value.trim().lowercase()) {
    "inprogress", "in_progress", "running", "started", "working" -> "working"
    "waiting", "pending", "queued" -> "waiting"
    "failed", "error" -> "failed"
    "cancelled", "canceled", "stopped", "interrupted" -> "stopped"
    "done", "complete", "completed", "success" -> "done"
    else -> "waiting"
}

private fun resolvedSubagentStatus(state: NativeChatState, item: JSONObject): String {
    val thread = subagentThreadId(item)
    return state.subagentStatuses[thread]?.let(::normalizedSubagentStatus)
        ?: normalizedSubagentStatus(jsonText(item, "status"))
}

private fun subagentStatusLabel(status: String, language: String): String = when (status) {
    "working" -> nativeText(language, "\u5904\u7406\u4e2d", "Working")
    "waiting" -> nativeText(language, "\u7b49\u5f85\u4e2d", "Waiting")
    "failed" -> nativeText(language, "\u5931\u8d25", "Failed")
    "stopped" -> nativeText(language, "\u5df2\u505c\u6b62", "Stopped")
    else -> nativeText(language, "\u5b8c\u6210", "Completed")
}

private fun isSubagentItem(item: JSONObject): Boolean = item.optString("type") in setOf("collabAgentToolCall", "subAgentActivity")

private fun isSubagentCandidate(item: JSONObject): Boolean {
    if (!isSubagentItem(item)) return false
    if (subagentThreadId(item).isNotBlank()) return true
    val tool = jsonText(item, "tool", "name").lowercase()
    return tool.contains("spawn")
}

private fun collectAllSubagentItems(state: NativeChatState): List<JSONObject> {
    val result = ArrayList<JSONObject>()
    fun add(value: JSONObject) {
        if (!isSubagentCandidate(value)) return
        val aliases = subagentAliases(value)
        val index = result.indexOfFirst { existing -> subagentAliases(existing).any(aliases::contains) }
        if (index >= 0) result[index] = mergeSubagentItems(result[index], value) else result.add(value)
    }
    state.messages.forEach { message ->
        if (message.role != NativeChatRole.ACTIVITY || !message.content.startsWith("PROCESS2|")) return@forEach
        runCatching {
            val payload = JSONObject(String(Base64.decode(message.content.substringAfter('|'), Base64.DEFAULT), Charsets.UTF_8))
            val tools = payload.optJSONArray("tools") ?: return@runCatching
            for (index in 0 until tools.length()) tools.optJSONObject(index)?.let(::add)
        }
    }
    state.liveSubagents.forEach { raw -> runCatching { add(JSONObject(raw)) } }
    return result.filter { subagentThreadId(it).isNotBlank() }
}

private fun collectSubagentItems(state: NativeChatState, current: JSONObject): List<JSONObject> {
    val result = collectAllSubagentItems(state).toMutableList()
    val aliases = subagentAliases(current)
    val index = result.indexOfFirst { existing -> subagentAliases(existing).any(aliases::contains) }
    if (index >= 0) result[index] = mergeSubagentItems(result[index], current)
    else if (subagentThreadId(current).isNotBlank()) result.add(current)
    return result
}

@Composable
private fun WorkPanelDialog(
    state: NativeChatState,
    onLoadSubagentHistory: (String) -> Unit,
    onEditGoal: () -> Unit,
    onClearGoal: () -> Unit,
    onExecutePlan: () -> Unit,
    onGitAction: (String, String) -> Unit,
    onSnapshotAction: (String, String) -> Unit,
    onRequestRestoreFile: (String) -> Unit,
    onWorktreeAction: (String, String) -> Unit,
    onRequestMergeWorktree: (String) -> Unit,
    onRequestRemoveWorktree: (String) -> Unit,
    onRestoreCheckpoint: (NativeConversationCheckpoint) -> Unit,
    onEditCheckpoint: (NativeConversationCheckpoint) -> Unit,
    onContinueTask: () -> Unit,
    onDismiss: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf("plan") }
    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    val agents = remember(state.revision, state.messages.size, state.liveSubagents.size) { collectAllSubagentItems(state) }
    val changes = remember(state.revision, state.messages.size, state.toolDetails.size) { collectAllFileChangeItems(state) }
    val modelChangedFiles = remember(changes.map { it.toString() }) { extractChangedFiles(changes) }
    val gitEntries = remember(state.gitSnapshot) { parseGitEntries(state.gitSnapshot) }
    val changedFiles = remember(modelChangedFiles, state.gitSnapshot) { mergeChangedFiles(modelChangedFiles, gitEntries) }
    val checkpoints = remember(state.revision, state.messages.size, state.phase) { NativeCheckpointModel.build(state.messages, state.phase) }
    val agentThreads = remember(agents) { agents.map(::subagentThreadId).filter { it.isNotBlank() } }
    val close: () -> Unit = { entered = false }
    LaunchedEffect(tab, agentThreads) {
        if (tab == "agents") agentThreads.forEach(onLoadSubagentHistory)
        if (tab in setOf("changes", "git") && !state.gitBusy) onGitAction("refresh", "")
        if (tab == "snapshots") onSnapshotAction("refresh", "")
        if (tab == "worktrees") onWorktreeAction("refresh", "")
    }
    LaunchedEffect(visible, entered) {
        if (visible && !entered) { delay(210L); visible = false; onDismiss() }
    }
    if (!visible) return
    LaunchedEffect(Unit) { delay(18L); entered = true }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val scrim by animateFloatAsState(if (entered) 0.16f else 0f, tween(170, easing = LinearEasing), label = "workPanelScrim")
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = scrim)).clickable(onClick = close))
            AnimatedVisibility(
                visible = entered,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = 430f)) { it } + fadeIn(tween(140)),
                exit = slideOutHorizontally(tween(190, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(120)),
            ) {
                Surface(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(0.91f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 14.dp,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selectedAgentId != null) {
                                IconButton(onClick = { selectedAgentId = null }) {
                                    Icon(HugeIcons.ArrowRight01, "\u8fd4\u56de", Modifier.graphicsLayer { rotationZ = 180f })
                                }
                            } else Spacer(Modifier.width(48.dp))
                            Text(
                                selectedAgentId?.let { id -> agents.firstOrNull { subagentKey(it) == id }?.let(::subagentName) } ?: "\u5de5\u4f5c\u9762\u677f",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(onClick = close) { Icon(HugeIcons.Cancel01, "\u5173\u95ed") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        if (selectedAgentId == null) {
                            WorkPanelTabs(
                                tab = tab,
                                planCount = parsePlanItems(state.planJson).size,
                                checkpointCount = checkpoints.size,
                                snapshotCount = state.workspaceSnapshots.size,
                                worktreeCount = state.worktrees.size,
                                agentCount = agents.size,
                                changeCount = changedFiles.size,
                                gitCount = runCatching { JSONObject(state.gitSnapshot).optJSONArray("entries")?.length() ?: 0 }.getOrDefault(0),
                                onTab = { tab = it },
                            )
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    val forward = workPanelTabIndex(targetState) >= workPanelTabIndex(initialState)
                                    (fadeIn(tween(160)) + slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { if (forward) it / 5 else -it / 5 }) togetherWith
                                        (fadeOut(tween(100)) + slideOutHorizontally(tween(170, easing = FastOutSlowInEasing)) { if (forward) -it / 5 else it / 5 })
                                },
                                label = "workPanelTab",
                            ) { selectedTab ->
                                when (selectedTab) {
                                    "agents" -> if (agents.isEmpty()) WorkPanelEmpty("\u6682\u65e0\u5b50\u4ee3\u7406", "\u5f53 Codex \u59d4\u6d3e\u4efb\u52a1\u540e\uff0c\u5b50\u4ee3\u7406\u4f1a\u663e\u793a\u5728\u8fd9\u91cc\u3002")
                                        else SubagentOverview(state, agents) { agent ->
                                            val thread = subagentThreadId(agent)
                                            selectedAgentId = subagentKey(agent)
                                            if (thread.isNotBlank()) onLoadSubagentHistory(thread)
                                        }
                                    "checkpoints" -> WorkCheckpointsView(
                                        checkpoints = checkpoints,
                                        actionEnabled = state.ready && !state.phase.active,
                                        onRestore = onRestoreCheckpoint,
                                        onEdit = onEditCheckpoint,
                                        onContinue = onContinueTask,
                                    )
                                    "snapshots" -> WorkSnapshotsView(state, onSnapshotAction, onRequestRestoreFile)
                                    "worktrees" -> WorktreesView(state, onWorktreeAction, onRequestMergeWorktree, onRequestRemoveWorktree)
                                    "changes" -> WorkChangesView(state, changes, changedFiles, gitEntries, onGitAction)
                                    "git" -> WorkGitView(state, onGitAction)
                                    else -> WorkPlanView(state.planJson, state.planExplanation, state.activeGoalObjective, state.ready && !state.phase.active, onEditGoal, onClearGoal, onExecutePlan)
                                }
                            }
                        } else {
                            val agent = agents.firstOrNull { subagentKey(it) == selectedAgentId }
                            if (agent != null) {
                                val thread = subagentThreadId(agent)
                                SubagentDetail(
                                    item = agent,
                                    history = state.subagentHistoryRefs[thread],
                                    historyLoading = thread in state.loadingSubagentHistories,
                                    historyError = state.subagentHistoryErrors[thread],
                                    status = resolvedSubagentStatus(state, agent),
                                    onRetryHistory = { if (thread.isNotBlank()) onLoadSubagentHistory(thread) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun workPanelTabIndex(tab: String): Int = listOf("plan", "checkpoints", "snapshots", "worktrees", "agents", "changes", "git").indexOf(tab).coerceAtLeast(0)

@Composable
private fun WorkPanelTabs(tab: String, planCount: Int, checkpointCount: Int, snapshotCount: Int, worktreeCount: Int, agentCount: Int, changeCount: Int, gitCount: Int, onTab: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "plan" to "${nativeText(language, "\u8ba1\u5212", "Plan")} $planCount",
            "checkpoints" to "${nativeText(language, "\u68c0\u67e5\u70b9", "Checkpoints")} $checkpointCount",
            "snapshots" to "${nativeText(language, "\u5feb\u7167", "Snapshots")} $snapshotCount",
            "worktrees" to "Worktrees $worktreeCount",
            "agents" to "${nativeText(language, "\u5b50\u4ee3\u7406", "Agents")} $agentCount",
            "changes" to "${nativeText(language, "\u53d8\u66f4", "Changes")} $changeCount",
            "git" to "Git $gitCount",
        ).forEach { (id, label) ->
            Surface(
                modifier = Modifier.clickable { onTab(id) },
                shape = CircleShape,
                color = if (tab == id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (tab == id) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) { Text(label, Modifier.padding(horizontal = 15.dp, vertical = 9.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, fontWeight = if (tab == id) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun WorktreesView(
    state: NativeChatState,
    onAction: (String, String) -> Unit,
    onRequestMerge: (String) -> Unit,
    onRequestRemove: (String) -> Unit,
) {
    val language = LocalNativeLanguage.current
    var branchName by remember(state.currentThreadId, state.projectPath) { mutableStateOf("") }
    val preview = remember(state.worktreeMergePreview) { runCatching { JSONObject(state.worktreeMergePreview) }.getOrNull() }
    val currentPath = state.projectPath.trimEnd('/', '\\')
    val mainPath = state.worktrees.firstOrNull()?.path.orEmpty().trimEnd('/', '\\')
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (preview != null) {
            item(key = "worktree-merge-preview") {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onAction("clearPreview", "") }) { Icon(HugeIcons.ArrowRight01, null, Modifier.graphicsLayer { rotationZ = 180f }) }
                            Column(Modifier.weight(1f)) {
                                Text("${preview.optString("sourceBranch")} → ${preview.optString("targetBranch")}", fontWeight = FontWeight.SemiBold)
                                Text(nativeText(language, "\u9694\u79bb\u4efb\u52a1\u5408\u5e76\u9884\u89c8", "Isolated task merge preview"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (preview.optString("commits").isNotBlank()) {
                            SelectionContainer { Text(preview.optString("commits").take(16_000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (preview.optString("stat").isNotBlank()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SelectionContainer { Text(preview.optString("stat").take(16_000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (preview.optBoolean("canMerge")) Button(
                            onClick = { onRequestMerge(state.worktreeMergePreview) },
                            enabled = !state.worktreeBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                        ) { Text(nativeText(language, "\u786e\u8ba4\u5408\u5e76", "Review and merge")) }
                        else Text(nativeText(language, "\u6ca1\u6709\u53ef\u5408\u5e76\u7684\u65b0\u63d0\u4ea4", "No new commits to merge"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item(key = "worktree-create") {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.LeftToRightListBullet, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(nativeText(language, "\u521b\u5efa\u9694\u79bb\u4efb\u52a1", "Create isolated task"), fontWeight = FontWeight.SemiBold)
                                Text(nativeText(language, "\u4e3a\u65b0\u5bf9\u8bdd\u521b\u5efa\u72ec\u7acb Git \u5206\u652f\u548c worktree", "Create a dedicated Git branch and worktree for a new conversation"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state.worktreeBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        OutlinedTextField(
                            value = branchName,
                            onValueChange = { branchName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(nativeText(language, "\u5206\u652f\u540d\u79f0", "Branch name")) },
                            placeholder = { Text("codex/feature-name") },
                        )
                        Button(
                            onClick = { onAction("create", branchName); branchName = "" },
                            enabled = !state.worktreeBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                        ) { Text(nativeText(language, "\u521b\u5efa\u5e76\u5728\u65b0\u5bf9\u8bdd\u6253\u5f00", "Create and open new conversation")) }
                    }
                }
            }
            if (state.worktreeNotice.isNotBlank()) item(key = "worktree-notice") {
                Text(state.worktreeNotice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
            }
            if (state.worktreeError.isNotBlank()) item(key = "worktree-error") {
                Text(state.worktreeError.take(6_000), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
            }
            if (state.worktrees.isEmpty() && !state.worktreeBusy) item(key = "worktree-empty") {
                WorkPanelInlineEmpty(nativeText(language, "\u5f53\u524d\u9879\u76ee\u4e0d\u662f Git \u4ed3\u5e93\uff0c\u6216 Git \u6682\u4e0d\u53ef\u7528", "The current project is not a Git repository, or Git is unavailable"))
            }
            itemsIndexed(state.worktrees, key = { _, item -> item.path }) { index, worktree ->
                val normalizedPath = worktree.path.trimEnd('/', '\\')
                val current = normalizedPath == currentPath
                val main = index == 0 || normalizedPath == mainPath
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.Folder01, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(worktree.branch.ifBlank { nativeText(language, "\u5206\u79bb HEAD", "Detached HEAD") }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(worktree.path, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                when { current -> nativeText(language, "\u5f53\u524d", "Current"); main -> nativeText(language, "\u4e3b\u5de5\u4f5c\u533a", "Main"); worktree.dirty -> nativeText(language, "\u6709\u53d8\u66f4", "Dirty"); else -> nativeText(language, "\u5e72\u51c0", "Clean") },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (worktree.dirty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                            if (!current) TextButton(enabled = !state.worktreeBusy, onClick = { onAction("open", worktree.path) }) { Text(nativeText(language, "\u65b0\u5bf9\u8bdd\u6253\u5f00", "Open task")) }
                            if (!main && !worktree.detached) TextButton(enabled = !state.worktreeBusy && !worktree.dirty, onClick = { onAction("previewMerge", worktree.path) }) { Text(nativeText(language, "\u9884\u89c8\u5408\u5e76", "Preview merge")) }
                            if (!main && !current) TextButton(enabled = !state.worktreeBusy && !worktree.dirty && !worktree.locked, onClick = { onRequestRemove(worktree.path) }) { Text(nativeText(language, "\u79fb\u9664", "Remove")) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun WorkSnapshotsView(
    state: NativeChatState,
    onAction: (String, String) -> Unit,
    onRequestRestoreFile: (String) -> Unit,
) {
    val language = LocalNativeLanguage.current
    val preview = remember(state.workspaceSnapshotPreview) { runCatching { JSONObject(state.workspaceSnapshotPreview) }.getOrNull() }
    val previewSnapshotId = preview?.optString("snapshotId").orEmpty()
    val previewSnapshot = state.workspaceSnapshots.firstOrNull { it.id == previewSnapshotId }
    val previewEntries = remember(state.workspaceSnapshotPreview) {
        val array = preview?.optJSONArray("entries")
        if (array == null) emptyList() else buildList { for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add) }
    }
    var label by remember(state.currentThreadId) { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (preview != null && previewSnapshot != null) {
            item(key = "snapshot-preview-header") {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
                    Row(Modifier.padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onAction("clearPreview", "") }) {
                            Icon(HugeIcons.ArrowRight01, nativeText(language, "\u8fd4\u56de", "Back"), Modifier.graphicsLayer { rotationZ = 180f })
                        }
                        Column(Modifier.weight(1f)) {
                            Text(previewSnapshot.label.ifBlank { nativeText(language, "\u5de5\u4f5c\u533a\u5feb\u7167", "Workspace snapshot") }, fontWeight = FontWeight.SemiBold)
                            Text(nativeText(language, "\u4e0e\u5f53\u524d\u5de5\u4f5c\u533a\u76f8\u6bd4 · ${previewEntries.size} \u4e2a\u6587\u4ef6", "Compared with current workspace · ${previewEntries.size} files"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.workspaceSnapshotBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (previewEntries.isEmpty() && !state.workspaceSnapshotBusy) item(key = "snapshot-no-diff") {
                WorkPanelInlineEmpty(nativeText(language, "\u5f53\u524d\u5de5\u4f5c\u533a\u4e0e\u8be5\u5feb\u7167\u4e00\u81f4", "The current workspace matches this snapshot"))
            }
            items(previewEntries, key = { it.optString("status") + "|" + it.optString("path") }) { entry ->
                SnapshotDiffCard(state, previewSnapshot, entry, onAction, onRequestRestoreFile)
            }
        } else {
            item(key = "snapshot-create") {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.Files02, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(nativeText(language, "\u975e\u7834\u574f\u6027\u5de5\u4f5c\u533a\u5feb\u7167", "Non-destructive workspace snapshot"), fontWeight = FontWeight.SemiBold)
                                Text(nativeText(language, "\u4e0d\u4fee\u6539\u5206\u652f\u3001\u6682\u5b58\u533a\u6216\u5de5\u4f5c\u533a", "Does not modify the branch, index, or working tree"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state.workspaceSnapshotBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(nativeText(language, "\u5feb\u7167\u540d\u79f0", "Snapshot name")) },
                            placeholder = { Text(nativeText(language, "\u4f8b\u5982\uff1a\u4fee\u590d\u767b\u5f55\u524d", "For example: Before login fix")) },
                        )
                        Button(
                            onClick = { onAction("create", label); label = "" },
                            enabled = !state.workspaceSnapshotBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                        ) { Text(nativeText(language, "\u521b\u5efa\u5feb\u7167", "Create snapshot")) }
                    }
                }
            }
            if (state.workspaceSnapshotNotice.isNotBlank()) item(key = "snapshot-notice") {
                Text(state.workspaceSnapshotNotice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
            }
            if (state.workspaceSnapshotError.isNotBlank()) item(key = "snapshot-error") {
                Text(state.workspaceSnapshotError.take(4_000), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
            }
            if (state.workspaceSnapshots.isEmpty() && !state.workspaceSnapshotBusy) item(key = "snapshot-empty") {
                WorkPanelInlineEmpty(nativeText(language, "\u8fd8\u6ca1\u6709\u6587\u4ef6\u5feb\u7167", "No workspace snapshots yet"))
            }
            items(state.workspaceSnapshots, key = { it.id }) { snapshot ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(HugeIcons.Files02, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(snapshot.label.ifBlank { nativeText(language, "\u5de5\u4f5c\u533a\u5feb\u7167", "Workspace snapshot") }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                val date = remember(snapshot.createdAt) { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(snapshot.createdAt)) }
                                Text(date + if (snapshot.automatic) nativeText(language, " · \u81ea\u52a8\u5907\u4efd", " · automatic backup") else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(enabled = !state.workspaceSnapshotBusy, onClick = { onAction("delete", snapshot.id) }) { Text(nativeText(language, "\u5220\u9664", "Delete")) }
                            TextButton(enabled = !state.workspaceSnapshotBusy, onClick = { onAction("preview", snapshot.id) }) { Text(nativeText(language, "\u9884\u89c8\u5dee\u5f02", "Preview changes")) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SnapshotDiffCard(
    state: NativeChatState,
    snapshot: NativeWorkspaceSnapshot,
    entry: JSONObject,
    onAction: (String, String) -> Unit,
    onRequestRestoreFile: (String) -> Unit,
) {
    val language = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    val path = entry.optString("path")
    val key = "${snapshot.id}|$path"
    val diff = state.workspaceSnapshotDiffs[key].orEmpty()
    var expanded by remember(key) { mutableStateOf(false) }
    LaunchedEffect(expanded, key, diff) {
        if (expanded && diff.isBlank() && key !in state.workspaceSnapshotDiffLoading) {
            onAction("diff", JSONObject().put("snapshotId", snapshot.id).put("path", path).toString())
        }
    }
    val status = entry.optString("status")
    val statusLabel = when {
        status.startsWith("A") -> nativeText(language, "\u5feb\u7167\u540e\u65b0\u589e", "Added after snapshot")
        status.startsWith("D") -> nativeText(language, "\u5feb\u7167\u540e\u5220\u9664", "Deleted after snapshot")
        status.startsWith("R") -> nativeText(language, "\u5feb\u7167\u540e\u91cd\u547d\u540d", "Renamed after snapshot")
        else -> nativeText(language, "\u5feb\u7167\u540e\u4fee\u6539", "Modified after snapshot")
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 13.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Files02, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(path.substringAfterLast('/').substringAfterLast('\\'), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("$statusLabel · +${entry.optInt("additions")} -${entry.optInt("deletions")} · $path", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (key in state.workspaceSnapshotDiffLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                if (diff.isNotBlank()) IconButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(diff)) }) { Icon(HugeIcons.Copy01, null, Modifier.size(17.dp)) }
                Icon(HugeIcons.ArrowDown01, null, Modifier.size(17.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f })
            }
            if (entry.optBoolean("restorable")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        onRequestRestoreFile(JSONObject().put("snapshotId", snapshot.id).put("path", path).toString())
                    }) { Text(nativeText(language, "\u6062\u590d\u6b64\u6587\u4ef6", "Restore this file")) }
                }
            } else {
                Text(nativeText(language, "\u4e3a\u907f\u514d\u610f\u5916\u5220\u9664\u6216\u91cd\u547d\u540d\uff0c\u8be5\u7c7b\u53d8\u66f4\u6682\u4e0d\u652f\u6301\u81ea\u52a8\u6062\u590d\u3002", "Automatic restore is disabled for added or renamed paths to avoid accidental deletion."), Modifier.padding(horizontal = 13.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ReasoningCapsuleExpand(expanded && diff.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SelectionContainer { GitDiffText(diff, Modifier.fillMaxWidth().padding(13.dp)) }
            }
        }
    }
}

@Composable
private fun WorkCheckpointsView(
    checkpoints: List<NativeConversationCheckpoint>,
    actionEnabled: Boolean,
    onRestore: (NativeConversationCheckpoint) -> Unit,
    onEdit: (NativeConversationCheckpoint) -> Unit,
    onContinue: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    if (checkpoints.isEmpty()) {
        WorkPanelEmpty(
            nativeText(language, "\u8fd8\u6ca1\u6709\u68c0\u67e5\u70b9", "No checkpoints yet"),
            nativeText(language, "\u6bcf\u6b21\u53d1\u9001\u4efb\u52a1\u90fd\u4f1a\u81ea\u52a8\u5f62\u6210\u4e00\u4e2a\u53ef\u56de\u6eda\u3001\u4fee\u6539\u6216\u91cd\u65b0\u6267\u884c\u7684\u68c0\u67e5\u70b9\u3002", "Each submitted task becomes a checkpoint that can be edited or rerun."),
        )
        return
    }
    val latest = checkpoints.last()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (latest.status in setOf("failed", "interrupted")) item(key = "continue-task") {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(nativeText(language, "\u4efb\u52a1\u672a\u5b8c\u6210", "Task incomplete"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        nativeText(language, "\u53ef\u4ee5\u4fdd\u7559\u5f53\u524d\u5de5\u4f5c\u533a\u548c\u4e0a\u4e0b\u6587\uff0c\u8ba9 Codex \u5148\u68c0\u67e5\u73b0\u72b6\u518d\u7ee7\u7eed\u3002", "Keep the current workspace and context, then let Codex inspect the state and continue."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f),
                    )
                    Button(onClick = onContinue, enabled = actionEnabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                        Text(nativeText(language, "\u4ece\u4e2d\u65ad\u5904\u7ee7\u7eed", "Continue from interruption"))
                    }
                }
            }
        }
        items(checkpoints.asReversed(), key = { it.messageId }) { checkpoint ->
            val statusColor = when (checkpoint.status) {
                "completed" -> Color(0xFF5E8B68)
                "running" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
            val statusLabel = when (checkpoint.status) {
                "completed" -> nativeText(language, "\u5df2\u5b8c\u6210", "Completed")
                "running" -> nativeText(language, "\u6267\u884c\u4e2d", "Running")
                "failed" -> nativeText(language, "\u5931\u8d25", "Failed")
                else -> nativeText(language, "\u5df2\u4e2d\u65ad", "Interrupted")
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.15f)) {
                            Text(checkpoint.turnNumber.toString(), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = statusColor)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(nativeText(language, "\u68c0\u67e5\u70b9 ${checkpoint.turnNumber}", "Checkpoint ${checkpoint.turnNumber}"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Medium)
                    }
                    Text(checkpoint.prompt, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (checkpoint.responsePreview.isNotBlank()) {
                        Text(checkpoint.responsePreview, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (checkpoint.status != "running") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(enabled = actionEnabled, onClick = { onEdit(checkpoint) }) { Text(nativeText(language, "\u4fee\u6539\u5e76\u6267\u884c", "Edit and rerun")) }
                            TextButton(enabled = actionEnabled, onClick = { onRestore(checkpoint) }) { Text(nativeText(language, "\u4ece\u6b64\u5904\u91cd\u65b0\u6267\u884c", "Rerun from here")) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

private fun parseGitEntries(raw: String): List<JSONObject> = runCatching {
    val array = JSONObject(raw).optJSONArray("entries") ?: JSONArray()
    buildList { for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add) }
}.getOrDefault(emptyList())

private fun mergeChangedFiles(modelFiles: List<ChangedFileEntry>, gitEntries: List<JSONObject>): List<ChangedFileEntry> {
    val result = linkedMapOf<String, ChangedFileEntry>()
    modelFiles.forEach { result[it.path] = it }
    gitEntries.forEach { entry ->
        val path = entry.optString("path")
        if (path.isNotBlank()) result[path] = ChangedFileEntry(path, entry.optString("operation", "edit"))
    }
    return result.values.toList()
}

@Composable
private fun WorkChangesView(
    state: NativeChatState,
    items: List<JSONObject>,
    files: List<ChangedFileEntry>,
    gitEntries: List<JSONObject>,
    onGitAction: (String, String) -> Unit,
) {
    val language = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    if (files.isEmpty()) {
        if (state.gitBusy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        WorkPanelEmpty(
            nativeText(language, "\u6682\u65e0\u6587\u4ef6\u53d8\u66f4", "No file changes"),
            nativeText(language, "Codex \u4fee\u6539\u6587\u4ef6\u540e\uff0c\u53d8\u66f4\u6458\u8981\u548c diff \u4f1a\u6c47\u603b\u5728\u8fd9\u91cc\u3002", "File summaries and diffs will appear here after Codex edits the workspace."),
        )
        return
    }
    val added = files.count { it.operation == "add" }
    val deleted = files.count { it.operation == "delete" }
    val edited = files.size - added - deleted
    val staged = gitEntries.count { it.optBoolean("staged") }
    val unstaged = gitEntries.count { it.optBoolean("unstaged") }
    val summary = buildString {
        append(nativeText(language, "\u53d8\u66f4 ${files.size} \u4e2a\u6587\u4ef6", "${files.size} changed files"))
        append(" · +$added ~${edited.coerceAtLeast(0)} -$deleted")
        files.forEach { append("\n").append(it.operation).append(" ").append(it.path) }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "changes-summary") {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
                Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.Files02, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nativeText(language, "\u6587\u4ef6\u5ba1\u67e5", "File review"), fontWeight = FontWeight.SemiBold)
                        Text("+$added  ~$edited  -$deleted · ${nativeText(language, "\u5df2\u6682\u5b58", "staged")} $staged · ${nativeText(language, "\u672a\u6682\u5b58", "unstaged")} $unstaged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.gitBusy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { onGitAction("refresh", "") }) { Icon(HugeIcons.Refresh03, nativeText(language, "\u5237\u65b0", "Refresh"), Modifier.size(18.dp)) }
                    IconButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(summary)) }) {
                        Icon(HugeIcons.Copy01, nativeText(language, "\u590d\u5236\u6458\u8981", "Copy summary"), Modifier.size(18.dp))
                    }
                }
            }
        }
        if (state.gitNotice.isNotBlank()) item(key = "changes-git-notice") {
            Text(state.gitNotice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (state.gitError.isNotBlank()) item(key = "changes-git-error") {
            Text(state.gitError.take(4_000), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
        }
        items(files, key = { it.path }) { file ->
            val matching = remember(file.path, items.map { it.toString() }) {
                items.mapNotNull { item ->
                    val detail = item.optString("changes").ifBlank { item.optString(NativeLargePayloadStore.PAYLOAD_PREVIEW) }
                    detail.takeIf { it.isNotBlank() && (it.contains(file.path) || items.size == 1) }
                }.distinct().joinToString("\n\n").take(80_000)
            }
            val gitEntry = gitEntries.firstOrNull { it.optString("path") == file.path }
            WorkChangeFileCard(state, file, gitEntry, matching, onGitAction)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun WorkChangeFileCard(
    state: NativeChatState,
    file: ChangedFileEntry,
    gitEntry: JSONObject?,
    modelDetail: String,
    onGitAction: (String, String) -> Unit,
) {
    val language = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember(file.path) { mutableStateOf(false) }
    val gitDiff = state.gitDiffs[file.path]
    val diffPayload = remember(gitDiff) { gitDiff?.let { runCatching { JSONObject(it) }.getOrNull() } }
    val stagedDiff = diffPayload?.optString("staged").orEmpty()
    val unstagedDiff = diffPayload?.optString("unstaged").orEmpty()
    val additions = diffPayload?.optInt("additions") ?: 0
    val deletions = diffPayload?.optInt("deletions") ?: 0
    val detail = remember(stagedDiff, unstagedDiff, modelDetail) {
        buildString {
            if (stagedDiff.isNotBlank()) append("## ").append(nativeText(language, "\u5df2\u6682\u5b58", "Staged")).append("\n").append(stagedDiff.trim())
            if (unstagedDiff.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("## ").append(nativeText(language, "\u672a\u6682\u5b58", "Unstaged")).append("\n").append(unstagedDiff.trim())
            }
            if (isEmpty()) append(modelDetail)
        }.take(240_000)
    }
    LaunchedEffect(expanded, file.path, gitEntry, gitDiff) {
        if (expanded && gitEntry != null && gitDiff == null && file.path !in state.gitDiffLoading) onGitAction("diff", file.path)
    }
    val operation = when (file.operation) {
        "add" -> nativeText(language, "\u65b0\u5efa", "Added")
        "delete" -> nativeText(language, "\u5220\u9664", "Deleted")
        else -> nativeText(language, "\u4fee\u6539", "Modified")
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 13.dp, end = 5.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.Files02, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.path.substringAfterLast('/').substringAfterLast('\\'), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        buildString {
                            append(operation).append(" · ").append(file.path)
                            if (additions > 0 || deletions > 0) append(" · +").append(additions).append(" -").append(deletions)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (file.path in state.gitDiffLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                if (detail.isNotBlank()) IconButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(detail)) }) {
                    Icon(HugeIcons.Copy01, nativeText(language, "\u590d\u5236 diff", "Copy diff"), Modifier.size(17.dp))
                }
                Icon(HugeIcons.ArrowDown01, null, Modifier.size(17.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f })
                Spacer(Modifier.width(8.dp))
            }
            if (gitEntry != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.End) {
                    if (gitEntry.optBoolean("unstaged")) TextButton(enabled = !state.gitBusy, onClick = { onGitAction("stage", file.path) }) {
                        Text(nativeText(language, "\u6682\u5b58", "Stage"))
                    }
                    if (gitEntry.optBoolean("staged")) TextButton(enabled = !state.gitBusy, onClick = { onGitAction("unstage", file.path) }) {
                        Text(nativeText(language, "\u53d6\u6d88\u6682\u5b58", "Unstage"))
                    }
                }
            }
            ReasoningCapsuleExpand(expanded && detail.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SelectionContainer {
                    GitDiffText(detail, Modifier.fillMaxWidth().padding(13.dp))
                }
            }
            if (expanded && file.path in state.gitDiffLoading && detail.isBlank()) {
                Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            }
            if (expanded && gitDiff != null && detail.isBlank() && diffPayload?.optString("error").orEmpty().isBlank()) {
                Text(
                    nativeText(language, "\u8be5\u6587\u4ef6\u6ca1\u6709\u53ef\u663e\u793a\u7684\u6587\u672c diff\uff0c\u53ef\u80fd\u662f\u4e8c\u8fdb\u5236\u6587\u4ef6\u3002", "No textual diff is available; this may be a binary file."),
                    Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            diffPayload?.optString("error")?.takeIf { it.isNotBlank() }?.let { error ->
                Text(error, Modifier.padding(horizontal = 13.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun GitDiffText(text: String, modifier: Modifier = Modifier) {
    val addition = Color(0xFF3F7D4C)
    val deletion = MaterialTheme.colorScheme.error
    val header = MaterialTheme.colorScheme.primary
    val annotated = remember(text, addition, deletion, header) {
        androidx.compose.ui.text.buildAnnotatedString {
            text.lineSequence().forEach { line ->
                val color = when {
                    line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") || line.startsWith("diff --git") || line.startsWith("## ") -> header
                    line.startsWith("+") -> addition
                    line.startsWith("-") -> deletion
                    else -> Color.Unspecified
                }
                withStyle(androidx.compose.ui.text.SpanStyle(color = color)) { append(line) }
                append('\n')
            }
        }
    }
    Text(annotated, modifier, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
}

@Composable
private fun WorkGitView(state: NativeChatState, onGitAction: (String, String) -> Unit) {
    val language = LocalNativeLanguage.current
    val snapshot = remember(state.gitSnapshot) { runCatching { JSONObject(state.gitSnapshot) }.getOrNull() }
    val entries = remember(state.gitSnapshot) {
        val array = snapshot?.optJSONArray("entries")
        if (array == null) emptyList() else buildList { for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add) }
    }
    var commitMessage by remember(state.currentThreadId, state.projectPath) { mutableStateOf("") }
    val stagedCount = entries.count { it.optBoolean("staged") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "git-header") {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
                Row(Modifier.padding(start = 14.dp, end = 5.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.LeftToRightListBullet, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(snapshot?.optString("branch").orEmpty().ifBlank { nativeText(language, "Git \u5de5\u4f5c\u533a", "Git workspace") }, fontWeight = FontWeight.SemiBold)
                        Text(state.projectPath.ifBlank { nativeText(language, "\u672a\u7ed1\u5b9a\u9879\u76ee", "No project selected") }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val ahead = snapshot?.optInt("ahead") ?: 0
                        val behind = snapshot?.optInt("behind") ?: 0
                        if (ahead > 0 || behind > 0) Text("↑$ahead  ↓$behind", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (state.gitBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { onGitAction("refresh", "") }) { Icon(HugeIcons.Refresh03, nativeText(language, "\u5237\u65b0", "Refresh"), Modifier.size(19.dp)) }
                }
            }
        }
        if (state.gitError.isNotBlank()) item(key = "git-action-error") {
            Text(state.gitError.take(4_000), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (state.gitNotice.isNotBlank()) item(key = "git-action-notice") {
            Text(state.gitNotice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (snapshot?.optBoolean("available", true) == false) item(key = "git-unavailable") {
            WorkPanelInlineEmpty(snapshot.optString("error", nativeText(language, "Git \u4e0d\u53ef\u7528", "Git unavailable")))
        } else if (!state.gitBusy && entries.isEmpty()) item(key = "git-clean") {
            WorkPanelInlineEmpty(nativeText(language, "\u5de5\u4f5c\u533a\u5e72\u51c0\uff0c\u6ca1\u6709\u5f85\u63d0\u4ea4\u53d8\u66f4", "Working tree clean"))
        } else {
            items(entries, key = { it.optString("originalPath", it.optString("path")) }) { entry ->
                val path = entry.optString("path")
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(HugeIcons.Files02, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(path.substringAfterLast('/').substringAfterLast('\\'), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text("${entry.optString("index")}${entry.optString("worktree")} · $path", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (entry.optBoolean("unstaged")) TextButton(enabled = !state.gitBusy, onClick = { onGitAction("stage", path) }) { Text(nativeText(language, "\u6682\u5b58", "Stage")) }
                        if (entry.optBoolean("staged")) TextButton(enabled = !state.gitBusy, onClick = { onGitAction("unstage", path) }) { Text(nativeText(language, "\u53d6\u6d88\u6682\u5b58", "Unstage")) }
                    }
                }
            }
            item(key = "git-commit") {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedTextField(
                            value = commitMessage,
                            onValueChange = { commitMessage = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(nativeText(language, "\u63d0\u4ea4\u8bf4\u660e", "Commit message")) },
                            minLines = 2,
                            maxLines = 4,
                        )
                        Button(
                            onClick = { onGitAction("commit", commitMessage); commitMessage = "" },
                            enabled = stagedCount > 0 && commitMessage.isNotBlank() && !state.gitBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(nativeText(language, "\u63d0\u4ea4 $stagedCount \u4e2a\u5df2\u6682\u5b58\u6587\u4ef6", "Commit $stagedCount staged files")) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun WorkPanelInlineEmpty(text: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(text, Modifier.padding(16.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun parsePlanItems(raw: String): List<JSONObject> = runCatching {
    val array = JSONArray(raw)
    buildList { for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add) }
}.getOrDefault(emptyList())

@Composable
private fun WorkPlanView(raw: String, explanation: String, goal: String, executeEnabled: Boolean, onEditGoal: () -> Unit, onClearGoal: () -> Unit, onExecutePlan: () -> Unit) {
    val language = LocalNativeLanguage.current
    val plan = remember(raw) { parsePlanItems(raw) }
    if (plan.isEmpty() && goal.isBlank()) {
        WorkPanelEmpty(nativeText(language, "\u8fd8\u6ca1\u6709\u8ba1\u5212", "No plan yet"), nativeText(language, "\u5207\u6362\u5230\u8ba1\u5212\u6a21\u5f0f\u5e76\u53d1\u9001\u4efb\u52a1\uff0cCodex \u7684\u6267\u884c\u8ba1\u5212\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc\u3002", "Switch to Plan mode and send a task; Codex will show the generated plan here."))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (plan.isNotEmpty()) item(key = "execute-plan") {
            Button(onClick = onExecutePlan, enabled = executeEnabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Icon(HugeIcons.Zap, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(nativeText(language, "\u6267\u884c\u6b64\u8ba1\u5212", "Execute this plan"))
            }
        }
        if (goal.isNotBlank()) item(key = "active-goal") {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(enabled = executeEnabled) { onEditGoal() },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                        Icon(HugeIcons.LookTop, null, Modifier.padding(8.dp).size(17.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nativeText(language, "\u6d3b\u8dc3\u76ee\u6807", "Active goal"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(goal, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                    IconButton(onClick = onClearGoal, enabled = executeEnabled, modifier = Modifier.size(34.dp)) {
                        Icon(HugeIcons.Cancel01, nativeText(language, "\u6e05\u9664\u76ee\u6807", "Clear goal"), Modifier.size(16.dp))
                    }
                }
            }
        }
        if (explanation.isNotBlank()) item(key = "explanation") {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(Modifier.padding(14.dp)) { DeferredHistoricalRichText(explanation) }
            }
        }
        itemsIndexed(plan, key = { index, item -> item.optString("step").ifBlank { index.toString() } }) { index, item ->
            val status = item.optString("status", "pending")
            val color = when (status) {
                "completed" -> Color(0xFF5E8B68)
                "in_progress", "inProgress" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    Surface(shape = CircleShape, color = color.copy(alpha = 0.16f)) {
                        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                            Text(if (status == "completed") "\u2713" else "${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        DeferredHistoricalRichText(item.optString("step", item.optString("text", item.toString())))
                        Text(when (status) { "completed" -> nativeText(language, "\u5df2\u5b8c\u6210", "Completed"); "in_progress", "inProgress" -> nativeText(language, "\u8fdb\u884c\u4e2d", "In progress"); else -> nativeText(language, "\u5f85\u5904\u7406", "Pending") }, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun WorkPanelEmpty(title: String, description: String) {
    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(HugeIcons.LeftToRightListBullet, null, Modifier.padding(14.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(description, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
    }
}

@Composable
private fun SubagentOverview(state: NativeChatState, agents: List<JSONObject>, onSelect: (JSONObject) -> Unit) {
    val unfinished = agents.filter { resolvedSubagentStatus(state, it) in setOf("waiting", "working") }
    val finished = agents.filterNot { it in unfinished }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "unfinished-title") {
            Text(
                "\u672a\u5b8c\u6210 \u00b7 ${unfinished.size}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
        if (unfinished.isEmpty()) item(key = "unfinished-empty") {
            Text(
                "\u6ca1\u6709\u6b63\u5728\u6267\u884c\u7684\u5b50\u4ee3\u7406",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        items(unfinished, key = { "active-${subagentKey(it)}" }) { agent -> SubagentOverviewRow(state, agent, onSelect) }
        item(key = "finished-title") {
            Text(
                "\u5df2\u5b8c\u6210 \u00b7 ${finished.size}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
            )
        }
        if (finished.isEmpty()) item(key = "finished-empty") {
            Text(
                "\u6682\u65e0\u5df2\u5b8c\u6210\u7684\u5b50\u4ee3\u7406",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        items(finished, key = { "done-${subagentKey(it)}" }) { agent -> SubagentOverviewRow(state, agent, onSelect) }
        item(key = "agent-list-bottom") { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SubagentOverviewRow(state: NativeChatState, agent: JSONObject, onSelect: (JSONObject) -> Unit) {
    val task = jsonText(agent, "task", "prompt", "input", "message")
    val status = resolvedSubagentStatus(state, agent)
    val statusLabel = subagentStatusLabel(status, LocalNativeLanguage.current)
    val statusColor = when (status) {
        "working" -> MaterialTheme.colorScheme.primary
        "waiting" -> MaterialTheme.colorScheme.tertiary
        "failed", "stopped" -> MaterialTheme.colorScheme.error
        else -> Color(0xFF5E8B68)
    }
    Surface(
        Modifier.fillMaxWidth().clickable { onSelect(agent) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.14f)) {
                Box(Modifier.padding(8.dp).size(16.dp), contentAlignment = Alignment.Center) {
                    if (status == "working") CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.8.dp, color = statusColor)
                    else Icon(HugeIcons.Sparkles, null, Modifier.size(16.dp), tint = statusColor)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(subagentName(agent), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
                }
                Text(
                    if (task.isBlank()) "\u70b9\u51fb\u67e5\u770b\u5b50\u4ee3\u7406\u8f93\u51fa" else task,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(HugeIcons.ArrowRight01, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class LoadedSubagentPage(
    val messages: List<JSONObject>,
    val startIndex: Int,
    val totalCount: Int,
)

private fun loadSubagentPage(reference: String, limit: Int): LoadedSubagentPage {
    if (reference.isBlank()) return LoadedSubagentPage(emptyList(), 0, 0)
    val page = NativeLargePayloadStore.subagentPage(reference, Int.MAX_VALUE, limit)
    val messages = page.messages.mapNotNull { raw -> runCatching { JSONObject(raw) }.getOrNull() }
    return LoadedSubagentPage(messages, page.startIndex, page.totalCount)
}

@Composable
private fun SubagentDetail(
    item: JSONObject,
    history: String?,
    historyLoading: Boolean,
    historyError: String?,
    status: String,
    onRetryHistory: () -> Unit,
) {
    val name = subagentName(item)
    val task = jsonText(item, "task", "prompt", "input", "message")
    val result = jsonText(item, "output", "result")
    var historyLimit by remember(history) { mutableIntStateOf(60) }
    val loadedPage by produceState<LoadedSubagentPage?>(initialValue = null, history, historyLimit) {
        value = withContext(Dispatchers.Default) { loadSubagentPage(history.orEmpty(), historyLimit) }
    }
    val messages = loadedPage?.messages.orEmpty()
    val displayMessages = remember(messages, task) {
        if (task.isBlank()) messages else messages.filterNot {
            it.optString("role") == "user" && it.optString("content").trim() == task.trim()
        }
    }
    val payloadLoading = !history.isNullOrBlank() && loadedPage == null
    val effectiveHistoryLoading = historyLoading || payloadLoading
    val statusLabel = subagentStatusLabel(status, LocalNativeLanguage.current)
    if (messages.isNotEmpty()) {
        val conversationListState = rememberLazyListState(
            initialFirstVisibleItemIndex = (displayMessages.lastIndex + 3).coerceAtLeast(0),
        )
        LaunchedEffect(history) {
            withFrameNanos { }
            val lastMessageItem = (displayMessages.size + 2).coerceAtLeast(0)
            conversationListState.scrollToItem(lastMessageItem)
        }
        val panelScope = rememberCoroutineScope()
        val showLatestButton by remember { derivedStateOf { conversationListState.canScrollForward } }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = conversationListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "agent-status") { AgentTimelineSection("\u72b6\u6001", statusLabel) }
                if (status == "failed" || status == "stopped") item(key = "agent-failure") { SubagentFailureSummary() }
                if (task.isNotBlank()) item(key = "agent-task") { AgentTimelineSection("\u59d4\u6d3e\u7684\u4efb\u52a1", task) }
                if ((loadedPage?.startIndex ?: 0) > 0) {
                    item(key = "load-earlier-agent-history") {
                        TextButton(onClick = { historyLimit += 60 }) {
                            Text(nativeText(LocalNativeLanguage.current, "\u52a0\u8f7d\u66f4\u65e9\u7684\u6267\u884c\u8bb0\u5f55", "Load earlier activity"))
                        }
                    }
                }
                item(key = "agent-output-title") {
                    Text(
                        "\u6267\u884c\u8bb0\u5f55 \u00b7 ${loadedPage?.totalCount ?: displayMessages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                itemsIndexed(
                    items = displayMessages,
                    key = { index, message -> message.optString("id").ifBlank { "${message.optString("role")}-${message.optString("content").hashCode()}-$index" } },
                    contentType = { _, message -> message.optString("role") },
                ) { _, message ->
                    SubagentConversationMessage(message, name)
                }
                if (effectiveHistoryLoading) item(key = "refreshing") { SubagentHistoryLoading("\u6b63\u5728\u540c\u6b65\u5b50\u4ee3\u7406\u6700\u65b0\u8f93\u51fa") }
                item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
            }
            AnimatedVisibility(
                visible = showLatestButton,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 18.dp),
                enter = fadeIn(tween(140)) + scaleIn(tween(190, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(100)) + scaleOut(tween(140, easing = FastOutSlowInEasing)),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        panelScope.launch {
                            conversationListState.animateScrollToItem((displayMessages.size + 2).coerceAtLeast(0))
                        }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(HugeIcons.ArrowDown01, "\u56de\u5230\u5b50\u4ee3\u7406\u6700\u65b0\u8f93\u51fa", Modifier.size(18.dp))
                }
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AgentTimelineSection("\u72b6\u6001", statusLabel)
            if (status == "failed" || status == "stopped") SubagentFailureSummary()
            if (task.isNotBlank()) AgentTimelineSection("\u59d4\u6d3e\u7684\u4efb\u52a1", task)
            if (result.isNotBlank()) {
                AgentTimelineSection("\u6700\u7ec8\u56de\u590d", result)
            } else if (effectiveHistoryLoading) {
                SubagentHistoryLoading(
                    if (status in setOf("waiting", "working")) "\u5b50\u4ee3\u7406\u6b63\u5728\u6267\u884c\uff0c\u8f93\u51fa\u4f1a\u81ea\u52a8\u66f4\u65b0"
                    else "\u6b63\u5728\u52a0\u8f7d\u5b50\u4ee3\u7406\u6700\u7ec8\u56de\u590d"
                )
            } else {
                AgentTimelineSection(
                    "\u6700\u7ec8\u56de\u590d",
                    if (historyError.isNullOrBlank()) "\u6682\u672a\u83b7\u53d6\u5230\u6700\u7ec8\u56de\u590d" else "\u52a0\u8f7d\u5931\u8d25\uff0c\u53ef\u4ee5\u91cd\u8bd5",
                )
                TextButton(onClick = onRetryHistory, modifier = Modifier.align(Alignment.End)) {
                    Icon(HugeIcons.Refresh03, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("\u91cd\u65b0\u52a0\u8f7d")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SubagentFailureSummary() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text("\u6267\u884c\u5931\u8d25", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(3.dp))
            Text(
                "\u5b50\u4ee3\u7406\u672a\u751f\u6210\u6700\u7ec8\u56de\u590d\u3002\u8be6\u7ec6\u6d3b\u52a8\u9ed8\u8ba4\u5df2\u6298\u53e0\uff0c\u53ef\u6309\u9700\u5c55\u5f00\u67e5\u770b\u3002",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun SubagentHistoryLoading(label: String = "\u6b63\u5728\u52a0\u8f7d\u5b50\u4ee3\u7406\u5bf9\u8bdd") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubagentConversationMessage(message: JSONObject, agentName: String) {
    val role = message.optString("role")
    val content = message.optString("content")
    when (role) {
        "user" -> {
            Column(Modifier.fillMaxWidth()) {
                Text("\u59d4\u6d3e\u7684\u4efb\u52a1", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(content, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
                }
            }
        }
        "assistant" -> {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(HugeIcons.Sparkles, null, modifier = Modifier.padding(5.dp).size(13.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(agentName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(7.dp))
                RichResponseText(content)
            }
        }
        "activity" -> SubagentActivityView(content)
    }
}

@Composable
private fun SubagentActivityView(content: String) {
    val payload = if (content.startsWith("PROCESS2|")) runCatching {
        JSONObject(String(Base64.decode(content.substringAfter('|'), Base64.DEFAULT), Charsets.UTF_8))
    }.getOrNull() else null
    if (payload == null) return
    val duration = payload.optLong("duration", 0L)
    val reasoning = payload.optString("reasoning")
    val command = payload.optString("command")
    val tools = payload.optJSONArray("tools")
    // A historical activity can be a very large document. Never auto-expand it when
    // the child detail opens, including failed/aborted activities with no duration.
    var expanded by remember(content) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(190, easing = FastOutSlowInEasing),
        label = "subagentReasoningArrow",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.Zap, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (duration > 0) "\u601d\u8003\u4e86 ${duration}s" else "\u601d\u8003\u4e0e\u6267\u884c",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    HugeIcons.ArrowDown01,
                    null,
                    modifier = Modifier.size(15.dp).graphicsLayer { rotationZ = arrowRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QElasticExpand(expanded) {
                SafeExpandableViewport(maxHeight = 520.dp) {
                    Column(Modifier.padding(start = 13.dp, end = 13.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        if (reasoning.isNotBlank()) DeferredHistoricalRichText(reasoning)
                        if (command.isNotBlank()) ToolTextCard("\u547d\u4ee4\u6267\u884c", command, false)
                        if (tools != null) for (index in 0 until tools.length()) {
                            val tool = runCatching { JSONObject(tools.optString(index)) }.getOrNull() ?: continue
                            when (tool.optString("type")) {
                                "commandExecution" -> CommandExecutionCard(tool)
                                "collabAgentToolCall", "subAgentActivity" -> Unit
                                else -> {
                                    val payloadRef = tool.optString(NativeLargePayloadStore.PAYLOAD_REF)
                                    ToolTextCard(
                                        tool.optString("type", "\u5de5\u5177\u8c03\u7528"),
                                        if (payloadRef.isNotBlank()) tool.optString(NativeLargePayloadStore.PAYLOAD_PREVIEW)
                                        else tool.optString("aggregatedOutput", tool.optString("output", tool.optString("detail", tool.toString(2)))),
                                        tool.optString("type") == "fileChange",
                                        payloadRef,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentTimelineSection(title: String, value: String, monospace: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(modifier = Modifier.size(9.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
            Box(Modifier.padding(top = 4.dp).width(1.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f).padding(bottom = 3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(7.dp))
            if (monospace) {
                SelectionContainer { Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            } else {
                RichResponseText(value)
            }
        }
    }
}

@Composable
private fun CommandExecutionGroup(
    completedCommands: List<JSONObject>,
    liveCommand: JSONObject?,
    liveOutput: String,
) {
    val language = LocalNativeLanguage.current
    val running = liveCommand != null
    val count = completedCommands.size + if (running) 1 else 0
    var expanded by remember { mutableStateOf(running) }
    LaunchedEffect(running) { if (running) expanded = true }
    val failedCount = remember(completedCommands) {
        completedCommands.count { item ->
            item.optString("status").equals("failed", true) ||
                (item.opt("exitCode")?.toString()?.toIntOrNull()?.let { it != 0 } == true)
        }
    }
    val accent = if (failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.64f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.7.dp, color = accent)
                else Icon(HugeIcons.Code, null, Modifier.size(15.dp), tint = accent)
                Spacer(Modifier.width(7.dp))
                Text(
                    if (running) nativeText(language, "正在运行命令", "Running commands")
                    else nativeText(language, "命令", "Commands"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (failedCount > 0) nativeText(language, "$count ? $failedCount ??", "$count ? $failedCount failed") else "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(5.dp))
                Icon(
                    HugeIcons.ArrowDown01, null, Modifier.size(14.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QElasticExpand(expanded) {
                Column(Modifier.padding(start = 7.dp, end = 7.dp, bottom = 6.dp)) {
                    completedCommands.forEach { item -> CommandExecutionCard(item, compact = true) }
                    if (liveCommand != null) {
                        CommandExecutionCard(liveCommand, running = true, liveOutput = liveOutput, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandExecutionCard(item: JSONObject, running: Boolean = false, liveOutput: String = "", compact: Boolean = false) {
    val language = LocalNativeLanguage.current
    val command = NativeCommandPresentation.rawCommand(item)
    val action = NativeCommandPresentation.action(item)
    val actionLabel = NativeCommandPresentation.label(action, language != "en")
    val subject = NativeCommandPresentation.subject(item)
    val cwd = item.optString("cwd", "")
    val exitCode = item.opt("exitCode")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
    val durationMs = item.optLong("durationMs", item.optLong("duration_ms", 0L))
    val status = item.optString("status")
    val isRunning = running || status.equals("inProgress", true) || status.equals("running", true)
    val failed = status.equals("failed", true) || (exitCode.toIntOrNull()?.let { it != 0 } == true)
    val outputRef = item.optString(NativeCommandOutputStore.OUTPUT_REF)
    val stderrRef = item.optString(NativeCommandOutputStore.STDERR_REF)
    val outputPreview = item.optString(NativeCommandOutputStore.OUTPUT_PREVIEW)
    val stderrPreview = item.optString(NativeCommandOutputStore.STDERR_PREVIEW)
    val outputChars = item.optInt(NativeCommandOutputStore.OUTPUT_CHARS, 0)
    val stderrChars = item.optInt(NativeCommandOutputStore.STDERR_CHARS, 0)
    val inlineStdout = item.optString("stdout", "")
    val inlineStderr = item.optString("stderr", "")
    val inlineAggregate = item.optString("aggregatedOutput", item.optString("output", ""))
    val inlineOutput = if (inlineStdout.isNotBlank() || inlineStderr.isNotBlank()) inlineStdout else inlineAggregate
    val cardKey = item.optString("id", item.optString("itemId", "$command|$cwd|$durationMs"))
    var expanded by remember(cardKey, running) { mutableStateOf(false) }
    var deferredOutput by remember(outputRef) { mutableStateOf("") }
    var deferredStderr by remember(stderrRef) { mutableStateOf("") }
    var outputLoading by remember(outputRef, stderrRef) { mutableStateOf(false) }
    var previewFallback by remember(outputRef, stderrRef) { mutableStateOf(false) }

    // A completed command keeps only refs in snapshot state. Resolve the potentially 1MB+
    // stream off the UI thread and release it from this card again when the card collapses.
    LaunchedEffect(expanded, outputRef, stderrRef) {
        if (!expanded) {
            deferredOutput = ""
            deferredStderr = ""
            outputLoading = false
            previewFallback = false
            return@LaunchedEffect
        }
        if (outputRef.isBlank() && stderrRef.isBlank()) return@LaunchedEffect
        outputLoading = true
        val resolved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            NativeCommandOutputStore.get(outputRef) to NativeCommandOutputStore.get(stderrRef)
        }
        deferredOutput = resolved.first ?: outputPreview
        deferredStderr = resolved.second ?: stderrPreview
        previewFallback = (outputRef.isNotBlank() && resolved.first == null) ||
            (stderrRef.isNotBlank() && resolved.second == null)
        outputLoading = false
    }

    val output = when {
        isRunning -> liveOutput
        outputRef.isNotBlank() -> deferredOutput
        else -> inlineOutput
    }
    val stderr = when {
        stderrRef.isNotBlank() -> deferredStderr
        else -> inlineStderr
    }
    val hasDetails = command.isNotBlank() || outputRef.isNotBlank() || stderrRef.isNotBlank() ||
        inlineOutput.isNotBlank() || inlineStderr.isNotBlank() || cwd.isNotBlank()
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        tween(150, easing = FastOutSlowInEasing),
        label = "commandArrow",
    )
    val accent = when {
        failed -> MaterialTheme.colorScheme.error
        isRunning -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF5E8B68)
    }
    val icon = when (action) {
        NativeCommandPresentation.LIST_FILES, NativeCommandPresentation.CREATE_DIRECTORY -> HugeIcons.Folder01
        NativeCommandPresentation.READ_FILE -> HugeIcons.Files02
        NativeCommandPresentation.SEARCH_FILES -> HugeIcons.Search01
        NativeCommandPresentation.GIT_DIFF, NativeCommandPresentation.GIT_STATUS, NativeCommandPresentation.GIT_LOG -> HugeIcons.Code
        NativeCommandPresentation.DEVICE_COMMAND -> HugeIcons.Code
        else -> HugeIcons.Sparkles
    }
    val statusLabel = when {
        failed -> nativeText(language, "\u547d\u4ee4\u6267\u884c\u5931\u8d25", "Command failed")
        isRunning -> nativeText(language, "\u6b63\u5728\u8fd0\u884c\u547d\u4ee4", "Running command")
        else -> nativeText(language, "\u5df2\u6267\u884c\u547d\u4ee4", "Command completed")
    }
    val capsuleLabel = command.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { actionLabel }

    val topPadding = if (compact) 2.dp else 6.dp
    val rowVerticalPadding = if (compact) 4.dp else 8.dp
    val iconBoxSize = if (compact) 20.dp else 27.dp
    val iconSize = if (compact) 12.dp else 15.dp
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (isRunning) 0.26f else 0.14f)),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = hasDetails) { expanded = !expanded }.padding(horizontal = if (compact) 8.dp else 11.dp, vertical = rowVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f), contentColor = accent) {
                    Box(Modifier.size(iconBoxSize), contentAlignment = Alignment.Center) {
                        if (isRunning) CircularProgressIndicator(Modifier.size(iconSize), strokeWidth = 1.6.dp, color = accent)
                        else Icon(icon, null, Modifier.size(iconSize), tint = accent)
                    }
                }
                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                Text(
                    capsuleLabel,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                    fontFamily = if (command.isNotBlank()) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                val meta = buildList {
                    if (durationMs > 0 && !isRunning) add("%.1fs".format(durationMs / 1000.0))
                    if (exitCode.isNotBlank() && failed) add("exit $exitCode")
                    val chars = outputChars + stderrChars
                    if (chars > 0 && !isRunning) add(formatCommandOutputChars(chars, language))
                }.joinToString(" \u00b7 ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = accent)
                    Spacer(Modifier.width(6.dp))
                }
                if (hasDetails) Icon(
                    HugeIcons.ArrowDown01,
                    if (expanded) nativeText(language, "\u6536\u8d77\u547d\u4ee4\u8be6\u60c5", "Collapse command details") else nativeText(language, "\u5c55\u5f00\u547d\u4ee4\u8be6\u60c5", "Expand command details"),
                    Modifier.size(15.dp).graphicsLayer { rotationZ = arrowRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QElasticExpand(expanded && hasDetails) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    if (command.isNotBlank()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp)) {
                            Text(nativeText(language, "\u539f\u59cb\u547d\u4ee4", "Raw command"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer {
                                Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (cwd.isNotBlank()) Text(cwd, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (outputLoading) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp)
                            Spacer(Modifier.width(7.dp))
                            Text(nativeText(language, "\u6b63\u5728\u8bfb\u53d6\u547d\u4ee4\u8f93\u51fa\u2026", "Loading command output\u2026"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (output.isNotBlank()) CommandStreamSection(nativeText(language, "\u8f93\u51fa", "Output"), output, false)
                    if (stderr.isNotBlank()) CommandStreamSection(nativeText(language, "\u9519\u8bef\u8f93\u51fa", "Error output"), stderr, true)
                    if (previewFallback) {
                        Text(
                            nativeText(language, "\u5b8c\u6574\u8f93\u51fa\u5df2\u4ece\u7f13\u5b58\u91ca\u653e\uff0c\u5f53\u524d\u663e\u793a\u6458\u8981\u3002", "The full output was released from cache; showing its preview."),
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatCommandOutputChars(chars: Int, language: String): String = when {
    chars >= 1_000_000 -> "%.1fM".format(chars / 1_000_000.0)
    chars >= 1_000 -> "%.1fK".format(chars / 1_000.0)
    else -> nativeText(language, "$chars \u5b57\u7b26", "$chars chars")
}

@Composable
private fun CommandStreamSection(title: String, value: String, error: Boolean) {
    val chunks = remember(value) { NativeUiRenderSafety.splitPlainText(value.trimEnd()) }
    var visibleCount by remember(value) { mutableIntStateOf(0) }
    LaunchedEffect(value) {
        visibleCount = 0
        chunks.indices.forEach { index ->
            withFrameNanos { }
            visibleCount = index + 1
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(if (error) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f) else Color.Transparent)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                chunks.take(visibleCount).forEach { chunk ->
                    Text(chunk, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (visibleCount < chunks.size) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Spacer(Modifier.width(6.dp))
                Text(nativeText(LocalNativeLanguage.current, "\u6b63\u5728\u52a0\u8f7d\u8f93\u51fa\u2026", "Loading output\u2026"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private enum class CommandViewType { DIRECTORY, FILE, SEARCH, TERMINAL }

private fun commandViewType(content: String): CommandViewType {
    val command = content.lineSequence().firstOrNull().orEmpty().removePrefix("$ ").trim()
    return when {
        Regex("(^|\\s|[/\"'`])(ls|tree|find|fd)(\\s|$)").containsMatchIn(command) || command.contains("Get-ChildItem") -> CommandViewType.DIRECTORY
        Regex("(^|\\s|[/\"'`])(cat|head|tail|sed)(\\s|$)").containsMatchIn(command) || command.contains("Get-Content") -> CommandViewType.FILE
        Regex("(^|\\s|[/\"'`])(rg|grep|findstr)(\\s|$)").containsMatchIn(command) || command.contains("Select-String") -> CommandViewType.SEARCH
        else -> CommandViewType.TERMINAL
    }
}

@Composable
private fun SmartCommandCard(content: String) {
    when (commandViewType(content)) {
        CommandViewType.DIRECTORY -> DirectoryOutputCard(content)
        CommandViewType.FILE -> FileOutputCard(content)
        CommandViewType.SEARCH -> SearchOutputCard(content)
        CommandViewType.TERMINAL -> ToolTextCard("命令执行", content, false)
    }
}

@Composable
private fun DirectoryOutputCard(content: String) {
    val rawLines = content.lines().drop(1).filter { it.isNotBlank() && !it.startsWith("exit ") }
    val lines = rawLines.sortedWith(compareBy<String> { line ->
        val name = line.trim().substringAfterLast(' ')
        !(name.endsWith("/") || line.startsWith("d"))
    }.thenBy { it.lowercase() })
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) lines else lines.take(12)
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Folder01, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text("目录内容", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(nativeText(language, "${lines.size} \u9879", "${lines.size} items"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            visible.forEach { line ->
                val name = line.trim().substringAfterLast(' ')
                val folder = name.endsWith("/") || line.startsWith("d")
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    val image = name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "svg")
                    Icon(if (folder) HugeIcons.Folder01 else if (image) HugeIcons.Image02 else HugeIcons.Files02, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp)); SelectionContainer { Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (lines.size > 12) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) nativeText(language, "\u6536\u8d77", "Collapse") else nativeText(language, "\u663e\u793a\u5269\u4f59 ${lines.size - 12} \u9879", "Show ${lines.size - 12} more")) }
        }
    }
}

@Composable
private fun FileOutputCard(content: String) {
    val command = content.lineSequence().firstOrNull().orEmpty().removePrefix("$ ")
    val name = command.trim().split(Regex("\\s+")).lastOrNull().orEmpty().substringAfterLast('/')
    ToolTextCard(name.ifBlank { "文件内容" }, content.lines().drop(1).filterNot { it.startsWith("exit ") }.joinToString("\n"), false)
}

@Composable
private fun SearchOutputCard(content: String) {
    val results = content.lines().drop(1).filter { it.isNotBlank() && !it.startsWith("exit ") }
    val grouped = results.groupBy { line ->
        val match = Regex("^(.+?):(\\d+):(.*)$").find(line)
        match?.groupValues?.get(1) ?: "搜索输出"
    }
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Search01, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text("搜索结果", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(nativeText(language, "${results.size} \u6761 \u00b7 ${grouped.size} \u4e2a\u6587\u4ef6", "${results.size} matches ? ${grouped.size} files"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QElasticExpand(expanded) {
                SafeExpandableViewport(maxHeight = 420.dp) {
                    Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                grouped.forEach { (file, lines) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(HugeIcons.Files02, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp)); Text(file, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    lines.forEach { line ->
                        val match = Regex("^(.+?):(\\d+):(.*)$").find(line)
                        val shown = if (match != null) "${match.groupValues[2].padStart(4)}  ${match.groupValues[3]}" else line
                        SelectionContainer { Text(shown, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                    }
                }
            }
        }
    }
}
@Composable
private fun ToolTextCard(title: String, detail: String, diff: Boolean, payloadRef: String = "") {
    var expanded by remember { mutableStateOf(false) }
    var loadedDetail by remember(payloadRef) { mutableStateOf<String?>(null) }
    LaunchedEffect(expanded, payloadRef) {
        if (expanded && payloadRef.isNotBlank() && loadedDetail == null) {
            loadedDetail = withContext(Dispatchers.Default) { NativeLargePayloadStore.get(payloadRef) }
        } else if (!expanded && loadedDetail != null) {
            // Release the large String from Compose-local state after collapse.
            loadedDetail = null
        }
    }
    val resolvedDetail = loadedDetail ?: detail
    val displayDetail = remember(resolvedDetail) { NativeUiRenderSafety.sanitizeToolDetail(resolvedDetail) }
    val failed = resolvedDetail.contains("failed", true) || resolvedDetail.contains("error", true) || Regex("exit [1-9]").containsMatchIn(resolvedDetail)
    val accent = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (diff) HugeIcons.Folder01 else HugeIcons.Sparkles, null, modifier = Modifier.size(17.dp), tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(if (failed) "失败" else "完成", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            QElasticExpand(expanded) {
                SafeExpandableViewport(maxHeight = 420.dp) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        if (diff) {
                            DiffText(displayDetail)
                        } else {
                            SelectionContainer {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    NativeUiRenderSafety.splitPlainText(displayDetail).forEach { chunk ->
                                        Text(chunk, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun DiffText(diff: String) {
    var visibleLineLimit by remember(diff) { mutableIntStateOf(300) }
    val visibleLines = remember(diff, visibleLineLimit) {
        diff.lineSequence().take(visibleLineLimit + 1).toList()
    }
    val hasMore = visibleLines.size > visibleLineLimit
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            visibleLines.take(visibleLineLimit).forEach { line ->
                val background = when {
                    line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    line.startsWith("+") -> Color(0xFFD8F3DC)
                    line.startsWith("-") -> Color(0xFFFFDAD6)
                    else -> Color.Transparent
                }
                val foreground = when {
                    line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF175C2C)
                    line.startsWith("-") && !line.startsWith("---") -> Color(0xFF8C1D18)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = line.ifEmpty { " " },
                    modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 10.dp, vertical = 1.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = foreground,
                )
            }
            if (hasMore) {
                TextButton(onClick = { visibleLineLimit += 300 }) {
                    Text(nativeText(LocalNativeLanguage.current, "\u52a0\u8f7d\u66f4\u591a diff", "Load more diff"))
                }
            }
        }
    }
}

@Composable
private fun RikkaErrorMessage(text: String, onRetry: (() -> Unit)?) {
    val language = LocalNativeLanguage.current
    val displayText = remember(text) { NativeUiRenderSafety.errorSummary(text) }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(nativeText(language, "\u53d1\u751f\u9519\u8bef", "Error"), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp)); SelectionContainer { Text(displayText, style = MaterialTheme.typography.bodySmall) }
            if (onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(nativeText(language, "\u91cd\u8bd5", "Retry"))
            }
        }
    }
}
@Composable
private fun NativeGoalBanner(objective: String, paused: Boolean, enabled: Boolean, onEdit: () -> Unit, onTogglePause: () -> Unit, onClear: () -> Unit) {
    val language = LocalNativeLanguage.current
    var expanded by remember(objective) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 0.78f, stiffness = 420f), label = "goalArrow")
    Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.LookTop, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(nativeText(language, if (paused) "\u76ee\u6807\u5df2\u6682\u505c" else "\u76ee\u6807\u8fdb\u884c\u4e2d", if (paused) "Goal paused" else "Goal active"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(objective, maxLines = if (expanded) 6 else 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Icon(HugeIcons.ArrowDown01, null, Modifier.size(16.dp).graphicsLayer { rotationZ = arrowRotation })
            }
            QElasticExpand(expanded) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit, enabled = enabled) { Text(nativeText(language, "\u7f16\u8f91", "Edit")) }
                    TextButton(onClick = onTogglePause, enabled = enabled) { Text(nativeText(language, if (paused) "\u6062\u590d" else "\u6682\u505c", if (paused) "Resume" else "Pause")) }
                    TextButton(onClick = onClear, enabled = enabled) { Text(nativeText(language, "\u6e05\u9664", "Clear")) }
                }
            }
        }
    }
}

@Composable
private fun ComposerModeCapsules(
    selectedMode: String,
    activeGoal: String,
    onModeSelected: (String) -> Unit,
    onRequestGoal: () -> Unit,
    onClearGoal: () -> Unit,
    selectedSkills: List<NativeSkill>,
    onRemoveSkill: (NativeSkill) -> Unit,
) {
    val language = LocalNativeLanguage.current
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Surface(
                modifier = Modifier.clickable { menuExpanded = true },
                shape = CircleShape,
                color = if (selectedMode == "plan") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selectedMode == "plan") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (selectedMode == "plan") HugeIcons.Zap else HugeIcons.Sparkles, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    AnimatedContent(
                        targetState = selectedMode,
                        transitionSpec = {
                            (fadeIn(tween(150)) + slideInVertically(tween(190, easing = FastOutSlowInEasing)) { it / 3 }) togetherWith
                                (fadeOut(tween(90)) + slideOutVertically(tween(140, easing = FastOutSlowInEasing)) { -it / 3 })
                        },
                        label = "composerModeLabel",
                    ) { mode ->
                        Text(if (mode == "plan") nativeText(language, "\u8ba1\u5212\u6a21\u5f0f", "Plan mode") else nativeText(language, "\u9ed8\u8ba4\u6a21\u5f0f", "Default mode"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(3.dp))
                    Icon(HugeIcons.ArrowDown01, null, Modifier.size(13.dp))
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(nativeText(language, "\u9ed8\u8ba4\u6a21\u5f0f", "Default mode")) },
                    leadingIcon = { Icon(HugeIcons.Sparkles, null, Modifier.size(18.dp)) },
                    onClick = { onModeSelected("default"); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(nativeText(language, "\u8ba1\u5212\u6a21\u5f0f", "Plan mode")) },
                    leadingIcon = { Icon(HugeIcons.Zap, null, Modifier.size(18.dp)) },
                    onClick = { onModeSelected("plan"); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(if (activeGoal.isBlank()) nativeText(language, "\u8bbe\u7f6e\u76ee\u6807", "Set goal") else nativeText(language, "\u7f16\u8f91\u76ee\u6807", "Edit goal")) },
                    leadingIcon = { Icon(HugeIcons.LookTop, null, Modifier.size(18.dp)) },
                    onClick = { menuExpanded = false; onRequestGoal() },
                )
            }
        }
        AnimatedVisibility(
            visible = activeGoal.isNotBlank(),
            enter = fadeIn(tween(150)) + expandHorizontally(tween(230, easing = FastOutSlowInEasing), expandFrom = Alignment.Start),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(180, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start),
        ) {
            Surface(
                modifier = Modifier.clickable { onClearGoal() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(Modifier.padding(start = 10.dp, end = 7.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.LookTop, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(nativeText(language, "\u76ee\u6807", "Goal"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(5.dp))
                    Icon(HugeIcons.Cancel01, nativeText(language, "\u6e05\u9664\u76ee\u6807", "Clear goal"), Modifier.size(13.dp))
                }
            }
        }
        selectedSkills.forEach { skill ->
            Surface(
                    modifier = Modifier.clickable { onRemoveSkill(skill) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(Modifier.padding(start = 10.dp, end = 7.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(HugeIcons.Files02, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(skill.name, maxLines = 1, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(5.dp))
                        Icon(HugeIcons.Cancel01, "\u79fb\u9664 Skill", Modifier.size(13.dp))
                    }
                }
        }
    }
}

@Composable
private fun GoalEditorDialog(initialValue: String, enabled: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialValue.isBlank()) nativeText(language, "\u8bbe\u7f6e\u6d3b\u8dc3\u76ee\u6807", "Set active goal") else nativeText(language, "\u7f16\u8f91\u6d3b\u8dc3\u76ee\u6807", "Edit active goal")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(nativeText(language, "Codex \u4f1a\u5728\u540e\u7eed\u5bf9\u8bdd\u4e2d\u6301\u7eed\u8ddf\u8e2a\u8fd9\u4e2a\u76ee\u6807\u3002", "Codex will keep tracking this goal in later turns."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 7,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text(nativeText(language, "\u63cf\u8ff0\u5e0c\u671b Codex \u6301\u7eed\u8ddf\u8fdb\u7684\u76ee\u6807", "Describe what Codex should keep working toward")) },
                )
            }
        },
        confirmButton = { TextButton(enabled = enabled && value.isNotBlank(), onClick = { onConfirm(value.trim()) }) { Text(nativeText(language, "\u542f\u7528\u76ee\u6807", "Enable goal")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RikkaChatInput(
    value: String,
    enabled: Boolean,
    loading: Boolean,
    modelLabel: String,
    onModelClick: () -> Unit,
    effortOptions: List<String>,
    selectedEffort: String,
    onEffortSelected: (String) -> Unit,
    selectedMode: String,
    permissionMode: String,
    onPermissionModeSelected: (String) -> Unit,
    activeGoal: String,
    onModeSelected: (String) -> Unit,
    onRequestGoal: () -> Unit,
    onClearGoal: () -> Unit,
    selectedSkills: List<NativeSkill>,
    onRemoveSkill: (NativeSkill) -> Unit,
    attachments: List<NativeAttachment>,
    onMoreClick: () -> Unit,
    onCompact: () -> Unit,
    onRetryLast: () -> Unit,
    onEditLast: () -> Unit,
    onRemoveAttachment: (NativeAttachment) -> Unit,
    onPreviewAttachment: (NativeAttachment) -> Unit,
    onValueChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalNativeLanguage.current
    val textState = remember { TextFieldState(initialText = value) }
    val latestExternalValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    var toolsExpanded by remember { mutableStateOf(false) }
    var permissionExpanded by remember { mutableStateOf(false) }

    // Do not read textState.text in this parent restart scope. The TextField and send button
    // below observe it in their own small scopes, so an IME edit cannot recompose attachments,
    // mode capsules, animated surfaces and the rest of the composer.
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collectLatest { localText ->
            if (localText.trimStart().startsWith("/")) toolsExpanded = true
            delay(1000L)
            if (localText != latestExternalValue) latestOnValueChange(localText)
        }
    }
    LaunchedEffect(value) {
        if (value != textState.text.toString()) textState.setTextAndPlaceCursorAtEnd(value)
    }
    if (permissionExpanded) {
        PermissionModeSheet(
            selected = permissionMode,
            onSelect = {
                onPermissionModeSelected(it)
                permissionExpanded = false
            },
            onDismiss = { permissionExpanded = false },
        )
    }
    if (toolsExpanded) {
        ModalBottomSheet(
            onDismissRequest = { toolsExpanded = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            NativeComposerToolSheet(
                onDismiss = { toolsExpanded = false },
                onCommand = { command ->
                    when (command) {
                        "__attachments__" -> onMoreClick()
                        "__compact__" -> onCompact()
                        "__retry__" -> onRetryLast()
                        "__edit__" -> onEditLast()
                        else -> {
                            textState.setTextAndPlaceCursorAtEnd(command)
                            onValueChange(command)
                        }
                    }
                    toolsExpanded = false
                },
            )
        }
    }
    val imeVisible = WindowInsets.isImeVisible
    // Apple-style spring: interruptible, slightly under-damped and transform-only. The input
    // keeps a stable layout width while the GPU scales it from the resting inset to full width,
    // so opening the IME does not remeasure the complete composer on every animation frame.
    val keyboardMorph by animateFloatAsState(
        targetValue = if (imeVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = 520f,
            visibilityThreshold = 0.001f,
        ),
        label = "inputKeyboardSpring",
    )
    val boundedKeyboardMorph = keyboardMorph.coerceIn(0f, 1f)
    val composerScaleX = 0.958f + 0.042f * keyboardMorph
    val composerScaleY = 0.988f + 0.012f * keyboardMorph
    val bottomCorner = 28.dp * (1f - boundedKeyboardMorph)
    val bottomPadding = if (imeVisible) 0.dp else 8.dp
    val keyboardOverlap = 3.dp * boundedKeyboardMorph
    val inputBorderColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.surfaceContainerLow,
        boundedKeyboardMorph,
    )
    val inputShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = bottomCorner, bottomStart = bottomCorner)
    val insetModifier = if (imeVisible) Modifier.imePadding() else Modifier
    Surface(modifier = modifier, color = Color.Transparent) {
        Column(
            modifier = insetModifier
                .padding(top = 8.dp, bottom = bottomPadding)
                .offset(y = keyboardOverlap)
                .graphicsLayer {
                    scaleX = composerScaleX
                    scaleY = composerScaleY
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        ) {
            Surface(
                // Text entry is a high-frequency interaction. Animating container size makes
                // non-empty/multiline text remeasure throughout the IME transition.
                modifier = Modifier.fillMaxWidth().onSizeChanged { onHeightChanged(it.height) },
                shape = inputShape,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, inputBorderColor),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    if (attachments.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(attachments, key = { it.path }) { attachment ->
                                Surface(
                                    modifier = Modifier.clickable { onPreviewAttachment(attachment) },
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(modifier = Modifier.padding(start = 6.dp, top = 5.dp, bottom = 5.dp, end = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (attachment.image) AttachmentThumbnail(attachment.path, Modifier.size(34.dp).clip(MaterialTheme.shapes.small))
                                        else Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                            Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) { Icon(HugeIcons.Files02, null, modifier = Modifier.size(17.dp)) }
                                        }
                                        Spacer(Modifier.width(7.dp))
                                        Column(modifier = Modifier.widthIn(max = 140.dp)) {
                                            Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                            Text(formatFileSize(java.io.File(attachment.path).length()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { onRemoveAttachment(attachment) }, modifier = Modifier.size(28.dp)) { Icon(HugeIcons.Cancel01, "移除", modifier = Modifier.size(14.dp)) }
                                    }
                                }
                            }
                        }
                    }
                    ComposerModeCapsules(
                        selectedMode = selectedMode,
                        activeGoal = activeGoal,
                        onModeSelected = onModeSelected,
                        onRequestGoal = onRequestGoal,
                        onClearGoal = onClearGoal,
                        selectedSkills = selectedSkills,
                        onRemoveSkill = onRemoveSkill,
                    )
                    BasicTextField(
                        state = textState,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 12.dp, vertical = 15.dp).onPreviewKeyEvent { event ->
                            val currentText = textState.text.toString()
                            if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.Enter && enabled && currentText.isNotBlank()) {
                                onSend(currentText)
                                textState.setTextAndPlaceCursorAtEnd("")
                                onValueChange("")
                                true
                            } else false
                        },
                        enabled = !loading,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = if (loading) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp,
                        ),
                        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 5),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorator = { innerTextField ->
                            Box(Modifier.fillMaxWidth()) {
                                if (textState.text.isEmpty()) {
                                    Text(
                                        nativeText(language, "\u8f93\u5165\u6d88\u606f", "Type a message"),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            InputTool(HugeIcons.Sparkles, modelLabel.ifBlank { "模型" }, onModelClick)
                            LiquidEffortTool(effortOptions, selectedEffort, onEffortSelected)
                            InputTool(
                                HugeIcons.Settings03,
                                NativePermissionMode.label(permissionMode, language != "en"),
                                { permissionExpanded = true },
                            )
                            InputTool(HugeIcons.Add01, "\u5de5\u5177", { toolsExpanded = true })
                        }
                        ComposerSendButton(
                            textState = textState,
                            enabled = enabled,
                            loading = loading,
                            hasAttachments = attachments.isNotEmpty(),
                            onSend = { currentText ->
                                onSend(currentText)
                                if (!loading && enabled && (currentText.isNotBlank() || attachments.isNotEmpty())) {
                                    textState.setTextAndPlaceCursorAtEnd("")
                                    onValueChange("")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerSendButton(
    textState: TextFieldState,
    enabled: Boolean,
    loading: Boolean,
    hasAttachments: Boolean,
    onSend: (String) -> Unit,
) {
    val inputText = textState.text.toString()
    val canSend = enabled && (inputText.isNotBlank() || hasAttachments)
    Surface(
        modifier = Modifier.size(42.dp).clickable(enabled = loading || canSend) { onSend(inputText) },
        shape = CircleShape,
        color = when {
            loading -> MaterialTheme.colorScheme.errorContainer
            !canSend -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.primary
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (loading) HugeIcons.Cancel01 else HugeIcons.ArrowUp02,
                contentDescription = if (loading) "\u505c\u6b62" else "\u53d1\u9001",
                modifier = Modifier.size(21.dp),
                tint = when {
                    loading -> MaterialTheme.colorScheme.onErrorContainer
                    !canSend -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else -> MaterialTheme.colorScheme.onPrimary
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionModeSheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val language = LocalNativeLanguage.current
    val options = listOf(
        Triple(
            NativePermissionMode.WORKSPACE,
            nativeText(language, "\u5de5\u4f5c\u533a\u8bbf\u95ee", "Workspace access"),
            nativeText(language, "\u53ef\u8bfb\u5199\u5f53\u524d\u9879\u76ee\uff1b\u8d8a\u754c\u64cd\u4f5c\u4f1a\u5148\u8be2\u95ee", "Read and write the project; ask before broader access"),
        ),
        Triple(
            NativePermissionMode.FULL_ACCESS,
            nativeText(language, "\u5b8c\u5168\u8bbf\u95ee", "Full access"),
            nativeText(language, "\u4e0d\u4f7f\u7528\u6c99\u7bb1\u4e14\u4e0d\u8be2\u95ee\uff0c\u547d\u4ee4\u53ef\u4ee5\u76f4\u63a5\u8fd0\u884c", "No sandbox or prompts; commands run directly"),
        ),
        Triple(
            NativePermissionMode.READ_ONLY,
            nativeText(language, "\u53ea\u8bfb", "Read only"),
            nativeText(language, "\u5141\u8bb8\u68c0\u67e5\u6587\u4ef6\uff0c\u4fee\u6539\u6216\u8d8a\u754c\u547d\u4ee4\u9700\u8981\u786e\u8ba4", "Inspect files; writes and broader commands require approval"),
        ),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(nativeText(language, "\u8bbf\u95ee\u6743\u9650", "Access permissions"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                nativeText(language, "\u6743\u9650\u4f1a\u5e94\u7528\u5230\u4e0b\u4e00\u6761\u6d88\u606f\uff0c\u4e5f\u4f1a\u8986\u76d6\u65e7\u5bf9\u8bdd\u4fdd\u5b58\u7684\u6c99\u7bb1\u8bbe\u7f6e\u3002", "Applies to the next message and overrides sandbox settings saved in older conversations."),
                Modifier.padding(top = 4.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            options.forEach { (mode, title, description) ->
                val active = NativePermissionMode.normalize(selected) == mode
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(mode) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (mode == NativePermissionMode.FULL_ACCESS) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f)) else null,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (active) HugeIcons.Tick02 else HugeIcons.Settings03,
                            null,
                            Modifier.size(20.dp),
                            tint = if (mode == NativePermissionMode.FULL_ACCESS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeComposerToolSheet(onDismiss: () -> Unit, onCommand: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    val tools = listOf(
        Triple(HugeIcons.Sparkles, "\u538b\u7f29\u4e0a\u4e0b\u6587", "__compact__"),
        Triple(HugeIcons.Add01, "\u6dfb\u52a0\u56fe\u7247\u6216\u6587\u4ef6", "__attachments__"),
        Triple(HugeIcons.Files02, "\u5217\u51fa\u5f53\u524d\u76ee\u5f55", "/ls"),
        Triple(HugeIcons.Search01, "\u641c\u7d22\u6587\u4ef6", "/search "),
        Triple(HugeIcons.PencilEdit01, "\u7f16\u8f91\u4e0a\u4e00\u6761\u6d88\u606f", "__edit__"),
        Triple(HugeIcons.Refresh03, "\u91cd\u65b0\u751f\u6210\u56de\u7b54", "__retry__"),
        Triple(HugeIcons.Cancel01, "\u6e05\u7a7a\u8f93\u5165", ""),
    )
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(nativeText(language, "\u5de5\u5177", "Tools"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(HugeIcons.Cancel01, "\u5173\u95ed") }
        }
        Text(nativeText(language, "\u8f93\u5165 / \u4e5f\u53ef\u4ee5\u968f\u65f6\u6253\u5f00\u6b64\u9762\u677f", "Type / to open this panel"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
        tools.forEach { (icon, title, command) ->
            Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onCommand(command) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(when (command) { "__attachments__" -> "\u9009\u62e9"; "__retry__" -> "\u6267\u884c"; "__edit__" -> "\u7f16\u8f91"; "" -> "\u6e05\u9664"; else -> command }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun decodeSampledBitmap(path: String, maxEdge: Int = 1280): android.graphics.Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) sample *= 2
    return android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun AttachmentThumbnail(path: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) { decodeSampledBitmap(path) }
    AndroidView(
        modifier = modifier,
        factory = { context -> android.widget.ImageView(context).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
        update = { view -> view.setImageBitmap(bitmap) },
    )
}

@Composable
private fun AttachmentPreviewDialog(attachment: NativeAttachment, onDismiss: () -> Unit) {
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(attachment.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (attachment.image) AttachmentThumbnail(attachment.path, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp).clip(MaterialTheme.shapes.large))
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (attachment.image) nativeText(language, "\u56fe\u7247\u9644\u4ef6", "Image attachment") else nativeText(language, "\u6587\u4ef6\u9644\u4ef6", "File attachment"), style = MaterialTheme.typography.labelLarge)
                        Text(formatFileSize(java.io.File(attachment.path).length()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(attachment.path, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u5b8c\u6210", "Done")) } },
    )
}

@Composable
private fun SkillPickerDialog(
    skills: List<NativeSkill>,
    selected: List<NativeSkill>,
    onDismiss: () -> Unit,
    onSelect: (NativeSkill) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val language = LocalNativeLanguage.current
    val results = remember(skills, query) {
        skills.filter { query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) }
    }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u5f15\u7528 Skill") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = CircleShape,
                    leadingIcon = { Icon(HugeIcons.Search01, null, Modifier.size(18.dp)) },
                    placeholder = { Text("\u641c\u7d22 Skill") },
                )
                if (results.isEmpty()) {
                    Text(nativeText(language, "\u6ca1\u6709\u627e\u5230\u53ef\u7528 Skill", "No skills found"), modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(results, key = { it.path }) { skill ->
                            val isSelected = selected.any { it.path == skill.path }
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !isSelected) { onSelect(skill) },
                                shape = RoundedCornerShape(17.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.Top) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) { Icon(HugeIcons.Sparkles, null, Modifier.padding(7.dp).size(15.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (skill.description.isNotBlank()) Text(skill.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                                    }
                                    Text(nativeText(language, "\u5df2\u5f15\u7528", "Added"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u5173\u95ed", "Close")) } },
    )
}

@Composable
private fun RikkaFilesPicker(onPickImage: () -> Unit, onPickFile: () -> Unit, onPickSkill: () -> Unit) {
    val language = LocalNativeLanguage.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RikkaFileAction(HugeIcons.Image02, "图片", onPickImage)
            RikkaFileAction(HugeIcons.Files02, "文件", onPickFile)
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        Text("附件会随下一条消息发送", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RikkaFileAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, modifier = Modifier.size(56.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, modifier = Modifier.size(24.dp)) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun effortLabel(value: String, language: String): String = when (value.lowercase()) {
    "none" -> nativeText(language, "\u5173\u95ed", "Off")
    "minimal" -> nativeText(language, "\u6781\u4f4e", "Minimal")
    "low" -> nativeText(language, "\u4f4e", "Low")
    "medium" -> nativeText(language, "\u4e2d", "Medium")
    "high" -> nativeText(language, "\u9ad8", "High")
    "xhigh" -> nativeText(language, "\u6781\u9ad8", "Very high")
    "ultra" -> "Ultra"
    else -> value
}

@Composable
private fun LiquidEffortTool(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var popupVisible by remember { mutableStateOf(false) }
    var contentReady by remember { mutableStateOf(false) }
    var previewEffort by remember(options, selected) { mutableStateOf(selected) }
    var effortDragging by remember { mutableStateOf(false) }
    val effortLabelScale by animateFloatAsState(
        targetValue = if (effortDragging) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "effortLabelScale",
    )
    val menuVisibility = remember { MutableTransitionState(false) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = when { pressed -> 1.13f; expanded -> 1.045f; else -> 1f },
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 460f),
        label = "effortPressScale",
    )
    val panelElevation by animateDpAsState(
        targetValue = if (contentReady && expanded) 10.dp else 0.dp,
        animationSpec = tween(190, delayMillis = if (expanded) 35 else 0, easing = LinearOutSlowInEasing),
        label = "effortPanelElevation",
    )
    fun open() { popupVisible = true; expanded = true; contentReady = false; menuVisibility.targetState = false }
    fun close() {
        contentReady = false; expanded = false; menuVisibility.targetState = false
        scope.launch { delay(265); popupVisible = false }
    }
    LaunchedEffect(popupVisible, expanded) {
        if (popupVisible && expanded) { delay(20); menuVisibility.targetState = true; delay(35); contentReady = true }
    }
    val gapPx = with(density) { 8.dp.roundToPx() }
    val shadowPadPx = with(density) { 14.dp.roundToPx() }
    val positionProvider = remember(gapPx, shadowPadPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.left - shadowPadPx).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val above = anchorBounds.top - popupContentSize.height + shadowPadPx - gapPx
                val y = if (above >= 0) above else (anchorBounds.bottom + gapPx - shadowPadPx).coerceAtMost(windowSize.height - popupContentSize.height)
                return IntOffset(x, y)
            }
        }
    }
    Box {
        Surface(
            modifier = Modifier.size(40.dp).graphicsLayer { scaleX = buttonScale; scaleY = buttonScale },
            shape = CircleShape,
            color = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (expanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = interaction, indication = null) { if (expanded) close() else open() }, contentAlignment = Alignment.Center) {
                Icon(HugeIcons.Zap, "思维强度", modifier = Modifier.size(21.dp))
            }
        }
        if (popupVisible) {
            Popup(popupPositionProvider = positionProvider, onDismissRequest = { close() }, properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)) {
                AnimatedVisibility(
                    visibleState = menuVisibility,
                    enter = fadeIn(tween(155, easing = LinearOutSlowInEasing)) +
                        scaleIn(initialScale = 0.76f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = spring(dampingRatio = 0.52f, stiffness = 315f)) +
                        slideInVertically(initialOffsetY = { -it / 45 }, animationSpec = spring(dampingRatio = 0.66f, stiffness = 370f)),
                    exit = fadeOut(tween(135)) + scaleOut(targetScale = 0.84f, transformOrigin = TransformOrigin(0f, 1f), animationSpec = tween(185, easing = FastOutSlowInEasing)),
                ) {
                    Box(Modifier.padding(14.dp)) {
                        Surface(
                            modifier = Modifier.width(310.dp), shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 5.dp, shadowElevation = panelElevation,
                        ) {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                AnimatedVisibility(
                                    visible = contentReady,
                                    enter = fadeIn(tween(145, delayMillis = 20)) + slideInHorizontally(initialOffsetX = { -it / 12 }, animationSpec = spring(dampingRatio = 0.66f, stiffness = 430f)),
                                    exit = fadeOut(tween(90)),
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(HugeIcons.Zap, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("思维强度", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        AnimatedContent(
                                            targetState = previewEffort,
                                            modifier = Modifier.graphicsLayer { scaleX = effortLabelScale; scaleY = effortLabelScale },
                                            transitionSpec = {
                                                (fadeIn(tween(90)) + slideInVertically(initialOffsetY = { it / 5 }, animationSpec = spring(dampingRatio = 0.72f, stiffness = 560f)))
                                                    .togetherWith(fadeOut(tween(70)) + slideOutVertically(targetOffsetY = { -it / 6 }, animationSpec = tween(90)))
                                            },
                                            label = "effortLabelPreview",
                                        ) { effort ->
                                            Text(effortLabel(effort, LocalNativeLanguage.current), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                AnimatedVisibility(
                                    visible = contentReady,
                                    enter = fadeIn(tween(160, delayMillis = 65)) + scaleIn(initialScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 0.5f), animationSpec = spring(dampingRatio = 0.64f, stiffness = 390f)),
                                    exit = fadeOut(tween(90)),
                                ) {
                                    LiquidEffortSlider(
                                        options = options,
                                        selected = selected,
                                        onPreview = { previewEffort = it },
                                        onDraggingChanged = { effortDragging = it },
                                        onSelect = onSelect,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                                AnimatedVisibility(
                                    visible = contentReady,
                                    enter = fadeIn(tween(150, delayMillis = 105)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(190, easing = LinearOutSlowInEasing)),
                                    exit = fadeOut(tween(80)),
                                ) {
                                    Text("拖动或点击选择，松手后自动吸附", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidEffortSlider(
    options: List<String>, selected: String,
    onPreview: (String) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val scope = rememberCoroutineScope()
    var visualIndex by remember(options) { mutableFloatStateOf(options.indexOf(selected).coerceAtLeast(0).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val previewIndex = visualIndex.roundToInt().coerceIn(options.indices)
    val thumbScale by animateFloatAsState(if (dragging) 1.22f else 1f, spring(dampingRatio = 0.72f, stiffness = 480f), label = "liquidThumbScale")

    fun updateDragging(value: Boolean) { dragging = value; onDraggingChanged(value) }
    fun settle(index: Float, commit: Boolean) {
        val target = index.roundToInt().coerceIn(options.indices)
        settleJob?.cancel()
        settling = true
        settleJob = scope.launch {
            animate(visualIndex, target.toFloat(), animationSpec = tween(150, easing = FastOutSlowInEasing)) { value, _ ->
                visualIndex = value
                onPreview(options[value.roundToInt().coerceIn(options.indices)])
            }
            visualIndex = target.toFloat()
            onPreview(options[target])
            if (commit && options[target] != selected) onSelect(options[target])
            settling = false
        }
    }
    LaunchedEffect(selected, options, dragging, settling) {
        if (!dragging && !settling) {
            visualIndex = options.indexOf(selected).coerceAtLeast(0).toFloat()
            onPreview(options[visualIndex.roundToInt().coerceIn(options.indices)])
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.CenterStart) {
            val steps = (options.size - 1).coerceAtLeast(1)
            val trackWidthPx = constraints.maxWidth.toFloat()
            val segmentPx = trackWidthPx / steps
            val progress = (visualIndex / steps).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)))
            Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.02f)).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                options.forEachIndexed { index, _ -> Box(Modifier.size(if (index == previewIndex) 7.dp else 5.dp).clip(CircleShape).background(if (index <= visualIndex + 0.01f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))) }
            }
            Surface(
                modifier = Modifier.size(42.dp, 28.dp).graphicsLayer {
                    translationX = (progress * (trackWidthPx - size.width)).coerceIn(0f, (trackWidthPx - size.width).coerceAtLeast(0f))
                    scaleX = thumbScale; scaleY = if (dragging) 1.10f else thumbScale
                },
                shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (dragging) 0.18f else 0.10f)),
                shadowElevation = if (dragging) 7.dp else 3.dp,
            ) { Box(contentAlignment = Alignment.Center) { Box(Modifier.size(25.dp, 12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.22f else 0.14f))) } }
            // Keep the gesture target fixed across the full track. A translated graphicsLayer does
            // not move hit-testing bounds, which made the visually moved thumb fail on the next drag.
            Box(
                Modifier.fillMaxWidth().height(46.dp)
                    .pointerInput(options, selected) {
                        detectTapGestures { offset ->
                            val target = ((offset.x / size.width) * steps).coerceIn(0f, steps.toFloat())
                            onPreview(options[target.roundToInt().coerceIn(options.indices)])
                            settle(target, commit = true)
                        }
                    }
                    .pointerInput(options, selected) {
                        var gestureIndex = visualIndex
                        detectDragGestures(
                            onDragStart = { offset ->
                                settleJob?.cancel()
                                updateDragging(true)
                                gestureIndex = ((offset.x / size.width) * steps).coerceIn(0f, steps.toFloat())
                                visualIndex = gestureIndex
                                onPreview(options[gestureIndex.roundToInt().coerceIn(options.indices)])
                            },
                            onDragCancel = {
                                settle(options.indexOf(selected).coerceAtLeast(0).toFloat(), commit = false)
                                updateDragging(false)
                            },
                            onDragEnd = {
                                settle(gestureIndex, commit = true)
                                updateDragging(false)
                            },
                        ) { change, _ ->
                            change.consume()
                            gestureIndex = ((change.position.x / size.width) * steps).coerceIn(0f, steps.toFloat())
                            visualIndex = gestureIndex
                            onPreview(options[gestureIndex.roundToInt().coerceIn(options.indices)])
                        }
                    },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            options.forEachIndexed { index, effort ->
                val active = index == previewIndex
                val tickScale = if (active && dragging) 1.08f else 1f
                Text(effortLabel(effort, LocalNativeLanguage.current), modifier = Modifier.graphicsLayer { scaleX = tickScale; scaleY = tickScale }, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InputTool(icon: ImageVector, description: String, onClick: () -> Unit = {}) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, description, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun RikkaDrawerV2(
    currentThreadId: String,
    modelLabel: String,
    conversations: List<NativeConversation>,
    onSearch: () -> Unit,
    onRenameConversation: (NativeConversation) -> Unit,
    onDeleteConversation: (NativeConversation) -> Unit,
    onToggleFavorite: (NativeConversation) -> Unit,
    onResumeConversation: (String) -> Unit,
    onClose: () -> Unit,
    onNewConversation: () -> Unit,
    onBackHome: () -> Unit,
    onOpenLegacyWebUi: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    val context = LocalContext.current
    var mode by remember { mutableStateOf("tasks") }
    var projectRevision by remember { mutableIntStateOf(0) }
    var newMenuExpanded by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var renameProject by remember { mutableStateOf<NativeDrawerProject?>(null) }
    var deleteProject by remember { mutableStateOf<NativeDrawerProject?>(null) }
    var expandedProjects by remember { mutableStateOf(emptySet<String>()) }
    val conversationRevision = conversations.fold(1) { value, conversation ->
        var result = 31 * value + conversation.threadId.hashCode()
        result = 31 * result + conversation.title.hashCode()
        result = 31 * result + conversation.projectPath.hashCode()
        result = 31 * result + conversation.state.hashCode()
        result = 31 * result + conversation.favorite.hashCode()
        result = 31 * result + conversation.attention.hashCode()
        result
    }
    val conversationSnapshot = remember(conversationRevision) { conversations.toList() }
    val registeredProjects = remember(projectRevision) { NativeDrawerProjectStore.registered(context) }
    val hiddenProjects = remember(projectRevision) { NativeDrawerProjectStore.hiddenPaths(context) }
    val projects = remember(conversationRevision, registeredProjects, hiddenProjects, projectRevision) {
        val paths = linkedSetOf<String>()
        registeredProjects.forEach { paths.add(it.path) }
        conversationSnapshot.mapTo(paths) { it.projectPath }.remove("")
        paths.filterNot(hiddenProjects::contains).map { path ->
            registeredProjects.firstOrNull { it.path == path }
                ?: NativeDrawerProject(path, NativeDrawerProjectStore.displayName(context, path))
        }
    }
    val tasksByProject = remember(conversationRevision) { conversationSnapshot.groupBy { it.projectPath } }
    val openTask: (NativeConversation) -> Unit = remember(context, onResumeConversation) {
        { conversation ->
            if (conversation.projectPath.isNotBlank()) {
                context.getSharedPreferences("codex_mobile", android.content.Context.MODE_PRIVATE).edit()
                    .putBoolean("custom_project_root_enabled", true)
                    .putString("custom_project_root", conversation.projectPath)
                    .commit()
            }
            onResumeConversation(conversation.threadId)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerQuickAction(HugeIcons.Search01, nativeText(language, "\u641c\u7d22", "Search"), Modifier.weight(1f), onSearch)
                Box(Modifier.weight(1f)) {
                    DrawerQuickAction(
                        HugeIcons.MessageAdd01,
                        nativeText(language, "\u65b0\u5efa", "New"),
                        Modifier.fillMaxWidth(),
                    ) { newMenuExpanded = true }
                    DropdownMenu(expanded = newMenuExpanded, onDismissRequest = { newMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(nativeText(language, "\u65b0\u5efa\u4efb\u52a1", "New task")) },
                            leadingIcon = { Icon(HugeIcons.MessageAdd01, null) },
                            onClick = { newMenuExpanded = false; onNewConversation() },
                        )
                        DropdownMenuItem(
                            text = { Text(nativeText(language, "\u65b0\u5efa\u9879\u76ee\u6587\u4ef6\u5939", "New project folder")) },
                            leadingIcon = { Icon(HugeIcons.Folder01, null) },
                            onClick = { newMenuExpanded = false; showNewProjectDialog = true },
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("tasks" to nativeText(language, "\u4efb\u52a1", "Tasks"), "projects" to nativeText(language, "\u9879\u76ee", "Projects")).forEach { (value, label) ->
                    Surface(
                        onClick = { mode = value },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                        color = if (mode == value) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (mode == value) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (mode == "tasks") {
                    if (conversationSnapshot.isEmpty()) {
                        item { DrawerEmptyState(nativeText(language, "\u8fd8\u6ca1\u6709\u4efb\u52a1", "No tasks yet")) }
                    } else {
                        items(conversationSnapshot, key = { "task:${it.threadId}" }) { conversation ->
                            DrawerConversationTaskRow(
                                conversation = conversation,
                                selected = conversation.threadId == currentThreadId,
                                indented = false,
                                onOpen = { openTask(conversation) },
                                onFavorite = { onToggleFavorite(conversation) },
                                onRename = { onRenameConversation(conversation) },
                                onDelete = { onDeleteConversation(conversation) },
                            )
                        }
                    }
                } else {
                    if (projects.isEmpty()) {
                        item { DrawerEmptyState(nativeText(language, "\u65b0\u5efa\u9879\u76ee\u540e\uff0cAI \u5c06\u9ed8\u8ba4\u8bfb\u53d6\u8be5\u6587\u4ef6\u5939", "Create a project to give AI a default readable folder")) }
                    }
                    projects.forEach { project ->
                        val projectTasks = tasksByProject[project.path].orEmpty()
                        item(key = "project:${project.path}") {
                            DrawerProjectRow(
                                project = project,
                                taskCount = projectTasks.size,
                                expanded = project.path in expandedProjects,
                                onToggle = {
                                    expandedProjects = if (project.path in expandedProjects) expandedProjects - project.path else expandedProjects + project.path
                                },
                                onRename = { renameProject = project },
                                onDelete = { deleteProject = project },
                            )
                        }
                        if (project.path in expandedProjects) {
                            if (projectTasks.isEmpty()) {
                                item(key = "empty:${project.path}") {
                                    Text(
                                        nativeText(language, "\u8be5\u9879\u76ee\u8fd8\u6ca1\u6709\u4efb\u52a1", "No tasks in this project"),
                                        modifier = Modifier.padding(start = 46.dp, top = 6.dp, bottom = 8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            } else {
                                items(projectTasks, key = { "project-task:${it.threadId}" }) { conversation ->
                                    DrawerConversationTaskRow(
                                        conversation = conversation,
                                        selected = conversation.threadId == currentThreadId,
                                        indented = true,
                                        onOpen = { openTask(conversation) },
                                        onFavorite = { onToggleFavorite(conversation) },
                                        onRename = { onRenameConversation(conversation) },
                                        onDelete = { onDeleteConversation(conversation) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DrawerQuickAction(
                HugeIcons.Settings03,
                nativeText(language, "\u8bbe\u7f6e", "Settings"),
                Modifier.fillMaxWidth(),
                onBackHome,
            )
        }
    }

    if (showNewProjectDialog) {
        var name by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        FlClashAnimatedDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text(nativeText(language, "\u65b0\u5efa\u9879\u76ee", "New project")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(nativeText(language, "\u9879\u76ee\u4f1a\u7ec4\u7ec7\u4efb\u52a1\uff0c\u5e76\u5c06\u8be5\u6587\u4ef6\u5939\u8bbe\u4e3a AI \u7684\u9ed8\u8ba4\u5de5\u4f5c\u76ee\u5f55\u3002", "Projects organize tasks and set a default folder the AI can work in."))
                    OutlinedTextField(name, { name = it; error = "" }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(nativeText(language, "\u9879\u76ee\u540d\u79f0", "Project name")) })
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { NativeDrawerProjectStore.create(context, name) }
                        .onSuccess { project ->
                            context.getSharedPreferences("codex_mobile", android.content.Context.MODE_PRIVATE).edit()
                                .putBoolean("custom_project_root_enabled", true)
                                .putString("custom_project_root", project.path)
                                .commit()
                            projectRevision++
                            expandedProjects = expandedProjects + project.path
                            showNewProjectDialog = false
                            onNewConversation()
                        }
                        .onFailure { error = it.message.orEmpty() }
                }) { Text(nativeText(language, "\u521b\u5efa", "Create")) }
            },
            dismissButton = { TextButton(onClick = { showNewProjectDialog = false }) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        )
    }

    renameProject?.let { project ->
        var name by remember(project.path) { mutableStateOf(project.name) }
        FlClashAnimatedDialog(
            onDismissRequest = { renameProject = null },
            title = { Text(nativeText(language, "\u91cd\u547d\u540d\u9879\u76ee", "Rename project")) },
            text = { OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = { TextButton(onClick = { NativeDrawerProjectStore.rename(context, project.path, name); projectRevision++; renameProject = null }) { Text(nativeText(language, "\u4fdd\u5b58", "Save")) } },
            dismissButton = { TextButton(onClick = { renameProject = null }) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        )
    }

    deleteProject?.let { project ->
        FlClashAnimatedDialog(
            onDismissRequest = { deleteProject = null },
            title = { Text(nativeText(language, "\u5220\u9664\u9879\u76ee\uff1f", "Delete project?")) },
            text = { Text(nativeText(language, "\u5c06\u4ece\u9879\u76ee\u5217\u8868\u79fb\u9664“${project.name}”\u3002\u6e90\u6587\u4ef6\u5939\u548c\u4efb\u52a1\u4e0d\u4f1a\u88ab\u5220\u9664\u3002", "Remove \u201c${project.name}\u201d from Projects. Its source folder and tasks will not be deleted.")) },
            confirmButton = { TextButton(onClick = { NativeDrawerProjectStore.remove(context, project.path); expandedProjects = expandedProjects - project.path; projectRevision++; deleteProject = null }) { Text(nativeText(language, "\u5220\u9664", "Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteProject = null }) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
        )
    }
}

@Composable
private fun DrawerEmptyState(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 28.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConversationAttentionBadge(attention: String) {
    val language = LocalNativeLanguage.current
    val label = when (attention) {
        "answer" -> nativeText(language, "\u5f85\u7b54", "Answer")
        "approval" -> nativeText(language, "\u5f85\u5ba1\u6279", "Approve")
        "resume" -> nativeText(language, "\u5f85\u7ee7\u7eed", "Resume")
        else -> nativeText(language, "\u5f85\u6267\u884c", "Plan")
    }
    val container = when (attention) {
        "answer" -> MaterialTheme.colorScheme.tertiaryContainer
        "approval" -> MaterialTheme.colorScheme.errorContainer
        "resume" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (attention) {
        "answer" -> MaterialTheme.colorScheme.onTertiaryContainer
        "approval" -> MaterialTheme.colorScheme.onErrorContainer
        "resume" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DrawerProjectRow(
    project: NativeDrawerProject,
    taskCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var menuExpanded by remember(project.path) { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = onToggle,
                onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menuExpanded = true },
            ),
            shape = RoundedCornerShape(15.dp),
            color = if (expanded) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
        ) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.ArrowRight01, null, Modifier.size(15.dp).graphicsLayer { rotationZ = if (expanded) 90f else 0f })
                Spacer(Modifier.width(9.dp))
                Icon(HugeIcons.Folder01, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(project.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(taskCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f))
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(text = { Text(nativeText(language, "\u91cd\u547d\u540d", "Rename")) }, leadingIcon = { Icon(HugeIcons.PencilEdit01, null) }, onClick = { menuExpanded = false; onRename() })
            DropdownMenuItem(text = { Text(nativeText(language, "\u5220\u9664", "Delete")) }, leadingIcon = { Icon(HugeIcons.Delete01, null) }, onClick = { menuExpanded = false; onDelete() })
        }
    }
}

@Composable
private fun DrawerConversationTaskRow(
    conversation: NativeConversation,
    selected: Boolean,
    indented: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var menuExpanded by remember(conversation.threadId) { mutableStateOf(false) }
    Box(Modifier.padding(start = if (indented) 28.dp else 0.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = onOpen,
                onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menuExpanded = true },
            ),
            shape = RoundedCornerShape(15.dp),
            color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        ) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (conversation.state == CodexTaskStore.RUNNING) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                else Icon(HugeIcons.Sparkles, null, Modifier.size(18.dp), tint = if (conversation.state == CodexTaskStore.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(conversation.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                if (conversation.attention.isNotBlank()) {
                    Spacer(Modifier.width(7.dp))
                    ConversationAttentionBadge(conversation.attention)
                }
                if (conversation.favorite) Icon(HugeIcons.InLove, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.76f))
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(text = { Text(if (conversation.favorite) nativeText(language, "\u53d6\u6d88\u6536\u85cf", "Unfavorite") else nativeText(language, "\u6536\u85cf", "Favorite")) }, leadingIcon = { Icon(HugeIcons.InLove, null) }, onClick = { menuExpanded = false; onFavorite() })
            DropdownMenuItem(text = { Text(nativeText(language, "\u91cd\u547d\u540d", "Rename")) }, leadingIcon = { Icon(HugeIcons.PencilEdit01, null) }, onClick = { menuExpanded = false; onRename() })
            DropdownMenuItem(text = { Text(nativeText(language, "\u5220\u9664", "Delete")) }, leadingIcon = { Icon(HugeIcons.Delete01, null) }, onClick = { menuExpanded = false; onDelete() })
        }
    }
}

@Composable
private fun RikkaDrawer(
    currentThreadId: String,
    modelLabel: String,
    conversations: List<NativeConversation>,
    onSearch: () -> Unit,
    onRenameConversation: (NativeConversation) -> Unit,
    onDeleteConversation: (NativeConversation) -> Unit,
    onToggleFavorite: (NativeConversation) -> Unit,
    onResumeConversation: (String) -> Unit,
    onClose: () -> Unit,
    onNewConversation: () -> Unit,
    onBackHome: () -> Unit,
    onOpenLegacyWebUi: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    var selectedCategory by remember { mutableStateOf("all") }
    val conversationListState = rememberLazyListState()
    var conversationRevision = 1
    conversations.forEach { conversation ->
        conversationRevision = 31 * conversationRevision + conversation.threadId.hashCode()
        conversationRevision = 31 * conversationRevision + conversation.title.hashCode()
        conversationRevision = 31 * conversationRevision + conversation.projectPath.hashCode()
        conversationRevision = 31 * conversationRevision + conversation.state.hashCode()
        conversationRevision = 31 * conversationRevision + conversation.favorite.hashCode()
        conversationRevision = 31 * conversationRevision + conversation.attention.hashCode()
    }
    val conversationSnapshot = remember(conversationRevision) { conversations.toList() }
    val projectPaths = remember(conversationRevision) {
        conversationSnapshot.asSequence().map { it.projectPath }.filter { it.isNotBlank() }.distinct().toList()
    }
    val visibleConversations = remember(conversationRevision, selectedCategory) {
        conversationSnapshot.filter { conversation ->
            when (selectedCategory) {
                "all" -> true
                "favorite" -> conversation.favorite
                else -> conversation.projectPath == selectedCategory
            }
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Sparkles, null, modifier = Modifier.size(24.dp)) }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fcode 用户", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Icon(HugeIcons.PencilEdit01, "编辑昵称", modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                    Text("今天想聊点什么？", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerQuickAction(HugeIcons.Search01, "搜索", Modifier.weight(1f), onSearch)
                DrawerQuickAction(HugeIcons.TransactionHistory, "历史", Modifier.weight(1f))
            }

            val categories = remember(projectPaths) { listOf("all", "favorite") + projectPaths }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(categories, key = { it }, contentType = { "drawer-category" }) { category ->
                    val label = when (category) { "all" -> nativeText(language, "\u5168\u90e8", "All"); "favorite" -> nativeText(language, "\u6536\u85cf", "Favorites"); else -> category.trimEnd('/').substringAfterLast('/').ifBlank { nativeText(language, "\u65e0\u9879\u76ee", "No project") } }
                    Surface(onClick = { selectedCategory = category }, shape = RoundedCornerShape(50), color = if (category == selectedCategory) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (category == "favorite") HugeIcons.InLove else HugeIcons.Folder01, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            LazyColumn(
                state = conversationListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(
                    visibleConversations,
                    key = { it.threadId },
                    contentType = { "drawer-conversation" },
                ) { conversation ->
                    val isCurrent = conversation.threadId == currentThreadId
                    var actionsExpanded by remember(conversation.threadId) { mutableStateOf(false) }
                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                    Box {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onResumeConversation(conversation.threadId) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        actionsExpanded = true
                                    },
                                ),
                            shape = RoundedCornerShape(28.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f) else Color.Transparent,
                            contentColor = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            if (conversation.state == CodexTaskStore.RUNNING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                val tint = if (conversation.state == CodexTaskStore.FAILED) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                Icon(HugeIcons.Sparkles, conversation.state, modifier = Modifier.size(19.dp), tint = tint)
                            }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    conversation.title,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (conversation.attention.isNotBlank()) {
                                    Spacer(Modifier.width(7.dp))
                                    ConversationAttentionBadge(conversation.attention)
                                }
                                if (conversation.favorite) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(HugeIcons.InLove, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.76f))
                                }
                            }
                        }
                        DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (conversation.favorite) nativeText(language, "\u53d6\u6d88\u6536\u85cf", "Unfavorite") else nativeText(language, "\u6536\u85cf", "Favorite")) },
                                leadingIcon = { Icon(HugeIcons.InLove, null) },
                                onClick = { actionsExpanded = false; onToggleFavorite(conversation) },
                            )
                            DropdownMenuItem(
                                text = { Text(nativeText(language, "\u5220\u9664", "Delete")) },
                                leadingIcon = { Icon(HugeIcons.Delete01, null) },
                                onClick = { actionsExpanded = false; onDeleteConversation(conversation) },
                            )
                        }
                    }
                }
            }

            NavigationDrawerItem(
                label = { Text(modelLabel.ifBlank { nativeText(language, "\u9009\u62e9\u52a9\u624b", "Select assistant") }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                selected = false,
                onClick = onNewConversation,
                icon = { Icon(HugeIcons.LookTop, "助手") },
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(HugeIcons.LookTop, "助手") }
                IconButton(onClick = {}) { Icon(HugeIcons.Sparkles, "功能") }
                IconButton(onClick = {}) { Icon(HugeIcons.InLove, "收藏") }
                IconButton(onClick = {}) { Icon(HugeIcons.ChartColumn, "统计") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onBackHome) { Icon(HugeIcons.Settings03, "设置") }
            }
        }
    }
}
@Composable
private fun ConversationMenu(
    conversation: NativeConversation,
    onRename: (NativeConversation) -> Unit,
    onDelete: (NativeConversation) -> Unit,
    onToggleFavorite: (NativeConversation) -> Unit,
) {
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.MoreVertical, nativeText(language, "\u66f4\u591a", "More"), modifier = Modifier.size(18.dp)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(if (conversation.favorite) nativeText(language, "\u53d6\u6d88\u6536\u85cf", "Unfavorite") else nativeText(language, "\u6536\u85cf", "Favorite")) }, leadingIcon = { Icon(HugeIcons.InLove, null) }, onClick = { expanded = false; onToggleFavorite(conversation) })
            DropdownMenuItem(text = { Text(nativeText(language, "\u91cd\u547d\u540d", "Rename")) }, leadingIcon = { Icon(HugeIcons.PencilEdit01, null) }, onClick = { expanded = false; onRename(conversation) })
            DropdownMenuItem(text = { Text(nativeText(language, "\u5220\u9664", "Delete")) }, leadingIcon = { Icon(HugeIcons.Delete01, null) }, onClick = { expanded = false; onDelete(conversation) })
        }
    }
}

@Composable
private fun ConversationSearchDialog(conversations: List<NativeConversation>, onDismiss: () -> Unit, onSelect: (NativeConversation) -> Unit) {
    var query by remember { mutableStateOf("") }
    val language = LocalNativeLanguage.current
    val results = conversations.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u641c\u7d22\u5bf9\u8bdd", "Search conversations")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(HugeIcons.Search01, null) }, placeholder = { Text(nativeText(language, "\u641c\u7d22\u5bf9\u8bdd", "Search conversations")) })
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.threadId }) { conversation ->
                        NavigationDrawerItem(label = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, selected = false, onClick = { onSelect(conversation) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u5173\u95ed", "Close")) } },
    )
}

@Composable
private fun RenameConversationDialog(conversation: NativeConversation, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember(conversation.threadId) { mutableStateOf(conversation.title) }
    val language = LocalNativeLanguage.current
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(nativeText(language, "\u91cd\u547d\u540d\u5bf9\u8bdd", "Rename conversation")) },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
        confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onConfirm(title.trim()) }) { Text(nativeText(language, "\u4fdd\u5b58", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u53d6\u6d88", "Cancel")) } },
    )
}

@Composable
private fun DrawerQuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Surface(modifier = modifier.liquidPress(onClick = onClick), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DrawerMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, null) },
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
    )
}
