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
        fable: String = "",
        smallFast: String = "",
        subagent: String = "",
        maxContext: String = "",
        autoCompact: String = "",
        maxOutput: String = "",
        apiTimeout: String = "",
        disableNonEssential: Boolean = false,
        maxEffort: Boolean = false,
        toolSearch: Boolean = false,
        disableUpdater: Boolean = false,
        agentTeams: Boolean = false,
        disableBetas: Boolean = false,
        coAuthored: Boolean = false,
        customJson: String = "",
        extra: Map<String, String> = emptyMap(),
    ) = ClaudeProfile(
        id = "p1", name = "Test", apiKey = apiKey, apiKeyField = apiKeyField,
        baseUrl = baseUrl, model = model, haikuModel = haiku, sonnetModel = sonnet,
        opusModel = opus, fableModel = fable, smallFastModel = smallFast,
        subagentModel = subagent,
        maxContextTokens = maxContext, autoCompactWindow = autoCompact,
        maxOutputTokens = maxOutput, apiTimeoutMs = apiTimeout,
        disableNonEssentialTraffic = disableNonEssential, maxEffort = maxEffort,
        enableToolSearch = toolSearch, disableAutoUpdater = disableUpdater,
        experimentalAgentTeams = agentTeams, disableExperimentalBetas = disableBetas,
        includeCoAuthoredBy = coAuthored, extraSettingsJson = customJson,
        extraEnv = extra,
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
        val env = ClaudeSettingsWriter.buildEnv(profile(model = "claude-sonnet-5"))
        assertEquals("claude-sonnet-5", env["ANTHROPIC_MODEL"])
        assertEquals("claude-sonnet-5", env["ANTHROPIC_DEFAULT_HAIKU_MODEL"])
        assertEquals("claude-sonnet-5", env["ANTHROPIC_DEFAULT_SONNET_MODEL"])
        assertEquals("claude-sonnet-5", env["ANTHROPIC_DEFAULT_OPUS_MODEL"])
        assertEquals("claude-sonnet-5", env["ANTHROPIC_DEFAULT_FABLE_MODEL"])
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

    @Test
    fun `fable tier and small fast model are written`() {
        val env = ClaudeSettingsWriter.buildEnv(
            profile(model = "main", fable = "claude-fable-5", smallFast = "claude-haiku-4-5"),
        )
        assertEquals("claude-fable-5", env["ANTHROPIC_DEFAULT_FABLE_MODEL"])
        assertEquals("claude-haiku-4-5", env["ANTHROPIC_SMALL_FAST_MODEL"])
    }

    @Test
    fun `fable tier falls back to primary when blank`() {
        val env = ClaudeSettingsWriter.buildEnv(profile(model = "claude-sonnet-5"))
        assertEquals("claude-sonnet-5", env["ANTHROPIC_DEFAULT_FABLE_MODEL"])
    }

    @Test
    fun `numeric tuning vars written only when non-blank`() {
        val env = ClaudeSettingsWriter.buildEnv(
            profile(model = "m", maxContext = "240000", maxOutput = "32000"),
        )
        assertEquals("240000", env["CLAUDE_CODE_MAX_CONTEXT_TOKENS"])
        assertEquals("32000", env["CLAUDE_CODE_MAX_OUTPUT_TOKENS"])
        assertFalse(env.containsKey("CLAUDE_CODE_AUTO_COMPACT_WINDOW"))
        assertFalse(env.containsKey("API_TIMEOUT_MS"))
    }

    @Test
    fun `toggles emit only when enabled`() {
        val env = ClaudeSettingsWriter.buildEnv(
            profile(model = "m", toolSearch = true, disableUpdater = true, maxEffort = false),
        )
        assertEquals("true", env["ENABLE_TOOL_SEARCH"])
        assertEquals("1", env["DISABLE_AUTOUPDATER"])
        assertFalse(env.containsKey("CLAUDE_CODE_EFFORT_LEVEL"))
        assertFalse(env.containsKey("CLAUDE_CODE_DISABLE_NON_ESSENTIAL_TRAFFIC"))
    }

    @Test
    fun `custom settings and includeCoAuthoredBy merge at root`() {
        val json = ClaudeSettingsWriter.buildSettingsJson(
            profile(model = "m", coAuthored = true, customJson = """{"permissions":{"allow":["Read"]}}"""),
        )
        val root = JSONObject(json)
        assertEquals("m", root.getJSONObject("env").getString("ANTHROPIC_MODEL"))
        assertTrue(root.getBoolean("includeCoAuthoredBy"))
        assertEquals("Read", root.getJSONObject("permissions").getJSONArray("allow").getString(0))
    }

    @Test
    fun `custom settings env key is ignored`() {
        val json = ClaudeSettingsWriter.buildSettingsJson(
            profile(model = "m", customJson = """{"env":{"FOO":"bar"},"statusLine":{"type":"command","command":"echo hi"}}"""),
        )
        val root = JSONObject(json)
        assertFalse(root.getJSONObject("env").has("FOO"))
        assertEquals("command", root.getJSONObject("statusLine").getString("type"))
    }

    @Test
    fun `includeCoAuthoredBy toggle wins over custom json`() {
        val json = ClaudeSettingsWriter.buildSettingsJson(
            profile(model = "m", coAuthored = true, customJson = """{"includeCoAuthoredBy":false}"""),
        )
        assertTrue(JSONObject(json).getBoolean("includeCoAuthoredBy"))
    }

    @Test
    fun `invalid custom settings json is ignored`() {
        val json = ClaudeSettingsWriter.buildSettingsJson(
            profile(model = "m", customJson = """{"broken":"""),
        )
        val root = JSONObject(json)
        assertEquals("m", root.getJSONObject("env").getString("ANTHROPIC_MODEL"))
        assertFalse(root.has("broken"))
    }

    @Test
    fun `modelOverride sets ANTHROPIC_MODEL and wins over extraEnv`() {
        val env = ClaudeSettingsWriter.buildEnv(
            profile(model = "main", extra = mapOf("ANTHROPIC_MODEL" to "claude-opus-4-1")),
            modelOverride = "opus",
        )
        assertEquals("opus", env["ANTHROPIC_MODEL"])
    }

    @Test
    fun `modelOverride defaults to primary when blank`() {
        val env = ClaudeSettingsWriter.buildEnv(profile(model = "claude-sonnet-5"), modelOverride = "")
        assertEquals("claude-sonnet-5", env["ANTHROPIC_MODEL"])
    }

    @Test
    fun `legacy profile json loads new fields with defaults`() {
        val legacy = JSONObject()
            .put("id", "old")
            .put("name", "Legacy")
            .put("apiKey", "sk-legacy")
        val migrated = ClaudeProfile.fromJson("old", legacy)
        assertTrue(migrated.fableModel.isBlank())
        assertTrue(migrated.smallFastModel.isBlank())
        assertTrue(migrated.maxContextTokens.isBlank())
        assertFalse(migrated.enableToolSearch)
        assertFalse(migrated.includeCoAuthoredBy)
        assertTrue(migrated.extraSettingsJson.isBlank())
    }

    @Test
    fun `sanitized trims new string fields`() {
        val cleaned = ClaudeProfile(
            id = "p", name = "x", apiKey = "k", model = "m",
            fableModel = " claude-fable-5 ",
            maxContextTokens = " 240000 ",
            extraSettingsJson = " { } ",
        ).sanitized()
        assertEquals("claude-fable-5", cleaned.fableModel)
        assertEquals("240000", cleaned.maxContextTokens)
        assertEquals("{ }", cleaned.extraSettingsJson)
    }

    @Test
    fun `write emits merged root atomically`() {
        val dir = java.nio.file.Files.createTempDirectory("claude-settings").toFile()
        try {
            val written = ClaudeSettingsWriter.write(
                dir,
                profile(model = "m", coAuthored = true, customJson = """{"permissions":{"allow":["Bash(*)"]}}"""),
            )
            assertTrue(written != null && written!!.isFile)
            val root = JSONObject(written!!.readText())
            assertEquals("m", root.getJSONObject("env").getString("ANTHROPIC_MODEL"))
            assertTrue(root.getBoolean("includeCoAuthoredBy"))
            assertEquals("Bash(*)", root.getJSONObject("permissions").getJSONArray("allow").getString(0))
            assertTrue(!dir.listFiles().any { it.name.endsWith(".tmp") })
        } finally {
            dir.deleteRecursively()
        }
    }
}
