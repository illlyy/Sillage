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
internal fun ChatBackgroundSettingsPage(
    lang: String,
    selectedValue: String,
    imagePath: String,
    imageDim: Float,
    dynamicConfig: FcodeChatDynamicBackgroundConfig,
    onBack: () -> Unit,
    onSelected: (String) -> Unit,
    onImageChanged: (String) -> Unit,
    onDimChanged: (Float) -> Unit,
    onDynamicConfigChanged: (FcodeChatDynamicBackgroundConfig) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = FcodeChatBackgroundStyle.from(selectedValue)
    val imageInfo = remember(imagePath) { ChatBackgroundImageStore.readInfo(imagePath) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var dynamicPreviewReplayToken by remember { mutableIntStateOf(0) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                ChatBackgroundImageStore.importImage(context, uri, imagePath)
            }
            result.onSuccess { imported ->
                onImageChanged(imported.path)
                onSelected(FcodeChatBackgroundStyle.CUSTOM.value)
                Toast.makeText(context, tr(lang, "自定义聊天背景已应用", "Custom chat background applied"), Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                importError = error.message ?: tr(lang, "无法导入所选图片", "Unable to import the selected image")
            }
            importing = false
        }
    }

    SettingsScaffold(
        tr(lang, "聊天背景", "Chat background"),
        tr(lang, "选择内置样式或导入自己的图片", "Choose a built-in style or import your own image"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                ChatBackgroundPreview(
                    lang = lang,
                    style = selected,
                    imagePath = imagePath,
                    imageDim = imageDim,
                    dynamicConfig = dynamicConfig,
                    replayToken = dynamicPreviewReplayToken,
                )
            }
            item { SettingsSection(tr(lang, "自定义图片", "Custom image")) }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
                ) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Folder01, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (imageInfo == null) tr(lang, "尚未选择图片", "No image selected") else tr(lang, "自定义图片已保存", "Custom image saved"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (imageInfo == null) tr(lang, "支持系统可识别的 PNG、JPEG、WebP 等图片", "Supports PNG, JPEG, WebP and other system image formats")
                                    else "${imageInfo.width} × ${imageInfo.height} · ${formatBackgroundImageSize(imageInfo.bytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { imagePicker.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(15.dp),
                                enabled = !importing,
                            ) { Text(if (imageInfo == null) tr(lang, "选择图片", "Choose image") else tr(lang, "更换图片", "Replace image")) }
                            if (imageInfo != null) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) { ChatBackgroundImageStore.remove(context, imagePath) }
                                        onImageChanged("")
                                        if (selected == FcodeChatBackgroundStyle.CUSTOM) onSelected(FcodeChatBackgroundStyle.THEME.value)
                                    },
                                    shape = RoundedCornerShape(15.dp),
                                ) { Text(tr(lang, "移除", "Remove")) }
                            }
                        }
                        if (imageInfo != null) {
                            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr(lang, "图片遮罩", "Image overlay"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(tr(lang, "降低图片干扰，让消息和文字保持清晰", "Reduce image distraction so messages and text stay readable"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${(imageDim * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = imageDim.coerceIn(0f, .72f),
                                onValueChange = onDimChanged,
                                valueRange = 0f..0.72f,
                                steps = 11,
                            )
                        }
                    }
                }
            }
            item { SettingsSection(tr(lang, "背景样式", "Background style")) }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FcodeChatBackgroundStyle.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { style ->
                                BackgroundChoiceCard(
                                    modifier = Modifier.weight(1f),
                                    lang = lang,
                                    style = style,
                                    selected = style == selected,
                                    onClick = {
                                        if (style == FcodeChatBackgroundStyle.CUSTOM && imageInfo == null) imagePicker.launch("image/*")
                                        else onSelected(style.value)
                                    },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            item { SettingsSection(tr(lang, "动态背景", "Animated backgrounds")) }
            item {
                DynamicBackgroundSettingsCard(
                    lang = lang,
                    config = dynamicConfig,
                    onConfigChanged = onDynamicConfigChanged,
                    onReplay = { dynamicPreviewReplayToken++ },
                )
            }
            item {
                Text(
                    tr(lang, "图片会复制到应用私有目录，不依赖相册 URI 的长期访问权限。背景只应用于原生聊天内容区域。", "The image is copied into private app storage and does not depend on long-term gallery URI access. It only applies to the native chat canvas."),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (importing) {
        AlertDialog(
            onDismissRequest = {},
            icon = { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) },
            title = { Text(tr(lang, "正在导入图片", "Importing image")) },
            text = { Text(tr(lang, "正在验证并复制到应用私有目录…", "Validating and copying into private app storage…")) },
            confirmButton = {},
            shape = RoundedCornerShape(28.dp),
        )
    }

    importError?.let { error ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(tr(lang, "无法使用这张图片", "Unable to use this image")) },
            text = { Text(error) },
            confirmButton = { Button(onClick = { importError = null }) { Text(tr(lang, "关闭", "Close")) } },
            shape = RoundedCornerShape(28.dp),
        )
    }
}

private fun formatBackgroundImageSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun ChatBackgroundPreview(
    lang: String,
    style: FcodeChatBackgroundStyle,
    imagePath: String,
    imageDim: Float,
    dynamicConfig: FcodeChatDynamicBackgroundConfig,
    replayToken: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
    ) {
        Box(Modifier.fillMaxWidth().height(270.dp)) {
            FcodeChatDynamicBackgroundPreview(
                modifier = Modifier.fillMaxSize(),
                style = style,
                imagePath = imagePath,
                imageDim = imageDim,
                config = dynamicConfig,
                animationKey = "settings-preview:${style.value}:${imagePath}:${dynamicConfig.enabled}:$replayToken",
            )
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(34.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Sparkles, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(tr(lang, "聊天背景预览", "Chat background preview"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(backgroundLabel(lang, style), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    modifier = Modifier.align(Alignment.End).fillMaxWidth(.72f),
                    shape = RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .94f),
                ) {
                    Text(tr(lang, "帮我整理一下这个项目。", "Help me organize this project."), Modifier.padding(13.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(.88f),
                    shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .94f),
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(tr(lang, "我会先检查结构，再给出清晰的修改计划。", "I will inspect the structure first, then provide a clear change plan."), style = MaterialTheme.typography.bodyMedium)
                        Text("• README  • app  • tests", Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundChoiceCard(
    modifier: Modifier,
    lang: String,
    style: FcodeChatBackgroundStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .65f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
        animationSpec = tween(180),
        label = "backgroundCardBorder",
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(88.dp)) {
                FcodeChatBackdrop(Modifier.fillMaxSize(), style, customImageMaxDimension = 512)
                Surface(
                    modifier = Modifier.align(Alignment.Center).width(64.dp).height(24.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .9f),
                ) {}
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(backgroundLabel(lang, style), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = {
                            (fadeIn(tween(130)) + scaleIn(initialScale = .75f, animationSpec = tween(130))).togetherWith(
                                fadeOut(tween(90)) + scaleOut(targetScale = .75f, animationSpec = tween(90)),
                            )
                        },
                        label = "backgroundSelectionTick",
                    ) { visible ->
                        if (visible) Icon(HugeIcons.Tick02, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicBackgroundSettingsCard(
    lang: String,
    config: FcodeChatDynamicBackgroundConfig,
    onConfigChanged: (FcodeChatDynamicBackgroundConfig) -> Unit,
    onReplay: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
    ) {
        Column {
            Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Sparkles, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(lang, "液态玻璃扩散", "Liquid glass reveal"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        tr(
                            lang,
                            "先模糊聊天背景，再由偏离中心的清晰玻璃圆扩散并永久铺满",
                            "Frost the chat first, then let an off-center clear glass circle expand and remain full-screen",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onConfigChanged(config.copy(enabled = it)) },
                    enabled = liquidGlassSupported,
                )
            }

            if (!liquidGlassSupported) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                Text(
                    tr(lang, "液态折射需要 Android 12 或更高版本。", "Liquid refraction requires Android 12 or newer."),
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (config.enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                Text(
                    tr(lang, "圆心默认在屏幕中心偏左上方；点击聊天页面可提前触发，不点击则按延迟自动播放。", "The origin sits slightly above-left of center. Tap the chat to start early, or let the delay trigger it automatically."),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DynamicBackgroundSliderRow(
                    label = tr(lang, "自动触发延迟", "Auto-start delay"),
                    value = config.autoStartDelayMs.toFloat(),
                    range = FcodeChatDynamicBackgroundConfig.MIN_DELAY_MS.toFloat()..FcodeChatDynamicBackgroundConfig.MAX_DELAY_MS.toFloat(),
                    format = { String.format(Locale.US, "%.1f s", it / 1000f) },
                    onValueChange = { onConfigChanged(config.copy(autoStartDelayMs = it.toInt())) },
                )
                DynamicBackgroundSliderRow(
                    label = tr(lang, "扩散时长", "Expansion time"),
                    value = config.expansionDurationMs.toFloat(),
                    range = FcodeChatDynamicBackgroundConfig.MIN_DURATION_MS.toFloat()..FcodeChatDynamicBackgroundConfig.MAX_DURATION_MS.toFloat(),
                    format = { String.format(Locale.US, "%.1f s", it / 1000f) },
                    onValueChange = { onConfigChanged(config.copy(expansionDurationMs = it.toInt())) },
                )
                DynamicBackgroundSliderRow(
                    label = tr(lang, "初始背景模糊", "Initial background blur"),
                    value = config.initialBlurDp,
                    range = FcodeChatDynamicBackgroundConfig.MIN_BLUR_DP..FcodeChatDynamicBackgroundConfig.MAX_BLUR_DP,
                    format = { "${it.toInt()} dp" },
                    onValueChange = { onConfigChanged(config.copy(initialBlurDp = it)) },
                )
                DynamicBackgroundSliderRow(
                    label = tr(lang, "玻璃折射高度", "Glass refraction height"),
                    value = config.refractionHeightDp,
                    range = FcodeChatDynamicBackgroundConfig.MIN_REFRACTION_HEIGHT_DP..FcodeChatDynamicBackgroundConfig.MAX_REFRACTION_HEIGHT_DP,
                    format = { "${it.toInt()} dp" },
                    onValueChange = { onConfigChanged(config.copy(refractionHeightDp = it)) },
                )
                DynamicBackgroundSliderRow(
                    label = tr(lang, "玻璃折射强度", "Glass refraction amount"),
                    value = config.refractionAmountDp,
                    range = FcodeChatDynamicBackgroundConfig.MIN_REFRACTION_AMOUNT_DP..FcodeChatDynamicBackgroundConfig.MAX_REFRACTION_AMOUNT_DP,
                    format = { "${it.toInt()} dp" },
                    onValueChange = { onConfigChanged(config.copy(refractionAmountDp = it)) },
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(lang, "轻微色散", "Chromatic edge"), style = MaterialTheme.typography.bodyMedium)
                        Text(tr(lang, "在玻璃边缘加入很轻的 RGB 分离", "Add a restrained RGB split at the glass edge"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.chromaticAberration,
                        onCheckedChange = { onConfigChanged(config.copy(chromaticAberration = it)) },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                ) {
                    TextButton(onClick = onReplay) { Text(tr(lang, "重播预览", "Replay preview")) }
                    TextButton(
                        onClick = {
                            onConfigChanged(FcodeChatDynamicBackgroundConfig())
                            onReplay()
                        },
                    ) { Text(tr(lang, "恢复默认", "Reset")) }
                }
            }
        }
    }
}

@Composable
private fun DynamicBackgroundSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}


