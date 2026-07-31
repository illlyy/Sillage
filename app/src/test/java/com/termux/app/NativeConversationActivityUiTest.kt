package com.termux.app

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NativeConversationActivityUiTest {
    @Before
    fun clearOutputStoreBeforeTest() {
        NativeCommandOutputStore.clear()
    }

    @After
    fun clearOutputStoreAfterTest() {
        NativeCommandOutputStore.clear()
    }

    @Test
    fun runningAndFailedCommandsAutoExpand() {
        assertTrue(nativeCommandAutoExpanded(NativeActivityItemStatus.RUNNING))
        assertTrue(nativeCommandAutoExpanded(NativeActivityItemStatus.FAILED))
        assertFalse(nativeCommandAutoExpanded(NativeActivityItemStatus.WAITING))
        assertFalse(nativeCommandAutoExpanded(NativeActivityItemStatus.COMPLETED))
    }

    @Test
    fun commandCollectionCollapsesAfterLastSuccessfulCommand() {
        assertTrue(nativeCommandCollectionAutoExpanded(runningCount = 1, failedCount = 0))
        assertTrue(nativeCommandCollectionAutoExpanded(runningCount = 0, failedCount = 1))
        assertFalse(nativeCommandCollectionAutoExpanded(runningCount = 0, failedCount = 0))
    }

    @Test
    fun explicitUserChoiceWinsOverAutomaticDisclosure() {
        assertFalse(resolveNativeCommandDisclosure(autoExpanded = true, userExpanded = false))
        assertTrue(resolveNativeCommandDisclosure(autoExpanded = false, userExpanded = true))
        assertTrue(resolveNativeCommandDisclosure(autoExpanded = true, userExpanded = null))
        assertFalse(resolveNativeCommandDisclosure(autoExpanded = false, userExpanded = null))
    }

    @Test
    fun fullOutputIsOnlyResolvedForAnExpandedReferencedCommand() {
        assertFalse(shouldResolveNativeCommandOutput(expanded = false, outputRef = "ref"))
        assertFalse(shouldResolveNativeCommandOutput(expanded = true, outputRef = ""))
        assertTrue(shouldResolveNativeCommandOutput(expanded = true, outputRef = "ref"))
    }

    @Test
    fun referencedOutputResolvesFullValueAndFallsBackToPreview() {
        val fullOutput = "first line\n" + "x".repeat(4_000) + "\nlast line"
        val compact = JSONObject(
            NativeCommandOutputStore.compactCommandItem(
                JSONObject().put("type", "commandExecution").put("aggregatedOutput", fullOutput),
            ),
        )
        val outputRef = compact.getString(NativeCommandOutputStore.OUTPUT_REF)
        val preview = compact.getString(NativeCommandOutputStore.OUTPUT_PREVIEW)

        assertEquals(fullOutput, resolveNativeCommandOutput(outputRef, preview))
        NativeCommandOutputStore.clear()
        assertEquals(preview, resolveNativeCommandOutput(outputRef, preview))
        assertEquals("inline", resolveNativeCommandOutput("", "inline"))
    }
}
