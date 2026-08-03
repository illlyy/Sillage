package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSessionActivityStoreTest {

    private fun storeWith(now: Long): Pair<NativeSessionActivityStore, Long> {
        // The store is a process singleton; tests reset it and track the fake clock.
        NativeSessionActivityStore.clear()
        return NativeSessionActivityStore to now
    }

    @Test
    fun blankThreadIdIsIgnored() {
        val (store, now) = storeWith(1_000L)
        store.markProcessing("", now = now)
        assertTrue(store.activities.isEmpty())
    }

    @Test
    fun markProcessingPreservesStartedAtAcrossRemarks() {
        val (store, now) = storeWith(1_000L)
        store.markProcessing("t", "first", now = now)
        store.markProcessing("t", "second", canInterrupt = true, now = now + 5_000L)
        val activity = store.activityFor("t")!!
        assertEquals(1_000L, activity.startedAt)
        assertEquals("second", activity.statusText)
        assertTrue(activity.canInterrupt)
    }

    @Test
    fun markIdleRemovesEntry() {
        val (store, now) = storeWith(1_000L)
        store.markProcessing("t", now = now)
        assertTrue(store.isProcessing("t"))
        store.markIdle("t")
        assertFalse(store.isProcessing("t"))
    }

    @Test
    fun staleIdleAckIsDiscarded() {
        val (store, now) = storeWith(1_000L)
        store.markProcessing("t", now = now)
        // An ack that describes a start before the entry's actual start is stale.
        store.markIdle("t", ifStartedBefore = now)
        assertTrue(store.isProcessing("t"))
    }

    @Test
    fun syncAddsSnapshotsAndDropsOrphanedAfterGrace() {
        val (store, now) = storeWith(1_000L)
        store.markProcessing("local-old", now = now)
        store.markProcessing("local-fresh", now = now + 9_000L)
        store.sync(
            snapshots = mapOf("server" to NativeSessionActivity("s", startedAt = now)),
            now = now + 20_000L,
        )
        // local-fresh is 11s old -> outside the 10s grace; local-old also dropped.
        assertFalse(store.isProcessing("local-old"))
        assertFalse(store.isProcessing("local-fresh"))
        assertTrue(store.isProcessing("server"))
    }

    @Test
    fun syncKeepsFreshLocalEntriesMissingFromSnapshot() {
        val (store, now) = storeWith(1_000L)
        store.markProcessing("local", now = now + 2_000L)
        store.sync(
            snapshots = emptyMap(),
            now = now + 9_000L,
        )
        assertTrue(store.isProcessing("local"))
    }

    @Test
    fun syncIsNoOpWhenNothingChanges() {
        val (store, now) = storeWith(1_000L)
        store.markProcessing("t", now = now)
        val before = store.activities
        store.sync(mapOf("t" to NativeSessionActivity("", startedAt = now)), now = now + 500L)
        assertEquals(before, store.activities)
    }

    @Test
    fun activityForReturnsNullForUnknownThread() {
        val (store, now) = storeWith(1_000L)
        assertNull(store.activityFor("missing"))
    }
}
