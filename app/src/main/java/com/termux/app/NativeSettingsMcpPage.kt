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


private data class McpSettingsSnapshot(
    val loaded: Boolean = false,
    val servers: List<NativeMcpServerConfig> = emptyList(),
    val statuses: Map<String, NativeMcpRuntimeStatus> = emptyMap(),
    val error: String = "",
)

@Composable
internal fun McpSettingsPage(
    lang: String,
    prefs: SharedPreferences,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val snapshot by produceState(McpSettingsSnapshot(), revision) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val servers = NativeMcpConfigStore.load()
                McpSettingsSnapshot(
                    loaded = true,
                    servers = servers,
                    statuses = NativeMcpRuntimeStatusStore.load(context, servers),
                )
            }.getOrElse { McpSettingsSnapshot(true, error = it.message.orEmpty()) }
        }
    }
    fun markChanged() {
        prefs.edit().putLong(NativeMcpConfigStore.REVISION_KEY, System.currentTimeMillis()).apply()
        revision++
    }
    fun refreshStatus() {
        if (refreshing) return
        refreshing = true
        CodexNativeRuntime.refreshMcpStatus()
        scope.launch {
            delay(2500) // Wait for the async mcpServerStatus/list response to be recorded.
            revision++
            refreshing = false
        }
    }
    SettingsScaffold(
        "MCP",
        tr(lang, "\u4e0e WebUI \u5171\u7528 Codex config.toml \u4e2d\u7684\u5916\u90e8\u5de5\u5177", "Share external tools from Codex config.toml with WebUI"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                Text(
                    tr(lang, "\u4fdd\u5b58\u540e\u8fd4\u56de\u804a\u5929\u9875\u4f1a\u81ea\u52a8\u91cd\u8f7d\u540e\u7aef\uff0c\u65b0\u5bf9\u8bdd\u5373\u53ef\u4f7f\u7528 MCP \u5de5\u5177\u3002", "After saving, returning to chat reloads the backend so new conversations can use the MCP tools."),
                    Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (snapshot.loaded && snapshot.servers.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { refreshStatus() }, enabled = !refreshing) {
                            Icon(HugeIcons.Refresh03, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (refreshing) tr(lang, "\u5237\u65b0\u4e2d…", "Refreshing…") else tr(lang, "\u5237\u65b0\u72b6\u6001", "Refresh status"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            when {
                !snapshot.loaded -> item { EmptySettingsState(HugeIcons.Code, tr(lang, "\u6b63\u5728\u8bfb\u53d6 MCP", "Loading MCP"), tr(lang, "\u6b63\u5728\u89e3\u6790 config.toml", "Parsing config.toml")) }
                snapshot.error.isNotBlank() -> item { Text(snapshot.error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) }
                snapshot.servers.isEmpty() -> item { EmptySettingsState(HugeIcons.Code, tr(lang, "\u8fd8\u6ca1\u6709 MCP \u670d\u52a1", "No MCP servers"), tr(lang, "\u6dfb\u52a0 STDIO \u6216 Streamable HTTP \u670d\u52a1", "Add a STDIO or Streamable HTTP server")) }
                else -> {
                    item { SettingsSection(tr(lang, "\u670d\u52a1\u5668", "Servers")) }
                    items(snapshot.servers, key = { it.key }) { server ->
                        val runtimeStatus = snapshot.statuses[server.key] ?: NativeMcpRuntimeStatus()
                        Card(
                            onClick = { onEdit(server.key) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                SettingsIcon(HugeIcons.Code); Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(server.key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(8.dp))
                                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                            Text(if (server.isHttp) "HTTP" else "STDIO", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        McpRuntimeStatusBadge(lang, runtimeStatus)
                                    }
                                    Text(
                                        if (server.isHttp) server.url else listOf(server.command, server.args.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val statusDetail = when (runtimeStatus.state) {
                                        "connected" -> if (runtimeStatus.toolNames.isNotEmpty())
                                            tr(lang, "\u5df2\u8fde\u63a5\uff0c${runtimeStatus.toolCount} \u4e2a\u5de5\u5177", "Connected, ${runtimeStatus.toolCount} tools") + "\n" + runtimeStatus.toolNames.joinToString(", ")
                                        else tr(lang, "\u5df2\u8fde\u63a5\uff0c${runtimeStatus.toolCount} \u4e2a\u5de5\u5177", "Connected, ${runtimeStatus.toolCount} tools")
                                        "unavailable" -> runtimeStatus.detail.ifBlank { tr(lang, "\u65e0\u6cd5\u8fde\u63a5\u4e0a\u6e38\u670d\u52a1", "Upstream unavailable") }
                                        "disabled" -> tr(lang, "\u5df2\u7981\u7528\uff0c\u4e0d\u4f1a\u5f71\u54cd\u5bf9\u8bdd", "Disabled; chat will continue normally")
                                        else -> tr(lang, "\u7b49\u5f85\u804a\u5929\u540e\u7aef\u68c0\u6d4b\uff0c\u70b9\u51fb\u5237\u65b0\u72b6\u6001\u91cd\u8bd5", "Waiting for probe; tap Refresh to retry")
                                    }
                                    Text(
                                        statusDetail,
                                        modifier = Modifier.padding(top = 5.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (runtimeStatus.state == "unavailable") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Switch(server.enabled, onCheckedChange = { enabled ->
                                    scope.launch {
                                        runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { NativeMcpConfigStore.setEnabled(server.key, enabled) } }
                                            .onSuccess { markChanged() }
                                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                                    }
                                })
                            }
                        }
                    }
                }
            }
            item {
                Button(onAdd, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(HugeIcons.Add01, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text(tr(lang, "\u6dfb\u52a0 MCP \u670d\u52a1", "Add MCP server"))
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun McpRuntimeStatusBadge(lang: String, status: NativeMcpRuntimeStatus) {
    val (label, color) = when (status.state) {
        "connected" -> tr(lang, "\u53ef\u7528", "Online") to androidx.compose.ui.graphics.Color(0xFF4F8A62)
        "unavailable" -> tr(lang, "\u65e0\u6cd5\u8fde\u63a5", "Offline") to MaterialTheme.colorScheme.error
        "disabled" -> tr(lang, "\u5df2\u7981\u7528", "Disabled") to MaterialTheme.colorScheme.onSurfaceVariant
        else -> tr(lang, "\u5f85\u68c0\u6d4b", "Checking") to MaterialTheme.colorScheme.primary
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
internal fun McpServerEditorPage(
    lang: String,
    existingKey: String?,
    prefs: SharedPreferences,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val loaded by produceState<Pair<Boolean, NativeMcpServerConfig?>>(false to null, existingKey) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) { true to NativeMcpConfigStore.load().firstOrNull { it.key == existingKey } }
    }
    if (!loaded.first) {
        SettingsScaffold("MCP", tr(lang, "\u6b63\u5728\u8bfb\u53d6\u914d\u7f6e", "Loading configuration"), onBack) { pad -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        return
    }
    key(existingKey, loaded.second) {
        McpServerEditorContent(lang, loaded.second, prefs, onBack, onSaved, onDeleted)
    }
}

@Composable
private fun McpServerEditorContent(
    lang: String,
    existing: NativeMcpServerConfig?,
    prefs: SharedPreferences,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.key.orEmpty()) }
    var http by remember { mutableStateOf(existing?.isHttp ?: false) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var required by remember { mutableStateOf(existing?.required ?: false) }
    var commandOrUrl by remember { mutableStateOf(if (existing?.isHttp == true) existing.url else existing?.command.orEmpty()) }
    var args by remember { mutableStateOf(existing?.args.orEmpty().joinToString("\n")) }
    var env by remember { mutableStateOf(existing?.env.orEmpty().entries.joinToString("\n") { "${it.key}=${it.value}" }) }
    var envVars by remember { mutableStateOf(existing?.envVars.orEmpty().joinToString("\n")) }
    var cwd by remember { mutableStateOf(existing?.cwd.orEmpty()) }
    var bearer by remember { mutableStateOf(existing?.bearerTokenEnvVar.orEmpty()) }
    var headers by remember { mutableStateOf(existing?.httpHeaders.orEmpty().entries.joinToString("\n") { "${it.key}=${it.value}" }) }
    var envHeaders by remember { mutableStateOf(existing?.envHttpHeaders.orEmpty().entries.joinToString("\n") { "${it.key}=${it.value}" }) }
    var startupTimeout by remember { mutableStateOf(existing?.startupTimeoutSec.orEmpty()) }
    var toolTimeout by remember { mutableStateOf(existing?.toolTimeoutSec.orEmpty()) }
    var enabledTools by remember { mutableStateOf(existing?.enabledTools.orEmpty().joinToString("\n")) }
    var disabledTools by remember { mutableStateOf(existing?.disabledTools.orEmpty().joinToString("\n")) }
    var approvalMode by remember { mutableStateOf(existing?.approvalMode.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun lines(value: String) = value.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    fun pairs(value: String): Map<String, String> = buildMap {
        value.lineSequence().forEach { raw ->
            val line = raw.trim(); if (line.isEmpty()) return@forEach
            val split = line.indexOf('='); if (split <= 0) throw IllegalArgumentException("Invalid KEY=VALUE: $line")
            put(line.substring(0, split).trim(), line.substring(split + 1).trim())
        }
    }
    fun markChanged() = prefs.edit().putLong(NativeMcpConfigStore.REVISION_KEY, System.currentTimeMillis()).apply()
    fun save() {
        val cleanName = name.trim()
        if (!cleanName.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_. -]{0,80}"))) { error = tr(lang, "\u540d\u79f0\u683c\u5f0f\u4e0d\u6b63\u786e", "Invalid server name"); return }
        if (commandOrUrl.isBlank()) { error = if (http) "URL is required" else "Command is required"; return }
        if (http && !commandOrUrl.trim().matches(Regex("https?://.+", RegexOption.IGNORE_CASE))) {
            error = tr(lang, "URL \u5fc5\u987b\u4ee5 http:// \u6216 https:// \u5f00\u5934", "URL must start with http:// or https://")
            return
        }
        fun validTimeout(value: String): Boolean = value.isBlank() || (value.toDoubleOrNull()?.let { it > 0.0 } == true)
        if (!validTimeout(startupTimeout) || !validTimeout(toolTimeout)) {
            error = tr(lang, "\u8d85\u65f6\u5fc5\u987b\u662f\u5927\u4e8e 0 \u7684\u6570\u5b57", "Timeouts must be numbers greater than 0")
            return
        }
        val server = runCatching {
            val allow = lines(enabledTools)
            val deny = lines(disabledTools)
            require(allow.intersect(deny.toSet()).isEmpty()) { tr(lang, "\u540c\u4e00\u5de5\u5177\u4e0d\u80fd\u540c\u65f6\u5141\u8bb8\u548c\u7981\u7528", "A tool cannot be both enabled and disabled") }
            NativeMcpServerConfig(
                key = cleanName, enabled = enabled, required = required,
                command = if (http) "" else commandOrUrl.trim(), args = lines(args), env = pairs(env), envVars = lines(envVars), cwd = cwd.trim(),
                url = if (http) commandOrUrl.trim() else "", bearerTokenEnvVar = bearer.trim(), httpHeaders = pairs(headers), envHttpHeaders = pairs(envHeaders),
                startupTimeoutSec = startupTimeout.trim(), toolTimeoutSec = toolTimeout.trim(), enabledTools = allow, disabledTools = deny, approvalMode = approvalMode.trim(),
            )
        }.getOrElse { error = it.message.orEmpty(); return }
        busy = true; error = ""
        scope.launch {
            runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { NativeMcpConfigStore.save(server, existing?.key) } }
                .onSuccess { markChanged(); onSaved() }
                .onFailure { error = it.message.orEmpty() }
            busy = false
        }
    }

    SettingsScaffold(
        if (existing == null) tr(lang, "\u6dfb\u52a0 MCP", "Add MCP") else tr(lang, "\u7f16\u8f91 MCP", "Edit MCP"),
        tr(lang, "\u914d\u7f6e STDIO \u6216 Streamable HTTP \u670d\u52a1", "Configure a STDIO or Streamable HTTP server"),
        onBack,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item { SettingsSection(tr(lang, "\u57fa\u672c\u4fe1\u606f", "Details")) }
            item { SettingsTextField(name, { name = it; error = "" }, tr(lang, "\u670d\u52a1\u540d\u79f0", "Server name"), "context7") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(!http, { http = false; error = "" }, { Text("STDIO") }, modifier = Modifier.weight(1f))
                    FilterChip(http, { http = true; error = "" }, { Text("Streamable HTTP") }, modifier = Modifier.weight(1f))
                }
            }
            item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "\u542f\u7528", "Enabled"), tr(lang, "\u5141\u8bb8 Codex \u542f\u52a8\u5e76\u4f7f\u7528\u6b64\u670d\u52a1", "Allow Codex to start and use this server"), enabled) { enabled = it } }
            item { ToggleSettingsRow(HugeIcons.Code, tr(lang, "\u5fc5\u9700\u670d\u52a1", "Required"), tr(lang, "\u521d\u59cb\u5316\u5931\u8d25\u65f6\u8ba9 Codex \u542f\u52a8\u5931\u8d25", "Fail Codex startup if this server cannot initialize"), required) { required = it } }
            item { SettingsSection(tr(lang, "\u8fde\u63a5", "Connection")) }
            item { SettingsTextField(commandOrUrl, { commandOrUrl = it; error = "" }, if (http) "URL" else tr(lang, "\u547d\u4ee4", "Command"), if (http) "https://example.com/mcp" else "npx") }
            if (!http) {
                item { SettingsMultilineField(args, { args = it }, tr(lang, "\u53c2\u6570\uff08\u6bcf\u884c\u4e00\u4e2a\uff09", "Arguments (one per line)"), "-y\n@upstash/context7-mcp") }
                item { SettingsMultilineField(env, { env = it }, tr(lang, "\u73af\u5883\u53d8\u91cf", "Environment variables"), "API_KEY=value") }
                item { SettingsMultilineField(envVars, { envVars = it }, tr(lang, "\u8f6c\u53d1\u73af\u5883\u53d8\u91cf", "Forward environment variables"), "LOCAL_TOKEN") }
                item { SettingsTextField(cwd, { cwd = it }, tr(lang, "\u5de5\u4f5c\u76ee\u5f55\uff08\u53ef\u9009\uff09", "Working directory (optional)"), "/data/data/com.termux/files/home") }
            } else {
                item { SettingsTextField(bearer, { bearer = it }, "Bearer token env var", "GITHUB_TOKEN") }
                item { SettingsMultilineField(headers, { headers = it }, tr(lang, "\u9759\u6001 HTTP Headers", "Static HTTP headers"), "X-Region=cn") }
                item { SettingsMultilineField(envHeaders, { envHeaders = it }, tr(lang, "\u73af\u5883 HTTP Headers", "Environment HTTP headers"), "Authorization=AUTH_ENV") }
            }
            item { SettingsSection(tr(lang, "\u9ad8\u7ea7", "Advanced")) }
            item { SettingsTextField(startupTimeout, { startupTimeout = it }, tr(lang, "\u542f\u52a8\u8d85\u65f6\uff08\u79d2\uff09", "Startup timeout (seconds)"), "10", keyboardType = KeyboardType.Decimal) }
            item { SettingsTextField(toolTimeout, { toolTimeout = it }, tr(lang, "\u5de5\u5177\u8d85\u65f6\uff08\u79d2\uff09", "Tool timeout (seconds)"), "60", keyboardType = KeyboardType.Decimal) }
            item { SettingsMultilineField(enabledTools, { enabledTools = it }, tr(lang, "\u5141\u8bb8\u7684\u5de5\u5177", "Enabled tools"), "search\nfetch") }
            item { SettingsMultilineField(disabledTools, { disabledTools = it }, tr(lang, "\u7981\u7528\u7684\u5de5\u5177", "Disabled tools"), "delete") }
            item { SettingsTextField(approvalMode, { approvalMode = it }, tr(lang, "\u9ed8\u8ba4\u5ba1\u6279\u6a21\u5f0f", "Default approval mode"), "auto / prompt / writes / approve") }
            if (error.isNotBlank()) item { Text(error, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
            item { Button(::save, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp), enabled = !busy, shape = RoundedCornerShape(16.dp)) { Text(if (busy) tr(lang, "\u4fdd\u5b58\u4e2d\u2026", "Saving…") else tr(lang, "\u4fdd\u5b58 MCP", "Save MCP")) } }
            if (existing != null) item { TextButton({ confirmDelete = true }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Icon(HugeIcons.Delete01, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr(lang, "\u5220\u9664 MCP \u670d\u52a1", "Remove MCP server"), color = MaterialTheme.colorScheme.error) } }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
    if (confirmDelete && existing != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(tr(lang, "\u5220\u9664 ${existing.key}\uff1f", "Remove ${existing.key}?")) },
        text = { Text(tr(lang, "\u8be5\u670d\u52a1\u5c06\u4ece config.toml \u4e2d\u79fb\u9664\u3002", "This server will be removed from config.toml.")) },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(tr(lang, "\u53d6\u6d88", "Cancel")) } },
        confirmButton = { TextButton({
            confirmDelete = false
            scope.launch {
                busy = true
                runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { NativeMcpConfigStore.remove(existing.key) } }
                    .onSuccess { markChanged(); onDeleted() }
                    .onFailure { error = it.message.orEmpty() }
                busy = false
            }
        }) { Text(tr(lang, "\u5220\u9664", "Remove"), color = MaterialTheme.colorScheme.error) } },
    )
}


