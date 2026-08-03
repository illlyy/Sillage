package com.termux.app

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * User-editable per-model token pricing, layered on top of [DEFAULT_MODEL_PRICES].
 * Stored as one JSON map in the `codex_mobile` preferences file.
 */
internal class NativeTokenPriceStore(
    private val preferences: SharedPreferences,
) {
    fun priceForModel(modelName: String): NativeModelPrice? {
        val overrides = readAll()
        overrides[modelName.trim().lowercase()]?.let { return it }
        return defaultPriceForModel(modelName)
    }

    fun readAll(): Map<String, NativeModelPrice> {
        val raw = preferences.getString(KEY, null) ?: return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return buildMap {
            json.keys().forEach { key ->
                val entry = json.optJSONObject(key) ?: return@forEach
                val input = entry.optDouble("inputPerMillion", entry.optDouble("input", -1.0))
                val output = entry.optDouble("outputPerMillion", entry.optDouble("output", -1.0))
                val cached = entry.optDouble("cachedInputPerMillion", entry.optDouble("cached", 0.0))
                if (input >= 0.0 && output >= 0.0) {
                    put(key, NativeModelPrice(input, output, cached))
                }
            }
        }
    }

    fun write(modelName: String, price: NativeModelPrice) {
        val all = readAll().toMutableMap()
        all[modelName.trim().lowercase()] = price
        persist(all)
    }

    fun remove(modelName: String) {
        val all = readAll().toMutableMap()
        all.remove(modelName.trim().lowercase())
        persist(all)
    }

    private fun persist(all: Map<String, NativeModelPrice>) {
        val json = JSONObject()
        all.forEach { (model, price) ->
            json.put(
                model,
                JSONObject()
                    .put("inputPerMillion", price.inputPerMillion)
                    .put("outputPerMillion", price.outputPerMillion)
                    .put("cachedInputPerMillion", price.cachedInputPerMillion),
            )
        }
        preferences.edit().putString(KEY, json.toString()).apply()
    }

    companion object {
        private const val KEY = "native_token_prices_v1"
    }
}
