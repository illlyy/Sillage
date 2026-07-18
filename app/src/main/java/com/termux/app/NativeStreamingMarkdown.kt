package com.termux.app

/** A Markdown prefix that will never change again for an append-only stream. */
data class NativeStreamingMarkdownBlock(
    val start: Int,
    val end: Int,
    val text: String,
)

data class NativeStreamingMarkdownSnapshot(
    val blocks: List<NativeStreamingMarkdownBlock>,
    val tail: String,
    val stableChars: Int,
    val sourceChars: Int,
)

/**
 * Incremental Markdown segmenter for streaming answers.
 *
 * Only the unfinished tail is rescanned. Paragraph/list/table boundaries and closed fenced
 * code blocks are promoted into immutable blocks, so Compose and Markwon can skip them on all
 * later deltas. A small completed prefix remains in the tail to avoid creating one TextView per
 * sentence; normal blocks settle around 1.2k characters.
 */
class NativeStreamingMarkdownAccumulator {
    private data class Boundary(val offset: Int, val priority: Boolean)

    private val stableBlocks = ArrayList<NativeStreamingMarkdownBlock>()
    private val boundaries = ArrayList<Boundary>()
    private var source = ""
    private var stableChars = 0
    private var scanCursor = 0
    private var fenceMarker = '\u0000'
    private var fenceLength = 0

    @Synchronized
    fun update(next: String, finished: Boolean): NativeStreamingMarkdownSnapshot {
        val appendCompatible = if (finished && source.isNotEmpty()) next.startsWith(source) else isLikelyAppend(next)
        if (!appendCompatible || next.length < stableChars) reset()
        source = next

        if (finished) {
            freeze(next.length)
        } else {
            scanAppendedLines()
            promoteStableBlocks()
        }
        return NativeStreamingMarkdownSnapshot(
            blocks = stableBlocks.toList(),
            tail = next.substring(stableChars),
            stableChars = stableChars,
            sourceChars = next.length,
        )
    }

    @Synchronized
    fun reset() {
        stableBlocks.clear()
        boundaries.clear()
        source = ""
        stableChars = 0
        scanCursor = 0
        fenceMarker = '\u0000'
        fenceLength = 0
    }

    /** Bounded append check: unlike String.startsWith(previous), this never rescans a
     * 100k answer for every tiny delta. The authoritative final snapshot performs the
     * exact prefix check only once when streaming ends. */
    private fun isLikelyAppend(next: String): Boolean {
        if (source.isEmpty()) return true
        if (next.length < source.length) return false
        fun matchesWindow(start: Int, length: Int): Boolean =
            length <= 0 || next.regionMatches(start, source, start, length, ignoreCase = false)
        val headLength = minOf(SAMPLE_CHARS, source.length)
        if (!matchesWindow(0, headLength)) return false
        val tailStart = (source.length - SAMPLE_CHARS).coerceAtLeast(0)
        if (!matchesWindow(tailStart, source.length - tailStart)) return false
        if (stableChars > 0) {
            val checkpointStart = (stableChars - SAMPLE_CHARS / 2).coerceAtLeast(0)
            val checkpointLength = minOf(SAMPLE_CHARS, source.length - checkpointStart)
            if (!matchesWindow(checkpointStart, checkpointLength)) return false
        }
        return true
    }

    /** Continues from the last incomplete line instead of scanning the complete answer. */
    private fun scanAppendedLines() {
        while (scanCursor < source.length) {
            val newline = source.indexOf('\n', scanCursor)
            if (newline < 0) return
            val lineEnd = newline + 1
            val line = source.substring(scanCursor, newline).removeSuffix("\r")
            val trimmedStart = line.trimStart()
            val trimmed = line.trim()

            if (fenceMarker != '\u0000') {
                if (isFenceClose(trimmed, fenceMarker, fenceLength)) {
                    fenceMarker = '\u0000'
                    fenceLength = 0
                    boundaries.add(Boundary(lineEnd, priority = true))
                }
            } else {
                val opening = fenceOpening(trimmedStart)
                if (opening != null) {
                    fenceMarker = opening.first
                    fenceLength = opening.second
                } else if (trimmed.isEmpty()) {
                    val candidate = source.substring(stableChars, lineEnd)
                    boundaries.add(
                        Boundary(
                            lineEnd,
                            priority = NativeUiRenderSafety.containsMarkdownTable(candidate),
                        ),
                    )
                }
            }
            scanCursor = lineEnd
        }
    }

    private fun promoteStableBlocks() {
        while (stableChars < source.length) {
            val boundary = chooseBoundary() ?: return
            freeze(boundary)
        }
    }

    private fun chooseBoundary(): Int? {
        val candidates = boundaries.filter { it.offset > stableChars }
        if (candidates.isEmpty()) return null
        fun hasContent(end: Int): Boolean = source.substring(stableChars, end).isNotBlank()

        // Closed code fences and complete GFM tables should become rich UI immediately,
        // even when smaller than the normal grouping target.
        candidates.firstOrNull { it.priority && hasContent(it.offset) }?.let { return it.offset }
        candidates.firstOrNull { it.offset - stableChars >= TARGET_BLOCK_CHARS && hasContent(it.offset) }
            ?.let { return it.offset }
        if (source.length - stableChars >= MAX_ACTIVE_SEMANTIC_TAIL_CHARS) {
            candidates.lastOrNull { hasContent(it.offset) }?.let { return it.offset }
        }
        return null
    }

    private fun freeze(requestedEnd: Int) {
        var end = requestedEnd.coerceIn(stableChars, source.length)
        if (end > stableChars && end < source.length && Character.isHighSurrogate(source[end - 1])) end--
        if (end <= stableChars) return
        val text = source.substring(stableChars, end)
        stableBlocks.add(NativeStreamingMarkdownBlock(stableChars, end, text))
        stableChars = end
        boundaries.removeAll { it.offset <= stableChars }
    }

    private fun fenceOpening(line: String): Pair<Char, Int>? {
        val marker = line.firstOrNull() ?: return null
        if (marker != '`' && marker != '~') return null
        val count = line.takeWhile { it == marker }.length
        return if (count >= 3) marker to count else null
    }

    private fun isFenceClose(line: String, marker: Char, minimumLength: Int): Boolean {
        if (line.length < minimumLength || line.any { it != marker }) return false
        return true
    }

    companion object {
        private const val TARGET_BLOCK_CHARS = 1_200
        private const val MAX_ACTIVE_SEMANTIC_TAIL_CHARS = 2_400
        private const val SAMPLE_CHARS = 64
    }
}
