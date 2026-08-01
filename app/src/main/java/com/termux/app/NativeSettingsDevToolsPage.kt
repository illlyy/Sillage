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
internal fun DevelopmentToolsSettingsPage(
    lang: String,
    prefs: SharedPreferences,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val selectionKey = "native_development_tool_selection_v1"
    val initialSelection = remember {
        if (prefs.contains(selectionKey)) {
            prefs.getString(selectionKey, "").orEmpty().split(',').filter(String::isNotBlank).toSet()
        } else {
            DevelopmentToolCatalog.recommendedIds
        }
    }
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(initialSelection) }
    var installedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bootstrapInstalled by remember { mutableStateOf(false) }
    var installLogAvailable by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var installStage by remember { mutableStateOf("") }
    var installDetail by remember { mutableStateOf("") }
    var installError by remember { mutableStateOf<String?>(null) }
    var confirmInstall by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    fun refreshDevelopmentEnvironment() {
        scope.launch {
            val snapshot = withContext(kotlinx.coroutines.Dispatchers.IO) {
                Triple(
                    DevelopmentToolInstaller.installedIds(),
                    DevelopmentToolInstaller.isBootstrapInstalled(),
                    DevelopmentToolInstaller.logFile().isFile,
                )
            }
            installedIds = snapshot.first
            bootstrapInstalled = snapshot.second
            installLogAvailable = snapshot.third
        }
    }

    LaunchedEffect(Unit) { refreshDevelopmentEnvironment() }

    fun updateSelection(value: Set<String>) {
        selectedIds = value.intersect(DevelopmentToolCatalog.allIds)
        prefs.edit().putString(selectionKey, selectedIds.joinToString(",")).apply()
    }

    fun installCodexIfSelected(selectedTools: List<DevelopmentTool>) {
        val codex = selectedTools.firstOrNull { it.codexCli } ?: return
        val host = activity ?: run {
            installError = tr(lang, "当前页面无法启动安装器", "The installer cannot be started from this context")
            return
        }
        scope.launch {
            val alreadyInstalled = withContext(kotlinx.coroutines.Dispatchers.IO) {
                DevelopmentToolInstaller.isInstalled(codex)
            }
            if (alreadyInstalled) {
                refreshDevelopmentEnvironment()
                return@launch
            }
            CodexInstaller.setupBootstrapIfNeeded(host) {
                scope.launch {
                    refreshDevelopmentEnvironment()
                    Toast.makeText(context, tr(lang, "Codex CLI 已安装", "Codex CLI installed"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun installSelectedTools() {
        val selectedTools = DevelopmentToolCatalog.tools.filter { it.id in selectedIds }
        if (selectedTools.isEmpty()) return
        val aptTools = selectedTools.filter { it.aptPackages.isNotEmpty() }
        val host = activity ?: run {
            installError = tr(lang, "当前页面无法启动安装器", "The installer cannot be started from this context")
            return
        }
        if (aptTools.isEmpty()) {
            installCodexIfSelected(selectedTools)
            return
        }
        installStage = tr(lang, "正在准备基础环境", "Preparing base environment")
        installDetail = tr(lang, "首次使用需要解压 Termux bootstrap", "The Termux bootstrap is extracted on first use")
        TermuxInstaller.setupBootstrapIfNeeded(host) {
            installing = true
            DevelopmentToolInstaller.installAptTools(
                activity = host,
                tools = aptTools,
                onProgress = { stage, detail ->
                    installStage = when (stage) {
                        "bootstrap" -> tr(lang, "正在准备基础环境", "Preparing base environment")
                        else -> tr(lang, "正在安装所选工具", "Installing selected tools")
                    }
                    installDetail = detail.ifBlank { tr(lang, "正在等待包管理器…", "Waiting for the package manager…") }
                },
                onComplete = { installed, log ->
                    installedIds = installed
                    installing = false
                    installStage = ""
                    installDetail = ""
                    Toast.makeText(
                        context,
                        tr(lang, "工具安装完成 · 日志：${log.name}", "Tools installed · log: ${log.name}"),
                        Toast.LENGTH_LONG,
                    ).show()
                    installCodexIfSelected(selectedTools)
                },
                onError = { message, log ->
                    installing = false
                    installError = tr(
                        lang,
                        "安装未完成：$message\n\n日志：${log.absolutePath}",
                        "Installation did not finish: $message\n\nLog: ${log.absolutePath}",
                    )
                    refreshDevelopmentEnvironment()
                },
            )
        }
    }

    SettingsScaffold(
        tr(lang, "开发工具与环境", "Development tools & environment"),
        tr(lang, "按需安装语言、构建、终端和媒体工具", "Install languages, build, terminal and media tools on demand"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                DevelopmentEnvironmentStatusCard(
                    lang = lang,
                    bootstrapInstalled = bootstrapInstalled,
                    installedCount = installedIds.size,
                    selectedCount = selectedIds.size,
                )
            }
            item { SettingsSection(tr(lang, "快速选择", "Quick selection")) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { updateSelection(DevelopmentToolCatalog.recommendedIds) },
                            modifier = Modifier.weight(1f),
                            enabled = !installing,
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(tr(lang, "推荐环境", "Recommended")) }
                        FilledTonalButton(
                            onClick = { updateSelection(DevelopmentToolCatalog.allIds) },
                            modifier = Modifier.weight(1f),
                            enabled = !installing,
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(tr(lang, "完整环境", "Full environment")) }
                    }
                    TextButton(
                        onClick = { updateSelection(emptySet()) },
                        modifier = Modifier.align(Alignment.End),
                        enabled = !installing,
                    ) { Text(tr(lang, "清除选择", "Clear selection")) }
                }
            }
            DevelopmentToolCategory.entries.forEach { category ->
                val tools = DevelopmentToolCatalog.tools.filter { it.category == category }
                item(key = "category-${category.name}") {
                    SettingsSection(developmentToolCategoryLabel(lang, category))
                }
                items(tools, key = { it.id }) { tool ->
                    DevelopmentToolRow(
                        lang = lang,
                        tool = tool,
                        selected = tool.id in selectedIds,
                        installed = tool.id in installedIds,
                        enabled = !installing,
                    ) {
                        updateSelection(
                            if (tool.id in selectedIds) selectedIds - tool.id else selectedIds + tool.id,
                        )
                    }
                }
            }
            item { SettingsSection(tr(lang, "安装", "Install")) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Button(
                        onClick = { confirmInstall = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedIds.isNotEmpty() && !installing,
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Icon(HugeIcons.Code, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr(lang, "安装所选工具（${selectedIds.size}）", "Install selected tools (${selectedIds.size})"))
                    }
                    if (installLogAvailable) {
                        TextButton(
                            onClick = { showLog = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text(tr(lang, "查看安装日志", "View installation log")) }
                    }
                    Text(
                        tr(
                            lang,
                            "Termux 工具从当前软件源下载；Codex CLI 从 OpenAI 官方 GitHub Release 下载。完整环境可能占用 1 GB 以上空间。",
                            "Termux tools use the configured package repositories; Codex CLI uses the official OpenAI GitHub Release. The full environment may use more than 1 GB.",
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    if (confirmInstall) {
        val selectedTools = DevelopmentToolCatalog.tools.filter { it.id in selectedIds }
        AlertDialog(
            onDismissRequest = { confirmInstall = false },
            icon = { Icon(HugeIcons.Code, null) },
            title = { Text(tr(lang, "确认安装", "Confirm installation")) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(tr(lang, "将安装 ${selectedTools.size} 组工具：", "${selectedTools.size} tool groups will be installed:"))
                    Text(
                        selectedTools.joinToString(" · ") { developmentToolTitle(lang, it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (selectedIds == DevelopmentToolCatalog.allIds) {
                        Text(
                            tr(lang, "完整环境下载量较大，请确认网络稳定并预留足够存储空间。", "The full environment is a large download. Ensure a stable network and sufficient storage."),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmInstall = false }) { Text(tr(lang, "取消", "Cancel")) } },
            confirmButton = {
                Button(onClick = {
                    confirmInstall = false
                    installSelectedTools()
                }) { Text(tr(lang, "开始安装", "Start installation")) }
            },
            shape = RoundedCornerShape(28.dp),
        )
    }

    if (installing) {
        AlertDialog(
            onDismissRequest = {},
            icon = { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) },
            title = { Text(installStage.ifBlank { tr(lang, "正在安装", "Installing") }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        installDetail.ifBlank { tr(lang, "正在等待包管理器…", "Waiting for the package manager…") },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        tr(lang, "安装期间请保持应用在前台，不要强制结束进程。", "Keep the app in the foreground and do not force-stop it during installation."),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            shape = RoundedCornerShape(28.dp),
        )
    }

    installError?.let { error ->
        AlertDialog(
            onDismissRequest = { installError = null },
            title = { Text(tr(lang, "安装未完成", "Installation incomplete")) },
            text = {
                Text(
                    error,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            dismissButton = {
                TextButton(onClick = { showLog = true }) { Text(tr(lang, "查看日志", "View log")) }
            },
            confirmButton = {
                Button(onClick = { installError = null }) { Text(tr(lang, "关闭", "Close")) }
            },
            shape = RoundedCornerShape(28.dp),
        )
    }

    val installLogText by produceState(initialValue = "", key1 = showLog) {
        if (showLog) {
            value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { DevelopmentToolInstaller.logFile().readText(Charsets.UTF_8).takeLast(12_000) }
                    .getOrElse { it.message.orEmpty() }
            }
        }
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text(tr(lang, "安装日志", "Installation log")) },
            text = {
                Text(
                    installLogText.ifBlank { tr(lang, "暂无日志", "No log yet") },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { Button(onClick = { showLog = false }) { Text(tr(lang, "完成", "Done")) } },
            shape = RoundedCornerShape(28.dp),
        )
    }
}

@Composable
private fun DevelopmentEnvironmentStatusCard(
    lang: String,
    bootstrapInstalled: Boolean,
    installedCount: Int,
    selectedCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .3f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(52.dp), shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Code, null, Modifier.size(25.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (bootstrapInstalled) tr(lang, "Termux 基础环境已就绪", "Termux base environment ready")
                        else tr(lang, "等待安装基础环境", "Base environment not installed"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        tr(lang, "已识别 $installedCount 项 · 已选择 $selectedCount 项", "$installedCount detected · $selectedCount selected"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f),
                    )
                }
            }
            Text(
                if (bootstrapInstalled) tr(lang, "已安装状态通过真实命令检测，不依赖旧环境标记。", "Installed state is detected from real commands rather than the legacy marker.")
                else tr(lang, "开始安装时会先解压应用内置的 Termux bootstrap。", "The bundled Termux bootstrap will be extracted before installation starts."),
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f),
            )
        }
    }
}

@Composable
private fun DevelopmentToolRow(
    lang: String,
    tool: DevelopmentTool,
    selected: Boolean,
    installed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .48f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f),
        ),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(developmentToolTitle(lang, tool), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (installed) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(tr(lang, "已安装", "Installed"), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(
                    developmentToolDescription(lang, tool),
                    Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    developmentToolSizeHint(lang, tool),
                    Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun developmentToolTitle(lang: String, tool: DevelopmentTool): String = if (lang == "zh") tool.titleZh else tool.titleEn
private fun developmentToolDescription(lang: String, tool: DevelopmentTool): String = if (lang == "zh") tool.descriptionZh else tool.descriptionEn
private fun developmentToolSizeHint(lang: String, tool: DevelopmentTool): String = if (lang == "zh") tool.sizeHintZh else tool.sizeHintEn
private fun developmentToolCategoryLabel(lang: String, category: DevelopmentToolCategory): String = when (category) {
    DevelopmentToolCategory.RUNTIME -> tr(lang, "基础与运行组件", "Runtime essentials")
    DevelopmentToolCategory.LANGUAGE -> tr(lang, "编程语言", "Programming languages")
    DevelopmentToolCategory.BUILD -> tr(lang, "编译与构建", "Build toolchains")
    DevelopmentToolCategory.TERMINAL -> tr(lang, "终端与远程", "Terminal & remote access")
    DevelopmentToolCategory.DATA -> tr(lang, "数据工具", "Data tools")
    DevelopmentToolCategory.MEDIA -> tr(lang, "媒体工具", "Media tools")
}


