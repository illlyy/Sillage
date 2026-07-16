package com.termux.app;

import android.app.Activity;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Direct JSONL bridge to `codex app-server --stdio`; no Node.js or external Termux required. */
final class CodexAppServerBridge {
    private static final String TAG = "IlyopCodexBridge";
    private final Activity activity;
    private final WebView webView;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<String, Long> turnStartedAtMs = new ConcurrentHashMap<>();
    private final Set<String> pendingFinalTurns = ConcurrentHashMap.newKeySet();
    private final Set<String> syntheticCompletedTurns = ConcurrentHashMap.newKeySet();
    /** Threads explicitly opened by the desktop UI; subagent threads never enter this set. */
    private final Set<String> primaryThreadIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, String> pendingPrimaryThreadTitles = new ConcurrentHashMap<>();
    private Process process;
    private BufferedWriter writer;
    private volatile String threadId;
    private volatile int initializeRequestId = -1;
    private LocalApiProxy apiProxy;
    private CodexDesktopBridge desktopBridge;

    CodexAppServerBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    void setDesktopBridge(CodexDesktopBridge bridge) { this.desktopBridge = bridge; }

    void sendDesktopRequest(JSONObject request) {
        try {
            logModelSelection(request);
            rememberPrimaryRequest(request);
            applyAndroidProviderOverrides(request);
            sendJson(request);
        }
        catch (Exception e) { if (desktopBridge != null) desktopBridge.onAppServerError(request.opt("id"), e.getMessage()); }
    }

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


    private static void logModelSelection(JSONObject request) {
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

    synchronized void start(String baseUrl, String apiKey, String model, String apiFormat) {
        android.content.SharedPreferences mobile = activity.getSharedPreferences("codex_mobile", Activity.MODE_PRIVATE);
        start(baseUrl, apiKey, model, apiFormat, mobile.getBoolean("mihomo_route_api", false), false);
    }

    synchronized void start(String baseUrl, String apiKey, String model, String apiFormat,
                            boolean routeThroughMihomo) {
        start(baseUrl, apiKey, model, apiFormat, routeThroughMihomo, false,
            CodexProviderStore.Profile.DEFAULT_ULTRA_SUBAGENT_LIMIT,
            CodexProviderStore.Profile.DEFAULT_NORMAL_SUBAGENT_LIMIT);
    }

    synchronized void start(String baseUrl, String apiKey, String model, String apiFormat,
                            boolean routeThroughMihomo, boolean forwardReasoningContext) {
        start(baseUrl, apiKey, model, apiFormat, routeThroughMihomo, forwardReasoningContext,
            CodexProviderStore.Profile.DEFAULT_ULTRA_SUBAGENT_LIMIT,
            CodexProviderStore.Profile.DEFAULT_NORMAL_SUBAGENT_LIMIT);
    }

    synchronized void start(String baseUrl, String apiKey, String model, String apiFormat,
                            boolean routeThroughMihomo, boolean forwardReasoningContext,
                            int ultraSubagentLimit, int normalSubagentLimit) {
        start(baseUrl, apiKey, model, apiFormat, routeThroughMihomo, forwardReasoningContext,
            ultraSubagentLimit, normalSubagentLimit, java.util.Collections.emptyMap());
    }

    synchronized void start(String baseUrl, String apiKey, String model, String apiFormat,
                            boolean routeThroughMihomo, boolean forwardReasoningContext,
                            int ultraSubagentLimit, int normalSubagentLimit,
                            Map<String, String> ultraTransportEfforts) {
        start(baseUrl, apiKey, model, apiFormat, routeThroughMihomo, forwardReasoningContext,
            ultraSubagentLimit, normalSubagentLimit, ultraTransportEfforts, false);
    }

    synchronized void start(String baseUrl, String apiKey, String model, String apiFormat,
                            boolean routeThroughMihomo, boolean forwardReasoningContext,
                            int ultraSubagentLimit, int normalSubagentLimit,
                            Map<String, String> ultraTransportEfforts,
                            boolean preventRecursiveSubagents) {
        final Map<String, String> transportEfforts = ultraTransportEfforts == null
            ? java.util.Collections.emptyMap() : new java.util.LinkedHashMap<>(ultraTransportEfforts);
        final int normalizedUltraLimit = CodexProviderStore.Profile.normalizeSubagentLimit(
            ultraSubagentLimit, CodexProviderStore.Profile.DEFAULT_ULTRA_SUBAGENT_LIMIT);
        final int normalizedNormalLimit = CodexProviderStore.Profile.normalizeSubagentLimit(
            normalSubagentLimit, CodexProviderStore.Profile.DEFAULT_NORMAL_SUBAGENT_LIMIT);
        stop();
        nextId.set(1);
        new Thread(() -> {
            try {
                File binary = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex");
                if (!binary.canExecute()) throw new IllegalStateException("Codex CLI is not installed");
                File home = TermuxConstants.TERMUX_HOME_DIR;
                File codexHome = new File(home, ".codex");
                home.mkdirs();
                codexHome.mkdirs();
                MihomoManager mihomo = MihomoManager.get(activity);
                if (routeThroughMihomo) mihomo.start();
                apiProxy = new LocalApiProxy(baseUrl, apiFormat, routeThroughMihomo, mihomo.mixedPort(),
                    forwardReasoningContext, transportEfforts, preventRecursiveSubagents);
                int proxyPort = apiProxy.start();
                String localBaseUrl = "http://127.0.0.1:" + proxyPort;
                String wireApi = LocalApiProxy.CODEX_WIRE_API;
                java.util.ArrayList<String> command = new java.util.ArrayList<>();
                command.add(binary.getAbsolutePath());
                command.add("-c"); command.add("model_provider=\"ilyop_android\"");
                command.add("-c"); command.add("model_providers.ilyop_android.name=\"Ilyop API\"");
                command.add("-c"); command.add("model_providers.ilyop_android.base_url=\"" + localBaseUrl + "\"");
                command.add("-c"); command.add("model_providers.ilyop_android.env_key=\"OPENAI_API_KEY\"");
                command.add("-c"); command.add("model_providers.ilyop_android.wire_api=\"" + wireApi + "\"");
                boolean enableMultiAgentV2 = !transportEfforts.isEmpty();
                for (String override : agentConfigOverrides(
                        normalizedUltraLimit, normalizedNormalLimit, enableMultiAgentV2)) {
                    command.add("-c");
                    command.add(override);
                }
                Log.i(TAG, "AGENT_LIMITS v2Enabled=" + enableMultiAgentV2
                    + " preventRecursiveSubagents=" + preventRecursiveSubagents
                    + " ultraV2Children=" + normalizedUltraLimit
                    + " ultraV2Slots=" + (normalizedUltraLimit + 1)
                    + " normalV1Children=" + (enableMultiAgentV2 ? "codex-default" : normalizedNormalLimit));
                File modelCatalog = new File(codexHome, "ilyop-model-catalog.json");
                if (modelCatalog.isFile()) {
                    command.add("-c");
                    command.add("model_catalog_json=\"" + modelCatalog.getAbsolutePath() + "\"");
                }
                if (model != null && !model.trim().isEmpty()) {
                    command.add("-c");
                    command.add("model=\"" + model.trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
                }
                command.add("app-server");
                command.add("--stdio");
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(home);
                builder.redirectErrorStream(false);
                Map<String, String> env = builder.environment();
                env.put("HOME", home.getAbsolutePath());
                env.put("CODEX_HOME", codexHome.getAbsolutePath());
                env.put("OPENAI_BASE_URL", baseUrl);
                env.put("OPENAI_API_KEY", apiKey);
                if (model != null && !model.isEmpty()) env.put("OPENAI_MODEL", model);
                env.put("PATH", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin:/system/xbin");
                env.put("TMPDIR", TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH);
                Process activeProcess = builder.start();
                process = activeProcess;
                writer = new BufferedWriter(new OutputStreamWriter(activeProcess.getOutputStream()));
                new Thread(() -> readStdout(activeProcess), "CodexAppServerOut").start();
                new Thread(() -> readStderr(activeProcess), "CodexAppServerErr").start();
                new Thread(() -> monitorProcess(activeProcess), "CodexAppServerWatch").start();
                sendInitialize();
            } catch (Exception e) {
                emit("onNativeError", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }, "CodexAppServerStart").start();
    }

    @JavascriptInterface public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (threadId == null) {
            emit("onNativeError", "Codex app-server is not ready yet");
            return;
        }
        try {
            JSONObject params = new JSONObject();
            params.put("threadId", threadId);
            JSONArray input = new JSONArray();
            input.put(new JSONObject().put("type", "text").put("text", text));
            params.put("input", input);
            sendRequest("turn/start", params);
        } catch (Exception e) {
            emit("onNativeError", e.getMessage());
        }
    }

    @JavascriptInterface public void newConversation() {
        threadId = null;
        try { sendThreadStart(); } catch (Exception e) { emit("onNativeError", e.getMessage()); }
    }

    private void sendInitialize() throws Exception {
        JSONObject clientInfo = new JSONObject()
            .put("name", "ilyop_codex_android")
            .put("title", "Codex by ilyop")
            .put("version", "0.1.0");
        initializeRequestId = sendRequest("initialize", new JSONObject().put("clientInfo", clientInfo)
            .put("capabilities", new JSONObject().put("experimentalApi", true)));
    }

    private void sendThreadStart() throws Exception {
        JSONObject params = new JSONObject();
        String cwd = TermuxConstants.TERMUX_HOME_DIR_PATH;
        android.content.SharedPreferences mobile = activity.getSharedPreferences("codex_mobile", Activity.MODE_PRIVATE);
        if (mobile.getBoolean("custom_project_root_enabled", true)) {
            String configured = mobile.getString("custom_project_root", "/storage/emulated/0/");
            if (configured != null && new File(configured).isDirectory()) cwd = configured;
        }
        params.put("cwd", cwd);
        params.put("approvalPolicy", "never");
        params.put("sandbox", "danger-full-access");
        sendRequest("thread/start", params);
    }

    private int sendRequest(String method, JSONObject params) throws Exception {
        int id = nextId.getAndIncrement();
        sendJson(new JSONObject().put("method", method).put("id", id).put("params", params));
        return id;
    }

    private synchronized void sendJson(JSONObject message) throws Exception {
        if (writer == null) throw new IllegalStateException("app-server is not running");
        android.util.Log.d(TAG, "SEND " + messageSummary(message));
        writer.write(message.toString());
        writer.newLine();
        writer.flush();
    }

    private void monitorProcess(Process activeProcess) {
        try {
            int exitCode = activeProcess.waitFor();
            boolean unexpected;
            synchronized (this) {
                unexpected = process == activeProcess;
                if (unexpected) {
                    process = null;
                    writer = null;
                    if (apiProxy != null) apiProxy.stop();
                    apiProxy = null;
                }
            }
            if (unexpected) {
                android.util.Log.w(TAG, "app-server exited with code " + exitCode);
                if (activity instanceof CodexHomeActivity) {
                    activity.runOnUiThread(() -> ((CodexHomeActivity) activity).onCodexAppServerExited(exitCode));
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
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

    private void readStdout(Process activeProcess) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject message = new JSONObject(line);
                android.util.Log.d(TAG, "RECV " + messageSummary(message));
                handleMessage(message);
            }
        } catch (Exception e) {
            if (activeProcess == process) emit("onNativeError", "app-server output stopped: " + e.getMessage());
        }
    }

    private void readStderr(Process activeProcess) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String safeLine = redactSensitiveLogLine(line);
                android.util.Log.e(TAG, "STDERR " + safeLine);
                emit("onLog", safeLine);
            }
        } catch (Exception ignored) {}
    }

    private void handleMessage(JSONObject message) throws Exception {
        rememberTurnStart(message);
        if (suppressSyntheticInterruptCompletion(message)) return;
        if (initializeRequestId >= 0 && message.has("id") && message.has("result")
                && message.optInt("id", -1) == initializeRequestId) {
            initializeRequestId = -1;
            sendJson(new JSONObject().put("method", "initialized"));
            if (desktopBridge != null) desktopBridge.onAppServerInitialized();
            return;
        }
        if (desktopBridge != null) desktopBridge.onAppServerMessage(message);
        if (message.has("error")) {
            emit("onNativeError", message.getJSONObject("error").optString("message", message.toString()));
            return;
        }
        if (message.has("id") && message.has("result")) {
            int id = message.optInt("id", -1);
            JSONObject result = message.optJSONObject("result");
            if (result != null && result.optJSONObject("thread") != null) {
                threadId = result.getJSONObject("thread").getString("id");
                primaryThreadIds.add(threadId);
                Integer responseId = message.has("id") ? message.optInt("id", -1) : -1;
                String pendingTitle = pendingPrimaryThreadTitles.remove(responseId);
                if (pendingTitle != null) CodexTaskStore.markRunning(activity, threadId, pendingTitle);
                emit("onReady", threadId);
            }
            return;
        }
        String method = message.optString("method", "");
        JSONObject params = message.optJSONObject("params");
        logCollabAgentEvent(method, params);
        if ("item/agentMessage/delta".equals(method) && params != null) {
            emit("onDelta", params.optString("delta", ""));
        } else if ("item/completed".equals(method) && isFinalAgentMessage(params)) {
            scheduleMissingTurnCompletion(params);
        } else if ("item/started".equals(method) && params != null) {
            JSONObject item = params.optJSONObject("item");
            if (item != null) emit("onItem", item.optString("type", "item"));
        } else if ("turn/completed".equals(method)) {
            clearPendingTurn(params);
            emit("onTurnComplete", "");
            if (isPrimaryTurn(params)) {
                String completedThread = params == null ? "" : params.optString("threadId", "");
                CodexTaskStore.markCompleted(activity, completedThread, turnFailed(params));
                notifyTaskCompleted();
            }
        } else if ("error".equals(method) && params != null) {
            JSONObject error = params.optJSONObject("error");
            emit("onNativeError", error == null ? params.toString() : error.optString("message", error.toString()));
        }
    }

    private static void logCollabAgentEvent(String method, JSONObject params) {
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

    private void rememberTurnStart(JSONObject message) {
        JSONObject turn = null;
        JSONObject result = message.optJSONObject("result");
        if (result != null) turn = result.optJSONObject("turn");
        if (turn == null) {
            JSONObject params = message.optJSONObject("params");
            if (params != null) turn = params.optJSONObject("turn");
        }
        if (turn == null || !turn.has("id")) return;
        String thread = turn.optString("threadId", "");
        if (thread.isEmpty()) {
            JSONObject params = message.optJSONObject("params");
            if (params != null) thread = params.optString("threadId", "");
        }
        if (thread.isEmpty()) return;
        long startedAtSeconds = turn.optLong("startedAt", 0L);
        String turnId = turn.optString("id");
        turnStartedAtMs.put(turnKey(thread, turnId),
            startedAtSeconds > 0 ? startedAtSeconds * 1000L : System.currentTimeMillis());
        scheduleTurnRuntimeDiagnostics(thread, turnId);
        scheduleTurnStallDiagnostics(thread, turnId);
    }

    private void scheduleTurnStallDiagnostics(String thread, String turn) {
        final String key = turnKey(thread, turn);
        for (long thresholdMs : new long[]{60_000L, 180_000L}) {
            webView.postDelayed(() -> {
                Long started = turnStartedAtMs.get(key);
                if (started == null) return;
                long elapsed = Math.max(0L, System.currentTimeMillis() - started);
                if (elapsed >= thresholdMs) Log.w(TAG, "COLLAB_STALL thread=" + shortId(thread)
                    + " turn=" + shortId(turn) + " elapsedMs=" + elapsed
                    + " activeTurns=" + turnStartedAtMs.size());
            }, thresholdMs);
        }
    }

    private void scheduleTurnRuntimeDiagnostics(String thread, String turn) {
        new Thread(() -> {
            File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
            for (int attempt = 0; attempt < 8; attempt++) {
                try {
                    File sessionFile = findSessionFile(sessionsRoot, thread);
                    String diagnostic = sessionFile == null ? null : readTurnRuntimeDiagnostic(sessionFile, turn);
                    if (diagnostic != null) {
                        Log.i(TAG, "RUNTIME thread=" + shortId(thread) + " turn=" + shortId(turn) + " " + diagnostic);
                        return;
                    }
                    Thread.sleep(500L);
                } catch (Exception error) {
                    Log.w(TAG, "Unable to read turn runtime diagnostics", error);
                    return;
                }
            }
            Log.w(TAG, "RUNTIME unavailable thread=" + shortId(thread) + " turn=" + shortId(turn));
        }, "CodexTurnDiagnostics").start();
    }

    private static File findSessionFile(File directory, String thread) {
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

    private static String shortId(String value) {
        if (value == null) return "unknown";
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private boolean suppressSyntheticInterruptCompletion(JSONObject message) {
        if (!"turn/completed".equals(message.optString("method"))) return false;
        JSONObject params = message.optJSONObject("params");
        String key = turnKey(params);
        if (key == null) return false;
        pendingFinalTurns.remove(key);
        turnStartedAtMs.remove(key);
        return syntheticCompletedTurns.remove(key);
    }

    private boolean isFinalAgentMessage(JSONObject params) {
        if (params == null) return false;
        JSONObject item = params.optJSONObject("item");
        return item != null && "agentMessage".equals(item.optString("type"))
            && "final_answer".equals(item.optString("phase"));
    }

    private void scheduleMissingTurnCompletion(JSONObject params) {
        String key = turnKey(params);
        if (key == null || !pendingFinalTurns.add(key)) return;
        String thread = params.optString("threadId");
        String turnId = params.optString("turnId");
        new Thread(() -> {
            try {
                Thread.sleep(1200L);
                if (!pendingFinalTurns.remove(key)) return;
                long now = System.currentTimeMillis();
                long started = turnStartedAtMs.getOrDefault(key, now - 1000L);
                syntheticCompletedTurns.add(key);
                JSONObject turn = new JSONObject()
                    .put("id", turnId)
                    .put("items", new JSONArray())
                    .put("itemsView", "notLoaded")
                    .put("status", "completed")
                    .put("error", JSONObject.NULL)
                    .put("startedAt", started / 1000L)
                    .put("completedAt", now / 1000L)
                    .put("durationMs", Math.max(1000L, now - started));
                JSONObject completed = new JSONObject().put("method", "turn/completed")
                    .put("params", new JSONObject().put("threadId", thread).put("turn", turn));
                android.util.Log.w(TAG, "Synthesizing missing turn/completed for " + turnId);
                if (desktopBridge != null) desktopBridge.onAppServerMessage(completed);
                emit("onTurnComplete", "");
                JSONObject syntheticParams = completed.optJSONObject("params");
                if (isPrimaryTurn(syntheticParams)) {
                    CodexTaskStore.markCompleted(activity, thread, false);
                    notifyTaskCompleted();
                }
                sendRequest("turn/interrupt", new JSONObject().put("threadId", thread).put("turnId", turnId));
            } catch (Exception e) {
                syntheticCompletedTurns.remove(key);
                android.util.Log.e(TAG, "Failed to complete final answer turn", e);
            }
        }, "CodexTurnCompletionFallback").start();
    }

    private void clearPendingTurn(JSONObject params) {
        String key = turnKey(params);
        if (key == null) return;
        pendingFinalTurns.remove(key);
        turnStartedAtMs.remove(key);
    }

    private static String turnKey(JSONObject params) {
        if (params == null) return null;
        JSONObject turn = params.optJSONObject("turn");
        String thread = params.optString("threadId", "");
        String turnId = turn == null ? params.optString("turnId", "") : turn.optString("id", "");
        return thread.isEmpty() || turnId.isEmpty() ? null : turnKey(thread, turnId);
    }

    private static String turnKey(String thread, String turn) { return thread + ":" + turn; }

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

    private void rememberPrimaryRequest(JSONObject request) {
        String method = request.optString("method", "");
        JSONObject params = request.optJSONObject("params");
        if (params == null) return;
        String requestedThread = params.optString("threadId", params.optString("thread_id", ""));
        if (!requestedThread.isEmpty()) primaryThreadIds.add(requestedThread);
        String title = extractTaskTitle(params);
        if ("turn/start".equals(method) && !requestedThread.isEmpty()) {
            CodexTaskStore.markRunning(activity, requestedThread, title);
        } else if ("thread/start".equals(method)) {
            int requestId = request.optInt("id", -1);
            if (requestId >= 0) pendingPrimaryThreadTitles.put(requestId, title);
        }
    }

    private static String extractTaskTitle(JSONObject params) {
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
        return prompt.isEmpty() ? "Codex ??" : prompt;
    }

    private boolean isPrimaryTurn(JSONObject params) {
        if (params == null) return false;
        String candidate = params.optString("threadId", "");
        return !candidate.isEmpty() && (primaryThreadIds.contains(candidate) || candidate.equals(threadId));
    }

    private static boolean turnFailed(JSONObject params) {
        if (params == null) return false;
        JSONObject turn = params.optJSONObject("turn");
        if (turn == null) return false;
        Object error = turn.opt("error");
        return error != null && error != JSONObject.NULL;
    }

    private void notifyTaskCompleted() {
        if (activity instanceof CodexHomeActivity) {
            activity.runOnUiThread(((CodexHomeActivity) activity)::onCodexTaskCompleted);
        }
    }

    private void emit(String function, String value) {
        android.util.Log.d(TAG, "EMIT function=" + function + " length=" + (value == null ? 0 : value.length()));
        final String quoted = JSONObject.quote(value == null ? "" : value);
        activity.runOnUiThread(() -> webView.evaluateJavascript(
            "window.codex && window.codex." + function + "(" + quoted + ")", null));
    }

    synchronized void stop() {
        threadId = null;
        initializeRequestId = -1;
        turnStartedAtMs.clear();
        pendingFinalTurns.clear();
        syntheticCompletedTurns.clear();
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        writer = null;
        if (process != null) process.destroy();
        process = null;
        if (apiProxy != null) apiProxy.stop();
        apiProxy = null;
    }
}

