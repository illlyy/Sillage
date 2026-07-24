package com.termux.app

import org.json.JSONObject

/** File change distilled from one or more Codex tool events for work-panel presentation. */
internal data class ChangedFileEntry(val path: String, val operation: String)

/** Parsed tool payloads shared by the message and work-panel presentation layers. */
internal data class ParsedToolDetails(
    val commands: List<JSONObject>,
    val fileChanges: List<JSONObject>,
    val images: List<JSONObject>,
)

private val CODEX_PATCH_FILE_REGEX = Regex(
    "(?m)^\\*\\*\\*\\s+(Update|Add|Delete) File:\\s*(.+)$",
    RegexOption.IGNORE_CASE,
)
private val UNIFIED_DIFF_FILE_REGEX = Regex("(?m)^(?:\\+\\+\\+|---)\\s+(?:[ab]/)?(.+)$")

/** Parses each immutable tool payload once and classifies it for all UI consumers. */
internal fun parseToolDetails(rawItems: List<String>): ParsedToolDetails {
    val commands = ArrayList<JSONObject>()
    val fileChanges = ArrayList<JSONObject>()
    val images = ArrayList<JSONObject>()
    rawItems.forEach { raw ->
        val item = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
        when {
            item.optString("type") == "commandExecution" -> commands.add(item)
            item.optString("type") == "fileChange" -> fileChanges.add(item)
            isImageToolItem(item.optString("type")) -> images.add(item)
        }
    }
    return ParsedToolDetails(commands, fileChanges, images)
}

internal fun associatedFileChangeItems(
    messages: List<NativeChatMessage>,
    assistantId: String,
): List<JSONObject> {
    val assistantIndex = messages.indexOfFirst { it.id == assistantId }
    if (assistantIndex <= 0) return emptyList()
    val result = mutableListOf<JSONObject>()
    var index = assistantIndex - 1
    while (index >= 0 && messages[index].role == NativeChatRole.ACTIVITY) {
        val content = messages[index].content
        if (content.startsWith("PROCESS2|")) {
            val payload = decodeProcessPayload(content)
            val tools = payload?.optJSONArray("tools")
            if (tools != null) for (toolIndex in 0 until tools.length()) {
                val item = tools.optJSONObject(toolIndex)
                    ?: runCatching { JSONObject(tools.optString(toolIndex)) }.getOrNull()
                    ?: continue
                if (item.optString("type") == "fileChange") result.add(item)
            }
        }
        index--
    }
    return result
}

internal fun collectAllFileChangeItems(
    messages: List<NativeChatMessage>,
    toolDetails: List<String>,
): List<JSONObject> {
    val result = mutableListOf<JSONObject>()
    messages.forEach { message ->
        if (message.role != NativeChatRole.ACTIVITY || !message.content.startsWith("PROCESS2|")) return@forEach
        val payload = decodeProcessPayload(message.content) ?: return@forEach
        val tools = payload.optJSONArray("tools") ?: return@forEach
        for (index in 0 until tools.length()) {
            val item = tools.optJSONObject(index)
                ?: runCatching { JSONObject(tools.optString(index)) }.getOrNull()
                ?: continue
            if (item.optString("type") == "fileChange") result.add(item)
        }
    }
    toolDetails.forEach { raw ->
        runCatching { JSONObject(raw) }.getOrNull()
            ?.takeIf { it.optString("type") == "fileChange" }
            ?.let(result::add)
    }
    return result.distinctBy { it.optString("id", it.optString("itemId", it.toString())) }
}

internal fun extractChangedFiles(items: List<JSONObject>): List<ChangedFileEntry> {
    val entries = linkedMapOf<String, ChangedFileEntry>()
    fun add(pathValue: String, operation: String) {
        val path = pathValue.trim().trim('"', '\'', '`').removePrefix("a/").removePrefix("b/")
        if (path.isBlank() || path == "/dev/null") return
        entries[path] = ChangedFileEntry(path, operation)
    }
    items.forEach { item ->
        val detail = item.optString("changes")
            .ifBlank { item.optString(NativeLargePayloadStore.PAYLOAD_PREVIEW) }
        listOf("path", "filePath", "file", "name").forEach { key ->
            item.optString(key).takeIf(String::isNotBlank)?.let { add(it, "edit") }
        }
        val changes = item.optJSONArray("changes")
        if (changes != null) for (index in 0 until changes.length()) {
            val change = changes.optJSONObject(index) ?: continue
            val path = listOf("path", "filePath", "file", "name")
                .firstNotNullOfOrNull { key -> change.optString(key).takeIf(String::isNotBlank) }
            if (path != null) add(path, change.optString("type", "edit").lowercase())
        }
        CODEX_PATCH_FILE_REGEX.findAll(detail).forEach { match ->
            add(match.groupValues[2], match.groupValues[1].lowercase())
        }
        UNIFIED_DIFF_FILE_REGEX.findAll(detail).forEach { match ->
            add(match.groupValues[1], "edit")
        }
    }
    return entries.values.toList()
}

internal fun isImageToolItem(type: String): Boolean = when (type) {
    "imageView", "view_image", "image" -> true
    else -> false
}

private fun decodeProcessPayload(content: String): JSONObject? = runCatching {
    JSONObject(String(NativeBase64.decode(content.substringAfter('|')), Charsets.UTF_8))
}.getOrNull()
