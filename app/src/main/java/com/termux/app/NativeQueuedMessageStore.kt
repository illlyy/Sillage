package com.termux.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable queued-message store (pattern: claudecodeui queued-message claim ticket). The queue
 * survives process death and rotation; [take] claims the queue durably before dispatch, so the
 * completion-time flush and the session-restore flush can never both send the same message even if
 * the process is killed mid-turn.
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

    /** Claim semantics: returns the stored queue and removes it durably (commit). */
    fun take(threadId: String): List<NativeQueuedFollowUp> {
        val items = read(threadId)
        // Synchronous on purpose. These messages are about to be executed -- tools run, files
        // change -- so "already sent" has to survive the process being killed a moment later. An
        // async apply() can still be sitting in the write queue at that point, and the next launch
        // would read the queue back and send every message a second time, re-running the tools.
        // One fsync on the send path is a fair price for that. The enqueue path keeps apply(),
        // because the two failures are not symmetric: losing an unsent message costs the user a
        // re-send, losing the claim costs them a duplicated run.
        if (items.isNotEmpty()) preferences.edit().remove(key(threadId)).commit()
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
