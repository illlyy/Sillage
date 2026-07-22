package com.termux.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object FcodeAppearancePreferences {
    const val COLOR_MODE = "native_theme_mode_v1"
    const val COLOR_PALETTE = "native_color_palette_v1"
    const val INTERFACE_STYLE = "native_interface_style_v1"
    const val CHAT_BACKGROUND = "native_chat_background_v1"
    const val CHAT_BACKGROUND_IMAGE = "native_chat_background_image_v1"
    const val CHAT_BACKGROUND_DIM = "native_chat_background_dim_v1"
    const val COMPACT_COMPOSER_ON_SCROLL = "native_compact_composer_on_scroll_v1"

    fun normalizeColorMode(value: String?): String = value?.takeIf { it in setOf("system", "light", "dark") } ?: "system"
}

internal enum class FcodeInterfaceStyle(val value: String) {
    MATERIAL("material"),
    LIQUID_GLASS("liquid_glass");

    companion object {
        fun from(value: String?): FcodeInterfaceStyle = entries.firstOrNull { it.value == value } ?: MATERIAL
    }
}

internal enum class FcodeColorPalette(val value: String) {
    ROSE("rose"),
    OCEAN("ocean"),
    FOREST("forest"),
    GRAPHITE("graphite");

    companion object {
        fun from(value: String?): FcodeColorPalette = entries.firstOrNull { it.value == value } ?: ROSE
    }
}

internal enum class FcodeChatBackgroundStyle(val value: String) {
    THEME("theme"),
    AURORA("aurora"),
    MIST("mist"),
    GRID("grid"),
    CUSTOM("custom");

    companion object {
        fun from(value: String?): FcodeChatBackgroundStyle = entries.firstOrNull { it.value == value } ?: THEME
    }
}

@Immutable
internal data class FcodeMarkdownColors(
    val cacheKey: String,
    val text: Color,
    val secondaryText: Color,
    val link: Color,
    val listMarker: Color,
    val quote: Color,
    val inlineCodeBackground: Color,
    val inlineCodeText: Color,
    val codeBlockBackground: Color,
    val codeBlockText: Color,
    val tableHeader: Color,
    val tableBorder: Color,
    val diffAddedBackground: Color,
    val diffAddedText: Color,
    val diffRemovedBackground: Color,
    val diffRemovedText: Color,
)

internal val LocalFcodeInterfaceStyle = staticCompositionLocalOf { FcodeInterfaceStyle.MATERIAL }
internal val LocalFcodeAppearanceRevision = staticCompositionLocalOf { 0 }
internal val LocalFcodeColorPalette = staticCompositionLocalOf { FcodeColorPalette.ROSE }
internal val LocalFcodeChatBackground = staticCompositionLocalOf { FcodeChatBackgroundStyle.THEME }
internal val LocalFcodeChatBackgroundImage = staticCompositionLocalOf { "" }
internal val LocalFcodeChatBackgroundDim = staticCompositionLocalOf { 0.32f }
internal val LocalFcodeMarkdownColors = staticCompositionLocalOf {
    fcodeMarkdownColors(FcodeColorPalette.ROSE, dark = false, scheme = fcodeColorScheme(FcodeColorPalette.ROSE, false))
}

internal fun fcodeDarkMode(colorMode: String, systemDark: Boolean): Boolean = when (colorMode) {
    "dark" -> true
    "light" -> false
    else -> systemDark
}

@Composable
internal fun currentFcodeDarkMode(colorMode: String): Boolean = fcodeDarkMode(colorMode, isSystemInDarkTheme())

private val RoseLight = lightColorScheme(
    primary = Color(0xFF73515A), onPrimary = Color(0xFFFDFBFB),
    primaryContainer = Color(0xFFE6CDD2), onPrimaryContainer = Color(0xFF553840),
    secondary = Color(0xFF67565A), onSecondary = Color(0xFFFDFBFB),
    secondaryContainer = Color(0xFFE6CDD2), onSecondaryContainer = Color(0xFF493C40),
    tertiary = Color(0xFF6E5A45), onTertiary = Color(0xFFFDFBFB),
    tertiaryContainer = Color(0xFFE6D6C1), onTertiaryContainer = Color(0xFF4F412F),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF5F1F1), onBackground = Color(0xFF241F20),
    surface = Color(0xFFF5F1F1), onSurface = Color(0xFF241F20),
    surfaceVariant = Color(0xFFE5DCDE), onSurfaceVariant = Color(0xFF504749),
    outline = Color(0xFF776C6E), outlineVariant = Color(0xFFCFC4C6),
    scrim = Color.Black, inverseSurface = Color(0xFF393334), inverseOnSurface = Color(0xFFF2E9EA),
    inversePrimary = Color(0xFFD9AEB7), surfaceDim = Color(0xFFDDD6D7), surfaceBright = Color(0xFFF5F1F1),
    surfaceContainerLowest = Color(0xFFFDFBFB), surfaceContainerLow = Color(0xFFF1EBEC),
    surfaceContainer = Color(0xFFEDE7E8), surfaceContainerHigh = Color(0xFFE8E1E2),
    surfaceContainerHighest = Color(0xFFE2DADB),
)

private val RoseDark = darkColorScheme(
    primary = Color(0xFFE6B8C1), onPrimary = Color(0xFF442830),
    primaryContainer = Color(0xFF5D3E47), onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Color(0xFFD6C1C5), onSecondary = Color(0xFF392D30),
    secondaryContainer = Color(0xFF514347), onSecondaryContainer = Color(0xFFF3DDE1),
    tertiary = Color(0xFFE0C1A2), onTertiary = Color(0xFF402D19),
    tertiaryContainer = Color(0xFF58442F), onTertiaryContainer = Color(0xFFFFDDBB),
    background = Color(0xFF171314), onBackground = Color(0xFFECE0E2),
    surface = Color(0xFF171314), onSurface = Color(0xFFECE0E2),
    surfaceVariant = Color(0xFF51474A), onSurfaceVariant = Color(0xFFD4C2C5),
    outline = Color(0xFF9C8C8F), outlineVariant = Color(0xFF51474A),
    surfaceContainerLowest = Color(0xFF120F10), surfaceContainerLow = Color(0xFF201B1C),
    surfaceContainer = Color(0xFF241F20), surfaceContainerHigh = Color(0xFF2F292A),
    surfaceContainerHighest = Color(0xFF3A3335),
)

internal fun fcodeColorScheme(palette: FcodeColorPalette, dark: Boolean): ColorScheme {
    val base = if (dark) RoseDark else RoseLight
    return when (palette) {
        FcodeColorPalette.ROSE -> base
        FcodeColorPalette.OCEAN -> if (dark) base.copy(
            primary = Color(0xFFA5C9FF), onPrimary = Color(0xFF00315C),
            primaryContainer = Color(0xFF174A78), onPrimaryContainer = Color(0xFFD3E4FF),
            secondary = Color(0xFFBBC7DB), onSecondary = Color(0xFF253140),
            secondaryContainer = Color(0xFF3B4859), onSecondaryContainer = Color(0xFFD7E4F7),
            tertiary = Color(0xFFD0BFE8), onTertiary = Color(0xFF372A4B),
            tertiaryContainer = Color(0xFF4E4062), onTertiaryContainer = Color(0xFFECDDFF),
            background = Color(0xFF101418), onBackground = Color(0xFFE1E7EF),
            surface = Color(0xFF101418), onSurface = Color(0xFFE1E7EF),
            surfaceVariant = Color(0xFF42474F), onSurfaceVariant = Color(0xFFC2C7CF),
            outline = Color(0xFF8C9199), outlineVariant = Color(0xFF42474F),
            surfaceContainerLowest = Color(0xFF0B0F13), surfaceContainerLow = Color(0xFF181C20),
            surfaceContainer = Color(0xFF1C2024), surfaceContainerHigh = Color(0xFF262A2E),
            surfaceContainerHighest = Color(0xFF313539),
        ) else base.copy(
            primary = Color(0xFF315F8F), onPrimary = Color.White,
            primaryContainer = Color(0xFFD3E4FF), onPrimaryContainer = Color(0xFF0D4774),
            secondary = Color(0xFF536175), onSecondary = Color.White,
            secondaryContainer = Color(0xFFD7E4F7), onSecondaryContainer = Color(0xFF3B495D),
            tertiary = Color(0xFF695779), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF0DBFF), onTertiaryContainer = Color(0xFF513F60),
            background = Color(0xFFF6F8FC), onBackground = Color(0xFF191C20),
            surface = Color(0xFFF6F8FC), onSurface = Color(0xFF191C20),
            surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF43474E),
            outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C6CF),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F2F6),
            surfaceContainer = Color(0xFFEAEDF1), surfaceContainerHigh = Color(0xFFE4E7EB),
            surfaceContainerHighest = Color(0xFFDEE1E5),
        )
        FcodeColorPalette.FOREST -> if (dark) base.copy(
            primary = Color(0xFF9FD6C2), onPrimary = Color(0xFF00382D),
            primaryContainer = Color(0xFF185143), onPrimaryContainer = Color(0xFFBAF2DD),
            secondary = Color(0xFFB4CCC2), onSecondary = Color(0xFF20372F),
            secondaryContainer = Color(0xFF374E46), onSecondaryContainer = Color(0xFFD0E8DE),
            tertiary = Color(0xFFC5CB95), onTertiary = Color(0xFF30350A),
            tertiaryContainer = Color(0xFF474D20), onTertiaryContainer = Color(0xFFE1E7AF),
            background = Color(0xFF101512), onBackground = Color(0xFFDFE9E3),
            surface = Color(0xFF101512), onSurface = Color(0xFFDFE9E3),
            surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFBFC9C3),
            outline = Color(0xFF89938D), outlineVariant = Color(0xFF404943),
            surfaceContainerLowest = Color(0xFF0B100D), surfaceContainerLow = Color(0xFF181D1A),
            surfaceContainer = Color(0xFF1C211E), surfaceContainerHigh = Color(0xFF262B28),
            surfaceContainerHighest = Color(0xFF313633),
        ) else base.copy(
            primary = Color(0xFF356B5C), onPrimary = Color.White,
            primaryContainer = Color(0xFFB9F0DC), onPrimaryContainer = Color(0xFF155143),
            secondary = Color(0xFF4E635B), onSecondary = Color.White,
            secondaryContainer = Color(0xFFD0E8DE), onSecondaryContainer = Color(0xFF374B43),
            tertiary = Color(0xFF5E642F), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE3E8AD), onTertiaryContainer = Color(0xFF474C1A),
            background = Color(0xFFF4F9F6), onBackground = Color(0xFF171D1A),
            surface = Color(0xFFF4F9F6), onSurface = Color(0xFF171D1A),
            surfaceVariant = Color(0xFFDDE5E0), onSurfaceVariant = Color(0xFF414945),
            outline = Color(0xFF717A75), outlineVariant = Color(0xFFC1C9C4),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEEF3F0),
            surfaceContainer = Color(0xFFE8EDEB), surfaceContainerHigh = Color(0xFFE2E7E5),
            surfaceContainerHighest = Color(0xFFDCE2DF),
        )
        FcodeColorPalette.GRAPHITE -> if (dark) base.copy(
            primary = Color(0xFFC5C4E1), onPrimary = Color(0xFF2E2E45),
            primaryContainer = Color(0xFF45445D), onPrimaryContainer = Color(0xFFE2E0FF),
            secondary = Color(0xFFC7C5D0), onSecondary = Color(0xFF30303A),
            secondaryContainer = Color(0xFF474650), onSecondaryContainer = Color(0xFFE3E1EC),
            tertiary = Color(0xFFE2BFD0), onTertiary = Color(0xFF432738),
            tertiaryContainer = Color(0xFF5B3D4F), onTertiaryContainer = Color(0xFFFFD8E8),
            background = Color(0xFF131316), onBackground = Color(0xFFE5E1E6),
            surface = Color(0xFF131316), onSurface = Color(0xFFE5E1E6),
            surfaceVariant = Color(0xFF47464F), onSurfaceVariant = Color(0xFFC8C5CF),
            outline = Color(0xFF92909A), outlineVariant = Color(0xFF47464F),
            surfaceContainerLowest = Color(0xFF0E0E11), surfaceContainerLow = Color(0xFF1B1B1E),
            surfaceContainer = Color(0xFF1F1F22), surfaceContainerHigh = Color(0xFF29292C),
            surfaceContainerHighest = Color(0xFF343437),
        ) else base.copy(
            primary = Color(0xFF5D5D75), onPrimary = Color.White,
            primaryContainer = Color(0xFFE2E0FF), onPrimaryContainer = Color(0xFF45445C),
            secondary = Color(0xFF5F5E68), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE4E2ED), onSecondaryContainer = Color(0xFF474650),
            tertiary = Color(0xFF79546A), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFD8E8), onTertiaryContainer = Color(0xFF5F3C52),
            background = Color(0xFFF7F6F9), onBackground = Color(0xFF1B1B1F),
            surface = Color(0xFFF7F6F9), onSurface = Color(0xFF1B1B1F),
            surfaceVariant = Color(0xFFE5E2E9), onSurfaceVariant = Color(0xFF47464E),
            outline = Color(0xFF787680), outlineVariant = Color(0xFFC9C6D0),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F0F3),
            surfaceContainer = Color(0xFFEBEAED), surfaceContainerHigh = Color(0xFFE5E4E8),
            surfaceContainerHighest = Color(0xFFDFDEE2),
        )
    }
}


/** Neutral Apple-style system palette used by the Liquid Glass interface style. */
internal fun liquidGlassColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        // iOS semantic accent: keep the material monochrome, reserve blue for actions/focus.
        primary = Color(0xFF0A84FF), onPrimary = Color.White,
        primaryContainer = Color(0xFF0B355F), onPrimaryContainer = Color(0xFFD6EAFF),
        secondary = Color(0xFFD1D1D6), onSecondary = Color(0xFF111113),
        secondaryContainer = Color(0xFF252527), onSecondaryContainer = Color(0xFFE5E5EA),
        tertiary = Color(0xFFE5E5EA), onTertiary = Color(0xFF111113),
        tertiaryContainer = Color(0xFF303033), onTertiaryContainer = Color(0xFFF2F2F7),
        error = Color(0xFFFF453A), onError = Color.Black,
        errorContainer = Color(0xFF5A1A18), onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF000000), onBackground = Color(0xFFF5F5F7),
        surface = Color(0xFF080808), onSurface = Color(0xFFF5F5F7),
        surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFFC7C7CC),
        outline = Color(0xFF8E8E93), outlineVariant = Color(0xFF38383A),
        scrim = Color.Black, inverseSurface = Color(0xFFF2F2F7), inverseOnSurface = Color(0xFF1C1C1E),
        inversePrimary = Color(0xFF007AFF), surfaceDim = Color(0xFF000000), surfaceBright = Color(0xFF323234),
        surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF111113),
        surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF252527),
        surfaceContainerHighest = Color(0xFF2C2C2E),
    )
} else {
    lightColorScheme(
        // Black/white remains the visual base; system blue only communicates interactivity.
        primary = Color(0xFF007AFF), onPrimary = Color.White,
        primaryContainer = Color(0xFFE1F0FF), onPrimaryContainer = Color(0xFF003A70),
        secondary = Color(0xFF3A3A3C), onSecondary = Color.White,
        secondaryContainer = Color(0xFFECECEF), onSecondaryContainer = Color(0xFF2C2C2E),
        tertiary = Color(0xFF48484A), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF0F0F2), onTertiaryContainer = Color(0xFF2C2C2E),
        error = Color(0xFFFF3B30), onError = Color.White,
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF8C1D18),
        background = Color(0xFFF5F5F7), onBackground = Color(0xFF111111),
        surface = Color(0xFFF9F9FB), onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF5C5C62),
        outline = Color(0xFF8E8E93), outlineVariant = Color(0xFFD1D1D6),
        scrim = Color.Black, inverseSurface = Color(0xFF1C1C1E), inverseOnSurface = Color(0xFFF5F5F7),
        inversePrimary = Color(0xFF0A84FF), surfaceDim = Color(0xFFE1E1E5), surfaceBright = Color.White,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F2F5),
        surfaceContainer = Color(0xFFEDEDF0), surfaceContainerHigh = Color(0xFFE8E8EB),
        surfaceContainerHighest = Color(0xFFE1E1E5),
    )
}

internal fun fcodeMarkdownColors(
    palette: FcodeColorPalette,
    dark: Boolean,
    scheme: ColorScheme,
    styleKey: String = FcodeInterfaceStyle.MATERIAL.value,
): FcodeMarkdownColors {
    // Code blocks should inherit the active theme surface. The previous light-mode
    // palette used near-black colors, so both inline code and recycled TextViews could flash black.
    val codeBackground = scheme.surfaceContainerHigh
    return FcodeMarkdownColors(
        cacheKey = "$styleKey-${palette.value}-${if (dark) "dark" else "light"}",
        text = scheme.onSurface,
        secondaryText = scheme.onSurfaceVariant,
        link = scheme.primary,
        listMarker = scheme.tertiary,
        quote = scheme.primary.copy(alpha = if (dark) 0.78f else 0.68f),
        inlineCodeBackground = scheme.primaryContainer.copy(alpha = if (dark) 0.72f else 0.76f),
        inlineCodeText = scheme.onPrimaryContainer,
        codeBlockBackground = codeBackground,
        codeBlockText = scheme.onSurface,
        tableHeader = scheme.secondaryContainer,
        tableBorder = scheme.outlineVariant,
        diffAddedBackground = if (dark) Color(0xFF173C2A) else Color(0xFFD8F3DC),
        diffAddedText = if (dark) Color(0xFFADEBC0) else Color(0xFF175C2C),
        diffRemovedBackground = if (dark) Color(0xFF4A2525) else Color(0xFFFFDAD6),
        diffRemovedText = if (dark) Color(0xFFFFB4AB) else Color(0xFF8C1D18),
    )
}

@Composable
internal fun FcodeChatBackdrop(
    modifier: Modifier = Modifier,
    style: FcodeChatBackgroundStyle = LocalFcodeChatBackground.current,
    customImagePath: String = LocalFcodeChatBackgroundImage.current,
    customImageDim: Float = LocalFcodeChatBackgroundDim.current,
    customImageMaxDimension: Int = 2048,
) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val customBitmapState = produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = if (style == FcodeChatBackgroundStyle.CUSTOM) "$customImagePath:$customImageMaxDimension" else "",
    ) {
        value = if (style == FcodeChatBackgroundStyle.CUSTOM && customImagePath.isNotBlank()) {
            withContext(Dispatchers.IO) { ChatBackgroundImageStore.decodeForDisplay(customImagePath, customImageMaxDimension.coerceIn(256, 4096)) }
        } else null
    }
    val customBitmap = customBitmapState.value
    DisposableEffect(customBitmap) {
        onDispose { customBitmap?.recycle() }
    }
    Box(modifier = modifier) {
        if (style == FcodeChatBackgroundStyle.CUSTOM && customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                when (style) {
                    FcodeChatBackgroundStyle.THEME, FcodeChatBackgroundStyle.CUSTOM -> {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(colors.primaryContainer.copy(alpha = 0.20f), Color.Transparent, Color.Transparent),
                            ),
                        )
                    }
                    FcodeChatBackgroundStyle.AURORA -> {
                        // One full-screen shader instead of two overlapping radial passes.
                        drawRect(
                            Brush.linearGradient(
                                colors = listOf(
                                    colors.primary.copy(alpha = 0.16f),
                                    Color.Transparent,
                                    colors.tertiary.copy(alpha = 0.12f),
                                ),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height),
                            ),
                        )
                    }
                    FcodeChatBackgroundStyle.MIST -> {
                        drawRect(
                            Brush.linearGradient(
                                colors = listOf(
                                    colors.secondaryContainer.copy(alpha = 0.34f),
                                    Color.Transparent,
                                    colors.primaryContainer.copy(alpha = 0.24f),
                                ),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height),
                            ),
                        )
                    }
                    FcodeChatBackgroundStyle.GRID -> {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(colors.primaryContainer.copy(alpha = 0.12f), Color.Transparent),
                            ),
                        )
                        // Halve the number of line draw calls on high-density displays.
                        val step = 52.dp.toPx()
                        val line = colors.outlineVariant.copy(alpha = 0.20f)
                        var x = 0f
                        while (x <= size.width) {
                            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                            x += step
                        }
                        var y = 0f
                        while (y <= size.height) {
                            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                            y += step
                        }
                    }
                }
            }
        }
        if (style == FcodeChatBackgroundStyle.CUSTOM && customBitmap != null && customImageDim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = customImageDim.coerceIn(0f, 0.72f))),
            )
        }
    }
}
