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
internal fun DeveloperSettingsPage(lang: String, onBack: () -> Unit) {
    var showPlayground by remember { mutableStateOf(false) }
    if (showPlayground) {
        SettingsScaffold(
            tr(lang, "页面切换动画", "Page transitions"),
            tr(lang, "现代化页面切换动画与交互测试", "Modern page transition and interaction testing"),
            { showPlayground = false },
        ) { contentPadding ->
            PageTransitionPlayground(lang, Modifier.fillMaxSize().padding(contentPadding))
        }
    } else {
        SettingsScaffold(
            tr(lang, "开发者选项", "Developer options"),
            tr(lang, "液态玻璃与页面切换动画调试", "Liquid glass and page transition debugging"),
            onBack,
        ) { contentPadding ->
            LazyColumn(Modifier.fillMaxSize().padding(contentPadding)) {
                item { LiquidGlassSettingsSection(lang) }
                item {
                    NavigationSettingsRow(
                        HugeIcons.MagicWand01,
                        tr(lang, "页面切换动画", "Page transitions"),
                        tr(lang, "现代化页面切换动画与交互测试", "Modern page transition and interaction testing"),
                        { showPlayground = true },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * Developer controls for the chat input's liquid-glass effect, mirroring the adjustable
 * parameters of the Backdrop catalog playground (vendor/AndroidLiquidGlass). Values persist to
 * SharedPreferences and are read live by the composer.
 */
@Composable
private fun LiquidGlassSettingsSection(lang: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("codex_mobile", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(LiquidGlassSpec.KEY_ENABLED, true)) }
    var cornerRadius by remember { mutableStateOf(prefs.getFloat(LiquidGlassSpec.KEY_CORNER_RADIUS, LiquidGlassSpec.DEFAULT_CORNER_RADIUS)) }
    var blur by remember { mutableStateOf(prefs.getFloat(LiquidGlassSpec.KEY_BLUR, LiquidGlassSpec.DEFAULT_BLUR)) }
    var refractionHeight by remember { mutableStateOf(prefs.getFloat(LiquidGlassSpec.KEY_REFRACTION_HEIGHT, LiquidGlassSpec.DEFAULT_REFRACTION_HEIGHT)) }
    var refractionAmount by remember { mutableStateOf(prefs.getFloat(LiquidGlassSpec.KEY_REFRACTION_AMOUNT, LiquidGlassSpec.DEFAULT_REFRACTION_AMOUNT)) }
    var chromatic by remember { mutableStateOf(prefs.getBoolean(LiquidGlassSpec.KEY_CHROMATIC, LiquidGlassSpec.DEFAULT_CHROMATIC)) }
    var bubbleEnabled by remember { mutableStateOf(prefs.getBoolean(UserBubbleLiquidGlassConfig.KEY_ENABLED, true)) }
    var bubbleCornerRadius by remember { mutableStateOf(prefs.getFloat(UserBubbleLiquidGlassConfig.KEY_CORNER_RADIUS, UserBubbleLiquidGlassConfig.DEFAULT_CORNER_RADIUS)) }
    var bubbleBlur by remember { mutableStateOf(prefs.getFloat(UserBubbleLiquidGlassConfig.KEY_BLUR, UserBubbleLiquidGlassConfig.DEFAULT_BLUR)) }
    var bubbleRefractionHeight by remember { mutableStateOf(prefs.getFloat(UserBubbleLiquidGlassConfig.KEY_REFRACTION_HEIGHT, UserBubbleLiquidGlassConfig.DEFAULT_REFRACTION_HEIGHT)) }
    var bubbleRefractionAmount by remember { mutableStateOf(prefs.getFloat(UserBubbleLiquidGlassConfig.KEY_REFRACTION_AMOUNT, UserBubbleLiquidGlassConfig.DEFAULT_REFRACTION_AMOUNT)) }
    var bubbleChromatic by remember { mutableStateOf(prefs.getBoolean(UserBubbleLiquidGlassConfig.KEY_CHROMATIC, UserBubbleLiquidGlassConfig.DEFAULT_CHROMATIC)) }
    var bubbleTintAlpha by remember { mutableStateOf(prefs.getFloat(UserBubbleLiquidGlassConfig.KEY_TINT_ALPHA, UserBubbleLiquidGlassConfig.DEFAULT_TINT_ALPHA)) }
    var topBarEnabled by remember { mutableStateOf(prefs.getBoolean(TopBarLiquidGlassConfig.KEY_ENABLED, true)) }
    var topBarBlur by remember { mutableStateOf(prefs.getFloat(TopBarLiquidGlassConfig.KEY_BLUR, TopBarLiquidGlassConfig.DEFAULT_BLUR)) }
    var topBarTint by remember { mutableStateOf(prefs.getFloat(TopBarLiquidGlassConfig.KEY_TINT_INTENSITY, TopBarLiquidGlassConfig.DEFAULT_TINT_INTENSITY)) }
    var topBarMaskHeight by remember { mutableStateOf(prefs.getFloat(TopBarLiquidGlassConfig.KEY_MASK_HEIGHT, TopBarLiquidGlassConfig.DEFAULT_MASK_HEIGHT)) }
    var topBarMaskStart by remember { mutableStateOf(prefs.getFloat(TopBarLiquidGlassConfig.KEY_MASK_START, TopBarLiquidGlassConfig.DEFAULT_MASK_START)) }
    var topBarMaskEnd by remember { mutableStateOf(prefs.getFloat(TopBarLiquidGlassConfig.KEY_MASK_END, TopBarLiquidGlassConfig.DEFAULT_MASK_END)) }
    var topBarTopAlpha by remember { mutableStateOf(prefs.getFloat(TopBarLiquidGlassConfig.KEY_TOP_ALPHA, TopBarLiquidGlassConfig.DEFAULT_TOP_ALPHA)) }
    var topBarBottomAlpha by remember { mutableStateOf(prefs.getFloat(TopBarLiquidGlassConfig.KEY_BOTTOM_ALPHA, TopBarLiquidGlassConfig.DEFAULT_BOTTOM_ALPHA)) }

    val topBarConfig = TopBarLiquidGlassConfig(
        enabled = topBarEnabled,
        blurRadiusDp = topBarBlur,
        tintIntensity = topBarTint,
        maskHeightDp = topBarMaskHeight.coerceIn(TopBarLiquidGlassConfig.MIN_MASK_HEIGHT, TopBarLiquidGlassConfig.MAX_MASK_HEIGHT),
        maskStartFraction = topBarMaskStart.coerceIn(0f, 0.92f),
        maskEndFraction = topBarMaskEnd.coerceIn(topBarMaskStart.coerceIn(0f, 0.92f) + 0.04f, 1f),
        topAlpha = topBarTopAlpha,
        bottomAlpha = topBarBottomAlpha,
    )
    SettingsSection(tr(lang, "液态玻璃 · 顶栏渐变 Mask", "Liquid glass · top-bar fade mask"))
    TopBarLiquidGlassPreview(topBarConfig, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    ToggleSettingsRow(
        HugeIcons.Sparkles,
        tr(lang, "启用顶栏渐变玻璃", "Enable progressive top-bar glass"),
        tr(lang, "在 Material 与液态玻璃主题中都向下连续淡出", "Fades continuously in both Material and Liquid Glass themes"),
        topBarEnabled,
    ) { value -> topBarEnabled = value; prefs.edit().putBoolean(TopBarLiquidGlassConfig.KEY_ENABLED, value).apply() }
    GlassSliderRow(tr(lang, "顶栏模糊", "Top-bar blur"), topBarBlur, 0f..32f, { "${it.toInt()} dp" }) { value ->
        topBarBlur = value; prefs.edit().putFloat(TopBarLiquidGlassConfig.KEY_BLUR, value).apply()
    }
    GlassSliderRow(
        tr(lang, "蒙版高度", "Mask height"),
        topBarMaskHeight,
        TopBarLiquidGlassConfig.MIN_MASK_HEIGHT..TopBarLiquidGlassConfig.MAX_MASK_HEIGHT,
        { "${it.toInt()} dp" },
    ) { value ->
        topBarMaskHeight = value
        prefs.edit().putFloat(TopBarLiquidGlassConfig.KEY_MASK_HEIGHT, value).apply()
    }
    GlassSliderRow(tr(lang, "玻璃色调", "Glass tint"), topBarTint, 0f..0.72f, { "${(it * 100).toInt()}%" }) { value ->
        topBarTint = value; prefs.edit().putFloat(TopBarLiquidGlassConfig.KEY_TINT_INTENSITY, value).apply()
    }
    GlassSliderRow(tr(lang, "渐变开始", "Fade start"), topBarMaskStart, 0f..0.92f, { "${(it * 100).toInt()}%" }) { value ->
        topBarMaskStart = value
        if (topBarMaskEnd < value + 0.04f) topBarMaskEnd = (value + 0.04f).coerceAtMost(1f)
        prefs.edit()
            .putFloat(TopBarLiquidGlassConfig.KEY_MASK_START, topBarMaskStart)
            .putFloat(TopBarLiquidGlassConfig.KEY_MASK_END, topBarMaskEnd)
            .apply()
    }
    GlassSliderRow(tr(lang, "渐变结束", "Fade end"), topBarMaskEnd, 0.04f..1f, { "${(it * 100).toInt()}%" }) { value ->
        topBarMaskEnd = value.coerceAtLeast(topBarMaskStart + 0.04f).coerceAtMost(1f)
        prefs.edit().putFloat(TopBarLiquidGlassConfig.KEY_MASK_END, topBarMaskEnd).apply()
    }
    GlassSliderRow(tr(lang, "顶部透明度", "Top opacity"), topBarTopAlpha, 0f..1f, { "${(it * 100).toInt()}%" }) { value ->
        topBarTopAlpha = value; prefs.edit().putFloat(TopBarLiquidGlassConfig.KEY_TOP_ALPHA, value).apply()
    }
    GlassSliderRow(tr(lang, "底部透明度", "Bottom opacity"), topBarBottomAlpha, 0f..1f, { "${(it * 100).toInt()}%" }) { value ->
        topBarBottomAlpha = value; prefs.edit().putFloat(TopBarLiquidGlassConfig.KEY_BOTTOM_ALPHA, value).apply()
    }
    TextButton(
        onClick = {
            topBarEnabled = true
            topBarBlur = TopBarLiquidGlassConfig.DEFAULT_BLUR
            topBarTint = TopBarLiquidGlassConfig.DEFAULT_TINT_INTENSITY
            topBarMaskHeight = TopBarLiquidGlassConfig.DEFAULT_MASK_HEIGHT
            topBarMaskStart = TopBarLiquidGlassConfig.DEFAULT_MASK_START
            topBarMaskEnd = TopBarLiquidGlassConfig.DEFAULT_MASK_END
            topBarTopAlpha = TopBarLiquidGlassConfig.DEFAULT_TOP_ALPHA
            topBarBottomAlpha = TopBarLiquidGlassConfig.DEFAULT_BOTTOM_ALPHA
            prefs.edit()
                .putBoolean(TopBarLiquidGlassConfig.KEY_ENABLED, true)
                .putFloat(TopBarLiquidGlassConfig.KEY_BLUR, TopBarLiquidGlassConfig.DEFAULT_BLUR)
                .putFloat(TopBarLiquidGlassConfig.KEY_TINT_INTENSITY, TopBarLiquidGlassConfig.DEFAULT_TINT_INTENSITY)
                .putFloat(TopBarLiquidGlassConfig.KEY_MASK_HEIGHT, TopBarLiquidGlassConfig.DEFAULT_MASK_HEIGHT)
                .putFloat(TopBarLiquidGlassConfig.KEY_MASK_START, TopBarLiquidGlassConfig.DEFAULT_MASK_START)
                .putFloat(TopBarLiquidGlassConfig.KEY_MASK_END, TopBarLiquidGlassConfig.DEFAULT_MASK_END)
                .putFloat(TopBarLiquidGlassConfig.KEY_TOP_ALPHA, TopBarLiquidGlassConfig.DEFAULT_TOP_ALPHA)
                .putFloat(TopBarLiquidGlassConfig.KEY_BOTTOM_ALPHA, TopBarLiquidGlassConfig.DEFAULT_BOTTOM_ALPHA)
                .apply()
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) { Text(tr(lang, "恢复顶栏默认值", "Reset top-bar defaults")) }

    SettingsSection(tr(lang, "液态玻璃 · 聊天输入框", "Liquid glass · chat input"))
    LiquidGlassPreview(
        LiquidGlassSpec(
            cornerRadiusDp = cornerRadius,
            blurRadiusDp = blur,
            refractionHeightDp = refractionHeight,
            refractionAmountDp = refractionAmount,
            chromaticAberration = chromatic,
        ),
        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    ToggleSettingsRow(
        HugeIcons.Sparkles,
        tr(lang, "启用液态玻璃", "Enable liquid glass"),
        if (liquidGlassSupported) tr(lang, "苹果风格折射玻璃，随明暗主题自适应", "Apple-style refractive glass, adapts to light/dark")
        else tr(lang, "需要 Android 12+，当前设备自动回退普通样式", "Requires Android 12+; falls back to the plain style here"),
        enabled,
    ) { value -> enabled = value; prefs.edit().putBoolean(LiquidGlassSpec.KEY_ENABLED, value).apply() }
    ToggleSettingsRow(
        HugeIcons.Image02,
        tr(lang, "色散", "Chromatic aberration"),
        tr(lang, "玻璃边缘的彩虹色散效果", "Rainbow dispersion at the glass edge"),
        chromatic,
    ) { value -> chromatic = value; prefs.edit().putBoolean(LiquidGlassSpec.KEY_CHROMATIC, value).apply() }
    GlassSliderRow(tr(lang, "圆角半径", "Corner radius"), cornerRadius, 8f..48f, { "${it.toInt()} dp" }) { value -> cornerRadius = value; prefs.edit().putFloat(LiquidGlassSpec.KEY_CORNER_RADIUS, value).apply() }
    GlassSliderRow(tr(lang, "模糊半径", "Blur radius"), blur, 0f..32f, { "${it.toInt()} dp" }) { value -> blur = value; prefs.edit().putFloat(LiquidGlassSpec.KEY_BLUR, value).apply() }
    GlassSliderRow(tr(lang, "折射高度", "Refraction height"), refractionHeight, 0f..48f, { "${it.toInt()} dp" }) { value -> refractionHeight = value; prefs.edit().putFloat(LiquidGlassSpec.KEY_REFRACTION_HEIGHT, value).apply() }
    GlassSliderRow(tr(lang, "折射强度", "Refraction amount"), refractionAmount, 0f..48f, { "${it.toInt()} dp" }) { value -> refractionAmount = value; prefs.edit().putFloat(LiquidGlassSpec.KEY_REFRACTION_AMOUNT, value).apply() }
    TextButton(
        onClick = {
            enabled = true
            cornerRadius = LiquidGlassSpec.DEFAULT_CORNER_RADIUS
            blur = LiquidGlassSpec.DEFAULT_BLUR
            refractionHeight = LiquidGlassSpec.DEFAULT_REFRACTION_HEIGHT
            refractionAmount = LiquidGlassSpec.DEFAULT_REFRACTION_AMOUNT
            chromatic = LiquidGlassSpec.DEFAULT_CHROMATIC
            prefs.edit()
                .putBoolean(LiquidGlassSpec.KEY_ENABLED, true)
                .putFloat(LiquidGlassSpec.KEY_CORNER_RADIUS, LiquidGlassSpec.DEFAULT_CORNER_RADIUS)
                .putFloat(LiquidGlassSpec.KEY_BLUR, LiquidGlassSpec.DEFAULT_BLUR)
                .putFloat(LiquidGlassSpec.KEY_REFRACTION_HEIGHT, LiquidGlassSpec.DEFAULT_REFRACTION_HEIGHT)
                .putFloat(LiquidGlassSpec.KEY_REFRACTION_AMOUNT, LiquidGlassSpec.DEFAULT_REFRACTION_AMOUNT)
                .putBoolean(LiquidGlassSpec.KEY_CHROMATIC, LiquidGlassSpec.DEFAULT_CHROMATIC)
                .apply()
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) { Text(tr(lang, "恢复默认", "Reset to defaults")) }

    SettingsSection(tr(lang, "液态玻璃 · 用户消息气泡", "Liquid glass · user bubble"))
    val bubbleConfig = UserBubbleLiquidGlassConfig(
        enabled = bubbleEnabled,
        spec = LiquidGlassSpec(
            cornerRadiusDp = bubbleCornerRadius,
            blurRadiusDp = bubbleBlur,
            refractionHeightDp = bubbleRefractionHeight,
            refractionAmountDp = bubbleRefractionAmount,
            chromaticAberration = bubbleChromatic,
        ),
        tintAlpha = bubbleTintAlpha,
    )
    UserBubbleLiquidGlassPreview(bubbleConfig, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    ToggleSettingsRow(
        HugeIcons.Sparkles,
        tr(lang, "启用用户气泡玻璃", "Enable glass user bubble"),
        tr(lang, "仅液态玻璃界面使用；发送动画在所有主题中保持启用", "Used by the Liquid Glass style; send motion stays available in every theme"),
        bubbleEnabled,
    ) { value -> bubbleEnabled = value; prefs.edit().putBoolean(UserBubbleLiquidGlassConfig.KEY_ENABLED, value).apply() }
    ToggleSettingsRow(
        HugeIcons.Image02,
        tr(lang, "气泡色散", "Bubble chromatic aberration"),
        tr(lang, "为气泡折射边缘加入轻微彩色分离", "Adds subtle color separation at the refracted edge"),
        bubbleChromatic,
    ) { value -> bubbleChromatic = value; prefs.edit().putBoolean(UserBubbleLiquidGlassConfig.KEY_CHROMATIC, value).apply() }
    GlassSliderRow(tr(lang, "气泡底色", "Bubble tint"), bubbleTintAlpha, 0.08f..0.58f, { "${(it * 100).toInt()}%" }) { value -> bubbleTintAlpha = value; prefs.edit().putFloat(UserBubbleLiquidGlassConfig.KEY_TINT_ALPHA, value).apply() }
    GlassSliderRow(tr(lang, "气泡圆角", "Bubble corner radius"), bubbleCornerRadius, 12f..36f, { "${it.toInt()} dp" }) { value -> bubbleCornerRadius = value; prefs.edit().putFloat(UserBubbleLiquidGlassConfig.KEY_CORNER_RADIUS, value).apply() }
    GlassSliderRow(tr(lang, "气泡模糊", "Bubble blur"), bubbleBlur, 0f..24f, { "${it.toInt()} dp" }) { value -> bubbleBlur = value; prefs.edit().putFloat(UserBubbleLiquidGlassConfig.KEY_BLUR, value).apply() }
    GlassSliderRow(tr(lang, "气泡折射高度", "Bubble refraction height"), bubbleRefractionHeight, 0f..36f, { "${it.toInt()} dp" }) { value -> bubbleRefractionHeight = value; prefs.edit().putFloat(UserBubbleLiquidGlassConfig.KEY_REFRACTION_HEIGHT, value).apply() }
    GlassSliderRow(tr(lang, "气泡折射强度", "Bubble refraction amount"), bubbleRefractionAmount, 0f..40f, { "${it.toInt()} dp" }) { value -> bubbleRefractionAmount = value; prefs.edit().putFloat(UserBubbleLiquidGlassConfig.KEY_REFRACTION_AMOUNT, value).apply() }
    TextButton(
        onClick = {
            bubbleEnabled = true
            bubbleCornerRadius = UserBubbleLiquidGlassConfig.DEFAULT_CORNER_RADIUS
            bubbleBlur = UserBubbleLiquidGlassConfig.DEFAULT_BLUR
            bubbleRefractionHeight = UserBubbleLiquidGlassConfig.DEFAULT_REFRACTION_HEIGHT
            bubbleRefractionAmount = UserBubbleLiquidGlassConfig.DEFAULT_REFRACTION_AMOUNT
            bubbleChromatic = UserBubbleLiquidGlassConfig.DEFAULT_CHROMATIC
            bubbleTintAlpha = UserBubbleLiquidGlassConfig.DEFAULT_TINT_ALPHA
            prefs.edit()
                .putBoolean(UserBubbleLiquidGlassConfig.KEY_ENABLED, true)
                .putFloat(UserBubbleLiquidGlassConfig.KEY_CORNER_RADIUS, UserBubbleLiquidGlassConfig.DEFAULT_CORNER_RADIUS)
                .putFloat(UserBubbleLiquidGlassConfig.KEY_BLUR, UserBubbleLiquidGlassConfig.DEFAULT_BLUR)
                .putFloat(UserBubbleLiquidGlassConfig.KEY_REFRACTION_HEIGHT, UserBubbleLiquidGlassConfig.DEFAULT_REFRACTION_HEIGHT)
                .putFloat(UserBubbleLiquidGlassConfig.KEY_REFRACTION_AMOUNT, UserBubbleLiquidGlassConfig.DEFAULT_REFRACTION_AMOUNT)
                .putBoolean(UserBubbleLiquidGlassConfig.KEY_CHROMATIC, UserBubbleLiquidGlassConfig.DEFAULT_CHROMATIC)
                .putFloat(UserBubbleLiquidGlassConfig.KEY_TINT_ALPHA, UserBubbleLiquidGlassConfig.DEFAULT_TINT_ALPHA)
                .apply()
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) { Text(tr(lang, "恢复气泡默认值", "Reset bubble defaults")) }
}

@Composable
private fun GlassSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String, onValueChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

/**
 * A container-transform playground inspired by FlClash's OpenContainer, rebuilt on Compose's
 * shared-element APIs and pushed further: the tapped card grows and glides toward the center
 * while the whole list recedes (scales down + dims) like a modern OS app-open animation.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PageTransitionPlayground(lang: String, modifier: Modifier = Modifier) {
    val items = remember {
        listOf(
            PlaygroundCard(
                "transform", "容器变换", "Container transform",
                "卡片展开为整页", "Card expands into a full page",
                "点击卡片后，它会从原来的位置平滑放大并移动到屏幕中心，背后的列表整体下压、变暗，形成类似现代系统打开应用的纵深感。",
                "Tap a card and it grows from its spot toward the center while the list behind recedes and dims, like a modern OS app-open animation.",
                HugeIcons.Sparkles, Color(0xFF7C6FF0),
            ),
            PlaygroundCard(
                "shared", "共享元素", "Shared element",
                "跨页面边界变形", "Morphs across page boundaries",
                "共享元素转场会让同一个视觉元素在两个页面之间连续变形，而不是简单的淡入淡出，从而保持空间的连贯性。",
                "A shared element morphs continuously between two pages instead of a plain cross-fade, keeping spatial continuity.",
                HugeIcons.Refresh03, Color(0xFF2E9E8F),
            ),
            PlaygroundCard(
                "motion", "运动曲线", "Motion curves",
                "强调减速与弹性", "Emphasized easing and springs",
                "打开使用快入慢出的强调曲线，让元素先快速移动、再轻柔落定；返回时列表从缩小状态弹回原位。",
                "Opening uses a fast-then-gentle emphasized curve; returning springs the list back from its receded state.",
                HugeIcons.Moon02, Color(0xFFE0823D),
            ),
        )
    }
    var selected by remember { mutableStateOf<PlaygroundCard?>(null) }
    val motionEasing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
    SharedTransitionLayout(modifier) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                if (targetState != null) {
                    // Opening: the detail fades in while the grid recedes (pushed down + dimmed).
                    fadeIn(tween(170, delayMillis = 150, easing = LinearOutSlowInEasing))
                        .togetherWith(
                            scaleOut(targetScale = 0.94f, animationSpec = tween(360, easing = motionEasing)) +
                                fadeOut(tween(210, delayMillis = 90, easing = FastOutSlowInEasing), targetAlpha = 0.3f),
                        )
                } else {
                    // Returning: the grid springs back from its receded state while the detail
                    // collapses into the originating card.
                    (fadeIn(tween(230, easing = LinearOutSlowInEasing)) +
                        scaleIn(initialScale = 0.94f, animationSpec = tween(360, easing = motionEasing)))
                        .togetherWith(fadeOut(tween(150, delayMillis = 150), targetAlpha = 0.4f))
                }
            },
            label = "playgroundContainer",
        ) { target ->
            if (target == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Text(
                            tr(lang, "点击下方卡片，体验容器变换转场", "Tap a card to preview the container transform"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(items, key = { it.id }) { item ->
                        Surface(
                            onClick = { selected = item },
                            modifier = Modifier
                                .fillMaxWidth()
                                .sharedBounds(
                                    rememberSharedContentState(key = "playground-${item.id}"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                ),
                            shape = RoundedCornerShape(26.dp),
                            color = item.color.copy(alpha = 0.15f),
                        ) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(46.dp), shape = RoundedCornerShape(16.dp), color = item.color) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(item.icon, null, Modifier.size(23.dp), tint = Color.White)
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(tr(lang, item.titleZh, item.titleEn), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(tr(lang, item.subtitleZh, item.subtitleEn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(HugeIcons.ArrowRight01, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedBounds(
                            rememberSharedContentState(key = "playground-${target.id}"),
                            animatedVisibilityScope = this@AnimatedContent,
                        ),
                    shape = RoundedCornerShape(26.dp),
                    color = target.color.copy(alpha = 0.12f),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { selected = null }) { Icon(HugeIcons.ArrowLeft01, null) }
                            Text(tr(lang, target.titleZh, target.titleEn), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Surface(
                            Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = target.color,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(target.icon, null, Modifier.size(72.dp), tint = Color.White)
                            }
                        }
                        Text(
                            tr(lang, target.bodyZh, target.bodyEn),
                            modifier = Modifier.padding(22.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 26.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}


