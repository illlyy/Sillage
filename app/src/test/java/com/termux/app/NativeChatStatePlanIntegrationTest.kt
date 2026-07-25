package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeChatStatePlanIntegrationTest {
    @Test
    fun splitPlanTagBeforeVisibleBodyNeverLeaksIntoAssistantMessage() {
        val state = NativeChatState()
        state.currentThreadId = "thread"
        state.addUser("make a plan")
        state.appendAssistant("<pro")
        state.appendAssistant("posed_plan>step one</proposed_plan>")
        state.completeAssistantItem("")

        val assistants = state.messages.filter { it.role == NativeChatRole.ASSISTANT }
        val plans = state.messages.filter { it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) }
        assertTrue(assistants.isEmpty())
        assertEquals(1, plans.size)
        assertEquals("step one", decodeNativeProposedPlan(plans.single().content))
        assertFalse(state.messages.any { it.content.contains("<pro") || it.content.contains("</proposed_plan>") })
    }

    @Test
    fun normalizedReplayOwnsLiveReasoningCommandsAndAssistantText() {
        val state = NativeChatState()
        state.currentThreadId = "thread"
        state.addUser("work")
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.ReasoningDelta("thread", "turn", "reasoning", "checking", sequence = 1),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.CommandStarted(
                "thread", "turn", "command", command = "echo ok",
                payload = "{\"id\":\"command\",\"command\":\"echo ok\"}", sequence = 2,
            ),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.CommandOutput("thread", "turn", "command", "ok\n", sequence = 3),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.CommandCompleted(
                "thread", "turn", "command", command = "echo ok",
                payload = "{\"id\":\"command\",\"command\":\"echo ok\",\"status\":\"completed\"}", sequence = 4,
            ),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn", "answer", "Done", sequence = 5),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantCompleted("thread", "turn", "answer", "Done", sequence = 6),
        )

        assertEquals("checking", state.activityGroups.single().reasoning)
        assertEquals(1, state.activityGroups.single().commandCount)
        assertEquals("Done", state.messages.single { it.role == NativeChatRole.ASSISTANT }.content)
    }

    @Test
    fun legacyLiveFieldsAreProjectedIntoTheUnifiedActivityModel() {
        val state = NativeChatState()
        state.currentThreadId = "thread"
        state.currentTurnId = "turn"
        state.phase = NativeTurnPhase.REASONING
        state.replaceReasoning("checking")
        state.startCommand("""{"id":"command","command":"echo ok"}""", "command")
        state.appendCommandOutput("ok\n", "command")

        val group = state.legacyLiveActivityGroup()

        assertEquals("live-fallback:thread:turn", group?.key)
        assertEquals("checking", group?.reasoning)
        assertEquals(1, group?.commandCount)
        assertEquals("ok\n", group?.commands?.single()?.outputPreview)
        assertTrue(group?.running == true)
    }

    @Test
    fun firstVisibleAssistantBodySealsExplorationBeforeTheAnswer() {
        val state = NativeChatState()
        state.currentThreadId = "thread"
        state.addUser("work")
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.ReasoningDelta("thread", "turn", "reasoning", "checking", sequence = 1),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.CommandStarted(
                "thread", "turn", "command", command = "echo ok",
                payload = "{\"id\":\"command\",\"command\":\"echo ok\"}", sequence = 2,
            ),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn", "answer", "Done", sequence = 3),
        )

        val processIndex = state.messages.indexOfFirst { it.content.startsWith("PROCESS2|") }
        val answerIndex = state.messages.indexOfFirst { it.role == NativeChatRole.ASSISTANT }
        assertTrue(processIndex >= 0)
        assertTrue(processIndex < answerIndex)
        assertEquals("", state.reasoningText)
        assertEquals("", state.liveCommandJson)
        assertFalse(state.activityGroups.single().running)
    }

    @Test
    fun dedicatedPlanDeltaWithoutStartedEventStillClosesExploration() {
        val state = NativeChatState()
        state.currentThreadId = "thread"
        state.addUser("plan")
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.ReasoningDelta("thread", "turn", delta = "thinking", sequence = 1),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.PlanDelta("thread", "turn", "plan-item", delta = "step", sequence = 2),
        )

        val processIndex = state.messages.indexOfFirst { it.content.startsWith("PROCESS2|") }
        val planIndex = state.messages.indexOfFirst { it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) }
        assertTrue(processIndex >= 0)
        assertTrue(processIndex < planIndex)
        assertEquals("", state.reasoningText)
    }

    @Test
    fun normalizedAuthoritativeCompletionWorksWithoutPriorDelta() {
        val state = NativeChatState()
        state.currentThreadId = "thread"
        state.addUser("answer")
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantCompleted(
                threadId = "thread",
                turnId = "turn",
                itemId = "answer",
                text = "authoritative text",
                finalAnswer = true,
                sequence = 1,
            ),
        )
        assertEquals("authoritative text", state.messages.single { it.role == NativeChatRole.ASSISTANT }.content)
    }

    @Test
    fun duplicateCompletionBarrierDoesNotAppendSecondAssistantBubble() {
        val state = NativeChatState()
        state.currentThreadId = "thread"
        state.addUser("answer")
        state.completeAssistantItem("same answer", "item-1")
        // Retained/legacy bridges may deliver the same completion again after the normalized
        // item lifecycle; it must update no structure in the transcript.
        state.appendAssistantFinal("same answer", "item-1")
        state.completeAssistantItem("same answer")

        val assistants = state.messages.filter { it.role == NativeChatRole.ASSISTANT }
        assertEquals(1, assistants.size)
        assertEquals("same answer", assistants.single().content)
    }

    @Test
    fun reasoningCompletionDoesNotSealBeforeTheFirstCommand() {
        val state = NativeChatState()
        state.currentThreadId = "thread"
        state.addUser("work")
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.ReasoningDelta("thread", "turn", delta = "checking", sequence = 1),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.ReasoningCompleted("thread", "turn", text = "checking", sequence = 2),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.CommandStarted(
                "thread", "turn", itemId = "command", command = "echo ok",
                payload = "{\"id\":\"command\",\"command\":\"echo ok\"}", sequence = 3,
            ),
        )

        assertEquals(1, state.activityGroups.size)
        assertTrue(state.messages.none { it.content.startsWith("PROCESS2|") })
        assertEquals(1, state.activityGroups.single().commandCount)
    }

    @Test
    fun repeatedTaggedPlansInDifferentTurnsKeepSeparateMessages() {
        val state = NativeChatState()
        state.currentThreadId = "thread"
        state.addUser("first")
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer-1", "<plan>same</plan>", sequence = 1),
        )
        state.completeAssistantItem("", "answer-1")

        state.addUser("second")
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-2", "answer-2", "<plan>same</plan>", sequence = 2),
        )
        state.completeAssistantItem("", "answer-2")

        val plans = state.messages.filter { it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) }
        assertEquals(2, plans.size)
        assertEquals(2, plans.map { it.id }.toSet().size)
    }
}
