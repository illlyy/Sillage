package com.termux.app

import org.json.JSONObject

/**
 * Approval-request presentation model (pattern: claudecodeui PermissionRequestsBanner).
 * Pure parsing of the bridge approval payload into a banner-friendly summary; the banner
 * itself renders the summary plus the three decisions (Deny / Allow session / Allow once).
 */
internal data class NativeApprovalSummary(
    val title: String,
    val detail: String,
    val command: String,
    val allowForSession: Boolean,
)

/** Decides which decisions the backend advertises for this request. */
internal fun nativeApprovalAllowsSession(params: JSONObject): Boolean {
    val available = params.optJSONArray("availableDecisions") ?: return true
    return (0 until available.length()).any { index ->
        available.opt(index)?.toString()?.contains("acceptForSession") == true
    }
}

internal fun nativeApprovalSummary(raw: String, language: String): NativeApprovalSummary {
    val payload = runCatching { JSONObject(raw) }.getOrNull()
    val method = payload?.optString("method").orEmpty()
    val params = payload?.optJSONObject("params") ?: JSONObject()
    // Claude advertises which tool it wants to use. Without reading it every non-command request
    // was announced as "allow file changes?", including read-only tools — which trains the user to
    // approve without reading, so the text is derived from the actual tool now.
    val toolName = params.optString("tool_name").ifBlank { params.optString("toolName") }
    val toolTitle = params.optString("title")
    val toolInput = params.optJSONObject("input")
    val isShellTool = toolName.equals("Bash", true) || toolName.equals("Shell", true)
    val isWriteTool = toolName.lowercase() in setOf("write", "edit", "multiedit", "notebookedit")
    val genericTool = toolName.isNotBlank() && !isWriteTool && !isShellTool
    val isCommand = method.contains("commandExecution") || method == "execCommandApproval" || isShellTool
    val isPermission = method.contains("permissions/requestApproval")
    val command = NativeCommandPresentation.rawCommand(params)
        .ifBlank { toolInput?.optString("command").orEmpty() }
        .ifBlank { if (isWriteTool) toolInput?.optString("file_path").orEmpty() else "" }
    val action = NativeCommandPresentation.action(params)
    val actionLabel = NativeCommandPresentation.label(action, language != "en")
    val reason = params.optString("reason")
    val title = when {
        isCommand -> nativeText(language, "允许执行命令？", "Allow command?")
        isPermission -> nativeText(language, "允许额外权限？", "Allow additional permissions?")
        genericTool -> nativeText(language, "允许使用工具？", "Allow tool?")
        else -> nativeText(language, "允许修改文件？", "Allow file changes?")
    }
    val detail = when {
        isCommand -> actionLabel
        isPermission -> nativeText(language, "模型请求扩大当前访问范围", "The model requests additional access")
        // Naming the tool is always more accurate than guessing at the intent — the previous text
        // claimed "writes outside the workspace" even for a file inside it, which is exactly the
        // kind of wording that teaches people to approve without reading.
        toolName.isNotBlank() -> listOf(NativeClaudeToolMapping.toolLabel(toolName, language), toolTitle)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        else -> nativeText(language, "模型请求写入工作区以外的位置", "The model requests writes outside the workspace")
    }
    return NativeApprovalSummary(
        title = title,
        detail = detail.ifBlank { reason },
        command = command,
        allowForSession = nativeApprovalAllowsSession(params),
    )
}
