package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTaskNotificationPolicyTest {
    @Test
    fun attentionEventsOutrankCompletion() {
        assertTrue(NativeTaskNotificationPolicy.priority(NativeTaskNotificationPolicy.ANSWER) > NativeTaskNotificationPolicy.priority(NativeTaskNotificationPolicy.COMPLETED))
        assertTrue(NativeTaskNotificationPolicy.priority(NativeTaskNotificationPolicy.APPROVAL) > NativeTaskNotificationPolicy.priority(NativeTaskNotificationPolicy.PLAN))
    }

    @Test
    fun fingerprintChangesForNewRequestToken() {
        val first = NativeTaskNotificationPolicy.fingerprint("thread", "answer", "request-1")
        val second = NativeTaskNotificationPolicy.fingerprint("thread", "answer", "request-2")
        assertTrue(first != second)
        assertEquals(first, NativeTaskNotificationPolicy.fingerprint("thread", "answer", "request-1"))
    }
}
