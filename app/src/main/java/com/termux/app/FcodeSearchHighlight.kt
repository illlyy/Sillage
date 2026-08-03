package com.termux.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * One-shot highlight flash for a search jump (pattern: claudecodeui search navigation highlight).
 * The overlay starts at a visible alpha and fades to zero over ~4s; the target is cleared by
 * the caller afterwards, so re-entry never re-flashes an old hit.
 */
@Composable
internal fun FcodeSearchHighlightBox(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val highlightAlpha = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            highlightAlpha.snapTo(0.5f)
            highlightAlpha.animateTo(0f, tween(durationMillis = 4_000, easing = LinearEasing))
        }
    }
    val background = MaterialTheme.colorScheme.primaryContainer
    Box(modifier) {
        if (highlightAlpha.value > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(background.copy(alpha = highlightAlpha.value)),
            )
        }
        content()
    }
}
