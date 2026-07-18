package com.termux.app;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class NativeCommandPresentationTest {
    @Test
    public void usesWebUiCommandActionsBeforeShellHeuristics() throws Exception {
        JSONObject item = new JSONObject().put("command", "opaque-wrapper")
            .put("commandActions", new JSONArray().put(new JSONObject().put("type", "listFiles")));
        assertEquals(NativeCommandPresentation.LIST_FILES, NativeCommandPresentation.action(item));
        assertEquals("\u5217\u51fa\u6587\u4ef6\u5939", NativeCommandPresentation.label(NativeCommandPresentation.LIST_FILES, true));
    }

    @Test
    public void summarizesCommonAndroidAndProjectCommands() throws Exception {
        assertEquals(NativeCommandPresentation.LIST_FILES, NativeCommandPresentation.action(item("Get-ChildItem -Force")));
        assertEquals(NativeCommandPresentation.READ_FILE, NativeCommandPresentation.action(item("Get-Content app/build.gradle")));
        assertEquals(NativeCommandPresentation.SEARCH_FILES, NativeCommandPresentation.action(item("rg -n TODO app/src")));
        assertEquals(NativeCommandPresentation.GIT_STATUS, NativeCommandPresentation.action(item("git status --short")));
        assertEquals(NativeCommandPresentation.RUN_TESTS, NativeCommandPresentation.action(item("./gradlew testDebugUnitTest")));
        assertEquals(NativeCommandPresentation.BUILD_PROJECT, NativeCommandPresentation.action(item("./gradlew assembleDebug")));
        assertEquals(NativeCommandPresentation.DEVICE_COMMAND, NativeCommandPresentation.action(item("adb devices")));
    }

    @Test
    public void keepsReadAndSearchSubjectsFromStructuredActions() throws Exception {
        JSONObject read = new JSONObject().put("commandActions", new JSONArray().put(
            new JSONObject().put("type", "read").put("name", "CodexChatScreen.kt")));
        assertEquals("CodexChatScreen.kt", NativeCommandPresentation.subject(read));
    }

    private static JSONObject item(String command) throws Exception {
        return new JSONObject().put("command", command);
    }
}
