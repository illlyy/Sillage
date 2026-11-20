package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceUiSnapshotParserTest {
    @Test
    fun parsesNodesRedactsPasswordsAndResolvesClickableAncestor() {
        val xml = """<hierarchy rotation="0">
            <node index="0" text="" class="android.widget.FrameLayout" package="com.example" content-desc="" clickable="false" focusable="false" scrollable="false" enabled="true" focused="false" selected="false" checked="false" password="false" bounds="[0,0][1080,2400]">
              <node index="0" text="Search" class="android.widget.EditText" package="com.example" content-desc="search field" clickable="true" focusable="true" scrollable="false" enabled="true" focused="true" selected="false" checked="false" password="true" bounds="[20,100][1000,200]" />
              <node index="1" text="Open" class="android.widget.Button" package="com.example" content-desc="" clickable="true" focusable="true" scrollable="false" enabled="true" focused="false" selected="false" checked="false" password="false" bounds="[20,240][300,340]" />
            </node>
        </hierarchy>"""
        val snapshot = DeviceUiSnapshotParser.parse(xml, now = 1234L)
        assertEquals("com.example", snapshot.packageName)
        assertTrue(snapshot.nodes.size >= 3)
        val password = snapshot.nodes.first { it.password }
        assertEquals("", password.text)
        assertEquals("", password.contentDescription)
        val button = snapshot.nodes.first { it.text == "Open" }
        val target = snapshot.clickableNode(button.id)
        assertNotNull(target)
        assertEquals(160, target!!.bounds.centerX())
        assertEquals(290, target.bounds.centerY())
        val layout = snapshot.layoutJson().getString("layout")
        assertTrue(layout.contains("Open"))
        assertTrue(layout.contains("center=160,290"))
        assertFalse(snapshot.nodes.first { it.text == "Open" }.editable)
    }

    @Test
    fun capsNodeCountAndMarksTruncated() {
        val children = (0 until 12).joinToString("") { index ->
            """<node text="item$index" class="android.widget.TextView" package="com.example" content-desc="" clickable="false" focusable="false" scrollable="false" enabled="true" focused="false" selected="false" checked="false" password="false" bounds="[0,$index][100,${index + 1}]" />"""
        }
        val xml = """<hierarchy><node package="com.example" bounds="[0,0][1080,2400]">$children</node></hierarchy>"""
        val snapshot = DeviceUiSnapshotParser.parse(xml, maxNodes = 5)
        assertEquals(5, snapshot.nodes.size)
        assertTrue(snapshot.truncated)
        assertFalse(snapshot.nodes.any { it.password })
    }

    @Test
    fun layoutKeepsMultilineLabelsOnOneStructuredLine() {
        val xml = """<hierarchy>
            <node text="first line&#10;second &quot;quoted&quot; line" class="android.widget.TextView" package="com.example" content-desc="line one&#10;line two" clickable="true" focusable="true" scrollable="false" enabled="true" focused="false" selected="false" checked="false" password="false" bounds="[10,20][110,120]" />
        </hierarchy>"""
        val layout = DeviceUiSnapshotParser.parse(xml).layoutJson().getString("layout")

        assertTrue(layout.contains("first line\\nsecond \\\"quoted\\\" line"))
        assertTrue(layout.contains("desc=\"line one\\nline two\""))
        assertEquals(1, layout.lines().size)
    }

    @Test
    fun semanticNodeQueryFindsLabelsAndClickableAncestors() {
        val xml = """<hierarchy>
            <node text="" class="android.widget.FrameLayout" package="com.example" clickable="true" focusable="false" scrollable="false" enabled="true" bounds="[0,0][1080,2400]">
              <node text="Open settings" class="android.widget.TextView" package="com.example" content-desc="Settings" clickable="false" focusable="false" scrollable="false" enabled="true" bounds="[20,100][800,180]" />
            </node>
        </hierarchy>"""
        val snapshot = DeviceUiSnapshotParser.parse(xml)

        val byText = snapshot.findNodeMatches(DeviceNodeQuery(text = "settings", match = "contains"))
        assertEquals(1, byText.size)
        assertEquals("Open settings", byText.single().node.text)
        assertEquals("0", byText.single().target?.id)

        val exact = snapshot.findNodeMatches(DeviceNodeQuery(query = "open settings", match = "exact"))
        assertEquals(1, exact.size)
        assertEquals("0/0", exact.single().node.id)
    }
}
