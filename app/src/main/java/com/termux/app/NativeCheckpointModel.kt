package com.termux.app

internal data class NativeConversationCheckpoint(
    val messageId: String,
    val turnNumber: Int,
    val prompt: String,
    val responsePreview: String,
    val status: String,
)

/** Derives durable checkpoints from persisted conversation turns; no parallel metadata can go stale. */
internal object NativeCheckpointModel {
    fun build(messages: List<NativeChatMessage>, phase: NativeTurnPhase): List<NativeConversationCheckpoint> {
        val userIndices = messages.indices.filter { messages[it].role == NativeChatRole.USER }
        return userIndices.mapIndexed { checkpointIndex, userIndex ->
            val nextUserIndex = userIndices.getOrNull(checkpointIndex + 1) ?: messages.size
            val response = messages.subList((userIndex + 1).coerceAtMost(messages.size), nextUserIndex)
                .lastOrNull { it.role == NativeChatRole.ASSISTANT }
                ?.content.orEmpty().trim()
            val latest = checkpointIndex == userIndices.lastIndex
            val status = when {
                !latest -> "completed"
                phase.active -> "running"
                phase == NativeTurnPhase.FAILED -> "failed"
                response.isBlank() -> "interrupted"
                else -> "completed"
            }
            val prompt = messages[userIndex].content
            NativeConversationCheckpoint(
                messageId = messages[userIndex].id,
                turnNumber = checkpointIndex + 1,
                prompt = prompt,
                responsePreview = response.take(240),
                status = status,
            )
        }
    }
}
