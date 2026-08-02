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


internal enum class CodexDependentFeature {
    WEB_UI,
    TERMUX,
    SETUP,
}

internal fun paletteLabel(lang: String, palette: FcodeColorPalette): String = when (palette) {
    FcodeColorPalette.WALLPAPER -> tr(lang, "壁纸取色", "Wallpaper colors")
    FcodeColorPalette.ROSE -> tr(lang, "绯樱", "Rose")
    FcodeColorPalette.OCEAN -> tr(lang, "海盐", "Ocean")
    FcodeColorPalette.FOREST -> tr(lang, "森屿", "Forest")
    FcodeColorPalette.GRAPHITE -> tr(lang, "石墨", "Graphite")
}

internal fun backgroundLabel(lang: String, style: FcodeChatBackgroundStyle): String = when (style) {
    FcodeChatBackgroundStyle.THEME -> tr(lang, "主题柔光", "Theme glow")
    FcodeChatBackgroundStyle.AURORA -> tr(lang, "极光", "Aurora")
    FcodeChatBackgroundStyle.MIST -> tr(lang, "薄雾", "Mist")
    FcodeChatBackgroundStyle.GRID -> tr(lang, "坐标网格", "Grid")
    FcodeChatBackgroundStyle.CUSTOM -> tr(lang, "自定义图片", "Custom image")
}

internal fun appearanceLabel(lang: String, paletteValue: String, colorMode: String): String =
    "${paletteLabel(lang, FcodeColorPalette.from(paletteValue))} · ${themeLabel(lang, colorMode)}"

@Composable
internal fun MissingCodexCliDialog(
    lang: String,
    feature: CodexDependentFeature,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    val reason = when (feature) {
        CodexDependentFeature.WEB_UI -> tr(
            lang,
            "WebUI 需要 Codex CLI 作为本地后端，当前设备尚未安装。",
            "WebUI needs Codex CLI as its local backend, but it is not installed on this device.",
        )
        CodexDependentFeature.TERMUX -> tr(
            lang,
            "Termux 中的 Codex 命令、API 代理和模型配置注入需要 Codex CLI，当前设备尚未安装。",
            "Codex commands, API proxying and model configuration injection in Termux require Codex CLI, but it is not installed.",
        )
        CodexDependentFeature.SETUP -> tr(
            lang,
            "WebUI、Termux 中的 Codex 命令和模型运行能力需要 Codex CLI，当前设备尚未安装。",
            "WebUI, Codex commands in Termux and model execution require Codex CLI, but it is not installed on this device.",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.Code, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
        title = { Text(tr(lang, "需要安装 Codex CLI", "Codex CLI required"), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(reason)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        tr(
                            lang,
                            "安装器将从 OpenAI 官方 GitHub Release 下载 Codex CLI。当前内置安装器仅支持 ARM64 设备，请保持网络连接。",
                            "The installer downloads Codex CLI from the official OpenAI GitHub Release. The built-in installer currently supports ARM64 devices only; keep your network connected.",
                        ),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(lang, "取消", "Cancel")) } },
        confirmButton = { Button(onClick = onInstall) { Text(tr(lang, "下载安装", "Download & install")) } },
        shape = RoundedCornerShape(28.dp),
    )
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScaffold(title: String, subtitle: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    ) {
                        Icon(HugeIcons.ArrowLeft01, "返回 / Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
internal fun SettingsSection(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
internal fun NavigationSettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 650f),
        label = "settingsNavigationPress",
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        },
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Icon(HugeIcons.ArrowRight01, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ToggleSettingsRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 650f),
        label = "settingsTogglePress",
    )
    Surface(
        onClick = { onCheckedChange(!checked) },
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        },
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp)); Switch(checked, onCheckedChange)
        }
    }
}

@Composable
internal fun SettingsIcon(icon: ImageVector) {
    Surface(Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}

@Composable
internal fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value, onValueChange,
        modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        visualTransformation = visualTransformation, trailingIcon = trailing, enabled = enabled, shape = RoundedCornerShape(14.dp),
    )
}

@Composable
internal fun SettingsMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).heightIn(min = 108.dp),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        minLines = 3,
        maxLines = 7,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
internal fun EmptySettingsState(icon: ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SettingsChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column {
            options.forEachIndexed { index, (value, label) ->
                Surface(
                    onClick = { onSelected(value) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (value == selected) Icon(HugeIcons.Tick02, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (index != options.lastIndex) HorizontalDivider()
            }
        } },
        confirmButton = { TextButton(onDismiss) { Text("OK") } },
    )
}

@Composable
internal fun InfoDialog(title: String, body: String, lang: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onDismiss) { Text(tr(lang, "完成", "Done")) } })
}

internal fun tr(lang: String, zh: String, en: String) = if (lang == "zh") zh else en
internal fun resolveLanguage(value: String) = when (value) {
    "en" -> "en"
    "zh" -> "zh"
    else -> if (Locale.getDefault().language == "en") "en" else "zh"
}
internal fun themeLabel(lang: String, value: String) = when (value) {
    "light" -> tr(lang, "浅色", "Light")
    "dark" -> tr(lang, "深色", "Dark")
    else -> tr(lang, "跟随系统", "System")
}
internal fun apiFormatLabel(value: String) = when (value) {
    "openai_chat" -> "Chat Completions"
    "openai_responses" -> "Responses API"
    else -> "Auto"
}
internal fun endpointLabel(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        val uri = Uri.parse(value)
        uri.host?.let { host -> uri.path?.trimEnd('/')?.takeIf(String::isNotBlank)?.let { "$host$it" } ?: host } ?: value
    }.getOrDefault(value)
}
internal fun isValidHttpUrl(value: String) = runCatching {
    val uri = Uri.parse(value)
    (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

