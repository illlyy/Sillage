package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCompactionPolicyTest {
    @Test
    fun fallbackTriggersAtConfiguredNinetyPercent() {
        val policy = NativeCompactionPolicy(NativeCompactionSettings(true, 90))
        val below = input(used = 89_999)
        val at = input(used = 90_000)
        assertFalse(policy.evaluate(below).shouldTrigger)
        val decision = policy.evaluate(at)
        assertTrue(decision.shouldTrigger)
        assertEquals(90_000L, decision.threshold)
        assertEquals(NativeCompactionTriggerSource.FALLBACK, decision.source)
    }

    @Test
    fun serverThresholdWinsOverFallbackPercent() {
        val policy = NativeCompactionPolicy(NativeCompactionSettings(true, 90))
        val decision = policy.evaluate(input(used = 80_000).copy(serverAutoCompactTokenLimit = 75_000))
        assertTrue(decision.shouldTrigger)
        assertEquals(75_000L, decision.threshold)
        assertEquals(NativeCompactionTriggerSource.SERVER, decision.source)
    }

    @Test
    fun unknownAndEstimatedUsageNeverTrigger() {
        val policy = NativeCompactionPolicy()
        assertEquals(NativeCompactionSkipReason.NO_CONTEXT_WINDOW, policy.evaluate(input(90_000).copy(contextWindow = 0)).skipReason)
        assertEquals(NativeCompactionSkipReason.ESTIMATED_USAGE, policy.evaluate(input(90_000).copy(estimated = true)).skipReason)
        assertEquals(NativeCompactionSkipReason.NO_RELIABLE_USAGE, policy.evaluate(input(90_000).copy(usageReliable = false)).skipReason)
    }

    @Test
    fun requestIsSuppressedForTurnUntilHysteresisReleases() {
        val policy = NativeCompactionPolicy()
        val value = input(92_000)
        assertTrue(policy.evaluate(value).shouldTrigger)
        policy.markRequested("thread", "turn", 1_000)
        assertFalse(policy.evaluate(value.copy(nowMs = 1_100)).shouldTrigger)
        policy.markStarted("thread", "turn")
        policy.markCompleted("thread", "turn", usedTokensAfter = 70_000, contextWindow = 100_000)
        assertTrue(policy.evaluate(value.copy(nowMs = 20_000)).shouldTrigger)
    }

    @Test
    fun missingLifecycleDoesNotAllowSecondRequestAfterWindow() {
        val policy = NativeCompactionPolicy()
        val value = input(92_000)
        assertTrue(policy.evaluate(value).shouldTrigger)
        policy.markRequested("thread", "turn", 1_000)
        assertFalse(policy.evaluate(value.copy(nowMs = 20_000)).shouldTrigger)
        assertEquals(NativeCompactionSkipReason.ALREADY_REQUESTED, policy.lastDecision()?.skipReason)
    }

    @Test
    fun missingPostCompactionUsageDoesNotReleaseHysteresis() {
        val policy = NativeCompactionPolicy()
        val value = input(95_000)
        assertTrue(policy.evaluate(value).shouldTrigger)
        policy.markRequested("thread", "turn", 1_000)
        policy.markStarted("thread", "turn")
        policy.markCompleted("thread", "turn", usedTokensAfter = 0, contextWindow = 100_000)
        assertEquals(
            NativeCompactionSkipReason.ALREADY_REQUESTED,
            policy.evaluate(value.copy(nowMs = 5_000)).skipReason,
        )
    }

    @Test
    fun laterLowUsageUpdateReleasesCompletedTurnLatch() {
        val policy = NativeCompactionPolicy()
        val high = input(92_000)
        assertTrue(policy.evaluate(high).shouldTrigger)
        policy.markRequested("thread", "turn", 1_000)
        policy.markStarted("thread", "turn")
        policy.markCompleted("thread", "turn", usedTokensAfter = 92_000, contextWindow = 100_000)
        assertFalse(policy.evaluate(input(80_000).copy(nowMs = 2_000)).shouldTrigger)
        assertEquals(NativeCompactionSkipReason.SERVER_THRESHOLD_NOT_REACHED, policy.lastDecision()?.skipReason)
        assertTrue(policy.evaluate(input(92_000).copy(nowMs = 3_000)).shouldTrigger)
    }

    @Test
    fun failedLifecycleReleasesLatchForARealRetry() {
        val policy = NativeCompactionPolicy()
        val high = input(92_000)
        assertTrue(policy.evaluate(high).shouldTrigger)
        policy.markRequested("thread", "turn", 1_000)
        policy.markStarted("thread", "turn")
        policy.markFailed("thread", "turn")
        assertTrue(policy.evaluate(high.copy(nowMs = 3_000)).shouldTrigger)
    }

    @Test
    fun serverThresholdCanBeUsedWhenCatalogWindowIsUnknown() {
        val policy = NativeCompactionPolicy(NativeCompactionSettings(enabled = true, fallbackPercent = 90))
        val decision = policy.evaluate(
            NativeCompactionPolicyInput(
                threadId = "t",
                turnId = "turn",
                contextWindow = 0,
                usedTokens = 100,
                serverAutoCompactTokenLimit = 90,
                usageReliable = true,
            ),
        )
        assertTrue(decision.shouldTrigger)
        assertEquals(NativeCompactionTriggerSource.SERVER, decision.source)
    }

    @Test
    fun estimatedOrMissingCurrentContextNeverTriggersFallback() {
        val policy = NativeCompactionPolicy()
        val estimated = policy.evaluate(
            NativeCompactionPolicyInput("t", "turn", 100, 95, estimated = true),
        )
        val missing = policy.evaluate(
            NativeCompactionPolicyInput("t", "turn", 100, 95, usageReliable = false),
        )
        assertEquals(NativeCompactionSkipReason.ESTIMATED_USAGE, estimated.skipReason)
        assertEquals(NativeCompactionSkipReason.NO_RELIABLE_USAGE, missing.skipReason)
    }

    private fun input(used: Long) = NativeCompactionPolicyInput(
        threadId = "thread",
        turnId = "turn",
        contextWindow = 100_000,
        usedTokens = used,
        nowMs = 1_000,
    )
}
