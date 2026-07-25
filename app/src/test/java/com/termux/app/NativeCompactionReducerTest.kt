package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCompactionReducerTest {
    @Test
    fun manualPendingStartedCompletedKeepsOneStableDivider() {
        val reducer = NativeCompactionReducer()
        val pending = reducer.createManualPending("thread", "turn", "request", 10)
        val started = reducer.accept(
            NativeProtocolEvent.CompactionStarted("thread", "turn", "server-item", sequence = 11, timestampMs = 20),
        )!!
        val completed = reducer.accept(
            NativeProtocolEvent.CompactionCompleted("thread", "turn", "server-item", sequence = 12, timestampMs = 30),
        )!!

        assertEquals(pending.id, started.id)
        assertEquals(pending.id, completed.id)
        assertEquals(NativeCompactionSource.MANUAL, completed.source)
        assertEquals(NativeCompactionStatus.COMPLETED, completed.status)
        assertEquals(1, reducer.items().size)
    }

    @Test
    fun manualPendingBindsWhenServerCreatesDedicatedCompactionTurn() {
        val reducer = NativeCompactionReducer()
        val pending = reducer.createManualPending("thread", "conversation-turn", "request", 10)
        val started = reducer.accept(
            NativeProtocolEvent.CompactionStarted(
                threadId = "thread",
                turnId = "compaction-turn",
                itemId = "server-item",
                sequence = 11,
                timestampMs = 20,
            ),
        )!!
        val completed = reducer.accept(
            NativeProtocolEvent.CompactionCompleted(
                threadId = "thread",
                turnId = "compaction-turn",
                itemId = "server-item",
                sequence = 12,
                timestampMs = 30,
            ),
        )!!

        assertEquals(pending.id, started.id)
        assertEquals(pending.id, completed.id)
        assertEquals("compaction-turn", completed.turnId)
        assertEquals(NativeCompactionSource.MANUAL, completed.source)
        assertEquals(NativeCompactionStatus.COMPLETED, completed.status)
        assertEquals(1, reducer.items().size)
    }

    @Test
    fun lifecycleWithoutPendingIsAutomatic() {
        val reducer = NativeCompactionReducer()
        val started = reducer.accept(NativeProtocolEvent.CompactionStarted("thread", "turn", "auto", sequence = 1))!!
        assertEquals(NativeCompactionSource.AUTOMATIC, started.source)
        assertEquals(NativeCompactionStatus.RUNNING, started.status)
    }

    @Test
    fun completionBeforeStartAndDuplicatesAreIdempotent() {
        val reducer = NativeCompactionReducer()
        reducer.accept(NativeProtocolEvent.CompactionCompleted("thread", "turn", "item", sequence = 2))
        reducer.accept(NativeProtocolEvent.CompactionCompleted("thread", "turn", "item", sequence = 2))
        reducer.accept(NativeProtocolEvent.CompactionStarted("thread", "turn", "item", sequence = 1))

        assertEquals(1, reducer.items().size)
        assertEquals(NativeCompactionStatus.COMPLETED, reducer.items().single().status)
        assertTrue(reducer.duplicateSuppressedCount() >= 1)
    }

    @Test
    fun multipleManualRequestsUseDifferentStableIdsAndRpcFailureUpdatesOne() {
        val reducer = NativeCompactionReducer()
        val first = reducer.createManualPending("thread", requestId = "one", nowMs = 1)
        val second = reducer.createManualPending("thread", requestId = "two", nowMs = 2)
        val failed = reducer.onRpcResult("thread", "one", success = false, error = "boom", nowMs = 3)!!

        assertNotEquals(first.id, second.id)
        assertEquals(first.id, failed.id)
        assertEquals(NativeCompactionStatus.FAILED, failed.status)
        assertEquals("boom", failed.error)
        assertEquals(NativeCompactionStatus.PENDING, reducer.items().first { it.id == second.id }.status)
    }

    @Test
    fun legacyNotificationSupportsCancelAndCompletion() {
        val reducer = NativeCompactionReducer()
        val running = reducer.onLegacyNotification("thread", "started", "legacy", nowMs = 1)
        val done = reducer.onLegacyNotification("thread", "completed", "legacy", nowMs = 2)
        assertEquals(running.id, done.id)
        assertEquals(NativeCompactionStatus.COMPLETED, done.status)
    }

    @Test
    fun requestIdAssociatesOutOfOrderManualStartsWithoutCreatingAnotherDivider() {
        val reducer = NativeCompactionReducer()
        val first = reducer.createManualPending("thread", requestId = "first", nowMs = 1)
        val second = reducer.createManualPending("thread", requestId = "second", nowMs = 2)
        val startedSecond = reducer.accept(
            NativeProtocolEvent.CompactionStarted(
                threadId = "thread", itemId = "server-second", requestId = "second", sequence = 10, timestampMs = 10,
            ),
        )!!
        val startedFirst = reducer.accept(
            NativeProtocolEvent.CompactionStarted(
                threadId = "thread", itemId = "server-first", requestId = "first", sequence = 11, timestampMs = 11,
            ),
        )!!

        assertEquals(second.id, startedSecond.id)
        assertEquals(first.id, startedFirst.id)
        assertEquals(2, reducer.items().size)
    }

    @Test
    fun restoringJournalKeepsSyntheticIdentityAndAllowsServerCompletion() {
        val reducer = NativeCompactionReducer()
        val restored = reducer.restore(
            NativeCompactionItem(
                id = "manual:thread:req",
                threadId = "thread",
                requestId = "req",
                source = NativeCompactionSource.MANUAL,
                status = NativeCompactionStatus.RUNNING,
                updatedAtMs = 5,
            ),
        )
        val completed = reducer.accept(
            NativeProtocolEvent.CompactionCompleted("thread", itemId = "server-item", sequence = 2, timestampMs = 6),
        )!!
        assertEquals(restored.id, completed.id)
        assertEquals(NativeCompactionStatus.COMPLETED, completed.status)
    }

    @Test
    fun legacyStartedWithoutItemIdIsNotDuplicated() {
        val reducer = NativeCompactionReducer()
        val first = reducer.onLegacyNotification("thread", "started", nowMs = 1)
        val replay = reducer.onLegacyNotification("thread", "started", nowMs = 2)
        val cancelled = reducer.onLegacyNotification("thread", "cancelled", error = "user", nowMs = 3)
        assertEquals(first.id, replay.id)
        assertEquals(first.id, cancelled.id)
        assertEquals(NativeCompactionStatus.CANCELLED, cancelled.status)
        assertEquals(1, reducer.items().size)
    }

    @Test
    fun explicitAutomaticLifecycleDoesNotConsumeManualMarker() {
        val reducer = NativeCompactionReducer()
        val manual = reducer.createManualPending("thread", requestId = "manual", nowMs = 1)
        val automatic = reducer.accept(
            NativeProtocolEvent.CompactionStarted(
                threadId = "thread",
                turnId = "turn",
                itemId = "automatic-item",
                source = NativeCompactionSource.AUTOMATIC,
                sequence = 2,
                timestampMs = 2,
            ),
        )!!
        assertEquals(NativeCompactionSource.AUTOMATIC, automatic.source)
        assertEquals(manual.id, reducer.items().first { it.id == manual.id }.id)
        assertEquals(2, reducer.items().size)
    }

    @Test
    fun completionWithoutStartBindsServerIdToPendingMarker() {
        val reducer = NativeCompactionReducer()
        val pending = reducer.createManualPending("thread", "turn", "request", nowMs = 1)
        val completed = reducer.accept(
            NativeProtocolEvent.CompactionCompleted(
                "thread", "turn", "server-item", sequence = 2, timestampMs = 2,
            ),
        )!!

        assertEquals(pending.id, completed.id)
        assertEquals("server-item", completed.serverItemId)
        assertEquals(1, reducer.items().size)
    }

    @Test
    fun delayedLifecycleAfterRpcSuccessReusesTerminalDivider() {
        val reducer = NativeCompactionReducer()
        val pending = reducer.createManualPending("thread", "turn", "request", nowMs = 1)
        reducer.onRpcResult("thread", "request", success = true, nowMs = 2)
        val started = reducer.accept(
            NativeProtocolEvent.CompactionStarted(
                "thread", "turn", "server-item", requestId = "request", sequence = 3, timestampMs = 3,
            ),
        )!!

        assertEquals(pending.id, started.id)
        assertEquals("server-item", started.serverItemId)
        assertEquals(NativeCompactionStatus.COMPLETED, started.status)
        assertEquals(1, reducer.items().size)
    }

    @Test
    fun delayedLifecycleAfterRpcSuccessCanUseDedicatedCompactionTurn() {
        val reducer = NativeCompactionReducer()
        val pending = reducer.createManualPending("thread", "conversation-turn", "request", nowMs = 1)
        reducer.onRpcResult("thread", "request", success = true, nowMs = 2)
        val started = reducer.accept(
            NativeProtocolEvent.CompactionStarted(
                threadId = "thread",
                turnId = "compaction-turn",
                itemId = "server-item",
                sequence = 3,
                timestampMs = 3,
            ),
        )!!

        assertEquals(pending.id, started.id)
        assertEquals("server-item", started.serverItemId)
        assertEquals("compaction-turn", started.turnId)
        assertEquals(NativeCompactionStatus.COMPLETED, started.status)
        assertEquals(1, reducer.items().size)
    }

    @Test
    fun reducerRemainsBoundedEvenWhenEveryItemIsPending() {
        val reducer = NativeCompactionReducer(maxItems = 2)
        reducer.createManualPending("thread", requestId = "one", nowMs = 1)
        reducer.createManualPending("thread", requestId = "two", nowMs = 2)
        reducer.createManualPending("thread", requestId = "three", nowMs = 3)
        assertEquals(2, reducer.items().size)
    }

    @Test
    fun completionWithoutStartAndWithoutPendingCreatesAutomaticDivider() {
        val reducer = NativeCompactionReducer()
        val completed = reducer.accept(
            NativeProtocolEvent.CompactionCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "server-only",
                sequence = 4,
                timestampMs = 10,
            ),
        )!!

        assertEquals(NativeCompactionSource.AUTOMATIC, completed.source)
        assertEquals(NativeCompactionStatus.COMPLETED, completed.status)
        assertEquals(1, reducer.items().size)
    }

    @Test
    fun requestIdBindsCompletionWhenServerOmitsItemId() {
        val reducer = NativeCompactionReducer()
        val first = reducer.createManualPending("thread", "turn", "first", nowMs = 1)
        val second = reducer.createManualPending("thread", "turn", "second", nowMs = 2)
        val completed = reducer.accept(
            NativeProtocolEvent.CompactionCompleted(
                threadId = "thread",
                turnId = "turn",
                requestId = "second",
                sequence = 3,
                timestampMs = 3,
            ),
        )!!

        assertEquals(second.id, completed.id)
        assertEquals(NativeCompactionStatus.COMPLETED, completed.status)
        assertEquals(NativeCompactionStatus.PENDING, reducer.items().first { it.id == first.id }.status)
    }

    @Test
    fun outOfOrderCompletionStillClosesKnownRunningItem() {
        val reducer = NativeCompactionReducer()
        reducer.accept(NativeProtocolEvent.CompactionStarted("thread", "turn", "item", sequence = 10, timestampMs = 10))
        val completed = reducer.accept(NativeProtocolEvent.CompactionCompleted("thread", "turn", "item", sequence = 9, timestampMs = 11))!!
        assertEquals(NativeCompactionStatus.COMPLETED, completed.status)
    }
}
