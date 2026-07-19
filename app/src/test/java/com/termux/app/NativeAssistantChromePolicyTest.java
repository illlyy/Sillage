package com.termux.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Set;
import static org.junit.Assert.*;

public class NativeAssistantChromePolicyTest {
    private static NativeChatMessage message(String id, NativeChatRole role, String text) {
        return new NativeChatMessage(id, role, text, false, 0L, false,
            java.util.Collections.emptyList(), java.util.Collections.emptyList());
    }

    @Test public void onlyLastAssistantSegmentOwnsChromePerTurn() {
        java.util.List<NativeChatMessage> messages = Arrays.asList(
            message("u1", NativeChatRole.USER, "one"),
            message("a1", NativeChatRole.ASSISTANT, "first segment"),
            message("activity", NativeChatRole.ACTIVITY, "PROCESS2|"),
            message("a2", NativeChatRole.ASSISTANT, "second segment"),
            message("u2", NativeChatRole.USER, "two"),
            message("a3", NativeChatRole.ASSISTANT, "final")
        );
        Set<String> result = NativeAssistantChromePolicy.terminalAssistantIds(messages, false);
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("a2", "a3")), result);
        assertEquals("first segment\n\nsecond segment",
            NativeAssistantChromePolicy.terminalAssistantText(messages, false).get("a2"));
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("a2")),
            NativeAssistantChromePolicy.terminalAssistantIds(messages, true));
    }
}
