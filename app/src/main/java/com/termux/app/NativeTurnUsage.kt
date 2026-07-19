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
) {
    val outputTokensPerSecond: Double
        get() = if (outputTokens <= 0 || durationMs <= 0) 0.0 else outputTokens * 1000.0 / durationMs
}

object NativeTokenUsageParser {
    @JvmStatic
    fun parse(raw: String, durationMs: Long = 0): NativeTurnUsage? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val last = root.optJSONObject("last")
            ?: root.optJSONObject("lastUsage")
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
        val input = value(last, "inputTokens", "input_tokens", "prompt_tokens")
        val cached = value(last, "cachedInputTokens", "cached_input_tokens", "cached_tokens")
        val output = value(last, "outputTokens", "output_tokens", "completion_tokens")
        val reasoning = value(last, "reasoningOutputTokens", "reasoning_output_tokens")
        val total = value(last, "totalTokens", "total_tokens").takeIf { it > 0 }
            ?: (input + output + reasoning)
        val context = value(root, "modelContextWindow", "model_context_window", "contextWindow")
        if (input + cached + output + reasoning + total + context <= 0) return null
        return NativeTurnUsage(input, cached, output, reasoning, total, context, durationMs.coerceAtLeast(0), estimated = false)
    }

    @JvmStatic
    fun estimate(outputText: String, durationMs: Long): NativeTurnUsage {
        val output = ceil(outputText.length / 4.0).toLong().coerceAtLeast(1)
        return NativeTurnUsage(outputTokens = output, totalTokens = output, durationMs = durationMs.coerceAtLeast(1), estimated = true)
    }
}
