package com.termux.app

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

/**
 * A lightweight, dependency-free syntax highlighter for chat code blocks.
 *
 * It is deliberately simpler than a full grammar (Prism/tree-sitter): a single-pass tokenizer
 * over comments, strings, numbers and a per-language-family keyword set. That keeps APK size
 * and highlight cost low while still giving code blocks clear, editor-like coloring. The
 * palette is chosen from the code block background luminance so it reads in light and dark
 * themes.
 */
internal object NativeCodeHighlighter {
    /** Skip highlighting for very large blocks to bound regex cost. */
    private const val MAX_HIGHLIGHT_CHARS = 20_000

    private data class Palette(
        val comment: Int,
        val string: Int,
        val number: Int,
        val keyword: Int,
    )

    // VS Code-inspired dark palette.
    private val DARK = Palette(
        comment = 0xFF6A9955.toInt(),
        string = 0xFFCE9178.toInt(),
        number = 0xFFB5CEA8.toInt(),
        keyword = 0xFF569CD6.toInt(),
    )

    // VS-inspired light palette.
    private val LIGHT = Palette(
        comment = 0xFF008000.toInt(),
        string = 0xFFA31515.toInt(),
        number = 0xFF098658.toInt(),
        keyword = 0xFF0000FF.toInt(),
    )

    private val C_FAMILY = setOf(
        "abstract", "as", "async", "await", "bool", "boolean", "break", "byte", "case", "catch",
        "char", "class", "const", "constexpr", "continue", "default", "delete", "do", "double",
        "else", "enum", "export", "extends", "extern", "false", "final", "finally", "float",
        "for", "friend", "function", "goto", "if", "implements", "import", "in", "instanceof",
        "int", "interface", "internal", "is", "let", "long", "namespace", "new", "null",
        "nullptr", "object", "operator", "override", "package", "private", "protected", "public",
        "readonly", "ref", "return", "sealed", "short", "signed", "sizeof", "static", "struct",
        "super", "switch", "template", "this", "throw", "throws", "true", "try", "type",
        "typedef", "typename", "typeof", "unsigned", "val", "var", "virtual", "void", "volatile",
        "when", "while", "yield",
    )

    private val JS = C_FAMILY + setOf(
        "undefined", "NaN", "Infinity", "of", "in", "typeof", "constructor", "prototype",
    )

    private val RUST = setOf(
        "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
        "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "move",
        "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super", "trait",
        "true", "type", "unsafe", "use", "where", "while",
    )

    private val GO = setOf(
        "break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough",
        "for", "func", "go", "goto", "if", "import", "interface", "map", "package", "range",
        "return", "select", "struct", "switch", "type", "var", "nil", "true", "false",
    )

    private val PYTHON = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
        "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import",
        "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "self",
        "True", "try", "while", "with", "yield",
    )

    private val SQL = setOf(
        "ADD", "ALL", "ALTER", "AND", "AS", "ASC", "BEGIN", "BETWEEN", "BY", "CASE", "CHECK",
        "COLUMN", "CONSTRAINT", "CREATE", "DATABASE", "DEFAULT", "DELETE", "DESC", "DISTINCT",
        "DROP", "ELSE", "END", "EXEC", "EXISTS", "FOREIGN", "FROM", "FULL", "GROUP", "HAVING",
        "IF", "IN", "INDEX", "INNER", "INSERT", "INTO", "IS", "JOIN", "KEY", "LEFT", "LIKE",
        "LIMIT", "NOT", "NULL", "ON", "OR", "ORDER", "OUTER", "PRIMARY", "PROCEDURE", "RIGHT",
        "SELECT", "SET", "TABLE", "THEN", "TOP", "TRUNCATE", "UNION", "UNIQUE", "UPDATE",
        "VALUES", "VIEW", "WHEN", "WHERE", "WITH",
    )

    private val SHELL = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case", "esac",
        "function", "select", "return", "exit", "local", "export", "readonly", "shift", "source",
        "echo", "cd", "ls", "cp", "mv", "rm", "mkdir", "cat", "grep", "sed", "awk", "curl",
    )

    /** Returns a highlighted [CharSequence], or null when highlighting is skipped. */
    @JvmStatic
    fun highlight(code: String, language: String, backgroundColor: Int): CharSequence? {
        if (language.isBlank() || code.isEmpty() || code.length > MAX_HIGHLIGHT_CHARS) return null
        val palette = if (isDarkBackground(backgroundColor)) DARK else LIGHT
        val lang = language.lowercase().trim()

        val commentRegex = when (lang) {
            "python", "py", "yaml", "yml", "ruby", "rb", "bash", "sh", "shell", "zsh", "toml",
            "properties", "makefile", "dockerfile", "r",
            -> Regex("""#[^\n]*""")
            "sql", "lua", "haskell",
            -> Regex("""--[^\n]*""")
            "html", "xml", "svg",
            -> Regex("""<!--[\s\S]*?-->""")
            else -> Regex("""/\*[\s\S]*?\*/|//[^\n]*""")
        }

        val keywords = when (lang) {
            "python", "py" -> PYTHON
            "javascript", "js", "typescript", "ts", "jsx", "tsx" -> JS
            "rust", "rs" -> RUST
            "go", "golang" -> GO
            "sql" -> SQL
            "bash", "sh", "shell", "zsh" -> SHELL
            else -> C_FAMILY
        }

        val keywordPattern = keywords.joinToString("|") { Regex.escape(it) }
        // Single-pass alternation: earlier alternatives win, so keywords/numbers inside
        // comments or strings are never double-colored.
        val combined = runCatching {
            Regex(
                "(?<comment>${commentRegex.pattern})" +
                    "|(?<string>\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)*'|`(?:[^`\\\\]|\\\\.)*`)" +
                    "|(?<number>\\b(?:0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)[fFdDlLuU]*)\\b)" +
                    "|(?<keyword>\\b(?:$keywordPattern)\\b)",
            )
        }.getOrNull() ?: return null

        val spannable: Spannable = SpannableString(code)
        runCatching {
            for (match in combined.findAll(code)) {
                val groups = match.groups
                val (color, bold) = when {
                    groups["comment"] != null -> palette.comment to false
                    groups["string"] != null -> palette.string to false
                    groups["number"] != null -> palette.number to false
                    groups["keyword"] != null -> palette.keyword to true
                    else -> continue
                }
                val range = match.range
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    range.first,
                    range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                if (bold) {
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        range.first,
                        range.last + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
        return spannable
    }

    private fun isDarkBackground(color: Int): Boolean =
        androidx.core.graphics.ColorUtils.calculateLuminance(color) < 0.5
}
