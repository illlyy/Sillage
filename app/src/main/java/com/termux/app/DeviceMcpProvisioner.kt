package com.termux.app

import android.content.Context
import org.json.JSONObject

internal object DeviceMcpProvisioner {
    fun provisionClaude(context: Context, endpoint: String) {
        runCatching { ClaudeMcpConfigStore.upsertManagedServer(DeviceControlMcpRegistry.MANAGED_SERVER_KEY, endpoint) }
            .onFailure { error ->
                runCatching {
                    FcodeLog.event(context, "device_control_claude_mcp_failed", JSONObject().put("message", error.message.orEmpty().take(200)))
                }
            }
    }

    fun removeClaude(context: Context) {
        runCatching { ClaudeMcpConfigStore.removeManagedServer(DeviceControlMcpRegistry.MANAGED_SERVER_KEY) }
    }

    fun claudeAllowedTools(): String = listOf(
        "device_status", "device_control_start", "device_control_stop", "device_snapshot", "device_layout",
        "device_find", "device_click", "device_apps", "device_app_info", "device_tap", "device_swipe",
        "device_scroll", "device_scroll_until", "device_key", "device_input_text", "device_launch_app",
        "device_open_url", "device_open_settings", "device_system_panel", "device_media",
        "device_wait", "device_wait_for", "device_shell",
    ).joinToString(",") { "mcp__${DeviceControlMcpRegistry.MANAGED_SERVER_KEY}__$it" }
}
