package com.termux.app

import android.content.Context
import org.json.JSONObject

internal object DeviceControlMcpRegistry {
    const val MANAGED_SERVER_KEY = "sillage_device"
    private var server: DeviceControlMcpServer? = null
    private var appContext: Context? = null

    @Synchronized
    fun ensureStarted(context: Context) {
        appContext = context.applicationContext
        if (!DeviceControlStore.enabled(context)) return
        val existing = server
        if (existing != null) {
            DeviceMcpProvisioner.provisionClaude(context, existing.endpoint)
            return
        }
        runCatching { DeviceControlMcpServer.start() }
            .onSuccess { started ->
                server = started
                DeviceControlRuntime.setMcpStatus(true, started.endpoint)
                DeviceMcpProvisioner.provisionClaude(context, started.endpoint)
                runCatching { FcodeLog.event(context, "device_control_mcp_started", JSONObject().put("mode", "loopback")) }
            }
            .onFailure { error ->
                DeviceControlRuntime.setMcpStatus(false, "")
                appContext?.let(DeviceMcpProvisioner::removeClaude)
                runCatching { FcodeLog.event(context, "device_control_mcp_failed", JSONObject().put("message", error.message.orEmpty().take(200))) }
            }
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        appContext?.let(DeviceMcpProvisioner::removeClaude)
        DeviceControlRuntime.setMcpStatus(false, "")
    }

    @Synchronized
    fun endpoint(): String = server?.endpoint.orEmpty()

    fun codexServerConfig(): JSONObject? {
        val endpoint = endpoint()
        if (endpoint.isBlank()) return null
        return JSONObject()
            .put("enabled", true)
            .put("required", false)
            .put("url", endpoint)
            .put("startup_timeout_sec", 10)
            .put("tool_timeout_sec", 180)
            .put("default_tools_approval_mode", "auto")
    }
}
