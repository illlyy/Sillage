package com.termux.app

internal data class NativeWorktreeEntry(
    val path: String,
    val head: String,
    val branch: String,
    val detached: Boolean,
    val locked: Boolean,
    val prunable: Boolean,
    val dirty: Boolean = false,
)

internal object NativeWorktreeProtocol {
    fun parse(raw: String): List<NativeWorktreeEntry> {
        val result = mutableListOf<NativeWorktreeEntry>()
        var path = ""
        var head = ""
        var branch = ""
        var detached = false
        var locked = false
        var prunable = false
        fun flush() {
            if (path.isNotBlank()) result.add(NativeWorktreeEntry(path, head, branch.removePrefix("refs/heads/"), detached, locked, prunable))
            path = ""; head = ""; branch = ""; detached = false; locked = false; prunable = false
        }
        raw.lineSequence().forEach { line ->
            if (line.isBlank()) { flush(); return@forEach }
            when {
                line.startsWith("worktree ") -> { if (path.isNotBlank()) flush(); path = line.removePrefix("worktree ").trim() }
                line.startsWith("HEAD ") -> head = line.removePrefix("HEAD ").trim()
                line.startsWith("branch ") -> branch = line.removePrefix("branch ").trim()
                line == "detached" -> detached = true
                line.startsWith("locked") -> locked = true
                line.startsWith("prunable") -> prunable = true
            }
        }
        flush()
        return result
    }

    fun normalizeBranch(value: String, timestamp: Long): String {
        val trimmed = value.trim().removePrefix("refs/heads/")
        val cleaned = trimmed
            .replace(Regex("[^A-Za-z0-9._/-]+"), "-")
            .replace(Regex("/{2,}"), "/")
            .trim('/', '.', '-')
            .replace("..", "-")
        return cleaned.ifBlank { "codex/task-$timestamp" }
    }

    fun pathSlug(branch: String): String = branch.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('.', '-').ifBlank { "task" }
}
