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
    fun turnCompletionPreservesBufferedTextWhenProviderOmitsItemComplete() {
        val state = NativeChatState()
        state.addUser("prompt")
        state.appendAssistant("partial answer")

        state.completeTurn()

        val assistant = state.messages.last { it.role == NativeChatRole.ASSISTANT }
        assertEquals("partial answer", assistant.content)
        assertEquals(false, assistant.streaming)
    }
}
