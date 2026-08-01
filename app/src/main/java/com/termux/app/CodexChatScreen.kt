@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.termux.app

import android.util.Base64
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
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
import androidx.compose.ui.unit.Constraints
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
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.highlight.Highlight
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.Idea01
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
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.hugeicons.stroke.Voice
import me.rerere.hugeicons.stroke.Zap

val LocalNativeLanguage = staticCompositionLocalOf { "zh" }
val LocalStreamAnimationsEnabled = staticCompositionLocalOf { true }
val LocalFixedStreamingViewportEnabled = staticCompositionLocalOf { true }
internal val LocalInteractiveScrollInProgress = staticCompositionLocalOf { false }
internal val LocalTextSelectionActivityChanged = staticCompositionLocalOf<(Boolean) -> Unit> { { } }
internal val LocalOpenSubagentDrawer = staticCompositionLocalOf<(JSONObject) -> Unit> { { } }
private const val CHAT_HISTORY_PAGE_SIZE = 24

internal val NativeTurnPhase.showsProcessingPanel: Boolean
    get() = when (this) {
        NativeTurnPhase.WAITING, NativeTurnPhase.REASONING, NativeTurnPhase.TOOL_RUNNING -> true
        else -> false
    }

@Immutable
internal data class PendingSendMotion(
    val token: Long,
    val messageId: String,
    val text: String,
    val sourceBounds: Rect,
    val targetBounds: Rect? = null,
)

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
    interfaceStyle: String = FcodeInterfaceStyle.MATERIAL.value,
    appearanceRevision: Int = 0,
    chatBackground: String = FcodeChatBackgroundStyle.THEME.value,
    chatBackgroundImage: String = "",
    chatBackgroundDim: Float = 0.32f,
    chatDynamicBackground: FcodeChatDynamicBackgroundConfig? = null,
    chatFontScale: Float? = null,
    materialTransparency: FcodeMaterialTransparencyConfig? = null,
    fixedStreamingViewport: Boolean = true,
    showResponseStats: Boolean = true,
    showModelSubtitle: Boolean = true,
    showReasoningTitles: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = currentFcodeDarkMode(themeMode)
    val palette = FcodeColorPalette.from(colorPalette)
    val style = FcodeInterfaceStyle.from(interfaceStyle)
    val background = FcodeChatBackgroundStyle.from(chatBackground)
    val dynamicContext = LocalContext.current
    val dynamicBackground = chatDynamicBackground ?: remember(appearanceRevision, dynamicContext) {
        readFcodeChatDynamicBackgroundConfig(dynamicContext)
    }
    val resolvedChatFontScale = chatFontScale ?: remember(appearanceRevision, dynamicContext) {
        readFcodeChatFontScale(dynamicContext)
    }
    val resolvedMaterialTransparency = materialTransparency ?: remember(appearanceRevision, dynamicContext) {
        readFcodeMaterialTransparencyConfig(dynamicContext)
    }
    val colorScheme = if (style == FcodeInterfaceStyle.LIQUID_GLASS) {
        liquidGlassColorScheme(dark)
    } else {
        fcodeResolvedColorScheme(dynamicContext, palette, dark)
    }
    val markdownColors = fcodeMarkdownColors(palette, dark, colorScheme, style.value)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    val themedContent: @Composable () -> Unit = {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalNativeLanguage provides language,
            LocalStreamAnimationsEnabled provides streamAnimations,
            LocalFixedStreamingViewportEnabled provides fixedStreamingViewport,
            LocalShowReasoning provides showReasoning,
            LocalAutoFollowOutput provides autoFollow,
            LocalShowResponseStats provides showResponseStats,
            LocalShowModelSubtitle provides showModelSubtitle,
            LocalShowReasoningTitles provides showReasoningTitles,
            LocalFcodeInterfaceStyle provides style,
            LocalFcodeAppearanceRevision provides appearanceRevision,
            LocalFcodeColorPalette provides palette,
            LocalFcodeChatBackground provides background,
            LocalFcodeChatBackgroundImage provides chatBackgroundImage,
            LocalFcodeChatBackgroundDim provides chatBackgroundDim.coerceIn(0f, 0.72f),
            LocalFcodeChatDynamicBackground provides dynamicBackground,
            LocalFcodeChatFontScale provides resolvedChatFontScale.coerceIn(0.5f, 2f),
            LocalFcodeMaterialTransparency provides resolvedMaterialTransparency.normalized(),
            LocalFcodeMarkdownColors provides markdownColors,
            content = content,
        )
    }
    if (style == FcodeInterfaceStyle.LIQUID_GLASS) {
        MaterialTheme(colorScheme = colorScheme, content = themedContent)
    } else {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = themedContent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeChatScreen(
    state: NativeChatState,
    onSend: (String) -> NativeSubmitResult?,
    onInputChange: (String) -> Unit,
    onRetry: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onStop: () -> Unit,
    onFollowUpActionChange: (NativeFollowUpSubmitAction) -> Unit,
    onRemoveQueuedFollowUp: (String) -> Unit,
    onNewConversation: () -> Unit,
    onNewConversationAtProject: (String) -> Unit,
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
    val interfaceStyle = LocalFcodeInterfaceStyle.current
    val appearanceRevision = LocalFcodeAppearanceRevision.current
    val topBarGlassConfig = remember(appearanceRevision, context) { readTopBarLiquidGlassConfig(context) }
    val progressiveTopBar = interfaceStyle == FcodeInterfaceStyle.LIQUID_GLASS &&
        topBarGlassConfig.enabled && liquidGlassSupported
    val dynamicBackground = LocalFcodeChatDynamicBackground.current
    val sendAnimationsEnabled = LocalStreamAnimationsEnabled.current
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
    val dynamicBackgroundTapSignal = remember(conversationListKey) { mutableLongStateOf(0L) }
    val dynamicBackgroundCanStart = dynamicBackground.enabled &&
        liquidGlassSupported &&
        conversationSurfaceStage != "loading"
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
    // Capture wallpaper and conversation chrome in separate layers, then composite them for
    // the glass input. A LayerBackdrop only records its own subtree: sampling the LazyColumn alone
    // leaves the wallpaper outside the source and degenerates into a translucent white card when
    // the conversation is empty. Keeping the composer outside both sources also avoids self-sampling.
    val wallpaperBackdrop = rememberLayerBackdrop()
    val conversationBackdrop = rememberLayerBackdrop()
    val chatBackdrop = rememberCombinedBackdrop(wallpaperBackdrop, conversationBackdrop)
    var pendingSendMotion by remember(conversationListKey) { mutableStateOf<PendingSendMotion?>(null) }
    var sendMotionRootBounds by remember { mutableStateOf<Rect?>(null) }
    var ultraBurst by remember(conversationListKey) { mutableStateOf<UltraBurstRequest?>(null) }
    var rootWindowBounds by remember { mutableStateOf(Rect.Zero) }
    var inputHeightPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Keep one owner for each inset: the list handles the IME, while the measured composer
    // reports only its visible controls. Navigation-bar clearance is added explicitly below.
    val imeVisible = WindowInsets.isImeVisible
    val navigationBarBottomPadding = if (imeVisible) 0.dp
        else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The progressive material may be taller than the interactive app bar. Keep the resting
    // conversation inset tied to Material's real bar height plus the visible status bar.
    val topBarContentPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        TopAppBarDefaults.TopAppBarExpandedHeight
    val conversationViewportModifier = Modifier
        .fillMaxSize()
        .then(if (progressiveTopBar) Modifier else Modifier.padding(top = topBarContentPadding))
        .imePadding()
    val conversationTopContentPadding = 16.dp + if (progressiveTopBar) topBarContentPadding else 0.dp
    val inputBottomPadding = with(density) { inputHeightPx.toDp() } + navigationBarBottomPadding + 8.dp
    val floatingInsetModifier = if (imeVisible) Modifier.imePadding() else Modifier
    val showScrollToBottom by remember(listState) { derivedStateOf { state.messages.isNotEmpty() && listState.canScrollForward } }
    val canExportConversation by remember(state.messages) {
        derivedStateOf { state.messages.any { it.role == NativeChatRole.USER || it.role == NativeChatRole.ASSISTANT } }
    }
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
    var pushGitRequest by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(pendingSendMotion?.token) {
        val activeMotion = pendingSendMotion ?: return@LaunchedEffect
        onUiMotionChanged(true)
        try {
            delay(1_600L)
            if (pendingSendMotion?.token == activeMotion.token) pendingSendMotion = null
        } finally {
            onUiMotionChanged(false)
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
                    onNewConversationAtProject = { projectPath ->
                        if (pendingConversationThreadId == null) {
                            pendingConversationThreadId = ""
                            scope.launch {
                                try {
                                    drawerState.close()
                                    withFrameNanos { }
                                    onNewConversationAtProject(projectPath)
                                } finally {
                                    pendingConversationThreadId = null
                                }
                            }
                        }
                    },
                    onBackHome = {
                        scope.launch {
                            // Keep the drawer out of the settings entry snapshot, but do not
                            // add a second frame of waiting when it is already closed. The
                            // navigation close keeps the same interruptible motion with a
                            // shorter Apple-style response.
                            if (!drawerState.isClosed) drawerState.closeForNavigation()
                            onBackHome()
                        }
                    },
                    onOpenLegacyWebUi = onOpenLegacyWebUi,
                )
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { rootWindowBounds = it.boundsInWindow() }
                    .pointerInput(conversationListKey, dynamicBackgroundCanStart) {
                        if (!dynamicBackgroundCanStart) return@pointerInput
                        // Observe taps at the chat root so buttons, bubbles, the composer and empty
                        // space can all start the reveal. Nothing is consumed, so normal clicks and
                        // scrolling continue to reach their original targets.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = down.position
                            var moved = false
                            var active = true
                            while (active) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if ((change.position - start).getDistance() > viewConfiguration.touchSlop) moved = true
                                active = change.pressed
                            }
                            if (!moved) dynamicBackgroundTapSignal.longValue += 1L
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // Blur is outside the capture modifier: the user sees the cached frosted
                        // wallpaper, while `wallpaperBackdrop` retains the crisp source sampled by
                        // the expanding liquid-glass shader.
                        .fcodeDynamicBackgroundBlur(dynamicBackground)
                        .layerBackdrop(wallpaperBackdrop)
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    AssistantBackdrop()
                }
                FcodeChatDynamicBackground(
                    modifier = Modifier.fillMaxSize(),
                    backdrop = wallpaperBackdrop,
                    config = dynamicBackground,
                    animationKey = conversationListKey,
                    autoStartAllowed = conversationSurfaceStage != "loading",
                    manualStartSignal = dynamicBackgroundTapSignal.longValue,
                )
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {},
                    bottomBar = {},
                ) { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Keep the capture layer full-height. A permanent layout offset here clips
                        // the conversation below the app bar, so the glass can only ever sample the
                        // wallpaper. Scrollable content owns the top inset instead (see below).
                        .onGloballyPositioned { sendMotionRootBounds = it.boundsInWindow() },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(conversationBackdrop)
                            .graphicsLayer {
                            val progress = conversationSurfaceProgress
                            alpha = 0.28f + 0.72f * progress
                            translationY = (1f - progress) * 10.dp.toPx()
                            scaleX = 0.992f + 0.008f * progress
                            scaleY = 0.992f + 0.008f * progress
                            transformOrigin = TransformOrigin.Center
                        },
                    ) {
                    if (state.historyLoading && state.messages.isEmpty() && state.currentThreadId.isNotBlank()) {
                        Box(Modifier.fillMaxSize().padding(top = topBarContentPadding)) {
                            ConversationHistoryLoading(state.conversationTitle)
                        }
                    } else if (state.messages.isEmpty()) {
                        RikkaEmptyState(
                            status = state.connectionLabel,
                            ready = state.ready,
                            onSuggestion = onInputChange,
                            modifier = Modifier.padding(
                                top = topBarContentPadding,
                                bottom = inputBottomPadding,
                            ),
                        )
                    } else {
                        // Do not cache by list size: streamed deltas replace the current
                        // message without changing size. A size-keyed snapshot permanently
                        // retained the first tiny delta and its streaming=true flag.
                        val visibleMessages by remember(conversationListKey, historyLimit) {
                            derivedStateOf { state.messages.takeLast(historyLimit) }
                        }
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
                        val retryStructureKey = remember(visibleMessages) {
                            var fingerprint = 1
                            visibleMessages.forEach { message ->
                                fingerprint = 31 * fingerprint + message.id.hashCode()
                                fingerprint = 31 * fingerprint + message.role.hashCode()
                                if (message.role == NativeChatRole.USER) {
                                    fingerprint = 31 * fingerprint + message.content.hashCode()
                                }
                            }
                            fingerprint
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
                                // Liquid Glass owns a full-screen viewport: the initial content
                                // padding keeps the first message below the controls, while scrolled
                                // text can pass behind the AlphaMask and become the live blur source.
                                // Material keeps the old clipped viewport and opaque top bar.
                                modifier = conversationViewportModifier,
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = conversationTopContentPadding,
                                    bottom = inputBottomPadding,
                                ),
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
                                    previousUser?.let { prompt -> {retryItem(prompt) } }
                                }
                                // Per-message reveal animation: bubbles scale in from the left
                                // edge with a spring, like modern chat apps. State is keyed by
                                // message id so streaming deltas or scroll recycling never
                                // re-trigger the animation.
                                var bubbleAppeared by remember(message.id) { mutableStateOf(false) }
                                val bubbleReveal by animateFloatAsState(
                                    targetValue = if (bubbleAppeared) 1f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = 380f,
                                        visibilityThreshold = 0.001f,
                                    ),
                                    label = "bubbleReveal",
                                )
                                LaunchedEffect(message.id) {
                                    withFrameNanos { }
                                    bubbleAppeared = true
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
                                    Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (bubbleReveal < 0.999f) Modifier.graphicsLayer {
                                                val p = bubbleReveal.coerceIn(0f, 1f)
                                                scaleX = 0.85f + 0.15f * p
                                                scaleY = 0.85f + 0.15f * p
                                                translationX = -(1f - p) * size.width * 0.32f
                                                alpha = p
                                                transformOrigin = TransformOrigin(0f, 0.5f)
                                            } else Modifier
                                        )
                                        .then(stableMessageModifier),
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
                                        wallpaperBackdrop = wallpaperBackdrop,
                                        sendingMotionActive = pendingSendMotion?.messageId == message.id,
                                        onUserBubbleBounds = if (pendingSendMotion?.messageId == message.id) {
                                            { bounds ->
                                                val current = pendingSendMotion
                                                if (current != null && current.messageId == message.id && current.targetBounds != bounds) {
                                                    pendingSendMotion = current.copy(targetBounds = bounds)
                                                }
                                            }
                                        } else null,
                                    )
                                }
                            }
                            if (state.phase.showsProcessingPanel && liveAssistantId == null) {
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
                                Icon(HugeIcons.ArrowDown01, nativeText(language, "回到底部", "Scroll to bottom"), modifier = Modifier.size(20.dp))
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
                        enabled = state.ready,
                        loading = state.phase.active,
                        compactEnabled = !state.phase.active && state.compactionItems.none { !it.isTerminal },
                        compactRunning = state.compactionItems.any { !it.isTerminal },
                        submitEnabled = state.compactionItems.none { !it.isTerminal },
                        conversationKey = conversationListKey,
                        conversationMoving = listDragged,
                        hasConversation = state.messages.isNotEmpty(),
                        followUpAction = state.followUpSubmitAction,
                        queuedFollowUps = state.queuedFollowUps,
                        onFollowUpActionChange = onFollowUpActionChange,
                        onRemoveQueuedFollowUp = onRemoveQueuedFollowUp,
                        onStop = onStop,
                        modelLabel = state.modelLabel,
                        onModelClick = { showModelPicker = true },
                        effortOptions = state.modelOptions.firstOrNull { it.id == state.selectedModel }?.efforts.orEmpty().ifEmpty { listOf("none", "low", "medium", "high", "xhigh") },
                        selectedEffort = state.selectedEffort,
                        onEffortSelected = onEffortSelected,
                        onUltraBurst = { rect ->
                            ultraBurst = UltraBurstRequest(
                                token = android.os.SystemClock.uptimeMillis(),
                                originWindowRect = rect,
                            )
                        },
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
                        onSend = { inputText, sourceBounds ->
                            followOutput = true
                            followPausedUntil = 0L
                            // TextFieldState remains focused while a turn is active. The Activity
                            // chooses same-turn steer or local queue without disabling the IME.
                            val result = onSend(inputText)
                            val messageId = result?.messageId
                            if (messageId != null && inputText.isNotBlank() && sourceBounds != null &&
                                sendAnimationsEnabled && !touchExplorationEnabled
                            ) {
                                pendingSendMotion = PendingSendMotion(
                                    token = android.os.SystemClock.uptimeMillis(),
                                    messageId = messageId,
                                    text = inputText.trim(),
                                    sourceBounds = sourceBounds,
                                )
                            }
                            if (messageId != null) {
                                scope.launch {
                                    withFrameNanos { }
                                    listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0), Int.MAX_VALUE)
                                }
                            }
                            result
                        },
                        onHeightChanged = { inputHeightPx = it },
                        backdrop = chatBackdrop,
                        modifier = Modifier
                            .align(Alignment.BottomCenter),
                    )
                    pendingSendMotion?.let { motion ->
                        SendMessageFlightOverlay(
                            motion = motion,
                            rootBounds = sendMotionRootBounds,
                            wallpaperBackdrop = wallpaperBackdrop,
                            onFinished = {
                                if (pendingSendMotion?.token == motion.token) pendingSendMotion = null
                            },
                        )
                    }
                }
            }
                // Keep the progressive material in the same full-screen stacking context as the
                // captured conversation. Scaffold's dedicated top-bar layer clips or occludes the
                // portion below the interactive bar, which prevents a 128dp AlphaMask tail.
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
                    canExport = canExportConversation,
                    onNewConversation = onNewConversation,
                    // Sample both the wallpaper and scrolling conversation; title/actions remain
                    // separate and crisp above the material layer.
                    backdrop = chatBackdrop,
                    glassConfig = topBarGlassConfig,
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(2f),
                )
                ultraBurst?.let { burst ->
                    UltraGlassShockwave(
                        modifier = Modifier.fillMaxSize().zIndex(3f),
                        backdrop = chatBackdrop,
                        requestToken = burst.token,
                        originWindowRect = burst.originWindowRect,
                        rootWindowRect = rootWindowBounds,
                        onFinished = { if (ultraBurst?.token == burst.token) ultraBurst = null },
                    )
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
            onRequestGitPush = { pushGitRequest = state.gitSnapshot; showWorkPanel = false },
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
            onSelect = { messageId ->
                showMessageSearch = false
                scope.launch {
                    val absoluteIndex = state.messages.indexOfFirst { it.id == messageId }
                    if (absoluteIndex < 0) return@launch
                    val requiredLimit = (state.messages.size - absoluteIndex).coerceAtLeast(1)
                    if (requiredLimit > historyLimit) historyLimit = requiredLimit
                    withFrameNanos { }
                    val hiddenCount = (state.messages.size - historyLimit).coerceAtLeast(0)
                    val loaderOffset = if (hiddenCount > 0) 1 else 0
                    val lazyIndex = (absoluteIndex - hiddenCount + loaderOffset).coerceAtLeast(0)
                    listState.animateScrollToItem(lazyIndex)
                }
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
    pushGitRequest?.let { raw ->
        val snapshot = remember(raw) { runCatching { JSONObject(raw) }.getOrNull() }
        val branch = snapshot?.optString("branch").orEmpty()
        val upstream = snapshot?.optString("upstream").orEmpty()
        FlClashAnimatedDialog(
            onDismissRequest = { pushGitRequest = null },
            title = { Text(nativeText(language, "推送分支", "Push branch")) },
            text = {
                Text(if (upstream.isBlank())
                    nativeText(language, "将把 `$branch` 发布到默认远程并设置 upstream。不会执行强制推送。", "Publish `$branch` to the default remote and set its upstream. Force push will not be used.")
                else nativeText(language, "将把 `$branch` 推送到 `$upstream`。不会执行强制推送。", "Push `$branch` to `$upstream`. Force push will not be used."))
            },
            confirmButton = { TextButton(onClick = { pushGitRequest = null; onGitAction("push", "") }) { Text(nativeText(language, "确认推送", "Push")) } },
            dismissButton = { TextButton(onClick = { pushGitRequest = null }) { Text(nativeText(language, "取消", "Cancel")) } },
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
            title = { Text(nativeText(language, "删除对话？", "Delete conversation?")) },
            text = { Text(nativeText(language, "确定从列表中删除“${conversation.title}”吗？", "Remove “${conversation.title}” from the conversation list?")) },
            confirmButton = { TextButton(onClick = { onDeleteConversation(conversation); deleteConversation = null }) { Text(nativeText(language, "删除", "Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConversation = null }) { Text(nativeText(language, "取消", "Cancel")) } },
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
