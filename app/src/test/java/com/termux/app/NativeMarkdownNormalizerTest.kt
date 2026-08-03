package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class NativeMarkdownNormalizerTest {

    @Test
    fun inlineCodeFencesCollapseToInlineCode() {
        assertEquals(
            "Use `kapt` for that.",
            normalizeInlineCodeFences("Use ```kapt``` for that."),
        )
    }

    @Test
    fun inlineCodeFencesKeepRealMultilineBlocksIntact() {
        val block = "```kotlin\nval x = 1\n```"
        assertEquals(block, normalizeInlineCodeFences(block))
    }

    @Test
    fun inlineCodeFencesHandleAdjacentSpans() {
        assertEquals(
            "`a` and `b`",
            normalizeInlineCodeFences("```a``` and ```b```"),
        )
    }

    @Test
    fun htmlEntitiesAreDecoded() {
        assertEquals(
            "a < b > c \"quoted\" 'apostrophe' & ampersand",
            decodeHtmlEntities("a &lt; b &gt; c &quot;quoted&quot; &#39;apostrophe&#39; &amp; ampersand"),
        )
    }

    @Test
    fun unescapeProtectsMathWhileUnescapingNewlines() {
        val input = "LaTeX: $\\frac{1}{2}$ and block $$\\int_0^1 x\\,dx$$ with \\n newline"
        val result = unescapeWithMathProtection(input)
        assertTrue(result.contains("$\\frac{1}{2}$"))
        // Double-dollar blocks restore as single-dollar (math renderers accept both).
        assertTrue(result.contains("$\\int_0^1 x\\,dx$"))
        assertTrue(result.contains("\n newline"))
        assertFalse(result.contains("\\n"))
    }

    @Test
    fun unescapeHandlesTabsAndCarriageReturns() {
        assertEquals("a\tb", unescapeWithMathProtection("a\\tb"))
        assertEquals("a\rb", unescapeWithMathProtection("a\\rb"))
    }

    @Test
    fun usageLimitTimestampSecondsIsFormatted() {
        val fixed = TimeZone.getTimeZone("GMT")
        // 2024-01-02T03:04:05Z in seconds
        val result = formatUsageLimitText(
            "Claude AI usage limit reached, reset at 1704164645",
            timeZone = fixed,
            locale = Locale.ENGLISH,
        )
        assertEquals("reset at 03:04 GMT+0000 (GMT) - 2 Jan 2024", result.substringAfter("reset at "))
    }

    @Test
    fun usageLimitTimestampMillisecondsIsFormatted() {
        val fixed = TimeZone.getTimeZone("GMT")
        val result = formatUsageLimitText(
            "usage limit reached (1704164645000)",
            timeZone = fixed,
            locale = Locale.ENGLISH,
        )
        assertTrue(result.contains("reset at 03:04"))
    }

    @Test
    fun usageLimitWithoutTimestampIsUntouched() {
        val input = "Claude AI usage limit reached"
        assertEquals(input, formatUsageLimitText(input))
    }

    @Test
    fun plainTextConversionPreservesCodeBlocks() {
        val markdown = """
            # Title

            Some **bold** and `inline` and [link](https://example.com).

            ```kotlin
            fun x() = "**not stripped**"
            ```

            - item one
            1. item two

            > quote
        """.trimIndent()
        val result = convertMarkdownToPlainText(markdown)
        assertTrue(result.contains("Title"))
        assertTrue(result.contains("Some bold and inline and link."))
        assertTrue(result.contains("fun x() = \"**not stripped**\""))
        assertTrue(result.contains("item one"))
        assertTrue(result.contains("item two"))
        assertFalse(result.contains("**bold**"))
        assertFalse(result.contains("> quote"))
        assertFalse(result.contains("@@" + "CODEBLOCK"))
    }

    @Test
    fun fullPipelineNormalizesResponse() {
        val input = "See ```inline``` here: $\\frac{1}{2}$ with \\n newline &lt;tag&gt;"
        val result = normalizeResponseMarkdown(input)
        assertEquals("See `inline` here: $\\frac{1}{2}$ with \n newline <tag>", result)
    }

    @Test
    fun fullPipelineLeavesNormalMarkdownUntouched() {
        val input = "# Title\n\nSome paragraph with `code` and [x](y).\n\n```kotlin\nval a = 1\n```"
        assertEquals(input, normalizeResponseMarkdown(input))
    }
}
