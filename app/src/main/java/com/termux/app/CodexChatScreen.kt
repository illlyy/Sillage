@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.termux.app

import android.util.Base64

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.DrawerValue
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.Code
import org.commonmark.node.BlockQuote
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
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

private val FcodeLightColors = lightColorScheme(
    primary = Color(0xFF73515A),
    onPrimary = Color(0xFFFDFBFB),
    primaryContainer = Color(0xFFE6CDD2),
    onPrimaryContainer = Color(0xFF553840),
    secondary = Color(0xFF67565A),
    onSecondary = Color(0xFFFDFBFB),
    secondaryContainer = Color(0xFFE6CDD2),
    onSecondaryContainer = Color(0xFF493C40),
    tertiary = Color(0xFF6E5A45),
    onTertiary = Color(0xFFFDFBFB),
    tertiaryContainer = Color(0xFFE6D6C1),
    onTertiaryContainer = Color(0xFF4F412F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFDFBFB),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF5F1F1),
    onBackground = Color(0xFF241F20),
    surface = Color(0xFFF5F1F1),
    onSurface = Color(0xFF241F20),
    surfaceVariant = Color(0xFFE5DCDE),
    onSurfaceVariant = Color(0xFF504749),
    outline = Color(0xFF776C6E),
    outlineVariant = Color(0xFFCFC4C6),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF393334),
    inverseOnSurface = Color(0xFFF2E9EA),
    inversePrimary = Color(0xFFD9AEB7),
    surfaceDim = Color(0xFFDDD6D7),
    surfaceBright = Color(0xFFF5F1F1),
    surfaceContainerLowest = Color(0xFFFDFBFB),
    surfaceContainerLow = Color(0xFFF1EBEC),
    surfaceContainer = Color(0xFFEDE7E8),
    surfaceContainerHigh = Color(0xFFE8E1E2),
    surfaceContainerHighest = Color(0xFFE2DADB),
)

private val FcodeDarkColors = darkColorScheme(
    primary = Color(0xFFE6B8C1), onPrimary = Color(0xFF442830),
    primaryContainer = Color(0xFF5D3E47), onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Color(0xFFD6C1C5), onSecondary = Color(0xFF392D30),
    secondaryContainer = Color(0xFF514347), onSecondaryContainer = Color(0xFFF3DDE1),
    background = Color(0xFF171314), onBackground = Color(0xFFECE0E2),
    surface = Color(0xFF171314), onSurface = Color(0xFFECE0E2),
    surfaceVariant = Color(0xFF51474A), onSurfaceVariant = Color(0xFFD4C2C5),
    outline = Color(0xFF9C8C8F), outlineVariant = Color(0xFF51474A),
    surfaceContainerLowest = Color(0xFF120F10), surfaceContainerLow = Color(0xFF201B1C),
    surfaceContainer = Color(0xFF241F20), surfaceContainerHigh = Color(0xFF2F292A),
    surfaceContainerHighest = Color(0xFF3A3335),
)
val LocalNativeLanguage = staticCompositionLocalOf { "zh" }
val LocalStreamAnimationsEnabled = staticCompositionLocalOf { true }
val LocalShowReasoning = staticCompositionLocalOf { true }
val LocalAutoFollowOutput = staticCompositionLocalOf { true }

internal fun nativeText(language: String, zh: String, en: String): String = if (language == "en") en else zh

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FcodeChatTheme(themeMode: String = "system", language: String = "zh", streamAnimations: Boolean = true, showReasoning: Boolean = true, autoFollow: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialExpressiveTheme(
        colorScheme = if (dark) FcodeDarkColors else FcodeLightColors,
        motionScheme = MotionScheme.expressive(),
        content = { androidx.compose.runtime.CompositionLocalProvider(LocalNativeLanguage provides language, LocalStreamAnimationsEnabled provides streamAnimations, LocalShowReasoning provides showReasoning, LocalAutoFollowOutput provides autoFollow, content = content) },
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
    onLoadSubagentHistory: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onEffortSelected: (String) -> Unit,
    onModeChange: (String) -> Unit,
    onSetGoal: (String) -> Unit,
    onClearGoal: () -> Unit,
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val listDragged by listState.interactionSource.collectIsDraggedAsState()
    var followOutput by remember { mutableStateOf(true) }
    var followPausedUntil by remember { mutableLongStateOf(0L) }
    var historyLimit by remember { mutableIntStateOf(40) }
    var inputHeightPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inputBottomPadding = with(density) { inputHeightPx.toDp() } + 8.dp
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottomPx > 0
    val floatingInsetModifier = if (imeVisible) Modifier.imePadding() else Modifier
    val showScrollToBottom by remember { derivedStateOf { state.messages.isNotEmpty() && listState.canScrollForward } }
    var showModelPicker by remember { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    var showConversationSearch by remember { mutableStateOf(false) }
    var showMessageSearch by remember { mutableStateOf(false) }
    var renameConversation by remember { mutableStateOf<NativeConversation?>(null) }
    var deleteConversation by remember { mutableStateOf<NativeConversation?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var editMessage by remember { mutableStateOf<NativeChatMessage?>(null) }
    var previewAttachment by remember { mutableStateOf<NativeAttachment?>(null) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showWorkPanel by remember { mutableStateOf(false) }
    var showSkillPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.busy, state.turnStartedAt) {
        while (state.busy) {
            elapsedSeconds = ((System.currentTimeMillis() - state.turnStartedAt).coerceAtLeast(0L) / 1000L)
            delay(1000L)
        }
        elapsedSeconds = 0L
    }

    LaunchedEffect(listDragged, autoFollowEnabled) {
        if (!autoFollowEnabled) { followOutput = false; return@LaunchedEffect }
        if (listDragged) {
            followOutput = false
        } else if (!listState.canScrollForward) {
            followOutput = true
        }
    }

    LaunchedEffect(state.conversationAnimationKey) {
        historyLimit = 40
        delay(16L)
        if (state.messages.isNotEmpty()) {
            val visibleCount = minOf(historyLimit, state.messages.size)
            val loaderOffset = if (state.messages.size > visibleCount) 1 else 0
            listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0), Int.MAX_VALUE)
        }
    }

    LaunchedEffect(inputHeightPx, imeBottomPx) {
        if (autoFollowEnabled && followOutput && !listDragged && state.messages.isNotEmpty()) {
            val visibleCount = minOf(historyLimit, state.messages.size)
            val loaderOffset = if (state.messages.size > visibleCount) 1 else 0
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
            if (!listState.canScrollForward || overflow < 0.5f) continue

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            RikkaDrawer(
                modelLabel = state.modelLabel,
                conversations = state.conversations,
                onSearch = { showConversationSearch = true },
                onRenameConversation = { renameConversation = it },
                onDeleteConversation = { deleteConversation = it },
                onToggleFavorite = onToggleFavorite,
                onResumeConversation = { threadId ->
                    onResumeConversation(threadId)
                    scope.launch { drawerState.close() }
                },
                onClose = { scope.launch { drawerState.close() } },
                onNewConversation = {
                    onNewConversation()
                    scope.launch { drawerState.close() }
                },
                onBackHome = onBackHome,
                onOpenLegacyWebUi = onOpenLegacyWebUi,
            )
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    RikkaTopBar(
                        title = state.conversationTitle,
                        modelLabel = state.modelLabel,
                        ready = state.ready,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenWorkPanel = { showWorkPanel = true },
                        onSearch = { showMessageSearch = true },
                        onExport = { shareConversation(context, state.conversationTitle, state.messages) },
                        canExport = state.messages.any { it.role == NativeChatRole.USER || it.role == NativeChatRole.ASSISTANT },
                        onNewConversation = onNewConversation,
                        onToggleTheme = onToggleTheme,
                    )
                },
                bottomBar = {},
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    AssistantBackdrop()
                    if (state.messages.isEmpty()) {
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
                        val liveAssistantId = if (state.phase == NativeTurnPhase.ANSWERING) visibleMessages.lastOrNull { it.role == NativeChatRole.ASSISTANT && it.streaming }?.id else null
                        val retryPrompts = remember(visibleMessages, state.conversationAnimationKey) {
                            buildMap<String, String> {
                                var lastUser: String? = null
                                visibleMessages.forEach { message ->
                                    if (message.role == NativeChatRole.USER) lastUser = message.content
                                    else if (message.role == NativeChatRole.ASSISTANT || message.role == NativeChatRole.ERROR) lastUser?.let { put(message.id, it) }
                                }
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().imePadding(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = inputBottomPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (hiddenMessageCount > 0) {
                                item(key = "history-loader", contentType = "history-loader") {
                                    TextButton(
                                        onClick = { historyLimit = (historyLimit + 40).coerceAtMost(state.messages.size) },
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
                                RikkaMessageItem(
                                    message = message,
                                    chatState = state,
                                    liveState = state.takeIf { message.id == liveAssistantId },
                                    elapsedSeconds = elapsedSeconds,
                                    onEdit = { editMessage = message },
                                    onRetry = previousUser?.let { prompt -> { onRetry(prompt) } },
                                    onLoadSubagentHistory = onLoadSubagentHistory,
                                    onQuote = { quoted ->
                                        val block = quoted.lineSequence().joinToString("\n") { "> $it" }
                                        onInputChange(listOf(state.input.trimEnd(), block, "").filter { it.isNotEmpty() }.joinToString("\n\n"))
                                    },
                                    onReasoningAutoCollapse = {
                                        followPausedUntil = android.os.SystemClock.uptimeMillis() + 560L
                                    },
                                    onPreviewAttachment = { previewAttachment = it },
                                )
                            }
                            if (state.phase in setOf(NativeTurnPhase.WAITING, NativeTurnPhase.REASONING, NativeTurnPhase.TOOL_RUNNING) && liveAssistantId == null) {
                                item("processing") { ProcessingPanel(state, elapsedSeconds, false, onLoadSubagentHistory, {}) }
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
                        activeGoal = state.activeGoalObjective,
                        onModeSelected = onModeChange,
                        onRequestGoal = { showGoalDialog = true },
                        onClearGoal = onClearGoal,
                        selectedSkills = state.selectedSkills,
                        onRemoveSkill = { state.selectedSkills.remove(it) },
                        attachments = state.attachments,
                        onMoreClick = { showFilesSheet = true },
                        onRetryLast = { state.messages.lastOrNull { it.role == NativeChatRole.USER }?.content?.let(onRetry) },
                        onEditLast = { editMessage = state.messages.lastOrNull { it.role == NativeChatRole.USER } },
                        onRemoveAttachment = onRemoveAttachment,
                        onPreviewAttachment = { previewAttachment = it },
                        onValueChange = onInputChange,
                        onSend = { if (state.phase.active) onStop() else onSend(state.input) },
                        onHeightChanged = { inputHeightPx = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
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
                    onModeChange("default")
                    onSend(nativeText(language, "\u8bf7\u6309\u7167\u5de5\u4f5c\u9762\u677f\u4e2d\u7684\u8ba1\u5212\u5f00\u59cb\u6267\u884c\u3002", "Execute the plan shown in the work panel."))
                    showWorkPanel = false
                }
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
            title = "选择模型",
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
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.86f, animationSpec = tween(250, easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1f))),
                exit = fadeOut(tween(145)) + scaleOut(targetScale = 0.90f, animationSpec = tween(190, easing = FastOutSlowInEasing)),
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
    val results = remember(query, messages.size) {
        if (query.isBlank()) emptyList()
        else messages.mapIndexedNotNull { index, message ->
            if (message.role != NativeChatRole.ACTIVITY && message.content.contains(query.trim(), ignoreCase = true)) index to message else null
        }
    }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索当前对话") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(HugeIcons.Search01, null) },
                    placeholder = { Text(nativeText(LocalNativeLanguage.current, "\u91cd\u8bd5", "Retry")) },
                )
                if (query.isNotBlank()) {
                    Text("找到 ${results.size} 条结果", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results, key = { it.first }) { (index, message) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(index) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(if (message.role == NativeChatRole.USER) "你" else "助手", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(message.content.replace('\n', ' '), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
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
private fun EmptyChatState(ready: Boolean, onPrompt: (String) -> Unit) {
    val prompts = remember {
        listOf(
            StarterPrompt(HugeIcons.Code, "理解项目", "分析当前工作区结构和核心逻辑", "请分析当前项目的目录结构，说明核心模块、启动流程和值得优先改进的地方。"),
            StarterPrompt(HugeIcons.Bug01, "检查问题", "查找潜在 bug、崩溃和异常边界", "请检查当前项目中可能的 bug、崩溃点和异常边界，优先修复高风险问题并运行验证。"),
            StarterPrompt(HugeIcons.MagicWand01, "改进体验", "优化 UI、性能和用户交互", "请审查当前项目的用户体验，直接实现一项最有价值的 UI 或交互优化，并完成测试。"),
            StarterPrompt(HugeIcons.Files02, "继续开发", "读取变更并建议下一步", "请检查当前 Git 变更和项目状态，总结已完成的工作，然后直接继续最合理的下一步。"),
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
        Text("今天想做什么？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (ready) "Codex 已准备好，可以与你一起处理当前项目" else "正在连接 Codex…",
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
    ready: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenWorkPanel: () -> Unit,
    onSearch: () -> Unit,
    onExport: () -> Unit,
    canExport: Boolean,
    onNewConversation: () -> Unit,
    onToggleTheme: () -> Unit,
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
                        (fadeIn(tween(150)) + scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 360f)) + slideInHorizontally { it / 8 })
                            .togetherWith(fadeOut(tween(110)) + scaleOut(targetScale = 1.06f, animationSpec = tween(120)) + slideOutHorizontally { -it / 10 })
                    },
                    label = "conversationTitleTransition",
                ) { value ->
                    Text(value, maxLines = 1, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = "默认助手 / ${modelLabel.ifBlank { "默认模型" }} (Fcode)",
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (ready) Color(0xFF43A047) else MaterialTheme.colorScheme.outline, CircleShape),
            )
            IconButton(onClick = onToggleTheme) {
                Icon(HugeIcons.Sparkles, contentDescription = nativeText(language, "\u5207\u6362\u4e3b\u9898", "Switch theme"))
            }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                        Color.Transparent,
                        Color.Transparent,
                    ),
                ),
            ),
    )
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
private fun RikkaMessageItem(message: NativeChatMessage, chatState: NativeChatState, liveState: NativeChatState?, elapsedSeconds: Long, onEdit: () -> Unit, onRetry: (() -> Unit)?, onLoadSubagentHistory: (String) -> Unit, onQuote: (String) -> Unit, onReasoningAutoCollapse: () -> Unit = {}, onPreviewAttachment: (NativeAttachment) -> Unit = {}) {
    when (message.role) {
        NativeChatRole.USER -> RikkaUserMessage(message.content, message.skills, message.attachments, onEdit, onPreviewAttachment)
        NativeChatRole.ASSISTANT -> RikkaAssistantMessage(message.id, message.content, message.streaming, message.revealStartedAt, message.finalOnlyReveal, liveState, elapsedSeconds, onRetry, onLoadSubagentHistory, onQuote, onReasoningAutoCollapse)
        NativeChatRole.ACTIVITY -> RikkaActivityMessage(message, chatState, onLoadSubagentHistory)
        NativeChatRole.ERROR -> RikkaErrorMessage(message.content, onRetry)
    }
}

@Composable
private fun RikkaUserMessage(text: String, skills: List<NativeSkill>, attachments: List<NativeAttachment>, onEdit: () -> Unit, onPreviewAttachment: (NativeAttachment) -> Unit) {
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
                    text = { Text("复制") },
                    leadingIcon = { Icon(HugeIcons.Copy01, null, modifier = Modifier.size(18.dp)) },
                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)); menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text("编辑并重新生成") },
                    leadingIcon = { Icon(HugeIcons.PencilEdit01, null, modifier = Modifier.size(18.dp)) },
                    onClick = { menuExpanded = false; onEdit() },
                )
            }
        }
        MessageActions(text = text, onEdit = onEdit)
    }
}


@Composable
private fun FinalOnlyAnswerReveal(text: String, revealStartedAt: Long) {
    // Do not promote an unbounded document to one blur/alpha GPU layer.
    if (!NativeUiRenderSafety.canAnimateDocument(text)) {
        RichResponseText(text)
        return
    }
    val shouldAnimate = revealStartedAt > 0L && System.currentTimeMillis() - revealStartedAt < 2_000L
    var revealed by remember(revealStartedAt) { mutableStateOf(!shouldAnimate) }
    val alpha by animateFloatAsState(if (revealed) 1f else 0f, tween(560, easing = LinearOutSlowInEasing), label = "finalAnswerAlpha")
    val blurRadius by animateDpAsState(if (revealed) 0.dp else 12.dp, tween(640, easing = FastOutSlowInEasing), label = "finalAnswerBlur")
    LaunchedEffect(revealStartedAt) { revealed = true }
    Box(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }.blur(blurRadius)) {
        RichResponseText(text)
    }
}

@Composable
private fun StreamingResponseText(messageId: String, text: String, streaming: Boolean, revealStartedAt: Long, finalOnlyReveal: Boolean) {
    if (finalOnlyReveal) {
        FinalOnlyAnswerReveal(text, revealStartedAt)
        return
    }
    var previousLength by remember(messageId) { mutableIntStateOf(text.length) }
    var showRichText by remember(messageId) { mutableStateOf(!streaming) }
    val tailStart = previousLength.coerceAtMost(text.length)
    androidx.compose.runtime.SideEffect { previousLength = text.length }

    LaunchedEffect(streaming) {
        if (streaming) {
            showRichText = false
        } else {
            // Give the final coalesced text snapshot a layout pass before replacing the
            // live chunk renderer with Markwon's final Markdown view.
            delay(220L)
            showRichText = true
        }
    }

    // AnimatedContent temporarily promotes the complete document to a hardware layer.
    // Keep the unbounded answer layer-free; the live tail already owns reveal motion.
    if (showRichText) {
        RichResponseText(text)
    } else {
        ChunkedLiveText(
            text = text,
            tailStart = tailStart,
            generation = text.length,
            reasoning = false,
            animateTail = true,
        )
    }
}

@Composable
private fun RikkaAssistantMessage(messageId: String, text: String, streaming: Boolean, revealStartedAt: Long, finalOnlyReveal: Boolean, liveState: NativeChatState?, elapsedSeconds: Long, onRetry: (() -> Unit)?, onLoadSubagentHistory: (String) -> Unit, onQuote: (String) -> Unit, onReasoningAutoCollapse: () -> Unit = {}) {
    val language = LocalNativeLanguage.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).combinedClickable(onClick = {}, onLongClick = { menuExpanded = true }),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (liveState != null) {
                    ProcessingPanel(liveState, elapsedSeconds, text.isNotBlank(), onLoadSubagentHistory, onReasoningAutoCollapse)
                    Spacer(Modifier.height(6.dp))
                }
                Text("\u9ed8\u8ba4\u52a9\u624b", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                StreamingResponseText(messageId, text, streaming, revealStartedAt, finalOnlyReveal)
                AnimatedVisibility(
                    visible = streaming,
                    enter = fadeIn(tween(140)) + expandVertically(tween(160, easing = LinearOutSlowInEasing), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(150, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top),
                ) {
                    Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                        Text("\u6b63\u5728\u751f\u6210", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            AnimatedVisibility(
                visible = !streaming && text.isNotBlank(),
                enter = fadeIn(tween(150, delayMillis = 60)) + expandVertically(tween(180, easing = LinearOutSlowInEasing), expandFrom = Alignment.Top),
                exit = fadeOut(tween(90)) + shrinkVertically(tween(120), shrinkTowards = Alignment.Top),
            ) {
                MessageActions(text = text, onRetry = onRetry, allowShare = true)
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("复制回答") },
                leadingIcon = { Icon(HugeIcons.Copy01, null, modifier = Modifier.size(18.dp)) },
                onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)); menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text("引用回答") },
                leadingIcon = { Icon(HugeIcons.LeftToRightListBullet, null, modifier = Modifier.size(18.dp)) },
                onClick = { menuExpanded = false; onQuote(text) },
            )
            if (onRetry != null) DropdownMenuItem(
                text = { Text("重新生成") },
                leadingIcon = { Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(18.dp)) },
                onClick = { menuExpanded = false; onRetry() },
            )
            DropdownMenuItem(
                text = { Text("分享回答") },
                leadingIcon = { Icon(HugeIcons.Share08, null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    menuExpanded = false
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
                    context.startActivity(android.content.Intent.createChooser(intent, "分享回答"))
                },
            )
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
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f), clip = true),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = spring(dampingRatio = 0.82f, stiffness = 440f), clip = true),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(75, easing = LinearEasing)) + slideInVertically(initialOffsetY = { -it / 8 }, animationSpec = tween(130, easing = LinearEasing)),
            exit = fadeOut(tween(65, easing = LinearEasing)) + slideOutVertically(targetOffsetY = { -it / 10 }, animationSpec = tween(105, easing = LinearEasing)),
        ) { content() }
    }
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
    if (!LocalStreamAnimationsEnabled.current) {
        StableLiveTextChunk(text, reasoning)
        return
    }
    androidx.compose.runtime.key(generation) {
        var visible by remember { mutableStateOf(false) }
        val tailAlpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0.30f,
            animationSpec = tween(260, easing = LinearEasing),
            label = if (reasoning) "reasoningTail" else "answerTail",
        )
        LaunchedEffect(Unit) { visible = true }
        val safeTailStart = tailStart.coerceIn(0, text.length)
        val baseColor = if (reasoning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        val animatedText = remember(text, safeTailStart, tailAlpha, baseColor) {
            androidx.compose.ui.text.buildAnnotatedString {
                if (safeTailStart > 0) append(text.substring(0, safeTailStart))
                withStyle(androidx.compose.ui.text.SpanStyle(color = baseColor.copy(alpha = tailAlpha))) {
                    append(text.substring(safeTailStart))
                }
            }
        }
        Text(
            text = animatedText,
            modifier = Modifier.fillMaxWidth(),
            style = if (reasoning) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            lineHeight = if (reasoning) 22.sp else 24.sp,
            letterSpacing = if (reasoning) 0.sp else 0.1.sp,
            color = baseColor,
        )
    }
}

@Composable
private fun ChunkedLiveText(text: String, tailStart: Int, generation: Int, reasoning: Boolean, animateTail: Boolean) {
    val chunks = remember(text) { splitLiveText(text) }
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
    var previousLength by remember { mutableIntStateOf(text.length) }
    val tailStart = previousLength.coerceAtMost(text.length)
    androidx.compose.runtime.SideEffect { previousLength = text.length }
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
private fun ProcessingPanel(state: NativeChatState, elapsedSeconds: Long, answerStarted: Boolean, onLoadSubagentHistory: (String) -> Unit, onAutomaticCollapse: () -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    var fullReasoning by remember { mutableStateOf(false) }
    val language = LocalNativeLanguage.current
    val showReasoning = LocalShowReasoning.current
    val reasoningScrollState = rememberScrollState()
    val reasoningPreview = expanded && !state.reasoningComplete && !fullReasoning
    val reasoningLive = expanded && !state.reasoningComplete

    // Match RikkaHub's important performance behavior: while reasoning is live, keep it
    // inside a bounded inner viewport. Only that viewport scrolls as text grows, so the
    // outer LazyColumn is not remeasured and displaced by thousands of reasoning lines.
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
            } else {
                delay(32L)
            }
        }
    }
    LaunchedEffect(state.reasoningComplete, answerStarted) {
        if (state.reasoningComplete && answerStarted) {
            delay(260L)
            onAutomaticCollapse()
            expanded = false
            fullReasoning = false
        }
    }
    val reasoningSeconds = if (state.reasoningCompletedAt > state.turnStartedAt) {
        (state.reasoningCompletedAt - state.turnStartedAt).coerceAtLeast(0L) / 1000L
    } else elapsedSeconds
    val statusText = when {
        state.phase == NativeTurnPhase.FAILED -> nativeText(language, "\u751f\u6210\u5931\u8d25", "Generation failed")
        state.phase == NativeTurnPhase.COMPLETED || state.reasoningComplete -> nativeText(language, "\u601d\u8003\u4e86 ${reasoningSeconds}s", "Thought for ${reasoningSeconds}s")
        state.phase == NativeTurnPhase.WAITING && elapsedSeconds >= 12L -> nativeText(language, "\u7b49\u5f85\u6a21\u578b\u54cd\u5e94 ${elapsedSeconds}s", "Waiting for model ${elapsedSeconds}s")
        state.phase == NativeTurnPhase.REASONING -> nativeText(language, "\u6b63\u5728\u601d\u8003 ${elapsedSeconds}s", "Thinking ${elapsedSeconds}s")
        state.phase == NativeTurnPhase.TOOL_RUNNING -> nativeText(language, "\u6b63\u5728\u8c03\u7528\u5de5\u5177 ${elapsedSeconds}s", "Running tools ${elapsedSeconds}s")
        state.phase == NativeTurnPhase.ANSWERING -> nativeText(language, "\u6b63\u5728\u751f\u6210 ${elapsedSeconds}s", "Generating ${elapsedSeconds}s")
        else -> nativeText(language, "\u5904\u7406\u4e2d ${elapsedSeconds}s", "Processing ${elapsedSeconds}s")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                when {
                    !expanded -> expanded = true
                    reasoningPreview -> fullReasoning = true
                    else -> {
                        expanded = false
                        fullReasoning = false
                    }
                }
            }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.reasoningComplete) {
                Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.width(8.dp))
            Text(statusText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reasoningPreview && state.reasoningText.length > 520) {
                Spacer(Modifier.weight(1f))
                Text(nativeText(language, "\u5c55\u5f00", "Expand"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        QElasticExpand(expanded) {
            SafeExpandableViewport(maxHeight = 520.dp) {
                Column {
                if (showReasoning && state.reasoningText.isNotBlank()) {
                    val liveReasoningModifier = if (reasoningLive) {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 132.dp)
                            .verticalScroll(reasoningScrollState)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    Box(modifier = Modifier.padding(top = 10.dp).then(liveReasoningModifier)) {
                        if (state.reasoningComplete) {
                            RichResponseText(state.reasoningText)
                        } else {
                            LiveReasoningText(state.reasoningText)
                        }
                    }
                }
                if (state.commandText.isNotBlank()) Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) { SelectionContainer { Text(state.commandText, modifier = Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } }
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
                                    history = state.subagentHistories[thread],
                                    historyLoading = thread in state.loadingSubagentHistories,
                                    onLoadHistory = onLoadSubagentHistory,
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
private fun RichResponseText(text: String) {
    val blocks = remember(text) { markdownBlocks(text) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            if (block.code) {
                RikkaCodeBlock(block.language, block.text)
            } else if (block.text.isNotBlank()) {
                val chunks = remember(block.text) { NativeUiRenderSafety.splitMarkdown(block.text) }
                chunks.forEach { chunk -> RichMarkdownText(chunk) }
            }
        }
    }
}

@Composable
private fun RikkaCodeBlock(language: String, code: String) {
    val languageUi = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    val lines = remember(code) { code.lines() }
    val collapsible = lines.size > 18 || code.length > 1800
    var expanded by remember(code) { mutableStateOf(!collapsible) }
    val visibleCode = remember(code, expanded) {
        if (expanded || !collapsible) code else lines.take(16).joinToString("\n")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = if (collapsible) 6.dp else 14.dp),
            ) {
                SelectionContainer {
                    Text(visibleCode, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
                }
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

private fun normalizeMarkdownLists(source: String): String {
    // Models occasionally place the first list marker directly after a lead-in sentence.
    // CommonMark then treats the whole response as a paragraph, so introduce only the
    // structurally unambiguous break while preserving normal prose and version numbers.
    return source
        .replace(Regex("""([\uFF1A:;\uFF1B])(?:[ \t]+)(?=(?:[-+*]|\d{1,3}[.)])\s+)"""), "$1\n")
        .replace(Regex("""(?m)^(\s*)([\u2022\u00B7])\s+"""), "$1- ")
}

private object NativeMarkdownRenderer {
    @Volatile private var renderer: Markwon? = null
    private val renderedCache = object : LinkedHashMap<String, android.text.Spanned>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.text.Spanned>?): Boolean = size > 48
    }

    fun get(context: android.content.Context): Markwon = renderer ?: synchronized(this) {
        renderer ?: Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    val density = context.resources.displayMetrics.density
                    builder
                        .blockMargin((20f * density).roundToInt())
                        .listItemColor(android.graphics.Color.rgb(184, 112, 130))
                        .bulletWidth((8f * density).roundToInt())
                        .bulletListItemStrokeWidth((2f * density).roundToInt())
                        .blockQuoteColor(android.graphics.Color.rgb(207, 151, 164))
                        .blockQuoteWidth((4f * density).roundToInt())
                        .linkColor(android.graphics.Color.rgb(157, 77, 99))
                        .isLinkUnderlined(false)
                        .codeBackgroundColor(android.graphics.Color.TRANSPARENT)
                        .codeTextColor(android.graphics.Color.rgb(117, 62, 78))
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    val density = context.resources.displayMetrics.density
                    builder.setFactory(Code::class.java) { _, _ ->
                        RoundedInlineCodeSpan(
                            horizontalPadding = 5f * density,
                            verticalPadding = 2f * density,
                            radius = 8f * density,
                            backgroundColor = android.graphics.Color.rgb(244, 230, 234),
                            textColor = android.graphics.Color.rgb(112, 57, 73),
                        )
                    }
                    builder.setFactory(BlockQuote::class.java) { _, _ ->
                        RoundedBlockQuoteSpan(
                            margin = 16f * density,
                            barWidth = 4f * density,
                            radius = 2f * density,
                            color = android.graphics.Color.rgb(207, 151, 164),
                        )
                    }
                }
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(42f) { builder -> builder.inlinesEnabled(true) })
            .build()
            .also { renderer = it }
    }

    fun cached(text: String): android.text.Spanned? = synchronized(renderedCache) { renderedCache[text] }

    fun render(markwon: Markwon, text: String): android.text.Spanned {
        cached(text)?.let { return it }
        val rendered = markwon.toMarkdown(normalizeMarkdownLists(text))
        synchronized(renderedCache) { renderedCache[text] = rendered }
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

@Composable
private fun RichMarkdownText(text: String) {
    val context = LocalContext.current
    val markwon = remember(context.applicationContext) { NativeMarkdownRenderer.get(context.applicationContext) }
    val parsed by produceState<android.text.Spanned?>(
        initialValue = NativeMarkdownRenderer.cached(text),
        key1 = text,
        key2 = markwon,
    ) {
        if (value == null) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                // Historical tool/reasoning output is not trusted Markdown. Markwon's
                // inline parser can throw on malformed backtick sequences; fall back to
                // the already-visible plain text instead of terminating the app.
                runCatching { NativeMarkdownRenderer.render(markwon, text) }.getOrNull()
            }
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { android.widget.TextView(it).apply {
            setTextIsSelectable(true)
            textSize = 16f
            includeFontPadding = false
            letterSpacing = 0.01f
            setLineSpacing(resources.displayMetrics.density * 4f, 1f)
            setPadding(0, (2f * resources.displayMetrics.density).roundToInt(), 0, (2f * resources.displayMetrics.density).roundToInt())
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            linksClickable = true
            setTextColor(android.graphics.Color.rgb(45, 40, 42))
            // Never expose an empty AndroidView while Markdown/formulas are parsed.
            this.text = text
            tag = "plain:$text"
        } },
        update = { view ->
            val rendered = parsed
            if (rendered != null && view.tag != text) {
                val applied = runCatching { markwon.setParsedMarkdown(view, rendered) }.isSuccess
                if (applied) view.tag = text else {
                    view.text = text
                    view.tag = "plain:$text"
                }
            } else if (rendered == null && view.tag != "plain:$text") {
                view.text = text
                view.tag = "plain:$text"
            }
        },
    )
}

@Composable
private fun MarkdownLikeText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        markdownBlocks(text).forEach { block ->
            if (block.code) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFF1F1B24)) {
                    Text(
                        block.text,
                        modifier = Modifier.padding(14.dp),
                        color = Color(0xFFF4EFF7),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            } else {
                Text(block.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun RikkaActivityMessage(message: NativeChatMessage, state: NativeChatState, onLoadSubagentHistory: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    val text = message.content
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
    val duration = payload?.optLong("duration")?.toString() ?: text.substringAfter("PROCESS|", "0").substringBefore('|')
    val reasoning = payload?.optString("reasoning").orEmpty()
    val command = payload?.optString("command").orEmpty()
    val reasoningUnavailable = payload?.optBoolean("reasoningUnavailable", false) == true
    val tools = payload?.optJSONArray("tools")
    // The live panel owns the expanded reasoning view. When completion inserts this
    // durable activity row, enter collapsed so the panel is not opened a second time.
    var expanded by remember(message.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(HugeIcons.Zap, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("思考了 ${duration}s", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        QElasticExpand(expanded) {
            SafeExpandableViewport(maxHeight = 520.dp) {
                Column {
        if (reasoning.isNotBlank()) Box(modifier = Modifier.padding(top = 10.dp)) { RichResponseText(reasoning) }
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
            val detail = when (type) {
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
                    history = state.subagentHistories[thread],
                    historyLoading = thread in state.loadingSubagentHistories,
                    onLoadHistory = onLoadSubagentHistory,
                )
            }
            else ToolTextCard(title, detail, type == "fileChange")
        }
                }
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
    history: String?,
    historyLoading: Boolean,
    onLoadHistory: (String) -> Unit,
) {
    val initialId = subagentThreadId(item).ifBlank { item.optString("id", item.toString().hashCode().toString()) }
    val agents = remember(state.revision, state.messages.size, state.liveSubagents.size, item.toString()) {
        collectSubagentItems(state, item)
    }
    val name = subagentName(item)
    val status = subagentStatusLabel(resolvedSubagentStatus(state, item), LocalNativeLanguage.current)
    var panelVisible by remember { mutableStateOf(false) }
    var panelEntered by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(initialId) }
    val closePanel: () -> Unit = { panelEntered = false }
    LaunchedEffect(panelVisible, panelEntered) {
        if (panelVisible && !panelEntered) {
            // This effect is cancelled automatically if the panel re-enters before the
            // exit motion finishes, so an old delayed close cannot hide a new panel.
            delay(210L)
            panelVisible = false
        }
    }
    Surface(
        modifier = Modifier.padding(top = 8.dp).clickable {
            selectedId = initialId
            panelEntered = false
            panelVisible = true
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
    if (panelVisible) {
        LaunchedEffect(Unit) { delay(20L); panelEntered = true }
        Dialog(onDismissRequest = closePanel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val scrimAlpha by animateFloatAsState(if (panelEntered) 0.16f else 0f, tween(170, easing = LinearEasing), label = "agentScrim")
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = scrimAlpha)).clickable(onClick = closePanel))
                AnimatedVisibility(
                    visible = panelEntered,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(150)),
                    exit = slideOutHorizontally(tween(190, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(130)),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(0.91f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 14.dp,
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (selectedId != null) {
                                    IconButton(onClick = { selectedId = null }) { Icon(HugeIcons.ArrowRight01, "\u8fd4\u56de\u5b50\u4ee3\u7406\u5217\u8868", Modifier.graphicsLayer { rotationZ = 180f }) }
                                } else Spacer(Modifier.width(48.dp))
                                Text(
                                    if (selectedId == null) "\u5b50\u4ee3\u7406" else subagentName(agents.firstOrNull { subagentKey(it) == selectedId } ?: item),
                                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                )
                                IconButton(onClick = closePanel) { Icon(HugeIcons.Cancel01, "\u5173\u95ed") }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                                val selected = selectedId
                                if (selected == null) {
                                    SubagentOverview(state, agents) { chosen ->
                                        val thread = subagentThreadId(chosen)
                                        selectedId = subagentKey(chosen)
                                        if (thread.isNotBlank()) onLoadHistory(thread)
                                    }
                                } else {
                                    val chosen = agents.firstOrNull { subagentKey(it) == selected } ?: item
                                    val thread = subagentThreadId(chosen)
                                    SubagentDetail(
                                        item = chosen,
                                        history = state.subagentHistories[thread] ?: if (thread == subagentThreadId(item)) history else null,
                                        historyLoading = thread in state.loadingSubagentHistories || (thread == subagentThreadId(item) && historyLoading),
                                        historyError = state.subagentHistoryErrors[thread],
                                        status = resolvedSubagentStatus(state, chosen),
                                        onRetryHistory = { if (thread.isNotBlank()) onLoadHistory(thread) },
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
    onDismiss: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf("plan") }
    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    val agents = remember(state.revision, state.messages.size, state.liveSubagents.size) { collectAllSubagentItems(state) }
    val agentThreads = remember(agents) { agents.map(::subagentThreadId).filter { it.isNotBlank() } }
    val close: () -> Unit = { entered = false }
    LaunchedEffect(tab, agentThreads) {
        if (tab == "agents") agentThreads.forEach(onLoadSubagentHistory)
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
                            WorkPanelTabs(tab = tab, planCount = parsePlanItems(state.planJson).size, agentCount = agents.size, onTab = { tab = it })
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    (fadeIn(tween(160)) + slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { if (targetState == "agents") it / 5 else -it / 5 }) togetherWith
                                        (fadeOut(tween(100)) + slideOutHorizontally(tween(170, easing = FastOutSlowInEasing)) { if (targetState == "agents") -it / 5 else it / 5 })
                                },
                                label = "workPanelTab",
                            ) { selectedTab ->
                                if (selectedTab == "agents") {
                                    if (agents.isEmpty()) WorkPanelEmpty("\u6682\u65e0\u5b50\u4ee3\u7406", "\u5f53 Codex \u59d4\u6d3e\u4efb\u52a1\u540e\uff0c\u5b50\u4ee3\u7406\u4f1a\u663e\u793a\u5728\u8fd9\u91cc\u3002")
                                    else SubagentOverview(state, agents) { agent ->
                                        val thread = subagentThreadId(agent)
                                        selectedAgentId = subagentKey(agent)
                                        if (thread.isNotBlank()) onLoadSubagentHistory(thread)
                                    }
                                } else WorkPlanView(state.planJson, state.planExplanation, state.activeGoalObjective, state.ready && !state.phase.active, onEditGoal, onClearGoal, onExecutePlan)
                            }
                        } else {
                            val agent = agents.firstOrNull { subagentKey(it) == selectedAgentId }
                            if (agent != null) {
                                val thread = subagentThreadId(agent)
                                SubagentDetail(
                                    item = agent,
                                    history = state.subagentHistories[thread],
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

@Composable
private fun WorkPanelTabs(tab: String, planCount: Int, agentCount: Int, onTab: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("plan" to "${nativeText(language, "\u8ba1\u5212", "Plan")} $planCount", "agents" to "${nativeText(language, "\u5b50\u4ee3\u7406", "Agents")} $agentCount").forEach { (id, label) ->
            Surface(
                modifier = Modifier.weight(1f).clickable { onTab(id) },
                shape = CircleShape,
                color = if (tab == id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (tab == id) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) { Text(label, Modifier.padding(vertical = 9.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, fontWeight = if (tab == id) FontWeight.SemiBold else FontWeight.Normal) }
        }
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
        if (explanation.isNotBlank()) item(key = "explanation") { Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 4.dp)) }
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
                        Text(item.optString("step", item.optString("text", item.toString())), style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
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

private fun parseSubagentMessages(history: String?): List<JSONObject> {
    if (history.isNullOrBlank()) return emptyList()
    return runCatching {
        val source = JSONArray(history)
        buildList {
            for (index in 0 until source.length()) source.optJSONObject(index)?.let(::add)
        }
    }.getOrDefault(emptyList())
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
    val messages = remember(history) { parseSubagentMessages(history) }
    val displayMessages = remember(messages, task) {
        if (task.isBlank()) messages else messages.filterNot {
            it.optString("role") == "user" && it.optString("content").trim() == task.trim()
        }
    }
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
                item(key = "agent-output-title") {
                    Text(
                        "\u6267\u884c\u8bb0\u5f55 \u00b7 ${displayMessages.size}",
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
                if (historyLoading) item(key = "refreshing") { SubagentHistoryLoading("\u6b63\u5728\u540c\u6b65\u5b50\u4ee3\u7406\u6700\u65b0\u8f93\u51fa") }
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
            } else if (historyLoading) {
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
                        if (reasoning.isNotBlank()) RichResponseText(reasoning)
                        if (command.isNotBlank()) ToolTextCard("\u547d\u4ee4\u6267\u884c", command, false)
                        if (tools != null) for (index in 0 until tools.length()) {
                            val tool = runCatching { JSONObject(tools.optString(index)) }.getOrNull() ?: continue
                            when (tool.optString("type")) {
                                "commandExecution" -> CommandExecutionCard(tool)
                                "collabAgentToolCall", "subAgentActivity" -> Unit
                                else -> ToolTextCard(
                                    tool.optString("type", "\u5de5\u5177\u8c03\u7528"),
                                    tool.optString("aggregatedOutput", tool.optString("output", tool.optString("detail", tool.toString(2)))),
                                    tool.optString("type") == "fileChange",
                                )
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
private fun CommandExecutionCard(item: JSONObject) {
    val command = item.optString("command", "")
    val stdout = item.optString("stdout", "")
    val stderr = item.optString("stderr", "")
    val aggregated = item.optString("aggregatedOutput", item.optString("output", ""))
    val output = if (stdout.isNotBlank() || stderr.isNotBlank()) stdout else aggregated
    val cwd = item.optString("cwd", "")
    val exitCode = item.opt("exitCode")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
    val durationMs = item.optLong("durationMs", item.optLong("duration_ms", 0L))
    val failed = item.optString("status").equals("failed", true) || (exitCode.toIntOrNull()?.let { it != 0 } == true)
    val normalized = buildString {
        append("$ ").append(command).append('\n')
        if (output.isNotBlank()) append(output.trimEnd()).append('\n')
        if (exitCode.isNotBlank()) append("exit ").append(exitCode)
    }
    if (commandViewType(normalized) != CommandViewType.TERMINAL && stderr.isBlank()) {
        SmartCommandCard(normalized)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val accent = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Sparkles, null, modifier = Modifier.size(17.dp), tint = accent)
                Spacer(Modifier.width(8.dp)); Column(modifier = Modifier.weight(1f)) {
                    Text(command.ifBlank { "命令执行" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                    if (cwd.isNotBlank()) Text(cwd, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val meta = buildList { if (durationMs > 0) add("%.1fs".format(durationMs / 1000.0)); if (exitCode.isNotBlank()) add("exit $exitCode") }.joinToString(" · ")
                Text(meta.ifBlank { if (failed) "失败" else "完成" }, style = MaterialTheme.typography.labelSmall, color = accent)
            }
            QElasticExpand(expanded) {
                SafeExpandableViewport(maxHeight = 420.dp) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        if (stdout.isNotBlank() || (stdout.isBlank() && aggregated.isNotBlank())) CommandStreamSection("\u8f93\u51fa", if (stdout.isNotBlank()) stdout else aggregated, false)
                        if (stderr.isNotBlank()) CommandStreamSection("\u9519\u8bef\u8f93\u51fa", stderr, true)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandStreamSection(title: String, value: String, error: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().background(if (error) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else Color.Transparent).padding(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NativeUiRenderSafety.splitPlainText(value.trimEnd()).forEach { chunk ->
                    Text(chunk, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
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
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) lines else lines.take(12)
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Folder01, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text("目录内容", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("${lines.size} 项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (lines.size > 12) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) "收起" else "显示剩余 ${lines.size - 12} 项") }
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
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Search01, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text("搜索结果", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("${results.size} 条 · ${grouped.size} 个文件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ToolTextCard(title: String, detail: String, diff: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val displayDetail = remember(detail) { NativeUiRenderSafety.sanitizeToolDetail(detail) }
    val failed = detail.contains("failed", true) || detail.contains("error", true) || Regex("exit [1-9]").containsMatchIn(detail)
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
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            diff.lineSequence().forEach { line ->
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
        }
    }
}

@Composable
private fun RikkaErrorMessage(text: String, onRetry: (() -> Unit)?) {
    val displayText = remember(text) { NativeUiRenderSafety.errorSummary(text) }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("发生错误", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp)); SelectionContainer { Text(displayText, style = MaterialTheme.typography.bodySmall) }
            if (onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("重试")
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
                Text(nativeText(language, "Codex ??????????????????", "Codex will keep tracking this goal in later turns."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    activeGoal: String,
    onModeSelected: (String) -> Unit,
    onRequestGoal: () -> Unit,
    onClearGoal: () -> Unit,
    selectedSkills: List<NativeSkill>,
    onRemoveSkill: (NativeSkill) -> Unit,
    attachments: List<NativeAttachment>,
    onMoreClick: () -> Unit,
    onRetryLast: () -> Unit,
    onEditLast: () -> Unit,
    onRemoveAttachment: (NativeAttachment) -> Unit,
    onPreviewAttachment: (NativeAttachment) -> Unit,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalNativeLanguage.current
    var toolsExpanded by remember { mutableStateOf(false) }
    val slashCommandMode = value.trimStart().startsWith("/")
    LaunchedEffect(slashCommandMode) {
        if (slashCommandMode) toolsExpanded = true
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
                        "__retry__" -> onRetryLast()
                        "__edit__" -> onEditLast()
                        else -> onValueChange(command)
                    }
                    toolsExpanded = false
                },
            )
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val bottomCorner by animateDpAsState(if (imeVisible) 0.dp else 28.dp, tween(210, easing = FastOutSlowInEasing), label = "inputBottomCorner")
    val sidePadding by animateDpAsState(if (imeVisible) 0.dp else 8.dp, tween(210, easing = FastOutSlowInEasing), label = "inputSidePadding")
    val bottomPadding by animateDpAsState(if (imeVisible) 0.dp else 8.dp, tween(210, easing = FastOutSlowInEasing), label = "inputBottomPadding")
    val keyboardOverlap by animateDpAsState(if (imeVisible) 3.dp else 0.dp, tween(180, easing = FastOutSlowInEasing), label = "inputKeyboardOverlap")
    val inputBorderColor by animateColorAsState(
        if (imeVisible) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        tween(160),
        label = "inputBorderColor",
    )
    val inputShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = bottomCorner, bottomStart = bottomCorner)
    val insetModifier = if (imeVisible) Modifier.imePadding() else Modifier
    Surface(modifier = modifier, color = Color.Transparent) {
        Column(
            modifier = insetModifier.padding(start = sidePadding, end = sidePadding, top = 8.dp, bottom = bottomPadding).offset(y = keyboardOverlap),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().onSizeChanged { onHeightChanged(it.height) }.animateContentSize(
                    animationSpec = spring(dampingRatio = 0.90f, stiffness = 520f),
                ),
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
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.Enter && enabled && value.isNotBlank()) {
                                onSend()
                                true
                            } else false
                        },
                        enabled = !loading,
                        minLines = 1,
                        maxLines = 5,
                        shape = MaterialTheme.shapes.largeIncreased,
                        placeholder = { Text("输入消息") },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                        ),
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
                            InputTool(HugeIcons.Add01, "\u5de5\u5177", { toolsExpanded = true })
                        }
                        Surface(
                            modifier = Modifier.size(42.dp).clickable(enabled = loading || (enabled && value.isNotBlank())) { onSend() },
                            shape = CircleShape,
                            color = when {
                                loading -> MaterialTheme.colorScheme.errorContainer
                                !enabled || value.isBlank() -> MaterialTheme.colorScheme.surfaceContainerHigh
                                else -> MaterialTheme.colorScheme.primary
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (loading) HugeIcons.Cancel01 else HugeIcons.ArrowUp02,
                                    contentDescription = if (loading) "停止" else "发送",
                                    modifier = Modifier.size(21.dp),
                                    tint = when {
                                        loading -> MaterialTheme.colorScheme.onErrorContainer
                                        !enabled || value.isBlank() -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        else -> MaterialTheme.colorScheme.onPrimary
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeComposerToolSheet(onDismiss: () -> Unit, onCommand: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    val tools = listOf(
        Triple(HugeIcons.Sparkles, "\u538b\u7f29\u4e0a\u4e0b\u6587", "/compact"),
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
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(attachment.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (attachment.image) AttachmentThumbnail(attachment.path, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp).clip(MaterialTheme.shapes.large))
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (attachment.image) "图片附件" else "文件附件", style = MaterialTheme.typography.labelLarge)
                        Text(formatFileSize(java.io.File(attachment.path).length()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(attachment.path, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
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
                    Text("\u6ca1\u6709\u627e\u5230\u53ef\u7528 Skill", modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    if (isSelected) Text("\u5df2\u5f15\u7528", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("\u5173\u95ed") } },
    )
}

@Composable
private fun RikkaFilesPicker(onPickImage: () -> Unit, onPickFile: () -> Unit, onPickSkill: () -> Unit) {
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

private fun effortLabel(value: String): String = when (value.lowercase()) {
    "none" -> "关闭"
    "minimal" -> "极低"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "极高"
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
                                            Text(effortLabel(effort), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                Text(effortLabel(effort), modifier = Modifier.graphicsLayer { scaleX = tickScale; scaleY = tickScale }, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun RikkaDrawer(
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
    val newestRunningThreadId = conversations.firstOrNull { it.state == CodexTaskStore.RUNNING }?.threadId
    LaunchedEffect(newestRunningThreadId) {
        if (newestRunningThreadId != null) conversationListState.animateScrollToItem(0)
    }
    val projectPaths = conversations.map { it.projectPath }.filter { it.isNotBlank() }.distinct()
    val visibleConversations = conversations.filter { conversation ->
        when (selectedCategory) {
            "all" -> true
            "favorite" -> conversation.favorite
            else -> conversation.projectPath == selectedCategory
        }
    }
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
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

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(listOf("all", "favorite") + projectPaths) { category ->
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
                items(visibleConversations, key = { it.threadId }) { conversation ->
                    NavigationDrawerItem(
                        label = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = false,
                        onClick = { onResumeConversation(conversation.threadId) },
                        icon = {
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
                        },
                        badge = { ConversationMenu(conversation, onRenameConversation, onDeleteConversation, onToggleFavorite) },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    )
                }
            }

            NavigationDrawerItem(
                label = { Text(modelLabel.ifBlank { "选择助手" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.MoreVertical, "更多", modifier = Modifier.size(18.dp)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(if (conversation.favorite) "取消收藏" else "收藏") }, leadingIcon = { Icon(HugeIcons.InLove, null) }, onClick = { expanded = false; onToggleFavorite(conversation) })
            DropdownMenuItem(text = { Text("重命名") }, leadingIcon = { Icon(HugeIcons.PencilEdit01, null) }, onClick = { expanded = false; onRename(conversation) })
            DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(HugeIcons.Delete01, null) }, onClick = { expanded = false; onDelete(conversation) })
        }
    }
}

@Composable
private fun ConversationSearchDialog(conversations: List<NativeConversation>, onDismiss: () -> Unit, onSelect: (NativeConversation) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = conversations.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索对话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(HugeIcons.Search01, null) }, placeholder = { Text("重命名对话") })
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.threadId }) { conversation ->
                        NavigationDrawerItem(label = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, selected = false, onClick = { onSelect(conversation) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun RenameConversationDialog(conversation: NativeConversation, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember(conversation.threadId) { mutableStateOf(conversation.title) }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
        confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onConfirm(title.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DrawerQuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
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
