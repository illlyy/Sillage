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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/** Unified live/history activity renderer backed only by domain DTOs. */
@Composable
internal fun NativeActivityGroupRenderer(
    group: NativeActivityGroup,
    subagents: List<NativeSubagentVisual> = emptyList(),
    modifier: Modifier = Modifier,
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
    onLoadSubagentHistory: ((String) -> Unit)? = null,
    enterExpanded: Boolean = false,
    onAutoCollapsed: (() -> Unit)? = null,
) {
    val language = LocalNativeLanguage.current
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
    val bodyFullyCollapsed = bodyVisibility.isIdle && !bodyVisibility.currentState
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
        if (expanded) {
            // Expand the shell first. The body only enters after the full-width shell has settled,
            // so reversing a partially collapsed card never lays out long content in a narrow pill.
            chromeVisibility.targetState = true
            bodyVisibility.targetState = chromeFullyExpanded
        } else {
            // Keep the shell full width until AnimatedVisibility reports that the body is gone.
            // This is animation-clock aware and cannot leave a delayed callback behind on reversal.
            bodyVisibility.targetState = false
            chromeVisibility.targetState = !bodyFullyCollapsed
        }
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = .88f, stiffness = 430f),
        label = "activityTimelineArrow",
    )
    val cardShape = RoundedCornerShape(18.dp)
    val groupedItems = remember(group.key, nonCommandItems) { groupConsecutiveTools(nonCommandItems) }
    val stepCount = (if (reasoning.isNotBlank()) 1 else 0) + group.commands.size + groupedItems.size
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
        Column {
            AnimatedVisibility(
                visibleState = chromeVisibility,
                enter = expandHorizontally(tween(180), expandFrom = Alignment.Start),
                exit = shrinkHorizontally(tween(180), shrinkTowards = Alignment.Start),
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
                // Keep fade and height in lockstep (the old fade finished in ~80ms while the
                // height tween ran 160ms, leaving a transparent shrinking rectangle that popped).
                enter = fadeIn(tween(220)) + expandVertically(tween(260, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(240, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top),
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .38f))
                    Column(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
                    ) {
                        val totalSteps = stepCount.coerceAtLeast(1)
                        var index = 0
                        if (reasoning.isNotBlank()) {
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
                                    reasoning,
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        group.commands.forEach { command ->
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
                        groupedItems.forEach { listItem ->
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
private fun NativeToolTimelineContent(item: NativeActivityItem) {
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
private fun NativeReasoningGroupCapsule(
    groupKey: String,
    reasoning: String,
    running: Boolean,
    initiallyExpanded: Boolean,
) {
    val language = LocalNativeLanguage.current
    // Include the running transition in the key so a completed group gets its WebUI-style
    // collapsed default without retaining the live expanded state forever.
    var expanded by remember(groupKey, running) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = .86f, stiffness = 340f),
        label = "activityReasoningArrow",
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fcodePressClickable(
                    onClickLabel = if (expanded) {
                        nativeText(language, "收起思考过程", "Collapse reasoning")
                    } else {
                        nativeText(language, "展开思考过程", "Expand reasoning")
                    },
                ) { expanded = !expanded },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = nativeActivitySurfaceAlpha(.88f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .36f)),
        ) {
            Row(
                Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.7.dp)
                else Icon(HugeIcons.Zap, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text(
                    nativeText(language, if (running) "正在思考" else "思考", if (running) "Thinking" else "Reasoning"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    HugeIcons.ArrowDown01,
                    null,
                    Modifier.size(15.dp).graphicsLayer { rotationZ = rotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(expanded) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = nativeActivitySurfaceAlpha(.72f)),
            ) {
                Text(
                    reasoning,
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun NativeCommandCollection(group: NativeActivityGroup) {
    val language = LocalNativeLanguage.current
    val running = group.runningCommandCount
    val failed = group.commands.count { it.status == NativeActivityItemStatus.FAILED }
    var userExpanded by remember(group.key) { mutableStateOf<Boolean?>(null) }
    val expanded = resolveNativeCommandDisclosure(
        // Command disclosure follows command state, not the enclosing turn. The group may keep
        // running for reasoning or another tool after its last command completes.
        autoExpanded = nativeCommandCollectionAutoExpanded(running, failed),
        userExpanded = userExpanded,
    )
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "activityCommandArrow")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = nativeActivitySurfaceAlpha(.78f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .38f)),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) {
                            nativeText(language, "收起命令集合", "Collapse commands")
                        } else {
                            nativeText(language, "展开命令集合", "Expand commands")
                        },
                    ) { userExpanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (running > 0) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.8.dp)
                else Icon(if (failed > 0) HugeIcons.Cancel01 else HugeIcons.Tick02, null, Modifier.size(15.dp), tint = if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    nativeText(language, "命令集合", "Commands"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val summary = buildList {
                    add(nativeText(language, "${group.commandCount} 条", "${group.commandCount}"))
                    if (running > 0) add(nativeText(language, "$running 运行中", "$running running"))
                    if (failed > 0) add(nativeText(language, "$failed 失败", "$failed failed"))
                }.joinToString(" · ")
                Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Icon(HugeIcons.ArrowDown01, null, Modifier.size(15.dp).graphicsLayer { rotationZ = rotation })
            }
            AnimatedVisibility(expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                    group.commands.forEachIndexed { index, command ->
                        key(command.id) { NativeCommandRow(command) }
                        if (index != group.commands.lastIndex) HorizontalDivider(
                            Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f),
                        )
                    }
                }
            }
        }
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
private fun NativeToolCollection(items: List<NativeActivityItem>) {
    val language = LocalNativeLanguage.current
    val grouped = items.groupingBy { it.type }.eachCount()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        grouped.forEach { (type, count) ->
            val label = when (type) {
                NativeActivityItemType.FILE_CHANGE -> nativeText(language, "文件修改", "File changes")
                NativeActivityItemType.WEB_SEARCH -> nativeText(language, "网页搜索", "Web searches")
                NativeActivityItemType.TOOL -> nativeText(language, "工具调用", "Tools")
                else -> nativeText(language, "活动", "Activity")
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = nativeActivitySurfaceAlpha(.74f))) {
                Text("$label · $count", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

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
                Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Medium)
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
