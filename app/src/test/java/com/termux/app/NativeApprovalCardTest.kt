package com.termux.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Approval used to be a floating banner parented to the bottom column but painted under the top
 * app bar: its Deny button was unreachable and a wedged request froze the whole chat surface. It is
 * now a message-flow card, which means the request has to survive a round trip through the
 * transcript — so encoding, identity and the summary text are all load-bearing.
 */
class NativeApprovalCardTest {

    private val claudeRequest = """
        {"method":"can_use_tool","params":{"tool_name":"Bash","command":"rm -rf /tmp/x","reason":"cleanup",
         "toolUseId":"toolu_abc","availableDecisions":["accept","acceptForSession","decline"]}}
    """.trimIndent()

    private val codexRequest = """
        {"method":"execCommandApproval","requestId":"req-1","params":{"command":["ls","-la"]}}
    """.trimIndent()

    @Test
    fun `approval content round trips through the message prefix`() {
        val encoded = encodeNativeApproval(claudeRequest, "acceptForSession")
        assertTrue(isNativeApprovalActivity(encoded))
        val decoded = decodeNativeApproval(encoded)!!
        assertEquals(claudeRequest, decoded.first)
        assertEquals("acceptForSession", decoded.second)
    }

    @Test
    fun `a pending card encodes an empty decision`() {
        val decoded = decodeNativeApproval(encodeNativeApproval(codexRequest, ""))!!
        assertEquals("", decoded.second)
    }

    @Test
    fun `malformed content decodes to null instead of throwing`() {
        assertNull(decodeNativeApproval("APPROVAL|not-base64!!"))
        assertFalse(isNativeApprovalActivity("PROCESS2|abc"))
    }

    /** Claude nests the id under `params`; Codex puts it at the top level. */
    @Test
    fun `request identity reads both wire shapes`() {
        assertEquals("toolu_abc", nativeApprovalRequestId(claudeRequest))
        assertEquals("req-1", nativeApprovalRequestId(codexRequest))
        assertEquals("", nativeApprovalRequestId("{}"))
    }

    @Test
    fun `a command approval still describes the command`() {
        val summary = nativeApprovalSummary(claudeRequest, "zh")
        assertEquals("允许执行命令？", summary.title)
        assertTrue(summary.command.contains("rm -rf"))
        assertTrue(summary.allowForSession)
    }

    /**
     * Every non-command request used to be announced as "allow file changes?" — including
     * read-only tools, which teaches the user to approve without reading the text.
     */
    @Test
    fun `a read-only tool approval names the tool instead of claiming a file write`() {
        val raw = """{"method":"can_use_tool","params":{"tool_name":"WebFetch","title":"Fetch https://x.dev"}}"""
        val summary = nativeApprovalSummary(raw, "zh")
        assertEquals("允许使用工具？", summary.title)
        assertTrue(summary.detail.contains("抓取网页"))
        assertTrue(summary.detail.contains("Fetch https://x.dev"))
    }

    @Test
    fun `a write tool approval still describes a file write and shows the path`() {
        val raw =
            """{"method":"can_use_tool","params":{"tool_name":"Write","input":{"file_path":"/other/x.txt"}}}"""
        val summary = nativeApprovalSummary(raw, "zh")
        assertEquals("允许修改文件？", summary.title)
        assertEquals("/other/x.txt", summary.command)
        // Must not claim the write is outside the workspace: the card shows the real path, and the
        // old wording was wrong for a file inside it.
        assertTrue(summary.detail.contains("写入文件"))
        assertFalse(summary.detail.contains("工作区以外"))
    }

    /** Only an unrecognised payload falls back to the legacy guess. */
    @Test
    fun `an unknown payload keeps the legacy wording`() {
        val summary = nativeApprovalSummary("""{"method":"something/else","params":{}}""", "zh")
        assertEquals("允许修改文件？", summary.title)
        assertTrue(summary.detail.contains("工作区以外"))
    }

    @Test
    fun `session memory is advertised from the available decisions`() {
        assertTrue(nativeApprovalAllowsSession(JSONObject(claudeRequest).optJSONObject("params")!!))
        val withoutSession = JSONObject("""{"availableDecisions":["accept","decline"]}""")
        assertFalse(nativeApprovalAllowsSession(withoutSession))
    }
}
