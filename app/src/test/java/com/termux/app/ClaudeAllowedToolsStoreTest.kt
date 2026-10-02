package com.termux.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ClaudeAllowedToolsStoreTest {

    private fun prefs() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("codex_mobile", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        prefs().edit().clear().apply()
    }

    @Test
    fun `toAllowedPattern keeps non-bash verbatim`() {
        assertEquals("Write", ClaudeAllowedToolsStore.toAllowedPattern("Write", ""))
        assertEquals("Edit", ClaudeAllowedToolsStore.toAllowedPattern("Edit", "foo"))
    }

    @Test
    fun `toAllowedPattern narrows bash to head`() {
        assertEquals("Bash", ClaudeAllowedToolsStore.toAllowedPattern("Bash", ""))
        assertEquals("Bash(ls:*)", ClaudeAllowedToolsStore.toAllowedPattern("Bash", "ls -la"))
        assertEquals("Bash(git push:*)", ClaudeAllowedToolsStore.toAllowedPattern("Bash", "git push origin main"))
        assertEquals("Bash(git status:*)", ClaudeAllowedToolsStore.toAllowedPattern("Shell", "git status"))
    }

    @Test
    fun `extractPattern handles claude flat and codex wrapped shapes`() {
        val flat = """{"tool_name":"Bash","command":"git push origin"}"""
        assertEquals("Bash(git push:*)", ClaudeAllowedToolsStore.extractPatternFromApprovalRaw(flat))
        val wrapped = """{"params":{"tool_name":"Write","command":""}}"""
        assertEquals("Write", ClaudeAllowedToolsStore.extractPatternFromApprovalRaw(wrapped))
        assertEquals("", ClaudeAllowedToolsStore.extractPatternFromApprovalRaw("{}"))
    }

    @Test
    fun `merge dedupes base and remembered`() {
        assertEquals("Read,Bash(ls:*),Write", ClaudeAllowedToolsStore.merge("Read,Bash(ls:*),Read", setOf("Write", "Read")))
    }

    @Test
    fun `load and add round-trip per profile`() {
        assertTrue(ClaudeAllowedToolsStore.load(prefs(), "p1").isEmpty())
        ClaudeAllowedToolsStore.add(prefs(), "p1", "Write")
        ClaudeAllowedToolsStore.add(prefs(), "p1", "Bash(git push:*)")
        assertEquals(setOf("Write", "Bash(git push:*)"), ClaudeAllowedToolsStore.load(prefs(), "p1"))
        assertTrue(ClaudeAllowedToolsStore.load(prefs(), "p2").isEmpty())
    }
}
