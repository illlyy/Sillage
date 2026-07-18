package com.termux.app

import org.json.JSONArray
import org.json.JSONObject

/** Codex-compatible permission presets shared by thread/start, thread/resume and turn/start. */
object NativePermissionMode {
    const val PREFERENCE_KEY = "native_permission_mode_v1"
    const val READ_ONLY = "read-only"
    const val WORKSPACE = "workspace"
    const val FULL_ACCESS = "full-access"

    @JvmStatic
    fun normalize(value: String?): String = when (value) {
        READ_ONLY -> READ_ONLY
        WORKSPACE -> WORKSPACE
        FULL_ACCESS -> FULL_ACCESS
        else -> FULL_ACCESS
    }

    @JvmStatic
    fun approvalPolicy(mode: String?): String = if (normalize(mode) == FULL_ACCESS) "never" else "on-request"

    @JvmStatic
    fun sandbox(mode: String?): String = when (normalize(mode)) {
        READ_ONLY -> "read-only"
        WORKSPACE -> "workspace-write"
        else -> "danger-full-access"
    }

    @JvmStatic
    fun sandboxPolicy(mode: String?, cwd: String): JSONObject = when (normalize(mode)) {
        READ_ONLY -> JSONObject()
            .put("type", "readOnly")
            .put("networkAccess", false)
        WORKSPACE -> JSONObject()
            .put("type", "workspaceWrite")
            .put("writableRoots", JSONArray().put(cwd))
            .put("networkAccess", false)
            .put("excludeSlashTmp", false)
            .put("excludeTmpdirEnvVar", false)
        else -> JSONObject().put("type", "dangerFullAccess")
    }

    /** Thread start/resume accepts the compact sandbox enum. */
    @JvmStatic
    fun applyThreadParams(params: JSONObject, mode: String?, cwd: String): JSONObject = params
        .put("cwd", cwd)
        .put("approvalPolicy", approvalPolicy(mode))
        .put("approvalsReviewer", "user")
        .put("sandbox", sandbox(mode))

    /** Turn start accepts the structured sticky sandbox policy. */
    @JvmStatic
    fun applyTurnParams(params: JSONObject, mode: String?, cwd: String): JSONObject = params
        .put("cwd", cwd)
        .put("approvalPolicy", approvalPolicy(mode))
        .put("approvalsReviewer", "user")
        .put("sandboxPolicy", sandboxPolicy(mode, cwd))

    @JvmStatic
    fun label(mode: String?, chinese: Boolean): String = if (chinese) when (normalize(mode)) {
        READ_ONLY -> "\u53ea\u8bfb"
        WORKSPACE -> "\u5de5\u4f5c\u533a\u8bbf\u95ee"
        else -> "\u5b8c\u5168\u8bbf\u95ee"
    } else when (normalize(mode)) {
        READ_ONLY -> "Read only"
        WORKSPACE -> "Workspace"
        else -> "Full access"
    }
}
