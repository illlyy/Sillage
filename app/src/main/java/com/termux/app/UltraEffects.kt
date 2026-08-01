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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.runtimeShaderEffect
import kotlin.math.atan2
import kotlin.math.cos
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
 *
 * This layer lives inside the activity window and refracts the chat content through the backdrop;
 * [UltraShockwaveOverlay] adds the bright energy ring on top of every panel window.
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
    val glowColor = if (isLight) Color(0xFF4FA9FF) else Color(0xFF9DB8FF)
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
 * previewed or selected. Stars are 1-3dp squares twinkling out of phase over a slowly shifting
 * night-nebula gradient; colorful meteors sweep the panel one after another in slow motion, each
 * with a long fading pixel trail and scattered embers. Entering the sky, stars light up one by one
 * (staggered) instead of the whole layer fading in at once.
 *
 * IMPORTANT: the background canvas must never fill the whole panel on its own. The panel lives in a
 * wrap-content Popup window, so fillMaxSize() here measures against the window's max height and
 * inflates the panel into a full-screen vertical strip. Callers should pass
 * `Modifier.matchParentSize()` so this layer tracks the panel without contributing to its size.
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
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        entrance.snapTo(0f)
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(1150, easing = ULTRA_SKY_ENTRANCE_EASING),
        )
    }
    val baseStar = if (isLight) Color(0xFFEAF2FF) else Color(0xFFDCE6FF)
    val palette = listOf(
        baseStar,
        Color(0xFFFFFFFF),
        Color(0xFF7DF9FF),
        Color(0xFFFF9EDB),
        Color(0xFFFFE9A8),
        Color(0xFFB48CFF),
    )
    val stars = remember(density, isLight) {
        val random = Random(0x5C0DEF11)
        buildList {
            repeat(56) {
                // About two thirds stay white-blue; the rest draw from the vivid palette so the
                // sky reads as a colorful night field instead of a flat gray-blue wash.
                val vivid = random.nextInt(100)
                add(
                    PixelStarSpec(
                        x = random.nextFloat(),
                        y = random.nextFloat() * 0.96f,
                        sizePx = with(density) { (1.2f + random.nextFloat() * 1.8f).dp.toPx() },
                        twinkleSpeed = 0.6f + random.nextFloat() * 2.4f,
                        phase = random.nextFloat() * 6.2832f,
                        brightness = 0.5f + random.nextFloat() * 0.5f,
                        color = if (vivid < 62) palette[random.nextInt(2)] else palette[2 + random.nextInt(4)],
                        delay = random.nextFloat() * 0.72f,
                    ),
                )
            }
        }
    }
    val meteors = remember(density, isLight) {
        val random = Random(0x1DEA17CE)
        buildList {
            repeat(METEOR_COUNT) { index ->
                // Entry heights spread across the panel so concurrent meteors cross different
                // bands instead of stacking on one diagonal.
                val entryBand = index % 3
                val angle = METEOR_ANGLE_BASE + index * METEOR_ANGLE_STEP + random.nextFloat() * 4f
                add(
                    MeteorSpec(
                        phase = index * METEOR_PHASE_STEP,
                        entryBand = entryBand,
                        angleRad = Math.toRadians(angle.toDouble()).toFloat(),
                        color = METEOR_COLORS[index % METEOR_COLORS.size],
                        head = MeteorHead.entries[index % MeteorHead.entries.size],
                        sparks = buildList {
                            repeat(12) {
                                add(
                                    MeteorSparkSpec(
                                        trailOffset = 0.06f + random.nextFloat() * 0.94f,
                                        lateral = with(density) { (random.nextFloat() * 7f - 3.5f).dp.toPx() },
                                        sizeScale = 0.30f + random.nextFloat() * 0.40f,
                                        alphaScale = 0.20f + random.nextFloat() * 0.30f,
                                    ),
                                )
                            }
                        },
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
    val skyPhase by twinkle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKY_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "pixelSkyPhase",
    )
    val meteorClock by twinkle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(METEOR_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "pixelMeteorClock",
    )
    val entranceValue = entrance.value
    val skyReveal = smoothstep01(0f, 0.24f, entranceValue)
    Canvas(modifier) {
        val skyTop = cycleLerp(
            if (isLight) SKY_LIGHT_TOPS else SKY_DARK_TOPS,
            skyPhase,
        )
        val skyBottom = cycleLerp(
            if (isLight) SKY_LIGHT_BOTTOMS else SKY_DARK_BOTTOMS,
            skyPhase,
        )
        // Entrance reveal: the sky sweeps in from the right edge with a soft gradient front.
        // Stars, nebula and meteors compute their own horizontal reveal factor so the curtain
        // moves leftward, while the gradient background itself fades in as one piece: painting
        // the gradient in vertical strips quantized each strip's alpha and showed visible
        // vertical banding on the panel.
        val edge = (1f - smoothstep01(0f, 0.78f, entranceValue)) * size.width
        val band = size.width * 0.45f
        fun revealAt(x: Float): Float = smoothstep01(edge - band, edge, x)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    skyTop.copy(alpha = (if (isLight) 0.62f else 0.46f) * alpha * skyReveal),
                    skyBottom.copy(alpha = (if (isLight) 0.78f else 0.56f) * alpha * skyReveal),
                ),
                startY = 0f,
                endY = size.height,
            ),
        )
        // Nebula blobs drift slowly with the sky cycle.
        val drift = sin(skyPhase * 6.2832f)
        val drift2 = cos(skyPhase * 6.2832f)
        drawNebula(
            center = Offset(
                size.width * (0.20f + 0.07f * drift),
                size.height * (0.28f + 0.05f * drift2),
            ),
            radius = size.width * 0.55f,
            color = Color(0xFF8B5CFF),
            alpha = 0.34f,
            layerAlpha = revealAt(size.width * (0.20f + 0.07f * drift)) * alpha * skyReveal,
        )
        drawNebula(
            center = Offset(
                size.width * (0.86f + 0.06f * drift2),
                size.height * (0.60f + 0.06f * drift),
            ),
            radius = size.width * 0.48f,
            color = Color(0xFF2E9BFF),
            alpha = 0.26f,
            layerAlpha = revealAt(size.width * (0.86f + 0.06f * drift2)) * alpha * skyReveal,
        )
        drawNebula(
            center = Offset(
                size.width * (0.58f + 0.05f * drift),
                size.height * (0.82f + 0.04f * drift2),
            ),
            radius = size.width * 0.42f,
            color = Color(0xFFFF5FA2),
            alpha = 0.16f,
            layerAlpha = revealAt(size.width * (0.58f + 0.05f * drift)) * alpha * skyReveal,
        )
        stars.forEach { star ->
            val twinkleValue = 0.5f + 0.5f * sin(twinkleClock * star.twinkleSpeed + star.phase)
            // Staggered lighting: each star fades in at its own moment so the sky appears to blink
            // into existence instead of sliding in as one flat layer.
            val lit = smoothstep01(star.delay, star.delay + 0.34f, entranceValue)
            // Birth glow: while a star is lighting up it burns brighter than its resting state and
            // wears a soft halo, then settles into the normal twinkle brightness.
            val glow = (1f - lit)
            val starAlpha = (star.brightness * (0.35f + 0.65f * twinkleValue) * (0.5f + 1.5f * glow) * lit * alpha)
                .coerceIn(0f, 1f)
            val reveal = revealAt(star.x * size.width)
            if (starAlpha > 0.02f && reveal > 0.02f) {
                val pos = Offset(star.x * size.width, star.y * size.height)
                val glowAlpha = glow * 0.5f * alpha * reveal
                if (glowAlpha > 0.02f) {
                    val halo = star.sizePx * 2.7f
                    drawRect(
                        color = star.color.copy(alpha = glowAlpha),
                        topLeft = Offset(pos.x - halo / 2f, pos.y - halo / 2f),
                        size = Size(halo, halo),
                    )
                }
                drawRect(
                    color = star.color.copy(alpha = starAlpha * reveal),
                    topLeft = pos,
                    size = Size(star.sizePx, star.sizePx),
                )
            }
        }
        meteors.forEach { meteor ->
            val rel = (meteorClock - meteor.phase + 1f) % 1f
            if (rel < METEOR_TRAVEL) {
                drawMeteor(meteor, rel / METEOR_TRAVEL, alpha * skyReveal, size, ::revealAt)
            }
        }
    }
}

private fun DrawScope.drawNebula(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
    layerAlpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha * layerAlpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * One slow, dreamy meteor crossing the panel with a long pixel trail, scattered embers and a
 * colored head that takes one of several shapes (five-point star, heart, arrow triangle or plain
 * pixel square). Several meteors overlap so the sky never shows a single streak.
 */
private fun DrawScope.drawMeteor(
    meteor: MeteorSpec,
    t: Float,
    layerAlpha: Float,
    viewSize: Size,
    revealAt: (Float) -> Float,
) {
    // Slow ease-in/out travel keeps the meteor dreamy instead of a quick streak.
    val eased = smoothstep01(0f, 1f, t)
    val dir = Offset(-cos(meteor.angleRad), sin(meteor.angleRad))
    // The flight spans the whole panel width (right edge to left edge) so most of the path is
    // visible inside the panel; entry height varies per meteor to keep several in view at once.
    val xSpan = viewSize.width * 1.10f
    val span = xSpan / -dir.x
    val from = Offset(
        viewSize.width * 1.02f,
        viewSize.height * (0.05f + meteor.entryBand * 0.24f),
    )
    val head = Offset(from.x + dir.x * span * eased, from.y + dir.y * span * eased)
    val reveal = revealAt(head.x)
    if (reveal <= 0.02f) return
    val headSize = 3.4.dp.toPx()
    val spacing = 4.6.dp.toPx()
    val trailBlocks = 14
    // Head glow: a soft halo block under the bright head.
    drawRect(
        color = meteor.color.copy(alpha = 0.16f * layerAlpha * reveal),
        topLeft = Offset(head.x - headSize * 1.1f, head.y - headSize * 1.1f),
        size = Size(headSize * 2.2f, headSize * 2.2f),
    )
    for (i in 0 until trailBlocks) {
        val fade = (1f - i / trailBlocks.toFloat()).coerceIn(0f, 1f)
        val trailAlpha = fade * fade * 0.82f * layerAlpha * reveal
        if (trailAlpha <= 0.02f) continue
        val pos = Offset(head.x - dir.x * spacing * i, head.y - dir.y * spacing * i)
        val blockSize = headSize * (1f - i * 0.06f)
        drawRect(
            color = if (i == 0) {
                meteor.color.copy(alpha = layerAlpha * 0.9f * reveal)
            } else {
                lerp(meteor.color, Color.White, (i - 1) / (trailBlocks - 1).toFloat())
                    .copy(alpha = trailAlpha)
            },
            topLeft = Offset(pos.x - blockSize / 2f, pos.y - blockSize / 2f),
            size = Size(blockSize, blockSize),
        )
    }
    // Embers scattered along the trail, drifting slightly off-axis.
    meteor.sparks.forEach { spark ->
        val trailPos = Offset(
            head.x - dir.x * spacing * spark.trailOffset * trailBlocks,
            head.y - dir.y * spacing * spark.trailOffset * trailBlocks,
        )
        val lateralOffset = Offset(-dir.y, dir.x) * spark.lateral
        val emberPos = trailPos + lateralOffset
        val emberSize = headSize * spark.sizeScale
        val emberAlpha = spark.alphaScale * (1f - spark.trailOffset * 0.6f) * layerAlpha * reveal
        drawRect(
            color = meteor.color.copy(alpha = emberAlpha),
            topLeft = Offset(emberPos.x - emberSize / 2f, emberPos.y - emberSize / 2f),
            size = Size(emberSize, emberSize),
        )
    }
    // The head itself: bright shape over a colored core, oriented along the travel direction.
    val headPath = meteorHeadPath(meteor.head, head, dir, headSize)
    drawPath(headPath, meteor.color.copy(alpha = 0.9f * layerAlpha * reveal))
    drawPath(
        headPath,
        Color.White.copy(alpha = 0.92f * layerAlpha * reveal),
        style = Stroke(width = headSize * 0.18f),
    )
}

private fun meteorHeadPath(
    head: MeteorHead,
    center: Offset,
    dir: Offset,
    size: Float,
): Path {
    val perp = Offset(-dir.y, dir.x)
    fun local(u: Float, v: Float): Offset = center + dir * (u * size) + perp * (v * size)
    return when (head) {
        MeteorHead.STAR -> {
            val outerR = size * 0.62f
            val innerR = outerR * 0.382f
            val baseAngle = atan2(dir.y, dir.x)
            val path = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outerR else innerR
                val a = baseAngle + i * (Math.PI / 5).toFloat()
                val x = center.x + r * cos(a)
                val y = center.y + r * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            path
        }
        MeteorHead.HEART -> {
            // Classic heart curve in local (u, v) space, tip pointing along the travel direction.
            val path = Path()
            var first = true
            val samples = 26
            for (i in 0..samples) {
                val t = i / samples.toFloat() * 6.2832f
                val x = 16f * sin(t) * sin(t) * sin(t)
                val y = 13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t)
                // Normalize: x in [-16, 16], y in [-17, 12]; tip of the heart at the bottom (-y
                // side). Map so the tip (+u) points along the travel direction.
                val u = (y + 5f) / 34f
                val v = x / 34f
                val p = local(u * 1.1f, v * 1.1f)
                if (first) {
                    path.moveTo(p.x, p.y)
                    first = false
                } else {
                    path.lineTo(p.x, p.y)
                }
            }
            path.close()
            path
        }
        MeteorHead.TRIANGLE -> {
            val tip = center + dir * size * 0.75f
            val back = center - dir * size * 0.45f
            val halfW = size * 0.5f
            val path = Path()
            path.moveTo(tip.x, tip.y)
            path.lineTo((back + perp * halfW).x, (back + perp * halfW).y)
            path.lineTo((back - perp * halfW).x, (back - perp * halfW).y)
            path.close()
            path
        }
        MeteorHead.SQUARE -> {
            val half = size * 0.4f
            val path = Path()
            path.addRect(
                androidx.compose.ui.geometry.Rect(
                    center.x - half, center.y - half,
                    center.x + half, center.y + half,
                ),
            )
            path
        }
    }
}

private fun cycleLerp(stops: List<Color>, phase: Float): Color {
    val n = stops.size
    val scaled = (phase.coerceIn(0f, 1f)) * n
    val idx = scaled.toInt().coerceIn(0, n - 2)
    val frac = scaled - idx
    return lerp(stops[idx], stops[idx + 1], frac)
}

private fun smoothstep01(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private const val METEOR_COUNT = 6
private const val METEOR_TRAVEL = 0.5f
private const val METEOR_PHASE_STEP = 1f / METEOR_COUNT
private const val METEOR_CYCLE_MS = 12000
private const val METEOR_ANGLE_BASE = 15f
private const val METEOR_ANGLE_STEP = 5f
private const val SKY_CYCLE_MS = 16000

private val ULTRA_SKY_ENTRANCE_EASING = CubicBezierEasing(0.16f, 0.64f, 0.22f, 1f)

private val METEOR_COLORS = listOf(
    Color(0xFF6FF3FF),
    Color(0xFFFF9EDB),
    Color(0xFFFFE9A8),
    Color(0xFFC9A7FF),
)

private val SKY_LIGHT_TOPS = listOf(
    Color(0xFF232D5E),
    Color(0xFF2B2768),
    Color(0xFF1F3A6B),
    Color(0xFF2A1E5C),
    Color(0xFF232D5E),
)

private val SKY_LIGHT_BOTTOMS = listOf(
    Color(0xFF0B0F2A),
    Color(0xFF14103A),
    Color(0xFF0A1A3A),
    Color(0xFF150D33),
    Color(0xFF0B0F2A),
)

private val SKY_DARK_TOPS = listOf(
    Color(0xFF101735),
    Color(0xFF191543),
    Color(0xFF0E2247),
    Color(0xFF181040),
    Color(0xFF101735),
)

private val SKY_DARK_BOTTOMS = listOf(
    Color(0xFF070B1E),
    Color(0xFF0C0930),
    Color(0xFF06132C),
    Color(0xFF0D0828),
    Color(0xFF070B1E),
)

private data class PixelStarSpec(
    val x: Float,
    val y: Float,
    val sizePx: Float,
    val twinkleSpeed: Float,
    val phase: Float,
    val brightness: Float,
    val color: Color,
    val delay: Float,
)

private data class MeteorSpec(
    val phase: Float,
    val entryBand: Int,
    val angleRad: Float,
    val color: Color,
    val head: MeteorHead,
    val sparks: List<MeteorSparkSpec>,
)

/** Shape of a meteor's bright head; different meteors get different shapes. */
private enum class MeteorHead { STAR, HEART, TRIANGLE, SQUARE }

private data class MeteorSparkSpec(
    val trailOffset: Float,
    val lateral: Float,
    val sizeScale: Float,
    val alphaScale: Float,
)

private fun farthestCornerRadius(size: Size, center: Offset): Float {
    val dx = maxOf(center.x, size.width - center.x)
    val dy = maxOf(center.y, size.height - center.y)
    return hypot(dx, dy) + 2f
}
