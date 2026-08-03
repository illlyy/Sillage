package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlinx.coroutines.runBlocking

class NativeConversationFullTextSearchTest {

    private val tempDir: File = File(System.getProperty("java.io.tmpdir"), "native-search-${System.nanoTime()}")

    private fun sessionFile(threadId: String, lines: List<String>): File {
        tempDir.mkdirs()
        val file = File(tempDir, "12345678-$threadId.jsonl")
        file.writeText(lines.joinToString("\n"))
        return file
    }

    private fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun snippetWindowsAroundFirstOccurrence() {
        val line = "The quick brown fox jumps over the lazy dog near the river"
        val snippet = buildNativeSearchSnippet(line, "fox", radius = 10)
        assertEquals("…" + "ick brown fox jumps ove" + "…", snippet)
    }

    @Test
    fun snippetShortLineStaysUnclipped() {
        val line = "hello world"
        assertEquals(line, buildNativeSearchSnippet(line, "hello"))
    }

    @Test
    fun snippetWithoutMatchClipsToRadius() {
        val line = "abcdefghijklmnopqrstuvwxyz"
        val snippet = buildNativeSearchSnippet(line, "zzz", radius = 5)
        assertEquals("abcdefghij…", snippet)
    }

    @Test
    fun searchMatchesCaseInsensitivelyAndBoundedPerFile() {
        val file = sessionFile(
            "t1",
            (1..100).map { "line $it contains needle value" },
        )
        val hits = searchNativeSessionFile(file, "t1", "NEEDLE", maxHitsPerFile = 5)
        assertEquals(5, hits.size)
        assertTrue(hits.all { it.threadId == "t1" && it.content.contains("needle") })
        cleanup()
    }

    @Test
    fun shortQueriesAreRejected() {
        val file = sessionFile("t1", listOf("aa aa"))
        assertTrue(searchNativeSessionFile(file, "t1", "a", maxHitsPerFile = 10).isEmpty())
        cleanup()
    }

    @Test
    fun missingFileYieldsNoHits() {
        val file = File(tempDir, "nope.jsonl")
        assertTrue(searchNativeSessionFile(file, "t", "needle").isEmpty())
        cleanup()
    }

    @Test
    fun engineScansAllSessionFilesAndReportsProgress() {
        sessionFile("t1", listOf("alpha needle beta"))
        sessionFile("t2", listOf("gamma", "delta needle epsilon"))
        sessionFile("t3", listOf("nothing here"))
        val engine = NativeConversationSearchEngine(sessionsRoot = { tempDir })
        var lastProgress = 0 to 0
        val hits = runBlocking {
            engine.search("needle", progress = { scanned, total -> lastProgress = scanned to total })
        }
        assertEquals(2, hits.size)
        assertEquals(setOf("t1", "t2"), hits.map { it.threadId }.toSet())
        assertTrue(lastProgress.first > 0)
        cleanup()
    }
}
