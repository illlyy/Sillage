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
}
