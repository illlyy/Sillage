package com.termux.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Verifies the cc-switch style settings.json rendering and legacy profile migration. */
class ClaudeSettingsWriterTest {

    private fun profile(
        apiKey: String = "sk-test",
        apiKeyField: String = ClaudeSettingsWriter.FIELD_AUTH_TOKEN,
        baseUrl: String = "",
        model: String = "",
        haiku: String = "",
        sonnet: String = "",
        opus: String = "",
        subagent: String = "",
        extra: Map<String, String> = emptyMap(),
    ) = ClaudeProfile(
        id = "p1", name = "Test", apiKey = apiKey, apiKeyField = apiKeyField,
        baseUrl = baseUrl, model = model, haikuModel = haiku, sonnetModel = sonnet,
        opusModel = opus, subagentModel = subagent, extraEnv = extra,
    )

    @Test
    fun `auth token mode writes AUTH_TOKEN key`() {
        val env = ClaudeSettingsWriter.buildEnv(profile(apiKey = "sk-tok"))
        assertTrue(env.containsKey("ANTHROPIC_AUTH_TOKEN"))
        assertEquals("sk-tok", env["ANTHROPIC_AUTH_TOKEN"])
        assertFalse(env.containsKey("ANTHROPIC_API_KEY"))
    }

    @Test
    fun `api key mode writes API_KEY key`() {
        val env = ClaudeSettingsWriter.buildEnv(
            profile(apiKey = "sk-ant-xyz", apiKeyField = ClaudeSettingsWriter.FIELD_API_KEY),
        )
        assertTrue(env.containsKey("ANTHROPIC_API_KEY"))
        assertEquals("sk-ant-xyz", env["ANTHROPIC_API_KEY"])
        assertFalse(env.containsKey("ANTHROPIC_AUTH_TOKEN"))
    }

    @Test
    fun `tier models fall back to primary model`() {
        val env = ClaudeSettingsWriter.buildEnv(profile(model = "claude-sonnet-4-5"))
        assertEquals("claude-sonnet-4-5", env["ANTHROPIC_MODEL"])
        assertEquals("claude-sonnet-4-5", env["ANTHROPIC_DEFAULT_HAIKU_MODEL"])
        assertEquals("claude-sonnet-4-5", env["ANTHROPIC_DEFAULT_SONNET_MODEL"])
        assertEquals("claude-sonnet-4-5", env["ANTHROPIC_DEFAULT_OPUS_MODEL"])
    }

    @Test
    fun `explicit tiers and subagent are preserved`() {
        val env = ClaudeSettingsWriter.buildEnv(
            profile(model = "main", haiku = "h", sonnet = "s", opus = "o", subagent = "sa"),
        )
        assertEquals("h", env["ANTHROPIC_DEFAULT_HAIKU_MODEL"])
        assertEquals("s", env["ANTHROPIC_DEFAULT_SONNET_MODEL"])
        assertEquals("o", env["ANTHROPIC_DEFAULT_OPUS_MODEL"])
        assertEquals("sa", env["CLAUDE_CODE_SUBAGENT_MODEL"])
    }

    @Test
    fun `base url and extra env are merged`() {
        val env = ClaudeSettingsWriter.buildEnv(
            profile(baseUrl = "https://relay.example.com", extra = mapOf("ENABLE_TOOL_SEARCH" to "true", "API_TIMEOUT_MS" to "300000")),
        )
        assertEquals("https://relay.example.com", env["ANTHROPIC_BASE_URL"])
        assertEquals("true", env["ENABLE_TOOL_SEARCH"])
        assertEquals("300000", env["API_TIMEOUT_MS"])
    }

    @Test
    fun `settings json is written atomically with env block`() {
        val dir = java.nio.file.Files.createTempDirectory("claude-settings").toFile()
        try {
            val written = ClaudeSettingsWriter.write(dir, profile())
            assertTrue(written != null && written!!.isFile)
            val parsed = JSONObject(written!!.readText())
            val env = parsed.getJSONObject("env")
            assertEquals("sk-test", env.optString("ANTHROPIC_AUTH_TOKEN"))
            assertTrue(!dir.listFiles().any { it.name.endsWith(".tmp") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `blank profile removes settings file`() {
        val dir = java.nio.file.Files.createTempDirectory("claude-settings").toFile()
        try {
            val file = File(dir, "settings.json")
            file.writeText("{}")
            ClaudeSettingsWriter.write(dir, profile(apiKey = ""))
            assertFalse(file.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `legacy profile json migrates with auth token default`() {
        val legacy = JSONObject()
            .put("id", "old")
            .put("name", "Legacy")
            .put("apiKey", "sk-legacy")
            .put("baseUrl", "https://relay.example.com")
            .put("model", "claude-sonnet-4-5")
        val migrated = ClaudeProfile.fromJson("old", legacy)
        assertEquals("sk-legacy", migrated.apiKey)
        assertEquals(ClaudeSettingsWriter.FIELD_AUTH_TOKEN, migrated.apiKeyField)
        assertEquals("https://relay.example.com", migrated.baseUrl)
        assertEquals("claude-sonnet-4-5", migrated.model)
        assertTrue(migrated.haikuModel.isBlank())
        // and it renders correctly
        val env = ClaudeSettingsWriter.buildEnv(migrated)
        assertEquals("sk-legacy", env["ANTHROPIC_AUTH_TOKEN"])
    }

    @Test
    fun `sanitized profile strips blank extra env`() {
        val profile = ClaudeProfile(
            id = "p", name = "x", apiKey = "k",
            extraEnv = mapOf("A" to "1", "B" to "   ", "C" to ""),
        ).sanitized()
        assertEquals(mapOf("A" to "1"), profile.extraEnv)
    }
}
