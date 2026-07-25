package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NativeCommandStateTest {
    @Before
    public void setUp() {
        NativeCommandOutputStore.clear();
    }

    @After
    public void tearDown() {
        NativeCommandOutputStore.clear();
    }

    @Test
    public void liveComposeStateStaysBoundedWhileFullStreamIsBuffered() throws Exception {
        NativeChatState state = new NativeChatState();
        state.startCommand(new JSONObject().put("id", "live-1").put("command", "large-output").toString());
        String output = "0123456789\n".repeat(20_000);

        state.appendCommandOutput(output);

        assertTrue(state.getCommandText().length() < 2_000);
        assertTrue(state.getCommandText().endsWith("0123456789\n"));
        state.completeCommand(new JSONObject().put("id", "live-1").put("exitCode", 0).toString());

        assertEquals("", state.getCommandText());
        assertEquals(1, state.getToolDetails().size());
        JSONObject compact = new JSONObject(state.getToolDetails().get(0));
        assertFalse(compact.has("aggregatedOutput"));
        assertTrue(compact.toString().length() < 2_000);
        assertEquals(output, NativeCommandOutputStore.get(compact.getString(NativeCommandOutputStore.OUTPUT_REF)));
    }
    @Test
    public void bridgeReferenceAvoidsDuplicatingTheMainThreadFallbackBuffer() throws Exception {
        NativeChatState state = new NativeChatState();
        state.startCommand(new JSONObject().put("id", "live-2").put("command", "echo done").toString());
        state.appendCommandOutput("streamed fallback that should not be copied");
        JSONObject bridgePayload = new JSONObject(NativeCommandOutputStore.compactCommandItem(
            new JSONObject().put("id", "live-2").put("aggregatedOutput", "authoritative output")));

        state.completeCommand(bridgePayload.toString());

        JSONObject storedItem = new JSONObject(state.getToolDetails().get(0));
        assertEquals(1, NativeCommandOutputStore.cachedEntryCount());
        assertEquals("authoritative output", NativeCommandOutputStore.get(
            storedItem.getString(NativeCommandOutputStore.OUTPUT_REF)));
    }

    @Test
    public void parallelOutputBeforeStartDoesNotBorrowAnotherCommandMetadata() throws Exception {
        NativeChatState state = new NativeChatState();
        state.startCommand(new JSONObject().put("id", "command-a").put("command", "echo a").toString(), "command-a");
        state.appendCommandOutput("b output", "command-b");
        state.completeCommand(new JSONObject().put("id", "command-b").put("status", "completed").toString(), "command-b");

        assertEquals(1, state.getToolDetails().size());
        JSONObject completed = new JSONObject(state.getToolDetails().get(0));
        assertEquals("command-b", completed.getString("id"));
        assertFalse("echo a".equals(completed.optString("command")));
        assertEquals("b output", NativeCommandOutputStore.get(
            completed.getString(NativeCommandOutputStore.OUTPUT_REF)));
    }

    @Test
    public void duplicateCommandCompletionUpdatesOneStoredItem() throws Exception {
        NativeChatState state = new NativeChatState();
        String started = new JSONObject().put("id", "same").put("command", "echo once").toString();
        String completed = new JSONObject().put("id", "same").put("command", "echo once").put("status", "completed").toString();
        state.startCommand(started, "same");
        state.completeCommand(completed, "same");
        state.completeCommand(completed, "same");

        assertEquals(1, state.getToolDetails().size());
    }

}
