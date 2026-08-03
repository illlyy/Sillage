package com.termux.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * Fetches the model list from a Claude-compatible endpoint (`GET {base}/v1/models`),
 * honoring the profile's auth mode: Bearer token (Authorization header) for relay
 * gateways or x-api-key for the official API. Mirrors the Codex editor's catalog fetch.
 */
internal object ClaudeModelFetcher {
    data class Result(val models: List<String>? = null, val error: String? = null) {
        val ok: Boolean get() = models != null
    }

    private val OFFICIAL_MODELS = listOf(
        "claude-sonnet-5", "claude-opus-5", "claude-haiku-4-5", "claude-fable-5",
    )

    fun officialModels(): List<String> = OFFICIAL_MODELS

    fun fetch(context: Context, baseUrl: String, apiKey: String, apiKeyField: String): Result {
        if (apiKey.isBlank()) return Result(error = "API Key 为空")
        val base = baseUrl.trim().trimEnd('/').ifBlank { "https://api.anthropic.com" }
        var connection: HttpURLConnection? = null
        return try {
            val endpoints = listOf(
                URL("$base/v1/models"),
                URL("$base/models"),
            )
            var lastError: Exception? = null
            for (endpoint in endpoints) {
                try {
                    connection = open(context, endpoint)
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 12_000
                    connection.readTimeout = 12_000
                    if (apiKeyField == ClaudeSettingsWriter.FIELD_API_KEY) {
                        connection.setRequestProperty("x-api-key", apiKey)
                        connection.setRequestProperty("anthropic-version", "2023-06-01")
                    } else {
                        connection.setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    connection.setRequestProperty("Content-Type", "application/json")
                    val status = connection.responseCode
                    val body = (if (status >= 400) connection.errorStream else connection.inputStream)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (status >= 400) {
                        lastError = java.io.IOException("HTTP $status${if (body.isBlank()) "" else ": ${body.take(300)}"}")
                        continue
                    }
                    val models = parseModels(body)
                    if (models.isNotEmpty()) return Result(models = models)
                    lastError = java.io.IOException("响应中没有模型列表")
                } finally {
                    connection?.disconnect()
                    connection = null
                }
            }
            Result(error = lastError?.message ?: "无法获取模型")
        } catch (error: Exception) {
            Result(error = error.message ?: error.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parseModels(body: String): List<String> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val data = root.optJSONArray("data") ?: root.optJSONArray("models") ?: return emptyList()
        val result = ArrayList<String>()
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index)
            if (item != null) {
                val id = item.optString("id").ifBlank { item.optString("name") }
                if (id.isNotBlank()) result.add(id)
            } else {
                val id = data.optString(index)
                if (id.isNotBlank()) result.add(id)
            }
        }
        return result.distinct().sortedBy { it.lowercase() }
    }

    private fun open(context: Context, url: URL): HttpURLConnection {
        val prefs = context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE)
        return if (prefs.getBoolean("mihomo_route_api", false)) {
            val manager = MihomoManager.get(context)
            if (!manager.isInstalled) throw java.io.IOException("已开启代理路由，但 Mihomo 尚未安装")
            if (!manager.isRunning) manager.start()
            url.openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", manager.mixedPort()))) as HttpURLConnection
        } else {
            url.openConnection() as HttpURLConnection
        }
    }
}
