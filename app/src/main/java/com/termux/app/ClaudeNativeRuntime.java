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
            String modelOverride) {
        String requestedFingerprint = String.join("\n",
            value(configurationFingerprint), value(claudeBinPath),
            value(permissionMode), value(allowedTools),
            String.valueOf(routeThroughMihomo));
        if (bridge != null && !requestedFingerprint.equals(fingerprint)) shutdown();
        appContext = activity.getApplicationContext();
        lastAttachRecreatedBridge = bridge == null;
        if (bridge == null) {
            CodexTaskStore.markInterruptedTasks(activity.getApplicationContext());
            bridge = new ClaudeAgentBridge(activity, listener);
            fingerprint = requestedFingerprint;
            bridge.start(claudeBinPath, configDir, profile, permissionMode,
                allowedTools, resumeThreadId, routeThroughMihomo, modelOverride);
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
