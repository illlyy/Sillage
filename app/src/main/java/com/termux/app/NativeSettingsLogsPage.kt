@file:OptIn(ExperimentalMaterial3Api::class)

package com.termux.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Share08
import me.rerere.hugeicons.stroke.Upload02
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One log file surfaced by the diagnostics page, with its share policy. */
private data class LogEntry(
    val key: String,
    val displayName: String,
    val file: File,
    /** false = under the Termux home (xhome/); share via a redacted cache copy, never FileProvider. */
    val shareViaRedactedCopy: Boolean,
)

private val LOG_LEVELS = listOf(
    FcodeLog.LEVEL_OFF to "OFF",
    FcodeLog.LEVEL_NORMAL to "NORMAL",
    FcodeLog.LEVEL_DEBUG to "DEBUG",
    FcodeLog.LEVEL_VERBOSE to "VERBOSE",
)

@Composable
internal fun LogsSettingsPage(lang: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var fileRevision by remember { mutableIntStateOf(0) }
    var viewing by remember { mutableStateOf<LogEntry?>(null) }
    var exportRequested by remember { mutableStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    var diagnosticMode by remember {
        mutableStateOf(
            context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE)
                .getBoolean("fcode_diagnostic_mode", false),
        )
    }
    val logLevel by produceState(initialValue = FcodeLog.currentLevel(), key1 = fileRevision) {
        value = FcodeLog.currentLevel()
    }
    val entries by produceState<List<LogEntry>>(initialValue = emptyList(), key1 = fileRevision) {
        value = withContext(Dispatchers.IO) { collectLogEntries(context.applicationContext) }
    }

    LaunchedEffect(exportRequested) {
        if (exportRequested > 0) {
            exporting = true
            val zip = withContext(Dispatchers.IO) { exportDiagnosticsZip(context.applicationContext, lang) }
            exporting = false
            if (zip != null) shareFile(context, zip, "application/zip", lang)
            else showToast(context, tr(lang, "导出诊断包失败", "Failed to export diagnostics"))
        }
    }

    SettingsScaffold(
        tr(lang, "日志与诊断", "Logs & diagnostics"),
        tr(lang, "查看、分享或清空本地运行日志", "View, share or clear local runtime logs"),
        onBack,
    ) { contentPadding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item { SettingsSection(tr(lang, "记录级别", "Log level")) }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LOG_LEVELS.forEach { (level, label) ->
                        FilterChip(
                            selected = logLevel == level,
                            onClick = {
                                FcodeLog.setLevel(context, level)
                                fileRevision++
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
            item {
                Text(
                    tr(lang, "默认 NORMAL；在分享前开启诊断模式可记录最详细日志。", "Default NORMAL; enable Diagnostic mode before sharing for the most detail."),
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ToggleSettingsRow(
                    HugeIcons.Upload02,
                    tr(lang, "诊断模式", "Diagnostic mode"),
                    tr(lang, "强制 VERBOSE 级别，记录协议事件与详细字段（不含密钥）", "Force VERBOSE level and record protocol events with detailed fields (no secrets)"),
                    diagnosticMode,
                ) { value ->
                    context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE).edit()
                        .putBoolean("fcode_diagnostic_mode", value).apply()
                    FcodeLog.setLevel(context, if (value) FcodeLog.LEVEL_VERBOSE else FcodeLog.LEVEL_NORMAL)
                    fileRevision++
                }
            }
            item { SettingsSection(tr(lang, "文件", "Files")) }
            if (entries.isEmpty()) {
                item { EmptySettingsState(HugeIcons.Files02, tr(lang, "暂无日志文件", "No log files yet"), tr(lang, "使用应用后日志会出现在这里。", "Logs appear here after using the app.")) }
            } else {
                items(entries, key = { it.key }) { entry ->
                    LogFileRow(lang, entry, onView = { viewing = entry }, onShare = {
                        shareLogEntry(context, entry, lang)
                    }, onDelete = {
                        entry.file.delete()
                        fileRevision++
                    })
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { exportRequested++ },
                        enabled = !exporting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(15.dp),
                    ) { Text(if (exporting) tr(lang, "打包中…", "Packaging…") else tr(lang, "导出诊断包", "Export diagnostics")) }
                    TextButton(onClick = {
                        FcodeLog.clear(context)
                        fileRevision++
                    }) { Text(tr(lang, "清空日志", "Clear logs")) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    viewing?.let { entry ->
        LogViewerDialog(lang, entry, onDismiss = { viewing = null })
    }
}

@Composable
private fun LogFileRow(lang: String, entry: LogEntry, onView: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    // File stats are read once per entry identity; recomposition must not stat the file system.
    val fileMeta = remember(entry.file) {
        val exists = entry.file.isFile
        val sizeText = if (exists) formatSize(entry.file.length()) else tr(lang, "不存在", "missing")
        val timeText = if (exists) formatTime(entry.file.lastModified()) else ""
        fileMetaOf(exists, sizeText, timeText)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(HugeIcons.Files02)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${fileMeta.sizeText} · ${fileMeta.timeText}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onView) { Icon(HugeIcons.Search01, tr(lang, "查看", "View"), Modifier.size(17.dp)) }
                IconButton(onClick = onShare, enabled = fileMeta.exists) { Icon(HugeIcons.Share08, tr(lang, "分享", "Share"), Modifier.size(17.dp)) }
                IconButton(onClick = onDelete, enabled = fileMeta.exists) { Icon(HugeIcons.Delete01, tr(lang, "删除", "Delete"), Modifier.size(17.dp)) }
            }
            if (entry.shareViaRedactedCopy) {
                Text(
                    tr(lang, "位于应用私有目录，分享时自动脱敏", "App-private; redacted automatically when shared"),
                    Modifier.padding(horizontal = 14.dp, vertical = 0.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun LogViewerDialog(lang: String, entry: LogEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val content by produceState(initialValue = "", key1 = entry.key) {
        value = withContext(Dispatchers.IO) { readTail(entry.file) }
    }
    val lines = remember(content) { content.lineSequence().toList() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(tr(lang, "返回", "Back")) }
                Text(entry.displayName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { clipboard.setText(AnnotatedString(content)) }) {
                    Icon(HugeIcons.Copy01, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(tr(lang, "复制", "Copy"))
                }
                TextButton(onClick = { shareText(context, content, entry.displayName, lang) }) { Text(tr(lang, "分享文本", "Share text")) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (content.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr(lang, "文件为空或不可读", "File is empty or unreadable"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(lines.size) { index ->
                        SelectionContainer {
                            Text(
                                lines[index],
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun collectLogEntries(context: Context): List<LogEntry> {
    val result = ArrayList<LogEntry>()
    FcodeLog.files(context).forEach { file ->
        if (file.isFile) result.add(LogEntry("fcode:" + file.name, file.name, file, shareViaRedactedCopy = false))
    }
    val diagDir = File(context.filesDir, "codex-diagnostics")
    val events = File(diagDir, "native-chat-events.jsonl")
    if (events.isFile) result.add(LogEntry("diag:events", "native-chat-events.jsonl", events, false))
    val eventsPrevious = File(diagDir, "native-chat-events.previous.jsonl")
    if (eventsPrevious.isFile) result.add(LogEntry("diag:events-previous", "native-chat-events.previous.jsonl", eventsPrevious, false))
    val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
    val claudeStderr = File(File(home, ".tmp"), "claude-stderr.log")
    if (claudeStderr.isFile) result.add(LogEntry("home:claude-stderr", "claude-stderr.log", claudeStderr, true))
    val crashLog = File(home, "crash_log.md")
    if (crashLog.isFile) result.add(LogEntry("home:crash_log", "crash_log.md", crashLog, true))
    val toolLog = File(home, "fcode-tool-installer.log")
    if (toolLog.isFile) result.add(LogEntry("home:tool-installer", "fcode-tool-installer.log", toolLog, true))
    return result.sortedWith(compareBy { it.displayName })
}

private fun shareLogEntry(context: Context, entry: LogEntry, lang: String) {
    val target = if (entry.shareViaRedactedCopy) {
        redactCopyToCache(context, entry.file, entry.displayName) ?: return
    } else {
        entry.file
    }
    shareFile(context, target, "text/plain", lang)
}

private fun shareFile(context: Context, file: File, mime: String, lang: String) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".logprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, tr(lang, "分享日志", "Share log")))
    } catch (e: Exception) {
        showToast(context, tr(lang, "无法分享日志：", "Unable to share log: ") + (e.message ?: e.javaClass.simpleName))
    }
}

private fun shareText(context: Context, text: String, subject: String, lang: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, tr(lang, "分享文本", "Share text")))
}

private fun redactCopyToCache(context: Context, source: File, name: String): File? {
    val dir = File(context.cacheDir, "fcode-diagnostics")
    if (!dir.isDirectory && !dir.mkdirs()) return null
    val target = File(dir, name)
    return try {
        FileOutputStream(target).use { out ->
            source.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    out.write((CodexAppServerBridgeProtocol.redactSensitiveLogLine(line) + "\n").toByteArray(Charsets.UTF_8))
                }
            }
        }
        target
    } catch (e: Exception) {
        null
    }
}

private fun exportDiagnosticsZip(context: Context, lang: String): File? {
    val dir = File(context.cacheDir, "fcode-diagnostics")
    if (!dir.isDirectory && !dir.mkdirs()) return null
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val zipFile = File(dir, "fcode-diagnostics-$stamp.zip")
    return try {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            FcodeLog.files(context).forEach { file ->
                if (file.isFile) zip.writeEntry("fcode-logs/" + file.name, file.readBytes())
            }
            val diagDir = File(context.filesDir, "codex-diagnostics")
            diagDir.listFiles()?.forEach { file ->
                if (file.isFile) zip.writeEntry("codex-diagnostics/" + file.name, file.readBytes())
            }
            val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
            listOf(
                File(File(home, ".tmp"), "claude-stderr.log") to "home/claude-stderr.log",
                File(home, "crash_log.md") to "home/crash_log.md",
                File(home, "fcode-tool-installer.log") to "home/fcode-tool-installer.log",
            ).forEach { (file, name) ->
                if (file.isFile) {
                    val redacted = file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.joinToString("\n") { CodexAppServerBridgeProtocol.redactSensitiveLogLine(it) }
                    }
                    zip.writeEntry(name, redacted.toByteArray(Charsets.UTF_8))
                }
            }
            val readme = buildString {
                appendLine("Fcode diagnostics export")
                appendLine("=======================")
                appendLine("Generated by: Logs & diagnostics page")
                appendLine()
                appendLine("Session / device metadata:")
                appendLine(FcodeLog.sessionInfo(context).toString(2))
                appendLine()
                appendLine("Repro notes: include the backend type, model, and the exact action that failed.")
            }
            zip.writeEntry("README.txt", readme.toByteArray(Charsets.UTF_8))
        }
        zipFile
    } catch (e: Exception) {
        null
    }
}

private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(bytes)
    closeEntry()
}

private fun readTail(file: File, maxBytes: Long = 256 * 1024): String {
    if (!file.isFile) return ""
    return try {
        val length = file.length()
        val start = (length - maxBytes).coerceAtLeast(0L)
        val bytes = ByteArray((length - start).toInt())
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            raf.readFully(bytes)
        }
        val text = String(bytes, Charsets.UTF_8)
        if (start > 0) "\n…（前文已截断）\n" + text else text
    } catch (e: Exception) {
        "读取失败 / Read failed: ${e.message}"
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
}

private data class FileMeta(val exists: Boolean, val sizeText: String, val timeText: String)

private fun fileMetaOf(exists: Boolean, sizeText: String, timeText: String) = FileMeta(exists, sizeText, timeText)

private val LOG_TIME_FORMAT = ThreadLocal.withInitial { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

private fun formatTime(ms: Long): String = LOG_TIME_FORMAT.get().format(Date(ms))

private fun showToast(context: Context, text: String) {
    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
}
