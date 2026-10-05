package com.termux.app

import android.content.Context

/**
 * Remembers the last thing that actually blocked the Claude CLI **on this device**.
 *
 * The install page can only say what it installed; it cannot know that the official Bun binary
 * dies on this particular phone under Android's seccomp filter. That distinction decides the fix
 * (switch to the npm distribution) and the user cannot see it anywhere: the failure surfaces as a
 * chat that never becomes usable, with nothing in the shareable log naming the cause. So the
 * bridge records what it saw, and settings shows it next to the version.
 *
 * Cleared as soon as the bridge reaches ready — a CLI that got that far demonstrably works, so
 * keeping a stale sandbox warning around would only teach the user to ignore the line.
 */
internal object ClaudeRuntimeNote {
    private const val PREFS = "claude_runtime_note"
    private const val KEY_SECCOMP_BLOCKED = "seccomp_blocked"
    private const val KEY_EXIT_CODE = "exit_code"

    /** True when the most recent recorded launch was killed by the app sandbox (exit 159). */
    fun seccompBlocked(context: Context): Boolean = prefs(context)
        .getBoolean(KEY_SECCOMP_BLOCKED, false)

    /** Exit code of the blocked launch, or 0 when unknown / not blocked. */
    fun blockedExitCode(context: Context): Int = prefs(context).getInt(KEY_EXIT_CODE, 0)

    fun recordSandboxBlock(context: Context, exitCode: Int) {
        prefs(context).edit()
            .putBoolean(KEY_SECCOMP_BLOCKED, true)
            .putInt(KEY_EXIT_CODE, exitCode)
            .apply()
    }

    /** Called once a CLI is up and usable; there is nothing left to warn about. */
    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_SECCOMP_BLOCKED)
            .remove(KEY_EXIT_CODE)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
