package com.termux.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class NativeWorkspaceSnapshot(
    val id: String,
    val threadId: String,
    val projectPath: String,
    val commit: String,
    val ref: String,
    val label: String,
    val createdAt: Long,
    val automatic: Boolean,
)

internal object NativeWorkspaceSnapshotCodec {
    fun encode(items: List<NativeWorkspaceSnapshot>): String = JSONArray().also { array ->
        items.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("threadId", item.threadId)
                .put("projectPath", item.projectPath)
                .put("commit", item.commit)
                .put("ref", item.ref)
                .put("label", item.label)
                .put("createdAt", item.createdAt)
                .put("automatic", item.automatic))
        }
    }.toString()

    fun decode(raw: String?): List<NativeWorkspaceSnapshot> = runCatching {
        val array = JSONArray(raw.orEmpty().ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val commit = item.optString("commit")
                if (id.isBlank() || commit.isBlank()) continue
                add(NativeWorkspaceSnapshot(
                    id = id,
                    threadId = item.optString("threadId"),
                    projectPath = item.optString("projectPath"),
                    commit = commit,
                    ref = item.optString("ref"),
                    label = item.optString("label"),
                    createdAt = item.optLong("createdAt"),
                    automatic = item.optBoolean("automatic"),
                ))
            }
        }
    }.getOrDefault(emptyList())
}

internal object NativeWorkspaceSnapshotPreview {
    fun build(snapshotId: String, currentCommit: String, nameStatus: String, numStat: String): String {
        val stats = mutableMapOf<String, Pair<Int, Int>>()
        numStat.lineSequence().forEach { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@forEach
            val path = parts.last()
            stats[path] = (parts[0].toIntOrNull() ?: 0) to (parts[1].toIntOrNull() ?: 0)
        }
        val entries = JSONArray()
        nameStatus.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@forEach
            val status = parts[0]
            val renamed = status.startsWith("R") || status.startsWith("C")
            val oldPath = if (renamed && parts.size >= 3) parts[1] else ""
            val path = if (renamed && parts.size >= 3) parts[2] else parts[1]
            val stat = stats[path] ?: (0 to 0)
            entries.put(JSONObject()
                .put("status", status)
                .put("path", path)
                .put("oldPath", oldPath)
                .put("additions", stat.first)
                .put("deletions", stat.second)
                .put("restorable", !status.startsWith("A") && !renamed))
        }
        return JSONObject()
            .put("snapshotId", snapshotId)
            .put("currentCommit", currentCommit)
            .put("entries", entries)
            .toString()
    }
}

internal object NativeWorkspaceSnapshotStore {
    private const val PREFERENCES = "codex_mobile"
    private const val KEY_PREFIX = "native_workspace_snapshots_v1_"
    private const val MAX_ITEMS = 16

    private fun key(threadId: String): String = KEY_PREFIX + threadId

    fun load(context: Context, threadId: String): List<NativeWorkspaceSnapshot> =
        NativeWorkspaceSnapshotCodec.decode(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(key(threadId), "[]"))

    fun add(context: Context, item: NativeWorkspaceSnapshot): List<NativeWorkspaceSnapshot> {
        val updated = (listOf(item) + load(context, item.threadId).filterNot { it.id == item.id }).take(MAX_ITEMS)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(key(item.threadId), NativeWorkspaceSnapshotCodec.encode(updated))
            .apply()
        return updated
    }

    fun remove(context: Context, threadId: String, id: String): List<NativeWorkspaceSnapshot> {
        val updated = load(context, threadId).filterNot { it.id == id }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(key(threadId), NativeWorkspaceSnapshotCodec.encode(updated))
            .apply()
        return updated
    }

    fun clear(context: Context, threadId: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(key(threadId)).apply()
    }
}
