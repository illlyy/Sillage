package com.termux.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal const val NATIVE_COMPACTION_PREFIX = "COMPACTION|"

internal object NativeHistoryAdapter {
    /**
     * Merge records from the server transcript and the bounded local journal. Older servers
     * persist a context_compacted notification without an item id, while the journal records the
     * same lifecycle with the later server item/request id; keying only by id would render two
     * completion dividers after a reload.
     */
    fun mergeCompactionTimeline(items: Iterable<NativeCompactionItem>): List<NativeCompactionItem> {
        val merged = ArrayList<NativeCompactionItem>()
        // History and the local journal are read independently.  A legacy
        // `context_compacted` notification is usually written at completion,
        // while the request/server records carry the original creation time.
        // Canonicalize in lifecycle order first so a legacy record cannot bind
        // to the request-only half before the complementary server-only half
        // is considered.  The stable tie breakers keep duplicate/replayed
        // records deterministic without changing their terminal status merge.
        val ordered = items.toList().sortedWith(
            compareBy<NativeCompactionItem> {
                it.createdAtMs.takeIf { timestamp -> timestamp > 0L } ?: Long.MAX_VALUE
            }
                .thenBy { it.updatedAtMs.takeIf { timestamp -> timestamp > 0L } ?: Long.MAX_VALUE }
                .thenBy { it.sequence.takeIf { sequence -> sequence > 0L } ?: Long.MAX_VALUE }
                .thenBy { it.id },
        )
        ordered.forEach { incoming ->
            val exactIndex = merged.indexOfFirst { existing ->
                existing.id == incoming.id ||
                    (!incoming.serverItemId.isNullOrBlank() && existing.serverItemId == incoming.serverItemId) ||
                    (!incoming.requestId.isNullOrBlank() && existing.requestId == incoming.requestId)
            }
            val compatibleIndex = if (exactIndex >= 0) exactIndex else merged.indexOfFirst { existing ->
                canMergeLegacyCompaction(existing, incoming)
            }
            if (compatibleIndex >= 0) {
                merged[compatibleIndex] = mergeCompaction(merged[compatibleIndex], incoming)
            } else {
                merged += incoming
            }
        }
        return merged.sortedWith(
            compareBy<NativeCompactionItem> { it.createdAtMs }
                .thenBy { it.updatedAtMs }
                .thenBy { it.id },
        )
    }

    fun encodeCompaction(item: NativeCompactionItem): String {
        val payload = JSONObject()
            .put("id", item.id)
            .put("threadId", item.threadId)
            .put("turnId", item.turnId ?: JSONObject.NULL)
            .put("itemId", item.serverItemId ?: JSONObject.NULL)
            .put("source", item.source.name.lowercase())
            .put("status", item.status.name.lowercase())
            .put("error", item.error)
            .put("requestId", item.requestId ?: JSONObject.NULL)
            .put("createdAtMs", item.createdAtMs)
            .put("updatedAtMs", item.updatedAtMs)
            .put("sequence", item.sequence)
        return NATIVE_COMPACTION_PREFIX + NativeBase64.encode(payload.toString().toByteArray(Charsets.UTF_8))
    }

    fun decodeCompaction(content: String, fallbackThreadId: String = ""): NativeCompactionItem? {
        if (!content.startsWith(NATIVE_COMPACTION_PREFIX)) return decodeLegacyNotice(content, fallbackThreadId)
        val payload = runCatching {
            JSONObject(String(NativeBase64.decode(content.substringAfter('|')), Charsets.UTF_8))
        }.getOrNull() ?: return null
        val id = payload.optString("id").ifBlank {
            "compaction:${payload.optString("threadId", payload.optString("thread_id", fallbackThreadId))}:${payload.optString("itemId", payload.optString("serverItemId", payload.optString("item_id", payload.optLong("createdAtMs", payload.optLong("created_at_ms")).toString())))}"
        }
        return NativeCompactionItem(
            id = id,
            threadId = payload.optString("threadId", payload.optString("thread_id", fallbackThreadId)),
            turnId = payload.cleanString("turnId") ?: payload.cleanString("turn_id"),
            serverItemId = payload.cleanString("itemId") ?: payload.cleanString("serverItemId") ?: payload.cleanString("item_id"),
            source = enumValue(payload.optString("source"), NativeCompactionSource.AUTOMATIC),
            status = enumValue(payload.optString("status"), NativeCompactionStatus.COMPLETED),
            error = payload.optString("error"),
            requestId = payload.cleanString("requestId") ?: payload.cleanString("request_id"),
            createdAtMs = payload.optLong("createdAtMs", payload.optLong("created_at_ms")),
            updatedAtMs = payload.optLong("updatedAtMs", payload.optLong("updated_at_ms", payload.optLong("createdAtMs", payload.optLong("created_at_ms")))),
            sequence = payload.optLong("sequence", payload.optLong("sequence_number")),
        )
    }

    fun decodeLegacyNotice(content: String, threadId: String = ""): NativeCompactionItem? {
        if (!content.startsWith("NOTICE|")) return null
        val text = content.substringAfter('|').trim()
        val lowered = text.lowercase()
        val compactionLike = listOf("压缩", "compact", "compaction").any(lowered::contains)
        if (!compactionLike) return null
        val status = when {
            listOf("失败", "failed", "error").any(lowered::contains) -> NativeCompactionStatus.FAILED
            listOf("取消", "cancel").any(lowered::contains) -> NativeCompactionStatus.CANCELLED
            listOf("正在", "开始", "启动", "starting", "started", "compacting", "in progress").any(lowered::contains) -> NativeCompactionStatus.RUNNING
            else -> NativeCompactionStatus.COMPLETED
        }
        val id = "legacy-compaction:${threadId}:${text.hashCode()}"
        return NativeCompactionItem(
            id = id,
            threadId = threadId,
            source = NativeCompactionSource.LEGACY,
            status = status,
            error = if (status == NativeCompactionStatus.FAILED) text else "",
        )
    }

    fun processGroup(message: NativeChatMessage, threadId: String = ""): NativeActivityGroup? {
        if (!message.content.startsWith("PROCESS2|") && !message.content.startsWith("PROCESS|")) return null
        val payload = runCatching {
            JSONObject(String(NativeBase64.decode(message.content.substringAfter('|')), Charsets.UTF_8))
        }.getOrNull() ?: return null
        return processGroup(message.id, threadId, payload, message.revealStartedAt)
    }

    /** Converts both current PROCESS2 and legacy PROCESS payloads through one renderer model. */
    fun processGroup(
        messageId: String,
        threadId: String = "",
        payload: JSONObject,
        createdAtMs: Long = 0L,
    ): NativeActivityGroup {
        val items = ArrayList<NativeActivityItem>()
        val tools = payload.optJSONArray("tools") ?: JSONArray()
        for (index in 0 until tools.length()) {
            val item = tools.optJSONObject(index)
                ?: runCatching { JSONObject(tools.optString(index)) }.getOrNull()
                ?: continue
            val type = when (normalizeType(item.optString("type", item.optString("item_type")))) {
                "commandexecution" -> NativeActivityItemType.COMMAND
                "filechange" -> NativeActivityItemType.FILE_CHANGE
                "websearch" -> NativeActivityItemType.WEB_SEARCH
                "collabagenttoolcall", "subagentactivity", "subagent", "subagenttoolcall" -> NativeActivityItemType.SUBAGENT
                else -> NativeActivityItemType.TOOL
            }
            val rawStatus = if (type == NativeActivityItemType.COMMAND) {
                NativeCommandOutputStore.resolvedCommandStatus(item)
            } else {
                item.optString("status", "completed")
            }.lowercase()
            val status = when (rawStatus) {
                "working", "running", "started", "start", "inprogress", "in_progress" -> NativeActivityItemStatus.RUNNING
                "waiting", "queued", "pending" -> NativeActivityItemStatus.WAITING
                "failed", "error", "cancelled", "canceled", "interrupted" -> NativeActivityItemStatus.FAILED
                else -> NativeActivityItemStatus.COMPLETED
            }
            val id = item.optString("id", item.optString("itemId", item.optString("callId", "$messageId:$index")))
            items += NativeActivityItem(
                id = id,
                type = type,
                title = when (type) {
                    NativeActivityItemType.COMMAND -> item.optString("command")
                    NativeActivityItemType.WEB_SEARCH -> item.optString("query")
                    NativeActivityItemType.SUBAGENT -> item.optString("agentName", item.optString("agentNickname"))
                    else -> item.optString("tool", item.optString("name", item.optString("type")))
                },
                text = item.optString("detail"),
                outputRef = item.optString(NativeCommandOutputStore.OUTPUT_REF, item.optString(NativeLargePayloadStore.PAYLOAD_REF)),
                outputPreview = item.optString(
                    NativeCommandOutputStore.OUTPUT_PREVIEW,
                    item.optString(
                        NativeLargePayloadStore.PAYLOAD_PREVIEW,
                        item.optString("aggregatedOutput", item.optString("output", item.optString("stdout"))).takeLast(4_096),
                    ),
                ),
                status = status,
                itemId = item.optString("id", item.optString("itemId")).takeIf(String::isNotBlank),
                agentThreadId = item.optString(
                    "agentThreadId",
                    item.optString("agent_thread_id", item.optString("threadId", item.optString("thread_id"))),
                ),
                callId = item.optString("callId", item.optString("call_id", item.optString("id"))),
            )
        }
        // Older PROCESS2 snapshots stored the active command beside `tools` instead of as a
        // commandExecution entry. Preserve it in the same grouped model without duplicating a
        // command that is already present in the tool array.
        val legacyCommand = payload.optString("command").trim()
        if (legacyCommand.isNotBlank() && items.none {
                it.type == NativeActivityItemType.COMMAND && it.title.trim() == legacyCommand
            }) {
            items.add(
                NativeActivityItem(
                    id = "$messageId:command",
                    type = NativeActivityItemType.COMMAND,
                    title = legacyCommand,
                    text = legacyCommand,
                    status = NativeActivityItemStatus.COMPLETED,
                    turnId = null,
                ),
            )
        }
        return NativeActivityGroup(
            key = "history:$messageId",
            threadId = threadId,
            turnId = null,
            segment = 0,
            reasoning = payload.optString("reasoning"),
            items = items,
            running = false,
            expandedByDefault = false,
            createdAtMs = createdAtMs,
            completedAtMs = createdAtMs,
        )
    }

    fun renderModel(
        threadId: String,
        messages: List<NativeChatMessage>,
        liveActivities: List<NativeActivityGroup> = emptyList(),
        liveCompactions: List<NativeCompactionItem> = emptyList(),
        liveSubagents: List<NativeSubagentVisual> = emptyList(),
    ): NativeConversationRenderModel {
        val activities = ArrayList<NativeActivityGroup>()
        val compactionTimeline = ArrayList<NativeCompactionItem>()
        val plans = ArrayList<NativePlanRenderItem>()
        val assistants = ArrayList<NativeConversationTextMessage>()
        val users = ArrayList<NativeConversationTextMessage>()
        val errors = ArrayList<String>()
        fun addPlan(candidate: NativePlanRenderItem) {
            val index = plans.indexOfFirst { existing -> plansEquivalent(existing.text, candidate.text) }
            if (index < 0) plans += candidate
            else if (candidate.dedicated && !plans[index].dedicated) plans[index] = candidate
        }
        messages.forEach { message ->
            when (message.role) {
                NativeChatRole.USER -> users += NativeConversationTextMessage(message.id, message.content, message.streaming)
                NativeChatRole.ASSISTANT -> {
                    val parts = if (message.content.contains('<')) NativePlanStreamParser.splitComplete(message.content) else emptyList()
                    if (parts.any { it.role == "plan" }) {
                        parts.forEachIndexed { index, part ->
                            val value = part.text.trim()
                            if (value.isBlank()) return@forEachIndexed
                            if (part.role == "plan") addPlan(
                                NativePlanRenderItem(
                                    id = "${message.id}:plan:$index",
                                    text = value,
                                    streaming = message.streaming,
                                    dedicated = false,
                                ),
                            ) else assistants += NativeConversationTextMessage(
                                id = if (parts.size == 1) message.id else "${message.id}:assistant:$index",
                                text = value,
                                streaming = message.streaming,
                            )
                        }
                    } else assistants += NativeConversationTextMessage(message.id, message.content, message.streaming)
                }
                NativeChatRole.ERROR -> errors += message.content
                NativeChatRole.ACTIVITY -> when {
                    message.content.startsWith("PROCESS2|") || message.content.startsWith("PROCESS|") -> processGroup(message, threadId)?.let(activities::add)
                    message.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) -> {
                        val value = decodeNativeProposedPlan(message.content)
                        addPlan(
                            NativePlanRenderItem(
                                id = message.id,
                                text = value,
                                streaming = message.streaming,
                                dedicated = true,
                            ),
                        )
                    }
                    else -> decodeCompaction(message.content, threadId)?.let(compactionTimeline::add)
                }
            }
        }
        activities += liveActivities
        compactionTimeline += liveCompactions
        val compactions = mergeCompactionTimeline(compactionTimeline)
        return NativeConversationRenderModel(
            threadId = threadId,
            activities = activities,
            compactions = compactions,
            plans = plans,
            subagents = NativeSubagentVisualFactory.mergeAll(liveSubagents),
            assistantMessages = assistants,
            userMessages = users,
            errors = errors,
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String, fallback: T): T =
        runCatching { enumValueOf<T>(raw.uppercase()) }.getOrDefault(fallback)

    private fun normalizedPlan(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase()

    private fun plansEquivalent(left: String, right: String): Boolean {
        val a = normalizedPlan(left)
        val b = normalizedPlan(right)
        if (a.isBlank() || b.isBlank()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    private fun normalizeType(value: String): String = value
        .replace("_", "")
        .replace("-", "")
        .lowercase()

    private fun mergeCompaction(old: NativeCompactionItem, next: NativeCompactionItem): NativeCompactionItem = old.copy(
        threadId = next.threadId.ifBlank { old.threadId },
        turnId = next.turnId ?: old.turnId,
        serverItemId = next.serverItemId ?: old.serverItemId,
        source = when {
            old.source == NativeCompactionSource.LEGACY && next.source != NativeCompactionSource.LEGACY -> next.source
            next.source == NativeCompactionSource.LEGACY -> old.source
            next.source != NativeCompactionSource.AUTOMATIC || old.source == NativeCompactionSource.AUTOMATIC -> next.source
            else -> old.source
        },
        status = if (next.updatedAtMs >= old.updatedAtMs) next.status else old.status,
        error = next.error.ifBlank { old.error },
        requestId = next.requestId ?: old.requestId,
        createdAtMs = if (old.createdAtMs > 0L) old.createdAtMs else next.createdAtMs,
        updatedAtMs = maxOf(old.updatedAtMs, next.updatedAtMs),
        sequence = if (next.sequence > 0L) next.sequence else old.sequence,
    )

    private fun canMergeLegacyCompaction(old: NativeCompactionItem, next: NativeCompactionItem): Boolean {
        if (old.threadId.isNotBlank() && next.threadId.isNotBlank() && old.threadId != next.threadId) return false
        val oldHasServer = !old.serverItemId.isNullOrBlank()
        val nextHasServer = !next.serverItemId.isNullOrBlank()
        val oldHasRequest = !old.requestId.isNullOrBlank()
        val nextHasRequest = !next.requestId.isNullOrBlank()
        if (oldHasServer && nextHasServer && old.serverItemId != next.serverItemId) return false
        if (oldHasRequest && nextHasRequest && old.requestId != next.requestId) return false
        val complementaryIdentity =
            (oldHasRequest && !oldHasServer && nextHasServer && !nextHasRequest) ||
                (nextHasRequest && !nextHasServer && oldHasServer && !oldHasRequest)
        val oldTurn = old.turnId?.takeIf { it.isNotBlank() }
        val nextTurn = next.turnId?.takeIf { it.isNotBlank() }
        if (oldTurn != null && nextTurn != null && oldTurn != nextTurn && !complementaryIdentity) return false
        val oldUnidentified = !oldHasServer && !oldHasRequest
        val nextUnidentified = !nextHasServer && !nextHasRequest
        if (!oldUnidentified && !nextUnidentified && !complementaryIdentity) return false
        if (oldUnidentified && nextUnidentified && old.isTerminal == next.isTerminal) {
            // Two completed legacy notifications can be two real compactions close together.
            // Only bind an unidentified pair when it represents opposite lifecycle halves
            // (running/pending -> terminal). Exact duplicate ids were already handled above.
            return false
        }
        if (complementaryIdentity) {
            // Before the reducer bound manual pending markers to the dedicated compaction turn,
            // the journal stored one request-only manual record plus one server-only automatic
            // record. Their creation times are effectively simultaneous even though completion
            // notifications can be several seconds apart.
            val oldCreated = old.createdAtMs.takeIf { it > 0L } ?: old.updatedAtMs
            val nextCreated = next.createdAtMs.takeIf { it > 0L } ?: next.updatedAtMs
            val compatibleSource = old.source == next.source ||
                old.source == NativeCompactionSource.LEGACY || next.source == NativeCompactionSource.LEGACY ||
                setOf(old.source, next.source) == setOf(NativeCompactionSource.MANUAL, NativeCompactionSource.AUTOMATIC)
            return compatibleSource && oldCreated > 0L && nextCreated > 0L &&
                abs(oldCreated - nextCreated) <= PENDING_BIND_WINDOW_MS
        }
        val oldTime = old.updatedAtMs.takeIf { it > 0L } ?: old.createdAtMs
        val nextTime = next.updatedAtMs.takeIf { it > 0L } ?: next.createdAtMs
        if (oldTime <= 0L || nextTime <= 0L || abs(oldTime - nextTime) > LEGACY_BIND_WINDOW_MS) return false
        return true
    }

    private const val PENDING_BIND_WINDOW_MS = 2_500L
    private const val LEGACY_BIND_WINDOW_MS = 60_000L

    private fun JSONObject.cleanString(key: String): String? = optString(key)
        .trim()
        .takeIf { it.isNotEmpty() && !it.equals("null", true) }
}
