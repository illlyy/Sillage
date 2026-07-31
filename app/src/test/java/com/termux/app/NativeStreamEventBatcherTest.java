package com.termux.app;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeStreamEventBatcherTest {
    @Test
    public void adjacentEventsAreMergedWithoutReorderingChannels() {
        NativeStreamEventBatcher batcher = new NativeStreamEventBatcher();
        assertFalse(batcher.offer("onReasoningDelta", "a"));
        assertFalse(batcher.offer("onReasoningDelta", "b"));
        assertFalse(batcher.offer("onDelta", "c"));
        assertFalse(batcher.offer("onDelta", "d"));
        assertFalse(batcher.offer("onReasoningDelta", "e"));

        List<NativeStreamEventBatcher.Event> events = batcher.drain();
        assertEquals(3, events.size());
        assertEquals("onReasoningDelta", events.get(0).function);
        assertEquals("ab", events.get(0).value);
        assertEquals("onDelta", events.get(1).function);
        assertEquals("cd", events.get(1).value);
        assertEquals("onReasoningDelta", events.get(2).function);
        assertEquals("e", events.get(2).value);
        assertTrue(batcher.isEmpty());
    }

    @Test
    public void largePendingPayloadRequestsImmediateDrain() {
        NativeStreamEventBatcher batcher = new NativeStreamEventBatcher();
        assertTrue(batcher.offer("onCommandDelta", "x".repeat(4096)));
        assertEquals(1, batcher.drain().size());
        assertFalse(batcher.offer("onDelta", "next"));
    }

    @Test
    public void clearDropsPendingEvents() {
        NativeStreamEventBatcher batcher = new NativeStreamEventBatcher();
        batcher.offer("onDelta", "discarded");
        batcher.clear();
        assertTrue(batcher.drain().isEmpty());
    }

    @Test
    public void routeResetCanDiscardConversationEventsWithoutHidingGlobalErrors() {
        NativeRouteEventGate gate = new NativeRouteEventGate();
        gate.resetRoute(1, "thread-a", true);
        NativeRouteEventGate.RouteToken route = gate.captureRouteToken();
        NativeStreamEventBatcher batcher = new NativeStreamEventBatcher();
        batcher.offer("onProtocolDelta", "stale", route);
        batcher.offerSeparate("onNativeError", "backend disconnected", null);

        batcher.clearRoutedEvents();

        List<NativeStreamEventBatcher.Event> events = batcher.drain();
        assertEquals(1, events.size());
        assertEquals("onNativeError", events.get(0).function);
        assertEquals("backend disconnected", events.get(0).value);
    }

    @Test
    public void standaloneLifecyclePayloadsAreNeverConcatenated() {
        NativeStreamEventBatcher batcher = new NativeStreamEventBatcher();
        batcher.offerSeparate("onProtocolEvent", "{\"kind\":\"assistantCompleted\"}");
        batcher.offerSeparate("onProtocolEvent", "{\"kind\":\"turnCompleted\"}");

        List<NativeStreamEventBatcher.Event> events = batcher.drain();
        assertEquals(2, events.size());
        assertEquals("{\"kind\":\"assistantCompleted\"}", events.get(0).value);
        assertEquals("{\"kind\":\"turnCompleted\"}", events.get(1).value);
    }

    @Test
    public void deltaBarrierDeltaPreservesStrictFifoAndNeverMergesAcrossBarrier() {
        NativeStreamEventBatcher batcher = new NativeStreamEventBatcher();
        batcher.offer("onProtocolDelta", "before");
        batcher.offerSeparate("onProtocolEvent", "barrier");
        batcher.offer("onProtocolDelta", "after");

        List<NativeStreamEventBatcher.Event> events = batcher.drain();
        assertEquals(3, events.size());
        assertEquals("onProtocolDelta", events.get(0).function);
        assertEquals("before", events.get(0).value);
        assertEquals("onProtocolEvent", events.get(1).function);
        assertEquals("barrier", events.get(1).value);
        assertEquals("onProtocolDelta", events.get(2).function);
        assertEquals("after", events.get(2).value);
    }

    @Test
    public void adjacentDeltasNeverMergeAcrossRouteEpochs() {
        NativeRouteEventGate gate = new NativeRouteEventGate();
        gate.resetRoute(1, "thread-a", true);
        NativeRouteEventGate.RouteToken routeA = gate.captureRouteToken("thread-a");

        NativeStreamEventBatcher batcher = new NativeStreamEventBatcher();
        batcher.offer("onProtocolDelta", "old", routeA);

        gate.resetRoute(2, "thread-b", true);
        NativeRouteEventGate.RouteToken routeB = gate.captureRouteToken("thread-b");
        batcher.offer("onProtocolDelta", "new", routeB);

        List<NativeStreamEventBatcher.Event> events = batcher.drain();
        assertEquals(2, events.size());
        assertEquals("old", events.get(0).value);
        assertEquals("new", events.get(1).value);
        assertFalse(gate.isCurrent(events.get(0).routeToken));
        assertTrue(gate.isCurrent(events.get(1).routeToken));
    }
}
