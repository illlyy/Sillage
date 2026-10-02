package com.termux.app

import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeSkillHintTest {

    @Test
    fun `hint includes description and caps count`() {
        val json = """[{"name":"pdf","description":"Read and summarize PDF files quickly"},{"name":"code-review"}]"""
        val hint = ClaudeAgentBridge.skillHintText(json)
        assertTrue(hint.contains("pdf"))
        assertTrue(hint.contains("Read and summarize PDF"))
        assertTrue(hint.contains("code-review"))
    }

    @Test
    fun `hint empty on blank`() {
        assertTrue(ClaudeAgentBridge.skillHintText("").isEmpty())
        assertTrue(ClaudeAgentBridge.skillHintText("[]").isEmpty())
        assertTrue(ClaudeAgentBridge.skillHintText(null).isEmpty())
    }
}
