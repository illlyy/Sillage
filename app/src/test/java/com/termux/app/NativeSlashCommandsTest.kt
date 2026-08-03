package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSlashCommandsTest {

    @Test
    fun triggerDetectsSlashAtLineStart() {
        val range = nativeSlashTriggerRange("/ls", 3)
        assertEquals(0..2, range)
    }

    @Test
    fun triggerDetectsSlashAfterWhitespace() {
        val range = nativeSlashTriggerRange("fix the build /ls", 17)
        assertEquals(14..16, range)
    }

    @Test
    fun triggerRejectsSlashMidWord() {
        assertNull(nativeSlashTriggerRange("foo/bar", 7))
        assertNull(nativeSlashTriggerRange("", 0))
    }

    @Test
    fun triggerRejectsInsideFencedCodeBlock() {
        val text = "```\ncode\n```\nnot code /ls"
        // At the end, fences are even (2) -> trigger works.
        assertTrue(nativeSlashTriggerRange(text, text.length) != null)
        // Inside the block (after the opening fence only), fences are odd -> no trigger.
        val inside = "```\ncode /ls"
        assertNull(nativeSlashTriggerRange(inside, inside.length))
    }

    @Test
    fun orderingPrefersMostUsed() {
        val commands = listOf(
            NativeSlashCommand("a", "alpha", ""),
            NativeSlashCommand("b", "beta", ""),
        )
        val usage = mapOf("b" to 5, "a" to 1)
        val ordered = orderSlashCommands(commands, usage)
        assertEquals(listOf("b", "a"), ordered.map { it.id })
    }

    @Test
    fun filteringIsPrefixFirstThenSubstring() {
        val commands = listOf(
            NativeSlashCommand("search", "/search ", ""),
            NativeSlashCommand("ls", "/ls", ""),
            NativeSlashCommand("sessions", "/sessions", ""),
        )
        // Users type the slash, so the query carries it too; "/ls" contains no "/s".
        val result = filterSlashCommands(commands, "/s")
        assertEquals(listOf("search", "sessions"), result.map { it.id })
    }

    @Test
    fun blankQueryReturnsAll() {
        val commands = listOf(NativeSlashCommand("a", "x", ""), NativeSlashCommand("b", "y", ""))
        assertEquals(2, filterSlashCommands(commands, "  ").size)
    }
}
