package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeComposerHintsTest {

    @Test
    fun idleHintIsPlainSend() {
        assertEquals("Enter 发送", nativeComposerSubmitHint(loading = false, followUpAction = NativeFollowUpSubmitAction.STEER, language = "zh"))
        assertEquals("Enter to send", nativeComposerSubmitHint(loading = false, followUpAction = NativeFollowUpSubmitAction.QUEUE, language = "en"))
    }

    @Test
    fun loadingWithSteerHintsSteering() {
        assertEquals("Enter 引导当前回答", nativeComposerSubmitHint(loading = true, followUpAction = NativeFollowUpSubmitAction.STEER, language = "zh"))
        assertEquals("Enter steers the current answer", nativeComposerSubmitHint(loading = true, followUpAction = NativeFollowUpSubmitAction.STEER, language = "en"))
    }

    @Test
    fun loadingWithQueueHintsQueuing() {
        assertEquals("Enter 排队发送", nativeComposerSubmitHint(loading = true, followUpAction = NativeFollowUpSubmitAction.QUEUE, language = "zh"))
        assertEquals("Enter to queue", nativeComposerSubmitHint(loading = true, followUpAction = NativeFollowUpSubmitAction.QUEUE, language = "en"))
    }
}
