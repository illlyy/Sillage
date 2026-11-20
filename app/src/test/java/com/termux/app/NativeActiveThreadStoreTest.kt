package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativeActiveThreadStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun reset() {
        context.getSharedPreferences("codex_mobile", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun eachBackendKeepsItsOwnLastThread() {
        NativeActiveThreadStore.remember(context, NativeBackendType.CODEX, "codex-thread")
        NativeActiveThreadStore.remember(context, NativeBackendType.CLAUDE, "claude-thread")

        assertEquals("codex-thread", NativeActiveThreadStore.read(context, NativeBackendType.CODEX))
        assertEquals("claude-thread", NativeActiveThreadStore.read(context, NativeBackendType.CLAUDE))
    }

    @Test
    fun deletingAnotherThreadCannotClearTheActiveRoute() {
        NativeActiveThreadStore.remember(context, NativeBackendType.CODEX, "active")

        NativeActiveThreadStore.clearIfMatches(context, NativeBackendType.CODEX, "other")
        assertEquals("active", NativeActiveThreadStore.read(context, NativeBackendType.CODEX))

        NativeActiveThreadStore.clearIfMatches(context, NativeBackendType.CODEX, "active")
        assertEquals("", NativeActiveThreadStore.read(context, NativeBackendType.CODEX))
    }
}
