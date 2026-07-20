package com.termux.app

import org.json.JSONArray
import org.json.JSONObject

/** Small, UI-neutral parser for the Git status used by the native work panel. */
internal object NativeGitWorkflow {
    private fun decodePath(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length < 2 || !trimmed.startsWith('"') || !trimmed.endsWith('"')) return trimmed
        val source = trimmed.substring(1, trimmed.length - 1)
        val result = StringBuilder()
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (char != '\\' || index + 1 >= source.length) {
                result.append(char)
                index++
                continue
            }
            val escaped = source[index + 1]
            result.append(when (escaped) {
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                else -> escaped
            })
            index += 2
        }
        return result.toString()
    }

    fun diffSnapshot(unstaged: String, staged: String, error: String = ""): String {
        val combined = sequenceOf(staged, unstaged).flatMap { it.lineSequence() }
        var additions = 0
        var deletions = 0
        combined.forEach { line ->
            if (line.startsWith("+") && !line.startsWith("+++")) additions++
            if (line.startsWith("-") && !line.startsWith("---")) deletions++
        }
        return JSONObject()
            .put("unstaged", unstaged.take(160_000))
            .put("staged", staged.take(160_000))
            .put("additions", additions)
            .put("deletions", deletions)
            .put("error", error.take(4_000))
            .toString()
    }

    fun unavailable(projectPath: String, message: String): String = JSONObject()
        .put("available", false)
        .put("projectPath", projectPath)
        .put("error", message)
        .put("entries", JSONArray())
        .toString()

    fun parse(projectPath: String, output: String): String {
        val lines = output.lineSequence().filter { it.isNotBlank() }.toList()
        var branch = ""
        var upstream = ""
        var ahead = 0
        var behind = 0
        val entries = JSONArray()

        lines.forEach { line ->
            if (line.startsWith("## ")) {
                val heading = line.removePrefix("## ")
                branch = heading.substringBefore("...").substringBefore(" [").trim().let { value ->
                    when {
                        value.startsWith("No commits yet on ") -> value.substringAfter("No commits yet on ")
                        value.startsWith("Initial commit on ") -> value.substringAfter("Initial commit on ")
                        else -> value
                    }
                }
                if ("..." in heading) upstream = heading.substringAfter("...").substringBefore(" [").trim()
                Regex("ahead (\\d+)").find(heading)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { ahead = it }
                Regex("behind (\\d+)").find(heading)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { behind = it }
                return@forEach
            }
            if (line.length < 3) return@forEach
            val indexStatus = line[0]
            val worktreeStatus = line[1]
            val rawPath = line.substring(3).trim()
            val path = decodePath(rawPath.substringAfter(" -> "))
            val staged = indexStatus != ' ' && indexStatus != '?'
            val unstaged = worktreeStatus != ' ' || (indexStatus == '?' && worktreeStatus == '?')
            val statusChars = "$indexStatus$worktreeStatus"
            val operation = when {
                'D' in statusChars -> "delete"
                'A' in statusChars || statusChars == "??" -> "add"
                'R' in statusChars -> "rename"
                else -> "edit"
            }
            entries.put(JSONObject()
                .put("path", path)
                .put("originalPath", rawPath)
                .put("index", indexStatus.toString())
                .put("worktree", worktreeStatus.toString())
                .put("staged", staged)
                .put("unstaged", unstaged)
                .put("operation", operation))
        }

        return JSONObject()
            .put("available", true)
            .put("projectPath", projectPath)
            .put("branch", branch)
            .put("upstream", upstream)
            .put("ahead", ahead)
            .put("behind", behind)
            .put("clean", entries.length() == 0)
            .put("entries", entries)
            .toString()
    }
}
