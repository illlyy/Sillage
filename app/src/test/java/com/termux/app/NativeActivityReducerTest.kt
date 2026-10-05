package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeActivityReducerTest {
    @Test
    fun reasoningAndManyCommandsStayInOneExplorationBeforeBody() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", "r", "first ", sequence = 1))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", "c1", "rg foo", sequence = 2))
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", "r", "second", sequence = 3))
        reducer.accept(NativeProtocolEvent.CommandCompleted("t", "turn", "c1", "rg foo", sequence = 4))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", "c2", "gradle test", sequence = 5))

        val groups = reducer.groups()
        assertEquals(1, groups.size)
        assertEquals("first second", groups.single().reasoning)
        assertEquals(2, groups.single().commandCount)
        assertEquals(1, groups.single().runningCommandCount)
    }

    @Test
    fun assistantBodyCutsExplorationAndLaterReasoningStartsNewGroup() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "before", sequence = 1))
        reducer.accept(NativeProtocolEvent.AssistantDelta("t", "turn", delta = "body", sequence = 2))
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "after", sequence = 3))

        val groups = reducer.groups()
        assertEquals(2, groups.size)
        assertEquals("before", groups[0].reasoning)
        assertTrue(groups[0].assistantBoundaryAfter)
        assertEquals("after", groups[1].reasoning)
        assertTrue(groups[1].assistantBoundaryBefore)
    }

    @Test
    fun parallelCommandsAreSeparatedByItemIdButRenderedTogether() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", "a", "one", sequence = 1))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", "b", "two", sequence = 2))
        reducer.accept(NativeProtocolEvent.CommandOutput("t", "turn", "b", "out", sequence = 3))
        reducer.accept(NativeProtocolEvent.CommandCompleted("t", "turn", "a", "one", sequence = 4))

        val group = reducer.groups().single()
        assertEquals(listOf("a", "b"), group.commands.mapNotNull { it.itemId })
        assertEquals("out", group.commands.first { it.itemId == "b" }.outputPreview)
    }

    @Test
    fun completedTurnCollapsesGroupAndClosesRunningItems() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", "a", "one", sequence = 1))
        reducer.accept(NativeProtocolEvent.TurnCompleted("t", "turn", sequence = 2))

        val group = reducer.groups().single()
        assertFalse(group.running)
        assertFalse(group.expandedByDefault)
        assertEquals(NativeActivityItemStatus.COMPLETED, group.commands.single().status)
    }

    @Test
    fun emptyAssistantLifecycleDoesNotSplitExploration() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "a", sequence = 1))
        reducer.accept(NativeProtocolEvent.AssistantDelta("t", "turn", itemId = "assistant", delta = "", sequence = 2))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", "cmd", "echo", sequence = 3))
        assertEquals(1, reducer.groups().size)
        assertEquals(1, reducer.groups().single().commandCount)
    }

    @Test
    fun legacyCommandCallbacksWithoutItemIdReuseOneRow() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", command = "echo hello", sequence = 1))
        reducer.accept(NativeProtocolEvent.CommandOutput("t", "turn", delta = "hello\n", sequence = 2))
        reducer.accept(NativeProtocolEvent.CommandCompleted("t", "turn", command = "echo hello", sequence = 3))

        val commands = reducer.groups().single().commands
        assertEquals(1, commands.size)
        assertEquals(NativeActivityItemStatus.COMPLETED, commands.single().status)
        assertEquals("hello\n", commands.single().outputPreview)
    }

    @Test
    fun tokenUsageMetadataDoesNotCreateAnotherExplorationGroup() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "thinking", sequence = 1))
        reducer.accept(NativeProtocolEvent.TokenUsageUpdated("t", "turn", inputTokens = 10, sequence = 2))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", itemId = "c", command = "echo", sequence = 3))
        assertEquals(1, reducer.groups().size)
        assertEquals(1, reducer.groups().single().commandCount)
    }

    @Test
    fun reasoningCompletionBeforeCommandStillSharesExplorationGroup() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "thinking", sequence = 1))
        reducer.accept(NativeProtocolEvent.ReasoningCompleted("t", "turn", text = "thinking", sequence = 2))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", itemId = "cmd", command = "echo", sequence = 3))

        assertEquals(1, reducer.groups().size)
        assertEquals("thinking", reducer.groups().single().reasoning)
        assertEquals(1, reducer.groups().single().commandCount)
    }

    @Test
    fun lifecycleBoundarySeparatesExplorationAroundCompaction() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "before", sequence = 1))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", itemId = "before-cmd", command = "one", sequence = 2))
        reducer.accept(NativeProtocolEvent.CompactionStarted("t", "turn", itemId = "compact", sequence = 3))
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "after", sequence = 4))

        val groups = reducer.groups()
        assertEquals(2, groups.size)
        assertEquals("before", groups[0].reasoning)
        assertEquals("after", groups[1].reasoning)
        assertTrue(groups[0].activityBoundaryAfter)
    }

    @Test
    fun bodyThenCommandCreatesPostBodyExplorationGroup() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "before", sequence = 1))
        reducer.accept(NativeProtocolEvent.AssistantDelta("t", "turn", itemId = "answer", delta = "body", sequence = 2))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", itemId = "after-cmd", command = "two", sequence = 3))

        val groups = reducer.groups()
        assertEquals(2, groups.size)
        assertEquals(1, groups[1].commandCount)
        assertTrue(groups[0].assistantBoundaryAfter)
    }

    @Test
    fun planOnlyAssistantDeltasDoNotCreateAnAssistantBoundary() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "thinking", sequence = 1))
        reducer.accept(NativeProtocolEvent.AssistantDelta("t", "turn", itemId = "answer", delta = "<pro", sequence = 2))
        reducer.accept(NativeProtocolEvent.AssistantDelta("t", "turn", itemId = "answer", delta = "posed_plan>step</proposed_plan>", sequence = 3))
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "still thinking", sequence = 4))

        assertEquals(1, reducer.groups().size)
        assertEquals("thinkingstill thinking", reducer.groups().single().reasoning)
    }

    @Test
    fun lateCompletionFromOlderTurnDoesNotMoveCurrentTurnCursor() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn-1", "old", "one", sequence = 1))
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn-2", delta = "current", sequence = 2))
        reducer.accept(NativeProtocolEvent.CommandCompleted("t", "turn-1", "old", "one", sequence = 3))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn-2", "new", "two", sequence = 4))

        val current = reducer.groups().single { it.turnId == "turn-2" }
        assertEquals("current", current.reasoning)
        assertEquals(listOf("two"), current.commands.map { it.title })
        assertEquals(2, reducer.groups().size)
    }

    @Test
    fun legacyCommandCompletionWithoutTurnIdUsesLatestThreadTurn() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", "cmd", "echo", sequence = 1))
        reducer.accept(NativeProtocolEvent.CommandOutput("t", itemId = "cmd", delta = "ok", sequence = 2))
        reducer.accept(NativeProtocolEvent.CommandCompleted("t", itemId = "cmd", command = "echo", sequence = 3))

        val group = reducer.groups().single()
        assertEquals("turn", group.turnId)
        assertEquals("ok", group.commands.single().outputPreview)
        assertEquals(NativeActivityItemStatus.COMPLETED, group.commands.single().status)
    }

    @Test
    fun missingTurnIdUsesTheStableThreadTurnKeyAcrossTurnChanges() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn-1", delta = "old", sequence = 1))
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn-2", delta = "new", sequence = 2))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", itemId = "late", command = "echo", sequence = 3))

        val current = reducer.groups().single { it.turnId == "turn-2" }
        assertEquals(1, current.commandCount)
        assertEquals("exploration:t:turn-2:0", current.key)
        assertEquals(2, reducer.groups().size)
    }

    @Test
    fun legacyCommandsKeepInsertionOrderWhenTheyHaveNoSequence() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", command = "first"))
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", command = "second"))

        assertEquals(listOf("first", "second"), reducer.groups().single().commands.map { it.title })
    }

    @Test
    fun lateUncorrelatedActivityAfterTurnCompletionDoesNotCreateGhostGroup() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", "cmd", "one", sequence = 1))
        reducer.accept(NativeProtocolEvent.TurnCompleted("t", "turn", sequence = 2))
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "late", sequence = 3))

        assertEquals(1, reducer.groups().size)
        assertEquals("one", reducer.groups().single().commands.single().title)
    }

    @Test
    fun duplicateLegacyReasoningCompletionAfterAssistantDoesNotCreateGhostGroup() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ReasoningDelta("t", "turn", delta = "thinking", sequence = 1))
        reducer.accept(NativeProtocolEvent.AssistantDelta("t", "turn", itemId = "answer", delta = "body", sequence = 2))
        // Retained bridges can replay the complete reasoning summary without an item id or
        // sequence. It must update the closed group, not add a second post-answer capsule.
        reducer.accept(NativeProtocolEvent.ReasoningCompleted("t", "turn", text = "thinking"))

        assertEquals(1, reducer.groups().size)
        assertEquals("thinking", reducer.groups().single().reasoning)
    }

    @Test
    fun lateFailedCompletionUpgradesSealedItems() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.CommandStarted("t", "turn", "c1", "one", sequence = 1))
        reducer.accept(NativeProtocolEvent.TurnCompleted("t", "turn", sequence = 2))
        assertEquals(NativeActivityItemStatus.COMPLETED, reducer.groups().single().commands.single().status)
        // A duplicate completion reporting failure is authoritative: sealed items follow.
        reducer.accept(NativeProtocolEvent.TurnCompleted("t", "turn", failed = true, sequence = 3))
        assertEquals(NativeActivityItemStatus.FAILED, reducer.groups().single().commands.single().status)
    }

    @Test
    fun anonymousSameTypeToolsDoNotOverwriteEachOther() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ToolCompleted("t", "turn", type = "read", title = "a.txt", sequence = 0))
        reducer.accept(NativeProtocolEvent.ToolCompleted("t", "turn", type = "read", title = "b.txt", sequence = 0))
        val tools = reducer.groups().single().items.filter { it.type == NativeActivityItemType.TOOL }
        assertEquals(2, tools.size)
        assertEquals(setOf("a.txt", "b.txt"), tools.map { it.title }.toSet())
    }

    @Test
    fun stoppedSubagentMapsToFailedLikeStateLayer() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.SubagentUpdated("t", "turn", agentThreadId = "agent-1", name = "Task", status = "stopped", sequence = 1))
        val item = reducer.groups().single().items.single { it.type == NativeActivityItemType.SUBAGENT }
        assertEquals(NativeActivityItemStatus.FAILED, item.status)
    }

    @Test
    fun toolStartOpensARunningRowBeforeCompletion() {
        // A tool other than a command used to stay invisible until it finished, so a call that never
        // returned left the screen empty and read as though nothing had been attempted at all.
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ToolStarted("t", "turn", "s1", type = "WebSearch", title = "android 17", sequence = 1))

        val item = reducer.groups().single().items.single()
        assertEquals(NativeActivityItemType.WEB_SEARCH, item.type)
        assertEquals(NativeActivityItemStatus.RUNNING, item.status)
        assertEquals("android 17", item.title)
    }

    @Test
    fun toolCompletionUpdatesTheRowThatStartedInsteadOfAddingOne() {
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ToolStarted("t", "turn", "s1", type = "WebSearch", title = "android 17", sequence = 1))
        reducer.accept(NativeProtocolEvent.ToolCompleted("t", "turn", "s1", type = "WebSearch", title = "android 17", sequence = 2))

        val items = reducer.groups().single().items
        assertEquals(1, items.size)
        assertEquals(NativeActivityItemStatus.COMPLETED, items.single().status)
    }

    @Test
    fun aLateStartDoesNotReopenACompletedRow() {
        // Start and completion travel through the batcher and may arrive out of order.
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ToolCompleted("t", "turn", "s1", type = "WebSearch", title = "q", sequence = 1))
        reducer.accept(NativeProtocolEvent.ToolStarted("t", "turn", "s1", type = "WebSearch", title = "q", sequence = 2))

        val items = reducer.groups().single().items
        assertEquals(1, items.size)
        assertEquals(NativeActivityItemStatus.COMPLETED, items.single().status)
    }

    @Test
    fun underscoredAndPascalCaseTypesMapToTheSameRow() {
        // The live path compared the raw string against a lowercased set, so a snake_case spelling
        // fell through to the generic tool row and rendered as raw JSON.
        val reducer = NativeActivityReducer()
        reducer.accept(NativeProtocolEvent.ToolCompleted("t", "turn", "a", type = "web_search_call", title = "q", sequence = 1))
        reducer.accept(NativeProtocolEvent.ToolCompleted("t", "turn", "b", type = "WebSearch", title = "q", sequence = 2))
        reducer.accept(NativeProtocolEvent.ToolCompleted("t", "turn", "c", type = "file_change", title = "f", sequence = 3))
        reducer.accept(NativeProtocolEvent.ToolCompleted("t", "turn", "d", type = "mcpToolCall", title = "mcp__fs__read", sequence = 4))

        val byId = reducer.groups().single().items.associateBy { it.itemId }
        assertEquals(NativeActivityItemType.WEB_SEARCH, byId["a"]?.type)
        assertEquals(NativeActivityItemType.WEB_SEARCH, byId["b"]?.type)
        assertEquals(NativeActivityItemType.FILE_CHANGE, byId["c"]?.type)
        assertEquals(NativeActivityItemType.MCP, byId["d"]?.type)
    }
}
