package com.termux.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable queued-message store (pattern: claudecodeui queued-message claim ticket). The queue
 * survives process death and rotation; sending removes the entry before dispatch, so two
 * flushers (turn completion here, session restore later) can never double-send.
 */
internal class NativeQueuedMessageStore(
    private val preferences: SharedPreferences,
) {
    fun read(threadId: String): List<NativeQueuedFollowUp> {
        val raw = preferences.getString(key(threadId), null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::decodeFollowUp)?.let(::add)
            }
        }
    }

    fun write(threadId: String, items: List<NativeQueuedFollowUp>) {
        if (items.isEmpty()) {
            preferences.edit().remove(key(threadId)).apply()
            return
        }
        val array = JSONArray()
        items.forEach { array.put(encodeFollowUp(it)) }
        preferences.edit().putString(key(threadId), array.toString()).apply()
    }

    /** Claim semantics: returns the stored queue and clears it atomically-ish (apply). */
    fun take(threadId: String): List<NativeQueuedFollowUp> {
        val items = read(threadId)
        if (items.isNotEmpty()) preferences.edit().remove(key(threadId)).apply()
        return items
    }

    fun count(threadId: String): Int = read(threadId).size

    companion object {
        private const val PREFIX = "native_queued_messages_v1_"

        @JvmStatic
        fun key(threadId: String): String = PREFIX + threadId

        @JvmStatic
        fun encodeFollowUp(followUp: NativeQueuedFollowUp): JSONObject = JSONObject()
            .put("id", followUp.id)
            .put("text", followUp.text)
            .put("model", followUp.model)
            .put("effort", followUp.effort)
            .put("mode", followUp.mode)
            .put(
                "attachments",
                JSONArray().apply {
                    followUp.attachments.forEach { attachment ->
                        put(
                            JSONObject()
                                .put("name", attachment.name)
                                .put("path", attachment.path)
                                .put("image", attachment.image),
                        )
                    }
                },
            )
            .put(
                "skills",
                JSONArray().apply {
                    followUp.skills.forEach { skill ->
                        put(
                            JSONObject()
                                .put("name", skill.name)
                                .put("description", skill.description)
                                .put("path", skill.path),
                        )
                    }
                },
            )

        @JvmStatic
        fun decodeFollowUp(json: JSONObject): NativeQueuedFollowUp? {
            val text = json.optString("text").trim()
            val attachments = json.optJSONArray("attachments")
            val skills = json.optJSONArray("skills")
            return NativeQueuedFollowUp(
                id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                text = text,
                attachments = if (attachments == null) emptyList() else buildList {
                    for (index in 0 until attachments.length()) {
                        attachments.optJSONObject(index)?.let { entry ->
                            add(NativeAttachment(entry.optString("name"), entry.optString("path"), entry.optBoolean("image")))
                        }
                    }
                },
                skills = if (skills == null) emptyList() else buildList {
                    for (index in 0 until skills.length()) {
                        skills.optJSONObject(index)?.let { entry ->
                            add(NativeSkill(entry.optString("name"), entry.optString("description"), entry.optString("path")))
                        }
                    }
                },
                model = json.optString("model"),
                effort = json.optString("effort"),
                mode = json.optString("mode"),
            )
        }
    }
}
