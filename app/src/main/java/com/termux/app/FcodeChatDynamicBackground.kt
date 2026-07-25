package com.termux.app

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.runtimeShaderEffect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * Draws the first animated chat-background material over an already captured wallpaper layer.
 *
 * The source backdrop stays crisp while the caller displays a cached blurred copy underneath.
 * A full-screen shader samples the crisp source and makes only the expanding circle visible.
 * This is the important distinction from simply painting a translucent circle over a blurred
 * image: every pixel inside the circle is replaced by the clear glass sample, so the blur disappears
 * behind it. At progress 1 the circle is larger than the farthest corner and remains in place.
 */
@Composable
internal fun FcodeChatDynamicBackground(
    modifier: Modifier,
    backdrop: Backdrop,
    config: FcodeChatDynamicBackgroundConfig,
    animationKey: Any,
    autoStartAllowed: Boolean = true,
    manualStartSignal: Long = 0L,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isLightTheme = rememberIsLightTheme()
    val latestConfig by rememberUpdatedState(config)
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) <= 0f
        }.getOrDefault(false)
    }
    val revealProgress = remember(animationKey) { Animatable(if (reducedMotion) 1f else 0f) }
    val dropletProgress = remember(animationKey) { Animatable(if (reducedMotion) 1f else 0f) }
    var expansionStarted by remember(animationKey) { mutableStateOf(reducedMotion) }

    fun startExpansion() {
        if (!latestConfig.enabled || expansionStarted) return
        expansionStarted = true
        val animationConfig = latestConfig
        scope.launch {
            if (reducedMotion) {
                revealProgress.snapTo(1f)
                dropletProgress.snapTo(1f)
                return@launch
            }
            // Let the droplet acquire a readable shape before the large reveal begins. The two
            // motions still overlap, so there is no stationary beat or spring->tween hand-off.
            coroutineScope {
                launch {
                    dropletProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f),
                    )
                }
                launch {
                    delay(DYNAMIC_REVEAL_OVERLAP_DELAY_MS)
                    revealProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = animationConfig.expansionDurationMs,
                            easing = DYNAMIC_REVEAL_EASING,
                        ),
                    )
                }
            }
        }
    }

    LaunchedEffect(animationKey, config.enabled, autoStartAllowed) {
        expansionStarted = false
        if (!config.enabled) {
            revealProgress.snapTo(1f)
            dropletProgress.snapTo(1f)
            return@LaunchedEffect
        }
        revealProgress.snapTo(if (reducedMotion) 1f else 0f)
        dropletProgress.snapTo(if (reducedMotion) 1f else 0f)
        if (reducedMotion) {
            expansionStarted = true
            return@LaunchedEffect
        }
        if (!autoStartAllowed) return@LaunchedEffect
        // Wait until the first laid-out chat frame is visible. This avoids revealing the
        // wallpaper while the route is still mounting and makes the delay feel intentional.
        withFrameNanos { }
        delay(config.autoStartDelayMs.toLong())
        startExpansion()
    }

    LaunchedEffect(manualStartSignal, autoStartAllowed, config.enabled) {
        if (manualStartSignal > 0L && autoStartAllowed && config.enabled) startExpansion()
    }

    if (!config.enabled || !liquidGlassSupported) return

    val tint = if (isLightTheme) {
        Color.White.copy(alpha = 0.035f)
    } else {
        Color.Black.copy(alpha = 0.055f)
    }
    Box(modifier.fillMaxSize()) {
        // Do not leave a clear seed visible during the initial delay. The glass is composed only
        // after an automatic or manual start request, then remains composed at progress 1.
        if (expansionStarted) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val parentWidthPx = with(density) { maxWidth.toPx() }
                val parentHeightPx = with(density) { maxHeight.toPx() }
                val targetCenter = Offset(parentWidthPx * DYNAMIC_ORIGIN_X, parentHeightPx * DYNAMIC_ORIGIN_Y)
                val maxRadius = farthestCornerRadius(
                    androidx.compose.ui.geometry.Size(parentWidthPx, parentHeightPx),
                    targetCenter,
                )
                val progress = revealProgress.value.coerceIn(0f, 1f)
                // Do not clip the spring at 1: the small overshoot is the visible Q-bounce. The
                // previous clamp turned the spring into a hard stop and made the entrance stiff.
                val droplet = dropletProgress.value.coerceIn(0f, DYNAMIC_DROPLET_MAX_SCALE)
                val seedRadius = DYNAMIC_SEED_RADIUS_PX(density)
                val radius =
                    seedRadius * droplet +
                        (maxRadius - seedRadius) * progress
                // Refraction is strongest while the boundary is travelling, then settles back to
                // the user's configured glass recipe as the circle passes beyond the corners.
                val edgeEnergy = 1f - smoothstep(0.45f, 0.96f, progress)
                val liveRefractionHeight =
                    config.refractionHeightDp * (1f + 0.22f * edgeEnergy)
                val liveRefractionAmount =
                    config.refractionAmountDp * (1f + 0.32f * edgeEnergy)

                // Keep the render target at the actual viewport size. The previous implementation
                // rendered a max-dimension square (3200x3200 on the QA device) and scaled it every
                // frame. This shader moves the circular boundary internally, preserving the same
                // material while avoiding the huge offscreen texture and its resampling cost.
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawPlainBackdrop(
                            backdrop = backdrop,
                            shape = { androidx.compose.ui.graphics.RectangleShape },
                            effects = {
                                runtimeShaderEffect(
                                    key = DYNAMIC_GLASS_SHADER_KEY,
                                    shaderString = DYNAMIC_GLASS_SHADER,
                                    uniformShaderName = "content",
                                ) {
                                    setFloatUniform("size", size.width, size.height)
                                    setFloatUniform("center", targetCenter.x, targetCenter.y)
                                    setFloatUniform("radius", radius)
                                    setFloatUniform(
                                        "refractionHeight",
                                        liveRefractionHeight.dp.toPx().coerceAtLeast(0.001f),
                                    )
                                    setFloatUniform(
                                        "refractionAmount",
                                        liveRefractionAmount.dp.toPx(),
                                    )
                                    setFloatUniform(
                                        "chromaticAberration",
                                        if (config.chromaticAberration) 1f else 0f,
                                    )
                                    setFloatUniform(
                                        "edgeSoftness",
                                        max(1.25f, density.density * 0.45f),
                                    )
                                    setColorUniform("tint", tint)
                                }
                            },
                        ),
                )
            }
        }
    }
}

private val DYNAMIC_REVEAL_EASING = CubicBezierEasing(
    0.22f,
    0.62f,
    0.24f,
    1f,
)

private const val DYNAMIC_REVEAL_OVERLAP_DELAY_MS = 110L
private const val DYNAMIC_DROPLET_MAX_SCALE = 1.12f
private const val DYNAMIC_GLASS_SHADER_KEY = "FcodeDynamicCircularGlassV2"

private const val DYNAMIC_GLASS_SHADER = """
    uniform shader content;
    uniform float2 size;
    uniform float2 center;
    uniform float radius;
    uniform float refractionHeight;
    uniform float refractionAmount;
    uniform float chromaticAberration;
    uniform float edgeSoftness;
    layout(color) uniform half4 tint;

    float circleMap(float x) {
        float clamped = clamp(x, 0.0, 1.0);
        return 1.0 - sqrt(max(0.0, 1.0 - clamped * clamped));
    }

    half4 glassColor(half4 sampled) {
        half luminance = dot(sampled.rgb, half3(0.213, 0.715, 0.072));
        sampled.rgb = mix(half3(luminance), sampled.rgb, 1.12);
        sampled.rgb = mix(sampled.rgb, tint.rgb, tint.a);
        return sampled;
    }

    half4 main(float2 coord) {
        float2 delta = coord - center;
        float distanceToCenter = max(length(delta), 0.001);
        float2 normal = delta / distanceToCenter;
        float signedDistance = distanceToCenter - radius;
        float coverage = 1.0 - smoothstep(-edgeSoftness, edgeSoftness, signedDistance);
        if (coverage <= 0.001) {
            return half4(0.0);
        }

        float insideDepth = max(-signedDistance, 0.0);
        float edgePhase = 1.0 - clamp(insideDepth / refractionHeight, 0.0, 1.0);
        float displacement = circleMap(edgePhase) * refractionAmount;
        float2 refractedCoord = coord + normal * displacement;

        half4 sampled;
        if (chromaticAberration > 0.5 && edgePhase > 0.001) {
            float dispersion = displacement * 0.065 * edgePhase;
            half4 middle = content.eval(refractedCoord);
            half4 red = content.eval(refractedCoord + normal * dispersion);
            half4 blue = content.eval(refractedCoord - normal * dispersion);
            sampled = half4(red.r, middle.g, blue.b, middle.a);
        } else {
            sampled = content.eval(refractedCoord);
        }
        sampled = glassColor(sampled);

        float2 lightDirection = normalize(float2(-0.68, -0.74));
        float sheen = pow(max(dot(normal, lightDirection), 0.0), 5.0);
        float shade = pow(max(dot(normal, -lightDirection), 0.0), 4.0);
        float edgeSheen = edgePhase * edgePhase;
        sampled.rgb = clamp(
            sampled.rgb + half3(sheen * edgeSheen * 0.14 - shade * edgeSheen * 0.035),
            half3(0.0),
            half3(1.0)
        );

        return sampled * half(coverage);
    }
"""

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A self-contained preview variant used by the chat-background settings page. */
@Composable
internal fun FcodeChatDynamicBackgroundPreview(
    modifier: Modifier,
    style: FcodeChatBackgroundStyle,
    imagePath: String,
    imageDim: Float,
    config: FcodeChatDynamicBackgroundConfig,
    animationKey: Any,
) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .fcodeDynamicBackgroundBlur(config)
                .layerBackdrop(backdrop),
        ) {
            FcodeChatBackdrop(
                modifier = Modifier.fillMaxSize(),
                style = style,
                customImagePath = imagePath,
                customImageDim = imageDim,
                customImageMaxDimension = 1280,
            )
        }
        FcodeChatDynamicBackground(
            modifier = Modifier.fillMaxSize(),
            backdrop = backdrop,
            config = config,
            animationKey = animationKey,
        )
    }
}

/**
 * Displays the wallpaper through a cached graphics-layer blur while [layerBackdrop] (placed after
 * this modifier) still records the crisp source for the reveal shader. Keeping the blur on the
 * source layer avoids re-running a second full-screen backdrop shader on every animation frame.
 */
internal fun Modifier.fcodeDynamicBackgroundBlur(
    config: FcodeChatDynamicBackgroundConfig,
): Modifier {
    return if (
        config.enabled &&
        liquidGlassSupported &&
        config.initialBlurDp > 0f
    ) {
        blur(
            radius = config.initialBlurDp.dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )
    } else {
        this
    }
}

private const val DYNAMIC_ORIGIN_X = 0.43f
private const val DYNAMIC_ORIGIN_Y = 0.46f

private fun DYNAMIC_SEED_RADIUS_PX(density: Density): Float = with(density) { 18.dp.toPx() }

private fun farthestCornerRadius(size: androidx.compose.ui.geometry.Size, center: Offset): Float {
    val dx = max(center.x, size.width - center.x)
    val dy = max(center.y, size.height - center.y)
    return hypot(dx, dy) + 2f
}
