package com.termux.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Classifies Claude Code tool calls into the activity-item types the shared timeline already
 * renders, and shapes their payloads so [NativeFileChangeParser] and the image card can consume
 * them.
 *
 * Why this exists: `ClaudeEventDecoder` used to funnel every unrecognised tool into a single
 * `type="tool"` event, so the timeline showed one generic "工具调用" row whose only content was an
 * opaque `call_...` id — the tool name, its arguments and any image it read were all invisible.
 * Codex already produced `fileChange` / `webSearch` / image items, so the fix is to translate
 * Claude's tool vocabulary into the same vocabulary instead of teaching the renderer a second one.
 *
 * Kept free of Android and Compose so it stays unit-testable (see `NativeClaudeToolMappingTest`).
 */
internal object NativeClaudeToolMapping {

    /** Activity item types understood by `NativeActivityReducer.completeTool`. */
    const val TYPE_FILE_CHANGE = "fileChange"
    const val TYPE_IMAGE = "image"
    const val TYPE_WEB_SEARCH = "webSearch"
    const val TYPE_TOOL = "tool"

    /** `Edit` hammering the same diff into the card would blow up the payload; above this we
     *  keep the file name and summary only (the full text is still in the tool result). */
    const val MAX_DIFF_LINES = 400

    /** Hard cap on a serialized diff; a minified single-line file can otherwise produce one
     *  enormous "line" that would be carried into the activity group. */
    const val MAX_DIFF_CHARS = 12_000

    private val IMAGE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif", "ico",
    )

    /**
     * Names the CLI uses for delegated work. `Task` was renamed to `Agent` in Claude Code 2.x,
     * which silently disabled the subagent card; keep both, and fall back to the input shape
     * because the name is the part that drifts between versions.
     */
    private val SUBAGENT_TOOLS = setOf("task", "task_simple", "agent", "subagent", "subagent_tool_use")

    fun isSubagentTool(toolName: String, input: JSONObject?): Boolean {
        if (toolName.lowercase() in SUBAGENT_TOOLS) return true
        return input?.has("subagent_type") == true || input?.has("subagentType") == true
    }

    /** Tools whose result is a file edit, and how to turn their input into a unified diff. */
    private val EDIT_TOOLS = setOf("write", "edit", "multiedit", "notebookedit")

    fun activityType(toolName: String, input: JSONObject?): String = when (toolName.lowercase()) {
        in EDIT_TOOLS -> TYPE_FILE_CHANGE
        "read" -> if (isImagePath(toolSubject(toolName, input))) TYPE_IMAGE else TYPE_TOOL
        "glob", "grep" -> TYPE_TOOL
        "websearch", "web_search", "webfetch", "web_fetch" -> TYPE_WEB_SEARCH
        else -> TYPE_TOOL
    }

    fun isImagePath(path: String): Boolean =
        path.isNotBlank() && path.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /**
     * The single argument worth showing next to the tool label: the file for read/write/edit, the
     * pattern for glob/grep, the URL for a fetch. Empty when the tool has no natural subject.
     */
    fun toolSubject(toolName: String, input: JSONObject?): String {
        if (input == null) return ""
        return when (toolName.lowercase()) {
            "read", "write", "edit", "multiedit" -> firstString(input, "file_path", "filePath", "path", "file")
            "notebookedit" -> firstString(input, "notebook_path", "notebookPath", "file_path")
            "glob", "grep" -> firstString(input, "pattern")
            "webfetch", "web_fetch" -> firstString(input, "url")
            "websearch", "web_search" -> firstString(input, "query")
            "bash", "shell" -> firstString(input, "command")
            else -> firstString(input)
        }
    }

    /** Localized label for a Claude tool name. Falls back to the raw name so an unknown tool is
     *  still identifiable rather than showing a generic "tool call". */
    fun toolLabel(toolName: String, language: String): String {
        val zh = language != "en"
        return when (toolName.lowercase()) {
            "read" -> if (zh) "读取文件" else "Read file"
            "write" -> if (zh) "写入文件" else "Write file"
            "edit", "multiedit" -> if (zh) "修改文件" else "Edit file"
            "notebookedit" -> if (zh) "修改 Notebook" else "Edit notebook"
            "glob" -> if (zh) "查找文件" else "Find files"
            "grep" -> if (zh) "搜索内容" else "Search contents"
            "websearch", "web_search" -> if (zh) "网页搜索" else "Web search"
            "webfetch", "web_fetch" -> if (zh) "抓取网页" else "Fetch page"
            "todowrite", "todoread" -> if (zh) "任务清单" else "Task list"
            "ls" -> if (zh) "列出目录" else "List directory"
            "bash", "shell" -> if (zh) "命令执行" else "Run command"
            "askuserquestion" -> if (zh) "询问用户" else "Ask user"
            "exitplanmode" -> if (zh) "提交计划" else "Submit plan"
            else -> toolName.ifBlank { if (zh) "工具调用" else "Tool call" }
        }
    }

    /** `"修改文件 · src/App.kt"` — the title the timeline row renders. */
    fun toolTitle(toolName: String, subject: String, language: String): String {
        val label = toolLabel(toolName, language)
        if (subject.isBlank() || subject == toolName) return label
        return "$label · ${shortenSubject(subject)}"
    }

    /**
     * Long absolute paths wrap the row onto three lines and push the label out of sight, so a dir
     * path is trimmed to its last couple of segments. The full path is still in the tool result and
     * in the diff card for anyone who needs it.
     */
    private fun shortenSubject(subject: String): String {
        if (subject.length <= 32 || !subject.contains('/')) return subject
        val parts = subject.split('/').filter { it.isNotBlank() }
        if (parts.size <= 2) return subject
        return "…/" + parts.takeLast(2).joinToString("/")
    }

    /**
     * Builds the `fileChange` payload consumed by [NativeFileChangeParser]: a `changes` array whose
     * entries carry either a real unified diff (`Edit`) or the whole content (`Write`), which the
     * parser turns into a synthesized add diff.
     *
     * Returns null when the input has no usable path, so the caller falls back to a generic row.
     */
    fun fileChangeItem(toolName: String, input: JSONObject?, toolUseId: String): JSONObject? {
        if (input == null) return null
        val path = toolSubject(toolName, input).trim().trim('"', '\'', '`')
        if (path.isBlank()) return null
        val changes = JSONArray()
        when (toolName.lowercase()) {
            "write" -> {
                val content = input.optString("content")
                if (content.isBlank()) return null
                changes.put(JSONObject().put("path", path).put("type", "add").put("content", content))
            }
            "edit" -> {
                editDiff(input.optString("old_string"), input.optString("new_string"), path)?.let { diff ->
                    changes.put(JSONObject().put("path", path).put("type", "update").put("unified_diff", diff))
                } ?: changes.put(JSONObject().put("path", path).put("type", "update"))
            }
            "multiedit" -> {
                val edits = input.optJSONArray("edits")
                val diff = buildString {
                    if (edits != null) for (index in 0 until edits.length()) {
                        val edit = edits.optJSONObject(index) ?: continue
                        editDiff(edit.optString("old_string"), edit.optString("new_string"), path)?.let {
                            if (isNotEmpty()) append('\n')
                            append(it)
                        }
                    }
                }
                if (diff.isBlank()) changes.put(JSONObject().put("path", path).put("type", "update"))
                else changes.put(JSONObject().put("path", path).put("type", "update").put("unified_diff", diff))
            }
            "notebookedit" -> {
                val content = input.optString("new_source")
                val operation = when (input.optString("edit_mode", "replace").lowercase()) {
                    "insert" -> "add"
                    "delete" -> "delete"
                    else -> "update"
                }
                changes.put(
                    JSONObject().put("path", path).put("type", operation)
                        .put(if (operation == "add") "content" else "unified_diff", content),
                )
            }
            else -> return null
        }
        if (changes.length() == 0) return null
        return JSONObject()
            .put("type", TYPE_FILE_CHANGE)
            .put("id", toolUseId)
            .put("changes", changes)
            .put("status", "completed")
    }

    /** `{ type: "image", path }` — the shape the inline image card reads. */
    fun imageItem(input: JSONObject?, toolUseId: String): JSONObject? {
        val path = input?.let { firstString(it, "file_path", "filePath", "path", "file") }.orEmpty()
        if (path.isBlank()) return null
        return JSONObject().put("type", TYPE_IMAGE).put("id", toolUseId).put("path", path)
    }

    /**
     * Unified diff for one string replacement. Only changed lines are emitted (the shared
     * [calculateDiff] is an LCS alignment that drops context), which is exactly the granularity a
     * user needs to confirm a single edit. Oversized diffs are dropped rather than serialized.
     */
    fun editDiff(oldText: String, newText: String, path: String): String? {
        if (oldText.isEmpty() && newText.isEmpty()) return null
        val lines = calculateDiff(oldText, newText)
        if (lines.isEmpty() || lines.size > MAX_DIFF_LINES) return null
        val removed = lines.count { it.type == NativeDiffLineType.REMOVED }
        val added = lines.count { it.type == NativeDiffLineType.ADDED }
        val diff = buildString {
            append("diff --git a/").append(path).append(" b/").append(path).append('\n')
            append("--- a/").append(path).append('\n')
            append("+++ b/").append(path).append('\n')
            append("@@ -1,").append(removed).append(" +1,").append(added).append(" @@\n")
            lines.forEach { line ->
                append(if (line.type == NativeDiffLineType.ADDED) '+' else '-')
                append(line.content).append('\n')
            }
        }
        return if (diff.length > MAX_DIFF_CHARS) null else diff
    }

    private fun firstString(input: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = input.opt(key) ?: continue
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
