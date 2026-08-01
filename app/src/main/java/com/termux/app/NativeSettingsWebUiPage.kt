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
internal fun WebUiSettingsPage(
    lang: String,
    prefs: SharedPreferences,
    activeProfile: CodexProviderStore.Profile?,
    onBack: () -> Unit,
    onOpenWebUi: () -> Unit,
    onOpenDashboard: () -> Unit,
    onClearCache: () -> Unit,
) {
    var fullscreen by remember { mutableStateOf(prefs.getBoolean("webui_fullscreen", true)) }
    var restartBackend by remember { mutableStateOf(prefs.getBoolean("restart_backend_on_config_change", true)) }
    var routeApi by remember { mutableStateOf(prefs.getBoolean("mihomo_route_api", false)) }
    var customRootEnabled by remember { mutableStateOf(prefs.getBoolean("custom_project_root_enabled", true)) }
    var customRoot by remember { mutableStateOf(prefs.getString("custom_project_root", "/storage/emulated/0/").orEmpty()) }
    var confirmReset by remember { mutableStateOf(false) }
    val canLaunch = activeProfile != null && activeProfile.baseUrl.isNotBlank() && activeProfile.apiKey.isNotBlank()
    SettingsScaffold("WebUI", tr(lang, "管理入口、显示方式与本地界面数据", "Manage launch, display and local UI data"), onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Codex WebUI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (canLaunch) tr(lang, "使用 ${activeProfile.name} 启动，并保留上次打开的页面。", "Launch with ${activeProfile.name} and keep the last open page.")
                            else tr(lang, "请先在“模型与 API”中完成配置。", "Complete Models & API setup first."),
                            Modifier.padding(top = 6.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f),
                        )
                        Button(onOpenWebUi, Modifier.fillMaxWidth().height(50.dp), enabled = canLaunch, shape = RoundedCornerShape(15.dp)) {
                            Icon(HugeIcons.Code, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text(tr(lang, "打开 WebUI", "Open WebUI"))
                        }
                    }
                }
            }
            item { SettingsSection(tr(lang, "显示与运行", "Display & runtime")) }
            item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "WebUI 全屏", "WebUI fullscreen"), tr(lang, "进入 WebUI 时隐藏原生工具栏", "Hide the native toolbar in WebUI"), fullscreen) { fullscreen = it; prefs.edit().putBoolean("webui_fullscreen", it).apply() } }
            item { ToggleSettingsRow(HugeIcons.Refresh03, tr(lang, "切换配置后重启", "Restart after config change"), tr(lang, "切换 API 后重新启动 WebUI 后端", "Restart the WebUI backend after switching APIs"), restartBackend) { restartBackend = it; prefs.edit().putBoolean("restart_backend_on_config_change", it).apply() } }
            item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "应用内请求使用 Mihomo", "Route app requests through Mihomo"), tr(lang, "仅影响 Fcode 发出的 API 请求", "Only affects API requests made by Fcode"), routeApi) { routeApi = it; prefs.edit().putBoolean("mihomo_route_api", it).apply() } }
            item { SettingsSection(tr(lang, "项目与存储", "Projects & storage")) }
            item { ToggleSettingsRow(HugeIcons.Folder01, tr(lang, "自定义项目默认目录", "Custom default project root"), tr(lang, "WebUI 打开项目时优先使用指定目录", "Use a preferred folder when opening projects"), customRootEnabled) { customRootEnabled = it; prefs.edit().putBoolean("custom_project_root_enabled", it).apply() } }
            if (customRootEnabled) item { SettingsTextField(customRoot, { customRoot = it; prefs.edit().putString("custom_project_root", it.trim()).apply() }, tr(lang, "默认项目目录", "Default project root"), "/storage/emulated/0/") }
            item { SettingsSection(tr(lang, "工具与数据", "Tools & data")) }
            item { NavigationSettingsRow(HugeIcons.Code, "MetaCubeXD", tr(lang, "查看代理流量、规则和连接", "Inspect proxy traffic, rules and connections"), onOpenDashboard) }
            item { NavigationSettingsRow(HugeIcons.Refresh03, tr(lang, "重置 WebUI 偏好与缓存", "Reset WebUI preferences & cache"), tr(lang, "不会删除 API 配置、会话和技能", "Keeps API configurations, conversations and skills")) { confirmReset = true } }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text(tr(lang, "重置 WebUI？", "Reset WebUI?")) },
        text = { Text(tr(lang, "这会删除 WebUI 的语言、外观、项目列表和本地界面状态，但不会删除 API 配置、Codex 会话和技能。", "This removes WebUI language, appearance, project list and local UI state, but keeps API configurations, Codex conversations and skills.")) },
        dismissButton = { TextButton({ confirmReset = false }) { Text(tr(lang, "取消", "Cancel")) } },
        confirmButton = { TextButton({ confirmReset = false; onClearCache() }) { Text(tr(lang, "确认重置", "Reset"), color = MaterialTheme.colorScheme.error) } },
    )
}

@Immutable
internal data class PlaygroundCard(
    val id: String,
    val titleZh: String,
    val titleEn: String,
    val subtitleZh: String,
    val subtitleEn: String,
    val bodyZh: String,
    val bodyEn: String,
    val icon: ImageVector,
    val color: Color,
)


