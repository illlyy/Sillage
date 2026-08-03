package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeToolConfigsTest {

    @Test
    fun unknownJsonTypeFallsBackToParametersCardWithToolName() {
        val config = NativeToolConfigs.ofJson("Read")
        assertEquals(NativeToolDisplayType.COLLAPSIBLE, config.displayType)
        assertEquals(NativeToolContentType.JSON, config.contentType)
        assertEquals(NativeToolCategory.UNKNOWN, config.category)
        assertEquals("Read", config.label("zh"))
        assertEquals("Read", config.label("en"))
    }

    @Test
    fun knownJsonTypesResolveToTheirDomainConfigs() {
        assertEquals(NativeToolCategory.COMMAND, NativeToolConfigs.ofJson("commandExecution").category)
        assertEquals(NativeToolContentType.DIFF, NativeToolConfigs.ofJson("fileChange").contentType)
        assertEquals(NativeToolCategory.WEB, NativeToolConfigs.ofJson("webSearch").category)
        assertEquals(NativeToolCategory.MCP, NativeToolConfigs.ofJson("mcpToolCall").category)
        assertEquals(NativeToolCategory.SUBAGENT, NativeToolConfigs.ofJson("collabAgentToolCall").category)
        assertEquals(NativeToolCategory.SUBAGENT, NativeToolConfigs.ofJson("subAgentActivity").category)
    }

    @Test
    fun domainTypesResolveToStableLabelsInBothLanguages() {
        assertEquals("命令执行", NativeToolConfigs.of(NativeActivityItemType.COMMAND).label("zh"))
        assertEquals("Command execution", NativeToolConfigs.of(NativeActivityItemType.COMMAND).label("en"))
        assertEquals("文件修改", NativeToolConfigs.of(NativeActivityItemType.FILE_CHANGE).label("zh"))
        assertEquals("File change", NativeToolConfigs.of(NativeActivityItemType.FILE_CHANGE).label("en"))
    }

    @Test
    fun unknownDomainToolUsesSharedDefaultConfig() {
        val config = NativeToolConfigs.of(NativeActivityItemType.TOOL)
        assertEquals(NativeToolDisplayType.COLLAPSIBLE, config.displayType)
        assertEquals(NativeToolContentType.JSON, config.contentType)
        assertEquals(NativeToolCategory.UNKNOWN, config.category)
    }

    @Test
    fun deriveToolStatus_missingStatusMeansRunning() {
        assertEquals(NativeToolStatus.RUNNING, deriveToolStatus(null))
        assertEquals(NativeToolStatus.RUNNING, deriveToolStatus(NativeActivityItemStatus.RUNNING))
        assertEquals(NativeToolStatus.RUNNING, deriveToolStatus(NativeActivityItemStatus.WAITING))
    }

    @Test
    fun deriveToolStatus_failedWithDenialMarkerIsDenied() {
        assertEquals(
            NativeToolStatus.DENIED,
            deriveToolStatus(NativeActivityItemStatus.FAILED, "user denied tool use"),
        )
        assertEquals(
            NativeToolStatus.DENIED,
            deriveToolStatus(NativeActivityItemStatus.FAILED, "tool disallowed by settings"),
        )
        assertEquals(
            NativeToolStatus.DENIED,
            deriveToolStatus(NativeActivityItemStatus.FAILED, "Permission request timed out"),
        )
    }

    @Test
    fun deriveToolStatus_failedWithoutDenialIsError() {
        assertEquals(
            NativeToolStatus.ERROR,
            deriveToolStatus(NativeActivityItemStatus.FAILED, "command exited with 127"),
        )
        assertEquals(
            NativeToolStatus.ERROR,
            deriveToolStatus(NativeActivityItemStatus.FAILED, ""),
        )
    }

    @Test
    fun deriveToolStatus_completedStaysCompleted() {
        assertEquals(
            NativeToolStatus.COMPLETED,
            deriveToolStatus(NativeActivityItemStatus.COMPLETED),
        )
    }

    @Test
    fun isToolUseDenied_matchesExactDenialPhrasingsCaseInsensitively() {
        assertTrue(isToolUseDenied("User Denied Tool Use"))
        assertTrue(isToolUseDenied("permission request cancelled by user"))
        assertFalse(isToolUseDenied("a normal failure message"))
        assertFalse(isToolUseDenied(""))
    }

    @Test
    fun shouldHideToolResult_neverHidesErrorsOrDenials() {
        val hideOnSuccess = NativeToolDisplayConfig(
            hideResultOnSuccess = true,
            labelZh = "x",
            labelEn = "x",
        )
        assertFalse(shouldHideToolResult(hideOnSuccess, NativeToolStatus.ERROR, hasResult = true))
        assertFalse(shouldHideToolResult(hideOnSuccess, NativeToolStatus.DENIED, hasResult = true))
    }

    @Test
    fun shouldHideToolResult_hidesSuccessOnlyWhenConfigOptsIn() {
        val hideOnSuccess = NativeToolDisplayConfig(
            hideResultOnSuccess = true,
            labelZh = "x",
            labelEn = "x",
        )
        val keepResult = NativeToolDisplayConfig(labelZh = "x", labelEn = "x")
        assertTrue(shouldHideToolResult(hideOnSuccess, NativeToolStatus.COMPLETED, hasResult = true))
        assertFalse(shouldHideToolResult(hideOnSuccess, NativeToolStatus.COMPLETED, hasResult = false))
        assertFalse(shouldHideToolResult(keepResult, NativeToolStatus.COMPLETED, hasResult = true))
    }
}
