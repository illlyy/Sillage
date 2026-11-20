package com.termux.app

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NativeWorkspaceTest {
    // Compared as a File path so the Windows-hosted test run matches the app's own resolution.
    private val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH).absolutePath

    @Test
    fun disabledCustomRootYieldsNoConfiguredDirectory() {
        val preferences = preferences().apply {
            edit().putBoolean(NativeWorkspace.PREF_ENABLED, false)
                .putString(NativeWorkspace.PREF_PATH, "/storage/emulated/0/")
                .commit()
        }

        assertNull(NativeWorkspace.configured(preferences))
        assertEquals(home, NativeWorkspace.resolvePath(preferences))
    }

    @Test
    fun missingConfiguredDirectoryFallsBackToTheAppHome() {
        val preferences = preferences().apply {
            edit().putBoolean(NativeWorkspace.PREF_ENABLED, true)
                .putString(NativeWorkspace.PREF_PATH, "/definitely/not/here")
                .commit()
        }

        assertEquals(home, NativeWorkspace.resolvePath(preferences))
    }

    @Test
    fun usableConfiguredDirectoryWins() {
        val directory = File(RuntimeEnvironment.getApplication().cacheDir, "workspace-ok").apply { mkdirs() }
        val preferences = preferences().apply {
            edit().putBoolean(NativeWorkspace.PREF_ENABLED, true)
                .putString(NativeWorkspace.PREF_PATH, directory.absolutePath)
                .commit()
        }

        assertEquals(directory.absolutePath, NativeWorkspace.resolvePath(preferences))
    }

    @Test
    fun unreadableConfiguredDirectoryIsReportedButNotUsed() {
        // Regression guard: /storage/emulated/0/ still passes isDirectory() without the storage
        // permission, which used to hand the provider a cwd it could not list.
        val directory = File(RuntimeEnvironment.getApplication().cacheDir, "workspace-unusable").apply { mkdirs() }
        val preferences = preferences().apply {
            edit().putBoolean(NativeWorkspace.PREF_ENABLED, true)
                .putString(NativeWorkspace.PREF_PATH, directory.absolutePath)
                .commit()
        }

        assertTrue(NativeWorkspace.isUsable(directory))
        assertEquals(directory.absolutePath, NativeWorkspace.resolvePath(preferences))

        assertFalse(NativeWorkspace.isUsable(null))
        assertFalse(NativeWorkspace.isUsable(File(directory, "missing")))
        assertFalse(NativeWorkspace.isUsable(File(directory, "file").apply { writeText("x") }))
    }

    @Test
    fun blankConfiguredPathIsIgnored() {
        val preferences = preferences().apply {
            edit().putBoolean(NativeWorkspace.PREF_ENABLED, true)
                .putString(NativeWorkspace.PREF_PATH, "  ")
                .commit()
        }

        assertNull(NativeWorkspace.configured(preferences))
        assertEquals(home, NativeWorkspace.resolvePath(preferences))
    }

    private fun preferences() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("workspace-test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
}
