package com.termux.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

private const val FCODE_PRESSED_SCALE = 0.985f
private const val FCODE_PRESS_DAMPING_RATIO = 0.82f
private const val FCODE_PRESS_STIFFNESS = 650f

/**
 * Fcode's ripple-free click treatment. The Foundation clickable owns enabled state, role and
 * accessibility semantics; this modifier only replaces the visual indication with a subtle press.
 */
@Composable
internal fun Modifier.fcodePressClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    return fcodePressFeedback(resolvedInteractionSource, enabled)
        .clickable(
            interactionSource = resolvedInteractionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/**
 * Ripple-free press treatment for rows that also expose long-click or double-click actions.
 * [combinedClickable] supplies the complete click/long-click semantics and gesture cancellation.
 */
@Composable
internal fun Modifier.fcodePressCombinedClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    return fcodePressFeedback(resolvedInteractionSource, enabled)
        .combinedClickable(
            interactionSource = resolvedInteractionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
            onClick = onClick,
        )
}

@Composable
private fun Modifier.fcodePressFeedback(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) FCODE_PRESSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = FCODE_PRESS_DAMPING_RATIO,
            stiffness = FCODE_PRESS_STIFFNESS,
        ),
        label = "fcodePressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
