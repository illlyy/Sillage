package com.termux.app

import android.content.SharedPreferences

/**
 * Which agent CLI backend drives the native chat. Persisted per app install; the settings
 * page can switch backends at runtime (each backend keeps its own API configuration).
 */
internal enum class NativeBackendType(val value: String) {
    CODEX("codex"),
    CLAUDE("claude");

    companion object {
        const val PREFERENCE_KEY = "native_backend_type_v1"

        fun from(value: String?): NativeBackendType =
            entries.firstOrNull { it.value == value } ?: CODEX

        fun current(prefs: SharedPreferences): NativeBackendType =
            from(prefs.getString(PREFERENCE_KEY, null))

        fun set(prefs: SharedPreferences, type: NativeBackendType) {
            prefs.edit().putString(PREFERENCE_KEY, type.value).apply()
        }
    }
}
