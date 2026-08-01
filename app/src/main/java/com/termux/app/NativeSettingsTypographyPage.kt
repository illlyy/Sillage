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
internal fun TypographySettingsPage(
    lang: String,
    chatFontScale: Float,
    interfaceStyle: FcodeInterfaceStyle,
    transparency: FcodeMaterialTransparencyConfig,
    onBack: () -> Unit,
    onChatFontScaleChange: (Float) -> Unit,
    onTransparencyChange: (FcodeMaterialTransparencyConfig) -> Unit,
) {
    val normalizedScale = chatFontScale.coerceIn(0.5f, 2f)
    SettingsScaffold(
        tr(lang, "文字与 Markdown", "Typography & Markdown"),
        tr(lang, "调整对话正文大小，并实时预览 Markdown", "Adjust conversation text and preview Markdown live"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item { SettingsSection(tr(lang, "对话字号", "Conversation text size")) }
            item {
                ChatFontScaleCard(
                    lang = lang,
                    value = normalizedScale,
                    onValueChange = onChatFontScaleChange,
                )
            }
            item { SettingsSection(tr(lang, "实时预览", "Live preview")) }
            item {
                CompositionLocalProvider(LocalFcodeChatFontScale provides normalizedScale) {
                    FcodeChatTypography {
                        TypographyMarkdownPreview(lang)
                    }
                }
            }
            if (interfaceStyle == FcodeInterfaceStyle.MATERIAL) {
                item { SettingsSection(tr(lang, "Material Expressive 透明度", "Material Expressive transparency")) }
                item { MaterialTransparencyPreview(lang, transparency) }
                item {
                    MaterialTransparencySettingsCard(
                        lang = lang,
                        value = transparency,
                        onValueChange = onTransparencyChange,
                    )
                }
            }
            item {
                Text(
                    tr(
                        lang,
                        "只缩放消息正文、Markdown、代码、思考与命令内容以及输入文字；顶栏、按钮和触控区域保持原尺寸。",
                        "Only message text, Markdown, code, reasoning and command content, and composer text are scaled. Headers, buttons, and touch targets keep their original size.",
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ChatFontScaleCard(
    lang: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val percentage = (value * 100).roundToInt()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(lang, "正文与代码字号", "Body and code size"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        tr(lang, "默认 87%，范围 50%–200%", "Default 87%, range 50%–200%"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "$percentage%",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Slider(
                value = value,
                onValueChange = { raw -> onValueChange((raw * 100).roundToInt() / 100f) },
                valueRange = 0.5f..2f,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("50%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("200%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (percentage != (FcodeAppearancePreferences.DEFAULT_CHAT_FONT_SCALE * 100).roundToInt()) {
                TextButton(
                    onClick = { onValueChange(FcodeAppearancePreferences.DEFAULT_CHAT_FONT_SCALE) },
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                ) {
                    Text(tr(lang, "恢复默认 87%", "Restore default 87%"))
                }
            }
        }
    }
}

@Composable
private fun TypographyMarkdownPreview(lang: String) {
    val colors = LocalFcodeMarkdownColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(lang, "让移动端工作流更清晰", "A clearer mobile workflow"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.text)
            Text(
                tr(lang, "正文会按上方比例显示，系统无障碍字号仍然继续生效。", "Body text follows the ratio above while the system accessibility scale remains active."),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
            )
            Row(verticalAlignment = Alignment.Top) {
                Text("• ", color = colors.listMarker, fontWeight = FontWeight.Bold)
                Text(tr(lang, "Markdown 列表、链接和引用", "Markdown lists, links, and quotes"), style = MaterialTheme.typography.bodyMedium, color = colors.link)
            }
            Row {
                Surface(Modifier.width(4.dp).height(46.dp), shape = RoundedCornerShape(50), color = colors.quote) {}
                Text(
                    tr(lang, "预览会随滑块即时变化。", "The preview changes immediately with the slider."),
                    Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                )
            }
            Surface(shape = RoundedCornerShape(14.dp), color = colors.codeBlockBackground) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("kotlin", style = MaterialTheme.typography.labelSmall, color = colors.secondaryText)
                    Text(
                        "val result = codex.run(\"continue\")",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = colors.codeBlockText,
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialTransparencyPreview(
    lang: String,
    value: FcodeMaterialTransparencyConfig,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .72f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = value.activityAlpha),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(8.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
                    Spacer(Modifier.width(9.dp))
                    Text(tr(lang, "思考与执行 · 3 个步骤", "Thinking & actions · 3 steps"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = value.userBubbleAlpha),
                ) {
                    Text(tr(lang, "帮我继续优化这个页面", "Keep polishing this page"), Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = value.composerAlpha),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f)),
            ) {
                Text(tr(lang, "输入消息…", "Message…"), Modifier.padding(horizontal = 15.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MaterialTransparencySettingsCard(
    lang: String,
    value: FcodeMaterialTransparencyConfig,
    onValueChange: (FcodeMaterialTransparencyConfig) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            TransparencySliderRow(
                title = tr(lang, "用户气泡", "User bubble"),
                subtitle = tr(lang, "你发送的消息背景", "Background behind messages you send"),
                value = value.userBubbleAlpha,
                onValueChange = { onValueChange(value.copy(userBubbleAlpha = it)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            TransparencySliderRow(
                title = tr(lang, "思考与命令卡", "Reasoning & command card"),
                subtitle = tr(lang, "思考、命令和工具活动表面", "Reasoning, command, and tool activity surfaces"),
                value = value.activityAlpha,
                onValueChange = { onValueChange(value.copy(activityAlpha = it)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            TransparencySliderRow(
                title = tr(lang, "输入框背景", "Composer background"),
                subtitle = tr(lang, "底部消息输入区域", "The message composer at the bottom"),
                value = value.composerAlpha,
                onValueChange = { onValueChange(value.copy(composerAlpha = it)) },
            )
            val defaults = FcodeMaterialTransparencyConfig()
            if (value != defaults) {
                TextButton(
                    onClick = { onValueChange(defaults) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(tr(lang, "恢复默认透明度", "Restore default transparency"))
                }
            }
        }
    }
}

@Composable
private fun TransparencySliderRow(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val normalized = value.coerceIn(FcodeMaterialTransparencyConfig.MIN_ALPHA, FcodeMaterialTransparencyConfig.MAX_ALPHA)
    Column(Modifier.padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${(normalized * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = normalized,
            onValueChange = { raw -> onValueChange((raw * 100).roundToInt() / 100f) },
            valueRange = FcodeMaterialTransparencyConfig.MIN_ALPHA..FcodeMaterialTransparencyConfig.MAX_ALPHA,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}


