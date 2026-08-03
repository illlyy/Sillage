package com.termux.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Sparkles
import org.json.JSONObject

/**
 * Command execution capsule used by both the live timeline and durable history. Resolves
 * potentially 1MB+ streams from [NativeCommandOutputStore] only while expanded.
 */
@Composable
internal fun CommandExecutionCard(item: JSONObject, running: Boolean = false, liveOutput: String = "", compact: Boolean = false) {
    val language = LocalNativeLanguage.current
    val command = NativeCommandPresentation.rawCommand(item)
    val action = NativeCommandPresentation.action(item)
    val actionLabel = NativeCommandPresentation.label(action, language != "en")
    val subject = NativeCommandPresentation.subject(item)
    val cwd = item.optString("cwd", "")
    val exitCode = item.opt("exitCode")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
    val durationMs = item.optLong("durationMs", item.optLong("duration_ms", 0L))
    val status = item.optString("status")
    val isRunning = running || status.equals("inProgress", true) || status.equals("running", true)
    val failed = status.equals("failed", true) || (exitCode.toIntOrNull()?.let { it != 0 } == true)
    val outputRef = item.optString(NativeCommandOutputStore.OUTPUT_REF)
    val stderrRef = item.optString(NativeCommandOutputStore.STDERR_REF)
    val outputPreview = item.optString(NativeCommandOutputStore.OUTPUT_PREVIEW)
    val stderrPreview = item.optString(NativeCommandOutputStore.STDERR_PREVIEW)
    val outputChars = item.optInt(NativeCommandOutputStore.OUTPUT_CHARS, 0)
    val stderrChars = item.optInt(NativeCommandOutputStore.STDERR_CHARS, 0)
    val inlineStdout = item.optString("stdout", "")
    val inlineStderr = item.optString("stderr", "")
    val inlineAggregate = item.optString("aggregatedOutput", item.optString("output", ""))
    val inlineOutput = if (inlineStdout.isNotBlank() || inlineStderr.isNotBlank()) inlineStdout else inlineAggregate
    val cardKey = item.optString("id", item.optString("itemId", "$command|$cwd|$durationMs"))
    var expanded by remember(cardKey, running) { mutableStateOf(false) }
    val pauseFollowForToggle = LocalPauseFollowDuringAnimation.current
    var deferredOutput by remember(outputRef) { mutableStateOf("") }
    var deferredStderr by remember(stderrRef) { mutableStateOf("") }
    var outputLoading by remember(outputRef, stderrRef) { mutableStateOf(false) }
    var previewFallback by remember(outputRef, stderrRef) { mutableStateOf(false) }

    // A completed command keeps only refs in snapshot state. Resolve the potentially 1MB+ stream
    // off the UI thread. The output is intentionally NOT released on collapse: clearing it at the
    // same frame the exit animation starts shrinks the content under the still-animating height,
    // leaving blank space that then pops away. Keep it so the shrink stays matched and re-expanding
    // is instant.
    LaunchedEffect(expanded, outputRef, stderrRef) {
        if (!expanded) return@LaunchedEffect
        if (outputRef.isBlank() && stderrRef.isBlank()) return@LaunchedEffect
        outputLoading = true
        val resolved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            NativeCommandOutputStore.get(outputRef) to NativeCommandOutputStore.get(stderrRef)
        }
        deferredOutput = resolved.first ?: outputPreview
        deferredStderr = resolved.second ?: stderrPreview
        previewFallback = (outputRef.isNotBlank() && resolved.first == null) ||
            (stderrRef.isNotBlank() && resolved.second == null)
        outputLoading = false
    }

    val output = when {
        isRunning -> liveOutput
        outputRef.isNotBlank() -> deferredOutput
        else -> inlineOutput
    }
    val stderr = when {
        stderrRef.isNotBlank() -> deferredStderr
        else -> inlineStderr
    }
    val hasDetails = command.isNotBlank() || outputRef.isNotBlank() || stderrRef.isNotBlank() ||
        inlineOutput.isNotBlank() || inlineStderr.isNotBlank() || cwd.isNotBlank()
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        tween(150, easing = FastOutSlowInEasing),
        label = "commandArrow",
    )
    val accent = when {
        failed -> MaterialTheme.colorScheme.error
        isRunning -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF5E8B68)
    }
    val icon = when (action) {
        NativeCommandPresentation.LIST_FILES, NativeCommandPresentation.CREATE_DIRECTORY -> HugeIcons.Folder01
        NativeCommandPresentation.READ_FILE -> HugeIcons.Files02
        NativeCommandPresentation.SEARCH_FILES -> HugeIcons.Search01
        NativeCommandPresentation.GIT_DIFF, NativeCommandPresentation.GIT_STATUS, NativeCommandPresentation.GIT_LOG -> HugeIcons.Code
        NativeCommandPresentation.DEVICE_COMMAND -> HugeIcons.Code
        else -> HugeIcons.Sparkles
    }
    val statusLabel = when {
        failed -> nativeText(language, "命令执行失败", "Command failed")
        isRunning -> nativeText(language, "正在运行命令", "Running command")
        else -> nativeText(language, "已执行命令", "Command completed")
    }
    val capsuleLabel = command.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { actionLabel }

    val topPadding = if (compact) 2.dp else 6.dp
    val rowVerticalPadding = if (compact) 4.dp else 8.dp
    val iconBoxSize = if (compact) 20.dp else 27.dp
    val iconSize = if (compact) 12.dp else 15.dp
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (isRunning) 0.26f else 0.14f)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        enabled = hasDetails,
                        onClickLabel = if (expanded) {
                            nativeText(language, "收起命令详情", "Collapse command details")
                        } else {
                            nativeText(language, "展开命令详情", "Expand command details")
                        },
                    ) {
                        pauseFollowForToggle()
                        expanded = !expanded
                    }
                    .padding(horizontal = if (compact) 8.dp else 11.dp, vertical = rowVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f), contentColor = accent) {
                    Box(Modifier.size(iconBoxSize), contentAlignment = Alignment.Center) {
                        if (isRunning) CircularProgressIndicator(Modifier.size(iconSize), strokeWidth = 1.6.dp, color = accent)
                        else Icon(icon, null, Modifier.size(iconSize), tint = accent)
                    }
                }
                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                Text(
                    capsuleLabel,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                    fontFamily = if (command.isNotBlank()) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                val meta = buildList {
                    if (durationMs > 0 && !isRunning) add("%.1fs".format(durationMs / 1000.0))
                    if (exitCode.isNotBlank() && failed) add("exit $exitCode")
                    val chars = outputChars + stderrChars
                    if (chars > 0 && !isRunning) add(formatCommandOutputChars(chars, language))
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = accent)
                    Spacer(Modifier.width(6.dp))
                }
                if (hasDetails) Icon(
                    HugeIcons.ArrowDown01,
                    if (expanded) nativeText(language, "收起命令详情", "Collapse command details") else nativeText(language, "展开命令详情", "Expand command details"),
                    Modifier.size(15.dp).graphicsLayer { rotationZ = arrowRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QElasticExpand(expanded && hasDetails) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                    if (command.isNotBlank()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp)) {
                            Text(nativeText(language, "原始命令", "Raw command"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer {
                                Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (cwd.isNotBlank()) Text(cwd, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (outputLoading) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.6.dp)
                            Spacer(Modifier.width(7.dp))
                            Text(nativeText(language, "正在读取命令输出…", "Loading command output…"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (output.isNotBlank()) CommandStreamSection(nativeText(language, "输出", "Output"), output, false)
                    if (stderr.isNotBlank()) CommandStreamSection(nativeText(language, "错误输出", "Error output"), stderr, true)
                    if (previewFallback) {
                        Text(
                            nativeText(language, "完整输出已从缓存释放，当前显示摘要。", "The full output was released from cache; showing its preview."),
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

internal fun formatCommandOutputChars(chars: Int, language: String): String = when {
    chars >= 1_000_000 -> "%.1fM".format(chars / 1_000_000.0)
    chars >= 1_000 -> "%.1fK".format(chars / 1_000.0)
    else -> nativeText(language, "$chars 字符", "$chars chars")
}

/** Progressive command output body; chunks are revealed frame by frame to keep jank low. */
@Composable
internal fun CommandStreamSection(title: String, value: String, error: Boolean) {
    val chunks = remember(value) { NativeUiRenderSafety.splitPlainText(value.trimEnd()) }
    var visibleCount by remember(value) { mutableIntStateOf(0) }
    LaunchedEffect(value) {
        visibleCount = 0
        chunks.indices.forEach { index ->
            withFrameNanos { }
            visibleCount = index + 1
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(if (error) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f) else Color.Transparent)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                chunks.take(visibleCount).forEach { chunk ->
                    Text(chunk, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (visibleCount < chunks.size) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Spacer(Modifier.width(6.dp))
                Text(nativeText(LocalNativeLanguage.current, "正在加载输出…", "Loading output…"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal enum class CommandViewType { DIRECTORY, FILE, SEARCH, TERMINAL }

internal fun commandViewType(content: String): CommandViewType {
    val command = content.lineSequence().firstOrNull().orEmpty().removePrefix("$ ").trim()
    return when {
        Regex("(^|\\s|[/\"'`])(ls|tree|find|fd)(\\s|$)").containsMatchIn(command) || command.contains("Get-ChildItem") -> CommandViewType.DIRECTORY
        Regex("(^|\\s|[/\"'`])(cat|head|tail|sed)(\\s|$)").containsMatchIn(command) || command.contains("Get-Content") -> CommandViewType.FILE
        Regex("(^|\\s|[/\"'`])(rg|grep|findstr)(\\s|$)").containsMatchIn(command) || command.contains("Select-String") -> CommandViewType.SEARCH
        else -> CommandViewType.TERMINAL
    }
}

@Composable
internal fun SmartCommandCard(content: String) {
    when (commandViewType(content)) {
        CommandViewType.DIRECTORY -> DirectoryOutputCard(content)
        CommandViewType.FILE -> FileOutputCard(content)
        CommandViewType.SEARCH -> SearchOutputCard(content)
        CommandViewType.TERMINAL -> ToolTextCard("命令执行", content, false)
    }
}

@Composable
internal fun DirectoryOutputCard(content: String) {
    val rawLines = content.lines().drop(1).filter { it.isNotBlank() && !it.startsWith("exit ") }
    val lines = rawLines.sortedWith(compareBy<String> { line ->
        val name = line.trim().substringAfterLast(' ')
        !(name.endsWith("/") || line.startsWith("d"))
    }.thenBy { it.lowercase() })
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) lines else lines.take(12)
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) nativeText(language, "收起目录内容", "Collapse directory contents")
                        else nativeText(language, "展开目录内容", "Expand directory contents"),
                    ) { expanded = !expanded }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.Folder01, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text("目录内容", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(nativeText(language, "${lines.size} 项", "${lines.size} items"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            visible.forEach { line ->
                val name = line.trim().substringAfterLast(' ')
                val folder = name.endsWith("/") || line.startsWith("d")
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    val image = name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "svg")
                    Icon(if (folder) HugeIcons.Folder01 else if (image) HugeIcons.Image02 else HugeIcons.Files02, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp)); SelectionContainer { Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (lines.size > 12) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) nativeText(language, "收起", "Collapse") else nativeText(language, "显示剩余 ${lines.size - 12} 项", "Show ${lines.size - 12} more")) }
        }
    }
}

@Composable
internal fun FileOutputCard(content: String) {
    val command = content.lineSequence().firstOrNull().orEmpty().removePrefix("$ ")
    val name = command.trim().split(Regex("\\s+")).lastOrNull().orEmpty().substringAfterLast('/')
    ToolTextCard(name.ifBlank { "文件内容" }, content.lines().drop(1).filterNot { it.startsWith("exit ") }.joinToString("\n"), false)
}

@Composable
internal fun SearchOutputCard(content: String) {
    val results = content.lines().drop(1).filter { it.isNotBlank() && !it.startsWith("exit ") }
    val grouped = results.groupBy { line ->
        val match = Regex("^(.+?):(\\d+):(.*)$").find(line)
        match?.groupValues?.get(1) ?: "搜索输出"
    }
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fcodePressClickable(
                        onClickLabel = if (expanded) nativeText(language, "收起搜索结果", "Collapse search results")
                        else nativeText(language, "展开搜索结果", "Expand search results"),
                    ) { expanded = !expanded }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.Search01, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text("搜索结果", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(nativeText(language, "${results.size} 条 · ${grouped.size} 个文件", "${results.size} matches · ${grouped.size} files"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QElasticExpand(expanded) {
                SafeExpandableViewport(maxHeight = 420.dp) {
                    Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                grouped.forEach { (file, lines) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(HugeIcons.Files02, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp)); Text(file, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    lines.forEach { line ->
                        val match = Regex("^(.+?):(\\d+):(.*)$").find(line)
                        val shown = if (match != null) "${match.groupValues[2].padStart(4)}  ${match.groupValues[3]}" else line
                        SelectionContainer { Text(shown, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                    }
                }
            }
        }
    }
}
