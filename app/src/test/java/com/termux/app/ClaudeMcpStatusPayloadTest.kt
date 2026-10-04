package com.termux.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Claude bridge answers `mcp_status` with the CLI's own `mcpServers` shape, but the MCP page and
 * the work panel read the shared runtime-status payload (`result.data[]`). These cover the
 * translation that lets one UI serve both backends.
 */
class ClaudeMcpStatusPayloadTest {

    private fun servers(json: String) = JSONArray(json)

    @Test
    fun mapsTheCliServerListIntoTheSharedPayloadShape() {
        val payload = ClaudeAgentBridge.mcpStatusPayload(
            servers("""[{"name":"context7","status":"connected"},{"name":"sentry","status":"failed"}]"""),
        )
        val data = payload.optJSONObject("result").optJSONArray("data")
        assertEquals(2, data.length())
        assertEquals("context7", data.optJSONObject(0).optString("name"))
        assertEquals("connected", data.optJSONObject(0).optString("status"))
        assertEquals("failed", data.optJSONObject(1).optString("status"))
    }

    @Test
    fun keepsAToolListSoTheBadgeCanCountTools() {
        val payload = ClaudeAgentBridge.mcpStatusPayload(
            servers("""[{"name":"context7","status":"connected","tools":["search","fetch"]}]"""),
        )
        val entry = payload.optJSONObject("result").optJSONArray("data").optJSONObject(0)
        assertEquals(2, entry.optJSONArray("tools").length())
    }

    @Test
    fun keepsAnErrorMessageSoTheBadgeCanExplainFailure() {
        val payload = ClaudeAgentBridge.mcpStatusPayload(
            servers("""[{"name":"sentry","status":"failed","error":"spawn ENOENT"}]"""),
        )
        val entry = payload.optJSONObject("result").optJSONArray("data").optJSONObject(0)
        assertEquals("spawn ENOENT", entry.optString("error"))
    }

    @Test
    fun skipsEntriesWithoutAName() {
        val payload = ClaudeAgentBridge.mcpStatusPayload(servers("""[{"status":"connected"},{"name":"ok"}]"""))
        assertEquals(1, payload.optJSONObject("result").optJSONArray("data").length())
    }

    @Test
    fun toleratesAnEmptyOrMissingList() {
        assertEquals(0, ClaudeAgentBridge.mcpStatusPayload(servers("[]"))
            .optJSONObject("result").optJSONArray("data").length())
        assertEquals(0, ClaudeAgentBridge.mcpStatusPayload(null)
            .optJSONObject("result").optJSONArray("data").length())
    }

    /**
     * `pending` is the CLI's "still handshaking". Treating it as settled would freeze every server
     * at 检测中, so the bridge re-probes on the strength of this flag.
     */
    @Test
    fun detectsStillHandshakingServers() {
        assertTrue(
            ClaudeAgentBridge.hasPendingServer(
                ClaudeAgentBridge.mcpStatusPayload(servers("""[{"name":"a","status":"pending"}]""")),
            ),
        )
        assertFalse(
            ClaudeAgentBridge.hasPendingServer(
                ClaudeAgentBridge.mcpStatusPayload(servers("""[{"name":"a","status":"connected"}]""")),
            ),
        )
        assertFalse(ClaudeAgentBridge.hasPendingServer(JSONObject()))
    }
}
