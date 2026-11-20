package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class CodexHistoryUserMessageTest {
    @Test
    public void unrelatedEventRecordsDoNotHideResponseItemUserMessages() throws Exception {
        JSONArray history = readHistory(
            record("event_msg", new JSONObject().put("type", "token_count")),
            record("response_item", userMessage("hello")),
            record("response_item", assistantMessage("hi"))
        );

        assertEquals(2, history.length());
        assertEquals("user", history.getJSONObject(0).getString("role"));
        assertEquals("hello", history.getJSONObject(0).getString("content"));
        assertEquals("assistant", history.getJSONObject(1).getString("role"));
    }

    @Test
    public void everyTurnKeepsItsUserRowWhenEventMessagesExist() throws Exception {
        JSONArray history = readHistory(
            record("event_msg", new JSONObject().put("type", "thread_settings_applied")),
            record("response_item", userMessage("first")),
            record("response_item", assistantMessage("one")),
            record("event_msg", new JSONObject().put("type", "token_count")),
            record("response_item", userMessage("second")),
            record("response_item", assistantMessage("two"))
        );

        assertEquals(4, history.length());
        assertEquals("first", history.getJSONObject(0).getString("content"));
        assertEquals("second", history.getJSONObject(2).getString("content"));
    }

    @Test
    public void eventAndResponseCopiesOfTheSamePromptAreDeduplicated() throws Exception {
        JSONArray history = readHistory(
            record("event_msg", new JSONObject().put("type", "user_message").put("message", "same prompt")),
            record("response_item", userMessage("same prompt")),
            record("response_item", assistantMessage("answer"))
        );

        assertEquals(2, history.length());
        assertEquals("user", history.getJSONObject(0).getString("role"));
        assertEquals("same prompt", history.getJSONObject(0).getString("content"));
    }

    @Test
    public void attachmentOnlyPromptIsNotTreatedAsInjectedContext() throws Exception {
        JSONObject user = userMessage("")
            .put("attachments", new JSONArray().put(new JSONObject()
                .put("name", "photo.png").put("path", "/tmp/photo.png").put("image", true)));
        JSONArray history = readHistory(
            record("response_item", user),
            record("response_item", assistantMessage("image received"))
        );

        assertEquals(2, history.length());
        assertEquals("user", history.getJSONObject(0).getString("role"));
        assertTrue(history.getJSONObject(0).getJSONArray("attachments").length() == 1);
    }

    @Test
    public void internalTurnAbortedContextNeverBecomesAUserMessage() throws Exception {
        JSONObject aborted = userMessage(
            "<turn_aborted>\nThe user interrupted the previous turn on purpose.\n</turn_aborted>")
            .put("internal_chat_message_metadata_passthrough", new JSONObject()
                .put("content_item_kinds", new JSONArray().put("generic.turn_aborted")));
        JSONArray history = readHistory(
            record("response_item", userMessage("real prompt")),
            record("response_item", assistantMessage("working")),
            record("response_item", aborted),
            record("response_item", assistantMessage("continued"))
        );

        assertEquals(3, history.length());
        assertEquals("real prompt", history.getJSONObject(0).getString("content"));
        assertEquals("assistant", history.getJSONObject(1).getString("role"));
        assertEquals("assistant", history.getJSONObject(2).getString("role"));
    }

    private static JSONObject userMessage(String text) throws Exception {
        return new JSONObject()
            .put("type", "message")
            .put("role", "user")
            .put("content", new JSONArray().put(new JSONObject().put("type", "input_text").put("text", text)));
    }

    private static JSONObject assistantMessage(String text) throws Exception {
        return new JSONObject()
            .put("type", "message")
            .put("role", "assistant")
            .put("content", new JSONArray().put(new JSONObject().put("type", "output_text").put("text", text)));
    }

    private static JSONObject record(String type, JSONObject payload) throws Exception {
        return new JSONObject().put("type", type).put("payload", payload);
    }

    private static JSONArray readHistory(JSONObject... records) throws Exception {
        Path file = Files.createTempFile("codex-user-history", ".jsonl");
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
