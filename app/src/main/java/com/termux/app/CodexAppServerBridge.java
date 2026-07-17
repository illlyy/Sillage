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
    interface EventListener {
        void onEvent(String function, String value);
    }

    private static final String TAG = "IlyopCodexBridge";
    private final Activity activity;
    private final WebView webView;
    private final EventListener eventListener;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<String, Long> turnStartedAtMs = new ConcurrentHashMap<>();
    private final Set<String> pendingFinalTurns = ConcurrentHashMap.newKeySet();
    private final Set<String> syntheticCompletedTurns = ConcurrentHashMap.newKeySet();
    private final Set<String> streamedAgentItemIds = ConcurrentHashMap.newKeySet();
    private long lastAgentDeltaAt;
    private int agentDeltaCount;
    private int agentDeltaChars;
    static final long MISSING_TURN_COMPLETION_CHECK_MS = 1200L;
    static final long MISSING_TURN_COMPLETION_WARNING_MS = 60_000L;
    static final int MISSING_TURN_COMPLETION_IDLE_CHECKS = 2;
    /** Threads explicitly opened by the desktop UI; subagent threads never enter this set. */
    private final Set<String> primaryThreadIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, String> pendingPrimaryThreadTitles = new ConcurrentHashMap<>();
    private Process process;
    private BufferedWriter writer;
    private volatile String threadId;
    private volatile String activeTurnId;
    private volatile int initializeRequestId = -1;
    private volatile int editRollbackRequestId = -1;
    private volatile String pendingEditedText;
    private volatile String pendingEditedModel;
    private volatile String pendingEditedEffort;
    private LocalApiProxy apiProxy;
    private CodexDesktopBridge desktopBridge;

    CodexAppServerBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.eventListener = null;
    }

    CodexAppServerBridge(Activity activity, EventListener eventListener) {
        this.activity = activity;
        this.webView = null;
        this.eventListener = eventListener;
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
        sendMessage(text, null, null);
    }

    void sendMessage(String text, String model, String effort) {
        sendMessage(text, model, effort, "[]");
    }

    void sendMessage(String text, String model, String effort, String attachmentsJson) {
        sendMessage(text, model, effort, attachmentsJson, null);
    }

    void sendMessage(String text, String model, String effort, String attachmentsJson, String collaborationMode) {
        sendMessage(text, model, effort, attachmentsJson, collaborationMode, "[]");
    }

    void sendMessage(String text, String model, String effort, String attachmentsJson, String collaborationMode, String skillsJson) {
        if (text == null) text = "";
        JSONArray attachments;
        try { attachments = new JSONArray(attachmentsJson == null ? "[]" : attachmentsJson); }
        catch (Exception ignored) { attachments = new JSONArray(); }
        JSONArray skills;
        try { skills = new JSONArray(skillsJson == null ? "[]" : skillsJson); }
        catch (Exception ignored) { skills = new JSONArray(); }
        if (text.trim().isEmpty() && attachments.length() == 0 && skills.length() == 0) return;
        if (threadId == null) {
            emit("onNativeError", "Codex app-server is not ready yet");
            return;
        }
        try {
            JSONObject params = new JSONObject();
            params.put("threadId", threadId);
            JSONArray input = new JSONArray();
            if (!text.trim().isEmpty()) input.put(new JSONObject().put("type", "text").put("text", text));
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.optJSONObject(i);
                if (attachment == null) continue;
                String path = attachment.optString("path", "");
                if (path.isEmpty()) continue;
                if (attachment.optBoolean("image", false)) {
                    input.put(new JSONObject().put("type", "localImage").put("path", path));
                } else {
                    input.put(new JSONObject().put("type", "text").put("text",
                        "Attached local file: " + path + " (" + attachment.optString("name", "file") + ")"));
                }
            }
            for (int i = 0; i < skills.length(); i++) {
                JSONObject skill = skills.optJSONObject(i);
                if (skill == null) continue;
                String name = skill.optString("name", "");
                String path = skill.optString("path", "");
                if (!name.isEmpty() && !path.isEmpty()) {
                    input.put(new JSONObject().put("type", "skill").put("name", name).put("path", path));
                }
            }
            params.put("input", input);
            if (model != null && !model.trim().isEmpty()) params.put("model", model);
            if (effort != null && !effort.trim().isEmpty()) params.put("effort", effort);
            if (collaborationMode != null && !collaborationMode.trim().isEmpty()) {
                JSONObject settings = new JSONObject();
                if (model != null && !model.trim().isEmpty()) settings.put("model", model);
                if (effort != null && !effort.trim().isEmpty()) settings.put("reasoning_effort", effort);
                settings.put("developer_instructions", JSONObject.NULL);
                params.put("collaborationMode", new JSONObject()
                    .put("mode", collaborationMode)
                    .put("settings", settings));
            }
            sendRequest("turn/start", params);
        } catch (Exception e) {
            emit("onNativeError", e.getMessage());
        }
    }

    void editTurn(String text, String model, String effort, int rollbackTurns) {
        if (threadId == null || text == null || text.trim().isEmpty()) return;
        try {
            pendingEditedText = text;
            pendingEditedModel = model;
            pendingEditedEffort = effort;
            editRollbackRequestId = sendRequest("thread/rollback", new JSONObject()
                .put("threadId", threadId)
                .put("numTurns", Math.max(1, rollbackTurns)));
        } catch (Exception e) { emit("onNativeError", e.getMessage()); }
    }

    @JavascriptInterface public void newConversation() {
        threadId = null;
        try { sendThreadStart(); } catch (Exception e) { emit("onNativeError", e.getMessage()); }
    }

    void resumeConversation(String resumeThreadId) {
        if (resumeThreadId == null || resumeThreadId.trim().isEmpty()) return;
        threadId = null;
        activeTurnId = null;
        new Thread(() -> {
            try {
                File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
                File sessionFile = findSessionFile(sessionsRoot, resumeThreadId);
                emit("onHistory", sessionFile == null ? "[]" : readConversationHistory(sessionFile).toString());
                sendRequest("thread/resume", new JSONObject().put("threadId", resumeThreadId));
            } catch (Exception e) {
                emit("onNativeError", e.getMessage());
            }
        }, "CodexConversationResume").start();
    }

    void loadSubagentHistory(String subagentThreadId) {
        if (subagentThreadId == null || subagentThreadId.trim().isEmpty()) return;
        new Thread(() -> {
            JSONObject result = new JSONObject();
            try {
                File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
                File sessionFile = findSessionFile(sessionsRoot, subagentThreadId);
                result.put("threadId", subagentThreadId);
                result.put("messages", sessionFile == null ? new JSONArray() : readConversationHistory(sessionFile));
                result.put("found", sessionFile != null);
            } catch (Exception error) {
                try {
                    result.put("threadId", subagentThreadId);
                    result.put("messages", new JSONArray());
                    result.put("error", error.getMessage());
                } catch (Exception ignored) {}
            }
            emit("onSubagentHistory", result.toString());
        }, "CodexSubagentHistory").start();
    }

    void loadSkills() {
        new Thread(() -> {
            JSONArray result = new JSONArray();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            scanSkillDirectory(new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "skills"), result, seen, 0);
            scanSkillDirectory(new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".agents"), "skills"), result, seen, 0);
            emit("onSkills", result.toString());
        }, "CodexSkillScanner").start();
    }

    private static void scanSkillDirectory(File directory, JSONArray output, java.util.Set<String> seen, int depth) {
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

    private static JSONObject readSkillSummary(File skillFile) throws Exception {
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

    private static String unquoteYaml(String value, String fallback) {
        String result = value == null ? "" : value.trim();
        if (result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\"")) ||
                (result.startsWith("'") && result.endsWith("'")))) result = result.substring(1, result.length() - 1);
        return result.isEmpty() ? fallback : result;
    }

    void setThreadGoal(String objective) {
        if (threadId == null || objective == null || objective.trim().isEmpty()) return;
        try {
            sendRequest("thread/goal/set", new JSONObject()
                .put("threadId", threadId)
                .put("objective", objective.trim())
                .put("status", "active"));
        } catch (Exception e) { emit("onNativeError", e.getMessage()); }
    }

    void clearThreadGoal() {
        if (threadId == null) return;
        try {
            sendRequest("thread/goal/clear", new JSONObject().put("threadId", threadId));
        } catch (Exception e) { emit("onNativeError", e.getMessage()); }
    }

    @JavascriptInterface public void interruptCurrentTurn() {
        String thread = threadId;
        String turn = activeTurnId;
        if (thread == null || turn == null) return;
        try {
            sendRequest("turn/interrupt", new JSONObject().put("threadId", thread).put("turnId", turn));
        } catch (Exception e) {
            emit("onNativeError", e.getMessage());
        }
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
        JSONObject request = new JSONObject().put("method", method).put("id", id).put("params", params);
        rememberPrimaryRequest(request);
        sendJson(request);
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
            else sendThreadStart();
            return;
        }
        if (desktopBridge != null) desktopBridge.onAppServerMessage(message);
        if (message.has("error")) {
            if (message.optInt("id", -1) == editRollbackRequestId) {
                editRollbackRequestId = -1;
                pendingEditedText = pendingEditedModel = pendingEditedEffort = null;
            }
            emit("onNativeError", message.getJSONObject("error").optString("message", message.toString()));
            return;
        }
        if (message.has("id") && message.optInt("id", -1) == editRollbackRequestId) {
            editRollbackRequestId = -1;
            String text = pendingEditedText;
            String model = pendingEditedModel;
            String effort = pendingEditedEffort;
            pendingEditedText = pendingEditedModel = pendingEditedEffort = null;
            if (message.has("error")) emit("onNativeError", message.optJSONObject("error").optString("message", "Unable to edit message"));
            else sendMessage(text, model, effort, "[]");
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
                if (pendingTitle != null && desktopBridge != null) CodexTaskStore.markRunning(activity, threadId, pendingTitle);
                emit("onReady", threadId);
            }
            return;
        }
        String method = message.optString("method", "");
        JSONObject params = message.optJSONObject("params");
        logCollabAgentEvent(method, params);
        if ("turn/plan/updated".equals(method) && params != null) {
            emit("onPlanUpdated", params.toString());
        }
        if (("item/started".equals(method) || "item/completed".equals(method)) && params != null) {
            JSONObject liveItem = params.optJSONObject("item");
            String liveType = liveItem == null ? "" : liveItem.optString("type", "");
            if ("collabAgentToolCall".equals(liveType) || "subAgentActivity".equals(liveType)) {
                emit("onSubagentEvent", liveItem.toString());
            }
        }
        if ("item/agentMessage/delta".equals(method) && params != null) {
            String itemId = params.optString("itemId", "");
            if (!itemId.isEmpty()) streamedAgentItemIds.add(itemId);
            String delta = params.optString("delta", "");
            long now = android.os.SystemClock.uptimeMillis();
            long gap = lastAgentDeltaAt == 0L ? 0L : now - lastAgentDeltaAt;
            lastAgentDeltaAt = now;
            agentDeltaCount++;
            agentDeltaChars += delta.length();
            Log.d(TAG, "agent-delta item=" + itemId + " chars=" + delta.length() + " gapMs=" + gap
                + " totalChunks=" + agentDeltaCount + " totalChars=" + agentDeltaChars);
            emit("onDelta", delta);
        } else if ("item/completed".equals(method) && isReasoningItem(params)) {
            JSONObject item = params.optJSONObject("item");
            String text = extractReasoningText(item);
            if (!text.isEmpty()) emit("onReasoningComplete", text);
        } else if ("item/completed".equals(method) && isCommandItem(params)) {
            JSONObject item = params.optJSONObject("item");
            emit("onCommandComplete", item == null ? "{}" : item.toString());
        } else if ("item/completed".equals(method) && isToolDetailItem(params)) {
            JSONObject item = params.optJSONObject("item");
            emit("onToolComplete", item == null ? "{}" : item.toString());
        } else if ("item/completed".equals(method) && isFinalAgentMessage(params)) {
            JSONObject item = params == null ? null : params.optJSONObject("item");
            Log.i(TAG, "agent-stream-complete chunks=" + agentDeltaCount + " chars=" + agentDeltaChars);
            lastAgentDeltaAt = 0L;
            agentDeltaCount = 0;
            agentDeltaChars = 0;
            String itemId = item == null ? "" : item.optString("id", "");
            if (itemId.isEmpty() || !streamedAgentItemIds.remove(itemId)) {
                String finalText = extractAgentMessageText(item);
                if (!finalText.isEmpty()) emit("onFinalAnswer", finalText);
            }
            scheduleMissingTurnCompletion(params);
        } else if (("item/reasoning/summaryTextDelta".equals(method) || "item/reasoning/textDelta".equals(method)) && params != null) {
            emit("onReasoningDelta", params.optString("delta", ""));
        } else if ("item/commandExecution/outputDelta".equals(method) && params != null) {
            emit("onCommandDelta", params.optString("delta", ""));
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

    private static boolean isToolDetailItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        if (item == null) return false;
        String type = item.optString("type", "");
        return "fileChange".equals(type) || "mcpToolCall".equals(type) || "webSearch".equals(type)
            || "collabAgentToolCall".equals(type);
    }

    private static boolean isReasoningItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        return item != null && "reasoning".equals(item.optString("type"));
    }

    private static boolean isCommandItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        return item != null && "commandExecution".equals(item.optString("type"));
    }

    private static String extractReasoningText(JSONObject item) {
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

    private static String extractAgentMessageText(JSONObject item) {
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
        if (thread.equals(threadId)) activeTurnId = turnId;
        turnStartedAtMs.put(turnKey(thread, turnId),
            startedAtSeconds > 0 ? startedAtSeconds * 1000L : System.currentTimeMillis());
        scheduleTurnRuntimeDiagnostics(thread, turnId);
        scheduleTurnStallDiagnostics(thread, turnId);
    }

    private void scheduleTurnStallDiagnostics(String thread, String turn) {
        final String key = turnKey(thread, turn);
        for (long thresholdMs : new long[]{60_000L, 180_000L}) {
            activity.getWindow().getDecorView().postDelayed(() -> {
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

    static String resolveConversationProject(String threadId) {
        try {
            File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
            File sessionFile = findSessionFile(sessionsRoot, threadId);
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
            Log.w(TAG, "Unable to resolve conversation project " + shortId(threadId), error);
            return "";
        }
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
            Log.w(TAG, "Unable to resolve conversation title " + shortId(threadId), error);
        }
        return "";
    }

    static JSONArray readConversationHistory(File sessionFile) throws Exception {
        JSONArray messages = new JSONArray();
        StringBuilder reasoning = new StringBuilder();
        StringBuilder command = new StringBuilder();
        JSONArray tools = new JSONArray();
        java.util.Map<String, JSONObject> calls = new java.util.HashMap<>();
        int lastProcessIndex = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sessionFile), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject record;
                try { record = new JSONObject(line); } catch (Exception ignored) { continue; }
                JSONObject payload = record.optJSONObject("payload");
                if (payload == null) continue;
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
                    long durationMs = payload.optLong("duration_ms", 0L);
                    if (lastProcessIndex >= 0 && durationMs > 0) {
                        JSONObject processMessage = messages.optJSONObject(lastProcessIndex);
                        String contentValue = processMessage == null ? "" : processMessage.optString("content", "");
                        if (contentValue.startsWith("PROCESS2|")) try {
                            String json = new String(android.util.Base64.decode(contentValue.substring(9), android.util.Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8);
                            JSONObject process = new JSONObject(json).put("duration", Math.max(1L, durationMs / 1000L));
                            String encoded = android.util.Base64.encodeToString(process.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
                            processMessage.put("content", "PROCESS2|" + encoded);
                            messages.put(lastProcessIndex, processMessage);
                        } catch (Exception ignored) {}
                    }
                    continue;
                }
                if (!"response_item".equals(record.optString("type"))) continue;
                String payloadType = payload.optString("type", "");
                if ("reasoning".equals(payloadType)) {
                    JSONArray summary = payload.optJSONArray("summary");
                    if (summary != null) for (int i = 0; i < summary.length(); i++) {
                        JSONObject part = summary.optJSONObject(i);
                        String value = part == null ? summary.optString(i, "") : part.optString("text", "");
                        if (!value.isEmpty()) reasoning.append(value).append('\n');
                    }
                    continue;
                }
                if ("function_call".equals(payloadType)) {
                    String callId = payload.optString("call_id", payload.optString("id", ""));
                    String name = payload.optString("name", "tool");
                    String arguments = payload.optString("arguments", "");
                    JSONObject args;
                    try { args = new JSONObject(arguments); }
                    catch (Exception ignored) { args = new JSONObject().put("raw", arguments); }
                    calls.put(callId, new JSONObject().put("name", name).put("arguments", args));
                    continue;
                }
                if ("function_call_output".equals(payloadType)) {
                    String callId = payload.optString("call_id", "");
                    JSONObject call = calls.remove(callId);
                    String output = payload.optString("output", "");
                    if (call != null) tools.put(historyToolCard(call, output));
                    else if (!output.isEmpty()) command.append(output.trim()).append('\n');
                    continue;
                }
                if (!"message".equals(payloadType)) continue;
                String role = payload.optString("role", "");
                if (!"user".equals(role) && !"assistant".equals(role)) continue;
                JSONArray content = payload.optJSONArray("content");
                if (content == null) continue;
                StringBuilder text = new StringBuilder();
                JSONArray referencedSkills = new JSONArray();
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
                    text.append(part.optString("text", ""));
                }
                String value = text.toString().trim();
                if ("user".equals(role) && isInjectedContextMessage(value)) continue;
                if ("assistant".equals(role) && (reasoning.length() > 0 || command.length() > 0 || tools.length() > 0)) {
                    JSONObject process = new JSONObject().put("duration", 0).put("reasoning", reasoning.toString().trim())
                        .put("command", command.toString().trim()).put("tools", tools);
                    String encoded = android.util.Base64.encodeToString(process.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
                    messages.put(new JSONObject().put("role", "activity").put("content", "PROCESS2|" + encoded));
                    lastProcessIndex = messages.length() - 1;
                    reasoning.setLength(0); command.setLength(0); tools = new JSONArray();
                }
                if (!value.isEmpty()) {
                    JSONObject historyMessage = new JSONObject().put("role", role).put("content", value);
                    if (referencedSkills.length() > 0) historyMessage.put("skills", referencedSkills);
                    messages.put(historyMessage);
                }
            }
        }
        return messages;
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
            .put("status", "started".equals(kind) ? "completed" : kind);
    }

    private static JSONObject historyToolCard(JSONObject call, String output) throws Exception {
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
                .put("aggregatedOutput", output).put("status", "completed");
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
            JSONObject result = new JSONObject().put("type", "collabAgentToolCall").put("name", name)
                .put("tool", name).put("detail", output.isEmpty() ? args.toString(2) : output).put("status", "completed");
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

    private static boolean isInjectedContextMessage(String value) {
        if (value == null) return true;
        String text = value.trim();
        return text.startsWith("<environment_context>")
            || text.startsWith("<permissions instructions>")
            || text.startsWith("<app-context>")
            || text.startsWith("<collaboration_mode>")
            || text.startsWith("<skills_instructions>")
            || text.startsWith("<plugins_instructions>")
            || (text.startsWith("<cwd>") && text.contains("<filesystem>"));
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

    static boolean isFinalAgentMessage(JSONObject params) {
        if (params == null) return false;
        JSONObject item = params.optJSONObject("item");
        return item != null && "agentMessage".equals(item.optString("type"))
            && "final_answer".equals(item.optString("phase"));
    }

    static boolean shouldSynthesizeMissingTurnCompletion(boolean pending, boolean hasActiveRequests,
                                                          int consecutiveIdleChecks) {
        return pending && !hasActiveRequests
            && consecutiveIdleChecks >= MISSING_TURN_COMPLETION_IDLE_CHECKS;
    }

    private void scheduleMissingTurnCompletion(JSONObject params) {
        String key = turnKey(params);
        if (key == null || !pendingFinalTurns.add(key)) return;
        String thread = params.optString("threadId");
        String turnId = params.optString("turnId");
        new Thread(() -> {
            int consecutiveIdleChecks = 0;
            long waitingSince = System.currentTimeMillis();
            long nextWarningAt = waitingSince + MISSING_TURN_COMPLETION_WARNING_MS;
            try {
                while (pendingFinalTurns.contains(key)) {
                    Thread.sleep(MISSING_TURN_COMPLETION_CHECK_MS);
                    if (!pendingFinalTurns.contains(key)) return;

                    LocalApiProxy proxy = apiProxy;
                    boolean hasActiveRequests = proxy != null && proxy.hasActiveRequests();
                    consecutiveIdleChecks = hasActiveRequests ? 0 : consecutiveIdleChecks + 1;
                    long checkTime = System.currentTimeMillis();
                    if (hasActiveRequests && checkTime >= nextWarningAt) {
                        android.util.Log.w(TAG, "Still waiting for real turn/completed while upstream requests are active for "
                            + turnId + " waitedMs=" + (checkTime - waitingSince));
                        nextWarningAt = checkTime + MISSING_TURN_COMPLETION_WARNING_MS;
                    }
                    if (!shouldSynthesizeMissingTurnCompletion(true, hasActiveRequests, consecutiveIdleChecks)) {
                        continue;
                    }
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
                    android.util.Log.w(TAG, "Synthesizing missing turn/completed after upstream became idle for " + turnId);
                    if (desktopBridge != null) desktopBridge.onAppServerMessage(completed);
                    emit("onTurnComplete", "");
                    JSONObject syntheticParams = completed.optJSONObject("params");
                    if (isPrimaryTurn(syntheticParams)) {
                        CodexTaskStore.markCompleted(activity, thread, false);
                        notifyTaskCompleted();
                    }
                    sendRequest("turn/interrupt", new JSONObject().put("threadId", thread).put("turnId", turnId));
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
        } else if ("thread/start".equals(method) && desktopBridge != null) {
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
        return prompt.isEmpty() ? "Codex 任务" : prompt;
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
        } else {
            activity.runOnUiThread(() -> CodexOverlayService.notifyTaskCompleted(activity));
        }
    }

    private void emit(String function, String value) {
        android.util.Log.d(TAG, "EMIT function=" + function + " length=" + (value == null ? 0 : value.length()));
        final String safeValue = value == null ? "" : value;
        final String quoted = JSONObject.quote(safeValue);
        activity.runOnUiThread(() -> {
            if (eventListener != null) eventListener.onEvent(function, safeValue);
            if (webView != null) webView.evaluateJavascript(
                "window.codex && window.codex." + function + "(" + quoted + ")", null);
        });
    }

    synchronized void stop() {
        threadId = null;
        activeTurnId = null;
        initializeRequestId = -1;
        turnStartedAtMs.clear();
        pendingFinalTurns.clear();
        syntheticCompletedTurns.clear();
        streamedAgentItemIds.clear();
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        writer = null;
        if (process != null) process.destroy();
        process = null;
        if (apiProxy != null) apiProxy.stop();
        apiProxy = null;
    }
}

