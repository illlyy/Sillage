package com.termux.app

/** Pure safeguards shared by the native Compose renderer and unit tests. */
object NativeUiRenderSafety {
    const val MAX_ANIMATED_DOCUMENT_CHARS = 4_000
    private const val MAX_CHUNK_CHARS = 3_200
    private const val MAX_CHUNK_LINES = 120
    private const val ERROR_PREVIEW_CHARS = 1_600

    /**
     * Splits a Markdown/prose block without losing source characters. Keeping each
     * Android TextView short avoids device GPU layer limits on pathological history.
     */
    @JvmStatic
    fun canAnimateDocument(source: String): Boolean =
        source.length <= MAX_ANIMATED_DOCUMENT_CHARS && source.count { it == '\n' } <= MAX_CHUNK_LINES

    @JvmStatic
    fun splitMarkdown(source: String): List<String> = splitText(source, preferParagraphs = true)

    @JvmStatic
    fun containsMarkdownTable(source: String): Boolean {
        val delimiter = Regex("""^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$""")
        return source.lineSequence().zipWithNext().any { (header, separator) ->
            header.contains('|') && delimiter.matches(separator)
        }
    }

    @JvmStatic
    fun splitPlainText(source: String): List<String> = splitText(source, preferParagraphs = false)

    /** Terminal protocol events must bypass the tiny-delta debounce or a delayed flush can
     * reactivate ANSWERING after the turn was already marked complete. */
    @JvmStatic
    fun shouldDeferStreamFlush(pendingLength: Int, boundary: Boolean, ageMs: Long, force: Boolean): Boolean =
        !force && pendingLength < 12 && !boundary && ageMs < 110L

    @JvmStatic
    fun sanitizeToolDetail(source: String): String {
        if (source.contains("SensitiveContentDetected", ignoreCase = true)) {
            return "\u8be5\u5de5\u5177\u8f93\u51fa\u89e6\u53d1\u4e86\u654f\u611f\u5185\u5bb9\u4fdd\u62a4\uff0c\u539f\u59cb\u8f93\u51fa\u5df2\u9690\u85cf\u3002"
        }
        return source
    }

    @JvmStatic
    fun errorSummary(source: String): String {
        if (source.contains("SensitiveContentDetected", ignoreCase = true)) {
            return "\u5b50\u4ee3\u7406\u9047\u5230\u654f\u611f\u5185\u5bb9\u4fdd\u62a4\uff0c\u5df2\u505c\u6b62\u672c\u6b21\u6267\u884c\u3002\u4f60\u53ef\u4ee5\u4fee\u6539\u4efb\u52a1\u8303\u56f4\u540e\u91cd\u8bd5\u3002"
        }
        if (source.length <= ERROR_PREVIEW_CHARS) return source
        return source.take(ERROR_PREVIEW_CHARS).trimEnd() + "\n\n\u9519\u8bef\u8be6\u60c5\u8fc7\u957f\uff0c\u5df2\u6298\u53e0\u663e\u793a\u3002"
    }

    private fun splitText(source: String, preferParagraphs: Boolean): List<String> {
        if (source.isEmpty()) return emptyList()
        val result = ArrayList<String>((source.length / MAX_CHUNK_CHARS) + 1)
        var start = 0
        while (start < source.length) {
            var hardEnd = (start + MAX_CHUNK_CHARS).coerceAtMost(source.length)
            var newlineCount = 0
            var cursor = start
            while (cursor < hardEnd) {
                if (source[cursor] == '\n') {
                    newlineCount++
                    if (newlineCount >= MAX_CHUNK_LINES) {
                        hardEnd = cursor + 1
                        break
                    }
                }
                cursor++
            }
            if (hardEnd >= source.length) {
                result.add(source.substring(start))
                break
            }

            val minimumUsefulBreak = start + (MAX_CHUNK_CHARS / 3).coerceAtMost(hardEnd - start)
            var end = -1
            if (preferParagraphs) {
                val paragraph = source.lastIndexOf("\n\n", hardEnd - 1)
                if (paragraph >= minimumUsefulBreak) end = paragraph + 2
            }
            if (end < 0) {
                val line = source.lastIndexOf('\n', hardEnd - 1)
                if (line >= minimumUsefulBreak) end = line + 1
            }
            if (end < 0) {
                var whitespace = hardEnd - 1
                while (whitespace >= minimumUsefulBreak && !source[whitespace].isWhitespace()) whitespace--
                if (whitespace >= minimumUsefulBreak) end = whitespace + 1
            }
            if (end <= start) end = hardEnd
            if (end < source.length && Character.isHighSurrogate(source[end - 1])) end--
            if (end <= start) end = hardEnd
            result.add(source.substring(start, end))
            start = end
        }
        return result
    }
}
