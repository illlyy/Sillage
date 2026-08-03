package com.termux.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Sparkles
import org.json.JSONObject

/** Compact status label for a subagent card. */
internal fun subagentCardStatusLabel(status: NativeActivityItemStatus, language: String): String = when (status) {
    NativeActivityItemStatus.RUNNING -> nativeText(language, "执行中…", "Working…")
    NativeActivityItemStatus.WAITING -> nativeText(language, "等待中", "Waiting")
    NativeActivityItemStatus.FAILED -> nativeText(language, "失败", "Failed")
    NativeActivityItemStatus.COMPLETED -> nativeText(language, "完成", "Done")
}

/**
 * Inline subagent execution card for the activity timeline (pattern: claudecodeui
 * SubagentContainer.tsx). Shows name, live status and a clamped prompt; tapping opens the
 * subagent drawer with its history.
 */
@Composable
internal fun FcodeSubagentCard(
    item: NativeActivityItem,
    onLoadHistory: (String) -> Unit,
) {
    val language = LocalNativeLanguage.current
    val openDrawer = LocalOpenSubagentDrawer.current
    val running = item.status == NativeActivityItemStatus.RUNNING ||
        item.status == NativeActivityItemStatus.WAITING
    val name = item.title.ifBlank { item.agentThreadId.takeIf { it.isNotBlank() }?.let { "Subagent ${it.take(6)}" } ?: "Subagent" }
    val description = item.text.trim()
    var expanded by remember(item.id) { mutableStateOf(false) }
    val accent = when (item.status) {
        NativeActivityItemStatus.FAILED -> MaterialTheme.colorScheme.error
        NativeActivityItemStatus.RUNNING -> MaterialTheme.colorScheme.primary
        NativeActivityItemStatus.WAITING -> MaterialTheme.colorScheme.tertiary
        NativeActivityItemStatus.COMPLETED -> Color(0xFF5E8B68)
    }
    val statusLabel = subagentCardStatusLabel(item.status, language)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.66f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = nativeText(language, "查看子代理详情", "Open subagent details"),
                    ) {
                        val anchor = JSONObject().apply {
                            put("type", "subAgentActivity")
                            put("agentThreadId", item.agentThreadId)
                            put("callId", item.callId)
                            put("itemId", item.itemId ?: item.id)
                            put("agentName", name)
                            put("status", item.status.name.lowercase())
                            if (description.isNotBlank()) put("detail", description)
                        }
                        openDrawer(anchor)
                        if (item.agentThreadId.isNotBlank()) onLoadHistory(item.agentThreadId)
                    }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.13f), contentColor = accent) {
                    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                        if (running) SubagentPulsingDot(accent)
                        else Icon(HugeIcons.Sparkles, null, Modifier.size(14.dp), tint = accent)
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (description.isNotBlank()) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(5.dp))
                Icon(
                    HugeIcons.ArrowDown01,
                    null,
                    Modifier.size(13.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (description.length > 200) {
                Row(Modifier.fillMaxWidth().padding(start = 11.dp, end = 11.dp, bottom = 8.dp)) {
                    Spacer(Modifier.width(35.dp))
                    Text(
                        if (expanded) nativeText(language, "收起", "Collapse") else nativeText(language, "展开描述", "Expand"),
                        modifier = Modifier.fcodePressClickable(
                            onClickLabel = if (expanded) nativeText(language, "收起描述", "Collapse description") else nativeText(language, "展开描述", "Expand description"),
                        ) { expanded = !expanded },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubagentPulsingDot(color: Color) {
    val alpha by rememberInfiniteTransition(label = "subagentDot").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "subagentDotAlpha",
    )
    Box(
        Modifier
            .size(8.dp)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}
