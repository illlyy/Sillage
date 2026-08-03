package com.termux.app

/**
 * Line-based diff via LCS alignment (pattern: claudecodeui messageTransforms.ts). Insertions and
 * deletions stay local — edits never cascade into a whole-file "changed" diff.
 */
internal enum class NativeDiffLineType { ADDED, REMOVED }

internal data class NativeDiffLine(
    val type: NativeDiffLineType,
    val content: String,
    val lineNum: Int?,
)

internal fun calculateDiff(oldContent: String, newContent: String): List<NativeDiffLine> {
    val oldLines = oldContent.split("\n")
    val newLines = newContent.split("\n")
    if (oldLines.isEmpty() || newLines.isEmpty()) return emptyList()
    val table = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
    for (i in oldLines.indices.reversed()) {
        for (j in newLines.indices.reversed()) {
            table[i][j] = if (oldLines[i] == newLines[j]) {
                table[i + 1][j + 1] + 1
            } else {
                maxOf(table[i + 1][j], table[i][j + 1])
            }
        }
    }
    val diff = mutableListOf<NativeDiffLine>()
    var i = 0
    var j = 0
    while (i < oldLines.size && j < newLines.size) {
        when {
            oldLines[i] == newLines[j] -> {
                i++
                j++
            }
            table[i + 1][j] >= table[i][j + 1] -> {
                diff.add(NativeDiffLine(NativeDiffLineType.REMOVED, oldLines[i], i + 1))
                i++
            }
            else -> {
                diff.add(NativeDiffLine(NativeDiffLineType.ADDED, newLines[j], j + 1))
                j++
            }
        }
    }
    while (i < oldLines.size) {
        diff.add(NativeDiffLine(NativeDiffLineType.REMOVED, oldLines[i], i + 1))
        i++
    }
    while (j < newLines.size) {
        diff.add(NativeDiffLine(NativeDiffLineType.ADDED, newLines[j], j + 1))
        j++
    }
    return diff
}

/**
 * Diff cache with an insertion-ordered bound. Repeated diffs of the same pair short-circuit;
 * the oldest entry is evicted once the cap is exceeded (LRU-ish, like the reference).
 */
internal class NativeCachedDiffCalculator(private val maxEntries: Int = 100) {

    private val cache = object : LinkedHashMap<String, List<NativeDiffLine>>() {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<NativeDiffLine>>?,
        ): Boolean = size > maxEntries
    }

    fun diff(oldContent: String, newContent: String): List<NativeDiffLine> {
        val key = oldContent + "\u0000" + newContent
        return cache.getOrPut(key) { calculateDiff(oldContent, newContent) }
    }

    fun clear() {
        cache.clear()
    }

    val size: Int get() = cache.size
}
