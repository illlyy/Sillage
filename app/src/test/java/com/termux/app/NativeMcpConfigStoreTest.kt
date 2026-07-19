package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMcpConfigStoreTest {
    @Test
    fun parsesWebUiCompatibleStdioAndHttpServers() {
        val source = """
            model = "demo"

            [mcp_servers.context7]
            command = "npx"
            args = ["-y", "@upstash/context7-mcp"]
            env = { "API_KEY" = "secret" }
            enabled = false

            [mcp_servers.figma]
            url = "https://mcp.figma.com/mcp"
            bearer_token_env_var = "FIGMA_TOKEN"
            http_headers = { "X-Region" = "cn" }
            startup_timeout_sec = 20
        """.trimIndent()

        val servers = NativeMcpConfigStore.parse(source)

        assertEquals(2, servers.size)
        assertEquals(listOf("-y", "@upstash/context7-mcp"), servers[0].args)
        assertEquals("secret", servers[0].env["API_KEY"])
        assertFalse(servers[0].enabled)
        assertTrue(servers[1].isHttp)
        assertEquals("FIGMA_TOKEN", servers[1].bearerTokenEnvVar)
        assertEquals("20", servers[1].startupTimeoutSec)
    }

    @Test
    fun replacementPreservesUnrelatedConfigurationAndNestedMaps() {
        val source = """
            model = "demo"

            [mcp_servers.docs]
            command = "docs-server"

            [mcp_servers.docs.env]
            DOCS_TOKEN = "value"

            [model_providers.demo]
            base_url = "https://example.com"
        """.trimIndent()
        val server = NativeMcpServerConfig(
            key = "remote docs",
            url = "https://developers.openai.com/mcp",
            httpHeaders = mapOf("X-Test" to "yes"),
            enabledTools = listOf("search", "fetch"),
        )

        val rendered = NativeMcpConfigStore.replaceMcpTables(source, listOf(server))
        val reparsed = NativeMcpConfigStore.parse(rendered).single()

        assertTrue(rendered.contains("[model_providers.demo]"))
        assertFalse(rendered.contains("docs-server"))
        assertTrue(rendered.contains("[mcp_servers.\"remote docs\"]"))
        assertEquals("yes", reparsed.httpHeaders["X-Test"])
        assertEquals(listOf("search", "fetch"), reparsed.enabledTools)
    }

    @Test
    fun roundTripsAllSupportedWebUiFields() {
        val server = NativeMcpServerConfig(
            key = "remote",
            enabled = true,
            required = true,
            url = "https://example.com/mcp",
            bearerTokenEnvVar = "MCP_TOKEN",
            httpHeaders = mapOf("X-Static" to "value"),
            envHttpHeaders = mapOf("Authorization" to "AUTH_HEADER"),
            startupTimeoutSec = "12.5",
            toolTimeoutSec = "90",
            enabledTools = listOf("search"),
            disabledTools = listOf("delete"),
            approvalMode = "prompt",
        )

        val parsed = NativeMcpConfigStore.parse(NativeMcpConfigStore.replaceMcpTables("model = \"demo\"", listOf(server))).single()

        assertEquals(server, parsed)
    }

    @Test
    fun buildsExplicitThreadConfigForNativeAppServer() {
        val config = NativeMcpConfigStore.threadConfig(
            listOf(
                NativeMcpServerConfig(
                    key = "docs",
                    url = "https://example.com/mcp",
                    enabledTools = listOf("search"),
                    startupTimeoutSec = "15",
                ),
            ),
        )

        val server = config.getJSONObject("mcp_servers").getJSONObject("docs")
        assertEquals("https://example.com/mcp", server.getString("url"))
        assertEquals("search", server.getJSONArray("enabled_tools").getString(0))
        assertEquals(15.0, server.getDouble("startup_timeout_sec"), 0.0)
    }
}
