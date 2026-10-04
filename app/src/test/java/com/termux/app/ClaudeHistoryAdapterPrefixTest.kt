package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The CLI transcript stores prompts *with* the bridge-injected prefixes (goal / plan mode), so a
 * replayed user message used to show a header the user never typed, and the drawer title inherited
 * it. These cover the stripping the history adapter now applies.
 */
class ClaudeHistoryAdapterPrefixTest {

    private val goalPrefix = "【当前目标】End every reply with token GOAL_ACK\n请围绕这个目标继续工作。\n\n"
    private val planPrefix =
        "请先制定实施计划：只进行分析与调研，不要修改、创建或删除任何文件，完成后调用 ExitPlanMode 提交计划供审批。\n\n"

    @Test
    fun stripsTheGoalPrefix() {
        assertEquals("Say ok in one word.", stripInjectedPromptPrefixes(goalPrefix + "Say ok in one word."))
    }

    @Test
    fun stripsPlanModePrefix() {
        assertEquals("Give me a plan.", stripInjectedPromptPrefixes(planPrefix + "Give me a plan."))
    }

    @Test
    fun stripsPlanModeWrappingAGoal() {
        // plan mode prepends its own prefix in front of the goal prefix, so the order is plan → goal.
        val text = planPrefix + goalPrefix + "Do the thing."
        assertEquals("Do the thing.", stripInjectedPromptPrefixes(text))
    }

    @Test
    fun leavesAnOrdinaryMessageUntouched() {
        val text = "Read probe.txt and summarise it."
        assertEquals(text, stripInjectedPromptPrefixes(text))
    }

    @Test
    fun keepsAMarkerThatIsNotActuallyAPrefix() {
        // A user may legitimately open a message with the same words. Without the blank-line
        // separator this is not a bridge prefix, so it must survive verbatim.
        val text = "【当前目标】这个词是什么意思？"
        assertEquals(text, stripInjectedPromptPrefixes(text))
    }

    @Test
    fun keepsAMarkerMentionedMidMessage() {
        val text = "请解释 【当前目标】\n\n 这个标记的作用。"
        assertEquals(text, stripInjectedPromptPrefixes(text))
    }
}
