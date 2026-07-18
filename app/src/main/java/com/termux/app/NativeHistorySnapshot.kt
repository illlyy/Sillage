package com.termux.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.LinkedHashMap

/** Immutable history payload prepared before crossing to the Compose main thread. */
data class NativeHistorySnapshot(
    val messages: List<NativeChatMessage>,
    val planJson: String = "[]",
    val planExplanation: String = "",
    val planPanelIndex: Int = -1,
    val estimatedChars: Int = 0,
    val contentFingerprint: Long = 0L,
) {
    fun hasSameContent(other: NativeHistorySnapshot): Boolean =
        contentFingerprint != 0L && contentFingerprint == other.contentFingerprint
}

/** Converts app-server history JSON into native message DTOs on a worker thread. */
internal object NativeHistoryParser {
    @JvmStatic
    fun parse(raw: String): NativeHistorySnapshot = parse(JSONArray(raw))

    @JvmStatic
    fun parse(items: JSONArray?): NativeHistorySnapshot {
        if (items == null) return NativeHistorySnapshot(emptyList())
        val parsed = ArrayList<NativeChatMessage>(items.length())
        var planJson = "[]"
        var planExplanation = ""
        var planPanelIndex = -1
        var estimatedChars = 0L

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val role = when (item.optString("role")) {
                "user" -> NativeChatRole.USER
                "assistant" -> NativeChatRole.ASSISTANT
                "activity" -> NativeChatRole.ACTIVITY
                else -> continue
            }
            var content = item.optString("content").trim()
            val messageSkills = buildList {
                val skillArray = item.optJSONArray("skills") ?: JSONArray()
                for (skillIndex in 0 until skillArray.length()) {
                    val skill = skillArray.optJSONObject(skillIndex) ?: continue
                    val name = skill.optString("name").trim()
                    val path = skill.optString("path").trim()
                    if (name.isNotEmpty()) add(NativeSkill(name, skill.optString("description"), path))
                }
            }
            val messageAttachments = buildList {
                val attachmentArray = item.optJSONArray("attachments") ?: JSONArray()
                for (attachmentIndex in 0 until attachmentArray.length()) {
                    val attachment = attachmentArray.optJSONObject(attachmentIndex) ?: continue
                    val path = attachment.optString("path").trim()
                    if (path.isNotEmpty()) add(
                        NativeAttachment(
                            attachment.optString("name", File(path).name),
                            path,
                            attachment.optBoolean("image"),
                        ),
                    )
                }
            }
            if (role == NativeChatRole.ACTIVITY && content.startsWith("PLAN|")) {
                decodePayload(content.substringAfter('|'))?.let { planPayload ->
                    planJson = planPayload.optJSONArray("plan")?.toString() ?: "[]"
                    planExplanation = planPayload.optString("explanation")
                    planPanelIndex = parsed.size
                }
                continue
            }
            if (role == NativeChatRole.ACTIVITY && content.startsWith("PROCESS2|")) {
                content = compactProcessContent(content)
            }
            if (content.isEmpty()) continue
            estimatedChars += content.length
            messageSkills.forEach { estimatedChars += it.name.length + it.description.length + it.path.length }
            messageAttachments.forEach { estimatedChars += it.name.length + it.path.length }
            parsed.add(
                NativeChatMessage(
                    role = role,
                    content = content,
                    skills = messageSkills,
                    attachments = messageAttachments,
                ),
            )
        }
        estimatedChars += planJson.length + planExplanation.length
        val contentFingerprint = contentFingerprint(parsed, planJson, planExplanation, planPanelIndex)
        return NativeHistorySnapshot(
            messages = parsed.toList(),
            planJson = planJson,
            planExplanation = planExplanation,
            planPanelIndex = planPanelIndex,
            estimatedChars = estimatedChars.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            contentFingerprint = contentFingerprint,
        )
    }

    private fun contentFingerprint(
        messages: List<NativeChatMessage>,
        planJson: String,
        planExplanation: String,
        planPanelIndex: Int,
    ): Long {
        var result = 1_125_899_906_842_597L
        fun add(value: Int) { result = result * 31L + value.toLong() }
        messages.forEach { message ->
            add(message.role.ordinal)
            add(stableContentHash(message.content))
            message.skills.forEach { skill -> add(skill.name.hashCode()); add(skill.description.hashCode()); add(skill.path.hashCode()) }
            message.attachments.forEach { attachment -> add(attachment.name.hashCode()); add(attachment.path.hashCode()); add(if (attachment.image) 1 else 0) }
        }
        add(planJson.hashCode())
        add(planExplanation.hashCode())
        add(planPanelIndex)
        return if (result == 0L) 1L else result
    }

    /** Command cache refs use random UUIDs on every disk parse. Normalize them and include
     * the full cached stream hash so equivalent history receives a stable fingerprint. */
    private fun stableContentHash(content: String): Int {
        if (!content.startsWith("PROCESS2|")) return content.hashCode()
        val payload = decodePayload(content.substringAfter('|')) ?: return content.hashCode()
        val tools = payload.optJSONArray("tools") ?: return payload.toString().hashCode()
        for (index in 0 until tools.length()) {
            val item = tools.optJSONObject(index) ?: continue
            if (item.optString("type") == "commandExecution") {
                listOf(
                    NativeCommandOutputStore.OUTPUT_REF to "_nativeOutputHash",
                    NativeCommandOutputStore.STDERR_REF to "_nativeStderrHash",
                ).forEach { (referenceKey, hashKey) ->
                    val reference = item.optString(referenceKey)
                    if (reference.isNotBlank()) {
                        val fallbackPreview = if (referenceKey == NativeCommandOutputStore.OUTPUT_REF) {
                            item.optString(NativeCommandOutputStore.OUTPUT_PREVIEW)
                        } else item.optString(NativeCommandOutputStore.STDERR_PREVIEW)
                        item.put(hashKey, (NativeCommandOutputStore.get(reference) ?: fallbackPreview).hashCode())
                        item.remove(referenceKey)
                    }
                }
            } else {
                val reference = item.optString(NativeLargePayloadStore.PAYLOAD_REF)
                if (reference.isNotBlank()) {
                    if (!item.has(NativeLargePayloadStore.PAYLOAD_HASH)) {
                        val fallback = item.optString(NativeLargePayloadStore.PAYLOAD_PREVIEW)
                        item.put(NativeLargePayloadStore.PAYLOAD_HASH, (NativeLargePayloadStore.get(reference) ?: fallback).hashCode())
                    }
                    item.remove(NativeLargePayloadStore.PAYLOAD_REF)
                }
            }
        }
        payload.put("tools", tools)
        return payload.toString().hashCode()
    }

    /** Protects legacy/raw PROCESS2 histories whose tool payloads were not compacted by Bridge. */
    internal fun compactProcessContent(content: String): String {
        val payload = decodePayload(content.substringAfter('|')) ?: return content
        val tools = payload.optJSONArray("tools") ?: JSONArray()
        var changed = false
        val legacyCommandOutput = payload.optString("command", "")
        if (legacyCommandOutput.isNotBlank()) {
            val legacyItem = JSONObject()
                .put("type", "commandExecution")
                .put("status", "completed")
                .put("aggregatedOutput", legacyCommandOutput)
            tools.put(JSONObject(NativeCommandOutputStore.compactCommandItem(legacyItem)))
            payload.put("command", "")
            changed = true
        }
        for (index in 0 until tools.length()) {
            val item = when (val value = tools.opt(index)) {
                is JSONObject -> value
                is String -> runCatching { JSONObject(value) }.getOrNull()
                else -> null
            } ?: continue
            if (item.optString("type") == "commandExecution") {
                val hasHeavyOutput = item.has("aggregatedOutput") || item.has("output") || item.has("stdout") || item.has("stderr")
                if (!hasHeavyOutput) continue
                tools.put(index, JSONObject(NativeCommandOutputStore.compactCommandItem(item)))
                changed = true
            } else {
                val compacted = NativeLargePayloadStore.compactToolItem(item)
                if (compacted != item.toString()) {
                    tools.put(index, JSONObject(compacted))
                    changed = true
                }
            }
        }
        if (!changed) return content
        payload.put("tools", tools)
        return "PROCESS2|" + Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
    }

    private fun decodePayload(encoded: String): JSONObject? = runCatching {
        JSONObject(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
    }.getOrNull()
}


/** Pure route check used to reject history prepared for a conversation the user left. */
internal object NativeHistoryRouteGuard {
    @JvmStatic
    fun shouldApply(expectedGeneration: Int, currentThreadId: String?, generation: Int, threadId: String): Boolean =
        expectedGeneration >= 0 && generation == expectedGeneration && threadId == currentThreadId
}

/** Process-scoped, bounded cache so a short conversation switch does not parse history again. */
internal object NativeHistorySnapshotCache {
    private const val MAX_ENTRIES = 3
    private const val MAX_CACHED_CHARS = 2_500_000
    private val snapshots = LinkedHashMap<String, NativeHistorySnapshot>(4, 0.75f, true)
    private var cachedChars = 0

    @JvmStatic
    @Synchronized
    fun get(threadId: String): NativeHistorySnapshot? = snapshots[threadId]

    @JvmStatic
    @Synchronized
    fun put(threadId: String, snapshot: NativeHistorySnapshot) {
        if (threadId.isBlank()) return
        snapshots.remove(threadId)?.let { cachedChars -= it.estimatedChars }
        if (snapshot.estimatedChars > MAX_CACHED_CHARS) return
        snapshots[threadId] = snapshot
        cachedChars += snapshot.estimatedChars
        while (snapshots.size > MAX_ENTRIES || cachedChars > MAX_CACHED_CHARS) {
            val eldest = snapshots.entries.iterator()
            if (!eldest.hasNext()) break
            val entry = eldest.next()
            cachedChars -= entry.value.estimatedChars
            eldest.remove()
        }
    }

    @JvmStatic
    @Synchronized
    fun remove(threadId: String) {
        snapshots.remove(threadId)?.let { cachedChars -= it.estimatedChars }
    }

    @JvmStatic
    @Synchronized
    fun clear() {
        snapshots.clear()
        cachedChars = 0
    }

    @JvmStatic
    @Synchronized
    fun size(): Int = snapshots.size

    @JvmStatic
    @Synchronized
    fun cachedCharacterCount(): Int = cachedChars
}
