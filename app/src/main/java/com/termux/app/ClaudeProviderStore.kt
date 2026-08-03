package com.termux.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent Claude backend configuration, modeled after cc-switch's provider shape:
 * an authentication mode (Bearer token vs API key), an optional base URL, a primary model
 * plus tier overrides (haiku/sonnet/opus), a subagent model, and arbitrary extra env vars.
 *
 * The active profile is rendered into `CLAUDE_CONFIG_DIR/settings.json` by
 * [ClaudeSettingsWriter] so the CLI consumes exactly what a desktop cc-switch would write.
 */
internal data class ClaudeProfile(
    val id: String,
    val name: String,
    val apiKey: String = "",
    val apiKeyField: String = ClaudeSettingsWriter.FIELD_AUTH_TOKEN,
    val baseUrl: String = "",
    val model: String = "",
    val haikuModel: String = "",
    val sonnetModel: String = "",
    val opusModel: String = "",
    val fableModel: String = "",
    val smallFastModel: String = "",
    val subagentModel: String = "",
    // Performance/context tuning (written verbatim to env, blank = omitted).
    val maxContextTokens: String = "",
    val autoCompactWindow: String = "",
    val maxOutputTokens: String = "",
    val apiTimeoutMs: String = "",
    // cc-switch style behavior toggles (env written only when enabled).
    val disableNonEssentialTraffic: Boolean = false,
    val maxEffort: Boolean = false,
    val enableToolSearch: Boolean = false,
    val disableAutoUpdater: Boolean = false,
    val experimentalAgentTeams: Boolean = false,
    val disableExperimentalBetas: Boolean = false,
    // includeCoAuthoredBy -> top-level settings.json key when enabled.
    val includeCoAuthoredBy: Boolean = false,
    // Arbitrary top-level settings.json keys (raw JSON object, merged into the root).
    val extraSettingsJson: String = "",
    val extraEnv: Map<String, String> = emptyMap(),
) {
    fun sanitized(): ClaudeProfile = copy(
        name = name.trim(),
        apiKey = apiKey.trim(),
        apiKeyField = ClaudeSettingsWriter.normalizeAuthField(apiKeyField),
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        haikuModel = haikuModel.trim(),
        sonnetModel = sonnetModel.trim(),
        opusModel = opusModel.trim(),
        fableModel = fableModel.trim(),
        smallFastModel = smallFastModel.trim(),
        subagentModel = subagentModel.trim(),
        maxContextTokens = maxContextTokens.trim(),
        autoCompactWindow = autoCompactWindow.trim(),
        maxOutputTokens = maxOutputTokens.trim(),
        apiTimeoutMs = apiTimeoutMs.trim(),
        // Never validate/drop the raw JSON here: validation lives in the editor, tolerance in
        // the writer, so invalid input is preserved rather than silently destroying user data.
        extraSettingsJson = extraSettingsJson.trim(),
        extraEnv = extraEnv.mapValues { (_, value) -> value.trim() }
            .filterValues { it.isNotBlank() },
    )

    companion object {
        const val DEFAULT_MODEL = "sonnet"

        fun fromJson(id: String, json: JSONObject): ClaudeProfile = ClaudeProfile(
            id = id,
            name = json.optString("name", "Claude"),
            apiKey = json.optString("apiKey"),
            // Legacy profiles stored only apiKey/baseUrl/model; auth field defaults to
            // Bearer token (cc-switch default) which covers most third-party gateways.
            apiKeyField = ClaudeSettingsWriter.normalizeAuthField(json.optString("apiKeyField")),
            baseUrl = json.optString("baseUrl"),
            model = json.optString("model", DEFAULT_MODEL).ifBlank { DEFAULT_MODEL },
            haikuModel = json.optString("haikuModel"),
            sonnetModel = json.optString("sonnetModel"),
            opusModel = json.optString("opusModel"),
            fableModel = json.optString("fableModel"),
            smallFastModel = json.optString("smallFastModel"),
            subagentModel = json.optString("subagentModel"),
            maxContextTokens = json.optString("maxContextTokens"),
            autoCompactWindow = json.optString("autoCompactWindow"),
            maxOutputTokens = json.optString("maxOutputTokens"),
            apiTimeoutMs = json.optString("apiTimeoutMs"),
            disableNonEssentialTraffic = json.optBoolean("disableNonEssentialTraffic", false),
            maxEffort = json.optBoolean("maxEffort", false),
            enableToolSearch = json.optBoolean("enableToolSearch", false),
            disableAutoUpdater = json.optBoolean("disableAutoUpdater", false),
            experimentalAgentTeams = json.optBoolean("experimentalAgentTeams", false),
            disableExperimentalBetas = json.optBoolean("disableExperimentalBetas", false),
            includeCoAuthoredBy = json.optBoolean("includeCoAuthoredBy", false),
            extraSettingsJson = json.optString("extraSettingsJson"),
            extraEnv = parseExtraEnv(json.optJSONObject("extraEnv")),
        )

        private fun parseExtraEnv(raw: JSONObject?): Map<String, String> {
            if (raw == null) return emptyMap()
            return buildMap {
                raw.keys().forEach { key ->
                    val value = raw.optString(key)
                    if (value.isNotBlank()) put(key, value)
                }
            }
        }
    }
}

/** Stores zero or more named Claude profiles, one of which is active. */
internal class ClaudeProviderStore(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY_PROFILES = "claude_profiles_v1"
        private const val KEY_ACTIVE = "active_claude_profile_id"
    }

    fun profiles(): List<ClaudeProfile> {
        val raw = prefs.getString(KEY_PROFILES, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(ClaudeProfile.fromJson(item.optString("id"), item))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun find(id: String): ClaudeProfile? = profiles().firstOrNull { it.id == id }

    fun active(): ClaudeProfile? {
        val activeId = prefs.getString(KEY_ACTIVE, null) ?: return null
        return find(activeId)
    }

    fun save(profile: ClaudeProfile): ClaudeProfile {
        val sanitized = profile.sanitized()
        val existing = profiles().filterNot { it.id == sanitized.id }
        val next = existing + sanitized
        prefs.edit().putString(KEY_PROFILES, toJson(next)).apply()
        if (prefs.getString(KEY_ACTIVE, null) == null) activate(sanitized.id)
        return sanitized
    }

    fun delete(id: String) {
        val next = profiles().filterNot { it.id == id }
        prefs.edit()
            .putString(KEY_PROFILES, toJson(next))
            .remove(KEY_ACTIVE)
            .apply()
        next.firstOrNull()?.let { activate(it.id) }
    }

    fun activate(id: String) {
        if (find(id) == null) return
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    private fun toJson(profiles: List<ClaudeProfile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            val extra = JSONObject()
            profile.extraEnv.forEach { (key, value) -> extra.put(key, value) }
            array.put(JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("apiKey", profile.apiKey)
                .put("apiKeyField", profile.apiKeyField)
                .put("baseUrl", profile.baseUrl)
                .put("model", profile.model)
                .put("haikuModel", profile.haikuModel)
                .put("sonnetModel", profile.sonnetModel)
                .put("opusModel", profile.opusModel)
                .put("fableModel", profile.fableModel)
                .put("smallFastModel", profile.smallFastModel)
                .put("subagentModel", profile.subagentModel)
                .put("maxContextTokens", profile.maxContextTokens)
                .put("autoCompactWindow", profile.autoCompactWindow)
                .put("maxOutputTokens", profile.maxOutputTokens)
                .put("apiTimeoutMs", profile.apiTimeoutMs)
                .put("disableNonEssentialTraffic", profile.disableNonEssentialTraffic)
                .put("maxEffort", profile.maxEffort)
                .put("enableToolSearch", profile.enableToolSearch)
                .put("disableAutoUpdater", profile.disableAutoUpdater)
                .put("experimentalAgentTeams", profile.experimentalAgentTeams)
                .put("disableExperimentalBetas", profile.disableExperimentalBetas)
                .put("includeCoAuthoredBy", profile.includeCoAuthoredBy)
                .put("extraSettingsJson", profile.extraSettingsJson)
                .put("extraEnv", extra))
        }
        return array.toString()
    }
}
