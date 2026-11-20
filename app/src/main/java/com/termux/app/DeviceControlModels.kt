package com.termux.app

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

internal enum class DeviceShizukuState {
    UNSUPPORTED,
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    STARTING,
    READY,
    ERROR,
}

@Immutable
internal data class DeviceControlSnapshot(
    val enabled: Boolean = false,
    val shizukuState: DeviceShizukuState = DeviceShizukuState.NOT_RUNNING,
    val detail: String = "",
    val serviceUid: Int = -1,
    val shizukuVersion: String = "",
    val shizukuCompatibilityWarning: Boolean = false,
    val sessionActive: Boolean = false,
    val sessionStartedAtMs: Long = 0L,
    val sessionExpiresAtMs: Long = 0L,
    val sessionMinutes: Int = DeviceControlStore.DEFAULT_SESSION_MINUTES,
    val mcpRunning: Boolean = false,
    val mcpEndpoint: String = "",
    val imeReady: Boolean = false,
    val inputMethodSwitched: Boolean = false,
) {
    val canStartSession: Boolean
        get() = enabled && shizukuState == DeviceShizukuState.READY && !sessionActive
}

@Immutable
internal data class DeviceAuditEntry(
    val timestampMs: Long,
    val tool: String,
    val target: String,
    val result: String,
    val durationMs: Long,
)

internal object DeviceControlStore {
    const val PREFS_NAME = "codex_mobile"
    const val KEY_ENABLED = "device_control_enabled_v1"
    const val KEY_SESSION_MINUTES = "device_control_session_minutes_v1"
    const val KEY_PREVIOUS_IME = "device_control_previous_ime_v1"
    const val KEY_IME_CLEANUP_PENDING = "device_control_ime_cleanup_pending_v1"
    const val DEFAULT_SESSION_MINUTES = 15

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun sessionMinutes(context: Context): Int {
        val value = prefs(context).getInt(KEY_SESSION_MINUTES, DEFAULT_SESSION_MINUTES)
        return value.coerceIn(5, 30)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setSessionMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_SESSION_MINUTES, minutes.coerceIn(5, 30)).apply()
    }

    fun previousIme(context: Context): String = prefs(context).getString(KEY_PREVIOUS_IME, "").orEmpty()

    fun setPreviousIme(context: Context, value: String?) {
        prefs(context).edit().putString(KEY_PREVIOUS_IME, value.orEmpty()).apply()
    }

    fun imeCleanupPending(context: Context): Boolean = prefs(context).getBoolean(KEY_IME_CLEANUP_PENDING, false)

    fun setImeCleanupPending(context: Context, pending: Boolean) {
        prefs(context).edit().putBoolean(KEY_IME_CLEANUP_PENDING, pending).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

internal object DeviceAuditStore {
    private const val MAX_ENTRIES = 50
    private val sequence = AtomicLong(0L)
    private val mutableEntries = MutableStateFlow<List<DeviceAuditEntry>>(emptyList())
    val entries = mutableEntries.asStateFlow()

    fun record(tool: String, target: String, result: String, startedAtMs: Long) {
        val entry = DeviceAuditEntry(
            timestampMs = System.currentTimeMillis(),
            tool = tool.take(48),
            target = sanitize(target).take(96),
            result = result.take(48),
            durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L),
        )
        mutableEntries.value = (listOf(entry) + mutableEntries.value).take(MAX_ENTRIES)
        sequence.incrementAndGet()
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("""(?i)(token|password|secret|authorization)\s*[=:]\s*\S+"""), "$1=***")
        .replace(Regex("""[A-Za-z0-9_-]{32,}"""), "***")
}

internal class DeviceToolException(
    val code: String,
    override val message: String,
    val hint: String = "",
) : Exception(message)
