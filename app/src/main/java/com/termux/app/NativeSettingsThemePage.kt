package com.termux.app

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.WebView
import android.widget.Toast
import com.termux.BuildConfig
import com.termux.R
import com.termux.app.update.AppUpdateManager
import com.termux.shared.termux.TermuxConstants
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Text
import me.rerere.hugeicons.stroke.Tick02


@Composable
internal fun ThemeSettingsPage(
    lang: String,
    colorMode: String,
    paletteValue: String,
    interfaceStyleValue: String,
    backgroundValue: String,
    onBack: () -> Unit,
    onColorModeChange: (String) -> Unit,
    onPaletteChange: (String) -> Unit,
    onInterfaceStyleChange: (String) -> Unit,
    onOpenChatBackground: () -> Unit,
) {
    val selectedPalette = FcodeColorPalette.from(paletteValue)
    val selectedStyle = FcodeInterfaceStyle.from(interfaceStyleValue)
    val selectedBackground = FcodeChatBackgroundStyle.from(backgroundValue)
    val dark = currentFcodeDarkMode(colorMode)
    SettingsScaffold(
        tr(lang, "主题", "Theme"),
        tr(lang, "配色、Markdown 表面与聊天背景", "Colors, Markdown surfaces and chat background"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                ThemeOverviewCard(
                    lang = lang,
                    palette = selectedPalette,
                    interfaceStyle = selectedStyle,
                    colorMode = colorMode,
                    background = selectedBackground,
                )
            }
            item { SettingsSection(tr(lang, "界面风格", "Interface style")) }
            item { InterfaceStyleCard(lang, selectedStyle) { onInterfaceStyleChange(it.value) } }
            item { SettingsSection(tr(lang, "颜色模式", "Color mode")) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "system" to tr(lang, "跟随系统", "System"),
                        "light" to tr(lang, "浅色", "Light"),
                        "dark" to tr(lang, "深色", "Dark"),
                    ).forEach { (value, label) ->
                        val selected = colorMode == value
                        val containerColor by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            animationSpec = tween(180),
                            label = "colorModeContainer",
                        )
                        val contentColor by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            animationSpec = tween(180),
                            label = "colorModeContent",
                        )
                        Surface(
                            onClick = { onColorModeChange(value) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = containerColor,
                            contentColor = contentColor,
                            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f)) else null,
                        ) {
                            Column(
                                Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (selected) Icon(HugeIcons.Tick02, null, Modifier.size(17.dp))
                                else Spacer(Modifier.height(17.dp))
                                Text(label, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            if (selectedStyle == FcodeInterfaceStyle.MATERIAL) {
                item { SettingsSection(tr(lang, "配色主题", "Color palette")) }
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FcodeColorPalette.entries.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { palette ->
                                    PaletteChoiceCard(
                                        modifier = Modifier.weight(1f),
                                        lang = lang,
                                        palette = palette,
                                        dark = dark,
                                        selected = palette == selectedPalette,
                                        onClick = { onPaletteChange(palette.value) },
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (selectedPalette == FcodeColorPalette.WALLPAPER) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .72f),
                        ) {
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    tr(lang, "主题色会跟随当前 Android 壁纸变化。", "Theme colors follow the current Android wallpaper.")
                                } else {
                                    tr(lang, "壁纸取色需要 Android 12 或更高版本；当前设备会自动回退到绯樱配色。", "Wallpaper colors require Android 12 or later; this device falls back to the Rose palette.")
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            } else {
                item { SettingsSection(tr(lang, "液态玻璃配色", "Liquid Glass colors")) }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
                            Spacer(Modifier.width(10.dp))
                            Surface(Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {}
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr(lang, "苹果黑白", "Apple monochrome"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(tr(lang, "会自动适配浅色与深色模式；切回 Material 后保留原配色。", "Adapts to light and dark; your Material palette is kept."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item { SettingsSection(tr(lang, "Markdown 配色", "Markdown colors")) }
            item { MarkdownThemePreview(lang) }
            item { SettingsSection(tr(lang, "聊天背景", "Chat background")) }
            item {
                NavigationSettingsRow(
                    HugeIcons.Sparkles,
                    tr(lang, "背景与预览", "Background & preview"),
                    "${backgroundLabel(lang, selectedBackground)} · ${tr(lang, "三级页面实时预览", "Live preview on the detail page")}",
                    onOpenChatBackground,
                )
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun ThemeOverviewCard(
    lang: String,
    palette: FcodeColorPalette,
    interfaceStyle: FcodeInterfaceStyle,
    colorMode: String,
    background: FcodeChatBackgroundStyle,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            FcodeChatBackdrop(Modifier.fillMaxSize(), background, customImageMaxDimension = 720)
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(HugeIcons.Sparkles, null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr(lang, "当前外观", "Current appearance"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${if (interfaceStyle == FcodeInterfaceStyle.LIQUID_GLASS) tr(lang, "液态玻璃", "Liquid Glass") else paletteLabel(lang, palette)} · ${themeLabel(lang, colorMode)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .92f),
                ) {
                    Text(
                        tr(lang, "主题、Markdown 和背景会同步更新。", "Theme, Markdown and background update together."),
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun InterfaceStyleCard(
    lang: String,
    selected: FcodeInterfaceStyle,
    onSelected: (FcodeInterfaceStyle) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
    ) {
        Column {
            FcodeInterfaceStyle.entries.forEachIndexed { index, style ->
                val active = selected == style
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(style) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        Modifier.size(40.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                HugeIcons.Sparkles,
                                null,
                                Modifier.size(20.dp),
                                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (style == FcodeInterfaceStyle.MATERIAL) "Material Expressive" else tr(lang, "液态玻璃", "Liquid Glass"),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (style == FcodeInterfaceStyle.MATERIAL) tr(lang, "Material 动态配色与组件", "Material color palettes and components")
                            else tr(lang, "苹果风黑白配色与折射玻璃", "Apple monochrome colors and refractive glass"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (active) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(HugeIcons.Tick02, tr(lang, "使用中", "Active"), Modifier.padding(8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                if (index < FcodeInterfaceStyle.entries.lastIndex) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                }
            }
        }
    }
}

@Composable
private fun PaletteChoiceCard(
    modifier: Modifier,
    lang: String,
    palette: FcodeColorPalette,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = fcodeResolvedColorScheme(context, palette, dark)
    val cardColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f) else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(180),
        label = "paletteCardColor",
    )
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .48f),
        animationSpec = tween(180),
        label = "paletteCardBorder",
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(paletteLabel(lang, palette), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = {
                            (fadeIn(tween(130)) + scaleIn(initialScale = .75f, animationSpec = tween(130))).togetherWith(
                                fadeOut(tween(90)) + scaleOut(targetScale = .75f, animationSpec = tween(90)),
                            )
                        },
                        label = "paletteSelectionTick",
                    ) { visible ->
                        if (visible) Icon(HugeIcons.Tick02, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.surfaceContainerHighest).forEach { color ->
                    Surface(Modifier.size(24.dp), shape = CircleShape, color = color, border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .65f))) {}
                }
            }
        }
    }
}

@Composable
private fun MarkdownThemePreview(lang: String) {
    val colors = LocalFcodeMarkdownColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(lang, "Markdown 预览", "Markdown preview"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text)
            Text(tr(lang, "正文颜色会保持足够对比度，链接与列表跟随当前配色。", "Body text keeps sufficient contrast; links and lists follow the selected palette."), style = MaterialTheme.typography.bodyMedium, color = colors.secondaryText)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("• ", color = colors.listMarker, fontWeight = FontWeight.Bold)
                Text(tr(lang, "列表标记", "List marker"), color = colors.text)
                Spacer(Modifier.width(10.dp))
                Text(tr(lang, "主题链接", "Theme link"), color = colors.link, fontWeight = FontWeight.Medium)
            }
            Surface(shape = RoundedCornerShape(9.dp), color = colors.inlineCodeBackground) {
                Text("codex --model gpt-5", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = colors.inlineCodeText, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            Row {
                Surface(Modifier.width(4.dp).height(42.dp), shape = RoundedCornerShape(50), color = colors.quote) {}
                Text(tr(lang, "引用颜色、行内代码和代码块均独立适配浅色与深色模式。", "Quotes, inline code and code blocks adapt independently to light and dark modes."), Modifier.padding(start = 11.dp), style = MaterialTheme.typography.bodySmall, color = colors.secondaryText)
            }
        }
    }
}


