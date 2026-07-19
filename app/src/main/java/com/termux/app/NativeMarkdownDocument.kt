package com.termux.app

/** Stable block types used by the native chat document renderer. */
enum class NativeMarkdownBlockType { PROSE, CODE, INLINE_CODE, TABLE }

enum class NativeMarkdownAlignment { START, CENTER, END }

data class NativeMarkdownTableCell(
    val text: String,
    val alignment: NativeMarkdownAlignment = NativeMarkdownAlignment.START,
)

data class NativeMarkdownTable(
    val header: List<NativeMarkdownTableCell>,
    val rows: List<List<NativeMarkdownTableCell>>,
    val columnCount: Int,
)

data class NativeMarkdownDocumentBlock(
    val type: NativeMarkdownBlockType,
    val text: String,
    val language: String = "",
    val table: NativeMarkdownTable? = null,
)

data class NativeMarkdownDocument(val blocks: List<NativeMarkdownDocumentBlock>)

/**
 * A small, deterministic block segmenter that runs before Markwon.
 *
 * It deliberately owns fenced code and GFM tables so those wide blocks can be rendered by
 * dedicated horizontally scrolling components. Prose is grouped only at safe blank-line
 * boundaries; this avoids the old fixed-character splitter cutting a heading/list/table in half.
 */
object NativeMarkdownDocumentParser {
    private const val TARGET_PROSE_CHARS = 5_600
    private val cache = object : LinkedHashMap<String, NativeMarkdownDocument>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NativeMarkdownDocument>?): Boolean = size > 32
    }
    private val fenceStart = Regex("""^\s{0,3}(`{3,}|~{3,})(.*)$""")
    private val tableDelimiterCell = Regex("""^:?-{2,}:?$""")
    private val missingHeadingSpace = Regex("""^(\s{0,3})(#{1,6})([^#\s].*)$""")
    private val unicodeBullet = Regex("""^(\s*)[\u2022\u00B7]\s+""")
    private val inlineListAfterPunctuation = Regex("""([\uFF1A:;\uFF1B])(?:[ \t]+)(?=(?:[-+*]|\d{1,3}[.)])\s+)""")
    private val standaloneInlineCode = Regex("""^\s*(`{1,2})([^`\n]+)\1\s*$""")

    fun cached(source: String): NativeMarkdownDocument? = synchronized(cache) { cache[source] }

    fun parseCached(source: String): NativeMarkdownDocument {
        cached(source)?.let { return it }
        val parsed = parse(source)
        synchronized(cache) { cache[source] = parsed }
        return parsed
    }

    @JvmStatic
    fun parse(source: String): NativeMarkdownDocument {
        if (source.isEmpty()) return NativeMarkdownDocument(emptyList())
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val blocks = ArrayList<NativeMarkdownDocumentBlock>()
        val prose = ArrayList<String>()
        var proseChars = 0

        fun flushProse() {
            if (prose.isEmpty()) return
            val text = normalizeProse(prose.joinToString("\n")).trim('\n')
            if (text.isNotBlank()) blocks += NativeMarkdownDocumentBlock(NativeMarkdownBlockType.PROSE, text)
            prose.clear()
            proseChars = 0
        }

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val opening = fenceStart.matchEntire(line)
            if (opening != null) {
                flushProse()
                val marker = opening.groupValues[1]
                val markerChar = marker[0]
                val markerLength = marker.length
                val language = opening.groupValues[2].trim().substringBefore(' ').take(32)
                val code = ArrayList<String>()
                index++
                while (index < lines.size) {
                    val candidate = lines[index]
                    val trimmed = candidate.trim()
                    val closingLength = trimmed.takeWhile { it == markerChar }.length
                    if (closingLength >= markerLength && trimmed.drop(closingLength).isBlank()) {
                        index++
                        break
                    }
                    code += candidate
                    index++
                }
                blocks += NativeMarkdownDocumentBlock(
                    type = NativeMarkdownBlockType.CODE,
                    text = code.joinToString("\n").trimEnd('\n'),
                    language = language,
                )
                continue
            }

            if (index + 1 < lines.size && isTableHeader(line, lines[index + 1])) {
                flushProse()
                val headerValues = splitTableLine(line)
                val alignments = splitTableLine(lines[index + 1]).map(::delimiterAlignment)
                val columnCount = maxOf(headerValues.size, alignments.size)
                val header = normalizeCells(headerValues, alignments, columnCount)
                val rows = ArrayList<List<NativeMarkdownTableCell>>()
                val raw = ArrayList<String>()
                raw += line
                raw += lines[index + 1]
                index += 2
                while (index < lines.size) {
                    val row = lines[index]
                    if (row.isBlank() || !containsUnescapedTablePipe(row)) break
                    val values = splitTableLine(row)
                    if (values.isEmpty()) break
                    rows += normalizeCells(values, alignments, columnCount)
                    raw += row
                    index++
                }
                blocks += NativeMarkdownDocumentBlock(
                    type = NativeMarkdownBlockType.TABLE,
                    text = raw.joinToString("\n"),
                    table = NativeMarkdownTable(header, rows, columnCount),
                )
                continue
            }

            val inlineCode = standaloneInlineCode.matchEntire(line)
            if (inlineCode != null && inlineCode.groupValues[2].length >= 18) {
                flushProse()
                blocks += NativeMarkdownDocumentBlock(
                    type = NativeMarkdownBlockType.INLINE_CODE,
                    text = inlineCode.groupValues[2],
                )
                index++
                continue
            }

            prose += line
            proseChars += line.length + 1
            index++
            if (line.isBlank() && proseChars >= TARGET_PROSE_CHARS) flushProse()
        }
        flushProse()
        return NativeMarkdownDocument(blocks)
    }

    /** Repairs common model-output variants without touching fenced code or table source. */
    @JvmStatic
    fun normalizeProse(source: String): String = source
        .lineSequence()
        .joinToString("\n") { line ->
            missingHeadingSpace.replace(line) { match ->
                "${match.groupValues[1]}${match.groupValues[2]} ${match.groupValues[3]}"
            }.replace(unicodeBullet, "$1- ")
        }
        .replace(inlineListAfterPunctuation, "$1\n")

    @JvmStatic
    fun splitTableLineForTesting(line: String): List<String> = splitTableLine(line)

    private fun isTableHeader(header: String, delimiter: String): Boolean {
        if (!containsUnescapedTablePipe(header)) return false
        val cells = splitTableLine(delimiter)
        return cells.size >= 2 && cells.all { tableDelimiterCell.matches(it.trim()) }
    }

    private fun delimiterAlignment(cell: String): NativeMarkdownAlignment {
        val value = cell.trim()
        return when {
            value.startsWith(':') && value.endsWith(':') -> NativeMarkdownAlignment.CENTER
            value.endsWith(':') -> NativeMarkdownAlignment.END
            else -> NativeMarkdownAlignment.START
        }
    }

    private fun normalizeCells(
        values: List<String>,
        alignments: List<NativeMarkdownAlignment>,
        columnCount: Int,
    ): List<NativeMarkdownTableCell> = List(columnCount) { column ->
        NativeMarkdownTableCell(
            text = values.getOrElse(column) { "" }.trim(),
            alignment = alignments.getOrElse(column) { NativeMarkdownAlignment.START },
        )
    }

    private fun containsUnescapedTablePipe(line: String): Boolean {
        var escaped = false
        var codeMarkerLength = 0
        var index = 0
        while (index < line.length) {
            val char = line[index]
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '`') {
                val run = line.drop(index).takeWhile { it == '`' }.length
                if (codeMarkerLength == 0) codeMarkerLength = run else if (run == codeMarkerLength) codeMarkerLength = 0
                index += run - 1
            } else if (char == '|' && codeMarkerLength == 0) {
                return true
            }
            index++
        }
        return false
    }

    private fun splitTableLine(line: String): List<String> {
        val trimmed = line.trim()
        val start = if (trimmed.startsWith('|')) 1 else 0
        val end = if (trimmed.endsWith('|') && !trimmed.endsWith("\\|")) trimmed.length - 1 else trimmed.length
        if (start >= end) return emptyList()
        val cells = ArrayList<String>()
        val cell = StringBuilder()
        var escaped = false
        var codeMarkerLength = 0
        var index = start
        while (index < end) {
            val char = trimmed[index]
            when {
                escaped -> {
                    cell.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == '`' -> {
                    val run = trimmed.substring(index, end).takeWhile { it == '`' }.length
                    if (codeMarkerLength == 0) codeMarkerLength = run else if (run == codeMarkerLength) codeMarkerLength = 0
                    repeat(run) { cell.append('`') }
                    index += run - 1
                }
                char == '|' && codeMarkerLength == 0 -> {
                    cells += cell.toString().trim()
                    cell.setLength(0)
                }
                else -> cell.append(char)
            }
            index++
        }
        if (escaped) cell.append('\\')
        cells += cell.toString().trim()
        return cells
    }
}
