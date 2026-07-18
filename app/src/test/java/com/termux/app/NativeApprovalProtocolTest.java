package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class NativeApprovalProtocolTest {
    @Test
    public void mapsCurrentAndLegacyDecisions() throws Exception {
        assertEquals("acceptForSession", NativeApprovalProtocol.result(
            "item/commandExecution/requestApproval", null, "acceptForSession").getString("decision"));
        assertEquals("approved_for_session", NativeApprovalProtocol.result(
            "execCommandApproval", null, "acceptForSession").getString("decision"));
        assertEquals("denied", NativeApprovalProtocol.result(
            "applyPatchApproval", null, "decline").getString("decision"));
    }

    @Test
    public void grantsRequestedPermissionProfileWithCorrectScope() throws Exception {
        JSONObject requested = new JSONObject().put("network", new JSONObject().put("enabled", true));
        JSONObject result = NativeApprovalProtocol.result("item/permissions/requestApproval",
            new JSONObject().put("permissions", requested), "acceptForSession");
        assertTrue(result.getJSONObject("permissions").getJSONObject("network").getBoolean("enabled"));
        assertEquals("session", result.getString("scope"));
    }
}
