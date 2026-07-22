package com.termux.app;

import android.app.Activity;
import android.content.Context;

import java.util.Map;

/** Process-scoped owner for the native app-server so turns survive Compose Activity recreation. */
final class CodexNativeRuntime {
    private static CodexAppServerBridge bridge;
    private static String fingerprint;
    private static Context appContext;
    private static boolean lastAttachRecreatedBridge;

    private CodexNativeRuntime() {}

    static synchronized CodexAppServerBridge attach(
            Activity activity,
            CodexAppServerBridge.EventListener listener,
            String configurationFingerprint,
            String baseUrl,
            String apiKey,
            String model,
            String apiFormat,
            boolean routeThroughMihomo,
            boolean forwardReasoningContext,
            int ultraSubagentLimit,
            int normalSubagentLimit,
            Map<String, String> transportEfforts,
            boolean multiAgentV2,
            boolean preventRecursiveSubagents) {
        String requestedFingerprint = String.join("\n",
            value(configurationFingerprint), value(baseUrl), value(apiKey), value(model), value(apiFormat),
            String.valueOf(routeThroughMihomo), String.valueOf(forwardReasoningContext),
            String.valueOf(ultraSubagentLimit), String.valueOf(normalSubagentLimit),
            String.valueOf(transportEfforts), String.valueOf(multiAgentV2), String.valueOf(preventRecursiveSubagents));
        if (bridge != null && !requestedFingerprint.equals(fingerprint)) shutdown();
        appContext = activity.getApplicationContext();
        lastAttachRecreatedBridge = bridge == null;
        if (bridge == null) {
            CodexTaskStore.markInterruptedTasks(activity.getApplicationContext());
            bridge = new CodexAppServerBridge(activity, listener);
            fingerprint = requestedFingerprint;
            bridge.start(baseUrl, apiKey, model, apiFormat, routeThroughMihomo,
                forwardReasoningContext, ultraSubagentLimit, normalSubagentLimit,
                transportEfforts, multiAgentV2, preventRecursiveSubagents);
        } else {
            bridge.rebind(activity, listener);
        }
        CodexOverlayService.syncKeepAlive(appContext);
        return bridge;
    }

    static synchronized void detach(CodexAppServerBridge.EventListener listener) {
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
