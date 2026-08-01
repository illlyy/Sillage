package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class CodexPlanHistoryTest {
    @Test
    public void proposedPlanSplitsFromSurroundingAssistantText() throws Exception {
        JSONArray parts = CodexAppServerBridgeHistory.splitHistoricalAssistantContent(
            "Preface\n<proposed_plan>\n# Final plan\n\n- first\n- second\n</proposed_plan>\nPostscript");

        assertEquals(3, parts.length());
        assertEquals("assistant", parts.getJSONObject(0).getString("role"));
        assertEquals("Preface", parts.getJSONObject(0).getString("content"));
        assertEquals("plan", parts.getJSONObject(1).getString("role"));
        assertEquals("# Final plan\n\n- first\n- second", parts.getJSONObject(1).getString("content"));
        assertEquals("assistant", parts.getJSONObject(2).getString("role"));
        assertEquals("Postscript", parts.getJSONObject(2).getString("content"));
    }

    @Test
    public void malformedPlanTagsCannotDisableMarkdownRendering() throws Exception {
        JSONArray parts = CodexAppServerBridgeHistory.splitHistoricalAssistantContent(
            "<proposed_plan>\n# Plan\n- item");

        assertEquals(1, parts.length());
        String content = parts.getJSONObject(0).getString("content");
        assertEquals("# Plan\n- item", content);
        assertFalse(content.contains("proposed_plan"));
    }

    @Test
    public void completedPlanItemWinsOverTaggedAssistantReplay() throws Exception {
        JSONArray history = readHistory(
            record("event_msg", new JSONObject()
                .put("type", "item_completed")
                .put("item", new JSONObject().put("type", "Plan").put("id", "plan-1").put("text", "# Plan\n- inspect"))),
            record("response_item", assistantMessage("<proposed_plan>\n# Plan\n- inspect\n</proposed_plan>"))
        );

        assertEquals(1, history.length());
        assertEquals("activity", history.getJSONObject(0).getString("role"));
        assertEquals("history-proposed-plan:plan-1", history.getJSONObject(0).getString("id"));
        assertEquals("plan-1", history.getJSONObject(0).getString("protocolItemId"));
        assertEquals("PROPOSED_PLAN|# Plan\n- inspect", history.getJSONObject(0).getString("content"));
        assertTrue(NativeHistoryParser.parse(history).getCompletedProtocolItemIds().contains("plan-1"));
    }

    @Test
    public void completedPlanItemPreventsUntaggedPlanFromBecomingAssistantBody() throws Exception {
        JSONArray history = readHistory(
            record("event_msg", new JSONObject()
                .put("type", "item_completed")
                .put("item", new JSONObject().put("type", "Plan").put("id", "plan-2").put("text", "# Plan\n- inspect"))),
            record("response_item", assistantMessage("# Plan\n- inspect"))
        );

        assertEquals(1, history.length());
        assertEquals("activity", history.getJSONObject(0).getString("role"));
        assertFalse(history.toString().contains("\"role\":\"assistant\""));
    }

    @Test
    public void assistantAndInlinePlanPreserveCompletedProtocolItemId() throws Exception {
        JSONArray history = readHistory(
            record("response_item", assistantMessage(
                "assistant-1", "Preface\n<proposed_plan>\n# Plan\n- inspect\n</proposed_plan>\nPostscript"))
        );

        assertEquals(3, history.length());
        for (int index = 0; index < history.length(); index++) {
            assertEquals("assistant-1", history.getJSONObject(index).getString("protocolItemId"));
        }
        NativeHistorySnapshot snapshot = NativeHistoryParser.parse(history);
        assertEquals(1, snapshot.getCompletedProtocolItemIds().size());
        assertTrue(snapshot.getCompletedProtocolItemIds().contains("assistant-1"));
    }

    private static JSONObject assistantMessage(String text) throws Exception {
        return assistantMessage("", text);
    }

    private static JSONObject assistantMessage(String id, String text) throws Exception {
        return new JSONObject()
            .put("type", "message")
            .put("id", id)
            .put("role", "assistant")
            .put("content", new JSONArray().put(new JSONObject().put("type", "output_text").put("text", text)));
    }

    private static JSONObject record(String type, JSONObject payload) throws Exception {
        return new JSONObject().put("type", type).put("payload", payload);
    }

    private static JSONArray readHistory(JSONObject... records) throws Exception {
        Path file = Files.createTempFile("codex-plan-history", ".jsonl");
        try {
            StringBuilder content = new StringBuilder();
            for (JSONObject record : records) content.append(record).append('\n');
            Files.writeString(file, content.toString(), StandardCharsets.UTF_8);
            return CodexAppServerBridgeHistory.readConversationHistory(file.toFile());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
