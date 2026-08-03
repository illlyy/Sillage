package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeEchoReconciliationTest {

    private fun user(text: String, id: String = nativeEchoId(), images: Int = 0, files: Int = 0) = NativeChatMessage(
        id = id,
        role = NativeChatRole.USER,
        content = text,
        attachments = buildList {
            repeat(images) { add(NativeAttachment("i$it", "p$it", image = true)) }
            repeat(files) { add(NativeAttachment("f$it", "p$it", image = false)) }
        },
    )

    private fun serverUser(text: String, id: String = "server-$text") = NativeChatMessage(
        id = id,
        role = NativeChatRole.USER,
        content = text,
    )

    @Test
    fun noEchoesReturnsServerListUntouched() {
        val server = listOf(serverUser("hi"), NativeChatMessage(role = NativeChatRole.ASSISTANT, content = "yo"))
        val result = reconcileNativeEchoes(emptyList(), server)
        assertEquals(server, result.merged)
        assertTrue(result.claimedEchoIds.isEmpty())
    }

    @Test
    fun matchingEchoIsClaimedAndDropped() {
        val echo = user("hello there")
        val server = listOf(serverUser("hello there"), NativeChatMessage(role = NativeChatRole.ASSISTANT, content = "answer"))
        val result = reconcileNativeEchoes(listOf(echo), server)
        assertEquals(2, result.merged.size)
        assertEquals(setOf(echo.id), result.claimedEchoIds)
        assertFalse(result.merged.any { it.id == echo.id })
    }

    @Test
    fun unindexedEchoSurvivesRefresh() {
        val echo = user("not yet on disk")
        val server = listOf(NativeChatMessage(role = NativeChatRole.ASSISTANT, content = "answer"))
        val result = reconcileNativeEchoes(listOf(echo), server)
        assertEquals(2, result.merged.size)
        assertTrue(result.merged.last().id == echo.id)
        assertTrue(result.claimedEchoIds.isEmpty())
    }

    @Test
    fun identicalSendsClaimOneToOne() {
        val first = user("same")
        val second = user("same")
        val server = listOf(serverUser("same"), NativeChatMessage(role = NativeChatRole.ASSISTANT, content = "a1"))
        val result = reconcileNativeEchoes(listOf(first, second), server)
        // First echo claims the row; second has no row left and stays pending.
        assertEquals(setOf(first.id), result.claimedEchoIds)
        assertTrue(result.merged.any { it.id == second.id })
        assertEquals(3, result.merged.size)
    }

    @Test
    fun attachmentCountsArePartOfTheFingerprint() {
        val echo = user("with pic", images = 1)
        val server = listOf(serverUser("with pic"))
        val result = reconcileNativeEchoes(listOf(echo), server)
        assertTrue(result.claimedEchoIds.isEmpty())
        assertTrue(result.merged.any { it.id == echo.id })
    }

    @Test
    fun differentTextIsNotClaimed() {
        val echo = user("edited locally")
        val server = listOf(serverUser("original"))
        val result = reconcileNativeEchoes(listOf(echo), server)
        assertTrue(result.claimedEchoIds.isEmpty())
        assertTrue(result.merged.any { it.id == echo.id })
    }

    @Test
    fun nonUserEchoesAreNeverClaimed() {
        val assistant = NativeChatMessage(id = "local_zz", role = NativeChatRole.ASSISTANT, content = "x")
        val server = listOf(NativeChatMessage(role = NativeChatRole.ASSISTANT, content = "x"))
        val result = reconcileNativeEchoes(listOf(assistant), server)
        assertTrue(result.claimedEchoIds.isEmpty())
    }
}
