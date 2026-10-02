package com.termux.app

import com.termux.shared.termux.TermuxConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Reads and edits the Claude Code global MCP table (`~/.claude.json` -> `mcpServers`), the same
 * shape the desktop CLI and cc-switch use. Every unrelated top-level field (projects history,
 * session state, ...) is preserved: only `mcpServers` is replaced, atomically.
 *
 * Project-level `.mcp.json` files are intentionally not edited here; the settings page manages
 * the global table (which the CLI loads for every project). The CLI re-reads it at spawn, so a
 * change takes effect after the next backend restart.
 */
object ClaudeMcpConfigStore {
    @JvmStatic
    fun globalFile(): File = File(TermuxConstants.TERMUX_HOME_DIR, ".claude.json")

    /** Cheap change detector for edits made by either the settings page or the CLI itself. */
    @JvmStatic
    fun fileFingerprint(): String {
        val file = globalFile()
        return if (file.isFile) "${file.lastModified()}:${file.length()}" else "missing"
    }

    @JvmStatic
    fun load(): List<NativeMcpServerConfig> = parseServers(readGlobalJson())

    @JvmStatic
    fun save(server: NativeMcpServerConfig, previousKey: String? = null) = synchronized(lock) {
        val root = readGlobalJson() ?: JSONObject()
        writeGlobal(mergeServer(root, server, previousKey))
    }

    @JvmStatic
    fun remove(key: String) = synchronized(lock) {
        val root = readGlobalJson() ?: return
        writeGlobal(removeServer(root, key))
    }

    @JvmStatic
    fun setEnabled(key: String, enabled: Boolean) = synchronized(lock) {
        val root = readGlobalJson() ?: return
        writeGlobal(setServerEnabled(root, key, enabled))
    }

    private val lock = Any()

    // ------------------------------------------------------------------ pure JSON helpers

    private fun copyRoot(root: JSONObject): JSONObject = JSONObject().apply {
        root.keys().forEach { key -> put(key, root.get(key)) }
    }

    /** Returns a new root with `server` merged into the `mcpServers` table (rename via [previousKey]). */
    internal fun mergeServer(root: JSONObject, server: NativeMcpServerConfig, previousKey: String? = null): JSONObject {
        val servers = copyTable(root.optJSONObject("mcpServers"))
        val old = previousKey?.trim().orEmpty()
        if (servers.has(server.key) && server.key != old) {
            throw IllegalArgumentException("An MCP server named '${server.key}' already exists")
        }
        if (old.isNotEmpty() && servers.has(old) && old != server.key) servers.remove(old)
        servers.put(server.key, renderServer(server))
        return copyRoot(root).put("mcpServers", servers)
    }

    internal fun removeServer(root: JSONObject, key: String): JSONObject {
        val servers = copyTable(root.optJSONObject("mcpServers"))
        if (servers.remove(key) == null) return root
        return copyRoot(root).put("mcpServers", servers)
    }

    internal fun setServerEnabled(root: JSONObject, key: String, enabled: Boolean): JSONObject {
        val servers = copyTable(root.optJSONObject("mcpServers"))
        val server = servers.optJSONObject(key) ?: return root
        val copy = JSONObject().apply { server.keys().forEach { k -> put(k, server.get(k)) } }
        if (enabled) copy.remove("disabled") else copy.put("disabled", true)
        servers.put(key, copy)
        return copyRoot(root).put("mcpServers", servers)
    }

    private fun copyTable(table: JSONObject?): JSONObject = JSONObject().apply {
        if (table == null) return@apply
        table.keys().forEach { key -> put(key, table.get(key)) }
    }

    internal fun parseServers(root: JSONObject?): List<NativeMcpServerConfig> {
        val table = root?.optJSONObject("mcpServers") ?: return emptyList()
        return buildList {
            table.keys().forEach { key ->
                table.optJSONObject(key)?.let { raw ->
                    parseServer(key, raw)?.let(::add)
                }
            }
        }
    }

    internal fun parseServer(key: String, raw: JSONObject): NativeMcpServerConfig? {
        val type = raw.optString("type", "").lowercase()
        val url = raw.optString("url", "")
        val isHttp = "http".equals(type) || "sse".equals(type) || (url.isNotBlank() && type.isBlank())
        return NativeMcpServerConfig(
            key = key,
            enabled = !raw.optBoolean("disabled", false),
            command = if (isHttp) "" else raw.optString("command", ""),
            args = jsonStringArray(raw.optJSONArray("args")),
            env = jsonStringMap(raw.optJSONObject("env")),
            cwd = raw.optString("cwd", ""),
            url = if (isHttp) url else "",
        )
    }

    internal fun renderServer(server: NativeMcpServerConfig): JSONObject {
        val value = JSONObject()
        if (server.isHttp) {
            value.put("type", "http")
            value.put("url", server.url)
        } else {
            value.put("command", server.command)
            if (server.args.isNotEmpty()) value.put("args", JSONArray(server.args))
            if (server.env.isNotEmpty()) value.put("env", JSONObject(server.env))
            if (server.cwd.isNotBlank()) value.put("cwd", server.cwd)
        }
        if (!server.enabled) value.put("disabled", true)
        return value
    }

    // ------------------------------------------------------------------ file IO

    /** @return top-level JSON of ~/.claude.json, or null when missing/unparseable. */
    private fun readGlobalJson(): JSONObject? {
        val file = globalFile()
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText(StandardCharsets.UTF_8)) }.getOrNull()
    }

    private fun writeGlobal(root: JSONObject) {
        val file = globalFile()
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, ".${file.name}.${System.nanoTime()}.tmp")
        temp.writeText(root.toString(2), StandardCharsets.UTF_8)
        if (file.exists() && !file.delete()) throw IllegalStateException("Unable to replace ${file.absolutePath}")
        if (!temp.renameTo(file)) {
            file.writeText(root.toString(2), StandardCharsets.UTF_8)
            temp.delete()
        }
    }

    private fun jsonStringArray(array: JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun jsonStringMap(map: JSONObject?): Map<String, String> = buildMap {
        if (map == null) return@buildMap
        map.keys().forEach { key -> map.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) } }
    }
}