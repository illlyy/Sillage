package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONArray;
import org.junit.Test;

public class CodexPlanHistoryTest {
    @Test
    public void proposedPlanSplitsFromSurroundingAssistantText() throws Exception {
        JSONArray parts = CodexAppServerBridge.splitHistoricalAssistantContent(
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
        JSONArray parts = CodexAppServerBridge.splitHistoricalAssistantContent(
            "<proposed_plan>\n# Plan\n- item");

        assertEquals(1, parts.length());
        String content = parts.getJSONObject(0).getString("content");
        assertEquals("# Plan\n- item", content);
        assertFalse(content.contains("proposed_plan"));
    }
}
