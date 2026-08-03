package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class NativeUserInputAnswersTest {

    private fun question(
        multiSelect: Boolean = false,
        required: Boolean = true,
        options: List<String> = emptyList(),
        isOther: Boolean? = null,
    ): JSONObject = JSONObject().apply {
        if (multiSelect) put("multiSelect", true)
        if (!required) put("required", false)
        if (options.isNotEmpty()) {
            val array = org.json.JSONArray()
            options.forEach { array.put(JSONObject().put("label", it)) }
            put("options", array)
        }
        if (isOther != null) put("isOther", isOther)
    }

    @Test
    fun optionsParseObjectsAndStrings() {
        val q = JSONObject()
        q.put("options", org.json.JSONArray().apply {
            put(JSONObject().put("label", "Yes").put("description", "do it"))
            put("plain string")
        })
        val options = nativeQuestionOptions(q)
        assertEquals(2, options.size)
        assertEquals("Yes", options[0].label)
        assertEquals("do it", options[0].description)
        assertEquals("plain string", options[1].label)
        assertEquals("", options[1].description)
    }

    @Test
    fun blankLabelsAreFiltered() {
        val q = JSONObject()
        q.put("options", org.json.JSONArray().apply {
            put(JSONObject().put("label", " "))
            put("ok")
        })
        assertEquals(listOf("ok"), nativeQuestionOptions(q).map { it.label })
    }

    @Test
    fun multiSelectIsDetected() {
        assertTrue(nativeQuestionMultiSelect(question(multiSelect = true)))
        assertFalse(nativeQuestionMultiSelect(question()))
    }

    @Test
    fun otherIsAllowedWhenRequestedOrWhenNoOptions() {
        assertTrue(nativeQuestionOtherAllowed(question(options = emptyList(), isOther = true)))
        assertTrue(nativeQuestionOtherAllowed(question(options = emptyList())))
        assertFalse(nativeQuestionOtherAllowed(question(options = listOf("a"))))
        assertTrue(nativeQuestionOtherAllowed(question(options = listOf("a"), isOther = true)))
    }

    @Test
    fun toggleSwapsSingleSelect() {
        assertEquals("b", nativeToggleOption("a", "b", multi = false))
    }

    @Test
    fun toggleMultiSelectAddsAndRemoves() {
        assertEquals("a,b", nativeToggleOption("a", "b", multi = true))
        assertEquals("a", nativeToggleOption("a,b", "b", multi = true))
        assertEquals("", nativeToggleOption("b", "b", multi = true))
    }

    @Test
    fun otherPartSeparatesFreeTextFromOptions() {
        val labels = setOf("a", "b")
        assertEquals("custom", nativeOtherPart("a,custom", labels))
        assertEquals("", nativeOtherPart("a,b", labels))
    }

    @Test
    fun applyOtherMergesFreeTextKeepingOptions() {
        val labels = setOf("a", "b")
        assertEquals("a,custom", nativeApplyOtherValue("a", labels, "custom"))
        assertEquals("a,custom", nativeApplyOtherValue("a,old", labels, "custom"))
        assertEquals("a", nativeApplyOtherValue("a", labels, ""))
        assertEquals("custom", nativeApplyOtherValue("", labels, "custom"))
    }

    @Test
    fun completionRequiresValueUnlessOptional() {
        assertFalse(nativeQuestionComplete(question(), ""))
        assertTrue(nativeQuestionComplete(question(), "x"))
        assertTrue(nativeQuestionComplete(question(required = false), ""))
    }
}
