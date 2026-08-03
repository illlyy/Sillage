package com.termux.app

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Slash-command trigger and ordering logic (pattern: claudecodeui useSlashCommands.ts).
 * Pure helpers: cursor-aware trigger (code-block aware), usage-frequency ordering and
 * filtering. The composer renders the menu; the usage counter lives in the app prefs.
 */
internal data class NativeSlashCommand(
    val id: String,
    val label: String,
    /** Text inserted into the composer, or "" when the command executes directly. */
    val insertText: String,
    val usageCount: Int = 0,
)

private const val SLASH_USAGE_PREFERENCE = "native_slash_usage_v1"

/** Reads the persisted usage histogram as a map of command id -> count. */
internal fun readSlashUsage(preferences: SharedPreferences): Map<String, Int> {
    val raw = preferences.getString(SLASH_USAGE_PREFERENCE, null) ?: return emptyMap()
    val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyMap()
    return buildMap {
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            entry.optString("id").takeIf { it.isNotBlank() }?.let { id -> put(id, entry.optInt("count", 0)) }
        }
    }
}

/** Records one usage of a command id and persists the histogram (bounded entry count). */
internal fun recordSlashUsage(preferences: SharedPreferences, commandId: String) {
    if (commandId.isBlank()) return
    val usage = readSlashUsage(preferences).toMutableMap()
    usage[commandId] = (usage[commandId] ?: 0) + 1
    val array = JSONArray()
    usage.entries.sortedByDescending { it.value }.take(60).forEach { (id, count) ->
        array.put(org.json.JSONObject().put("id", id).put("count", count))
    }
    preferences.edit().putString(SLASH_USAGE_PREFERENCE, array.toString()).apply()
}

/**
 * Returns the matched `/word` range before [cursor], or null when the slash sits inside a
 * fenced code block (odd number of ``` before the cursor) or there is no trigger.
 */
internal fun nativeSlashTriggerRange(text: String, cursor: Int): IntRange? {
    if (text.isBlank() || cursor <= 0) return null
    val prefix = text.take(cursor.coerceAtMost(text.length))
    val fences = Regex("```").findAll(prefix).count()
    if (fences % 2 == 1) return null
    val match = Regex("(?:^|\\s)(/\\S*)$").find(prefix) ?: return null
    val slashStart = prefix.lastIndexOf(match.groupValues[1])
    return slashStart until cursor
}

/** Orders commands by usage count (descending); stable for equal counts. */
internal fun orderSlashCommands(commands: List<NativeSlashCommand>, usage: Map<String, Int>): List<NativeSlashCommand> =
    commands.sortedByDescending { usage[it.id] ?: 0 }

/** Prefix-first filter: prefix matches win, then substring matches on label. */
internal fun filterSlashCommands(commands: List<NativeSlashCommand>, query: String): List<NativeSlashCommand> {
    val q = query.trim()
    if (q.isBlank()) return commands
    val (prefixMatches, substringMatches) = commands.partition { it.label.startsWith(q, ignoreCase = true) }
    return prefixMatches + substringMatches.filter { it.label.contains(q, ignoreCase = true) }
}

/** Built-in slash commands shared by the composer menu. */
internal fun builtinSlashCommands(): List<NativeSlashCommand> = listOf(
    NativeSlashCommand("ls", "/ls", insertText = "/ls"),
    NativeSlashCommand("search", "/search ", insertText = "/search "),
    NativeSlashCommand("compact", "/compact", insertText = "/compact"),
)
