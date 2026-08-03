package com.termux.app

import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.text.SimpleDateFormat

/**
 * Chat output normalization pipeline (pattern: claudecodeui chatFormatting.ts). Pure functions
 * only; applied once per completed markdown block, never on the streaming tail.
 */

/** Collapses single-line ``` ``` ``` spans into inline code (models emit fenced inline snippets). */
internal fun normalizeInlineCodeFences(text: String): String = runCatching {
    Regex("```([^`\n]*?)```").replace(text) { match ->
        "`" + match.groupValues[1] + "`"
    }
}.getOrDefault(text)

private val HTML_ENTITY_PATTERN = Regex("&(?:lt|gt|quot|#39|amp);")

/** Decodes the HTML entities models frequently leak into plain text. */
internal fun decodeHtmlEntities(text: String): String = runCatching {
    HTML_ENTITY_PATTERN.replace(text) { match ->
        when (match.value) {
            "&lt;" -> "<"
            "&gt;" -> ">"
            "&quot;" -> "\""
            "&#39;" -> "'"
            "&amp;" -> "&"
            else -> match.value
        }
    }
}.getOrDefault(text)

private const val MATH_PLACEHOLDER_PREFIX = "@@MATH"
private const val MATH_PLACEHOLDER_SUFFIX = "@@"

/**
 * Unescapes `\n`/`\t`/`\r` while protecting `$...$` and `$$...$$` math with placeholders so
 * unescaping never corrupts LaTeX (which legitimately contains backslashes).
 */
internal fun unescapeWithMathProtection(text: String): String = runCatching {
    val placeholders = mutableListOf<String>()
    var protected = Regex("\\$\\$([\\s\\S]*?)\\$\\$").replace(text) { match ->
        placeholders.add(match.groupValues[1])
        MATH_PLACEHOLDER_PREFIX + (placeholders.size - 1) + MATH_PLACEHOLDER_SUFFIX
    }
    protected = Regex("\\$([\\s\\S]*?)\\$").replace(protected) { match ->
        placeholders.add(match.groupValues[1])
        MATH_PLACEHOLDER_PREFIX + (placeholders.size - 1) + MATH_PLACEHOLDER_SUFFIX
    }
    val unescaped = protected
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\r", "\r")
    Regex("$MATH_PLACEHOLDER_PREFIX(\\d+)$MATH_PLACEHOLDER_SUFFIX").replace(unescaped) { match ->
        val index = match.groupValues[1].toIntOrNull() ?: return@replace match.value
        val math = placeholders.getOrNull(index) ?: return@replace match.value
        "\$$math\$"
    }
}.getOrDefault(text)

/**
 * Replaces usage-limit epoch timestamps (seconds or milliseconds) with a human readable
 * "reset at HH:MM (zone) - d Mon yyyy" fragment (claudecodeui formatUsageLimitText).
 */
internal fun formatUsageLimitText(
    text: String,
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): String = runCatching {
    if (!text.contains("usage limit", ignoreCase = true)) return@runCatching text
    val timestamp = Regex("(\\d{10,13})").find(text)?.groupValues?.get(1) ?: return@runCatching text
    var epoch = timestamp.toLongOrNull() ?: return@runCatching text
    // Seconds (10 digits) vs milliseconds (13 digits) heuristic.
    if (epoch < 1_000_000_000_000L) epoch *= 1000L
    val formatter = SimpleDateFormat("HH:mm 'GMT'Z '('z')' - d MMM yyyy", locale).apply {
        this.timeZone = timeZone
    }
    text.replace(timestamp, "reset at ${formatter.format(Date(epoch))}")
}.getOrDefault(text)

private const val CODEBLOCK_PLACEHOLDER_PREFIX = "@@CODEBLOCK"
private const val CODEBLOCK_PLACEHOLDER_SUFFIX = "@@"

/**
 * Markdown to plain text conversion for copy-as-text. Fenced code blocks are extracted into
 * placeholders first so their content survives syntax stripping untouched.
 */
internal fun convertMarkdownToPlainText(markdown: String): String = runCatching {
    var text = markdown
    val codeBlocks = mutableListOf<String>()
    text = Regex("```([\\s\\S]*?)```").replace(text) { match ->
        codeBlocks.add(match.groupValues[1])
        CODEBLOCK_PLACEHOLDER_PREFIX + (codeBlocks.size - 1) + CODEBLOCK_PLACEHOLDER_SUFFIX
    }
    text = text
        .replace(Regex("`([^`]*?)`"), "$1")
        .replace(Regex("!\\[(.*?)]\\((.*?)\\)"), "$1")
        .replace(Regex("\\[(.*?)]\\((.*?)\\)"), "$1")
        .replace(Regex("^>\\s?", RegexOption.MULTILINE), "")
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
        .replace("**", "")
        .replace("__", "")
        .replace("~~", "")
        .replace(Regex("\\*([^*]+?)\\*"), "$1")
        .replace(Regex("_([^_]+?)_"), "$1")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("\\n{3,}"), "\n\n")
    codeBlocks.forEachIndexed { index, code ->
        text = text.replace(CODEBLOCK_PLACEHOLDER_PREFIX + index + CODEBLOCK_PLACEHOLDER_SUFFIX, code)
    }
    text.trim()
}.getOrDefault(markdown)

/** Full normalization pipeline applied once per completed assistant markdown block. */
internal fun normalizeResponseMarkdown(text: String): String {
    var result = text
    result = normalizeInlineCodeFences(result)
    result = decodeHtmlEntities(result)
    result = unescapeWithMathProtection(result)
    result = formatUsageLimitText(result)
    return result
}
