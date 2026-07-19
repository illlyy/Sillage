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

/** Selects a bounded suffix for the live viewport; completed rendering still uses all blocks. */
object NativeStreamingMarkdownWindow {
    @JvmStatic
    fun firstVisibleBlock(
        blocks: List<NativeStreamingMarkdownBlock>,
        tailChars: Int,
        budgetChars: Int = 6_000,
    ): Int {
        var budget = (budgetChars - tailChars).coerceAtLeast(0)
        var index = blocks.size
        while (index > 0 && (budget > 0 || index == blocks.size)) {
            index--
            budget -= blocks[index].text.length
        }
        return index
    }
}

/**
 * Incremental Markdown segmenter for streaming answers.
 *
 * Production streams use [append], which keeps the full source in a private StringBuilder and
 * publishes only immutable stable blocks plus a bounded semantic tail. [update] remains available
 * for authoritative replacement/final payloads and tests, but append-only updates never copy the
 * complete growing answer.
 */
class NativeStreamingMarkdownAccumulator {
    private data class Boundary(val offset: Int, val priority: Boolean)

    private val stableBlocks = ArrayList<NativeStreamingMarkdownBlock>()
    private var publishedBlocks: List<NativeStreamingMarkdownBlock> = emptyList()
    private val boundaries = ArrayList<Boundary>()
    private val source = StringBuilder()
    private var stableChars = 0
    private var scanCursor = 0
    private var fenceMarker = '\u0000'
    private var fenceLength = 0

    /** Appends one already-sanitized stream batch without materializing the full document. */
    @Synchronized
    fun append(delta: String, finished: Boolean = false): NativeStreamingMarkdownSnapshot {
        if (delta.isNotEmpty()) source.append(delta)
        advance(finished)
        return snapshot()
    }

    /** Materializes the complete document only for completion actions or explicit callers. */
    @Synchronized
    fun materialize(): String = source.toString()

    /** Applies an authoritative full document, preserving incremental state when it is append-only. */
    @Synchronized
    fun update(next: String, finished: Boolean): NativeStreamingMarkdownSnapshot {
        val appendCompatible = if (finished && source.isNotEmpty()) startsWithCurrentSource(next) else isLikelyAppend(next)
        if (!appendCompatible || next.length < stableChars) reset()
        if (next.length > source.length) {
            source.append(next, source.length, next.length)
        } else if (next.length < source.length) {
            reset()
            source.append(next)
        }
        advance(finished)
        return snapshot()
    }

    @Synchronized
    fun reset() {
        stableBlocks.clear()
        publishedBlocks = emptyList()
        boundaries.clear()
        source.setLength(0)
        stableChars = 0
        scanCursor = 0
        fenceMarker = '\u0000'
        fenceLength = 0
    }

    private fun advance(finished: Boolean) {
        if (finished) {
            freeze(source.length)
        } else {
            scanAppendedLines()
            promoteStableBlocks()
        }
    }

    private fun snapshot(): NativeStreamingMarkdownSnapshot = NativeStreamingMarkdownSnapshot(
        blocks = publishedBlocks,
        tail = source.substring(stableChars),
        stableChars = stableChars,
        sourceChars = source.length,
    )

    private fun startsWithCurrentSource(next: String): Boolean {
        if (next.length < source.length) return false
        for (index in 0 until source.length) if (next[index] != source[index]) return false
        return true
    }

    /** Bounded append check: the authoritative final snapshot performs the exact check once. */
    private fun isLikelyAppend(next: String): Boolean {
        if (source.isEmpty()) return true
        if (next.length < source.length) return false
        fun matchesWindow(start: Int, length: Int): Boolean {
            if (length <= 0) return true
            for (index in start until start + length) if (next[index] != source[index]) return false
            return true
        }
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
            val newline = findNewline(scanCursor)
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
                    boundaries.add(Boundary(lineEnd, priority = NativeUiRenderSafety.containsMarkdownTable(candidate)))
                }
            }
            scanCursor = lineEnd
        }
    }

    private fun findNewline(start: Int): Int {
        for (index in start until source.length) if (source[index] == '\n') return index
        return -1
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
        publishedBlocks = stableBlocks.toList()
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
