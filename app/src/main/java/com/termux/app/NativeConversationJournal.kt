package com.termux.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * App-owned, bounded conversation journal used only as a recovery layer.
 *
 * Backend transcripts stay authoritative. This journal exists because a provider request can hang
 * or the process can be killed before the CLI writes its rollout/session file. The user's prompt is
 * persisted before the network call, and completed assistant text is appended afterwards.
 */
internal object NativeConversationJournal {
    private const val DIRECTORY = "conversation-journal"
    private const val MAX_FILE_BYTES = 4L * 1024L * 1024L
    private const val MAX_READ_MESSAGES = 4_000
    private const val MAX_OVERLAP_MESSAGES = 64
    private const val WRITER_THREAD_NAME = "NativeConversationJournal"
    private const val WRITE_BARRIER_TIMEOUT_SECONDS = 15L

    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, WRITER_THREAD_NAME).apply { isDaemon = true }
    }

    /**
     * Waits for every append queued before this call. A route switch can race a turn-completion
     * append; reading the file directly would then rebuild history from a truncated or stale tail.
     * This is called from history workers, never from the UI thread.
     */
    private fun awaitPendingWrites(): Boolean {
        if (Thread.currentThread().name == WRITER_THREAD_NAME) return true
        val barrier = CountDownLatch(1)
        if (runCatching { writer.execute(barrier::countDown) }.isFailure) return false
        return runCatching {
            barrier.await(WRITE_BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.onFailure {
            Thread.currentThread().interrupt()
        }.getOrDefault(false)
    }

    private fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).also { it.mkdirs() }

    private fun file(context: Context, threadId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(threadId.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(directory(context), "$digest.jsonl")
    }

    /** Persists a user prompt before it is handed to the backend. */
    fun appendUser(context: Context, threadId: String, message: NativeChatMessage) {
        if (message.role != NativeChatRole.USER) return
        if (threadId.isBlank()) return
        val appContext = context.applicationContext
        // Prompts are the irreplaceable half of a failed provider call. Do this one small append
        // synchronously so the user row is already durable when turn/start is sent. Assistant
        // output stays on the writer executor because it can contain large payloads.
        synchronized(lock) { writeLocked(appContext, threadId, listOf(message), compactBeforeWrite = false) }
        if (file(appContext, threadId).length() >= MAX_FILE_BYTES) {
            writer.execute {
                synchronized(lock) { runCatching { compact(file(appContext, threadId)) } }
            }
        }
    }

    /** Persists completed assistant items for this turn. */
    fun appendAssistant(context: Context, threadId: String, messages: List<NativeChatMessage>) {
        append(context, threadId, messages.filter { message ->
            message.role == NativeChatRole.ASSISTANT &&
                message.content.isNotBlank() &&
                !message.content.startsWith(NATIVE_PROPOSED_PLAN_PREFIX) &&
                !message.content.startsWith(NATIVE_IMPLEMENT_PLAN_DISPLAY_PREFIX)
        })
    }

    private fun append(context: Context, threadId: String, messages: List<NativeChatMessage>) {
        if (threadId.isBlank() || messages.isEmpty()) return
        val appContext = context.applicationContext
        writer.execute {
            synchronized(lock) { writeLocked(appContext, threadId, messages, compactBeforeWrite = true) }
        }
    }

    private fun writeLocked(
        context: Context,
        threadId: String,
        messages: List<NativeChatMessage>,
        compactBeforeWrite: Boolean,
    ) {
        runCatching {
            val target = file(context, threadId)
            if (compactBeforeWrite && target.length() >= MAX_FILE_BYTES) compact(target)
            target.appendText(
                buildString {
                    messages.forEach { message ->
                        append(message.toJournalJson().toString()).append('\n')
                    }
                },
                Charsets.UTF_8,
            )
        }
    }

    private fun NativeChatMessage.toJournalJson(): JSONObject = JSONObject()
        .put("version", 1)
        .put("id", id)
        .put("role", role.name.lowercase())
        .put("content", content)
        .put("timestamp", System.currentTimeMillis())

    /**
     * Merges journal entries into a backend snapshot. The backend wins whenever it already has a
     * matching suffix; the journal only fills a missing tail (or an entirely empty history).
     */
    @JvmStatic
    fun recover(context: Context, threadId: String, server: NativeHistorySnapshot): NativeHistorySnapshot {
        if (threadId.isBlank()) return server
        // A stalled writer means the local tail is not trustworthy yet. Keep the backend snapshot
        // rather than racing a partial JSON line; the next route load can recover the journal.
        if (!awaitPendingWrites()) return server
        val local = read(context, threadId)
        if (local.isEmpty()) return server
        if (server.messages.isEmpty()) {
            return NativeHistorySnapshot(
                messages = local,
                estimatedChars = local.sumOf { it.content.length },
                contentFingerprint = 0L,
            )
        }
        val merged = mergeTail(server.messages, local)
        if (merged.size == server.messages.size) return server
        val appendedChars = merged.drop(server.messages.size).sumOf { it.content.length }
        return server.copy(
            messages = merged,
            estimatedChars = (server.estimatedChars.toLong() + appendedChars).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            contentFingerprint = 0L,
        )
    }

    internal fun mergeTail(
        server: List<NativeChatMessage>,
        local: List<NativeChatMessage>,
    ): List<NativeChatMessage> {
        if (local.isEmpty()) return server
        if (server.isEmpty()) return local
        // Common case 1: the backend persisted an exact prefix, but the newest local tail arrived
        // after its last flush (or the process died before it flushed at all).
        if (server.size <= local.size && sameSequence(server, local.take(server.size))) {
            return server + local.drop(server.size)
        }
        // Common case 2: the journal is a bounded recent window and the backend already has it.
        if (local.size <= server.size && sameSequence(server.takeLast(local.size), local)) {
            return server
        }
        // Partial/truncated overlap: recover a missing journal tail without duplicating rows.
        val maxOverlap = minOf(MAX_OVERLAP_MESSAGES, server.size, local.size)
        for (overlap in maxOverlap downTo 1) {
            if (sameSequence(server.takeLast(overlap), local.take(overlap))) {
                return server + local.drop(overlap)
            }
        }
        // If the two histories do not share a recognizable boundary, prefer the backend snapshot.
        return server
    }

    private fun sameSequence(a: List<NativeChatMessage>, b: List<NativeChatMessage>): Boolean {
        if (a.size != b.size) return false
        for (index in a.indices) {
            if (!sameJournalMessage(a[index], b[index])) return false
        }
        return true
    }

    private fun sameJournalMessage(a: NativeChatMessage, b: NativeChatMessage): Boolean =
        a.role == b.role && a.content.trim() == b.content.trim()

    private fun read(context: Context, threadId: String): List<NativeChatMessage> {
        val source = file(context, threadId)
        if (!source.isFile) return emptyList()
        val tail = ArrayDeque<NativeChatMessage>(MAX_READ_MESSAGES)
        runCatching {
            source.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val json = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                if (json.optInt("version", 1) != 1) return@forEachLine
                val role = when (json.optString("role")) {
                    "user" -> NativeChatRole.USER
                    "assistant" -> NativeChatRole.ASSISTANT
                    else -> return@forEachLine
                }
                val content = json.optString("content")
                if (content.isBlank()) return@forEachLine
                val id = json.optString("id").ifBlank {
                    "journal:${content.hashCode()}:${json.optLong("timestamp", 0L)}"
                }
                tail.addLast(NativeChatMessage(id = id, role = role, content = content, streaming = false))
                while (tail.size > MAX_READ_MESSAGES) tail.removeFirst()
            }
        }
        return tail.toList()
    }

    fun delete(context: Context, threadId: String) {
        if (threadId.isBlank()) return
        val appContext = context.applicationContext
        writer.execute {
            synchronized(lock) {
                runCatching { file(appContext, threadId).delete() }
            }
        }
    }

    private fun compact(target: File) {
        val lines = ArrayDeque<String>(MAX_READ_MESSAGES)
        runCatching {
            target.forEachLine { line ->
                if (line.isNotBlank()) {
                    lines.addLast(line)
                    while (lines.size > MAX_READ_MESSAGES) lines.removeFirst()
                }
            }
            target.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        }
    }
}
