package com.termux.app

import org.json.JSONObject
import java.io.File
import java.util.TreeMap

/**
 * Renders a Claude profile into the `env` block of `CLAUDE_CONFIG_DIR/settings.json`,
 * the same shape cc-switch writes on desktop. The Claude CLI reads this file at startup
 * and merges every key into its process environment, so tier models, subagent model and
 * extra tuning vars all work without passing anything on the command line.
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
    private const val ENV_SUBAGENT = "CLAUDE_CODE_SUBAGENT_MODEL"
    private const val ENV_BASE_URL = "ANTHROPIC_BASE_URL"

    fun normalizeAuthField(value: String?): String = when (value) {
        FIELD_API_KEY -> FIELD_API_KEY
        else -> FIELD_AUTH_TOKEN
    }

    /** Builds the sorted env block for a profile (tier fields fall back to the primary model). */
    fun buildEnv(profile: ClaudeProfile): Map<String, String> {
        val env = TreeMap<String, String>()
        val primary = profile.model.ifBlank { ClaudeProfile.DEFAULT_MODEL }
        if (profile.apiKey.isNotBlank()) env[profile.apiKeyField] = profile.apiKey
        if (profile.baseUrl.isNotBlank()) env[ENV_BASE_URL] = profile.baseUrl
        env[ENV_MODEL] = primary
        env[ENV_HAIKU] = profile.haikuModel.ifBlank { primary }
        env[ENV_SONNET] = profile.sonnetModel.ifBlank { primary }
        env[ENV_OPUS] = profile.opusModel.ifBlank { primary }
        if (profile.subagentModel.isNotBlank()) env[ENV_SUBAGENT] = profile.subagentModel
        profile.extraEnv.forEach { (key, value) ->
            if (key.isNotBlank()) env[key] = value
        }
        return env
    }

    /** Serializes the env block as the settings.json payload. */
    fun buildSettingsJson(profile: ClaudeProfile): String {
        val env = JSONObject()
        buildEnv(profile).forEach { (key, value) -> env.put(key, value) }
        return JSONObject().put("env", env).toString()
    }

    /**
     * Atomically writes the settings file for the active profile. Returns the written file,
     * or null when the profile is blank (nothing to write).
     */
    fun write(configDir: File, profile: ClaudeProfile?): File? {
        val settingsFile = File(configDir, "settings.json")
        if (profile == null || profile.apiKey.isBlank()) {
            if (settingsFile.exists()) settingsFile.delete()
            return null
        }
        if (!configDir.isDirectory && !configDir.mkdirs()) return null
        val temp = File(configDir, "settings.json.tmp")
        temp.writeText(buildSettingsJson(profile), Charsets.UTF_8)
        if (!temp.renameTo(settingsFile)) {
            // rename may fail on some file systems; fall back to direct write
            settingsFile.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
        return settingsFile
    }
}
