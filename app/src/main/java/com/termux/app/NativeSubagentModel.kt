package com.termux.app

import org.json.JSONObject

/** Pure JSON helpers for the subagent drawers and work panel. */
internal fun jsonText(item: JSONObject, vararg keys: String): String {
    keys.forEach { key ->
        if (item.has(key) && !item.isNull(key)) {
            val value = item.optString(key, "").trim()
            if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) return value
        }
    }
    return ""
}

internal fun subagentThreadId(item: JSONObject): String {
    jsonText(item, "agentThreadId", "agent_thread_id").takeIf { it.isNotBlank() }?.let { return it }
    val receivers = item.optJSONArray("receiverThreadIds")
        ?: item.optJSONArray("receiver_thread_ids")
        ?: return ""
    for (index in 0 until receivers.length()) {
        if (!receivers.isNull(index)) {
            val raw = receivers.opt(index)
            val value = if (raw is JSONObject) {
                jsonText(raw, "agentThreadId", "agent_thread_id", "threadId", "thread_id", "id")
            } else receivers.optString(index, "").trim()
            if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) return value
        }
    }
    return ""
}

internal fun subagentKey(item: JSONObject): String = subagentThreadId(item).ifBlank {
    jsonText(item, "id", "callId", "tool").ifBlank { item.toString().hashCode().toString() }
}

internal fun subagentAliases(item: JSONObject): Set<String> = buildSet {
    subagentThreadId(item).takeIf { it.isNotBlank() }?.let(::add)
    listOf("id", "callId", "eventId").forEach { key ->
        jsonText(item, key).takeIf { it.isNotBlank() }?.let(::add)
    }
}

internal fun mergeSubagentItems(existing: JSONObject, incoming: JSONObject): JSONObject {
    val merged = runCatching { JSONObject(existing.toString()) }.getOrElse { JSONObject() }
    incoming.keys().forEach { key ->
        val value = incoming.opt(key)
        if (value != null && value != JSONObject.NULL && (!(value is String) || value.isNotBlank())) {
            merged.put(key, value)
        }
    }
    return merged
}

internal fun subagentName(item: JSONObject): String {
    val id = subagentThreadId(item)
    val fallback = if (id.isNotBlank()) "子代理 ${id.take(6)}" else "子代理"
    val explicit = jsonText(item, "agentName", "agentNickname", "nickname", "agent")
    if (explicit.isNotBlank() && !explicit.equals("subAgentActivity", true)) {
        return explicit.substringAfterLast('/')
    }
    val pathName = jsonText(item, "agentPath").substringAfterLast('/').trim()
    return pathName.ifBlank { fallback }
}

internal fun normalizedSubagentStatus(value: String): String = when (value.trim().lowercase()) {
    "inprogress", "in_progress", "running", "started", "working" -> "working"
    "waiting", "pending", "queued" -> "waiting"
    "failed", "error" -> "failed"
    "cancelled", "canceled", "stopped", "interrupted" -> "stopped"
    "done", "complete", "completed", "success" -> "done"
    else -> "waiting"
}

internal fun isSubagentItem(item: JSONObject): Boolean =
    item.optString("type") in setOf("collabAgentToolCall", "subAgentActivity")

/** True when the item is a subagent: recognized subagent type, or an agent thread identity
 *  on an item that carries no `type` at all. The normalized protocol path projects subagents
 *  without a `type` field (only agentThreadId/receiverThreadIds), so a bare identity must be
 *  accepted here too; known other types keep their strict check. */
internal fun isSubagentCandidate(item: JSONObject): Boolean {
    val type = item.optString("type", "").trim()
    if (type.isEmpty() && subagentThreadId(item).isNotBlank()) return true
    if (!isSubagentItem(item)) return false
    if (subagentThreadId(item).isNotBlank()) return true
    val tool = jsonText(item, "tool", "name").lowercase()
    return tool.contains("spawn")
}

/** Counts produced by [collectAllSubagentItems] so callers can diagnose "missing subagents". */
internal data class SubagentCollectStats(
    val process2Messages: Int = 0,
    val decodedPayloads: Int = 0,
    val toolsFound: Int = 0,
    val candidateItems: Int = 0,
    val filteredBlankThreadId: Int = 0,
    val outputCount: Int = 0,
    /** Distinct `type` values of items rejected by [isSubagentCandidate], capped; "(no type)" when absent. */
    val rejectedTypes: String = "",
)

internal fun collectAllSubagentItems(
    messages: List<NativeChatMessage>,
    liveSubagents: List<String>,
    onStats: (SubagentCollectStats) -> Unit = {},
): List<JSONObject> {
    val result = ArrayList<JSONObject>()
    var process2Messages = 0
    var decodedPayloads = 0
    var toolsFound = 0
    var candidateItems = 0
    val rejectedTypes = LinkedHashSet<String>()
    fun add(value: JSONObject) {
        if (!isSubagentCandidate(value)) {
            if (rejectedTypes.size < 8) {
                val type = value.optString("type")
                rejectedTypes.add(if (type.isBlank()) "(no type)" else type)
            }
            return
        }
        candidateItems++
        val aliases = subagentAliases(value)
        val index = result.indexOfFirst { existing ->
            subagentAliases(existing).any(aliases::contains)
        }
        if (index >= 0) result[index] = mergeSubagentItems(result[index], value)
        else result.add(value)
    }
    messages.forEach { message ->
        if (message.role != NativeChatRole.ACTIVITY || !message.content.startsWith("PROCESS2|")) return@forEach
        process2Messages++
        runCatching {
            val payload = JSONObject(
                String(NativeBase64.decode(message.content.substringAfter('|')), Charsets.UTF_8),
            )
            decodedPayloads++
            val tools = payload.optJSONArray("tools") ?: return@runCatching
            // Keep the historical contract unchanged: only object entries are accepted here.
            for (index in 0 until tools.length()) tools.optJSONObject(index)?.let {
                toolsFound++
                add(it)
            }
        }
    }
    liveSubagents.forEach { raw -> runCatching { add(JSONObject(raw)) } }
    var filteredBlankThreadId = 0
    val output = result.filter {
        if (subagentThreadId(it).isBlank()) {
            filteredBlankThreadId++
            false
        } else {
            true
        }
    }
    onStats(SubagentCollectStats(
        process2Messages = process2Messages,
        decodedPayloads = decodedPayloads,
        toolsFound = toolsFound,
        candidateItems = candidateItems,
        filteredBlankThreadId = filteredBlankThreadId,
        outputCount = output.size,
        rejectedTypes = rejectedTypes.joinToString(","),
    ))
    return output
}

internal fun collectSubagentItems(
    messages: List<NativeChatMessage>,
    liveSubagents: List<String>,
    current: JSONObject,
): List<JSONObject> {
    val result = collectAllSubagentItems(messages, liveSubagents).toMutableList()
    val aliases = subagentAliases(current)
    val index = result.indexOfFirst { existing -> subagentAliases(existing).any(aliases::contains) }
    if (index >= 0) result[index] = mergeSubagentItems(result[index], current)
    else if (subagentThreadId(current).isNotBlank()) result.add(current)
    return result
}

/** Resolve a renderer-only visual back to the richest protocol item available for its drawer. */
internal fun subagentDrawerAnchor(
    visual: NativeSubagentVisual,
    candidates: List<JSONObject>,
): JSONObject {
    val visualAliases = buildSet {
        addAll(visual.aliases)
        visual.agentThreadId.takeIf(String::isNotBlank)?.let(::add)
        visual.callId.takeIf(String::isNotBlank)?.let(::add)
        visual.seed.takeIf(String::isNotBlank)?.let(::add)
    }
    val matched = candidates.firstOrNull { candidate ->
        subagentAliases(candidate).any(visualAliases::contains)
    }
    return runCatching { JSONObject(matched?.toString().orEmpty()) }.getOrElse { JSONObject() }.apply {
        if (!has("type")) put("type", "subAgentActivity")
        if (visual.agentThreadId.isNotBlank()) put("agentThreadId", visual.agentThreadId)
        if (visual.callId.isNotBlank()) put("callId", visual.callId)
        if (jsonText(this, "agentName", "agentNickname", "nickname").isBlank()) put("agentName", visual.name)
        if (jsonText(this, "status", "state").isBlank()) put("status", visual.status.name.lowercase())
    }
}
