package com.termux.app;

import android.app.Activity;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CodexAppServerBridgeDeliveryTest {
    @Test
    public void eventOfferedDuringDispatchGetsAFollowUpDrain() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> values = new ArrayList<>();
        AtomicReference<CodexAppServerBridge> bridgeRef = new AtomicReference<>();
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity,
            listener((function, value) -> {
                values.add(value);
                if ("first".equals(value)) invokeEmit(bridgeRef.get(), "onProtocolEvent", "second");
            }));
        bridgeRef.set(bridge);
        resetRoute(bridge, "thread-a", true);

        invokeEmit(bridge, "onProtocolEvent", "first");
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(Arrays.asList("first", "second"), values);
    }

    @Test
    public void routeResetDropsQueuedConversationEventsButPreservesGlobalError() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> events = new ArrayList<>();
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity,
            listener((function, value) -> events.add(function + ":" + value)));
        resetRoute(bridge, "thread-a", true);

        invokeEmit(bridge, "onProtocolEvent", "stale-route-event");
        invokeEmit(bridge, "onNativeError", "backend disconnected");
        resetRoute(bridge, "thread-b", true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(Arrays.asList("onNativeError:backend disconnected"), events);
    }

    @Test
    public void deltaBarrierDeltaIsDeliveredInStrictFifoOrder() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> events = new ArrayList<>();
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity,
            listener((function, value) -> events.add(function + ":" + value)));
        resetRoute(bridge, "thread-a", true);

        invokeEmit(bridge, "onProtocolDelta", "before");
        invokeEmit(bridge, "onProtocolEvent", "barrier");
        invokeEmit(bridge, "onProtocolDelta", "after");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS);

        assertEquals(Arrays.asList(
            "onProtocolDelta:before",
            "onProtocolEvent:barrier",
            "onProtocolDelta:after"
        ), events);
    }

    @Test
    public void sameThreadResumeResponseDoesNotDropQueuedLiveDelta() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> events = new ArrayList<>();
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity,
            listener((function, value) -> events.add(function + ":" + value)));
        setWriter(bridge, new BufferedWriter(new StringWriter()));
        NativeRouteEventGate.RouteToken routeToken = resetRoute(bridge, "thread-a", true);
        int requestId = sendNavigationRequest(
            bridge, "thread/resume", new JSONObject().put("threadId", "thread-a"),
            routeToken.getNavigationGeneration());

        invokeEmit(bridge, "onProtocolDelta", "live answer");
        invokeHandleMessage(bridge, new JSONObject()
            .put("id", requestId)
            .put("result", new JSONObject()
                .put("thread", new JSONObject().put("id", "thread-a"))));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS);

        assertEquals(Arrays.asList(
            "onProtocolDelta:live answer",
            "onReady:thread-a"
        ), events);
    }

    @Test
    public void navigationResponseCannotReclaimRouteSelectedWhileItWaitsForDeliveryLock()
            throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> events = new ArrayList<>();
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity,
            listener((function, value) -> events.add(function + ":" + value)));
        setWriter(bridge, new BufferedWriter(new StringWriter()));
        NativeRouteEventGate.RouteToken routeA = resetRoute(bridge, "thread-a", true);
        int requestId = sendNavigationRequest(
            bridge, "thread/resume", new JSONObject().put("threadId", "thread-a"),
            routeA.getNavigationGeneration());
        JSONObject response = new JSONObject()
            .put("id", requestId)
            .put("result", new JSONObject()
                .put("thread", new JSONObject().put("id", "thread-a")));
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Throwable> threadError = new AtomicReference<>();
        Object deliveryLock = nativeRouteDeliveryLock(bridge);
        Thread responseThread = new Thread(() -> {
            started.countDown();
            try {
                invokeHandleMessage(bridge, response);
            } catch (Throwable error) {
                threadError.set(error);
            }
        });

        synchronized (deliveryLock) {
            responseThread.start();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            responseThread.join(100L);
            assertTrue(responseThread.isAlive());
            incrementNavigationGeneration(bridge);
            resetRoute(bridge, "thread-b", true);
        }
        responseThread.join(1_000L);
        if (threadError.get() != null) throw new AssertionError(threadError.get());
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(new ArrayList<>(), events);
        assertEquals("thread-b", visibleThreadId(bridge));
    }

    @Test
    public void liveDeltaQueuedBeforeHistoryIsVisibleAsSoonAsHistoryIsInstalled() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> timeline = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        CodexAppServerBridge.EventListener listener = new CodexAppServerBridge.EventListener() {
            @Override
            public void onEvent(String function, String value) {
                timeline.add(function);
                payloads.add(value);
            }

            @Override
            public void onHistoryPrepared(String threadId, int generation,
                                          NativeHistorySnapshot snapshot) {
                timeline.add("history:" + threadId);
            }
        };
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity, listener);
        StringWriter appServerInput = new StringWriter();
        setWriter(bridge, new BufferedWriter(appServerInput));
        setAppServerInitialized(bridge, true);
        NativeRouteEventGate.RouteToken routeToken = resetRoute(bridge, "thread-a", false);
        AtomicBoolean historyResolved = new AtomicBoolean(false);
        Runnable historyTimeout = scheduleHistoryTimeout(
            bridge, "thread-a", routeToken, historyResolved, false);

        invokeHandleMessage(bridge, assistantDelta("thread-a", "turn-a", "item-a", "live answer"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS);
        assertEquals(new ArrayList<>(), timeline);

        NativeHistorySnapshot history = NativeHistoryParser.parse(new JSONArray());
        assertTrue(claimHistoryDelivery(
            bridge, "thread-a", routeToken, historyResolved, historyTimeout));
        listener.onHistoryPrepared("thread-a", routeToken.getNavigationGeneration(), history);
        markRouteReadyAndReplay(bridge, routeToken, history);
        continueConversationResume(bridge, "thread-a", routeToken, false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            CodexAppServerBridge.HISTORY_LOAD_TIMEOUT_MS + 32L, TimeUnit.MILLISECONDS);

        assertEquals(Arrays.asList(
            "history:thread-a",
            "onProtocolDelta"
        ), timeline);
        JSONObject delta = new JSONObject(payloads.get(0).trim());
        assertEquals("assistantDelta", delta.getString("kind"));
        assertEquals("thread-a", delta.getString("threadId"));
        assertEquals("turn-a", delta.getString("turnId"));
        assertEquals("item-a", delta.getString("itemId"));
        assertEquals("live answer", delta.getString("delta"));
        JSONObject resumeRequest = new JSONObject(appServerInput.toString().trim());
        assertEquals("thread/resume", resumeRequest.getString("method"));
    }

    @Test
    public void historyTimeoutFailsOpenAndReleasesQueuedLiveDelta() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> timeline = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        CodexAppServerBridge.EventListener listener = new CodexAppServerBridge.EventListener() {
            @Override
            public void onEvent(String function, String value) {
                timeline.add(function);
                payloads.add(value);
            }

            @Override
            public void onHistoryPrepared(String threadId, int generation,
                                          NativeHistorySnapshot snapshot) {
                timeline.add("history:" + threadId);
            }
        };
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity, listener);
        StringWriter appServerInput = new StringWriter();
        setWriter(bridge, new BufferedWriter(appServerInput));
        setAppServerInitialized(bridge, true);
        NativeRouteEventGate.RouteToken routeToken = resetRoute(bridge, "thread-a", false);
        AtomicBoolean historyResolved = new AtomicBoolean(false);
        Runnable historyTimeout = scheduleHistoryTimeout(
            bridge, "thread-a", routeToken, historyResolved, false);

        invokeHandleMessage(bridge, assistantDelta("thread-a", "turn-a", "item-a", "live answer"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS);
        assertEquals(new ArrayList<>(), timeline);

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            CodexAppServerBridge.HISTORY_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS);

        assertEquals(Arrays.asList(
            "history:thread-a",
            "onProtocolDelta",
            "onHistoryWarning"
        ), timeline);
        JSONObject delta = new JSONObject(payloads.get(0).trim());
        assertEquals("assistantDelta", delta.getString("kind"));
        assertEquals("live answer", delta.getString("delta"));
        assertEquals(
            "Conversation history loading timed out; live output has been restored",
            payloads.get(1));
        JSONObject resumeRequest = new JSONObject(appServerInput.toString().trim());
        assertEquals("thread/resume", resumeRequest.getString("method"));
        assertEquals("thread-a", resumeRequest.getJSONObject("params").getString("threadId"));
        String firstResume = appServerInput.toString();
        assertFalse(claimHistoryDelivery(
            bridge, "thread-a", routeToken, historyResolved, historyTimeout));
        assertEquals(firstResume, appServerInput.toString());
    }

    @Test
    public void historyTimeoutForStaleRouteCannotAffectTheNewConversation() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> events = new ArrayList<>();
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity,
            listener((function, value) -> events.add(function + ":" + value)));
        StringWriter appServerInput = new StringWriter();
        setWriter(bridge, new BufferedWriter(appServerInput));
        setAppServerInitialized(bridge, true);
        NativeRouteEventGate.RouteToken routeA = resetRoute(bridge, "thread-a", false);
        scheduleHistoryTimeout(bridge, "thread-a", routeA, new AtomicBoolean(false), false);

        resetRoute(bridge, "thread-b", true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            CodexAppServerBridge.HISTORY_LOAD_TIMEOUT_MS + 32L, TimeUnit.MILLISECONDS);

        assertEquals(new ArrayList<>(), events);
        assertEquals("", appServerInput.toString());
    }

    @Test
    public void retainedRuntimeTimeoutMarksRouteReadyWithoutSendingResume() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> timeline = new ArrayList<>();
        CodexAppServerBridge.EventListener listener = new CodexAppServerBridge.EventListener() {
            @Override
            public void onEvent(String function, String value) {
                timeline.add(function);
            }

            @Override
            public void onHistoryPrepared(String threadId, int generation,
                                          NativeHistorySnapshot snapshot) {
                timeline.add("history:" + threadId);
            }
        };
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity, listener);
        StringWriter appServerInput = new StringWriter();
        setWriter(bridge, new BufferedWriter(appServerInput));
        setAppServerInitialized(bridge, true);
        NativeRouteEventGate.RouteToken routeToken = resetRoute(bridge, "thread-a", false);
        scheduleHistoryTimeout(bridge, "thread-a", routeToken, new AtomicBoolean(false), true);

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            CodexAppServerBridge.HISTORY_LOAD_TIMEOUT_MS + 32L, TimeUnit.MILLISECONDS);

        assertEquals(Arrays.asList(
            "history:thread-a",
            "onHistoryWarning",
            "onReady"
        ), timeline);
        assertEquals("", appServerInput.toString());
    }

    @Test
    public void staleRequestResponseFailureNeverFallsBackToTheNewRoute() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> events = new ArrayList<>();
        AtomicReference<String> rawRequest = new AtomicReference<>();
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity,
            listener((function, value) -> {
                events.add(function + ":" + value);
                if ("onUserInputRequest".equals(function)) rawRequest.set(value);
            }));
        resetRoute(bridge, "thread-a", true);
        invokeHandleMessage(bridge, userInputRequest(8, "thread-a", "turn-a"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        events.clear();

        resetRoute(bridge, "thread-b", true);
        bridge.respondUserInput(rawRequest.get(), "{}");
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(new ArrayList<>(), events);
    }

    @Test
    public void sequentiallyReusedRequestIdCannotResolveIntoTheNewRoute() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> events = new ArrayList<>();
        AtomicReference<String> rawRequest = new AtomicReference<>();
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity,
            listener((function, value) -> {
                events.add(function + ":" + value);
                if ("onUserInputRequest".equals(function)) rawRequest.set(value);
            }));
        setWriter(bridge, new BufferedWriter(new StringWriter()));
        resetRoute(bridge, "thread-a", true);
        invokeHandleMessage(bridge, userInputRequest(7, "thread-a", "turn-a"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        bridge.respondUserInput(rawRequest.get(), "{}");
        events.clear();

        resetRoute(bridge, "thread-b", true);
        rememberActiveTurn(bridge, "thread-b", "turn-b");
        invokeHandleMessage(bridge, userInputRequest(7, "thread-b", "turn-b"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, events.size());
        JSONObject visibleRequest = new JSONObject(rawRequest.get());
        assertEquals("thread-b", visibleRequest.getJSONObject("params").getString("threadId"));
        assertEquals("turn-b", visibleRequest.getJSONObject("params").getString("turnId"));
        events.clear();

        invokeHandleMessage(bridge, new JSONObject()
            .put("method", "serverRequest/resolved")
            .put("params", new JSONObject().put("requestId", 7)));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(new ArrayList<>(), events);
    }

    @Test
    public void ignoredBackgroundRequestIdStillQuarantinesLaterReuse() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> events = new ArrayList<>();
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity,
            listener((function, value) -> events.add(function + ":" + value)));
        resetRoute(bridge, "thread-b", true);
        rememberActiveTurn(bridge, "thread-b", "turn-b");

        invokeHandleMessage(bridge, userInputRequest(9, "thread-a", "turn-a"));
        invokeHandleMessage(bridge, userInputRequest(9, "thread-b", "turn-b"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        events.clear();

        invokeHandleMessage(bridge, new JSONObject()
            .put("method", "serverRequest/resolved")
            .put("params", new JSONObject().put("requestId", 9)));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(new ArrayList<>(), events);
    }

    @Test
    public void staleServerGenerationCannotResolveRequestFromReplacementProcess() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> events = new ArrayList<>();
        AtomicReference<String> rawRequest = new AtomicReference<>();
        CodexAppServerBridge bridge = new CodexAppServerBridge(activity,
            listener((function, value) -> {
                events.add(function + ":" + value);
                if ("onUserInputRequest".equals(function)) rawRequest.set(value);
            }));
        Process replacementProcess = fakeProcess();
        Process staleProcess = fakeProcess();
        setServerProcess(bridge, replacementProcess, 2);
        resetRoute(bridge, "thread-b", true);
        rememberActiveTurn(bridge, "thread-b", "turn-b");

        invokeHandleMessageFromProcess(
            bridge, replacementProcess, 2, userInputRequest(11, "thread-b", "turn-b"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, events.size());
        JSONObject visibleRequest = new JSONObject(rawRequest.get());
        assertEquals("thread-b", visibleRequest.getJSONObject("params").getString("threadId"));
        events.clear();

        invokeHandleMessageFromProcess(bridge, staleProcess, 1, new JSONObject()
            .put("method", "serverRequest/resolved")
            .put("params", new JSONObject().put("requestId", 11)));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(new ArrayList<>(), events);
    }

    private static CodexAppServerBridge.EventListener listener(EventConsumer consumer) {
        return new CodexAppServerBridge.EventListener() {
            @Override
            public void onEvent(String function, String value) {
                consumer.accept(function, value);
            }

            @Override
            public void onHistoryPrepared(String threadId, int generation,
                                          NativeHistorySnapshot snapshot) {
            }
        };
    }

    private static NativeRouteEventGate.RouteToken resetRoute(
            CodexAppServerBridge bridge, String thread, boolean ready)
            throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod(
            "resetVisibleRoute", String.class, boolean.class);
        method.setAccessible(true);
        return (NativeRouteEventGate.RouteToken) method.invoke(bridge, thread, ready);
    }

    private static void markRouteReadyAndReplay(
            CodexAppServerBridge bridge, NativeRouteEventGate.RouteToken routeToken,
            NativeHistorySnapshot history) throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod(
            "markVisibleRouteReadyAndReplay", NativeRouteEventGate.RouteToken.class,
            NativeHistorySnapshot.class);
        method.setAccessible(true);
        method.invoke(bridge, routeToken, history);
    }

    private static void invokeEmit(CodexAppServerBridge bridge, String function, String value) {
        try {
            Method method = CodexAppServerBridge.class.getDeclaredMethod(
                "emit", String.class, String.class);
            method.setAccessible(true);
            method.invoke(bridge, function, value);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static JSONObject userInputRequest(int id, String thread, String turn) throws Exception {
        return new JSONObject()
            .put("id", id)
            .put("method", "item/tool/requestUserInput")
            .put("params", new JSONObject()
                .put("threadId", thread)
                .put("turnId", turn)
                .put("questions", new JSONArray()));
    }

    private static JSONObject assistantDelta(String thread, String turn, String item, String delta)
            throws Exception {
        return new JSONObject()
            .put("method", "item/agentMessage/delta")
            .put("params", new JSONObject()
                .put("threadId", thread)
                .put("turnId", turn)
                .put("itemId", item)
                .put("delta", delta));
    }

    private static void invokeHandleMessage(CodexAppServerBridge bridge, JSONObject message)
            throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod("handleMessage", JSONObject.class);
        method.setAccessible(true);
        method.invoke(bridge, message);
    }

    private static int sendNavigationRequest(
            CodexAppServerBridge bridge, String methodName, JSONObject params, int generation)
            throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod(
            "sendNavigationRequest", String.class, JSONObject.class, int.class);
        method.setAccessible(true);
        return (Integer) method.invoke(bridge, methodName, params, generation);
    }

    private static void invokeHandleMessageFromProcess(
            CodexAppServerBridge bridge, Process process, int generation, JSONObject message)
            throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod(
            "handleMessageFromProcess", Process.class, int.class, JSONObject.class);
        method.setAccessible(true);
        method.invoke(bridge, process, generation, message);
    }

    private static void rememberActiveTurn(CodexAppServerBridge bridge, String thread, String turn)
            throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod(
            "rememberActiveTurn", String.class, String.class);
        method.setAccessible(true);
        method.invoke(bridge, thread, turn);
    }

    private static Runnable scheduleHistoryTimeout(
            CodexAppServerBridge bridge, String thread,
            NativeRouteEventGate.RouteToken routeToken, AtomicBoolean historyResolved,
            boolean retainedRuntime) throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod(
            "scheduleHistoryLoadTimeout", String.class, int.class,
            NativeRouteEventGate.RouteToken.class, AtomicBoolean.class,
            NativeHistorySnapshot.class, boolean.class);
        method.setAccessible(true);
        return (Runnable) method.invoke(
            bridge, thread, routeToken.getNavigationGeneration(), routeToken,
            historyResolved, NativeHistoryParser.parse(new JSONArray()), retainedRuntime);
    }

    private static boolean claimHistoryDelivery(
            CodexAppServerBridge bridge, String thread,
            NativeRouteEventGate.RouteToken routeToken, AtomicBoolean historyResolved,
            Runnable historyTimeout) throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod(
            "claimHistoryDelivery", String.class, int.class,
            NativeRouteEventGate.RouteToken.class, AtomicBoolean.class, Runnable.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(
            bridge, thread, routeToken.getNavigationGeneration(), routeToken,
            historyResolved, historyTimeout);
    }

    private static void continueConversationResume(
            CodexAppServerBridge bridge, String thread,
            NativeRouteEventGate.RouteToken routeToken, boolean retainedRuntime) throws Exception {
        Method method = CodexAppServerBridge.class.getDeclaredMethod(
            "continueConversationResume", String.class, int.class,
            NativeRouteEventGate.RouteToken.class, boolean.class);
        method.setAccessible(true);
        method.invoke(bridge, thread, routeToken.getNavigationGeneration(), routeToken,
            retainedRuntime);
    }

    private static void setWriter(CodexAppServerBridge bridge, BufferedWriter writer) throws Exception {
        Field field = CodexAppServerBridge.class.getDeclaredField("writer");
        field.setAccessible(true);
        field.set(bridge, writer);
    }

    private static void setAppServerInitialized(CodexAppServerBridge bridge, boolean initialized)
            throws Exception {
        Field field = CodexAppServerBridge.class.getDeclaredField("appServerInitialized");
        field.setAccessible(true);
        field.setBoolean(bridge, initialized);
    }

    private static void setServerProcess(
            CodexAppServerBridge bridge, Process process, int generation) throws Exception {
        Field processField = CodexAppServerBridge.class.getDeclaredField("process");
        processField.setAccessible(true);
        processField.set(bridge, process);
        Field generationField = CodexAppServerBridge.class.getDeclaredField("serverGeneration");
        generationField.setAccessible(true);
        ((AtomicInteger) generationField.get(bridge)).set(generation);
    }

    private static Object nativeRouteDeliveryLock(CodexAppServerBridge bridge) throws Exception {
        Field field = CodexAppServerBridge.class.getDeclaredField("nativeRouteDeliveryLock");
        field.setAccessible(true);
        return field.get(bridge);
    }

    private static void incrementNavigationGeneration(CodexAppServerBridge bridge) throws Exception {
        Field field = CodexAppServerBridge.class.getDeclaredField("navigationGeneration");
        field.setAccessible(true);
        ((AtomicInteger) field.get(bridge)).incrementAndGet();
    }

    private static String visibleThreadId(CodexAppServerBridge bridge) throws Exception {
        Field field = CodexAppServerBridge.class.getDeclaredField("visibleThreadId");
        field.setAccessible(true);
        return (String) field.get(bridge);
    }

    private static Process fakeProcess() {
        return new Process() {
            @Override
            public java.io.OutputStream getOutputStream() {
                return new java.io.ByteArrayOutputStream();
            }

            @Override
            public java.io.InputStream getInputStream() {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }

            @Override
            public java.io.InputStream getErrorStream() {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }

            @Override
            public int waitFor() {
                return 0;
            }

            @Override
            public int exitValue() {
                return 0;
            }

            @Override
            public void destroy() {
            }
        };
    }

    private interface EventConsumer {
        void accept(String function, String value);
    }
}
