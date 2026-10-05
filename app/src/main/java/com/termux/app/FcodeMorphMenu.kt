@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.termux.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.semantics.hideFromAccessibility
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
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    // ONE clock. The container morph, the dim layer, the source button's state and the row stagger
    // all read from it, so no part can finish before the one beside it. Because it is an Animatable
    // living outside the state branches, an interruption (tap outside, tap the anchor, back press)
    // reverses from whatever frame it is on instead of restarting.
    //
    // The previous build ran a 360ms container against a 400ms+80ms stagger and an anchor that
    // faded out over 120ms and back in over 240ms after a 150ms delay: four separate clocks, which
    // is what read as several things happening slightly out of step rather than one menu opening.
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(MORPH_DURATION_MS, easing = MORPH_EASING),
        )
    }
    BackHandler(enabled = expanded, onBack = onDismissRequest)

    SharedTransitionLayout(modifier) {
        val sharedState = rememberSharedContentState(key = "fcodeMorphMenu")
        Box(Modifier.fillMaxSize()) {
            // A whisper of a dim layer, faded by the same clock so it cannot pop in ahead of the
            // panel. It is invisible to accessibility so the menu items stay directly focusable,
            // and it only takes clicks while the menu is actually open, so a closing menu cannot
            // swallow a tap meant for the screen underneath.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = MORPH_SCRIM_ALPHA * progress.value))
                    .semantics { hideFromAccessibility() }
                    .then(
                        if (expanded) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismissRequest,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    // Identical in both directions, so the menu returns along exactly the path it
                    // opened by, at the same speed. The previous build opened over 360ms and closed
                    // over a spring with a 150ms-delayed anchor fade -- and its own comment admitted
                    // the anchor came back "late", which is a window where neither the panel nor the
                    // button is legible.
                    fadeIn(tween(MORPH_DURATION_MS, easing = MORPH_EASING))
                        .togetherWith(fadeOut(tween(MORPH_DURATION_MS, easing = MORPH_EASING)))
                },
                label = "fcodeMorphMenuContainer",
            ) { open ->
                // Both branches are full-screen boxes, so AnimatedContent has no size to tween and
                // the only thing moving the panel is sharedBounds. The branch fade above is the
                // single opacity change; sharedBounds deliberately contributes none, because
                // stacking a branch fade on a sharedBounds enter transition is how the old build
                // ended up compounding two transforms onto one element.
                Box(Modifier.fillMaxSize()) {
                    if (open) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    end = anchorEndOffset,
                                    top = anchorTopPadding + ANCHOR_SIZE + ANCHOR_PANEL_GAP,
                                ),
                        ) {
                            // The panel is the shared element, so it grows out of the button's exact
                            // footprint and, on the way back, collapses into it.
                            Box(
                                Modifier
                                    .size(panelWidth, panelHeight)
                                    .sharedBounds(
                                        sharedContentState = sharedState,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = MORPH_BOUNDS,
                                        enter = EnterTransition.None,
                                        exit = ExitTransition.None,
                                    ),
                            ) {
                                MorphMenuPanel(items, progress.value, onDismissRequest)
                            }
                        }
                    } else {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = anchorEndOffset, top = anchorTopPadding),
                        ) {
                            // The other half of the shared element: the button's exact rect, and
                            // nothing drawn inside it. The button itself is composed separately
                            // below and is never removed, so this only supplies the bounds the morph
                            // starts from and returns to.
                            Box(
                                Modifier
                                    .size(ANCHOR_SIZE)
                                    .sharedBounds(
                                        sharedContentState = sharedState,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = MORPH_BOUNDS,
                                        enter = EnterTransition.None,
                                        exit = ExitTransition.None,
                                    ),
                            )
                        }
                    }
                }
            }
            // The source button: drawn last so it stays above the panel, and never removed. The
            // whole point of the motion is that it is visibly where the menu came from and where it
            // goes back to, so it dims instead of disappearing -- fading it to nothing (what the
            // previous build did) left the closing animation with no source to return into.
            //
            // It is also the only way to close the menu by tapping it, which the old build could not
            // do at all: the button was gone while the menu was open.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = anchorEndOffset, top = anchorTopPadding),
            ) {
                Box(
                    Modifier
                        .size(ANCHOR_SIZE)
                        .graphicsLayer { alpha = morphAnchorAlpha(progress.value) }
                        .clip(RoundedCornerShape(24.dp))
                        .background(surface.copy(alpha = 0.55f)),
                )
                IconButton(
                    onClick = { if (expanded) onDismissRequest() else onAnchorClick() },
                    modifier = Modifier.graphicsLayer { alpha = morphAnchorAlpha(progress.value) },
                ) {
                    if (anchorContent != null) {
                        anchorContent()
                    } else {
                        Icon(
                            HugeIcons.MoreVertical,
                            contentDescription = nativeText(language, "更多操作", "More actions"),
                            tint = onSurface,
                        )
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
                val eased = morphMenuItemReveal(index, items.size, reveal)
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

/**
 * Reveal progress for one menu row, as a pure function so the arithmetic is unit-testable.
 *
 * The step is derived from the item count rather than being a constant. With fixed constants the
 * stagger window overflowed as soon as the menu grew: with four items the last row settled at
 * alpha 0.66 and 6dp below its resting place, because the animation ended before its own window
 * did, and a fifth item would have started at 0.94 and finished at alpha 0.07 -- a row that is
 * effectively invisible for as long as the menu is open. Deriving the step keeps the whole
 * stagger inside the animated range for any count, and reserves a margin so the last row also
 * *finishes* rather than arriving on the final frame.
 */
internal fun morphMenuItemReveal(index: Int, count: Int, reveal: Float): Float {
    if (count <= 0) return 1f
    val start = MORPH_ITEM_STAGGER_START
    val step = if (count <= 1) {
        0f
    } else {
        (1f - MORPH_ITEM_STAGGER_SPAN - MORPH_ITEM_STAGGER_END_MARGIN - start) / (count - 1).toFloat()
    }
    val progress = ((reveal - (start + index * step)) / MORPH_ITEM_STAGGER_SPAN).coerceIn(0f, 1f)
    return smoothstep01(progress)
}

/**
 * Opacity of the source button across the open morph.
 *
 * Never reaches zero, and that is the point: the whole motion is "from the button, back into the
 * button", so the button has to stay visible for the return to have somewhere to land. The previous
 * build faded it to 0 on open and then faded it back in on a delayed, differently-timed curve, which
 * left a window where neither the panel nor the button was legible.
 */
internal fun morphAnchorAlpha(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return 1f - (1f - MORPH_ANCHOR_DIM) * t
}

private val MORPH_EASING = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The one spec every part of the morph runs on, container bounds included.
 *
 * sharedBounds does NOT take its duration from the enter/exit transitions or from the enclosing
 * AnimatedContent: left alone it runs on its own default spring, so the container would finish on a
 * different clock from the dim layer, the source button and the row stagger. Measured while
 * building evidence for this: with the other two slowed to 5s for frame capture, the panel still
 * reached full size within a few hundred milliseconds. That silent second clock is a good part of
 * what "several things happening out of step" was.
 */
private val MORPH_BOUNDS = BoundsTransform { _, _ -> tween(MORPH_DURATION_MS, easing = MORPH_EASING) }

/** One duration for both directions: the menu closes along the path it opened by, at the same speed. */
private const val MORPH_DURATION_MS = 300

/** Dim behind the open menu. Faded on the shared clock so it cannot arrive ahead of the panel. */
private const val MORPH_SCRIM_ALPHA = 0.04f

/** How far the source button dims while the menu is out; 1 = untouched, 0 = invisible. */
private const val MORPH_ANCHOR_DIM = 0.55f

private val ANCHOR_SIZE = 48.dp
private val ANCHOR_END_GAP_DEFAULT = 4.dp
private val ANCHOR_TOP_GAP = 8.dp

/** Gap between the button and the panel beneath it, so dimming the button stays legible. */
private val ANCHOR_PANEL_GAP = 8.dp

private val PANEL_WIDTH = 248.dp
private val ITEM_HEIGHT = 48.dp
private val PANEL_VERTICAL_PADDING = 6.dp

/** Fraction of the reveal consumed before the first row starts; the rest is shared by the stagger. */
private const val MORPH_ITEM_STAGGER_START = 0.14f

/** Reveal window of a single row. */
private const val MORPH_ITEM_STAGGER_SPAN = 0.28f

/** Reveal kept in reserve after the last row, so it settles instead of arriving on the last frame. */
private const val MORPH_ITEM_STAGGER_END_MARGIN = 0.06f
