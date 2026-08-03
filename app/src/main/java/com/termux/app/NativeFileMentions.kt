package com.termux.app

import java.io.File

/**
 * @-file mention completion (pattern: claudecodeui useFileMentions.tsx). Pure helpers for the
 * trigger range, project file scanning (bounded), filtering and text replacement; the composer
 * renders the suggestion menu.
 */
internal const val MENTION_MAX_FILES = 500
internal const val MENTION_MAX_DEPTH = 6

/** Returns the `/word`-style range of the `@query` before [cursor], or null. */
internal fun nativeMentionTriggerRange(text: String, cursor: Int): IntRange? {
    if (text.isBlank() || cursor <= 0) return null
    val prefix = text.take(cursor.coerceAtMost(text.length))
    // The mention starts at the last '@' that is not preceded by another word character.
    var index = prefix.lastIndexOf('@')
    if (index < 0) return null
    if (index > 0 && prefix[index - 1].isLetterOrDigit()) return null
    // Only alphanumeric/`/`/`.`/`-`/`_` chars belong to the query.
    var end = index + 1
    while (end < prefix.length && prefix[end].isMentionChar()) end++
    if (end == index + 1) return null
    return index until end
}

private fun Char.isMentionChar(): Boolean = isLetterOrDigit() || this == '/' || this == '.' || this == '-' || this == '_'

/** Bounded recursive scan; returns workspace-relative paths like `src/Main.kt`. */
internal fun nativeScanMentionFiles(
    root: File,
    maxFiles: Int = MENTION_MAX_FILES,
    maxDepth: Int = MENTION_MAX_DEPTH,
): List<String> {
    if (!root.isDirectory) return emptyList()
    val results = ArrayList<String>(maxFiles)
    fun visit(directory: File, depth: Int, prefix: String) {
        if (results.size >= maxFiles || depth > maxDepth) return
        val children = directory.listFiles() ?: return
        children.sortedBy { it.name.lowercase() }.forEach { child ->
            if (results.size >= maxFiles) return@forEach
            val relative = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            when {
                child.isDirectory -> visit(child, depth + 1, relative)
                else -> results.add(relative)
            }
        }
    }
    visit(root, 0, "")
    return results
}

/** Name or path substring match, capped at [maxResults]. */
internal fun filterMentionFiles(
    files: List<String>,
    query: String,
    maxResults: Int = 10,
): List<String> {
    val q = query.trim()
    if (q.isBlank()) return files.take(maxResults)
    val nameMatches = files.filter { it.substringAfterLast('/').contains(q, ignoreCase = true) }
    val pathMatches = files.filter { it.contains(q, ignoreCase = true) && it.substringAfterLast('/').contains(q, ignoreCase = true).not() }
    return (nameMatches + pathMatches).take(maxResults)
}

/** Replaces the `@query` range with the full path plus a trailing space. */
internal fun nativeApplyMention(text: String, range: IntRange, filePath: String): String =
    text.replaceRange(range.first, range.last + 1, "$filePath ")
