package com.termux.app

import android.content.Context
import android.content.Context.MODE_PRIVATE

/**
 * Process-independent route memory for the last conversation shown in each backend.
 *
 * The app-server runtime already remembers its current thread, but that value dies with the
 * process. Without this small preference, Android reclaiming a background chat makes the next
 * launch create a fresh thread even though every rollout is still on disk.
 */
internal object NativeActiveThreadStore {
    private const val PREFERENCES = "codex_mobile"
    private const val KEY_PREFIX = "native_active_thread_v1_"

    private fun key(backend: NativeBackendType): String = KEY_PREFIX + backend.value

    fun read(context: Context, backend: NativeBackendType): String =
        prefs(context).getString(key(backend), "").orEmpty().trim()

    fun remember(context: Context, backend: NativeBackendType, threadId: String) {
        val value = threadId.trim()
        if (value.isEmpty()) return
        // Route changes are rare; commit so a process reclaim immediately after backgrounding
        // cannot lose the only pointer back to the on-disk rollout.
        prefs(context).edit().putString(key(backend), value).commit()
    }

    fun clear(context: Context, backend: NativeBackendType) {
        prefs(context).edit().remove(key(backend)).commit()
    }

    fun clearIfMatches(context: Context, backend: NativeBackendType, threadId: String) {
        if (read(context, backend) == threadId.trim()) clear(context, backend)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
}
