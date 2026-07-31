package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeLiveAssistantStateTest {
    @Test
    fun tokenBatchesDoNotReplaceLazyListMessage() {
        val state = NativeChatState()
        state.addUser("prompt")
        state.appendAssistant("hello")
        val message = state.messages.last()

        state.appendAssistant(" world")

        assertSame(message, state.messages.last())
        assertEquals("", state.messages.last().content)
        assertEquals("hello world", state.liveAssistantText)
        assertEquals("hello world", state.liveAssistantSnapshot.tail)
        assertEquals(11, state.liveAssistantSnapshot.sourceChars)
        assertTrue(state.messages.last().streaming)
    }

    @Test
    fun completionSealsBufferedTextIntoHistoryMessage() {
        val state = NativeChatState()
        state.addUser("prompt")
        state.appendAssistant("hello")
        state.appendAssistant(" world")

        state.completeAssistantItem("hello world")

        assertEquals("hello world", state.messages.last().content)
        assertEquals("", state.liveAssistantText)
        assertEquals("", state.liveAssistantMessageId)
        assertEquals(false, state.messages.last().streaming)
    }

    @Test
    fun coalescedCompletionUsesWholeAnswerRevealWhenNoLiveFrameWasPresented() {
        val state = NativeChatState()
        state.addUser("prompt")
        state.appendAssistant("answer")

        state.completeAssistantItem("answer")

        assertTrue(state.messages.last().finalOnlyReveal)
    }

    @Test
    fun presentedStreamDoesNotReplayWholeAnswerRevealAtCompletion() {
        val state = NativeChatState()
        state.addUser("prompt")
        state.appendAssistant("answer")
        val messageId = state.messages.last().id
        state.markLiveAssistantPresented(messageId, state.liveAssistantSnapshot.sourceChars)

        state.completeAssistantItem("answer")

        assertEquals(false, state.messages.last().finalOnlyReveal)
    }

    @Test
    fun turnCompletionPreservesBufferedTextWhenProviderOmitsItemComplete() {
        val state = NativeChatState()
        state.addUser("prompt")
        state.appendAssistant("partial answer")

        state.completeTurn()

        val assistant = state.messages.last { it.role == NativeChatRole.ASSISTANT }
        assertEquals("partial answer", assistant.content)
        assertEquals(false, assistant.streaming)
    }

    @Test
    fun turnCompletionAttachesReportedUsageToFinalAssistantMessage() {
        val state = NativeChatState()
        state.addUser("prompt")
        state.appendAssistant("answer")
        state.updateTokenUsage("""{"last":{"inputTokens":20,"outputTokens":8,"totalTokens":28}}""")

        state.completeTurn()

        val usage = state.messages.last { it.role == NativeChatRole.ASSISTANT }.usage!!
        assertEquals(20, usage.inputTokens)
        assertEquals(8, usage.outputTokens)
        assertEquals(28, usage.totalTokens)
        assertEquals(false, usage.estimated)
    }

    @Test
    fun turnCompletionEstimatesUsageWhenBackendDoesNotReportIt() {
        val state = NativeChatState()
        state.addUser("prompt")
        state.appendAssistant("a".repeat(40))

        state.completeTurn()

        val usage = state.messages.last { it.role == NativeChatRole.ASSISTANT }.usage!!
        assertEquals(10, usage.outputTokens)
        assertTrue(usage.estimated)
    }

    @Test
    fun recoverableNoticeDoesNotMarkTheTurnFailed() {
        val state = NativeChatState()
        state.phase = NativeTurnPhase.WAITING
        state.connectionLabel = "正在恢复对话…"

        state.addNotice("Conversation history loading timed out")

        assertEquals(NativeTurnPhase.WAITING, state.phase)
        assertEquals("正在恢复对话…", state.connectionLabel)
        assertEquals(NativeChatRole.ACTIVITY, state.messages.last().role)
        assertEquals("NOTICE|Conversation history loading timed out", state.messages.last().content)
    }

    @Test
    fun protocolTagsAreRemovedWithThePrecompiledStreamMatcher() {
        val state = NativeChatState()
        state.addUser("prompt")

        state.appendAssistant("<final>Hello</final><plan> world</plan><proposed_plan mode=\"x\">!</proposed_plan>")

        assertEquals("Hello world!", state.liveAssistantText)
    }

}
