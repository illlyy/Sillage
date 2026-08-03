package com.termux.app

import androidx.compose.runtime.Immutable
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class NativeChatRole {
    USER,
    ASSISTANT,
    ACTIVITY,
    ERROR,
}

internal enum class NativeTurnPhase(val active: Boolean) {
    IDLE(false), WAITING(true), REASONING(true), TOOL_RUNNING(true), ANSWERING(true), STOPPING(true), COMPLETED(false), FAILED(false)
}

internal const val NATIVE_PROPOSED_PLAN_PREFIX = "PROPOSED_PLAN|"
internal const val NATIVE_IMPLEMENT_PLAN_DISPLAY_PREFIX = "IMPLEMENT_PLAN|"

internal const val NATIVE_HIDE_STATUS_BAR_PREFERENCE = "native_hide_status_bar_v1"
internal fun encodeNativeProposedPlan(text: String): String = NATIVE_PROPOSED_PLAN_PREFIX + text
internal fun decodeNativeProposedPlan(content: String): String = content.substringAfter('|')
internal fun encodeNativeImplementPlan(text: String): String = NATIVE_IMPLEMENT_PLAN_DISPLAY_PREFIX +
    NativeBase64.encode(text.toByteArray(Charsets.UTF_8))
internal fun decodeNativeImplementPlan(content: String): String = runCatching {
    String(NativeBase64.decode(content.substringAfter('|')), Charsets.UTF_8)
}.getOrDefault("")
internal fun encodeNativeUserInputAnswers(answers: Map<String, String>): String = JSONObject().also { result ->
    answers.forEach { (id, answer) ->
        answer.trim().takeIf { it.isNotBlank() }?.let { value ->
            result.put(id, JSONObject().put("answers", JSONArray().put(value)))
        }
    }
}.toString()

@Immutable
data class NativeAttachment(val name: String, val path: String, val image: Boolean)

internal enum class NativeFollowUpSubmitAction {
    STEER, QUEUE;

    companion object {
        fun from(value: String?): NativeFollowUpSubmitAction =
            if (value.equals("queue", ignoreCase = true)) QUEUE else STEER
    }
}

@Immutable
internal data class NativeQueuedFollowUp(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val attachments: List<NativeAttachment>,
    val skills: List<NativeSkill>,
    val model: String,
    val effort: String,
    val mode: String,
)

@Immutable
internal data class NativeSubmitResult(
    val accepted: Boolean,
    val messageId: String? = null,
    val queued: Boolean = false,
)

@Immutable
data class NativeSkill(val name: String, val description: String, val path: String)

@Immutable
internal data class NativeModelOption(val id: String, val name: String, val efforts: List<String>, val defaultEffort: String)

@Immutable
internal data class NativeConversation(
    val threadId: String,
    val title: String,
    val state: String,
    val projectPath: String,
    val favorite: Boolean,
    val attention: String = "",
) { val projectName: String get() = projectPath.trimEnd('/').substringAfterLast('/').ifBlank { "无项目" } }

@Immutable
data class NativeChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: NativeChatRole,
    val content: String,
    val streaming: Boolean = false,
    val revealStartedAt: Long = 0L,
    val finalOnlyReveal: Boolean = false,
    val usage: NativeTurnUsage? = null,
    val skills: List<NativeSkill> = emptyList(),
    val attachments: List<NativeAttachment> = emptyList(),
    // True when this durable ACTIVITY row was just sealed from a live turn (turn completion). The
    // historical card enters expanded and auto-collapses with animation instead of popping in.
    val enterExpanded: Boolean = false,
)
