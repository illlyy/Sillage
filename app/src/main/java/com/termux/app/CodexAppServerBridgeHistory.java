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

/** Static protocol helpers for CodexAppServerBridgeHistory; no bridge state required. */
final class CodexAppServerBridgeHistory {
    private CodexAppServerBridgeHistory() {}

    static void findSessionFiles(File directory, java.util.Set<String> threadIds, java.util.Map<String, File> result) {
        if (directory == null || !directory.isDirectory() || threadIds.isEmpty() || result.size() >= threadIds.size()) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (result.size() >= threadIds.size()) return;
            if (child.isDirectory()) {
                findSessionFiles(child, threadIds, result);
                continue;
            }
            String name = child.getName();
            if (!name.endsWith(".jsonl")) continue;
            for (String threadId : threadIds) {
                if (!result.containsKey(threadId) && name.endsWith("-" + threadId + ".jsonl")) {
                    result.put(threadId, child);
                    break;
                }
            }
        }
    }

    static File findSessionFile(File directory, String thread) {
        if (directory == null || !directory.isDirectory() || thread == null || thread.isEmpty()) return null;
        File[] children = directory.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findSessionFile(child, thread);
                if (found != null) return found;
            } else if (child.getName().endsWith("-" + thread + ".jsonl")) {
                return child;
            }
        }
        return null;
    }

    static String resolveConversationProject(File sessionFile) {
        try {
            if (sessionFile == null) return "";
            String fallback = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(sessionFile), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JSONObject record;
                    try { record = new JSONObject(line); } catch (Exception ignored) { continue; }
                    JSONObject payload = record.optJSONObject("payload");
                    if (payload == null) continue;
                    if ("turn_context".equals(record.optString("type"))) {
                        JSONArray roots = payload.optJSONArray("workspace_roots");
                        if (roots != null && roots.length() > 0) {
                            String root = roots.optString(0, "").trim();
                            if (!root.isEmpty()) return root;
                        }
                        String cwd = payload.optString("cwd", "").trim();
                        if (!cwd.isEmpty()) fallback = cwd;
                    } else if ("session_meta".equals(record.optString("type")) && fallback.isEmpty()) {
                        fallback = payload.optString("cwd", "").trim();
                    }
                }
            }
            return fallback;
        } catch (Exception error) {
            Log.w(TAG, "Unable to resolve conversation project from " + sessionFile, error);
            return "";
        }
    }

    static String resolveConversationProject(String threadId) {
        File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
        return resolveConversationProject(findSessionFile(sessionsRoot, threadId));
    }

    static String resolveConversationTitle(String threadId) {
        try {
            File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
            File sessionFile = findSessionFile(sessionsRoot, threadId);
            if (sessionFile == null) return "";
            JSONArray history = readConversationHistory(sessionFile);
            for (int i = 0; i < history.length(); i++) {
                JSONObject message = history.optJSONObject(i);
                if (message == null || !"user".equals(message.optString("role"))) continue;
                String title = message.optString("content", "").replaceAll("\s+", " ").trim();
                if (!title.isEmpty()) return title.length() > 52 ? title.substring(0, 51) + "…" : title;
            }
        } catch (Exception error) {
            Log.w(TAG, "Unable to resolve conversation title " + CodexAppServerBridgeProtocol.shortId(threadId), error);
        }
        return "";
    }

    static long historyReasoningDurationSeconds(long startedAtMs, long finishedAtMs, boolean hasReasoning) {
        if (!hasReasoning) return 0L;
        if (startedAtMs <= 0L || finishedAtMs <= startedAtMs) return 1L;
        return Math.max(1L, (finishedAtMs - startedAtMs) / 1000L);
    }

    static long parseRecordTimestampMs(String value) {
        if (value == null || value.isEmpty()) return 0L;
        java.util.regex.Matcher match = RECORD_TIMESTAMP_PATTERN.matcher(value.trim());
        if (!match.matches()) return 0L;
        try {
            String fraction = match.group(2);
            String millis = fraction == null ? "000" : (fraction + "000").substring(0, 3);
            String zone = match.group(3);
            if ("Z".equals(zone)) zone = "+0000";
            else zone = zone.replace(":", "");
            java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US);
            parser.setLenient(false);
            java.util.Date parsed = parser.parse(match.group(1) + "." + millis + zone);
            return parsed == null ? 0L : parsed.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    static int appendHistoricalProcess(
            JSONArray messages, StringBuilder reasoning, StringBuilder command, JSONArray tools,
            long reasoningStartedAtMs, long finishedAtMs) throws Exception {
        boolean hasProcess = reasoning.length() > 0 || command.length() > 0 || tools.length() > 0;
        if (!hasProcess) return -1;
        long reasoningDuration = historyReasoningDurationSeconds(
            reasoningStartedAtMs, finishedAtMs, reasoning.length() > 0);
        JSONObject process = new JSONObject().put("duration", reasoningDuration)
            .put("reasoning", reasoning.toString().trim())
            .put("command", command.toString().trim()).put("tools", tools)
            .put("reasoningUnavailable", reasoning.length() == 0);
        String encoded = NativeBase64.encode(
            process.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        messages.put(new JSONObject().put("role", "activity").put("content", "PROCESS2|" + encoded));
        return messages.length() - 1;
    }

    static JSONArray readConversationHistory(File sessionFile) throws Exception {
        JSONArray messages = new JSONArray();
        StringBuilder reasoning = new StringBuilder();
        StringBuilder command = new StringBuilder();
        JSONArray tools = new JSONArray();
        java.util.Map<String, JSONObject> calls = new java.util.HashMap<>();
        java.util.Map<String, Integer> historicalCompactionIndices = new java.util.LinkedHashMap<>();
        JSONObject pendingUserMessage = null;
        String pendingHistoricalPlan = "";
        String pendingHistoricalPlanId = "";
        boolean sawEventMessage = false;
        int lastProcessIndex = -1;
        long reasoningStartedAtMs = 0L;
        long latestRecordAtMs = 0L;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sessionFile), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject record;
                try { record = new JSONObject(line); } catch (Exception ignored) { continue; }
                JSONObject payload = record.optJSONObject("payload");
                if (payload == null) continue;
                long recordAtMs = parseRecordTimestampMs(record.optString("timestamp", ""));
                if (recordAtMs > 0L) latestRecordAtMs = recordAtMs;
                boolean eventMessageRecord = "event_msg".equals(record.optString("type"));
                if (eventMessageRecord) sawEventMessage = true;
                JSONObject historicalCompaction = historicalCompactionItem(record, payload, recordAtMs);
                if (historicalCompaction != null) {
                    String compactionKey = historicalCompaction.optString("serverItemId", "");
                    if (compactionKey.isEmpty()) compactionKey = historicalCompaction.optString("id", "");
                    Integer existingIndex = historicalCompactionIndices.get(compactionKey);
                    String encoded = encodeHistoricalCompaction(historicalCompaction);
                    if (existingIndex != null && existingIndex >= 0 && existingIndex < messages.length()) {
                        JSONObject previous = messages.optJSONObject(existingIndex);
                        if (previous != null) {
                            previous.put("content", encoded);
                            previous.put("role", "activity");
                            messages.put(existingIndex, previous);
                        }
                    } else {
                        historicalCompactionIndices.put(compactionKey, messages.length());
                        messages.put(new JSONObject().put("role", "activity").put("content", encoded));
                    }
                    continue;
                }
                JSONObject historicalPlanItem = completedHistoricalPlanItem(record, payload);
                if (historicalPlanItem != null) {
                    String planText = normalizedHistoricalPlanText(CodexAppServerBridgeProtocol.extractAgentMessageText(historicalPlanItem));
                    if (!planText.isEmpty()) {
                        pendingHistoricalPlan = planText;
                        pendingHistoricalPlanId = historicalProtocolItemId(historicalPlanItem);
                    }
                    continue;
                }
                if (eventMessageRecord && "user_message".equals(payload.optString("type"))) {
                    appendHistoricalProposedPlan(messages, pendingHistoricalPlan, pendingHistoricalPlanId);
                    pendingHistoricalPlan = "";
                    pendingHistoricalPlanId = "";
                    JSONObject confirmed = confirmedHistoryUserMessage(pendingUserMessage, payload);
                    if (confirmed != null) messages.put(confirmed);
                    pendingUserMessage = null;
                    continue;
                }
                if ("event_msg".equals(record.optString("type")) && "plan_update".equals(payload.optString("type"))) {
                    String encodedPlan = android.util.Base64.encodeToString(
                        payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        android.util.Base64.NO_WRAP);
                    messages.put(new JSONObject().put("role", "activity").put("content", "PLAN|" + encodedPlan));
                    continue;
                }
                if ("event_msg".equals(record.optString("type")) && "sub_agent_activity".equals(payload.optString("type"))) {
                    tools.put(historySubagentActivityCard(payload));
                    continue;
                }
                if ("event_msg".equals(record.optString("type")) && "task_complete".equals(payload.optString("type"))) {
                    int pendingProcessIndex = appendHistoricalProcess(
                        messages, reasoning, command, tools, reasoningStartedAtMs, recordAtMs);
                    if (pendingProcessIndex >= 0) {
                        lastProcessIndex = pendingProcessIndex;
                        reasoning.setLength(0); command.setLength(0); tools = new JSONArray();
                        reasoningStartedAtMs = 0L;
                    }
                    long durationMs = payload.optLong("duration_ms", 0L);
                    if (lastProcessIndex >= 0 && durationMs > 0) {
                        JSONObject processMessage = messages.optJSONObject(lastProcessIndex);
                        String contentValue = processMessage == null ? "" : processMessage.optString("content", "");
                        if (contentValue.startsWith("PROCESS2|")) try {
                            String json = new String(NativeBase64.decode(contentValue.substring(9)), java.nio.charset.StandardCharsets.UTF_8);
                            JSONObject process = new JSONObject(json);
                            if (process.optLong("duration", 0L) <= 0L) {
                                process.put("duration", Math.max(1L, durationMs / 1000L));
                            }
                            String encoded = NativeBase64.encode(process.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            processMessage.put("content", "PROCESS2|" + encoded);
                            messages.put(lastProcessIndex, processMessage);
                        } catch (Exception ignored) {}
                    }
                    continue;
                }
                if (!"response_item".equals(record.optString("type"))) continue;
                String payloadType = payload.optString("type", "");
                if ("reasoning".equals(payloadType)) {
                    if (reasoningStartedAtMs == 0L) reasoningStartedAtMs = recordAtMs;
                    JSONArray summary = payload.optJSONArray("summary");
                    if (summary != null) for (int i = 0; i < summary.length(); i++) {
                        JSONObject part = summary.optJSONObject(i);
                        String value = part == null ? summary.optString(i, "") : part.optString("text", "");
                        if (!value.isEmpty()) reasoning.append(value).append('\n');
                    }
                    continue;
                }
                if ("function_call".equals(payloadType) || "custom_tool_call".equals(payloadType)) {
                    String callId = payload.optString("call_id", payload.optString("id", ""));
                    String name = payload.optString("name", "tool");
                    Object arguments = "custom_tool_call".equals(payloadType)
                        ? payload.opt("input") : payload.opt("arguments");
                    JSONObject args = historicalToolArguments(arguments);
                    calls.put(callId, new JSONObject().put("id", callId).put("name", name).put("arguments", args));
                    continue;
                }
                if ("function_call_output".equals(payloadType) || "custom_tool_call_output".equals(payloadType)) {
                    String callId = payload.optString("call_id", "");
                    JSONObject call = calls.remove(callId);
                    String output = historicalToolOutput(payload.opt("output"));
                    if (call != null) {
                        JSONObject historyItem = historyToolCard(call, output);
                        if ("commandExecution".equals(historyItem.optString("type"))) {
                            tools.put(new JSONObject(NativeCommandOutputStore.compactCommandItem(historyItem)));
                        } else {
                            tools.put(historyItem);
                        }
                    } else if (!output.isEmpty()) command.append(output.trim()).append('\n');
                    continue;
                }
                if (!"message".equals(payloadType)) continue;
                String role = payload.optString("role", "");
                if (!"user".equals(role) && !"assistant".equals(role)) continue;
                String assistantProtocolItemId = "assistant".equals(role)
                    ? historicalProtocolItemId(payload) : "";
                JSONArray content = payload.optJSONArray("content");
                if (content == null) continue;
                StringBuilder text = new StringBuilder();
                JSONArray referencedSkills = new JSONArray();
                JSONArray referencedAttachments = new JSONArray();
                for (int i = 0; i < content.length(); i++) {
                    JSONObject part = content.optJSONObject(i);
                    if (part == null) continue;
                    String type = part.optString("type", "");
                    if ("skill".equals(type)) {
                        String name = part.optString("name", "");
                        String path = part.optString("path", "");
                        if (!name.isEmpty()) referencedSkills.put(new JSONObject().put("name", name).put("path", path));
                        continue;
                    }
                    if (!"input_text".equals(type) && !"output_text".equals(type)) continue;
                    String partText = part.optString("text", "");
                    if ("user".equals(role) && partText.startsWith("Attached local file: ")) {
                        String attachmentValue = partText.substring("Attached local file: ".length());
                        int nameStart = attachmentValue.lastIndexOf(" (");
                        String path = (nameStart >= 0 ? attachmentValue.substring(0, nameStart) : attachmentValue).trim();
                        String name = partText.contains("(") ? partText.substring(partText.lastIndexOf('(') + 1).replace(")", "").trim() : new File(path).getName();
                        if (!path.isEmpty()) referencedAttachments.put(new JSONObject().put("name", name).put("path", path).put("image", false));
                        continue;
                    }
                    text.append(partText);
                }
                String value = text.toString().trim();
                if ("user".equals(role)) {
                    appendHistoricalProposedPlan(messages, pendingHistoricalPlan, pendingHistoricalPlanId);
                    pendingHistoricalPlan = "";
                    pendingHistoricalPlanId = "";
                    if (pendingUserMessage != null && !sawEventMessage
                            && !isInjectedContextMessage(pendingUserMessage.optString("content", ""))) {
                        messages.put(pendingUserMessage);
                    }
                    pendingUserMessage = historyUserMessage(value, referencedSkills, referencedAttachments);
                    continue;
                }
                if ("assistant".equals(role)) {
                    if (pendingUserMessage != null) {
                        if (!sawEventMessage
                                && !isInjectedContextMessage(pendingUserMessage.optString("content", ""))) {
                            messages.put(pendingUserMessage);
                        }
                        pendingUserMessage = null;
                    }
                    int processIndex = appendHistoricalProcess(
                        messages, reasoning, command, tools, reasoningStartedAtMs, recordAtMs);
                    if (processIndex >= 0) lastProcessIndex = processIndex;
                    reasoning.setLength(0); command.setLength(0); tools = new JSONArray();
                    reasoningStartedAtMs = 0L;
                }
                if (!value.isEmpty()) {
                    if ("assistant".equals(role)) {
                        appendHistoricalProposedPlan(messages, pendingHistoricalPlan, pendingHistoricalPlanId);
                        appendHistoricalAssistantContent(
                            messages, value, pendingHistoricalPlan, assistantProtocolItemId);
                    }
                }
                if ("assistant".equals(role)) {
                    // A dedicated Plan can be persisted without a trailing assistant mirror.
                    // Flush it here even when that mirror is empty.
                    if (value.isEmpty()) {
                        appendHistoricalProposedPlan(messages, pendingHistoricalPlan, pendingHistoricalPlanId);
                    }
                    pendingHistoricalPlan = "";
                    pendingHistoricalPlanId = "";
                }
            }
        }
        appendHistoricalProcess(
            messages, reasoning, command, tools, reasoningStartedAtMs, latestRecordAtMs);
        appendHistoricalProposedPlan(messages, pendingHistoricalPlan, pendingHistoricalPlanId);
        if (pendingUserMessage != null && !sawEventMessage
                && !isInjectedContextMessage(pendingUserMessage.optString("content", ""))) {
            messages.put(pendingUserMessage);
        }
        return messages;
    }

    static JSONArray splitHistoricalAssistantContent(String value) throws Exception {
        JSONArray parts = new JSONArray();
        java.util.List<NativePlanContentPart> scanned = NativePlanStreamParser.splitComplete(value);
        for (NativePlanContentPart part : scanned) {
            appendHistoricalContentPart(parts, part.getRole(), part.getText());
        }
        return parts;
    }

    static JSONObject completedHistoricalPlanItem(JSONObject record, JSONObject payload) {
        if (record == null || payload == null) return null;
        String recordType = CodexAppServerBridgeProtocol.normalizeItemType(record.optString("type", ""));
        String payloadType = CodexAppServerBridgeProtocol.normalizeItemType(payload.optString("type", payload.optString("item_type", "")));
        JSONObject item = payload.optJSONObject("item");
        if (item == null && "responseitem".equals(recordType) && "plan".equals(payloadType)) item = payload;
        if (item == null) return null;
        String itemType = CodexAppServerBridgeProtocol.normalizeItemType(item.optString("type", item.optString("item_type", "")));
        if (!"plan".equals(itemType)) return null;

        // response_item/plan is already a terminal persisted item. event_msg histories also
        // contain item_started records, which must not create a second partial card.
        if ("responseitem".equals(recordType)) return item;
        String status = CodexAppServerBridgeProtocol.normalizeItemType(item.optString("status", payload.optString("status", "")));
        boolean completed = payloadType.contains("completed") || payloadType.contains("complete")
            || "completed".equals(status) || "complete".equals(status);
        return completed ? item : null;
    }

    static String normalizedHistoricalPlanText(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return "";
        StringBuilder plan = new StringBuilder();
        for (NativePlanContentPart part : NativePlanStreamParser.splitComplete(text)) {
            if ("plan".equals(part.getRole())) plan.append(part.getText());
        }
        return plan.length() > 0 ? plan.toString().trim() : text;
    }

    static boolean historicalPlansEquivalent(String left, String right) {
        String a = normalizedHistoricalPlanForComparison(left);
        String b = normalizedHistoricalPlanForComparison(right);
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    static boolean historicalPlanMirror(String assistantText, String planText) {
        String assistant = normalizedHistoricalPlanForComparison(assistantText);
        String plan = normalizedHistoricalPlanForComparison(planText);
        return !assistant.isEmpty() && assistant.equals(plan);
    }

    static String normalizedHistoricalPlanForComparison(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ")
            .toLowerCase(java.util.Locale.ROOT);
    }

    static void appendHistoricalProposedPlan(JSONArray messages, String text, String itemId) throws Exception {
        if (text == null || text.trim().isEmpty()) return;
        JSONObject message = new JSONObject()
            .put("role", "activity")
            .put("content", "PROPOSED_PLAN|" + text.trim());
        if (itemId != null && !itemId.isEmpty()) {
            message.put("id", "history-proposed-plan:" + itemId);
            message.put("protocolItemId", itemId);
        }
        messages.put(message);
    }

    static String historicalProtocolItemId(JSONObject item) {
        if (item == null) return "";
        String value = item.optString("id", "").trim();
        if (value.isEmpty()) value = item.optString("itemId", "").trim();
        if (value.isEmpty()) value = item.optString("item_id", "").trim();
        return value;
    }

    static JSONObject historicalCompactionItem(JSONObject record, JSONObject payload, long recordAtMs) throws Exception {
        if (payload == null) return null;
        JSONObject item = payload.optJSONObject("item");
        if (item == null) item = payload;
        String type = CodexAppServerBridgeProtocol.normalizeItemType(item.optString("type", item.optString("item_type", payload.optString("type", ""))));
        String method = CodexAppServerBridgeProtocol.normalizeItemType(payload.optString("method", payload.optString("event", record.optString("type", ""))))
            .replace("/", "").replace(".", "");
        boolean compaction = type.contains("contextcompaction") || type.contains("contextcompacted")
            || method.contains("contextcompaction") || method.contains("contextcompacted")
            || method.contains("contextcompacted");
        if (!compaction) return null;

        String thread = payload.optString("threadId", payload.optString("thread_id", ""));
        if (thread.isEmpty()) thread = item.optString("threadId", item.optString("thread_id", ""));
        JSONObject turn = payload.optJSONObject("turn");
        String turnId = payload.optString("turnId", payload.optString("turn_id", ""));
        if (turnId.isEmpty() && turn != null) turnId = turn.optString("id", turn.optString("turnId", ""));
        String serverItemId = payload.optString("itemId", payload.optString("item_id", ""));
        if (serverItemId.isEmpty()) serverItemId = item.optString("id", item.optString("itemId", item.optString("item_id", "")));

        String rawStatus = item.optString("status", item.optString("state", payload.optString("status", payload.optString("state", ""))));
        String normalizedStatus = CodexAppServerBridgeProtocol.normalizeItemType(rawStatus);
        String status;
        if (normalizedStatus.contains("cancel")) status = "cancelled";
        else if (normalizedStatus.contains("fail") || normalizedStatus.contains("error")) status = "failed";
        else if (normalizedStatus.contains("start") || normalizedStatus.contains("run") || normalizedStatus.contains("progress")) status = "running";
        else status = "completed";
        String error = item.optString("error", payload.optString("error", payload.optString("message", "")));
        if (error.isEmpty() && "failed".equals(status)) error = rawStatus;
        long timestamp = recordAtMs > 0L ? recordAtMs : System.currentTimeMillis();
        long createdAt = item.optLong("createdAtMs", item.optLong("created_at_ms", timestamp));
        if (createdAt <= 0L) createdAt = timestamp;
        String id = "history-compaction:" + (thread.isEmpty() ? "thread" : thread) + ":"
            + (serverItemId.isEmpty() ? (turnId.isEmpty() ? String.valueOf(createdAt) : turnId) : serverItemId);
        String sourceRaw = payload.optString("source", payload.optString("origin", item.optString("source", item.optString("origin", ""))));
        boolean automatic = payload.has("automatic")
            ? payload.optBoolean("automatic", true)
            : item.optBoolean("automatic", !"manual".equalsIgnoreCase(sourceRaw));
        // `event_msg/context_compacted` is a legacy notification, not a second automatic
        // request. Keep its source explicit so a journal record with the later server item id can
        // be merged into the same timeline divider during history restore.
        boolean legacyNotification = serverItemId.isEmpty() && sourceRaw.isEmpty()
            && !payload.has("automatic") && !item.has("automatic")
            && (type.contains("contextcompacted") || type.contains("contextcompaction"));
        String source = legacyNotification ? "legacy"
            : ("manual".equalsIgnoreCase(sourceRaw) || !automatic ? "manual" : "automatic");
        return new JSONObject()
            .put("id", id)
            .put("threadId", thread)
            .put("turnId", turnId.isEmpty() ? JSONObject.NULL : turnId)
            .put("itemId", serverItemId.isEmpty() ? JSONObject.NULL : serverItemId)
            .put("source", source)
            .put("status", status)
            .put("error", error)
            .put("requestId", payload.optString("requestId", payload.optString("request_id", "")))
            .put("createdAtMs", createdAt)
            .put("updatedAtMs", timestamp)
            .put("sequence", 0L);
    }

    static String encodeHistoricalCompaction(JSONObject item) {
        return "COMPACTION|" + android.util.Base64.encodeToString(
            item.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
    }

    static void appendHistoricalContentPart(JSONArray parts, String role, String value) throws Exception {
        String text = value == null ? "" : value.trim();
        if (!text.isEmpty()) parts.put(new JSONObject().put("role", role).put("content", text));
    }

    static void appendHistoricalAssistantContent(JSONArray messages, String value) throws Exception {
        appendHistoricalAssistantContent(messages, value, "", "");
    }

    static void appendHistoricalAssistantContent(JSONArray messages, String value,
                                                         String pendingHistoricalPlan,
                                                         String protocolItemId) throws Exception {
        JSONArray parts = splitHistoricalAssistantContent(value);
        if (parts.length() == 1) {
            JSONObject only = parts.getJSONObject(0);
            if ("assistant".equals(only.optString("role"))
                    && historicalPlanMirror(only.optString("content"), pendingHistoricalPlan)) {
                // Some app-server versions persist the dedicated Plan item and then repeat its
                // untagged text as an assistant message. The Plan item is authoritative.
                return;
            }
        }
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            String content = part.getString("content");
            if ("plan".equals(part.getString("role"))) {
                if (historicalPlansEquivalent(content, pendingHistoricalPlan)) continue;
                // The surrounding messages array is already JSON encoded; a second Base64
                // layer only creates full-plan allocations on every native stream update.
                JSONObject message = new JSONObject().put("role", "activity")
                    .put("content", "PROPOSED_PLAN|" + content);
                if (protocolItemId != null && !protocolItemId.isEmpty()) {
                    message.put("protocolItemId", protocolItemId);
                }
                messages.put(message);
            } else {
                JSONObject message = new JSONObject().put("role", "assistant").put("content", content);
                if (protocolItemId != null && !protocolItemId.isEmpty()) {
                    message.put("protocolItemId", protocolItemId);
                }
                messages.put(message);
            }
        }
    }

    static JSONObject historySubagentActivityCard(JSONObject payload) throws Exception {
        String agentThread = payload.optString("agent_thread_id", "");
        String agentPath = payload.optString("agent_path", "");
        String kind = payload.optString("kind", "started");
        return new JSONObject().put("type", "collabAgentToolCall")
            .put("id", payload.optString("event_id", agentThread))
            .put("tool", "subAgentActivity")
            .put("agentThreadId", agentThread)
            .put("agentName", agentPath.isEmpty() ? "subagent" : agentPath.substring(agentPath.lastIndexOf('/') + 1))
            .put("status", "started".equals(kind) ? "working" : kind);
    }

    static JSONObject historicalToolArguments(Object value) throws Exception {
        if (value instanceof JSONObject) {
            try { return new JSONObject(value.toString()); }
            catch (Exception ignored) { return new JSONObject(); }
        }
        String raw = value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
        try { return new JSONObject(raw); }
        catch (Exception ignored) { return new JSONObject().put("raw", raw); }
    }

    static String historicalToolOutput(Object value) {
        StringBuilder output = new StringBuilder();
        appendHistoricalToolOutput(value, output);
        return output.toString().trim();
    }

    static void appendHistoricalToolOutput(Object value, StringBuilder output) {
        if (value == null || value == JSONObject.NULL) return;
        if (value instanceof JSONArray) {
            JSONArray parts = (JSONArray) value;
            for (int index = 0; index < parts.length(); index++) {
                appendHistoricalToolOutput(parts.opt(index), output);
            }
            return;
        }
        if (value instanceof JSONObject) {
            JSONObject part = (JSONObject) value;
            Object nested = part.has("text") ? part.opt("text")
                : part.has("content") ? part.opt("content")
                : part.has("output") ? part.opt("output")
                : part.has("message") ? part.opt("message")
                : part.toString();
            appendHistoricalToolOutput(nested, output);
            return;
        }
        String text = String.valueOf(value);
        if (text.isEmpty()) return;
        if (output.length() > 0 && output.charAt(output.length() - 1) != '\n'
                && text.charAt(0) != '\n') output.append('\n');
        output.append(text);
    }

    static JSONObject historyToolCard(JSONObject call, String output) throws Exception {
        String name = call.optString("name", "tool");
        JSONObject args = call.optJSONObject("arguments");
        if (args == null) args = new JSONObject();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("shell") || lower.contains("command") || lower.equals("exec") || lower.equals("terminal")) {
            Object commandValue = args.opt("command");
            String commandText = commandValue instanceof JSONArray
                ? ((JSONArray) commandValue).join(" ").replace("\"", "")
                : args.optString("command", args.optString("cmd", args.optString("raw", name)));
            JSONObject result = new JSONObject().put("type", "commandExecution").put("command", commandText)
                .put("aggregatedOutput", output);
            result.put("status", NativeCommandOutputStore.resolvedCommandStatus(result, output));
            if (args.has("cwd")) result.put("cwd", args.optString("cwd", ""));
            return result;
        }
        if (lower.contains("patch") || lower.contains("file_change") || lower.contains("write_file")) {
            return new JSONObject().put("type", "fileChange").put("changes", output.isEmpty() ? args.toString(2) : output)
                .put("status", "completed");
        }
        if (lower.contains("web_search") || lower.equals("search")) {
            return new JSONObject().put("type", "webSearch").put("query", args.optString("query", args.toString()))
                .put("output", output).put("status", "completed");
        }
        if (lower.contains("collab") || lower.contains("agent")) {
            JSONObject result = new JSONObject().put("type", "collabAgentToolCall")
                .put("id", call.optString("id", "")).put("name", name)
                .put("tool", name).put("detail", output.isEmpty() ? args.toString(2) : output)
                .put("status", lower.contains("spawn") ? "working" : "completed");
            String taskName = args.optString("task_name", args.optString("taskName", ""));
            String taskMessage = args.optString("message", args.optString("task", args.optString("prompt", "")));
            if (!taskName.isEmpty()) result.put("agentName", taskName);
            if (!taskMessage.isEmpty()) result.put("task", taskMessage);
            String agentId = args.optString("agent_id", args.optString("agentId", args.optString("thread_id", args.optString("threadId", ""))));
            try {
                JSONObject outputJson = new JSONObject(output);
                if (agentId.isEmpty()) agentId = outputJson.optString("agent_id", outputJson.optString("agentId", outputJson.optString("thread_id", outputJson.optString("threadId", ""))));
                String nickname = outputJson.optString("nickname", outputJson.optString("name", ""));
                if (!nickname.isEmpty()) result.put("agentName", nickname);
            } catch (Exception ignored) {}
            if (agentId.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matcher(output);
                if (matcher.find()) agentId = matcher.group();
            }
            if (!agentId.isEmpty()) result.put("agentThreadId", agentId);
            return result;
        }
        return new JSONObject().put("type", "mcpToolCall").put("tool", name)
            .put("arguments", args).put("output", output).put("status", "completed");
    }

    static JSONObject historyUserMessage(String value, JSONArray skills, JSONArray attachments) throws Exception {
        String text = normalizeHistoricalUserText(value);
        JSONObject message = new JSONObject().put("role", "user").put("content", text);
        if (skills != null && skills.length() > 0) message.put("skills", skills);
        if (attachments != null && attachments.length() > 0) message.put("attachments", attachments);
        return message;
    }

    static JSONObject confirmedHistoryUserMessage(JSONObject pending, JSONObject eventPayload) throws Exception {
        String fallback = pending == null ? "" : pending.optString("content", "");
        String text = normalizeHistoricalUserText(eventPayload == null ? fallback
            : eventPayload.optString("message", eventPayload.optString("text", fallback)));
        JSONObject message = pending == null
            ? new JSONObject().put("role", "user")
            : new JSONObject(pending.toString());
        message.put("content", text);
        JSONArray attachments = message.optJSONArray("attachments");
        if (attachments == null) attachments = new JSONArray();
        JSONArray localImages = eventPayload == null ? null : eventPayload.optJSONArray("local_images");
        if (localImages != null) for (int index = 0; index < localImages.length(); index++) {
            String path = localImages.optString(index, "");
            if (!path.isEmpty()) attachments.put(new JSONObject()
                .put("name", new File(path).getName()).put("path", path).put("image", true));
        }
        if (attachments.length() > 0) message.put("attachments", attachments);
        boolean hasSkills = message.optJSONArray("skills") != null && message.optJSONArray("skills").length() > 0;
        return text.isEmpty() && attachments.length() == 0 && !hasSkills ? null : message;
    }

    static String normalizeHistoricalUserText(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.startsWith(CodexAppServerBridge.IMPLEMENT_PLAN_PROMPT_PREFIX)) return text;
        String plan = text.substring(CodexAppServerBridge.IMPLEMENT_PLAN_PROMPT_PREFIX.length()).trim();
        String encoded = NativeBase64.encode(plan.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return CodexAppServerBridge.IMPLEMENT_PLAN_DISPLAY_PREFIX + encoded;
    }

    static boolean isInjectedContextMessage(String value) {
        if (value == null) return true;
        String text = value.trim();
        if (text.isEmpty()) return true;
        if (text.startsWith("<environment_context>")
            || text.startsWith("<permissions instructions>")
            || text.startsWith("<app-context>")
            || text.startsWith("<collaboration_mode>")
            || text.startsWith("<skills_instructions>")
            || text.startsWith("<plugins_instructions>")
            || text.startsWith("<goal>")
            || text.startsWith("<thread_goal>")
            || text.startsWith("<task_goal>")
            || text.startsWith("<goal_context>")
            || (text.startsWith("<cwd>") && text.contains("<filesystem>"))) return true;

        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String[] internalMarkers = new String[] {
            "<environment_context>", "<permissions instructions>", "<app-context>",
            "<collaboration_mode>", "<skills_instructions>", "<plugins_instructions>",
            "<goal>", "<thread_goal>", "<task_goal>", "<goal_context>"
        };
        int markerCount = 0;
        for (String marker : internalMarkers) if (lower.contains(marker)) markerCount++;
        if (markerCount >= 2 || (markerCount >= 1 && text.length() >= 1_500)) return true;
        return lower.startsWith("you are codex")
            && (lower.contains("filesystem sandboxing") || lower.contains("available skills")
                || lower.contains("developer instructions") || lower.contains("collaboration mode"));
    }

    static String readTurnRuntimeDiagnostic(File sessionFile, String turnId) throws Exception {
        String source = "unknown";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(sessionFile), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject record;
                try { record = new JSONObject(line); }
                catch (Exception ignored) { continue; }
                JSONObject payload = record.optJSONObject("payload");
                if (payload == null) continue;
                if ("session_meta".equals(record.optString("type"))) {
                    // A forked child rollout can later contain an inherited parent session_meta.
                    // Keep the first source so diagnostics do not mislabel a depth-1 child as vscode.
                    if ("unknown".equals(source)) source = describeSessionSource(payload.opt("source"));
                    continue;
                }
                if (!"turn_context".equals(record.optString("type"))
                        || !turnId.equals(payload.optString("turn_id"))) continue;
                return "source=" + source
                    + " model=" + payload.optString("model", "unknown")
                    + " effort=" + payload.optString("effort", "unknown")
                    + " multiAgentVersion=" + payload.optString("multi_agent_version", "disabled")
                    + " multiAgentMode=" + payload.optString("multi_agent_mode", "none");
            }
        }
        return null;
    }

    static String describeSessionSource(Object value) {
        if (value instanceof JSONObject) {
            JSONObject source = (JSONObject) value;
            JSONObject subagent = source.optJSONObject("subagent");
            JSONObject spawn = subagent == null ? null : subagent.optJSONObject("thread_spawn");
            if (spawn != null) return "subagent(depth=" + spawn.optInt("depth", -1) + ")";
            return "structured";
        }
        String text = value == null || value == JSONObject.NULL ? "unknown" : String.valueOf(value).trim();
        return text.isEmpty() ? "unknown" : text;
    }

    static String historyTailAssistantText(NativeHistorySnapshot historySnapshot) {
        if (historySnapshot == null) return "";
        java.util.List<NativeChatMessage> messages = historySnapshot.getMessages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            NativeChatMessage message = messages.get(index);
            if (message.getRole() == NativeChatRole.ACTIVITY) continue;
            return message.getRole() == NativeChatRole.ASSISTANT ? message.getContent() : "";
        }
        return "";
    }

}
