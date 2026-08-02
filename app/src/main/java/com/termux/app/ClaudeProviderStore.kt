package com.termux.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent Claude backend configuration (API key, optional base URL, model).
 * Stored in the same private prefs file as Codex profiles; secrets never leave the app.
 */
internal data class ClaudeProfile(
    val id: String,
    val name: String,
    val apiKey: String,
    val baseUrl: String = "",
    val model: String = DEFAULT_MODEL,
) {
    fun sanitized(): ClaudeProfile = copy(
        name = name.trim(),
        apiKey = apiKey.trim(),
        baseUrl = baseUrl.trim(),
        model = model.trim().ifBlank { DEFAULT_MODEL },
    )

    companion object {
        const val DEFAULT_MODEL = "sonnet"

        fun fromJson(id: String, json: JSONObject): ClaudeProfile = ClaudeProfile(
            id = id,
            name = json.optString("name", "Claude"),
            apiKey = json.optString("apiKey"),
            baseUrl = json.optString("baseUrl"),
            model = json.optString("model", DEFAULT_MODEL).ifBlank { DEFAULT_MODEL },
        )
    }
}

/** Stores zero or more named Claude profiles, one of which is active. */
internal class ClaudeProviderStore(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY_PROFILES = "claude_profiles_v1"
        private const val KEY_ACTIVE = "active_claude_profile_id"
        const val PREF_GOAL = "native_thread_goal_v1_"
        const val PREF_GOAL_STATUS = "native_thread_goal_status_v1_"
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
            array.put(JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("apiKey", profile.apiKey)
                .put("baseUrl", profile.baseUrl)
                .put("model", profile.model))
        }
        return array.toString()
    }
}
