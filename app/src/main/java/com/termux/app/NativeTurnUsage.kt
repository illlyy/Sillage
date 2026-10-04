package com.termux.app

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import kotlin.math.ceil

@Immutable
data class NativeTurnUsage(
    val inputTokens: Long = 0,
    val cachedInputTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningOutputTokens: Long = 0,
    val totalTokens: Long = 0,
    val contextWindow: Long = 0,
    val durationMs: Long = 0,
    val estimated: Boolean = false,
    /** Reliable current-context total when the server exposes a cumulative usage object. */
    val currentContextTokens: Long = 0,
    /** Whether currentContextTokens came from an explicit cumulative/current-context field. */
    val contextUsageReliable: Boolean = false,
    /** Optional server threshold; this is never sent back to app-server by the native UI. */
    val autoCompactTokenLimit: Long = 0,
    /** Breakdown of what currently occupies the window, when the backend reports one. */
    val contextCategories: List<NativeContextCategory> = emptyList(),
) {
    val outputTokensPerSecond: Double
        get() = if (outputTokens <= 0 || durationMs <= 0) 0.0 else outputTokens * 1000.0 / durationMs

    /** 0..1 occupancy of the context window; 0 when either side is unknown. */
    val contextUsedFraction: Double
        get() = if (contextWindow <= 0L || currentContextTokens <= 0L) 0.0
        else (currentContextTokens.toDouble() / contextWindow.toDouble()).coerceIn(0.0, 1.0)

    /** Only trustworthy context numbers should be shown; a missing window is not a zero. */
    val contextOccupancyKnown: Boolean
        get() = contextUsageReliable && currentContextTokens > 0L && contextWindow > 0L
}

/** One row of the context breakdown (system prompt, tools, messages, free space, ...). */
@Immutable
data class NativeContextCategory(val name: String, val tokens: Long)

object NativeTokenUsageParser {
    @JvmStatic
    fun parse(raw: String, durationMs: Long = 0): NativeTurnUsage? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val last = root.optJSONObject("last")
            ?: root.optJSONObject("lastUsage")
            ?: root.optJSONObject("last_usage")
            ?: root.optJSONObject("lastTokenUsage")
            ?: root.optJSONObject("last_token_usage")
            ?: root.optJSONObject("current")
            ?: root.optJSONObject("currentUsage")
            ?: root.optJSONObject("current_usage")
            ?: root.optJSONObject("usage")
            ?: root
        fun value(source: JSONObject, vararg keys: String): Long {
            keys.forEach { key ->
                val item = source.opt(key)
                when (item) {
                    is Number -> return item.toLong().coerceAtLeast(0)
                    is String -> item.toLongOrNull()?.let { return it.coerceAtLeast(0) }
                }
            }
            return 0
        }
        fun bool(source: JSONObject, vararg keys: String): Boolean {
            keys.forEach { key ->
                if (!source.has(key)) return@forEach
                val value = source.opt(key)
                when (value) {
                    is Boolean -> if (value) return true
                    is String -> if (value.equals("true", true) || value.equals("estimated", true)) return true
                    is Number -> if (value.toInt() != 0) return true
                }
            }
            return false
        }
        fun flag(source: JSONObject, vararg keys: String): Boolean? {
            keys.forEach { key ->
                if (!source.has(key)) return@forEach
                return when (val value = source.opt(key)) {
                    is Boolean -> value
                    is String -> when {
                        value.equals("true", true) || value.equals("reliable", true) -> true
                        value.equals("false", true) || value.equals("unreliable", true) -> false
                        else -> null
                    }
                    is Number -> value.toInt() != 0
                    null -> null
                    else -> null
                }
            }
            return null
        }
        // Claude reports its cached input as `cache_read_input_tokens` (+ `cache_creation_input_tokens`
        // for tokens written to the cache this turn). None of those matched the old key list, so the
        // cache count was permanently 0 and "输入与缓存" understated what the model actually read.
        // Cache writes are billed closer to plain input than to a cache hit, so they are counted as
        // input rather than silently dropped.
        val cacheRead = value(last, "cacheReadInputTokens", "cache_read_input_tokens", "cachedInputTokens", "cached_input_tokens", "cached_tokens")
        val cacheWrite = value(last, "cacheCreationInputTokens", "cache_creation_input_tokens")
        val cached = cacheRead + cacheWrite
        val input = value(last, "inputTokens", "input_tokens", "prompt_tokens") + cacheWrite
        val output = value(last, "outputTokens", "output_tokens", "completion_tokens")
        val reasoning = value(last, "reasoningOutputTokens", "reasoning_output_tokens")
        val total = value(last, "totalTokens", "total_tokens").takeIf { it > 0 }
            ?: (input + output + reasoning)
        val context = (value(root, "modelContextWindow", "model_context_window", "contextWindow", "context_window")
            .takeIf { it > 0L } ?: value(last, "modelContextWindow", "model_context_window", "contextWindow", "context_window"))
        val autoCompactLimit = value(
            root,
            "autoCompactTokenLimit",
            "auto_compact_token_limit",
            "autoCompactTokenLimitTokens",
            "auto_compact_token_limit_tokens",
            "modelAutoCompactTokenLimit",
            "model_auto_compact_token_limit",
        ).takeIf { it > 0L } ?: value(
            last,
            "autoCompactTokenLimit",
            "auto_compact_token_limit",
            "modelAutoCompactTokenLimit",
            "model_auto_compact_token_limit",
        )
        val cumulative = root.optJSONObject("total")
            ?: root.optJSONObject("totalUsage")
            ?: root.optJSONObject("total_usage")
            ?: root.optJSONObject("totalTokenUsage")
            ?: root.optJSONObject("total_token_usage")
            ?: root.optJSONObject("cumulative")
            ?: root.optJSONObject("cumulativeUsage")
            ?: root.optJSONObject("cumulative_usage")
        val explicitCurrent = value(root, "contextTokens", "context_tokens", "currentContextTokens", "current_context_tokens")
            .takeIf { it > 0L }
            ?: value(last, "contextTokens", "context_tokens", "currentContextTokens", "current_context_tokens")
        val currentContext = when {
            explicitCurrent > 0L -> explicitCurrent
            cumulative != null -> value(cumulative, "contextTokens", "context_tokens", "totalTokens", "total_tokens", "inputTokens", "input_tokens")
            else -> 0L
        }
        val contextUsageReliable = flag(
            root,
            "contextUsageReliable", "context_usage_reliable", "usageReliable", "usage_reliable",
            "contextReliable", "context_reliable",
        ) ?: flag(
            last,
            "contextUsageReliable", "context_usage_reliable", "usageReliable", "usage_reliable",
            "contextReliable", "context_reliable",
        ) ?: (explicitCurrent > 0L || (cumulative != null && currentContext > 0L))
        val estimated = bool(root, "estimated", "isEstimated", "usageEstimated", "usage_estimated") ||
            bool(last, "estimated", "isEstimated", "usageEstimated", "usage_estimated")
        if (input + cached + output + reasoning + total + context + currentContext + autoCompactLimit <= 0) return null
        return NativeTurnUsage(
            inputTokens = input,
            cachedInputTokens = cached,
            outputTokens = output,
            reasoningOutputTokens = reasoning,
            totalTokens = total,
            contextWindow = context,
            durationMs = durationMs.coerceAtLeast(0),
            estimated = estimated,
            currentContextTokens = currentContext,
            contextUsageReliable = contextUsageReliable,
            autoCompactTokenLimit = autoCompactLimit,
            contextCategories = contextCategories(root).ifEmpty { contextCategories(last) },
        )
    }

    /**
     * Context breakdown, when the backend sends one (Claude's `get_context_usage` answer). Kept in
     * the backend's own order so the detail dialog can show it as-is.
     */
    private fun contextCategories(source: JSONObject): List<NativeContextCategory> {
        val array = source.optJSONArray("contextCategories")
            ?: source.optJSONArray("context_categories")
            ?: return emptyList()
        val result = ArrayList<NativeContextCategory>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name")
            if (name.isBlank()) continue
            result.add(NativeContextCategory(name, item.optLong("tokens", 0L).coerceAtLeast(0L)))
        }
        return result
    }

    @JvmStatic
    fun estimate(outputText: String, durationMs: Long): NativeTurnUsage {
        val output = ceil(outputText.length / 4.0).toLong().coerceAtLeast(1)
        return NativeTurnUsage(outputTokens = output, totalTokens = output, durationMs = durationMs.coerceAtLeast(1), estimated = true)
    }
}
