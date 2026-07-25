package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePlanStreamParserTest {
    @Test
    fun tagCanBeSplitAcrossArbitraryDeltas() {
        val parser = NativePlanStreamParser()
        parser.append("Intro<pro")
        parser.append("posed_plan># Plan")
        val result = parser.append("\n- step</proposed_plan>Done")
        assertEquals("IntroDone", result.assistantText)
        assertEquals("# Plan\n- step", result.planText)
    }

    @Test
    fun dedicatedPlanWinsAndDoesNotDuplicateFallback() {
        val parser = NativePlanStreamParser()
        parser.append("<plan>same plan</plan>", itemId = "p")
        parser.append("same plan", itemId = "p", dedicated = true)
        val result = parser.complete(itemId = "p", dedicated = true)
        assertEquals("same plan", result.planText)
        assertTrue(result.dedicatedPlan)
        assertTrue(result.planOnly)
    }

    @Test
    fun mixedBodyAndPlanAreSeparatedWithoutEmptyBodyBubble() {
        val parser = NativePlanStreamParser()
        val mixed = parser.complete("Before<propose_plan>Plan</propose_plan>After")
        assertEquals("BeforeAfter", mixed.assistantText)
        assertEquals("Plan", mixed.planText)
        assertFalse(mixed.planOnly)

        parser.reset()
        val only = parser.complete("<plan>Only</plan>")
        assertEquals("", only.assistantText)
        assertTrue(only.planOnly)
    }

    @Test
    fun unclosedMarkupIsRecoveredAndNaturalPlanLanguageStaysBody() {
        val parser = NativePlanStreamParser()
        val result = parser.complete("正文<proposed_plan>未闭合计划")
        assertEquals("正文", result.assistantText)
        assertEquals("未闭合计划", result.planText)
        assertFalse(result.assistantText.contains("<proposed_plan>"))

        parser.reset()
        assertEquals("计划：今天先写测试", parser.complete("计划：今天先写测试").assistantText)
    }

    @Test
    fun orderedPartsCanBeReusedByHistoryWithoutLeakingTags() {
        val parts = NativePlanStreamParser.splitComplete("before<plan>step</plan>after")
        assertEquals(listOf("assistant", "plan", "assistant"), parts.map { it.role })
        assertEquals(listOf("before", "step", "after"), parts.map { it.text })
    }

    @Test
    fun lifecycleStartResetsWhenNextItemBeginsBeforePreviousCompletion() {
        val parser = NativePlanStreamParser()
        parser.start("plan-1", dedicated = true)
        parser.append("first", "plan-1", dedicated = true)
        parser.start("plan-2", dedicated = true)
        val result = parser.append("second", "plan-2", dedicated = true)
        assertEquals("second", result.planText)
    }

    @Test
    fun dedicatedStartsWithoutIdsStillCreateSeparateItems() {
        val parser = NativePlanStreamParser()
        parser.start(dedicated = true)
        parser.append("first", dedicated = true)
        parser.complete(dedicated = true)
        parser.start(dedicated = true)
        val result = parser.append("second", dedicated = true)
        assertEquals("second", result.planText)
    }
}
