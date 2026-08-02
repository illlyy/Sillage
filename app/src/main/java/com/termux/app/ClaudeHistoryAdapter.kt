package com.termux.app

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import org.json.JSONObject
import java.io.File

/**
 * Claude session discovery and history loading.
 *
 * Claude persists transcripts as JSONL under `CLAUDE_CONFIG_DIR/projects/<cwd>/<sessionId>.jsonl`
 * (default `~/.claude/projects`). This adapter scans those files for the conversation list
 * (title from `ai-title`/`custom-title`/`last-prompt` metadata rows, project from `cwd`),
 * and renders a lightweight history snapshot from user/assistant text for resumed sessions —
 * mirroring the codex history pipeline without depending on the Claude CLI.
 */
internal object ClaudeHistoryAdapter {
    private const val MAX_SESSION_SCAN = 400
    private const val MAX_TITLE_CHARS = 52

    fun configDir(): File = File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".claude")

    fun projectsRoot(): File = File(configDir(), "projects")

    /** Scans Claude transcripts and returns conversation metadata for the drawer/list. */
    fun listConversations(context: Context): List<NativeConversation> {
        val result = ArrayList<NativeConversation>()
        val root = projectsRoot()
        val candidates = ArrayList<File>()
        collectJsonl(root, candidates, 0)
        var scanned = 0
        for (file in candidates) {
            if (scanned >= MAX_SESSION_SCAN) break
            scanned++
            val sessionId = readSessionId(file) ?: continue
            val title = resolveTitle(file, sessionId)
                .ifBlank { firstUserPrompt(file, sessionId) }
                .ifBlank { "Claude 会话" }
            val project = readProjectPath(file)
            result.add(
                NativeConversation(
                    threadId = sessionId,
                    title = title,
                    state = CodexTaskStore.COMPLETED,
                    projectPath = project,
                    favorite = false,
                ),
            )
        }
        // Most recent transcripts last (append-only); show newest first.
        result.reverse()
        return result
    }

    /**
     * Builds a simplified history snapshot for a resumed session: user/assistant text messages,
     * tool activity as PROCESS2 capsules (best-effort). Large files are tail-scanned.
     */
    fun loadSnapshot(context: Context, sessionId: String): NativeHistorySnapshot? {
        val file = findSessionFile(sessionId) ?: return null
        val messages = ArrayList<NativeChatMessage>()
        val lines = readTailLines(file, maxLines = 2000)
        var assistantBuffer = StringBuilder()
        val createdAt = System.currentTimeMillis()
        for (line in lines) {
            if (line.isBlank()) continue
            val entry = runCatching { JSONObject(line) }.getOrNull() ?: continue
            if (!entry.optString("sessionId").equals(sessionId, ignoreCase = true) &&
                !entry.optString("session_id").equals(sessionId, ignoreCase = true)
            ) continue
            val type = entry.optString("type")
            if (type == "user") {
                flushAssistant(messages, assistantBuffer, createdAt)
                val text = extractText(entry.optJSONObject("message"))
                if (text.isNotBlank()) messages.add(
                    NativeChatMessage(role = NativeChatRole.USER, content = text, revealStartedAt = createdAt),
                )
            } else if (type == "assistant") {
                val text = extractText(entry.optJSONObject("message"))
                if (text.isNotBlank()) {
                    if (assistantBuffer.isNotEmpty()) assistantBuffer.append("\n\n")
                    assistantBuffer.append(text)
                }
            }
        }
        flushAssistant(messages, assistantBuffer, createdAt)
        if (messages.isEmpty()) return null
        val estimated = messages.sumOf { it.content.length }
        return NativeHistorySnapshot(messages = messages, estimatedChars = estimated)
    }

    private fun flushAssistant(messages: ArrayList<NativeChatMessage>, buffer: StringBuilder, createdAt: Long) {
        if (buffer.isBlank()) return
        messages.add(NativeChatMessage(role = NativeChatRole.ASSISTANT, content = buffer.toString().trim(), revealStartedAt = createdAt))
        buffer.setLength(0)
    }

    private fun extractText(message: JSONObject?): String {
        if (message == null) return ""
        val content = message.opt("content") ?: return ""
        return when (content) {
            is String -> content
            is org.json.JSONArray -> {
                val text = StringBuilder()
                for (i in 0 until content.length()) {
                    val block = content.optJSONObject(i) ?: continue
                    if (block.optString("type") == "text") text.append(block.optString("text"))
                }
                text.toString()
            }
            else -> ""
        }
    }

    // ---------------------------------------------------------------- file helpers

    private fun collectJsonl(directory: File, out: ArrayList<File>, depth: Int) {
        if (depth > 4 || !directory.isDirectory) return
        val entries = directory.listFiles() ?: return
        for (entry in entries) {
            val name = entry.name
            if (entry.isDirectory) {
                if (name == "subagents" || name == "tool-results") continue
                collectJsonl(entry, out, depth + 1)
            } else if (entry.isFile && name.endsWith(".jsonl")) {
                out.add(entry)
            }
        }
    }

    private fun findSessionFile(sessionId: String): File? {
        val candidates = ArrayList<File>()
        collectJsonl(projectsRoot(), candidates, 0)
        for (file in candidates) {
            val id = readSessionId(file)
            if (id != null && id.equals(sessionId, ignoreCase = true)) return file
        }
        return null
    }

    /** Reads sessionId from the first valid JSON line of the transcript. */
    private fun readSessionId(file: File): String? {
        val reader = file.bufferedReader(Charsets.UTF_8)
        reader.use {
            var count = 0
            while (count++ < 50) {
                val line = it.readLine() ?: break
                if (line.isBlank()) continue
                val entry = runCatching { JSONObject(line) }.getOrNull() ?: continue
                val id = entry.optString("sessionId")
                    .ifBlank { entry.optString("session_id") }
                if (id.isNotBlank()) return id
            }
        }
        return null
    }

    private fun readProjectPath(file: File): String {
        try {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                var count = 0
                while (count++ < 50) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val entry = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    val cwd = entry.optString("cwd")
                    if (cwd.isNotBlank() && cwd != "null") return cwd
                }
            }
        } catch (ignored: Exception) {}
        return ""
    }

    /** Title resolution: scan the tail for ai-title / custom-title / last-prompt rows. */
    private fun resolveTitle(file: File, sessionId: String): String {
        val lines = readTailLines(file, maxLines = 200)
        for (line in lines.asReversed()) {
            if (line.isBlank()) continue
            val entry = runCatching { JSONObject(line) }.getOrNull() ?: continue
            if (!entry.optString("sessionId").equals(sessionId, ignoreCase = true)) continue
            val type = entry.optString("type")
            val title = when (type) {
                "ai-title" -> entry.optString("aiTitle")
                "custom-title" -> entry.optString("customTitle")
                "last-prompt" -> entry.optString("lastPrompt")
                else -> ""
            }
            if (title.isNotBlank()) return title.take(MAX_TITLE_CHARS)
        }
        return ""
    }

    /** Lightweight AI title fallback: the first user prompt, truncated. */
    private fun firstUserPrompt(file: File, sessionId: String): String {
        try {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                var count = 0
                while (count++ < 2000) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val entry = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    if (entry.optString("type") != "user") continue
                    if (!entry.optString("sessionId").equals(sessionId, ignoreCase = true)) continue
                    val text = extractText(entry.optJSONObject("message"))
                    if (text.isNotBlank()) return text.trim().replace(Regex("\\s+"), " ").take(MAX_TITLE_CHARS)
                }
            }
        } catch (ignored: Exception) {}
        return ""
    }

    private fun readTailLines(file: File, maxLines: Int): List<String> {
        return try {
            val all = file.readLines(Charsets.UTF_8)
            if (all.size <= maxLines) all else all.subList(all.size - maxLines, all.size)
        } catch (ignored: Exception) {
            emptyList()
        }
    }
}
