package com.termux.app

/** Inline submit hint (pattern: claudecodeui ChatComposer submit hints). */
internal fun nativeComposerSubmitHint(
    loading: Boolean,
    followUpAction: NativeFollowUpSubmitAction,
    language: String,
): String = when {
    !loading -> nativeText(language, "Enter 发送", "Enter to send")
    followUpAction == NativeFollowUpSubmitAction.STEER -> nativeText(language, "Enter 引导当前回答", "Enter steers the current answer")
    else -> nativeText(language, "Enter 排队发送", "Enter to queue")
}
