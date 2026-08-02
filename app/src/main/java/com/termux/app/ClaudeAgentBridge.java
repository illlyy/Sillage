package com.termux.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Direct NDJSON bridge to the Claude Code CLI (`claude --output-format stream-json`).
 *
 * Spawns the musl arm64 binary as a child process, decodes its stream into the same normalized
 * events the Codex bridge emits (onDelta/onReasoningDelta/onCommandStarted/onTurnComplete/...),
 * and forwards outbound UI actions back through the control channel. Goal is simulated
 * client-side (Claude has no native goal), permissions ride the can_use_tool control request,
 * and AskUserQuestion is answered by injecting a tool_result user message.
 */
final class ClaudeAgentBridge extends NativeBackendBridge {
    private static final String TAG = "ClaudeAgentBridge";

    static final String PREF_GOAL = "native_thread_goal_v1_";
    static final String PREF_GOAL_STATUS = "native_thread_goal_status_v1_";

    private static final long INIT_TIMEOUT_MS = 30_000L;
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    private final Context appContext;
    private final SharedPreferences prefs;
    private volatile WeakReference<Activity> activityRef;
    private volatile NativeBackendBridge.EventListener eventListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // child process
    private Process process;
    private BufferedWriter writer;
    private Thread readerThread;
    private volatile boolean running;

    // launch configuration
    private String claudeBinPath;
    private String configDir;
    private ClaudeProfile profile;
    private String permissionMode = "default";
    private String allowedTools = "";
    private boolean routeThroughMihomo;
    private String resumeThreadId;
    private final AtomicInteger generation = new AtomicInteger();

    // conversation state
    private volatile String sessionId;
    private volatile boolean ready;
    private final ClaudeEventDecoder decoder;
    private final AtomicLong nativeSequence = new AtomicLong();

    // control channel correlation
    private final ConcurrentHashMap<String, String> pendingApprovalToolUseIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingUserInputToolUseIds = new ConcurrentHashMap<>();

    private final Runnable initTimeoutRunnable = () -> {
        if (sessionId == null && running) {
            Log.w(TAG, "Claude CLI initialization timed out");
            emit("onNativeError", "Claude CLI 初始化超时：请检查 API Key、Base URL 与网络连接。");
            stopInternal();
        }
    };

    ClaudeAgentBridge(Activity activity, NativeBackendBridge.EventListener listener) {
        this.appContext = activity.getApplicationContext();
        this.activityRef = new WeakReference<>(activity);
        this.eventListener = listener;
        this.prefs = appContext.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE);
        this.decoder = new ClaudeEventDecoder(
            () -> visibleThreadId(),
            (function, value) -> { emit(function, value); return kotlin.Unit.INSTANCE; }
        );
    }

    String visibleThreadId() { return sessionId == null ? "" : sessionId; }

    // ------------------------------------------------------------------ lifecycle

    synchronized void start(
            String claudeBinPath,
            String configDir,
            ClaudeProfile profile,
            String permissionMode,
            String allowedTools,
            String resumeThreadId,
            boolean routeThroughMihomo) {
        this.claudeBinPath = claudeBinPath;
        this.configDir = configDir;
        this.profile = profile;
        this.permissionMode = permissionMode == null ? "default" : permissionMode;
        this.allowedTools = allowedTools == null ? "" : allowedTools;
        this.resumeThreadId = resumeThreadId == null ? "" : resumeThreadId;
        this.routeThroughMihomo = routeThroughMihomo;
        spawn();
    }

    private void spawn() {
        stopInternal();
        generation.incrementAndGet();
        ready = false;
        sessionId = null;
        decoder.reset("");
        try {
            java.util.ArrayList<String> command = new java.util.ArrayList<>();
            command.add(claudeBinPath);
            command.add("--output-format");
            command.add("stream-json");
            command.add("--input-format");
            command.add("stream-json");
            command.add("--verbose");
            command.add("--permission-prompt-tool");
            command.add("stdio");
            command.add("--permission-mode");
            command.add(permissionMode);
            if (!allowedTools.isEmpty()) {
                command.add("--allowedTools");
                command.add(allowedTools);
            }
            if (!resumeThreadId.isEmpty()) {
                command.add("--resume=" + resumeThreadId);
            } else {
                command.add("--session-id");
                command.add(UUID.randomUUID().toString());
            }
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(new File(termuxHome()));
            // Credentials, base URL and models live in CLAUDE_CONFIG_DIR/settings.json
            // (written by ClaudeSettingsWriter, same shape as desktop cc-switch). Nothing
            // credential-like is passed on the command line.
            if (configDir != null && !configDir.isEmpty()) {
                ClaudeSettingsWriter.INSTANCE.write(new File(configDir), profile);
            }
            java.util.Map<String, String> env = builder.environment();
            env.put("CLAUDE_CODE_ENTRYPOINT", "android-fcode");
            env.put("CLAUDE_AGENT_SDK_SKIP_VERSION_CHECK", "1");
            if (configDir != null && !configDir.isEmpty()) env.put("CLAUDE_CONFIG_DIR", configDir);
            if (routeThroughMihomo) {
                int mixedPort = MihomoManager.get(appContext).mixedPort();
                if (mixedPort > 0) {
                    String proxy = "http://127.0.0.1:" + mixedPort;
                    env.put("HTTPS_PROXY", proxy);
                    env.put("HTTP_PROXY", proxy);
                    env.put("CLAUDE_CODE_PROXY_RESOLVES_HOSTS", "1");
                }
            }
            process = builder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            BufferedReader stdout = new BufferedReader(
                new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            readerThread = new Thread(() -> readLoop(stdout), "ClaudeAgentReader");
            readerThread.setDaemon(true);
            readerThread.start();
            // stderr must be drained too: an unread pipe fills up and blocks the CLI.
            BufferedReader stderr = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8));
            Thread stderrThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = stderr.readLine()) != null) {
                        Log.d(TAG, "cli: " + line);
                    }
                } catch (Exception ignored) {}
            }, "ClaudeAgentStderr");
            stderrThread.setDaemon(true);
            stderrThread.start();
            running = true;
            sendControl("req_init", new JSONObject().put("subtype", "initialize").put("hooks", new JSONObject()));
            // A stuck CLI (bad key, network, config) must surface instead of "connecting" forever.
            mainHandler.removeCallbacks(initTimeoutRunnable);
            mainHandler.postDelayed(initTimeoutRunnable, INIT_TIMEOUT_MS);
        } catch (Exception e) {
            Log.w(TAG, "Failed to spawn claude", e);
            emit("onNativeError", "无法启动 Claude CLI: " + e.getMessage());
        }
    }

    private String termuxHome() {
        try {
            return com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH;
        } catch (Throwable t) {
            return "/data/data/com.ilyop.codex/files/home";
        }
    }

    private void stopInternal() {
        Process current = process;
        if (current != null) {
            try { current.destroy(); } catch (Throwable ignored) {}
            process = null;
        }
        Thread thread = readerThread;
        if (thread != null) {
            try { thread.join(1200L); } catch (InterruptedException ignored) {}
            readerThread = null;
        }
        writer = null;
        running = false;
        pendingApprovalToolUseIds.clear();
        pendingUserInputToolUseIds.clear();
    }

    // ------------------------------------------------------------------ reader

    private void readLoop(BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                if (line.isBlank()) continue;
                JSONObject message;
                try {
                    message = new JSONObject(line);
                } catch (Exception ignored) {
                    continue;
                }
                handleLine(message);
            }
        } catch (Exception e) {
            if (running) Log.w(TAG, "Reader stopped", e);
        } finally {
            if (running) {
                running = false;
                emit("onNativeError", "Claude CLI 进程已退出");
            }
        }
    }

    private void handleLine(JSONObject message) {
        String type = message.optString("type");
        if ("control_request".equals(type)) {
            handleControlRequest(message);
            return;
        }
        if ("control_response".equals(type)) {
            return;
        }
        // capture session id as soon as it appears; a fresh session has none until the first user message
        String observed = decoder.observeSession(message);
        if (observed != null && !observed.isEmpty() && sessionId == null) {
            sessionId = observed;
            ready = true;
            mainHandler.removeCallbacks(initTimeoutRunnable);
            emit("onReady", observed);
            restoreClientGoal(observed);
            decoder.reset(observed);
        }
        decoder.decode(message, sessionId == null ? "" : sessionId);
    }

    private void handleControlRequest(JSONObject message) {
        try {
            handleControlRequestInternal(message);
        } catch (Exception e) {
            Log.w(TAG, "control request handling failed", e);
        }
    }

    private void handleControlRequestInternal(JSONObject message) throws Exception {
        JSONObject request = message.optJSONObject("request");
        if (request == null) return;
        String subtype = request.optString("subtype");
        String requestId = message.optString("request_id", "");
        if ("can_use_tool".equals(subtype)) {
            String toolUseId = request.optString("tool_use_id");
            String toolName = request.optString("tool_name", request.optString("title"));
            pendingApprovalToolUseIds.put(requestId, toolUseId);
            boolean isCommand = "Bash".equals(toolName) || "Shell".equals(toolName) || "bash".equals(toolName);
            JSONObject input = request.optJSONObject("input");
            JSONObject params = new JSONObject()
                .put("threadId", visibleThreadId())
                .put("requestId", requestId)
                .put("tool_use_id", toolUseId)
                .put("tool_name", toolName)
                .put("method", isCommand ? "execCommandApproval" : "toolApproval")
                .put("command", input == null ? "" : input.optString("command", input.optString("cmd")))
                .put("cwd", input == null ? "" : input.optString("cwd"))
                .put("reason", request.optString("message"))
                .put("title", request.optString("title", toolName))
                .put("input", input == null ? JSONObject.NULL : input)
                .put("message", request.optString("message"))
                .put("description", request.optString("description"));
            emit("onApprovalRequest", params.toString());
        } else if ("hook_callback".equals(subtype)) {
            respondControl(requestId, new JSONObject().put("continue", true));
        } else if ("mcp_message".equals(subtype)) {
            respondControl(requestId, new JSONObject().put("error", "mcp_message not supported"));
        } else if ("interrupt".equals(subtype)) {
            respondControl(requestId, new JSONObject());
        }
    }

    private void respondControl(String requestId, JSONObject responseBody) {
        try {
            // The CLI correlates by request_id inside the nested response object.
            JSONObject response = new JSONObject()
                .put("type", "control_response")
                .put("request_id", requestId)
                .put("response", new JSONObject()
                    .put("subtype", "success")
                    .put("request_id", requestId)
                    .put("response", responseBody));
            writeLine(response.toString());
        } catch (Exception e) {
            Log.w(TAG, "control response failed", e);
        }
    }

    private void sendControl(String requestId, JSONObject request) {
        try {
            JSONObject envelope = new JSONObject()
                .put("type", "control_request")
                .put("request_id", requestId)
                .put("request", request);
            writeLine(envelope.toString());
        } catch (Exception e) {
            Log.w(TAG, "control request failed", e);
        }
    }

    // ------------------------------------------------------------------ outbound

    private void writeLine(String line) throws Exception {
        BufferedWriter out = writer;
        if (out == null) throw new IllegalStateException("Claude CLI is not running");
        synchronized (out) {
            out.write(line);
            out.write("\n");
            out.flush();
        }
    }

    private String goalPrefix() {
        String thread = visibleThreadId();
        if (thread.isEmpty()) return "";
        String objective = java.util.Optional.ofNullable(prefs.getString(PREF_GOAL + thread, "")).orElse("");
        String status = java.util.Optional.ofNullable(prefs.getString(PREF_GOAL_STATUS + thread, "active")).orElse("active");
        if (objective.isBlank() || !"active".equals(status)) return "";
        return "【当前目标】" + objective + "\n请围绕这个目标继续工作。\n\n";
    }

    @Override
    void sendMessage(String text, String model, String effort) {
        sendMessage(text, model, effort, "", "default", "");
    }

    @Override
    void sendMessage(String text, String model, String effort, String attachmentsJson) {
        sendMessage(text, model, effort, attachmentsJson, "default", "");
    }

    @Override
    void sendMessage(String text, String model, String effort, String attachmentsJson, String collaborationMode) {
        sendMessage(text, model, effort, attachmentsJson, collaborationMode, "");
    }

    @Override
    void sendMessage(String text, String model, String effort, String attachmentsJson, String collaborationMode, String skillsJson) {
        if (text == null || text.isBlank() || !running) return;
        decoder.beginTurn(UUID.randomUUID().toString());
        String finalText = goalPrefix() + text;
        try {
            JSONObject user = new JSONObject()
                .put("type", "user")
                .put("message", new JSONObject().put("role", "user").put("content", buildContent(finalText, attachmentsJson)));
            writeLine(user.toString());
        } catch (Exception e) {
            emit("onNativeError", "发送消息失败: " + e.getMessage());
        }
    }

    /** Builds the user content array: text block plus base64 image blocks for attachments. */
    private JSONArray buildContent(String text, String attachmentsJson) {
        JSONArray blocks = new JSONArray();
        try {
            blocks.put(new JSONObject().put("type", "text").put("text", text));
        } catch (Exception e) {
            return new JSONArray().put(text);
        }
        if (attachmentsJson == null || attachmentsJson.isEmpty()) return blocks;
        try {
            JSONArray attachments = new JSONArray(attachmentsJson);
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.optJSONObject(i);
                if (attachment == null) continue;
                String path = attachment.optString("path");
                if (path.isBlank()) continue;
                if (attachment.optBoolean("image")) {
                    JSONObject image = imageBlock(path);
                    if (image != null) blocks.put(image);
                } else {
                    blocks.put(new JSONObject().put("type", "text")
                        .put("text", "[附件: " + attachment.optString("name") + " 位于 " + path + "]"));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "attachment build failed", e);
        }
        return blocks;
    }

    private JSONObject imageBlock(String path) {
        try {
            File file = new File(path);
            if (!file.isFile() || file.length() <= 0L || file.length() > MAX_IMAGE_BYTES) return null;
            byte[] data = new byte[(int) file.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int offset = 0;
                while (offset < data.length) {
                    int count = in.read(data, offset, data.length - offset);
                    if (count < 0) break;
                    offset += count;
                }
            }
            String base64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
            return new JSONObject()
                .put("type", "image")
                .put("source", new JSONObject()
                    .put("type", "base64")
                    .put("media_type", mediaTypeFor(path))
                    .put("data", base64));
        } catch (Exception e) {
            Log.w(TAG, "image block failed for " + path, e);
            return null;
        }
    }

    private static String mediaTypeFor(String path) {
        String lower = path.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }

    @Override
    void editTurn(String text, String model, String effort, int rollbackTurns) {
        if (text == null || text.isBlank() || !running) return;
        decoder.beginTurn(UUID.randomUUID().toString());
        try {
            JSONObject user = new JSONObject()
                .put("type", "user")
                .put("message", new JSONObject().put("role", "user")
                    .put("content", "请修改你上一条回复：\n\n" + text));
            writeLine(user.toString());
        } catch (Exception e) {
            emit("onNativeError", "修改消息失败: " + e.getMessage());
        }
    }

    @Override
    int steerMessage(String text, String attachmentsJson, String skillsJson) {
        sendMessage(text, "", "", "", "default", "");
        // Claude has no steer-result correlation; the host registers a fixed key that is
        // never resolved (no onSteerResult), which is harmless and keeps the submit path intact.
        return 1;
    }

    @Override
    void interruptCurrentTurn() {
        try {
            sendControl("req_interrupt_" + nativeSequence.incrementAndGet(),
                new JSONObject().put("subtype", "interrupt"));
        } catch (Exception e) {
            Log.w(TAG, "interrupt failed", e);
        }
    }

    @Override
    void setNativeContinuationPending(String sourceThreadId, boolean pending) {
        // Claude continues turns natively; the client hint is a Codex concept.
    }

    @Override
    int resumeConversation(String resumeThreadId) {
        String target = resumeThreadId == null ? "" : resumeThreadId;
        // The process is already starting (or running) with this resume id: the attach path
        // passes the retained thread at spawn, and ChatActivity calls resumeConversation
        // again right after attach. Avoid a second spawn.
        if (this.resumeThreadId.equals(target) && process != null) return 0;
        this.resumeThreadId = target;
        spawn();
        return 0;
    }

    @Override
    int restoreRetainedConversation(String resumeThreadId) {
        return resumeConversation(resumeThreadId);
    }

    @Override
    void newConversationAtCwd(String requestedCwd) {
        resumeThreadId = "";
        spawn();
    }

    @Override
    void loadSubagentHistory(String subagentThreadId, int generation) {
        // Claude transcripts live under CLAUDE_CONFIG_DIR/projects/.../subagents/.
        // First version keeps this no-op; subagent messages render inline instead.
    }

    @Override
    void loadSkills() {
        try {
            JSONArray result = new JSONArray();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            scanSkillDirectory(new File(configDir == null ? "" : configDir, "skills"), result, seen, 0);
            scanSkillDirectory(new File(termuxHome(), ".claude/skills"), result, seen, 0);
            emit("onSkills", result.toString());
        } catch (Exception e) {
            Log.w(TAG, "loadSkills failed", e);
        }
    }

    private static void scanSkillDirectory(File directory, JSONArray output, java.util.Set<String> seen, int depth) {
        if (directory == null || !directory.isDirectory() || depth > 3) return;
        File[] entries = directory.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                File skillMd = new File(entry, "SKILL.md");
                if (skillMd.isFile()) {
                    String path = entry.getAbsolutePath();
                    if (seen.add(path)) {
                        try {
                            output.put(new JSONObject()
                                .put("name", entry.getName())
                                .put("path", path)
                                .put("description", readSkillDescription(skillMd)));
                        } catch (Exception ignored) {}
                    }
                } else {
                    scanSkillDirectory(entry, output, seen, depth + 1);
                }
            }
        }
    }

    private static String readSkillDescription(File skillMd) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(skillMd), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            boolean inFront = false;
            StringBuilder description = new StringBuilder();
            int guard = 0;
            while ((line = reader.readLine()) != null && guard++ < 30) {
                String trimmed = line.trim();
                if (trimmed.equals("---")) {
                    inFront = !inFront;
                    continue;
                }
                if (inFront) {
                    if (trimmed.startsWith("description:")) {
                        description.append(trimmed.substring("description:".length()).trim());
                        break;
                    }
                    if (description.length() > 0) break;
                }
            }
            return description.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ goal (client-side)

    @Override
    void setThreadGoal(String objective) {
        String thread = visibleThreadId();
        if (thread.isEmpty() || objective == null || objective.isBlank()) return;
        prefs.edit()
            .putString(PREF_GOAL + thread, objective.trim())
            .putString(PREF_GOAL_STATUS + thread, "active")
            .apply();
        emitGoalState(thread, objective, "active");
    }

    @Override
    void getThreadGoal() {
        String thread = visibleThreadId();
        if (thread.isEmpty()) return;
        String objective = java.util.Optional.ofNullable(prefs.getString(PREF_GOAL + thread, "")).orElse("");
        String status = java.util.Optional.ofNullable(prefs.getString(PREF_GOAL_STATUS + thread, "active")).orElse("active");
        emitGoalState(thread, objective, status);
    }

    @Override
    void setThreadGoalStatus(String status) {
        String thread = visibleThreadId();
        if (thread.isEmpty()) return;
        String normalized = ("active".equals(status) || "paused".equals(status) || "budgetLimited".equals(status)) ? status : "active";
        prefs.edit().putString(PREF_GOAL_STATUS + thread, normalized).apply();
        emitGoalState(thread, java.util.Optional.ofNullable(prefs.getString(PREF_GOAL + thread, "")).orElse(""), normalized);
    }

    @Override
    void clearThreadGoal() {
        String thread = visibleThreadId();
        if (thread.isEmpty()) return;
        prefs.edit()
            .remove(PREF_GOAL + thread)
            .remove(PREF_GOAL_STATUS + thread)
            .apply();
        try {
            emit("onGoalCleared", new JSONObject().put("threadId", thread).toString());
        } catch (Exception ignored) {}
    }

    private void restoreClientGoal(String thread) {
        if (thread.isEmpty()) return;
        String objective = java.util.Optional.ofNullable(prefs.getString(PREF_GOAL + thread, "")).orElse("");
        String status = java.util.Optional.ofNullable(prefs.getString(PREF_GOAL_STATUS + thread, "active")).orElse("active");
        emitGoalState(thread, objective, status);
    }

    private void emitGoalState(String thread, String objective, String status) {
        try {
            JSONObject goal = new JSONObject()
                .put("objective", objective == null ? "" : objective.trim())
                .put("status", status == null ? "active" : status);
            emit("onGoalUpdated", new JSONObject()
                .put("threadId", thread)
                .put("goal", goal)
                .toString());
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------ approval / user input

    @Override
    void respondApprovalRequest(String rawRequest, String decision) {
        String requestId = runCatching(rawRequest, "requestId");
        if (requestId.isBlank()) return;
        boolean allow = "allow".equalsIgnoreCase(decision) || "approve".equalsIgnoreCase(decision)
            || "accept".equalsIgnoreCase(decision) || "yes".equalsIgnoreCase(decision);
        try {
            JSONObject body = new JSONObject()
                .put("behavior", allow ? "allow" : "deny");
            if (!allow) body.put("message", "User rejected this tool call");
            respondControl(requestId, body);
            pendingApprovalToolUseIds.remove(requestId);
        } catch (Exception e) {
            Log.w(TAG, "approval response failed", e);
        }
    }

    @Override
    void respondUserInput(String rawRequest, String answersJson) {
        if (rawRequest == null || rawRequest.isBlank()) return;
        String toolUseId = runCatching(rawRequest, "toolUseId");
        if (toolUseId.isBlank()) toolUseId = runCatching(rawRequest, "requestId");
        if (toolUseId.isBlank()) return;
        if (!running) return;
        try {
            JSONObject answer = new JSONObject()
                .put("type", "tool_result")
                .put("tool_use_id", toolUseId)
                .put("content", answersJson == null ? "{}" : answersJson);
            JSONObject user = new JSONObject()
                .put("type", "user")
                .put("message", new JSONObject().put("role", "user")
                    .put("content", new JSONArray().put(answer)));
            writeLine(user.toString());
            pendingUserInputToolUseIds.remove(toolUseId);
        } catch (Exception e) {
            emit("onNativeError", "提交回答失败: " + e.getMessage());
        }
    }

    private static String runCatching(String raw, String key) {
        try {
            JSONObject json = new JSONObject(raw);
            String direct = json.optString(key);
            if (!direct.isBlank()) return direct;
            JSONObject params = json.optJSONObject("params");
            if (params != null) return params.optString(key, "");
        } catch (Exception ignored) {}
        return "";
    }

    // ------------------------------------------------------------------ misc

    @Override
    void compactThread() {
        // Claude auto-compacts; report an immediate no-op completion so the UI never spins.
        emit("onCompactStatus", "completed");
    }

    @Override
    void compactThread(String nativeRequestId) { compactThread(); }

    @Override
    void refreshMcpStatus() { /* first version: no-op */ }

    @Override
    void rebind(Activity activity, NativeBackendBridge.EventListener listener) {
        if (activity != null) this.activityRef = new WeakReference<>(activity);
        this.eventListener = listener;
    }

    @Override
    void detach(NativeBackendBridge.EventListener listener) {
        if (this.eventListener != listener) return;
        this.eventListener = null;
        Activity bound = activityRef == null ? null : activityRef.get();
        if (bound == listener) activityRef.clear();
    }

    @Override
    void stop() {
        stopInternal();
    }

    @Override
    boolean isRunning() {
        return running && process != null;
    }

    @Override
    String currentVisibleThreadId() {
        return visibleThreadId();
    }

    private void emit(String function, String value) {
        NativeBackendBridge.EventListener listener = eventListener;
        if (listener == null) return;
        mainHandler.post(() -> listener.onEvent(function, value));
    }
}
