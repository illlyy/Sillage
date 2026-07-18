package com.termux.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.UUID

/** Keeps non-command tool payloads and subagent histories outside Compose snapshot state. */
object NativeLargePayloadStore {
    const val PAYLOAD_REF = "_nativePayloadRef"
    const val PAYLOAD_CHARS = "_nativePayloadChars"
    const val PAYLOAD_PREVIEW = "_nativePayloadPreview"
    const val PAYLOAD_HASH = "_nativePayloadHash"
    const val MESSAGE_COUNT = "_nativeMessageCount"

    private const val MAX_PREVIEW_CHARS = 768
    private const val MAX_CACHE_CHARS = 16 * 1024 * 1024
    private const val MAX_ENTRIES = 96
    private const val MAX_INLINE_METADATA_CHARS = 2_048
    private val heavyKeys = setOf(
        "aggregatedOutput", "stdout", "stderr", "output", "result", "content",
        "changes", "detail", "arguments", "input",
    )

    private data class Entry(
        val text: String,
        val messages: List<String>?,
        val characterCount: Int,
    )

    data class SubagentPage(
        val messages: List<String>,
        val startIndex: Int,
        val totalCount: Int,
    )

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val slotReferences = HashMap<String, String>()
    private var cachedChars = 0

    @JvmStatic
    fun compactToolItem(item: JSONObject?): String {
        val source = item ?: JSONObject()
        if (source.optString(PAYLOAD_REF).isNotBlank()) return source.toString()
        val type = source.optString("type")
        if (type !in setOf("fileChange", "mcpToolCall", "webSearch", "collabAgentToolCall", "subAgentActivity")) {
            return source.toString()
        }

        val compact = JSONObject()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in heavyKeys || key.startsWith("_native")) continue
            val value = source.opt(key)
            when (value) {
                null -> Unit
                is String -> {
                    if (value.length <= MAX_INLINE_METADATA_CHARS) compact.put(key, value)
                    else compact.put(key + "Preview", preview(value))
                }
                is JSONObject, is JSONArray -> {
                    val serialized = value.toString()
                    if (serialized.length <= MAX_INLINE_METADATA_CHARS) compact.put(key, value)
                }
                else -> compact.put(key, value)
            }
        }
        if (compact.optString("type").isBlank()) compact.put("type", type)
        if (compact.optString("status").isBlank()) compact.put("status", "completed")

        val detail = toolDetail(source)
        val toolSlot = source.optString("id").ifBlank { UUID.randomUUID().toString() }
        if (detail.isNotBlank()) attachPayload(compact, detail, "tool:$toolSlot:$type")
        return compact.toString()
    }

    @JvmStatic
    fun compactSubagentHistoryResult(result: JSONObject?): String {
        val source = result ?: JSONObject()
        val messages = source.optJSONArray("messages") ?: JSONArray()
        val serializedMessages = ArrayList<String>(messages.length())
        var chars = 0
        for (index in 0 until messages.length()) {
            val value = messages.opt(index)
            val serialized = when (value) {
                is JSONObject -> {
                    val message = JSONObject(value.toString())
                    if (message.optString("role") == "activity") {
                        val content = message.optString("content")
                        if (content.startsWith("PROCESS2|")) {
                            message.put("content", NativeHistoryParser.compactProcessContent(content))
                        }
                    }
                    message.toString()
                }
                is JSONArray -> value.toString()
                null, JSONObject.NULL -> continue
                else -> value.toString()
            }
            serializedMessages.add(serialized)
            chars += serialized.length
        }
        val compact = JSONObject()
        val resultKeys = source.keys()
        while (resultKeys.hasNext()) {
            val key = resultKeys.next()
            if (key != "messages") compact.put(key, source.opt(key))
        }
        val threadId = source.optString("threadId")
        val reference = putEntry(
            text = "",
            messages = serializedMessages,
            chars = chars,
            stableSlot = "subagent:$threadId",
        )
        compact.put(PAYLOAD_REF, reference)
        compact.put(PAYLOAD_CHARS, chars)
        compact.put(PAYLOAD_HASH, stableMessagesHash(serializedMessages))
        compact.put(MESSAGE_COUNT, serializedMessages.size)
        return compact.toString()
    }

    @JvmStatic
    @Synchronized
    fun get(reference: String): String? = entries[reference]?.text?.takeIf { it.isNotEmpty() }

    @JvmStatic
    @Synchronized
    fun contains(reference: String): Boolean = reference.isNotBlank() && entries.containsKey(reference)

    @JvmStatic
    @Synchronized
    fun subagentPage(reference: String, beforeExclusive: Int, limit: Int): SubagentPage {
        val messages = entries[reference]?.messages ?: return SubagentPage(emptyList(), 0, 0)
        val end = beforeExclusive.coerceIn(0, messages.size)
        val start = (end - limit.coerceIn(1, 200)).coerceAtLeast(0)
        return SubagentPage(messages.subList(start, end).toList(), start, messages.size)
    }

    @JvmStatic
    @Synchronized
    fun clear() {
        entries.clear()
        slotReferences.clear()
        cachedChars = 0
    }

    @JvmStatic
    @Synchronized
    fun cachedEntryCount(): Int = entries.size

    @JvmStatic
    @Synchronized
    fun cachedCharacterCount(): Int = cachedChars

    private fun toolDetail(source: JSONObject): String {
        val type = source.optString("type")
        return when (type) {
            "fileChange" -> firstText(source, "changes", "output", "detail", "result")
                .ifBlank { source.toString(2) }
            "mcpToolCall" -> buildString {
                val tool = source.optString("tool", source.optString("name", "MCP"))
                if (tool.isNotBlank()) append(tool)
                source.opt("arguments")?.takeUnless { it == JSONObject.NULL }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("Arguments\n").append(prettyValue(it))
                }
                firstText(source, "output", "result", "content", "detail").takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("Output\n").append(it)
                }
            }
            "webSearch" -> buildString {
                val query = source.optString("query")
                if (query.isNotBlank()) append(query)
                firstText(source, "output", "result", "content", "detail").takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append(it)
                }
            }
            "collabAgentToolCall", "subAgentActivity" ->
                firstText(source, "detail", "output", "result", "content")
            else -> source.toString(2)
        }
    }

    private fun firstText(source: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = source.opt(key) ?: continue
            if (value == JSONObject.NULL) continue
            val text = prettyValue(value)
            if (text.isNotBlank()) return text
        }
        return ""
    }

    private fun prettyValue(value: Any): String = when (value) {
        is JSONObject -> value.toString(2)
        is JSONArray -> value.toString(2)
        else -> value.toString()
    }

    private fun attachPayload(target: JSONObject, value: String, stableSlot: String) {
        val reference = putEntry(value, null, value.length, stableSlot)
        target.put(PAYLOAD_REF, reference)
        target.put(PAYLOAD_CHARS, value.length)
        target.put(PAYLOAD_PREVIEW, preview(value))
        target.put(PAYLOAD_HASH, value.hashCode())
    }

    @Synchronized
    private fun putEntry(
        text: String,
        messages: List<String>?,
        chars: Int,
        stableSlot: String,
    ): String {
        val previousReference = slotReferences[stableSlot]
        val previous = previousReference?.let(entries::get)
        if (previous != null && previous.text == text && previous.messages == messages) return previousReference

        if (previousReference != null) removeEntry(previousReference)
        val reference = stableSlot + ":" + chars + ":" + (text.hashCode() * 31 + (messages?.hashCode() ?: 0)) + ":" + UUID.randomUUID()
        entries[reference] = Entry(text, messages, chars)
        slotReferences[stableSlot] = reference
        cachedChars += chars
        trim()
        return reference
    }

    private fun removeEntry(reference: String) {
        val removed = entries.remove(reference) ?: return
        cachedChars -= removed.characterCount
        val slot = slotReferences.entries.firstOrNull { it.value == reference }?.key
        if (slot != null) slotReferences.remove(slot)
    }

    private fun trim() {
        while (entries.size > MAX_ENTRIES || cachedChars > MAX_CACHE_CHARS) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) break
            val entry = iterator.next()
            cachedChars -= entry.value.characterCount
            iterator.remove()
            val slot = slotReferences.entries.firstOrNull { it.value == entry.key }?.key
            if (slot != null) slotReferences.remove(slot)
        }
    }

    private fun preview(value: String): String {
        if (value.length <= MAX_PREVIEW_CHARS * 2) return value
        val omitted = value.length - MAX_PREVIEW_CHARS * 2
        return buildString(MAX_PREVIEW_CHARS * 2 + 48) {
            append(value, 0, MAX_PREVIEW_CHARS)
            append("\n... ").append(omitted).append(" chars omitted ...\n")
            append(value, value.length - MAX_PREVIEW_CHARS, value.length)
        }
    }

    private fun stableMessagesHash(messages: List<String>): Int {
        var result = 1
        messages.forEach { result = 31 * result + it.hashCode() }
        return result
    }
}
