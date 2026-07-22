package com.termux.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NativeGitWorkflowTest {
    @Test
    fun parsesBranchAndWorkingTreeBuckets() {
        val result = JSONObject(NativeGitWorkflow.parse("/repo", """
            ## main...origin/main [ahead 2, behind 1]
             M app/A.kt
            M  app/B.kt
            ?? app/New.kt
            R  old.txt -> new.txt
        """.trimIndent()))

        assertEquals("main", result.getString("branch"))
        assertEquals("origin/main", result.getString("upstream"))
        assertEquals(2, result.getInt("ahead"))
        assertEquals(1, result.getInt("behind"))
        assertFalse(result.getBoolean("clean"))
        val entries = result.getJSONArray("entries")
        assertEquals(4, entries.length())
        assertEquals(false, entries.getJSONObject(0).getBoolean("staged"))
        assertEquals(true, entries.getJSONObject(0).getBoolean("unstaged"))
        assertEquals(true, entries.getJSONObject(1).getBoolean("staged"))
        assertEquals("new.txt", entries.getJSONObject(3).getString("path"))
    }

    @Test
    fun normalizesUnbornBranchHeading() {
        val result = JSONObject(NativeGitWorkflow.parse("/repo", "## No commits yet on main\n?? README.md"))
        assertEquals("main", result.getString("branch"))
        assertEquals("add", result.getJSONArray("entries").getJSONObject(0).getString("operation"))
    }

    @Test
    fun normalizesDetachedHeadHeading() {
        val result = JSONObject(NativeGitWorkflow.parse("/repo", "## HEAD (no branch)"))
        assertEquals("", result.getString("branch"))
    }

    @Test
    fun countsChangedLinesAcrossStagedAndUnstagedDiffs() {
        val result = JSONObject(NativeGitWorkflow.diffSnapshot(
            "--- a/A.kt\n+++ b/A.kt\n@@ -1 +1,2 @@\n-old\n+new\n+next",
            "--- a/B.kt\n+++ b/B.kt\n@@ -1 +1 @@\n-before\n+after",
        ))
        assertEquals(3, result.getInt("additions"))
        assertEquals(2, result.getInt("deletions"))
    }

    @Test
    fun decodesQuotedPathsUsedByPorcelainOutput() {
        val result = JSONObject(NativeGitWorkflow.parse("/repo", "## main\n M \"docs/my file.md\""))
        assertEquals("docs/my file.md", result.getJSONArray("entries").getJSONObject(0).getString("path"))
    }
}
