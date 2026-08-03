package com.termux.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.DarkMode
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03

/** One palette entry: display text, fuzzy keywords and the action to run. */
internal data class NativePaletteEntry(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val keywords: List<String> = emptyList(),
    val onClick: () -> Unit,
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return title.contains(q, ignoreCase = true) ||
            subtitle.contains(q, ignoreCase = true) ||
            keywords.any { it.contains(q, ignoreCase = true) }
    }
}

/**
 * Global command palette (pattern: claudecodeui CommandPalette.tsx, adapted for a phone).
 * A full-screen dialog (independent window) hosts a search box and grouped results: quick
 * actions first, then conversations. Selecting an entry runs it and closes the palette.
 */
@Composable
internal fun FcodeCommandPalette(
    visible: Boolean,
    conversations: List<NativeConversation>,
    currentThreadId: String,
    onDismiss: () -> Unit,
    onNewConversation: () -> Unit,
    onResumeConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenFullTextSearch: () -> Unit,
) {
    if (!visible) return
    val language = LocalNativeLanguage.current
    var query by remember { mutableStateOf("") }
    val actions = remember(onNewConversation, onOpenSettings, onToggleTheme, onOpenFullTextSearch) {
        listOf(
            NativePaletteEntry(
                id = "action-new",
                title = nativeText(language, "开始新对话", "Start new chat"),
                icon = HugeIcons.MessageAdd01,
                keywords = listOf("new", "chat", "新建", "对话"),
                onClick = { onNewConversation() },
            ),
            NativePaletteEntry(
                id = "action-settings",
                title = nativeText(language, "打开设置", "Open settings"),
                icon = HugeIcons.Settings03,
                keywords = listOf("settings", "设置"),
                onClick = { onOpenSettings() },
            ),
            NativePaletteEntry(
                id = "action-theme",
                title = nativeText(language, "切换主题", "Toggle theme"),
                icon = HugeIcons.DarkMode,
                keywords = listOf("theme", "dark", "light", "主题", "深色"),
                onClick = { onToggleTheme() },
            ),
            NativePaletteEntry(
                id = "action-search",
                title = nativeText(language, "搜索所有对话内容", "Search all conversations"),
                icon = HugeIcons.Search01,
                keywords = listOf("search", "full", "text", "搜索", "全文"),
                onClick = { onOpenFullTextSearch() },
            ),
        )
    }
    val conversationEntries = remember(conversations, onResumeConversation) {
        conversations.map { conversation ->
            NativePaletteEntry(
                id = "conv-" + conversation.threadId,
                title = conversation.title,
                subtitle = conversation.projectName,
                keywords = listOf(conversation.projectPath, conversation.threadId),
                onClick = { onResumeConversation(conversation.threadId) },
            )
        }
    }
    val filteredActions = actions.filter { it.matches(query) }
    val filteredConversations = conversationEntries.filter { it.matches(query) }
    LaunchedEffect(Unit) { query = "" }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(indication = null, interactionSource = null) { onDismiss() },
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 28.dp)
                    .heightIn(max = 600.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 18.dp,
            ) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(HugeIcons.Search01, null, Modifier.size(18.dp)) },
                        placeholder = { Text(nativeText(language, "搜索对话或输入命令…", "Search conversations or type a command…")) },
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (filteredActions.isNotEmpty()) {
                            item(key = "header-actions") {
                                Text(
                                    nativeText(language, "操作", "Actions"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            items(filteredActions, key = { it.id }) { entry ->
                                PaletteRow(entry, currentThreadId, onDismiss)
                            }
                        }
                        if (filteredActions.isNotEmpty() && filteredConversations.isNotEmpty()) {
                            item(key = "divider") {
                                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                        if (filteredConversations.isNotEmpty()) {
                            item(key = "header-convs") {
                                Text(
                                    nativeText(language, "对话", "Conversations"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            items(filteredConversations, key = { it.id }) { entry ->
                                PaletteRow(entry, currentThreadId, onDismiss)
                            }
                        }
                        if (filteredActions.isEmpty() && filteredConversations.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    nativeText(language, "没有匹配结果", "No matches"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(entry: NativePaletteEntry, currentThreadId: String, onDismiss: () -> Unit) {
    val selected = entry.id == "conv-$currentThreadId"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fcodePressClickable(onClickLabel = entry.title) {
                onDismiss()
                entry.onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.icon != null) {
            Icon(
                entry.icon,
                null,
                Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    entry.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(HugeIcons.ArrowRight01, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}
