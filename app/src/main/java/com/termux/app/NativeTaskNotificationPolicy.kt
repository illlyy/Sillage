package com.termux.app

object NativeTaskNotificationPolicy {
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val ANSWER = "answer"
    const val APPROVAL = "approval"
    const val PLAN = "plan"
    const val RESUME = "resume"

    fun fingerprint(threadId: String, kind: String, token: String): String =
        "$threadId|$kind|${token.trim().take(240)}"

    fun priority(kind: String): Int = when (kind) {
        ANSWER -> 5
        APPROVAL -> 4
        FAILED, RESUME -> 3
        PLAN -> 2
        COMPLETED -> 1
        else -> 0
    }
}
