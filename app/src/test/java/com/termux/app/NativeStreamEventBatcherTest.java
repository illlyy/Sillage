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
}
