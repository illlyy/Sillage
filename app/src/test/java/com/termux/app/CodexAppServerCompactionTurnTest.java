package com.termux.app;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CodexAppServerCompactionTurnTest {
    @Test
    public void primaryCompletionDoesNotBindButDedicatedTurnDoes() {
        CodexAppServerBridge.CompactionTurnTracker tracker =
            new CodexAppServerBridge.CompactionTurnTracker();
        tracker.requestStarted("thread", "primary", 1_000L);

        assertFalse(tracker.observeCandidate(
            "thread", "primary", 1_001L, false, "primary"));
        assertTrue(tracker.observeCandidate(
            "thread", "compact", 1_002L, false, "primary"));
        assertTrue(tracker.isAuxiliaryTurn("thread", "compact"));
        assertEquals("primary", tracker.primaryTurnFor("thread", "compact"));

        assertTrue(tracker.complete("thread", "compact"));
        assertTrue(tracker.isAuxiliaryTurn("thread", "compact"));
        assertFalse(tracker.hasPendingOrActiveCompaction("thread", 1_003L));
    }

    @Test
    public void emptyPrimaryWeakStartWaitsForCompactRpcAcknowledgement() {
        CodexAppServerBridge.CompactionTurnTracker tracker =
            new CodexAppServerBridge.CompactionTurnTracker();
        tracker.requestStarted("thread", "", 1_000L);

        assertFalse(tracker.observeCandidate(
            "thread", "ordinary", 1_001L, false, ""));
        assertTrue(tracker.shouldQuarantineWeakStart("thread", "ordinary", 1_001L));

        tracker.requestSucceeded("thread", 1_002L);
        assertTrue(tracker.observeCandidate(
            "thread", "compact", 1_003L, false, ""));
        assertTrue(tracker.isAuxiliaryTurn("thread", "compact"));
    }

    @Test
    public void strongContextSignalBindsAutomaticDedicatedTurnEvenAfterRequestWindow() {
        CodexAppServerBridge.CompactionTurnTracker tracker =
            new CodexAppServerBridge.CompactionTurnTracker();
        tracker.requestStarted("thread", "primary", 1_000L);
        long expired = 1_000L + CodexAppServerBridge.COMPACTION_TURN_BIND_WINDOW_MS + 1L;

        assertFalse(tracker.observeCandidate(
            "thread", "ordinary", expired, false, "primary"));
        assertFalse(tracker.observeCandidate(
            "thread", "primary", expired, true, "primary"));
        assertTrue(tracker.observeCandidate(
            "thread", "automatic-compact", expired, true, "primary"));
        assertEquals("primary", tracker.primaryTurnFor("thread", "automatic-compact"));
    }

    @Test
    public void completedPrimaryClassificationOutlivesTaskCompletionSettleWindow() {
        CodexAppServerBridge.PrimaryCompletionTracker tracker =
            new CodexAppServerBridge.PrimaryCompletionTracker();
        tracker.record("thread", "primary", 1_000L);

        long afterTaskSettle = 1_000L + CodexAppServerBridge.TASK_COMPLETION_SETTLE_MS + 1L;
        assertTrue(tracker.shouldQuarantineWeakStart("thread", "compact", afterTaskSettle));
        assertEquals("primary", tracker.primaryTurnHint("thread", "compact", afterTaskSettle));
        assertFalse(tracker.shouldQuarantineWeakStart(
            "thread", "compact",
            1_000L + CodexAppServerBridge.PRIMARY_COMPLETION_TOMBSTONE_MS + 1L));
    }

    @Test
    public void terminalCompactionItemWaitsForGraceThenConvergesWithoutTurnCompletion() {
        CodexAppServerBridge.CompactionTurnTracker tracker =
            new CodexAppServerBridge.CompactionTurnTracker();
        tracker.observeCandidate("thread", "compact", 1_000L, true, "primary");

        assertTrue(tracker.observeLifecycleTerminal("thread", "compact", 2_000L));
        assertTrue(tracker.hasPendingOrActiveCompaction(
            "thread", 2_000L + CodexAppServerBridge.COMPACTION_TERMINAL_SETTLE_MS - 1L));
        assertFalse(tracker.hasPendingOrActiveCompaction(
            "thread", 2_000L + CodexAppServerBridge.COMPACTION_TERMINAL_SETTLE_MS));
        assertTrue(tracker.isAuxiliaryTurn("thread", "compact"));
    }

    @Test
    public void realAuxiliaryTurnCompletionSettlesBeforeTerminalItemGraceExpires() {
        CodexAppServerBridge.CompactionTurnTracker tracker =
            new CodexAppServerBridge.CompactionTurnTracker();
        tracker.observeCandidate("thread", "compact", 1_000L, true, "primary");
        tracker.observeLifecycleTerminal("thread", "compact", 2_000L);

        assertTrue(tracker.complete("thread", "compact"));
        assertFalse(tracker.hasPendingOrActiveCompaction("thread", 2_001L));
    }

    @Test
    public void onlyLiveTurnObservationsCreateTurnStartedLifecycle() throws Exception {
        JSONObject inProgress = new JSONObject().put("id", "turn").put("status", "inProgress");
        JSONObject completed = new JSONObject().put("id", "turn").put("status", "completed");

        assertTrue(CodexAppServerBridgeProtocol.isTurnStartObservation(
            new JSONObject().put("method", "turn/started"), "turn/started", inProgress));
        assertTrue(CodexAppServerBridgeProtocol.isTurnStartObservation(
            new JSONObject().put("id", 1).put("result", new JSONObject().put("turn", inProgress)),
            "", inProgress));
        assertFalse(CodexAppServerBridgeProtocol.isTurnStartObservation(
            new JSONObject().put("method", "turn/completed"), "turn/completed", completed));
        assertFalse(CodexAppServerBridgeProtocol.isTurnStartObservation(
            new JSONObject().put("id", 1).put("result", new JSONObject().put("turn", completed)),
            "", completed));
    }

    @Test
    public void completionCallbackCarriesStableIdentityAndContinuationState() throws Exception {
        JSONObject params = CodexAppServerBridgeProtocol.turnLifecycleParams(
            "thread", "turn", new JSONObject().put("status", "completed"));
        JSONObject payload = new JSONObject(
            CodexAppServerBridgeProtocol.turnLifecycleCallbackPayload(params, true));

        assertEquals("thread", payload.getString("threadId"));
        assertEquals("turn", payload.getString("turnId"));
        assertEquals("turn", payload.getJSONObject("turn").getString("id"));
        assertEquals("thread", payload.getJSONObject("turn").getString("threadId"));
        assertTrue(payload.getBoolean("hasPendingContinuation"));
    }

    @Test
    public void auxiliaryCompletionHelperRejectsPrimaryTurn() throws Exception {
        CodexAppServerBridge.CompactionTurnTracker tracker =
            new CodexAppServerBridge.CompactionTurnTracker();
        tracker.requestStarted("thread", "primary", 1_000L);
        tracker.observeCandidate("thread", "compact", 1_001L, true, "primary");

        JSONObject auxiliary = completionParams("thread", "compact");
        JSONObject primary = completionParams("thread", "primary");
        assertTrue(CodexAppServerBridgeProtocol.isAuxiliaryCompactionTurnCompletion(tracker, auxiliary));
        assertFalse(CodexAppServerBridgeProtocol.isAuxiliaryCompactionTurnCompletion(tracker, primary));
    }

    @Test
    public void automaticDedicatedCompactionCannotReplaceOrCompletePrimaryTurn() throws Exception {
        List<String> functions = new ArrayList<>();
        CodexAppServerBridge bridge = bridge(functions);
        resetRoute(bridge, "thread", true);

        handle(bridge, turnStarted("thread", "primary"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        functions.clear();

        handle(bridge, turnStarted("thread", "compact"));
        assertEquals("primary", activeTurnId(bridge));
        handle(bridge, new JSONObject()
            .put("method", "item/started")
            .put("params", new JSONObject()
                .put("threadId", "thread")
                .put("turnId", "compact")
                .put("item", new JSONObject()
                    .put("id", "compact-item")
                    .put("type", "contextCompaction"))));
        handle(bridge, new JSONObject()
            .put("method", "turn/completed")
            .put("params", completionParams("thread", "compact")));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("primary", activeTurnId(bridge));
        assertFalse(functions.contains("onTurnComplete"));
        assertFalse(functions.contains("onProtocolEvent:turnCompleted"));
    }

    @Test
    public void postCompletionAutomaticCompactionTurnIsReclassifiedBeforeItCanCompleteTask()
            throws Exception {
        List<String> functions = new ArrayList<>();
        CodexAppServerBridge bridge = bridge(functions);
        resetRoute(bridge, "thread", true);
        handle(bridge, turnStarted("thread", "primary"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        functions.clear();

        handle(bridge, new JSONObject()
            .put("method", "turn/completed")
            .put("params", completionParams("thread", "primary")));
        handle(bridge, turnStarted("thread", "compact"));
        handle(bridge, new JSONObject()
            .put("method", "item/started")
            .put("params", new JSONObject()
                .put("threadId", "thread")
                .put("turnId", "compact")
                .put("item", new JSONObject()
                    .put("id", "compact-item")
                    .put("type", "contextCompaction"))));
        handle(bridge, new JSONObject()
            .put("method", "turn/completed")
            .put("params", completionParams("thread", "compact")));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertNull(activeTurnId(bridge));
        assertEquals(1, Collections.frequency(functions, "onTurnComplete"));
        assertEquals(1, Collections.frequency(functions, "onProtocolEvent:turnCompleted"));
        assertFalse(functions.contains("onProtocolEvent:turnStarted"));
    }

    @Test
    public void lateAutomaticCompactionStartRemainsAuxiliaryAfterTaskSettleStateIsGone()
            throws Exception {
        List<String> functions = new ArrayList<>();
        CodexAppServerBridge bridge = bridge(functions);
        resetRoute(bridge, "thread", true);
        handle(bridge, turnStarted("thread", "primary"));
        handle(bridge, new JSONObject()
            .put("method", "turn/completed")
            .put("params", completionParams("thread", "primary")));
        clearDeferredTaskCompletions(bridge);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        functions.clear();

        handle(bridge, turnStarted("thread", "compact"));
        handle(bridge, contextCompactionItem("item/started", "thread", "compact", "running"));
        handle(bridge, new JSONObject()
            .put("method", "turn/completed")
            .put("params", completionParams("thread", "compact")));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertNull(activeTurnId(bridge));
        assertFalse(functions.contains("onProtocolEvent:turnStarted"));
        assertFalse(functions.contains("onTurnComplete"));
        assertFalse(functions.contains("onProtocolEvent:turnCompleted"));
    }

    @Test
    public void terminalCompactionItemReleasesDeferredTaskAfterBoundedGrace() throws Exception {
        List<String> functions = new ArrayList<>();
        CodexAppServerBridge bridge = bridge(functions);
        Context context = appContext(bridge);
        context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE).edit().clear().commit();
        CodexTaskStore.markRunning(context, "thread", "Compacting task");
        resetRoute(bridge, "thread", true);
        handle(bridge, turnStarted("thread", "primary"));
        handle(bridge, contextCompactionItem(
            "item/started", "thread", "compact", "running"));
        handle(bridge, new JSONObject()
            .put("method", "turn/completed")
            .put("params", completionParams("thread", "primary")));

        handle(bridge, contextCompactionItem(
            "item/completed", "thread", "compact", "completed"));

        assertTrue(tracker(bridge).hasPendingOrActiveCompaction(
            "thread", System.currentTimeMillis()));
        assertEquals(CodexTaskStore.RUNNING, CodexTaskStore.current(context).get(0).state);
        assertFalse(tracker(bridge).hasPendingOrActiveCompaction("thread", Long.MAX_VALUE));
        makeDeferredTaskCompletionReady(bridge, "thread");
        finalizeDeferredTaskCompletion(bridge, "thread");
        assertEquals(CodexTaskStore.COMPLETED, CodexTaskStore.current(context).get(0).state);
    }

    @Test
    public void failedInterruptedAndCancelledStatusesAreAllFailures() throws Exception {
        assertTrue(CodexAppServerBridgeProtocol.turnFailed(completionParamsWithStatus("failed")));
        assertTrue(CodexAppServerBridgeProtocol.turnFailed(completionParamsWithStatus("interrupted")));
        assertTrue(CodexAppServerBridgeProtocol.turnFailed(completionParamsWithStatus("cancelled")));
        assertTrue(CodexAppServerBridgeProtocol.turnFailed(completionParamsWithStatus("canceled")));
        assertFalse(CodexAppServerBridgeProtocol.turnFailed(completionParamsWithStatus("completed")));
        JSONObject errored = completionParamsWithStatus("completed");
        errored.getJSONObject("turn").put("error", new JSONObject().put("message", "boom"));
        assertTrue(CodexAppServerBridgeProtocol.turnFailed(errored));
    }

    @Test
    public void finalAnswerAndIdleRemainNonTerminalUntilRealTurnCompleted() throws Exception {
        List<String> functions = new ArrayList<>();
        CodexAppServerBridge bridge = bridge(functions);
        Context context = appContext(bridge);
        context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE).edit()
            .clear()
            .putBoolean("completion_notification", true)
            .commit();
        CodexTaskStore.markRunning(context, "thread", "Live task");
        resetRoute(bridge, "thread", true);
        handle(bridge, turnStarted("thread", "primary"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        functions.clear();

        handle(bridge, new JSONObject()
            .put("method", "item/completed")
            .put("params", new JSONObject()
                .put("threadId", "thread")
                .put("turnId", "primary")
                .put("item", new JSONObject()
                    .put("id", "answer")
                    .put("type", "agentMessage")
                    .put("phase", "final_answer")
                    .put("text", "done"))));
        handle(bridge, new JSONObject()
            .put("method", "thread/status/changed")
            .put("params", new JSONObject()
                .put("threadId", "thread")
                .put("status", new JSONObject().put("type", "idle"))));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("primary", activeTurnId(bridge));
        assertFalse(functions.contains("onTurnComplete"));
        assertFalse(functions.contains("onProtocolEvent:turnCompleted"));
        assertEquals(CodexTaskStore.RUNNING, CodexTaskStore.current(context).get(0).state);
        assertFalse(context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE).contains(
            "native_task_notification_fingerprint_v1_thread"));
    }

    @Test
    public void unacknowledgedCompactStartWithNoPrimaryIsQuarantined() throws Exception {
        List<String> functions = new ArrayList<>();
        CodexAppServerBridge bridge = bridge(functions);
        resetRoute(bridge, "thread", true);
        tracker(bridge).requestStarted("thread", "", System.currentTimeMillis());

        handle(bridge, turnStarted("thread", "candidate"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertNull(activeTurnId(bridge));
        assertFalse(functions.contains("onProtocolEvent"));
    }

    private static CodexAppServerBridge bridge(List<String> functions) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        return new CodexAppServerBridge(activity, new CodexAppServerBridge.EventListener() {
            @Override public void onEvent(String function, String value) {
                functions.add(function);
                if ("onProtocolEvent".equals(function)) {
                    try {
                        functions.add(function + ":" + new JSONObject(value).optString("kind"));
                    } catch (Exception ignored) {}
                }
            }

            @Override public void onHistoryPrepared(
                    String threadId, int generation, NativeHistorySnapshot snapshot) {}
        });
    }

    private static JSONObject turnStarted(String thread, String turn) throws Exception {
        return new JSONObject()
            .put("method", "turn/started")
            .put("params", new JSONObject()
                .put("threadId", thread)
                .put("turn", new JSONObject()
                    .put("id", turn)
                    .put("status", "inProgress")));
    }

    private static JSONObject completionParams(String thread, String turn) throws Exception {
        return new JSONObject()
            .put("threadId", thread)
            .put("turn", new JSONObject()
                .put("id", turn)
                .put("status", "completed")
                .put("error", JSONObject.NULL));
    }

    private static JSONObject completionParamsWithStatus(String status) throws Exception {
        JSONObject params = completionParams("thread", "turn");
        params.getJSONObject("turn").put("status", status);
        return params;
    }

    private static JSONObject contextCompactionItem(
            String method, String thread, String turn, String status) throws Exception {
        return new JSONObject()
            .put("method", method)
            .put("params", new JSONObject()
                .put("threadId", thread)
                .put("turnId", turn)
                .put("item", new JSONObject()
                    .put("id", "compact-item")
                    .put("type", "contextCompaction")
                    .put("status", status)));
    }

    private static void handle(CodexAppServerBridge bridge, JSONObject message) throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod("handleMessage", JSONObject.class);
        method.setAccessible(true);
        try {
            method.invoke(bridge, message);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw error;
        }
    }

    private static void resetRoute(CodexAppServerBridge bridge, String thread, boolean ready)
            throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod(
            "resetVisibleRoute", String.class, boolean.class);
        method.setAccessible(true);
        method.invoke(bridge, thread, ready);
    }

    private static String activeTurnId(CodexAppServerBridge bridge) throws Exception {
        Field field = CodexAppServerBridge.class.getDeclaredField("activeTurnId");
        field.setAccessible(true);
        return (String) field.get(bridge);
    }

    private static Context appContext(CodexAppServerBridge bridge) throws Exception {
        Field field = CodexAppServerBridge.class.getDeclaredField("appContext");
        field.setAccessible(true);
        return (Context) field.get(bridge);
    }

    private static void clearDeferredTaskCompletions(CodexAppServerBridge bridge) throws Exception {
        Field field = CodexAppServerBridge.class.getDeclaredField("deferredTaskCompletions");
        field.setAccessible(true);
        ((java.util.Map<?, ?>) field.get(bridge)).clear();
    }

    private static void makeDeferredTaskCompletionReady(
            CodexAppServerBridge bridge, String thread) throws Exception {
        Field mapField = CodexAppServerBridge.class.getDeclaredField("deferredTaskCompletions");
        mapField.setAccessible(true);
        Object completion = ((java.util.Map<?, ?>) mapField.get(bridge)).get(thread);
        Field deadline = completion.getClass().getDeclaredField("notBeforeMs");
        deadline.setAccessible(true);
        deadline.setLong(completion, System.currentTimeMillis() - 1L);
    }

    private static void finalizeDeferredTaskCompletion(
            CodexAppServerBridge bridge, String thread) throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod(
            "maybeFinalizeDeferredTaskCompletion", String.class);
        method.setAccessible(true);
        method.invoke(bridge, thread);
    }

    private static CodexAppServerBridge.CompactionTurnTracker tracker(
            CodexAppServerBridge bridge) throws Exception {
        Field field = CodexAppServerBridge.class.getDeclaredField("compactionTurnTracker");
        field.setAccessible(true);
        return (CodexAppServerBridge.CompactionTurnTracker) field.get(bridge);
    }
}
