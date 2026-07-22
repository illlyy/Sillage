package com.termux.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeGitRemoteProtocolTest {
    @Test
    fun parsesRemotesAndRemovesHttpsCredentials() {
        val remotes = NativeGitRemoteProtocol.parseRemotes("""
            origin  https://secret-token@github.com/example/repo.git?access_token=hidden (fetch)
            origin  https://secret-token@github.com/example/repo.git?access_token=hidden (push)
        """.trimIndent())
        assertEquals(1, remotes.size)
        assertEquals("origin", remotes.single().name)
        assertEquals("https://github.com/example/repo.git", remotes.single().pushUrl)
        assertFalse(remotes.single().pushUrl.contains("secret-token"))
        assertFalse(remotes.single().pushUrl.contains("access_token"))
    }

    @Test
    fun parsesCommitHistoryRecords() {
        val history = NativeGitRemoteProtocol.parseHistory(
            "abc\u001fabc1234\u001fAda\u001f1700000000\u001fAdd feature\u001fHEAD -> main\u001e",
        )
        assertEquals(1, history.length())
        assertEquals("Add feature", history.getJSONObject(0).getString("subject"))
    }

    @Test
    fun createsGithubCompareLinkAndHandoffMarkdown() {
        val raw = NativeGitRemoteProtocol.buildHandoff(
            "/repo/task", "codex/feature", "main", "origin",
            "git@github.com:example/repo.git", 2, 0,
            "- abc1234 Add feature", "1 file changed", "M\tapp.kt", false,
        )
        val handoff = JSONObject(raw)
        assertEquals("https://github.com/example/repo/compare/main...codex%2Ffeature?expand=1", handoff.getString("prUrl"))
        assertTrue(handoff.getString("markdown").contains("git push -u origin codex/feature"))
        assertTrue(handoff.getString("markdown").contains("M\tapp.kt"))
    }
}
