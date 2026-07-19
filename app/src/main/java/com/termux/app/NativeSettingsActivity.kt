package com.termux.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.WebView
import android.widget.Toast
import com.termux.BuildConfig
import com.termux.shared.termux.TermuxConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Text
import me.rerere.hugeicons.stroke.Tick02

private enum class SettingsPage { ROOT, THEME, CHAT_BACKGROUND, OVERLAY, DEVELOPMENT_TOOLS, MODEL_CONFIGS, MODEL_EDITOR, WEB_UI, PROXY }

private enum class CodexDependentFeature {
    WEB_UI,
    TERMUX,
    SETUP,
}

class NativeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val providerStore = CodexProviderStore(prefs)
        setContent {
            var page by remember { mutableStateOf(SettingsPage.ROOT) }
            var editingProfileId by remember { mutableStateOf<String?>(null) }
            var providerRevision by remember { mutableIntStateOf(0) }
            var theme by remember { mutableStateOf(FcodeAppearancePreferences.normalizeColorMode(prefs.getString(KEY_THEME, "system"))) }
            var colorPalette by remember { mutableStateOf(FcodeColorPalette.from(prefs.getString(FcodeAppearancePreferences.COLOR_PALETTE, FcodeColorPalette.ROSE.value)).value) }
            var chatBackground by remember { mutableStateOf(FcodeChatBackgroundStyle.from(prefs.getString(FcodeAppearancePreferences.CHAT_BACKGROUND, FcodeChatBackgroundStyle.THEME.value)).value) }
            var chatBackgroundImage by remember { mutableStateOf(prefs.getString(FcodeAppearancePreferences.CHAT_BACKGROUND_IMAGE, "").orEmpty()) }
            var chatBackgroundDim by remember { mutableFloatStateOf(prefs.getFloat(FcodeAppearancePreferences.CHAT_BACKGROUND_DIM, 0.32f).coerceIn(0f, 0.72f)) }
            var language by remember { mutableStateOf(prefs.getString(KEY_LANGUAGE, "system").orEmpty()) }
            var animations by remember { mutableStateOf(prefs.getBoolean(KEY_STREAM_ANIMATIONS, true)) }
            var reasoning by remember { mutableStateOf(prefs.getBoolean(KEY_SHOW_REASONING, true)) }
            var follow by remember { mutableStateOf(prefs.getBoolean(KEY_AUTO_FOLLOW, true)) }
            var dialog by remember { mutableStateOf<String?>(null) }
            var missingCliFeature by remember { mutableStateOf<CodexDependentFeature?>(null) }
            var codexCliInstalled by remember { mutableStateOf(isCodexCliInstalled()) }
            val lang = resolveLanguage(language)
            val requestCodexFeature: (CodexDependentFeature) -> Unit = { feature ->
                if (codexCliInstalled || isCodexCliInstalled()) {
                    codexCliInstalled = true
                    when (feature) {
                        CodexDependentFeature.WEB_UI -> openWebUi()
                        CodexDependentFeature.TERMUX -> openTermux()
                        CodexDependentFeature.SETUP -> Unit
                    }
                } else {
                    missingCliFeature = feature
                }
            }
            val navigateBack = {
                page = when (page) {
                    SettingsPage.MODEL_EDITOR -> SettingsPage.MODEL_CONFIGS
                    SettingsPage.CHAT_BACKGROUND -> SettingsPage.THEME
                    SettingsPage.THEME, SettingsPage.OVERLAY, SettingsPage.DEVELOPMENT_TOOLS, SettingsPage.MODEL_CONFIGS, SettingsPage.WEB_UI, SettingsPage.PROXY -> SettingsPage.ROOT
                    SettingsPage.ROOT -> { finish(); SettingsPage.ROOT }
                }
            }
            FcodeChatTheme(
                theme, lang, animations, reasoning, follow,
                colorPalette = colorPalette,
                chatBackground = chatBackground,
                chatBackgroundImage = chatBackgroundImage,
                chatBackgroundDim = chatBackgroundDim,
            ) {
                BackHandler(onBack = navigateBack)
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "settingsPage",
                ) { target ->
                    when (target) {
                        SettingsPage.ROOT -> SettingsRootPage(
                            lang, providerStore.active(), theme, colorPalette, animations, reasoning, follow,
                            onBack = { finish() },
                            onModels = { page = SettingsPage.MODEL_CONFIGS },
                            onWebUi = { page = SettingsPage.WEB_UI },
                            onTermux = { requestCodexFeature(CodexDependentFeature.TERMUX) },
                            onInstallCodexCli = { requestCodexFeature(CodexDependentFeature.SETUP) },
                            codexCliInstalled = codexCliInstalled,
                            onProxy = { page = SettingsPage.PROXY },
                            onOverlay = { page = SettingsPage.OVERLAY },
                            onDevelopmentTools = { page = SettingsPage.DEVELOPMENT_TOOLS },
                            onTheme = { page = SettingsPage.THEME },
                            onLanguage = { dialog = "language" },
                            onTypography = { dialog = "typography" },
                            onAnimations = { animations = it; prefs.edit().putBoolean(KEY_STREAM_ANIMATIONS, it).apply() },
                            onReasoning = { reasoning = it; prefs.edit().putBoolean(KEY_SHOW_REASONING, it).apply() },
                            onFollow = { follow = it; prefs.edit().putBoolean(KEY_AUTO_FOLLOW, it).apply() },
                            prefs = prefs,
                            onAbout = { dialog = "about" },
                        )
                        SettingsPage.THEME -> ThemeSettingsPage(
                            lang = lang,
                            colorMode = theme,
                            paletteValue = colorPalette,
                            backgroundValue = chatBackground,
                            onBack = navigateBack,
                            onColorModeChange = { value ->
                                theme = FcodeAppearancePreferences.normalizeColorMode(value)
                                prefs.edit().putString(KEY_THEME, theme).apply()
                            },
                            onPaletteChange = { value ->
                                colorPalette = FcodeColorPalette.from(value).value
                                prefs.edit().putString(FcodeAppearancePreferences.COLOR_PALETTE, colorPalette).apply()
                            },
                            onOpenChatBackground = { page = SettingsPage.CHAT_BACKGROUND },
                        )
                        SettingsPage.CHAT_BACKGROUND -> ChatBackgroundSettingsPage(
                            lang = lang,
                            selectedValue = chatBackground,
                            imagePath = chatBackgroundImage,
                            imageDim = chatBackgroundDim,
                            onBack = navigateBack,
                            onSelected = { value ->
                                chatBackground = FcodeChatBackgroundStyle.from(value).value
                                prefs.edit().putString(FcodeAppearancePreferences.CHAT_BACKGROUND, chatBackground).apply()
                            },
                            onImageChanged = { path ->
                                chatBackgroundImage = path
                                prefs.edit().putString(FcodeAppearancePreferences.CHAT_BACKGROUND_IMAGE, path).apply()
                            },
                            onDimChanged = { value ->
                                chatBackgroundDim = value.coerceIn(0f, 0.72f)
                                prefs.edit().putFloat(FcodeAppearancePreferences.CHAT_BACKGROUND_DIM, chatBackgroundDim).apply()
                            },
                        )
                        SettingsPage.DEVELOPMENT_TOOLS -> DevelopmentToolsSettingsPage(
                            lang = lang,
                            prefs = prefs,
                            onBack = navigateBack,
                        )
                        SettingsPage.OVERLAY -> OverlaySettingsPage(
                            lang = lang,
                            prefs = prefs,
                            onBack = navigateBack,
                        )
                        SettingsPage.MODEL_CONFIGS -> ModelConfigurationsPage(
                            lang, providerStore.all(), providerStore.active()?.id, providerRevision,
                            onBack = navigateBack,
                            onAdd = { editingProfileId = null; page = SettingsPage.MODEL_EDITOR },
                            onEdit = { editingProfileId = it; page = SettingsPage.MODEL_EDITOR },
                            onActivate = { providerStore.activate(it); providerRevision++ },
                        )
                        SettingsPage.MODEL_EDITOR -> key(editingProfileId, providerRevision) {
                            ModelConfigurationEditor(
                                lang, editingProfileId?.let(providerStore::find), providerStore,
                                onBack = navigateBack,
                                onSaved = { providerRevision++; page = SettingsPage.MODEL_CONFIGS },
                                onDeleted = { providerRevision++; page = SettingsPage.MODEL_CONFIGS },
                            )
                        }
                        SettingsPage.PROXY -> ProxySettingsPage(
                            lang = lang,
                            prefs = prefs,
                            onBack = navigateBack,
                            onOpenDashboard = { startActivity(Intent(this@NativeSettingsActivity, MihomoDashboardActivity::class.java)) },
                        )
                        SettingsPage.WEB_UI -> WebUiSettingsPage(
                            lang, prefs, providerStore.active(), navigateBack,
                            onOpenWebUi = { requestCodexFeature(CodexDependentFeature.WEB_UI) },
                            onOpenDashboard = { startActivity(Intent(this@NativeSettingsActivity, MihomoDashboardActivity::class.java)) },
                            onClearCache = { resetWebUiPreferences(lang) },
                        )
                    }
                }
                when (dialog) {
                    "language" -> ChoiceDialog(
                        tr(lang, "选择语言", "Choose language"),
                        listOf("system" to tr(lang, "跟随系统", "System"), "zh" to "简体中文", "en" to "English"),
                        language, { dialog = null },
                    ) { language = it; prefs.edit().putString(KEY_LANGUAGE, it).apply(); dialog = null }
                    "typography" -> InfoDialog(
                        tr(lang, "文字与 Markdown", "Typography & Markdown"),
                        tr(lang, "已支持 Markdown、代码块、表格、列表和公式渲染。字号与行距沿用系统无障碍显示设置。", "Markdown, code blocks, tables, lists and math are supported. Font scale follows system accessibility settings."),
                        lang, { dialog = null },
                    )
                    "about" -> InfoDialog(
                        "Fcode",
                        tr(lang, "版本 ${BuildConfig.VERSION_NAME}\n原生 Compose 对话与设置\n独立 WebUI、Goal、Plan 与多任务支持", "Version ${BuildConfig.VERSION_NAME}\nNative Compose chat and settings\nIndependent WebUI, Goal, Plan and multitasking support"),
                        lang, { dialog = null },
                    )
                }
                missingCliFeature?.let { feature ->
                    MissingCodexCliDialog(
                        lang = lang,
                        feature = feature,
                        onDismiss = { missingCliFeature = null },
                        onInstall = {
                            missingCliFeature = null
                            CodexInstaller.setupBootstrapIfNeeded(this@NativeSettingsActivity) {
                                codexCliInstalled = isCodexCliInstalled()
                                when (feature) {
                                    CodexDependentFeature.WEB_UI -> openWebUi()
                                    CodexDependentFeature.TERMUX -> openTermux()
                                    CodexDependentFeature.SETUP -> Unit
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    private fun isCodexCliInstalled(): Boolean =
        File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex").canExecute()

    private fun openWebUi() {
        CodexNativeRuntime.shutdown()
        startActivity(Intent(this, CodexHomeActivity::class.java)
            .setAction(CodexHomeActivity.ACTION_OPEN_WEBUI)
            // Recreate the legacy host so its EditText-backed launch state is synchronized
            // from the profile that may just have been edited on this native screen.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun openTermux() {
        CodexNativeRuntime.shutdown()
        startActivity(Intent(this, CodexHomeActivity::class.java)
            .setAction(CodexHomeActivity.ACTION_OPEN_TERMUX)
            // Recreate the host so the terminal proxy uses the latest provider settings.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private fun resetWebUiPreferences(lang: String) {
        getSharedPreferences("codex_desktop_bridge", MODE_PRIVATE).edit().clear().apply()
        WebView(this).also { it.clearCache(true); it.destroy() }
        Toast.makeText(this, tr(lang, "WebUI 偏好和缓存已重置", "WebUI preferences and cache reset"), Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val PREFS_NAME = "codex_mobile"
        const val KEY_THEME = "native_theme_mode_v1"
        const val KEY_LANGUAGE = "native_language_v1"
        const val KEY_STREAM_ANIMATIONS = "native_stream_animations_v1"
        const val KEY_SHOW_REASONING = "native_show_reasoning_v1"
        const val KEY_AUTO_FOLLOW = "native_auto_follow_v1"
    }
}

@Composable
private fun DevelopmentToolsSettingsPage(
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
    var selectedIds by remember { mutableStateOf(initialSelection) }
    var installedIds by remember { mutableStateOf(DevelopmentToolInstaller.installedIds()) }
    var installing by remember { mutableStateOf(false) }
    var installStage by remember { mutableStateOf("") }
    var installDetail by remember { mutableStateOf("") }
    var installError by remember { mutableStateOf<String?>(null) }
    var confirmInstall by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    fun updateSelection(value: Set<String>) {
        selectedIds = value.intersect(DevelopmentToolCatalog.allIds)
        prefs.edit().putString(selectionKey, selectedIds.joinToString(",")).apply()
    }

    fun installCodexIfSelected(selectedTools: List<DevelopmentTool>) {
        val codex = selectedTools.firstOrNull { it.codexCli } ?: return
        if (DevelopmentToolInstaller.isInstalled(codex)) {
            installedIds = DevelopmentToolInstaller.installedIds()
            return
        }
        val host = activity ?: run {
            installError = tr(lang, "当前页面无法启动安装器", "The installer cannot be started from this context")
            return
        }
        CodexInstaller.setupBootstrapIfNeeded(host) {
            installedIds = DevelopmentToolInstaller.installedIds()
            Toast.makeText(context, tr(lang, "Codex CLI 已安装", "Codex CLI installed"), Toast.LENGTH_SHORT).show()
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
                    installedIds = DevelopmentToolInstaller.installedIds()
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
                    bootstrapInstalled = DevelopmentToolInstaller.isBootstrapInstalled(),
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
                    val log = DevelopmentToolInstaller.logFile()
                    if (log.exists()) {
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

    if (showLog) {
        val log = DevelopmentToolInstaller.logFile()
        val logText = remember(log.lastModified(), log.length()) {
            runCatching { log.readText(Charsets.UTF_8).takeLast(12_000) }
                .getOrElse { it.message.orEmpty() }
        }
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text(tr(lang, "安装日志", "Installation log")) },
            text = {
                Text(
                    logText.ifBlank { tr(lang, "暂无日志", "No log yet") },
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

private data class OverlayGestureSetting(
    val title: String,
    val key: String,
    val fallback: String,
)

@Composable
private fun OverlaySettingsPage(
    lang: String,
    prefs: SharedPreferences,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionGranted by remember { mutableStateOf(canDrawOverlays(context)) }
    var overlayEnabled by remember {
        mutableStateOf(prefs.getBoolean("overlay_enabled", false) && permissionGranted)
    }
    var edgeSnap by remember { mutableStateOf(prefs.getBoolean("overlay_edge_snap", true)) }
    var completionBubble by remember { mutableStateOf(prefs.getBoolean("completion_bubble", true)) }
    var completionNotification by remember { mutableStateOf(prefs.getBoolean("completion_notification", false)) }
    var keepAlive by remember {
        mutableStateOf(prefs.getBoolean(CodexOverlayService.PREF_NATIVE_TASK_KEEP_ALIVE, true))
    }
    var batteryUnrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }
    var selectedGesture by remember { mutableStateOf<OverlayGestureSetting?>(null) }
    val gestureValues = remember {
        mutableStateMapOf(
            "overlay_ball_tap_action" to prefs.getString("overlay_ball_tap_action", "tasks").orEmpty(),
            "overlay_ball_double_action" to prefs.getString("overlay_ball_double_action", "open").orEmpty(),
            "overlay_ball_long_action" to prefs.getString("overlay_ball_long_action", "snap").orEmpty(),
            "overlay_bubble_tap_action" to prefs.getString("overlay_bubble_tap_action", "open").orEmpty(),
            "overlay_bubble_double_action" to prefs.getString("overlay_bubble_double_action", "toggle_bubble").orEmpty(),
            "overlay_bubble_long_action" to prefs.getString("overlay_bubble_long_action", "open").orEmpty(),
        )
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionGranted = canDrawOverlays(context)
        if (permissionGranted) {
            prefs.edit().putBoolean("overlay_enabled", true).putBoolean("overlay_enable_pending", false).apply()
            overlayEnabled = true
            CodexOverlayService.start(context)
            Toast.makeText(context, tr(lang, "Codex 悬浮窗已开启", "Codex floating window enabled"), Toast.LENGTH_SHORT).show()
        } else {
            prefs.edit().putBoolean("overlay_enabled", false).putBoolean("overlay_enable_pending", false).apply()
            overlayEnabled = false
            Toast.makeText(context, tr(lang, "尚未获得悬浮窗权限", "Floating window permission was not granted"), Toast.LENGTH_SHORT).show()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        completionNotification = granted
        prefs.edit().putBoolean("completion_notification", granted).apply()
        if (!granted) Toast.makeText(context, tr(lang, "需要通知权限才能发送完成提醒", "Notification permission is required for completion alerts"), Toast.LENGTH_SHORT).show()
    }
    val batterySettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryUnrestricted = isBatteryUnrestricted(context)
    }

    fun enableOverlay() {
        permissionGranted = canDrawOverlays(context)
        if (permissionGranted) {
            prefs.edit().putBoolean("overlay_enabled", true).putBoolean("overlay_enable_pending", false).apply()
            overlayEnabled = true
            CodexOverlayService.start(context)
            Toast.makeText(context, tr(lang, "Codex 悬浮窗已开启", "Codex floating window enabled"), Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putBoolean("overlay_enable_pending", true).apply()
        runCatching {
            overlayPermissionLauncher.launch(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }.onFailure {
            prefs.edit().putBoolean("overlay_enable_pending", false).apply()
            Toast.makeText(context, tr(lang, "无法打开悬浮窗权限设置", "Unable to open floating window permission settings"), Toast.LENGTH_SHORT).show()
        }
    }

    fun disableOverlay() {
        prefs.edit().putBoolean("overlay_enabled", false).putBoolean("overlay_enable_pending", false).apply()
        overlayEnabled = false
        CodexOverlayService.stop(context)
        Toast.makeText(context, tr(lang, "悬浮窗已关闭", "Floating window disabled"), Toast.LENGTH_SHORT).show()
    }

    SettingsScaffold(
        tr(lang, "悬浮窗与后台", "Floating window & background"),
        tr(lang, "悬浮球、任务提醒、手势与后台连接", "Bubble, task alerts, gestures and background connection"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                OverlayStatusCard(
                    lang = lang,
                    enabled = overlayEnabled,
                    permissionGranted = permissionGranted,
                    onAction = { if (overlayEnabled) disableOverlay() else enableOverlay() },
                )
            }
            item { SettingsSection(tr(lang, "悬浮球", "Floating bubble")) }
            item {
                ToggleSettingsRow(
                    HugeIcons.Sparkles,
                    tr(lang, "启用悬浮图标", "Enable floating icon"),
                    if (permissionGranted) tr(lang, "在其他应用上层显示 Codex 悬浮球", "Show the Codex bubble above other apps")
                    else tr(lang, "开启时将跳转系统页面申请显示权限", "Enabling opens the system overlay permission page"),
                    overlayEnabled,
                ) { enabled -> if (enabled) enableOverlay() else disableOverlay() }
            }
            item {
                ToggleSettingsRow(
                    HugeIcons.ArrowRight01,
                    tr(lang, "拖动后自动贴边", "Snap to edge after dragging"),
                    tr(lang, "松手后吸附到最近边缘，并按屏幕高度比例保存位置", "Attach to the nearest edge and preserve the vertical position"),
                    edgeSnap,
                ) {
                    edgeSnap = it
                    prefs.edit().putBoolean("overlay_edge_snap", it).apply()
                }
            }
            item { SettingsSection(tr(lang, "悬浮球手势", "Bubble gestures")) }
            items(overlayBallGestureSettings(lang), key = { it.key }) { setting ->
                NavigationSettingsRow(
                    HugeIcons.Sparkles,
                    setting.title,
                    overlayActionLabel(lang, gestureValues[setting.key] ?: setting.fallback),
                ) { selectedGesture = setting }
            }
            item { SettingsSection(tr(lang, "任务气泡手势", "Task bubble gestures")) }
            items(overlayTaskBubbleGestureSettings(lang), key = { it.key }) { setting ->
                NavigationSettingsRow(
                    HugeIcons.Code,
                    setting.title,
                    overlayActionLabel(lang, gestureValues[setting.key] ?: setting.fallback),
                ) { selectedGesture = setting }
            }
            item { SettingsSection(tr(lang, "任务完成提醒", "Task completion alerts")) }
            item {
                ToggleSettingsRow(
                    HugeIcons.Sparkles,
                    tr(lang, "悬浮任务气泡", "Floating task bubble"),
                    tr(lang, "任务完成时在悬浮图标旁显示完成气泡", "Show a completion bubble beside the floating icon"),
                    completionBubble,
                ) {
                    completionBubble = it
                    prefs.edit().putBoolean("completion_bubble", it).apply()
                }
            }
            item {
                ToggleSettingsRow(
                    HugeIcons.Refresh03,
                    tr(lang, "系统完成通知", "System completion notification"),
                    tr(lang, "任务完成时发送可点击的系统通知", "Send a tappable system notification when a task completes"),
                    completionNotification,
                ) { enabled ->
                    if (enabled && android.os.Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        completionNotification = enabled
                        prefs.edit().putBoolean("completion_notification", enabled).apply()
                    }
                }
            }
            item { SettingsSection(tr(lang, "后台连接", "Background connection")) }
            item {
                ToggleSettingsRow(
                    HugeIcons.Refresh03,
                    tr(lang, "任务后台保持", "Background task keep-alive"),
                    tr(lang, "任务运行时保持 CPU 与网络连接；悬浮窗开启时服务会常驻", "Keep CPU and network active while tasks run; the service stays active with the overlay"),
                    keepAlive,
                ) {
                    keepAlive = it
                    prefs.edit().putBoolean(CodexOverlayService.PREF_NATIVE_TASK_KEEP_ALIVE, it).apply()
                    CodexOverlayService.syncKeepAlive(context)
                }
            }
            item {
                NavigationSettingsRow(
                    HugeIcons.Settings03,
                    tr(lang, "电池优化", "Battery optimization"),
                    if (batteryUnrestricted) tr(lang, "已允许后台不受限", "Background use is unrestricted")
                    else tr(lang, "受系统限制 · 建议允许后台运行", "System restricted · allowing background use is recommended"),
                ) {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || batteryUnrestricted) {
                        Toast.makeText(context, tr(lang, "Codex 已允许后台不受限", "Codex is already unrestricted in the background"), Toast.LENGTH_SHORT).show()
                    } else {
                        val request = Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        runCatching { batterySettingsLauncher.launch(request) }
                            .onFailure {
                                batterySettingsLauncher.launch(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
                ) {
                    Text(
                        tr(lang, "悬浮窗开启后会显示低优先级常驻通知，并使用 CPU 与 Wi‑Fi 保持来降低切到后台后断开连接的概率。", "When enabled, the floating window uses a low-priority foreground notification plus CPU and Wi-Fi locks to reduce background disconnects."),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    selectedGesture?.let { setting ->
        ChoiceDialog(
            title = setting.title,
            options = overlayActionOptions(lang),
            selected = gestureValues[setting.key] ?: setting.fallback,
            onDismiss = { selectedGesture = null },
        ) { value ->
            gestureValues[setting.key] = value
            prefs.edit().putString(setting.key, value).apply()
            selectedGesture = null
        }
    }
}

@Composable
private fun OverlayStatusCard(
    lang: String,
    enabled: Boolean,
    permissionGranted: Boolean,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = .36f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            HugeIcons.Sparkles,
                            null,
                            Modifier.size(27.dp),
                            tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            enabled -> tr(lang, "悬浮窗正在运行", "Floating window is running")
                            permissionGranted -> tr(lang, "悬浮窗已关闭", "Floating window is off")
                            else -> tr(lang, "需要显示权限", "Display permission required")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            enabled -> tr(lang, "可拖动悬浮球并查看任务状态", "Drag the bubble and inspect task status")
                            permissionGranted -> tr(lang, "权限已就绪，可以随时开启", "Permission is ready; enable it at any time")
                            else -> tr(lang, "系统需要允许 Fcode 显示在其他应用上层", "Android must allow Fcode to appear above other apps")
                        },
                        Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    when {
                        enabled -> tr(lang, "关闭悬浮窗", "Disable floating window")
                        permissionGranted -> tr(lang, "开启悬浮窗", "Enable floating window")
                        else -> tr(lang, "授权并开启", "Grant permission & enable")
                    },
                )
            }
        }
    }
}

private fun overlayBallGestureSettings(lang: String): List<OverlayGestureSetting> = listOf(
    OverlayGestureSetting(tr(lang, "单击悬浮球", "Tap floating bubble"), "overlay_ball_tap_action", "tasks"),
    OverlayGestureSetting(tr(lang, "双击悬浮球", "Double-tap floating bubble"), "overlay_ball_double_action", "open"),
    OverlayGestureSetting(tr(lang, "长按悬浮球", "Long-press floating bubble"), "overlay_ball_long_action", "snap"),
)

private fun overlayTaskBubbleGestureSettings(lang: String): List<OverlayGestureSetting> = listOf(
    OverlayGestureSetting(tr(lang, "单击任务气泡", "Tap task bubble"), "overlay_bubble_tap_action", "open"),
    OverlayGestureSetting(tr(lang, "双击任务气泡", "Double-tap task bubble"), "overlay_bubble_double_action", "toggle_bubble"),
    OverlayGestureSetting(tr(lang, "长按任务气泡", "Long-press task bubble"), "overlay_bubble_long_action", "open"),
)

private fun overlayActionOptions(lang: String): List<Pair<String, String>> = listOf(
    "tasks" to tr(lang, "显示当前任务列表", "Show current tasks"),
    "open" to tr(lang, "打开 Codex", "Open Codex"),
    "snap" to tr(lang, "贴到最近屏幕边缘", "Snap to nearest edge"),
    "toggle_bubble" to tr(lang, "显示或隐藏任务气泡", "Show or hide task bubble"),
    "hide_overlay" to tr(lang, "关闭悬浮窗", "Disable floating window"),
    "none" to tr(lang, "不执行操作", "Do nothing"),
)

private fun overlayActionLabel(lang: String, value: String): String =
    overlayActionOptions(lang).firstOrNull { it.first == value }?.second
        ?: tr(lang, "不执行操作", "Do nothing")

private fun canDrawOverlays(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(context)

private fun isBatteryUnrestricted(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return true
    val power = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
    return power?.isIgnoringBatteryOptimizations(context.packageName) == true
}

@Composable
private fun ThemeSettingsPage(
    lang: String,
    colorMode: String,
    paletteValue: String,
    backgroundValue: String,
    onBack: () -> Unit,
    onColorModeChange: (String) -> Unit,
    onPaletteChange: (String) -> Unit,
    onOpenChatBackground: () -> Unit,
) {
    val selectedPalette = FcodeColorPalette.from(paletteValue)
    val selectedBackground = FcodeChatBackgroundStyle.from(backgroundValue)
    val dark = currentFcodeDarkMode(colorMode)
    SettingsScaffold(
        tr(lang, "主题与外观", "Theme & appearance"),
        tr(lang, "配色、Markdown 与聊天背景", "Color, Markdown and chat background"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                ThemeOverviewCard(
                    lang = lang,
                    palette = selectedPalette,
                    colorMode = colorMode,
                    background = selectedBackground,
                )
            }
            item { SettingsSection(tr(lang, "界面风格", "Interface style")) }
            item { InterfaceStyleCard(lang) }
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
                        Surface(
                            onClick = { onColorModeChange(value) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
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
                        Text("${paletteLabel(lang, palette)} · ${themeLabel(lang, colorMode)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
private fun InterfaceStyleCard(lang: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(HugeIcons.Sparkles)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Material Expressive", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(tr(lang, "当前设计风格", "Current design style"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(tr(lang, "使用中", "Active"), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Sparkles, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(lang, "液态玻璃", "Liquid Glass"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tr(lang, "主题接口已预留，接入后无需重做页面", "Theme hooks are ready; pages will not need rebuilding"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(tr(lang, "待接入", "Planned"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val scheme = fcodeColorScheme(palette, dark)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .48f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(paletteLabel(lang, palette), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (selected) Icon(HugeIcons.Tick02, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
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

@Composable
private fun ChatBackgroundSettingsPage(
    lang: String,
    selectedValue: String,
    imagePath: String,
    imageDim: Float,
    onBack: () -> Unit,
    onSelected: (String) -> Unit,
    onImageChanged: (String) -> Unit,
    onDimChanged: (Float) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = FcodeChatBackgroundStyle.from(selectedValue)
    val imageInfo = remember(imagePath) { ChatBackgroundImageStore.readInfo(imagePath) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
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
            item { ChatBackgroundPreview(lang, selected) }
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
            item { FutureMotionPreviewCard(lang) }
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
private fun ChatBackgroundPreview(lang: String, style: FcodeChatBackgroundStyle) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
    ) {
        Box(Modifier.fillMaxWidth().height(270.dp)) {
            FcodeChatBackdrop(Modifier.fillMaxSize(), style, customImageMaxDimension = 1280)
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
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .65f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
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
                if (selected) Icon(HugeIcons.Tick02, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun FutureMotionPreviewCard(lang: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Sparkles, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(lang, "动态预览区域已预留", "Motion preview area is ready"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(tr(lang, "未来的粒子、渐变与液态动效会直接在上方聊天预览中播放。", "Future particles, gradients and liquid motion will play directly in the chat preview above."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(tr(lang, "未来", "Future"), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun paletteLabel(lang: String, palette: FcodeColorPalette): String = when (palette) {
    FcodeColorPalette.ROSE -> tr(lang, "绯樱", "Rose")
    FcodeColorPalette.OCEAN -> tr(lang, "海盐", "Ocean")
    FcodeColorPalette.FOREST -> tr(lang, "森屿", "Forest")
    FcodeColorPalette.GRAPHITE -> tr(lang, "石墨", "Graphite")
}

private fun backgroundLabel(lang: String, style: FcodeChatBackgroundStyle): String = when (style) {
    FcodeChatBackgroundStyle.THEME -> tr(lang, "主题柔光", "Theme glow")
    FcodeChatBackgroundStyle.AURORA -> tr(lang, "极光", "Aurora")
    FcodeChatBackgroundStyle.MIST -> tr(lang, "薄雾", "Mist")
    FcodeChatBackgroundStyle.GRID -> tr(lang, "坐标网格", "Grid")
    FcodeChatBackgroundStyle.CUSTOM -> tr(lang, "自定义图片", "Custom image")
}

private fun appearanceLabel(lang: String, paletteValue: String, colorMode: String): String =
    "${paletteLabel(lang, FcodeColorPalette.from(paletteValue))} · ${themeLabel(lang, colorMode)}"

@Composable
private fun MissingCodexCliDialog(
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

@Composable
private fun SettingsRootPage(
    lang: String,
    provider: CodexProviderStore.Profile?,
    theme: String,
    colorPalette: String,
    animations: Boolean,
    reasoning: Boolean,
    follow: Boolean,
    onBack: () -> Unit,
    onModels: () -> Unit,
    onWebUi: () -> Unit,
    onTermux: () -> Unit,
    onInstallCodexCli: () -> Unit,
    codexCliInstalled: Boolean,
    onProxy: () -> Unit,
    onOverlay: () -> Unit,
    onDevelopmentTools: () -> Unit,
    onTheme: () -> Unit,
    onLanguage: () -> Unit,
    onTypography: () -> Unit,
    onAnimations: (Boolean) -> Unit,
    onReasoning: (Boolean) -> Unit,
    onFollow: (Boolean) -> Unit,
    prefs: SharedPreferences,
    onAbout: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
            item {
                val manager = remember { MihomoManager.get(context) }
                val proxyStatus = when {
                    !manager.isSupported -> tr(lang, "当前设备不支持内置内核", "Built-in core is unsupported on this device")
                    !manager.isInstalled -> tr(lang, "未安装 · 可离线安装", "Not installed · offline install available")
                    manager.isRunning -> tr(lang, "运行中 · 127.0.0.1:${manager.mixedPort()}", "Running · 127.0.0.1:${manager.mixedPort()}")
                    else -> tr(lang, "已安装 · 当前已停止", "Installed · currently stopped")
                }
                NavigationSettingsRow(HugeIcons.Code, tr(lang, "网络与代理", "Network & proxy"), proxyStatus, onProxy)
            }
            item { SettingsSection(tr(lang, "外观", "Appearance")) }
            item { NavigationSettingsRow(HugeIcons.Moon02, tr(lang, "主题与外观", "Theme & appearance"), appearanceLabel(lang, colorPalette, theme), onTheme) }
            item { NavigationSettingsRow(HugeIcons.LanguageCircle, tr(lang, "语言", "Language"), tr(lang, "简体中文 / 跟随系统", "English / System"), onLanguage) }
            item { NavigationSettingsRow(HugeIcons.Text, tr(lang, "文字与 Markdown", "Typography & Markdown"), tr(lang, "代码、表格、列表与公式", "Code, tables, lists and math"), onTypography) }
            item { SettingsSection(tr(lang, "对话", "Conversation")) }
            item { ToggleSettingsRow(HugeIcons.Sparkles, tr(lang, "流式动画", "Streaming animation"), tr(lang, "让新生成的内容平滑出现", "Animate newly generated content"), animations, onAnimations) }
            item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "思考过程", "Reasoning"), tr(lang, "在回答中显示模型的推理摘要", "Show model reasoning summaries"), reasoning, onReasoning) }
            item { ToggleSettingsRow(HugeIcons.ArrowRight01, tr(lang, "自动跟随回答", "Auto-follow output"), tr(lang, "生成时保持滚动到最新内容", "Keep the latest output visible"), follow, onFollow) }
            item { SettingsSection(tr(lang, "系统", "System")) }
            item {
                val installedTools = DevelopmentToolInstaller.installedIds().size
                NavigationSettingsRow(
                    HugeIcons.Code,
                    tr(lang, "开发工具与环境", "Development tools & environment"),
                    if (DevelopmentToolInstaller.isBootstrapInstalled())
                        tr(lang, "已安装 $installedTools 项 · 可选择语言、构建与终端工具", "$installedTools installed · choose languages, build and terminal tools")
                    else tr(lang, "基础环境未安装 · 可按需或一键安装完整环境", "Base environment missing · install selected tools or the full environment"),
                    onDevelopmentTools,
                )
            }
            item {
                val overlayGranted = canDrawOverlays(context)
                val overlayEnabled = prefs.getBoolean("overlay_enabled", false) && overlayGranted
                val overlaySummary = when {
                    overlayEnabled -> tr(lang, "悬浮球已开启 · 手势、提醒与后台保持", "Bubble enabled · gestures, reminders and keep-alive")
                    !overlayGranted -> tr(lang, "需要悬浮窗权限 · 配置手势与完成提醒", "Overlay permission required · configure gestures and completion alerts")
                    else -> tr(lang, "已关闭 · 配置手势、提醒与后台保持", "Off · configure gestures, reminders and keep-alive")
                }
                NavigationSettingsRow(HugeIcons.Sparkles, tr(lang, "悬浮窗与后台", "Floating window & background"), overlaySummary, onOverlay)
            }
            item { NavigationSettingsRow(HugeIcons.Settings03, tr(lang, "关于 Fcode", "About Fcode"), "${tr(lang, "版本", "Version")} ${BuildConfig.VERSION_NAME}", onAbout) }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun ActiveProviderCard(lang: String, provider: CodexProviderStore.Profile?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(HugeIcons.Sparkles, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(lang, "当前模型配置", "Active model configuration"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f))
                    Text(provider?.name ?: tr(lang, "尚未配置", "Not configured"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Icon(HugeIcons.ArrowRight01, null, Modifier.size(20.dp))
            }
            Text(
                if (provider == null) tr(lang, "添加 API 地址、密钥和模型后即可开始对话。", "Add an API endpoint, key and model to start chatting.")
                else listOf(provider.model.ifBlank { tr(lang, "默认模型", "Default model") }, endpointLabel(provider.baseUrl)).filter { it.isNotBlank() }.joinToString(" · "),
                Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .82f),
            )
        }
    }
}

@Composable
private fun ModelConfigurationsPage(
    lang: String,
    profiles: List<CodexProviderStore.Profile>,
    activeProfileId: String?,
    revision: Int,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onActivate: (CodexProviderStore.Profile) -> Unit,
) {
    SettingsScaffold(tr(lang, "模型与 API", "Models & API"), tr(lang, "管理服务商、密钥和默认模型", "Manage providers, keys and default models"), onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            if (profiles.isEmpty()) {
                item { EmptySettingsState(HugeIcons.Sparkles, tr(lang, "还没有模型配置", "No model configurations"), tr(lang, "新建配置后，原生对话、WebUI 和终端会共用它。", "Native chat, WebUI and the terminal share the same configuration.")) }
            } else {
                item { SettingsSection(tr(lang, "配置", "Configurations")) }
                items(profiles, key = { "${it.id}-$revision" }) { profile ->
                    ProviderCard(lang, profile, profile.id == activeProfileId, { onEdit(profile.id) }, { onActivate(profile) })
                }
            }
            item {
                Button(onAdd, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(HugeIcons.Add01, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text(tr(lang, "新建配置", "New configuration"))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ProviderCard(lang: String, profile: CodexProviderStore.Profile, active: Boolean, onEdit: () -> Unit, onActivate: () -> Unit) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (active) Surface(Modifier.padding(start = 8.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
                            Text(tr(lang, "使用中", "Active"), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(profile.model.ifBlank { tr(lang, "未设置默认模型", "No default model") }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(endpointLabel(profile.baseUrl), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(HugeIcons.ArrowRight01, null, Modifier.size(20.dp))
            }
            if (!active) TextButton(onActivate, Modifier.align(Alignment.End)) {
                Icon(HugeIcons.Tick02, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(tr(lang, "设为当前配置", "Set active"))
            }
        }
    }
}

@Composable
private fun ModelConfigurationEditor(
    lang: String,
    existing: CodexProviderStore.Profile?,
    store: CodexProviderStore,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("codex_mobile", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var providerId by remember { mutableStateOf(existing?.id ?: store.newId()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
    var apiFormat by remember { mutableStateOf(existing?.apiFormat?.ifBlank { "auto" } ?: "auto") }
    val models = remember(existing?.id) {
        mutableStateListOf<CodexProviderStore.ModelConfig>().apply {
            addAll(existing?.models?.map { it.copy() }.orEmpty())
            if (isEmpty() && !existing?.model.isNullOrBlank()) add(CodexProviderStore.ModelConfig(existing.model, existing.model, 0L))
        }
    }
    var defaultModelId by remember { mutableStateOf(existing?.model.orEmpty()) }
    var forwardReasoning by remember { mutableStateOf(existing?.forwardReasoningContext ?: false) }
    var ultraSubagentLimit by remember { mutableStateOf((existing?.ultraSubagentLimit ?: CodexProviderStore.Profile.DEFAULT_ULTRA_SUBAGENT_LIMIT).toString()) }
    var normalSubagentLimit by remember { mutableStateOf((existing?.normalSubagentLimit ?: CodexProviderStore.Profile.DEFAULT_NORMAL_SUBAGENT_LIMIT).toString()) }
    var customSubagentStability by remember { mutableStateOf(existing?.customSubagentStability ?: true) }
    var proxyEnabled by remember { mutableStateOf(existing?.proxyEnabled ?: false) }
    var proxyWebUi by remember { mutableStateOf(existing?.proxyWebUi ?: false) }
    var proxyTermux by remember { mutableStateOf(existing?.proxyTermux ?: false) }
    var showKey by remember { mutableStateOf(false) }
    var formatMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyAction by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<CodexProviderStore.ModelConfig?>(null) }
    var modelSeed by remember { mutableStateOf<CodexProviderStore.ModelConfig?>(null) }
    var showModelEditor by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<CodexProviderStore.ModelConfig>?>(null) }
    var pendingDeleteModel by remember { mutableStateOf<CodexProviderStore.ModelConfig?>(null) }

    fun openModelEditor(editing: CodexProviderStore.ModelConfig?, seed: CodexProviderStore.ModelConfig?) {
        editingModel = editing
        modelSeed = seed
        showModelEditor = true
    }
    fun validateProfile(): Triple<Int, Int, String>? {
        val cleanName = name.trim()
        val cleanId = providerId.trim()
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val cleanKey = apiKey.trim()
        val ultra = ultraSubagentLimit.toIntOrNull()
        val normal = normalSubagentLimit.toIntOrNull()
        error = when {
            cleanName.isBlank() -> tr(lang, "请输入配置名称", "Enter a configuration name")
            cleanId.isBlank() -> tr(lang, "请输入配置 ID", "Enter a configuration ID")
            !isValidHttpUrl(cleanUrl) -> tr(lang, "请输入有效的 HTTP(S) API 地址", "Enter a valid HTTP(S) API URL")
            cleanKey.isBlank() -> tr(lang, "请输入 API Key", "Enter an API key")
            ultra == null || ultra !in 1..CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT -> tr(lang, "Ultra/V2 子代理数应为 1–${CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT}", "Ultra/V2 subagent limit must be 1–${CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT}")
            normal == null || normal !in 1..CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT -> tr(lang, "普通/V1 子代理数应为 1–${CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT}", "Normal/V1 subagent limit must be 1–${CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT}")
            else -> null
        }
        if (error != null) return null
        val selected = defaultModelId.takeIf { id -> models.any { it.id == id } } ?: models.firstOrNull()?.id.orEmpty()
        return Triple(ultra!!, normal!!, selected)
    }
    fun pendingProfile(): CodexProviderStore.Profile? {
        val (ultra, normal, selected) = validateProfile() ?: return null
        return CodexProviderStore.Profile(
            providerId.trim(), name.trim(), note.trim(), baseUrl.trim().trimEnd('/'), apiKey.trim(), selected,
            apiFormat, models, proxyEnabled, proxyEnabled && proxyWebUi, proxyEnabled && proxyTermux,
            forwardReasoning, ultra, normal, customSubagentStability,
        )
    }
    fun save() {
        val profile = pendingProfile() ?: return
        val activeBefore = store.active()?.id
        store.save(profile)
        if (activeBefore == null || activeBefore == existing?.id) store.activate(profile)
        onSaved()
    }
    fun runTest() {
        val profile = pendingProfile() ?: return
        busyAction = "test"
        scope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) { testProviderConnection(context, prefs, profile.baseUrl, profile.apiKey) }
            busyAction = null
            Toast.makeText(context, result.message, if (result.ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
        }
    }
    fun detectFormat() {
        if (!isValidHttpUrl(baseUrl.trim()) || apiKey.isBlank()) {
            error = tr(lang, "请先填写 URL 和 API Key", "Enter the URL and API key first")
            return
        }
        busyAction = "detect"
        scope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) { detectApiFormat(context, prefs, baseUrl, apiKey) }
            busyAction = null
            if (result.value != null) apiFormat = result.value else error = result.error
        }
    }
    fun fetchCatalog() {
        if (!isValidHttpUrl(baseUrl.trim()) || apiKey.isBlank()) {
            error = tr(lang, "请先填写 URL 和 API Key", "Enter the URL and API key first")
            return
        }
        busyAction = "fetch"
        scope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) { fetchProviderModels(context, prefs, baseUrl, apiKey) }
            busyAction = null
            if (result.models != null) fetchedModels = result.models else error = result.error
        }
    }

    SettingsScaffold(
        if (existing == null) tr(lang, "新建模型配置", "New model configuration") else tr(lang, "编辑模型配置", "Edit model configuration"),
        tr(lang, "服务商、模型目录与 Codex 运行能力", "Provider, model catalog and Codex capabilities"), onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item { SettingsSection(tr(lang, "基本信息", "Details")) }
            item { SettingsTextField(name, { name = it; error = null }, tr(lang, "配置名称", "Configuration name"), tr(lang, "例如：工作 API", "e.g. Work API")) }
            item { SettingsTextField(providerId, { providerId = it; error = null }, "ID", "provider-id", enabled = existing == null) }
            item { SettingsTextField(note, { note = it }, tr(lang, "备注（可选）", "Note (optional)"), tr(lang, "团队、用途或额度说明", "Team, purpose or quota")) }
            item { SettingsSection(tr(lang, "连接", "Connection")) }
            item { SettingsTextField(baseUrl, { baseUrl = it; error = null }, "API Base URL", "https://api.example.com/v1", keyboardType = KeyboardType.Uri) }
            item { SettingsTextField(apiKey, { apiKey = it; error = null }, "API Key", "sk-…", visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(), trailing = { TextButton({ showKey = !showKey }) { Text(tr(lang, if (showKey) "隐藏" else "显示", if (showKey) "Hide" else "Show")) } }) }
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    OutlinedButton({ formatMenu = true }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(tr(lang, "API 格式：", "API format: ")); Text(apiFormatLabel(apiFormat), fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(formatMenu, { formatMenu = false }) {
                        listOf("auto", "openai_responses", "openai_chat").forEach { value -> DropdownMenuItem(
                            text = { Text(apiFormatLabel(value)) }, leadingIcon = if (value == apiFormat) ({ Icon(HugeIcons.Tick02, null, Modifier.size(18.dp)) }) else null,
                            onClick = { apiFormat = value; formatMenu = false },
                        ) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(::detectFormat, Modifier.weight(1f), enabled = busyAction == null) { Text(if (busyAction == "detect") tr(lang, "检测中…", "Detecting…") else tr(lang, "自动检测", "Auto-detect")) }
                    OutlinedButton(::runTest, Modifier.weight(1f), enabled = busyAction == null) { Text(if (busyAction == "test") tr(lang, "测试中…", "Testing…") else tr(lang, "测试连接", "Test connection")) }
                }
            }
            item { SettingsSection(tr(lang, "模型目录", "Model catalog")) }
            item {
                Text(
                    if (defaultModelId.isBlank()) tr(lang, "尚未设置默认模型", "No default model") else tr(lang, "默认模型：${modelDisplayName(models.firstOrNull { it.id == defaultModelId }) ?: defaultModelId}", "Default: ${modelDisplayName(models.firstOrNull { it.id == defaultModelId }) ?: defaultModelId}"),
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = if (defaultModelId.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton({ openModelEditor(null, null) }, Modifier.weight(1f)) { Icon(HugeIcons.Add01, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(tr(lang, "手动添加", "Add manually")) }
                    FilledTonalButton(::fetchCatalog, Modifier.weight(1f), enabled = busyAction == null) { Icon(HugeIcons.Refresh03, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(if (busyAction == "fetch") tr(lang, "获取中…", "Fetching…") else tr(lang, "获取模型", "Fetch models")) }
                }
            }
            if (models.isEmpty()) item { EmptyModelCatalog(lang) }
            items(models, key = { it.id }) { item ->
                ModelCatalogCard(
                    lang, item, item.id == defaultModelId,
                    onDefault = { defaultModelId = item.id },
                    onEdit = { openModelEditor(item, item) },
                    onDelete = { pendingDeleteModel = item },
                )
            }
            item { SettingsSection(tr(lang, "子代理并发", "Subagent concurrency")) }
            item { Text(tr(lang, "V2/Ultra 与普通/V1 分别使用独立上限，范围 1–${CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT}。包含 V2 模型时，Codex 不会同时写入 agents.max_threads。", "V2/Ultra and normal/V1 use separate limits from 1–${CodexProviderStore.Profile.MAX_SUBAGENT_LIMIT}. Codex omits agents.max_threads when V2 models are present."), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { SettingsTextField(ultraSubagentLimit, { ultraSubagentLimit = it; error = null }, tr(lang, "Ultra / V2 最大子代理数", "Ultra / V2 max subagents"), "3", keyboardType = KeyboardType.Number) }
            item { SettingsTextField(normalSubagentLimit, { normalSubagentLimit = it; error = null }, tr(lang, "普通 / V1 最大子代理数", "Normal / V1 max subagents"), "6", keyboardType = KeyboardType.Number) }
            item { ToggleSettingsRow(HugeIcons.Sparkles, tr(lang, "自定义模型子代理稳定模式", "Custom-model subagent stability"), tr(lang, "移除子代理递归 spawn_agent，并限制等待超时", "Prevent recursive child spawning and cap wait timeouts"), customSubagentStability) { customSubagentStability = it } }
            item { SettingsSection(tr(lang, "代理与上下文", "Proxy & context")) }
            item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "此配置使用内置代理", "Use built-in proxy"), tr(lang, "仅影响 Fcode 自己发出的 API 请求", "Only affects API requests made by Fcode"), proxyEnabled) { proxyEnabled = it; if (!it) { proxyWebUi = false; proxyTermux = false } } }
            if (proxyEnabled) {
                item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "WebUI 一键开启代理", "Auto-start proxy for WebUI"), tr(lang, "进入 WebUI 时自动启动 Mihomo", "Start Mihomo when opening WebUI"), proxyWebUi) { proxyWebUi = it } }
                item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "Termux 一键开启代理", "Auto-start proxy for Termux"), tr(lang, "打开内置终端时自动启动 Mihomo", "Start Mihomo when opening the terminal"), proxyTermux) { proxyTermux = it } }
            }
            item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "转发 reasoning.context", "Forward reasoning.context"), tr(lang, "仅控制 Responses reasoning.context；DeepSeek reasoning_content 会自动保留", "Controls Responses reasoning.context only; DeepSeek reasoning_content is preserved automatically"), forwardReasoning) { forwardReasoning = it } }
            if (error != null) item { Text(error.orEmpty(), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            item { Button(::save, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(HugeIcons.Tick02, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr(lang, "保存配置", "Save configuration")) } }
            if (existing != null) item { TextButton({ confirmDelete = true }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Icon(HugeIcons.Delete01, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr(lang, "删除此配置", "Delete configuration"), color = MaterialTheme.colorScheme.error) } }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    if (showModelEditor) ModelEditorDialog(
        lang = lang, editing = editingModel, seed = modelSeed,
        existingIds = models.filter { it !== editingModel }.map { it.id },
        onDismiss = { showModelEditor = false },
        onSave = { saved ->
            val oldId = editingModel?.id.orEmpty()
            val index = editingModel?.let(models::indexOf) ?: -1
            if (index >= 0) models[index] = saved else models.add(saved)
            if (defaultModelId.isBlank() || defaultModelId == oldId) defaultModelId = saved.id
            showModelEditor = false
        },
    )
    fetchedModels?.let { fetched -> FetchModelsDialog(
        lang, fetched, models.map { it.id },
        onDismiss = { fetchedModels = null },
        onChoose = { fetchedModel ->
            val current = models.firstOrNull { it.id.equals(fetchedModel.id, true) }
            fetchedModels = null
            openModelEditor(current, fetchedModel)
        },
    ) }
    pendingDeleteModel?.let { target -> AlertDialog(
        onDismissRequest = { pendingDeleteModel = null },
        title = { Text(tr(lang, "删除模型？", "Delete model?")) },
        text = { Text(tr(lang, "将从此配置中删除“${modelDisplayName(target) ?: target.id}”。", "Remove “${modelDisplayName(target) ?: target.id}” from this configuration.")) },
        dismissButton = { TextButton({ pendingDeleteModel = null }) { Text(tr(lang, "取消", "Cancel")) } },
        confirmButton = { TextButton({ models.remove(target); if (defaultModelId == target.id) defaultModelId = models.firstOrNull()?.id.orEmpty(); pendingDeleteModel = null }) { Text(tr(lang, "删除", "Delete"), color = MaterialTheme.colorScheme.error) } },
    ) }
    if (confirmDelete && existing != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(tr(lang, "删除模型配置？", "Delete model configuration?")) },
        text = { Text(tr(lang, "“${existing.name}”及其中保存的 API Key 和模型目录将被删除。", "“${existing.name}”, its API key and model catalog will be deleted.")) },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(tr(lang, "取消", "Cancel")) } },
        confirmButton = { TextButton({ store.delete(existing); confirmDelete = false; onDeleted() }) { Text(tr(lang, "删除", "Delete"), color = MaterialTheme.colorScheme.error) } },
    )
}

@Composable
private fun EmptyModelCatalog(lang: String) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(tr(lang, "还没有模型，可以从 API 获取或手动添加。", "No models yet. Fetch from the API or add one manually."), Modifier.padding(22.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModelCatalogCard(lang: String, model: CodexProviderStore.ModelConfig, isDefault: Boolean, onDefault: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (isDefault) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(modelDisplayName(model) ?: model.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(model.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isDefault) Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) { Text(tr(lang, "默认", "Default"), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) }
            }
            val details = buildList {
                if (model.contextWindow > 0) add("${model.contextWindow} tokens")
                if (model.supportedReasoningEfforts.isNotBlank()) add(model.supportedReasoningEfforts)
                if (model.multiAgentVersion.isNotBlank()) add(model.multiAgentVersion.uppercase())
            }.joinToString(" · ")
            if (details.isNotBlank()) Text(details, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                if (!isDefault) TextButton(onDefault) { Text(tr(lang, "设为默认", "Set default")) }
                TextButton(onEdit) { Text(tr(lang, "编辑", "Edit")) }
                TextButton(onDelete) { Text(tr(lang, "删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ModelEditorDialog(
    lang: String,
    editing: CodexProviderStore.ModelConfig?,
    seed: CodexProviderStore.ModelConfig?,
    existingIds: List<String>,
    onDismiss: () -> Unit,
    onSave: (CodexProviderStore.ModelConfig) -> Unit,
) {
    val value = remember(seed?.id, editing?.id) { (seed ?: CodexProviderStore.ModelConfig("", "", 0L)).copy() }
    var name by remember { mutableStateOf(value.name) }
    var id by remember { mutableStateOf(value.id) }
    var description by remember { mutableStateOf(value.description) }
    var contextWindow by remember { mutableStateOf(value.contextWindow.takeIf { it > 0 }?.toString().orEmpty()) }
    var compactLimit by remember { mutableStateOf(value.autoCompactTokenLimit.takeIf { it > 0 }?.toString().orEmpty()) }
    var effectivePercent by remember { mutableStateOf(value.effectiveContextWindowPercent.toString()) }
    var defaultEffort by remember { mutableStateOf(value.defaultReasoningEffort) }
    var supportedEfforts by remember { mutableStateOf(value.supportedReasoningEfforts) }
    var ultraTransportEffort by remember { mutableStateOf(value.ultraTransportEffort) }
    var defaultSummary by remember { mutableStateOf(value.defaultReasoningSummary) }
    var defaultVerbosity by remember { mutableStateOf(value.defaultVerbosity) }
    var shellType by remember { mutableStateOf(value.shellType) }
    var multiAgentVersion by remember { mutableStateOf(value.multiAgentVersion) }
    var toolMode by remember { mutableStateOf(value.toolMode) }
    var baseInstructions by remember { mutableStateOf(value.baseInstructions) }
    var imageInput by remember { mutableStateOf(value.imageInput) }
    var imageDetail by remember { mutableStateOf(value.imageDetailOriginal) }
    var reasoningSummaries by remember { mutableStateOf(value.reasoningSummaries) }
    var parallelTools by remember { mutableStateOf(value.parallelToolCalls) }
    var verbosity by remember { mutableStateOf(value.verbosity) }
    var webSearch by remember { mutableStateOf(value.webSearch) }
    var skillsInstructions by remember { mutableStateOf(value.includeSkillsInstructions) }
    var responsesLite by remember { mutableStateOf(value.responsesLite) }
    var applyPatch by remember { mutableStateOf(value.applyPatchTool) }
    var error by remember { mutableStateOf<String?>(null) }

    fun saveModel() {
        val nextId = id.trim()
        val context = contextWindow.ifBlank { "0" }.toLongOrNull()
        val compact = compactLimit.ifBlank { "0" }.toLongOrNull()
        val percent = effectivePercent.toIntOrNull()
        val invalidEffort = CodexProviderStore.ModelConfig.invalidReasoningEffort(supportedEfforts)
        val normalizedEfforts = CodexProviderStore.ModelConfig.normalizeEfforts(supportedEfforts)
        error = when {
            nextId.isBlank() -> tr(lang, "请填写模型 ID", "Enter a model ID")
            existingIds.any { it.equals(nextId, true) } -> tr(lang, "该模型 ID 已存在", "This model ID already exists")
            context == null || context < 0 -> tr(lang, "上下文窗口应为非负整数", "Context window must be a non-negative integer")
            compact == null || compact < 0 -> tr(lang, "压缩阈值应为非负整数", "Compact limit must be a non-negative integer")
            context > 0 && compact > context -> tr(lang, "压缩阈值不能超过上下文窗口", "Compact limit cannot exceed context window")
            percent == null || percent !in 1..100 -> tr(lang, "可用上下文比例应为 1–100", "Effective context percent must be 1–100")
            normalizedEfforts.isBlank() -> tr(lang, "至少选择一个推理强度", "Select at least one reasoning effort")
            invalidEffort.isNotBlank() -> tr(lang, "不支持的推理强度：$invalidEffort", "Unsupported reasoning effort: $invalidEffort")
            !CodexProviderStore.ModelConfig.supportsEffort(normalizedEfforts, defaultEffort) -> tr(lang, "支持列表必须包含默认推理强度 $defaultEffort", "Supported efforts must include default $defaultEffort")
            ultraTransportEffort.isNotBlank() && !CodexProviderStore.ModelConfig.supportsEffort(normalizedEfforts, ultraTransportEffort) -> tr(lang, "支持列表必须包含 Ultra 上游强度 $ultraTransportEffort", "Supported efforts must include Ultra transport effort $ultraTransportEffort")
            else -> null
        }
        if (error != null) return
        val resolvedMultiAgent = if (CodexProviderStore.ModelConfig.supportsEffort(normalizedEfforts, "ultra") && multiAgentVersion.isBlank()) "v2" else multiAgentVersion
        onSave(CodexProviderStore.ModelConfig(
            name.trim(), nextId, description.trim(), baseInstructions.trim(), context!!, compact!!, percent!!,
            defaultEffort, normalizedEfforts, defaultSummary, defaultVerbosity, shellType,
            imageInput, imageInput && imageDetail, reasoningSummaries, parallelTools, verbosity, webSearch,
            skillsInstructions, responsesLite, applyPatch, resolvedMultiAgent, toolMode, ultraTransportEffort,
        ))
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SettingsScaffold(
                if (editing == null) tr(lang, "添加模型", "Add model") else tr(lang, "编辑模型", "Edit model"),
                tr(lang, "定义 Codex 模型目录中的完整运行能力", "Define complete Codex model catalog capabilities"), onDismiss,
            ) { pad ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                    item { SettingsSection(tr(lang, "模型信息", "Model information")) }
                    item { SettingsTextField(name, { name = it }, tr(lang, "模型名称", "Model name"), tr(lang, "例如：GPT 5.6 Sol", "e.g. GPT 5.6 Sol")) }
                    item { SettingsTextField(id, { id = it; error = null }, tr(lang, "模型 ID", "Model ID"), "gpt-5.6-sol") }
                    item { SettingsTextField(description, { description = it }, tr(lang, "模型描述", "Description"), tr(lang, "用途、特点或供应商备注", "Purpose, characteristics or provider notes")) }
                    item { SettingsTextField(contextWindow, { contextWindow = it; error = null }, tr(lang, "上下文窗口（tokens）", "Context window (tokens)"), "200000", keyboardType = KeyboardType.Number) }
                    item { SettingsTextField(compactLimit, { compactLimit = it; error = null }, tr(lang, "自动压缩阈值（tokens）", "Auto-compact limit (tokens)"), tr(lang, "可留空", "Optional"), keyboardType = KeyboardType.Number) }
                    item { SettingsTextField(effectivePercent, { effectivePercent = it; error = null }, tr(lang, "可用上下文比例（%）", "Effective context (%)"), "95", keyboardType = KeyboardType.Number) }
                    item { SettingsSection(tr(lang, "推理与输出", "Reasoning & output")) }
                    item { ChoiceSettingsField(tr(lang, "默认推理强度", "Default reasoning effort"), defaultEffort, reasoningEffortOptions(lang)) { defaultEffort = it } }
                    item { SettingsTextField(supportedEfforts, { supportedEfforts = it; error = null }, tr(lang, "支持的推理强度（逗号分隔）", "Supported efforts (comma-separated)"), "none,minimal,low,medium,high,xhigh,max,ultra") }
                    item { ChoiceSettingsField(tr(lang, "Ultra 上游推理强度", "Ultra transport effort"), ultraTransportEffort, listOf("" to tr(lang, "自动（最高兼容）", "Auto (highest compatible)")) + reasoningEffortOptions(lang).filter { it.first != "ultra" }) { ultraTransportEffort = it } }
                    item { Text(tr(lang, "本地仍保持 Ultra 主动多代理；该值只控制发送给上游的 reasoning effort。", "Local Ultra multi-agent remains enabled; this only controls the upstream reasoning effort."), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { ChoiceSettingsField(tr(lang, "默认推理摘要", "Default reasoning summary"), defaultSummary, listOf("none" to tr(lang, "不生成", "None"), "auto" to tr(lang, "自动", "Auto"), "concise" to tr(lang, "简洁", "Concise"), "detailed" to tr(lang, "详细", "Detailed"))) { defaultSummary = it } }
                    item { ChoiceSettingsField(tr(lang, "默认输出详细度", "Default verbosity"), defaultVerbosity, listOf("low" to tr(lang, "简洁", "Low"), "medium" to tr(lang, "适中", "Medium"), "high" to tr(lang, "详细", "High"))) { defaultVerbosity = it } }
                    item { ChoiceSettingsField(tr(lang, "Shell 工具", "Shell tool"), shellType, listOf("shell_command" to "Shell Command", "default" to tr(lang, "默认", "Default"), "local" to tr(lang, "本地", "Local"), "unified_exec" to tr(lang, "统一执行", "Unified exec"), "disabled" to tr(lang, "禁用", "Disabled"))) { shellType = it } }
                    item { ChoiceSettingsField(tr(lang, "多代理版本", "Multi-agent version"), multiAgentVersion, listOf("" to tr(lang, "关闭", "Off"), "v1" to "V1", "v2" to "V2 (Ultra)")) { multiAgentVersion = it } }
                    item {
                        FilledTonalButton(
                            onClick = {
                                var normalized = CodexProviderStore.ModelConfig.normalizeEfforts(supportedEfforts)
                                if (!CodexProviderStore.ModelConfig.supportsEffort(normalized, "ultra")) normalized = if (normalized.isBlank()) "ultra" else "$normalized,ultra"
                                supportedEfforts = CodexProviderStore.ModelConfig.normalizeEfforts(normalized)
                                multiAgentVersion = "v2"; toolMode = ""; ultraTransportEffort = ""
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        ) { Text(tr(lang, "启用 Ultra / V2 子代理预设", "Enable Ultra / V2 subagent preset")) }
                    }
                    item { ChoiceSettingsField(tr(lang, "Codex 工具模式", "Codex tool mode"), toolMode, listOf("" to tr(lang, "默认", "Default"), "code_mode_only" to "Code Mode Only")) { toolMode = it } }
                    item { Text(tr(lang, "Code Mode Only 是 Sol 官方能力；第三方模型建议保持默认，否则可能缺少 spawn_agent 与 Shell 工具。", "Code Mode Only is an official Sol capability. Keep the default for third-party models or spawn_agent and Shell tools may be missing."), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item {
                        OutlinedTextField(
                            value = baseInstructions, onValueChange = { baseInstructions = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).heightIn(min = 120.dp),
                            label = { Text(tr(lang, "基础指令（高级）", "Base instructions (advanced)")) },
                            placeholder = { Text(tr(lang, "留空则使用 Fcode 默认 Codex 指令", "Leave blank to use Fcode's default Codex instructions")) },
                            minLines = 4, shape = RoundedCornerShape(14.dp),
                        )
                    }
                    item { SettingsSection(tr(lang, "模型能力", "Model capabilities")) }
                    item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "图片输入", "Image input"), tr(lang, "允许向模型发送图片", "Allow images to be sent to the model"), imageInput) { imageInput = it; if (!it) imageDetail = false } }
                    item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "原图细节", "Original image detail"), tr(lang, "保留原始图片细节参数", "Preserve original image detail parameters"), imageDetail && imageInput) { if (imageInput) imageDetail = it } }
                    item { ToggleSettingsRow(HugeIcons.Sparkles, tr(lang, "推理摘要参数", "Reasoning summaries"), tr(lang, "支持 reasoning summary 参数", "Supports the reasoning summary parameter"), reasoningSummaries) { reasoningSummaries = it } }
                    item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "并行工具", "Parallel tool calls"), tr(lang, "允许同时调用多个工具", "Allow multiple tool calls in parallel"), parallelTools) { parallelTools = it } }
                    item { ToggleSettingsRow(HugeIcons.Text, tr(lang, "输出详细度", "Verbosity"), tr(lang, "支持 verbosity 参数", "Supports the verbosity parameter"), verbosity) { verbosity = it } }
                    item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "Web 搜索", "Web search"), tr(lang, "支持内置搜索工具", "Supports the built-in search tool"), webSearch) { webSearch = it } }
                    item { ToggleSettingsRow(HugeIcons.Sparkles, tr(lang, "技能说明", "Skills instructions"), tr(lang, "在基础提示中加入技能使用说明", "Include skill usage instructions in the base prompt"), skillsInstructions) { skillsInstructions = it } }
                    item { ToggleSettingsRow(HugeIcons.Code, "Responses Lite", tr(lang, "使用精简 Responses 协议", "Use the lightweight Responses protocol"), responsesLite) { responsesLite = it } }
                    item { ToggleSettingsRow(HugeIcons.Code, "Apply Patch", tr(lang, "启用 Apply Patch 工具", "Enable the Apply Patch tool"), applyPatch) { applyPatch = it } }
                    if (error != null) item { Text(error.orEmpty(), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    item { Button(::saveModel, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(HugeIcons.Tick02, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr(lang, "保存模型", "Save model")) } }
                    item { Spacer(Modifier.height(28.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceSettingsField(label: String, selected: String, options: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(selectedLabel, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (value, text) -> DropdownMenuItem(
                text = { Text(text) },
                leadingIcon = if (value == selected) ({ Icon(HugeIcons.Tick02, null, Modifier.size(18.dp)) }) else null,
                onClick = { onSelected(value); expanded = false },
            ) }
        }
    }
}

private fun reasoningEffortOptions(lang: String) = listOf(
    "none" to tr(lang, "关闭", "None"), "minimal" to tr(lang, "最少", "Minimal"),
    "low" to tr(lang, "低", "Low"), "medium" to tr(lang, "中", "Medium"),
    "high" to tr(lang, "高", "High"), "xhigh" to tr(lang, "超高", "XHigh"),
    "max" to tr(lang, "最大", "Max"), "ultra" to "Ultra",
)

@Composable
private fun FetchModelsDialog(
    lang: String,
    fetched: List<CodexProviderStore.ModelConfig>,
    existingIds: List<String>,
    onDismiss: () -> Unit,
    onChoose: (CodexProviderStore.ModelConfig) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "API 可用模型", "Models available from API")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(fetched, key = { it.id }) { model ->
                    Surface(onClick = { onChoose(model) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(modelDisplayName(model) ?: model.id, fontWeight = FontWeight.Medium)
                                Text(model.id + if (model.contextWindow > 0) " · ${model.contextWindow} tokens" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(if (existingIds.any { it.equals(model.id, true) }) tr(lang, "更新", "Update") else tr(lang, "添加", "Add"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(tr(lang, "完成", "Done")) } },
    )
}

private data class ConnectionTestResult(val ok: Boolean, val message: String)
private data class FormatDetectionResult(val value: String?, val error: String?)
private data class ModelFetchResult(val models: List<CodexProviderStore.ModelConfig>?, val error: String?)

private fun testProviderConnection(context: android.content.Context, prefs: SharedPreferences, baseUrl: String, apiKey: String): ConnectionTestResult = try {
    val connection = openSettingsConnection(context, prefs, java.net.URL(baseUrl.trimEnd('/') + "/models")) as java.net.HttpURLConnection
    connection.requestMethod = "GET"; connection.connectTimeout = 12_000; connection.readTimeout = 12_000
    connection.setRequestProperty("Authorization", "Bearer $apiKey"); connection.setRequestProperty("Accept", "application/json")
    val code = connection.responseCode; connection.disconnect()
    if (code in 200..299) ConnectionTestResult(true, "连接成功") else ConnectionTestResult(false, "HTTP $code")
} catch (error: Exception) {
    ConnectionTestResult(false, "${error.javaClass.simpleName}: ${error.message.orEmpty()}")
}

private fun detectApiFormat(context: android.content.Context, prefs: SharedPreferences, baseUrl: String, apiKey: String): FormatDetectionResult = try {
    val responses = probeSettingsEndpoint(context, prefs, baseUrl, "/responses", apiKey)
    val chat = probeSettingsEndpoint(context, prefs, baseUrl, "/chat/completions", apiKey)
    when {
        responses != 404 && responses != 405 -> FormatDetectionResult("openai_responses", null)
        chat != 404 && chat != 405 -> FormatDetectionResult("openai_chat", null)
        else -> FormatDetectionResult(null, "未找到 Responses 或 Chat Completions 端点")
    }
} catch (error: Exception) {
    FormatDetectionResult(null, "检测失败：${error.message ?: error.javaClass.simpleName}")
}

private fun probeSettingsEndpoint(context: android.content.Context, prefs: SharedPreferences, baseUrl: String, suffix: String, apiKey: String): Int {
    val connection = openSettingsConnection(context, prefs, java.net.URL(baseUrl.trim().trimEnd('/') + suffix)) as java.net.HttpURLConnection
    connection.requestMethod = "POST"; connection.connectTimeout = 10_000; connection.readTimeout = 10_000; connection.doOutput = true
    connection.setRequestProperty("Authorization", "Bearer $apiKey"); connection.setRequestProperty("Content-Type", "application/json")
    val body = "{}".toByteArray(Charsets.UTF_8); connection.setFixedLengthStreamingMode(body.size)
    connection.outputStream.use { it.write(body) }
    return connection.responseCode.also { connection.disconnect() }
}

private fun fetchProviderModels(context: android.content.Context, prefs: SharedPreferences, baseUrl: String, apiKey: String): ModelFetchResult {
    var connection: java.net.HttpURLConnection? = null
    return try {
        connection = openSettingsConnection(context, prefs, java.net.URL(baseUrl.trim().trimEnd('/') + "/models")) as java.net.HttpURLConnection
        connection.requestMethod = "GET"; connection.connectTimeout = 12_000; connection.readTimeout = 12_000
        connection.setRequestProperty("Authorization", "Bearer $apiKey"); connection.setRequestProperty("Content-Type", "application/json")
        val status = connection.responseCode
        val body = (if (status >= 400) connection.errorStream else connection.inputStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status >= 400) throw java.io.IOException("HTTP $status${if (body.isBlank()) "" else ": $body"}")
        val root = org.json.JSONObject(body)
        val data = root.optJSONArray("data") ?: root.optJSONArray("models") ?: throw java.io.IOException("响应中没有 models/data")
        val result = ArrayList<CodexProviderStore.ModelConfig>()
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val model = CodexProviderStore.ModelConfig.from(item)
            if (model.id.isNotBlank()) result.add(model)
        }
        if (result.isEmpty()) throw java.io.IOException("没有可用模型")
        ModelFetchResult(result.sortedBy { it.id.lowercase() }, null)
    } catch (error: Exception) {
        ModelFetchResult(null, error.message ?: error.javaClass.simpleName)
    } finally {
        connection?.disconnect()
    }
}

private fun openSettingsConnection(context: android.content.Context, prefs: SharedPreferences, url: java.net.URL): java.net.URLConnection {
    if (!prefs.getBoolean("mihomo_route_api", false)) return url.openConnection()
    val manager = MihomoManager.get(context)
    if (!manager.isInstalled) throw java.io.IOException("已启用应用内代理，但 Mihomo 尚未安装")
    if (!manager.isRunning) manager.start()
    return url.openConnection(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", manager.mixedPort())))
}

private fun modelDisplayName(model: CodexProviderStore.ModelConfig?): String? = model?.name?.trim()?.takeIf { it.isNotBlank() } ?: model?.id

private enum class ProxySection { OVERVIEW, NODES, SUBSCRIPTIONS, SETTINGS }

@Composable
private fun ProxySettingsPage(
    lang: String,
    prefs: SharedPreferences,
    onBack: () -> Unit,
    onOpenDashboard: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember { MihomoManager.get(context) }
    val controller = remember { MihomoControllerClient(manager) }
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(ProxySection.OVERVIEW) }
    var runtimeRevision by remember { mutableIntStateOf(0) }
    var groupReloadKey by remember { mutableIntStateOf(0) }
    var subscriptions by remember { mutableStateOf<List<MihomoManager.Subscription>>(emptyList()) }
    var groups by remember { mutableStateOf<List<MihomoControllerClient.ProxyGroup>>(emptyList()) }
    var groupsLoading by remember { mutableStateOf(false) }
    var selectedGroupName by remember { mutableStateOf(prefs.getString("mihomo_proxy_group", "").orEmpty()) }
    var proxySort by remember { mutableStateOf(prefs.getString("mihomo_proxy_sort", "default").orEmpty()) }
    var routeApi by remember { mutableStateOf(prefs.getBoolean("mihomo_route_api", false)) }
    var autoStart by remember { mutableStateOf(prefs.getBoolean("mihomo_auto_start", false)) }
    var proxyNotice by remember { mutableStateOf(prefs.getString("proxy_notice_text", "").orEmpty()) }
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAddSubscription by remember { mutableStateOf(false) }
    var pendingDeleteSubscription by remember { mutableStateOf<MihomoManager.Subscription?>(null) }

    // Observe the polling tick so external process exits/starts refresh the status card.
    @Suppress("UNUSED_VARIABLE") val runtimeTick = runtimeRevision
    val installed = manager.isInstalled
    val running = manager.isRunning
    val supported = manager.isSupported

    fun runAction(
        loading: String,
        success: String,
        refreshSubs: Boolean = false,
        refreshProxyGroups: Boolean = false,
        action: () -> Unit,
    ) {
        if (busyLabel != null) return
        scope.launch {
            busyLabel = loading
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching(action) }
            busyLabel = null
            if (result.isSuccess) {
                runtimeRevision++
                if (refreshSubs) subscriptions = withContext(kotlinx.coroutines.Dispatchers.IO) { manager.subscriptions() }
                if (refreshProxyGroups) groupReloadKey++
                Toast.makeText(context, success, Toast.LENGTH_SHORT).show()
            } else {
                errorMessage = result.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
            }
        }
    }
    fun installBundle() {
        if (!supported) {
            errorMessage = tr(lang, "当前内置内核仅支持 ARM64（arm64-v8a）", "The bundled core currently supports ARM64 (arm64-v8a) only")
            return
        }
        if (busyLabel != null) return
        scope.launch {
            busyLabel = tr(lang, "正在准备组件…", "Preparing components…")
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    manager.install { progress -> scope.launch { busyLabel = progress } }
                }
            }
            busyLabel = null
            if (result.isSuccess) {
                runtimeRevision++
                subscriptions = withContext(kotlinx.coroutines.Dispatchers.IO) { manager.subscriptions() }
                Toast.makeText(context, tr(lang, "Mihomo 与 MetaCubeXD 已就绪", "Mihomo and MetaCubeXD are ready"), Toast.LENGTH_SHORT).show()
            } else {
                errorMessage = result.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (!manager.isInstalled) {
                errorMessage = tr(lang, "请先安装 Mihomo 组件", "Install the Mihomo components first")
            } else {
                val displayName = queryDisplayName(context, uri).substringBeforeLast('.').ifBlank { tr(lang, "本地配置", "Local profile") }
                runAction(
                    tr(lang, "正在导入配置…", "Importing configuration…"),
                    tr(lang, "配置已导入并启用", "Configuration imported and activated"),
                    refreshSubs = true,
                    refreshProxyGroups = true,
                ) {
                    context.contentResolver.openInputStream(uri)?.use { manager.importSubscription(displayName, it) }
                        ?: throw java.io.IOException("Unable to open selected file")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        subscriptions = withContext(kotlinx.coroutines.Dispatchers.IO) { manager.subscriptions() }
        while (isActive) {
            delay(2_000)
            runtimeRevision++
        }
    }
    LaunchedEffect(section, groupReloadKey, running) {
        if (section == ProxySection.NODES && running) {
            groupsLoading = true
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { controller.groups() } }
            groupsLoading = false
            if (result.isSuccess) {
                groups = result.getOrDefault(emptyList())
                val selected = groups.firstOrNull { it.name == selectedGroupName } ?: groups.firstOrNull()
                if (selected != null && selected.name != selectedGroupName) {
                    selectedGroupName = selected.name
                    prefs.edit().putString("mihomo_proxy_group", selected.name).apply()
                }
            } else {
                errorMessage = result.exceptionOrNull()?.message
            }
        } else if (!running) {
            groups = emptyList()
        }
    }

    SettingsScaffold(
        tr(lang, "网络与代理", "Network & proxy"),
        tr(lang, "Mihomo 内核、订阅与节点管理", "Mihomo core, subscriptions and node management"),
        onBack,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ProxySectionBar(lang, section) { section = it }
            if (busyLabel != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(busyLabel.orEmpty(), Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            when (section) {
                ProxySection.OVERVIEW -> ProxyOverviewContent(
                    lang, manager, installed, running, supported, routeApi, subscriptions.firstOrNull { it.active }, busyLabel != null,
                    onPrimaryAction = {
                        when {
                            !installed -> installBundle()
                            running -> runAction(tr(lang, "正在停止…", "Stopping…"), tr(lang, "Mihomo 已停止", "Mihomo stopped"), refreshProxyGroups = true) { manager.stop() }
                            else -> runAction(tr(lang, "正在启动…", "Starting…"), tr(lang, "Mihomo 已启动", "Mihomo started"), refreshProxyGroups = true) { manager.start() }
                        }
                    },
                    onRouteChange = {
                        routeApi = it; prefs.edit().putBoolean("mihomo_route_api", it).apply()
                        if (it && installed && !running) runAction(tr(lang, "正在启动…", "Starting…"), tr(lang, "Mihomo 已启动", "Mihomo started"), refreshProxyGroups = true) { manager.start() }
                        else if (it && !installed) errorMessage = tr(lang, "路由已开启，请先安装 Mihomo 组件", "Routing is enabled; install Mihomo first")
                    },
                    onSubscriptions = { section = ProxySection.SUBSCRIPTIONS },
                    onDashboard = { if (running) onOpenDashboard() else errorMessage = tr(lang, "请先启动 Mihomo", "Start Mihomo first") },
                )
                ProxySection.NODES -> ProxyNodesContent(
                    lang, installed, running, groups, groupsLoading, selectedGroupName, proxySort,
                    onStart = { if (!installed) installBundle() else runAction(tr(lang, "正在启动…", "Starting…"), tr(lang, "Mihomo 已启动", "Mihomo started"), refreshProxyGroups = true) { manager.start() } },
                    onRefresh = { groupReloadKey++ },
                    onGroup = { selectedGroupName = it; prefs.edit().putString("mihomo_proxy_group", it).apply() },
                    onSort = { proxySort = it; prefs.edit().putString("mihomo_proxy_sort", it).apply() },
                    onTest = { group -> runAction(tr(lang, "正在测试延迟…", "Testing latency…"), tr(lang, "测速完成", "Latency test complete"), refreshProxyGroups = true) { controller.testGroup(group.name) } },
                    onSelect = { group, node -> runAction(tr(lang, "正在切换节点…", "Switching node…"), tr(lang, "已切换到 ${node.name}", "Switched to ${node.name}"), refreshProxyGroups = true) { controller.select(group.name, node.name) } },
                )
                ProxySection.SUBSCRIPTIONS -> ProxySubscriptionsContent(
                    lang, installed, subscriptions,
                    onAdd = { if (installed) showAddSubscription = true else errorMessage = tr(lang, "请先安装 Mihomo 组件", "Install Mihomo first") },
                    onImport = { importLauncher.launch(arrayOf("application/yaml", "text/yaml", "text/x-yaml", "text/plain", "application/octet-stream")) },
                    onActivate = { item -> runAction(tr(lang, "正在切换订阅…", "Switching subscription…"), tr(lang, "已切换到 ${item.name}", "Switched to ${item.name}"), refreshSubs = true, refreshProxyGroups = true) { manager.activateSubscription(item.id) } },
                    onUpdate = { item -> runAction(tr(lang, "正在更新 ${item.name}…", "Updating ${item.name}…"), tr(lang, "订阅已更新", "Subscription updated"), refreshSubs = true, refreshProxyGroups = true) { manager.updateSubscription(item.id) } },
                    onDelete = { pendingDeleteSubscription = it },
                )
                ProxySection.SETTINGS -> ProxyRuntimeSettingsContent(
                    lang, manager, installed, routeApi, autoStart, proxyNotice,
                    onInstall = ::installBundle,
                    onRoute = { routeApi = it; prefs.edit().putBoolean("mihomo_route_api", it).apply() },
                    onAutoStart = { autoStart = it; prefs.edit().putBoolean("mihomo_auto_start", it).apply() },
                    onNotice = { proxyNotice = it.take(40); prefs.edit().putString("proxy_notice_text", proxyNotice).apply() },
                    onDashboard = { if (running) onOpenDashboard() else errorMessage = tr(lang, "请先启动 Mihomo", "Start Mihomo first") },
                )
            }
        }
    }

    if (showAddSubscription) AddSubscriptionDialog(
        lang = lang,
        onDismiss = { showAddSubscription = false },
        onAdd = { name, url ->
            showAddSubscription = false
            runAction(
                tr(lang, "正在下载订阅…", "Downloading subscription…"),
                tr(lang, "订阅已添加并启用", "Subscription added and activated"),
                refreshSubs = true,
                refreshProxyGroups = true,
            ) { manager.addSubscription(name, url) }
        },
    )
    pendingDeleteSubscription?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSubscription = null },
            title = { Text(tr(lang, "删除这个订阅？", "Delete this subscription?")) },
            text = { Text(item.name) },
            dismissButton = { TextButton({ pendingDeleteSubscription = null }) { Text(tr(lang, "取消", "Cancel")) } },
            confirmButton = {
                TextButton({
                    pendingDeleteSubscription = null
                    runAction(tr(lang, "正在删除订阅…", "Deleting subscription…"), tr(lang, "订阅已删除", "Subscription deleted"), refreshSubs = true, refreshProxyGroups = true) { manager.deleteSubscription(item.id) }
                }) { Text(tr(lang, "删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(tr(lang, "代理操作未完成", "Proxy action not completed")) },
            text = { Text(message) },
            confirmButton = { TextButton({ errorMessage = null }) { Text(tr(lang, "关闭", "Close")) } },
        )
    }
}

@Composable
private fun ProxySectionBar(lang: String, selected: ProxySection, onSelected: (ProxySection) -> Unit) {
    val items = listOf(
        ProxySection.OVERVIEW to tr(lang, "概览", "Overview"),
        ProxySection.NODES to tr(lang, "节点", "Nodes"),
        ProxySection.SUBSCRIPTIONS to tr(lang, "订阅", "Profiles"),
        ProxySection.SETTINGS to tr(lang, "设置", "Settings"),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (section, label) ->
            Surface(
                onClick = { onSelected(section) },
                modifier = Modifier,
                shape = RoundedCornerShape(14.dp),
                color = if (section == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    label,
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (section == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProxyOverviewContent(
    lang: String,
    manager: MihomoManager,
    installed: Boolean,
    running: Boolean,
    supported: Boolean,
    routeApi: Boolean,
    activeSubscription: MihomoManager.Subscription?,
    busy: Boolean,
    onPrimaryAction: () -> Unit,
    onRouteChange: (Boolean) -> Unit,
    onSubscriptions: () -> Unit,
    onDashboard: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(44.dp), shape = CircleShape, color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Box(contentAlignment = Alignment.Center) { Icon(HugeIcons.Code, null, Modifier.size(22.dp), tint = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mihomo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                when { !supported -> tr(lang, "设备不支持", "Unsupported device"); !installed -> tr(lang, "组件未安装", "Components not installed"); running -> tr(lang, "本地内核运行中", "Local core is running"); else -> tr(lang, "已安装，当前停止", "Installed and stopped") },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(Modifier.size(12.dp), shape = CircleShape, color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant) {}
                    }
                    if (installed) Text(
                        "Mixed  127.0.0.1:${manager.mixedPort()}\nController  127.0.0.1:${manager.controllerPort()}",
                        Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onPrimaryAction, enabled = supported && !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(50.dp), shape = RoundedCornerShape(15.dp),
                    ) {
                        Text(when { !installed -> tr(lang, "安装离线组件", "Install bundled components"); running -> tr(lang, "停止 Mihomo", "Stop Mihomo"); else -> tr(lang, "启动 Mihomo", "Start Mihomo") })
                    }
                }
            }
        }
        item { SettingsSection(tr(lang, "当前订阅", "Active profile")) }
        item {
            NavigationSettingsRow(
                HugeIcons.Folder01,
                activeSubscription?.name ?: tr(lang, "暂无当前订阅", "No active profile"),
                activeSubscription?.let { if (it.isRemote) it.url else tr(lang, "本地 YAML 配置", "Local YAML configuration") } ?: tr(lang, "添加 URL 或导入配置文件", "Add a URL or import a configuration file"),
                onSubscriptions,
            )
        }
        item { SettingsSection(tr(lang, "应用内代理", "In-app routing")) }
        item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "Fcode 请求使用 Mihomo", "Route Fcode through Mihomo"), tr(lang, "不创建 Android VPN，也不会影响其他应用", "Does not create an Android VPN or affect other apps"), routeApi, onRouteChange) }
        item { NavigationSettingsRow(HugeIcons.Code, "MetaCubeXD", tr(lang, "流量、规则、连接和高级控制台", "Traffic, rules, connections and advanced console"), onDashboard) }
    }
}

@Composable
private fun ProxyNodesContent(
    lang: String,
    installed: Boolean,
    running: Boolean,
    groups: List<MihomoControllerClient.ProxyGroup>,
    loading: Boolean,
    selectedGroupName: String,
    sort: String,
    onStart: () -> Unit,
    onRefresh: () -> Unit,
    onGroup: (String) -> Unit,
    onSort: (String) -> Unit,
    onTest: (MihomoControllerClient.ProxyGroup) -> Unit,
    onSelect: (MihomoControllerClient.ProxyGroup, MihomoControllerClient.ProxyNode) -> Unit,
) {
    if (!installed || !running) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { EmptySettingsState(HugeIcons.Code, if (!installed) tr(lang, "尚未安装代理组件", "Proxy components are not installed") else tr(lang, "Mihomo 当前已停止", "Mihomo is stopped"), tr(lang, "启动内核后即可读取代理组、选择节点并测试延迟。", "Start the core to load proxy groups, select nodes and test latency.")) }
            item { Button(onStart, Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(50.dp), shape = RoundedCornerShape(15.dp)) { Text(if (!installed) tr(lang, "安装组件", "Install components") else tr(lang, "启动 Mihomo", "Start Mihomo")) } }
        }
        return
    }
    val selectedGroup = groups.firstOrNull { it.name == selectedGroupName } ?: groups.firstOrNull()
    val sortedNodes = remember(selectedGroup, sort) {
        selectedGroup?.nodes?.toList()?.let { nodes ->
            when (sort) {
                "name" -> nodes.sortedBy { it.name.lowercase() }
                "delay" -> nodes.sortedWith(compareBy<MihomoControllerClient.ProxyNode> { if (it.delay > 0) it.delay else Int.MAX_VALUE }.thenBy { it.name.lowercase() })
                else -> nodes
            }
        }.orEmpty()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(lang, "代理节点", "Proxy nodes"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(tr(lang, "按代理组选择并查看最近延迟", "Select by proxy group and inspect recent latency"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onRefresh) { Icon(HugeIcons.Refresh03, tr(lang, "刷新", "Refresh")) }
            }
        }
        if (loading && groups.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) }
        if (groups.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { group -> ProxyChoiceChip(group.name, group.name == selectedGroup?.name) { onGroup(group.name) } }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("default" to tr(lang, "默认", "Default"), "delay" to tr(lang, "延迟", "Latency"), "name" to tr(lang, "名称", "Name")).forEach { (value, label) ->
                        ProxyChoiceChip(label, sort == value) { onSort(value) }
                    }
                }
            }
            selectedGroup?.let { group ->
                item {
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(group.name, fontWeight = FontWeight.SemiBold)
                                Text(tr(lang, "当前：${group.selected.ifBlank { "—" }}", "Selected: ${group.selected.ifBlank { "—" }}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton({ onTest(group) }) { Text(tr(lang, "测速", "Test")) }
                        }
                    }
                }
                items(sortedNodes, key = { "${group.name}-${it.name}" }) { node ->
                    ProxyNodeCard(lang, node, node.name == group.selected) { onSelect(group, node) }
                }
            }
        } else if (!loading) {
            item { EmptySettingsState(HugeIcons.Code, tr(lang, "没有可选择的代理组", "No selectable proxy groups"), tr(lang, "检查当前订阅是否包含 select、url-test 等代理组。", "Check whether the active profile contains select or url-test proxy groups.")) }
        }
    }
}

@Composable
private fun ProxyChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier, shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ProxyNodeCard(lang: String, node: MihomoControllerClient.ProxyNode, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(10.dp), shape = CircleShape, color = when { selected -> MaterialTheme.colorScheme.primary; !node.alive -> MaterialTheme.colorScheme.error; node.delay > 0 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.outlineVariant }) {}
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(node.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(node.type.ifBlank { tr(lang, "代理节点", "Proxy node") }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (node.delay > 0) "${node.delay} ms" else if (!node.alive) tr(lang, "不可用", "Offline") else "—", style = MaterialTheme.typography.labelLarge, color = when { !node.alive -> MaterialTheme.colorScheme.error; node.delay in 1..250 -> MaterialTheme.colorScheme.primary; node.delay > 0 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
            if (selected) { Spacer(Modifier.width(8.dp)); Icon(HugeIcons.Tick02, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun ProxySubscriptionsContent(
    lang: String,
    installed: Boolean,
    subscriptions: List<MihomoManager.Subscription>,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onActivate: (MihomoManager.Subscription) -> Unit,
    onUpdate: (MihomoManager.Subscription) -> Unit,
    onDelete: (MihomoManager.Subscription) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(tr(lang, "订阅配置", "Subscription profiles"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(tr(lang, "添加 URL、导入 YAML，并在多个配置间切换", "Add URLs, import YAML and switch between profiles"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onAdd, Modifier.weight(1f), enabled = installed, shape = RoundedCornerShape(14.dp)) { Icon(HugeIcons.Add01, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(tr(lang, "添加订阅", "Add URL")) }
                FilledTonalButton(onImport, Modifier.weight(1f), enabled = installed, shape = RoundedCornerShape(14.dp)) { Icon(HugeIcons.Folder01, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(tr(lang, "导入文件", "Import file")) }
            }
        }
        if (!installed) {
            item { EmptySettingsState(HugeIcons.Code, tr(lang, "请先安装代理组件", "Install proxy components first"), tr(lang, "组件安装完成后才能校验和保存订阅配置。", "Profiles can be validated and saved after the components are installed.")) }
        } else if (subscriptions.isEmpty()) {
            item { EmptySettingsState(HugeIcons.Folder01, tr(lang, "暂无订阅", "No profiles"), tr(lang, "添加订阅 URL，或从设备导入 YAML 配置。", "Add a subscription URL or import a YAML configuration from the device.")) }
        } else {
            item { SettingsSection(tr(lang, "订阅列表", "Profiles")) }
            items(subscriptions, key = { it.id }) { item ->
                SubscriptionCard(lang, item, { onActivate(item) }, { onUpdate(item) }, { onDelete(item) })
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    lang: String,
    item: MihomoManager.Subscription,
    onActivate: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = if (item.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (item.isRemote) item.url else tr(lang, "本地 YAML 文件", "Local YAML file"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (item.active) Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) { Text(tr(lang, "当前使用", "Active"), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) }
            }
            if (item.updatedAt > 0) Text(formatSubscriptionTime(lang, item.updatedAt), Modifier.padding(top = 7.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.End) {
                if (!item.active) TextButton(onActivate) { Text(tr(lang, "使用", "Use")) }
                if (item.isRemote) TextButton(onUpdate) { Text(tr(lang, "更新", "Update")) }
                TextButton(onDelete) { Text(tr(lang, "删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun AddSubscriptionDialog(lang: String, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "添加订阅", "Add subscription")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr(lang, "名称（可选）", "Name (optional)")) }, placeholder = { Text(tr(lang, "例如：机场订阅", "e.g. Main profile")) }, singleLine = true)
                OutlinedTextField(url, { url = it; error = false }, label = { Text(tr(lang, "订阅 URL", "Subscription URL")) }, placeholder = { Text("https://example.com/config.yaml") }, singleLine = true, isError = error, supportingText = if (error) ({ Text(tr(lang, "请输入有效的 HTTP(S) 地址", "Enter a valid HTTP(S) URL")) }) else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr(lang, "取消", "Cancel")) } },
        confirmButton = { TextButton({ if (isValidHttpUrl(url.trim())) onAdd(name.trim(), url.trim()) else error = true }) { Text(tr(lang, "添加", "Add")) } },
    )
}

@Composable
private fun ProxyRuntimeSettingsContent(
    lang: String,
    manager: MihomoManager,
    installed: Boolean,
    routeApi: Boolean,
    autoStart: Boolean,
    proxyNotice: String,
    onInstall: () -> Unit,
    onRoute: (Boolean) -> Unit,
    onAutoStart: (Boolean) -> Unit,
    onNotice: (String) -> Unit,
    onDashboard: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { SettingsSection(tr(lang, "组件", "Components")) }
        item { NavigationSettingsRow(HugeIcons.Refresh03, tr(lang, "Mihomo 内核与网页面板", "Mihomo core & dashboard"), "Mihomo v${MihomoManager.CORE_VERSION} · MetaCubeXD v${MihomoManager.DASHBOARD_VERSION} · ${if (installed) tr(lang, "已安装", "Installed") else tr(lang, "未安装", "Not installed")}", onInstall) }
        item { SettingsSection(tr(lang, "运行行为", "Runtime behavior")) }
        item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "应用内 URL 使用 Mihomo", "Route in-app URLs through Mihomo"), tr(lang, "不会影响浏览器或其他 Android 应用", "Does not affect the browser or other Android apps"), routeApi, onRoute) }
        item { ToggleSettingsRow(HugeIcons.Refresh03, tr(lang, "随应用自动启动", "Start with the app"), tr(lang, "只启动本地内核，不创建 Android VPN", "Starts the local core without creating an Android VPN"), autoStart, onAutoStart) }
        item { SettingsSection(tr(lang, "顶部提示", "Proxy-ready notice")) }
        item {
            OutlinedTextField(
                value = proxyNotice, onValueChange = onNotice,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                label = { Text(tr(lang, "提示文字", "Notice text")) },
                placeholder = { Text(tr(lang, "代理已开启", "Proxy enabled")) },
                supportingText = { Text("${proxyNotice.length}/40") },
                singleLine = true, shape = RoundedCornerShape(14.dp),
            )
        }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(9.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
                    Spacer(Modifier.width(10.dp))
                    Text(proxyNotice.ifBlank { tr(lang, "代理已开启", "Proxy enabled") }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(tr(lang, "预览", "Preview"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { SettingsSection(tr(lang, "高级", "Advanced")) }
        item { NavigationSettingsRow(HugeIcons.Code, "MetaCubeXD", tr(lang, "查看流量、规则、连接和节点详情", "Inspect traffic, rules, connections and nodes"), onDashboard) }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr(lang, "本地端点", "Local endpoints"), fontWeight = FontWeight.SemiBold)
                    Text("Mixed Port   127.0.0.1:${manager.mixedPort()}\nController   127.0.0.1:${manager.controllerPort()}", Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(tr(lang, "只代理 Fcode 流量。控制器仅监听本机，并使用应用私有随机密钥保护；不会创建系统 VPN。", "Only Fcode traffic is routed. The controller listens on loopback, uses an app-private random secret, and does not create a system VPN."), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull().orEmpty().ifBlank { "config.yaml" }
}

private fun formatSubscriptionTime(lang: String, timestamp: Long): String {
    val formatted = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(timestamp))
    return tr(lang, "更新于 $formatted", "Updated $formatted")
}

@Composable
private fun WebUiSettingsPage(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, subtitle: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack) { Icon(HugeIcons.ArrowLeft01, null) }
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        content = content,
    )
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
private fun NavigationSettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
private fun ToggleSettingsRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
private fun SettingsIcon(icon: ImageVector) {
    Surface(Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
    }
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value, onValueChange,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        visualTransformation = visualTransformation, trailingIcon = trailing, enabled = enabled, shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun EmptySettingsState(icon: ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
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
private fun InfoDialog(title: String, body: String, lang: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onDismiss) { Text(tr(lang, "完成", "Done")) } })
}

private fun tr(lang: String, zh: String, en: String) = if (lang == "zh") zh else en
private fun resolveLanguage(value: String) = when (value) {
    "en" -> "en"
    "zh" -> "zh"
    else -> if (Locale.getDefault().language == "en") "en" else "zh"
}
private fun themeLabel(lang: String, value: String) = when (value) {
    "light" -> tr(lang, "浅色", "Light")
    "dark" -> tr(lang, "深色", "Dark")
    else -> tr(lang, "跟随系统", "System")
}
private fun apiFormatLabel(value: String) = when (value) {
    "openai_chat" -> "Chat Completions"
    "openai_responses" -> "Responses API"
    else -> "Auto"
}
private fun endpointLabel(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        val uri = Uri.parse(value)
        uri.host?.let { host -> uri.path?.trimEnd('/')?.takeIf(String::isNotBlank)?.let { "$host$it" } ?: host } ?: value
    }.getOrDefault(value)
}
private fun isValidHttpUrl(value: String) = runCatching {
    val uri = Uri.parse(value)
    (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)
