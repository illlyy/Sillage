package com.termux.app;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.util.Log;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import static com.termux.app.CodexAppServerBridge.TAG;
import static com.termux.app.CodexAppServerBridge.RECORD_TIMESTAMP_PATTERN;

/** Static protocol helpers for CodexAppServerBridgeProtocol; no bridge state required. */
final class CodexAppServerBridgeProtocol {
    private CodexAppServerBridgeProtocol() {}

    static void applyAndroidProviderOverrides(JSONObject request) throws Exception {
        String method = request.optString("method");
        if (!"thread/start".equals(method) && !"thread/resume".equals(method)) return;
        JSONObject params = request.optJSONObject("params");
        if (params == null) return;
        params.put("modelProvider", "ilyop_android");
        JSONObject config = params.optJSONObject("config");
        if (config == null) return;
        config.remove("model_provider");
        config.remove("model_providers.codex_vscode_copilot");
        if (config.length() == 0) params.remove("config");
    }

    static void logModelSelection(JSONObject request) {
        String method = request.optString("method");
        if (!"thread/start".equals(method) && !"thread/resume".equals(method) && !"turn/start".equals(method)) return;
        JSONObject params = request.optJSONObject("params");
        if (params == null) return;
        String model = params.optString("model");
        String effort = params.optString("effort");
        JSONObject config = params.optJSONObject("config");
        if (config != null) {
            if (model.isEmpty()) model = config.optString("model");
            if (effort.isEmpty()) effort = config.optString("model_reasoning_effort");
        }
        Log.i(TAG, "method=" + method + " model=" + (model.isEmpty() ? "stored/default" : model)
            + " effort=" + (effort.isEmpty() ? "stored/default" : effort));
    }

    static JSONObject navigationDetails(int generation, String thread) {
        JSONObject details = new JSONObject();
        try { details.put("generation", generation).put("thread", thread); }
        catch (Exception ignored) {}
        return details;
    }

    static String subagentSessionStatus(File sessionFile) {
        if (sessionFile == null || !sessionFile.isFile()) return "waiting";
        String status = "working";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(sessionFile), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject record;
                try { record = new JSONObject(line); } catch (Exception ignored) { continue; }
                if (!"event_msg".equals(record.optString("type"))) continue;
                JSONObject payload = record.optJSONObject("payload");
                if (payload == null) continue;
                String type = payload.optString("type", "");
                if ("task_complete".equals(type)) status = "done";
                else if ("task_failed".equals(type) || "turn_aborted".equals(type)
                        || "task_cancelled".equals(type)) status = "failed";
            }
        } catch (Exception ignored) {}
        return status;
    }

    static void scanSkillDirectory(File directory, JSONArray output, java.util.Set<String> seen, int depth) {
        if (directory == null || !directory.isDirectory() || depth > 8) return;
        File skillFile = new File(directory, "SKILL.md");
        if (skillFile.isFile()) {
            try {
                String canonical = skillFile.getCanonicalPath();
                if (seen.add(canonical)) output.put(readSkillSummary(skillFile));
            } catch (Exception ignored) {}
        }
        File[] children = directory.listFiles(File::isDirectory);
        if (children == null) return;
        for (File child : children) scanSkillDirectory(child, output, seen, depth + 1);
    }

    static JSONObject readSkillSummary(File skillFile) throws Exception {
        String fallbackName = skillFile.getParentFile().getName();
        String name = fallbackName;
        String description = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(skillFile), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            boolean frontmatter = false;
            int count = 0;
            while ((line = reader.readLine()) != null && count++ < 60) {
                String trimmed = line.trim();
                if (count == 1 && "---".equals(trimmed)) { frontmatter = true; continue; }
                if (frontmatter && "---".equals(trimmed)) break;
                if (!frontmatter) continue;
                if (trimmed.startsWith("name:")) name = unquoteYaml(trimmed.substring(5).trim(), fallbackName);
                else if (trimmed.startsWith("description:")) description = unquoteYaml(trimmed.substring(12).trim(), "");
            }
        }
        return new JSONObject().put("name", name).put("description", description).put("path", skillFile.getAbsolutePath());
    }

    static String unquoteYaml(String value, String fallback) {
        String result = value == null ? "" : value.trim();
        if (result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\"")) ||
                (result.startsWith("'") && result.endsWith("'")))) result = result.substring(1, result.length() - 1);
        return result.isEmpty() ? fallback : result;
    }

    static boolean isCompletedGoal(JSONObject goal) {
        return goal != null && "complete".equals(goal.optString("status", ""));
    }

    static void applyNativeMcpConfig(JSONObject params) throws Exception {
        JSONObject nativeConfig = NativeMcpConfigStore.threadConfig();
        JSONObject servers = nativeConfig.optJSONObject("mcp_servers");
        if (servers == null || servers.length() == 0) return;
        JSONObject config = params.optJSONObject("config");
        if (config == null) {
            config = new JSONObject();
            params.put("config", config);
        }
        config.put("mcp_servers", servers);
    }

    static void purgeStaleAgentsMaxThreads(File configFile) {
        try {
            if (!configFile.isFile()) return;
            java.util.List<String> result = new java.util.ArrayList<>();
            boolean removed = false;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(configFile), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("max_threads")) { removed = true; continue; }
                    result.add(line);
                }
            }
            if (removed) {
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(configFile)) {
                    for (String line : result) output.write((line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {
        }
    }

    static String[] agentConfigOverrides(int ultraSubagentLimit, int normalSubagentLimit) {
        return agentConfigOverrides(ultraSubagentLimit, normalSubagentLimit, false);
    }

    static String[] agentConfigOverrides(int ultraSubagentLimit, int normalSubagentLimit,
                                         boolean enableMultiAgentV2) {
        int ultra = CodexProviderStore.Profile.normalizeSubagentLimit(
            ultraSubagentLimit, CodexProviderStore.Profile.DEFAULT_ULTRA_SUBAGENT_LIMIT);
        int normal = CodexProviderStore.Profile.normalizeSubagentLimit(
            normalSubagentLimit, CodexProviderStore.Profile.DEFAULT_NORMAL_SUBAGENT_LIMIT);
        if (enableMultiAgentV2) {
            // Official Codex rejects agents.max_threads while MultiAgentV2 is enabled. V1 models
            // in a mixed catalog therefore use Codex's stable default; V2 uses the configured slots.
            return new String[]{
                "suppress_unstable_features_warning=true",
                "features.multi_agent_v2.enabled=true",
                "features.multi_agent_v2.max_concurrent_threads_per_session=" + (ultra + 1)
            };
        }
        return new String[]{
            "features.multi_agent_v2.enabled=false",
            "features.multi_agent_v2.max_concurrent_threads_per_session=" + (ultra + 1),
            "agents.max_threads=" + normal
        };
    }

    static String mcpStatusSummary(JSONObject message) {
        JSONObject error = message == null ? null : message.optJSONObject("error");
        if (error != null) return "error=" + error.optString("message", "unknown");
        JSONObject result = message == null ? null : message.optJSONObject("result");
        JSONArray data = result == null ? null : result.optJSONArray("data");
        if (data == null) return "servers=unknown";
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        int toolCount = 0;
        for (int i = 0; i < data.length(); i++) {
            JSONObject server = data.optJSONObject(i);
            if (server == null) continue;
            String name = server.optString("name", server.optString("serverName", ""));
            if (!name.isEmpty()) names.add(name);
            JSONObject tools = server.optJSONObject("tools");
            if (tools != null) toolCount += tools.length();
            else {
                JSONArray toolArray = server.optJSONArray("tools");
                if (toolArray != null) toolCount += toolArray.length();
            }
        }
        return "servers=" + data.length() + " tools=" + toolCount + " names=" + String.join(",", names);
    }

    static boolean isMcpAvailabilityError(JSONObject message) {
        JSONObject error = message == null ? null : message.optJSONObject("error");
        if (error == null) return false;
        String value = (error.optString("message", "") + " " + error.optString("data", ""))
            .toLowerCase(java.util.Locale.ROOT);
        if (!value.contains("mcp")) return false;
        return value.contains("connect") || value.contains("initializ") || value.contains("start")
            || value.contains("unavailable") || value.contains("timed out") || value.contains("timeout")
            || value.contains("closed") || value.contains("refused");
    }

    static boolean isToolDetailItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        if (item == null) return false;
        String type = normalizeItemType(item.optString("type", item.optString("item_type", "")));
        return "filechange".equals(type) || "mcptoolcall".equals(type) || "websearch".equals(type)
            || "collabagenttoolcall".equals(type) || "subagentactivity".equals(type)
            || "imageview".equals(type) || "viewimage".equals(type);
    }

    static boolean isPlanItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        return item != null && "plan".equals(normalizeItemType(item.optString("type", item.optString("item_type", ""))));
    }

    static boolean isReasoningItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        return item != null && "reasoning".equals(normalizeItemType(item.optString("type", item.optString("item_type", ""))));
    }

    static boolean isCommandItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        return item != null && "commandexecution".equals(normalizeItemType(item.optString("type", item.optString("item_type", ""))));
    }

    static String normalizeItemType(String value) {
        return value == null ? "" : value.replace("_", "").replace("-", "")
            .toLowerCase(java.util.Locale.ROOT);
    }

    static boolean isContextCompactionItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        String type = item == null
            ? (params == null ? "" : params.optString("itemType",
                params.optString("item_type", params.optString("type", ""))))
            : item.optString("type", item.optString("item_type", ""));
        String normalized = normalizeProtocolName(type);
        return "contextcompaction".equals(normalized) || "contextcompacted".equals(normalized);
    }

    static boolean isContextCompactionLifecycleMethod(String method) {
        String normalized = normalizeProtocolName(method);
        return normalized.contains("contextcompaction") &&
            (normalized.endsWith("started") || normalized.endsWith("start")
                || normalized.endsWith("completed") || normalized.endsWith("complete")
                || normalized.endsWith("failed") || normalized.endsWith("cancelled") || normalized.endsWith("canceled"));
    }

    static boolean isContextCompactionTerminalSignal(
            String method, JSONObject params) {
        if ("item/completed".equals(method) && isContextCompactionItem(params)) return true;
        String normalized = normalizeProtocolName(method);
        if (!(normalized.contains("contextcompaction")
                || normalized.contains("contextcompacted"))) return false;
        return normalized.endsWith("completed") || normalized.endsWith("complete")
            || normalized.endsWith("compacted") || normalized.endsWith("failed")
            || normalized.endsWith("cancelled") || normalized.endsWith("canceled");
    }

    static String protocolThreadId(JSONObject params) {
        if (params == null) return "";
        String value = params.optString("threadId", params.optString("thread_id", ""));
        JSONObject turn = params.optJSONObject("turn");
        JSONObject item = params.optJSONObject("item");
        if (value.isEmpty() && turn != null) value = turn.optString("threadId", turn.optString("thread_id", ""));
        if (value.isEmpty() && item != null) value = item.optString("threadId", item.optString("thread_id", ""));
        if (value.isEmpty() && item != null) value = item.optString("senderThreadId", item.optString("sender_thread_id", ""));
        return value;
    }

    static String protocolTurnId(JSONObject params) {
        if (params == null) return "";
        String value = params.optString("turnId", params.optString("turn_id", ""));
        JSONObject turn = params.optJSONObject("turn");
        if (value.isEmpty() && turn != null) value = turn.optString("id", turn.optString("turnId", turn.optString("turn_id", "")));
        JSONObject item = params.optJSONObject("item");
        if (value.isEmpty() && item != null) value = item.optString("turnId", item.optString("turn_id", ""));
        return value;
    }

    static String protocolItemId(JSONObject params) {
        if (params == null) return "";
        String value = params.optString("itemId", params.optString("item_id", ""));
        JSONObject item = params.optJSONObject("item");
        if (value.isEmpty() && item != null) value = item.optString("id", item.optString("itemId", item.optString("item_id", "")));
        return value;
    }

    static String normalizeProtocolName(String value) {
        return value == null ? "" : value
            .replace("_", "")
            .replace("-", "")
            .replace("/", "")
            .replace(".", "")
            .toLowerCase(java.util.Locale.ROOT);
    }

    static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    static String extractReasoningText(JSONObject item) {
        if (item == null) return "";
        String text = item.optString("text", "");
        if (!text.isEmpty()) return text;
        JSONArray summary = item.optJSONArray("summary");
        if (summary == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < summary.length(); i++) {
            Object part = summary.opt(i);
            if (part instanceof String) out.append(part);
            else if (part instanceof JSONObject) out.append(((JSONObject) part).optString("text", ""));
        }
        return out.toString();
    }

    static String extractAgentMessageText(JSONObject item) {
        if (item == null) return "";
        String text = item.optString("text", "");
        if (!text.isEmpty()) return text;
        JSONArray content = item.optJSONArray("content");
        if (content == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject part = content.optJSONObject(i);
            if (part == null) continue;
            String value = part.optString("text", "");
            if (!value.isEmpty()) out.append(value);
        }
        return out.toString();
    }

    static void logCollabAgentEvent(String method, JSONObject params) {
        if (params == null || (!("item/started".equals(method)) && !("item/completed".equals(method)))) return;
        JSONObject item = params.optJSONObject("item");
        if (item == null) return;
        String type = item.optString("type", "");
        String thread = shortId(params.optString("threadId", item.optString("senderThreadId", "")));
        String turn = shortId(params.optString("turnId", ""));
        String itemId = shortId(item.optString("id", ""));
        if ("collabAgentToolCall".equals(type)) {
            JSONArray receivers = item.optJSONArray("receiverThreadIds");
            Log.i(TAG, "COLLAB event=" + method + " thread=" + thread + " turn=" + turn
                + " item=" + itemId + " tool=" + item.optString("tool", "unknown")
                + " status=" + item.optString("status", "unknown")
                + " receivers=" + (receivers == null ? 0 : receivers.length()));
        } else if ("subAgentActivity".equals(type)) {
            Log.i(TAG, "COLLAB event=" + method + " thread=" + thread + " turn=" + turn
                + " item=" + itemId + " type=subAgentActivity agent="
                + shortId(item.optString("agentThreadId", "")));
        }
    }

    static boolean isTurnStartObservation(JSONObject message, String method, JSONObject turn) {
        if ("turn/started".equals(method)) return true;
        if (message == null || !message.has("result") || turn == null) return false;
        String status = turn.optString("status", "").replace("_", "")
            .replace("-", "").toLowerCase(java.util.Locale.ROOT);
        return !"completed".equals(status) && !"failed".equals(status)
            && !"cancelled".equals(status) && !"canceled".equals(status)
            && !"interrupted".equals(status);
    }

    static JSONObject turnLifecycleParams(String sourceThread, String turnId, JSONObject turn) {
        JSONObject params = new JSONObject();
        try {
            String safeThread = sourceThread == null ? "" : sourceThread;
            String safeTurnId = turnId == null ? "" : turnId;
            JSONObject safeTurn = turn == null
                ? new JSONObject() : new JSONObject(turn.toString());
            if (!safeTurnId.isEmpty() && safeTurn.optString("id", "").isEmpty()) {
                safeTurn.put("id", safeTurnId);
            }
            if (!safeThread.isEmpty()
                    && safeTurn.optString("threadId", safeTurn.optString("thread_id", "")).isEmpty()) {
                safeTurn.put("threadId", safeThread);
            }
            params.put("threadId", safeThread)
                .put("turnId", safeTurnId)
                .put("turn", safeTurn);
        } catch (Exception ignored) {}
        return params;
    }

    static boolean isAuxiliaryCompactionTurnCompletion(
            CodexAppServerBridge.CompactionTurnTracker tracker, JSONObject params) {
        return tracker != null && params != null && tracker.isAuxiliaryTurn(
            protocolThreadId(params), protocolTurnId(params));
    }

    static String turnLifecycleCallbackPayload(JSONObject params) {
        return turnLifecycleCallbackPayload(params, false);
    }

    static String turnLifecycleCallbackPayload(
            JSONObject params, boolean hasPendingContinuation) {
        JSONObject turn = params == null ? null : params.optJSONObject("turn");
        JSONObject payload = turnLifecycleParams(
            protocolThreadId(params), protocolTurnId(params), turn);
        try { payload.put("hasPendingContinuation", hasPendingContinuation); }
        catch (Exception ignored) {}
        return payload.toString();
    }

    static String shortId(String value) {
        if (value == null) return "unknown";
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    static boolean isAgentMessageItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        return item != null && "agentmessage".equals(normalizeItemType(item.optString("type", item.optString("item_type", ""))));
    }

    static boolean isFinalAgentMessage(JSONObject params) {
        if (!isAgentMessageItem(params)) return false;
        JSONObject item = params.optJSONObject("item");
        String phase = item.optString("phase", item.optString("itemPhase", item.optString("item_phase", "")))
            .replace("-", "_").replace(" ", "_").toLowerCase(java.util.Locale.ROOT);
        return "final_answer".equals(phase) || "finalanswer".equals(phase)
            || "final".equals(phase) || "answer".equals(phase)
            || item.optBoolean("final", false) || item.optBoolean("isFinal", false);
    }

    static boolean isIdleThreadStatus(JSONObject params) {
        if (params == null) return false;
        JSONObject status = params.optJSONObject("status");
        return status != null && "idle".equals(status.optString("type"));
    }

    static String turnKey(JSONObject params) {
        if (params == null) return null;
        JSONObject turn = params.optJSONObject("turn");
        String thread = params.optString("threadId", "");
        String turnId = turn == null ? params.optString("turnId", "") : turn.optString("id", "");
        return thread.isEmpty() || turnId.isEmpty() ? null : turnKey(thread, turnId);
    }

    static String turnKey(String thread, String turn) { return thread + ":" + turn; }

    static String redactSensitiveLogLine(String line) {
        if (line == null || line.isEmpty()) return "";
        String redacted = line.replaceAll("sk-[A-Za-z0-9_-]{8,}", "[REDACTED]");
        redacted = redacted.replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,}]+", "$1[REDACTED]");
        redacted = redacted.replaceAll("(?i)((?:api[_-]?key|bearer[_-]?token|experimental_bearer_token)\\s*[=:]\\s*)[^\\s,}]+", "$1[REDACTED]");
        return redacted;
    }

    static String messageSummary(JSONObject message) {
        String method = message.optString("method");
        Object id = message.opt("id");
        StringBuilder summary = new StringBuilder();
        if (!method.isEmpty()) summary.append("method=").append(method);
        if (id != null && id != JSONObject.NULL) {
            if (summary.length() > 0) summary.append(' ');
            summary.append("id=").append(id);
        }
        if (message.has("result")) summary.append(" result=true");
        if (message.has("error")) summary.append(" error=true");
        return summary.length() == 0 ? "event" : summary.toString();
    }

    static String extractTaskTitle(JSONObject params) {
        JSONArray input = params.optJSONArray("input");
        if (input != null) {
            for (int i = input.length() - 1; i >= 0; i--) {
                Object value = input.opt(i);
                if (value instanceof JSONObject) {
                    JSONObject item = (JSONObject) value;
                    String text = item.optString("text", item.optString("content", ""));
                    if (!text.isEmpty()) return text;
                } else if (value instanceof String && !((String) value).isEmpty()) return (String) value;
            }
        }
        String prompt = params.optString("prompt", params.optString("message", ""));
        return prompt.isEmpty() ? "Codex task" : prompt;
    }

    static boolean isVisibleThreadEvent(JSONObject params, String visibleThread) {
        if (visibleThread == null || visibleThread.isEmpty()) return false;
        if (params == null) return true;
        String candidate = params.optString("threadId", params.optString("thread_id", ""));
        if (candidate.isEmpty()) {
            JSONObject turn = params.optJSONObject("turn");
            if (turn != null) candidate = turn.optString("threadId", turn.optString("thread_id", ""));
        }
        if (candidate.isEmpty()) {
            JSONObject item = params.optJSONObject("item");
            if (item != null) candidate = item.optString("threadId", item.optString("thread_id", item.optString("senderThreadId", item.optString("sender_thread_id", ""))));
        }
        return candidate.isEmpty() || candidate.equals(visibleThread);
    }

    static boolean hasVerifiableRequestRoute(JSONObject params, String activeTurn) {
        if (params == null) return false;
        if (!protocolThreadId(params).isEmpty()) return true;
        String candidateTurn = protocolTurnId(params);
        return !candidateTurn.isEmpty() && activeTurn != null && candidateTurn.equals(activeTurn);
    }

    static boolean hasAnyRequestRouteIdentity(JSONObject params) {
        return params != null
            && (!protocolThreadId(params).isEmpty() || !protocolTurnId(params).isEmpty());
    }

    static String serverRequestRouteKey(Object requestId) {
        if (requestId == null || requestId == JSONObject.NULL) return "";
        if (requestId instanceof Number) return "number:" + requestId;
        if (requestId instanceof String) return "string:" + requestId;
        return requestId.getClass().getName() + ":" + requestId;
    }

    static boolean requestRouteIdentityMatches(String sourceThreadId, String turnId,
                                               JSONObject params) {
        if (params == null) return false;
        String resolvedThread = protocolThreadId(params);
        if (!resolvedThread.isEmpty() && !resolvedThread.equals(sourceThreadId)) return false;
        String resolvedTurn = protocolTurnId(params);
        if (resolvedTurn.isEmpty()) return true;
        if (turnId == null || turnId.isEmpty()) return !resolvedThread.isEmpty();
        return resolvedTurn.equals(turnId);
    }

    static boolean canBufferVisibleRouteEvents(boolean nativeUi, boolean listenerAttached,
                                                String visibleThread, boolean routeReady) {
        return nativeUi && listenerAttached && visibleThread != null
            && !visibleThread.isEmpty() && !routeReady;
    }

    static boolean isVisibleRouteFunction(String function) {
        if (function == null) return false;
        switch (function) {
            case "onReady":
            case "onHistoryWarning":
            case "onProtocolEvent":
            case "onProtocolDelta":
            case "onCommandDeltaV2":
            case "onDelta":
            case "onAssistantItemComplete":
            case "onFinalAnswer":
            case "onReasoningDelta":
            case "onReasoningComplete":
            case "onCommandStarted":
            case "onCommandDelta":
            case "onCommandComplete":
            case "onToolComplete":
            case "onPlanStarted":
            case "onPlanDelta":
            case "onPlanComplete":
            case "onPlanUpdated":
            case "onItem":
            case "onSubagentEvent":
            case "onTokenUsage":
            case "onTurnComplete":
            case "onTurnError":
            case "onCompactionRpcResult":
            case "onCompactStatus":
            case "onApprovalRequest":
            case "onUserInputRequest":
            case "onUserInputResolved":
            case "onGoalUpdated":
            case "onGoalCleared":
            case "onSteerResult":
                return true;
            default:
                return false;
        }
    }

    static boolean shouldAcceptVisibleProtocolEvent(JSONObject params, boolean routeReady,
                                                     String visibleThread, String activeTurn) {
        return shouldAcceptVisibleProtocolEvent(
            params, routeReady, visibleThread, activeTurn, isContextCompactionItem(params));
    }

    static boolean shouldAcceptVisibleProtocolEvent(JSONObject params, boolean routeReady,
                                                     String visibleThread, String activeTurn,
                                                     boolean compactionSignal) {
        if (!routeReady || !isVisibleThreadEvent(params, visibleThread)) return false;
        // Some notifications omit threadId but still carry turnId.  Once the visible turn is
        // known, reject a child/background turn instead of allowing its reasoning/tool stream to
        // enter the primary chat merely because the thread field was absent.
        if (activeTurn != null && !activeTurn.isEmpty()) {
            String candidateTurn = protocolTurnId(params);
            if (!candidateTurn.isEmpty() && !candidateTurn.equals(activeTurn)
                    && !compactionSignal) return false;
        }
        return true;
    }

    static boolean isContextCompactionSignal(String method, JSONObject params) {
        if (isContextCompactionItem(params)) return true;
        String normalized = normalizeProtocolName(method);
        return normalized.contains("contextcompaction") || normalized.contains("contextcompacted");
    }

    static boolean turnFailed(JSONObject params) {
        if (params == null) return false;
        JSONObject turn = params.optJSONObject("turn");
        if (turn == null) return false;
        String status = normalizeProtocolName(turn.optString("status", ""));
        if ("failed".equals(status) || "interrupted".equals(status)
                || "cancelled".equals(status) || "canceled".equals(status)) return true;
        Object error = turn.opt("error");
        return error != null && error != JSONObject.NULL;
    }

    static boolean isHighFrequencyNotification(String method) {
        return "item/agentMessage/delta".equals(method)
            || "item/plan/delta".equals(method)
            || "item/reasoning/summaryTextDelta".equals(method)
            || "item/reasoning/textDelta".equals(method)
            || "item/commandExecution/outputDelta".equals(method);
    }

    static boolean isHighFrequencyEmission(String function) {
        return "onDelta".equals(function) || "onPlanDelta".equals(function)
            || "onReasoningDelta".equals(function) || "onCommandDelta".equals(function)
            || "onCommandDeltaV2".equals(function) || "onProtocolDelta".equals(function);
    }

    /**
     * Turns app-server/CLI model-rejection messages into actionable guidance for the native chat.
     * The CLI's own strings are English and terse ("There's an issue with the selected model
     * (deepseek-v4-pro)...", "rejected unsupported model/reasoning combination: ..."). The raw
     * message is kept when nothing matches. Pure so it is unit-testable.
     */
    static String translateModelError(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String lower = raw.toLowerCase(Locale.ROOT);
        String model = extractModelName(raw);
        if (lower.contains("unsupported model/reasoning combination")
                || lower.contains("does not support")) {
            String effort = extractEffort(raw);
            StringBuilder hint = new StringBuilder();
            hint.append("所选模型");
            if (!model.isEmpty()) hint.append(" ").append(model);
            hint.append(" 不支持当前推理强度");
            if (!effort.isEmpty()) hint.append(" ").append(effort);
            hint.append("。请降低思维强度，或在「模型目录」中为该模型添加该强度后重试。");
            return hint.toString();
        }
        if (lower.contains("issue with the selected model")
                || lower.contains("may not exist")
                || lower.contains("may not have access")
                || lower.contains("model not found")
                || lower.contains("unknown model")) {
            StringBuilder hint = new StringBuilder();
            hint.append("所选模型");
            if (!model.isEmpty()) hint.append(" ").append(model);
            hint.append(" 无法使用：它不在当前 API 的可用模型列表中，或该 API Key 无权访问它。");
            hint.append("请到「模型目录」确认已添加该模型，或更换为 API 提供的模型 ID。");
            return hint.toString();
        }
        return raw;
    }

    /** Extracts a model id from messages like "model (deepseek-v4-pro)" or "model \"gpt-5.6-sol\"". */
    static String extractModelName(String raw) {
        if (raw == null) return "";
        java.util.regex.Matcher m = MODEL_NAME_PATTERN.matcher(raw);
        if (m.find()) {
            String first = m.group(1);
            String second = m.group(2);
            return first == null ? (second == null ? "" : second) : first;
        }
        return "";
    }

    /** Extracts the offending reasoning effort from "does not support \"ultra\"". */
    static String extractEffort(String raw) {
        if (raw == null) return "";
        java.util.regex.Matcher m = EFFORT_PATTERN.matcher(raw);
        if (m.find()) return m.group(1);
        return "";
    }

    private static final java.util.regex.Pattern MODEL_NAME_PATTERN = java.util.regex.Pattern.compile(
        "(?:model|selected model)\\s*\\(([^)]+)\\)|model\\s+[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern EFFORT_PATTERN = java.util.regex.Pattern.compile(
        "does not support\\s+[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

}
