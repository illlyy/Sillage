package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI sends accept / acceptForSession / decline / cancel.
 * Both accept variants must allow the tool; cancel maps to deny
 * at the control-protocol level (behavior allow/deny only).
 */
class ClaudeAgentBridgeApprovalTest {

    @Test
    fun `accept variants allow`() {
        assertEquals("allow", ClaudeAgentBridge.mapApprovalBehavior("accept"))
        assertEquals("allow", ClaudeAgentBridge.mapApprovalBehavior("acceptForSession"))
        assertEquals("allow", ClaudeAgentBridge.mapApprovalBehavior("ACCEPTFORSESSION"))
        assertEquals("allow", ClaudeAgentBridge.mapApprovalBehavior("allow"))
        assertEquals("allow", ClaudeAgentBridge.mapApprovalBehavior("approve"))
        assertEquals("allow", ClaudeAgentBridge.mapApprovalBehavior("yes"))
    }

    @Test
    fun `decline cancel and unknown deny`() {
        assertEquals("deny", ClaudeAgentBridge.mapApprovalBehavior("decline"))
        assertEquals("deny", ClaudeAgentBridge.mapApprovalBehavior("cancel"))
        assertEquals("deny", ClaudeAgentBridge.mapApprovalBehavior(""))
        assertEquals("deny", ClaudeAgentBridge.mapApprovalBehavior(null))
        assertEquals("deny", ClaudeAgentBridge.mapApprovalBehavior("bogus"))
    }

    @Test
    fun `cancel is flagged separately from decline`() {
        assertTrue(ClaudeAgentBridge.isApprovalCancelled("cancel"))
        assertTrue(ClaudeAgentBridge.isApprovalCancelled("CANCEL"))
        assertFalse(ClaudeAgentBridge.isApprovalCancelled("decline"))
        assertFalse(ClaudeAgentBridge.isApprovalCancelled("accept"))
        assertFalse(ClaudeAgentBridge.isApprovalCancelled(null))
    }
}
