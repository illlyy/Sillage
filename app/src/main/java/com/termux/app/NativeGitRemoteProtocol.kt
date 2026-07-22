package com.termux.app

import java.net.URI
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

internal data class NativeGitRemote(
    val name: String,
    val fetchUrl: String,
    val pushUrl: String,
)

internal object NativeGitRemoteProtocol {
    private val remoteLine = Regex("^(\\S+)\\s+(.+?)\\s+\\((fetch|push)\\)$")

    fun parseRemotes(raw: String): List<NativeGitRemote> {
        val values = linkedMapOf<String, Pair<String, String>>()
        raw.lineSequence().forEach { line ->
            val match = remoteLine.matchEntire(line.trim()) ?: return@forEach
            val name = match.groupValues[1]
            val url = sanitizeRemoteUrl(match.groupValues[2])
            val previous = values[name] ?: ("" to "")
            values[name] = if (match.groupValues[3] == "fetch") url to previous.second else previous.first to url
        }
        return values.map { (name, urls) -> NativeGitRemote(name, urls.first, urls.second.ifBlank { urls.first }) }
    }

    fun parseHistory(raw: String): JSONArray = JSONArray().also { result ->
        raw.split('\u001e').forEach { record ->
            val fields = record.trim('\n', '\r').split('\u001f')
            if (fields.size < 5 || fields[0].isBlank()) return@forEach
            result.put(JSONObject()
                .put("hash", fields[0])
                .put("shortHash", fields[1])
                .put("author", fields[2])
                .put("timestamp", fields[3].toLongOrNull() ?: 0L)
                .put("subject", fields[4])
                .put("refs", fields.getOrElse(5) { "" }))
        }
    }

    fun enrichSnapshot(base: String, remotes: List<NativeGitRemote>, history: JSONArray, hasHead: Boolean): String {
        val snapshot = JSONObject(base)
        val upstreamRemote = snapshot.optString("upstream").substringBefore('/', "")
        val preferred = remotes.firstOrNull { it.name == upstreamRemote }
            ?: remotes.firstOrNull { it.name == "origin" }
            ?: remotes.firstOrNull()
        snapshot.put("hasHead", hasHead)
        snapshot.put("detached", hasHead && snapshot.optString("branch").isBlank())
        snapshot.put("remote", preferred?.name.orEmpty())
        snapshot.put("remoteUrl", preferred?.pushUrl.orEmpty())
        snapshot.put("remotes", JSONArray().also { array ->
            remotes.forEach { remote ->
                array.put(JSONObject()
                    .put("name", remote.name)
                    .put("fetchUrl", remote.fetchUrl)
                    .put("pushUrl", remote.pushUrl))
            }
        })
        snapshot.put("history", history)
        return snapshot.toString()
    }

    fun buildHandoff(
        sourcePath: String,
        sourceBranch: String,
        targetBranch: String,
        remoteName: String,
        remoteUrl: String,
        ahead: Int,
        behind: Int,
        commits: String,
        stat: String,
        files: String,
        dirty: Boolean,
    ): String {
        val cleanUrl = sanitizeRemoteUrl(remoteUrl)
        val prUrl = pullRequestUrl(cleanUrl, targetBranch, sourceBranch)
        val markdown = buildString {
            appendLine("# Pull Request handoff")
            appendLine()
            appendLine("- Source: `$sourceBranch`")
            appendLine("- Target: `$targetBranch`")
            if (remoteName.isNotBlank()) appendLine("- Remote: `$remoteName`${cleanUrl.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}")
            appendLine("- Branch delta: $ahead ahead, $behind behind")
            if (dirty) appendLine("- Warning: the isolated worktree still has uncommitted changes")
            appendLine()
            appendLine("## Commits")
            appendLine()
            appendLine(commits.trim().ifBlank { "No commits unique to this branch." })
            appendLine()
            appendLine("## Changed files")
            appendLine()
            appendLine("```text")
            appendLine(files.trim().ifBlank { "No committed file changes." })
            appendLine("```")
            if (stat.isNotBlank()) {
                appendLine()
                appendLine("## Diff stat")
                appendLine()
                appendLine("```text")
                appendLine(stat.trim())
                appendLine("```")
            }
            if (remoteName.isNotBlank()) {
                appendLine()
                appendLine("## Publish")
                appendLine()
                appendLine("```sh")
                appendLine("git push -u $remoteName $sourceBranch")
                appendLine("```")
            }
            if (prUrl.isNotBlank()) {
                appendLine()
                appendLine("## Create Pull Request")
                appendLine()
                appendLine(prUrl)
            }
        }.trim()
        return JSONObject()
            .put("sourcePath", sourcePath)
            .put("sourceBranch", sourceBranch)
            .put("targetBranch", targetBranch)
            .put("remote", remoteName)
            .put("remoteUrl", cleanUrl)
            .put("ahead", ahead)
            .put("behind", behind)
            .put("dirty", dirty)
            .put("prUrl", prUrl)
            .put("markdown", markdown)
            .toString()
    }

    fun sanitizeRemoteUrl(value: String): String {
        val trimmed = value.trim()
        if (!trimmed.contains("://")) return trimmed
        return runCatching {
            val uri = URI(trimmed)
            URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString()
        }.getOrDefault(trimmed.replace(Regex("(://)[^/@]+@"), "$1"))
    }

    fun pullRequestUrl(remoteUrl: String, targetBranch: String, sourceBranch: String): String {
        val repository = repositoryWebUrl(remoteUrl) ?: return ""
        val host = runCatching { URI(repository).host.orEmpty().lowercase() }.getOrDefault("")
        val target = encode(targetBranch)
        val source = encode(sourceBranch)
        return when {
            host == "github.com" || host.endsWith(".github.com") -> "$repository/compare/$target...$source?expand=1"
            host.contains("gitlab") -> "$repository/-/merge_requests/new?merge_request%5Bsource_branch%5D=$source&merge_request%5Btarget_branch%5D=$target"
            host.contains("gitee") -> "$repository/compare/$target...$source"
            else -> repository
        }
    }

    private fun repositoryWebUrl(remoteUrl: String): String? {
        val sanitized = sanitizeRemoteUrl(remoteUrl).trim()
        if (sanitized.isBlank()) return null
        val host: String
        val path: String
        if (sanitized.contains("://")) {
            val uri = runCatching { URI(sanitized) }.getOrNull() ?: return null
            host = uri.host.orEmpty()
            path = uri.path.orEmpty()
        } else {
            val match = Regex("^(?:[^@]+@)?([^:]+):(.+)$").matchEntire(sanitized) ?: return null
            host = match.groupValues[1]
            path = "/" + match.groupValues[2]
        }
        if (host.isBlank() || path.isBlank()) return null
        return "https://$host/${path.trim('/').removeSuffix(".git")}".trimEnd('/')
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
