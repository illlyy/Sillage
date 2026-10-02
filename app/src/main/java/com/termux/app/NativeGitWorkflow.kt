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
        val octalBytes = java.io.ByteArrayOutputStream()
        fun flushOctal() {
            if (octalBytes.size() > 0) {
                result.append(octalBytes.toByteArray().toString(Charsets.UTF_8))
                octalBytes.reset()
            }
        }
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (char != '\\' || index + 1 >= source.length) {
                flushOctal()
                result.append(char)
                index++
                continue
            }
            val next = source[index + 1]
            // Git quotes non-ASCII paths as octal UTF-8 bytes: "\303\244" -> ä.
            if (next in '0'..'7' && index + 3 < source.length + 1) {
                var end = index + 1
                while (end < source.length && end < index + 4 && source[end] in '0'..'7') end++
                val octal = source.substring(index + 1, end)
                val byte = runCatching { octal.toInt(8) }.getOrNull()
                if (byte != null && byte in 0..255) {
                    octalBytes.write(byte)
                    index = end
                    continue
                }
            }
            flushOctal()
            val escaped = next
            result.append(when (escaped) {
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                else -> escaped
            })
            index += 2
        }
        flushOctal()
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
                        value == "HEAD (no branch)" || value == "HEAD" -> ""
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
                'U' in statusChars || statusChars in setOf("AA", "DD", "AU", "UA", "DU", "UD") -> "conflict"
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
