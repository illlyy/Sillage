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
internal fun ConversationHistoryLoading(title: String) {
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
internal fun RikkaTopBar(
    title: String,
    modelLabel: String,
    onOpenDrawer: () -> Unit,
    onOpenWorkPanel: () -> Unit,
    onSearch: () -> Unit,
    onExport: () -> Unit,
    canExport: Boolean,
    onNewConversation: () -> Unit,
    backdrop: Backdrop? = null,
    glassConfig: TopBarLiquidGlassConfig,
    modifier: Modifier = Modifier,
) {
    val language = LocalNativeLanguage.current
    // Material deliberately remains opaque; developer mask controls belong to Liquid Glass.
    val useProgressiveGlass = LocalFcodeInterfaceStyle.current == FcodeInterfaceStyle.LIQUID_GLASS &&
        glassConfig.enabled && liquidGlassSupported && backdrop != null
    val isLight = rememberIsLightTheme()
    val tint = if (isLight) Color.White else Color.Black
    val surfaceColor = MaterialTheme.colorScheme.surface
    var menuExpanded by remember { mutableStateOf(false) }
    val glassMaterialModifier = if (useProgressiveGlass) {
        Modifier.drawPlainBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = { applyTopBarProgressiveGlass(glassConfig, tint) },
        )
    } else Modifier
    val topBarModifier = if (useProgressiveGlass) Modifier else Modifier.background(surfaceColor)
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            // The material owns a real 128dp-style sampling surface. This composable is placed
            // as a full-screen overlay, while conversation content keeps the bar-sized safe inset.
            Box(Modifier.then(glassMaterialModifier))
            TopAppBar(
                modifier = Modifier.then(topBarModifier).statusBarsPadding(),
                // The separate material layer owns the progressive glass. Keeping this
                // container transparent prevents it from flattening the alpha fade.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(HugeIcons.Menu03, contentDescription = nativeText(language, "对话列表", "Conversations"))
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
                            text = modelLabel.ifBlank { nativeText(language, "默认模型", "Default model") },
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelSmall,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNewConversation) {
                        Icon(HugeIcons.MessageAdd01, contentDescription = nativeText(language, "新对话", "New conversation"))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(HugeIcons.MoreVertical, contentDescription = nativeText(language, "更多操作", "More actions"))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(nativeText(language, "搜索当前对话", "Search this conversation")) },
                                leadingIcon = { Icon(HugeIcons.Search01, null, Modifier.size(18.dp)) },
                                onClick = { menuExpanded = false; onSearch() },
                            )
                            DropdownMenuItem(
                                text = { Text(nativeText(language, "工作面板", "Work panel")) },
                                leadingIcon = { Icon(HugeIcons.LeftToRightListBullet, null, Modifier.size(18.dp)) },
                                onClick = { menuExpanded = false; onOpenWorkPanel() },
                            )
                            DropdownMenuItem(
                                text = { Text(nativeText(language, "导出对话", "Export conversation")) },
                                leadingIcon = { Icon(HugeIcons.Share08, null, Modifier.size(18.dp)) },
                                enabled = canExport,
                                onClick = { menuExpanded = false; onExport() },
                            )
                        }
                    }
                },
            )
        },
    ) { measurables, constraints ->
        val topBarPlaceable = measurables[1].measure(constraints)
        val materialHeightPx = if (useProgressiveGlass) {
            glassConfig.maskHeightDp.dp.roundToPx().coerceAtLeast(topBarPlaceable.height)
        } else 0
        val materialPlaceable = measurables[0].measure(
            Constraints.fixed(topBarPlaceable.width, materialHeightPx),
        )
        layout(topBarPlaceable.width, maxOf(topBarPlaceable.height, materialHeightPx)) {
            // The material is measured at its real height so the Backdrop layer is not clipped.
            // Placing it first keeps title and actions crisp above the progressive glass.
            materialPlaceable.placeRelative(0, 0)
            topBarPlaceable.placeRelative(0, 0)
        }
    }
}

@Composable
internal fun AssistantBackdrop() {
    FcodeChatBackdrop(Modifier.fillMaxSize())
}

@Composable
internal fun RikkaEmptyState(
    status: String,
    ready: Boolean,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalNativeLanguage.current
    val suggestions = remember(language) {
        listOf(
            nativeText(language, "理解项目", "Understand project") to
                nativeText(language, "请分析当前项目的目录结构和核心逻辑。", "Analyze the current project structure and core logic."),
            nativeText(language, "检查问题", "Find problems") to
                nativeText(language, "请检查当前项目中可能的 bug 和异常边界。", "Check the current project for bugs and edge cases."),
            nativeText(language, "继续开发", "Continue development") to
                nativeText(language, "请检查 Git 变更和项目状态，然后建议下一步。", "Review Git changes and suggest the next step."),
        )
    }
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Keep the empty route as content over the chat backdrop, not as a second
        // opaque card that hides the wallpaper and makes the page feel modal.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        HugeIcons.Sparkles,
                        contentDescription = null,
                        modifier = Modifier.size(27.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                nativeText(language, "今天想做什么？", "What would you like to build?"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (status.isNotBlank()) Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { (label, prompt) ->
                    Surface(
                        modifier = Modifier.fcodePressClickable(
                            enabled = ready,
                            onClickLabel = label,
                        ) { onSuggestion(prompt) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SendMessageFlightOverlay(
    motion: PendingSendMotion,
    rootBounds: Rect?,
    wallpaperBackdrop: Backdrop?,
    onFinished: () -> Unit,
) {
    val root = rootBounds ?: return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val source = motion.sourceBounds.translate(Offset(-root.left, -root.top))
    val targetWindow = motion.targetBounds
    val textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp, letterSpacing = 0.1.sp)

    if (targetWindow == null) {
        Text(
            motion.text,
            modifier = Modifier
                .offset { IntOffset(source.left.roundToInt(), source.top.roundToInt()) }
                .width(with(density) { source.width.toDp() })
                .zIndex(20f)
                .clearAndSetSemantics { },
            color = MaterialTheme.colorScheme.onSurface,
            style = textStyle,
            maxLines = 5,
            overflow = TextOverflow.Clip,
        )
        return
    }

    val target = targetWindow.translate(Offset(-root.left, -root.top))
    val progress = remember(motion.token) { Animatable(0f) }
    LaunchedEffect(motion.token, targetWindow) {
        progress.snapTo(0f)
        withFrameNanos { }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = 430f,
                visibilityThreshold = 0.001f,
            ),
        )
        onFinished()
    }

    val p = progress.value
    val horizontalPaddingPx = with(density) { 16.dp.toPx() }
    val verticalPaddingPx = with(density) { 12.dp.toPx() }
    val startX = source.left - horizontalPaddingPx
    val startY = source.top - verticalPaddingPx
    val x = startX + (target.left - startX) * p
    val y = startY + (target.top - startY) * p
    val backgroundAlpha = ((p - 0.10f) / 0.42f).coerceIn(0f, 1f)
    val landingPhase = ((p.coerceIn(0f, 1f) - 0.70f) / 0.30f).coerceIn(0f, 1f)
    val landingScale = 1f + 0.038f * sin(landingPhase * Math.PI.toFloat())
    val appearanceRevision = LocalFcodeAppearanceRevision.current
    val bubbleContext = LocalContext.current
    val bubbleConfig = remember(appearanceRevision, bubbleContext) { readUserBubbleLiquidGlassConfig(bubbleContext) }
    val isLightTheme = rememberIsLightTheme()
    val useLiquidGlass = LocalFcodeInterfaceStyle.current == FcodeInterfaceStyle.LIQUID_GLASS &&
        bubbleConfig.enabled && liquidGlassSupported && wallpaperBackdrop != null
    val cornerRadius = if (useLiquidGlass) bubbleConfig.spec.cornerRadiusDp.dp else 22.dp
    val shape = RoundedCornerShape(cornerRadius, cornerRadius, 6.dp, cornerRadius)
    val tint = (if (isLightTheme) Color.White else Color.Black).copy(alpha = bubbleConfig.tintAlpha)

    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(
                width = with(density) { target.width.toDp() },
                height = with(density) { target.height.toDp() },
            )
            .graphicsLayer {
                scaleX = landingScale
                scaleY = landingScale
                transformOrigin = TransformOrigin(1f, 1f)
            }
            .zIndex(20f)
            .clearAndSetSemantics { },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backgroundAlpha }
                .then(
                    if (useLiquidGlass && wallpaperBackdrop != null) Modifier.drawBackdrop(
                        backdrop = wallpaperBackdrop,
                        shape = { shape },
                        effects = { applyLiquidGlassEffects(bubbleConfig.spec, isLightTheme) },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(tint) },
                    ) else Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
                ),
        )
        Text(
            motion.text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = if (useLiquidGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer,
            style = textStyle,
            maxLines = 5,
            overflow = TextOverflow.Clip,
        )
    }
}

