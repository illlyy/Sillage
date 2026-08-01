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


private data class SettingsEnvironmentSnapshot(
    val loaded: Boolean = false,
    val installedToolCount: Int = 0,
    val bootstrapInstalled: Boolean = false,
    val mihomoSupported: Boolean = true,
    val mihomoInstalled: Boolean = false,
    val mihomoRunning: Boolean = false,
    val mihomoMixedPort: Int = MihomoManager.DEFAULT_MIXED_PORT,
    val overlayGranted: Boolean = false,
    val overlayEnabled: Boolean = false,
)


@Composable
internal fun SettingsRootPage(
    lang: String,
    provider: CodexProviderStore.Profile?,
    onBack: () -> Unit,
    onModels: () -> Unit,
    onWebUi: () -> Unit,
    onTermux: () -> Unit,
    onPinWebUiShortcut: () -> Unit,
    onPinTermuxShortcut: () -> Unit,
    onInstallCodexCli: () -> Unit,
    codexCliInstalled: Boolean,
    onProxy: () -> Unit,
    onOverlay: () -> Unit,
    onDevelopmentTools: () -> Unit,
    onAppearance: () -> Unit,
    onMcp: () -> Unit,
    onSkills: () -> Unit,
    onLanguage: () -> Unit,
    onTypography: () -> Unit,
    onDeveloper: () -> Unit,
    environmentRevision: Int,
    prefs: SharedPreferences,
    onAbout: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val environment by produceState(
        initialValue = SettingsEnvironmentSnapshot(),
        key1 = context.applicationContext,
        key2 = environmentRevision,
    ) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val manager = MihomoManager.get(context.applicationContext)
            SettingsEnvironmentSnapshot(
                loaded = true,
                installedToolCount = DevelopmentToolInstaller.installedIds().size,
                bootstrapInstalled = DevelopmentToolInstaller.isBootstrapInstalled(),
                mihomoSupported = manager.isSupported,
                mihomoInstalled = manager.isInstalled,
                mihomoRunning = manager.isRunning,
                mihomoMixedPort = manager.mixedPort(),
                overlayGranted = canDrawOverlays(context),
                overlayEnabled = prefs.getBoolean("overlay_enabled", false),
            )
        }
    }
    SettingsScaffold(
        tr(lang, "设置", "Settings"),
        tr(lang, "调整 Fcode 的模型、界面与运行方式", "Configure models, appearance and runtime behavior"),
        onBack,
    ) { contentPadding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item { ActiveProviderCard(lang, provider, onModels) }
            item { SettingsSection(tr(lang, "服务与工具", "Services & tools")) }
            if (!codexCliInstalled) {
                item {
                    NavigationSettingsRow(
                        HugeIcons.Code,
                        "Codex CLI",
                        tr(lang, "未安装 · WebUI 和 Termux 需要此组件", "Not installed · required by WebUI and Termux"),
                        onInstallCodexCli,
                    )
                }
            }
            item {
                NavigationSettingsRow(
                    HugeIcons.Sparkles,
                    tr(lang, "模型与 API", "Models & API"),
                    if (provider == null) tr(lang, "添加服务商、密钥与默认模型", "Add a provider, key and default model")
                    else "${provider.name} · ${provider.model.ifBlank { tr(lang, "未选择模型", "No model") }}",
                    onModels,
                )
            }
            item { NavigationSettingsRow(HugeIcons.Code, "WebUI", tr(lang, "入口、全屏、项目目录与缓存", "Launch, fullscreen, project root and cache"), onWebUi) }
            item { NavigationSettingsRow(HugeIcons.Code, tr(lang, "Termux 终端", "Termux terminal"), tr(lang, "检查工具、运行命令和管理项目文件", "Inspect tools, run commands and manage project files"), onTermux) }
            item { SettingsSection(tr(lang, "桌面快捷方式", "Home screen shortcuts")) }
            item {
                LauncherShortcutsCard(
                    lang = lang,
                    onPinWebUi = onPinWebUiShortcut,
                    onPinTermux = onPinTermuxShortcut,
                )
            }
            item { SettingsSection(tr(lang, "扩展与连接", "Extensions & connections")) }
            item { NavigationSettingsRow(HugeIcons.Code, "MCP", tr(lang, "\u8fde\u63a5\u5916\u90e8\u5de5\u5177\u3001\u6570\u636e\u6e90\u4e0e\u8fdc\u7a0b\u670d\u52a1", "Connect external tools, data sources and remote services"), onMcp) }
            item { NavigationSettingsRow(HugeIcons.Sparkles, "Skills", tr(lang, "\u6d4f\u89c8\u5b98\u65b9 Skill \u5e76\u7ba1\u7406\u5df2\u5b89\u88c5\u5185\u5bb9", "Browse official skills and manage installed skills"), onSkills) }
            item {
                val proxyStatus = when {
                    !environment.loaded -> tr(lang, "正在检查运行状态…", "Checking runtime status…")
                    !environment.mihomoSupported -> tr(lang, "当前设备不支持内置内核", "Built-in core is unsupported on this device")
                    !environment.mihomoInstalled -> tr(lang, "未安装 · 可离线安装", "Not installed · offline install available")
                    environment.mihomoRunning -> tr(
                        lang,
                        "运行中 · 127.0.0.1:${environment.mihomoMixedPort}",
                        "Running · 127.0.0.1:${environment.mihomoMixedPort}",
                    )
                    else -> tr(lang, "已安装 · 当前已停止", "Installed · currently stopped")
                }
                NavigationSettingsRow(HugeIcons.Code, tr(lang, "网络与代理", "Network & proxy"), proxyStatus, onProxy)
            }
            item { SettingsSection(tr(lang, "外观", "Appearance")) }
            item { NavigationSettingsRow(HugeIcons.Moon02, tr(lang, "\u5916\u89c2", "Appearance"), tr(lang, "\u4e3b\u9898\u3001\u804a\u5929\u754c\u9762\u4e0e\u751f\u6210\u663e\u793a", "Theme, chat interface and generation display"), onAppearance) }
            item { NavigationSettingsRow(HugeIcons.LanguageCircle, tr(lang, "语言", "Language"), tr(lang, "简体中文 / 跟随系统", "English / System"), onLanguage) }
            item { NavigationSettingsRow(HugeIcons.Text, tr(lang, "文字与 Markdown", "Typography & Markdown"), tr(lang, "代码、表格、列表与公式", "Code, tables, lists and math"), onTypography) }
            item { NavigationSettingsRow(HugeIcons.Code, tr(lang, "开发者选项", "Developer options"), tr(lang, "页面切换动画与交互测试", "Page transition and interaction testing"), onDeveloper) }
            item { SettingsSection(tr(lang, "系统", "System")) }
            item {
                val developmentSummary = when {
                    !environment.loaded -> tr(lang, "正在检查已安装工具…", "Checking installed tools…")
                    environment.bootstrapInstalled -> tr(
                        lang,
                        "已安装 ${environment.installedToolCount} 项 · 可选择语言、构建与终端工具",
                        "${environment.installedToolCount} installed · choose languages, build and terminal tools",
                    )
                    else -> tr(lang, "基础环境未安装 · 可按需或一键安装完整环境", "Base environment missing · install selected tools or the full environment")
                }
                NavigationSettingsRow(
                    HugeIcons.Code,
                    tr(lang, "开发工具与环境", "Development tools & environment"),
                    developmentSummary,
                    onDevelopmentTools,
                )
            }
            item {
                val overlaySummary = when {
                    !environment.loaded -> tr(lang, "正在检查系统权限…", "Checking system permission…")
                    environment.overlayEnabled && environment.overlayGranted -> tr(lang, "悬浮球已开启 · 手势、提醒与后台保持", "Bubble enabled · gestures, reminders and keep-alive")
                    !environment.overlayGranted -> tr(lang, "需要悬浮窗权限 · 配置手势与完成提醒", "Overlay permission required · configure gestures and completion alerts")
                    else -> tr(lang, "已关闭 · 配置手势、提醒与后台保持", "Off · configure gestures, reminders and keep-alive")
                }
                NavigationSettingsRow(HugeIcons.Sparkles, tr(lang, "悬浮窗与后台", "Floating window & background"), overlaySummary, onOverlay)
            }
            item {
                NavigationSettingsRow(
                    HugeIcons.Settings03,
                    tr(lang, "关于应用", "About app"),
                    tr(lang, "版本、更新、作者与 GitHub 仓库", "Version, updates, author and GitHub repository"),
                    onAbout,
                )
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun LauncherShortcutsCard(
    lang: String,
    onPinWebUi: () -> Unit,
    onPinTermux: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
        Column {
            LauncherShortcutRow(
                title = "WebUI",
                subtitle = tr(lang, "从桌面直接进入 WebUI", "Open WebUI directly from the home screen"),
                actionLabel = tr(lang, "添加", "Add"),
                onClick = onPinWebUi,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
            )
            LauncherShortcutRow(
                title = tr(lang, "Termux 终端", "Termux terminal"),
                subtitle = tr(lang, "从桌面直接启动内置终端", "Launch the built-in terminal from the home screen"),
                actionLabel = tr(lang, "添加", "Add"),
                onClick = onPinTermux,
            )
        }
    }
}

@Composable
private fun LauncherShortcutRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(HugeIcons.Code)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(HugeIcons.Add01, null, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(actionLabel)
        }
    }
}


