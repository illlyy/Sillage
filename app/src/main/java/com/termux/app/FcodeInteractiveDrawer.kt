package com.termux.app

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object FcodeDrawerPhysics {
    const val MIN_FLING_VELOCITY_PX_PER_SECOND = 365f

    fun settleTarget(progress: Float, velocityPxPerSecond: Float): Float = when {
        velocityPxPerSecond >= MIN_FLING_VELOCITY_PX_PER_SECOND -> 1f
        velocityPxPerSecond <= -MIN_FLING_VELOCITY_PX_PER_SECOND -> 0f
        progress >= 0.5f -> 1f
        else -> 0f
    }
}

/**
 * Interruptible drawer state. Progress is read from graphicsLayer lambdas, so animation frames
 * update RenderNode transforms without recomposing or remeasuring the chat document.
 */
@Stable
internal class FcodeInteractiveDrawerState internal constructor(initiallyOpen: Boolean) {
    internal var progress by mutableFloatStateOf(if (initiallyOpen) 1f else 0f)
        private set

    var motionActive by mutableStateOf(initiallyOpen)
        private set

    var targetOpen by mutableStateOf(initiallyOpen)
        private set

    private var animationJob: Job? = null

    val isOpen: Boolean get() = targetOpen && progress >= 0.999f
    val isClosed: Boolean get() = !targetOpen && progress <= 0.001f

    fun beginDrag() {
        animationJob?.cancel()
        animationJob = null
        motionActive = true
    }

    fun dragBy(normalizedDelta: Float) {
        progress = (progress + normalizedDelta).coerceIn(0f, 1f)
        motionActive = true
    }

    suspend fun open(initialVelocity: Float = 0f) = animateTo(1f, initialVelocity)

    suspend fun close(initialVelocity: Float = 0f) = animateTo(0f, initialVelocity)

    suspend fun settle(widthPx: Float, velocityPxPerSecond: Float) {
        if (widthPx <= 0f) return
        val normalizedVelocity = (velocityPxPerSecond / widthPx).coerceIn(-4f, 4f)
        val target = FcodeDrawerPhysics.settleTarget(progress, velocityPxPerSecond)
        animateTo(target, normalizedVelocity)
    }

    private suspend fun animateTo(target: Float, initialVelocity: Float) {
        val runningJob = currentCoroutineContext()[Job]
        if (animationJob !== runningJob) animationJob?.cancel()
        animationJob = runningJob
        targetOpen = target > 0.5f
        motionActive = true
        try {
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = initialVelocity,
                animationSpec = spring(
                    dampingRatio = 0.94f,
                    stiffness = 520f,
                    visibilityThreshold = 0.001f,
                ),
            ) { value, _ ->
                progress = value.coerceIn(0f, 1f)
            }
            progress = target
        } finally {
            if (animationJob === runningJob) {
                animationJob = null
                motionActive = progress > 0.001f
                if (progress <= 0.001f) targetOpen = false
                if (progress >= 0.999f) targetOpen = true
            }
        }
    }

}

@Composable
internal fun rememberFcodeInteractiveDrawerState(
    initiallyOpen: Boolean = false,
): FcodeInteractiveDrawerState = remember { FcodeInteractiveDrawerState(initiallyOpen) }

/**
 * Kelivo-style left drawer: drawer and main content meet at one moving seam. Both surfaces are
 * retained as graphics layers; the chat list is never remeasured during drawer motion.
 */
@Composable
internal fun FcodeInteractiveDrawer(
    state: FcodeInteractiveDrawerState,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    drawerWidthFraction: Float = 0.78f,
    minDrawerWidth: Dp = 292.dp,
    maxDrawerWidth: Dp = 360.dp,
    edgeSwipeWidth: Dp = 28.dp,
    openFromAnywhere: Boolean = true,
    scrimColor: Color,
    maxScrimAlpha: Float = 0.22f,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = state.motionActive) { progress ->
        state.beginDrag()
        try {
            progress.collect { event ->
                val targetProgress = 1f - event.progress
                state.dragBy(targetProgress - state.progress)
            }
            state.close()
        } catch (_: CancellationException) {
            // Re-open from the exact presentation value when the user cancels the gesture.
            withContext(NonCancellable) { state.open() }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val widthByFraction = maxWidth * drawerWidthFraction.coerceIn(0.6f, 0.9f)
        // Multi-window can make the complete viewport narrower than the normal minimum.
        // Keep the bounds ordered so the reusable drawer never crashes in a narrow split pane.
        val maxAllowedDrawerWidth = maxDrawerWidth.coerceAtMost(maxWidth)
        val minAllowedDrawerWidth = minDrawerWidth.coerceAtMost(maxAllowedDrawerWidth)
        val drawerWidth = widthByFraction.coerceIn(minAllowedDrawerWidth, maxAllowedDrawerWidth)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val drawerWidthPx = with(density) { drawerWidth.toPx() }
        val edgeSwipeWidthPx = with(density) { edgeSwipeWidth.toPx() }

        val dragModifier = Modifier.pointerInput(state, drawerWidthPx, edgeSwipeWidthPx, gesturesEnabled, openFromAnywhere) {
            if (!gesturesEnabled || drawerWidthPx <= 0f) return@pointerInput
            var dragAccepted = false
            var velocityTracker = VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragAccepted = state.motionActive || openFromAnywhere || offset.x <= edgeSwipeWidthPx
                    if (dragAccepted) {
                        velocityTracker = VelocityTracker()
                        state.beginDrag()
                    }
                },
                onHorizontalDrag = { change, dragAmount ->
                    if (dragAccepted) {
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        state.dragBy(dragAmount / drawerWidthPx)
                        change.consume()
                    }
                },
                onDragEnd = {
                    if (dragAccepted) {
                        val velocity = velocityTracker.calculateVelocity().x
                        scope.launch { state.settle(drawerWidthPx, velocity) }
                    }
                    dragAccepted = false
                },
                onDragCancel = {
                    if (dragAccepted) scope.launch { state.settle(drawerWidthPx, 0f) }
                    dragAccepted = false
                },
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = drawerWidthPx * state.progress
                }
                .then(dragModifier),
        ) {
            content()
            // Keep the scrim node composed, but draw alpha directly. graphicsLayer(alpha) forced a
            // full-screen offscreen buffer on every drawer frame at 1440p.
            val scrimTapModifier = if (state.motionActive) {
                Modifier.pointerInput(state) {
                    detectTapGestures(onTap = { scope.launch { state.close() } })
                }
            } else Modifier
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            color = scrimColor,
                            alpha = maxScrimAlpha.coerceIn(0f, 1f) * state.progress,
                        )
                    }
                    .then(scrimTapModifier),
            )
        }

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(drawerWidth)
                .fillMaxHeight()
                .zIndex(1f)
                .graphicsLayer {
                    translationX = -drawerWidthPx * (1f - state.progress)
                }
                .then(dragModifier),
        ) {
            drawerContent()
        }
    }
}
