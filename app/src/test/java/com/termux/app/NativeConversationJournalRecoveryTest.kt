package com.termux.app

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativeConversationJournalRecoveryTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun userPromptIsAlreadyDurableBeforeBackendSend() {
        val threadId = "prompt-${UUID.randomUUID()}"
        val user = NativeChatMessage(
            id = "user-1",
            role = NativeChatRole.USER,
            content = "persist me before turn/start",
        )

        NativeConversationJournal.appendUser(context, threadId, user)
        val recovered = NativeConversationJournal.recover(
            context,
            threadId,
            NativeHistorySnapshot(emptyList(), estimatedChars = 0),
        )

        assertEquals(listOf(user), recovered.messages)
    }

    @Test
    fun recoveryWaitsForQueuedAssistantWrites() {
        val threadId = "assistant-${UUID.randomUUID()}"
        val assistants = (0 until 500).map { index ->
            NativeChatMessage(
                id = "assistant-$index",
                role = NativeChatRole.ASSISTANT,
                content = "answer-$index-${"x".repeat(64)}",
            )
        }

        NativeConversationJournal.appendAssistant(context, threadId, assistants)
        val recovered = NativeConversationJournal.recover(
            context,
            threadId,
            NativeHistorySnapshot(emptyList(), estimatedChars = 0),
        )

        assertEquals(assistants.size, recovered.messages.size)
        assertTrue(recovered.messages.last().content.startsWith("answer-499-"))
    }
}
