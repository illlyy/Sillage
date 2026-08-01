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
internal fun CollabAgentCapsule(
    item: JSONObject,
    state: NativeChatState,
    onLoadHistory: (String) -> Unit,
    visual: NativeSubagentVisual? = null,
) {
    // Details are hosted once at screen level. Keeping a Dialog and navigation state inside
    // every historical message retained a large amount of dormant composition state.
    val openDrawer = LocalOpenSubagentDrawer.current
    val name = subagentName(item)
    val status = subagentStatusLabel(resolvedSubagentStatus(state, item), LocalNativeLanguage.current)
    val resolvedVisual = visual ?: remember(item) {
        NativeSubagentVisualFactory.create(
            agentThreadId = subagentThreadId(item),
            callId = jsonText(item, "callId", "id"),
            name = name,
            status = resolvedSubagentStatus(state, item),
            aliases = subagentAliases(item),
        )
    }
    val isLight = rememberIsLightTheme()
    val chipColor = Color(if (isLight) resolvedVisual.lightColorArgb else resolvedVisual.darkColorArgb).copy(alpha = .82f)
    val iconTint = if (isLight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier
            .padding(top = 8.dp)
            .fcodePressClickable(
                onClickLabel = nativeText(LocalNativeLanguage.current, "查看子代理详情", "Open subagent details"),
            ) {
                openDrawer(item)
                val thread = subagentThreadId(item)
                if (thread.isNotBlank()) onLoadHistory(thread)
            },
        shape = CircleShape,
        color = chipColor,
        contentColor = iconTint,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(15.dp), contentAlignment = Alignment.Center) {
                Surface(
                    Modifier.size(8.dp),
                    shape = CircleShape,
                    color = when (resolvedVisual.status) {
                        NativeSubagentStatus.WORKING -> MaterialTheme.colorScheme.primary
                        NativeSubagentStatus.WAITING -> MaterialTheme.colorScheme.outline
                        NativeSubagentStatus.DONE -> Color(0xFF5E8B68)
                        NativeSubagentStatus.FAILED -> MaterialTheme.colorScheme.error
                    },
                ) {}
            }
            Spacer(Modifier.width(7.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = iconTint)
            Spacer(Modifier.width(8.dp))
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.68f))
            Spacer(Modifier.width(5.dp))
            Icon(HugeIcons.ArrowRight01, null, Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubagentDrawer(
    item: JSONObject,
    state: NativeChatState,
    onLoadHistory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialId = item.optBoolean("_openOverview", false).let { overview ->
        if (overview) null else subagentKey(item)
    }
    val messageSnapshot = state.messages.toList()
    val liveSubagentSnapshot = state.liveSubagents.toList()
    val itemSnapshot = item.toString()
    val agents = remember(messageSnapshot, liveSubagentSnapshot, itemSnapshot) {
        collectSubagentItems(messageSnapshot, liveSubagentSnapshot, item)
    }
    var selectedId by remember(initialId) { mutableStateOf<String?>(initialId) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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

internal fun resolvedSubagentStatus(state: NativeChatState, item: JSONObject): String {
    val thread = subagentThreadId(item)
    return state.subagentStatuses[thread]?.let(::normalizedSubagentStatus)
        ?: normalizedSubagentStatus(jsonText(item, "status"))
}

internal fun subagentStatusLabel(status: String, language: String): String = when (status) {
    "working" -> nativeText(language, "\u5904\u7406\u4e2d", "Working")
    "waiting" -> nativeText(language, "\u7b49\u5f85\u4e2d", "Waiting")
    "failed" -> nativeText(language, "\u5931\u8d25", "Failed")
    "stopped" -> nativeText(language, "\u5df2\u505c\u6b62", "Stopped")
    else -> nativeText(language, "\u5b8c\u6210", "Completed")
}

@Composable
internal fun SubagentOverview(state: NativeChatState, agents: List<JSONObject>, onSelect: (JSONObject) -> Unit) {
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
internal fun SubagentOverviewRow(state: NativeChatState, agent: JSONObject, onSelect: (JSONObject) -> Unit) {
    val language = LocalNativeLanguage.current
    val task = jsonText(agent, "task", "prompt", "input", "message")
    val status = resolvedSubagentStatus(state, agent)
    val statusLabel = subagentStatusLabel(status, language)
    val statusColor = when (status) {
        "working" -> MaterialTheme.colorScheme.primary
        "waiting" -> MaterialTheme.colorScheme.tertiary
        "failed", "stopped" -> MaterialTheme.colorScheme.error
        else -> Color(0xFF5E8B68)
    }
    Surface(
        Modifier
            .fillMaxWidth()
            .fcodePressClickable(
                onClickLabel = nativeText(language, "查看子代理详情", "View agent details"),
            ) { onSelect(agent) },
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

internal data class LoadedSubagentPage(
    val messages: List<JSONObject>,
    val startIndex: Int,
    val totalCount: Int,
)

internal fun loadSubagentPage(reference: String, limit: Int): LoadedSubagentPage {
    if (reference.isBlank()) return LoadedSubagentPage(emptyList(), 0, 0)
    val page = NativeLargePayloadStore.subagentPage(reference, Int.MAX_VALUE, limit)
    val messages = page.messages.mapNotNull { raw -> runCatching { JSONObject(raw) }.getOrNull() }
    return LoadedSubagentPage(messages, page.startIndex, page.totalCount)
}

@Composable
internal fun SubagentDetail(
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
internal fun SubagentFailureSummary() {
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
internal fun SubagentHistoryLoading(label: String = "\u6b63\u5728\u52a0\u8f7d\u5b50\u4ee3\u7406\u5bf9\u8bdd") {
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
internal fun SubagentConversationMessage(message: JSONObject, agentName: String) {
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
internal fun SubagentActivityView(content: String) {
    val payload = remember(content) {
        if (content.startsWith("PROCESS2|")) runCatching {
            JSONObject(String(Base64.decode(content.substringAfter('|'), Base64.DEFAULT), Charsets.UTF_8))
        }.getOrNull() else null
    }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) "\u6536\u8d77\u5b50\u4ee3\u7406\u6d3b\u52a8" else "\u5c55\u5f00\u5b50\u4ee3\u7406\u6d3b\u52a8",
                    ) { expanded = !expanded }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
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
internal fun AgentTimelineSection(title: String, value: String, monospace: Boolean = false) {
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

