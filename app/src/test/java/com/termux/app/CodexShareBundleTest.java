package com.termux.app;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CodexShareBundleTest {
    @Test
    public void androidShareBridgeIsBundled() throws Exception {
        File preload = new File("src/main/assets/codex-desktop/assets/preload.js");
        if (!preload.isFile()) preload = new File("app/src/main/assets/codex-desktop/assets/preload.js");
        assertTrue("Android preload bundle missing", preload.isFile());
        String source = new String(Files.readAllBytes(preload.toPath()), StandardCharsets.UTF_8);
        assertTrue(source.contains("__codexAndroidShare"));
        assertTrue(source.contains("readSharedFiles"));
        assertTrue(source.contains("new DragEvent(\"drop\""));
    }
}
