package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class NativeQueuedMessageStoreTest {

    private fun followUp(
        id: String,
        text: String,
        images: Int = 1,
        files: Int = 1,
        skills: Int = 0,
    ) = NativeQueuedFollowUp(
        id = id,
        text = text,
        attachments = buildList {
            repeat(images) { add(NativeAttachment("a$it", "/tmp/a$it.png", image = true)) }
            repeat(files) { add(NativeAttachment("doc$it", "/tmp/doc$it.pdf", image = false)) }
        },
        skills = buildList {
            repeat(skills) { add(NativeSkill("skill$it", "desc", "/tmp/s$it")) }
        },
        model = "gpt-5",
        effort = "medium",
        mode = "plan",
    )

    @Test
    fun encodeDecodeRoundTripsAllFields() {
        val followUp = followUp("id-1", "continue the work", images = 2, skills = 3)
        val decoded = NativeQueuedMessageStore.decodeFollowUp(NativeQueuedMessageStore.encodeFollowUp(followUp))!!
        assertEquals(followUp.id, decoded.id)
        assertEquals(followUp.text, decoded.text)
        assertEquals(followUp.model, decoded.model)
        assertEquals(followUp.effort, decoded.effort)
        assertEquals(followUp.mode, decoded.mode)
        assertEquals(followUp.attachments, decoded.attachments)
        assertEquals(followUp.skills, decoded.skills)
    }

    @Test
    fun blankTextDecodesToEmptyFollowUp() {
        val json = JSONObject().put("text", "  ")
        val decoded = NativeQueuedMessageStore.decodeFollowUp(json)
        assertTrue(decoded != null && decoded.text.isEmpty())
    }

    @Test
    fun missingArraysDecodeEmpty() {
        val json = JSONObject().put("text", "hi")
        val decoded = NativeQueuedMessageStore.decodeFollowUp(json)!!
        assertTrue(decoded.attachments.isEmpty())
        assertTrue(decoded.skills.isEmpty())
    }

    @Test
    fun encodeHandlesEmptyCollections() {
        val decoded = NativeQueuedMessageStore.decodeFollowUp(
            NativeQueuedMessageStore.encodeFollowUp(followUp("x", "t", images = 0, files = 0, skills = 0)),
        )!!
        assertTrue(decoded.attachments.isEmpty())
        assertTrue(decoded.skills.isEmpty())
    }

    @Test
    fun arrayEncodingProducesExpectedJsonShape() {
        val json = NativeQueuedMessageStore.encodeFollowUp(followUp("id", "text"))
        assertEquals("id", json.optString("id"))
        val attachments = json.optJSONArray("attachments")
        assertEquals(2, attachments?.length())
        assertEquals(true, attachments?.optJSONObject(0)?.optBoolean("image"))
        assertEquals(false, attachments?.optJSONObject(1)?.optBoolean("image"))
    }
}
