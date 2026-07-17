package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class NativeUiRenderSafetyTest {
    @Test
    public void markdownChunksPreserveSourceAndStayBounded() {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 700; i++) {
            source.append(i).append(". a fairly long markdown list row for render safety\n");
        }
        List<String> chunks = NativeUiRenderSafety.splitMarkdown(source.toString());
        assertTrue(chunks.size() > 1);
        assertEquals(source.toString(), String.join("", chunks));
        for (String chunk : chunks) {
            assertTrue("chunk chars", chunk.length() <= 3200);
            assertTrue("chunk lines", chunk.chars().filter(c -> c == '\n').count() <= 120);
        }
    }

    @Test
    public void tallDocumentsNeverUseWholeDocumentGpuAnimation() {
        assertTrue(NativeUiRenderSafety.canAnimateDocument("short answer"));
        assertFalse(NativeUiRenderSafety.canAnimateDocument(("x\n").repeat(500)));
        assertFalse(NativeUiRenderSafety.canAnimateDocument("x".repeat(5000)));
    }

    @Test
    public void markdownChunksDoNotSplitSurrogatePairs() {
        String source = "\uD83D\uDE00".repeat(2200);
        List<String> chunks = NativeUiRenderSafety.splitMarkdown(source);
        assertEquals(source, String.join("", chunks));
        for (String chunk : chunks) {
            assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
            assertFalse(Character.isLowSurrogate(chunk.charAt(0)));
        }
    }

    @Test
    public void sensitiveProviderErrorsNeverExposeRawToolOutput() {
        String raw = "private-command-output\nAgent errored: SensitiveContentDetected";
        String error = NativeUiRenderSafety.errorSummary(raw);
        String tool = NativeUiRenderSafety.sanitizeToolDetail(raw);
        assertTrue(error.contains("\u654f\u611f\u5185\u5bb9\u4fdd\u62a4"));
        assertTrue(tool.contains("\u654f\u611f\u5185\u5bb9\u4fdd\u62a4"));
        assertFalse(error.contains("private-command-output"));
        assertFalse(tool.contains("private-command-output"));
    }

    @Test
    public void oversizedErrorsAreCompact() {
        String raw = "x".repeat(5000);
        String result = NativeUiRenderSafety.errorSummary(raw);
        assertTrue(result.length() < raw.length());
        assertTrue(result.contains("\u5df2\u6298\u53e0\u663e\u793a"));
    }
}
