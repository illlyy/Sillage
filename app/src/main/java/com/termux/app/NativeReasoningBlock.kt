package com.termux.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Zap

/**
 * claude.ai-style reasoning accordion (pattern: claudecodeui Reasoning.tsx):
 * - auto-expands while streaming, auto-collapses 1s after streaming ends (once),
 * - header label switches between a shimmer "Thinking…" and "Thought for N seconds",
 * - duration is measured from stream start to stream end, rounded up.
 */
internal fun reasoningDurationLabel(seconds: Int?, streaming: Boolean, language: String): String = when {
    streaming || seconds == null || seconds <= 0 -> nativeText(language, "思考中…", "Thinking…")
    else -> nativeText(language, "思考了 ${seconds}s", "Thought for ${seconds}s")
}

@Composable
internal fun FcodeReasoningBlock(
    content: String,
    isStreaming: Boolean,
    durationSeconds: Int?,
    modifier: Modifier = Modifier,
    open: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    contentPadding: Modifier = Modifier.padding(top = 10.dp),
    headerOnly: Boolean = false,
) {
    val language = LocalNativeLanguage.current
    var internalOpen by remember { mutableStateOf(isStreaming) }
    var hasAutoClosed by remember { mutableStateOf(false) }
    val expanded = open ?: internalOpen
    LaunchedEffect(isStreaming, open) {
        if (open != null) return@LaunchedEffect
        if (isStreaming) {
            hasAutoClosed = false
            internalOpen = true
        } else if (internalOpen && !hasAutoClosed) {
            delay(1_000)
            internalOpen = false
            hasAutoClosed = true
        }
    }
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        tween(150, easing = FastOutSlowInEasing),
        label = "reasoningBlockArrow",
    )
    val pauseFollowForToggle = LocalPauseFollowDuringAnimation.current
    val headerLabel = reasoningDurationLabel(durationSeconds, isStreaming, language)
    Column(modifier = modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fcodePressClickable(
                    onClickLabel = if (expanded) {
                        nativeText(language, "收起思考过程", "Collapse reasoning")
                    } else {
                        nativeText(language, "展开思考过程", "Expand reasoning")
                    },
                ) {
                    pauseFollowForToggle()
                    if (onToggle != null) onToggle(!expanded) else internalOpen = !expanded
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                HugeIcons.Zap,
                null,
                modifier = Modifier.size(16.dp),
                tint = if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                headerLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isStreaming) {
                FcodeStreamingShimmerDot()
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                HugeIcons.ArrowDown01,
                null,
                Modifier.size(15.dp).graphicsLayer { rotationZ = arrowRotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!headerOnly) {
            QElasticExpand(expanded && content.isNotBlank()) {
                Column(modifier = contentPadding) {
                    DeferredHistoricalRichText(content)
                }
            }
        }
    }
}

/** Small pulsing dot used while reasoning is in flight. */
@Composable
internal fun FcodeStreamingShimmerDot(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "reasoningDot").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "reasoningDotAlpha",
    )
    Box(
        modifier = modifier
            .size(7.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape),
    )
}
