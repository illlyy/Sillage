package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeComposerActionPolicyTest {
    @Test
    fun activeTurnSwitchesFromSendToStopAsSoonAsDraftIsCleared() {
        assertEquals(
            NativeComposerAction.SEND,
            nativeComposerAction("Guide the current answer", hasAttachments = false, enabled = true, loading = true),
        )
        assertEquals(
            NativeComposerAction.STOP,
            nativeComposerAction("", hasAttachments = false, enabled = true, loading = true),
        )
    }

    @Test
    fun emptyIdleComposerCannotSendButAttachmentsCan() {
        assertEquals(
            NativeComposerAction.DISABLED,
            nativeComposerAction("   ", hasAttachments = false, enabled = true, loading = false),
        )
        assertEquals(
            NativeComposerAction.SEND,
            nativeComposerAction("", hasAttachments = true, enabled = true, loading = false),
        )
    }

    @Test
    fun unavailableComposerNeverSendsDraft() {
        assertEquals(
            NativeComposerAction.DISABLED,
            nativeComposerAction("stale draft", hasAttachments = false, enabled = false, loading = false),
        )
    }
}
