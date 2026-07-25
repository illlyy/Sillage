package com.termux.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Provider/model-scoped preferences for the native fallback only. */
internal class NativeCompactionSettingsStore(
    private val preferences: SharedPreferences,
) {
    fun read(profileId: String, modelId: String): NativeCompactionSettings {
        val raw = preferences.getString(key(profileId, modelId), null) ?: return NativeCompactionSettings()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return NativeCompactionSettings()
        return NativeCompactionSettings(
            enabled = json.optBoolean("enabled", true),
            fallbackPercent = json.optInt("fallbackPercent", json.optInt("fallback_percent", 90)).coerceIn(80, 95),
        )
    }

    fun write(profileId: String, modelId: String, settings: NativeCompactionSettings) {
        preferences.edit().putString(
            key(profileId, modelId),
            JSONObject()
                .put("enabled", settings.enabled)
                .put("fallbackPercent", settings.normalizedPercent)
                .toString(),
        ).apply()
    }

    fun remove(profileId: String, modelId: String) {
        preferences.edit().remove(key(profileId, modelId)).apply()
    }

    companion object {
        private const val PREFIX = "native_compaction_v1_"

        @JvmStatic
        fun key(profileId: String, modelId: String): String = PREFIX +
            sanitize(profileId.ifBlank { "default" }) + "_" + sanitize(modelId.ifBlank { "default" })

        private fun sanitize(value: String): String = value.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}

/** Persists only compact lifecycle metadata; command/reasoning payloads never enter this journal. */
internal class NativeCompactionJournalStore(
    private val preferences: SharedPreferences,
    private val maxItemsPerThread: Int = 32,
) {
    fun load(threadId: String): List<NativeCompactionItem> {
        val raw = preferences.getString(key(threadId), null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val decoded = buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                decode(value, threadId)?.let(::add)
            }
        }
        val canonical = NativeHistoryAdapter.mergeCompactionTimeline(decoded)
            .sortedBy { it.updatedAtMs }
            .takeLast(maxItemsPerThread)
        // Older builds could persist the manual request and the server lifecycle as two
        // complementary records. Rendering already canonicalizes them, but leaving the raw
        // duplicate in SharedPreferences makes every process restart repeat the repair and can
        // reintroduce it when another record is appended. Rewrite only when the normalized JSON
        // actually differs so ordinary reads remain side-effect free.
        val canonicalRaw = encodeAll(canonical)
        if (canonicalRaw != raw) {
            preferences.edit().putString(key(threadId), canonicalRaw).apply()
        }
        return canonical
    }

    fun record(item: NativeCompactionItem) {
        val merged = NativeHistoryAdapter.mergeCompactionTimeline(load(item.threadId) + item)
        val encoded = encodeAll(merged.sortedBy { it.updatedAtMs }.takeLast(maxItemsPerThread))
        preferences.edit().putString(key(item.threadId), encoded).apply()
    }

    fun merge(threadId: String, serverItems: Iterable<NativeCompactionItem>): List<NativeCompactionItem> {
        return NativeHistoryAdapter.mergeCompactionTimeline(load(threadId) + serverItems)
            .sortedBy { it.updatedAtMs }
            .takeLast(maxItemsPerThread)
    }

    fun clear(threadId: String) {
        preferences.edit().remove(key(threadId)).apply()
    }

    private fun encodeAll(items: Iterable<NativeCompactionItem>): String = JSONArray().apply {
        items.forEach { put(encode(it)) }
    }.toString()

    private fun encode(item: NativeCompactionItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("turnId", item.turnId ?: JSONObject.NULL)
        .put("serverItemId", item.serverItemId ?: JSONObject.NULL)
        .put("source", item.source.name.lowercase())
        .put("status", item.status.name.lowercase())
        .put("error", item.error)
        .put("requestId", item.requestId ?: JSONObject.NULL)
        .put("createdAtMs", item.createdAtMs)
        .put("updatedAtMs", item.updatedAtMs)
        .put("sequence", item.sequence)

    private fun decode(value: JSONObject, threadId: String): NativeCompactionItem? {
        val id = value.optString("id").trim()
        if (id.isBlank()) return null
        val source = runCatching { NativeCompactionSource.valueOf(value.optString("source", "automatic").uppercase()) }
            .getOrDefault(NativeCompactionSource.LEGACY)
        val status = runCatching { NativeCompactionStatus.valueOf(value.optString("status", "completed").uppercase()) }
            .getOrDefault(NativeCompactionStatus.COMPLETED)
        return NativeCompactionItem(
            id = id,
            threadId = threadId,
            turnId = value.optString("turnId").takeIf { it.isNotBlank() && !it.equals("null", true) },
            serverItemId = value.optString("serverItemId").takeIf { it.isNotBlank() && !it.equals("null", true) },
            source = source,
            status = status,
            error = value.optString("error"),
            requestId = value.optString("requestId").takeIf { it.isNotBlank() && !it.equals("null", true) },
            createdAtMs = value.optLong("createdAtMs"),
            updatedAtMs = value.optLong("updatedAtMs"),
            sequence = value.optLong("sequence"),
        )
    }

    private fun key(threadId: String): String = "native_compaction_journal_v1_" +
        NativeSubagentVisualFactory.stableHash(threadId).toUInt().toString(16)
}
