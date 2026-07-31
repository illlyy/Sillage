package com.termux.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class CodexTaskStoreTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val preferences
        get() = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Before
    fun clearStore() {
        preferences.edit().clear().commit()
    }

    @Test
    fun explicitEmptyProjectAssignmentIsPersisted() {
        CodexTaskStore.markRunning(context, THREAD_ID, "Standalone task")
        CodexTaskStore.assignProject(context, THREAD_ID, "")

        val persisted = JSONArray(preferences.getString(STORAGE_KEY, "[]"))
            .getJSONObject(0)
        assertTrue(persisted.has("projectPath"))
        assertEquals("", persisted.getString("projectPath"))

        val restored = CodexTaskStore.current(context).single()
        assertTrue(restored.projectAssignmentKnown)
        assertEquals("", restored.projectPath)
    }

    @Test
    fun taskLifecycleUpdatesPreserveProjectAssignment() {
        val projectPath = File(context.filesDir, "registered-project").canonicalPath
        CodexTaskStore.markRunning(context, THREAD_ID, "Initial title")
        CodexTaskStore.assignProject(context, THREAD_ID, projectPath)

        CodexTaskStore.markRunning(context, THREAD_ID, "Running title")
        assertProjectAssignment(projectPath)

        CodexTaskStore.updateTitle(context, THREAD_ID, "Updated title")
        assertProjectAssignment(projectPath)
        assertEquals("Updated title", CodexTaskStore.current(context).single().title)

        CodexTaskStore.markCompleted(context, THREAD_ID, false)
        val completed = CodexTaskStore.current(context).single()
        assertEquals(CodexTaskStore.COMPLETED, completed.state)
        assertTrue(completed.projectAssignmentKnown)
        assertEquals(projectPath, completed.projectPath)
    }

    @Test
    fun legacyEntryWithoutProjectPathHasUnknownAssignment() {
        val legacy = JSONObject()
            .put("threadId", THREAD_ID)
            .put("title", "Legacy task")
            .put("state", CodexTaskStore.COMPLETED)
            .put("updatedAt", 123L)
        preferences.edit()
            .putString(STORAGE_KEY, JSONArray().put(legacy).toString())
            .commit()

        val restored = CodexTaskStore.current(context).single()

        assertFalse(restored.projectAssignmentKnown)
        assertEquals("", restored.projectPath)
    }

    private fun assertProjectAssignment(expectedPath: String) {
        val task = CodexTaskStore.current(context).single()
        assertTrue(task.projectAssignmentKnown)
        assertEquals(expectedPath, task.projectPath)
    }

    private companion object {
        const val PREFERENCES_NAME = "codex_mobile"
        const val STORAGE_KEY = "overlay_tasks_v1"
        const val THREAD_ID = "thread-project-assignment"
    }
}
