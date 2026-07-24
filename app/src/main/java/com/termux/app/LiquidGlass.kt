package com.termux.app

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/**
 * Adjustable liquid-glass parameters, calibrated to the Backdrop catalog recipes
 * (vendor/AndroidLiquidGlass): DialogContent uses a theme-dependent blur (light 16dp / dark 8dp)
 * and components such as LiquidBottomTabs use lens(24dp, 24dp+). Persisted in SharedPreferences
 * so the developer options can tune the chat input's glass live.
 */
@Immutable
data class LiquidGlassSpec(
    val cornerRadiusDp: Float = DEFAULT_CORNER_RADIUS,
    val blurRadiusDp: Float = DEFAULT_BLUR,
    val refractionHeightDp: Float = DEFAULT_REFRACTION_HEIGHT,
    val refractionAmountDp: Float = DEFAULT_REFRACTION_AMOUNT,
    val chromaticAberration: Boolean = DEFAULT_CHROMATIC,
) {
    companion object {
        const val DEFAULT_CORNER_RADIUS = 28f
        const val DEFAULT_BLUR = 10f
        const val DEFAULT_REFRACTION_HEIGHT = 24f
        const val DEFAULT_REFRACTION_AMOUNT = 24f
        const val DEFAULT_CHROMATIC = false

        const val KEY_ENABLED = "liquid_glass_enabled"
        const val KEY_CORNER_RADIUS = "liquid_glass_corner_radius"
        const val KEY_BLUR = "liquid_glass_blur_dp"
        const val KEY_REFRACTION_HEIGHT = "liquid_glass_refraction_height_dp"
        const val KEY_REFRACTION_AMOUNT = "liquid_glass_refraction_amount_dp"
        const val KEY_CHROMATIC = "liquid_glass_chromatic"
    }
}

/** Result of reading the liquid-glass developer settings. */
@Immutable
data class LiquidGlassConfig(val enabled: Boolean, val spec: LiquidGlassSpec)



@Immutable
data class UserBubbleLiquidGlassConfig(
    val enabled: Boolean,
    val spec: LiquidGlassSpec,
    val tintAlpha: Float,
) {
    companion object {
        const val DEFAULT_CORNER_RADIUS = 22f
        const val DEFAULT_BLUR = 7f
        const val DEFAULT_REFRACTION_HEIGHT = 16f
        const val DEFAULT_REFRACTION_AMOUNT = 20f
        const val DEFAULT_CHROMATIC = false
        const val DEFAULT_TINT_ALPHA = 0.24f

        const val KEY_ENABLED = "user_bubble_liquid_glass_enabled"
        const val KEY_CORNER_RADIUS = "user_bubble_liquid_glass_corner_radius"
        const val KEY_BLUR = "user_bubble_liquid_glass_blur_dp"
        const val KEY_REFRACTION_HEIGHT = "user_bubble_liquid_glass_refraction_height_dp"
        const val KEY_REFRACTION_AMOUNT = "user_bubble_liquid_glass_refraction_amount_dp"
        const val KEY_CHROMATIC = "user_bubble_liquid_glass_chromatic"
        const val KEY_TINT_ALPHA = "user_bubble_liquid_glass_tint_alpha"
    }
}

/**
 * Progressive top-bar material copied from the AndroidLiquidGlass AlphaMask recipe. Unlike a
 * translucent card, the blur and tint themselves fade out toward the bottom edge.
 */
@Immutable
data class TopBarLiquidGlassConfig(
    val enabled: Boolean = true,
    val blurRadiusDp: Float = DEFAULT_BLUR,
    val tintIntensity: Float = DEFAULT_TINT_INTENSITY,
    val maskHeightDp: Float = DEFAULT_MASK_HEIGHT,
    val maskStartFraction: Float = DEFAULT_MASK_START,
    val maskEndFraction: Float = DEFAULT_MASK_END,
    val topAlpha: Float = DEFAULT_TOP_ALPHA,
    val bottomAlpha: Float = DEFAULT_BOTTOM_ALPHA,
) {
    companion object {
        const val DEFAULT_BLUR = 10f
        const val DEFAULT_TINT_INTENSITY = 0.20f
        const val DEFAULT_MASK_HEIGHT = 128f
        const val MIN_MASK_HEIGHT = 104f
        const val MAX_MASK_HEIGHT = 196f
        const val DEFAULT_MASK_START = 0.5f
        const val DEFAULT_MASK_END = 1f
        const val DEFAULT_TOP_ALPHA = 1f
        const val DEFAULT_BOTTOM_ALPHA = 0f

        const val KEY_ENABLED = "top_bar_liquid_glass_enabled"
        const val KEY_BLUR = "top_bar_liquid_glass_blur_dp"
        const val KEY_TINT_INTENSITY = "top_bar_liquid_glass_tint_intensity"
        const val KEY_MASK_HEIGHT = "top_bar_liquid_glass_mask_height_dp"
        const val KEY_MASK_START = "top_bar_liquid_glass_mask_start"
        const val KEY_MASK_END = "top_bar_liquid_glass_mask_end"
        const val KEY_TOP_ALPHA = "top_bar_liquid_glass_top_alpha"
        const val KEY_BOTTOM_ALPHA = "top_bar_liquid_glass_bottom_alpha"
    }
}

/** Real liquid glass needs RenderEffect / RuntimeShader, available from Android 12 (API 31). */
val liquidGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= 31

/**
 * Reads the developer settings. Deliberately not memoized with a stable key: SharedPreferences
 * reads are cheap in-memory lookups, and re-reading each recomposition lets tweaks made in the
 * settings screen apply without a restart. [LiquidGlassSpec] is a data class, so unchanged values
 * do not trigger downstream recomposition.
 */
fun readLiquidGlassConfig(context: Context): LiquidGlassConfig {
    val prefs = context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE)
    return LiquidGlassConfig(
        enabled = prefs.getBoolean(LiquidGlassSpec.KEY_ENABLED, true),
        spec = LiquidGlassSpec(
            cornerRadiusDp = prefs.getFloat(LiquidGlassSpec.KEY_CORNER_RADIUS, LiquidGlassSpec.DEFAULT_CORNER_RADIUS),
            blurRadiusDp = prefs.getFloat(LiquidGlassSpec.KEY_BLUR, LiquidGlassSpec.DEFAULT_BLUR),
            refractionHeightDp = prefs.getFloat(LiquidGlassSpec.KEY_REFRACTION_HEIGHT, LiquidGlassSpec.DEFAULT_REFRACTION_HEIGHT),
            refractionAmountDp = prefs.getFloat(LiquidGlassSpec.KEY_REFRACTION_AMOUNT, LiquidGlassSpec.DEFAULT_REFRACTION_AMOUNT),
            chromaticAberration = prefs.getBoolean(LiquidGlassSpec.KEY_CHROMATIC, LiquidGlassSpec.DEFAULT_CHROMATIC),
        ),
    )
}

fun readUserBubbleLiquidGlassConfig(context: Context): UserBubbleLiquidGlassConfig {
    val prefs = context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE)
    return UserBubbleLiquidGlassConfig(
        enabled = prefs.getBoolean(UserBubbleLiquidGlassConfig.KEY_ENABLED, true),
        spec = LiquidGlassSpec(
            cornerRadiusDp = prefs.getFloat(UserBubbleLiquidGlassConfig.KEY_CORNER_RADIUS, UserBubbleLiquidGlassConfig.DEFAULT_CORNER_RADIUS),
            blurRadiusDp = prefs.getFloat(UserBubbleLiquidGlassConfig.KEY_BLUR, UserBubbleLiquidGlassConfig.DEFAULT_BLUR),
            refractionHeightDp = prefs.getFloat(UserBubbleLiquidGlassConfig.KEY_REFRACTION_HEIGHT, UserBubbleLiquidGlassConfig.DEFAULT_REFRACTION_HEIGHT),
            refractionAmountDp = prefs.getFloat(UserBubbleLiquidGlassConfig.KEY_REFRACTION_AMOUNT, UserBubbleLiquidGlassConfig.DEFAULT_REFRACTION_AMOUNT),
            chromaticAberration = prefs.getBoolean(UserBubbleLiquidGlassConfig.KEY_CHROMATIC, UserBubbleLiquidGlassConfig.DEFAULT_CHROMATIC),
        ),
        tintAlpha = prefs.getFloat(UserBubbleLiquidGlassConfig.KEY_TINT_ALPHA, UserBubbleLiquidGlassConfig.DEFAULT_TINT_ALPHA).coerceIn(0.08f, 0.58f),
    )
}

fun readTopBarLiquidGlassConfig(context: Context): TopBarLiquidGlassConfig {
    val prefs = context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE)
    val start = prefs.getFloat(TopBarLiquidGlassConfig.KEY_MASK_START, TopBarLiquidGlassConfig.DEFAULT_MASK_START)
        .coerceIn(0f, 0.92f)
    val end = prefs.getFloat(TopBarLiquidGlassConfig.KEY_MASK_END, TopBarLiquidGlassConfig.DEFAULT_MASK_END)
        .coerceIn(start + 0.04f, 1f)
    return TopBarLiquidGlassConfig(
        enabled = prefs.getBoolean(TopBarLiquidGlassConfig.KEY_ENABLED, true),
        blurRadiusDp = prefs.getFloat(TopBarLiquidGlassConfig.KEY_BLUR, TopBarLiquidGlassConfig.DEFAULT_BLUR).coerceIn(0f, 32f),
        tintIntensity = prefs.getFloat(TopBarLiquidGlassConfig.KEY_TINT_INTENSITY, TopBarLiquidGlassConfig.DEFAULT_TINT_INTENSITY).coerceIn(0f, 0.72f),
        maskHeightDp = prefs.getFloat(TopBarLiquidGlassConfig.KEY_MASK_HEIGHT, TopBarLiquidGlassConfig.DEFAULT_MASK_HEIGHT)
            .coerceIn(TopBarLiquidGlassConfig.MIN_MASK_HEIGHT, TopBarLiquidGlassConfig.MAX_MASK_HEIGHT),
        maskStartFraction = start,
        maskEndFraction = end,
        topAlpha = prefs.getFloat(TopBarLiquidGlassConfig.KEY_TOP_ALPHA, TopBarLiquidGlassConfig.DEFAULT_TOP_ALPHA).coerceIn(0f, 1f),
        bottomAlpha = prefs.getFloat(TopBarLiquidGlassConfig.KEY_BOTTOM_ALPHA, TopBarLiquidGlassConfig.DEFAULT_BOTTOM_ALPHA).coerceIn(0f, 1f),
    )
}

/** Whether the active Material3 scheme is light, derived from the background luminance. */
@Composable
fun rememberIsLightTheme(): Boolean {
    val background = MaterialTheme.colorScheme.background
    return remember(background) {
        (0.299f * background.red + 0.587f * background.green + 0.114f * background.blue) > 0.5f
    }
}

/**
 * Apple-style glass tint that follows the Material3 light/dark scheme: frosted white in light
 * theme, frosted black in dark theme. The backdrop blur/vibrancy do the heavy lifting; this is
 * just the translucent base coat drawn over the sampled content.
 */
@Composable
fun rememberLiquidGlassTint(): Color {
    // Keep the surface coat subordinate to the sampled backdrop, but neutral enough that a vivid
    // wallpaper does not recolor every control. This sits between the earlier card-like 55% coat
    // and the overly chromatic 30% experiment; blur and refraction remain clearly visible.
    return if (rememberIsLightTheme()) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.36f)
}

/**
 * Applies the liquid-glass effect stack for the given spec inside a drawBackdrop scope. The blur
 * is theme-dependent (light x1.6 / dark x0.8) to match the reference DialogContent recipe, which
 * yields light 16dp / dark 8dp at the default base of 10dp - enough to frost the content behind.
 */
fun BackdropEffectScope.applyLiquidGlassEffects(
    spec: LiquidGlassSpec,
    isLight: Boolean,
    depthEffect: Boolean = true,
) {
    vibrancy()
    val blurDp = spec.blurRadiusDp * (if (isLight) 1.6f else 0.8f)
    blur(blurDp.dp.toPx())
    lens(
        refractionHeight = spec.refractionHeightDp.dp.toPx(),
        refractionAmount = spec.refractionAmountDp.dp.toPx(),
        depthEffect = depthEffect,
        chromaticAberration = spec.chromaticAberration,
    )
}

/**
 * Interactive action recipe adapted from AndroidLiquidGlass/LiquidBottomTabs. The resting action
 * remains readable, while press progress increases lens depth and chromatic separation so the
 * sampled backdrop appears to gather into the control instead of merely scaling a flat circle.
 */
fun BackdropEffectScope.applyInteractiveLiquidActionEffects(
    spec: LiquidGlassSpec,
    isLight: Boolean,
    pressProgress: Float,
) {
    val progress = pressProgress.coerceIn(0f, 1f)
    vibrancy()
    val blurDp = spec.blurRadiusDp * (if (isLight) 1.6f else 0.8f)
    blur(blurDp.dp.toPx())
    lens(
        refractionHeight = (spec.refractionHeightDp * (0.72f + 0.48f * progress)).dp.toPx(),
        refractionAmount = (spec.refractionAmountDp * (0.72f + 0.58f * progress)).dp.toPx(),
        depthEffect = true,
        // The reference bottom-tab selection lens always enables chromatic separation.
        chromaticAberration = true,
    )
}

/**
 * The reference shader uses `smoothstep(size.y, size.y * .5, coord.y)`. The two fractions below
 * expose those edges while preserving the same reversed-smoothstep semantics: solid at the top,
 * continuously transparent at the bottom. Tint is mixed into the already masked backdrop, so it
 * can never turn into an opaque white card at the fade edge.
 */
fun BackdropEffectScope.applyTopBarProgressiveGlass(
    config: TopBarLiquidGlassConfig,
    tint: Color,
) {
    blur(config.blurRadiusDp.dp.toPx())
    runtimeShaderEffect(
        "FcodeTopBarAlphaMaskV1",
        """
            uniform shader content;
            uniform float2 size;
            layout(color) uniform half4 tint;
            uniform float tintIntensity;
            uniform float maskStart;
            uniform float maskEnd;
            uniform float topAlpha;
            uniform float bottomAlpha;

            half4 main(float2 coord) {
                float fade = smoothstep(maskEnd * size.y, maskStart * size.y, coord.y);
                float maskAlpha = mix(bottomAlpha, topAlpha, fade);
                half4 sampled = content.eval(coord) * maskAlpha;
                half4 coated = tint * maskAlpha;
                return mix(sampled, coated, tintIntensity);
            }
        """.trimIndent(),
        "content",
    ) {
        setFloatUniform("size", size.width, size.height)
        setColorUniform("tint", tint)
        setFloatUniform("tintIntensity", config.tintIntensity)
        setFloatUniform("maskStart", config.maskStartFraction)
        setFloatUniform("maskEnd", config.maskEndFraction)
        setFloatUniform("topAlpha", config.topAlpha)
        setFloatUniform("bottomAlpha", config.bottomAlpha)
    }
}

/**
 * Live preview of the liquid-glass effect over sample content, used by the developer options so
 * parameter tweaks are visible immediately. A dedicated [rememberLayerBackdrop] captures the
 * sample content and the floating card refracts/blurs it exactly like the chat input does.
 */
@Composable
fun LiquidGlassPreview(spec: LiquidGlassSpec, modifier: Modifier = Modifier) {
    val backdrop = rememberLayerBackdrop()
    val isLight = rememberIsLightTheme()
    val tint = rememberLiquidGlassTint()
    Box(
        modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(
                    Brush.linearGradient(
                        colors = if (isLight) {
                            listOf(Color(0xFF7FB2FF), Color(0xFFB08BE8), Color(0xFFFF9EC0))
                        } else {
                            listOf(Color(0xFF24356B), Color(0xFF1E5F74), Color(0xFF4A2C6D))
                        },
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Liquid Glass \u9884\u89c8",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "\u73bb\u7483\u540e\u7684\u6587\u5b57\u5e94\u88ab\u67d4\u5316\u6a21\u7cca\uff0c\u8fb9\u7f18\u4ea7\u751f\u6298\u5c04\u3002",
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "\u62d6\u52a8\u4e0b\u65b9\u6ed1\u6746\uff0c\u5b9e\u65f6\u89c2\u5bdf\u6548\u679c\u53d8\u5316\u3002",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.72f)
                .height(62.dp)
                .padding(bottom = 14.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(spec.cornerRadiusDp.dp) },
                    effects = { applyLiquidGlassEffects(spec, isLight) },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(tint) },
                ),
        )
    }
}


/** Developer preview for the progressive top-bar mask over scroll-like sample content. */
@Composable
fun TopBarLiquidGlassPreview(config: TopBarLiquidGlassConfig, modifier: Modifier = Modifier) {
    val backdrop = rememberLayerBackdrop()
    val isLight = rememberIsLightTheme()
    val tint = if (isLight) Color.White else Color.Black
    Box(
        modifier
            .fillMaxWidth()
            .height(216.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(
                    Brush.linearGradient(
                        if (isLight) listOf(Color(0xFFB9D8FF), Color(0xFFF1C8E7), Color(0xFFFFF4D0))
                        else listOf(Color(0xFF13213A), Color(0xFF3A1F48), Color(0xFF283348)),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Behind the progressive glass", color = if (isLight) Color(0xFF162033) else Color.White)
            Text("The backdrop stays readable at the top and fades continuously into chat content.", color = if (isLight) Color(0xB8162033) else Color.White.copy(alpha = 0.78f))
            Text("Alpha mask · blur · tint", color = if (isLight) Color(0xA8162033) else Color.White.copy(alpha = 0.66f))
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(
                    config.maskHeightDp
                        .coerceIn(TopBarLiquidGlassConfig.MIN_MASK_HEIGHT, TopBarLiquidGlassConfig.MAX_MASK_HEIGHT)
                        .dp,
                )
                .then(
                    if (config.enabled && liquidGlassSupported) Modifier.drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = { applyTopBarProgressiveGlass(config, tint) },
                    ) else Modifier.background(tint.copy(alpha = 0.18f))
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                "Liquid Glass · Top bar",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}


@Composable
fun UserBubbleLiquidGlassPreview(config: UserBubbleLiquidGlassConfig, modifier: Modifier = Modifier) {
    val backdrop = rememberLayerBackdrop()
    val isLight = rememberIsLightTheme()
    val tint = (if (isLight) Color.White else Color.Black).copy(alpha = config.tintAlpha)
    val shape = RoundedCornerShape(
        topStart = config.spec.cornerRadiusDp.dp,
        topEnd = config.spec.cornerRadiusDp.dp,
        bottomEnd = 6.dp,
        bottomStart = config.spec.cornerRadiusDp.dp,
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(142.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(
                    Brush.linearGradient(
                        if (isLight) listOf(Color(0xFF8EC5FC), Color(0xFFE0C3FC), Color(0xFFFDFBFB))
                        else listOf(Color(0xFF111827), Color(0xFF312E81), Color(0xFF0F172A)),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .then(
                    if (config.enabled && liquidGlassSupported) Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = { applyLiquidGlassEffects(config.spec, isLight) },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(tint) },
                    ) else Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
                ),
        ) {
            Text(
                "Liquid Glass bubble",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = if (config.enabled && liquidGlassSupported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
