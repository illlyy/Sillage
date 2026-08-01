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

internal fun effortLabel(value: String, language: String): String = when (value.lowercase()) {
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
internal fun LiquidEffortTool(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onUltraBurst: (Rect) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var popupVisible by remember { mutableStateOf(false) }
    var contentReady by remember { mutableStateOf(false) }
    var previewEffort by remember(options, selected) { mutableStateOf(selected) }
    var effortDragging by remember { mutableStateOf(false) }
    // Anchor of the effort button in app-window coordinates, refreshed during popup placement.
    // Popup windows report their own coordinate space from onGloballyPositioned, so the burst
    // origin is captured from the placement pass instead, which receives the real window bounds.
    var effortAnchorRect by remember { mutableStateOf<Rect?>(null) }
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
    // Commit the selection, then fire the full-screen glass burst once the Ultra effort is
    // actually engaged. The slider only commits on change, so re-selecting Ultra does not stack
    // bursts while it is already active.
    val commitEffort: (String) -> Unit = { value ->
        onSelect(value)
        if (value.equals("ultra", ignoreCase = true)) effortAnchorRect?.let(onUltraBurst)
    }
    LaunchedEffect(popupVisible, expanded) {
        if (popupVisible && expanded) { delay(20); menuVisibility.targetState = true; delay(35); contentReady = true }
    }
    val gapPx = with(density) { 8.dp.roundToPx() }
    val shadowPadPx = with(density) { 14.dp.roundToPx() }
    val positionProvider = remember(gapPx, shadowPadPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val anchor = Rect(
                    anchorBounds.left.toFloat(),
                    anchorBounds.top.toFloat(),
                    anchorBounds.right.toFloat(),
                    anchorBounds.bottom.toFloat(),
                )
                if (effortAnchorRect != anchor) effortAnchorRect = anchor
                val x = (anchorBounds.left - shadowPadPx).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val above = anchorBounds.top - popupContentSize.height + shadowPadPx - gapPx
                val y = if (above >= 0) above else (anchorBounds.bottom + gapPx - shadowPadPx).coerceAtMost(windowSize.height - popupContentSize.height)
                return IntOffset(x, y)
            }
        }
    }
    Box {
        // While the Ultra effort is active the Zap button breathes a primary-colored ring and the
        // icon shifts to primary, echoing the overdrive state without a separate badge.
        val ultraActive = selected.equals("ultra", ignoreCase = true)
        val ultraGlowAlpha = if (ultraActive) {
            val glowTransition = rememberInfiniteTransition(label = "ultraZapGlow")
            glowTransition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "ultraZapGlowAlpha",
            ).value
        } else 0f
        Surface(
            modifier = Modifier.size(40.dp).graphicsLayer { scaleX = buttonScale; scaleY = buttonScale },
            shape = CircleShape,
            color = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (expanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (ultraActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = ultraGlowAlpha)) else null,
        ) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = interaction, indication = null) { if (expanded) close() else open() }, contentAlignment = Alignment.Center) {
                Icon(HugeIcons.Zap, "思维强度", modifier = Modifier.size(21.dp), tint = if (ultraActive) MaterialTheme.colorScheme.primary else LocalContentColor.current)
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
                            Box(Modifier.clip(RoundedCornerShape(22.dp))) {
                                // Starry sky material: only the Ultra effort stages it, fading in
                                // while the slider previews the Ultra segment and out when it leaves.
                                // matchParentSize keeps the panel's own height; a fill-based size
                                // would measure against the wrap-content Popup window and stretch
                                // the panel into a full-screen vertical strip.
                                PixelStarrySky(
                                    visible = previewEffort.equals("ultra", ignoreCase = true),
                                    modifier = Modifier.matchParentSize(),
                                )
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
                                        onSelect = commitEffort,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                                // While Ultra is engaged the hint text is redundant: the starry sky
                                // already signals the state. It fades out in place without changing
                                // the panel size (a fixed slot keeps the layout stable).
                                val hintAlpha by animateFloatAsState(
                                    targetValue = if (ultraActive) 0f else 1f,
                                    animationSpec = tween(180),
                                    label = "effortHintAlpha",
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(30.dp)
                                        .graphicsLayer { alpha = hintAlpha },
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text("拖动或点击选择，松手后自动吸附", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun LiquidEffortSlider(
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

