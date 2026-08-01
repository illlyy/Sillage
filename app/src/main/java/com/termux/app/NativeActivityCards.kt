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
internal fun RikkaActivityMessage(message: NativeChatMessage, state: NativeChatState, onLoadSubagentHistory: (String) -> Unit) {
    val language = LocalNativeLanguage.current
    val openSubagentDrawer = LocalOpenSubagentDrawer.current
    val text = message.content
    if (isNativePlanActivity(text)) {
        NativePlanActivityMessage(
            message = message,
            planJson = state.planJson,
            planExplanation = state.planExplanation,
        ) { planText, streaming ->
            if (streaming) {
                StreamingResponseText(message.id, planText, true, message.revealStartedAt, false)
            } else {
                DeferredHistoricalRichText(planText)
            }
        }
        return
    }
    if (text.startsWith(NATIVE_COMPACTION_PREFIX)) {
        NativeCompactionDivider(
            item = NativeHistoryAdapter.decodeCompaction(text, state.currentThreadId),
            messageId = message.id,
        )
        return
    }
    if (text.startsWith("NOTICE|")) {
        val legacyCompaction = NativeHistoryAdapter.decodeLegacyNotice(text, state.currentThreadId)
        if (legacyCompaction != null) {
            NativeCompactionDivider(legacyCompaction, message.id)
            return
        }
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
    val payload = remember(text) {
        if (text.startsWith("PROCESS2|") || text.startsWith("PROCESS|")) runCatching {
            JSONObject(String(Base64.decode(text.substringAfter('|'), Base64.DEFAULT), Charsets.UTF_8))
        }.getOrNull() else null
    }
    if (payload != null) {
        // Both current PROCESS2 and legacy PROCESS snapshots use the same domain group. This
        // keeps history in lockstep with the live renderer instead of regressing to one capsule
        // per command after a restart.
        val group = remember(message.id, state.currentThreadId, text) {
            NativeHistoryAdapter.processGroup(message.id, state.currentThreadId, payload, message.revealStartedAt)
        }
        val messageSnapshot = state.messages.toList()
        val liveSubagentSnapshot = state.liveSubagents.toList()
        val subagentCandidates = remember(messageSnapshot, liveSubagentSnapshot) {
            collectAllSubagentItems(messageSnapshot, liveSubagentSnapshot)
        }
        NativeActivityGroupRenderer(
            group = group,
            onSubagentClick = { visual ->
                val anchor = subagentDrawerAnchor(visual, subagentCandidates)
                openSubagentDrawer(anchor)
                subagentThreadId(anchor).takeIf { it.isNotBlank() }?.let(onLoadSubagentHistory)
            },
            onSubagentOverflowClick = { visuals ->
                val first = visuals.firstOrNull()
                val anchor = first?.let { subagentDrawerAnchor(it, subagentCandidates) }
                    ?: JSONObject().put("type", "subAgentActivity")
                anchor.put("_openOverview", true)
                openSubagentDrawer(anchor)
            },
        )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fcodePressClickable(
                    onClickLabel = if (expanded) {
                        nativeText(language, "收起历史过程", "Collapse historical process")
                    } else {
                        nativeText(language, "展开历史过程", "Expand historical process")
                    },
                ) { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
        val historicalFileChangeItems = if (tools == null) emptyList() else (0 until tools.length()).mapNotNull { toolIndex ->
            runCatching { JSONObject(tools.optString(toolIndex)) }.getOrNull()?.takeIf { it.optString("type") == "fileChange" }
        }
        if (historicalFileChangeItems.isNotEmpty()) FileDiffCard(historicalFileChangeItems, state.projectPath)
        val historicalImageItems = if (tools == null) emptyList() else (0 until tools.length()).mapNotNull { toolIndex ->
            runCatching { JSONObject(tools.optString(toolIndex)) }.getOrNull()?.takeIf { isImageToolItem(it.optString("type")) }
        }
        if (historicalImageItems.isNotEmpty()) ImageGroupCard(historicalImageItems)
        if (tools != null) for (index in 0 until tools.length()) {
            val item = runCatching { JSONObject(tools.optString(index)) }.getOrNull() ?: continue
            val type = item.optString("type")
            if (type == "fileChange") continue
            if (isImageToolItem(type)) continue
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
internal fun CommandExecutionCard(item: JSONObject, running: Boolean = false, liveOutput: String = "", compact: Boolean = false) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        enabled = hasDetails,
                        onClickLabel = if (expanded) {
                            nativeText(language, "收起命令详情", "Collapse command details")
                        } else {
                            nativeText(language, "展开命令详情", "Expand command details")
                        },
                    ) { expanded = !expanded }
                    .padding(horizontal = if (compact) 8.dp else 11.dp, vertical = rowVerticalPadding),
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

internal fun formatCommandOutputChars(chars: Int, language: String): String = when {
    chars >= 1_000_000 -> "%.1fM".format(chars / 1_000_000.0)
    chars >= 1_000 -> "%.1fK".format(chars / 1_000.0)
    else -> nativeText(language, "$chars \u5b57\u7b26", "$chars chars")
}

@Composable
internal fun CommandStreamSection(title: String, value: String, error: Boolean) {
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

internal enum class CommandViewType { DIRECTORY, FILE, SEARCH, TERMINAL }

internal fun commandViewType(content: String): CommandViewType {
    val command = content.lineSequence().firstOrNull().orEmpty().removePrefix("$ ").trim()
    return when {
        Regex("(^|\\s|[/\"'`])(ls|tree|find|fd)(\\s|$)").containsMatchIn(command) || command.contains("Get-ChildItem") -> CommandViewType.DIRECTORY
        Regex("(^|\\s|[/\"'`])(cat|head|tail|sed)(\\s|$)").containsMatchIn(command) || command.contains("Get-Content") -> CommandViewType.FILE
        Regex("(^|\\s|[/\"'`])(rg|grep|findstr)(\\s|$)").containsMatchIn(command) || command.contains("Select-String") -> CommandViewType.SEARCH
        else -> CommandViewType.TERMINAL
    }
}

@Composable
internal fun SmartCommandCard(content: String) {
    when (commandViewType(content)) {
        CommandViewType.DIRECTORY -> DirectoryOutputCard(content)
        CommandViewType.FILE -> FileOutputCard(content)
        CommandViewType.SEARCH -> SearchOutputCard(content)
        CommandViewType.TERMINAL -> ToolTextCard("命令执行", content, false)
    }
}

@Composable
internal fun DirectoryOutputCard(content: String) {
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) nativeText(language, "\u6536\u8d77\u76ee\u5f55\u5185\u5bb9", "Collapse directory contents")
                        else nativeText(language, "\u5c55\u5f00\u76ee\u5f55\u5185\u5bb9", "Expand directory contents"),
                    ) { expanded = !expanded }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
internal fun FileOutputCard(content: String) {
    val command = content.lineSequence().firstOrNull().orEmpty().removePrefix("$ ")
    val name = command.trim().split(Regex("\\s+")).lastOrNull().orEmpty().substringAfterLast('/')
    ToolTextCard(name.ifBlank { "文件内容" }, content.lines().drop(1).filterNot { it.startsWith("exit ") }.joinToString("\n"), false)
}

@Composable
internal fun SearchOutputCard(content: String) {
    val results = content.lines().drop(1).filter { it.isNotBlank() && !it.startsWith("exit ") }
    val grouped = results.groupBy { line ->
        val match = Regex("^(.+?):(\\d+):(.*)$").find(line)
        match?.groupValues?.get(1) ?: "搜索输出"
    }
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) nativeText(language, "\u6536\u8d77\u641c\u7d22\u7ed3\u679c", "Collapse search results")
                        else nativeText(language, "\u5c55\u5f00\u641c\u7d22\u7ed3\u679c", "Expand search results"),
                    ) { expanded = !expanded }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
internal fun ToolTextCard(title: String, detail: String, diff: Boolean, payloadRef: String = "") {
    val language = LocalNativeLanguage.current
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) nativeText(language, "\u6536\u8d77\u5de5\u5177\u8be6\u60c5", "Collapse tool details")
                        else nativeText(language, "\u5c55\u5f00\u5de5\u5177\u8be6\u60c5", "Expand tool details"),
                    ) { expanded = !expanded }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
internal fun DiffText(diff: String) {
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

/**
 * Expandable capsule that renders per-file unified diffs for one or more `fileChange` tool items.
 * The heavy diff payload lives in [NativeLargePayloadStore]; it is loaded only when the capsule is
 * expanded. A lightweight `fileSummary` (path + operation) keeps the collapsed header correct.
 */
@Composable
internal fun FileDiffCard(items: List<JSONObject>, projectPath: String = "") {
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    val itemsKey = items.map { it.toString() }
    val summaryFiles = remember(itemsKey) { NativeFileChangeParser.summarize(items) }
    val payloadRefs = remember(itemsKey) {
        items.mapNotNull { it.optString(NativeLargePayloadStore.PAYLOAD_REF).takeIf(String::isNotBlank) }
    }
    var loadedEntries by remember(itemsKey) { mutableStateOf<List<NativeFileChangeEntry>?>(null) }
    LaunchedEffect(expanded, payloadRefs) {
        if (expanded && loadedEntries == null && payloadRefs.isNotEmpty()) {
            loadedEntries = withContext(Dispatchers.Default) {
                payloadRefs.flatMap { ref ->
                    NativeLargePayloadStore.get(ref)?.let(NativeFileChangeParser::parsePayload).orEmpty()
                }
            }
        } else if (!expanded && loadedEntries != null) {
            loadedEntries = null
        }
    }
    val files = remember(summaryFiles, loadedEntries) {
        NativeFileChangeParser.mergeForDisplay(summaryFiles, loadedEntries)
    }
    if (files.isEmpty()) return
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = 0.88f, stiffness = 320f),
        label = "fileDiffArrow",
    )
    Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fcodePressClickable(
                    onClickLabel = if (expanded) nativeText(language, "\u6536\u8d77\u6587\u4ef6\u53d8\u66f4", "Collapse file changes")
                    else nativeText(language, "\u5c55\u5f00\u6587\u4ef6\u53d8\u66f4", "Expand file changes"),
                ) { expanded = !expanded },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Files02, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text(
                    nativeText(language, "\u4fee\u6539\u4e86 ${files.size} \u4e2a\u6587\u4ef6", "${files.size} files changed"),
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
                Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    files.forEach { file -> FileDiffRow(file, projectPath) }
                }
            }
        }
    }
}

@Composable
internal fun FileDiffRow(file: NativeFileChangeEntry, projectPath: String) {
    val language = LocalNativeLanguage.current
    var expanded by remember(file.path) { mutableStateOf(false) }
    var viewFilePath by remember(file.path) { mutableStateOf<String?>(null) }
    val resolvedPath = remember(file.path, projectPath) { resolveFilePath(file.path, projectPath) }
    val operation = when (file.operation) {
        "add" -> nativeText(language, "\u65b0\u5efa", "Added")
        "delete" -> nativeText(language, "\u5220\u9664", "Deleted")
        "move" -> nativeText(language, "\u79fb\u52a8", "Moved")
        else -> nativeText(language, "\u7f16\u8f91", "Edited")
    }
    val operationColor = when (file.operation) {
        "add" -> Color(0xFF175C2C)
        "delete" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val rowArrow by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = 0.88f, stiffness = 320f),
        label = "fileDiffRowArrow",
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fcodePressClickable(
                    onClickLabel = if (expanded) nativeText(language, "\u6536\u8d77\u6587\u4ef6 diff", "Collapse file diff")
                    else nativeText(language, "\u5c55\u5f00\u6587\u4ef6 diff", "Expand file diff"),
                ) { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HugeIcons.Files02, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                if (file.path != file.fileName) Text(file.path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            if (resolvedPath != null) IconButton(onClick = { viewFilePath = resolvedPath }, modifier = Modifier.size(30.dp)) {
                Icon(HugeIcons.Search01, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(operation, style = MaterialTheme.typography.labelSmall, color = operationColor, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(5.dp))
            Icon(HugeIcons.ArrowDown01, null, Modifier.size(13.dp).graphicsLayer { rotationZ = rowArrow }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        QElasticExpand(expanded) {
            SafeExpandableViewport(maxHeight = 360.dp) {
                when {
                    file.hasDiff -> DiffText(file.unifiedDiff)
                    file.fullContent.isNotBlank() -> FileContentViewer(file.fullContent)
                    else -> Text(
                        nativeText(language, "\u5c55\u5f00\u540e\u52a0\u8f7d diff", "Expand to load diff"),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        viewFilePath?.let { path ->
            FileViewDialog(filePath = path, fileName = file.fileName, onDismiss = { viewFilePath = null })
        }
    }
}

/** Monospace file content viewer with a line-number gutter and progressive loading. */
@Composable
internal fun FileContentViewer(content: String) {
    val language = LocalNativeLanguage.current
    var lineLimit by remember(content) { mutableIntStateOf(300) }
    val lines = remember(content, lineLimit) { content.split("\n").take(lineLimit + 1) }
    val hasMore = lines.size > lineLimit
    val gutterWidth = remember(lines.size) { lines.size.coerceAtLeast(1).toString().length.coerceAtLeast(2) }
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            lines.take(lineLimit).forEachIndexed { index, line ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(
                        (index + 1).toString().padStart(gutterWidth),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        line.ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (hasMore) {
                TextButton(onClick = { lineLimit += 300 }) {
                    Text(nativeText(language, "\u52a0\u8f7d\u66f4\u591a", "Load more"))
                }
            }
        }
    }
}

/** Resolves a changed-file path to an existing disk file, joining [projectPath] when relative. */
internal fun resolveFilePath(path: String, projectPath: String): String? {
    val trimmed = path.trim().trim('"', '\'', '`')
    if (trimmed.isBlank()) return null
    val direct = java.io.File(trimmed)
    if (direct.isAbsolute && direct.exists()) return direct.absolutePath
    if (projectPath.isNotBlank()) {
        val joined = java.io.File(projectPath, trimmed)
        if (joined.exists()) return joined.absolutePath
    }
    if (direct.exists()) return direct.absolutePath
    return null
}

/** Full-screen viewer that reads a file from disk on a background thread and shows its content. */
@Composable
internal fun FileViewDialog(filePath: String, fileName: String, onDismiss: () -> Unit) {
    val language = LocalNativeLanguage.current
    var content by remember(filePath) { mutableStateOf<String?>(null) }
    var failed by remember(filePath) { mutableStateOf(false) }
    LaunchedEffect(filePath) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { java.io.File(filePath).readText(Charsets.UTF_8) }.getOrNull()
        }
        if (loaded == null) failed = true else content = loaded
    }
    val sizeLabel = remember(filePath) { formatFileSize(java.io.File(filePath).length()) }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(fileName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sizeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    when {
                        failed -> Text(
                            nativeText(language, "\u65e0\u6cd5\u8bfb\u53d6\u6587\u4ef6", "Cannot read file"),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        content == null -> Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(nativeText(language, "\u6b63\u5728\u8bfb\u53d6\u2026", "Loading\u2026"), style = MaterialTheme.typography.bodySmall)
                        }
                        else -> SafeExpandableViewport(maxHeight = 420.dp) { FileContentViewer(content!!) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "\u5173\u95ed", "Close")) } },
    )
}

/** Inline preview of one or more model-produced images; multiple images in a turn are grouped. */
@Composable
internal fun ImageGroupCard(items: List<JSONObject>) {
    val language = LocalNativeLanguage.current
    val imagesKey = items.map { it.toString() }
    val images = remember(imagesKey) {
        items.mapNotNull { item ->
            listOf("path", "imagePath", "url", "file")
                .firstNotNullOfOrNull { key -> item.optString(key).takeIf(String::isNotBlank) }
        }.distinct()
    }
    if (images.isEmpty()) return
    var previewPath by remember { mutableStateOf<String?>(null) }
    val single = images.size == 1
    Surface(
        modifier = Modifier.widthIn(max = if (single) 320.dp else 300.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                images.forEach { path ->
                    AttachmentThumbnail(
                        path,
                        (if (single) Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 240.dp) else Modifier.size(88.dp))
                            .clip(RoundedCornerShape(11.dp))
                            .fcodePressClickable(
                                onClickLabel = nativeText(language, "\u9884\u89c8\u56fe\u7247", "Preview image"),
                            ) { previewPath = path },
                        maxEdge = if (single) 960 else 320,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Image02, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (single) java.io.File(images.first()).name.ifBlank { nativeText(language, "\u56fe\u7247", "Image") }
                    else nativeText(language, "${images.size} \u5f20\u56fe\u7247", "${images.size} images"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    previewPath?.let { path ->
        AttachmentPreviewDialog(
            attachment = NativeAttachment(java.io.File(path).name.ifBlank { "image" }, path, true),
            onDismiss = { previewPath = null },
        )
    }
}

