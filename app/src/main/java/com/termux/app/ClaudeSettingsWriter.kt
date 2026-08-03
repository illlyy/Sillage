package com.termux.app

import org.json.JSONObject
import java.io.File
import java.util.TreeMap

/**
 * Renders a Claude profile into `CLAUDE_CONFIG_DIR/settings.json`, the same shape cc-switch
 * writes on desktop. The Claude CLI reads this file at startup and merges every `env` key into
 * its process environment, so tier models, subagent model and extra tuning vars all work
 * without passing anything on the command line.
 *
 * Beyond the `env` block, arbitrary top-level keys are supported: [ClaudeProfile.extraSettingsJson]
 * (a raw JSON object) and [ClaudeProfile.includeCoAuthoredBy] are merged into the root, matching
 * cc-switch's full settingsConfig passthrough.
 *
 * Writing is atomic (temp file + rename) and keys are emitted in sorted order so repeated
 * switches produce a stable diff.
 */
internal object ClaudeSettingsWriter {
    const val FIELD_AUTH_TOKEN = "ANTHROPIC_AUTH_TOKEN"
    const val FIELD_API_KEY = "ANTHROPIC_API_KEY"

    private const val ENV_MODEL = "ANTHROPIC_MODEL"
    private const val ENV_HAIKU = "ANTHROPIC_DEFAULT_HAIKU_MODEL"
    private const val ENV_SONNET = "ANTHROPIC_DEFAULT_SONNET_MODEL"
    private const val ENV_OPUS = "ANTHROPIC_DEFAULT_OPUS_MODEL"
    private const val ENV_FABLE = "ANTHROPIC_DEFAULT_FABLE_MODEL"
    private const val ENV_SMALL_FAST = "ANTHROPIC_SMALL_FAST_MODEL"
    private const val ENV_SUBAGENT = "CLAUDE_CODE_SUBAGENT_MODEL"
    private const val ENV_BASE_URL = "ANTHROPIC_BASE_URL"
    private const val ENV_MAX_CONTEXT = "CLAUDE_CODE_MAX_CONTEXT_TOKENS"
    private const val ENV_AUTO_COMPACT = "CLAUDE_CODE_AUTO_COMPACT_WINDOW"
    private const val ENV_MAX_OUTPUT = "CLAUDE_CODE_MAX_OUTPUT_TOKENS"
    private const val ENV_API_TIMEOUT = "API_TIMEOUT_MS"
    private const val ENV_DISABLE_NON_ESSENTIAL = "CLAUDE_CODE_DISABLE_NON_ESSENTIAL_TRAFFIC"
    private const val ENV_MAX_EFFORT = "CLAUDE_CODE_EFFORT_LEVEL"
    private const val ENV_TOOL_SEARCH = "ENABLE_TOOL_SEARCH"
    private const val ENV_DISABLE_AUTOUPDATER = "DISABLE_AUTOUPDATER"
    private const val ENV_AGENT_TEAMS = "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS"
    private const val ENV_DISABLE_BETAS = "CLAUDE_CODE_DISABLE_EXPERIMENTAL_BETAS"

    fun normalizeAuthField(value: String?): String = when (value) {
        FIELD_API_KEY -> FIELD_API_KEY
        else -> FIELD_AUTH_TOKEN
    }

    /**
     * Builds the sorted env block for a profile (tier fields fall back to the primary model).
     * [modelOverride] carries the chat model picker's runtime selection and, when set, wins over
     * both the profile primary and any `ANTHROPIC_MODEL` the user placed in [ClaudeProfile.extraEnv].
     */
    fun buildEnv(profile: ClaudeProfile, baseUrlOverride: String? = null, modelOverride: String? = null): Map<String, String> {
        val env = TreeMap<String, String>()
        val primary = profile.model.ifBlank { ClaudeProfile.DEFAULT_MODEL }
        if (profile.apiKey.isNotBlank()) env[profile.apiKeyField] = profile.apiKey
        // The bridge may route the musl CLI through the loopback proxy; the override then
        // replaces the real base URL so the CLI talks to 127.0.0.1 and Java owns TLS/DNS.
        val baseUrl = baseUrlOverride?.takeIf { it.isNotBlank() } ?: profile.baseUrl
        if (baseUrl.isNotBlank()) env[ENV_BASE_URL] = baseUrl
        env[ENV_MODEL] = primary
        env[ENV_HAIKU] = profile.haikuModel.ifBlank { primary }
        env[ENV_SONNET] = profile.sonnetModel.ifBlank { primary }
        env[ENV_OPUS] = profile.opusModel.ifBlank { primary }
        env[ENV_FABLE] = profile.fableModel.ifBlank { primary }
        if (profile.smallFastModel.isNotBlank()) env[ENV_SMALL_FAST] = profile.smallFastModel
        if (profile.subagentModel.isNotBlank()) env[ENV_SUBAGENT] = profile.subagentModel
        // Performance/context tuning — written verbatim, blank = omitted.
        putIfNotBlank(env, ENV_MAX_CONTEXT, profile.maxContextTokens)
        putIfNotBlank(env, ENV_AUTO_COMPACT, profile.autoCompactWindow)
        putIfNotBlank(env, ENV_MAX_OUTPUT, profile.maxOutputTokens)
        putIfNotBlank(env, ENV_API_TIMEOUT, profile.apiTimeoutMs)
        // Behavior toggles — only emitted when enabled (matches the CLI's off-by-defaults).
        if (profile.disableNonEssentialTraffic) env[ENV_DISABLE_NON_ESSENTIAL] = "1"
        if (profile.maxEffort) env[ENV_MAX_EFFORT] = "max"
        if (profile.enableToolSearch) env[ENV_TOOL_SEARCH] = "true"
        if (profile.disableAutoUpdater) env[ENV_DISABLE_AUTOUPDATER] = "1"
        if (profile.experimentalAgentTeams) env[ENV_AGENT_TEAMS] = "1"
        if (profile.disableExperimentalBetas) env[ENV_DISABLE_BETAS] = "1"
        // extraEnv keeps its escape-hatch precedence: a user key here wins over structured fields.
        profile.extraEnv.forEach { (key, value) ->
            if (key.isNotBlank()) env[key] = value
        }
        // Runtime chat selection is applied last so it is authoritative for ANTHROPIC_MODEL.
        modelOverride?.takeIf { it.isNotBlank() }?.let { env[ENV_MODEL] = it }
        return env
    }

    /**
     * Serializes the profile as the settings.json payload: the `env` block plus any top-level
     * keys from [ClaudeProfile.extraSettingsJson] and [ClaudeProfile.includeCoAuthoredBy].
     * Never throws — invalid custom JSON is skipped defensively so spawn cannot crash.
     */
    fun buildSettingsJson(profile: ClaudeProfile, baseUrlOverride: String? = null, modelOverride: String? = null): String {
        val env = JSONObject()
        buildEnv(profile, baseUrlOverride, modelOverride).forEach { (key, value) -> env.put(key, value) }
        val root = JSONObject().put("env", env)
        parseCustomSettings(profile.extraSettingsJson)?.let { custom ->
            custom.keys().forEach { key ->
                // `env` is reserved/managed by the app; never let custom JSON clobber it.
                if (key != "env") root.put(key, custom.get(key))
            }
        }
        // One-way toggle: when on it forces true (overriding a custom false); when off it adds
        // nothing, so a custom `true` from the JSON still stands.
        if (profile.includeCoAuthoredBy) root.put("includeCoAuthoredBy", true)
        return root.toString()
    }

    /**
     * Atomically writes the settings file for the active profile. Returns the written file,
     * or null when the profile is blank (nothing to write).
     */
    fun write(configDir: File, profile: ClaudeProfile?, baseUrlOverride: String? = null, modelOverride: String? = null): File? {
        val settingsFile = File(configDir, "settings.json")
        if (profile == null || profile.apiKey.isBlank()) {
            if (settingsFile.exists()) settingsFile.delete()
            return null
        }
        if (!configDir.isDirectory && !configDir.mkdirs()) return null
        val temp = File(configDir, "settings.json.tmp")
        temp.writeText(buildSettingsJson(profile, baseUrlOverride, modelOverride), Charsets.UTF_8)
        if (!temp.renameTo(settingsFile)) {
            // rename may fail on some file systems; fall back to direct write
            settingsFile.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
        return settingsFile
    }

    private fun putIfNotBlank(env: TreeMap<String, String>, key: String, value: String) {
        if (value.isNotBlank()) env[key] = value
    }

    private fun parseCustomSettings(raw: String?): JSONObject? =
        if (raw.isNullOrBlank()) null else runCatching { JSONObject(raw) }.getOrNull()
}
