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
internal fun WorkPanelDialog(
    state: NativeChatState,
    onLoadSubagentHistory: (String) -> Unit,
    onEditGoal: () -> Unit,
    onClearGoal: () -> Unit,
    onExecutePlan: () -> Unit,
    onGitAction: (String, String) -> Unit,
    onRequestGitPush: () -> Unit,
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
    // Subagent details are hosted in the shared bottom-sheet drawer (SubagentDrawer), never
    // inside this side panel, so opening one closes the work panel and pops the drawer instead.
    val openDrawer = LocalOpenSubagentDrawer.current
    // SnapshotStateList.toList() reuses its immutable backing list until content changes.
    // These indexes must not follow the global stream revision: answer token batches do not
    // change process history and should never decode the whole conversation again.
    val messageSnapshot = state.messages.toList()
    val liveSubagentSnapshot = state.liveSubagents.toList()
    val toolDetailsSnapshot = state.toolDetails.toList()
    val agents = remember(messageSnapshot, liveSubagentSnapshot) {
        collectAllSubagentItems(messageSnapshot, liveSubagentSnapshot)
    }
    val changes = remember(messageSnapshot, toolDetailsSnapshot) {
        collectAllFileChangeItems(messageSnapshot, toolDetailsSnapshot)
    }
    val modelChangedFiles = remember(changes) { extractChangedFiles(changes) }
    val gitEntries = remember(state.gitSnapshot) { parseGitEntries(state.gitSnapshot) }
    val changedFiles = remember(modelChangedFiles, state.gitSnapshot) { mergeChangedFiles(modelChangedFiles, gitEntries) }
    val checkpoints = remember(messageSnapshot, state.phase) { NativeCheckpointModel.build(messageSnapshot, state.phase) }
    val agentThreads = remember(agents) { agents.map(::subagentThreadId).filter { it.isNotBlank() } }
    val close: () -> Unit = { entered = false }
    LaunchedEffect(tab, agentThreads) {
        if (tab == "agents") agentThreads.forEach(onLoadSubagentHistory)
        if ((tab == "changes" || tab == "git") && !state.gitBusy) onGitAction("refresh", "")
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
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = scrim))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = close,
                    ),
            )
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
                            Spacer(Modifier.width(48.dp))
                            Text(
                                "\u5de5\u4f5c\u9762\u677f",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(onClick = close) { Icon(HugeIcons.Cancel01, "\u5173\u95ed") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        WorkPanelTabs(
                            tab = tab,
                            planCount = parseNativePlanItems(state.planJson).size,
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
                                        // Replace in-panel subagent details with the shared drawer.
                                        close()
                                        openDrawer(agent)
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
                                "git" -> WorkGitView(state, onGitAction, onRequestGitPush)
                                else -> WorkPlanView(state.planJson, state.planExplanation, state.activeGoalObjective, state.ready && !state.phase.active, onEditGoal, onClearGoal, onExecutePlan)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun workPanelTabIndex(tab: String): Int = listOf("plan", "checkpoints", "snapshots", "worktrees", "agents", "changes", "git").indexOf(tab).coerceAtLeast(0)

@Composable
internal fun WorkPanelTabs(tab: String, planCount: Int, checkpointCount: Int, snapshotCount: Int, worktreeCount: Int, agentCount: Int, changeCount: Int, gitCount: Int, onTab: (String) -> Unit) {
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
                modifier = Modifier.fcodePressClickable(
                    onClickLabel = nativeText(language, "切换到$label", "Show $label"),
                ) { onTab(id) },
                shape = CircleShape,
                color = if (tab == id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (tab == id) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) { Text(label, Modifier.padding(horizontal = 15.dp, vertical = 9.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, fontWeight = if (tab == id) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
internal fun WorktreesView(
    state: NativeChatState,
    onAction: (String, String) -> Unit,
    onRequestMerge: (String) -> Unit,
    onRequestRemove: (String) -> Unit,
) {
    val language = LocalNativeLanguage.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var branchName by remember(state.currentThreadId, state.projectPath) { mutableStateOf("") }
    val preview = remember(state.worktreeMergePreview) { runCatching { JSONObject(state.worktreeMergePreview) }.getOrNull() }
    val handoff = remember(state.worktreePrHandoff) { runCatching { JSONObject(state.worktreePrHandoff) }.getOrNull() }
    val currentPath = state.projectPath.trimEnd('/', '\\')
    val mainPath = state.worktrees.firstOrNull()?.path.orEmpty().trimEnd('/', '\\')
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (handoff != null) {
            item(key = "worktree-pr-handoff") {
                val markdown = handoff.optString("markdown")
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onAction("clearPrHandoff", "") }) { Icon(HugeIcons.ArrowRight01, null, Modifier.graphicsLayer { rotationZ = 180f }) }
                            Column(Modifier.weight(1f)) {
                                Text("${handoff.optString("sourceBranch")} → ${handoff.optString("targetBranch")}", fontWeight = FontWeight.SemiBold)
                                Text(nativeText(language, "Pull Request 交接信息", "Pull Request handoff"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (handoff.optBoolean("dirty")) Text(
                            nativeText(language, "该隔离任务仍有未提交变更，交接信息只包含已提交内容。", "This isolated task has uncommitted changes; the handoff only includes committed work."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        SelectionContainer { Text(markdown.take(32_000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(markdown)) }) { Text(nativeText(language, "复制", "Copy")) }
                            TextButton(onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, markdown)
                                context.startActivity(android.content.Intent.createChooser(intent, nativeText(language, "分享 PR 交接", "Share PR handoff")))
                            }) { Text(nativeText(language, "分享", "Share")) }
                        }
                    }
                }
            }
        } else if (preview != null) {
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
                val current = normalizedPath == currentPath || currentPath.startsWith(normalizedPath + "/") || currentPath.startsWith(normalizedPath + "\\")
                val main = index == 0 || normalizedPath == mainPath
                val referenced = state.conversations.any {
                    val conversationPath = it.projectPath.trimEnd('/', '\\')
                    conversationPath == normalizedPath || conversationPath.startsWith(normalizedPath + "/") || conversationPath.startsWith(normalizedPath + "\\")
                }
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
                            if (!main && !worktree.detached) TextButton(enabled = !state.worktreeBusy, onClick = { onAction("preparePr", worktree.path) }) { Text(nativeText(language, "PR 交接", "PR handoff")) }
                            if (!main && !worktree.detached) TextButton(enabled = !state.worktreeBusy && !worktree.dirty, onClick = { onAction("previewMerge", worktree.path) }) { Text(nativeText(language, "\u9884\u89c8\u5408\u5e76", "Preview merge")) }
                            if (!main && !current) TextButton(enabled = !state.worktreeBusy && !worktree.dirty && !worktree.locked && !referenced, onClick = { onRequestRemove(worktree.path) }) { Text(nativeText(language, "\u79fb\u9664", "Remove")) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
internal fun WorkSnapshotsView(
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
internal fun SnapshotDiffCard(
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) nativeText(language, "\u6536\u8d77\u5dee\u5f02", "Collapse diff")
                        else nativeText(language, "\u5c55\u5f00\u5dee\u5f02", "Expand diff"),
                    ) { expanded = !expanded }
                    .padding(start = 13.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
internal fun WorkCheckpointsView(
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

internal fun parseGitEntries(raw: String): List<JSONObject> = runCatching {
    val array = JSONObject(raw).optJSONArray("entries") ?: JSONArray()
    buildList { for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add) }
}.getOrDefault(emptyList())

internal fun mergeChangedFiles(modelFiles: List<ChangedFileEntry>, gitEntries: List<JSONObject>): List<ChangedFileEntry> {
    val result = linkedMapOf<String, ChangedFileEntry>()
    modelFiles.forEach { result[it.path] = it }
    gitEntries.forEach { entry ->
        val path = entry.optString("path")
        if (path.isNotBlank()) result[path] = ChangedFileEntry(path, entry.optString("operation", "edit"))
    }
    return result.values.toList()
}

@Composable
internal fun WorkChangesView(
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
internal fun WorkChangeFileCard(
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
                Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) nativeText(language, "\u6536\u8d77\u6587\u4ef6\u53d8\u66f4", "Collapse file changes")
                        else nativeText(language, "\u5c55\u5f00\u6587\u4ef6\u53d8\u66f4", "Expand file changes"),
                    ) { expanded = !expanded }
                    .padding(start = 13.dp, end = 5.dp, top = 10.dp, bottom = 10.dp),
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
internal fun GitDiffText(text: String, modifier: Modifier = Modifier) {
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
internal fun WorkGitView(state: NativeChatState, onGitAction: (String, String) -> Unit, onRequestPush: () -> Unit) {
    val language = LocalNativeLanguage.current
    val snapshot = remember(state.gitSnapshot) { runCatching { JSONObject(state.gitSnapshot) }.getOrNull() }
    val entries = remember(state.gitSnapshot) { jsonObjects(snapshot?.optJSONArray("entries")) }
    val history = remember(state.gitSnapshot) { jsonObjects(snapshot?.optJSONArray("history")) }
    var commitMessage by remember(state.currentThreadId, state.projectPath) { mutableStateOf("") }
    val stagedCount = entries.count { it.optBoolean("staged") }
    val branch = snapshot?.optString("branch").orEmpty()
    val upstream = snapshot?.optString("upstream").orEmpty()
    val remote = snapshot?.optString("remote").orEmpty()
    val remoteUrl = snapshot?.optString("remoteUrl").orEmpty()
    val ahead = snapshot?.optInt("ahead") ?: 0
    val behind = snapshot?.optInt("behind") ?: 0
    val canPush = snapshot?.optBoolean("hasHead") == true && branch.isNotBlank() && remote.isNotBlank()
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
                        Text(branch.ifBlank { nativeText(language, "Git 工作区", "Git workspace") }, fontWeight = FontWeight.SemiBold)
                        Text(state.projectPath.ifBlank { nativeText(language, "未绑定项目", "No project selected") }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (upstream.isNotBlank()) Text("$upstream · ↑$ahead ↓$behind", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        else if (remote.isNotBlank()) Text(nativeText(language, "尚未设置 upstream · 远程 $remote", "No upstream · remote $remote"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.gitBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { onGitAction("refresh", "") }) { Icon(HugeIcons.Refresh03, nativeText(language, "刷新", "Refresh"), Modifier.size(19.dp)) }
                }
            }
        }
        if (snapshot?.optBoolean("available", true) != false && remote.isNotBlank()) item(key = "git-remote") {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(nativeText(language, "远程协作", "Remote collaboration"), fontWeight = FontWeight.SemiBold)
                    Text("$remote · $remoteUrl", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(enabled = !state.gitBusy, onClick = { onGitAction("fetch", "") }) { Text("Fetch") }
                        Button(enabled = canPush && !state.gitBusy, onClick = onRequestPush, shape = RoundedCornerShape(14.dp)) {
                            Text(if (upstream.isBlank()) nativeText(language, "发布分支", "Publish branch") else "Push")
                        }
                    }
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
            WorkPanelInlineEmpty(snapshot.optString("error", nativeText(language, "Git 不可用", "Git unavailable")))
        } else {
            if (!state.gitBusy && entries.isEmpty()) item(key = "git-clean") {
                WorkPanelInlineEmpty(nativeText(language, "工作区干净，没有待提交变更", "Working tree clean"))
            }
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
                        if (entry.optBoolean("unstaged")) TextButton(enabled = !state.gitBusy, onClick = { onGitAction("stage", path) }) { Text(nativeText(language, "暂存", "Stage")) }
                        if (entry.optBoolean("staged")) TextButton(enabled = !state.gitBusy, onClick = { onGitAction("unstage", path) }) { Text(nativeText(language, "取消暂存", "Unstage")) }
                    }
                }
            }
            if (entries.isNotEmpty()) item(key = "git-commit") {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedTextField(
                            value = commitMessage,
                            onValueChange = { commitMessage = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(nativeText(language, "提交说明", "Commit message")) },
                            minLines = 2,
                            maxLines = 4,
                        )
                        Button(
                            onClick = { onGitAction("commit", commitMessage); commitMessage = "" },
                            enabled = stagedCount > 0 && commitMessage.isNotBlank() && !state.gitBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(nativeText(language, "提交 $stagedCount 个已暂存文件", "Commit $stagedCount staged files")) }
                    }
                }
            }
            if (history.isNotEmpty()) item(key = "git-history-title") {
                Text(nativeText(language, "提交历史", "Commit history"), modifier = Modifier.padding(start = 4.dp, top = 8.dp), fontWeight = FontWeight.SemiBold)
            }
            items(history, key = { it.optString("hash") }) { commit ->
                val date = remember(commit.optLong("timestamp")) {
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                        .format(java.util.Date(commit.optLong("timestamp") * 1000L))
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(commit.optString("subject"), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text("${commit.optString("shortHash")} · ${commit.optString("author")} · $date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (commit.optString("refs").isNotBlank()) Text(commit.optString("refs"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

internal fun jsonObjects(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else buildList {
    for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
}

@Composable
internal fun WorkPanelInlineEmpty(text: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(text, Modifier.padding(16.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun WorkPlanView(raw: String, explanation: String, goal: String, executeEnabled: Boolean, onEditGoal: () -> Unit, onClearGoal: () -> Unit, onExecutePlan: () -> Unit) {
    val language = LocalNativeLanguage.current
    val plan = remember(raw) { parseNativePlanItems(raw) }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        enabled = executeEnabled,
                        onClickLabel = nativeText(language, "\u7f16\u8f91\u6d3b\u8dc3\u76ee\u6807", "Edit active goal"),
                    ) { onEditGoal() },
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
internal fun WorkPanelEmpty(title: String, description: String) {
    Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(HugeIcons.LeftToRightListBullet, null, Modifier.padding(14.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(description, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
    }
}

