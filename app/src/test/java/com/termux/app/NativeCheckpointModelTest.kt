package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeCheckpointModelTest {
    @Test
    fun derivesCompletedAndFailedTurnsFromHistory() {
        val messages = listOf(
            NativeChatMessage(id = "u1", role = NativeChatRole.USER, content = "first"),
            NativeChatMessage(role = NativeChatRole.ASSISTANT, content = "done"),
            NativeChatMessage(id = "u2", role = NativeChatRole.USER, content = "second"),
            NativeChatMessage(role = NativeChatRole.ERROR, content = "failed"),
        )
        val checkpoints = NativeCheckpointModel.build(messages, NativeTurnPhase.FAILED)
        assertEquals(listOf("completed", "failed"), checkpoints.map { it.status })
        assertEquals("u2", checkpoints.last().messageId)
    }

    @Test
    fun marksLatestTurnRunningOrInterrupted() {
        val messages = listOf(NativeChatMessage(id = "u1", role = NativeChatRole.USER, content = "work"))
        assertEquals("running", NativeCheckpointModel.build(messages, NativeTurnPhase.TOOL_RUNNING).single().status)
        assertEquals("interrupted", NativeCheckpointModel.build(messages, NativeTurnPhase.COMPLETED).single().status)
    }
}
