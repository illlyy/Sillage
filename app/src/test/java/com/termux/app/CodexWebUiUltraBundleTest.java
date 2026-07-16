package com.termux.app;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CodexWebUiUltraBundleTest {
    @Test
    public void ultraIsEnabledAtBothBundleCallSites() throws Exception {
        File assets = new File("src/main/assets/codex-desktop/assets");
        if (!assets.isDirectory()) assets = new File("app/src/main/assets/codex-desktop/assets");
        assertTrue("Codex desktop asset directory missing", assets.isDirectory());
        File selector = find(assets, "BfuFOm2j.js");
        File threadPage = find(assets, "BF1QkwFT.js");
        String selectorText = new String(Files.readAllBytes(selector.toPath()), StandardCharsets.UTF_8);
        String threadText = new String(Files.readAllBytes(threadPage.toPath()), StandardCharsets.UTF_8);
        assertFalse(selectorText.contains("1186680773"));
        assertFalse(threadText.contains("1186680773"));
        assertTrue(selectorText.contains("            l = i;"));
        assertTrue(threadText.contains("includeUltraReasoningEffort: true,"));
        File zhCn = find(assets, "zh-CN-DWd1t5CM.js");
        String zhCnText = new String(Files.readAllBytes(zhCn.toPath()), StandardCharsets.UTF_8);
        assertTrue(zhCnText.contains("\"composer.mode.local.reasoning.xhigh.label\":`\u6781\u9ad8`"));
        assertTrue(zhCnText.contains("\"composer.mode.local.reasoning.ultra.label\":`Ultra\uff08\u4e3b\u52a8\u591a\u4ee3\u7406\uff09`"));
        assertFalse(zhCnText.contains("\"composer.mode.local.reasoning.ultra.label\":`\u6781\u9ad8`"));
    }

    @Test
    public void backgroundSubagentUiIsEnabledAndCannotBeRemotelyFiltered() throws Exception {
        File assets = new File("src/main/assets/codex-desktop/assets");
        if (!assets.isDirectory()) assets = new File("app/src/main/assets/codex-desktop/assets");
        assertTrue("Codex desktop asset directory missing", assets.isDirectory());
        File featureGate = find(assets, "Cs6pZQzU.js");
        String featureGateText = new String(Files.readAllBytes(featureGate.toPath()), StandardCharsets.UTF_8);
        assertFalse(featureGateText.contains("1221508807"));
        assertTrue(featureGateText.contains("function Wo(){return!0}"));

        File composer = find(assets, "DN861ZdI.js");
        String composerText = new String(Files.readAllBytes(composer.toPath()), StandardCharsets.UTF_8);
        assertTrue(composerText.contains("composer.backgroundSubagents.summary"));
        assertTrue(composerText.contains("collabAgentToolCall"));
    }

    @Test
    public void androidPreloadConsumesBatchedAppServerMessages() throws Exception {
        File assets = new File("src/main/assets/codex-desktop/assets");
        if (!assets.isDirectory()) assets = new File("app/src/main/assets/codex-desktop/assets");
        assertTrue("Codex desktop asset directory missing", assets.isDirectory());
        File preload = find(assets, "preload.js");
        String text = new String(Files.readAllBytes(preload.toPath()), StandardCharsets.UTF_8);
        assertTrue("Android batch receiver missing",
            text.contains("window.__codexDesktopReceiveBatch ??="));
        assertTrue("Batch receiver must forward every message to the normal IPC handler",
            text.contains("for (const incoming of incomingBatch) window.__codexDesktopReceive(incoming)"));
    }

    @Test
    public void androidBridgeSuppliesLocalCustomAgentsShapeRequiredByComposer() throws Exception {
        File bridge = new File("src/main/java/com/termux/app/CodexDesktopBridge.java");
        if (!bridge.isFile()) bridge = new File("app/src/main/java/com/termux/app/CodexDesktopBridge.java");
        assertTrue("Codex desktop bridge source missing", bridge.isFile());
        String text = new String(Files.readAllBytes(bridge.toPath()), StandardCharsets.UTF_8);
        assertTrue("Composer add-context menu requires an agents array",
            text.contains("url.endsWith(\"/local-custom-agents\")")
                && text.contains("new JSONObject().put(\"agents\", new JSONArray())"));
    }

    private static File find(File directory, String suffix) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(suffix));
        assertNotNull(files);
        assertEquals("Expected one bundle ending with " + suffix, 1, files.length);
        return files[0];
    }
}
