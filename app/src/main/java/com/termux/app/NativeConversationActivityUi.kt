@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.termux.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** Unified live/history activity renderer backed only by domain DTOs. */
@Composable
internal fun NativeActivityGroupRenderer(
    group: NativeActivityGroup,
    subagents: List<NativeSubagentVisual> = emptyList(),
    modifier: Modifier = Modifier,
    onSubagentClick: (NativeSubagentVisual) -> Unit = {},
    onSubagentOverflowClick: (List<NativeSubagentVisual>) -> Unit = {},
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
        if (reasoning.isNotBlank()) {
            NativeReasoningGroupCapsule(
                groupKey = group.key,
                reasoning = reasoning,
                running = group.running,
                initiallyExpanded = group.expandedByDefault,
            )
        }
        if (group.commands.isNotEmpty()) NativeCommandCollection(group)
        val nonCommandItems = group.items.filter {
            it.type != NativeActivityItemType.COMMAND && it.type != NativeActivityItemType.SUBAGENT
        }
        if (nonCommandItems.isNotEmpty()) NativeToolCollection(nonCommandItems)
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
            modifier = Modifier.widthIn(max = 680.dp).clickable { expanded = !expanded },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .88f),
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
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .72f),
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
    var expanded by remember(group.key, group.running) { mutableStateOf(group.expandedByDefault) }
    val running = group.runningCommandCount
    val failed = group.commands.count { it.status == NativeActivityItemStatus.FAILED }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "activityCommandArrow")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .38f)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 9.dp),
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
                        NativeCommandRow(command)
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
private fun NativeCommandRow(command: NativeActivityItem) {
    val statusColor = when (command.status) {
        NativeActivityItemStatus.FAILED -> MaterialTheme.colorScheme.error
        NativeActivityItemStatus.RUNNING -> MaterialTheme.colorScheme.primary
        NativeActivityItemStatus.WAITING -> MaterialTheme.colorScheme.tertiary
        NativeActivityItemStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(7.dp), shape = CircleShape, color = statusColor) {}
            Spacer(Modifier.width(8.dp))
            Text(
                command.title.ifBlank { command.text.ifBlank { "command" } },
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        command.outputPreview.takeIf { it.isNotBlank() }?.let { preview ->
            Text(
                preview.takeLast(1_200),
                Modifier.fillMaxWidth().padding(start = 15.dp, top = 6.dp),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .74f)) {
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
                modifier = Modifier.clickable { onSubagentClick(visual) },
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
            modifier = Modifier.clickable { onOverflowClick(merged.drop(8)) },
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
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().then(if (failed && item.error.isNotBlank()) Modifier.clickable { expanded = !expanded } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f),
            )
            // A small opaque-enough center plate keeps the status legible over wallpaper while
            // retaining the WebUI-style rule that visually cuts the timeline in two.
            Surface(
                modifier = Modifier.padding(horizontal = 8.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .86f),
                border = BorderStroke(1.dp, tint.copy(alpha = .18f)),
            ) {
                Row(
                    Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (running) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp, color = tint)
                    else Icon(if (failed || cancelled) HugeIcons.Cancel01 else HugeIcons.Tick02, null, Modifier.size(14.dp), tint = tint)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Medium)
                }
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
