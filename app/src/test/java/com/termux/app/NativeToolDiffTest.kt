package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeToolDiffTest {

    @Test
    fun identicalContentProducesNoDiff() {
        assertEquals(emptyList<NativeDiffLine>(), calculateDiff("a\nb\nc", "a\nb\nc"))
    }

    @Test
    fun insertionStaysLocal() {
        val diff = calculateDiff("a\nb\nc", "a\nx\nb\nc")
        assertEquals(1, diff.size)
        assertEquals(NativeDiffLineType.ADDED, diff.single().type)
        assertEquals("x", diff.single().content)
    }

    @Test
    fun deletionStaysLocal() {
        val diff = calculateDiff("a\nb\nc", "a\nc")
        assertEquals(1, diff.size)
        assertEquals(NativeDiffLineType.REMOVED, diff.single().type)
        assertEquals("b", diff.single().content)
    }

    @Test
    fun editDoesNotCascadeIntoSingleBigDiff() {
        val oldContent = (1..50).joinToString("\n") { "line $it" }
        val newContent = (1..50).joinToString("\n") { if (it == 25) "line 25 changed" else "line $it" }
        val diff = calculateDiff(oldContent, newContent)
        assertEquals(2, diff.size)
        assertEquals(NativeDiffLineType.REMOVED, diff[0].type)
        assertEquals("line 25", diff[0].content)
        assertEquals(NativeDiffLineType.ADDED, diff[1].type)
        assertEquals("line 25 changed", diff[1].content)
        assertEquals(25, diff[0].lineNum)
        assertEquals(25, diff[1].lineNum)
    }

    @Test
    fun trailingAppendIsReported() {
        val diff = calculateDiff("a\nb", "a\nb\nc")
        assertEquals(1, diff.size)
        assertEquals(NativeDiffLineType.ADDED, diff.single().type)
        assertEquals("c", diff.single().content)
    }

    @Test
    fun fullReplacementProducesRemovedThenAdded() {
        val diff = calculateDiff("old line", "new line")
        assertEquals(2, diff.size)
        assertEquals(NativeDiffLineType.REMOVED, diff[0].type)
        assertEquals(NativeDiffLineType.ADDED, diff[1].type)
    }

    @Test
    fun cachedCalculatorReturnsSameResult() {
        val calculator = NativeCachedDiffCalculator(maxEntries = 10)
        val first = calculator.diff("a\nb", "a\nc")
        val second = calculator.diff("a\nb", "a\nc")
        assertEquals(first, second)
        assertEquals(1, calculator.size)
    }

    @Test
    fun cachedCalculatorEvictsOldestEntries() {
        val calculator = NativeCachedDiffCalculator(maxEntries = 3)
        calculator.diff("1", "1a")
        calculator.diff("2", "2a")
        calculator.diff("3", "3a")
        calculator.diff("4", "4a")
        assertEquals(3, calculator.size)
        calculator.diff("4", "4a")
        assertEquals(3, calculator.size)
    }

    @Test
    fun emptyLineContentIsHandled() {
        val diff = calculateDiff("a\n\nb", "a\n\nc")
        assertEquals(2, diff.size)
        assertTrue(diff.any { it.type == NativeDiffLineType.REMOVED && it.content == "b" })
        assertTrue(diff.any { it.type == NativeDiffLineType.ADDED && it.content == "c" })
    }
}
