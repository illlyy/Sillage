@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.termux.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.Zap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
private fun nativeActivitySurfaceAlpha(fallback: Float): Float =
    if (LocalFcodeInterfaceStyle.current == FcodeInterfaceStyle.MATERIAL) {
        LocalFcodeMaterialTransparency.current.activityAlpha
    } else {
        fallback
    }

/**
 * Body inputs captured at collapse time. Rendering the exit from this snapshot keeps the animated
 * height constant while live group mutations (running flips, command output resolution) land, so
 * the shrink completes instead of re-targeting and stranding a full-width empty card.
 */
private data class FrozenTimelineBody(
    val reasoning: String,
    val commands: List<NativeActivityItem>,
    val items: List<NativeToolListItem>,
    val stepCount: Int,
)

/** Unified live/history activity renderer backed only by domain DTOs. */
@Composable
internal fun NativeActivityGroupRenderer(
    group: NativeActivityGroup,
    subagents: List<NativeSubagentVisual> = emptyList(),
    modifier: Modifier = Modifier,
    /** Workspace root, used to resolve relative tool paths (a `Read`/`Edit` argument is often
     *  relative to the CLI's working directory). */
    projectPath: String = "",
    onSubagentClick: (NativeSubagentVisual) -> Unit = {},
    onSubagentOverflowClick: (List<NativeSubagentVisual>) -> Unit = {},
    onLoadSubagentHistory: ((String) -> Unit)? = null,
    enterExpanded: Boolean = false,
    onAutoCollapsed: (() -> Unit)? = null,
) {
    val language = LocalNativeLanguage.current
    val reasoning = group.reasoning.trim()
    val renderedSubagents = remember(group.key, group.items, subagents) {
        if (subagents.isNotEmpty()) subagents
        else group.items.asSequence()
            .filter { it.type == NativeActivityItemType.SUBAGENT }
            .map(NativeSubagentVisualFactory::fromActivityItem)
            .toList()
    }
    Column(
        modifier = modifier.fillMaxWidth().widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val nonCommandItems = group.items.filter {
            it.type != NativeActivityItemType.COMMAND &&
                it.type != NativeActivityItemType.SUBAGENT &&
                it.type != NativeActivityItemType.REASONING
        }
        if (reasoning.isNotBlank() || group.commands.isNotEmpty() || nonCommandItems.isNotEmpty()) {
            NativeActivityTimelineCard(
                group = group,
                reasoning = reasoning,
                nonCommandItems = nonCommandItems,
                projectPath = projectPath,
                onLoadSubagentHistory = onLoadSubagentHistory,
                enterExpanded = enterExpanded,
                onAutoCollapsed = onAutoCollapsed,
            )
        }
        if (renderedSubagents.isNotEmpty()) NativeSubagentVisualFlowRow(
            renderedSubagents,
            onSubagentClick = onSubagentClick,
            onOverflowClick = onSubagentOverflowClick,
        )
        if (reasoning.isBlank() && group.items.isEmpty() && group.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp)
                Spacer(Modifier.width(8.dp))
                Text(nativeText(language, "处理中", "Processing"), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun NativeActivityTimelineCard(
    group: NativeActivityGroup,
    reasoning: String,
    nonCommandItems: List<NativeActivityItem>,
    projectPath: String = "",
    onLoadSubagentHistory: ((String) -> Unit)? = null,
    enterExpanded: Boolean = false,
    onAutoCollapsed: (() -> Unit)? = null,
) {
    val language = LocalNativeLanguage.current
    // Same motion language as the container-transform playground in Developer options
    // (PageTransitionPlayground): the M3 emphasized curve, a long 360 ms body, a restrained 0.94
    // scale for depth, and a fade that recedes to 0.3 instead of vanishing. The old fold ran three
    // different durations (220/260/240) on a plain decelerate curve with no scale at all, which is
    // why it read as "cheap" next to that playground — short, flat, and with no depth cue.
    val motionEasing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
    val foldEnter = tween<IntSize>(durationMillis = 360, easing = motionEasing)
    val foldExit = tween<IntSize>(durationMillis = 300, easing = motionEasing)
    val runningCommands = group.runningCommandCount
    val failedItems = group.failedCount
    val automaticExpansion = group.expandedByDefault ||
        nativeCommandCollectionAutoExpanded(runningCommands, failedItems) ||
        enterExpanded
    var userExpanded by remember(group.key) { mutableStateOf<Boolean?>(null) }
    val expanded = resolveNativeCommandDisclosure(automaticExpansion, userExpanded)
    val bodyVisibility = remember(group.key) {
        MutableTransitionState(automaticExpansion)
    }
    val chromeVisibility = remember(group.key) {
        MutableTransitionState(automaticExpansion)
    }
    val chromeFullyExpanded = chromeVisibility.isIdle && chromeVisibility.currentState
    // A freshly-sealed historical card enters expanded (matching the live panel it replaces) and
    // folds itself back after a beat WITH the exit animation — the live panel used to be disposed
    // and a collapsed sibling popped in, which read as "no animation".
    val pauseFollowForToggle = LocalPauseFollowDuringAnimation.current
    val currentExpanded by rememberUpdatedState(expanded)
    LaunchedEffect(group.key, enterExpanded, group.running) {
        if (enterExpanded && !group.running) {
            delay(1_200)
            if (currentExpanded) {
                userExpanded = false
                pauseFollowForToggle()
                onAutoCollapsed?.invoke()
            }
        }
    }
    SideEffect {
        // Shell and body move together. Staging them (the shell settles, then the body enters)
        // left a ~180 ms gap between the tap and any content appearing, which read as the tap
        // being ignored. The horizontal step is a clip rather than a re-measure, so laying the
        // body out early never squeezes long content into a narrow pill. Collapsing stays in one
        // phase: with a staged collapse the Surface used to linger as a full-width empty block.
        chromeVisibility.targetState = expanded
        bodyVisibility.targetState = expanded
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 360, easing = motionEasing),
        label = "activityTimelineArrow",
    )
    val cardShape = RoundedCornerShape(18.dp)
    val groupedItems = remember(group.key, nonCommandItems) { groupConsecutiveTools(nonCommandItems) }
    val stepCount = (if (reasoning.isNotBlank()) 1 else 0) + group.commands.size + groupedItems.size
    // Body inputs frozen at collapse time so live group mutations (running flips, command output
    // resolution arriving mid-exit) cannot re-target the exit animation — which used to strand the
    // card as a full-width empty rectangle. The header keeps rendering the live group, so status
    // and step count still update while collapsed.
    var frozenBody by remember(group.key) { mutableStateOf<FrozenTimelineBody?>(null) }
    LaunchedEffect(group.key, expanded) {
        frozenBody = if (expanded) null else FrozenTimelineBody(
            reasoning = reasoning,
            commands = group.commands,
            items = groupedItems,
            stepCount = stepCount,
        )
    }
    val statusColor = when {
        failedItems > 0 -> MaterialTheme.colorScheme.error
        group.running || runningCommands > 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val statusLabel = when {
        failedItems > 0 -> nativeText(language, "$failedItems 项失败", "$failedItems failed")
        group.running || runningCommands > 0 -> nativeText(language, "进行中", "Running")
        else -> nativeText(language, "已完成", "Done")
    }
    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = nativeActivitySurfaceAlpha(.84f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .36f)),
    ) {
        Column(Modifier.clipToBounds()) {
            AnimatedVisibility(
                visibleState = chromeVisibility,
                enter = expandHorizontally(foldEnter, expandFrom = Alignment.Start),
                exit = shrinkHorizontally(foldExit, shrinkTowards = Alignment.Start),
            ) {
                // A zero-height, full-width child is the card's width anchor. Because the Surface
                // measures this animated width directly, its background and border shrink with it
                // instead of snapping while only an outer layout bound animates.
                Spacer(Modifier.fillMaxWidth().height(0.dp))
            }
            Row(
                Modifier
                    .then(if (chromeFullyExpanded) Modifier.fillMaxWidth() else Modifier)
                    .fcodePressClickable(
                        onClickLabel = if (expanded) {
                            nativeText(language, "收起思考与执行", "Collapse reasoning and actions")
                        } else {
                            nativeText(language, "展开思考与执行", "Expand reasoning and actions")
                        },
                    ) {
                        pauseFollowForToggle()
                        userExpanded = !resolveNativeCommandDisclosure(
                            automaticExpansion,
                            userExpanded,
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (group.running || runningCommands > 0) {
                    CircularProgressIndicator(
                        Modifier.size(15.dp),
                        strokeWidth = 1.8.dp,
                        color = statusColor,
                    )
                } else {
                    Icon(
                        if (failedItems > 0) HugeIcons.Cancel01 else HugeIcons.Tick02,
                        null,
                        Modifier.size(15.dp),
                        tint = statusColor,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    nativeText(language, "思考与执行", "Reasoning & actions"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    nativeText(language, "$stepCount 个步骤", "$stepCount steps"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (expanded && chromeFullyExpanded) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    HugeIcons.ArrowDown01,
                    null,
                    Modifier.size(15.dp).graphicsLayer { rotationZ = rotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visibleState = bodyVisibility,
                // Container-transform shape: the shell opens on the emphasized curve while the
                // body scales up from 0.94, so the content reads as rising out of the card instead
                // of being revealed. The short delayed fade means the frame of the card is there
                // first, and fading out to 0.3 (not 0) keeps the collapse from looking like a cut.
                enter = scaleIn(initialScale = 0.94f, animationSpec = tween(360, easing = motionEasing)) +
                    fadeIn(tween(170, delayMillis = 60, easing = LinearOutSlowInEasing)) +
                    expandVertically(foldEnter, expandFrom = Alignment.Top) +
                    expandHorizontally(foldEnter, expandFrom = Alignment.Start),
                exit = scaleOut(targetScale = 0.94f, animationSpec = tween(300, easing = motionEasing)) +
                    fadeOut(tween(150, delayMillis = 90), targetAlpha = 0.3f) +
                    shrinkVertically(foldExit, shrinkTowards = Alignment.Top) +
                    shrinkHorizontally(foldExit, shrinkTowards = Alignment.Start),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .38f))
                    Column(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
                    ) {
                        // While collapsed, render the frozen snapshot so live mutations cannot
                        // re-target the exit; the live group feeds the body only when expanded.
                        val body = frozenBody
                        val bodyReasoning = body?.reasoning ?: reasoning
                        val bodyCommands = body?.commands ?: group.commands
                        val bodyItems = body?.items ?: groupedItems
                        val totalSteps = (body?.stepCount ?: stepCount).coerceAtLeast(1)
                        var index = 0
                        if (bodyReasoning.isNotBlank()) {
                            NativeTimelineStep(
                                markerColor = MaterialTheme.colorScheme.primary,
                                isLast = index++ == totalSteps - 1,
                            ) {
                                Text(
                                    nativeText(language, "思考", "Reasoning"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    bodyReasoning,
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        bodyCommands.forEach { command ->
                            val commandColor = when (command.status) {
                                NativeActivityItemStatus.FAILED -> MaterialTheme.colorScheme.error
                                NativeActivityItemStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                NativeActivityItemStatus.WAITING -> MaterialTheme.colorScheme.tertiary
                                NativeActivityItemStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            key(command.id) {
                                NativeTimelineStep(
                                    markerColor = commandColor,
                                    isLast = index++ == totalSteps - 1,
                                ) {
                                    NativeCommandRow(
                                        command = command,
                                        showStatusDot = false,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        bodyItems.forEach { listItem ->
                            val itemColor = when (listItem) {
                                is NativeToolListItem.Item -> itemColorFor(listItem.item)
                                is NativeToolListItem.Group -> listItem.itemColor()
                            }
                            key(listItem.firstItem.id) {
                                NativeTimelineStep(
                                    markerColor = itemColor,
                                    isLast = index++ == totalSteps - 1,
                                ) {
                                    when (listItem) {
                                        is NativeToolListItem.Item -> when (listItem.item.type) {
                                            NativeActivityItemType.SUBAGENT -> FcodeSubagentCard(
                                                item = listItem.item,
                                                onLoadHistory = { thread -> onLoadSubagentHistory?.invoke(thread) },
                                            )
                                            // An edit renders its diff and an image renders a
                                            // thumbnail; both used to collapse into a bare
                                            // "tool call" row with no visible content.
                                            NativeActivityItemType.FILE_CHANGE -> NativeToolFileChangeCard(listItem.item)
                                            NativeActivityItemType.IMAGE -> NativeToolImageCard(listItem.item, projectPath)
                                            else -> NativeToolTimelineContent(listItem.item)
                                        }
                                        is NativeToolListItem.Group -> FcodeToolGroup(listItem)
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
private fun NativeTimelineStep(
    markerColor: Color,
    isLast: Boolean,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(22.dp).fillMaxHeight()) {
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f)),
                )
            }
            Surface(
                modifier = Modifier.size(9.dp).align(Alignment.TopCenter),
                shape = CircleShape,
                color = markerColor,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {}
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 7.dp, bottom = if (isLast) 12.dp else 15.dp),
            content = { content() },
        )
    }
}

@Composable
internal fun NativeToolTimelineContent(item: NativeActivityItem) {
    val language = LocalNativeLanguage.current
    val label = NativeToolConfigs.of(item.type).label(language)
    val detail = item.title.ifBlank { item.text }.trim()
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (detail.isNotBlank()) {
        Text(
            detail,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}



@Composable
private fun NativeCommandRow(
    command: NativeActivityItem,
    modifier: Modifier = Modifier,
    showStatusDot: Boolean = true,
) {
    val language = LocalNativeLanguage.current
    val statusColor = when (command.status) {
        NativeActivityItemStatus.FAILED -> MaterialTheme.colorScheme.error
        NativeActivityItemStatus.RUNNING -> MaterialTheme.colorScheme.primary
        NativeActivityItemStatus.WAITING -> MaterialTheme.colorScheme.tertiary
        NativeActivityItemStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when (command.status) {
        NativeActivityItemStatus.FAILED -> nativeText(language, "失败", "Failed")
        NativeActivityItemStatus.RUNNING -> nativeText(language, "运行中", "Running")
        NativeActivityItemStatus.WAITING -> nativeText(language, "等待中", "Waiting")
        NativeActivityItemStatus.COMPLETED -> nativeText(language, "已完成", "Done")
    }
    val commandText = command.title.ifBlank { command.text.ifBlank { "command" } }
    val hasDetails = commandText.isNotBlank() || command.outputRef.isNotBlank() || command.outputPreview.isNotBlank()
    var userExpanded by remember(command.id) { mutableStateOf<Boolean?>(null) }
    val expanded = resolveNativeCommandDisclosure(
        autoExpanded = nativeCommandAutoExpanded(command.status),
        userExpanded = userExpanded,
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = .88f, stiffness = 520f),
        label = "activityCommandRowArrow",
    )
    var resolvedOutput by remember(command.id) { mutableStateOf<String?>(null) }
    var outputLoading by remember(command.id) { mutableStateOf(false) }

    LaunchedEffect(expanded, command.outputRef, command.outputPreview) {
        // Do NOT clear resolvedOutput on collapse: clearing it at the same frame the exit animation
        // starts shrinks the content under the still-animating height, leaving blank space that pops.
        if (!shouldResolveNativeCommandOutput(expanded, command.outputRef)) {
            outputLoading = false
            return@LaunchedEffect
        }
        outputLoading = true
        resolvedOutput = withContext(Dispatchers.Default) {
            resolveNativeCommandOutput(command.outputRef, command.outputPreview)
        }
        outputLoading = false
    }

    val visibleOutput = if (command.outputRef.isBlank()) command.outputPreview else resolvedOutput.orEmpty()
    val pauseFollowForToggle = LocalPauseFollowDuringAnimation.current
    Column(modifier.fillMaxWidth()) {
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
                ) {
                    pauseFollowForToggle()
                    userExpanded = !expanded
                }
                .padding(
                    start = if (showStatusDot) 12.dp else 0.dp,
                    end = if (showStatusDot) 12.dp else 0.dp,
                    top = if (showStatusDot) 9.dp else 0.dp,
                    bottom = if (showStatusDot) 9.dp else 7.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showStatusDot) {
                Surface(Modifier.size(7.dp), shape = CircleShape, color = statusColor) {}
                Spacer(Modifier.width(8.dp))
            }
            Text(
                commandText.lineSequence().firstOrNull().orEmpty(),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(8.dp))
            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
            if (hasDetails) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    HugeIcons.ArrowDown01,
                    if (expanded) nativeText(language, "收起命令详情", "Collapse command details")
                    else nativeText(language, "展开命令详情", "Expand command details"),
                    Modifier.size(14.dp).graphicsLayer { rotationZ = arrowRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(expanded && hasDetails) {
            Column(
                Modifier.fillMaxWidth().padding(
                    start = if (showStatusDot) 27.dp else 0.dp,
                    end = if (showStatusDot) 12.dp else 0.dp,
                    bottom = 10.dp,
                ),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .28f))
                Text(
                    nativeText(language, "原始命令", "Raw command"),
                    modifier = Modifier.padding(top = 9.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                SelectionContainer {
                    Text(
                        commandText,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (outputLoading) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            nativeText(language, "正在读取完整输出…", "Loading full output…"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (visibleOutput.isNotBlank()) {
                    NativeCommandOutputBody(visibleOutput)
                }
            }
        }
    }
}

@Composable
private fun NativeCommandOutputBody(output: String) {
    val language = LocalNativeLanguage.current
    val chunks = remember(output) { NativeUiRenderSafety.splitPlainText(output.trimEnd()) }
    val scrollState = rememberScrollState()
    Text(
        nativeText(language, "输出", "Output"),
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = nativeActivitySurfaceAlpha(.72f)),
    ) {
        SelectionContainer {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                chunks.forEach { chunk ->
                    Text(
                        chunk,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

internal fun nativeCommandAutoExpanded(status: NativeActivityItemStatus): Boolean =
    status == NativeActivityItemStatus.RUNNING || status == NativeActivityItemStatus.FAILED

internal fun nativeCommandCollectionAutoExpanded(runningCount: Int, failedCount: Int): Boolean =
    runningCount > 0 || failedCount > 0

internal fun resolveNativeCommandDisclosure(autoExpanded: Boolean, userExpanded: Boolean?): Boolean =
    userExpanded ?: autoExpanded

internal fun shouldResolveNativeCommandOutput(expanded: Boolean, outputRef: String): Boolean =
    expanded && outputRef.isNotBlank()

internal fun resolveNativeCommandOutput(outputRef: String, preview: String): String =
    if (outputRef.isBlank()) preview else NativeCommandOutputStore.get(outputRef) ?: preview


@Composable
internal fun NativeSubagentVisualFlowRow(
    items: List<NativeSubagentVisual>,
    modifier: Modifier = Modifier,
    onSubagentClick: (NativeSubagentVisual) -> Unit = {},
    onOverflowClick: (List<NativeSubagentVisual>) -> Unit = {},
) {
    val language = LocalNativeLanguage.current
    val merged = remember(items) { NativeSubagentVisualFactory.mergeAll(items) }
    val inline = remember(merged) { NativeSubagentVisualFactory.inline(merged, maxVisible = 8) }
    // Respect the app's explicit appearance preference (not only the platform system mode), so
    // chips keep the same palette as the rest of the native chat in forced light/dark themes.
    val dark = !rememberIsLightTheme()
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        inline.visible.forEach { visual ->
            val background = Color(if (dark) visual.darkColorArgb else visual.lightColorArgb)
            val status = when (visual.status) {
                NativeSubagentStatus.WORKING -> MaterialTheme.colorScheme.primary
                NativeSubagentStatus.WAITING -> MaterialTheme.colorScheme.tertiary
                NativeSubagentStatus.DONE -> Color(0xFF5E8B68)
                NativeSubagentStatus.FAILED -> MaterialTheme.colorScheme.error
            }
            Surface(
                modifier = Modifier.fcodePressClickable(
                    onClickLabel = nativeText(language, "查看子代理详情", "Open subagent details"),
                ) { onSubagentClick(visual) },
                shape = CircleShape,
                color = background,
                border = BorderStroke(1.dp, status.copy(alpha = .34f)),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.Sparkles, null, Modifier.size(14.dp), tint = status)
                    Spacer(Modifier.width(6.dp))
                    Text(visual.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Surface(Modifier.size(6.dp), shape = CircleShape, color = status) {}
                }
            }
        }
        if (inline.overflowCount > 0) Surface(
            modifier = Modifier.fcodePressClickable(
                onClickLabel = nativeText(language, "查看全部子代理", "View all subagents"),
            ) { onOverflowClick(merged.drop(8)) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                nativeText(language, "还有 ${inline.overflowCount} 个子代理", "${inline.overflowCount} more subagents"),
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** WebUI-style single lifecycle divider. */
@Composable
internal fun NativeCompactionDivider(item: NativeCompactionItem?, messageId: String) {
    if (item == null) return
    val language = LocalNativeLanguage.current
    var expanded by remember(messageId, item.id) { mutableStateOf(false) }
    val running = item.status == NativeCompactionStatus.PENDING || item.status == NativeCompactionStatus.RUNNING
    val failed = item.status == NativeCompactionStatus.FAILED
    val cancelled = item.status == NativeCompactionStatus.CANCELLED
    val label = when {
        running -> nativeText(language, "正在压缩", "Compacting") +
            if (item.source == NativeCompactionSource.AUTOMATIC) nativeText(language, " · 自动", " · automatic") else ""
        failed -> nativeText(language, "压缩失败", "Compaction failed")
        cancelled -> nativeText(language, "已取消", "Cancelled")
        else -> nativeText(language, "完成", "Completed")
    }
    // Say what the compaction actually bought. Only shown once it finished and only when the
    // backend reported figures -- Claude omits `postTokens` when it preserved a segment instead of
    // summarising everything, and Codex reports nothing at all, so both cases stay quiet rather
    // than implying a saving that did not happen.
    val savings = if (item.status != NativeCompactionStatus.COMPLETED || item.preTokens <= 0L) {
        ""
    } else if (item.postTokens > 0L) {
        nativeText(
            language,
            " · ${formatNativeTokenCount(item.preTokens)} → ${formatNativeTokenCount(item.postTokens)}",
            " · ${formatNativeTokenCount(item.preTokens)} → ${formatNativeTokenCount(item.postTokens)}",
        )
    } else {
        nativeText(
            language,
            " · 压缩前 ${formatNativeTokenCount(item.preTokens)}",
            " · from ${formatNativeTokenCount(item.preTokens)}",
        )
    }
    val tint = when {
        failed -> MaterialTheme.colorScheme.error
        cancelled -> MaterialTheme.colorScheme.onSurfaceVariant
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val disclosureModifier = if (failed && item.error.isNotBlank()) {
        Modifier.fcodePressClickable(
            onClickLabel = if (expanded) {
                nativeText(language, "收起压缩错误", "Collapse compaction error")
            } else {
                nativeText(language, "展开压缩错误", "Expand compaction error")
            },
        ) { expanded = !expanded }
    } else Modifier
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().then(disclosureModifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f),
            )
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp, color = tint)
                else Icon(if (failed || cancelled) HugeIcons.Cancel01 else HugeIcons.Tick02, null, Modifier.size(14.dp), tint = tint)
                Text(
                    label + savings,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    fontWeight = FontWeight.Medium,
                )
            }
            HorizontalDivider(
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f),
            )
        }
        AnimatedVisibility(expanded && failed && item.error.isNotBlank()) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .5f),
            ) {
                Text(item.error, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
