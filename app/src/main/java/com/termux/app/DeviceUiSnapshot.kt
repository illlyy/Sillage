package com.termux.app

import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.Locale

private fun escapeLayoutValue(value: String): String = buildString(value.length) {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '"' -> append("\\\"")
            else -> append(character)
        }
    }
}

internal data class DeviceUiNode(
    val id: String,
    val parentId: String?,
    val depth: Int,
    val packageName: String,
    val className: String,
    val text: String,
    val contentDescription: String,
    val resourceId: String,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val focused: Boolean,
    val selected: Boolean,
    val checked: Boolean,
    val password: Boolean,
) {
    fun toJson(screenWidth: Int, screenHeight: Int): JSONObject = JSONObject()
        .put("id", id)
        .put("parent_id", parentId ?: JSONObject.NULL)
        .put("depth", depth)
        .put("class", className)
        .put("text", text)
        .put("description", contentDescription)
        .put("resource_id", resourceId)
        .put("bounds", JSONObject()
            .put("left", bounds.left)
            .put("top", bounds.top)
            .put("right", bounds.right)
            .put("bottom", bounds.bottom)
            .put("center_x", bounds.centerX())
            .put("center_y", bounds.centerY()))
        .put("screen_width", screenWidth)
        .put("screen_height", screenHeight)
        .put("clickable", clickable)
        .put("editable", editable)
        .put("scrollable", scrollable)
        .put("enabled", enabled)
        .put("focused", focused)
        .put("selected", selected)
        .put("checked", checked)
        .put("password", password)
}

internal data class DeviceUiSnapshot(
    val snapshotId: String,
    val capturedAtMs: Long,
    val packageName: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val nodes: List<DeviceUiNode>,
    val truncated: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("snapshot_id", snapshotId)
        .put("captured_at_ms", capturedAtMs)
        .put("package", packageName)
        .put("screen_width", screenWidth)
        .put("screen_height", screenHeight)
        .put("node_count", nodes.size)
        .put("truncated", truncated)
        .put("nodes", JSONArray().apply { nodes.forEach { put(it.toJson(screenWidth, screenHeight)) } })

    fun layoutJson(): JSONObject {
        val interactive = nodes.filter {
            it.text.isNotBlank() || it.contentDescription.isNotBlank() || it.resourceId.isNotBlank() ||
                it.clickable || it.editable || it.scrollable || it.focused
        }
        val lines = interactive.joinToString("\n") { node ->
            val role = node.className.substringAfterLast('.')
            val label = when {
                node.password -> "<password>"
                node.text.isNotBlank() -> node.text
                node.contentDescription.isNotBlank() -> node.contentDescription
                node.resourceId.isNotBlank() -> node.resourceId
                else -> ""
            }
            val flags = buildList {
                if (node.clickable) add("clickable")
                if (node.editable) add("editable")
                if (node.scrollable) add("scrollable")
                if (node.focused) add("focused")
                if (node.selected) add("selected")
                if (node.checked) add("checked")
                if (!node.enabled) add("disabled")
            }.joinToString(",")
            val prefix = "  ".repeat(node.depth)
            val escapedLabel = escapeLayoutValue(label)
            val description = if (node.contentDescription.isNotBlank() && node.contentDescription != label) {
                " desc=\"${escapeLayoutValue(node.contentDescription)}\""
            } else ""
            val resource = if (node.resourceId.isNotBlank()) {
                " resource_id=${escapeLayoutValue(node.resourceId)}"
            } else ""
            "${prefix}[id=${node.id}] $role \"$escapedLabel\"$description$resource bounds=${node.bounds.left},${node.bounds.top},${node.bounds.right},${node.bounds.bottom} center=${node.bounds.centerX()},${node.bounds.centerY()}${if (flags.isBlank()) "" else " flags=$flags"}"
        }
        return JSONObject()
            .put("snapshot_id", snapshotId)
            .put("package", packageName)
            .put("screen_width", screenWidth)
            .put("screen_height", screenHeight)
            .put("node_count", interactive.size)
            .put("truncated", truncated)
            .put("layout", lines)
    }

    fun node(nodeId: String): DeviceUiNode? = nodes.firstOrNull { it.id == nodeId }

    fun clickableNode(nodeId: String): DeviceUiNode? {
        val byId = nodes.associateBy { it.id }
        var current = byId[nodeId] ?: return null
        repeat(12) {
            if (current.clickable || current.editable || current.scrollable) return current
            current = current.parentId?.let(byId::get) ?: return null
        }
        return null
    }
}

internal data class DeviceNodeQuery(
    val query: String = "",
    val text: String = "",
    val description: String = "",
    val resourceId: String = "",
    val className: String = "",
    val match: String = "contains",
    val caseSensitive: Boolean = false,
) {
    val hasSelector: Boolean
        get() = query.isNotBlank() || text.isNotBlank() || description.isNotBlank() ||
            resourceId.isNotBlank() || className.isNotBlank()
}

internal data class DeviceNodeMatch(
    val node: DeviceUiNode,
    val target: DeviceUiNode?,
)

internal fun DeviceUiSnapshot.findNodeMatches(query: DeviceNodeQuery): List<DeviceNodeMatch> {
    if (!query.hasSelector) return emptyList()
    val exact = query.match.equals("exact", ignoreCase = true)
    fun matchesField(value: String, rawExpected: String): Boolean {
        if (rawExpected.isBlank()) return true
        val actual = if (query.caseSensitive) value else value.lowercase(Locale.ROOT)
        val expected = if (query.caseSensitive) rawExpected else rawExpected.lowercase(Locale.ROOT)
        return if (exact) actual == expected else actual.contains(expected)
    }
    return nodes.mapNotNull { node ->
        val queryMatches = query.query.isBlank() || listOf(
            node.text,
            node.contentDescription,
            node.resourceId,
            node.className,
        ).any { matchesField(it, query.query) }
        if (!queryMatches ||
            !matchesField(node.text, query.text) ||
            !matchesField(node.contentDescription, query.description) ||
            !matchesField(node.resourceId, query.resourceId) ||
            !matchesField(node.className, query.className)
        ) return@mapNotNull null
        DeviceNodeMatch(node = node, target = clickableNode(node.id))
    }
}

internal object DeviceUiSnapshotParser {
    private val boundsRegex = Regex("""\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]""")

    fun parse(xml: String, maxNodes: Int = 300, now: Long = System.currentTimeMillis()): DeviceUiSnapshot {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(StringReader(xml)) }
        val stack = ArrayDeque<String>()
        val childCounts = mutableMapOf<String, Int>()
        val nodes = mutableListOf<DeviceUiNode>()
        var rootPackage = ""
        var width = 0
        var height = 0
        var event = parser.eventType
        var truncated = false
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "node") {
                val parent = stack.lastOrNull()
                val childIndex = childCounts[parent.orEmpty()] ?: 0
                childCounts[parent.orEmpty()] = childIndex + 1
                val id = if (parent.isNullOrBlank()) childIndex.toString() else "$parent/$childIndex"
                val bounds = parseBounds(parser.getAttributeValue(null, "bounds"))
                width = maxOf(width, bounds.right)
                height = maxOf(height, bounds.bottom)
                val pkg = parser.getAttributeValue(null, "package").orEmpty()
                if (rootPackage.isBlank()) rootPackage = pkg
                val password = parser.getAttributeValue(null, "password").toBoolean()
                val className = parser.getAttributeValue(null, "class").orEmpty()
                val editableClass = className.contains("EditText") || className.contains("AutoCompleteTextView") ||
                    className.contains("SearchView")
                val node = DeviceUiNode(
                    id = id,
                    parentId = parent,
                    depth = stack.size,
                    packageName = pkg,
                    className = className,
                    text = if (password) "" else parser.getAttributeValue(null, "text").orEmpty(),
                    contentDescription = if (password) "" else parser.getAttributeValue(null, "content-desc").orEmpty(),
                    resourceId = parser.getAttributeValue(null, "resource-id").orEmpty(),
                    bounds = bounds,
                    clickable = parser.getAttributeValue(null, "clickable").toBoolean(),
                    editable = editableClass && parser.getAttributeValue(null, "focusable").toBoolean(),
                    scrollable = parser.getAttributeValue(null, "scrollable").toBoolean(),
                    enabled = parser.getAttributeValue(null, "enabled").toBoolean(),
                    focused = parser.getAttributeValue(null, "focused").toBoolean(),
                    selected = parser.getAttributeValue(null, "selected").toBoolean(),
                    checked = parser.getAttributeValue(null, "checked").toBoolean(),
                    password = password,
                )
                if (nodes.size < maxNodes) nodes += node else truncated = true
                stack.addLast(id)
            } else if (event == XmlPullParser.END_TAG && parser.name == "node") {
                if (stack.isNotEmpty()) stack.removeLast()
            }
            event = parser.next()
        }
        if (nodes.isEmpty()) throw IllegalArgumentException("UI hierarchy contained no nodes")
        return DeviceUiSnapshot(
            snapshotId = "%x-%x".format(now, xml.hashCode().toLong() and 0xffffffffL),
            capturedAtMs = now,
            packageName = rootPackage,
            screenWidth = width,
            screenHeight = height,
            nodes = nodes,
            truncated = truncated,
        )
    }

    private fun parseBounds(value: String?): Rect {
        val match = boundsRegex.find(value.orEmpty()) ?: return Rect()
        val values = match.groupValues.drop(1).mapNotNull(String::toIntOrNull)
        if (values.size != 4) return Rect()
        return Rect(values[0], values[1], values[2], values[3])
    }
}
