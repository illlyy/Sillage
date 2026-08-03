package com.termux.app

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Full-text conversation search (pattern: claudecodeui useSessionMessageSearch). Scans the
 * Codex session JSONL store for query hits, extracts bounded snippets, and caps work per file
 * so a huge transcript cannot stall the search. Pure functions are unit-testable; the engine
 * runs on the default dispatcher with cancellation support.
 */
internal data class NativeSearchHit(
    val threadId: String,
    val lineNumber: Int,
    val snippet: String,
    /** The full matched line; used to locate the message in the conversation for jump-to. */
    val content: String,
)

/** Window around the first query occurrence; ellipses mark clipping at either end. */
internal fun buildNativeSearchSnippet(line: String, query: String, radius: Int = 60): String {
    if (radius <= 0) return line
    val lower = line.lowercase()
    val index = lower.indexOf(query.lowercase())
    if (index < 0) return if (line.length <= radius * 2) line else line.take(radius * 2) + "…"
    val start = (index - radius).coerceAtLeast(0)
    val end = (index + query.length + radius).coerceAtMost(line.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < line.length) "…" else ""
    return prefix + line.substring(start, end).trim() + suffix
}

/** Scans one session JSONL file, bounded by [maxHitsPerFile]; stops reading once saturated. */
internal fun searchNativeSessionFile(
    file: File,
    threadId: String,
    query: String,
    maxHitsPerFile: Int = 20,
): List<NativeSearchHit> {
    val normalized = query.trim().lowercase()
    if (normalized.length < 2 || !file.isFile) return emptyList()
    val hits = ArrayList<NativeSearchHit>(minOf(maxHitsPerFile, 8))
    runCatching {
        file.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
            var lineNumber = 0
            while (hits.size < maxHitsPerFile) {
                val line = reader.readLine() ?: break
                lineNumber++
                if (line.lowercase().contains(normalized)) {
                    hits.add(
                        NativeSearchHit(
                            threadId = threadId,
                            lineNumber = lineNumber,
                            snippet = buildNativeSearchSnippet(line, normalized),
                            content = line.trim().take(2_000),
                        ),
                    )
                }
            }
        }
    }
    return hits
}

internal class NativeConversationSearchEngine(
    private val sessionsRoot: () -> File?,
) {
    /**
     * Searches all session files for [query]. [maxFiles] bounds the directory scan; progress
     * reports (scanned, total) let the UI show a live counter. Cancellation is cooperative.
     */
    suspend fun search(
        query: String,
        maxHitsPerFile: Int = 20,
        maxFiles: Int = 300,
        progress: (scanned: Int, total: Int) -> Unit = { _, _ -> },
    ): List<NativeSearchHit> = withContext(Dispatchers.Default) {
        val root = sessionsRoot() ?: return@withContext emptyList()
        if (!root.isDirectory) return@withContext emptyList()
        val files = ArrayList<Pair<File, String>>(maxFiles)
        collectSessionFiles(root, files, maxFiles)
        val total = files.size
        val results = ArrayList<NativeSearchHit>()
        files.forEachIndexed { index, (file, threadId) ->
            coroutineContext.ensureActive()
            results.addAll(searchNativeSessionFile(file, threadId, query, maxHitsPerFile))
            if (index % 8 == 0 || index == total - 1) progress(index + 1, total)
        }
        results
    }

    /** Recursive scan; a session file is `*-<threadId>.jsonl` under the sessions root. */
    private fun collectSessionFiles(directory: File, out: MutableList<Pair<File, String>>, maxFiles: Int) {
        if (out.size >= maxFiles) return
        val children = directory.listFiles() ?: return
        for (child in children) {
            if (out.size >= maxFiles) return
            if (child.isDirectory) {
                collectSessionFiles(child, out, maxFiles)
                continue
            }
            val name = child.name
            if (!name.endsWith(".jsonl")) continue
            val threadId = name.removeSuffix(".jsonl").substringAfterLast('-')
            if (threadId.length in 2..128) out.add(child to threadId)
        }
    }
}
