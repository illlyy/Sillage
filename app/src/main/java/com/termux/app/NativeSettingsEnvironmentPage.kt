package com.termux.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.TransactionHistory

/**
 * Environment self-check page.
 *
 * Interaction rules this page follows deliberately:
 * - It never writes anything. Every row either explains itself or sends you to the single page
 *   that owns that component, because a previous "fix" here rebuilt the runtime directory and
 *   silently removed the Claude CLI.
 * - Sorting is by severity, not by category: failures are what you came for.
 * - Healthy rows collapse into one line so a good environment reads as "nothing to do".
 * - No action button appears on a row that has no action; no dead buttons.
 */
@Composable
internal fun EnvironmentSettingsPage(
    lang: String,
    prefs: SharedPreferences,
    environmentRevision: Int,
    onBack: () -> Unit,
    onOpenDevelopmentTools: () -> Unit,
    onOpenModelConfigs: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    var checks by remember { mutableStateOf<List<NativeEnvCheck>>(emptyList()) }
    var showHealthy by remember { mutableStateOf(false) }

    // The app targets SDK 28, so the grantable permission is the legacy storage one; asking here
    // is the only way to fix the workspace without sending the user on a settings hunt.
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            revision++
        } else {
            Toast.makeText(
                context,
                tr(
                    lang,
                    "未授予存储权限，工作区会继续使用应用私有目录",
                    "Without storage access the workspace stays in the app-private directory",
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Re-runs when the page is first shown, on explicit "retest", and whenever the activity
    // resumes (so a permission just granted in system settings is reflected without a retest).
    LaunchedEffect(revision, environmentRevision) {
        running = true
        checks = withContext(Dispatchers.IO) {
            runCatching { NativeEnvironmentDoctor.run(context.applicationContext, prefs) }
                .getOrDefault(emptyList())
        }
        running = false
    }

    val ordered = remember(checks) { NativeEnvironmentDoctor.ordered(checks) }
    val counts = remember(checks) { NativeEnvironmentDoctor.counts(checks) }
    val failures = ordered.filter { it.state == NativeEnvState.FAIL }
    val warnings = ordered.filter { it.state == NativeEnvState.WARN }
    val healthy = ordered.filter { it.state == NativeEnvState.OK }
    val notes = ordered.filter { it.state == NativeEnvState.INFO }

    fun perform(action: NativeEnvAction) {
        when (action) {
            NativeEnvAction.NONE -> Unit
            NativeEnvAction.OPEN_DEVELOPMENT_TOOLS -> onOpenDevelopmentTools()
            NativeEnvAction.OPEN_MODEL_CONFIGS -> onOpenModelConfigs()
            NativeEnvAction.OPEN_SKILLS -> onOpenSkills()
            NativeEnvAction.OPEN_MCP -> onOpenMcp()
            NativeEnvAction.OPEN_LOGS -> onOpenLogs()
            NativeEnvAction.OPEN_APP_SETTINGS -> openAppSettings(context)
            NativeEnvAction.OPEN_NOTIFICATION_SETTINGS -> openNotificationSettings(context)
            NativeEnvAction.OPEN_BATTERY_SETTINGS -> openBatterySettings(context)
            NativeEnvAction.OPEN_OVERLAY_SETTINGS -> openOverlaySettings(context)
            NativeEnvAction.REQUEST_STORAGE -> runCatching {
                storageLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }.onFailure { openAppSettings(context) }
            NativeEnvAction.RUN_ENVIRONMENT_SETUP -> {
                // The one-time tool provisioning only ever ran from the WebUI activity, so a user
                // who lives in native chat never got git/node/python. Run it from here, where the
                // finding is actually reported, and refresh the checks when it finishes.
                val host = context as? android.app.Activity
                if (host == null) {
                    onOpenDevelopmentTools()
                } else {
                    CodexInstaller.setupDevelopmentExtensions(host) { revision++ }
                }
            }
        }
    }

    fun copyReport() {
        val text = NativeEnvironmentDoctor.exportText(context, prefs, checks)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Fcode environment report", text))
        Toast.makeText(
            context,
            tr(lang, "自检结果已复制（不含密钥）", "Report copied (no secrets included)"),
            Toast.LENGTH_SHORT,
        ).show()
    }

    SettingsScaffold(
        tr(lang, "环境自检", "Environment check"),
        tr(lang, "只做检查，不会改动任何文件或配置", "Read-only: nothing is modified"),
        onBack,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                SummaryCard(
                    lang = lang,
                    running = running,
                    counts = counts,
                    onRetest = { revision++ },
                    onCopy = ::copyReport,
                )
            }
            if (running && checks.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            tr(lang, "正在检测运行环境、网络与权限…", "Checking runtime, network and permissions…"),
                            Modifier.padding(top = FcodeSpace.md.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@LazyColumn
            }
            if (!running && checks.isEmpty()) {
                item {
                    EmptySettingsState(
                        HugeIcons.Settings03,
                        tr(lang, "没有拿到检测结果", "No result"),
                        tr(lang, "重新检测一次；若仍然为空，请从「日志与诊断」导出一份报告。", "Run the check again; if it stays empty, export a report from Logs & diagnostics."),
                    )
                }
                return@LazyColumn
            }
            if (failures.isNotEmpty()) {
                item { SettingsSection(tr(lang, "需要处理", "Needs attention")) }
                items(failures, key = { it.id }) { check ->
                    CheckRow(lang, check, onAction = ::perform)
                }
            }
            if (warnings.isNotEmpty()) {
                item { SettingsSection(tr(lang, "需要注意", "Worth knowing")) }
                items(warnings, key = { it.id }) { check ->
                    CheckRow(lang, check, onAction = ::perform)
                }
            }
            if (healthy.isNotEmpty()) {
                item {
                    HealthyHeader(
                        lang = lang,
                        count = healthy.size,
                        expanded = showHealthy,
                        onToggle = { showHealthy = !showHealthy },
                    )
                }
                if (showHealthy) {
                    items(healthy, key = { it.id }) { check ->
                        CheckRow(lang, check, onAction = ::perform)
                    }
                }
            }
            if (notes.isNotEmpty()) {
                item { SettingsSection(tr(lang, "信息", "Information")) }
                items(notes, key = { it.id }) { check ->
                    CheckRow(lang, check, onAction = ::perform)
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(
    lang: String,
    running: Boolean,
    counts: Map<NativeEnvState, Int>,
    onRetest: () -> Unit,
    onCopy: () -> Unit,
) {
    val failures = counts[NativeEnvState.FAIL] ?: 0
    val warnings = counts[NativeEnvState.WARN] ?: 0
    val healthy = counts[NativeEnvState.OK] ?: 0
    val summary = when {
        running -> tr(lang, "正在检测…", "Checking…")
        failures > 0 -> tr(lang, "$failures 项需要处理", "$failures need attention")
        warnings > 0 -> tr(lang, "运行正常，$warnings 项可以改善", "Working, $warnings could be improved")
        else -> tr(lang, "$healthy 项检查全部正常", "All $healthy checks passed")
    }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = when {
            running -> MaterialTheme.colorScheme.surfaceContainerLow
            failures > 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        },
    ) {
        Column(Modifier.padding(FcodeSpace.lg.dp)) {
            Text(summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!running) {
                Text(
                    tr(
                        lang,
                        "$healthy 项正常 · $warnings 项可改善",
                        "$healthy healthy · $warnings improvable",
                    ),
                    Modifier.padding(top = FcodeSpace.xs.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(top = FcodeSpace.md.dp), horizontalArrangement = Arrangement.spacedBy(FcodeSpace.sm.dp)) {
                FilledTonalButton(onClick = onRetest, enabled = !running, shape = RoundedCornerShape(14.dp)) {
                    Icon(HugeIcons.Refresh03, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(FcodeSpace.sm.dp))
                    Text(tr(lang, "重新检测", "Run again"))
                }
                OutlinedButton(onClick = onCopy, enabled = !running, shape = RoundedCornerShape(14.dp)) {
                    Icon(HugeIcons.TransactionHistory, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(FcodeSpace.sm.dp))
                    Text(tr(lang, "复制报告", "Copy report"))
                }
            }
        }
    }
}

@Composable
private fun HealthyHeader(lang: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Spacer(Modifier.width(FcodeSpace.md.dp))
            Text(
                tr(lang, "$count 项检查正常", "$count checks passed"),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onToggle) {
                Text(tr(lang, if (expanded) "收起" else "查看", if (expanded) "Hide" else "Show"))
                Spacer(Modifier.width(2.dp))
                Icon(
                    HugeIcons.ArrowRight01,
                    null,
                    Modifier.size(14.dp).rotate(if (expanded) 90f else 0f),
                )
            }
        }
    }
}

@Composable
private fun CheckRow(lang: String, check: NativeEnvCheck, onAction: (NativeEnvAction) -> Unit) {
    val accent = when (check.state) {
        NativeEnvState.FAIL -> MaterialTheme.colorScheme.error
        NativeEnvState.WARN -> MaterialTheme.colorScheme.tertiary
        NativeEnvState.OK -> MaterialTheme.colorScheme.primary
        NativeEnvState.INFO -> MaterialTheme.colorScheme.outline
    }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(FcodeSpace.lg.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.width(FcodeSpace.md.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr(lang, check.titleZh, check.titleEn),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        groupLabel(lang, check.group),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    tr(lang, check.detailZh, check.detailEn),
                    Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (check.action != NativeEnvAction.NONE) {
                    TextButton(
                        onClick = { onAction(check.action) },
                        Modifier.padding(top = 2.dp),
                        contentPadding = PaddingValues(horizontal = FcodeSpace.md.dp, vertical = 0.dp),
                    ) {
                        Text(tr(lang, check.actionZh, check.actionEn))
                    }
                }
            }
        }
    }
}

private fun groupLabel(lang: String, group: NativeEnvGroup): String = when (group) {
    NativeEnvGroup.RUNTIME -> tr(lang, "运行环境", "Runtime")
    NativeEnvGroup.CLI -> "CLI"
    NativeEnvGroup.PROCESS -> tr(lang, "进程环境", "Process env")
    NativeEnvGroup.NETWORK -> tr(lang, "网络", "Network")
    NativeEnvGroup.STORAGE -> tr(lang, "存储", "Storage")
    NativeEnvGroup.PERMISSION -> tr(lang, "权限", "Permission")
    NativeEnvGroup.EXTENSIONS -> tr(lang, "扩展", "Extensions")
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openBatterySettings(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.fromParts("package", context.packageName, null))
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    // Android 11+ can reject the direct request; the settings list always exists.
    if (!runCatching { context.startActivity(direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) {
        runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        .setData(Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
