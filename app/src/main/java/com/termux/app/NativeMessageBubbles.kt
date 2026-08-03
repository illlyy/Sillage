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

@Composable
internal fun RikkaMessageItem(
    message: NativeChatMessage,
    assistantActionText: String?,
    chatState: NativeChatState,
    liveState: NativeChatState?,
    onEdit: () -> Unit,
    onRetry: (() -> Unit)?,
    onLoadSubagentHistory: (String) -> Unit,
    onQuote: (String) -> Unit,
    onReasoningAutoCollapse: () -> Unit = {},
    onPreviewAttachment: (NativeAttachment) -> Unit = {},
    wallpaperBackdrop: Backdrop? = null,
) {
    FcodeChatTypography {
        when (message.role) {
            NativeChatRole.USER -> {
                val implementsPlan = message.content.startsWith(NATIVE_IMPLEMENT_PLAN_DISPLAY_PREFIX)
                val displayText = if (implementsPlan) nativeText(LocalNativeLanguage.current, "\u662f\uff0c\u6267\u884c\u6b64\u8ba1\u5212", "Yes, implement this plan") else message.content
                RikkaUserMessage(displayText, message.skills, message.attachments, onEdit, onPreviewAttachment, editable = !implementsPlan, backdrop = wallpaperBackdrop)
            }
            NativeChatRole.ASSISTANT -> {
                val liveSnapshot = if (message.streaming && chatState.liveAssistantMessageId == message.id) {
                    chatState.liveAssistantSnapshot
                } else null
                val historicalFileChanges = remember(message.id, chatState.conversationAnimationKey, chatState.messages.size) {
                    associatedFileChangeItems(chatState.messages, message.id)
                }
                // Tool payloads change far less often than the streamed answer. Snapshot the
                // state list so token batches do not parse every completed tool JSON again.
                val liveToolDetails = liveState?.toolDetails?.toList().orEmpty()
                val liveFileChanges = remember(liveToolDetails) {
                    parseToolDetails(liveToolDetails).fileChanges
                }
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
                    chatState.projectPath,
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
}

@Composable
internal fun RikkaUserMessage(
    text: String,
    skills: List<NativeSkill>,
    attachments: List<NativeAttachment>,
    onEdit: () -> Unit,
    onPreviewAttachment: (NativeAttachment) -> Unit,
    editable: Boolean = true,
    backdrop: Backdrop? = null,
) {
    val language = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    val appearanceRevision = LocalFcodeAppearanceRevision.current
    val bubbleContext = LocalContext.current
    val bubbleConfig = remember(appearanceRevision, bubbleContext) { readUserBubbleLiquidGlassConfig(bubbleContext) }
    val isLightTheme = rememberIsLightTheme()
    val useLiquidGlass = LocalFcodeInterfaceStyle.current == FcodeInterfaceStyle.LIQUID_GLASS &&
        bubbleConfig.enabled && liquidGlassSupported && backdrop != null
    val materialBubbleAlpha = LocalFcodeMaterialTransparency.current.userBubbleAlpha
    val bubbleTint = (if (isLightTheme) Color.White else Color.Black).copy(alpha = bubbleConfig.tintAlpha)
    val bubbleShape = RoundedCornerShape(
        topStart = bubbleConfig.spec.cornerRadiusDp.dp,
        topEnd = bubbleConfig.spec.cornerRadiusDp.dp,
        bottomEnd = 6.dp,
        bottomStart = bubbleConfig.spec.cornerRadiusDp.dp,
    )
    var menuExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (attachments.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                attachments.forEach { attachment ->
                    val sizeLabel = remember(attachment.path) {
                        formatFileSize(java.io.File(attachment.path).length())
                    }
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 250.dp)
                            .fcodePressClickable(
                                onClickLabel = nativeText(language, "\u9884\u89c8\u9644\u4ef6", "Preview attachment"),
                            ) { onPreviewAttachment(attachment) },
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
                                Text(sizeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fcodePressCombinedClickable(onClick = {}, onLongClick = { menuExpanded = true })
                    .then(
                        if (useLiquidGlass && backdrop != null) Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { bubbleShape },
                            effects = { applyLiquidGlassEffects(bubbleConfig.spec, isLightTheme) },
                            highlight = { Highlight.Plain },
                            onDrawSurface = { drawRect(bubbleTint) },
                        ) else Modifier
                    ),
                shape = bubbleShape,
                color = if (useLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.primaryContainer.copy(alpha = materialBubbleAlpha),
                contentColor = if (useLiquidGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer,
                border = if (useLiquidGlass) BorderStroke(0.7.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)) else null,
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
internal fun FinalOnlyAnswerReveal(
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
internal fun StreamingResponseText(
    messageId: String,
    text: String,
    streaming: Boolean,
    revealStartedAt: Long,
    finalOnlyReveal: Boolean,
    liveSnapshot: NativeStreamingMarkdownSnapshot? = null,
    onQuoteSelection: ((String) -> Unit)? = null,
    onLiveSnapshotPresented: ((Int) -> Unit)? = null,
    projectPath: String = "",
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
            projectPath = projectPath,
        )
    }
    if (streaming) {
        LiveAnswerViewport(generation = generation, content = content)
    } else {
        content()
    }
}

@Composable
internal fun LiveAnswerViewport(generation: Int, content: @Composable () -> Unit) {
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
internal fun StreamingMarkdownSnapshotContent(
    snapshot: NativeStreamingMarkdownSnapshot,
    tailStart: Int,
    generation: Int,
    streaming: Boolean,
    onQuoteSelection: ((String) -> Unit)? = null,
    projectPath: String = "",
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
                if (streaming && !LocalStreamingMarkdownRenderEnabled.current) {
                    // Do not create Markwon AndroidViews while tokens are arriving unless the
                    // user opts into live Markdown via the streaming-render toggle. Even with
                    // background parsing, applying spans and measuring each new TextView runs on
                    // the UI thread. Plain stable Compose chunks keep generation frame-friendly;
                    // completed output is upgraded to rich Markdown in bounded batches above.
                    StableLiveTextChunk(block.text, reasoning = false)
                } else {
                    StableStreamingMarkdownBlock(block.text, onQuoteSelection, projectPath)
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
internal fun StableStreamingMarkdownBlock(text: String, onQuoteSelection: ((String) -> Unit)? = null, projectPath: String = "") {
    // Restartable boundary: an immutable completed block is skipped on later deltas while
    // Markwon parses it once in the background. Only the unfinished tail keeps changing.
    RichResponseText(text, onQuoteSelection, projectPath)
}

@Composable
internal fun RikkaAssistantMessage(
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
    projectPath: String,
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
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .fcodePressCombinedClickable(onClick = {}, onLongClick = { if (showChrome) menuExpanded = true }),
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
                        projectPath = projectPath,
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
                            MessageActions(text = actionText, onRetry = onRetry, allowShare = true, assistant = true)
                        } else if (streaming) {
                            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(7.dp))
                                Text(nativeText(language, "正在生成", "Generating"), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (LocalShowResponseStats.current && (streaming || usage != null)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 32.dp)) {
                        if (!streaming && showChrome && usage != null) {
                            ResponseUsageFooterCompact(usage, modelName = liveState?.modelLabel.orEmpty())
                        }
                    }
                }
                if (!streaming && fileChangeItems.isNotEmpty()) {
                    FileDiffCard(fileChangeItems, projectPath)
                }
        }
        DropdownMenu(expanded = showChrome && menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(nativeText(language, "复制回答", "Copy answer")) },
                leadingIcon = { Icon(HugeIcons.Copy01, null, modifier = Modifier.size(18.dp)) },
                onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(actionText)); menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text(nativeText(language, "引用回答", "Quote answer")) },
                leadingIcon = { Icon(HugeIcons.LeftToRightListBullet, null, modifier = Modifier.size(18.dp)) },
                onClick = { menuExpanded = false; onQuote(actionText) },
            )
            if (onRetry != null) DropdownMenuItem(
                text = { Text(nativeText(language, "重新生成", "Regenerate")) },
                leadingIcon = { Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(18.dp)) },
                onClick = { menuExpanded = false; onRetry() },
            )
            DropdownMenuItem(
                text = { Text(nativeText(language, "分享回答", "Share answer")) },
                leadingIcon = { Icon(HugeIcons.Share08, null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    menuExpanded = false
                    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, actionText)
                    context.startActivity(Intent.createChooser(intent, nativeText(language, "分享回答", "Share answer")))
                },
            )
        }
    }
}

@Composable
internal fun ResponseUsageFooter(usage: NativeTurnUsage) {
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
internal fun ResponseUsageFooterCompact(usage: NativeTurnUsage, modelName: String = "") {
    val language = LocalNativeLanguage.current
    val context = LocalContext.current
    val priceStore = remember(context) {
        NativeTokenPriceStore(context.applicationContext.getSharedPreferences("codex_mobile", android.content.Context.MODE_PRIVATE))
    }
    val price = remember(modelName) { priceStore.priceForModel(modelName) }
    val cost = remember(usage, price) { price?.let { estimateTurnCost(usage, it) } }
    var showCostDetails by remember { mutableStateOf(false) }
    val prefix = if (usage.estimated) "≈" else ""
    if (usage.inputTokens <= 0 && usage.cachedInputTokens <= 0 && usage.outputTokens <= 0 &&
        usage.reasoningOutputTokens <= 0 && usage.outputTokensPerSecond <= 0.0 && usage.durationMs <= 0
    ) return
    val mutedColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.56f)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (usage.inputTokens > 0 || usage.cachedInputTokens > 0) {
            val cached = if (usage.cachedInputTokens > 0) {
                nativeText(language, "（${formatNativeTokenCount(usage.cachedInputTokens)} 缓存）", " (${formatNativeTokenCount(usage.cachedInputTokens)} cached)")
            } else ""
            ResponseUsageMetric(
                icon = HugeIcons.Upload02,
                description = nativeText(language, "输入与缓存", "Input and cache"),
                text = "$prefix${formatNativeTokenCount(usage.inputTokens)} tokens$cached",
                color = mutedColor,
            )
        }
        if (usage.outputTokens > 0) {
            ResponseUsageMetric(
                icon = HugeIcons.Download04,
                description = nativeText(language, "输出", "Output"),
                text = "$prefix${formatNativeTokenCount(usage.outputTokens)} tokens",
                color = mutedColor,
            )
        }
        if (usage.reasoningOutputTokens > 0) {
            ResponseUsageMetric(
                icon = HugeIcons.Idea01,
                description = nativeText(language, "推理", "Reasoning"),
                text = nativeText(language, "${formatNativeTokenCount(usage.reasoningOutputTokens)} 推理", "${formatNativeTokenCount(usage.reasoningOutputTokens)} reasoning"),
                color = mutedColor,
            )
        }
        usage.outputTokensPerSecond.takeIf { it > 0.0 }?.let { rate ->
            ResponseUsageMetric(
                icon = HugeIcons.Zap,
                description = nativeText(language, "速度", "Speed"),
                text = String.format(Locale.US, "%.1f tok/s", rate),
                color = mutedColor,
            )
        }
        if (usage.durationMs > 0) {
            val seconds = usage.durationMs / 1000.0
            ResponseUsageMetric(
                icon = HugeIcons.Clock02,
                description = nativeText(language, "用时", "Duration"),
                text = if (seconds < 10.0) String.format(Locale.US, "%.1fs", seconds)
                    else String.format(Locale.US, "%.0fs", seconds),
                color = mutedColor,
            )
        }
        cost?.takeIf { it > 0.0 }?.let { estimated ->
            ResponseUsageMetric(
                icon = HugeIcons.ChartColumn,
                description = nativeText(language, "成本", "Cost"),
                text = "$prefix${formatNativeCost(estimated)}",
                color = mutedColor,
                onClick = { showCostDetails = true },
            )
        }
    }
    if (showCostDetails && price != null) {
        NativeCostDetailDialog(
            usage = usage,
            modelName = modelName,
            price = price,
            cost = cost ?: 0.0,
            onDismiss = { showCostDetails = false },
        )
    }
}

@Composable
internal fun NativeCostDetailDialog(
    usage: NativeTurnUsage,
    modelName: String,
    price: NativeModelPrice,
    cost: Double,
    onDismiss: () -> Unit,
) {
    val language = LocalNativeLanguage.current
    @Composable
    fun row(label: String, tokens: Long, perMillion: Double) {
        val lineCost = estimateTokenCost(tokens, perMillion)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${formatNativeTokenCount(tokens)} × $${"%.3f".format(perMillion)}/M",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "$${"%.4f".format(lineCost)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
            )
        }
    }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(modelName.ifBlank { nativeText(language, "成本明细", "Cost details") }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (usage.inputTokens > 0) row(nativeText(language, "输入", "Input"), usage.inputTokens, price.inputPerMillion)
                if (usage.cachedInputTokens > 0) row(nativeText(language, "缓存输入", "Cached input"), usage.cachedInputTokens, price.cachedInputPerMillion)
                if (usage.outputTokens > 0) row(nativeText(language, "输出", "Output"), usage.outputTokens, price.outputPerMillion)
                if (usage.reasoningOutputTokens > 0) row(nativeText(language, "推理输出", "Reasoning output"), usage.reasoningOutputTokens, price.outputPerMillion)
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(nativeText(language, "合计", "Total"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(formatNativeCost(cost), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    nativeText(language, "价格为估算值，可在设置中调整。", "Prices are estimates; tune them in Settings."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "关闭", "Close")) } },
    )
}

@Composable
internal fun ResponseUsageMetric(
    icon: ImageVector,
    description: String,
    text: String,
    color: Color,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = description, modifier = Modifier.size(12.dp), tint = color)
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
    if (onClick != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fcodePressClickable(
                onClickLabel = description,
            ) { onClick() }.padding(horizontal = 2.dp),
        ) { content() }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) { content() }
    }
}

@Composable
internal fun MessageActions(
    text: String,
    onEdit: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    allowShare: Boolean = false,
    assistant: Boolean = false,
) {
    val language = LocalNativeLanguage.current
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        FcodeCopyAction(text, formatMenu = assistant)
        if (assistant && text.isNotBlank()) {
            FcodeTtsAction(text)
        }
        if (onEdit != null) IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.PencilEdit01, "编辑", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onRetry != null) IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.Refresh03, "重新生成", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (allowShare) IconButton(onClick = {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
            context.startActivity(android.content.Intent.createChooser(intent, "分享回答"))
        }, modifier = Modifier.size(32.dp)) { Icon(HugeIcons.Share08, "分享", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * Copy action with format selection (pattern: claudecodeui MessageCopyControl). Assistant
 * messages copy as plain text or Markdown; the plain-text conversion protects fenced code
 * blocks so their content survives syntax stripping. A brief check feedback confirms the copy.
 */
@Composable
internal fun FcodeCopyAction(text: String, formatMenu: Boolean = false, modifier: Modifier = Modifier) {    val language = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var copiedAt by remember { mutableStateOf<Long?>(null) }
    fun copy(format: String) {
        val content = if (format == "md") text else convertMarkdownToPlainText(text)
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(content))
        copiedAt = System.currentTimeMillis()
    }
    LaunchedEffect(copiedAt) {
        if (copiedAt != null) {
            kotlinx.coroutines.delay(1_600)
            copiedAt = null
        }
    }
    Box(modifier = modifier) {
        IconButton(
            onClick = { if (formatMenu) menuOpen = true else copy("text") },
            modifier = Modifier.size(32.dp),
        ) {
            val copied = copiedAt != null
            Icon(
                if (copied) HugeIcons.Tick02 else HugeIcons.Copy01,
                if (copied) {
                    nativeText(language, "已复制", "Copied")
                } else {
                    nativeText(language, "复制", "Copy")
                },
                modifier = Modifier.size(16.dp),
                tint = if (copied) Color(0xFF5E8B68) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(nativeText(language, "复制为文本", "Copy as text")) },
                onClick = { copy("text"); menuOpen = false },
            )
            DropdownMenuItem(
                text = { Text(nativeText(language, "复制为 Markdown", "Copy as Markdown")) },
                onClick = { copy("md"); menuOpen = false },
            )
        }
    }
}

/** Read-aloud action for assistant answers; morphs into a stop control while speaking. */
@Composable
internal fun FcodeTtsAction(text: String, modifier: Modifier = Modifier) {
    val language = LocalNativeLanguage.current
    val context = LocalContext.current
    val speaking = FcodeTtsController.speaking
    IconButton(
        onClick = {
            if (FcodeTtsController.speaking) {
                FcodeTtsController.stop()
            } else {
                FcodeTtsController.speak(context, text)
            }
        },
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            if (speaking) HugeIcons.Cancel01 else HugeIcons.Voice,
            if (speaking) {
                nativeText(language, "停止朗读", "Stop reading")
            } else {
                nativeText(language, "朗读", "Read aloud")
            },
            modifier = Modifier.size(16.dp),
            tint = if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun QElasticExpand(visible: Boolean, modifier: Modifier = Modifier, durationMs: Int = 280, content: @Composable () -> Unit) {
    // Smooth, coordinated height+fade tween. The old spring delivered full height in ~120ms while
    // the linear fade lagged behind — on enter that read as "a full-size bar, then text fills in";
    // on exit the fade finished first leaving a transparent shrinking rectangle that popped away at
    // the end (shifting the whole list). No spring means no overshoot, so the layout below moves
    // monotonically and the auto-follow motor can track it instead of lagging then snapping.
    // [durationMs] scales the whole accordion; the historical card uses a gentler, longer value.
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(durationMs, easing = FastOutSlowInEasing), clip = true)
            + fadeIn(tween(durationMs - 40, easing = LinearEasing)),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(durationMs - 40, easing = FastOutSlowInEasing), clip = true)
            + fadeOut(tween(durationMs - 80, easing = LinearEasing)),
    ) { content() }
}

@Composable
internal fun SafeExpandableViewport(maxHeight: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
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

internal data class LiveTextChunk(val start: Int, val text: String)

internal fun splitLiveText(text: String, targetSize: Int = 520, maxSize: Int = 760): List<LiveTextChunk> {
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
internal fun StableLiveTextChunk(text: String, reasoning: Boolean) {
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
internal fun FadingTailText(text: String, tailStart: Int, generation: Int, reasoning: Boolean) {
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
internal fun ChunkedLiveText(text: String, tailStart: Int, generation: Int, reasoning: Boolean, animateTail: Boolean) {
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
internal fun LiveReasoningText(text: String) {
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
internal fun DeferredHistoricalRichText(text: String) {
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
internal fun ActiveProcessingPanel(
    state: NativeChatState,
    answerStarted: Boolean,
    onLoadSubagentHistory: (String) -> Unit,
    onAutomaticCollapse: () -> Unit,
) {
    val openSubagentDrawer = LocalOpenSubagentDrawer.current
    val messageSnapshot = state.messages.toList()
    val liveSubagentSnapshot = state.liveSubagents.toList()
    val subagentCandidates = remember(messageSnapshot, liveSubagentSnapshot) {
        collectAllSubagentItems(messageSnapshot, liveSubagentSnapshot)
    }
    // Normalized protocol events own the live group. The compatibility adapter covers the tiny
    // interval (or an old callback) before that event is reduced, while still producing the same
    // domain DTO and using the same renderer as history.
    val domainGroup = state.activityGroups.lastOrNull { it.running }
        ?: state.legacyLiveActivityGroup()
    if (domainGroup != null) {
        NativeActivityGroupRenderer(
            group = domainGroup,
            subagents = state.conversationRenderModel.subagents,
            onSubagentClick = { visual ->
                val anchor = subagentDrawerAnchor(visual, subagentCandidates)
                openSubagentDrawer(anchor)
                subagentThreadId(anchor).takeIf { it.isNotBlank() }?.let(onLoadSubagentHistory)
            },
            onSubagentOverflowClick = { visuals ->
                // Reuse the same near-full-height overview drawer as an individual capsule.
                val anchor = visuals.firstOrNull()
                    ?.let { subagentDrawerAnchor(it, subagentCandidates) }
                    ?: subagentCandidates.firstOrNull()?.let { JSONObject(it.toString()) }
                    ?: JSONObject().put("type", "collabAgentToolCall")
                anchor.put("_openOverview", true)
                openSubagentDrawer(anchor)
            },
            onLoadSubagentHistory = onLoadSubagentHistory,
        )
    } else if (state.phase.active) {
        // No exploration group yet (model still thinking before any reasoning/command streams).
        // Show a live elapsed "thinking" row so the wait is never a blank spinner.
        ThinkingElapsedRow(state)
    }
}

@Composable
internal fun ThinkingElapsedRow(state: NativeChatState) {
    val language = LocalNativeLanguage.current
    val start = state.phaseStartedAt.takeIf { it > 0L } ?: state.turnStartedAt
    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(start, state.phase) {
        while (start > 0L && state.phase.active) {
            elapsedSeconds = ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                nativeText(language, "思考中 · ${elapsedSeconds}s", "Thinking · ${elapsedSeconds}s"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ReasoningCapsuleExpand(visible: Boolean, content: @Composable () -> Unit) {
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
internal fun RikkaErrorMessage(text: String, onRetry: (() -> Unit)?) {
    val language = LocalNativeLanguage.current
    val displayText = remember(text) { NativeUiRenderSafety.errorSummary(text) }
    val retrying = text.startsWith("正在重试") || text.startsWith("目标自动重试")
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (retrying) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (retrying) nativeText(language, "\u6b63\u5728\u91cd\u8bd5", "Retrying")
                    else nativeText(language, "\u53d1\u751f\u9519\u8bef", "Error"),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp)); SelectionContainer { Text(displayText, style = MaterialTheme.typography.bodySmall) }
            if (!retrying && onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Icon(HugeIcons.Refresh03, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(nativeText(language, "\u91cd\u8bd5", "Retry"))
            }
        }
    }
}

