package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NativeSubagentVisualTest {
    @Test
    fun stableSeedAndColorSurviveStatusUpdates() {
        val first = NativeSubagentVisualFactory.create("thread-1", "call", "Research", "working")
        val second = NativeSubagentVisualFactory.create("thread-1", "call", "Research", "done")
        assertEquals(first.seed, second.seed)
        assertEquals(first.colorIndex, second.colorIndex)
        assertEquals(first.lightColorArgb, second.lightColorArgb)
        assertNotEquals(first.status, second.status)
    }

    @Test
    fun aliasesMergeAndOverflowIsCappedAtEight() {
        val spawn = NativeSubagentVisualFactory.create(callId = "call-1", name = "Agent", aliases = listOf("alias"))
        val update = NativeSubagentVisualFactory.create(agentThreadId = "thread-1", callId = "call-1", status = "done", aliases = listOf("alias"))
        val extras = (2..10).map { NativeSubagentVisualFactory.create(agentThreadId = "thread-$it") }
        val inline = NativeSubagentVisualFactory.inline(listOf(spawn, update) + extras)
        assertEquals(8, inline.visible.size)
        assertEquals(2, inline.overflowCount)
        assertEquals(NativeSubagentStatus.DONE, inline.visible.first().status)
    }

    @Test
    fun discoveringThreadIdLaterDoesNotRecolorCallSeed() {
        val first = NativeSubagentVisualFactory.create(callId = "call-1", name = "Agent", status = "working")
        val later = NativeSubagentVisualFactory.create(
            agentThreadId = "thread-1",
            callId = "call-1",
            name = "Agent",
            status = "done",
        )
        val merged = NativeSubagentVisualFactory.merge(first, later)
        assertEquals("call-1", merged.seed)
        assertEquals(first.colorIndex, merged.colorIndex)
        assertEquals(NativeSubagentStatus.DONE, merged.status)
    }
}
