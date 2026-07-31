package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDrawerTaskClassificationTest {
    @Test
    fun registeredProjectTasksAreGroupedAndStandaloneOrderIsPreserved() {
        val projectA = "/storage/emulated/0/Projects/Alpha"
        val projectB = "/storage/emulated/0/Projects/Beta"
        val result = classifyNativeDrawerTasks(
            conversations = listOf(
                conversation("standalone-empty", ""),
                conversation("alpha-1", "$projectA/"),
                conversation("unregistered", "/storage/emulated/0/Downloads"),
                conversation("beta-1", projectB),
                conversation("alpha-2", "$projectA/./"),
            ),
            registeredProjectPaths = listOf(projectB, projectA),
            hiddenProjectPaths = emptySet(),
        )

        assertEquals(listOf(projectB, projectA), result.visibleProjects)
        assertEquals(listOf("standalone-empty", "unregistered"), result.standaloneTasks.map { it.threadId })
        assertEquals(listOf("beta-1"), result.tasksByProject.getValue(projectB).map { it.threadId })
        assertEquals(listOf("alpha-1", "alpha-2"), result.tasksByProject.getValue(projectA).map { it.threadId })
    }

    @Test
    fun hiddenAndRemovedProjectsReturnTheirTasksToStandalone() {
        val hiddenProject = "/storage/emulated/0/Projects/Hidden"
        val removedProject = "/storage/emulated/0/Projects/Removed"
        val result = classifyNativeDrawerTasks(
            conversations = listOf(
                conversation("hidden", hiddenProject),
                conversation("removed", removedProject),
            ),
            registeredProjectPaths = listOf(hiddenProject),
            hiddenProjectPaths = setOf("$hiddenProject/"),
        )

        assertTrue(result.visibleProjects.isEmpty())
        assertTrue(result.tasksByProject.isEmpty())
        assertEquals(listOf("hidden", "removed"), result.standaloneTasks.map { it.threadId })
    }

    @Test
    fun windowsPathsMatchCaseInsensitivelyAcrossSeparatorsAndDotSegments() {
        val registered = "C:\\Work\\FCode\\"
        val result = classifyNativeDrawerTasks(
            conversations = listOf(
                conversation("windows", "c:/work/other/../fcode/./"),
            ),
            registeredProjectPaths = listOf(registered),
            hiddenProjectPaths = emptySet(),
        )

        assertTrue(result.standaloneTasks.isEmpty())
        assertEquals(listOf("windows"), result.tasksByProject.getValue(registered).map { it.threadId })
    }

    @Test
    fun androidPathsRemainCaseSensitive() {
        val registered = "/storage/emulated/0/Projects/Fcode"
        val result = classifyNativeDrawerTasks(
            conversations = listOf(
                conversation("exact", "$registered/"),
                conversation("different-case", "/storage/emulated/0/Projects/fcode"),
            ),
            registeredProjectPaths = listOf(registered),
            hiddenProjectPaths = emptySet(),
        )

        assertEquals(listOf("exact"), result.tasksByProject.getValue(registered).map { it.threadId })
        assertEquals(listOf("different-case"), result.standaloneTasks.map { it.threadId })
    }

    @Test
    fun duplicateRegisteredVariantsUseFirstPathAndKeepEmptyProjects() {
        val first = "D:\\Code\\App"
        val duplicate = "d:/code/app/"
        val empty = "D:\\Code\\Empty"
        val result = classifyNativeDrawerTasks(
            conversations = listOf(conversation("app", duplicate)),
            registeredProjectPaths = listOf(first, duplicate, empty, ""),
            hiddenProjectPaths = emptySet(),
        )

        assertEquals(listOf(first, empty), result.visibleProjects)
        assertEquals(listOf("app"), result.tasksByProject.getValue(first).map { it.threadId })
        assertEquals(emptyList<NativeConversation>(), result.tasksByProject.getValue(empty))
    }

    @Test
    fun hiddenWindowsProjectMatchesCaseAndSlashVariants() {
        val registered = "C:\\Work\\Secret"
        val result = classifyNativeDrawerTasks(
            conversations = listOf(conversation("secret", registered)),
            registeredProjectPaths = listOf(registered),
            hiddenProjectPaths = setOf("c:/work/secret/"),
        )

        assertTrue(result.visibleProjects.isEmpty())
        assertEquals(listOf("secret"), result.standaloneTasks.map { it.threadId })
    }

    @Test
    fun legacyProjectMigrationKeepsRawPathUntilProjectIsRegistered() {
        val resolvedCwd = "C:\\Work\\Fcode\\.\\"
        val beforeRegistration = resolveNativeConversationProjectMigration(
            cachedResolvedPath = null,
            freshlyResolvedPath = resolvedCwd,
            registeredProjectPaths = listOf("C:\\Work\\Other"),
        )

        assertEquals(resolvedCwd, beforeRegistration.resolvedPath)
        assertNull(beforeRegistration.registeredProjectPath)

        val registeredProject = "c:/work/fcode"
        val afterRegistration = resolveNativeConversationProjectMigration(
            cachedResolvedPath = beforeRegistration.resolvedPath,
            freshlyResolvedPath = null,
            registeredProjectPaths = listOf(registeredProject),
        )

        assertEquals(resolvedCwd, afterRegistration.resolvedPath)
        assertEquals(registeredProject, afterRegistration.registeredProjectPath)

        val afterRestart = resolveNativeConversationProjectMigration(
            cachedResolvedPath = null,
            freshlyResolvedPath = resolvedCwd,
            registeredProjectPaths = listOf(registeredProject),
        )
        assertEquals(registeredProject, afterRestart.registeredProjectPath)
    }

    @Test
    fun blankLegacyResolutionDoesNotBecomeAProjectAssignment() {
        val migration = resolveNativeConversationProjectMigration(
            cachedResolvedPath = null,
            freshlyResolvedPath = "   ",
            registeredProjectPaths = listOf("/storage/emulated/0/Projects/Fcode"),
        )

        assertNull(migration.resolvedPath)
        assertNull(migration.registeredProjectPath)
    }

    private fun conversation(id: String, projectPath: String) = NativeConversation(
        threadId = id,
        title = id,
        state = CodexTaskStore.COMPLETED,
        projectPath = projectPath,
        favorite = false,
    )
}
