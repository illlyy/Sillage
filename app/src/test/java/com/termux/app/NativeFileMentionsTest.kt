package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NativeFileMentionsTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "native-mentions-${System.nanoTime()}")

    private fun buildTree() {
        tempDir.mkdirs()
        File(tempDir, "src").mkdirs()
        File(tempDir, "docs").mkdirs()
        File(tempDir, "src/Main.kt").writeText("")
        File(tempDir, "src/Util.kt").writeText("")
        File(tempDir, "docs/readme.md").writeText("")
        File(tempDir, "README.md").writeText("")
    }

    private fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun triggerDetectsAtMention() {
        val range = nativeMentionTriggerRange("see @src/M", 10)
        assertEquals(4..9, range)
    }

    @Test
    fun triggerRejectsMidWordAndBareAt() {
        assertNull(nativeMentionTriggerRange("foo@bar", 7))
        assertNull(nativeMentionTriggerRange("say @", 5))
        assertNull(nativeMentionTriggerRange("", 0))
    }

    @Test
    fun triggerTakesLatestAt() {
        val range = nativeMentionTriggerRange("a @x and @src", 13)
        assertEquals(9..12, range)
    }

    @Test
    fun scanCollectsRelativePathsBounded() {
        buildTree()
        val files = nativeScanMentionFiles(tempDir)
        assertTrue(files.contains("src/Main.kt"))
        assertTrue(files.contains("docs/readme.md"))
        assertTrue(files.contains("README.md"))
        cleanup()
    }

    @Test
    fun scanRespectsDepthAndCountBounds() {
        buildTree()
        val shallow = nativeScanMentionFiles(tempDir, maxDepth = 0)
        assertEquals(listOf("README.md"), shallow)
        val tiny = nativeScanMentionFiles(tempDir, maxFiles = 1)
        assertEquals(1, tiny.size)
        cleanup()
    }

    @Test
    fun filterPrefersNameMatchesOverPathMatches() {
        val files = listOf("src/main/Utils.kt", "Main.kt", "other/Elsewhere.kt")
        val result = filterMentionFiles(files, "main", maxResults = 10)
        // Name matches come before path-only matches.
        assertEquals("Main.kt", result.first())
        assertTrue(result.contains("src/main/Utils.kt"))
        assertEquals(2, result.size)
    }

    @Test
    fun blankQueryReturnsHead() {
        val files = (1..20).map { "f$it.kt" }
        assertEquals(10, filterMentionFiles(files, "  ").size)
    }

    @Test
    fun applyReplacesMentionWithPath() {
        val text = "see @src/M"
        val range = nativeMentionTriggerRange(text, 11)!!
        assertEquals("see src/Main.kt ", nativeApplyMention(text, range, "src/Main.kt"))
    }
}
