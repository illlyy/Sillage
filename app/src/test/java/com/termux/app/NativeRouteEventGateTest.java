package com.termux.app;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeRouteEventGateTest {
    @Test
    public void currentRouteEventsWaitForHistoryThenReplayInOrder() {
        NativeRouteEventGate gate = new NativeRouteEventGate();
        gate.resetRoute(7, "thread-a", false);

        assertEquals(NativeRouteEventGate.Admission.DEFERRED,
            gate.offer(7, "thread-a", "onProtocolDelta", delta("item-1", "你")));
        assertEquals(NativeRouteEventGate.Admission.DEFERRED,
            gate.offer(7, "thread-a", "onProtocolDelta", delta("item-1", "好")));
        assertEquals(NativeRouteEventGate.Admission.DEFERRED,
            gate.offer(7, "thread-a", "onTurnComplete", ""));

        // Adjacent deltas for one item are compacted without crossing the lifecycle barrier.
        assertEquals(2, gate.deferredCount());
        List<NativeRouteEventGate.Event> replay = gate.markReadyAndReplay(
            7, "thread-a", Collections.emptySet());
        assertEquals(2, replay.size());
        assertEquals("onProtocolDelta", replay.get(0).function);
        assertTrue(replay.get(0).value.contains("你"));
        assertTrue(replay.get(0).value.contains("好"));
        assertEquals("onTurnComplete", replay.get(1).function);
        assertTrue(gate.isReady(7, "thread-a"));
    }

    @Test
    public void switchingFromAtoBRejectsEveryLateAEventAndReadyCallback() {
        NativeRouteEventGate gate = new NativeRouteEventGate();
        gate.resetRoute(10, "thread-a", false);
        assertEquals(NativeRouteEventGate.Admission.DEFERRED,
            gate.offer(10, "thread-a", "onProtocolDelta", delta("item-a", "old")));

        gate.resetRoute(11, "thread-b", false);
        assertEquals(0, gate.deferredCount());
        assertEquals(NativeRouteEventGate.Admission.REJECTED_STALE_ROUTE,
            gate.offer(10, "thread-a", "onProtocolDelta", delta("item-a", "late")));
        assertEquals(NativeRouteEventGate.Admission.REJECTED_STALE_ROUTE,
            gate.offer(11, "thread-a", "onProtocolDelta", delta("item-a", "wrong thread")));

        assertTrue(gate.markReadyAndReplay(10, "thread-a", Collections.emptySet()).isEmpty());
        assertFalse(gate.isReady(11, "thread-b"));
        assertEquals(NativeRouteEventGate.Admission.DEFERRED,
            gate.offer(11, "thread-b", "onProtocolDelta", delta("item-b", "current")));
        assertEquals(1, gate.markReadyAndReplay(
            11, "thread-b", Collections.emptySet()).size());
    }

    @Test
    public void historyCoveredItemIsRemovedFromReplayAndRejectedAfterReady() {
        NativeRouteEventGate gate = new NativeRouteEventGate();
        gate.resetRoute(3, "thread-a", false);
        gate.offer(3, "thread-a", "onProtocolDelta", delta("history-item", "duplicate"));
        gate.offer(3, "thread-a", "onProtocolDelta", delta("live-item", "visible"));
        gate.offer(3, "thread-a", "onTurnComplete", "");

        List<NativeRouteEventGate.Event> replay = gate.markReadyAndReplay(
            3, "thread-a", Collections.singleton("history-item"));
        assertEquals(2, replay.size());
        assertEquals("live-item", replay.get(0).protocolItemId);
        assertEquals("onTurnComplete", replay.get(1).function);

        // Close the snapshot/app-server race after replay, not only while draining the queue.
        assertEquals(NativeRouteEventGate.Admission.REJECTED_HISTORY_COVERED,
            gate.offer(3, "thread-a", "onProtocolDelta", delta("history-item", "late duplicate")));
        assertEquals(NativeRouteEventGate.Admission.DISPATCH_NOW,
            gate.offer(3, "thread-a", "onProtocolDelta", delta("new-item", "new")));
    }

    @Test
    public void nativeErrorBypassesLoadingAndHistoryFailureCanFailOpen() throws Exception {
        NativeRouteEventGate gate = new NativeRouteEventGate();
        gate.resetRoute(4, "thread-a", false);
        gate.offer(4, "thread-a", "onProtocolDelta", delta("item-1", "answer"));

        assertEquals(NativeRouteEventGate.Admission.DISPATCH_NOW,
            gate.offer(4, "thread-a", "onNativeError", "history read failed"));
        List<NativeRouteEventGate.Event> replay = gate.failOpen(4, "thread-a");
        assertEquals(1, replay.size());
        assertEquals("answer", new org.json.JSONObject(replay.get(0).value.trim()).optString("delta"));
        assertTrue(gate.isReady(4, "thread-a"));
    }

    @Test
    public void protocolItemIdentitySupportsJsonAndCoalescedJsonl() {
        String first = delta("item-9", "a");
        String second = delta("item-9", "b");
        assertEquals("item-9", NativeRouteEventGate.protocolItemIdFromPayload(first));
        assertEquals("item-9", NativeRouteEventGate.protocolItemIdFromPayload(first + second));
        assertEquals("", NativeRouteEventGate.protocolItemIdFromPayload(
            first + delta("item-10", "c")));
        assertEquals("nested", NativeRouteEventGate.protocolItemIdFromPayload(
            "{\"item\":{\"id\":\"nested\"}}"));
    }

    @Test
    public void routeTokenAtomicallyCapturesFallbackThreadAndReadyState() {
        NativeRouteEventGate gate = new NativeRouteEventGate();
        gate.resetRoute(21, "thread-a", false);

        NativeRouteEventGate.RouteToken fallback = gate.captureRouteToken("  ");
        assertEquals(21, fallback.getNavigationGeneration());
        assertEquals("thread-a", fallback.getSourceThreadId());
        assertFalse(fallback.isReady());
        assertTrue(gate.isCurrent(fallback));
        assertEquals(NativeRouteEventGate.Admission.DEFERRED,
            gate.offer(fallback, "onProtocolDelta", delta("item-21", "current")));

        NativeRouteEventGate.RouteToken explicitOther = gate.captureRouteToken("thread-b");
        assertEquals("thread-b", explicitOther.getSourceThreadId());
        assertFalse(gate.isCurrent(explicitOther));
        assertEquals(NativeRouteEventGate.Admission.REJECTED_STALE_ROUTE,
            gate.offer(explicitOther, "onTurnComplete", ""));

        gate.markReadyAndReplay(fallback, Collections.emptySet());
        NativeRouteEventGate.RouteToken ready = gate.captureRouteToken();
        assertTrue(ready.isReady());
        assertTrue(gate.isCurrent(ready));
    }

    @Test
    public void everyResetInvalidatesOldTokenEvenWhenExternalRouteIdentityIsReused() {
        NativeRouteEventGate gate = new NativeRouteEventGate();
        gate.resetRoute(30, "thread-a", false);
        NativeRouteEventGate.RouteToken old = gate.captureRouteToken();
        assertTrue(gate.isCurrent(old));

        // A route refresh may reuse both values. The private epoch still prevents an already
        // posted batch drain or lifecycle barrier from entering the refreshed conversation.
        gate.resetRoute(30, "thread-a", false);
        assertFalse(gate.isCurrent(old));
        assertEquals(NativeRouteEventGate.Admission.REJECTED_STALE_ROUTE,
            gate.offer(old, "onProtocolDelta", delta("old-item", "late")));
        assertTrue(gate.markReadyAndReplay(old, Collections.emptySet()).isEmpty());
        assertFalse(gate.isReady(30, "thread-a"));

        NativeRouteEventGate.RouteToken current = gate.captureRouteToken();
        assertTrue(gate.isCurrent(current));
        assertEquals(NativeRouteEventGate.Admission.DEFERRED,
            gate.offer(current, "onProtocolDelta", delta("new-item", "accepted")));
    }

    @Test
    public void manyLongAdjacentDeltasUseOneLinearAccumulatorAndPreserveContent() {
        NativeRouteEventGate gate = new NativeRouteEventGate();
        gate.resetRoute(40, "thread-a", false);
        NativeRouteEventGate.RouteToken token = gate.captureRouteToken();
        String chunk = String.join("", Collections.nCopies(2048, "x"));
        StringBuilder expected = new StringBuilder(512 * chunk.length());

        for (int index = 0; index < 512; index++) {
            expected.append(chunk);
            assertEquals(NativeRouteEventGate.Admission.DEFERRED,
                gate.offer(token, "onDelta", chunk, "long-item"));
        }

        assertEquals(1, gate.deferredCount());
        List<NativeRouteEventGate.Event> replay = gate.markReadyAndReplay(
            token, Collections.emptySet());
        assertEquals(1, replay.size());
        assertEquals(expected.length(), replay.get(0).value.length());
        assertEquals(expected.toString(), replay.get(0).value);
    }

    private static String delta(String itemId, String value) {
        return "{\"kind\":\"assistantDelta\",\"threadId\":\"thread-a\","
            + "\"itemId\":\"" + itemId + "\",\"delta\":\"" + value + "\"}\n";
    }
}
