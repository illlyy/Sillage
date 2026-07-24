package com.termux.app

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class NativeMcpRuntimeStatus(
    val state: String = "checking",
    val toolCount: Int = 0,
    val toolNames: List<String> = emptyList(),
    val detail: String = "",
    val checkedAt: Long = 0L,
)

/** Last app-server MCP probe, persisted so connectivity belongs to the MCP page, not chat. */
object NativeMcpRuntimeStatusStore {
    private const val PREFS = "native_mcp_runtime_status_v1"
    private const val PAYLOAD = "payload"
    private const val CHECKED_AT = "checked_at"

    @JvmStatic
    fun record(context: Context, message: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PAYLOAD, message)
            .putLong(CHECKED_AT, System.currentTimeMillis())
            .apply()
    }

    @JvmStatic
    fun recordFailure(context: Context, detail: String) {
        record(context, JSONObject().put("error", JSONObject().put("message", detail.take(800))).toString())
    }

    fun load(context: Context, servers: List<NativeMcpServerConfig>): Map<String, NativeMcpRuntimeStatus> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val checkedAt = prefs.getLong(CHECKED_AT, 0L)
        val payload = prefs.getString(PAYLOAD, "").orEmpty()
        val message = runCatching { JSONObject(payload) }.getOrNull()
        val globalError = message?.optJSONObject("error")?.optString("message").orEmpty()
        val result = message?.optJSONObject("result")
        val data = result?.optJSONArray("data") ?: JSONArray()
        val discovered = linkedMapOf<String, NativeMcpRuntimeStatus>()
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val name = item.optString("name", item.optString("serverName", "")).trim()
            if (name.isEmpty()) continue
            val error = item.optString("error").ifBlank {
                item.optJSONObject("error")?.optString("message").orEmpty()
            }
            val statusText = item.optString("status").lowercase()
            val unavailable = error.isNotBlank() || statusText.contains("fail") || statusText.contains("error") || statusText.contains("unavailable")
            val tools = item.opt("tools")
            discovered[name] = NativeMcpRuntimeStatus(
                state = if (unavailable) "unavailable" else "connected",
                toolCount = toolCount(tools),
                toolNames = toolNames(tools),
                detail = error.ifBlank { item.optString("status") },
                checkedAt = checkedAt,
            )
        }
        return servers.associate { server ->
            val status = when {
                !server.enabled -> NativeMcpRuntimeStatus("disabled", checkedAt = checkedAt)
                discovered.containsKey(server.key) -> discovered.getValue(server.key)
                globalError.isNotBlank() -> NativeMcpRuntimeStatus("unavailable", detail = globalError, checkedAt = checkedAt)
                checkedAt > 0L -> NativeMcpRuntimeStatus("unavailable", detail = "Server was not discovered by Codex", checkedAt = checkedAt)
                else -> NativeMcpRuntimeStatus("checking")
            }
            server.key to status
        }
    }

    private fun toolCount(value: Any?): Int = when (value) {
        is JSONArray -> value.length()
        is JSONObject -> value.length()
        else -> 0
    }

    private fun toolNames(value: Any?): List<String> = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted()
        is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it)?.optString("name", "")?.ifBlank { null } }.sorted()
        else -> emptyList()
    }
}
