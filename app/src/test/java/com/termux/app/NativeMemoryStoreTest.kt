package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NativeMemoryStoreTest {
    @Test
    fun escapingRoundTripsTabsNewlinesAndBackslashes() {
        val raw = "第一行\n第二行\t有制表符\\反斜杠"

        val restored = NativeMemoryStore.unescape(NativeMemoryStore.escape(raw))

        assertEquals(raw, restored)
    }

    @Test
    fun escapedLineNeverContainsRawTabsOrNewlines() {
        val escaped = NativeMemoryStore.escape("a\tb\nc")

        assertTrue(!escaped.contains('\t'))
        assertTrue(!escaped.contains('\n'))
    }

    @Test
    fun loadParsesTsvAndSortsNewestFirst() {
        val file = File.createTempFile("memories", ".tsv").apply { deleteOnExit() }
        file.writeText(
            listOf(
                "v1\tm1\tnormal\t1000\t2026-01-01 00:00\tbuild\t旧记忆\t旧内容",
                "v1\tm2\tcore\t2000\t2026-01-02 00:00\tprefs\t名字\t叫我小陈",
                "not a valid line",
            ).joinToString("\n"),
            Charsets.UTF_8,
        )

        val memories = NativeMemoryStore.load(file)

        assertEquals(2, memories.size)
        assertEquals("m2", memories[0].id)
        assertEquals(NativeMemoryTier.CORE, memories[0].tier)
        assertEquals(listOf("prefs"), memories[0].tags)
        assertEquals("叫我小陈", memories[0].body)
        assertEquals(NativeMemoryTier.NORMAL, memories[1].tier)
    }

    @Test
    fun indexLineContainsDateTitleAndTags() {
        val memory = NativeMemory(
            id = "m1",
            tier = NativeMemoryTier.NORMAL,
            createdAt = 1_700_000_000_000L,
            dateLabel = "2026-11-19 10:00",
            tags = listOf("build", "gradle"),
            title = "构建失败",
            body = "细节",
        )

        val line = NativeMemoryStore.indexLine(memory)
        val expectedDay = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(memory.createdAt))

        assertTrue(line.startsWith("- [$expectedDay]"))
        assertTrue(line.contains("构建失败"))
        assertTrue(line.contains("#build"))
        assertTrue(line.contains("#gradle"))
    }
}
