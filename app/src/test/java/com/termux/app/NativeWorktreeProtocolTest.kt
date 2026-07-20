package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWorktreeProtocolTest {
    @Test
    fun parsesPorcelainWorktreeList() {
        val entries = NativeWorktreeProtocol.parse("""
            worktree /repo
            HEAD abc123
            branch refs/heads/main

            worktree /home/.fcode/worktrees/task
            HEAD def456
            branch refs/heads/codex/task
            locked
        """.trimIndent())
        assertEquals(2, entries.size)
        assertEquals("main", entries.first().branch)
        assertEquals("codex/task", entries.last().branch)
        assertTrue(entries.last().locked)
        assertFalse(entries.last().detached)
    }

    @Test
    fun normalizesBranchAndFilesystemSlug() {
        assertEquals("feature/login-fix", NativeWorktreeProtocol.normalizeBranch(" feature/login fix ", 1L))
        assertEquals("feature-login-fix", NativeWorktreeProtocol.pathSlug("feature/login-fix"))
        assertEquals("codex/task-42", NativeWorktreeProtocol.normalizeBranch("", 42L))
    }
}
