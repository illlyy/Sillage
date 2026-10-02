package com.termux.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorRetryPredicateTest {

    @Test
    fun `retry prefixes detected`() {
        assertTrue(isRetryingErrorMessage("正在重试第 2 次…"))
        assertTrue(isRetryingErrorMessage("目标自动重试中"))
        assertTrue(isRetryingErrorMessage("Retrying request…"))
        assertTrue(isRetryingErrorMessage("  正在重试"))
    }

    @Test
    fun `normal errors not retrying`() {
        assertFalse(isRetryingErrorMessage("网络连接失败"))
        assertFalse(isRetryingErrorMessage("Error: model not found"))
        assertFalse(isRetryingErrorMessage(""))
    }
}
