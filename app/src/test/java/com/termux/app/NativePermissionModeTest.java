package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONObject;
import org.junit.Test;

public class NativePermissionModeTest {
    @Test
    public void defaultsToExistingFullAccessBehavior() throws Exception {
        JSONObject thread = NativePermissionMode.applyThreadParams(new JSONObject(), null, "/workspace");
        assertEquals("never", thread.getString("approvalPolicy"));
        assertEquals("danger-full-access", thread.getString("sandbox"));
        assertEquals("/workspace", thread.getString("cwd"));
    }

    @Test
    public void appliesWorkspacePolicyToEveryTurn() throws Exception {
        JSONObject turn = NativePermissionMode.applyTurnParams(new JSONObject(), NativePermissionMode.WORKSPACE, "/workspace");
        assertEquals("on-request", turn.getString("approvalPolicy"));
        JSONObject sandbox = turn.getJSONObject("sandboxPolicy");
        assertEquals("workspaceWrite", sandbox.getString("type"));
        assertEquals("/workspace", sandbox.getJSONArray("writableRoots").getString(0));
        assertFalse(sandbox.getBoolean("networkAccess"));
    }

    @Test
    public void readOnlyNeverGetsNormalizedToWorkspaceWrite() throws Exception {
        JSONObject turn = NativePermissionMode.applyTurnParams(new JSONObject(), NativePermissionMode.READ_ONLY, "/workspace");
        assertEquals("readOnly", turn.getJSONObject("sandboxPolicy").getString("type"));
        assertEquals("read-only", NativePermissionMode.sandbox(NativePermissionMode.READ_ONLY));
    }
}
