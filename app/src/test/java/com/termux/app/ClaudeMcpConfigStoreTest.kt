package com.termux.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the ~/.claude.json mcpServers JSON table: parsing, rendering, atomic table edits. */
class ClaudeMcpConfigStoreTest {

    private fun server(
        key: String = "filesystem",
        command: String = "npx",
        args: List<String> = listOf("-y", "@modelcontextprotocol/server-filesystem"),
        enabled: Boolean = true,
    ) = NativeMcpServerConfig(
        key = key, enabled = enabled, command = command, args = args,
        env = mapOf("API_KEY" to "v1"), cwd = "/home/user",
    )

    private fun root(vararg extra: Pair<String, JSONObject>) = JSONObject().apply {
        put("mcpServers", JSONObject())
        extra.forEach { (key, value) -> put(key, value) }
    }

    @Test
    fun `load parses stdio and http servers`() {
        val table = JSONObject()
            .put("filesystem", JSONObject()
                .put("command", "npx")
                .put("args", JSONArrayOf("-y", "@modelcontextprotocol/server-filesystem"))
                .put("env", JSONObject().put("KEY", "value"))
                .put("cwd", "/home/user"))
            .put("web", JSONObject().put("type", "http").put("url", "https://example.com/mcp"))
            .put("off", JSONObject().put("command", "echo").put("disabled", true))
        val parsed = ClaudeMcpConfigStore.parseServers(JSONObject().put("mcpServers", table))

        assertEquals(3, parsed.size)
        val fs = parsed.first { it.key == "filesystem" }
        assertEquals("npx", fs.command)
        assertEquals(listOf("-y", "@modelcontextprotocol/server-filesystem"), fs.args)
        assertEquals(mapOf("KEY" to "value"), fs.env)
        assertEquals("/home/user", fs.cwd)
        assertTrue(fs.enabled)
        val web = parsed.first { it.key == "web" }
        assertTrue(web.isHttp)
        assertEquals("https://example.com/mcp", web.url)
        assertFalse(parsed.first { it.key == "off" }.enabled)
    }

    @Test
    fun `legacy url without type is treated as http`() {
        val parsed = ClaudeMcpConfigStore.parseServers(
            JSONObject().put("mcpServers",
                JSONObject().put("sse", JSONObject().put("url", "http://localhost:8000/sse"))),
        )
        assertTrue(parsed.single().isHttp)
        assertEquals("http://localhost:8000/sse", parsed.single().url)
    }

    @Test
    fun `empty or null root yields no servers`() {
        assertTrue(ClaudeMcpConfigStore.parseServers(null).isEmpty())
        assertTrue(ClaudeMcpConfigStore.parseServers(JSONObject()).isEmpty())
    }

    @Test
    fun `save merges into the table and preserves unrelated fields`() {
        val existing = root("projects" to JSONObject().put("count", 42))
        val merged = ClaudeMcpConfigStore.mergeServer(existing, server())
        val servers = merged.optJSONObject("mcpServers")!!
        val entry = servers.optJSONObject("filesystem")!!
        assertEquals("npx", entry.optString("command"))
        assertEquals(listOf("-y", "@modelcontextprotocol/server-filesystem"), jsonArray(entry.optJSONArray("args")))
        assertEquals("v1", entry.optJSONObject("env").optString("API_KEY"))
        assertEquals("/home/user", entry.optString("cwd"))
        // Unrelated top-level fields survive.
        assertEquals(42, merged.optJSONObject("projects").optInt("count"))
        // The original root is untouched (immutable helper contract).
        assertNull(existing.optJSONObject("mcpServers").optJSONObject("filesystem"))
    }

    @Test
    fun `save renames when previousKey differs`() {
        val existing = root()
        ClaudeMcpConfigStore.mergeServer(existing, server(key = "fs2"), previousKey = "fs1")
        // Provide the original name first to prove rename removes it.
        val withOld = ClaudeMcpConfigStore.mergeServer(existing, server(key = "fs1"))
        val renamed = ClaudeMcpConfigStore.mergeServer(withOld, server(key = "fs2"), previousKey = "fs1")
        val servers = renamed.optJSONObject("mcpServers")!!
        assertNull(servers.optJSONObject("fs1"))
        assertTrue(servers.has("fs2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `save rejects a duplicate key`() {
        val existing = ClaudeMcpConfigStore.mergeServer(root(), server(key = "dup"))
        ClaudeMcpConfigStore.mergeServer(existing, server(key = "dup", command = "other"))
    }

    @Test
    fun `remove deletes only the target key`() {
        val existing = ClaudeMcpConfigStore.mergeServer(root(), server(key = "a"))
        val withB = ClaudeMcpConfigStore.mergeServer(existing, server(key = "b"))
        val removed = ClaudeMcpConfigStore.removeServer(withB, "a")
        val servers = removed.optJSONObject("mcpServers")!!
        assertFalse(servers.has("a"))
        assertTrue(servers.has("b"))
        // Removing a missing key is a no-op.
        assertEquals(2, ClaudeMcpConfigStore.removeServer(withB, "nope").optJSONObject("mcpServers").length())
    }

    @Test
    fun `setEnabled toggles the disabled flag`() {
        val existing = ClaudeMcpConfigStore.mergeServer(root(), server(key = "x", enabled = true))
        val disabled = ClaudeMcpConfigStore.setServerEnabled(existing, "x", false)
        assertTrue(disabled.optJSONObject("mcpServers").optJSONObject("x").optBoolean("disabled"))
        val reEnabled = ClaudeMcpConfigStore.setServerEnabled(disabled, "x", true)
        assertFalse(reEnabled.optJSONObject("mcpServers").optJSONObject("x").has("disabled"))
    }

    @Test
    fun `render http server emits type and url`() {
        val http = NativeMcpServerConfig(key = "web", url = "https://example.com/mcp", enabled = false)
        val rendered = ClaudeMcpConfigStore.renderServer(http)
        assertEquals("http", rendered.optString("type"))
        assertEquals("https://example.com/mcp", rendered.optString("url"))
        assertTrue(rendered.optBoolean("disabled"))
    }

    private fun JSONArrayOf(vararg values: String) = org.json.JSONArray(values.toList())
    private fun jsonArray(array: org.json.JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) array.optString(index)?.let(::add)
    }
}