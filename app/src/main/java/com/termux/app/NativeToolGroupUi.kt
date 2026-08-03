package com.termux.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Sparkles

private fun NativeToolCategory.icon(): ImageVector = when (this) {
    NativeToolCategory.FILE -> HugeIcons.Files02
    NativeToolCategory.SEARCH, NativeToolCategory.WEB -> HugeIcons.Search01
    NativeToolCategory.COMMAND -> HugeIcons.Code
    else -> HugeIcons.Sparkles
}

@Composable
internal fun NativeToolListItem.Group.itemColor(): androidx.compose.ui.graphics.Color = when (aggregateStatus()) {
    NativeActivityItemStatus.FAILED -> MaterialTheme.colorScheme.error
    NativeActivityItemStatus.RUNNING -> MaterialTheme.colorScheme.primary
    NativeActivityItemStatus.WAITING -> MaterialTheme.colorScheme.tertiary
    NativeActivityItemStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Collapses a run of consecutive same-type tool items into one expandable container. */
@Composable
internal fun FcodeToolGroup(group: NativeToolListItem.Group) {
    val language = LocalNativeLanguage.current
    val config = NativeToolConfigs.of(group.type)
    val accent = group.itemColor()
    var expanded by remember(group.firstItem.id, group.count) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        tween(150, easing = FastOutSlowInEasing),
        label = "toolGroupArrow",
    )
    val preview = remember(group.items) {
        group.items.take(2).joinToString("  ") { it.title.ifBlank { it.text } }.trim()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) {
                            nativeText(language, "收起 ${config.label(language)} 组", "Collapse ${config.label(language)} group")
                        } else {
                            nativeText(language, "展开 ${config.label(language)} 组", "Expand ${config.label(language)} group")
                        },
                    ) { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f), contentColor = accent) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        Icon(config.category.icon(), null, Modifier.size(13.dp), tint = accent)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    config.label(language),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (preview.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        preview,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text("×${group.count}", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(
                    HugeIcons.ArrowDown01,
                    null,
                    Modifier.size(14.dp).graphicsLayer { rotationZ = arrowRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QElasticExpand(expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    group.items.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(Modifier.size(5.dp), shape = CircleShape, color = itemColorFor(item)) {}
                            Spacer(Modifier.width(8.dp))
                            Text(
                                item.title.ifBlank { item.text }.ifBlank { config.label(language) },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val statusLabel = when (item.status) {
                                NativeActivityItemStatus.FAILED -> nativeText(language, "失败", "Failed")
                                NativeActivityItemStatus.RUNNING -> nativeText(language, "运行中", "Running")
                                NativeActivityItemStatus.WAITING -> nativeText(language, "等待", "Waiting")
                                NativeActivityItemStatus.COMPLETED -> nativeText(language, "完成", "Done")
                            }
                            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = itemColorFor(item))
                        }
                        if (index != group.items.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = 10.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun itemColorFor(item: NativeActivityItem): androidx.compose.ui.graphics.Color = when (item.status) {
    NativeActivityItemStatus.FAILED -> MaterialTheme.colorScheme.error
    NativeActivityItemStatus.RUNNING -> MaterialTheme.colorScheme.primary
    NativeActivityItemStatus.WAITING -> MaterialTheme.colorScheme.tertiary
    NativeActivityItemStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
}
