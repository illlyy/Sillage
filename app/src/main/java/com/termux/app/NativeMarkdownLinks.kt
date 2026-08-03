package com.termux.app

/**
 * Workspace-aware markdown link routing (pattern: claudecodeui Markdown.tsx). External schemes
 * open in the browser; `src/foo.ts` / `src/foo.ts:130` style references open in the file viewer;
 * anything else is ignored.
 */
internal sealed interface NativeMarkdownLinkAction {
    data object External : NativeMarkdownLinkAction
    data class FileRef(val path: String, val line: Int?) : NativeMarkdownLinkAction
    data object None : NativeMarkdownLinkAction
}

internal val EXTERNAL_HREF_SCHEMES: Set<String> = setOf("http", "https", "mailto", "tel", "data", "geo", "ftp")

internal fun isExternalHref(url: String): Boolean {
    val lower = url.trim().lowercase()
    if (lower == "#") return false
    if (lower.startsWith("//")) return true
    val scheme = lower.substringBefore(':', "")
    return scheme.isNotBlank() && scheme in EXTERNAL_HREF_SCHEMES
}

/** Path heuristic: has a path separator or a plausible file extension, after quotes/brackets. */
internal fun looksLikeFilePath(ref: String): Boolean {
    val cleaned = ref.trim().trim('"', '\'', '`', '<', '>')
    if (cleaned.isBlank()) return false
    if (cleaned.startsWith(".")) return true
    val hasSeparator = cleaned.contains('/') || cleaned.contains('\\')
    val dotIndex = cleaned.lastIndexOf('.')
    val extension = if (dotIndex > 0 && dotIndex < cleaned.length - 1) cleaned.substring(dotIndex + 1) else ""
    val hasExtension = extension.isNotEmpty() && extension.length <= 8 && extension.all { it.isLetterOrDigit() }
    return hasSeparator || hasExtension
}

/** Strips a `:line` / `:line:col` suffix and routes the remainder. */
internal fun resolveMarkdownLink(url: String): NativeMarkdownLinkAction {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return NativeMarkdownLinkAction.None
    if (isExternalHref(trimmed)) return NativeMarkdownLinkAction.External
    var path = trimmed
    var line: Int? = null
    Regex("^(.*?):(\\d+)(?::\\d+)?$").find(trimmed)?.let { match ->
        path = match.groupValues[1]
        line = match.groupValues[2].toIntOrNull()
    }
    if (looksLikeFilePath(path)) return NativeMarkdownLinkAction.FileRef(path, line)
    return NativeMarkdownLinkAction.None
}

/**
 * Cheap JSON-response detection for assistant text: starts with `{`/`[`, ends with the
 * matching bracket, and parses. Bounded to avoid paying JSON.parse on huge answers.
 */
internal fun looksLikeJsonResponse(text: String, maxChars: Int = 20_000): Boolean {
    val trimmed = text.trim()
    if (trimmed.length < 2 || trimmed.length > maxChars) return false
    val first = trimmed.first()
    if (first != '{' && first != '[') return false
    if (trimmed.last() != (if (first == '{') '}' else ']')) return false
    return runCatching {
        if (first == '{') org.json.JSONObject(trimmed) else org.json.JSONArray(trimmed)
    }.isSuccess
}
