package com.termux.app

/**
 * Declarative tool display registry (pattern: claudecodeui toolConfigs.ts). Every tool type
 * resolves to data — label, display shape, content kind, category — so new or unknown tools
 * render through a stable fallback instead of ad-hoc branches.
 */

internal enum class NativeToolDisplayType { ONE_LINE, COLLAPSIBLE, HIDDEN }

internal enum class NativeToolContentType { TEXT, DIFF, FILE_LIST, JSON }

/** Derived presentation status; [RUNNING] also covers waiting items. */
internal enum class NativeToolStatus { RUNNING, COMPLETED, DENIED, ERROR }

internal enum class NativeToolCategory { COMMAND, FILE, SEARCH, WEB, MCP, SUBAGENT, UNKNOWN }

internal data class NativeToolDisplayConfig(
    val displayType: NativeToolDisplayType = NativeToolDisplayType.ONE_LINE,
    val contentType: NativeToolContentType = NativeToolContentType.TEXT,
    val labelZh: String,
    val labelEn: String,
    val category: NativeToolCategory = NativeToolCategory.UNKNOWN,
    val hideResultOnSuccess: Boolean = false,
) {
    fun label(language: String): String = if (language == "en") labelEn else labelZh
}

/** Exact denial phrasings emitted by agent CLIs for declined permission prompts. */
internal val TOOL_DENIAL_MARKERS: List<String> = listOf(
    "user denied tool use",
    "tool disallowed by settings",
    "permission request timed out",
    "permission request cancelled",
    "permission request rejected",
)

internal fun isToolUseDenied(text: String): Boolean =
    text.isNotBlank() && TOOL_DENIAL_MARKERS.any { text.contains(it, ignoreCase = true) }

/**
 * Missing status means the item is still in flight. Waiting items render as running;
 * failures are split into user-denied vs real errors.
 */
internal fun deriveToolStatus(status: NativeActivityItemStatus?, detail: String = ""): NativeToolStatus = when (status) {
    null, NativeActivityItemStatus.RUNNING, NativeActivityItemStatus.WAITING -> NativeToolStatus.RUNNING
    NativeActivityItemStatus.FAILED -> if (isToolUseDenied(detail)) NativeToolStatus.DENIED else NativeToolStatus.ERROR
    NativeActivityItemStatus.COMPLETED -> NativeToolStatus.COMPLETED
}

/**
 * Result suppression rule: errors and denials must never be hidden, regardless of config.
 * A success result is hidden only when the config opts in (e.g. diff cards).
 */
internal fun shouldHideToolResult(
    config: NativeToolDisplayConfig,
    status: NativeToolStatus,
    hasResult: Boolean,
): Boolean {
    if (status == NativeToolStatus.ERROR || status == NativeToolStatus.DENIED) return false
    return config.hideResultOnSuccess && hasResult
}

internal object NativeToolConfigs {

    val DEFAULT: NativeToolDisplayConfig = NativeToolDisplayConfig(
        displayType = NativeToolDisplayType.COLLAPSIBLE,
        contentType = NativeToolContentType.JSON,
        labelZh = "工具调用",
        labelEn = "Tool call",
        category = NativeToolCategory.UNKNOWN,
    )

    fun of(type: NativeActivityItemType): NativeToolDisplayConfig = when (type) {
        NativeActivityItemType.COMMAND -> NativeToolDisplayConfig(
            displayType = NativeToolDisplayType.ONE_LINE,
            contentType = NativeToolContentType.TEXT,
            labelZh = "命令执行",
            labelEn = "Command execution",
            category = NativeToolCategory.COMMAND,
            hideResultOnSuccess = true,
        )
        NativeActivityItemType.FILE_CHANGE -> NativeToolDisplayConfig(
            displayType = NativeToolDisplayType.COLLAPSIBLE,
            contentType = NativeToolContentType.DIFF,
            labelZh = "文件修改",
            labelEn = "File change",
            category = NativeToolCategory.FILE,
            hideResultOnSuccess = true,
        )
        NativeActivityItemType.WEB_SEARCH -> NativeToolDisplayConfig(
            displayType = NativeToolDisplayType.ONE_LINE,
            contentType = NativeToolContentType.TEXT,
            labelZh = "网页搜索",
            labelEn = "Web search",
            category = NativeToolCategory.WEB,
        )
        NativeActivityItemType.SUBAGENT -> NativeToolDisplayConfig(
            displayType = NativeToolDisplayType.ONE_LINE,
            contentType = NativeToolContentType.TEXT,
            labelZh = "子代理",
            labelEn = "Subagent",
            category = NativeToolCategory.SUBAGENT,
        )
        NativeActivityItemType.REASONING -> NativeToolDisplayConfig(
            displayType = NativeToolDisplayType.HIDDEN,
            contentType = NativeToolContentType.TEXT,
            labelZh = "思考",
            labelEn = "Reasoning",
            category = NativeToolCategory.UNKNOWN,
        )
        NativeActivityItemType.MCP -> NativeToolDisplayConfig(
            displayType = NativeToolDisplayType.COLLAPSIBLE,
            contentType = NativeToolContentType.JSON,
            labelZh = "MCP 工具",
            labelEn = "MCP tool",
            category = NativeToolCategory.MCP,
            hideResultOnSuccess = true,
        )
        NativeActivityItemType.TOOL -> DEFAULT
    }

    /** Historical PROCESS2 payload types keep the same presentation as the live domain types. */
    fun ofJson(type: String): NativeToolDisplayConfig = when (type) {
        "commandExecution" -> of(NativeActivityItemType.COMMAND)
        "fileChange" -> of(NativeActivityItemType.FILE_CHANGE)
        "webSearch" -> of(NativeActivityItemType.WEB_SEARCH)
        "mcpToolCall" -> NativeToolDisplayConfig(
            displayType = NativeToolDisplayType.COLLAPSIBLE,
            contentType = NativeToolContentType.JSON,
            labelZh = "MCP 工具",
            labelEn = "MCP tool",
            category = NativeToolCategory.MCP,
            hideResultOnSuccess = true,
        )
        "collabAgentToolCall", "subAgentActivity" -> of(NativeActivityItemType.SUBAGENT)
        else -> NativeToolDisplayConfig(
            displayType = NativeToolDisplayType.COLLAPSIBLE,
            contentType = NativeToolContentType.JSON,
            labelZh = type,
            labelEn = type,
            category = NativeToolCategory.UNKNOWN,
        )
    }
}
