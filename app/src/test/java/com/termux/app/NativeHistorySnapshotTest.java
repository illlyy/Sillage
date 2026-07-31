package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NativeHistorySnapshotTest {
    @Before
    public void setUp() {
        NativeHistorySnapshotCache.clear();
        NativeCommandOutputStore.clear();
    }

    @After
    public void tearDown() {
        NativeHistorySnapshotCache.clear();
        NativeCommandOutputStore.clear();
    }

    @Test
    public void parsesLargeHistoryIntoImmutableMessageSnapshot() throws Exception {
        JSONArray history = new JSONArray();
        for (int index = 0; index < 2_000; index++) {
            history.put(new JSONObject()
                .put("role", index % 2 == 0 ? "user" : "assistant")
                .put("content", "message-" + index + "-" + "x".repeat(128)));
        }

        NativeHistorySnapshot snapshot = NativeHistoryParser.parse(history);

        assertEquals(2_000, snapshot.getMessages().size());
        assertTrue(snapshot.getMessages().get(0).getContent().startsWith("message-0-"));
        assertTrue(snapshot.getMessages().get(1_999).getContent().startsWith("message-1999-"));
        assertTrue(snapshot.getEstimatedChars() > 250_000);
    }

    @Test
    public void extractsPlanWithoutKeepingProtocolMessage() throws Exception {
        JSONObject plan = new JSONObject()
            .put("plan", new JSONArray().put(new JSONObject().put("step", "Inspect").put("status", "pending")))
            .put("explanation", "First inspect the project");
        JSONArray history = new JSONArray()
            .put(new JSONObject().put("role", "user").put("content", "make a plan"))
            .put(new JSONObject().put("role", "activity").put("content", encoded("PLAN|", plan)))
            .put(new JSONObject().put("role", "assistant").put("content", "done"));

        NativeHistorySnapshot snapshot = NativeHistoryParser.parse(history);
        NativeChatState state = new NativeChatState();
        state.applyHistorySnapshot(snapshot);

        assertEquals(2, snapshot.getMessages().size());
        assertEquals(1, snapshot.getPlanPanelIndex());
        assertEquals("First inspect the project", snapshot.getPlanExplanation());
        assertEquals(3, state.getMessages().size());
        assertTrue(state.getMessages().get(1).getContent().startsWith("PLAN_PANEL|complete|1"));
    }

    @Test
    public void dedicatedPlanCardSuppressesDuplicateInlinePlanPanelOnRestore() throws Exception {
        JSONObject plan = new JSONObject()
            .put("plan", new JSONArray().put(new JSONObject().put("step", "Inspect").put("status", "pending")))
            .put("explanation", "First inspect the project");
        JSONArray history = new JSONArray()
            .put(new JSONObject().put("role", "user").put("content", "make a plan"))
            .put(new JSONObject().put("role", "activity").put("content", encoded("PLAN|", plan)))
            .put(new JSONObject().put("role", "activity").put("content", "PROPOSED_PLAN|# Plan\n- Inspect"));

        NativeChatState state = new NativeChatState();
        state.applyHistorySnapshot(NativeHistoryParser.parse(history));

        assertEquals(2, state.getMessages().size());
        assertEquals(1, state.getMessages().stream()
            .filter(message -> message.getContent().startsWith("PROPOSED_PLAN|"))
            .count());
        assertFalse(state.getMessages().stream()
            .anyMatch(message -> message.getContent().startsWith("PLAN_PANEL|")));
        assertEquals("First inspect the project", state.getPlanExplanation());
    }

    @Test
    public void collectsUniqueCompletedProtocolItemIdsFromHistoryMessages() throws Exception {
        JSONArray history = new JSONArray()
            .put(new JSONObject().put("role", "assistant").put("content", "answer")
                .put("protocolItemId", " assistant-1 "))
            .put(new JSONObject().put("role", "activity").put("content", "PROPOSED_PLAN|# Plan")
                .put("protocolItemId", "plan-1"))
            .put(new JSONObject().put("role", "assistant").put("content", "duplicate id")
                .put("protocolItemId", "assistant-1"))
            .put(new JSONObject().put("role", "assistant").put("content", "legacy message")
                .put("protocolItemId", "   "));

        NativeHistorySnapshot snapshot = NativeHistoryParser.parse(history);

        assertEquals(2, snapshot.getCompletedProtocolItemIds().size());
        assertTrue(snapshot.getCompletedProtocolItemIds().contains("assistant-1"));
        assertTrue(snapshot.getCompletedProtocolItemIds().contains("plan-1"));
    }

    @Test
    public void compactsLegacyCommandOutputWhileParsingProcessHistory() throws Exception {
        String output = "terminal-line\n".repeat(20_000);
        JSONObject command = new JSONObject()
            .put("type", "commandExecution")
            .put("command", "large-output")
            .put("aggregatedOutput", output)
            .put("status", "completed");
        String legacyOutput = "legacy-output\n".repeat(10_000);
        JSONObject process = new JSONObject()
            .put("duration", 2)
            .put("reasoning", "checking")
            .put("command", legacyOutput)
            .put("tools", new JSONArray().put(command));
        JSONArray history = new JSONArray().put(new JSONObject()
            .put("role", "activity")
            .put("content", encoded("PROCESS2|", process)));

        NativeHistorySnapshot snapshot = NativeHistoryParser.parse(history);
        JSONObject compactProcess = decode(snapshot.getMessages().get(0).getContent(), "PROCESS2|");
        JSONObject compactCommand = compactProcess.getJSONArray("tools").getJSONObject(0);

        assertFalse(compactCommand.has("aggregatedOutput"));
        assertEquals("", compactProcess.getString("command"));
        assertEquals(2, compactProcess.getJSONArray("tools").length());
        JSONObject compactLegacy = compactProcess.getJSONArray("tools").getJSONObject(1);
        assertTrue(snapshot.getMessages().get(0).getContent().length() < 6_000);
        assertEquals(output, NativeCommandOutputStore.get(
            compactCommand.getString(NativeCommandOutputStore.OUTPUT_REF)));
        assertEquals(legacyOutput, NativeCommandOutputStore.get(
            compactLegacy.getString(NativeCommandOutputStore.OUTPUT_REF)));
    }

    @Test
    public void fingerprintIgnoresRandomCommandCacheReferences() throws Exception {
        String output = "same-output\n".repeat(2_000);
        JSONObject process = new JSONObject()
            .put("duration", 1)
            .put("reasoning", "same")
            .put("tools", new JSONArray().put(new JSONObject()
                .put("type", "commandExecution")
                .put("command", "echo")
                .put("aggregatedOutput", output)));
        JSONArray history = new JSONArray().put(new JSONObject()
            .put("role", "activity")
            .put("content", encoded("PROCESS2|", process)));

        NativeHistorySnapshot first = NativeHistoryParser.parse(history);
        NativeHistorySnapshot second = NativeHistoryParser.parse(history);

        assertTrue(first.getContentFingerprint() != 0L);
        assertEquals(first.getContentFingerprint(), second.getContentFingerprint());
        assertTrue(first.hasSameContent(second));
    }

    @Test
    public void snapshotCacheUsesAccessOrderedThreeEntryLru() {
        NativeHistorySnapshot a = snapshot(100);
        NativeHistorySnapshot b = snapshot(100);
        NativeHistorySnapshot c = snapshot(100);
        NativeHistorySnapshot d = snapshot(100);
        NativeHistorySnapshotCache.put("a", a);
        NativeHistorySnapshotCache.put("b", b);
        NativeHistorySnapshotCache.put("c", c);
        assertSame(a, NativeHistorySnapshotCache.get("a"));

        NativeHistorySnapshotCache.put("d", d);

        assertEquals(3, NativeHistorySnapshotCache.size());
        assertNull(NativeHistorySnapshotCache.get("b"));
        assertSame(a, NativeHistorySnapshotCache.get("a"));
        assertSame(d, NativeHistorySnapshotCache.get("d"));
    }

    @Test
    public void snapshotCacheRespectsCharacterBudgetAndSkipsOversizeHistory() {
        NativeHistorySnapshotCache.put("too-large", snapshot(2_500_001));
        assertEquals(0, NativeHistorySnapshotCache.size());

        NativeHistorySnapshotCache.put("first", snapshot(2_000_000));
        NativeHistorySnapshotCache.put("second", snapshot(800_000));

        assertNull(NativeHistorySnapshotCache.get("first"));
        assertTrue(NativeHistorySnapshotCache.get("second") != null);
        assertTrue(NativeHistorySnapshotCache.cachedCharacterCount() <= 2_500_000);
    }

    @Test
    public void routeGuardRejectsLateHistoryFromAnotherSwitch() {
        assertTrue(NativeHistoryRouteGuard.shouldApply(7, "thread-b", 7, "thread-b"));
        assertFalse(NativeHistoryRouteGuard.shouldApply(7, "thread-b", 6, "thread-b"));
        assertFalse(NativeHistoryRouteGuard.shouldApply(7, "thread-b", 7, "thread-a"));
        assertFalse(NativeHistoryRouteGuard.shouldApply(-1, "thread-b", -1, "thread-b"));
    }

    @Test
    public void legacyAssistantTailFallbackStopsAtANewerUserMessage() {
        NativeHistorySnapshot snapshot = new NativeHistorySnapshot(
            Arrays.asList(
                message("a1", NativeChatRole.ASSISTANT, "previous answer"),
                message("u2", NativeChatRole.USER, "new question"),
                message("activity", NativeChatRole.ACTIVITY, "PROCESS2|timeline")
            ),
            "[]", "", -1, 0, 0L, Collections.emptySet());

        assertEquals("", CodexAppServerBridge.historyTailAssistantText(snapshot));
    }

    @Test
    public void legacyAssistantTailFallbackMayIgnoreTrailingActivity() {
        NativeHistorySnapshot snapshot = new NativeHistorySnapshot(
            Arrays.asList(
                message("u1", NativeChatRole.USER, "question"),
                message("a1", NativeChatRole.ASSISTANT, "answer"),
                message("activity", NativeChatRole.ACTIVITY, "PROCESS2|timeline")
            ),
            "[]", "", -1, 0, 0L, Collections.emptySet());

        assertEquals("answer", CodexAppServerBridge.historyTailAssistantText(snapshot));
    }

    @Test
    public void sourceLessBlockingRequestRequiresTheCurrentTurnIdentity() throws Exception {
        assertTrue(CodexAppServerBridge.hasVerifiableRequestRoute(
            new JSONObject().put("turnId", "turn-current"), "turn-current"));
        assertFalse(CodexAppServerBridge.hasVerifiableRequestRoute(
            new JSONObject().put("turnId", "turn-old"), "turn-current"));
        assertFalse(CodexAppServerBridge.hasVerifiableRequestRoute(new JSONObject(), "turn-current"));
        assertTrue(CodexAppServerBridge.hasVerifiableRequestRoute(
            new JSONObject().put("threadId", "thread-a"), "turn-current"));
    }

    @Test
    public void serverRequestRouteKeysPreserveJsonRpcIdType() {
        assertFalse(CodexAppServerBridge.serverRequestRouteKey(1)
            .equals(CodexAppServerBridge.serverRequestRouteKey("1")));
        assertEquals(CodexAppServerBridge.serverRequestRouteKey(1),
            CodexAppServerBridge.serverRequestRouteKey(Integer.valueOf(1)));
    }

    @Test
    public void resolvedRequestIdentityCannotCrossThreadsOrTurns() throws Exception {
        assertTrue(CodexAppServerBridge.requestRouteIdentityMatches(
            "thread-a", "turn-a", new JSONObject()
                .put("threadId", "thread-a").put("turnId", "turn-a")));
        assertFalse(CodexAppServerBridge.requestRouteIdentityMatches(
            "thread-a", "turn-a", new JSONObject()
                .put("threadId", "thread-b").put("turnId", "turn-a")));
        assertFalse(CodexAppServerBridge.requestRouteIdentityMatches(
            "thread-a", "turn-a", new JSONObject()
                .put("threadId", "thread-a").put("turnId", "turn-b")));
        assertFalse(CodexAppServerBridge.requestRouteIdentityMatches(
            "thread-a", "", new JSONObject().put("turnId", "turn-b")));
    }

    private static NativeHistorySnapshot snapshot(int estimatedChars) {
        return new NativeHistorySnapshot(
            Collections.emptyList(), "[]", "", -1, estimatedChars, 0L, Collections.emptySet());
    }

    private static NativeChatMessage message(String id, NativeChatRole role, String content) {
        return new NativeChatMessage(id, role, content, false, 0L, false, null,
            Collections.emptyList(), Collections.emptyList());
    }

    private static String encoded(String prefix, JSONObject value) {
        return prefix + Base64.getEncoder().encodeToString(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject decode(String value, String prefix) throws Exception {
        return new JSONObject(new String(
            Base64.getDecoder().decode(value.substring(prefix.length())), StandardCharsets.UTF_8));
    }
}
