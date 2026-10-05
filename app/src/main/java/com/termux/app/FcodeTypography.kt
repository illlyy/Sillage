package com.termux.app

/**
 * The app's spacing scale, in one place.
 *
 * Before this the message area alone used 2/3/4/6/7/8/10 dp with no relationship between them, so
 * two gaps that meant the same thing had different sizes and two that meant different things had
 * the same one. Everything here is a multiple of four.
 */
object FcodeSpace {
    /** Hairline: inside a single control. */
    const val xs = 4f

    /** Two things of the same voice, e.g. two paragraphs of one answer. */
    const val sm = 8f

    /** Around something that stands apart from the prose: a code fence, a table, a card. */
    const val md = 12f

    /** Between groups inside one section. */
    const val lg = 16f

    /** Above a heading, and between sections. */
    const val xl = 20f

    /** Page edges, and the space above a dialog title. */
    const val xxl = 24f
}

/**
 * The typographic scale for message content.
 *
 * Sizes are the ones already shipping; naming them is what stops a fourth one appearing. There
 * were three before: 15.5sp for prose, 13sp for code, and a 12sp path that turned out to have no
 * callers at all and is now deleted.
 */
object FcodeType {
    /** Answer body: prose, headings and lists all inherit this. */
    const val bodySp = 15.5f
    const val bodyLineHeightMultiplier = 1.12f

    /** Code and tables sit a step below the body so they read as quoted evidence, not prose. */
    const val codeSp = 13f
    const val codeLineHeightMultiplier = 1.04f

    /** Extra leading in dp, on top of the multiplier, because CJK is cramped without it. */
    const val bodyLineSpacingExtraDp = 2f
    const val codeLineSpacingExtraDp = 2f
}

/**
 * Vertical gap to place above a block in an answer body.
 *
 * The body used to run every block on one uniform gap, which is why a long answer read as a wall:
 * a heading, the paragraph under it, and a code fence all looked equally related to what came
 * before. The gap now says what the relationship is -- prose flows, structure gets air, a heading
 * gets an opening.
 *
 * Pure and Android-free so the rule is unit-testable; the composition only converts the number.
 */
internal fun fcodeBlockGapDp(
    previousType: NativeMarkdownBlockType?,
    currentType: NativeMarkdownBlockType,
    currentText: String,
): Float {
    // The first block defines the top of the body; its own spacing belongs to the bubble.
    if (previousType == null) return 0f
    // A heading opens a new thought, whichever kind of block it is rendered as.
    if (currentType == NativeMarkdownBlockType.PROSE &&
        currentText.trimStart().startsWith("#")
    ) {
        return FcodeSpace.xl
    }
    if (isStructural(currentType) || isStructural(previousType)) return FcodeSpace.md
    return FcodeSpace.sm
}

/** Code fences, tables and standalone inline code are boxed, so they need not to touch prose. */
private fun isStructural(type: NativeMarkdownBlockType): Boolean =
    type == NativeMarkdownBlockType.CODE ||
        type == NativeMarkdownBlockType.TABLE ||
        type == NativeMarkdownBlockType.INLINE_CODE
