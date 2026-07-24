package com.termux.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeChatDerivedDataTest {
    @Test
    fun toolPayloadsAreClassifiedWithoutLeakingInvalidOrUnknownItems() {
        val parsed = parseToolDetails(
            listOf(
                JSONObject().put("id", "command").put("type", "commandExecution").toString(),
                JSONObject().put("id", "change").put("type", "fileChange").toString(),
                JSONObject().put("id", "image-1").put("type", "imageView").toString(),
                JSONObject().put("id", "image-2").put("type", "view_image").toString(),
                JSONObject().put("id", "unknown").put("type", "other").toString(),
                "not-json",
            ),
        )

        assertEquals(listOf("command"), parsed.commands.map { it.optString("id") })
        assertEquals(listOf("change"), parsed.fileChanges.map { it.optString("id") })
        assertEquals(listOf("image-1", "image-2"), parsed.images.map { it.optString("id") })
    }

    @Test
    fun assistantOnlyOwnsTheImmediatelyPrecedingActivityFileChanges() {
        val messages = listOf(
            processMessage("old-activity", JSONArray().put(fileChange("old", "old.txt"))),
            NativeChatMessage(id = "user", role = NativeChatRole.USER, content = "boundary"),
            processMessage("activity-1", JSONArray().put(fileChange("one", "one.txt"))),
            processMessage("activity-2", JSONArray().put(fileChange("two", "two.txt").toString())),
            NativeChatMessage(id = "assistant", role = NativeChatRole.ASSISTANT, content = "answer"),
            processMessage("later", JSONArray().put(fileChange("later", "later.txt"))),
        )

        val associated = associatedFileChangeItems(messages, "assistant")

        assertEquals(setOf("one.txt", "two.txt"), associated.map { it.optString("path") }.toSet())
        assertTrue(associatedFileChangeItems(messages, "missing").isEmpty())
    }

    @Test
    fun historicalAndLiveFileChangesKeepFirstOccurrencePerStableId() {
        val historical = fileChange("same", "history.kt")
        val messages = listOf(processMessage("activity", JSONArray().put(historical)))
        val live = listOf(
            fileChange("same", "live.kt").toString(),
            JSONObject().put("itemId", "other").put("type", "fileChange").put("path", "other.kt").toString(),
            JSONObject().put("id", "command").put("type", "commandExecution").toString(),
            "invalid",
        )

        val collected = collectAllFileChangeItems(messages, live)

        assertEquals(2, collected.size)
        assertEquals("history.kt", collected.first { it.optString("id") == "same" }.optString("path"))
        assertEquals("other.kt", collected.first { it.optString("itemId") == "other" }.optString("path"))
    }

    @Test
    fun changedFilesAreExtractedFromFieldsPatchSyntaxAndUnifiedDiffs() {
        val changes = JSONArray().put(
            JSONObject().put("path", "b/src/New.kt").put("type", "ADD"),
        )
        val items = listOf(
            JSONObject().put("type", "fileChange").put("path", "a/src/Main.kt"),
            JSONObject().put("type", "fileChange").put("changes", changes),
            JSONObject().put("type", "fileChange").put("changes", "*** Delete File: old.txt"),
            JSONObject().put("type", "fileChange").put(
                NativeLargePayloadStore.PAYLOAD_PREVIEW,
                "--- a/renamed.txt\n+++ b/renamed.txt\n--- /dev/null",
            ),
        )

        val files = extractChangedFiles(items).associateBy { it.path }

        assertEquals("edit", files.getValue("src/Main.kt").operation)
        assertEquals("add", files.getValue("src/New.kt").operation)
        assertEquals("delete", files.getValue("old.txt").operation)
        assertEquals("edit", files.getValue("renamed.txt").operation)
        assertEquals(4, files.size)
    }

    private fun fileChange(id: String, path: String): JSONObject = JSONObject()
        .put("id", id)
        .put("type", "fileChange")
        .put("path", path)

    private fun processMessage(id: String, tools: JSONArray): NativeChatMessage {
        val payload = JSONObject().put("tools", tools).toString().toByteArray(Charsets.UTF_8)
        return NativeChatMessage(
            id = id,
            role = NativeChatRole.ACTIVITY,
            content = "PROCESS2|${NativeBase64.encode(payload)}",
        )
    }
}
