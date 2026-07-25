package com.termux.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativeCompactionJournalStoreTest {
    @Test
    fun loadPersistsCanonicalRequestAndServerLifecycle() {
        val threadId = "thread"
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("compaction-journal-test", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        val storageKey = "native_compaction_journal_v1_" +
            NativeSubagentVisualFactory.stableHash(threadId).toUInt().toString(16)
        val requestOnly = journalItem(
            id = "manual",
            turnId = "turn-before",
            source = "manual",
            requestId = "request",
            createdAtMs = 1_000L,
            updatedAtMs = 1_900L,
        )
        val serverOnly = journalItem(
            id = "server",
            turnId = "dedicated-turn",
            source = "automatic",
            serverItemId = "server-item",
            createdAtMs = 1_020L,
            updatedAtMs = 12_000L,
        )
        preferences.edit()
            .putString(storageKey, JSONArray().put(requestOnly).put(serverOnly).toString())
            .commit()

        val restored = NativeCompactionJournalStore(preferences).load(threadId)

        assertEquals(1, restored.size)
        assertEquals("request", restored.single().requestId)
        assertEquals("server-item", restored.single().serverItemId)
        assertEquals(NativeCompactionSource.MANUAL, restored.single().source)
        val persisted = JSONArray(preferences.getString(storageKey, null))
        assertEquals(1, persisted.length())
        assertEquals("request", persisted.getJSONObject(0).optString("requestId"))
        assertEquals("server-item", persisted.getJSONObject(0).optString("serverItemId"))
        assertNotNull(preferences.getString(storageKey, null))
    }

    private fun journalItem(
        id: String,
        turnId: String,
        source: String,
        requestId: String? = null,
        serverItemId: String? = null,
        createdAtMs: Long,
        updatedAtMs: Long,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("turnId", turnId)
        .put("serverItemId", serverItemId ?: JSONObject.NULL)
        .put("source", source)
        .put("status", "completed")
        .put("error", "")
        .put("requestId", requestId ?: JSONObject.NULL)
        .put("createdAtMs", createdAtMs)
        .put("updatedAtMs", updatedAtMs)
        .put("sequence", 0L)
}
