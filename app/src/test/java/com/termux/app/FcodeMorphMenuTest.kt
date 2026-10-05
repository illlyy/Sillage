package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stagger arithmetic that drives the "more" menu's row reveal.
 *
 * This exists because the previous implementation used fixed constants
 * (`START = 0.30 / STEP = 0.16 / SPAN = 0.36`) that silently overran the animated range: the last
 * row's window began at 0.78, so once the reveal settled at 1.0 it was still only 61% through its
 * own window -- permanently alpha 0.66 and 6dp low. A fifth item would have begun at 0.94 and
 * finished at alpha 0.07. Nothing caught it because nothing tested the arithmetic.
 */
class FcodeMorphMenuTest {

    private fun assertClose(expected: Float, actual: Float, message: String = "") {
        assertEquals(message, expected.toDouble(), actual.toDouble(), 1e-4)
    }

    @Test
    fun everyRowReachesFullOpacityByTheEndForAnyCount() {
        for (count in 1..8) {
            val last = morphMenuItemReveal(index = count - 1, count = count, reveal = 1f)
            assertClose(1f, last, "count=$count left its last row at alpha $last")
        }
    }

    @Test
    fun theLastRowStillHasMarginBeforeTheAnimationEnds() {
        // Finishing exactly at reveal == 1.0 would mean the row arrives on the final frame, which
        // reads as a snap. The reserve keeps it settled before the container stops moving.
        for (count in 1..8) {
            val last = morphMenuItemReveal(index = count - 1, count = count, reveal = 0.95f)
            assertClose(1f, last, "count=$count last row had not settled by reveal 0.95")
        }
    }

    @Test
    fun fourRowsIsTheCaseThatUsedToBreak() {
        // The menu that exposed this ships four items. Row index 3 is the one that used to stick at
        // alpha 0.66; this pins the value it must reach instead.
        assertClose(1f, morphMenuItemReveal(index = 3, count = 4, reveal = 1f))
    }

    @Test
    fun aFifthRowWouldAlsoBeVisible() {
        // The old constants still "worked" for four rows in the sense that three were fine; the
        // fifth was where a row would have been invisible while the menu was open.
        assertClose(1f, morphMenuItemReveal(index = 4, count = 5, reveal = 1f))
    }

    @Test
    fun rowsAreFullyHiddenBeforeTheStaggerStarts() {
        for (count in 1..8) {
            assertEquals(0f, morphMenuItemReveal(index = count - 1, count = count, reveal = 0f), 1e-4f)
        }
    }

    @Test
    fun rowsNeverLeadTheRowAboveThem() {
        // Reveal must cascade downwards; a later row that leads an earlier one would read as the
        // menu assembling backwards.
        for (count in 2..8) {
            for (step in 0..40) {
                val reveal = step / 40f
                for (index in 0 until count - 1) {
                    val upper = morphMenuItemReveal(index, count, reveal)
                    val lower = morphMenuItemReveal(index + 1, count, reveal)
                    assertTrue(
                        "count=$count reveal=$reveal row ${index + 1} led row $index",
                        lower <= upper + 1e-4f,
                    )
                }
            }
        }
    }

    @Test
    fun anEmptyMenuIsTreatedAsFullyRevealed() {
        assertEquals(1f, morphMenuItemReveal(index = 0, count = 0, reveal = 0f), 1e-4f)
    }

    @Test
    fun theSourceButtonIsUntouchedWhileTheMenuIsClosed() {
        assertClose(1f, morphAnchorAlpha(0f))
    }

    @Test
    fun theSourceButtonDimsButDoesNotVanishWhileTheMenuIsOpen() {
        val open = morphAnchorAlpha(1f)
        assertClose(0.55f, open)
        assertTrue("the source faded to $open", open > 0f)
    }

    @Test
    fun theSourceButtonNeverDisappearsAtAnyPointInTheMorph() {
        // The regression this pins: the button used to fade to 0 on open, which left the closing
        // animation with no source to return into -- the motion read as a menu appearing from
        // nowhere rather than one unfolding out of the button.
        for (step in 0..100) {
            val alpha = morphAnchorAlpha(step / 100f)
            assertTrue("alpha collapsed to $alpha at ${step}%", alpha >= 0.5f)
        }
    }

    @Test
    fun theSourceButtonDimsMonotonicallyAsTheMenuOpens() {
        var previous = morphAnchorAlpha(0f)
        for (step in 1..100) {
            val alpha = morphAnchorAlpha(step / 100f)
            assertTrue("alpha rose from $previous to $alpha", alpha <= previous + 1e-4f)
            previous = alpha
        }
    }

    @Test
    fun theSourceButtonIsClampedForOutOfRangeProgress() {
        assertClose(1f, morphAnchorAlpha(-3f))
        assertClose(0.55f, morphAnchorAlpha(4f))
    }
}
