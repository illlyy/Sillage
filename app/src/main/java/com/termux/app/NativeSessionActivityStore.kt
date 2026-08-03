package com.termux.app

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Cross-conversation activity map (pattern: claudecodeui useSessionProtection.ts). One
 * process-wide source of truth for which threads are still working, so the drawer, the chat
 * activity indicator and any future UI derive from the same map instead of local copies.
 *
 * Concurrency note: writers must run on the main thread (the bridge dispatches events there).
 * The state is a plain Compose [mutableStateOf] map so composition observes it directly.
 */
@Immutable
internal data class NativeSessionActivity(
    val statusText: String = "",
    val canInterrupt: Boolean = false,
    /** Epoch ms of the first processing mark; survives idempotent re-marks. */
    val startedAt: Long = 0L,
)

internal object NativeSessionActivityStore {

    /** Local entries missing from a server snapshot survive for this long (race coverage). */
    const val LOCAL_ACTIVITY_GRACE_MS = 10_000L

    var activities: Map<String, NativeSessionActivity> by mutableStateOf(emptyMap())
        private set

    /**
     * Idempotent processing mark: preserves the original [NativeSessionActivity.startedAt]
     * and a non-blank status text from earlier marks.
     */
    fun markProcessing(
        threadId: String,
        statusText: String = "",
        canInterrupt: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ) {
        if (threadId.isBlank()) return
        val current = activities[threadId]
        val updated = current?.copy(
            statusText = statusText.ifBlank { current.statusText },
            canInterrupt = canInterrupt,
            startedAt = current.startedAt.takeIf { it > 0L } ?: now,
        ) ?: NativeSessionActivity(statusText, canInterrupt, now)
        if (current == updated) return
        activities = activities + (threadId to updated)
    }

    /**
     * Stale-ack guard: an idle ack describing a start before [ifStartedBefore] is discarded
     * when the entry started later (a newer request superseded the ack).
     */
    fun markIdle(
        threadId: String,
        ifStartedBefore: Long = Long.MAX_VALUE,
    ) {
        if (threadId.isBlank()) return
        val current = activities[threadId] ?: return
        if (current.startedAt >= ifStartedBefore) return
        activities = activities - threadId
    }

    /**
     * Merges an authoritative snapshot (e.g. task store) into the local map. Entries the
     * snapshot lacks survive for [LOCAL_ACTIVITY_GRACE_MS] to cover races.
     */
    fun sync(
        snapshots: Map<String, NativeSessionActivity>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (snapshots.isEmpty() && activities.isEmpty()) return
        val merged = LinkedHashMap<String, NativeSessionActivity>(activities)
        snapshots.forEach { (id, activity) -> merged[id] = activity }
        activities.forEach { (id, local) ->
            val orphaned = !snapshots.containsKey(id)
            val withinGrace = local.startedAt > 0L && now - local.startedAt < LOCAL_ACTIVITY_GRACE_MS
            if (orphaned && !withinGrace) merged.remove(id)
        }
        val next = merged.toMap()
        if (next != activities) activities = next
    }

    fun activityFor(threadId: String): NativeSessionActivity? = activities[threadId]

    fun isProcessing(threadId: String): Boolean = activities.containsKey(threadId)

    fun clear() {
        activities = emptyMap()
    }
}
