package com.termux.app

import java.util.Locale

/**
 * Token usage formatting and cost estimation (pattern: claudecodeui TokenUsageSummary + cost
 * modal). Pricing is per-million-token; the store keeps user-editable overrides on top of a
 * built-in table for common Codex/Claude models.
 */
internal data class NativeModelPrice(
    val inputPerMillion: Double,
    val outputPerMillion: Double,
    val cachedInputPerMillion: Double = 0.0,
)

/** Smart token count formatting: x.xM / rounded K / plain thousands. */
internal fun formatNativeTokenCount(count: Long): String = when {
    count >= 10_000_000 -> "%.0fM".format(count / 1_000_000.0)
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 10_000 -> "%.0fK".format(count / 1_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> String.format(Locale.US, "%,d", count)
}

internal fun estimateTokenCost(tokens: Long, pricePerMillion: Double): Double =
    if (tokens <= 0 || pricePerMillion <= 0.0) 0.0 else tokens / 1_000_000.0 * pricePerMillion

/** Total estimated cost of one turn, or null when no pricing applies. */
internal fun estimateTurnCost(
    usage: NativeTurnUsage,
    price: NativeModelPrice,
): Double? {
    if (usage.inputTokens + usage.cachedInputTokens + usage.outputTokens + usage.reasoningOutputTokens <= 0) return null
    val total = estimateTokenCost(usage.inputTokens, price.inputPerMillion) +
        estimateTokenCost(usage.cachedInputTokens, price.cachedInputPerMillion) +
        estimateTokenCost(usage.outputTokens + usage.reasoningOutputTokens, price.outputPerMillion)
    return if (total <= 0.0) null else total
}

internal fun formatNativeCost(cost: Double): String = when {
    cost >= 100.0 -> "$%.0f".format(cost)
    cost >= 1.0 -> "$%.2f".format(cost)
    cost >= 0.01 -> "$%.3f".format(cost)
    else -> "$%.4f".format(cost)
}

/** Built-in per-million-token prices; the store can override per model. */
internal val DEFAULT_MODEL_PRICES: Map<String, NativeModelPrice> = mapOf(
    // OpenAI Codex / GPT-5 family (approximate public pricing).
    "gpt-5" to NativeModelPrice(inputPerMillion = 1.25, outputPerMillion = 10.0, cachedInputPerMillion = 0.125),
    "gpt-5-mini" to NativeModelPrice(inputPerMillion = 0.25, outputPerMillion = 2.0, cachedInputPerMillion = 0.025),
    "gpt-5-nano" to NativeModelPrice(inputPerMillion = 0.05, outputPerMillion = 0.4, cachedInputPerMillion = 0.005),
    "codex-mini-latest" to NativeModelPrice(inputPerMillion = 0.25, outputPerMillion = 2.0, cachedInputPerMillion = 0.025),
    "o3" to NativeModelPrice(inputPerMillion = 2.0, outputPerMillion = 8.0, cachedInputPerMillion = 0.5),
    "o4-mini" to NativeModelPrice(inputPerMillion = 1.1, outputPerMillion = 4.4, cachedInputPerMillion = 0.275),
    "gpt-4.1" to NativeModelPrice(inputPerMillion = 2.0, outputPerMillion = 8.0, cachedInputPerMillion = 0.5),
    "gpt-4.1-mini" to NativeModelPrice(inputPerMillion = 0.4, outputPerMillion = 1.6, cachedInputPerMillion = 0.1),
    "gpt-4o" to NativeModelPrice(inputPerMillion = 2.5, outputPerMillion = 10.0, cachedInputPerMillion = 1.25),
    // Anthropic Claude family.
    "claude-sonnet-4-5" to NativeModelPrice(inputPerMillion = 3.0, outputPerMillion = 15.0, cachedInputPerMillion = 0.3),
    "claude-opus-4-1" to NativeModelPrice(inputPerMillion = 15.0, outputPerMillion = 75.0, cachedInputPerMillion = 1.5),
    "claude-3-5-haiku" to NativeModelPrice(inputPerMillion = 1.0, outputPerMillion = 5.0, cachedInputPerMillion = 0.1),
)

internal fun defaultPriceForModel(modelName: String): NativeModelPrice? {
    val normalized = modelName.trim().lowercase()
    if (normalized.isBlank()) return null
    // Exact match first, then a prefix match so "gpt-5.1-2025-..." resolves to the gpt-5 row.
    DEFAULT_MODEL_PRICES[normalized]?.let { return it }
    return DEFAULT_MODEL_PRICES.entries
        .firstOrNull { (key, _) ->
            normalized.startsWith("$key-") || normalized.startsWith("$key.") || normalized.startsWith("$key/")
        }
        ?.value
}
