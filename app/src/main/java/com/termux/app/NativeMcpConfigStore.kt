package com.termux.app

import androidx.compose.runtime.Immutable
import com.termux.shared.termux.TermuxConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

@Immutable
data class NativeMcpServerConfig(
    val key: String,
    val enabled: Boolean = true,
    val required: Boolean = false,
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val envVars: List<String> = emptyList(),
    val cwd: String = "",
    val url: String = "",
    val bearerTokenEnvVar: String = "",
    val httpHeaders: Map<String, String> = emptyMap(),
    val envHttpHeaders: Map<String, String> = emptyMap(),
    val startupTimeoutSec: String = "",
    val toolTimeoutSec: String = "",
    val enabledTools: List<String> = emptyList(),
    val disabledTools: List<String> = emptyList(),
    val approvalMode: String = "",
) {
    val isHttp: Boolean get() = url.isNotBlank()
}

/** Reads and edits only Codex MCP tables while preserving every unrelated config.toml setting. */
object NativeMcpConfigStore {
    const val REVISION_KEY = "native_mcp_config_revision_v1"
    private val lock = Any()

    @JvmStatic
    fun configFile(): File = File(File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "config.toml")

    /** Cheap change detector for edits made by either the native settings page or WebUI. */
    @JvmStatic
    fun fileFingerprint(): String = configFile().let { file ->
        if (!file.isFile) "missing" else "${file.lastModified()}:${file.length()}"
    }

    @JvmStatic
    fun load(): List<NativeMcpServerConfig> = synchronized(lock) {
        parse(configFile().takeIf(File::isFile)?.readText(StandardCharsets.UTF_8).orEmpty())
    }

    /** Explicit thread config used by the native app-server bridge. */
    @JvmStatic
    fun threadConfig(): JSONObject = synchronized(lock) {
        val file = configFile()
        threadConfig(parse(file.takeIf(File::isFile)?.readText(StandardCharsets.UTF_8).orEmpty()))
    }

    internal fun threadConfig(configuredServers: List<NativeMcpServerConfig>): JSONObject {
        val servers = JSONObject()
        configuredServers.forEach { server ->
            val value = JSONObject()
                .put("enabled", server.enabled)
                .put("required", server.required)
            if (server.isHttp) {
                value.put("url", server.url)
                if (server.bearerTokenEnvVar.isNotBlank()) value.put("bearer_token_env_var", server.bearerTokenEnvVar)
                if (server.httpHeaders.isNotEmpty()) value.put("http_headers", JSONObject(server.httpHeaders))
                if (server.envHttpHeaders.isNotEmpty()) value.put("env_http_headers", JSONObject(server.envHttpHeaders))
            } else {
                value.put("command", server.command)
                if (server.args.isNotEmpty()) value.put("args", JSONArray(server.args))
                if (server.env.isNotEmpty()) value.put("env", JSONObject(server.env))
                if (server.envVars.isNotEmpty()) value.put("env_vars", JSONArray(server.envVars))
                if (server.cwd.isNotBlank()) value.put("cwd", server.cwd)
            }
            server.startupTimeoutSec.toDoubleOrNull()?.let { value.put("startup_timeout_sec", it) }
            server.toolTimeoutSec.toDoubleOrNull()?.let { value.put("tool_timeout_sec", it) }
            if (server.enabledTools.isNotEmpty()) value.put("enabled_tools", JSONArray(server.enabledTools))
            if (server.disabledTools.isNotEmpty()) value.put("disabled_tools", JSONArray(server.disabledTools))
            if (server.approvalMode.isNotBlank()) value.put("default_tools_approval_mode", server.approvalMode)
            servers.put(server.key, value)
        }
        return JSONObject().put("mcp_servers", servers)
    }

    @JvmStatic
    fun save(server: NativeMcpServerConfig, previousKey: String? = null) = synchronized(lock) {
        val file = configFile()
        val all = parse(file.takeIf(File::isFile)?.readText(StandardCharsets.UTF_8).orEmpty()).toMutableList()
        val old = previousKey?.trim().orEmpty()
        require(all.none { it.key == server.key && it.key != old }) { "An MCP server named '${server.key}' already exists" }
        if (old.isNotEmpty()) all.removeAll { it.key == old }
        all.removeAll { it.key == server.key }
        all.add(server)
        write(file, replaceMcpTables(file.takeIf(File::isFile)?.readText(StandardCharsets.UTF_8).orEmpty(), all))
    }

    @JvmStatic
    fun remove(key: String) = synchronized(lock) {
        val file = configFile()
        val current = file.takeIf(File::isFile)?.readText(StandardCharsets.UTF_8).orEmpty()
        write(file, replaceMcpTables(current, parse(current).filterNot { it.key == key }))
    }

    @JvmStatic
    fun setEnabled(key: String, enabled: Boolean) = synchronized(lock) {
        val file = configFile()
        val current = file.takeIf(File::isFile)?.readText(StandardCharsets.UTF_8).orEmpty()
        val next = parse(current).map { if (it.key == key) it.copy(enabled = enabled) else it }
        write(file, replaceMcpTables(current, next))
    }

    /** Provider changes rewrite the base config. Carry the MCP tables across that rewrite. */
    @JvmStatic
    fun mergePreservingMcp(file: File, replacementBase: String): String {
        val existing = runCatching { if (file.isFile) file.readText(StandardCharsets.UTF_8) else "" }.getOrDefault("")
        val mcp = extractMcpTables(existing).trim()
        return buildString {
            append(replacementBase.trimEnd()).append('\n')
            if (mcp.isNotEmpty()) append('\n').append(mcp).append('\n')
        }
    }

    internal fun parse(source: String): List<NativeMcpServerConfig> {
        data class MutableServer(
            val key: String,
            var enabled: Boolean = true,
            var required: Boolean = false,
            var command: String = "",
            var args: List<String> = emptyList(),
            val env: LinkedHashMap<String, String> = linkedMapOf(),
            var envVars: List<String> = emptyList(),
            var cwd: String = "",
            var url: String = "",
            var bearer: String = "",
            val headers: LinkedHashMap<String, String> = linkedMapOf(),
            val envHeaders: LinkedHashMap<String, String> = linkedMapOf(),
            var startup: String = "",
            var tool: String = "",
            var enabledTools: List<String> = emptyList(),
            var disabledTools: List<String> = emptyList(),
            var approval: String = "",
        )

        val servers = linkedMapOf<String, MutableServer>()
        var section: List<String> = emptyList()
        source.lineSequence().forEach { raw ->
            val line = stripComment(raw).trim()
            if (line.isEmpty()) return@forEach
            parseSection(line)?.let { section = it; return@forEach }
            if (section.size < 2 || section[0] != "mcp_servers") return@forEach
            val server = servers.getOrPut(section[1]) { MutableServer(section[1]) }
            val assignment = splitAssignment(line) ?: return@forEach
            val key = unquote(assignment.first.trim())
            val value = assignment.second.trim()
            when (section.getOrNull(2)) {
                "env" -> server.env[key] = parseString(value)
                "http_headers" -> server.headers[key] = parseString(value)
                "env_http_headers" -> server.envHeaders[key] = parseString(value)
                null -> when (key) {
                    "enabled" -> server.enabled = parseBoolean(value, true)
                    "required" -> server.required = parseBoolean(value, false)
                    "command" -> server.command = parseString(value)
                    "args" -> server.args = parseStringArray(value)
                    "env" -> server.env.putAll(parseInlineMap(value))
                    "env_vars" -> server.envVars = parseStringArray(value)
                    "cwd" -> server.cwd = parseString(value)
                    "url" -> server.url = parseString(value)
                    "bearer_token_env_var" -> server.bearer = parseString(value)
                    "http_headers" -> server.headers.putAll(parseInlineMap(value))
                    "env_http_headers" -> server.envHeaders.putAll(parseInlineMap(value))
                    "startup_timeout_sec", "startup_timeout_ms" -> server.startup = value
                    "tool_timeout_sec" -> server.tool = value
                    "enabled_tools" -> server.enabledTools = parseStringArray(value)
                    "disabled_tools" -> server.disabledTools = parseStringArray(value)
                    "default_tools_approval_mode" -> server.approval = parseString(value)
                }
            }
        }
        return servers.values.map { value ->
            NativeMcpServerConfig(
                key = value.key, enabled = value.enabled, required = value.required,
                command = value.command, args = value.args, env = value.env.toMap(), envVars = value.envVars,
                cwd = value.cwd, url = value.url, bearerTokenEnvVar = value.bearer,
                httpHeaders = value.headers.toMap(), envHttpHeaders = value.envHeaders.toMap(),
                startupTimeoutSec = value.startup, toolTimeoutSec = value.tool,
                enabledTools = value.enabledTools, disabledTools = value.disabledTools,
                approvalMode = value.approval,
            )
        }
    }

    internal fun replaceMcpTables(source: String, servers: List<NativeMcpServerConfig>): String = buildString {
        val base = stripMcpTables(source).trimEnd()
        if (base.isNotEmpty()) append(base).append("\n\n")
        servers.sortedBy { it.key.lowercase() }.forEachIndexed { index, server ->
            if (index > 0) append('\n')
            append(render(server))
        }
    }.trimEnd().let { if (it.isEmpty()) "" else "$it\n" }

    private fun render(server: NativeMcpServerConfig): String = buildString {
        append("[mcp_servers.").append(tomlKey(server.key)).append("]\n")
        append("enabled = ").append(server.enabled).append('\n')
        if (server.required) append("required = true\n")
        if (server.isHttp) {
            append("url = ").append(tomlString(server.url)).append('\n')
            if (server.bearerTokenEnvVar.isNotBlank()) append("bearer_token_env_var = ").append(tomlString(server.bearerTokenEnvVar)).append('\n')
            if (server.httpHeaders.isNotEmpty()) append("http_headers = ").append(tomlMap(server.httpHeaders)).append('\n')
            if (server.envHttpHeaders.isNotEmpty()) append("env_http_headers = ").append(tomlMap(server.envHttpHeaders)).append('\n')
        } else {
            append("command = ").append(tomlString(server.command)).append('\n')
            if (server.args.isNotEmpty()) append("args = ").append(tomlArray(server.args)).append('\n')
            if (server.env.isNotEmpty()) append("env = ").append(tomlMap(server.env)).append('\n')
            if (server.envVars.isNotEmpty()) append("env_vars = ").append(tomlArray(server.envVars)).append('\n')
            if (server.cwd.isNotBlank()) append("cwd = ").append(tomlString(server.cwd)).append('\n')
        }
        if (server.startupTimeoutSec.isNotBlank()) append("startup_timeout_sec = ").append(server.startupTimeoutSec.trim()).append('\n')
        if (server.toolTimeoutSec.isNotBlank()) append("tool_timeout_sec = ").append(server.toolTimeoutSec.trim()).append('\n')
        if (server.enabledTools.isNotEmpty()) append("enabled_tools = ").append(tomlArray(server.enabledTools)).append('\n')
        if (server.disabledTools.isNotEmpty()) append("disabled_tools = ").append(tomlArray(server.disabledTools)).append('\n')
        if (server.approvalMode.isNotBlank()) append("default_tools_approval_mode = ").append(tomlString(server.approvalMode)).append('\n')
    }

    private fun write(file: File, value: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, ".${file.name}.${System.nanoTime()}.tmp")
        temp.writeText(value, StandardCharsets.UTF_8)
        if (file.exists() && !file.delete()) throw IllegalStateException("Unable to replace ${file.absolutePath}")
        if (!temp.renameTo(file)) {
            file.writeText(value, StandardCharsets.UTF_8)
            temp.delete()
        }
    }

    private fun stripMcpTables(source: String): String = filterMcpTables(source, keep = false)
    private fun extractMcpTables(source: String): String = filterMcpTables(source, keep = true)

    private fun filterMcpTables(source: String, keep: Boolean): String {
        val out = StringBuilder()
        var inMcp = false
        source.lineSequence().forEach { raw ->
            parseSection(raw.trim())?.let { inMcp = it.firstOrNull() == "mcp_servers" }
            if (inMcp == keep) out.append(raw).append('\n')
        }
        return out.toString()
    }

    private fun parseSection(line: String): List<String>? {
        if (!line.startsWith('[') || !line.endsWith(']') || line.startsWith("[[")) return null
        val body = line.substring(1, line.length - 1).trim()
        val result = mutableListOf<String>()
        val token = StringBuilder()
        var quote = '\u0000'
        var escaped = false
        body.forEach { char ->
            when {
                escaped -> { token.append(char); escaped = false }
                char == '\\' && quote != '\u0000' -> { token.append(char); escaped = true }
                quote != '\u0000' && char == quote -> { token.append(char); quote = '\u0000' }
                quote != '\u0000' -> token.append(char)
                char == '\'' || char == '"' -> { quote = char; token.append(char) }
                char == '.' -> { result.add(unquote(token.toString().trim())); token.setLength(0) }
                else -> token.append(char)
            }
        }
        if (token.isNotBlank()) result.add(unquote(token.toString().trim()))
        return result
    }

    private fun stripComment(line: String): String {
        var quote = '\u0000'; var escaped = false
        line.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' && quote != '\u0000' -> escaped = true
                quote != '\u0000' && char == quote -> quote = '\u0000'
                quote == '\u0000' && (char == '\'' || char == '"') -> quote = char
                quote == '\u0000' && char == '#' -> return line.substring(0, index)
            }
        }
        return line
    }

    private fun splitAssignment(line: String): Pair<String, String>? {
        var quote = '\u0000'; var escaped = false
        line.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' && quote != '\u0000' -> escaped = true
                quote != '\u0000' && char == quote -> quote = '\u0000'
                quote == '\u0000' && (char == '\'' || char == '"') -> quote = char
                quote == '\u0000' && char == '=' -> return line.substring(0, index) to line.substring(index + 1)
            }
        }
        return null
    }

    private fun parseBoolean(value: String, fallback: Boolean) = when (value.trim().lowercase()) { "true" -> true; "false" -> false; else -> fallback }
    private fun parseString(value: String): String = unquote(value.trim())
    private fun unquote(value: String): String {
        val v = value.trim()
        if (v.length < 2) return v
        if ((v.first() == '"' && v.last() == '"') || (v.first() == '\'' && v.last() == '\'')) {
            return v.substring(1, v.length - 1).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
        }
        return v
    }

    private fun parseStringArray(value: String): List<String> = Regex("\"((?:\\\\.|[^\"])*)\"|'([^']*)'")
        .findAll(value).map { unquote(it.value) }.toList()

    private fun parseInlineMap(value: String): Map<String, String> {
        val body = value.trim().removePrefix("{").removeSuffix("}")
        val result = linkedMapOf<String, String>()
        splitComma(body).forEach { entry -> splitAssignment(entry)?.let { result[unquote(it.first)] = parseString(it.second) } }
        return result
    }

    private fun splitComma(value: String): List<String> {
        val result = mutableListOf<String>(); val token = StringBuilder(); var quote = '\u0000'; var escaped = false
        value.forEach { char ->
            when {
                escaped -> { token.append(char); escaped = false }
                char == '\\' && quote != '\u0000' -> { token.append(char); escaped = true }
                quote != '\u0000' && char == quote -> { token.append(char); quote = '\u0000' }
                quote != '\u0000' -> token.append(char)
                char == '\'' || char == '"' -> { quote = char; token.append(char) }
                char == ',' -> { result.add(token.toString()); token.setLength(0) }
                else -> token.append(char)
            }
        }
        if (token.isNotBlank()) result.add(token.toString())
        return result
    }

    private fun tomlKey(value: String): String = if (value.matches(Regex("[A-Za-z0-9_-]+"))) value else tomlString(value)
    private fun tomlString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    private fun tomlArray(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]") { tomlString(it) }
    private fun tomlMap(values: Map<String, String>): String = values.entries.joinToString(prefix = "{ ", postfix = " }") { "${tomlString(it.key)} = ${tomlString(it.value)}" }
}
