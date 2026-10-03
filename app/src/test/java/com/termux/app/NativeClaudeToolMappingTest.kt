package com.termux.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claude speaks its own tool vocabulary; the shared timeline speaks Codex's. These tests pin the
 * translation, because getting it wrong is invisible: the tool still runs, it just renders as an
 * anonymous `call_...` row with no subject, no diff and no image.
 */
class NativeClaudeToolMappingTest {

    private fun input(json: String) = JSONObject(json)

    @Test
    fun `edit tool maps to file change`() {
        assertEquals(
            NativeClaudeToolMapping.TYPE_FILE_CHANGE,
            NativeClaudeToolMapping.activityType("Edit", input("""{"file_path":"src/App.kt"}""")),
        )
        assertEquals(
            NativeClaudeToolMapping.TYPE_FILE_CHANGE,
            NativeClaudeToolMapping.activityType("Write", input("""{"file_path":"a.txt"}""")),
        )
        assertEquals(
            NativeClaudeToolMapping.TYPE_FILE_CHANGE,
            NativeClaudeToolMapping.activityType("MultiEdit", JSONObject()),
        )
    }

    @Test
    fun `reading an image maps to the image type and anything else to a tool`() {
        assertEquals(
            NativeClaudeToolMapping.TYPE_IMAGE,
            NativeClaudeToolMapping.activityType("Read", input("""{"file_path":"probe.png"}""")),
        )
        assertEquals(
            NativeClaudeToolMapping.TYPE_IMAGE,
            NativeClaudeToolMapping.activityType("Read", input("""{"file_path":"a/b/photo.JPEG"}""")),
        )
        assertEquals(
            NativeClaudeToolMapping.TYPE_TOOL,
            NativeClaudeToolMapping.activityType("Read", input("""{"file_path":"Main.kt"}""")),
        )
    }

    @Test
    fun `web tools map to the web search type`() {
        assertEquals(
            NativeClaudeToolMapping.TYPE_WEB_SEARCH,
            NativeClaudeToolMapping.activityType("WebFetch", input("""{"url":"https://x.dev"}""")),
        )
        assertEquals(
            NativeClaudeToolMapping.TYPE_WEB_SEARCH,
            NativeClaudeToolMapping.activityType("WebSearch", input("""{"query":"kotlin"}""")),
        )
    }

    /**
     * Claude Code renamed the delegated-work tool from `Task` to `Agent`; the decoder only knew the
     * old names, so subagent capsules silently stopped appearing for every 2.x CLI.
     */
    @Test
    fun `agent is recognised as the subagent tool`() {
        assertTrue(NativeClaudeToolMapping.isSubagentTool("Agent", JSONObject()))
        assertTrue(NativeClaudeToolMapping.isSubagentTool("Task", JSONObject()))
        assertTrue(NativeClaudeToolMapping.isSubagentTool("task_simple", JSONObject()))
    }

    @Test
    fun `an unknown subagent tool name is still recognised by its input shape`() {
        assertTrue(NativeClaudeToolMapping.isSubagentTool("SomeFutureName", input("""{"subagent_type":"Explore"}""")))
        assertFalse(NativeClaudeToolMapping.isSubagentTool("Read", input("""{"file_path":"a.txt"}""")))
    }

    @Test
    fun `tool subject picks the argument worth showing`() {
        assertEquals("probe.png", NativeClaudeToolMapping.toolSubject("Read", input("""{"file_path":"probe.png"}""")))
        assertEquals("**/*.kt", NativeClaudeToolMapping.toolSubject("Glob", input("""{"pattern":"**/*.kt"}""")))
        assertEquals("https://x.dev", NativeClaudeToolMapping.toolSubject("WebFetch", input("""{"url":"https://x.dev"}""")))
        assertEquals("", NativeClaudeToolMapping.toolSubject("TodoWrite", input("""{"todos":[]}""")))
    }

    @Test
    fun `title carries the localized label and the subject`() {
        assertEquals("读取文件 · probe.png", NativeClaudeToolMapping.toolTitle("Read", "probe.png", "zh"))
        assertEquals("Read file · probe.png", NativeClaudeToolMapping.toolTitle("Read", "probe.png", "en"))
        // An unknown tool keeps its own name rather than hiding behind a generic label.
        assertEquals("SomeTool", NativeClaudeToolMapping.toolTitle("SomeTool", "", "zh"))
    }

    /** A long absolute path would wrap the row and push the label off screen. */
    @Test
    fun `a long absolute path is shortened to its last two segments`() {
        assertEquals(
            "读取文件 · …/xhome/probe.txt",
            NativeClaudeToolMapping.toolTitle("Read", "/data/data/com.ilyop.codex/xhome/probe.txt", "zh"),
        )
        // Short subjects are left untouched.
        assertEquals("修改文件 · Main.kt", NativeClaudeToolMapping.toolTitle("Edit", "Main.kt", "zh"))
    }

    /** A write is a whole-file addition, so the parser can synthesize its diff from the content. */
    @Test
    fun `write produces an add change carrying the content`() {
        val item = NativeClaudeToolMapping.fileChangeItem(
            "Write",
            input("""{"file_path":"notes.md","content":"line one\nline two"}"""),
            "toolu_1",
        )
        assertNotNull(item)
        val change = item!!.optJSONArray("changes")!!.optJSONObject(0)!!
        assertEquals("notes.md", change.optString("path"))
        assertEquals("add", change.optString("type"))
        assertEquals("line one\nline two", change.optString("content"))
        val parsed = NativeFileChangeParser.parse(item)
        assertEquals(1, parsed.size)
        assertTrue(parsed.first().unifiedDiff.contains("+line one"))
    }

    /** An edit is a replacement, so the diff is generated from the two strings. */
    @Test
    fun `edit produces a unified diff of the replacement`() {
        val item = NativeClaudeToolMapping.fileChangeItem(
            "Edit",
            input("""{"file_path":"Main.kt","old_string":"val a = 1","new_string":"val a = 2"}"""),
            "toolu_2",
        )
        assertNotNull(item)
        val diff = item!!.optJSONArray("changes")!!.optJSONObject(0)!!.optString("unified_diff")
        assertTrue(diff.contains("--- a/Main.kt"))
        assertTrue(diff.contains("-val a = 1"))
        assertTrue(diff.contains("+val a = 2"))
        assertEquals("Main.kt", NativeFileChangeParser.parse(item).first().path)
    }

    @Test
    fun `an edit without a path yields no card`() {
        assertNull(NativeClaudeToolMapping.fileChangeItem("Edit", input("""{"old_string":"a","new_string":"b"}"""), "id"))
        assertNull(NativeClaudeToolMapping.fileChangeItem("Edit", null, "id"))
    }

    @Test
    fun `multiedit merges every replacement into one diff`() {
        val item = NativeClaudeToolMapping.fileChangeItem(
            "MultiEdit",
            input(
                """{"file_path":"A.kt","edits":[{"old_string":"one","new_string":"ONE"},{"old_string":"two","new_string":"TWO"}]}""",
            ),
            "toolu_3",
        )
        val diff = item!!.optJSONArray("changes")!!.optJSONObject(0)!!.optString("unified_diff")
        assertTrue(diff.contains("-one"))
        assertTrue(diff.contains("+ONE"))
        assertTrue(diff.contains("-two"))
        assertTrue(diff.contains("+TWO"))
    }

    /** A minified line can make one "line" enormous; the diff is dropped instead of carried. */
    @Test
    fun `an oversized diff is dropped rather than serialized`() {
        val huge = "x".repeat(NativeClaudeToolMapping.MAX_DIFF_CHARS + 100)
        assertNull(NativeClaudeToolMapping.editDiff("old", huge, "big.js"))
    }

    @Test
    fun `image items carry the path the card renders`() {
        val item = NativeClaudeToolMapping.imageItem(input("""{"file_path":"probe.png"}"""), "toolu_4")
        assertEquals("probe.png", item!!.optString("path"))
        assertEquals(NativeClaudeToolMapping.TYPE_IMAGE, item.optString("type"))
        assertNull(NativeClaudeToolMapping.imageItem(input("""{"file_path":""}"""), "toolu_5"))
    }

    @Test
    fun `image detection is extension based and case insensitive`() {
        assertTrue(NativeClaudeToolMapping.isImagePath("/tmp/a/b.PNG"))
        assertTrue(NativeClaudeToolMapping.isImagePath("shot.webp"))
        assertFalse(NativeClaudeToolMapping.isImagePath("Main.kt"))
        assertFalse(NativeClaudeToolMapping.isImagePath(""))
    }

    /** The classification must land on types the reducer can actually map to an item type. */
    @Test
    fun `produced types are understood by the reducer vocabulary`() {
        val json = JSONArray()
        json.put(JSONObject().put("path", "a.txt").put("type", "add").put("content", "x"))
        val item = JSONObject().put("type", NativeClaudeToolMapping.TYPE_FILE_CHANGE).put("changes", json)
        assertFalse(NativeFileChangeParser.parse(item).isEmpty())
        assertTrue(isImageToolItem(NativeClaudeToolMapping.TYPE_IMAGE))
    }
}
