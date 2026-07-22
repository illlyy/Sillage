package com.termux.app

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single changed file extracted from a Codex `fileChange` tool item.
 *
 * The app-server emits `fileChange` items whose `changes` array carries one entry per file with a
 * real unified diff. That array is stripped by [NativeLargePayloadStore.compactToolItem] (it is a
 * heavy key) and stored in the payload store, so the UI loads it lazily and parses it here.
 */
@Immutable
data class NativeFileChangeEntry(
    val path: String,
    val operation: String,
    val unifiedDiff: String,
    val fullContent: String,
    val movePath: String = "",
) {
    val fileName: String get() = path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }
    val hasDiff: Boolean get() = unifiedDiff.isNotBlank()
}

/**
 * Parses Codex `fileChange` tool items into per-file [NativeFileChangeEntry] records.
 *
 * Two field conventions are tolerated because app-server versions differ:
 *  - `type` / `unified_diff` / `content` / `move_path`
 *  - `kind` / `diff`
 */
object NativeFileChangeParser {

    private val PATH_KEYS = arrayOf("path", "filePath", "file", "name")

    /** Lightweight per-file summary (path + operation) for collapsed headers, no diff loading. */
    fun summarize(items: List<JSONObject>): List<NativeFileChangeEntry> {
        val result = linkedMapOf<String, NativeFileChangeEntry>()
        for (item in items) {
            val summary = item.optJSONArray("fileSummary")
            if (summary != null) {
                for (index in 0 until summary.length()) {
                    val entry = summary.optJSONObject(index) ?: continue
                    val path = entry.optString("path", "").trim()
                    if (path.isBlank()) continue
                    val op = entry.optString("op", "update").lowercase().ifBlank { "update" }
                    result[path] = NativeFileChangeEntry(path, op, "", "", "")
                }
                continue
            }
            parse(item).forEach { entry -> if (entry.path.isNotBlank()) result[entry.path] = entry }
        }
        return result.values.toList()
    }

    /** Parse inline `changes` from an item. Returns empty when only a payload reference exists. */
    fun parse(item: JSONObject): List<NativeFileChangeEntry> {
        val array = item.optJSONArray("changes")
        if (array != null) return parseChangesArray(array)
        val text = item.optString("changes")
        if (text.isNotBlank()) return parseChangesText(text)
        return emptyList()
    }

    /** Parse a payload loaded from [NativeLargePayloadStore] (the serialized `changes`). */
    fun parsePayload(raw: String): List<NativeFileChangeEntry> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            val parsed = runCatching { parseChangesArray(JSONArray(trimmed)) }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }
        if (trimmed.startsWith("{")) {
            val parsed = runCatching { listOf(parseChangeObject(JSONObject(trimmed))) }.getOrNull()
            if (parsed != null && parsed.first().path.isNotBlank()) return parsed
        }
        return parseChangesText(trimmed)
    }

    /** Merge lazy-loaded entries (with diffs) over the lightweight summary (path + op). */
    fun mergeForDisplay(summary: List<NativeFileChangeEntry>, loaded: List<NativeFileChangeEntry>?): List<NativeFileChangeEntry> {
        if (loaded.isNullOrEmpty()) return summary
        val result = linkedMapOf<String, NativeFileChangeEntry>()
        loaded.forEach { if (it.path.isNotBlank()) result[it.path] = it }
        summary.forEach { if (it.path.isNotBlank() && !result.containsKey(it.path)) result[it.path] = it }
        return result.values.toList()
    }

    fun synthesizeAddDiff(path: String, content: String): String {
        val lines = content.split("\n")
        return buildString {
            append("diff --git a/").append(path).append(" b/").append(path).append('\n')
            append("--- /dev/null\n")
            append("+++ b/").append(path).append('\n')
            append("@@ -0,0 +1,").append(lines.size).append(" @@\n")
            for (line in lines) append('+').append(line).append('\n')
        }
    }

    fun synthesizeDeleteDiff(path: String, content: String): String {
        val lines = content.split("\n")
        return buildString {
            append("diff --git a/").append(path).append(" b/").append(path).append('\n')
            append("--- a/").append(path).append('\n')
            append("+++ /dev/null\n")
            append("@@ -1,").append(lines.size).append(" +0,0 @@\n")
            for (line in lines) append('-').append(line).append('\n')
        }
    }

    private fun parseChangesArray(array: JSONArray): List<NativeFileChangeEntry> {
        val result = ArrayList<NativeFileChangeEntry>(array.length())
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val entry = parseChangeObject(obj)
            if (entry.path.isNotBlank()) result.add(entry)
        }
        return result
    }

    private fun parseChangeObject(obj: JSONObject): NativeFileChangeEntry {
        val path = firstNonBlank(obj, *PATH_KEYS).trim().trim('"', '\'', '`').removePrefix("a/").removePrefix("b/")
        val operation = firstNonBlank(obj, "type", "kind").lowercase().ifBlank { "update" }
        val movePath = firstNonBlank(obj, "move_path", "movePath")
        val diff = firstNonBlank(obj, "unified_diff", "unifiedDiff", "diff")
        val content = firstNonBlank(obj, "content")
        val unifiedDiff = when {
            diff.isNotBlank() -> normalizeDiff(path, movePath, diff)
            operation == "add" && content.isNotBlank() -> synthesizeAddDiff(path, content)
            operation == "delete" && content.isNotBlank() -> synthesizeDeleteDiff(path, content)
            else -> ""
        }
        return NativeFileChangeEntry(path, operation, unifiedDiff, content, movePath)
    }

    private fun normalizeDiff(path: String, movePath: String, diff: String): String {
        val trimmed = diff.trimStart('\n')
        val target = movePath.ifBlank { path }
        val hasFileHeader = Regex("""(^|\n)---\s""").containsMatchIn(trimmed)
        val hasGitHeader = Regex("""^diff --git """, RegexOption.MULTILINE).containsMatchIn(trimmed)
        val body = if (hasFileHeader) trimmed else "--- a/$path\n+++ b/$target\n$trimmed"
        return if (hasGitHeader) body else "diff --git a/$path b/$target\n$body"
    }

    private fun parseChangesText(text: String): List<NativeFileChangeEntry> {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            runCatching { return parseChangesArray(JSONArray(trimmed)) }
        }
        if (!looksLikeDiff(trimmed)) return emptyList()
        return splitUnifiedDiff(trimmed)
    }

    private fun looksLikeDiff(text: String): Boolean =
        text.contains("diff --git") || (text.contains("--- ") && text.contains("+++ "))

    private fun splitUnifiedDiff(text: String): List<NativeFileChangeEntry> {
        val chunks = ArrayList<String>()
        val current = StringBuilder()
        for (line in text.split("\n")) {
            if (line.startsWith("diff --git ") && current.isNotEmpty()) {
                chunks.add(current.toString())
                current.setLength(0)
            }
            current.append(line).append('\n')
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks.mapNotNull { chunk ->
            val path = extractDiffPath(chunk) ?: return@mapNotNull null
            NativeFileChangeEntry(path, "update", chunk.trimEnd(), "")
        }
    }

    private fun extractDiffPath(chunk: String): String? {
        Regex("""diff --git a/(.+?) b/(.+)""").find(chunk)?.let { return it.groupValues[2].trim() }
        Regex("""(?m)^\+\+\+ (?:b/)?(.+)$""").find(chunk)?.let { return it.groupValues[1].trim().substringBefore('\t') }
        return null
    }

    private fun firstNonBlank(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = obj.opt(key) ?: continue
            if (value == JSONObject.NULL) continue
            val text = when (value) {
                is String -> value
                is JSONObject, is JSONArray -> value.toString()
                else -> value.toString()
            }
            if (text.isNotBlank()) return text
        }
        return ""
    }
}
