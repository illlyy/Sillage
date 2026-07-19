package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NativeStreamingMarkdownTest {
    @Test
    public void keepsSmallUnfinishedParagraphInAnimatedTail() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();

        NativeStreamingMarkdownSnapshot snapshot = accumulator.update("Short paragraph still arriving", false);

        assertTrue(snapshot.getBlocks().isEmpty());
        assertEquals("Short paragraph still arriving", snapshot.getTail());
        assertEquals(0, snapshot.getStableChars());
    }

    @Test
    public void freezesGroupedParagraphsAndOnlyLeavesActiveTail() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();
        String stable = paragraph("first", 500) + paragraph("second", 500) + paragraph("third", 500);
        String source = stable + "active tail";

        NativeStreamingMarkdownSnapshot snapshot = accumulator.update(source, false);

        assertEquals(1, snapshot.getBlocks().size());
        assertEquals(stable, snapshot.getBlocks().get(0).getText());
        assertEquals("active tail", snapshot.getTail());
        assertEquals(source, reconstruct(snapshot));
    }

    @Test
    public void reusesPreviouslyFrozenBlockInstancesAcrossDeltas() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();
        String stable = paragraph("a", 600) + paragraph("b", 600);
        NativeStreamingMarkdownSnapshot first = accumulator.update(stable + "tail", false);

        NativeStreamingMarkdownSnapshot second = accumulator.update(stable + "tail grows", false);

        assertEquals(1, first.getBlocks().size());
        assertSame(first.getBlocks(), second.getBlocks());
        assertSame(first.getBlocks().get(0), second.getBlocks().get(0));
        assertEquals("tail grows", second.getTail());
    }

    @Test
    public void doesNotSplitBlankLinesInsideOpenCodeFence() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();
        String open = "```kotlin\nfun main() {\n\n    println(1)\n";

        NativeStreamingMarkdownSnapshot pending = accumulator.update(open, false);
        NativeStreamingMarkdownSnapshot closed = accumulator.update(open + "}\n```\nnext", false);

        assertTrue(pending.getBlocks().isEmpty());
        assertEquals(1, closed.getBlocks().size());
        assertTrue(closed.getBlocks().get(0).getText().endsWith("```\n"));
        assertEquals("next", closed.getTail());
    }

    @Test
    public void promotesCompletedTableBeforeNormalSizeTarget() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();
        String table = "| Name | Value |\n| --- | ---: |\n| Alpha | 1 |\n\n";

        NativeStreamingMarkdownSnapshot incomplete = accumulator.update(table.stripTrailing(), false);
        NativeStreamingMarkdownSnapshot complete = accumulator.update(table + "following", false);

        assertTrue(incomplete.getBlocks().isEmpty());
        assertEquals(1, complete.getBlocks().size());
        assertEquals(table, complete.getBlocks().get(0).getText());
        assertEquals("following", complete.getTail());
    }

    @Test
    public void keepsListTogetherUntilItsMarkdownBoundaryIsStable() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();
        String list = "- first item\n- second item\n  - nested item\n\n";

        NativeStreamingMarkdownSnapshot live = accumulator.update(list + "next paragraph", false);
        NativeStreamingMarkdownSnapshot finished = accumulator.update(list + "next paragraph", true);

        assertTrue(live.getBlocks().isEmpty());
        assertEquals(list + "next paragraph", live.getTail());
        assertEquals(list + "next paragraph", reconstruct(finished));
        assertEquals("", finished.getTail());
    }

    @Test
    public void finalSnapshotFreezesResidualMarkdownWithoutWholeDocumentSwap() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();
        String prefix = paragraph("stable", 1_300);
        NativeStreamingMarkdownSnapshot live = accumulator.update(prefix + "## Final heading\nlast paragraph", false);
        NativeStreamingMarkdownBlock stableBlock = live.getBlocks().get(0);

        NativeStreamingMarkdownSnapshot finished = accumulator.update(prefix + "## Final heading\nlast paragraph", true);

        assertEquals("", finished.getTail());
        assertEquals(2, finished.getBlocks().size());
        assertSame(stableBlock, finished.getBlocks().get(0));
        assertEquals(prefix + "## Final heading\nlast paragraph", reconstruct(finished));
    }

    @Test
    public void authoritativeReplacementResetsOldStableBlocks() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();
        String original = paragraph("old", 1_300) + "old tail";
        NativeStreamingMarkdownSnapshot first = accumulator.update(original, false);
        assertFalse(first.getBlocks().isEmpty());

        String replacement = "Completely replaced final answer";
        NativeStreamingMarkdownSnapshot replaced = accumulator.update(replacement, true);

        assertEquals(1, replaced.getBlocks().size());
        assertEquals(replacement, reconstruct(replaced));
        assertEquals(0, replaced.getBlocks().get(0).getStart());
    }

    @Test
    public void longParagraphStreamKeepsSemanticTailBoundedWhenBoundariesExist() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();
        StringBuilder source = new StringBuilder();
        for (int index = 0; index < 200; index++) source.append(paragraph("p" + index, 180));
        source.append("latest tail");

        NativeStreamingMarkdownSnapshot snapshot = accumulator.update(source.toString(), false);

        assertTrue(snapshot.getBlocks().size() > 10);
        assertTrue(snapshot.getTail().length() < 2_400);
        assertEquals(source.toString(), reconstruct(snapshot));
    }

    @Test
    public void liveWindowKeepsOnlyABoundedStableSuffix() {
        java.util.ArrayList<NativeStreamingMarkdownBlock> blocks = new java.util.ArrayList<>();
        for (int index = 0; index < 10; index++) {
            int start = index * 1000;
            blocks.add(new NativeStreamingMarkdownBlock(start, start + 1000, "x".repeat(1000)));
        }
        int first = NativeStreamingMarkdownWindow.firstVisibleBlock(blocks, 1500, 6000);
        assertEquals(5, first);
        assertEquals(9, NativeStreamingMarkdownWindow.firstVisibleBlock(blocks, 9000, 6000));
        assertEquals(0, NativeStreamingMarkdownWindow.firstVisibleBlock(java.util.Collections.emptyList(), 0, 6000));
    }

    @Test
    public void appendApiPublishesBoundedTailWithoutFullDocumentCopies() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();
        StringBuilder source = new StringBuilder();
        NativeStreamingMarkdownSnapshot snapshot = null;
        for (int index = 0; index < 500; index++) {
            String delta = "delta-" + index + " " + "word ".repeat(12) + "\n\n";
            source.append(delta);
            snapshot = accumulator.append(delta, false);
        }
        assertTrue(snapshot != null);
        assertEquals(source.toString(), reconstruct(snapshot));
        assertTrue(snapshot.getBlocks().size() > 10);
        assertTrue(snapshot.getTail().length() < 2_400);
        assertEquals(source.length(), snapshot.getSourceChars());
    }

    @Test
    public void handlesHundredsOfAppendOnlyUpdatesWithoutChangingFrozenPrefix() {
        NativeStreamingMarkdownAccumulator accumulator = new NativeStreamingMarkdownAccumulator();
        StringBuilder source = new StringBuilder();
        NativeStreamingMarkdownBlock firstFrozen = null;
        for (int index = 0; index < 500; index++) {
            source.append("delta-").append(index).append(' ').append("word ".repeat(12)).append("\n\n");
            NativeStreamingMarkdownSnapshot snapshot = accumulator.update(source.toString(), false);
            if (!snapshot.getBlocks().isEmpty()) {
                if (firstFrozen == null) firstFrozen = snapshot.getBlocks().get(0);
                else assertSame(firstFrozen, snapshot.getBlocks().get(0));
            }
            assertEquals(source.toString(), reconstruct(snapshot));
        }
        assertTrue(firstFrozen != null);
    }

    private static String paragraph(String seed, int targetLength) {
        StringBuilder value = new StringBuilder(seed).append(' ');
        while (value.length() < targetLength) value.append("word ");
        return value.substring(0, targetLength) + "\n\n";
    }

    private static String reconstruct(NativeStreamingMarkdownSnapshot snapshot) {
        StringBuilder value = new StringBuilder();
        for (NativeStreamingMarkdownBlock block : snapshot.getBlocks()) value.append(block.getText());
        value.append(snapshot.getTail());
        return value.toString();
    }
}
