package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRetryStateTest {
    @Test
    fun repeatedProviderRetriesUpdateOneStableCard() {
        val state = NativeChatState()
        state.addUser("finish the goal")

        state.updateRetryStatus("network unavailable", automaticGoal = true, terminal = false)
        val firstId = state.messages.last().id
        state.updateRetryStatus("network unavailable", automaticGoal = true, terminal = false)

        assertEquals(2, state.retryStatusAttempts())
        assertEquals(2, state.messages.size)
        assertEquals(firstId, state.messages.last().id)
        assertTrue(state.messages.last().content.contains("第 2 次"))
    }

    @Test
    fun goalRetryKeepsCardAndReplacesFailedAttemptOutput() {
        val state = NativeChatState()
        state.addUser("finish the goal")
        state.appendAssistant("partial answer")
        state.completeAssistantItem("partial answer")
        state.updateRetryStatus("retry limit reached", automaticGoal = true, terminal = true)
        val retryId = state.messages.last().id

        state.prepareForRetry(preserveRetryStatus = true)

        assertEquals(listOf(NativeChatRole.USER, NativeChatRole.ERROR), state.messages.map { it.role })
        assertEquals(retryId, state.messages.last().id)
    }

    @Test
    fun recoveredRetryCardBecomesOneNotice() {
        val state = NativeChatState()
        state.addUser("finish the goal")
        state.updateRetryStatus("temporary error", automaticGoal = true, terminal = false)
        state.updateRetryStatus("temporary error", automaticGoal = true, terminal = false)

        state.completeRetryStatus()

        assertEquals(2, state.messages.size)
        assertEquals(NativeChatRole.ACTIVITY, state.messages.last().role)
        assertTrue(state.messages.last().content.contains("共 2 次"))
        assertEquals(false, state.hasRetryStatus())
    }
}
