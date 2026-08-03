package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeTokenPricingTest {

    @Test
    fun tokenCountFormatting() {
        assertEquals("1.2K", formatNativeTokenCount(1_234))
        assertEquals("1.2K", formatNativeTokenCount(1_200))
        assertEquals("12K", formatNativeTokenCount(12_000))
        assertEquals("1.5M", formatNativeTokenCount(1_500_000))
        assertEquals("12M", formatNativeTokenCount(12_000_000))
        assertEquals("0", formatNativeTokenCount(0))
    }

    @Test
    fun costEstimationPerMillion() {
        assertEquals(1.0, estimateTokenCost(1_000_000, 1.0), 1e-9)
        assertEquals(0.000_125, estimateTokenCost(125, 1.0), 1e-9)
        assertEquals(0.0, estimateTokenCost(0, 10.0), 1e-9)
        assertEquals(0.0, estimateTokenCost(100, 0.0), 1e-9)
    }

    @Test
    fun turnCostIncludesCachedAndReasoningTokens() {
        val usage = NativeTurnUsage(
            inputTokens = 100_000,
            cachedInputTokens = 400_000,
            outputTokens = 10_000,
            reasoningOutputTokens = 5_000,
        )
        val price = NativeModelPrice(inputPerMillion = 1.0, outputPerMillion = 10.0, cachedInputPerMillion = 0.1)
        val expected = 100_000 / 1e6 * 1.0 + 400_000 / 1e6 * 0.1 + 15_000 / 1e6 * 10.0
        assertEquals(expected, estimateTurnCost(usage, price)!!, 1e-9)
    }

    @Test
    fun emptyUsageYieldsNoCost() {
        val usage = NativeTurnUsage()
        assertNull(estimateTurnCost(usage, NativeModelPrice(1.0, 2.0)))
    }

    @Test
    fun costFormatting() {
        assertEquals("$123", formatNativeCost(123.4))
        assertEquals("$1.23", formatNativeCost(1.234))
        assertEquals("$0.012", formatNativeCost(0.0123))
        assertEquals("$0.0001", formatNativeCost(0.0001))
    }

    @Test
    fun defaultPricesResolveExactAndPrefixedModels() {
        assertEquals(1.25, defaultPriceForModel("gpt-5")!!.inputPerMillion, 1e-9)
        assertEquals(10.0, defaultPriceForModel("gpt-5.1-2025-08-07")!!.outputPerMillion, 1e-9)
        assertEquals(3.0, defaultPriceForModel("claude-sonnet-4-5")!!.inputPerMillion, 1e-9)
        assertNull(defaultPriceForModel(""))
        assertNull(defaultPriceForModel("unknown-model-xyz"))
    }
}
