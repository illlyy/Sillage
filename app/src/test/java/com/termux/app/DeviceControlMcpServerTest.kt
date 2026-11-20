package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class DeviceControlMcpServerTest {
    @Test
    fun servesInitializeAndToolsListOverLoopback() {
        val server = DeviceControlMcpServer.start()
        try {
            val initialize = post(server.endpoint, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""")
            assertEquals(200, initialize.first)
            assertTrue(initialize.second.contains("sillage_device"))

            val tools = post(server.endpoint, """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""")
            assertEquals(200, tools.first)
            assertTrue(tools.second.contains("device_control_start"))
            assertTrue(tools.second.contains("device_layout"))
            assertTrue(tools.second.contains("device_snapshot"))
            assertTrue(tools.second.contains("device_find"))
            assertTrue(tools.second.contains("device_click"))
            assertTrue(tools.second.contains("device_scroll_until"))
            assertTrue(tools.second.contains("device_wait_for"))
            assertTrue(tools.second.contains("device_open_url"))
            assertTrue(tools.second.contains("device_open_settings"))
            assertTrue(tools.second.contains("device_system_panel"))
            assertTrue(tools.second.contains("device_app_info"))
            assertTrue(tools.second.contains("device_media"))
            assertTrue(tools.second.contains("app_name"))
            assertTrue(tools.second.contains("device_shell"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun rejectsWrongToken() {
        val server = DeviceControlMcpServer.start()
        try {
            val response = post(server.endpoint + "-wrong", """{"jsonrpc":"2.0","id":1,"method":"ping"}""")
            assertEquals(404, response.first)
        } finally {
            server.stop()
        }
    }

    private fun post(url: String, body: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json, text/event-stream")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return code to text
    }
}
