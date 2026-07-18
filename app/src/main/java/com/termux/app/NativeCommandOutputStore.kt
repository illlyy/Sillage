package com.termux.app

import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.UUID

/**
 * Keeps large command streams out of Compose snapshot state.
 *
 * App-server notifications are decoded on the bridge reader thread. Completed command items
 * are compacted there and only a small metadata object crosses to the main thread. The full
 * output stays in this bounded LRU until a command card is explicitly expanded.
 */
object NativeCommandOutputStore {
    const val OUTPUT_REF = "_nativeOutputRef"
    const val STDERR_REF = "_nativeStderrRef"
    const val OUTPUT_CHARS = "_nativeOutputChars"
    const val STDERR_CHARS = "_nativeStderrChars"
    const val OUTPUT_PREVIEW = "_nativeOutputPreview"
    const val STDERR_PREVIEW = "_nativeStderrPreview"

    private const val MAX_PREVIEW_CHARS = 768
    private const val MAX_CACHE_CHARS = 8 * 1024 * 1024
    private const val MAX_ENTRIES = 64
    private val heavyFields = setOf("aggregatedOutput", "output", "stdout", "stderr")
    private val values = LinkedHashMap<String, String>(16, 0.75f, true)
    private var cachedChars = 0

    /** Compact a completed item. Safe to call again on an already compacted item. */
    @JvmStatic
    fun compactCommandItem(item: JSONObject?): String = compactCommandItem(item, "")

    @JvmStatic
    fun compactCommandItem(item: JSONObject?, fallbackOutput: String): String {
        val source = item ?: JSONObject()
        val compact = JSONObject()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in heavyFields) compact.put(key, source.opt(key))
        }
        compact.put("type", "commandExecution")
        if (compact.optString("status").isBlank()) compact.put("status", "completed")

        // A payload emitted by the bridge is already compact. Preserve its references rather
        // than duplicating the stream when NativeChatState merges the live fallback buffer.
        val existingOutputRef = source.optString(OUTPUT_REF)
        val existingStderrRef = source.optString(STDERR_REF)
        if (existingOutputRef.isNotBlank() || existingStderrRef.isNotBlank()) {
            copyCompactOutputMetadata(source, compact)
            return compact.toString()
        }

        val stdout = source.optString("stdout", "")
        val stderr = source.optString("stderr", "")
        val aggregate = source.optString("aggregatedOutput", source.optString("output", ""))
        val output = when {
            stdout.isNotEmpty() -> stdout
            stderr.isNotEmpty() -> ""
            aggregate.isNotEmpty() -> aggregate
            else -> fallbackOutput
        }
        attachOutput(compact, output, error = false)
        attachOutput(compact, stderr, error = true)
        return compact.toString()
    }

    /** A bounded tail used while a command is still running. */
    @JvmStatic
    fun livePreview(source: CharSequence): String {
        if (source.length <= MAX_PREVIEW_CHARS * 2) return source.toString()
        val omitted = source.length - (MAX_PREVIEW_CHARS * 2)
        return buildString(MAX_PREVIEW_CHARS * 2 + 48) {
            append("\u2026 ").append(omitted).append(" chars omitted \u2026\n")
            append(source, source.length - (MAX_PREVIEW_CHARS * 2), source.length)
        }
    }

    @JvmStatic
    @Synchronized
    fun get(reference: String): String? = if (reference.isBlank()) null else values[reference]

    @JvmStatic
    @Synchronized
    fun clear() {
        values.clear()
        cachedChars = 0
    }

    @JvmStatic
    @Synchronized
    fun cachedEntryCount(): Int = values.size

    @JvmStatic
    @Synchronized
    fun cachedCharacterCount(): Int = cachedChars

    private fun copyCompactOutputMetadata(source: JSONObject, target: JSONObject) {
        listOf(OUTPUT_REF, STDERR_REF, OUTPUT_CHARS, STDERR_CHARS, OUTPUT_PREVIEW, STDERR_PREVIEW).forEach { key ->
            if (source.has(key)) target.put(key, source.opt(key))
        }
    }

    private fun attachOutput(target: JSONObject, value: String, error: Boolean) {
        if (value.isEmpty()) return
        val reference = UUID.randomUUID().toString()
        put(reference, value)
        target.put(if (error) STDERR_REF else OUTPUT_REF, reference)
        target.put(if (error) STDERR_CHARS else OUTPUT_CHARS, value.length)
        target.put(if (error) STDERR_PREVIEW else OUTPUT_PREVIEW, preview(value))
    }

    @Synchronized
    private fun put(reference: String, value: String) {
        values.remove(reference)?.let { cachedChars -= it.length }
        values[reference] = value
        cachedChars += value.length
        while (values.size > MAX_ENTRIES || cachedChars > MAX_CACHE_CHARS) {
            val eldest = values.entries.iterator()
            if (!eldest.hasNext()) break
            val entry = eldest.next()
            cachedChars -= entry.value.length
            eldest.remove()
        }
    }

    private fun preview(value: String): String {
        if (value.length <= MAX_PREVIEW_CHARS) return value
        val head = MAX_PREVIEW_CHARS * 2 / 3
        val tail = MAX_PREVIEW_CHARS - head
        val omitted = value.length - head - tail
        return buildString(MAX_PREVIEW_CHARS + 48) {
            append(value, 0, head)
            append("\n\u2026 ").append(omitted).append(" chars omitted \u2026\n")
            append(value, value.length - tail, value.length)
        }
    }
}
