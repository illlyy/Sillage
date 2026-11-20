package com.termux.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Tick02

@Composable
internal fun DeviceControlSettingsPage(lang: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by DeviceControlRuntime.state.collectAsState()
    val audits by DeviceAuditStore.entries.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showDuration by remember { mutableStateOf(false) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val notificationEnabled = remember(permissionRevision) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val overlayEnabled = remember(permissionRevision) { Settings.canDrawOverlays(context) }

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionRevision++
        DeviceControlRuntime.refresh()
    }
    val notificationSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionRevision++
        DeviceControlRuntime.refresh()
    }

    fun startSession() {
        if (!overlayEnabled && !notificationEnabled) {
            error = tr(lang, "至少需要悬浮窗或通知权限，确保你能随时停止控制会话。", "Allow overlay or notifications so the session can always be stopped.")
            overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            return
        }
        scope.launch {
            busy = true
            error = ""
            runCatching { DeviceControlRuntime.startSession(state.sessionMinutes) }
                .onFailure { error = it.message.orEmpty() }
            busy = false
        }
    }

    fun openShizuku() {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
    }

    fun openDeveloperSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
    }

    SettingsScaffold(
        title = tr(lang, "设备控制", "Device control"),
        subtitle = tr(lang, "通过 Shizuku 让 AI 观察和操作本机", "Let AI observe and operate this phone through Shizuku"),
        onBack = onBack,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                ToggleSettingsRow(
                    HugeIcons.MagicWand01,
                    tr(lang, "启用设备控制", "Enable device control"),
                    tr(lang, "启动本机 MCP 工具，并允许 Codex / Claude 发现设备能力", "Start the local MCP tools for Codex and Claude"),
                    state.enabled,
                ) { enabled ->
                    DeviceControlRuntime.setEnabled(enabled)
                    if (enabled) DeviceControlRuntime.refresh()
                }
            }
            item { SettingsSection(tr(lang, "连接状态", "Connection")) }
            item { DeviceStatusCard(lang, state) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ openShizuku() }, Modifier.weight(1f)) {
                        Text(if (state.shizukuState == DeviceShizukuState.NOT_INSTALLED) tr(lang, "安装 Shizuku", "Install Shizuku") else tr(lang, "打开 Shizuku", "Open Shizuku"))
                    }
                    OutlinedButton({ openDeveloperSettings() }, Modifier.weight(1f)) { Text(tr(lang, "开发者设置", "Developer settings")) }
                }
            }
            if (state.shizukuCompatibilityWarning) {
                item {
                    OutlinedButton(
                        onClick = {
                            val url = "https://github.com/RikkaApps/Shizuku/releases/download/v13.5.4/shizuku-v13.5.4.r1049.0e53409-release.apk"
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text(tr(lang, "下载 Shizuku 13.5.4 兼容版", "Download compatible Shizuku 13.5.4")) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state.shizukuState) {
                        DeviceShizukuState.PERMISSION_REQUIRED -> Button({ DeviceControlRuntime.requestPermission() }, Modifier.weight(1f)) {
                            Text(tr(lang, "授予 Shizuku 权限", "Grant Shizuku permission"))
                        }
                        DeviceShizukuState.READY -> OutlinedButton({ DeviceControlRuntime.refresh() }, Modifier.weight(1f)) {
                            Text(tr(lang, "重新检查", "Check again"))
                        }
                        else -> Button({ DeviceControlRuntime.refresh() }, Modifier.weight(1f)) { Text(tr(lang, "重新连接", "Reconnect")) }
                    }
                    OutlinedButton({
                        notificationSettingsLauncher.launch(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }, Modifier.weight(1f)) {
                        Text(if (notificationEnabled) tr(lang, "通知已开启", "Notifications on") else tr(lang, "打开通知设置", "Notification settings"))
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text(if (overlayEnabled) tr(lang, "悬浮停止按钮已授权", "Overlay stop button allowed") else tr(lang, "授予悬浮停止按钮权限", "Allow overlay stop button")) }
            }

            item { SettingsSection(tr(lang, "AI 控制会话", "AI control session")) }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (state.sessionActive) tr(lang, "控制会话进行中", "Control session active") else tr(lang, "AI 当前不能操作设备", "AI cannot operate the device"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (state.sessionActive) tr(lang, "会话结束后所有 UI 工具立即失效；锁屏也会立即结束。", "All UI tools stop at timeout, manual stop, or screen lock.")
                            else tr(lang, "AI 收到手机操作请求时会自行开启；你也可以在这里手动开始。shell 仍逐次确认。", "AI can start a session itself when asked to operate the phone. You can also start it here. Shell still asks every time."),
                            Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton({ showDuration = true }, Modifier.weight(1f), enabled = !state.sessionActive) {
                                Text("${state.sessionMinutes} min")
                            }
                            Button(
                                onClick = { if (state.sessionActive) DeviceControlRuntime.stopSession("user") else startSession() },
                                Modifier.weight(1f),
                                enabled = !busy && (state.sessionActive || state.canStartSession),
                            ) {
                                Text(
                                    when {
                                        busy -> tr(lang, "启动中…", "Starting…")
                                        state.sessionActive -> tr(lang, "立即停止", "Stop now")
                                        else -> tr(lang, "开始控制", "Start control")
                                    }
                                )
                            }
                        }
                        if (error.isNotBlank()) Text(error, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SettingsSection(tr(lang, "MCP 工具服务", "MCP tool service")) }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (state.mcpRunning) "sillage_device · ${tr(lang, "已连接", "connected")}" else "sillage_device · ${tr(lang, "未运行", "not running")}", fontWeight = FontWeight.SemiBold)
                        Text(tr(lang, "仅绑定 127.0.0.1；端点令牌不会写入日志。修改设置后请新开会话或重启当前后端。", "Loopback only. The endpoint token is never logged. Open a new conversation or restart the backend after changes."), Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SettingsSection(tr(lang, "最近操作", "Recent operations")) }
            if (audits.isEmpty()) {
                item { Text(tr(lang, "尚无操作记录", "No operations yet"), Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(audits.take(20), key = { "${it.timestampMs}-${it.tool}" }) { entry ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(entry.tool.removePrefix("device_"), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(entry.result, color = if (entry.result == "ok") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Text("${entry.durationMs} ms", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    if (showDuration) {
        SettingsChoiceDialog(
            tr(lang, "控制会话时长", "Control session duration"),
            listOf(
                "5" to tr(lang, "5 分钟", "5 minutes"),
                "10" to tr(lang, "10 分钟", "10 minutes"),
                "15" to tr(lang, "15 分钟", "15 minutes"),
                "20" to tr(lang, "20 分钟", "20 minutes"),
                "30" to tr(lang, "30 分钟", "30 minutes"),
            ),
            state.sessionMinutes.toString(),
            { showDuration = false },
        ) { value ->
            DeviceControlRuntime.setSessionMinutes(value.toInt())
            showDuration = false
        }
    }
}

@Composable
private fun DeviceStatusCard(lang: String, state: DeviceControlSnapshot) {
    val title = when (state.shizukuState) {
        DeviceShizukuState.UNSUPPORTED -> tr(lang, "系统不支持", "Unsupported")
        DeviceShizukuState.NOT_INSTALLED -> tr(lang, "未安装 Shizuku", "Shizuku not installed")
        DeviceShizukuState.NOT_RUNNING -> tr(lang, "Shizuku 未运行", "Shizuku not running")
        DeviceShizukuState.PERMISSION_REQUIRED -> tr(lang, "等待 Shizuku 授权", "Waiting for Shizuku permission")
        DeviceShizukuState.STARTING -> tr(lang, "正在连接 Shizuku", "Connecting to Shizuku")
        DeviceShizukuState.READY -> tr(lang, "设备控制已就绪", "Device control ready")
        DeviceShizukuState.ERROR -> tr(lang, "连接异常", "Connection error")
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    if (state.shizukuState == DeviceShizukuState.READY) HugeIcons.Tick02 else HugeIcons.Settings03,
                    null,
                    tint = if (state.shizukuState == DeviceShizukuState.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(horizontal = 5.dp))
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            Text(state.detail.ifBlank { tr(lang, "检查 Shizuku 和 Android 无线调试状态。", "Check Shizuku and Android wireless debugging.") }, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (state.shizukuVersion.isNotBlank()) Text("Shizuku ${state.shizukuVersion}${if (state.serviceUid >= 0) " · UID ${state.serviceUid}" else ""} · MCP ${if (state.mcpRunning) "ON" else "OFF"}", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium)
            if (Build.VERSION.SDK_INT < 30) Text(tr(lang, "本功能首版要求 Android 11+。", "This feature requires Android 11+."), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
        }
    }
}

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
