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
    public void recognizesGfmTablesWithoutMisclassifyingPipedProse() {
        String table = "| Name | Status | Note |\n|---|:---:|---:|\n| A | done | first |";
        assertTrue(NativeUiRenderSafety.containsMarkdownTable(table));
        assertFalse(NativeUiRenderSafety.containsMarkdownTable("Use A | B in prose\nwithout a delimiter row"));
        assertTrue(NativeUiRenderSafety.containsMarkdownTable("a | b\n--|--"));
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

    @Test
    public void streamFlushSlowsProgressivelyWithoutMakingShortAnswersLaggy() {
        assertEquals(56L, NativeUiRenderSafety.streamFlushDelayMs(200, 10, 56L));
        assertEquals(120L, NativeUiRenderSafety.streamFlushDelayMs(8_000, 10, 56L));
        assertEquals(168L, NativeUiRenderSafety.streamFlushDelayMs(32_000, 10, 56L));
        assertEquals(220L, NativeUiRenderSafety.streamFlushDelayMs(96_000, 10, 56L));
        assertEquals(220L, NativeUiRenderSafety.streamFlushDelayMs(Integer.MAX_VALUE, Integer.MAX_VALUE, 56L));
        assertEquals(120L, NativeUiRenderSafety.streamFlushDelayMs(9_000, 10, 120L));
    }

    @Test
    public void liveTextUsesACriticallyDampedBoundedReveal() {
        assertEquals(1f, NativeStreamRevealPolicy.STREAM_DAMPING_RATIO, 0.0001f);
        assertTrue(NativeStreamRevealPolicy.STREAM_STIFFNESS > 0f);
        assertTrue(NativeStreamRevealPolicy.STREAM_FEATHER_DP >= 20f);
        assertTrue(NativeStreamRevealPolicy.DURATION_MS < 300);
    }

    @Test
    public void terminalEventsNeverLeaveATinyDeltaScheduled() {
        org.junit.Assert.assertTrue(NativeUiRenderSafety.shouldDeferStreamFlush(4, false, 20L, false));
        org.junit.Assert.assertFalse(NativeUiRenderSafety.shouldDeferStreamFlush(4, false, 20L, true));
    }

    @Test
    public void uiMotionDefersLiveUpdatesButNeverTerminalFlushes() {
        assertTrue(NativeUiRenderSafety.shouldDeferStreamFlushForUiMotion(true, false));
        assertFalse(NativeUiRenderSafety.shouldDeferStreamFlushForUiMotion(true, true));
        assertFalse(NativeUiRenderSafety.shouldDeferStreamFlushForUiMotion(false, false));
    }

    @org.junit.Test
    public void plainProseSkipsRichMarkdownRenderer() {
        org.junit.Assert.assertFalse(NativeUiRenderSafety.requiresRichMarkdown("?????\nSecond paragraph."));
        org.junit.Assert.assertTrue(NativeUiRenderSafety.requiresRichMarkdown("## Heading"));
        org.junit.Assert.assertTrue(NativeUiRenderSafety.requiresRichMarkdown("- item"));
        org.junit.Assert.assertTrue(NativeUiRenderSafety.requiresRichMarkdown("**bold**"));
        org.junit.Assert.assertTrue(NativeUiRenderSafety.requiresRichMarkdown("[link](https://example.com)"));
        org.junit.Assert.assertTrue(NativeUiRenderSafety.requiresRichMarkdown("1) ordered"));
        org.junit.Assert.assertTrue(NativeUiRenderSafety.requiresRichMarkdown("Title\n===="));
        org.junit.Assert.assertTrue(NativeUiRenderSafety.requiresRichMarkdown("inline $x$"));
        org.junit.Assert.assertTrue(NativeUiRenderSafety.requiresRichMarkdown("<br>"));
    }

}
