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
internal fun AppearanceSettingsPage(
    lang: String,
    theme: String,
    colorPalette: String,
    interfaceStyle: String,
    onBack: () -> Unit,
    onTheme: () -> Unit,
    onChat: () -> Unit,
) {
    SettingsScaffold(
        tr(lang, "\u5916\u89c2", "Appearance"),
        tr(lang, "\u5206\u5f00\u7ba1\u7406\u4e3b\u9898\u4e0e\u804a\u5929\u754c\u9762", "Manage theme and chat presentation separately"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item { SettingsSection(tr(lang, "\u5206\u7c7b", "Categories")) }
            item { NavigationSettingsRow(HugeIcons.Moon02, tr(lang, "\u4e3b\u9898", "Theme"), appearanceLabel(lang, colorPalette, theme), onTheme) }
            item { NavigationSettingsRow(HugeIcons.Sparkles, tr(lang, "\u804a\u5929", "Chat"), tr(lang, "\u6d41\u5f0f\u52a8\u753b\u3001\u81ea\u52a8\u8ddf\u968f\u3001\u601d\u8003\u4e0e\u5c3e\u90e8\u4fe1\u606f", "Streaming, following, reasoning and response footer"), onChat) }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
internal fun ChatAppearanceSettingsPage(
    lang: String,
    animations: Boolean,
    fixedStreamingViewport: Boolean,
    reasoning: Boolean,
    follow: Boolean,
    showResponseStats: Boolean,
    showModelSubtitle: Boolean,
    showReasoningTitles: Boolean,
    hideStatusBar: Boolean,
    compactComposerOnScroll: Boolean,
    onBack: () -> Unit,
    onAnimations: (Boolean) -> Unit,
    onFixedStreamingViewport: (Boolean) -> Unit,
    onReasoning: (Boolean) -> Unit,
    onFollow: (Boolean) -> Unit,
    onShowResponseStats: (Boolean) -> Unit,
    onShowModelSubtitle: (Boolean) -> Unit,
    onShowReasoningTitles: (Boolean) -> Unit,
    onHideStatusBar: (Boolean) -> Unit,
    onCompactComposerOnScroll: (Boolean) -> Unit,
) {
    SettingsScaffold(
        tr(lang, "\u804a\u5929", "Chat"),
        tr(lang, "\u63a7\u5236\u751f\u6210\u52a8\u753b\u3001\u5e03\u5c40\u4e0e\u56de\u7b54\u8be6\u60c5", "Control generation motion, layout and response details"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item { SettingsSection(tr(lang, "\u751f\u6210", "Generation")) }
            item { ToggleSettingsRow(HugeIcons.Sparkles, tr(lang, "\u6d41\u5f0f\u52a8\u753b", "Streaming animation"), tr(lang, "\u7528\u6e10\u53d8\u906e\u7f69\u663e\u793a\u65b0\u751f\u6210\u6587\u5b57", "Reveal newly generated text with a gradient mask"), animations, onAnimations) }
            item { ToggleSettingsRow(HugeIcons.Text, tr(lang, "\u751f\u6210\u65f6\u56fa\u5b9a\u6b63\u6587\u9ad8\u5ea6", "Fixed streaming viewport"), tr(lang, "\u964d\u4f4e\u957f\u56de\u7b54\u6301\u7eed\u589e\u957f\u65f6\u7684\u5e03\u5c40\u5f00\u9500", "Reduce layout work while long responses grow"), fixedStreamingViewport, onFixedStreamingViewport) }
            item { ToggleSettingsRow(HugeIcons.ArrowRight01, tr(lang, "\u81ea\u52a8\u8ddf\u968f\u56de\u7b54", "Auto-follow output"), tr(lang, "\u751f\u6210\u65f6\u4fdd\u6301\u6700\u65b0\u5185\u5bb9\u53ef\u89c1", "Keep the newest output visible while generating"), follow, onFollow) }
            item { SettingsSection(tr(lang, "\u754c\u9762", "Interface")) }
            item { ToggleSettingsRow(HugeIcons.LookTop, tr(lang, "\u9690\u85cf\u72b6\u6001\u680f", "Hide status bar"), tr(lang, "\u8ba9\u539f\u751f\u804a\u5929\u754c\u9762\u4f7f\u7528\u66f4\u5927\u7684\u53ef\u89c6\u533a\u57df", "Use more vertical space in the native chat UI"), hideStatusBar, onHideStatusBar) }
            item { ToggleSettingsRow(HugeIcons.Text, tr(lang, "滚动时收起输入框", "Compact composer on scroll"), tr(lang, "滑动对话后将输入框收成玻璃长条；点击长条会弹性展开", "Collapse the composer into a glass pill after scrolling; tap it to spring open"), compactComposerOnScroll, onCompactComposerOnScroll) }
            item { SettingsSection(tr(lang, "\u5185\u5bb9", "Content")) }
            item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "\u601d\u8003\u8fc7\u7a0b", "Reasoning"), tr(lang, "\u5728\u56de\u7b54\u4e2d\u663e\u793a\u6a21\u578b\u7684\u63a8\u7406\u6458\u8981", "Show model reasoning summaries"), reasoning, onReasoning) }
            item { ToggleSettingsRow(HugeIcons.Sparkles, tr(lang, "\u601d\u8003\u6807\u9898", "Reasoning titles"), tr(lang, "\u5c06 Sol \u7b49\u6a21\u578b\u8f93\u51fa\u7684\u7b80\u77ed\u601d\u8003\u6458\u8981\u663e\u793a\u4e3a\u80f6\u56ca\u6807\u9898", "Use short reasoning summaries from models such as Sol as the capsule title"), showReasoningTitles, onShowReasoningTitles) }
            item { ToggleSettingsRow(HugeIcons.Text, tr(lang, "\u9876\u680f\u663e\u793a\u6a21\u578b", "Show model in header"), tr(lang, "\u5728\u5bf9\u8bdd\u6807\u9898\u4e0b\u663e\u793a\u5f53\u524d\u6a21\u578b\u540d\u79f0", "Show the active model below the conversation title"), showModelSubtitle, onShowModelSubtitle) }
            item { ToggleSettingsRow(HugeIcons.Text, tr(lang, "\u663e\u793a\u56de\u7b54\u5c3e\u90e8\u4fe1\u606f", "Show response footer"), tr(lang, "\u663e\u793a Token\u3001\u8f93\u51fa\u901f\u5ea6\u3001\u8017\u65f6\u4e0e\u7f13\u5b58\u7528\u91cf", "Show tokens, output speed, duration and cached usage"), showResponseStats, onShowResponseStats) }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}


