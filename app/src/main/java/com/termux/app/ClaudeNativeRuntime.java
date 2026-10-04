package com.termux.app;

import android.app.Activity;
import android.content.Context;

/** Process-scoped owner for the Claude Code bridge; mirrors CodexNativeRuntime. */
final class ClaudeNativeRuntime {
    private static ClaudeAgentBridge bridge;
    private static String fingerprint;
    private static Context appContext;
    private static boolean lastAttachRecreatedBridge;

    private ClaudeNativeRuntime() {}

    static synchronized ClaudeAgentBridge attach(
            Activity activity,
            NativeBackendBridge.EventListener listener,
            String configurationFingerprint,
            String claudeBinPath,
            String configDir,
            ClaudeProfile profile,
            String permissionMode,
            String allowedTools,
            String resumeThreadId,
            boolean routeThroughMihomo,
            String modelOverride,
            String effortOverride) {
        String requestedFingerprint = String.join("\n",
            value(configurationFingerprint), value(claudeBinPath),
            value(permissionMode), value(allowedTools),
            String.valueOf(routeThroughMihomo), value(effortOverride));
        // Rebuild when the configuration changed OR the previous bridge's CLI process died
        // (e.g. seccomp exit 159). A stale bridge otherwise re-binds forever without re-spawning,
        // so retrying the backend after a crash would never recover until the app is killed.
        if (bridge != null && (!requestedFingerprint.equals(fingerprint) || !isRunning())) {
            FcodeLog.d("ClaudeNativeRuntime", "Fingerprint changed or CLI process dead, rebuilding bridge");
            shutdown();
        }
        appContext = activity.getApplicationContext();
        lastAttachRecreatedBridge = bridge == null;
        try {
            FcodeLog.event(appContext, "claude_attach", new org.json.JSONObject()
                .put("recreatedBridge", lastAttachRecreatedBridge)
                .put("fingerprintChanged", bridge != null && !requestedFingerprint.equals(fingerprint))
                .put("wasRunning", isRunning())
                .put("binPath", value(claudeBinPath))
                .put("resumeThreadId", CodexAppServerBridgeProtocol.shortId(value(resumeThreadId))));
        } catch (Exception ignored) {}
        if (bridge == null) {
            CodexTaskStore.markInterruptedTasks(activity.getApplicationContext());
            bridge = new ClaudeAgentBridge(activity, listener);
            fingerprint = requestedFingerprint;
            bridge.start(claudeBinPath, configDir, profile, permissionMode,
                allowedTools, resumeThreadId, routeThroughMihomo, modelOverride, effortOverride);
        } else {
            bridge.rebind(activity, listener);
        }
        CodexOverlayService.syncKeepAlive(appContext);
        return bridge;
    }

    static synchronized void detach(NativeBackendBridge.EventListener listener) {
        if (bridge != null) bridge.detach(listener);
    }

    static synchronized boolean exists() { return bridge != null; }

    static synchronized boolean lastAttachRecreatedBridge() { return lastAttachRecreatedBridge; }

    static synchronized boolean isRunning() {
        return bridge != null && bridge.isRunning();
    }

    /**
     * Asks the live CLI for its MCP server status. Returns false when there is no session to ask —
     * the reply comes over the control channel of a running process, so callers should tell the
     * user to open a conversation rather than wait for a probe that can never arrive.
     */
    static synchronized boolean refreshMcpStatus() {
        if (bridge == null || !bridge.isRunning()) return false;
        bridge.refreshMcpStatus();
        return true;
    }

    static synchronized String currentThreadId() {
        return bridge == null ? null : bridge.currentVisibleThreadId();
    }

    static synchronized void shutdown() {
        Context context = appContext;
        if (bridge != null) bridge.stop();
        bridge = null;
        fingerprint = null;
        lastAttachRecreatedBridge = false;
        if (context != null) {
            CodexTaskStore.markInterruptedTasks(context);
            CodexOverlayService.syncKeepAlive(context);
        }
        appContext = null;
    }

    private static String value(String value) { return value == null ? "" : value; }
}
