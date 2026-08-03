package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class NativeApprovalModelTest {

    private fun commandApprovalRaw(command: String = "rm -rf build", available: String = "accept,decline,acceptForSession"): String =
        JSONObject()
            .put("method", "execCommandApproval")
            .put(
                "params",
                JSONObject()
                    .put("command", command)
                    .put("cwd", "/data/data/com.termux/files/home/project")
                    .put("availableDecisions", org.json.JSONArray().apply {
                        available.split(",").forEach { put(it) }
                    }),
            )
            .toString()

    @Test
    fun commandApprovalSummarizesCommand() {
        val summary = nativeApprovalSummary(commandApprovalRaw(), "en")
        assertEquals("Allow command?", summary.title)
        assertTrue(summary.detail.isNotBlank())
        assertTrue(summary.command.contains("rm -rf build"))
        assertTrue(summary.allowForSession)
    }

    @Test
    fun sessionDecisionAdvertisedOnlyWhenAvailable() {
        val withSession = nativeApprovalAllowsSession(
            JSONObject().put("availableDecisions", org.json.JSONArray().apply { put("accept"); put("acceptForSession") }),
        )
        assertTrue(withSession)
        val withoutSession = nativeApprovalAllowsSession(
            JSONObject().put("availableDecisions", org.json.JSONArray().apply { put("accept"); put("decline") }),
        )
        assertFalse(withoutSession)
        assertTrue(nativeApprovalAllowsSession(JSONObject()))
    }

    @Test
    fun unknownMethodFallsBackToFileChangeTitle() {
        val raw = JSONObject().put("method", "somethingElse").toString()
        val summary = nativeApprovalSummary(raw, "zh")
        assertEquals("允许修改文件？", summary.title)
    }

    @Test
    fun malformedRawYieldsEmptySafeSummary() {
        val summary = nativeApprovalSummary("not json", "en")
        assertTrue(summary.command.isBlank())
    }

    @Test
    fun titlesAreBilingual() {
        assertEquals("Allow command?", nativeApprovalSummary(commandApprovalRaw(), "en").title)
        assertEquals("允许执行命令？", nativeApprovalSummary(commandApprovalRaw(), "zh").title)
    }
}
