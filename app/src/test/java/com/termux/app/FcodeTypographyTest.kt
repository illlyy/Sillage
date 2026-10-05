package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The block-spacing rule for an answer body.
 *
 * This replaced one uniform 10dp gap for every block, which is why a heading, the paragraph under
 * it and a code fence all read as equally related -- a long answer looked like a wall. The rule is
 * a pure function precisely so it can be pinned: the composition only turns the number into dp.
 */
class FcodeTypographyTest {

    private fun gap(
        previous: NativeMarkdownBlockType?,
        current: NativeMarkdownBlockType,
        text: String = "body",
    ) = fcodeBlockGapDp(previous, current, text)

    @Test
    fun theFirstBlockCarriesNoGapOfItsOwn() {
        // The top of the body belongs to the bubble around it, not to the block.
        assertEquals(0f, gap(null, NativeMarkdownBlockType.PROSE), 1e-4f)
        assertEquals(0f, gap(null, NativeMarkdownBlockType.CODE), 1e-4f)
    }

    @Test
    fun proseFlowsIntoProse() {
        assertEquals(FcodeSpace.sm, gap(NativeMarkdownBlockType.PROSE, NativeMarkdownBlockType.PROSE), 1e-4f)
    }

    @Test
    fun codeStandsApartFromProseInBothDirections() {
        assertEquals(FcodeSpace.md, gap(NativeMarkdownBlockType.PROSE, NativeMarkdownBlockType.CODE), 1e-4f)
        assertEquals(FcodeSpace.md, gap(NativeMarkdownBlockType.CODE, NativeMarkdownBlockType.PROSE), 1e-4f)
    }

    @Test
    fun tablesAndStandaloneInlineCodeAreStructuralToo() {
        for (structural in listOf(NativeMarkdownBlockType.TABLE, NativeMarkdownBlockType.INLINE_CODE)) {
            assertEquals(FcodeSpace.md, gap(NativeMarkdownBlockType.PROSE, structural), 1e-4f)
            assertEquals(FcodeSpace.md, gap(structural, NativeMarkdownBlockType.PROSE), 1e-4f)
        }
    }

    @Test
    fun aHeadingGetsAnOpening() {
        val heading = "## What changed"
        assertEquals(FcodeSpace.xl, gap(NativeMarkdownBlockType.PROSE, NativeMarkdownBlockType.PROSE, heading), 1e-4f)
        // Even straight after a code fence the heading is what opens the next thought.
        assertEquals(FcodeSpace.xl, gap(NativeMarkdownBlockType.CODE, NativeMarkdownBlockType.PROSE, heading), 1e-4f)
        // Leading whitespace must not defeat the detection.
        assertEquals(FcodeSpace.xl, gap(NativeMarkdownBlockType.PROSE, NativeMarkdownBlockType.PROSE, "   ### Indented"), 1e-4f)
    }

    @Test
    fun aHashInsideProseIsNotAHeading() {
        // A bare `#` only means a heading at the start of a block.
        assertEquals(FcodeSpace.sm, gap(NativeMarkdownBlockType.PROSE, NativeMarkdownBlockType.PROSE, "issue #42 is fixed"), 1e-4f)
    }

    @Test
    fun everyGapComesFromTheSpacingScale() {
        // Guards the reason this file exists: no gap may be an invented number again.
        val scale = setOf(FcodeSpace.xs, FcodeSpace.sm, FcodeSpace.md, FcodeSpace.lg, FcodeSpace.xl, FcodeSpace.xxl)
        val types = NativeMarkdownBlockType.values()
        for (previous in types) {
            for (current in types) {
                for (text in listOf("body", "# heading")) {
                    val value = gap(previous, current, text)
                    assertTrue("gap $value is not on the scale", value in scale)
                }
            }
        }
    }
}
