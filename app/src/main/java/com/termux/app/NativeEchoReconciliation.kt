package com.termux.app

import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID

/**
 * Optimistic user-echo reconciliation (pattern: claudecodeui sessionMessageReconciliation.ts).
 *
 * User messages are added locally with a `local_`-prefixed id. When a history snapshot
 * arrives, each pending echo claims at most one server row with the same fingerprint
 * (trimmed text + attachment counts), one-to-one, so repeated identical sends never claim a
 * single server row twice. Echoes without a server copy yet (JSONL indexing lag) survive the
 * refresh instead of flashing away.
 */
internal const val NATIVE_ECHO_ID_PREFIX = "local_"

internal fun nativeEchoId(): String = NATIVE_ECHO_ID_PREFIX + UUID.randomUUID()

internal data class NativeEchoFingerprint(
    val trimmedText: String,
    val imageCount: Int,
    val fileCount: Int,
)

internal fun nativeEchoFingerprint(message: NativeChatMessage): NativeEchoFingerprint? {
    if (message.role != NativeChatRole.USER) return null
    val images = message.attachments.count { it.image }
    return NativeEchoFingerprint(
        trimmedText = message.content.trim(),
        imageCount = images,
        fileCount = message.attachments.size - images,
    )
}

internal data class NativeEchoReconcileResult(
    val merged: List<NativeChatMessage>,
    val claimedEchoIds: Set<String>,
)

internal fun reconcileNativeEchoes(
    echoes: List<NativeChatMessage>,
    serverMessages: List<NativeChatMessage>,
): NativeEchoReconcileResult {
    if (echoes.isEmpty()) return NativeEchoReconcileResult(serverMessages, emptySet())
    val serverRowsByFingerprint = LinkedHashMap<NativeEchoFingerprint, MutableList<NativeChatMessage>>()
    serverMessages.filter { it.role == NativeChatRole.USER }.forEach { row ->
        nativeEchoFingerprint(row)?.let { fingerprint ->
            serverRowsByFingerprint.getOrPut(fingerprint) { mutableListOf() }.add(row)
        }
    }
    val claimedEchoIds = LinkedHashSet<String>()
    val unclaimed = mutableListOf<NativeChatMessage>()
    for (echo in echoes) {
        val fingerprint = nativeEchoFingerprint(echo) ?: continue
        val rows = serverRowsByFingerprint[fingerprint]
        val row = rows?.firstOrNull()
        if (row != null) {
            // One-to-one claim: each echo consumes exactly one server row.
            rows.removeAt(0)
            claimedEchoIds.add(echo.id)
        } else {
            unclaimed.add(echo)
        }
    }
    // Unclaimed echoes (not yet indexed server-side) stay appended; claimed ones are dropped
    // because the server row now represents that turn.
    return NativeEchoReconcileResult(serverMessages + unclaimed, claimedEchoIds)
}
