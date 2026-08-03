@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.termux.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MoreVertical

/**
 * One menu entry rendered inside [FcodeMorphMenu].
 */
@Immutable
internal data class FcodeMorphMenuItem(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * A container-transform "more" menu: the anchor button seamlessly grows into a floating vertical
 * menu panel and collapses back into the button, powered by the same shared-element machinery as
 * the developer-options playground (container transform + emphasized curves + spring settle).
 *
 * The whole menu lives in a single full-screen layer (the caller places it with a high zIndex,
 * e.g. `Modifier.fillMaxSize().zIndex(4f)`), so both the anchor and the panel share one
 * [SharedTransitionLayout] scope: the shared bounds interpolate the button square into the panel
 * rectangle while the panel content (surface + staggered items) fades in over it. Because the two
 * states are branches of one [AnimatedContent], every interruption (tap outside, tap the anchor,
 * back press) reverses the animation from the current frame - the panel returns exactly the way it
 * opened.
 *
 * The panel's top-right corner is pinned to the anchor button's top-right corner (below the status
 * bar, flush with the top bar's actions), so it grows down-left from the button.
 *
 * IMPORTANT: like all Fcode overlays, the content never uses fillMaxSize() inside its own sizing;
 * the panel is measured to its items and the full-screen scrim is only a clickable dim layer.
 */
@Composable
internal fun FcodeMorphMenu(
    expanded: Boolean,
    onAnchorClick: () -> Unit,
    onDismissRequest: () -> Unit,
    items: List<FcodeMorphMenuItem>,
    modifier: Modifier = Modifier,
    anchorContent: (@Composable () -> Unit)? = null,
    // Horizontal gap between the screen's right edge and the anchor's right edge. The chat top bar
    // places its actions flush at `4.dp` from the edge, so an anchor sitting LEFT of another
    // action must add that action's width (see CodexChatScreen).
    anchorEndOffset: Dp = ANCHOR_END_GAP_DEFAULT,
) {
    val language = LocalNativeLanguage.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val anchorTopPadding = statusTop + ANCHOR_TOP_GAP
    val panelWidth = PANEL_WIDTH
    val panelHeight = ITEM_HEIGHT * items.size.toFloat() + PANEL_VERTICAL_PADDING * 2f
    // Item entrance is driven by one Animatable that lives outside the AnimatedContent branches, so
    // opening and closing share the same clock and reverse seamlessly from any mid-flight frame.
    val itemReveal = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            itemReveal.animateTo(1f, tween(400, delayMillis = 80, easing = MORPH_EASING))
        } else {
            itemReveal.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
    }
    BackHandler(enabled = expanded, onBack = onDismissRequest)

    SharedTransitionLayout(modifier) {
        val sharedState = rememberSharedContentState(key = "fcodeMorphMenu")
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                if (targetState) {
                    // Opening: the panel fades in while the anchor (icon + seed) dissolves quickly.
                    // scaleIn keeps the container morph on the emphasized 360ms clock so the shared
                    // bounds interpolation matches the playground's opening motion.
                    (fadeIn(tween(200, delayMillis = 90, easing = LinearOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.98f,
                            animationSpec = tween(360, easing = MORPH_EASING),
                        ))
                        .togetherWith(
                            fadeOut(tween(120, easing = FastOutSlowInEasing), targetAlpha = 0f),
                        )
                } else {
                    // Closing: the anchor fades back in late (once the panel has mostly collapsed),
                    // while the panel recedes with a slight spring overshoot - the container
                    // morph's bounds interpolation inherits this spring, giving the gentle bounce.
                    (fadeIn(tween(240, delayMillis = 150, easing = LinearOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.94f,
                            animationSpec = tween(300, easing = MORPH_EASING),
                        ))
                        .togetherWith(
                            fadeOut(tween(180, delayMillis = 40, easing = LinearOutSlowInEasing)) +
                                scaleOut(
                                    targetScale = 0.94f,
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
                                ),
                        )
                }
            },
            label = "fcodeMorphMenuContainer",
        ) { open ->
            Box(Modifier.fillMaxSize()) {
                if (open) {
                    // A whisper of a dim layer: taps anywhere outside the panel dismiss the menu.
                    // It is invisible to accessibility so the menu items stay directly focusable.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.04f))
                            .semantics { invisibleToUser() }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismissRequest,
                            ),
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = anchorEndOffset, top = anchorTopPadding),
                    ) {
                        // The whole panel (surface + items) is the shared element, so it zooms out
                        // of the button bounds exactly like the playground card expands to a page.
                        Box(
                            Modifier
                                .size(panelWidth, panelHeight)
                                .sharedBounds(
                                    sharedContentState = sharedState,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    enter = fadeIn(
                                        tween(240, delayMillis = 60, easing = LinearOutSlowInEasing),
                                    ),
                                    exit = fadeOut(tween(190, easing = LinearOutSlowInEasing)),
                                ),
                        ) {
                            MorphMenuPanel(items, itemReveal.value, onDismissRequest)
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = anchorEndOffset, top = anchorTopPadding),
                    ) {
                        // The shared seed: the same surface material at reduced alpha, so the morph
                        // starts from the button's exact footprint and material continuity holds in
                        // both directions.
                        Box(
                            Modifier
                                .size(ANCHOR_SIZE)
                                .sharedBounds(
                                    sharedContentState = sharedState,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    enter = fadeIn(
                                        tween(260, delayMillis = 120, easing = LinearOutSlowInEasing),
                                    ),
                                    exit = fadeOut(tween(130)),
                                ),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                                    ),
                            )
                        }
                        IconButton(onClick = onAnchorClick) {
                            if (anchorContent != null) {
                                anchorContent()
                            } else {
                                Icon(
                                    HugeIcons.MoreVertical,
                                    contentDescription = nativeText(language, "更多操作", "More actions"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The floating panel: an elevated rounded surface holding the staggered menu items. Item reveal is
 * derived from the shared [reveal] progress so items slide in from the button's direction on open
 * and slide back into it on close, each at its own offset.
 */
@Composable
private fun MorphMenuPanel(
    items: List<FcodeMorphMenuItem>,
    reveal: Float,
    onDismissRequest: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(18.dp),
        color = surface,
        tonalElevation = 4.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, outline.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = PANEL_VERTICAL_PADDING)) {
            items.forEachIndexed { index, item ->
                val start = ITEM_STAGGER_START + index * ITEM_STAGGER_STEP
                val progress = ((reveal - start) / ITEM_STAGGER_SPAN).coerceIn(0f, 1f)
                val eased = smoothstep01(progress)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT)
                        .graphicsLayer {
                            alpha = eased
                            translationY = (1f - eased) * 18.dp.toPx()
                        },
                ) {
                    MorphMenuRow(item, onSurface) {
                        onDismissRequest()
                        item.onClick()
                    }
                }
            }
        }
    }
}

@Composable
private fun MorphMenuRow(
    item: FcodeMorphMenuItem,
    onSurface: Color,
    onClick: () -> Unit,
) {
    val contentAlpha = if (item.enabled) 1f else 0.4f
    Row(
        Modifier
            .fillMaxWidth()
            .height(ITEM_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (item.enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            item.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = onSurface.copy(alpha = contentAlpha),
        )
    }
}

private fun smoothstep01(t: Float): Float {
    val v = t.coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}

private val MORPH_EASING = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val ANCHOR_SIZE = 48.dp
private val ANCHOR_END_GAP_DEFAULT = 4.dp
private val ANCHOR_TOP_GAP = 8.dp
private val PANEL_WIDTH = 248.dp
private val ITEM_HEIGHT = 48.dp
private val PANEL_VERTICAL_PADDING = 6.dp
private val ITEM_STAGGER_START = 0.30f
private val ITEM_STAGGER_STEP = 0.16f
private val ITEM_STAGGER_SPAN = 0.36f
