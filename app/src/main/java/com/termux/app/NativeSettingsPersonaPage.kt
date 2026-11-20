package com.termux.app

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Star

/**
 * Persona library + memory bank.
 *
 * Interaction rules:
 * - Picking a persona is a single tap on its row; there is no separate "apply" dialog to confirm.
 * - Destructive actions (deleting a persona or a memory) always ask first and name the item.
 * - The memory section explains the cost model in place, because "why isn't everything sent to
 *   the model" is the question every user asks the first time they see an index instead of bodies.
 */
@Composable
internal fun PersonaSettingsPage(
    lang: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Subscribe to both stores so edits made in dialogs re-render the page immediately.
    @Suppress("UNUSED_EXPRESSION") NativePersonaStore.revision
    @Suppress("UNUSED_EXPRESSION") NativeMemoryStore.revision

    var personas by remember { mutableStateOf(NativePersonaStore.load(context)) }
    var activeId by remember { mutableStateOf(NativePersonaStore.activeId(NativePersonaStore.prefs(context))) }
    var editing by remember { mutableStateOf<NativePersona?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deletingPersona by remember { mutableStateOf<NativePersona?>(null) }
    var memories by remember { mutableStateOf(NativeMemoryStore.load(context)) }
    var editingMemory by remember { mutableStateOf<NativeMemory?>(null) }
    var addingMemory by remember { mutableStateOf(false) }
    var deletingMemory by remember { mutableStateOf<NativeMemory?>(null) }

    fun reload() {
        personas = NativePersonaStore.load(context)
        activeId = NativePersonaStore.activeId(NativePersonaStore.prefs(context))
        memories = NativeMemoryStore.load(context)
    }

    val active = personas.firstOrNull { it.id == activeId } ?: personas.firstOrNull()
    val core = memories.filter { it.tier == NativeMemoryTier.CORE }
    val normal = memories.filter { it.tier == NativeMemoryTier.NORMAL }

    SettingsScaffold(
        title = tr(lang, "人物与记忆", "Persona & memory"),
        subtitle = tr(
            lang,
            "名字、头像、口吻，以及 AI 可以自己检索的长期记忆",
            "Name, avatar, tone, and long-term memory the assistant can search itself",
        ),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
        ) {
            item {
                active?.let { persona ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(persona.emoji.ifBlank { "✦" }, style = MaterialTheme.typography.headlineSmall)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        persona.name.ifBlank { tr(lang, "未命名", "Unnamed") },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        tr(lang, "正在使用", "In use"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                OutlinedButton(onClick = { editing = persona }) { Text(tr(lang, "编辑", "Edit")) }
                            }
                            if (persona.greeting.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    persona.greeting,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (persona.tone.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    tr(lang, "口吻：", "Tone: ") + persona.tone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item { SettingsSection(tr(lang, "人物库", "Personas")) }
            items(personas, key = { it.id }) { persona ->
                PersonaRow(
                    persona = persona,
                    selected = persona.id == activeId,
                    onSelect = {
                        NativePersonaStore.setActive(context, persona.id)
                        reload()
                    },
                    onEdit = { editing = persona },
                    onDelete = if (persona.builtin) null else ({ deletingPersona = persona }),
                )
            }
            item {
                Surface(
                    onClick = { creating = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(HugeIcons.Add01, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(tr(lang, "新建人物", "New persona"), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            item {
                Text(
                    tr(
                        lang,
                        "记忆分成两层：核心记忆每次对话都会带上（保持简短）；其余记忆只发送标题索引，AI 需要时用 fcode-memory recall 自行查询。这样记忆再多也不会把上下文塞满。",
                        "Memory has two tiers: core memories are always included (keep them short); everything else is only a title index, and the assistant calls fcode-memory recall when it needs details. That keeps a growing store from flooding the context.",
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { SettingsSection(tr(lang, "核心记忆", "Core memories")) }
            if (core.isEmpty()) {
                item {
                    Text(
                        tr(
                            lang,
                            "还没有核心记忆。适合放：怎么称呼你、常用技术栈、沟通偏好。",
                            "No core memories yet. Good candidates: how to address you, your stack, communication preferences.",
                        ),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(core, key = { it.id }) { memory ->
                    MemoryRow(
                        lang = lang,
                        memory = memory,
                        onEdit = { editingMemory = memory },
                        onDelete = { deletingMemory = memory },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    FilledTonalButton(
                        onClick = { addingMemory = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(tr(lang, "添加记忆", "Add memory"))
                    }
                }
            }

            if (normal.isNotEmpty()) {
                item { SettingsSection(tr(lang, "其他记忆（只注入标题）", "Other memories (index only)")) }
                items(normal, key = { it.id }) { memory ->
                    MemoryRow(
                        lang = lang,
                        memory = memory,
                        onEdit = { editingMemory = memory },
                        onDelete = { deletingMemory = memory },
                    )
                }
            }

            item {
                TextButton(
                    onClick = {
                        NativePersonaStore.resetToBuiltins(context)
                        reload()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) { Text(tr(lang, "恢复内置人物", "Restore built-in personas")) }
            }
        }
    }

    if (creating || editing != null) {
        val target = editing
        PersonaEditorDialog(
            lang = lang,
            persona = target ?: NativePersona(
                id = "",
                name = "",
                emoji = "✦",
                greeting = "",
                tone = "",
                prompt = "",
            ),
            title = if (target == null) tr(lang, "新建人物", "New persona") else tr(lang, "编辑人物", "Edit persona"),
            onDismiss = { creating = false; editing = null },
            onSave = { draft ->
                if (target == null) {
                    val created = NativePersonaStore.create(context, draft.name.ifBlank { tr(lang, "新人物", "New persona") }, draft.emoji)
                    NativePersonaStore.upsert(context, draft.copy(id = created.id, builtin = false))
                } else {
                    NativePersonaStore.upsert(context, draft)
                }
                creating = false
                editing = null
                reload()
            },
        )
    }

    deletingPersona?.let { persona ->
        AlertDialog(
            onDismissRequest = { deletingPersona = null },
            title = { Text(tr(lang, "删除人物", "Delete persona")) },
            text = { Text(tr(lang, "确定删除「${persona.name}」吗？", "Delete “${persona.name}”?")) },
            dismissButton = { TextButton(onClick = { deletingPersona = null }) { Text(tr(lang, "取消", "Cancel")) } },
            confirmButton = {
                TextButton(onClick = {
                    NativePersonaStore.delete(context, persona.id)
                    deletingPersona = null
                    reload()
                }) { Text(tr(lang, "删除", "Delete")) }
            },
        )
    }

    if (addingMemory || editingMemory != null) {
        val target = editingMemory
        MemoryEditorDialog(
            lang = lang,
            memory = target,
            onDismiss = { editingMemory = null; addingMemory = false },
            onSave = { draft ->
                if (draft.id.isBlank()) {
                    NativeMemoryStore.append(
                        context,
                        title = draft.title,
                        body = draft.body,
                        tags = draft.tags,
                        core = draft.tier == NativeMemoryTier.CORE,
                    )
                } else {
                    NativeMemoryStore.update(context, draft)
                }
                editingMemory = null
                addingMemory = false
                reload()
            },
        )
    }

    deletingMemory?.let { memory ->
        AlertDialog(
            onDismissRequest = { deletingMemory = null },
            title = { Text(tr(lang, "删除记忆", "Delete memory")) },
            text = { Text(tr(lang, "确定删除「${memory.title}」吗？删除后 AI 无法再检索到它。", "Delete “${memory.title}”? The assistant will no longer be able to recall it.")) },
            dismissButton = { TextButton(onClick = { deletingMemory = null }) { Text(tr(lang, "取消", "Cancel")) } },
            confirmButton = {
                TextButton(onClick = {
                    NativeMemoryStore.delete(context, memory.id)
                    deletingMemory = null
                    reload()
                }) { Text(tr(lang, "删除", "Delete")) }
            },
        )
    }
}

@Composable
private fun PersonaRow(
    persona: NativePersona,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(persona.emoji.ifBlank { "✦" }, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(persona.name.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (persona.tone.isNotBlank()) {
                    Text(
                        persona.tone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onEdit) { Text("✎") }
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Icon(
                        HugeIcons.Delete02,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryRow(
    lang: String,
    memory: NativeMemory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (memory.tier == NativeMemoryTier.CORE) {
                Icon(
                    HugeIcons.Star,
                    contentDescription = tr(lang, "核心记忆", "Core memory"),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(memory.title.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    memory.dateLabel.take(10) + if (memory.tags.isEmpty()) "" else " · " + memory.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) {
                Icon(
                    HugeIcons.Delete02,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonaEditorDialog(
    lang: String,
    persona: NativePersona,
    title: String,
    onDismiss: () -> Unit,
    onSave: (NativePersona) -> Unit,
) {
    var name by remember { mutableStateOf(persona.name) }
    var emoji by remember { mutableStateOf(persona.emoji) }
    var greeting by remember { mutableStateOf(persona.greeting) }
    var tone by remember { mutableStateOf(persona.tone) }
    var prompt by remember { mutableStateOf(persona.prompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(2) },
                        label = { Text(tr(lang, "头像", "Avatar")) },
                        singleLine = true,
                        modifier = Modifier.width(96.dp),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(tr(lang, "名字", "Name")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = greeting,
                    onValueChange = { greeting = it },
                    label = { Text(tr(lang, "问候语", "Greeting")) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tone,
                    onValueChange = { tone = it },
                    label = { Text(tr(lang, "口吻 / 语气", "Tone")) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(tr(lang, "补充设定", "Extra instructions")) },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(lang, "取消", "Cancel")) } },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(persona.copy(name = name.trim(), emoji = emoji.ifBlank { "✦" }, greeting = greeting.trim(), tone = tone.trim(), prompt = prompt.trim()))
                },
            ) { Text(tr(lang, "保存", "Save")) }
        },
    )
}

@Composable
private fun MemoryEditorDialog(
    lang: String,
    memory: NativeMemory?,
    onDismiss: () -> Unit,
    onSave: (NativeMemory) -> Unit,
) {
    val editing = memory != null && memory.id.isNotBlank()
    var title by remember { mutableStateOf(memory?.title.orEmpty()) }
    var body by remember { mutableStateOf(memory?.body.orEmpty()) }
    var tags by remember { mutableStateOf(memory?.tags?.joinToString(", ").orEmpty()) }
    var core by remember { mutableStateOf(memory?.tier == NativeMemoryTier.CORE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) tr(lang, "编辑记忆", "Edit memory") else tr(lang, "添加记忆", "Add memory")) },
        text = {
            Column(Modifier.height(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(tr(lang, "标题（用于索引）", "Title (used in the index)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(tr(lang, "标签，逗号分隔", "Tags, comma separated")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(tr(lang, "内容", "Content")) },
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = core, onCheckedChange = { core = it })
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tr(lang, "核心记忆（每次对话都带上）", "Core memory (always included)"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(lang, "取消", "Cancel")) } },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val tier = if (core) NativeMemoryTier.CORE else NativeMemoryTier.NORMAL
                    val tagList = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    val base = memory ?: NativeMemory(
                        id = "",
                        tier = tier,
                        createdAt = System.currentTimeMillis(),
                        dateLabel = NativeMemoryStore.dateLabel(System.currentTimeMillis()),
                        tags = tagList,
                        title = title.trim(),
                        body = body.trim(),
                    )
                    onSave(base.copy(title = title.trim(), body = body.trim(), tags = tagList, tier = tier))
                },
            ) { Text(tr(lang, "保存", "Save")) }
        },
    )
}
