package com.termux.app

import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Session-scoped Claude tool allow-list memory.
 *
 * The Claude CLI is spawned with `--allowedTools`; "allow for session" cannot retroactively
 * widen a running CLI, so an acceptForSession both allows the current request (via the
 * control-response) and remembers the pattern for the *next* spawn. Stored per active
 * profile in `codex_mobile` prefs.
 */
internal object ClaudeAllowedToolsStore {
    private const val KEY_PREFIX = "native_claude_allowed_tools_v1_"

    private fun keyFor(profileId: String): String {
        val encoded = Base64.encodeToString(
            profileId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return KEY_PREFIX + encoded
    }

    fun load(prefs: SharedPreferences, profileId: String): Set<String> {
        if (profileId.isBlank()) return emptySet()
        return prefs.getStringSet(keyFor(profileId), emptySet()).orEmpty().toSet()
    }

    fun add(prefs: SharedPreferences, profileId: String, pattern: String): Set<String> {
        if (profileId.isBlank() || pattern.isBlank()) return load(prefs, profileId)
        val next = load(prefs, profileId) + pattern.trim()
        prefs.edit().putStringSet(keyFor(profileId), next).apply()
        return next
    }

    fun clear(prefs: SharedPreferences, profileId: String) {
        if (profileId.isBlank()) return
        prefs.edit().remove(keyFor(profileId)).apply()
    }

    /**
     * Builds a CLI `--allowedTools` pattern from a tool name + command.
     * Non-Bash tools are returned verbatim; Bash collapses to `Bash(<head>:*)`
     * where head is the first word (plus second word for `git`) to avoid
     * over-broad `Bash` grants from a single approval.
     */
    fun toAllowedPattern(toolName: String, command: String): String {
        val name = toolName.trim()
        if (name.isEmpty()) return ""
        val isBash = name.equals("Bash", ignoreCase = true) || name.equals("Shell", ignoreCase = true)
        if (!isBash) return name
        val cmd = command.trim()
        if (cmd.isEmpty()) return "Bash"
        val parts = cmd.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "Bash"
        val head = if (parts[0] == "git" && parts.size > 1) "git ${parts[1]}" else parts[0]
        // Strip leading path/dash noise; keep it simple and predictable.
        val clean = head.trim().take(64)
        if (clean.isEmpty()) return "Bash"
        return "Bash($clean:*)"
    }

    /**
     * Extracts an allow pattern from an approval request payload.
     * Handles both Claude flat shape `{"tool_name":..,"command":..}` and
     * Codex wrapped shape `{"params":{...}}`.
     */
    fun extractPatternFromApprovalRaw(raw: String): String {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return ""
        val params = root.optJSONObject("params")
        val toolName = root.optString("tool_name", "")
            .ifBlank { params?.optString("tool_name", "").orEmpty() }
            .ifBlank { params?.optString("toolName", "").orEmpty() }
            .ifBlank { root.optString("toolName", "") }
            .ifBlank { root.optString("title", "") }
            .ifBlank { params?.optString("title", "").orEmpty() }
        val command = root.optString("command", "")
            .ifBlank { params?.optString("command", "").orEmpty() }
        if (toolName.isBlank()) return ""
        return toAllowedPattern(toolName, command)
    }

    /** Merges a base allow-list CSV with session-remembered patterns. */
    fun merge(baseCsv: String, remembered: Set<String>): String {
        val base = baseCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val extra = remembered.map { it.trim() }.filter { it.isNotEmpty() }
        return (base + extra).distinct().joinToString(",")
    }
}
