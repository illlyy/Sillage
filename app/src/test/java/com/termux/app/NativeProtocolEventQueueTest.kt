package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProtocolEventQueueTest {
    @Test
    fun drainOrdersEventsAndSuppressesAlreadyEmittedDuplicates() {
        val queue = NativeOrderedProtocolEventQueue()
        queue.offer(NativeProtocolEvent.ReasoningDelta("t", delta = "second", sequence = 2))
        queue.offer(NativeProtocolEvent.ReasoningDelta("t", delta = "first", sequence = 1))

        val ordered = queue.drain().filterIsInstance<NativeProtocolEvent.ReasoningDelta>()
        assertEquals(listOf("first", "second"), ordered.map { it.delta })
        assertTrue(queue.offer(NativeProtocolEvent.ReasoningDelta("t", delta = "duplicate", sequence = 2)).isEmpty())
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun unsequencedCompatibilityEventsPassThroughImmediately() {
        val queue = NativeOrderedProtocolEventQueue()
        val event = NativeProtocolEvent.Boundary("t", kind = "legacy")
        assertEquals(listOf(event), queue.offer(event))
    }

    @Test
    fun unsequencedBarrierDrainsEarlierDeltasFirst() {
        val queue = NativeOrderedProtocolEventQueue()
        val delta = NativeProtocolEvent.ReasoningDelta("t", delta = "before", sequence = 4)
        val barrier = NativeProtocolEvent.Boundary("t", kind = "complete")
        queue.offer(delta)
        assertEquals(listOf(delta, barrier), queue.offer(barrier))
    }

    @Test
    fun localTurnResetDropsPendingButKeepsSequenceWatermark() {
        val queue = NativeOrderedProtocolEventQueue()
        val emitted = NativeProtocolEvent.ReasoningDelta("t", delta = "old", sequence = 7)
        queue.offer(emitted)
        assertEquals(listOf(emitted), queue.drain())

        queue.offer(NativeProtocolEvent.ReasoningDelta("t", delta = "queued", sequence = 8))
        queue.clearPendingPreservingWatermarks()

        assertTrue(queue.offer(NativeProtocolEvent.ReasoningDelta("t", delta = "late-old", sequence = 8)).isEmpty())
        assertTrue(queue.drain().isEmpty())
        val fresh = NativeProtocolEvent.ReasoningDelta("t", delta = "fresh", sequence = 9)
        queue.offer(fresh)
        assertEquals(listOf(fresh), queue.drain())
    }
}
