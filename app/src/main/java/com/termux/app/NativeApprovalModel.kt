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
    val isCommand = method.contains("commandExecution") || method == "execCommandApproval"
    val isPermission = method.contains("permissions/requestApproval")
    val command = NativeCommandPresentation.rawCommand(params)
    val action = NativeCommandPresentation.action(params)
    val actionLabel = NativeCommandPresentation.label(action, language != "en")
    val reason = params.optString("reason")
    val title = when {
        isCommand -> nativeText(language, "允许执行命令？", "Allow command?")
        isPermission -> nativeText(language, "允许额外权限？", "Allow additional permissions?")
        else -> nativeText(language, "允许修改文件？", "Allow file changes?")
    }
    val detail = when {
        isCommand -> actionLabel
        isPermission -> nativeText(language, "模型请求扩大当前访问范围", "The model requests additional access")
        else -> nativeText(language, "模型请求写入工作区以外的位置", "The model requests writes outside the workspace")
    }
    return NativeApprovalSummary(
        title = title,
        detail = detail.ifBlank { reason },
        command = command,
        allowForSession = nativeApprovalAllowsSession(params),
    )
}
