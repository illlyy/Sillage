package com.termux.app

/**
 * Normalized, UI-independent events emitted by the app-server bridge.
 *
 * The bridge is allowed to add fields to the payload as protocol versions evolve, but the
 * reducers only depend on this small stable contract.  Keeping sequence on every event is
 * important: app-server notifications can be delivered on different reader paths and a
 * reducer must be able to ignore stale/duplicate notifications without guessing from text.
 */
internal sealed interface NativeProtocolEvent {
    val threadId: String
    val turnId: String?
    val itemId: String?
    val sequence: Long
    val timestampMs: Long

    data class ReasoningDelta(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val delta: String,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class ReasoningCompleted(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val text: String = "",
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class AssistantDelta(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val delta: String,
        val itemPhase: String? = null,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class AssistantCompleted(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val text: String = "",
        val finalAnswer: Boolean = false,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class CommandStarted(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val command: String = "",
        val cwd: String = "",
        val payload: String = "",
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class CommandOutput(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val delta: String,
        val outputRef: String = "",
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class CommandCompleted(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val command: String = "",
        val outputRef: String = "",
        val status: String = "completed",
        val exitCode: Int? = null,
        val payload: String = "",
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class ToolCompleted(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val type: String,
        val title: String = "",
        val payloadRef: String = "",
        val status: String = "completed",
        val payload: String = "",
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class PlanStarted(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val dedicated: Boolean = true,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class PlanDelta(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val delta: String,
        val dedicated: Boolean = true,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class PlanCompleted(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val text: String = "",
        val dedicated: Boolean = true,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class SubagentUpdated(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val agentThreadId: String = "",
        val callId: String = "",
        val name: String = "",
        val status: String = "waiting",
        val alias: String = "",
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class CompactionStarted(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val source: NativeCompactionSource? = null,
        val requestId: String? = null,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class CompactionCompleted(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val error: String? = null,
        val cancelled: Boolean = false,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
        val source: NativeCompactionSource? = null,
        val requestId: String? = null,
    ) : NativeProtocolEvent

    data class CompactionFailed(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val error: String = "",
        val cancelled: Boolean = false,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
        val source: NativeCompactionSource? = null,
        val requestId: String? = null,
    ) : NativeProtocolEvent

    data class TokenUsageUpdated(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val inputTokens: Long = 0L,
        val cachedInputTokens: Long = 0L,
        val outputTokens: Long = 0L,
        val reasoningTokens: Long = 0L,
        val currentContextTokens: Long = 0L,
        val contextWindow: Long = 0L,
        val estimated: Boolean = false,
        val contextUsageReliable: Boolean = false,
        val autoCompactTokenLimit: Long = 0L,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class TurnCompleted(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val failed: Boolean = false,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class Error(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val message: String,
        val retryable: Boolean = false,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent

    data class Boundary(
        override val threadId: String,
        override val turnId: String? = null,
        override val itemId: String? = null,
        val kind: String,
        override val sequence: Long = 0L,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : NativeProtocolEvent
}

/** A small generic event used by protocol replay tests and forward-compatible bridge code. */
internal data class NativeProtocolRawEvent(
    override val threadId: String,
    override val turnId: String? = null,
    override val itemId: String? = null,
    val method: String,
    val payload: String = "",
    override val sequence: Long = 0L,
    override val timestampMs: Long = System.currentTimeMillis(),
) : NativeProtocolEvent

/**
 * Small, thread-aware reorder buffer used by live callbacks and protocol replay.  App-server
 * notifications can cross reader paths, so a callback can observe sequence 12 before sequence
 * 11.  The queue never waits forever for a missing sequence: callers can drain it at a UI flush
 * boundary, while already-emitted/stale events are ignored.
 */
internal class NativeOrderedProtocolEventQueue(
    private val maxPending: Int = 256,
) {
    private data class Entry(val order: Long, val event: NativeProtocolEvent)

    private val pending = java.util.PriorityQueue<Entry>(compareBy<Entry> {
        it.event.sequence.takeIf { sequence -> sequence > 0L } ?: Long.MAX_VALUE
    }.thenBy { it.event.timestampMs }.thenBy { it.order })
    private val lastEmittedByThread = HashMap<String, Long>()
    private var orderCounter = 0L

    @Synchronized
    fun offer(event: NativeProtocolEvent): List<NativeProtocolEvent> {
        if (event.sequence <= 0L) {
            // An unsequenced legacy callback is usually a completion barrier.  Do not let a
            // previously queued normalized delta drain after it.
            return drainLocked(0) + event
        }
        val thread = event.threadId.ifBlank { "thread" }
        if (event.sequence <= (lastEmittedByThread[thread] ?: Long.MIN_VALUE)) return emptyList()
        pending.removeIf { existing ->
            existing.event.threadId == event.threadId && existing.event.sequence == event.sequence
        }
        pending.add(Entry(++orderCounter, event))
        if (pending.size > maxPending) return drainLocked(maxPending / 2)
        return emptyList()
    }

    @Synchronized
    fun drain(): List<NativeProtocolEvent> = drainLocked(0)

    @Synchronized
    fun clear() {
        pending.clear()
        lastEmittedByThread.clear()
        orderCounter = 0L
    }

    private fun drainLocked(keepAtLeast: Int): List<NativeProtocolEvent> {
        if (pending.isEmpty()) return emptyList()
        val result = ArrayList<NativeProtocolEvent>(pending.size)
        while (pending.size > keepAtLeast) {
            val event = pending.poll()?.event ?: break
            val thread = event.threadId.ifBlank { "thread" }
            val previous = lastEmittedByThread[thread] ?: Long.MIN_VALUE
            if (event.sequence <= previous) continue
            lastEmittedByThread[thread] = event.sequence
            result += event
        }
        return result
    }
}
