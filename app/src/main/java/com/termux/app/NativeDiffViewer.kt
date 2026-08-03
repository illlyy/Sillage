package com.termux.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Search01
import org.json.JSONObject

/** Line-based unified diff with progressive reveal. */
@Composable
internal fun DiffText(diff: String) {
    var visibleLineLimit by remember(diff) { mutableIntStateOf(300) }
    val visibleLines = remember(diff, visibleLineLimit) {
        diff.lineSequence().take(visibleLineLimit + 1).toList()
    }
    val hasMore = visibleLines.size > visibleLineLimit
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            visibleLines.take(visibleLineLimit).forEach { line ->
                val background = when {
                    line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@") -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    line.startsWith("+") -> Color(0xFFD8F3DC)
                    line.startsWith("-") -> Color(0xFFFFDAD6)
                    else -> Color.Transparent
                }
                val foreground = when {
                    line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF175C2C)
                    line.startsWith("-") && !line.startsWith("---") -> Color(0xFF8C1D18)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = line.ifEmpty { " " },
                    modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 10.dp, vertical = 1.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = foreground,
                )
            }
            if (hasMore) {
                TextButton(onClick = { visibleLineLimit += 300 }) {
                    Text(nativeText(LocalNativeLanguage.current, "加载更多 diff", "Load more diff"))
                }
            }
        }
    }
}

/**
 * Expandable capsule that renders per-file unified diffs for one or more `fileChange` tool items.
 * The heavy diff payload lives in [NativeLargePayloadStore]; it is loaded only when the capsule is
 * expanded. A lightweight `fileSummary` (path + operation) keeps the collapsed header correct.
 */
@Composable
internal fun FileDiffCard(items: List<JSONObject>, projectPath: String = "") {
    val language = LocalNativeLanguage.current
    var expanded by remember { mutableStateOf(false) }
    val itemsKey = items.map { it.toString() }
    val summaryFiles = remember(itemsKey) { NativeFileChangeParser.summarize(items) }
    val payloadRefs = remember(itemsKey) {
        items.mapNotNull { it.optString(NativeLargePayloadStore.PAYLOAD_REF).takeIf(String::isNotBlank) }
    }
    var loadedEntries by remember(itemsKey) { mutableStateOf<List<NativeFileChangeEntry>?>(null) }
    LaunchedEffect(expanded, payloadRefs) {
        if (expanded && loadedEntries == null && payloadRefs.isNotEmpty()) {
            loadedEntries = withContext(Dispatchers.Default) {
                payloadRefs.flatMap { ref ->
                    NativeLargePayloadStore.get(ref)?.let(NativeFileChangeParser::parsePayload).orEmpty()
                }
            }
        } else if (!expanded && loadedEntries != null) {
            loadedEntries = null
        }
    }
    val files = remember(summaryFiles, loadedEntries) {
        NativeFileChangeParser.mergeForDisplay(summaryFiles, loadedEntries)
    }
    if (files.isEmpty()) return
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = 0.88f, stiffness = 320f),
        label = "fileDiffArrow",
    )
    Column(Modifier.padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fcodePressClickable(
                    onClickLabel = if (expanded) nativeText(language, "收起文件变更", "Collapse file changes")
                    else nativeText(language, "展开文件变更", "Expand file changes"),
                ) { expanded = !expanded },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HugeIcons.Files02, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Text(
                    nativeText(language, "修改了 ${files.size} 个文件", "${files.size} files changed"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(6.dp))
                Icon(HugeIcons.ArrowDown01, null, Modifier.size(14.dp).graphicsLayer { rotationZ = arrowRotation }, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        ReasoningCapsuleExpand(expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.66f),
            ) {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    files.forEach { file -> FileDiffRow(file, projectPath) }
                }
            }
        }
    }
}

@Composable
internal fun FileDiffRow(file: NativeFileChangeEntry, projectPath: String) {
    val language = LocalNativeLanguage.current
    var expanded by remember(file.path) { mutableStateOf(false) }
    var viewFilePath by remember(file.path) { mutableStateOf<String?>(null) }
    val resolvedPath = remember(file.path, projectPath) { resolveFilePath(file.path, projectPath) }
    val operation = when (file.operation) {
        "add" -> nativeText(language, "新建", "Added")
        "delete" -> nativeText(language, "删除", "Deleted")
        "move" -> nativeText(language, "移动", "Moved")
        else -> nativeText(language, "编辑", "Edited")
    }
    val operationColor = when (file.operation) {
        "add" -> Color(0xFF175C2C)
        "delete" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val rowArrow by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = 0.88f, stiffness = 320f),
        label = "fileDiffRowArrow",
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fcodePressClickable(
                    onClickLabel = if (expanded) nativeText(language, "收起文件 diff", "Collapse file diff")
                    else nativeText(language, "展开文件 diff", "Expand file diff"),
                ) { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HugeIcons.Files02, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                if (file.path != file.fileName) Text(file.path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            if (resolvedPath != null) IconButton(onClick = { viewFilePath = resolvedPath }, modifier = Modifier.size(30.dp)) {
                Icon(HugeIcons.Search01, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(operation, style = MaterialTheme.typography.labelSmall, color = operationColor, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(5.dp))
            Icon(HugeIcons.ArrowDown01, null, Modifier.size(13.dp).graphicsLayer { rotationZ = rowArrow }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        QElasticExpand(expanded) {
            SafeExpandableViewport(maxHeight = 360.dp) {
                when {
                    file.hasDiff -> DiffText(file.unifiedDiff)
                    file.fullContent.isNotBlank() -> FileContentViewer(file.fullContent)
                    else -> Text(
                        nativeText(language, "展开后加载 diff", "Expand to load diff"),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        viewFilePath?.let { path ->
            FileViewDialog(filePath = path, fileName = file.fileName, onDismiss = { viewFilePath = null })
        }
    }
}

/** Monospace file content viewer with a line-number gutter and progressive loading. */
@Composable
internal fun FileContentViewer(content: String) {
    val language = LocalNativeLanguage.current
    var lineLimit by remember(content) { mutableIntStateOf(300) }
    val lines = remember(content, lineLimit) { content.split("\n").take(lineLimit + 1) }
    val hasMore = lines.size > lineLimit
    val gutterWidth = remember(lines.size) { lines.size.coerceAtLeast(1).toString().length.coerceAtLeast(2) }
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            lines.take(lineLimit).forEachIndexed { index, line ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(
                        (index + 1).toString().padStart(gutterWidth),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        line.ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (hasMore) {
                TextButton(onClick = { lineLimit += 300 }) {
                    Text(nativeText(language, "加载更多", "Load more"))
                }
            }
        }
    }
}

/** Resolves a changed-file path to an existing disk file, joining [projectPath] when relative. */
internal fun resolveFilePath(path: String, projectPath: String): String? {
    val trimmed = path.trim().trim('"', '\'', '`')
    if (trimmed.isBlank()) return null
    val direct = java.io.File(trimmed)
    if (direct.isAbsolute && direct.exists()) return direct.absolutePath
    if (projectPath.isNotBlank()) {
        val joined = java.io.File(projectPath, trimmed)
        if (joined.exists()) return joined.absolutePath
    }
    if (direct.exists()) return direct.absolutePath
    return null
}

/** Full-screen viewer that reads a file from disk on a background thread and shows its content. */
@Composable
internal fun FileViewDialog(filePath: String, fileName: String, onDismiss: () -> Unit) {
    val language = LocalNativeLanguage.current
    var content by remember(filePath) { mutableStateOf<String?>(null) }
    var failed by remember(filePath) { mutableStateOf(false) }
    LaunchedEffect(filePath) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { java.io.File(filePath).readText(Charsets.UTF_8) }.getOrNull()
        }
        if (loaded == null) failed = true else content = loaded
    }
    val sizeLabel = remember(filePath) { formatFileSize(java.io.File(filePath).length()) }
    FlClashAnimatedDialog(
        onDismissRequest = onDismiss,
        title = { Text(fileName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sizeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    when {
                        failed -> Text(
                            nativeText(language, "无法读取文件", "Cannot read file"),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        content == null -> Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(nativeText(language, "正在读取…", "Loading…"), style = MaterialTheme.typography.bodySmall)
                        }
                        else -> SafeExpandableViewport(maxHeight = 420.dp) { FileContentViewer(content!!) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(nativeText(language, "关闭", "Close")) } },
    )
}
