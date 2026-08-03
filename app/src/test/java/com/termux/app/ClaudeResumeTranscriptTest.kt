package com.termux.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClaudeResumeTranscriptTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "claude-resume-${System.nanoTime()}")

    private fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun classicProjectsLayoutIsFound() {
        val cwd = File(tempDir, "projects/abc123").apply { mkdirs() }
        File(cwd, "019fc2a1-dead-beef.jsonl").writeText("{}")
        assertTrue(ClaudeAgentBridge.resumeTranscriptExists(tempDir, "019fc2a1-dead-beef"))
        cleanup()
    }

    @Test
    fun newerSessionsLayoutIsFound() {
        File(File(tempDir, "sessions"), "019fc2a1-dead-beef.jsonl").apply {
            parentFile.mkdirs()
            writeText("{}")
        }
        assertTrue(ClaudeAgentBridge.resumeTranscriptExists(tempDir, "019fc2a1-dead-beef"))
        cleanup()
    }

    @Test
    fun ghostSessionIdIsNotFound() {
        val cwd = File(tempDir, "projects/abc123").apply { mkdirs() }
        File(cwd, "another-id.jsonl").writeText("{}")
        assertFalse(ClaudeAgentBridge.resumeTranscriptExists(tempDir, "019fc2a1-dead-beef"))
        cleanup()
    }

    @Test
    fun emptyArgsAreRejected() {
        assertFalse(ClaudeAgentBridge.resumeTranscriptExists(null, "x"))
        assertFalse(ClaudeAgentBridge.resumeTranscriptExists(tempDir, ""))
        assertFalse(ClaudeAgentBridge.resumeTranscriptExists(tempDir, null))
    }

    @Test
    fun missingConfigDirIsNotFound() {
        assertFalse(ClaudeAgentBridge.resumeTranscriptExists(File(tempDir, "does-not-exist"), "019fc2a1-dead-beef"))
        cleanup()
    }
}
