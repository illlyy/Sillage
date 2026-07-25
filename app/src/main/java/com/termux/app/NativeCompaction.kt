package com.termux.app

import java.util.LinkedHashMap
import java.util.UUID
import kotlin.math.roundToLong

internal enum class NativeCompactionSource { MANUAL, AUTOMATIC, LEGACY }
internal enum class NativeCompactionStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

/** One durable divider in the conversation. Its id never changes when the server item appears. */
internal data class NativeCompactionItem(
    val id: String,
    val threadId: String,
    val turnId: String? = null,
    val serverItemId: String? = null,
    val source: NativeCompactionSource = NativeCompactionSource.AUTOMATIC,
    val status: NativeCompactionStatus = NativeCompactionStatus.PENDING,
    val error: String = "",
    val requestId: String? = null,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = createdAtMs,
    val sequence: Long = 0L,
) {
    val isTerminal: Boolean
        get() = status in setOf(NativeCompactionStatus.COMPLETED, NativeCompactionStatus.FAILED, NativeCompactionStatus.CANCELLED)
}

internal data class NativeCompactionSettings(
    val enabled: Boolean = true,
    val fallbackPercent: Int = 90,
) {
    val normalizedPercent: Int get() = fallbackPercent.coerceIn(80, 95)
}

internal enum class NativeCompactionSkipReason {
    DISABLED,
    NO_CONTEXT_WINDOW,
    NO_RELIABLE_USAGE,
    ESTIMATED_USAGE,
    NO_ACTIVE_TURN,
    ALREADY_RUNNING,
    WAITING_FOR_INPUT,
    STOPPING,
    SERVER_THRESHOLD_NOT_REACHED,
    DEBOUNCED,
    ALREADY_REQUESTED,
}

internal enum class NativeCompactionTriggerSource { SERVER, FALLBACK }

internal data class NativeCompactionPolicyInput(
    val threadId: String,
    val turnId: String?,
    val contextWindow: Long,
    val usedTokens: Long,
    val serverAutoCompactTokenLimit: Long = 0L,
    val usageReliable: Boolean = true,
    val estimated: Boolean = false,
    val turnActive: Boolean = true,
    val compactionInProgress: Boolean = false,
    val waitingForUserInput: Boolean = false,
    val stopping: Boolean = false,
    val nowMs: Long = System.currentTimeMillis(),
)

internal data class NativeCompactionDecision(
    val shouldTrigger: Boolean,
    val source: NativeCompactionTriggerSource? = null,
    val threshold: Long = 0L,
    val skipReason: NativeCompactionSkipReason? = null,
    val diagnostics: Map<String, String> = emptyMap(),
)

/**
 * Stateful client fallback policy. The server remains authoritative; this policy is only used
 * when the server did not expose a trustworthy limit. It implements debounce, one request per
 * turn, and an 85% hysteresis release after a successful compaction.
 */
internal class NativeCompactionPolicy(
    private val settings: NativeCompactionSettings = NativeCompactionSettings(),
    private val debounceMs: Long = 1_500L,
    private val requestWindowMs: Long = 8_000L,
    private val releasePercent: Int = 85,
) {
    private var requestedTurn: String? = null
    private var requestedAtMs: Long = 0L
    private var runningTurn: String? = null
    private var latchedTurn: String? = null
    private var lastDecision: NativeCompactionDecision? = null

    fun evaluate(input: NativeCompactionPolicyInput): NativeCompactionDecision {
        val result = when {
            !settings.enabled -> skipped(NativeCompactionSkipReason.DISABLED)
            input.contextWindow <= 0L && input.serverAutoCompactTokenLimit <= 0L -> skipped(NativeCompactionSkipReason.NO_CONTEXT_WINDOW)
            !input.usageReliable -> skipped(NativeCompactionSkipReason.NO_RELIABLE_USAGE)
            input.estimated -> skipped(NativeCompactionSkipReason.ESTIMATED_USAGE)
            !input.turnActive -> skipped(NativeCompactionSkipReason.NO_ACTIVE_TURN)
            input.compactionInProgress || runningTurn != null -> skipped(NativeCompactionSkipReason.ALREADY_RUNNING)
            input.waitingForUserInput -> skipped(NativeCompactionSkipReason.WAITING_FOR_INPUT)
            input.stopping -> skipped(NativeCompactionSkipReason.STOPPING)
            else -> evaluateThreshold(input)
        }
        lastDecision = result
        return result
    }

    fun shouldTrigger(input: NativeCompactionPolicyInput): Boolean = evaluate(input).shouldTrigger

    fun markRequested(threadId: String, turnId: String?, nowMs: Long = System.currentTimeMillis()) {
        requestedTurn = turnKey(threadId, turnId)
        requestedAtMs = nowMs
    }

    fun markStarted(threadId: String, turnId: String?) {
        val key = turnKey(threadId, turnId)
        runningTurn = key
        requestedTurn = key
        latchedTurn = key
    }

    fun markCompleted(threadId: String, turnId: String?, usedTokensAfter: Long = 0L, contextWindow: Long = 0L) {
        val key = turnKey(threadId, turnId)
        runningTurn = null
        // A zero post-compaction value means the callback did not carry usage metadata. Do not
        // treat missing data as "below 85%" or the same turn could issue a second request.
        if (contextWindow > 0 && usedTokensAfter > 0L &&
            usedTokensAfter.toDouble() * 100.0 < contextWindow.toDouble() * releasePercent
        ) {
            latchedTurn = null
            requestedTurn = null
            requestedAtMs = 0L
        } else {
            latchedTurn = key
        }
    }

    fun markFailed(threadId: String, turnId: String?) {
        val key = turnKey(threadId, turnId)
        runningTurn = null
        // A failed/cancelled lifecycle is not a successful compaction. Release both latches so a
        // later reliable usage update may retry the same turn after the normal debounce window.
        // Keeping the old latch here made a transient RPC failure look like a successful
        // compaction and permanently disabled automatic fallback for the rest of the turn.
        if (requestedTurn == key || latchedTurn == key) {
            requestedTurn = null
            latchedTurn = null
            requestedAtMs = 0L
        }
    }

    fun resetTurn(threadId: String, turnId: String?) {
        val key = turnKey(threadId, turnId)
        if (requestedTurn == key || runningTurn == key || latchedTurn == key) {
            requestedTurn = null
            runningTurn = null
            latchedTurn = null
            requestedAtMs = 0L
        }
    }

    fun reset() {
        requestedTurn = null
        requestedAtMs = 0L
        runningTurn = null
        latchedTurn = null
        lastDecision = null
    }

    fun lastDecision(): NativeCompactionDecision? = lastDecision

    private fun evaluateThreshold(input: NativeCompactionPolicyInput): NativeCompactionDecision {
        val serverThreshold = input.serverAutoCompactTokenLimit.takeIf { it > 0L }
        val threshold = serverThreshold ?: run {
            // Avoid overflowing a Long when a malformed model catalog reports an enormous window.
            (input.contextWindow.toDouble() * settings.normalizedPercent.toDouble() / 100.0)
                .coerceAtMost(Long.MAX_VALUE.toDouble())
                .roundToLong()
        }
        val key = turnKey(input.threadId, input.turnId)
        // A post-compaction token update can arrive after the lifecycle completion callback. Use
        // that reliable usage to release the hysteresis latch even if markCompleted saw the
        // pre-compaction total.
        if (latchedTurn == key && input.usedTokens > 0L && input.contextWindow > 0L &&
            input.usedTokens.toDouble() * 100.0 < input.contextWindow.toDouble() * releasePercent
        ) {
            latchedTurn = null
            requestedTurn = null
            requestedAtMs = 0L
        }
        if (threshold <= 0L || input.usedTokens < threshold) {
            return NativeCompactionDecision(
                shouldTrigger = false,
                source = if (serverThreshold != null) NativeCompactionTriggerSource.SERVER else NativeCompactionTriggerSource.FALLBACK,
                threshold = threshold,
                skipReason = NativeCompactionSkipReason.SERVER_THRESHOLD_NOT_REACHED,
                diagnostics = mapOf(
                    "compaction_fallback_skipped_reason" to "threshold_not_reached",
                    "compaction_trigger_source" to if (serverThreshold != null) "server" else "fallback",
                ),
            )
        }
        if (latchedTurn == key) {
            return skipped(NativeCompactionSkipReason.ALREADY_REQUESTED, threshold)
        }
        if (requestedTurn == key) {
            val age = input.nowMs - requestedAtMs
            if (age < debounceMs) return skipped(NativeCompactionSkipReason.DEBOUNCED, threshold)
            // A fallback request is a once-per-turn operation.  Older code treated the end of
            // requestWindowMs as permission to issue another request even when the server had
            // simply not emitted a lifecycle item yet, which could create a second compaction
            // and two dividers.  Only an explicit start/completion/failure/reset may release this
            // latch; requestWindowMs remains part of the constructor for binary/source
            // compatibility with callers that tune the debounce policy.
            return skipped(NativeCompactionSkipReason.ALREADY_REQUESTED, threshold)
        }
        return NativeCompactionDecision(
            shouldTrigger = true,
            source = if (serverThreshold != null) NativeCompactionTriggerSource.SERVER else NativeCompactionTriggerSource.FALLBACK,
            threshold = threshold,
            diagnostics = mapOf("compaction_trigger_source" to if (serverThreshold != null) "server" else "fallback"),
        )
    }

    private fun skipped(reason: NativeCompactionSkipReason, threshold: Long = 0L): NativeCompactionDecision {
        val diagnostics = LinkedHashMap<String, String>()
        diagnostics["compaction_fallback_skipped_reason"] = reason.name.lowercase()
        if (reason == NativeCompactionSkipReason.ALREADY_REQUESTED ||
            reason == NativeCompactionSkipReason.ALREADY_RUNNING
        ) {
            diagnostics["compaction_duplicate_suppressed"] = "true"
        }
        return NativeCompactionDecision(
            shouldTrigger = false,
            threshold = threshold,
            skipReason = reason,
            diagnostics = diagnostics,
        )
    }

    private fun turnKey(threadId: String, turnId: String?): String =
        threadId + ":" + turnId.orEmpty()
}

/**
 * Idempotent lifecycle reducer. Synthetic manual markers are consumed by the first matching
 * server item and are updated in place, so a start/completed pair always renders one divider.
 */
internal class NativeCompactionReducer(
    private val maxItems: Int = 64,
) {
    private val itemLimit = maxItems.coerceAtLeast(1)
    private val itemsById = LinkedHashMap<String, NativeCompactionItem>()
    private val aliases = HashMap<String, String>()
    private val pendingByThread = LinkedHashMap<String, ArrayDeque<String>>()
    /** RPC-only versions may complete before an item lifecycle appears. */
    private val requestAliases = HashMap<String, String>()
    private var manualCounter = 0L
    private val lastSequenceByThread = HashMap<String, Long>()
    private var duplicateSuppressed = 0

    fun reset() {
        itemsById.clear()
        aliases.clear()
        pendingByThread.clear()
        requestAliases.clear()
        manualCounter = 0L
        lastSequenceByThread.clear()
        duplicateSuppressed = 0
    }

    fun createManualPending(
        threadId: String,
        turnId: String? = null,
        requestId: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): NativeCompactionItem = createPending(threadId, turnId, requestId, NativeCompactionSource.MANUAL, nowMs)

    fun createAutomaticPending(
        threadId: String,
        turnId: String? = null,
        requestId: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): NativeCompactionItem = createPending(threadId, turnId, requestId, NativeCompactionSource.AUTOMATIC, nowMs)

    private fun createPending(
        threadId: String,
        turnId: String?,
        requestId: String?,
        source: NativeCompactionSource,
        nowMs: Long,
    ): NativeCompactionItem {
        val stableSuffix = requestId?.takeIf { it.isNotBlank() } ?: "${nowMs}-${++manualCounter}"
        val prefix = if (source == NativeCompactionSource.MANUAL) "manual-compaction" else "automatic-compaction"
        val id = "$prefix:${threadId.ifBlank { "thread" }}:$stableSuffix"
        val existing = itemsById[id]
        if (existing != null) {
            duplicateSuppressed++
            return existing
        }
        val item = NativeCompactionItem(
            id = id,
            threadId = threadId,
            turnId = turnId,
            source = source,
            status = NativeCompactionStatus.PENDING,
            requestId = requestId,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )
        itemsById[id] = item
        requestId?.takeIf { it.isNotBlank() }?.let { requestAliases[it] = id }
        pendingByThread.getOrPut(threadId) { ArrayDeque() }.addLast(id)
        trim()
        return item
    }

    fun accept(event: NativeProtocolEvent): NativeCompactionItem? {
        val sequenceThread = event.threadId.ifBlank { "thread" }
        val previousSequence = lastSequenceByThread[sequenceThread] ?: Long.MIN_VALUE
        if (event.sequence > 0L && event.sequence <= previousSequence) {
            duplicateSuppressed++
            // A terminal notification can legitimately arrive after a newer start on a different
            // reader path. Keep duplicate starts idempotent, but still apply a known completion or
            // failure to its correlated divider instead of leaving it permanently RUNNING.
            return when (event) {
                is NativeProtocolEvent.CompactionCompleted -> completed(event)
                is NativeProtocolEvent.CompactionFailed -> failed(event)
                else -> find(event.itemId, event.threadId)
            }
        }
        if (event.sequence > previousSequence) lastSequenceByThread[sequenceThread] = event.sequence
        return when (event) {
            is NativeProtocolEvent.CompactionStarted -> started(event)
            is NativeProtocolEvent.CompactionCompleted -> completed(event)
            is NativeProtocolEvent.CompactionFailed -> failed(event)
            else -> null
        }
    }

    fun onEvent(event: NativeProtocolEvent): NativeCompactionItem? = accept(event)

    fun onLegacyNotification(
        threadId: String,
        status: String,
        itemId: String? = null,
        error: String = "",
        sequence: Long = 0L,
        nowMs: Long = System.currentTimeMillis(),
    ): NativeCompactionItem = when (status.lowercase().replace('-', '_').replace(' ', '_')) {
        "started", "start", "begin", "begun", "running", "in_progress", "inprogress" -> started(
            NativeProtocolEvent.CompactionStarted(threadId, itemId = itemId, sequence = sequence, timestampMs = nowMs),
        ) ?: find(itemId, threadId) ?: createTerminal(
            threadId, null, itemId, NativeCompactionSource.LEGACY, null,
            NativeCompactionStatus.RUNNING, "", nowMs, sequence,
        )
        "cancelled", "canceled" -> failed(
            NativeProtocolEvent.CompactionFailed(threadId, itemId = itemId, error = error, cancelled = true, sequence = sequence, timestampMs = nowMs),
        ) ?: createTerminal(
            threadId, null, itemId, NativeCompactionSource.LEGACY, null,
            NativeCompactionStatus.CANCELLED, error, nowMs, sequence,
        )
        "failed", "failure", "error" -> failed(
            NativeProtocolEvent.CompactionFailed(threadId, itemId = itemId, error = error, sequence = sequence, timestampMs = nowMs),
        ) ?: createTerminal(
            threadId, null, itemId, NativeCompactionSource.LEGACY, null,
            NativeCompactionStatus.FAILED, error, nowMs, sequence,
        )
        else -> completed(
            NativeProtocolEvent.CompactionCompleted(threadId, itemId = itemId, sequence = sequence, timestampMs = nowMs),
        ) ?: createTerminal(
            threadId, null, itemId, NativeCompactionSource.LEGACY, null,
            NativeCompactionStatus.COMPLETED, "", nowMs, sequence,
        )
    }

    fun onRpcResult(
        threadId: String,
        requestId: String? = null,
        success: Boolean,
        cancelled: Boolean = false,
        error: String = "",
        nowMs: Long = System.currentTimeMillis(),
    ): NativeCompactionItem? {
        requestId?.takeIf { it.isNotBlank() }?.let { id ->
            requestAliases[id]?.let(itemsById::get)?.takeIf { it.isTerminal }?.let { return it }
        }
        val pending = pendingByThread[threadId]?.firstOrNull { id ->
            val item = itemsById[id]
            item != null && !item.isTerminal && (item.requestId == requestId || requestId.isNullOrBlank())
        } ?: return null
        val current = itemsById[pending] ?: return null
        val updated = current.copy(
            status = when {
                cancelled -> NativeCompactionStatus.CANCELLED
                success -> NativeCompactionStatus.COMPLETED
                else -> NativeCompactionStatus.FAILED
            },
            error = error,
            updatedAtMs = nowMs,
        )
        itemsById[pending] = updated
        requestId?.takeIf { it.isNotBlank() }?.let { requestAliases[it] = pending }
        if (updated.isTerminal) removePending(threadId, pending)
        return updated
    }

    fun items(threadId: String? = null): List<NativeCompactionItem> = itemsById.values
        .filter { threadId.isNullOrBlank() || it.threadId == threadId }
        .sortedWith(compareBy<NativeCompactionItem> { it.sequence.takeIf { seq -> seq > 0 } ?: Long.MAX_VALUE }.thenBy { it.createdAtMs }.thenBy { it.id })

    fun snapshot(threadId: String? = null): List<NativeCompactionItem> = items(threadId)

    /** Restore a journal/history item without manufacturing a new synthetic id. */
    fun restore(item: NativeCompactionItem): NativeCompactionItem {
        val existing = itemsById[item.id]
            ?: item.serverItemId?.let { serverId ->
                itemsById.values.firstOrNull { it.serverItemId == serverId }
            }
            ?: item.requestId?.takeIf { it.isNotBlank() }?.let { requestId ->
                itemsById.values.firstOrNull { it.requestId == requestId && it.threadId == item.threadId }
            }
            ?: run {
                // A server history item often has a new item id but no request id. If exactly
                // one synthetic lifecycle is still open for the same turn, it is safe to bind
                // the journal marker; with multiple pending requests we keep them independent.
                val candidates = itemsById.values.filter {
                    it.threadId == item.threadId && !it.isTerminal && it.serverItemId.isNullOrBlank() &&
                        (item.turnId.isNullOrBlank() || it.turnId == item.turnId)
                }
                candidates.singleOrNull()
            }
        if (existing != null) {
            val merged = mergeItems(existing, item)
            itemsById[existing.id] = merged
            item.serverItemId?.takeIf { it.isNotBlank() }?.let { aliases[it] = existing.id }
            merged.requestId?.takeIf { it.isNotBlank() }?.let { requestAliases[it] = existing.id }
            if (!merged.isTerminal) pendingByThread.getOrPut(merged.threadId) { ArrayDeque() }
                .also { if (!it.contains(existing.id)) it.addLast(existing.id) }
            return merged
        }
        itemsById[item.id] = item
        item.serverItemId?.takeIf { it.isNotBlank() }?.let { aliases[it] = item.id }
        item.requestId?.takeIf { it.isNotBlank() }?.let { requestAliases[it] = item.id }
        if (!item.isTerminal) pendingByThread.getOrPut(item.threadId) { ArrayDeque() }.addLast(item.id)
        trim()
        return item
    }

    fun duplicateSuppressedCount(): Int = duplicateSuppressed

    private fun started(event: NativeProtocolEvent.CompactionStarted): NativeCompactionItem? {
        val serverId = event.itemId?.takeIf { it.isNotBlank() }
        val existing = findExact(serverId)
        val pendingCandidates = pendingByThread[event.threadId]?.filter { id ->
            val candidate = itemsById[id]
            candidate != null &&
                // If a newer server explicitly tells us whether this is an automatic or manual
                // lifecycle, never consume a marker belonging to the other source.
                (event.source == null || candidate.source == event.source || candidate.source == NativeCompactionSource.LEGACY)
        }.orEmpty()
        val sameTurnPending = pendingCandidates.filter { id ->
            val candidate = itemsById.getValue(id)
            event.turnId.isNullOrBlank() || candidate.turnId.isNullOrBlank() || candidate.turnId == event.turnId
        }
        // A request id is the strongest association. If an old server does not echo it, consume
        // the oldest pending marker for this turn so two manual clicks still produce two dividers.
        // `thread/compact/start` creates its own server turn on current app-server builds. The
        // synthetic marker was created before that turn exists and therefore carries the previous
        // conversation turn id. Fall back to the oldest eligible marker on the thread instead of
        // manufacturing a second automatic divider for the same manual request.
        val pending = pendingCandidates.firstOrNull { id ->
            val candidate = itemsById[id]
            candidate?.requestId?.let { it.isNotBlank() && it == event.requestId } == true
        } ?: sameTurnPending.firstOrNull { id -> itemsById[id]?.isTerminal == false }
            ?: pendingCandidates.firstOrNull { id -> itemsById[id]?.isTerminal == false }
            ?: pendingCandidates.firstOrNull()
        // A few legacy notifications omit itemId.  If the same lifecycle is replayed, reuse the
        // currently running marker instead of manufacturing another divider.  Concurrent modern
        // requests carry item ids and therefore remain independent.
        val activeLegacy = if (serverId.isNullOrBlank()) itemsById.values.lastOrNull { candidate ->
            candidate.threadId == event.threadId && !candidate.isTerminal &&
                (event.turnId.isNullOrBlank() || candidate.turnId == event.turnId) &&
                (event.source == null || candidate.source == event.source || candidate.source == NativeCompactionSource.LEGACY)
        } else null
        val requestBound = event.requestId?.takeIf { it.isNotBlank() }?.let { requestAliases[it] }?.let(itemsById::get)
        val terminalRpcBound = if (requestBound == null && event.requestId.isNullOrBlank()) {
            val candidates = itemsById.values.filter { candidate ->
                candidate.threadId == event.threadId && candidate.isTerminal &&
                    candidate.serverItemId.isNullOrBlank() &&
                    (event.source == null || candidate.source == event.source) &&
                    candidate.source != NativeCompactionSource.LEGACY &&
                    event.timestampMs >= candidate.updatedAtMs &&
                    event.timestampMs - candidate.updatedAtMs <= RPC_BIND_WINDOW_MS
            }
            candidates.firstOrNull { candidate ->
                event.turnId.isNullOrBlank() || candidate.turnId.isNullOrBlank() || candidate.turnId == event.turnId
            } ?: candidates.singleOrNull()
        } else null
        val target = existing ?: requestBound ?: pending?.let(itemsById::get) ?: activeLegacy ?: terminalRpcBound
        if (target?.isTerminal == true) {
            val rebound = target.copy(
                serverItemId = serverId ?: target.serverItemId,
                turnId = event.turnId ?: target.turnId,
                requestId = event.requestId ?: target.requestId,
                updatedAtMs = maxOf(target.updatedAtMs, event.timestampMs),
            )
            itemsById[target.id] = rebound
            if (!serverId.isNullOrBlank()) aliases[serverId] = target.id
            event.requestId?.takeIf { it.isNotBlank() }?.let { requestAliases[it] = target.id }
            duplicateSuppressed++
            return rebound
        }
        val source = event.source ?: target?.source ?: if (pending != null) NativeCompactionSource.MANUAL else NativeCompactionSource.AUTOMATIC
        val id = target?.id ?: "compaction:${event.threadId.ifBlank { "thread" }}:${serverId ?: event.sequence.takeIf { it > 0 } ?: UUID.randomUUID()}"
        val updated = (target ?: NativeCompactionItem(
            id = id,
            threadId = event.threadId,
            turnId = event.turnId,
            source = source,
            createdAtMs = event.timestampMs,
        )).copy(
            serverItemId = serverId ?: target?.serverItemId,
            turnId = event.turnId ?: target?.turnId,
            source = source,
            status = NativeCompactionStatus.RUNNING,
            requestId = event.requestId ?: target?.requestId,
            updatedAtMs = event.timestampMs,
            sequence = event.sequence.takeIf { it > 0 } ?: target?.sequence ?: 0L,
        )
        itemsById[id] = updated
        if (!serverId.isNullOrBlank()) aliases[serverId] = id
        updated.requestId?.takeIf { it.isNotBlank() }?.let { requestAliases[it] = id }
        if (!updated.isTerminal) {
            pendingByThread.getOrPut(event.threadId) { ArrayDeque() }
                .also { if (!it.contains(id)) it.addLast(id) }
        }
        if (pending != null && pending != id) {
            aliases[pending] = id
            // Keep the synthetic id as the visual identity. The code above intentionally chooses
            // target=pending first; this branch is only reached for an alias collision.
            itemsById.remove(pending)
            removePending(event.threadId, pending)
        }
        trim()
        return updated
    }

    private fun completed(event: NativeProtocolEvent.CompactionCompleted): NativeCompactionItem? {
        val current = (findRequest(event.requestId)
            ?: findExact(event.itemId)
            ?: if (!event.itemId.isNullOrBlank() || !event.requestId.isNullOrBlank()) started(
                NativeProtocolEvent.CompactionStarted(
                    threadId = event.threadId,
                    turnId = event.turnId,
                    itemId = event.itemId,
                    source = event.source,
                    requestId = event.requestId,
                    sequence = event.sequence,
                    timestampMs = event.timestampMs,
                ),
            ) else find(event.itemId, event.threadId, event.turnId, event.source, event.timestampMs)
            ?: createTerminal(
                threadId = event.threadId,
                turnId = event.turnId,
                serverItemId = event.itemId,
                source = event.source ?: NativeCompactionSource.AUTOMATIC,
                requestId = event.requestId,
                status = if (event.cancelled || !event.error.isNullOrBlank()) NativeCompactionStatus.FAILED else NativeCompactionStatus.COMPLETED,
                error = event.error.orEmpty(),
                timestampMs = event.timestampMs,
                sequence = event.sequence,
             )) ?: return null
        val status = when {
            event.cancelled -> NativeCompactionStatus.CANCELLED
            event.error.isNullOrBlank() -> NativeCompactionStatus.COMPLETED
            else -> NativeCompactionStatus.FAILED
        }
        val updated = current.copy(status = status, error = event.error.orEmpty(), updatedAtMs = event.timestampMs)
        itemsById[current.id] = updated
        removePending(event.threadId, current.id)
        return updated
    }

    private fun failed(event: NativeProtocolEvent.CompactionFailed): NativeCompactionItem? {
        val current = (findRequest(event.requestId)
            ?: findExact(event.itemId)
            ?: if (!event.itemId.isNullOrBlank() || !event.requestId.isNullOrBlank()) started(
                NativeProtocolEvent.CompactionStarted(
                    threadId = event.threadId,
                    turnId = event.turnId,
                    itemId = event.itemId,
                    source = event.source,
                    requestId = event.requestId,
                    sequence = event.sequence,
                    timestampMs = event.timestampMs,
                ),
            ) else find(event.itemId, event.threadId, event.turnId, event.source, event.timestampMs)
            ?: createTerminal(
                threadId = event.threadId,
                turnId = event.turnId,
                serverItemId = event.itemId,
                source = event.source ?: NativeCompactionSource.AUTOMATIC,
                requestId = event.requestId,
                status = if (event.cancelled) NativeCompactionStatus.CANCELLED else NativeCompactionStatus.FAILED,
                error = event.error,
                timestampMs = event.timestampMs,
                sequence = event.sequence,
             )) ?: return null
        val updated = current.copy(
            status = if (event.cancelled) NativeCompactionStatus.CANCELLED else NativeCompactionStatus.FAILED,
            error = event.error,
            updatedAtMs = event.timestampMs,
        )
        itemsById[current.id] = updated
        removePending(event.threadId, current.id)
        return updated
    }

    private fun findRequest(requestId: String?): NativeCompactionItem? = requestId
        ?.takeIf { it.isNotBlank() }
        ?.let { requestAliases[it] }
        ?.let(itemsById::get)

    private fun createTerminal(
        threadId: String,
        turnId: String?,
        serverItemId: String?,
        source: NativeCompactionSource,
        requestId: String?,
        status: NativeCompactionStatus,
        error: String,
        timestampMs: Long,
        sequence: Long,
    ): NativeCompactionItem {
        val suffix = serverItemId?.takeIf { it.isNotBlank() }
            ?: requestId?.takeIf { it.isNotBlank() }
            ?: sequence.takeIf { it > 0L }
            ?: timestampMs
        val id = "compaction:${threadId.ifBlank { "thread" }}:$suffix"
        val item = NativeCompactionItem(
            id = id,
            threadId = threadId,
            turnId = turnId,
            serverItemId = serverItemId?.takeIf { it.isNotBlank() },
            source = source,
            status = status,
            error = error,
            requestId = requestId?.takeIf { it.isNotBlank() },
            createdAtMs = timestampMs,
            updatedAtMs = timestampMs,
            sequence = sequence,
        )
        itemsById[id] = item
        item.serverItemId?.let { aliases[it] = id }
        item.requestId?.let { requestAliases[it] = id }
        trim()
        return item
    }

    private fun find(
        itemId: String?,
        threadId: String,
        turnId: String? = null,
        source: NativeCompactionSource? = null,
        timestampMs: Long = 0L,
    ): NativeCompactionItem? {
        findExact(itemId)?.let { return it }
        // An item id can be introduced only on the server side. Prefer a synthetic marker that
        // has not been bound yet; falling back to an already-running server item can attach a
        // late completion to the wrong divider when two compactions overlap in a replay.
        val active = itemsById.values.firstOrNull {
            it.threadId == threadId && !it.isTerminal && it.serverItemId.isNullOrBlank() &&
                (turnId.isNullOrBlank() || it.turnId.isNullOrBlank() || it.turnId == turnId) &&
                (source == null || it.source == source || it.source == NativeCompactionSource.LEGACY)
        } ?: itemsById.values.firstOrNull {
            it.threadId == threadId && !it.isTerminal &&
                (turnId.isNullOrBlank() || it.turnId.isNullOrBlank() || it.turnId == turnId) &&
                (source == null || it.source == source || it.source == NativeCompactionSource.LEGACY)
        }
        if (active != null) return active
        // RPC-only servers can return success before emitting item/completed. A subsequent
        // lifecycle completion without an id should bind to that just-finished synthetic marker
        // instead of appending a second divider. Restrict this to a short same-turn window.
        return itemsById.values.lastOrNull {
            it.threadId == threadId && it.isTerminal && it.serverItemId.isNullOrBlank() &&
                it.source != NativeCompactionSource.LEGACY &&
                (turnId.isNullOrBlank() || it.turnId.isNullOrBlank() || it.turnId == turnId) &&
                (source == null || it.source == source) &&
                (timestampMs <= 0L || (timestampMs >= it.updatedAtMs && timestampMs - it.updatedAtMs <= RPC_BIND_WINDOW_MS))
        }
    }

    private fun findExact(itemId: String?): NativeCompactionItem? {
        if (!itemId.isNullOrBlank()) {
            val id = aliases[itemId] ?: itemId
            itemsById[id]?.let { return it }
            itemsById.values.firstOrNull { it.serverItemId == itemId }?.let { return it }
        }
        return null
    }

    private fun removePending(threadId: String, id: String) {
        pendingByThread[threadId]?.remove(id)
        if (pendingByThread[threadId].isNullOrEmpty()) pendingByThread.remove(threadId)
    }

    private fun trim() {
        while (itemsById.size > itemLimit) {
            // Remove the oldest terminal item anywhere in the insertion order. A pending item
            // must not pin the whole map above its configured bound when it happens to be first.
            val removable = itemsById.entries.firstOrNull { it.value.isTerminal }
                ?: itemsById.entries.firstOrNull()
                ?: break
            removeItem(removable.key)
        }
    }

    private fun removeItem(id: String) {
        val removed = itemsById.remove(id) ?: return
        removePending(removed.threadId, id)
        aliases.entries.removeIf { it.value == id }
        requestAliases.entries.removeIf { it.value == id }
    }

    private fun mergeItems(old: NativeCompactionItem, next: NativeCompactionItem): NativeCompactionItem {
        val nextIsNewer = next.updatedAtMs >= old.updatedAtMs
        return old.copy(
            threadId = next.threadId.ifBlank { old.threadId },
            turnId = next.turnId ?: old.turnId,
            serverItemId = next.serverItemId ?: old.serverItemId,
            source = when {
                old.source == NativeCompactionSource.LEGACY && next.source != NativeCompactionSource.LEGACY -> next.source
                next.source == NativeCompactionSource.LEGACY -> old.source
                next.source != NativeCompactionSource.AUTOMATIC || old.source == NativeCompactionSource.AUTOMATIC -> next.source
                else -> old.source
            },
            status = if (nextIsNewer) next.status else old.status,
            error = next.error.ifBlank { old.error },
            requestId = next.requestId ?: old.requestId,
            createdAtMs = if (old.createdAtMs > 0L) old.createdAtMs else next.createdAtMs,
            updatedAtMs = maxOf(old.updatedAtMs, next.updatedAtMs),
            sequence = if (next.sequence > 0L) next.sequence else old.sequence,
        )
    }

    private companion object {
        const val RPC_BIND_WINDOW_MS = 30_000L
    }
}

/** Bounded in-memory journal; the Android adapter persists its compact JSON representation. */
internal class NativeCompactionJournal(
    private val maxThreads: Int = 16,
    private val maxItemsPerThread: Int = 32,
) {
    private val entries = LinkedHashMap<String, MutableList<NativeCompactionItem>>(maxThreads, 0.75f, true)

    @Synchronized
    fun record(item: NativeCompactionItem) {
        val list = entries.getOrPut(item.threadId) { ArrayList() }
        val index = list.indexOfFirst { it.id == item.id || (item.serverItemId != null && it.serverItemId == item.serverItemId) }
        if (index >= 0) list[index] = merge(list[index], item) else list.add(item)
        list.sortWith(compareBy<NativeCompactionItem> { it.updatedAtMs }.thenBy { it.id })
        while (list.size > maxItemsPerThread) list.removeAt(0)
        while (entries.size > maxThreads) entries.entries.iterator().let { it.next(); it.remove() }
    }

    @Synchronized
    fun read(threadId: String): List<NativeCompactionItem> = entries[threadId]?.toList().orEmpty()

    @Synchronized
    fun merge(threadId: String, incoming: Iterable<NativeCompactionItem>): List<NativeCompactionItem> {
        incoming.forEach(::record)
        return read(threadId)
    }

    @Synchronized
    fun clear() = entries.clear()

    private fun merge(old: NativeCompactionItem, next: NativeCompactionItem): NativeCompactionItem = old.copy(
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
        createdAtMs = if (old.createdAtMs > 0) old.createdAtMs else next.createdAtMs,
        updatedAtMs = maxOf(old.updatedAtMs, next.updatedAtMs),
        sequence = if (next.sequence > 0) next.sequence else old.sequence,
    )
}
