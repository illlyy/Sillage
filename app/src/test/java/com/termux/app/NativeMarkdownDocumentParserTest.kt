package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMarkdownDocumentParserTest {
    @Test
    fun separatesFencesTablesAndProseWithoutLosingWideContent() {
        val source = """#Heading

| name | value |
| :-- | --: |
| a \| b | `x|y` |

~~~kotlin
val x = "|"
~~~
""".trimIndent()
        val document = NativeMarkdownDocumentParser.parse(source)
        assertEquals(
            listOf(NativeMarkdownBlockType.PROSE, NativeMarkdownBlockType.TABLE, NativeMarkdownBlockType.CODE),
            document.blocks.map { it.type },
        )
        assertEquals("# Heading", document.blocks.first().text)
        val table = document.blocks[1].table!!
        assertEquals(2, table.columnCount)
        assertEquals("a | b", table.rows.single()[0].text)
        assertEquals("`x|y`", table.rows.single()[1].text)
        assertEquals(NativeMarkdownAlignment.END, table.header[1].alignment)
        assertEquals("kotlin", document.blocks.last().language)
        assertEquals("val x = \"|\"", document.blocks.last().text)
    }

    @Test
    fun keepsUnclosedFenceAsCodeAndPromotesLongStandaloneInlineCode() {
        val source = """`adb shell settings get secure enabled_accessibility_services`

```text
still streaming"""
        val blocks = NativeMarkdownDocumentParser.parse(source).blocks
        assertEquals(2, blocks.size)
        assertEquals(NativeMarkdownBlockType.INLINE_CODE, blocks[0].type)
        assertEquals(NativeMarkdownBlockType.CODE, blocks[1].type)
        assertEquals("adb shell settings get secure enabled_accessibility_services", blocks[0].text)
        assertEquals("still streaming", blocks[1].text)
    }

    @Test
    fun acceptsTwoDashGfmDelimiterAndIgnoresPipesInsideInlineCode() {
        val cells = NativeMarkdownDocumentParser.splitTableLineForTesting("| `a|b` | c\\|d |")
        assertEquals(listOf("`a|b`", "c|d"), cells)
        val document = NativeMarkdownDocumentParser.parse("a | b\n-- | --\n1 | 2")
        assertEquals(NativeMarkdownBlockType.TABLE, document.blocks.single().type)
    }
}
