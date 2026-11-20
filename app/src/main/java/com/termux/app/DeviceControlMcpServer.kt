package com.termux.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.security.SecureRandom

internal class DeviceControlMcpServer private constructor(
    private val stopServer: () -> Unit,
    val endpoint: String,
) {
    fun stop() {
        runCatching(stopServer)
    }

    companion object {
        fun start(): DeviceControlMcpServer {
            val port = ServerSocket(0).use { it.localPort }
            val token = newToken()
            val engine = embeddedServer(CIO, host = "127.0.0.1", port = port) {
                routing {
                    post("/mcp/{token}") {
                        val supplied = call.parameters["token"].orEmpty()
                        if (supplied != token) {
                            call.respondText("Not found", status = HttpStatusCode.NotFound)
                            return@post
                        }
                        if (!validHost(call.request.headers[HttpHeaders.Host])) {
                            call.respondText("Invalid host", status = HttpStatusCode.Forbidden)
                            return@post
                        }
                        val origin = call.request.headers[HttpHeaders.Origin]
                        if (!origin.isNullOrBlank() && !validOrigin(origin, port)) {
                            call.respondText("Invalid origin", status = HttpStatusCode.Forbidden)
                            return@post
                        }
                        val body = call.receiveText()
                        if (body.length > MAX_REQUEST_CHARS) {
                            call.respondText("Request too large", status = HttpStatusCode.PayloadTooLarge)
                            return@post
                        }
                        val request = runCatching { JSONObject(body) }.getOrNull()
                        if (request == null) {
                            val error = jsonRpcError(JSONObject.NULL, -32700, "Parse error")
                            call.respondText(error.toString(), ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }
                        if (!request.has("id")) {
                            call.respond(HttpStatusCode.Accepted)
                            return@post
                        }
                        val response = handleRequest(request)
                        if (response == null) {
                            call.respondText("Method not found", status = HttpStatusCode.NotFound)
                        } else {
                            call.respondText(response.toString(), ContentType.Application.Json, HttpStatusCode.OK)
                        }
                    }
                    get("/mcp/{token}") {
                        call.response.header(HttpHeaders.Allow, "POST, DELETE")
                        call.respond(HttpStatusCode.MethodNotAllowed)
                    }
                }
            }
            engine.start(wait = false)
            return DeviceControlMcpServer({ engine.stop(500, 1500) }, "http://127.0.0.1:$port/mcp/$token")
        }

        private suspend fun handleRequest(request: JSONObject): JSONObject? {
            val id = request.opt("id") ?: JSONObject.NULL
            val method = request.optString("method")
            return when (method) {
                "initialize" -> jsonRpcResult(id, JSONObject()
                    .put("protocolVersion", negotiatedVersion(request.optJSONObject("params")))
                    .put("capabilities", JSONObject().put("tools", JSONObject()))
                    .put("serverInfo", JSONObject().put("name", "sillage_device").put("version", "1.1.0"))
                    .put("instructions", SERVER_INSTRUCTIONS))
                "ping" -> jsonRpcResult(id, JSONObject())
                "tools/list" -> jsonRpcResult(id, JSONObject().put("tools", toolDefinitions()))
                "tools/call" -> {
                    val params = request.optJSONObject("params") ?: JSONObject()
                    val name = params.optString("name")
                    val result = runCatching { invokeTool(name, params.optJSONObject("arguments") ?: JSONObject()) }
                        .getOrElse { error ->
                            val errorJson = if (error is DeviceToolException) {
                                JSONObject().put("ok", false).put("code", error.code).put("message", error.message).put("hint", error.hint)
                            } else {
                                JSONObject().put("ok", false).put("code", "INTERNAL_ERROR").put("message", error.message.orEmpty())
                            }
                            ToolOutcome(JSONArray().put(textContent(errorJson.toString())), true)
                        }
                    jsonRpcResult(id, JSONObject()
                        .put("content", result.content)
                        .put("isError", result.isError))
                }
                else -> jsonRpcError(id, -32601, "Method not found: $method")
            }
        }

        private suspend fun invokeTool(name: String, args: JSONObject): ToolOutcome = when (name) {
            "device_control_start" -> {
                val wasActive = DeviceControlRuntime.state.value.sessionActive
                val snapshot = DeviceControlRuntime.startSession(args.optInt("minutes", 15))
                val data = JSONObject()
                    .put("ok", true)
                    .put("session_active", snapshot.sessionActive)
                    .put("already_active", wasActive)
                    .put("session_minutes", snapshot.sessionMinutes)
                    .put("session_expires_at_ms", snapshot.sessionExpiresAtMs)
                    .put("message", if (wasActive) "设备控制会话已在运行" else "设备控制会话已开启")
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_control_stop" -> {
                DeviceControlRuntime.stopSession("ai_request")
                ToolOutcome(JSONArray().put(textContent(JSONObject().put("ok", true).put("session_active", false).toString())))
            }
            "device_layout" -> {
                val maxNodes = args.optInt("max_nodes", 400)
                val (snapshot, _) = DeviceControlRuntime.snapshot(false, maxNodes)
                ToolOutcome(JSONArray().put(textContent(snapshot.layoutJson().toString())))
            }
            "device_status" -> {
                val data = DeviceControlRuntime.statusJson()
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_snapshot" -> {
                val includeScreenshot = args.optBoolean("include_screenshot", true)
                val maxNodes = args.optInt("max_nodes", 300)
                val (snapshot, screenshot) = DeviceControlRuntime.snapshot(includeScreenshot, maxNodes)
                val content = JSONArray().put(textContent(snapshot.toJson().toString()))
                if (screenshot != null) {
                    encodedScreenshot(screenshot)?.let { image ->
                        content.put(JSONObject()
                            .put("type", "image")
                            .put("data", image)
                            .put("mimeType", "image/jpeg"))
                    }
                }
                ToolOutcome(content)
            }
            "device_apps" -> {
                val data = DeviceControlRuntime.apps(args.optString("query"), args.optInt("limit", 50))
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_find" -> {
                val data = DeviceControlRuntime.findNodes(
                    query = nodeQuery(args),
                    freshSnapshot = args.optBoolean("fresh_snapshot", true),
                    maxNodes = args.optInt("max_nodes", 400),
                    limit = args.optInt("limit", 50),
                )
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_click" -> {
                val data = DeviceControlRuntime.clickMatch(
                    query = nodeQuery(args),
                    index = args.optInt("index", -1).takeIf { it >= 0 },
                    snapshotId = args.optString("snapshot_id"),
                    nodeId = args.optString("node_id"),
                    durationMs = args.optLong("duration_ms", 50L),
                )
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_tap" -> {
                val data = DeviceControlRuntime.tap(
                    snapshotId = args.optString("snapshot_id").ifBlank { null },
                    nodeId = args.optString("node_id").ifBlank { null },
                    x = args.optInt("x", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                    y = args.optInt("y", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE },
                    durationMs = args.optLong("duration_ms", 50L),
                )
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_swipe" -> {
                val data = DeviceControlRuntime.swipe(
                    args.getInt("from_x"), args.getInt("from_y"), args.getInt("to_x"), args.getInt("to_y"),
                    args.optLong("duration_ms", 300L),
                )
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_scroll" -> {
                val data = DeviceControlRuntime.scroll(
                    direction = args.getString("direction"),
                    amount = args.optDouble("amount", 0.55).toFloat(),
                    durationMs = args.optLong("duration_ms", 320L),
                    snapshotId = args.optString("snapshot_id"),
                    nodeId = args.optString("node_id"),
                )
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_scroll_until" -> {
                val data = DeviceControlRuntime.scrollUntil(
                    query = nodeQuery(args),
                    direction = args.optString("direction", "up"),
                    amount = args.optDouble("amount", 0.55).toFloat(),
                    maxScrolls = args.optInt("max_scrolls", 8),
                    durationMs = args.optLong("duration_ms", 320L),
                    nodeId = args.optString("node_id"),
                )
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_wait_for" -> {
                val data = DeviceControlRuntime.waitForCondition(
                    query = nodeQuery(args),
                    targetPackage = args.optString("package"),
                    notPresent = args.optBoolean("not_present", false),
                    timeoutMs = args.optLong("timeout_ms", 8_000L),
                    intervalMs = args.optLong("interval_ms", 500L),
                )
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_key" -> {
                val data = DeviceControlRuntime.key(args.getString("key"))
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_input_text" -> {
                val text = args.optString("text")
                if (text.length > 16_000) throw DeviceToolException("INVALID_ARGUMENT", "text exceeds 16000 characters")
                val data = DeviceControlRuntime.inputText(text, args.optString("mode", "append"), args.optBoolean("press_enter", false))
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_launch_app" -> {
                val data = DeviceControlRuntime.launchApp(
                    args.optString("package_name"),
                    args.optString("app_name"),
                    args.optString("activity"),
                )
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_open_url" -> {
                val data = DeviceControlRuntime.openUrl(args.getString("url"), args.optString("package_name"))
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_open_settings" -> {
                val data = DeviceControlRuntime.openSettings(args.optString("page", "settings"), args.optString("package_name"))
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_system_panel" -> {
                val data = DeviceControlRuntime.systemPanel(args.getString("action"))
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_app_info" -> {
                val data = DeviceControlRuntime.appInfo(args.getString("package_name"))
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_media" -> {
                val data = DeviceControlRuntime.mediaControl(args.getString("action"))
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_wait" -> {
                val data = DeviceControlRuntime.waitFor(args.optLong("milliseconds", 500L))
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            "device_shell" -> {
                val data = DeviceControlRuntime.shell(args.getString("command"), args.optInt("timeout_seconds", 30))
                ToolOutcome(JSONArray().put(textContent(data.toString())))
            }
            else -> throw DeviceToolException("UNKNOWN_TOOL", "Unknown device tool: $name")
        }

        private fun toolDefinitions(): JSONArray = JSONArray().apply {
            put(tool("device_control_start", "Start an AI device-control session automatically when the user asks you to operate the phone. Requires Shizuku ready and a visible stop control (overlay or notifications).", objectSchema(JSONObject()
                .put("minutes", JSONObject().put("type", "integer").put("minimum", 5).put("maximum", 30).put("default", 15))), false, false))
            put(tool("device_control_stop", "Stop the current AI device-control session and remove the visible control affordance.", objectSchema(JSONObject()), false, false))
            put(tool("device_layout", "Return a text-only snapshot of the current screen: app package, screen size, buttons, text boxes, labels, node ids, bounds and center coordinates. Use this when the model cannot inspect images.", objectSchema(JSONObject()
                .put("max_nodes", JSONObject().put("type", "integer").put("minimum", 20).put("maximum", 500).put("default", 400))), true, false))
            put(tool("device_status", "Read Sillage device-control and Shizuku connection status. Does not require a control session.", objectSchema(JSONObject()), false, false))
            put(tool(
                "device_snapshot",
                "Observe the current screen. Returns a compact UI hierarchy with stable node ids and, by default, a screenshot image. Start every UI task from this tool and use node ids instead of guessing coordinates.",
                objectSchema(JSONObject()
                    .put("include_screenshot", JSONObject().put("type", "boolean").put("default", true))
                    .put("max_nodes", JSONObject().put("type", "integer").put("minimum", 20).put("maximum", 500).put("default", 300))),
                true,
                false,
            ))
            put(tool("device_apps", "Search installed applications by visible app name or package name. Returns label, package_name, system and enabled fields. Use this before launching an app when the package name is unknown.", objectSchema(JSONObject()
                .put("query", JSONObject().put("type", "string"))
                .put("limit", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 200))), true, false))
            put(tool("device_find", "Find visible UI nodes by text, description, resource id, class, or a general query across all of them. Returns stable node ids and click target coordinates. Prefer this over guessing from screenshots.", selectorSchema(JSONObject()
                .put("fresh_snapshot", JSONObject().put("type", "boolean").put("default", true))
                .put("max_nodes", JSONObject().put("type", "integer").put("minimum", 20).put("maximum", 500).put("default", 400))
                .put("limit", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 100).put("default", 50))), true, false))
            put(tool("device_click", "Find and click a visible UI node by text, description, resource id, class, node_id, or a general query. If multiple nodes match, specify a 0-based index or use device_find and then pass snapshot_id + node_id.", selectorSchema(JSONObject()
                .put("index", JSONObject().put("type", "integer").put("minimum", 0))
                .put("snapshot_id", JSONObject().put("type", "string"))
                .put("node_id", JSONObject().put("type", "string"))
                .put("duration_ms", JSONObject().put("type", "integer").put("minimum", 50).put("maximum", 5000).put("default", 50))), true, false))
            put(tool("device_tap", "Tap a node from the latest snapshot or tap absolute screen coordinates. Prefer snapshot_id + node_id.", objectSchema(JSONObject()
                .put("snapshot_id", JSONObject().put("type", "string"))
                .put("node_id", JSONObject().put("type", "string"))
                .put("x", JSONObject().put("type", "integer"))
                .put("y", JSONObject().put("type", "integer"))
                .put("duration_ms", JSONObject().put("type", "integer").put("minimum", 50).put("maximum", 5000))), true, false))
            put(tool("device_swipe", "Swipe between absolute screen coordinates.", objectSchema(JSONObject()
                .put("from_x", JSONObject().put("type", "integer"))
                .put("from_y", JSONObject().put("type", "integer"))
                .put("to_x", JSONObject().put("type", "integer"))
                .put("to_y", JSONObject().put("type", "integer"))
                .put("duration_ms", JSONObject().put("type", "integer").put("minimum", 50).put("maximum", 5000)),
                required = arrayOf("from_x", "from_y", "to_x", "to_y")), true, false))
            put(tool("device_scroll", "Scroll the screen or one scrollable node without calculating coordinates. Finger direction semantics: up means finger moves up and content moves down; down means finger moves down and content moves up.", objectSchema(JSONObject()
                .put("direction", JSONObject().put("type", "string").put("enum", JSONArray().put("up").put("down").put("left").put("right")))
                .put("amount", JSONObject().put("type", "number").put("minimum", 0.1).put("maximum", 0.9).put("default", 0.55))
                .put("duration_ms", JSONObject().put("type", "integer").put("minimum", 50).put("maximum", 5000).put("default", 320))
                .put("snapshot_id", JSONObject().put("type", "string"))
                .put("node_id", JSONObject().put("type", "string")),
                required = arrayOf("direction")), true, false))
            put(tool("device_scroll_until", "Repeatedly scroll and inspect the UI until a matching text/description/id appears. Use this for long lists, settings pages, and search result lists.", selectorSchema(JSONObject()
                .put("direction", JSONObject().put("type", "string").put("enum", JSONArray().put("up").put("down").put("left").put("right")).put("default", "up"))
                .put("amount", JSONObject().put("type", "number").put("minimum", 0.1).put("maximum", 0.9).put("default", 0.55))
                .put("max_scrolls", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 12).put("default", 8))
                .put("duration_ms", JSONObject().put("type", "integer").put("minimum", 50).put("maximum", 5000).put("default", 320))
                .put("node_id", JSONObject().put("type", "string"))), true, false))
            put(tool("device_wait_for", "Wait until matching text/description/id appears or disappears, or until a package becomes/stops being the visible package. Polls the real UI hierarchy instead of sleeping blindly.", selectorSchema(JSONObject()
                .put("package", JSONObject().put("type", "string"))
                .put("not_present", JSONObject().put("type", "boolean").put("default", false))
                .put("timeout_ms", JSONObject().put("type", "integer").put("minimum", 250).put("maximum", 30000).put("default", 8000))
                .put("interval_ms", JSONObject().put("type", "integer").put("minimum", 200).put("maximum", 2000).put("default", 500))), true, false))
            put(tool("device_key", "Send an Android key such as BACK, HOME, RECENTS, ENTER, DELETE, POWER, WAKEUP, VOLUME_UP or VOLUME_DOWN.", objectSchema(JSONObject()
                .put("key", JSONObject().put("type", "string")), required = arrayOf("key")), true, false))
            put(tool("device_input_text", "Commit Unicode text to the currently focused text field through Sillage's bridge IME. Tap and verify the target field first.", objectSchema(JSONObject()
                .put("text", JSONObject().put("type", "string"))
                .put("mode", JSONObject().put("type", "string").put("enum", JSONArray().put("append").put("replace")).put("default", "append"))
                .put("press_enter", JSONObject().put("type", "boolean").put("default", false)),
                required = arrayOf("text")), true, false))
            put(tool("device_launch_app", "Launch an installed application by package_name or by visible app_name. If app_name is ambiguous, the tool returns candidates and asks for package_name.", objectSchema(JSONObject()
                .put("package_name", JSONObject().put("type", "string"))
                .put("app_name", JSONObject().put("type", "string"))
                .put("activity", JSONObject().put("type", "string"))), true, false))
            put(tool("device_app_info", "Read package metadata and the launcher activity for one installed app: label, version, SDK levels, ABI, data directory, and enabled/system state.", objectSchema(JSONObject()
                .put("package_name", JSONObject().put("type", "string")), required = arrayOf("package_name")), true, false))
            put(tool("device_open_url", "Open an http/https URL, mailto:, tel:, geo:, or app deep link through Android's VIEW intent. Optionally constrain it to a package.", objectSchema(JSONObject()
                .put("url", JSONObject().put("type", "string"))
                .put("package_name", JSONObject().put("type", "string")), required = arrayOf("url")), true, false, true))
            put(tool("device_open_settings", "Open a system Settings page. Supported pages: settings, wifi, internet, bluetooth, apps, app_info, notifications, sound, display, battery, accessibility, location, privacy, storage, developer. app_info requires package_name.", objectSchema(JSONObject()
                .put("page", JSONObject().put("type", "string").put("enum", JSONArray().put("settings").put("wifi").put("internet").put("bluetooth").put("apps").put("app_info").put("notifications").put("sound").put("display").put("battery").put("accessibility").put("location").put("privacy").put("storage").put("developer")))
                .put("package_name", JSONObject().put("type", "string")), required = arrayOf("page")), true, false))
            put(tool("device_system_panel", "Expand the notification shade, expand quick settings, or collapse the shade. Inspect the resulting UI with device_layout before tapping.", objectSchema(JSONObject()
                .put("action", JSONObject().put("type", "string").put("enum", JSONArray().put("notifications").put("quick_settings").put("collapse"))), required = arrayOf("action")), true, false))
            put(tool("device_media", "Control the active media session or music volume: play, pause, play_pause, stop, next, previous, mute, volume_up, volume_down, volume_get.", objectSchema(JSONObject()
                .put("action", JSONObject().put("type", "string").put("enum", JSONArray().put("play").put("pause").put("play_pause").put("stop").put("next").put("previous").put("mute").put("volume_up").put("volume_down").put("volume_get"))), required = arrayOf("action")), true, false))
            put(tool("device_wait", "Wait briefly for the UI to settle, then report the focused window.", objectSchema(JSONObject()
                .put("milliseconds", JSONObject().put("type", "integer").put("minimum", 50).put("maximum", 5000).put("default", 500))), true, false))
            put(tool("device_shell", "Run one non-interactive command as the Shizuku shell UID. The user must explicitly approve the exact command on every call, even inside an active session.", objectSchema(JSONObject()
                .put("command", JSONObject().put("type", "string"))
                .put("timeout_seconds", JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 60).put("default", 30)),
                required = arrayOf("command")), true, true))
        }

        private fun tool(
            name: String,
            description: String,
            schema: JSONObject,
            requiresSession: Boolean,
            destructive: Boolean,
            openWorld: Boolean = false,
        ): JSONObject = JSONObject()
            .put("name", name)
            .put("description", description)
            .put("inputSchema", schema)
            .put("annotations", JSONObject()
                .put("readOnlyHint", !destructive && (name == "device_status" || name == "device_snapshot" || name == "device_layout" || name == "device_apps" || name == "device_find" || name == "device_wait_for" || name == "device_app_info" || name == "device_wait"))
                .put("destructiveHint", destructive)
                .put("openWorldHint", openWorld))

        private fun selectorSchema(extra: JSONObject = JSONObject()): JSONObject {
            val properties = JSONObject()
                .put("query", JSONObject().put("type", "string").put("description", "Search text, description, resource id and class together"))
                .put("text", JSONObject().put("type", "string"))
                .put("description", JSONObject().put("type", "string"))
                .put("resource_id", JSONObject().put("type", "string"))
                .put("class_name", JSONObject().put("type", "string"))
                .put("match", JSONObject().put("type", "string").put("enum", JSONArray().put("contains").put("exact")).put("default", "contains"))
                .put("case_sensitive", JSONObject().put("type", "boolean").put("default", false))
            extra.keys().forEach { key -> properties.put(key, extra.opt(key)) }
            return objectSchema(properties)
        }

        private fun nodeQuery(args: JSONObject): DeviceNodeQuery = DeviceNodeQuery(
            query = args.optString("query"),
            text = args.optString("text"),
            description = args.optString("description"),
            resourceId = args.optString("resource_id"),
            className = args.optString("class_name"),
            match = args.optString("match", "contains"),
            caseSensitive = args.optBoolean("case_sensitive", false),
        )

        private fun objectSchema(properties: JSONObject, required: Array<String> = emptyArray()): JSONObject = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray().apply { required.forEach(::put) })
            .put("additionalProperties", false)

        private fun negotiatedVersion(params: JSONObject?): String {
            val requested = params?.optString("protocolVersion").orEmpty()
            return requested.ifBlank { "2025-06-18" }
        }

        private fun jsonRpcResult(id: Any, result: JSONObject): JSONObject =
            JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)

        private fun jsonRpcError(id: Any, code: Int, message: String): JSONObject =
            JSONObject().put("jsonrpc", "2.0").put("id", id).put("error", JSONObject().put("code", code).put("message", message))

        private fun textContent(text: String): JSONObject = JSONObject().put("type", "text").put("text", text)

        private fun encodedScreenshot(raw: ByteArray): String? {
            val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
            val maxEdge = maxOf(bitmap.width, bitmap.height)
            val scaled = if (maxEdge > 1280) {
                val ratio = 1280f / maxEdge
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
            } else bitmap
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }

        private fun validHost(value: String?): Boolean {
            val raw = value.orEmpty().trim()
            val host = if (raw.startsWith("[")) raw.substringAfter('[').substringBefore(']').lowercase()
            else raw.substringBefore(':').lowercase()
            return host == "127.0.0.1" || host == "localhost" || host == "::1"
        }

        private fun validOrigin(value: String, port: Int): Boolean = runCatching {
            val uri = java.net.URI(value)
            (uri.host == "127.0.0.1" || uri.host == "localhost" || uri.host == "::1") && uri.port == port
        }.getOrDefault(false)

        private fun newToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        private data class ToolOutcome(val content: JSONArray, val isError: Boolean = false)
        private const val MAX_REQUEST_CHARS = 256 * 1024
        private const val SERVER_INSTRUCTIONS =
            "When the user asks you to operate the phone, call device_control_start yourself. Use device_layout or device_find for text-only understanding, device_snapshot when vision helps, and device_wait_for/device_scroll_until instead of blind sleeps or coordinate guesses. Prefer device_click with text/resource_id selectors, then verify the result. device_apps and device_app_info handle package discovery; device_open_url, device_open_settings, device_system_panel and device_media cover common intents without raw shell. Prefer node_id or semantic selectors over coordinates. Stop the session when the task is complete. device_shell always waits for explicit user approval; never try to bypass it."
    }
}
