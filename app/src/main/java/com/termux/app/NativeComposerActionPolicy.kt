package com.termux.app

internal enum class NativeComposerAction {
    DISABLED,
    SEND,
    STOP,
}

internal fun nativeComposerAction(
    inputText: String,
    hasAttachments: Boolean,
    enabled: Boolean,
    loading: Boolean,
): NativeComposerAction {
    val hasDraft = inputText.isNotBlank() || hasAttachments
    return when {
        loading && !hasDraft -> NativeComposerAction.STOP
        enabled && hasDraft -> NativeComposerAction.SEND
        else -> NativeComposerAction.DISABLED
    }
}
