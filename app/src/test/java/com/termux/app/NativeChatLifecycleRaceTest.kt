package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeChatLifecycleRaceTest {
    @Test
    fun auxiliaryCompactionTurnCannotReplaceOrTerminatePrimaryAnswerTurn() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("hello")
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnStarted("thread", "turn-user", sequence = 1))
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-user", "answer", "prefix", sequence = 2),
        )

        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.CompactionStarted("thread", "turn-compact", "compact", sequence = 3),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.CompactionCompleted("thread", "turn-compact", "compact", sequence = 4),
        )
        // A retained/older bridge may still leak the auxiliary turn completion. The state layer
        // must reject it instead of closing the primary answer.
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.TurnCompleted("thread", "turn-compact", sequence = 5),
        )
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-user", "answer", " tail", sequence = 6),
        )

        assertEquals("turn-user", state.currentTurnId)
        // Compaction is a visible activity boundary: the pre-compaction text is sealed before the
        // divider, while the same primary turn continues streaming into a new assistant segment.
        assertEquals(
            "prefix",
            state.messages.single { it.role == NativeChatRole.ASSISTANT && !it.streaming }.content,
        )
        assertEquals(" tail", state.liveAssistantText)
        assertEquals(NativeTurnPhase.ANSWERING, state.phase)
        assertTrue(state.busy)
        assertTrue(state.compactionItems.single().isTerminal)
    }

    @Test
    fun finalAssistantItemSealsBubbleButOnlyTurnCompletedEndsTheTurn() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("hello")
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnStarted("thread", "turn-1", sequence = 1))
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantCompleted(
                "thread", "turn-1", "answer", "done", finalAnswer = true, sequence = 2,
            ),
        )

        assertEquals("done", state.messages.single { it.role == NativeChatRole.ASSISTANT }.content)
        assertTrue(state.busy)
        assertFalse(state.phase == NativeTurnPhase.COMPLETED)

        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnCompleted("thread", "turn-1", sequence = 3))

        assertEquals(NativeTurnPhase.COMPLETED, state.phase)
        assertFalse(state.busy)
    }

    @Test
    fun failedTurnCompletionKeepsTerminalFailureState() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("hello")
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnStarted("thread", "turn-1", sequence = 1))
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer", "partial", sequence = 2),
        )

        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.TurnCompleted("thread", "turn-1", failed = true, sequence = 3),
        )

        assertEquals(NativeTurnPhase.FAILED, state.phase)
        assertEquals("连接异常", state.connectionLabel)
        assertFalse(state.busy)
    }

    @Test
    fun lateFailedCompletionUpgradesEarlierSuccessfulCompletion() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("hello")
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnStarted("thread", "turn-1", sequence = 1))
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnCompleted("thread", "turn-1", sequence = 2))
        assertEquals(NativeTurnPhase.COMPLETED, state.phase)

        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.TurnCompleted("thread", "turn-1", failed = true, sequence = 3),
        )

        assertEquals(NativeTurnPhase.FAILED, state.phase)
        assertEquals("连接异常", state.connectionLabel)
    }

    @Test
    fun serverStartedContinuationResetsCompletedBarrierForTheNewTurn() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("continue until done")
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnStarted("thread", "turn-1", sequence = 1))
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer-1", "first", sequence = 2),
        )
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnCompleted("thread", "turn-1", sequence = 3))

        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnStarted("thread", "turn-2", sequence = 4))
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-2", "answer-2", "second", sequence = 5),
        )

        assertEquals("turn-2", state.currentTurnId)
        assertEquals(NativeTurnPhase.ANSWERING, state.phase)
        assertTrue(state.busy)
        assertTrue(state.messages.any { it.role == NativeChatRole.ASSISTANT && it.content == "first" })
        assertEquals("second", state.liveAssistantText)
    }

    @Test
    fun staleOldTurnItemCannotClaimIdentityAfterNewUserMessage() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("first")
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnStarted("thread", "turn-1", sequence = 1))
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer-1", "first answer", sequence = 2),
        )
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnCompleted("thread", "turn-1", sequence = 3))

        state.addUser("second")
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnStarted("thread", "turn-1", sequence = 4))
        assertEquals("", state.currentTurnId)
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.ReasoningDelta("thread", turnId = null, delta = "new preface", sequence = 5),
        )
        assertEquals("", state.currentTurnId)
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer-1", " stale tail", sequence = 6),
        )
        state.acceptNormalizedProtocolEvent(NativeProtocolEvent.TurnStarted("thread", "turn-2", sequence = 7))
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-2", "answer-2", "second answer", sequence = 8),
        )

        assertEquals("turn-2", state.currentTurnId)
        assertTrue(state.messages.none { it.content.contains("stale tail") })
        assertEquals("second answer", state.liveAssistantText)
    }

    @Test
    fun terminalAndLifecycleDrainOrderPublishesProtocolBeforeBufferedCompatibilityStreams() {
        val order = mutableListOf<String>()

        NativePendingUiEventDrain.run(
            protocol = { order += "protocol" },
            reasoning = { order += "reasoning" },
            assistant = { order += "assistant" },
            plan = { order += "plan" },
            command = { order += "command" },
        )

        assertEquals(listOf("protocol", "reasoning", "assistant", "plan", "command"), order)
    }

    @Test
    fun staleCompletionIsRejectedBeforeItCanDrainCompatibilityBuffers() {
        var drained = false

        val accepted = NativeTurnCompletionDrainGate.drainIfOwned(
            completionThreadId = "thread",
            currentThreadId = "thread",
            completionTurnId = "turn-old",
            completionEpoch = null,
            currentEpoch = 2L,
            currentTurnActive = true,
            acceptTurn = { it == "turn-current" },
            drain = { drained = true },
        )

        assertFalse(accepted)
        assertFalse(drained)
        assertFalse(
            NativeTurnCompletionDrainGate.drainIfOwned(
                completionThreadId = "thread",
                currentThreadId = null,
                completionTurnId = "turn-current",
                completionEpoch = null,
                currentEpoch = 2L,
                currentTurnActive = true,
                acceptTurn = { true },
                drain = { drained = true },
            ),
        )
        assertFalse(drained)
        assertTrue(
            NativeTurnCompletionDrainGate.drainIfOwned(
                completionThreadId = "thread",
                currentThreadId = "thread",
                completionTurnId = "",
                completionEpoch = 3L,
                currentEpoch = 3L,
                currentTurnActive = true,
                acceptTurn = { true },
                drain = { drained = true },
            ),
        )
        assertTrue(drained)
    }

    @Test
    fun legacyCompatibilityBuffersStayOwnedByTheirThreadTurnAndEpoch() {
        val gate = NativeLegacyPendingStreamScopeGate()
        assertTrue(gate.capture("thread", ""))
        assertTrue(gate.capture("thread", "turn-current"))
        assertTrue(gate.canDrain("thread", "turn-current"))
        assertFalse(gate.canDrain("thread", "turn-old"))
        assertFalse(gate.canDrain("other-thread", "turn-current"))

        gate.advanceEpoch()
        assertTrue(gate.capture("thread", "turn-next"))
        assertFalse(gate.canDrain("thread", "turn-current"))
        assertTrue(gate.canDrain("thread", "turn-next"))
    }

    @Test
    fun unidentifiedCompletionCannotDrainAnActiveNewLocalEpoch() {
        var drained = false

        assertFalse(
            NativeTurnCompletionDrainGate.drainIfOwned(
                completionThreadId = "thread",
                currentThreadId = "thread",
                completionTurnId = "",
                completionEpoch = null,
                currentEpoch = 3L,
                currentTurnActive = true,
                acceptTurn = { true },
                drain = { drained = true },
            ),
        )
        assertFalse(drained)
    }

    @Test
    fun chatStateRejectsUnidentifiedCompletionWhileNewTurnIsWaiting() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("new turn")

        assertEquals(NativeTurnPhase.WAITING, state.phase)
        assertFalse(state.shouldAcceptProtocolTurnCompletion(""))
        assertTrue(state.shouldAcceptProtocolTurnCompletion("", localEpochMatches = true))

        state.completeTurn()
        assertTrue(state.shouldAcceptProtocolTurnCompletion(""))
    }

    @Test
    fun continuationHintRouteLeavePreservesOldThreadUntilExplicitResync() {
        val emissions = mutableListOf<Pair<String, Boolean>>()
        val router = NativeContinuationHintRouter()

        router.sync("thread-old", true) { threadId, pending -> emissions += threadId to pending }
        router.leave("thread-old")
        router.sync("thread-new", false) { threadId, pending -> emissions += threadId to pending }

        assertEquals(listOf("thread-old" to true, "thread-new" to false), emissions)
        router.sync("thread-old", false) { threadId, pending -> emissions += threadId to pending }
        assertEquals(
            listOf("thread-old" to true, "thread-new" to false, "thread-old" to false),
            emissions,
        )
    }

    @Test
    fun leavingUnknownRetainedContinuationDoesNotInventFalse() {
        val emissions = mutableListOf<Pair<String, Boolean>>()
        val router = NativeContinuationHintRouter()

        val detachedThread = router.leave("retained-thread")

        assertEquals("retained-thread", detachedThread)
        assertTrue(emissions.isEmpty())
    }

    @Test
    fun lateSameTurnDeltaExtendsVisibleAnswerButCannotResurrectFailedTurn() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("hello")
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer", "prefix", sequence = 1),
        )
        state.addError("backend disconnected")

        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer", " tail", sequence = 2),
        )

        assertEquals("prefix tail", state.liveAssistantText)
        assertEquals(NativeTurnPhase.FAILED, state.phase)
        assertEquals("连接异常", state.connectionLabel)
        assertFalse(state.busy)
        assertEquals(1, state.messages.count { it.role == NativeChatRole.ASSISTANT })
        assertTrue(state.messages.indexOfFirst { it.role == NativeChatRole.ASSISTANT } <
            state.messages.indexOfFirst { it.role == NativeChatRole.ERROR })
    }

    @Test
    fun queuedDeltaDrainedAfterErrorIsKeptWhileDifferentTurnIsRejected() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("hello")
        state.currentTurnId = "turn-1"
        val queue = NativeOrderedProtocolEventQueue()
        queue.offer(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer", "kept", sequence = 1),
        )
        state.addError("backend disconnected")

        queue.drain().forEach(state::acceptNormalizedProtocolEvent)
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-2", "answer-2", "stale", sequence = 2),
        )

        assertEquals("kept", state.liveAssistantText)
        assertFalse(state.liveAssistantText.contains("stale"))
        assertEquals("turn-1", state.currentTurnId)
        assertEquals(NativeTurnPhase.FAILED, state.phase)
    }

    @Test
    fun explicitNewTurnResetsFailureBarrier() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("first")
        state.currentTurnId = "turn-1"
        state.addError("failed")

        state.addUser("second")
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-2", "answer-2", "accepted", sequence = 2),
        )

        assertEquals("turn-2", state.currentTurnId)
        assertEquals("accepted", state.liveAssistantText)
        assertEquals(NativeTurnPhase.ANSWERING, state.phase)
        assertTrue(state.busy)
    }

    @Test
    fun lateDeltaAfterCompletionMergesIntoTerminalBubbleWithoutMakingTurnBusy() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("hello")
        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer", "Hello", sequence = 1),
        )
        state.completeTurn()

        state.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer", " world", sequence = 2),
        )

        val assistants = state.messages.filter { it.role == NativeChatRole.ASSISTANT }
        assertEquals(1, assistants.size)
        assertEquals("Hello world", assistants.single().content)
        assertEquals(NativeTurnPhase.COMPLETED, state.phase)
        assertFalse(state.busy)
    }

    @Test
    fun legacyPlanAndItemCallbacksCannotResurrectFailedTurn() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("plan")
        state.currentTurnId = "turn-1"
        state.addError("backend disconnected")

        state.appendProposedPlanDelta("{\"itemId\":\"plan-1\",\"delta\":\"kept plan\"}")
        state.addActivity("commandExecution")

        assertEquals(NativeTurnPhase.FAILED, state.phase)
        assertFalse(state.busy)
        assertEquals(
            "kept plan",
            decodeNativeProposedPlan(state.messages.single { it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) }.content),
        )
        assertTrue(state.messages.indexOfFirst { it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) } <
            state.messages.indexOfFirst { it.role == NativeChatRole.ERROR })
    }

    @Test
    fun destroyHandoffMaterializesQueuedBodyAndRecreateContinuesSameStreamingMessage() {
        val oldState = NativeChatState().apply { currentThreadId = "thread" }
        oldState.addUser("hello")
        val queue = NativeOrderedProtocolEventQueue()
        queue.offer(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer", "partial", sequence = 1),
        )
        // This is the synchronous protocol drain performed before Activity detach.
        queue.drain().forEach(oldState::acceptNormalizedProtocolEvent)
        val handoff = oldState.lifecycleHandoffSnapshot()
        val assistant = handoff.history.messages.single { it.role == NativeChatRole.ASSISTANT }
        assertEquals("partial", assistant.content)
        assertTrue(assistant.streaming)

        val staleDisk = NativeHistorySnapshot(
            messages = handoff.history.messages.filter { it.role == NativeChatRole.USER },
        )
        val merged = NativeChatLifecycleHandoff.mergeFreshHistory(staleDisk, handoff)
        assertSame(handoff.history, merged)

        val recreated = NativeChatState().apply { currentThreadId = "thread" }
        recreated.applyHistorySnapshot(merged)
        recreated.restoreLifecycleUiState(handoff)
        recreated.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantDelta("thread", "turn-1", "answer", " tail", sequence = 2),
        )
        recreated.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.AssistantCompleted(
                "thread", "turn-1", "answer", "partial tail", finalAnswer = false, sequence = 3,
            ),
        )

        val recreatedAssistants = recreated.messages.filter { it.role == NativeChatRole.ASSISTANT }
        assertEquals(1, recreatedAssistants.size)
        assertEquals("partial tail", recreatedAssistants.single().content)
        assertFalse(recreatedAssistants.single().streaming)
    }

    @Test
    fun semanticallyExtendedFreshProcessReleasesPartialLifecycleProcess() {
        val partialState = NativeChatState().apply { currentThreadId = "thread" }
        partialState.addUser("work")
        partialState.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.ReasoningDelta("thread", "turn-1", delta = "think", sequence = 1),
        )
        val partial = partialState.lifecycleHandoffSnapshot()

        val extendedState = NativeChatState().apply { currentThreadId = "thread" }
        extendedState.addUser("work")
        extendedState.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.ReasoningDelta("thread", "turn-1", delta = "thinking more", sequence = 1),
        )
        val extended = extendedState.lifecycleHandoffSnapshot().history

        assertSame(extended, NativeChatLifecycleHandoff.mergeFreshHistory(extended, partial))
    }

    @Test
    fun recreateRestoresReasoningReducerBeforeTheNextNormalizedTail() {
        val oldState = NativeChatState().apply { currentThreadId = "thread" }
        oldState.addUser("work")
        oldState.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.ReasoningDelta("thread", "turn-1", delta = "think", sequence = 1),
        )
        val handoff = oldState.lifecycleHandoffSnapshot()

        val recreated = NativeChatState().apply { currentThreadId = "thread" }
        recreated.applyHistorySnapshot(handoff.history)
        recreated.restoreLifecycleUiState(handoff)
        assertEquals("think", recreated.reasoningText)
        assertEquals("think", recreated.activityGroups.single().reasoning)
        assertFalse(recreated.messages.any { it.id.startsWith("lifecycle-process:") })

        recreated.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.ReasoningDelta("thread", "turn-1", delta = "ing", sequence = 2),
        )

        assertEquals("thinking", recreated.reasoningText)
        assertEquals("thinking", recreated.activityGroups.single().reasoning)
    }

    @Test
    fun recreateRestoresCommandPrefixBeforeTheNextOutputDelta() {
        val oldState = NativeChatState().apply { currentThreadId = "thread" }
        oldState.addUser("work")
        oldState.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.CommandStarted(
                "thread", "turn-1", "command-1", command = "echo",
                payload = "{\"id\":\"command-1\",\"type\":\"commandExecution\",\"command\":\"echo\",\"status\":\"inProgress\"}",
                sequence = 1,
            ),
        )
        oldState.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.CommandOutput("thread", "turn-1", "command-1", "prefix", sequence = 2),
        )
        val handoff = oldState.lifecycleHandoffSnapshot()

        val recreated = NativeChatState().apply { currentThreadId = "thread" }
        recreated.applyHistorySnapshot(handoff.history)
        recreated.restoreLifecycleUiState(handoff)
        recreated.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.CommandOutput("thread", "turn-1", "command-1", " tail", sequence = 3),
        )

        val command = recreated.activityGroups.single().commands.single()
        assertEquals("echo", command.title)
        assertEquals("prefix tail", command.outputPreview)
    }

    @Test
    fun recreateContinuesDedicatedPlanInsteadOfReplacingItWithTheTail() {
        val oldState = NativeChatState().apply { currentThreadId = "thread" }
        oldState.addUser("plan")
        oldState.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.PlanDelta("thread", "turn-1", "plan-1", "step one", sequence = 1),
        )
        val handoff = oldState.lifecycleHandoffSnapshot()

        val recreated = NativeChatState().apply { currentThreadId = "thread" }
        recreated.applyHistorySnapshot(handoff.history)
        recreated.restoreLifecycleUiState(handoff)
        recreated.acceptNormalizedProtocolEvent(
            NativeProtocolEvent.PlanDelta("thread", "turn-1", "plan-1", " + step two", sequence = 2),
        )

        val plans = recreated.messages.filter { it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) }
        assertEquals(1, plans.size)
        assertEquals("step one + step two", decodeNativeProposedPlan(plans.single().content))
        assertEquals(NativeTurnPhase.ANSWERING, recreated.phase)
    }

    @Test
    fun recreateKeepsSplitFallbackPlanTagParserState() {
        val oldState = NativeChatState().apply { currentThreadId = "thread" }
        oldState.addUser("plan")
        oldState.appendAssistant("<pro")
        val handoff = oldState.lifecycleHandoffSnapshot()
        val staleDisk = NativeHistorySnapshot(handoff.history.messages)
        assertSame(handoff.history, NativeChatLifecycleHandoff.mergeFreshHistory(staleDisk, handoff))

        val recreated = NativeChatState().apply { currentThreadId = "thread" }
        recreated.applyHistorySnapshot(handoff.history)
        recreated.restoreLifecycleUiState(handoff)
        recreated.appendAssistant("posed_plan>step</proposed_plan>")

        assertTrue(recreated.messages.none { it.role == NativeChatRole.ASSISTANT })
        val plan = recreated.messages.single { it.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) }
        assertEquals("step", decodeNativeProposedPlan(plan.content))
    }

    @Test
    fun lifecycleTailIsRestoredOnlyWhenTheSameRuntimeWasRetained() {
        val state = NativeChatState().apply { currentThreadId = "thread" }
        state.addUser("work")
        val handoff = state.lifecycleHandoffSnapshot()

        assertTrue(NativeChatLifecycleHandoffPolicy.canRestore(true, "thread", handoff))
        assertFalse(NativeChatLifecycleHandoffPolicy.canRestore(false, "thread", handoff))
        assertFalse(NativeChatLifecycleHandoffPolicy.canRestore(true, "other-thread", handoff))
    }
}
