package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeConversationJournalTest {
    @Test
    fun emptyBackendHistoryIsFullyRecoveredFromJournal() {
        val local = listOf(
            message("u1", NativeChatRole.USER, "hello"),
            message("a1", NativeChatRole.ASSISTANT, "hi"),
        )

        assertEquals(local, NativeConversationJournal.mergeTail(emptyList(), local))
    }

    @Test
    fun backendPrefixGetsTheMissingJournalTail() {
        val server = listOf(
            message("u1", NativeChatRole.USER, "hello"),
            message("a1", NativeChatRole.ASSISTANT, "hi"),
        )
        val local = server + message("u2", NativeChatRole.USER, "continue")

        assertEquals(local, NativeConversationJournal.mergeTail(server, local))
    }

    @Test
    fun longBackendPrefixStillGetsTheNewestJournalTail() {
        val server = (0 until 100).map { index ->
            message("m$index", if (index % 2 == 0) NativeChatRole.USER else NativeChatRole.ASSISTANT, "message $index")
        }
        val local = server + message("tail", NativeChatRole.USER, "new tail")

        assertEquals(local, NativeConversationJournal.mergeTail(server, local))
    }

    @Test
    fun unrelatedBackendHistoryWinsWithoutInjectingDuplicates() {
        val server = listOf(message("server", NativeChatRole.USER, "server prompt"))
        val local = listOf(message("local", NativeChatRole.USER, "different prompt"))

        assertEquals(server, NativeConversationJournal.mergeTail(server, local))
    }

    private fun message(id: String, role: NativeChatRole, content: String) = NativeChatMessage(
        id = id,
        role = role,
        content = content,
        streaming = false,
    )
}
