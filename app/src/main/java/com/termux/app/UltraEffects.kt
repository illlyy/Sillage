package com.termux.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.runtimeShaderEffect
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** A burst request: selecting the Ultra effort from the thinking-intensity tool. */
data class UltraBurstRequest(
    val token: Long,
    val originWindowRect: Rect,
)

/**
 * Full-screen "glass overdrive" shockwave played when Ultra is selected. A glass ring refracts,
 * disperses (chromatic aberration) and glows its way out from the effort button, overdriving the
 * same liquid-glass material family as the dynamic chat background, then fades before it fully
 * exits the corners. Degrades to plain glowing rings when runtime shaders are unavailable.
 */
@Composable
fun UltraGlassShockwave(
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    requestToken: Long,
    originWindowRect: Rect,
    rootWindowRect: Rect,
    onFinished: () -> Unit,
) {
    if (requestToken <= 0L) return
    val density = LocalDensity.current
    val isLight = rememberIsLightTheme()
    val glowColor = if (isLight) Color(0xFF5C9BFF) else Color(0xFF9DB8FF)
    val latestOnFinished by rememberUpdatedState(onFinished)
    val progress = remember(requestToken) { Animatable(0f) }
    val origin = if (rootWindowRect != Rect.Zero) {
        Offset(
            originWindowRect.center.x - rootWindowRect.left,
            originWindowRect.center.y - rootWindowRect.top,
        )
    } else null
    val seedRadiusPx = with(density) { 14.dp.toPx() }

    LaunchedEffect(requestToken) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(880, easing = ULTRA_BURST_EASING),
        )
        latestOnFinished()
    }

    Box(modifier) {
        val p = progress.value.coerceIn(0f, 1f)
        // The ring fades out only after it has travelled well past the screen corners.
        val energy = ((1f - p) / 0.38f).coerceIn(0f, 1f)
        val energySoft = energy * energy
        if (liquidGlassSupported) {
            val thicknessPx = with(density) { (28.dp * (0.35f + 0.65f * energy)).toPx() }
            val refractionPx = with(density) { (30.dp * energySoft).toPx() } + 2f
            Box(
                Modifier
                    .fillMaxSize()
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            runtimeShaderEffect(
                                key = ULTRA_SHOCKWAVE_SHADER_KEY,
                                shaderString = ULTRA_SHOCKWAVE_SHADER,
                                uniformShaderName = "content",
                            ) {
                                val sizePx = Size(size.width.toFloat(), size.height.toFloat())
                                val center = origin ?: Offset(sizePx.width * 0.5f, sizePx.height * 0.46f)
                                val maxRadius = farthestCornerRadius(sizePx, center) * 1.16f
                                setFloatUniform("size", sizePx.width, sizePx.height)
                                setFloatUniform("center", center.x, center.y)
                                setFloatUniform("radius", seedRadiusPx + (maxRadius - seedRadiusPx) * p)
                                setFloatUniform("thickness", thicknessPx)
                                setFloatUniform("refractionAmount", refractionPx)
                                setFloatUniform("energy", energy)
                                setFloatUniform("glowStrength", 0.62f)
                                setColorUniform("glowColor", glowColor)
                            }
                        },
                    ),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val sizePx = size
                val center = origin ?: Offset(sizePx.width * 0.5f, sizePx.height * 0.46f)
                val maxRadius = farthestCornerRadius(sizePx, center) * 1.16f
                val radius = seedRadiusPx + (maxRadius - seedRadiusPx) * p
                drawCircle(
                    color = glowColor.copy(alpha = 0.5f * energy),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 14.dp.toPx() * (0.5f + 0.5f * energy)),
                )
                drawCircle(
                    color = glowColor.copy(alpha = 0.16f * energy),
                    radius = radius * 0.86f,
                    center = center,
                    style = Stroke(width = 26.dp.toPx() * energy),
                )
            }
        }
    }
}

private val ULTRA_BURST_EASING = CubicBezierEasing(0.04f, 0.74f, 0.10f, 1f)

private const val ULTRA_SHOCKWAVE_SHADER_KEY = "FcodeUltraShockwaveV1"

private const val ULTRA_SHOCKWAVE_SHADER = """
    uniform shader content;
    uniform float2 size;
    uniform float2 center;
    uniform float radius;
    uniform float thickness;
    uniform float refractionAmount;
    uniform float energy;
    uniform float glowStrength;
    layout(color) uniform half4 glowColor;

    half4 main(float2 coord) {
        float2 delta = coord - center;
        float dist = max(length(delta), 0.001);
        float2 normal = delta / dist;
        float band = abs(dist - radius);
        float thick = max(thickness, 0.001);
        float mainRing = 1.0 - smoothstep(0.0, thick, band);
        float echo = (1.0 - smoothstep(0.0, thick * 1.6, abs(dist - radius * 0.86))) * 0.45;
        float ring = max(mainRing, echo) * energy;
        if (ring <= 0.001) {
            return content.eval(coord);
        }
        float displacement = ring * refractionAmount;
        float2 refracted = coord + normal * displacement;
        float dispersion = displacement * 0.12;
        half4 middle = content.eval(refracted);
        half4 red = content.eval(refracted + normal * dispersion);
        half4 blue = content.eval(refracted - normal * dispersion);
        half4 sampled = half4(red.r, middle.g, blue.b, middle.a);
        float leading = smoothstep(thick * 0.55, 0.0, band) * energy;
        sampled.rgb += glowColor.rgb * (leading * glowStrength);
        sampled.rgb += glowColor.rgb * ((1.0 - smoothstep(0.0, thick * 0.4, band)) * energy * 0.10);
        return sampled;
    }
"""

/**
 * Pixel-styled starry sky shown inside the thinking-intensity panel only while the Ultra effort is
 * previewed or selected. Stars are 1-2dp squares that twinkle out of phase; a meteor diagonally
 * sweeps the panel on a fixed cycle with a fading square-pixel trail.
 */
@Composable
fun PixelStarrySky(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(420),
        label = "pixelStarrySkyAlpha",
    )
    if (alpha < 0.01f) return
    val density = LocalDensity.current
    val isLight = rememberIsLightTheme()
    val starColor = if (isLight) Color(0xFFE8F1FF) else Color(0xFFD9E5FF)
    val skyTint = Color(0xFF070B18)
    val stars = remember(density) {
        val random = Random(0x5C0DEF11)
        buildList {
            repeat(52) {
                add(
                    PixelStarSpec(
                        x = random.nextFloat(),
                        y = random.nextFloat() * 0.96f,
                        sizePx = with(density) { (1f + random.nextFloat() * 1.4f).dp.toPx() },
                        twinkleSpeed = 0.6f + random.nextFloat() * 2.2f,
                        phase = random.nextFloat() * 6.2832f,
                        brightness = 0.45f + random.nextFloat() * 0.55f,
                    ),
                )
            }
        }
    }
    val twinkle = rememberInfiniteTransition(label = "pixelStarryTwinkle")
    val twinkleClock by twinkle.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
        label = "pixelStarryClock",
    )
    val meteorCycle by twinkle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5600, easing = LinearEasing), RepeatMode.Restart),
        label = "pixelMeteorCycle",
    )
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    skyTint.copy(alpha = (if (isLight) 0.26f else 0.10f) * alpha),
                    skyTint.copy(alpha = (if (isLight) 0.40f else 0.18f) * alpha),
                ),
            ),
        )
        stars.forEach { star ->
            val twinkleValue = 0.5f + 0.5f * sin(twinkleClock * star.twinkleSpeed + star.phase)
            val starAlpha = (star.brightness * (0.35f + 0.65f * twinkleValue) * alpha).coerceIn(0f, 1f)
            if (starAlpha > 0.02f) {
                drawRect(
                    color = starColor.copy(alpha = starAlpha),
                    topLeft = Offset(star.x * size.width, star.y * size.height),
                    size = Size(star.sizePx, star.sizePx),
                )
            }
        }
        if (meteorCycle < METEOR_ACTIVE_WINDOW) {
            val t = meteorCycle / METEOR_ACTIVE_WINDOW
            val from = Offset(size.width * 0.94f, -size.height * 0.06f)
            val to = Offset(size.width * 0.04f, size.height * 0.92f)
            val direction = Offset(to.x - from.x, to.y - from.y)
            val length = hypot(direction.x, direction.y).coerceAtLeast(0.001f)
            val unit = Offset(direction.x / length, direction.y / length)
            val head = Offset(from.x + direction.x * t, from.y + direction.y * t)
            val headSize = 2.6.dp.toPx()
            val spacing = 3.6.dp.toPx()
            for (i in 0 until 8) {
                val fade = (1f - i / 8f).coerceIn(0f, 1f)
                val trailAlpha = fade * fade * 0.9f * alpha
                if (trailAlpha <= 0.02f) continue
                val pos = Offset(head.x - unit.x * spacing * i, head.y - unit.y * spacing * i)
                val blockSize = headSize * (1f - i * 0.09f)
                drawRect(
                    color = if (i == 0) Color.White.copy(alpha = alpha) else starColor.copy(alpha = trailAlpha),
                    topLeft = Offset(pos.x - blockSize / 2f, pos.y - blockSize / 2f),
                    size = Size(blockSize, blockSize),
                )
            }
        }
    }
}

private const val METEOR_ACTIVE_WINDOW = 0.20f

private data class PixelStarSpec(
    val x: Float,
    val y: Float,
    val sizePx: Float,
    val twinkleSpeed: Float,
    val phase: Float,
    val brightness: Float,
)

private fun farthestCornerRadius(size: Size, center: Offset): Float {
    val dx = maxOf(center.x, size.width - center.x)
    val dy = maxOf(center.y, size.height - center.y)
    return hypot(dx, dy) + 2f
}
