package com.termux.app

import org.json.JSONObject

/** Wire compatibility for current v2 and legacy app-server approval callbacks. */
object NativeApprovalProtocol {
    @JvmStatic
    fun isApprovalMethod(method: String?): Boolean = method in setOf(
        "item/commandExecution/requestApproval",
        "item/fileChange/requestApproval",
        "item/permissions/requestApproval",
        "execCommandApproval",
        "applyPatchApproval",
    )

    @JvmStatic
    fun result(method: String, params: JSONObject?, decision: String): JSONObject {
        if (method == "item/permissions/requestApproval") {
            val accepted = decision == "accept" || decision == "acceptForSession"
            val requested = params?.optJSONObject("permissions")
            return JSONObject()
                .put("permissions", if (accepted && requested != null) requested else JSONObject())
                .put("scope", if (decision == "acceptForSession") "session" else "turn")
        }
        val legacy = method == "execCommandApproval" || method == "applyPatchApproval"
        val wireDecision = if (legacy) when (decision) {
            "accept" -> "approved"
            "acceptForSession" -> "approved_for_session"
            "cancel" -> "abort"
            else -> "denied"
        } else when (decision) {
            "accept", "acceptForSession", "cancel" -> decision
            else -> "decline"
        }
        return JSONObject().put("decision", wireDecision)
    }
}
