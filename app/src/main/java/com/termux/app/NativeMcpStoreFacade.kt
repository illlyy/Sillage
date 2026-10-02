package com.termux.app

import android.content.SharedPreferences

/**
 * Backend-aware MCP store dispatch. The settings pages are shared between backends: Codex keeps
 * its `~/.codex/config.toml` tables, Claude keeps the `~/.claude.json` `mcpServers` table, so
 * every store call resolves the active backend and routes to the right implementation.
 */
object NativeMcpStoreFacade {
    @JvmStatic
    fun isClaude(prefs: SharedPreferences): Boolean =
        NativeBackendType.current(prefs) == NativeBackendType.CLAUDE

    /** Revision key bumped on every edit; chat and WebUI reload the backend when it changes. */
    @JvmStatic
    fun revisionKey(prefs: SharedPreferences): String =
        if (isClaude(prefs)) "native_claude_mcp_revision_v1" else NativeMcpConfigStore.REVISION_KEY

    @JvmStatic
    fun fileFingerprint(prefs: SharedPreferences): String =
        if (isClaude(prefs)) ClaudeMcpConfigStore.fileFingerprint() else NativeMcpConfigStore.fileFingerprint()

    @JvmStatic
    fun load(prefs: SharedPreferences): List<NativeMcpServerConfig> =
        if (isClaude(prefs)) ClaudeMcpConfigStore.load() else NativeMcpConfigStore.load()

    @JvmStatic
    fun save(prefs: SharedPreferences, server: NativeMcpServerConfig, previousKey: String? = null) {
        if (isClaude(prefs)) ClaudeMcpConfigStore.save(server, previousKey)
        else NativeMcpConfigStore.save(server, previousKey)
    }

    @JvmStatic
    fun remove(prefs: SharedPreferences, key: String) {
        if (isClaude(prefs)) ClaudeMcpConfigStore.remove(key)
        else NativeMcpConfigStore.remove(key)
    }

    @JvmStatic
    fun setEnabled(prefs: SharedPreferences, key: String, enabled: Boolean) {
        if (isClaude(prefs)) ClaudeMcpConfigStore.setEnabled(key, enabled)
        else NativeMcpConfigStore.setEnabled(key, enabled)
    }

    /** Human-readable config location for the settings page subtitle. */
    @JvmStatic
    fun configLocation(prefs: SharedPreferences): String =
        if (isClaude(prefs)) "~/.claude.json mcpServers" else "~/.codex/config.toml"
}