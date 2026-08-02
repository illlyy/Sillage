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
import me.rerere.hugeicons.stroke.Download04
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
internal fun ModelConfigurationsPage(
    lang: String,
    profiles: List<CodexProviderStore.Profile>,
    activeProfileId: String?,
    revision: Int,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onActivate: (CodexProviderStore.Profile) -> Unit,
    backendType: NativeBackendType = NativeBackendType.CODEX,
    onBackendChange: (NativeBackendType) -> Unit = {},
    claudeProfiles: List<ClaudeProfile> = emptyList(),
    activeClaudeId: String? = null,
    claudeRevision: Int = 0,
    onClaudeAdd: () -> Unit = {},
    onClaudeEdit: (String) -> Unit = {},
    onClaudeActivate: (String) -> Unit = {},
    claudeInstalled: Boolean = false,
    onUninstallClaude: () -> Unit = {},
    codexInstalled: Boolean = false,
    onUninstallCodex: () -> Unit = {},
) {
    var showInstall by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf<String?>(null) }
    SettingsScaffold(tr(lang, "模型与 API", "Models & API"), tr(lang, "选择后端并管理 API 配置", "Choose a backend and manage API configurations"), onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SettingsSection(tr(lang, "后端", "Backend"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackendChoiceChip(
                            label = tr(lang, "Codex", "Codex"),
                            subtitle = tr(lang, "OpenAI 编程代理", "OpenAI coding agent"),
                            selected = backendType == NativeBackendType.CODEX,
                            onClick = { onBackendChange(NativeBackendType.CODEX) },
                            modifier = Modifier.weight(1f),
                        )
                        BackendChoiceChip(
                            label = tr(lang, "Claude", "Claude"),
                            subtitle = tr(lang, "Anthropic Claude Code", "Anthropic Claude Code"),
                            selected = backendType == NativeBackendType.CLAUDE,
                            onClick = { onBackendChange(NativeBackendType.CLAUDE) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        tr(
                            lang,
                            "原生聊天会使用所选后端运行；两个后端各自的 API 配置相互独立。",
                            "Native chat uses the selected backend; each backend keeps its own API configuration.",
                        ),
                        Modifier.padding(top = 8.dp, start = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (backendType == NativeBackendType.CLAUDE) {
                item { SettingsSection(tr(lang, "Claude 配置", "Claude configuration")) }
                if (!claudeInstalled) {
                    item {
                        Surface(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(tr(lang, "Claude CLI 尚未安装", "Claude CLI is not installed"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    tr(lang, "需要下载官方 arm64 静态二进制（约 82 MB）后才能使用 Claude 后端。", "The official arm64 static binary (~82 MB) must be downloaded before the Claude backend can run."),
                                    Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = { showInstall = true },
                                    Modifier.fillMaxWidth().padding(top = 10.dp),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Icon(HugeIcons.Download04, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(tr(lang, "下载并安装 Claude CLI", "Download & install Claude CLI"))
                                }
                            }
                        }
                    }
                } else {
                    item {
                        TextButton(
                            onClick = { confirmUninstall = "claude" },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        ) {
                            Icon(HugeIcons.Delete01, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "卸载 Claude CLI", "Uninstall Claude CLI"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (claudeProfiles.isEmpty()) {
                    item { EmptySettingsState(HugeIcons.Sparkles, tr(lang, "还没有 Claude 配置", "No Claude configuration"), tr(lang, "创建后填写 API Key（可选 Base URL）即可使用。", "Create one and fill in your API key (base URL optional).")) }
                } else {
                    items(claudeProfiles, key = { "${it.id}-$claudeRevision" }) { profile ->
                        ClaudeProfileCard(
                            lang = lang,
                            profile = profile,
                            active = profile.id == activeClaudeId,
                            onActivate = { onClaudeActivate(profile.id) },
                            onEdit = { onClaudeEdit(profile.id) },
                        )
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClaudeAdd, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(HugeIcons.Add01, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text(tr(lang, "新建 Claude 配置", "New Claude configuration"))
                        }
                    }
                }
            } else {
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
                if (codexInstalled) {
                    item {
                        TextButton(
                            onClick = { confirmUninstall = "codex" },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        ) {
                            Icon(HugeIcons.Delete01, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "卸载 Codex CLI", "Uninstall Codex CLI"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (showInstall) {
        ClaudeInstallDialog(
            lang = lang,
            onDismiss = { showInstall = false },
        )
    }
    when (confirmUninstall) {
        "claude" -> AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            icon = { Icon(HugeIcons.Delete01, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(tr(lang, "卸载 Claude CLI？", "Uninstall Claude CLI?")) },
            text = { Text(tr(lang, "将删除 Claude 命令行工具（保留 API 配置与对话记录）。", "The Claude CLI binary will be removed; API profiles and transcripts are kept.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = null
                    onUninstallClaude()
                }) { Text(tr(lang, "卸载", "Uninstall"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = null }) { Text(tr(lang, "取消", "Cancel")) } },
            shape = RoundedCornerShape(28.dp),
        )
        "codex" -> AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            icon = { Icon(HugeIcons.Delete01, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(tr(lang, "卸载 Codex CLI？", "Uninstall Codex CLI?")) },
            text = { Text(tr(lang, "将删除 Codex 命令行工具。WebUI 与终端中的 Codex 命令也会随之不可用。", "The Codex CLI binary will be removed. WebUI and Codex commands in the terminal will stop working.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = null
                    onUninstallCodex()
                }) { Text(tr(lang, "卸载", "Uninstall"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = null }) { Text(tr(lang, "取消", "Cancel")) } },
            shape = RoundedCornerShape(28.dp),
        )
        else -> Unit
    }
}

@Composable
private fun ClaudeInstallDialog(
    lang: String,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var stage by remember { mutableStateOf(tr(lang, "准备中…", "Preparing…")) }
    var percent by remember { mutableIntStateOf(-1) }
    var done by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        ClaudeInstaller.installAsync(context, object : ClaudeInstaller.Progress {
            override fun onStage(stageLabel: String, detail: String) {
                stage = detail
            }

            override fun onDownloadProgress(downloaded: Long, total: Long, percentValue: Int) {
                percent = percentValue
                stage = tr(lang, "下载中 $percentValue%", "Downloading $percentValue%")
            }

            override fun onComplete(success: Boolean, error: String?) {
                done = true
                if (!success) failed = error
            }
        })
    }
    AlertDialog(
        onDismissRequest = { if (done) onDismiss() },
        icon = { if (failed == null && !done) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) },
        title = {
            Text(
                when {
                    failed != null -> tr(lang, "安装失败", "Install failed")
                    done -> tr(lang, "安装完成", "Installed")
                    else -> tr(lang, "安装 Claude CLI", "Installing Claude CLI")
                },
            )
        },
        text = { Text(failed ?: stage) },
        confirmButton = {
            if (done) TextButton(onClick = onDismiss) { Text(tr(lang, "关闭", "Close")) }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun ClaudePresetChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ClaudeModelChip(label: String, selected: String, onSelect: (String) -> Unit) {
    val active = label == selected
    Surface(
        onClick = { onSelect(label) },
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null,
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ClaudeModelPickerDialog(
    lang: String,
    models: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(lang, "选择模型", "Select a model")) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(models, key = { it }) { modelId ->
                    Surface(
                        onClick = { onSelect(modelId); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            modelId,
                            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr(lang, "取消", "Cancel")) } },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun ClaudeAuthChip(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Full-page Claude profile editor (same shape as the Codex ModelConfigurationEditor):
 * presets for new profiles, auth mode chips, primary model, collapsible tier/subagent/env
 * sections, save bar and delete action.
 */
@Composable
internal fun ClaudeConfigurationEditor(
    lang: String,
    initial: ClaudeProfile?,
    onBack: () -> Unit,
    onSave: (ClaudeProfile) -> Unit,
    onDelete: (String) -> Unit = {},
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var apiKeyField by remember { mutableStateOf(initial?.apiKeyField ?: ClaudeSettingsWriter.FIELD_AUTH_TOKEN) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: ClaudeProfile.DEFAULT_MODEL) }
    var haikuModel by remember { mutableStateOf(initial?.haikuModel ?: "") }
    var sonnetModel by remember { mutableStateOf(initial?.sonnetModel ?: "") }
    var opusModel by remember { mutableStateOf(initial?.opusModel ?: "") }
    var subagentModel by remember { mutableStateOf(initial?.subagentModel ?: "") }
    var extraEnv by remember { mutableStateOf(initial?.extraEnv?.toMutableMap() ?: mutableMapOf()) }
    var newEnvKey by remember { mutableStateOf("") }
    var newEnvValue by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var fetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val canSave = apiKey.isNotBlank()

    fun fetchModels() {
        if (apiKey.isBlank() || fetchingModels) return
        fetchingModels = true
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ClaudeModelFetcher.fetch(context, baseUrl, apiKey, apiKeyField)
            }
            fetchingModels = false
            if (result.ok) {
                fetchedModels = result.models
                showModelPicker = true
            } else {
                Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun applyPreset(profile: ClaudeProfile) {
        name = profile.name
        apiKey = profile.apiKey
        apiKeyField = profile.apiKeyField
        baseUrl = profile.baseUrl
        model = profile.model
        haikuModel = profile.haikuModel
        sonnetModel = profile.sonnetModel
        opusModel = profile.opusModel
        subagentModel = profile.subagentModel
        extraEnv = profile.extraEnv.toMutableMap()
    }

    SettingsScaffold(
        if (initial == null) tr(lang, "新建 Claude 配置", "New Claude configuration")
        else tr(lang, "编辑 Claude 配置", "Edit Claude configuration"),
        tr(lang, "认证方式、模型档位与附加环境变量", "Auth mode, model tiers and extra env"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            if (initial == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SettingsSection(tr(lang, "模板", "Template"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ClaudePresetChip(tr(lang, "官方 API", "Official API")) { applyPreset(ClaudePresets.OFFICIAL) }
                            ClaudePresetChip(tr(lang, "通用中转", "Generic relay")) { applyPreset(ClaudePresets.GENERIC_RELAY) }
                            ClaudePresetChip("DeepSeek") { applyPreset(ClaudePresets.DEEPSEEK) }
                            ClaudePresetChip("Kimi") { applyPreset(ClaudePresets.KIMI) }
                        }
                    }
                }
            }
            item { SettingsSection(tr(lang, "基本", "Basics")) }
            item {
                SettingsTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = tr(lang, "名称", "Name"),
                    placeholder = tr(lang, "例如：我的 Claude", "e.g. My Claude"),
                )
            }
            item { SettingsSection(tr(lang, "认证", "Authentication")) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClaudeAuthChip(
                        label = tr(lang, "Bearer Token", "Bearer Token"),
                        subtitle = tr(lang, "中转网关常用", "Relay gateways"),
                        selected = apiKeyField == ClaudeSettingsWriter.FIELD_AUTH_TOKEN,
                        onClick = { apiKeyField = ClaudeSettingsWriter.FIELD_AUTH_TOKEN },
                        modifier = Modifier.weight(1f),
                    )
                    ClaudeAuthChip(
                        label = tr(lang, "API Key", "API Key"),
                        subtitle = tr(lang, "官方 API 用", "Official API"),
                        selected = apiKeyField == ClaudeSettingsWriter.FIELD_API_KEY,
                        onClick = { apiKeyField = ClaudeSettingsWriter.FIELD_API_KEY },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                SettingsTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = if (apiKeyField == ClaudeSettingsWriter.FIELD_AUTH_TOKEN) tr(lang, "Token / Key", "Token / Key") else tr(lang, "API Key", "API Key"),
                    placeholder = tr(lang, "sk-…", "sk-…"),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            }
            item {
                SettingsTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = tr(lang, "Base URL（可选）", "Base URL (optional)"),
                    placeholder = tr(lang, "留空使用官方 API", "Leave blank for the official API"),
                )
            }
            if (baseUrl.isNotBlank() && !isValidHttpUrl(baseUrl)) {
                item {
                    Text(
                        tr(lang, "Base URL 不是有效地址", "Base URL is not a valid address"),
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            item { SettingsSection(tr(lang, "模型", "Models")) }
            item {
                SettingsTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = tr(lang, "主模型", "Primary model"),
                    placeholder = tr(lang, "sonnet / opus / haiku", "sonnet / opus / haiku"),
                )
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(tr(lang, "官方模型", "Official models"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ClaudeModelChip("claude-sonnet-4-5", model) { model = it }
                        ClaudeModelChip("claude-opus-4-1", model) { model = it }
                        ClaudeModelChip("claude-haiku-4-5", model) { model = it }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = canSave && !fetchingModels,
                        onClick = { fetchModels() },
                    ) {
                        if (fetchingModels) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(HugeIcons.Refresh03, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(tr(lang, "从服务获取模型列表", "Fetch models from API"))
                    }
                }
            }
            item {
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(if (showAdvanced) tr(lang, "收起高级选项", "Hide advanced") else tr(lang, "高级模型与附加设置", "Advanced models & env"))
                }
            }
            if (showAdvanced) {
                item {
                    SettingsTextField(value = haikuModel, onValueChange = { haikuModel = it }, label = tr(lang, "Haiku 档模型（空=主模型）", "Haiku tier (blank = primary)"), placeholder = "")
                }
                item {
                    SettingsTextField(value = sonnetModel, onValueChange = { sonnetModel = it }, label = tr(lang, "Sonnet 档模型（空=主模型）", "Sonnet tier (blank = primary)"), placeholder = "")
                }
                item {
                    SettingsTextField(value = opusModel, onValueChange = { opusModel = it }, label = tr(lang, "Opus 档模型（空=主模型）", "Opus tier (blank = primary)"), placeholder = "")
                }
                item {
                    SettingsTextField(value = subagentModel, onValueChange = { subagentModel = it }, label = tr(lang, "子代理模型（可选）", "Subagent model (optional)"), placeholder = "")
                }
                item { SettingsSection(tr(lang, "附加环境变量", "Extra env vars")) }
                extraEnv.forEach { (key, value) ->
                    item(key = "env-$key") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) {
                            Text("$key = $value", Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { extraEnv = extraEnv.toMutableMap().apply { remove(key) } }) { Text(tr(lang, "删除", "Remove")) }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SettingsTextField(value = newEnvKey, onValueChange = { newEnvKey = it }, label = tr(lang, "键", "Key"), placeholder = "ENABLE_TOOL_SEARCH", modifier = Modifier.weight(1f))
                        SettingsTextField(value = newEnvValue, onValueChange = { newEnvValue = it }, label = tr(lang, "值", "Value"), placeholder = "true", modifier = Modifier.weight(1f))
                    }
                }
                item {
                    TextButton(
                        enabled = newEnvKey.isNotBlank(),
                        onClick = {
                            extraEnv = extraEnv.toMutableMap().apply { put(newEnvKey.trim(), newEnvValue.trim()) }
                            newEnvKey = ""
                            newEnvValue = ""
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) { Text(tr(lang, "添加", "Add")) }
                }
            }
            item {
                Button(
                    onClick = {
                        onSave(
                            (initial ?: ClaudeProfile(id = java.util.UUID.randomUUID().toString(), name = "", apiKey = ""))
                                .copy(
                                    name = name, apiKey = apiKey, apiKeyField = apiKeyField,
                                    baseUrl = baseUrl, model = model,
                                    haikuModel = haikuModel, sonnetModel = sonnetModel,
                                    opusModel = opusModel, subagentModel = subagentModel,
                                    extraEnv = extraEnv.toMap(),
                                ),
                        )
                        onBack()
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(HugeIcons.Tick02, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr(lang, "保存配置", "Save configuration"))
                }
            }
            if (initial != null) {
                item {
                    TextButton(
                        onClick = { onDelete(initial.id); onBack() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(tr(lang, "删除此配置", "Delete this configuration"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (showModelPicker) {
        fetchedModels?.let { models ->
            ClaudeModelPickerDialog(
                lang = lang,
                models = models,
                onDismiss = { showModelPicker = false },
                onSelect = { model = it },
            )
        }
    }
}

/** cc-switch style presets; user fills in the credential after picking one. */
private object ClaudePresets {
    val OFFICIAL = ClaudeProfile(
        id = "", name = "官方 API", apiKey = "",
        apiKeyField = ClaudeSettingsWriter.FIELD_API_KEY,
        baseUrl = "", model = "claude-sonnet-4-20250514",
        sonnetModel = "claude-sonnet-4-20250514", opusModel = "claude-opus-4-1",
        haikuModel = "claude-haiku-4-5",
    )
    val GENERIC_RELAY = ClaudeProfile(
        id = "", name = "通用中转", apiKey = "",
        apiKeyField = ClaudeSettingsWriter.FIELD_AUTH_TOKEN,
        baseUrl = "https://api.example.com", model = "claude-sonnet-4-5",
    )
    val DEEPSEEK = ClaudeProfile(
        id = "", name = "DeepSeek", apiKey = "",
        apiKeyField = ClaudeSettingsWriter.FIELD_AUTH_TOKEN,
        baseUrl = "https://api.deepseek.com/anthropic", model = "deepseek-chat",
        sonnetModel = "deepseek-chat", opusModel = "deepseek-reasoner", haikuModel = "deepseek-chat",
    )
    val KIMI = ClaudeProfile(
        id = "", name = "Kimi", apiKey = "",
        apiKeyField = ClaudeSettingsWriter.FIELD_AUTH_TOKEN,
        baseUrl = "https://api.moonshot.cn/anthropic", model = "kimi-k2",
        sonnetModel = "kimi-k2", opusModel = "kimi-k2", haikuModel = "kimi-k2",
    )
}

@Composable
private fun BackendChoiceChip(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(HugeIcons.Tick02, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClaudeProfileCard(
    lang: String,
    profile: ClaudeProfile,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
) {
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
                        Text(profile.name.ifBlank { tr(lang, "未命名", "Unnamed") }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (active) Surface(Modifier.padding(start = 8.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
                            Text(tr(lang, "使用中", "Active"), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(profile.model.ifBlank { ClaudeProfile.DEFAULT_MODEL }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (profile.baseUrl.isBlank()) tr(lang, "官方 API", "Official API") else endpointLabel(profile.baseUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(HugeIcons.ArrowRight01, null, Modifier.size(20.dp))
            }
            if (!active) TextButton(onActivate, Modifier.align(Alignment.End)) {
                Text(tr(lang, "设为当前", "Use this profile"))
            }
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
internal fun ModelConfigurationEditor(
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
        if (busyAction != null) return
        val profile = pendingProfile() ?: return
        busyAction = "save"
        scope.launch {
            val result = runCatching {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val activeBefore = store.active()?.id
                    if (activeBefore == null || activeBefore == existing?.id) store.activate(profile)
                    else store.save(profile)
                }
            }
            busyAction = null
            result.onSuccess { onSaved() }
                .onFailure { failure -> error = failure.message ?: tr(lang, "保存配置失败", "Failed to save configuration") }
        }
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
            item { Button(onClick = ::save, enabled = busyAction == null, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(HugeIcons.Tick02, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr(lang, "保存配置", "Save configuration")) } }
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
        confirmButton = { TextButton(
            onClick = {
                if (busyAction != null) return@TextButton
                confirmDelete = false
                busyAction = "delete"
                scope.launch {
                    val result = runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { store.delete(existing) } }
                    busyAction = null
                    result.onSuccess { onDeleted() }
                        .onFailure { failure -> error = failure.message ?: tr(lang, "删除配置失败", "Failed to delete configuration") }
                }
            },
            enabled = busyAction == null,
        ) { Text(tr(lang, "删除", "Delete"), color = MaterialTheme.colorScheme.error) } },
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
                    item { SettingsTextField(compactLimit, { compactLimit = it; error = null }, tr(lang, "自动压缩阈值（auto_compact_token_limit，tokens）", "Auto-compact threshold (auto_compact_token_limit, tokens)"), tr(lang, "可留空", "Optional"), keyboardType = KeyboardType.Number) }
                    item {
                        Text(
                            tr(
                                lang,
                                "达到该阈值后，由 Codex / app-server 在同一任务内自动压缩上下文并继续执行。原生 UI 只展示压缩状态，不会主动抢占任务或额外触发压缩。",
                                "At this threshold, Codex / app-server automatically compacts context and continues within the same task. The native UI only presents compaction status; it never preempts the task or triggers extra compaction.",
                            ),
                            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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


