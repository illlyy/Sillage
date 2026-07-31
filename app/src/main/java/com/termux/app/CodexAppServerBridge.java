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

/** Direct JSONL bridge to `codex app-server --stdio`; no Node.js or external Termux required. */
final class CodexAppServerBridge {
    static final String IMPLEMENT_PLAN_PROMPT_PREFIX = "PLEASE IMPLEMENT THIS PLAN:";
    static final String IMPLEMENT_PLAN_DISPLAY_PREFIX = "IMPLEMENT_PLAN|";
    interface EventListener {
        void onEvent(String function, String value);
        void onHistoryPrepared(String threadId, int generation, NativeHistorySnapshot snapshot);
    }

    private static final String TAG = "IlyopCodexBridge";
    private static final java.util.regex.Pattern RECORD_TIMESTAMP_PATTERN = java.util.regex.Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d+))?(Z|[+-]\\d{2}:?\\d{2})$");
    private final Context appContext;
    private volatile WeakReference<Activity> activityRef;
    private final WebView webView;
    private volatile EventListener eventListener;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<String, Long> turnStartedAtMs = new ConcurrentHashMap<>();
    /** Main conversation turns whose normalized start lifecycle was already emitted. */
    private final Set<String> emittedTurnStartedTurns = ConcurrentHashMap.newKeySet();
    private final Set<String> streamedAgentItemIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, String> pendingGoalGetRequests = new ConcurrentHashMap<>();
    /** Route captured when a native follow-up is sent; a late response must not bind to a new chat. */
    private final Map<Integer, NativeRouteEventGate.RouteToken> pendingSteerRequestRoutes =
        new ConcurrentHashMap<>();
    /** Server-originated approval/input requests keep their original identity until resolved. */
    private final Map<String, PendingServerRequestRoute> pendingServerRequestRoutes =
        new ConcurrentHashMap<>();
    /** Process-lifetime tombstones detect sequential JSON-RPC id reuse after a response. */
    private final Set<String> seenServerRequestRouteKeys = ConcurrentHashMap.newKeySet();
    /** A concurrently reused JSON-RPC id cannot safely identify either pending request. */
    private final Set<String> ambiguousServerRequestRouteKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> autoClearingCompletedGoalThreads = ConcurrentHashMap.newKeySet();
    private long lastAgentDeltaAt;
    private int agentDeltaCount;
    private int agentDeltaChars;
    /** Threads explicitly opened by the desktop UI; subagent threads never enter this set. */
    private final Set<String> primaryThreadIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, String> pendingPrimaryThreadTitles = new ConcurrentHashMap<>();
    private final AtomicInteger navigationGeneration = new AtomicInteger();
    /** Invalidates an asynchronous process bootstrap when start/stop is called again. */
    private final AtomicInteger serverGeneration = new AtomicInteger();
    private final Map<Integer, Integer> pendingNavigationGenerations = new ConcurrentHashMap<>();
    private final Map<Integer, String> pendingNavigationMethods = new ConcurrentHashMap<>();
    /** Outbound turn/start requests are strong evidence that a new primary turn may replace one. */
    private final Map<String, String> pendingPrimaryTurnStartRequestThreads = new ConcurrentHashMap<>();
    /** All outbound compact requests, including Desktop/WebUI calls, keep their request identity. */
    private final Map<String, String> pendingCompactRequestThreads = new ConcurrentHashMap<>();
    private Process process;
    private BufferedWriter writer;
    private volatile String threadId;
    /** Thread whose events may mutate the currently visible native Compose conversation. */
    private volatile String visibleThreadId;
    private volatile boolean visibleRouteReady;
    private volatile String activeTurnId;
    private volatile boolean activeTurnConfirmedPrimary;
    private volatile int initializeRequestId = -1;
    private volatile boolean appServerInitialized;
    private String deferredResumeThreadId;
    private int deferredResumeGeneration = -1;
    private volatile int editRollbackRequestId = -1;
    private volatile int compactRequestId = -1;
    private volatile String compactRequestThreadId;
    private volatile String compactNativeRequestId;
    private final AtomicLong nativeProtocolSequence = new AtomicLong();
    private volatile int collaborationModesRequestId = -1;
    private volatile int mcpStatusRequestId = -1;
    private volatile JSONObject planCollaborationMode;
    private volatile String pendingEditedText;
    private volatile String pendingEditedModel;
    private volatile String pendingEditedEffort;
    private LocalApiProxy apiProxy;
    private CodexDesktopBridge desktopBridge;
    private static final long NATIVE_STREAM_BATCH_MS = 32L;
    static final long COMPACTION_TURN_BIND_WINDOW_MS = 30_000L;
    static final long TASK_COMPLETION_SETTLE_MS = 500L;
    static final long PRIMARY_COMPLETION_TOMBSTONE_MS = 60_000L;
    static final long COMPACTION_TERMINAL_SETTLE_MS = 2_000L;
    static final long HISTORY_LOAD_TIMEOUT_MS = 5_000L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NativeStreamEventBatcher nativeStreamBatcher = new NativeStreamEventBatcher();
    /** Keeps live events behind the history snapshot without losing route or protocol identity. */
    private final NativeRouteEventGate nativeRouteEventGate = new NativeRouteEventGate();
    /** Keeps explicit thread/compact/start turns separate from the active conversation turn. */
    private final CompactionTurnTracker compactionTurnTracker = new CompactionTurnTracker();
    /** Classifies a weak post-completion start without depending on task-finalization state. */
    private final PrimaryCompletionTracker primaryCompletionTracker =
        new PrimaryCompletionTracker();
    /** Native UI continuation state (queued follow-up, goal retry, or working subagent), per thread. */
    private final Set<String> nativeContinuationPendingThreads = ConcurrentHashMap.newKeySet();
    /** A task terminal state delayed until its native continuation has genuinely drained. */
    private final Map<String, DeferredTaskCompletion> deferredTaskCompletions =
        new ConcurrentHashMap<>();
    /** Unconfirmed post-completion starts wait for their first non-compaction event. */
    private final Map<String, ProvisionalTurnStart> provisionalTurnStarts =
        new ConcurrentHashMap<>();
    /** Serializes route admission, history handoff, reset and delivery-queue insertion. */
    private final Object nativeRouteDeliveryLock = new Object();
    /** Route identity captured on the app-server reader thread before any later navigation. */
    private final ThreadLocal<NativeRouteEventGate.RouteToken> nativeRouteEmission = new ThreadLocal<>();
    private final AtomicBoolean nativeStreamDrainScheduled = new AtomicBoolean();
    private final Runnable nativeStreamDrain = this::drainNativeStreamEvents;

    private static final class PendingServerRequestRoute {
        final NativeRouteEventGate.RouteToken routeToken;
        final String sourceThreadId;
        final String turnId;
        final String method;

        PendingServerRequestRoute(NativeRouteEventGate.RouteToken routeToken, JSONObject params,
                                  String method) {
            this.routeToken = routeToken;
            this.sourceThreadId = routeToken == null ? "" : routeToken.getSourceThreadId();
            this.turnId = protocolTurnId(params);
            this.method = method == null ? "" : method;
        }

        boolean sameIdentity(PendingServerRequestRoute other) {
            return other != null && routeToken != null && routeToken.belongsToSameRoute(other.routeToken)
                && sourceThreadId.equals(other.sourceThreadId)
                && turnId.equals(other.turnId)
                && method.equals(other.method);
        }

        boolean matchesResolution(JSONObject params) {
            return requestRouteIdentityMatches(sourceThreadId, turnId, params);
        }
    }

    private static final class DeferredTaskCompletion {
        final String threadId;
        final String turnId;
        final boolean failed;
        final long notBeforeMs;

        DeferredTaskCompletion(String threadId, String turnId, boolean failed, long notBeforeMs) {
            this.threadId = threadId;
            this.turnId = turnId;
            this.failed = failed;
            this.notBeforeMs = notBeforeMs;
        }
    }

    private static final class ProvisionalTurnStart {
        final String threadId;
        final String turnId;
        final JSONObject turn;

        ProvisionalTurnStart(String threadId, String turnId, JSONObject turn) {
            this.threadId = threadId;
            this.turnId = turnId;
            this.turn = turn == null ? new JSONObject() : turn;
        }
    }

    /**
     * Pure request-to-turn correlation for explicit context compaction.
     *
     * App-server versions disagree on where the dedicated turn id first appears: the compact RPC
     * response, turn/started, or the contextCompaction item lifecycle. Keep a short-lived pending
     * request per thread and bind the first different turn. The primary turn id is retained only
     * so a late item lifecycle can repair an older accidental auxiliary overwrite.
     */
    static final class CompactionTurnTracker {
        private static final class Pending {
            final String primaryTurnId;
            final long expiresAtMs;
            boolean rpcAcknowledged;

            Pending(String primaryTurnId, long expiresAtMs) {
                this.primaryTurnId = primaryTurnId == null ? "" : primaryTurnId;
                this.expiresAtMs = expiresAtMs;
            }
        }

        private final Map<String, Pending> pendingByThread = new ConcurrentHashMap<>();
        private final Map<String, String> primaryTurnByAuxiliaryTurn = new ConcurrentHashMap<>();
        private final Set<String> completedAuxiliaryTurns = ConcurrentHashMap.newKeySet();
        private final Map<String, Long> terminalAuxiliaryTurnDeadlines = new ConcurrentHashMap<>();

        synchronized void requestStarted(String sourceThread, String primaryTurnId, long nowMs) {
            if (sourceThread == null || sourceThread.isEmpty()) return;
            pendingByThread.put(sourceThread, new Pending(
                primaryTurnId, nowMs + COMPACTION_TURN_BIND_WINDOW_MS));
        }

        synchronized void requestFailed(String sourceThread) {
            if (sourceThread != null && !sourceThread.isEmpty()) pendingByThread.remove(sourceThread);
        }

        synchronized void requestSucceeded(String sourceThread, long nowMs) {
            Pending pending = activePending(sourceThread, nowMs);
            if (pending != null) pending.rpcAcknowledged = true;
        }

        synchronized boolean observeCandidate(String sourceThread, String turnId, long nowMs,
                                                boolean strongEvidence, String primaryTurnHint) {
            if (sourceThread == null || sourceThread.isEmpty() || turnId == null || turnId.isEmpty()) {
                return false;
            }
            String key = turnKey(sourceThread, turnId);
            if (primaryTurnByAuxiliaryTurn.containsKey(key)) return true;
            Pending pending = activePending(sourceThread, nowMs);
            if (pending == null) {
                // A server-controlled automatic compaction has no client compact RPC. Only the
                // contextCompaction lifecycle itself is strong enough to create an auxiliary turn.
                if (!strongEvidence || (primaryTurnHint != null && !primaryTurnHint.isEmpty()
                        && primaryTurnHint.equals(turnId))) return false;
                primaryTurnByAuxiliaryTurn.put(key,
                    primaryTurnHint == null ? "" : primaryTurnHint);
                return true;
            }
            // An explicit compact request can first complete the active primary turn. Never bind
            // that completion as the auxiliary compaction turn; wait for the different turn id.
            if (!pending.primaryTurnId.isEmpty() && pending.primaryTurnId.equals(turnId)) return false;
            // Before an RPC acknowledgement, an ordinary boundary with no prior primary identity
            // could just be the user's next real turn. Wait for response/item evidence instead.
            if (!strongEvidence && pending.primaryTurnId.isEmpty() && !pending.rpcAcknowledged) {
                return false;
            }
            primaryTurnByAuxiliaryTurn.put(key, pending.primaryTurnId);
            pendingByThread.remove(sourceThread);
            return true;
        }

        synchronized boolean observeCandidate(String sourceThread, String turnId, long nowMs) {
            return observeCandidate(sourceThread, turnId, nowMs, false, "");
        }

        private Pending activePending(String sourceThread, long nowMs) {
            if (sourceThread == null || sourceThread.isEmpty()) return null;
            Pending pending = pendingByThread.get(sourceThread);
            if (pending != null && nowMs > pending.expiresAtMs) {
                pendingByThread.remove(sourceThread);
                return null;
            }
            return pending;
        }

        synchronized boolean isAuxiliaryTurn(String sourceThread, String turnId) {
            return sourceThread != null && !sourceThread.isEmpty()
                && turnId != null && !turnId.isEmpty()
                && primaryTurnByAuxiliaryTurn.containsKey(turnKey(sourceThread, turnId));
        }

        synchronized String primaryTurnFor(String sourceThread, String turnId) {
            if (sourceThread == null || turnId == null) return "";
            return primaryTurnByAuxiliaryTurn.getOrDefault(turnKey(sourceThread, turnId), "");
        }

        synchronized boolean complete(String sourceThread, String turnId) {
            if (sourceThread == null || turnId == null) return false;
            String key = turnKey(sourceThread, turnId);
            if (!primaryTurnByAuxiliaryTurn.containsKey(key)) return false;
            terminalAuxiliaryTurnDeadlines.remove(key);
            completedAuxiliaryTurns.add(key);
            return true;
        }

        synchronized boolean observeLifecycleTerminal(
                String sourceThread, String turnId, long nowMs) {
            if (sourceThread == null || sourceThread.isEmpty()
                    || turnId == null || turnId.isEmpty()) return false;
            String key = turnKey(sourceThread, turnId);
            if (!primaryTurnByAuxiliaryTurn.containsKey(key)
                    || completedAuxiliaryTurns.contains(key)) return false;
            return terminalAuxiliaryTurnDeadlines.putIfAbsent(
                key, nowMs + COMPACTION_TERMINAL_SETTLE_MS) == null;
        }

        synchronized boolean hasPendingOrActiveCompaction(String sourceThread, long nowMs) {
            if (activePending(sourceThread, nowMs) != null) return true;
            String prefix = (sourceThread == null ? "" : sourceThread) + ":";
            if (prefix.length() == 1) return false;
            for (String key : primaryTurnByAuxiliaryTurn.keySet()) {
                if (!key.startsWith(prefix) || completedAuxiliaryTurns.contains(key)) continue;
                Long terminalDeadline = terminalAuxiliaryTurnDeadlines.get(key);
                if (terminalDeadline != null && nowMs >= terminalDeadline) {
                    terminalAuxiliaryTurnDeadlines.remove(key, terminalDeadline);
                    completedAuxiliaryTurns.add(key);
                    continue;
                }
                return true;
            }
            return false;
        }

        synchronized boolean shouldQuarantineWeakStart(
                String sourceThread, String turnId, long nowMs) {
            if (turnId == null || turnId.isEmpty()) return false;
            Pending pending = activePending(sourceThread, nowMs);
            return pending != null && pending.primaryTurnId.isEmpty()
                && !pending.rpcAcknowledged;
        }

        synchronized String pendingThreadIfUnambiguous(long nowMs) {
            String candidate = "";
            for (Map.Entry<String, Pending> entry : pendingByThread.entrySet()) {
                if (nowMs > entry.getValue().expiresAtMs) {
                    pendingByThread.remove(entry.getKey());
                    continue;
                }
                if (!candidate.isEmpty()) return "";
                candidate = entry.getKey();
            }
            return candidate;
        }

        synchronized void reset() {
            pendingByThread.clear();
            primaryTurnByAuxiliaryTurn.clear();
            completedAuxiliaryTurns.clear();
            terminalAuxiliaryTurnDeadlines.clear();
        }
    }

    /**
     * A short classification tombstone for the most recently completed primary turn per thread.
     * It deliberately outlives the 500ms task-completion settle state: automatic compaction can
     * publish its dedicated turn/started later, and that weak boundary must wait for item evidence
     * instead of becoming a new primary turn.
     */
    static final class PrimaryCompletionTracker {
        private static final class Tombstone {
            final String turnId;
            final long expiresAtMs;

            Tombstone(String turnId, long expiresAtMs) {
                this.turnId = turnId;
                this.expiresAtMs = expiresAtMs;
            }
        }

        private final Map<String, Tombstone> byThread = new ConcurrentHashMap<>();

        synchronized void record(String sourceThread, String turnId, long nowMs) {
            if (sourceThread == null || sourceThread.isEmpty()
                    || turnId == null || turnId.isEmpty()) return;
            byThread.put(sourceThread,
                new Tombstone(turnId, nowMs + PRIMARY_COMPLETION_TOMBSTONE_MS));
        }

        synchronized boolean shouldQuarantineWeakStart(
                String sourceThread, String candidateTurnId, long nowMs) {
            Tombstone tombstone = active(sourceThread, nowMs);
            return tombstone != null && candidateTurnId != null && !candidateTurnId.isEmpty()
                && !candidateTurnId.equals(tombstone.turnId);
        }

        synchronized String primaryTurnHint(
                String sourceThread, String candidateTurnId, long nowMs) {
            Tombstone tombstone = active(sourceThread, nowMs);
            if (tombstone == null || candidateTurnId == null
                    || candidateTurnId.equals(tombstone.turnId)) return "";
            return tombstone.turnId;
        }

        synchronized void clear(String sourceThread) {
            if (sourceThread != null && !sourceThread.isEmpty()) byThread.remove(sourceThread);
        }

        private Tombstone active(String sourceThread, long nowMs) {
            if (sourceThread == null || sourceThread.isEmpty()) return null;
            Tombstone tombstone = byThread.get(sourceThread);
            if (tombstone != null && nowMs > tombstone.expiresAtMs) {
                byThread.remove(sourceThread, tombstone);
                return null;
            }
            return tombstone;
        }

        synchronized void reset() {
            byThread.clear();
        }
    }

    CodexAppServerBridge(Activity activity, WebView webView) {
        this.appContext = activity.getApplicationContext();
        this.activityRef = new WeakReference<>(activity);
        this.webView = webView;
        this.eventListener = null;
    }

    CodexAppServerBridge(Activity activity, EventListener eventListener) {
        this.appContext = activity.getApplicationContext();
        this.activityRef = new WeakReference<>(activity);
        this.webView = null;
        this.eventListener = eventListener;
    }

    void setDesktopBridge(CodexDesktopBridge bridge) { this.desktopBridge = bridge; }

    void rebind(Activity activity, EventListener listener) {
        if (activity != null) this.activityRef = new WeakReference<>(activity);
        this.eventListener = listener;
    }

    void detach(EventListener listener) {
        if (this.eventListener != listener) return;
        this.eventListener = null;
        Activity boundActivity = boundActivity();
        if (boundActivity == listener) this.activityRef.clear();
    }

    private Activity boundActivity() {
        WeakReference<Activity> reference = activityRef;
        return reference == null ? null : reference.get();
    }

    String currentVisibleThreadId() { return visibleThreadId; }
    boolean isRunning() { return process != null || writer != null; }

    synchronized void setNativeContinuationPending(String sourceThreadId, boolean pending) {
        String sourceThread = sourceThreadId == null ? "" : sourceThreadId.trim();
        if (sourceThread.isEmpty()) return;
        if (pending) {
            nativeContinuationPendingThreads.add(sourceThread);
            return;
        }
        nativeContinuationPendingThreads.remove(sourceThread);
        maybeFinalizeDeferredTaskCompletion(sourceThread);
    }

    void sendDesktopRequest(JSONObject request) {
        try {
            logModelSelection(request);
            rememberPrimaryRequest(request);
            applyAndroidProviderOverrides(request);
            sendJson(request);
        }
        catch (Exception e) {
            forgetPrimaryRequest(request);
            if (desktopBridge != null) desktopBridge.onAppServerError(request.opt("id"), e.getMessage());
        }
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
        android.content.SharedPreferences mobile = appContext.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE);
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
            ultraSubagentLimit, normalSubagentLimit, java.util.Collections.emptyMap(), false);
    }

    synchronized void start(String baseUrl, String apiKey, String model, String apiFormat,
                            boolean routeThroughMihomo, boolean forwardReasoningContext,
                            int ultraSubagentLimit, int normalSubagentLimit,
                            Map<String, String> ultraTransportEfforts, boolean multiAgentV2) {
        start(baseUrl, apiKey, model, apiFormat, routeThroughMihomo, forwardReasoningContext,
            ultraSubagentLimit, normalSubagentLimit, ultraTransportEfforts, multiAgentV2, false);
    }

    synchronized void start(String baseUrl, String apiKey, String model, String apiFormat,
                            boolean routeThroughMihomo, boolean forwardReasoningContext,
                            int ultraSubagentLimit, int normalSubagentLimit,
                            Map<String, String> ultraTransportEfforts, boolean multiAgentV2,
                            boolean preventRecursiveSubagents) {
        final Map<String, String> transportEfforts = ultraTransportEfforts == null
            ? java.util.Collections.emptyMap() : new java.util.LinkedHashMap<>(ultraTransportEfforts);
        final int normalizedUltraLimit = CodexProviderStore.Profile.normalizeSubagentLimit(
            ultraSubagentLimit, CodexProviderStore.Profile.DEFAULT_ULTRA_SUBAGENT_LIMIT);
        final int normalizedNormalLimit = CodexProviderStore.Profile.normalizeSubagentLimit(
            normalSubagentLimit, CodexProviderStore.Profile.DEFAULT_NORMAL_SUBAGENT_LIMIT);
        stop();
        nextId.set(1);
        final int generation = serverGeneration.incrementAndGet();
        new Thread(() -> {
            LocalApiProxy localProxy = null;
            Process activeProcess = null;
            try {
                if (!isServerGenerationActive(generation)) return;
                File binary = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex");
                if (!binary.canExecute()) throw new IllegalStateException("Codex CLI is not installed");
                File home = TermuxConstants.TERMUX_HOME_DIR;
                File codexHome = new File(home, ".codex");
                home.mkdirs();
                codexHome.mkdirs();
                purgeStaleAgentsMaxThreads(new File(codexHome, "config.toml"));
                MihomoManager mihomo = MihomoManager.get(appContext);
                if (routeThroughMihomo) mihomo.start();
                if (!isServerGenerationActive(generation)) return;
                localProxy = new LocalApiProxy(baseUrl, apiFormat, routeThroughMihomo, mihomo.mixedPort(),
                    forwardReasoningContext, transportEfforts, preventRecursiveSubagents);
                int proxyPort = localProxy.start();
                synchronized (this) {
                    if (!isServerGenerationActive(generation)) {
                        localProxy.stop();
                        return;
                    }
                    apiProxy = localProxy;
                }
                String localBaseUrl = "http://127.0.0.1:" + proxyPort;
                String wireApi = LocalApiProxy.CODEX_WIRE_API;
                java.util.ArrayList<String> command = new java.util.ArrayList<>();
                command.add(binary.getAbsolutePath());
                command.add("-c"); command.add("model_provider=\"ilyop_android\"");
                command.add("-c"); command.add("model_providers.ilyop_android.name=\"Ilyop API\"");
                command.add("-c"); command.add("model_providers.ilyop_android.base_url=\"" + localBaseUrl + "\"");
                command.add("-c"); command.add("model_providers.ilyop_android.env_key=\"OPENAI_API_KEY\"");
                command.add("-c"); command.add("model_providers.ilyop_android.wire_api=\"" + wireApi + "\"");
                boolean enableMultiAgentV2 = multiAgentV2;
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
                // Critical for MCP stdio servers: without LD_LIBRARY_PATH, child processes
                // (npx/node/python) cannot find their shared libraries and fail to start.
                env.put("LD_LIBRARY_PATH", TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
                env.put("PREFIX", TermuxConstants.TERMUX_PREFIX_DIR_PATH);
                if (!isServerGenerationActive(generation)) {
                    localProxy.stop();
                    return;
                }
                activeProcess = builder.start();
                BufferedWriter activeWriter = new BufferedWriter(new OutputStreamWriter(activeProcess.getOutputStream()));
                synchronized (this) {
                    if (!isServerGenerationActive(generation)) {
                        try { activeWriter.close(); } catch (Exception ignored) {}
                        activeProcess.destroy();
                        if (apiProxy == localProxy) apiProxy = null;
                        localProxy.stop();
                        return;
                    }
                    process = activeProcess;
                    writer = activeWriter;
                }
                final Process processForThreads = activeProcess;
                new Thread(() -> readStdout(processForThreads, generation), "CodexAppServerOut").start();
                new Thread(() -> readStderr(processForThreads, generation), "CodexAppServerErr").start();
                new Thread(() -> monitorProcess(processForThreads, generation), "CodexAppServerWatch").start();
                sendInitialize(generation);
            } catch (Exception e) {
                boolean current = isServerGenerationActive(generation);
                if (current) {
                    cleanupFailedBootstrap(generation, localProxy, activeProcess);
                    emit("onNativeError", e.getClass().getSimpleName() + ": " + e.getMessage());
                } else {
                    if (activeProcess != null) activeProcess.destroy();
                    if (localProxy != null) localProxy.stop();
                }
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
            String permissionMode = configuredPermissionMode();
            String permissionCwd = configuredCwd();
            NativePermissionMode.applyTurnParams(params, permissionMode, permissionCwd);
            Log.i(TAG, "TURN_PERMISSIONS mode=" + permissionMode + " approval="
                + NativePermissionMode.approvalPolicy(permissionMode) + " sandbox="
                + NativePermissionMode.sandbox(permissionMode));
            if (model != null && !model.trim().isEmpty()) params.put("model", model);
            if (effort != null && !effort.trim().isEmpty()) params.put("effort", effort);
            if (collaborationMode != null && !collaborationMode.trim().isEmpty()) {
                // collaborationMode/list returns the exact wire schema. Its model and
                // reasoning_effort are top-level fields (not a nested settings object).
                // Sending the desktop wrapper shape here is accepted but silently ignored.
                JSONObject requestedMode = "plan".equals(collaborationMode) && planCollaborationMode != null
                    ? new JSONObject(planCollaborationMode.toString())
                    : new JSONObject().put("mode", collaborationMode);
                if (model != null && !model.trim().isEmpty()) requestedMode.put("model", model);
                if (effort != null && !effort.trim().isEmpty()) requestedMode.put("reasoning_effort", effort);
                // The current CLI requires a settings object as well as the mode
                // descriptor. Keep both forms for compatibility with older builds.
                JSONObject settings = new JSONObject();
                if (model != null && !model.trim().isEmpty()) settings.put("model", model);
                if (effort != null && !effort.trim().isEmpty()) settings.put("reasoning_effort", effort);
                settings.put("developer_instructions", JSONObject.NULL);
                requestedMode.put("settings", settings);
                params.put("collaborationMode", requestedMode);
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

    private static JSONObject navigationDetails(int generation, String thread) {
        JSONObject details = new JSONObject();
        try { details.put("generation", generation).put("thread", thread); }
        catch (Exception ignored) {}
        return details;
    }

    @JavascriptInterface public void newConversation() {
        newConversationAtCwd(null);
    }

    void newConversationAtCwd(String requestedCwd) {
        int generation = navigationGeneration.incrementAndGet();
        clearDeferredResume();
        resetVisibleRoute(null, false);
        NativeChatDiagnostics.record(appContext, "conversation_route", navigationDetails(generation, "new"));
        try { sendThreadStart(requestedCwd); } catch (Exception e) { emit("onNativeError", e.getMessage()); }
    }

    int resumeConversation(String resumeThreadId) {
        return resumeConversation(resumeThreadId, false);
    }

    int restoreRetainedConversation(String resumeThreadId) {
        return resumeConversation(resumeThreadId, true);
    }

    private int resumeConversation(String resumeThreadId, boolean allowMissingRollout) {
        if (resumeThreadId == null || resumeThreadId.trim().isEmpty()) return navigationGeneration.get();
        final String requestedThread = resumeThreadId.trim();
        final int generation = navigationGeneration.incrementAndGet();
        clearDeferredResume();
        // Change the visible route synchronously, before history I/O and thread/resume.
        // Events from the previously displayed background turn are rejected immediately.
        final NativeRouteEventGate.RouteToken routeToken = resetVisibleRoute(requestedThread, false);
        primaryThreadIds.add(requestedThread);
        NativeChatDiagnostics.record(appContext, "conversation_route", navigationDetails(generation, shortId(requestedThread)));
        final AtomicBoolean historyDeliveryResolved = new AtomicBoolean(false);
        final NativeHistorySnapshot emptySnapshot = NativeHistoryParser.parse(new JSONArray());
        final Runnable historyTimeout = scheduleHistoryLoadTimeout(
            requestedThread, generation, routeToken, historyDeliveryResolved, emptySnapshot,
            allowMissingRollout);
        new Thread(() -> {
            final long historyStartedAt = android.os.SystemClock.uptimeMillis();
            try {
                File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
                File sessionFile = findSessionFile(sessionsRoot, requestedThread);
                JSONArray history = sessionFile == null ? new JSONArray() : readConversationHistory(sessionFile);
                NativeHistorySnapshot historySnapshot = NativeHistoryParser.parse(history);
                NativeChatDiagnostics.record(appContext, "history_prepared", navigationDetails(generation, shortId(requestedThread))
                    .put("messages", historySnapshot.getMessages().size())
                    .put("estimatedChars", historySnapshot.getEstimatedChars())
                    .put("durationMs", android.os.SystemClock.uptimeMillis() - historyStartedAt));
                if (navigationGeneration.get() != generation || !requestedThread.equals(visibleThreadId)) return;
                final boolean retainInMemoryThread = allowMissingRollout && sessionFile == null;
                mainHandler.post(() -> {
                    if (!claimHistoryDelivery(requestedThread, generation, routeToken,
                            historyDeliveryResolved, historyTimeout)) return;
                    // JSON, Base64 and message DTO parsing completed on the resume worker.
                    // Main only swaps one immutable snapshot before opening the event gate.
                    try {
                        if (eventListener != null) eventListener.onHistoryPrepared(
                            requestedThread, generation, historySnapshot);
                    } catch (Exception historyApplyError) {
                        Log.e(TAG, "Unable to apply prepared conversation history", historyApplyError);
                        failOpenVisibleRoute(routeToken);
                        emitForRoute(routeToken, "onHistoryWarning",
                            "Unable to display conversation history: " + historyApplyError.getMessage());
                        continueConversationResume(
                            requestedThread, generation, routeToken, allowMissingRollout);
                        return;
                    }
                    // The history snapshot must be installed before live events are released.
                    // Otherwise a queued delta can be painted briefly and then overwritten by the
                    // disk snapshot on the next main-loop turn.
                    markVisibleRouteReadyAndReplay(routeToken, historySnapshot);
                    continueConversationResume(
                        requestedThread, generation, routeToken, retainInMemoryThread);
                });
            } catch (Exception e) {
                final String historyError = e.getClass().getSimpleName() + ": "
                    + (e.getMessage() == null ? "Unable to read conversation history" : e.getMessage());
                mainHandler.post(() -> {
                    if (!claimHistoryDelivery(requestedThread, generation, routeToken,
                            historyDeliveryResolved, historyTimeout)) return;
                    // Finish the Activity's loading state even when disk history is unavailable.
                    // Live output is more useful than a permanently blank route, so fail-open the
                    // gate before surfacing the explicit history error.
                    try {
                        if (eventListener != null) eventListener.onHistoryPrepared(
                            requestedThread, generation, emptySnapshot);
                    } catch (Exception callbackError) {
                        Log.w(TAG, "Unable to apply empty history fallback", callbackError);
                    }
                    failOpenVisibleRoute(routeToken);
                    emitForRoute(routeToken, "onHistoryWarning", historyError);
                    continueConversationResume(
                        requestedThread, generation, routeToken, allowMissingRollout);
                });
            }
        }, "CodexConversationResume").start();
        return generation;
    }

    private Runnable scheduleHistoryLoadTimeout(
            String requestedThread, int generation, NativeRouteEventGate.RouteToken routeToken,
            AtomicBoolean historyDeliveryResolved, NativeHistorySnapshot emptySnapshot,
            boolean retainedRuntimeFallback) {
        Runnable timeout = () -> {
            if (!isCurrentHistoryRoute(requestedThread, generation, routeToken)
                    || !historyDeliveryResolved.compareAndSet(false, true)) return;
            try {
                NativeChatDiagnostics.record(appContext, "history_timeout",
                    navigationDetails(generation, shortId(requestedThread))
                        .put("timeoutMs", HISTORY_LOAD_TIMEOUT_MS)
                        .put("deferredEvents", nativeRouteEventGate.deferredCount()));
            } catch (Exception ignored) {}
            try {
                if (eventListener != null) eventListener.onHistoryPrepared(
                    requestedThread, generation, emptySnapshot);
            } catch (Exception callbackError) {
                Log.w(TAG, "Unable to apply history timeout fallback", callbackError);
            }
            // A slow or wedged history reader must not hide an already-running model response.
            // Late history is ignored by historyDeliveryResolved so it cannot overwrite replayed
            // live output; the next visit can retry loading the persisted snapshot.
            failOpenVisibleRoute(routeToken);
            emitForRoute(routeToken, "onHistoryWarning",
                "Conversation history loading timed out; live output has been restored");
            continueConversationResume(
                requestedThread, generation, routeToken, retainedRuntimeFallback);
        };
        mainHandler.postDelayed(timeout, HISTORY_LOAD_TIMEOUT_MS);
        return timeout;
    }

    private boolean claimHistoryDelivery(
            String requestedThread, int generation, NativeRouteEventGate.RouteToken routeToken,
            AtomicBoolean historyDeliveryResolved, Runnable historyTimeout) {
        if (!isCurrentHistoryRoute(requestedThread, generation, routeToken)
                || !historyDeliveryResolved.compareAndSet(false, true)) return false;
        mainHandler.removeCallbacks(historyTimeout);
        return true;
    }

    private void continueConversationResume(
            String requestedThread, int generation, NativeRouteEventGate.RouteToken routeToken,
            boolean retainedInMemoryThread) {
        if (!isCurrentHistoryRoute(requestedThread, generation, routeToken)) return;
        if (retainedInMemoryThread) {
            NativeChatDiagnostics.record(appContext, "retained_runtime_restored",
                navigationDetails(generation, shortId(requestedThread)));
            emitForRoute(routeToken, "onReady", requestedThread);
            return;
        }
        try {
            if (deferResumeUntilInitialized(requestedThread, generation)) return;
            JSONObject resumeParams = new JSONObject().put("threadId", requestedThread);
            applyNativeMcpConfig(resumeParams);
            String permissionMode = configuredPermissionMode();
            NativePermissionMode.applyThreadParams(resumeParams, permissionMode, configuredCwd());
            Log.i(TAG, "RESUME_PERMISSIONS mode=" + permissionMode + " sandbox="
                + NativePermissionMode.sandbox(permissionMode));
            sendNavigationRequest("thread/resume", resumeParams, generation);
        } catch (Exception error) {
            emitNativeErrorForRoute(routeToken, error.getMessage());
        }
    }

    private boolean isCurrentHistoryRoute(
            String requestedThread, int generation, NativeRouteEventGate.RouteToken routeToken) {
        return navigationGeneration.get() == generation
            && requestedThread.equals(visibleThreadId)
            && nativeRouteEventGate.isCurrent(routeToken);
    }

    void loadSubagentHistory(String subagentThreadId, int generation) {
        if (subagentThreadId == null || subagentThreadId.trim().isEmpty()) return;
        new Thread(() -> {
            JSONObject result = new JSONObject();
            try {
                File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
                File sessionFile = findSessionFile(sessionsRoot, subagentThreadId);
                JSONArray messages = sessionFile == null ? new JSONArray() : readConversationHistory(sessionFile);
                result.put("threadId", subagentThreadId);
                result.put("generation", generation);
                result.put("messages", messages);
                result.put("found", sessionFile != null);
                result.put("status", subagentSessionStatus(sessionFile));
                NativeChatDiagnostics.record(appContext, "subagent_history", new JSONObject()
                    .put("thread", shortId(subagentThreadId))
                    .put("threadTail", subagentThreadId.length() > 8 ? subagentThreadId.substring(subagentThreadId.length() - 8) : subagentThreadId)
                    .put("found", sessionFile != null).put("messages", messages.length()));
            } catch (Exception error) {
                try {
                    result.put("threadId", subagentThreadId);
                    result.put("generation", generation);
                    result.put("messages", new JSONArray());
                    result.put("status", "failed");
                    result.put("error", error.getMessage());
                } catch (Exception ignored) {}
            }
            emit("onSubagentHistory", NativeLargePayloadStore.compactSubagentHistoryResult(result));
        }, "CodexSubagentHistory").start();
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
            autoClearingCompletedGoalThreads.remove(threadId);
            sendRequest("thread/goal/set", new JSONObject()
                .put("threadId", threadId)
                .put("objective", objective.trim())
                .put("status", "active"));
        } catch (Exception e) { emit("onNativeError", e.getMessage()); }
    }

    void getThreadGoal() {
        String currentThread = threadId;
        if (currentThread == null || currentThread.trim().isEmpty()) return;
        try {
            int requestId = sendRequest("thread/goal/get", new JSONObject().put("threadId", currentThread));
            pendingGoalGetRequests.put(requestId, currentThread);
        } catch (Exception error) {
            Log.w(TAG, "Unable to synchronize native thread goal", error);
        }
    }

    void compactThread() {
        compactThread(null);
    }

    void compactThread(String nativeRequestId) {
        final String requestedThread;
        final String primaryTurn;
        synchronized (nativeRouteDeliveryLock) {
            requestedThread = threadId;
            primaryTurn = activeTurnId;
        }
        if (requestedThread == null || requestedThread.trim().isEmpty()) return;
        compactionTurnTracker.requestStarted(
            requestedThread, primaryTurn, System.currentTimeMillis());
        compactRequestThreadId = requestedThread;
        try {
            compactNativeRequestId = nativeRequestId;
            if (webView != null) emit("onCompactStatus", "started");
            int requestId = nextId.getAndIncrement();
            compactRequestId = requestId;
            JSONObject request = new JSONObject()
                .put("method", "thread/compact/start")
                .put("id", requestId)
                .put("params", new JSONObject().put("threadId", requestedThread));
            rememberPrimaryRequest(request);
            sendJson(request);
        } catch (Exception e) {
            pendingCompactRequestThreads.remove(serverRequestRouteKey(compactRequestId));
            compactRequestId = -1;
            compactionTurnTracker.requestFailed(requestedThread);
            emitCompactionRpcResult(false, e.getMessage());
            compactRequestThreadId = null;
            if (webView != null) emit("onCompactStatus", "failed");
            emit("onNativeError", e.getMessage());
        }
    }

    private void emitCompactionRpcResult(boolean success, String error) {
        if (webView == null) {
            try {
                emit("onCompactionRpcResult", new JSONObject()
                    .put("threadId", compactRequestThreadId == null
                        ? (threadId == null ? "" : threadId) : compactRequestThreadId)
                    .put("requestId", compactNativeRequestId == null ? JSONObject.NULL : compactNativeRequestId)
                    .put("success", success)
                    .put("error", error == null ? "" : error)
                    .put("sequence", nativeProtocolSequence.incrementAndGet())
                    .put("timestampMs", System.currentTimeMillis())
                    .toString());
            } catch (Exception ignored) {}
        }
        compactNativeRequestId = null;
    }

    void setThreadGoalStatus(String status) {
        if (threadId == null) return;
        String normalized = "paused".equals(status) ? "paused" : "active";
        try {
            sendRequest("thread/goal/set", new JSONObject()
                .put("threadId", threadId)
                .put("status", normalized));
        } catch (Exception e) { emit("onNativeError", e.getMessage()); }
    }

    void clearThreadGoal() {
        if (threadId == null) return;
        try {
            sendRequest("thread/goal/clear", new JSONObject().put("threadId", threadId));
        } catch (Exception e) { emit("onNativeError", e.getMessage()); }
    }

    private void publishNativeGoalState(String goalThread, JSONObject goal) throws Exception {
        if (desktopBridge != null || goalThread == null || goalThread.isEmpty()) return;
        JSONObject payload = new JSONObject().put("threadId", goalThread);
        if (goal == null) {
            autoClearingCompletedGoalThreads.remove(goalThread);
            if (goalThread.equals(visibleThreadId)) emit("onGoalCleared", payload.toString());
            return;
        }
        payload.put("goal", goal);
        if (goalThread.equals(visibleThreadId)) emit("onGoalUpdated", payload.toString());
        if (!isCompletedGoal(goal)) {
            autoClearingCompletedGoalThreads.remove(goalThread);
        } else if (autoClearingCompletedGoalThreads.add(goalThread)) {
            try {
                sendRequest("thread/goal/clear", new JSONObject().put("threadId", goalThread));
            } catch (Exception error) {
                autoClearingCompletedGoalThreads.remove(goalThread);
                Log.w(TAG, "Unable to clear completed thread goal for " + shortId(goalThread), error);
            }
        }
    }

    static boolean isCompletedGoal(JSONObject goal) {
        return goal != null && "complete".equals(goal.optString("status", ""));
    }

    /**
     * Adds input to the active turn using the v2 steering precondition. Returns the request id, or
     * -1 when the server has not published an active turn yet so the caller can queue locally.
     */
    int steerMessage(String text, String attachmentsJson, String skillsJson) {
        if (text == null) text = "";
        String thread;
        String turn;
        NativeRouteEventGate.RouteToken requestRoute;
        synchronized (nativeRouteDeliveryLock) {
            thread = threadId;
            turn = activeTurnId;
            if (thread == null || thread.isEmpty() || turn == null || turn.isEmpty()) return -1;
            requestRoute = nativeRouteEventGate.captureRouteToken(thread);
            if (!nativeRouteEventGate.isCurrent(requestRoute)) return -1;
        }
        try {
            JSONArray attachments;
            try { attachments = new JSONArray(attachmentsJson == null ? "[]" : attachmentsJson); }
            catch (Exception ignored) { attachments = new JSONArray(); }
            JSONArray skills;
            try { skills = new JSONArray(skillsJson == null ? "[]" : skillsJson); }
            catch (Exception ignored) { skills = new JSONArray(); }
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
            if (input.length() == 0) return -1;
            JSONObject params = new JSONObject()
                .put("threadId", thread)
                .put("expectedTurnId", turn)
                .put("input", input);
            int requestId = nextId.getAndIncrement();
            pendingSteerRequestRoutes.put(requestId, requestRoute);
            try {
                sendJson(new JSONObject().put("method", "turn/steer").put("id", requestId).put("params", params));
            } catch (Exception error) {
                pendingSteerRequestRoutes.remove(requestId);
                throw error;
            }
            return requestId;
        } catch (Exception error) {
            Log.w(TAG, "Unable to steer active turn", error);
            return -1;
        }
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

    void respondUserInput(String rawRequest, String answersJson) {
        Object requestId = null;
        JSONObject requestParams = null;
        NativeRouteEventGate.RouteToken requestRoute = null;
        try {
            JSONObject request = new JSONObject(rawRequest == null ? "{}" : rawRequest);
            requestId = request.opt("requestId");
            if (requestId == null || requestId == JSONObject.NULL) return;
            requestParams = request.optJSONObject("params");
            requestRoute = routeForServerRequestResponse(requestId, requestParams);
            JSONObject result = new JSONObject();
            result.put("answers", new JSONObject(answersJson == null ? "{}" : answersJson));
            sendJson(new JSONObject().put("id", requestId).put("result", result));
            retireServerRequestRoute(requestId);
        } catch (Exception e) {
            if (requestRoute == null && requestParams != null) {
                requestRoute = captureBlockingRequestRoute(requestParams);
            }
            if (requestRoute == null && hasAnyRequestRouteIdentity(requestParams)) {
                Log.w(TAG, "Unable to answer stale user-input request", e);
                return;
            }
            emitNativeErrorForRoute(requestRoute, e.getMessage());
        }
    }

    void respondApprovalRequest(String rawRequest, String decision) {
        Object requestId = null;
        JSONObject requestParams = null;
        NativeRouteEventGate.RouteToken requestRoute = null;
        try {
            JSONObject request = new JSONObject(rawRequest == null ? "{}" : rawRequest);
            requestId = request.opt("requestId");
            String method = request.optString("method", "");
            if (requestId == null || requestId == JSONObject.NULL || method.isEmpty()) return;
            requestParams = request.optJSONObject("params");
            requestRoute = routeForServerRequestResponse(requestId, requestParams);
            JSONObject result = NativeApprovalProtocol.result(method, requestParams, decision);
            sendJson(new JSONObject().put("id", requestId).put("result", result));
            retireServerRequestRoute(requestId);
            Log.i(TAG, "APPROVAL_RESPONSE method=" + method + " decision=" + decision);
        } catch (Exception e) {
            if (requestRoute == null && requestParams != null) {
                requestRoute = captureBlockingRequestRoute(requestParams);
            }
            if (requestRoute == null && hasAnyRequestRouteIdentity(requestParams)) {
                Log.w(TAG, "Unable to answer stale approval request", e);
                return;
            }
            emitNativeErrorForRoute(requestRoute, e.getMessage());
        }
    }


    private synchronized void clearDeferredResume() {
        deferredResumeThreadId = null;
        deferredResumeGeneration = -1;
    }

    /** Returns true when the resume will be sent by the initialize-response handler. */
    private synchronized boolean deferResumeUntilInitialized(String resumeThreadId, int generation) {
        if (appServerInitialized) return false;
        deferredResumeThreadId = resumeThreadId;
        deferredResumeGeneration = generation;
        return true;
    }

    private synchronized void sendInitialize(int generation) throws Exception {
        if (!isServerGenerationActive(generation)) return;
        JSONObject clientInfo = new JSONObject()
            .put("name", "ilyop_codex_android")
            .put("title", "Codex by ilyop")
            .put("version", "0.1.0");
        initializeRequestId = sendRequest("initialize", new JSONObject().put("clientInfo", clientInfo)
            .put("capabilities", new JSONObject().put("experimentalApi", true)));
    }

    /** Re-probe MCP server status on demand (called from settings page). */
    void refreshMcpStatus() {
        if (desktopBridge != null) return; // Only native mode probes MCP status directly.
        try {
            mcpStatusRequestId = sendRequest("mcpServerStatus/list", new JSONObject()
                .put("cursor", JSONObject.NULL).put("limit", 100).put("detail", "full"));
        } catch (Exception error) {
            android.util.Log.w(TAG, "Unable to inspect MCP server status", error);
        }
    }

    private String configuredCwd() {
        String cwd = TermuxConstants.TERMUX_HOME_DIR_PATH;
        android.content.SharedPreferences mobile = appContext.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE);
        if (mobile.getBoolean("custom_project_root_enabled", true)) {
            String configured = mobile.getString("custom_project_root", "/storage/emulated/0/");
            if (configured != null && new File(configured).isDirectory()) cwd = configured;
        }
        return cwd;
    }

    private String configuredPermissionMode() {
        android.content.SharedPreferences mobile = appContext.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE);
        return NativePermissionMode.normalize(mobile.getString(NativePermissionMode.PREFERENCE_KEY, NativePermissionMode.FULL_ACCESS));
    }

    private void sendThreadStart() throws Exception { sendThreadStart(null); }

    private void sendThreadStart(String requestedCwd) throws Exception {
        JSONObject params = new JSONObject();
        applyNativeMcpConfig(params);
        String mode = configuredPermissionMode();
        String cwd = requestedCwd == null || requestedCwd.trim().isEmpty() ? configuredCwd() : requestedCwd.trim();
        if (!new File(cwd).isDirectory()) cwd = configuredCwd();
        NativePermissionMode.applyThreadParams(params, mode, cwd);
        Log.i(TAG, "THREAD_PERMISSIONS mode=" + mode + " approval="
            + NativePermissionMode.approvalPolicy(mode) + " sandbox=" + NativePermissionMode.sandbox(mode));
        int generation = navigationGeneration.get();
        if (eventListener != null) sendNavigationRequest("thread/start", params, generation);
        else sendRequest("thread/start", params);
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

    private int sendNavigationRequest(String method, JSONObject params, int generation) throws Exception {
        int id = nextId.getAndIncrement();
        JSONObject request = new JSONObject().put("method", method).put("id", id).put("params", params);
        rememberPrimaryRequest(request);
        pendingNavigationGenerations.put(id, generation);
        pendingNavigationMethods.put(id, method);
        try {
            sendJson(request);
        } catch (Exception error) {
            pendingNavigationGenerations.remove(id);
            pendingNavigationMethods.remove(id);
            pendingPrimaryThreadTitles.remove(id);
            throw error;
        }
        return id;
    }

    private int sendRequest(String method, JSONObject params) throws Exception {
        int id = nextId.getAndIncrement();
        JSONObject request = new JSONObject().put("method", method).put("id", id).put("params", params);
        rememberPrimaryRequest(request);
        try {
            sendJson(request);
        } catch (Exception error) {
            forgetPrimaryRequest(request);
            throw error;
        }
        return id;
    }

    private synchronized void sendJson(JSONObject message) throws Exception {
        if (writer == null) throw new IllegalStateException("app-server is not running");
        android.util.Log.d(TAG, "SEND " + messageSummary(message));
        writer.write(message.toString());
        writer.newLine();
        writer.flush();
    }

    private boolean isServerGenerationActive(int generation) {
        return serverGeneration.get() == generation;
    }

    /** Tear down a partially bootstrapped generation without touching a newer one. */
    private void cleanupFailedBootstrap(int generation, LocalApiProxy localProxy, Process activeProcess) {
        BufferedWriter writerToClose = null;
        Process processToDestroy = null;
        LocalApiProxy proxyToStop = null;
        synchronized (this) {
            if (!isServerGenerationActive(generation)) return;
            if (activeProcess != null) {
                processToDestroy = activeProcess;
                if (process == activeProcess) {
                    process = null;
                    writerToClose = writer;
                    writer = null;
                }
            }
            if (localProxy != null) {
                if (apiProxy == localProxy) apiProxy = null;
                proxyToStop = localProxy;
            }
        }
        try { if (writerToClose != null) writerToClose.close(); } catch (Exception ignored) {}
        if (processToDestroy != null) processToDestroy.destroy();
        if (proxyToStop != null) proxyToStop.stop();
    }

    private synchronized boolean isCurrentServerProcess(Process candidate, int generation) {
        return isServerGenerationActive(generation) && process == candidate;
    }

    private void monitorProcess(Process activeProcess, int generation) {
        try {
            int exitCode = activeProcess.waitFor();
            boolean unexpected;
            synchronized (this) {
                unexpected = isServerGenerationActive(generation) && process == activeProcess;
                if (unexpected) {
                    process = null;
                    writer = null;
                    if (apiProxy != null) apiProxy.stop();
                    apiProxy = null;
                }
            }
            if (unexpected) {
                android.util.Log.w(TAG, "app-server exited with code " + exitCode);
                if (webView == null) {
                    CodexTaskStore.markInterruptedTasks(appContext);
                    emit("onNativeError", "Codex backend disconnected unexpectedly (exit code "
                        + exitCode + "). The current task has stopped.");
                }
                Activity boundActivity = boundActivity();
                if (boundActivity instanceof CodexHomeActivity) {
                    mainHandler.post(() -> ((CodexHomeActivity) boundActivity).onCodexAppServerExited(exitCode));
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * agents.max_threads is now supplied only via command-line overrides (agentConfigOverrides),
     * which set it solely when multi_agent_v2 is disabled. Strip any stale max_threads left in
     * config.toml by earlier builds so it can never coexist with an enabled multi_agent_v2, even
     * though the command line would otherwise be overridden by this on-disk value.
     */
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

    private void readStdout(Process activeProcess, int generation) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!isCurrentServerProcess(activeProcess, generation)) return;
                JSONObject message = new JSONObject(line);
                if (!isHighFrequencyNotification(message.optString("method", ""))) {
                    android.util.Log.d(TAG, "RECV " + messageSummary(message));
                }
                handleMessageFromProcess(activeProcess, generation, message);
            }
        } catch (Exception e) {
            if (isCurrentServerProcess(activeProcess, generation)) emit("onNativeError", "app-server output stopped: " + e.getMessage());
        }
    }

    /**
     * Keeps one stdout message linearized with stop/start. A generation check performed before
     * parsing is insufficient: stop() could clear request tombstones and install a new process
     * before the old message mutates route state. Both operations use this bridge monitor, so an
     * old message either completes before stop or is rejected after the new generation wins.
     */
    private synchronized void handleMessageFromProcess(
            Process activeProcess, int generation, JSONObject message) throws Exception {
        if (serverGeneration.get() != generation || process != activeProcess) return;
        handleMessage(message);
    }

    private void readStderr(Process activeProcess, int generation) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!isCurrentServerProcess(activeProcess, generation)) return;
                String safeLine = redactSensitiveLogLine(line);
                android.util.Log.e(TAG, "STDERR " + safeLine);
                emit("onLog", safeLine);
            }
        } catch (Exception ignored) {}
    }

    private void handleMessage(JSONObject message) throws Exception {
        rememberTurnStart(message);
        resolveOutgoingRequestTracking(message);
        if (message.has("id") && message.has("method")) {
            String inboundMethod = message.optString("method", "");
            if (NativeApprovalProtocol.isApprovalMethod(inboundMethod)) {
                JSONObject inboundParams = message.optJSONObject("params");
                NativeRouteEventGate.RouteToken requestRoute = captureBlockingRequestRoute(inboundParams);
                String requestedThread = protocolThreadId(inboundParams);
                if (requestedThread.isEmpty() && requestRoute != null) {
                    requestedThread = requestRoute.getSourceThreadId();
                }
                if (!requestedThread.isEmpty()) {
                    NativeTaskNotificationManager.notifyEvent(appContext, requestedThread,
                        NativeTaskNotificationPolicy.APPROVAL, String.valueOf(message.opt("id")), "");
                }
                rememberServerRequestRoute(message.opt("id"), requestRoute,
                    inboundParams, inboundMethod);
                if (requestRoute != null) {
                    emitForRoute(requestRoute, "onApprovalRequest", new JSONObject()
                        .put("requestId", message.opt("id"))
                        .put("method", inboundMethod)
                        .put("params", message.optJSONObject("params")).toString());
                }
                Log.i(TAG, "APPROVAL_REQUEST method=" + inboundMethod);
                return;
            }
            if (inboundMethod.contains("requestUserInput") || "item/tool/requestUserInput".equals(inboundMethod)) {
                JSONObject inboundParams = message.optJSONObject("params");
                NativeRouteEventGate.RouteToken requestRoute = captureBlockingRequestRoute(inboundParams);
                String requestedThread = protocolThreadId(inboundParams);
                if (requestedThread.isEmpty() && requestRoute != null) {
                    requestedThread = requestRoute.getSourceThreadId();
                }
                String questionDetail = "";
                JSONArray questions = inboundParams == null ? null : inboundParams.optJSONArray("questions");
                if (questions != null && questions.length() > 0) {
                    JSONObject firstQuestion = questions.optJSONObject(0);
                    if (firstQuestion != null) questionDetail = firstQuestion.optString("header",
                        firstQuestion.optString("question", ""));
                }
                if (!requestedThread.isEmpty()) {
                    NativeTaskNotificationManager.notifyEvent(appContext, requestedThread,
                        NativeTaskNotificationPolicy.ANSWER, String.valueOf(message.opt("id")), questionDetail);
                }
                rememberServerRequestRoute(message.opt("id"), requestRoute,
                    inboundParams, inboundMethod);
                if (requestRoute != null) {
                    emitForRoute(requestRoute, "onUserInputRequest", new JSONObject()
                        .put("requestId", message.opt("id"))
                        .put("method", inboundMethod)
                        .put("params", message.optJSONObject("params")).toString());
                }
                return;
            }
        }
        if (initializeRequestId >= 0 && message.has("id") && message.has("result")
                && message.optInt("id", -1) == initializeRequestId) {
            initializeRequestId = -1;
            sendJson(new JSONObject().put("method", "initialized"));
            try { collaborationModesRequestId = sendRequest("collaborationMode/list", new JSONObject()); }
            catch (Exception error) { android.util.Log.w(TAG, "Unable to list collaboration modes", error); }
            if (desktopBridge == null) try {
                mcpStatusRequestId = sendRequest("mcpServerStatus/list", new JSONObject()
                    .put("cursor", JSONObject.NULL).put("limit", 100).put("detail", "full"));
            } catch (Exception error) {
                android.util.Log.w(TAG, "Unable to inspect MCP server status", error);
            }
            String deferredThread;
            int deferredGeneration;
            synchronized (this) {
                appServerInitialized = true;
                deferredThread = deferredResumeThreadId;
                deferredGeneration = deferredResumeGeneration;
                deferredResumeThreadId = null;
                deferredResumeGeneration = -1;
            }
            if (desktopBridge != null) {
                desktopBridge.onAppServerInitialized();
            } else if (deferredThread != null
                    && deferredGeneration == navigationGeneration.get()
                    && deferredThread.equals(visibleThreadId)) {
                JSONObject resumeParams = new JSONObject().put("threadId", deferredThread);
                applyNativeMcpConfig(resumeParams);
                String permissionMode = configuredPermissionMode();
                NativePermissionMode.applyThreadParams(resumeParams, permissionMode, configuredCwd());
                sendNavigationRequest("thread/resume", resumeParams, deferredGeneration);
            } else if (threadId == null) {
                // A native resume may still be loading history. Its worker will send the request
                // after observing appServerInitialized, so do not create an orphan new thread.
                sendThreadStart();
            }
            return;
        }
        if (mcpStatusRequestId >= 0 && message.optInt("id", -1) == mcpStatusRequestId) {
            mcpStatusRequestId = -1;
            String summary = mcpStatusSummary(message);
            NativeMcpRuntimeStatusStore.record(appContext, message.toString());
            Log.i(TAG, "MCP_STATUS " + summary);
            emit("onMcpStatus", summary);
            return;
        }
        if (desktopBridge != null) desktopBridge.onAppServerMessage(message);
        int steerResponseId = message.optInt("id", -1);
        NativeRouteEventGate.RouteToken steerRoute = steerResponseId < 0
            ? null : pendingSteerRequestRoutes.remove(steerResponseId);
        if (steerRoute != null) {
            JSONObject event = new JSONObject().put("requestId", steerResponseId).put("accepted", !message.has("error"));
            if (message.has("error")) {
                JSONObject error = message.optJSONObject("error");
                event.put("message", error == null ? "Unable to steer the active turn"
                    : error.optString("message", "Unable to steer the active turn"));
                if (error != null && error.has("data")) event.put("data", error.opt("data"));
            }
            emitForRoute(steerRoute, "onSteerResult", event.toString());
            scheduleDeferredTaskCompletionCheck(steerRoute.getSourceThreadId());
            return;
        }
        if (message.has("error")) {
            int failedRequestId = message.optInt("id", -1);
            Integer failedNavigationGeneration = pendingNavigationGenerations.remove(failedRequestId);
            String failedNavigationMethod = pendingNavigationMethods.remove(failedRequestId);
            pendingPrimaryThreadTitles.remove(failedRequestId);
            if (failedNavigationMethod != null && eventListener != null
                    && (failedNavigationGeneration == null
                        || failedNavigationGeneration != navigationGeneration.get())) {
                NativeChatDiagnostics.record(appContext, "ignored_navigation_error", new JSONObject()
                    .put("method", failedNavigationMethod)
                    .put("generation", failedNavigationGeneration));
                return;
            }
            String goalThread = pendingGoalGetRequests.remove(message.optInt("id", -1));
            if (goalThread != null) {
                Log.w(TAG, "Unable to read thread goal for " + shortId(goalThread) + ": "
                    + message.optJSONObject("error").optString("message", "unknown error"));
                return;
            }
            if (isMcpAvailabilityError(message)) {
                String detail = message.optJSONObject("error").optString("message", "MCP server unavailable");
                NativeMcpRuntimeStatusStore.recordFailure(appContext, detail);
                Log.w(TAG, "MCP unavailable; continuing native chat without the failed server: " + detail);
                emit("onMcpStatus", "error=" + detail);
                return;
            }
            if (message.optInt("id", -1) == compactRequestId) {
                String failedCompactThread = compactRequestThreadId;
                compactRequestId = -1;
                compactionTurnTracker.requestFailed(failedCompactThread);
                String compactError = message.getJSONObject("error").optString("message", message.toString());
                emitCompactionRpcResult(false, compactError);
                compactRequestThreadId = null;
                if (webView != null) emit("onCompactStatus", "failed");
            }
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
        if (message.has("id") && message.optInt("id", -1) == collaborationModesRequestId) {
            collaborationModesRequestId = -1;
            JSONObject wrapper = message.optJSONObject("result");
            JSONArray modes = wrapper == null ? null : wrapper.optJSONArray("data");
            if (modes == null && wrapper != null) modes = wrapper.optJSONArray("modes");
            if (modes != null) {
                for (int index = 0; index < modes.length(); index++) {
                    JSONObject candidate = modes.optJSONObject(index);
                    if (candidate == null) continue;
                    String mode = candidate.optString("mode", candidate.optString("id", candidate.optString("name", "")));
                    if ("plan".equalsIgnoreCase(mode)) { planCollaborationMode = candidate; break; }
                }
            }
            android.util.Log.i(TAG, "COLLAB_MODES " + redactSensitiveLogLine(String.valueOf(message.opt("result"))));
        }
        if (message.has("id") && message.has("result")) {
            int id = message.optInt("id", -1);
            String goalThread = pendingGoalGetRequests.remove(id);
            if (goalThread != null) {
                JSONObject result = message.optJSONObject("result");
                publishNativeGoalState(goalThread, result == null ? null : result.optJSONObject("goal"));
                return;
            }
            if (id == compactRequestId) {
                compactRequestId = -1;
                emitCompactionRpcResult(true, "");
                compactRequestThreadId = null;
                if (webView != null) emit("onCompactStatus", "completed");
            }
            JSONObject result = message.optJSONObject("result");
            if (result != null && result.optJSONObject("thread") != null) {
                String resultThreadId = result.getJSONObject("thread").getString("id");
                primaryThreadIds.add(resultThreadId);
                Integer responseId = message.has("id") ? message.optInt("id", -1) : -1;
                String pendingTitle = pendingPrimaryThreadTitles.remove(responseId);
                if (pendingTitle != null && desktopBridge != null) CodexTaskStore.markRunning(appContext, resultThreadId, pendingTitle);
                Integer responseGeneration = pendingNavigationGenerations.remove(responseId);
                String navigationMethod = pendingNavigationMethods.remove(responseId);
                boolean acceptForNative;
                NativeRouteEventGate.RouteToken readyRoute = null;
                synchronized (nativeRouteDeliveryLock) {
                    acceptForNative = eventListener == null || (responseGeneration != null
                        && responseGeneration == navigationGeneration.get()
                        && (visibleThreadId == null || visibleThreadId.equals(resultThreadId)));
                    if (acceptForNative) {
                        boolean sameResumeRoute = "thread/resume".equals(navigationMethod)
                            && resultThreadId.equals(visibleThreadId);
                        readyRoute = sameResumeRoute
                            ? nativeRouteEventGate.captureRouteToken(resultThreadId)
                            : resetVisibleRoute(resultThreadId, true);
                    }
                }
                if (acceptForNative) {
                    emitForRoute(readyRoute, "onReady", resultThreadId);
                } else {
                    NativeChatDiagnostics.record(appContext, "ignored_navigation_result", new JSONObject()
                        .put("thread", shortId(resultThreadId)).put("generation", responseGeneration));
                }
            } else {
                pendingNavigationGenerations.remove(id);
                pendingNavigationMethods.remove(id);
                pendingPrimaryThreadTitles.remove(id);
            }
            return;
        }
        String method = message.optString("method", "");
        JSONObject params = message.optJSONObject("params");
        NativeRouteEventGate.RouteToken notificationRoute =
            nativeRouteEventGate.captureRouteToken(protocolThreadId(params));
        boolean primaryEvent = shouldAcceptNotificationForRoute(method, params, notificationRoute);
        nativeRouteEmission.remove();
        if (primaryEvent) nativeRouteEmission.set(notificationRoute);
        try {
        logCollabAgentEvent(method, params);
        if ("thread/goal/updated".equals(method) && params != null) {
            publishNativeGoalState(params.optString("threadId", ""), params.optJSONObject("goal"));
            return;
        }
        if ("thread/goal/cleared".equals(method) && params != null) {
            String clearedThread = params.optString("threadId", "");
            autoClearingCompletedGoalThreads.remove(clearedThread);
            if (desktopBridge == null && isVisibleThreadEvent(params, visibleThreadId)) {
                emit("onGoalCleared", new JSONObject().put("threadId", clearedThread).toString());
            }
            return;
        }
        if ("serverRequest/resolved".equals(method) && params != null) {
            String requestKey = serverRequestRouteKey(params.opt("requestId"));
            PendingServerRequestRoute pendingRequest = ambiguousServerRequestRouteKeys.contains(requestKey)
                ? null : pendingServerRequestRoutes.remove(requestKey);
            NativeRouteEventGate.RouteToken requestRoute = null;
            if (pendingRequest != null) {
                if (!pendingRequest.matchesResolution(params)) {
                    Log.w(TAG, "Ignoring resolved request with mismatched route identity");
                    return;
                }
                synchronized (nativeRouteDeliveryLock) {
                    if (nativeRouteEventGate.isCurrent(pendingRequest.routeToken)) {
                        requestRoute = pendingRequest.routeToken;
                    }
                }
            }
            if (requestRoute == null) requestRoute = captureBlockingRequestRoute(params);
            if (desktopBridge == null && requestRoute != null) {
                emitForRoute(requestRoute, "onUserInputResolved", params.toString());
            }
            if (pendingRequest != null) {
                scheduleDeferredTaskCompletionCheck(pendingRequest.sourceThreadId);
            }
            return;
        }
        if (!primaryEvent && "item/completed".equals(method) && params != null) {
            JSONObject ignoredItem = params.optJSONObject("item");
            String ignoredThread = params.optString("threadId", "");
            String ignoredEvent = primaryThreadIds.contains(ignoredThread) ? "ignored_background_item" : "ignored_child_item";
            NativeChatDiagnostics.record(appContext, ignoredEvent, new JSONObject()
                .put("thread", shortId(ignoredThread))
                .put("visibleThread", shortId(visibleThreadId))
                .put("routeReady", visibleRouteReady)
                .put("type", ignoredItem == null ? "" : ignoredItem.optString("type", "")));
        }
        if ("event_msg".equals(method) && params != null && primaryEvent) {
            JSONObject eventPayload = params.optJSONObject("payload");
            if (eventPayload == null) eventPayload = params.optJSONObject("msg");
            if (eventPayload != null && "plan_update".equals(eventPayload.optString("type"))) {
                NativeChatDiagnostics.record(appContext, "live_plan_event_msg", eventPayload);
                emit("onPlanUpdated", eventPayload.toString());
            }
        }
        boolean planLikeMethod = method.toLowerCase(java.util.Locale.ROOT).contains("plan")
            || (params != null && (params.has("plan") || params.has("proposedPlan")));
        if (planLikeMethod && !"item/plan/delta".equals(method)) {
            // Plan deltas can arrive a few characters at a time. Persisting every one
            // floods the diagnostics executor and disk while the UI is streaming.
            NativeChatDiagnostics.record(appContext, "plan_signal", new JSONObject()
                .put("method", method).put("primary", primaryEvent)
                .put("params", params == null ? JSONObject.NULL : params));
        }
        if (("turn/plan/updated".equals(method) || "turn/planUpdated".equals(method)
                || "plan/updated".equals(method) || "item/plan/updated".equals(method)
                || "item/planUpdated".equals(method)) && params != null && primaryEvent) {
            // Keep the raw payload: native normalizes params.plan, turn.plan and
            // payload.plan because app-server versions differ in nesting.
            NativeChatDiagnostics.record(appContext, "plan_updated", new JSONObject()
                .put("method", method).put("thread", shortId(params.optString("threadId", ""))));
            emit("onPlanUpdated", params.toString());
        }
        if (("item/started".equals(method) || "item/completed".equals(method)) && params != null && primaryEvent) {
            emitProtocolItemEvent(method, params);
        }
        if (primaryEvent && params != null && isContextCompactionLifecycleMethod(method)
                && !("item/started".equals(method) || "item/completed".equals(method))) {
            emitProtocolCompactionLifecycle(method, params);
        }
        if (primaryEvent && params != null && (
                "contextCompacted".equals(method) || "context/compacted".equals(method)
                || "thread/contextCompacted".equals(method) || "thread/context_compacted".equals(method))) {
            try {
                JSONObject legacy = new JSONObject()
                    .put("kind", "contextCompactionCompleted")
                    .put("method", method)
                    .put("threadId", protocolThreadId(params))
                    .put("turnId", protocolTurnId(params))
                    .put("itemId", protocolItemId(params))
                    .put("legacy", true)
                    .put("sequence", nativeProtocolSequence.incrementAndGet())
                    .put("timestampMs", System.currentTimeMillis());
                emit("onProtocolEvent", legacy.toString());
            } catch (Exception ignored) {}
        }
        if (("item/started".equals(method) || "item/completed".equals(method)) && params != null && primaryEvent) {
            JSONObject liveItem = params.optJSONObject("item");
            String liveType = liveItem == null ? "" : liveItem.optString("type", "");
            JSONArray liveReceivers = liveItem == null ? null : liveItem.optJSONArray("receiverThreadIds");
            boolean hasAgentIdentity = liveItem != null && (!liveItem.optString("agentThreadId", "").isEmpty()
                || (liveReceivers != null && liveReceivers.length() > 0));
            if ("subAgentActivity".equals(liveType) || ("collabAgentToolCall".equals(liveType) && hasAgentIdentity)) {
                JSONObject presentationItem = new JSONObject(liveItem.toString());
                if ("item/started".equals(method)) {
                    presentationItem.put("status", "working");
                } else if ("subAgentActivity".equals(liveType)) {
                    // Some app-server versions omit status on the completed subAgentActivity
                    // item. Do not turn a finished chip back into a working one; preserve an
                    // explicit failure/cancel state and otherwise expose a terminal status.
                    String existingStatus = presentationItem.optString("status", "").trim();
                    String normalizedStatus = normalizeProtocolName(existingStatus);
                    if (existingStatus.isEmpty() || "working".equals(normalizedStatus)
                            || "running".equals(normalizedStatus) || "inprogress".equals(normalizedStatus)) {
                        presentationItem.put("status", "done");
                    }
                }
                emit("onSubagentEvent", NativeLargePayloadStore.compactToolItem(presentationItem));
                NativeChatDiagnostics.record(appContext, "subagent_capsule", new JSONObject()
                    .put("method", method).put("type", liveType)
                    .put("thread", shortId(params.optString("threadId", ""))));
            }
        }
        if (primaryEvent && "item/plan/delta".equals(method) && params != null) {
            // Plan mode emits its final <proposed_plan> block as a dedicated Plan item,
            // not as an agentMessage. Forward that stream explicitly or the native UI
            // remains blank until it reparses the rollout after a route change.
            emitProtocolDeltaEvent("planDelta", params, params.optString("delta", ""));
            if (webView != null) emit("onPlanDelta", params.toString());
        } else if (primaryEvent && "item/started".equals(method) && isContextCompactionItem(params)) {
            // The normalized event above owns the native divider. Do not turn compaction into a
            // generic processing label/capsule.
        } else if (primaryEvent && "item/completed".equals(method) && isContextCompactionItem(params)) {
            // Completion updates the same stable divider via onProtocolEvent.
        } else if (primaryEvent && "item/started".equals(method) && isPlanItem(params)) {
            if (webView != null) emit("onPlanStarted", params.toString());
        } else if (primaryEvent && "item/completed".equals(method) && isPlanItem(params)) {
            if (webView != null) emit("onPlanComplete", params.toString());
        } else if (primaryEvent && "item/started".equals(method) && isCommandItem(params)) {
            JSONObject item = params == null ? null : params.optJSONObject("item");
            if (webView != null) emit("onCommandStarted", item == null ? "{}" : item.toString());
        } else if (primaryEvent && "item/agentMessage/delta".equals(method) && params != null) {
            String itemId = params.optString("itemId", "");
            if (!itemId.isEmpty()) streamedAgentItemIds.add(itemId);
            String delta = params.optString("delta", "");
            lastAgentDeltaAt = android.os.SystemClock.uptimeMillis();
            agentDeltaCount++;
            agentDeltaChars += delta.length();
            emitProtocolDeltaEvent("assistantDelta", params, delta);
            if (webView != null) emit("onDelta", delta);
        } else if (primaryEvent && "item/completed".equals(method) && isReasoningItem(params)) {
            JSONObject item = params.optJSONObject("item");
            String text = extractReasoningText(item);
            if (webView != null && !text.isEmpty()) emit("onReasoningComplete", text);
        } else if (primaryEvent && "item/completed".equals(method) && isCommandItem(params)) {
            JSONObject item = params.optJSONObject("item");
            // handleNotification runs on the app-server reader thread. Strip large streams
            // here so the main thread receives only command metadata and an output reference.
            if (webView != null) emit("onCommandComplete", NativeCommandOutputStore.compactCommandItem(item));
        } else if (primaryEvent && "item/completed".equals(method) && isToolDetailItem(params)) {
            JSONObject item = params.optJSONObject("item");
            if (webView != null) emit("onToolComplete", NativeLargePayloadStore.compactToolItem(item));
        } else if (primaryEvent && "item/completed".equals(method) && isAgentMessageItem(params)) {
            JSONObject item = params == null ? null : params.optJSONObject("item");
            boolean finalAnswer = isFinalAgentMessage(params);
            Log.i(TAG, "agent-stream-complete chunks=" + agentDeltaCount + " chars=" + agentDeltaChars
                + " phase=" + (item == null ? "" : item.optString("phase", "missing")));
            lastAgentDeltaAt = 0L;
            agentDeltaCount = 0;
            agentDeltaChars = 0;
            String itemId = item == null ? "" : item.optString("id", "");
            if (!itemId.isEmpty()) streamedAgentItemIds.remove(itemId);
            // A completed agent message seals only that text item. The authoritative turn
            // boundary is the real turn/completed notification; neither a final answer item nor
            // thread idle may synthesize it without risking interruption of compaction or another
            // continuation owned by the same server turn.
            String completedText = extractAgentMessageText(item);
            if (webView != null && !completedText.isEmpty()) {
                emit(finalAnswer ? "onFinalAnswer" : "onAssistantItemComplete", completedText);
            }
        } else if (primaryEvent && ("item/reasoning/summaryTextDelta".equals(method) || "item/reasoning/textDelta".equals(method)) && params != null) {
            String delta = params.optString("delta", "");
            emitProtocolDeltaEvent("reasoningDelta", params, delta);
            if (webView != null) emit("onReasoningDelta", delta);
        } else if (primaryEvent && "item/commandExecution/outputDelta".equals(method) && params != null) {
            emitProtocolCommandDelta(params);
            if (webView != null) emit("onCommandDelta", params.optString("delta", ""));
        } else if (primaryEvent && "item/started".equals(method) && params != null) {
            JSONObject item = params.optJSONObject("item");
            if (item != null) emit("onItem", item.optString("type", "item"));
        } else if (primaryEvent && params != null
                && "threadtokenusageupdated".equals(normalizeProtocolName(method))) {
            JSONObject usage = params.optJSONObject("tokenUsage");
            if (usage == null) usage = params.optJSONObject("token_usage");
            JSONObject usagePayload = usage == null ? new JSONObject() : new JSONObject(usage.toString());
            // Threshold/context metadata has appeared both beside and inside tokenUsage across
            // app-server releases. Preserve it without inventing turn/start parameters.
            String[] usageKeys = new String[] {
                "autoCompactTokenLimit", "auto_compact_token_limit", "contextWindow", "context_window",
                "autoCompactTokenLimitTokens", "auto_compact_token_limit_tokens",
                "modelAutoCompactTokenLimit", "model_auto_compact_token_limit",
                "modelContextWindow", "model_context_window", "contextTokens", "context_tokens",
                "currentContextTokens", "current_context_tokens", "contextUsageReliable", "context_usage_reliable",
                "usageReliable", "usage_reliable", "estimated", "isEstimated",
            };
            for (String key : usageKeys) if (!usagePayload.has(key) && params.has(key)) usagePayload.put(key, params.opt(key));
            emitProtocolLifecycleEvent("tokenUsageUpdated", params, usagePayload);
            if (webView != null) emit("onTokenUsage", usagePayload.toString());
        } else if ("thread/status/changed".equals(method) && isIdleThreadStatus(params)) {
            completeVisibleTurnFromIdle(params);
        } else if ("turn/completed".equals(method)) {
            String completedThread = protocolThreadId(params);
            String completedTurnId = protocolTurnId(params);
            if (isAuxiliaryCompactionTurnCompletion(compactionTurnTracker, params)) {
                clearPendingTurn(params);
                compactionTurnTracker.complete(completedThread, completedTurnId);
                NativeChatDiagnostics.record(appContext, "compaction_turn_complete", new JSONObject()
                    .put("thread", shortId(completedThread)).put("turn", shortId(completedTurnId)));
                maybeFinalizeDeferredTaskCompletion(completedThread);
                return;
            }
            boolean exactPrimaryCompletion = primaryEvent
                && isExactActivePrimaryTurn(completedThread, completedTurnId);
            boolean hasPendingContinuation = exactPrimaryCompletion
                && hasPendingContinuation(completedThread);
            if (exactPrimaryCompletion) {
                primaryCompletionTracker.record(
                    completedThread, completedTurnId, System.currentTimeMillis());
            }
            clearPendingTurn(params);
            if (exactPrimaryCompletion) {
                emitProtocolLifecycleEvent("turnCompleted", params, params == null ? null : params.optJSONObject("turn"));
                emit("onTurnComplete", turnLifecycleCallbackPayload(
                    params, hasPendingContinuation));
            }
            if (exactPrimaryCompletion) {
                boolean failed = turnFailed(params);
                recordOrFinalizeTaskCompletion(
                    completedThread, completedTurnId, failed, hasPendingContinuation);
            }
        } else if (primaryEvent && "error".equals(method) && params != null) {
            // Error notifications with willRetry=true are part of one turn. Keep their protocol
            // metadata so the native UI can update one retry card in place and, when a goal is
            // active, continue after the server's bounded internal retry budget is exhausted.
            emitProtocolLifecycleEvent("error", params, params);
            emit("onTurnError", params.toString());
        }
        } finally {
            nativeRouteEmission.remove();
        }
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

    private static boolean isMcpAvailabilityError(JSONObject message) {
        JSONObject error = message == null ? null : message.optJSONObject("error");
        if (error == null) return false;
        String value = (error.optString("message", "") + " " + error.optString("data", ""))
            .toLowerCase(java.util.Locale.ROOT);
        if (!value.contains("mcp")) return false;
        return value.contains("connect") || value.contains("initializ") || value.contains("start")
            || value.contains("unavailable") || value.contains("timed out") || value.contains("timeout")
            || value.contains("closed") || value.contains("refused");
    }

    private static boolean isToolDetailItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        if (item == null) return false;
        String type = normalizeItemType(item.optString("type", item.optString("item_type", "")));
        return "filechange".equals(type) || "mcptoolcall".equals(type) || "websearch".equals(type)
            || "collabagenttoolcall".equals(type) || "subagentactivity".equals(type)
            || "imageview".equals(type) || "viewimage".equals(type);
    }

    private static boolean isPlanItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        return item != null && "plan".equals(normalizeItemType(item.optString("type", item.optString("item_type", ""))));
    }

    private static boolean isReasoningItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        return item != null && "reasoning".equals(normalizeItemType(item.optString("type", item.optString("item_type", ""))));
    }

    private static boolean isCommandItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        return item != null && "commandexecution".equals(normalizeItemType(item.optString("type", item.optString("item_type", ""))));
    }

    private static String normalizeItemType(String value) {
        return value == null ? "" : value.replace("_", "").replace("-", "")
            .toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isContextCompactionItem(JSONObject params) {
        JSONObject item = params == null ? null : params.optJSONObject("item");
        String type = item == null
            ? (params == null ? "" : params.optString("itemType",
                params.optString("item_type", params.optString("type", ""))))
            : item.optString("type", item.optString("item_type", ""));
        String normalized = normalizeProtocolName(type);
        return "contextcompaction".equals(normalized) || "contextcompacted".equals(normalized);
    }

    private static boolean isContextCompactionLifecycleMethod(String method) {
        String normalized = normalizeProtocolName(method);
        return normalized.contains("contextcompaction") &&
            (normalized.endsWith("started") || normalized.endsWith("start")
                || normalized.endsWith("completed") || normalized.endsWith("complete")
                || normalized.endsWith("failed") || normalized.endsWith("cancelled") || normalized.endsWith("canceled"));
    }

    private static boolean isContextCompactionTerminalSignal(
            String method, JSONObject params) {
        if ("item/completed".equals(method) && isContextCompactionItem(params)) return true;
        String normalized = normalizeProtocolName(method);
        if (!(normalized.contains("contextcompaction")
                || normalized.contains("contextcompacted"))) return false;
        return normalized.endsWith("completed") || normalized.endsWith("complete")
            || normalized.endsWith("compacted") || normalized.endsWith("failed")
            || normalized.endsWith("cancelled") || normalized.endsWith("canceled");
    }

    private void emitProtocolCompactionLifecycle(String method, JSONObject params) {
        if (webView != null || params == null) return;
        try {
            JSONObject copy = new JSONObject(params.toString());
            JSONObject item = copy.optJSONObject("item");
            if (item == null) {
                item = new JSONObject().put("type", "contextCompaction");
                String itemId = protocolItemId(copy);
                if (!itemId.isEmpty()) item.put("id", itemId);
                copy.put("item", item);
            } else if (item.optString("type").isEmpty()) item.put("type", "contextCompaction");
            String normalized = normalizeProtocolName(method);
            boolean completed = normalized.endsWith("completed") || normalized.endsWith("complete")
                || normalized.endsWith("failed") || normalized.endsWith("cancelled") || normalized.endsWith("canceled");
            emitProtocolItemEvent(completed ? "item/completed" : "item/started", copy);
        } catch (Exception ignored) {}
    }

    private static String protocolThreadId(JSONObject params) {
        if (params == null) return "";
        String value = params.optString("threadId", params.optString("thread_id", ""));
        JSONObject turn = params.optJSONObject("turn");
        JSONObject item = params.optJSONObject("item");
        if (value.isEmpty() && turn != null) value = turn.optString("threadId", turn.optString("thread_id", ""));
        if (value.isEmpty() && item != null) value = item.optString("threadId", item.optString("thread_id", ""));
        if (value.isEmpty() && item != null) value = item.optString("senderThreadId", item.optString("sender_thread_id", ""));
        return value;
    }

    private static String protocolTurnId(JSONObject params) {
        if (params == null) return "";
        String value = params.optString("turnId", params.optString("turn_id", ""));
        JSONObject turn = params.optJSONObject("turn");
        if (value.isEmpty() && turn != null) value = turn.optString("id", turn.optString("turnId", turn.optString("turn_id", "")));
        JSONObject item = params.optJSONObject("item");
        if (value.isEmpty() && item != null) value = item.optString("turnId", item.optString("turn_id", ""));
        return value;
    }

    private static String protocolItemId(JSONObject params) {
        if (params == null) return "";
        String value = params.optString("itemId", params.optString("item_id", ""));
        JSONObject item = params.optJSONObject("item");
        if (value.isEmpty() && item != null) value = item.optString("id", item.optString("itemId", item.optString("item_id", "")));
        return value;
    }

    private static String normalizeProtocolName(String value) {
        return value == null ? "" : value
            .replace("_", "")
            .replace("-", "")
            .replace("/", "")
            .replace(".", "")
            .toLowerCase(java.util.Locale.ROOT);
    }

    private void emitProtocolItemEvent(String method, JSONObject params) {
        if (webView != null || params == null) return;
        try {
            JSONObject item = params.optJSONObject("item");
            String itemType = item == null ? "" : item.optString("type", item.optString("item_type", ""));
            String normalizedItemType = normalizeItemType(itemType);
            String lifecycle = "item/started".equals(method) ? "started" : "completed";
            String kind;
            if (isContextCompactionItem(params)) kind = "contextCompaction" + capitalize(lifecycle);
            else if (isCommandItem(params)) kind = "command" + capitalize(lifecycle);
            else if (isReasoningItem(params)) kind = "reasoning" + capitalize(lifecycle);
            else if (isPlanItem(params)) kind = "plan" + capitalize(lifecycle);
            else if (isAgentMessageItem(params)) kind = "assistant" + capitalize(lifecycle);
            else if (item != null && ("subagentactivity".equals(normalizedItemType) || "collabagenttoolcall".equals(normalizedItemType))) kind = "subagentUpdated";
            else kind = "tool" + capitalize(lifecycle);

            JSONObject safeItem = item == null ? new JSONObject() : new JSONObject(item.toString());
            if ("commandexecution".equals(normalizedItemType) && "completed".equals(lifecycle)) {
                safeItem = new JSONObject(NativeCommandOutputStore.compactCommandItem(item));
            } else if ("completed".equals(lifecycle) && isToolDetailItem(params)) {
                safeItem = new JSONObject(NativeLargePayloadStore.compactToolItem(item));
            } else if ("agentmessage".equals(normalizedItemType)) {
                String text = extractAgentMessageText(item);
                safeItem.remove("content");
                safeItem.put("text", text);
            }
            if ("subagentactivity".equals(normalizedItemType)
                    || "collabagenttoolcall".equals(normalizedItemType)) {
                String status = safeItem.optString("status", "").trim();
                String normalizedStatus = normalizeProtocolName(status);
                if ("started".equals(lifecycle)) {
                    safeItem.put("status", "working");
                } else if (status.isEmpty() || "working".equals(normalizedStatus)
                        || "running".equals(normalizedStatus) || "inprogress".equals(normalizedStatus)) {
                    safeItem.put("status", "done");
                }
            }
            JSONObject payload = new JSONObject()
                .put("kind", kind)
                .put("method", method)
                .put("threadId", protocolThreadId(params))
                .put("turnId", protocolTurnId(params))
                .put("itemId", protocolItemId(params))
                .put("itemType", itemType)
                .put("sequence", nativeProtocolSequence.incrementAndGet())
                .put("timestampMs", System.currentTimeMillis())
                .put("item", safeItem);
            emit("onProtocolEvent", payload.toString());
        } catch (Exception error) {
            Log.w(TAG, "Unable to normalize protocol item event " + method, error);
        }
    }

    private void emitProtocolCommandDelta(JSONObject params) {
        if (webView != null || params == null) return;
        try {
            emit("onCommandDeltaV2", new JSONObject()
                .put("kind", "commandOutput")
                .put("threadId", protocolThreadId(params))
                .put("turnId", protocolTurnId(params))
                .put("itemId", protocolItemId(params))
                .put("sequence", nativeProtocolSequence.incrementAndGet())
                .put("timestampMs", System.currentTimeMillis())
                .put("delta", params.optString("delta", ""))
                .toString() + "\n");
        } catch (Exception ignored) {}
    }

    /** High-frequency normalized delta. It carries the same identity metadata as lifecycle items
     * and is batched by emit(), so the native reducer never has to infer an item from text order. */
    private void emitProtocolDeltaEvent(String kind, JSONObject params, String delta) {
        if (webView != null || params == null || delta == null || delta.isEmpty()) return;
        try {
            JSONObject item = params.optJSONObject("item");
            emit("onProtocolDelta", new JSONObject()
                .put("kind", kind)
                .put("threadId", protocolThreadId(params))
                .put("turnId", protocolTurnId(params))
                .put("itemId", protocolItemId(params))
                .put("itemPhase", item == null ? params.optString("phase", "") : item.optString("phase", ""))
                .put("sequence", nativeProtocolSequence.incrementAndGet())
                .put("timestampMs", System.currentTimeMillis())
                .put("delta", delta)
                .toString() + "\n");
        } catch (Exception ignored) {}
    }

    private void emitProtocolLifecycleEvent(String kind, JSONObject params, JSONObject details) {
        if (webView != null) return;
        try {
            JSONObject payload = new JSONObject()
                .put("kind", kind)
                .put("threadId", protocolThreadId(params))
                .put("turnId", protocolTurnId(params))
                .put("itemId", protocolItemId(params))
                .put("sequence", nativeProtocolSequence.incrementAndGet())
                .put("timestampMs", System.currentTimeMillis());
            if (details != null) payload.put("details", details);
            emit("onProtocolEvent", payload.toString());
        } catch (Exception ignored) {}
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
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
        JSONObject result = message.optJSONObject("result");
        JSONObject params = message.optJSONObject("params");
        JSONObject turn = result == null ? null : result.optJSONObject("turn");
        if (turn == null) {
            if (params != null) turn = params.optJSONObject("turn");
        }
        String method = message.optString("method", "");
        String turnId = turn == null ? protocolTurnId(params) : turn.optString("id", "");
        if (turnId.isEmpty()) turnId = protocolTurnId(params);
        String sourceThread = turn == null ? protocolThreadId(params)
            : turn.optString("threadId", turn.optString("thread_id", ""));
        if (sourceThread.isEmpty()) sourceThread = protocolThreadId(params);

        long nowMs = System.currentTimeMillis();
        String responseKey = message.has("id") && !message.has("method")
            ? serverRequestRouteKey(message.opt("id")) : "";
        String compactResponseThread = responseKey.isEmpty()
            ? null : pendingCompactRequestThreads.get(responseKey);
        String primaryStartResponseThread = responseKey.isEmpty()
            ? null : pendingPrimaryTurnStartRequestThreads.get(responseKey);
        boolean compactRpcResponse = compactResponseThread != null;
        boolean primaryStartRpcResponse = primaryStartResponseThread != null;
        if (sourceThread.isEmpty() && compactRpcResponse) {
            sourceThread = compactResponseThread;
        }
        if (sourceThread.isEmpty() && primaryStartRpcResponse) {
            sourceThread = primaryStartResponseThread;
        }

        boolean compactionSignal = isContextCompactionSignal(method, params);
        if (sourceThread.isEmpty() && (compactRpcResponse || compactionSignal)) {
            sourceThread = compactionTurnTracker.pendingThreadIfUnambiguous(nowMs);
        }

        boolean turnBoundarySignal = "turn/started".equals(method) || "turn/completed".equals(method);
        String primaryTurnHint = compactionSignal
            ? compactionPrimaryTurnHint(sourceThread, turnId, nowMs)
            : activeTurnForThread(sourceThread);
        boolean auxiliaryTurn = compactionTurnTracker.isAuxiliaryTurn(sourceThread, turnId);
        if (!auxiliaryTurn && (compactRpcResponse || compactionSignal || turnBoundarySignal)) {
            auxiliaryTurn = compactionTurnTracker.observeCandidate(
                sourceThread, turnId, nowMs,
                compactRpcResponse || compactionSignal, primaryTurnHint);
        }
        if (auxiliaryTurn) {
            provisionalTurnStarts.remove(sourceThread);
            repairAuxiliaryActiveTurn(sourceThread, turnId);
            if (isContextCompactionTerminalSignal(method, params)
                    && compactionTurnTracker.observeLifecycleTerminal(
                        sourceThread, turnId, nowMs)) {
                scheduleDeferredTaskCompletionCheck(
                    sourceThread, COMPACTION_TERMINAL_SETTLE_MS + 50L);
            }
        } else if (!compactionSignal && !"turn/started".equals(method)) {
            promoteProvisionalTurnStart(sourceThread, turnId);
        }

        if (!auxiliaryTurn && "turn/started".equals(method)
                && compactionTurnTracker.shouldQuarantineWeakStart(
                    sourceThread, turnId, nowMs)) {
            Log.i(TAG, "Quarantining turn start until compact RPC identity is known turn="
                + shortId(turnId));
            return;
        }
        if (auxiliaryTurn) return;

        if (!isTurnStartObservation(message, method, turn) || sourceThread.isEmpty() || turnId.isEmpty()) {
            return;
        }
        boolean confirmedPrimaryStart = primaryStartRpcResponse
            || hasPendingPrimaryTurnStart(sourceThread);
        boolean postCompletionWeakStart = !confirmedPrimaryStart
            && primaryCompletionTracker.shouldQuarantineWeakStart(
                sourceThread, turnId, nowMs);
        long startedAtSeconds = turn == null ? 0L : turn.optLong("startedAt", 0L);
        if (!rememberActiveTurn(sourceThread, turnId, confirmedPrimaryStart)) return;
        String key = turnKey(sourceThread, turnId);
        Long previousStart = turnStartedAtMs.putIfAbsent(key,
            startedAtSeconds > 0 ? startedAtSeconds * 1000L : nowMs);
        if (previousStart == null) {
            scheduleTurnRuntimeDiagnostics(sourceThread, turnId);
            scheduleTurnStallDiagnostics(sourceThread, turnId);
        }
        if (postCompletionWeakStart) {
            provisionalTurnStarts.put(sourceThread,
                new ProvisionalTurnStart(sourceThread, turnId, turn));
            return;
        }
        provisionalTurnStarts.remove(sourceThread);
        emitMainTurnStarted(sourceThread, turnId, turn);
    }

    private void resolveOutgoingRequestTracking(JSONObject message) {
        if (message == null || !message.has("id") || message.has("method")) return;
        String requestKey = serverRequestRouteKey(message.opt("id"));
        if (requestKey.isEmpty()) return;
        String primaryStartThread = pendingPrimaryTurnStartRequestThreads.remove(requestKey);
        if (primaryStartThread != null) {
            scheduleDeferredTaskCompletionCheck(primaryStartThread);
        }
        String compactThread = pendingCompactRequestThreads.remove(requestKey);
        if (compactThread == null) return;
        if (message.has("error")) {
            compactionTurnTracker.requestFailed(compactThread);
            maybeFinalizeDeferredTaskCompletion(compactThread);
        } else {
            compactionTurnTracker.requestSucceeded(
                compactThread, System.currentTimeMillis());
            scheduleDeferredTaskCompletionCheck(
                compactThread, COMPACTION_TURN_BIND_WINDOW_MS + 250L);
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

    private void repairAuxiliaryActiveTurn(String sourceThread, String auxiliaryTurn) {
        synchronized (nativeRouteDeliveryLock) {
            if (sourceThread == null || !sourceThread.equals(threadId)
                    || auxiliaryTurn == null || !auxiliaryTurn.equals(activeTurnId)) return;
            String primaryTurn = compactionTurnTracker.primaryTurnFor(sourceThread, auxiliaryTurn);
            activeTurnId = !primaryTurn.isEmpty()
                    && turnStartedAtMs.containsKey(turnKey(sourceThread, primaryTurn))
                ? primaryTurn : null;
            activeTurnConfirmedPrimary = activeTurnId != null;
        }
    }

    private void emitMainTurnStarted(String sourceThread, String turnId, JSONObject turn) {
        String key = turnKey(sourceThread, turnId);
        if (!emittedTurnStartedTurns.add(key)) return;
        JSONObject params = turnLifecycleParams(sourceThread, turnId, turn);
        emitProtocolLifecycleEvent("turnStarted", params, turn);
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
            CompactionTurnTracker tracker, JSONObject params) {
        return tracker != null && params != null && tracker.isAuxiliaryTurn(
            protocolThreadId(params), protocolTurnId(params));
    }

    private boolean isExactActivePrimaryTurn(String sourceThread, String turnId) {
        if (sourceThread == null || sourceThread.isEmpty() || turnId == null || turnId.isEmpty()) {
            return false;
        }
        synchronized (nativeRouteDeliveryLock) {
            return sourceThread.equals(threadId) && sourceThread.equals(visibleThreadId)
                && turnId.equals(activeTurnId)
                && !compactionTurnTracker.isAuxiliaryTurn(sourceThread, turnId);
        }
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

    private String activeTurnForThread(String sourceThread) {
        synchronized (nativeRouteDeliveryLock) {
            return sourceThread != null && sourceThread.equals(threadId) && activeTurnId != null
                ? activeTurnId : "";
        }
    }

    private String compactionPrimaryTurnHint(
            String sourceThread, String candidateTurn, long nowMs) {
        String completedPrimary = primaryCompletionTracker.primaryTurnHint(
            sourceThread, candidateTurn, nowMs);
        synchronized (nativeRouteDeliveryLock) {
            if (sourceThread != null && sourceThread.equals(threadId)
                    && activeTurnId != null && !activeTurnId.isEmpty()) {
                if (activeTurnId.equals(candidateTurn) && !activeTurnConfirmedPrimary
                        && !completedPrimary.isEmpty()) {
                    return completedPrimary;
                }
                return activeTurnId;
            }
        }
        return completedPrimary;
    }

    private boolean hasPendingPrimaryTurnStart(String sourceThread) {
        if (sourceThread == null || sourceThread.isEmpty()) return false;
        for (String pendingThread : pendingPrimaryTurnStartRequestThreads.values()) {
            if (sourceThread.equals(pendingThread)) return true;
        }
        return false;
    }

    private void promoteProvisionalTurnStart(String sourceThread, String turnId) {
        if (sourceThread == null || sourceThread.isEmpty() || turnId == null || turnId.isEmpty()) {
            return;
        }
        ProvisionalTurnStart provisional = provisionalTurnStarts.get(sourceThread);
        if (provisional == null || !turnId.equals(provisional.turnId)) return;
        synchronized (nativeRouteDeliveryLock) {
            if (!sourceThread.equals(threadId) || !sourceThread.equals(visibleThreadId)
                    || !turnId.equals(activeTurnId)) return;
            activeTurnConfirmedPrimary = true;
        }
        if (!provisionalTurnStarts.remove(sourceThread, provisional)) return;
        primaryCompletionTracker.clear(sourceThread);
        deferredTaskCompletions.remove(sourceThread);
        emitMainTurnStarted(sourceThread, turnId, provisional.turn);
    }

    private boolean rememberActiveTurn(String sourceThread, String turn,
                                       boolean confirmedPrimaryStart) {
        synchronized (nativeRouteDeliveryLock) {
            if (sourceThread != null && sourceThread.equals(threadId)
                    && sourceThread.equals(visibleThreadId)) {
                if (activeTurnId != null && !activeTurnId.isEmpty()
                        && !activeTurnId.equals(turn) && !confirmedPrimaryStart
                        && turnStartedAtMs.containsKey(turnKey(sourceThread, activeTurnId))) {
                    Log.w(TAG, "Ignoring unverified turn replacement active=" + shortId(activeTurnId)
                        + " candidate=" + shortId(turn));
                    return false;
                }
                activeTurnId = turn;
                activeTurnConfirmedPrimary = confirmedPrimaryStart;
                if (confirmedPrimaryStart) {
                    primaryCompletionTracker.clear(sourceThread);
                    provisionalTurnStarts.remove(sourceThread);
                    deferredTaskCompletions.remove(sourceThread);
                }
                return true;
            }
        }
        return false;
    }

    /** Retained for focused delivery tests that model an ordinary unconfirmed notification. */
    private boolean rememberActiveTurn(String sourceThread, String turn) {
        return rememberActiveTurn(sourceThread, turn, false);
    }

    private void clearActiveTurnIfMatches(String sourceThread, String turn) {
        synchronized (nativeRouteDeliveryLock) {
            if (sourceThread != null && sourceThread.equals(threadId)
                    && turn != null && turn.equals(activeTurnId)) {
                activeTurnId = null;
                activeTurnConfirmedPrimary = false;
            }
        }
    }

    private void scheduleTurnStallDiagnostics(String thread, String turn) {
        final String key = turnKey(thread, turn);
        for (long thresholdMs : new long[]{60_000L, 180_000L}) {
            mainHandler.postDelayed(() -> {
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

    private static void findSessionFiles(File directory, java.util.Set<String> threadIds, java.util.Map<String, File> result) {
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

    private static String resolveConversationProject(File sessionFile) {
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

    static java.util.Map<String, String> resolveConversationProjects(java.util.Collection<String> requestedThreadIds) {
        java.util.LinkedHashSet<String> threadIds = new java.util.LinkedHashSet<>();
        if (requestedThreadIds != null) for (String threadId : requestedThreadIds) {
            if (threadId != null && !threadId.isEmpty()) threadIds.add(threadId);
        }
        java.util.Map<String, String> projects = new java.util.HashMap<>();
        if (threadIds.isEmpty()) return projects;

        File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
        java.util.Map<String, File> files = new java.util.HashMap<>();
        findSessionFiles(sessionsRoot, threadIds, files);
        for (java.util.Map.Entry<String, File> entry : files.entrySet()) {
            String project = resolveConversationProject(entry.getValue());
            if (!project.isEmpty()) projects.put(entry.getKey(), project);
        }
        return projects;
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

    private static int appendHistoricalProcess(
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
                    String planText = normalizedHistoricalPlanText(extractAgentMessageText(historicalPlanItem));
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

    /** Split persisted plan-mode output into normal assistant text and plan Markdown.
     *
     * Keep history on the same stateful scanner as the live stream. The old implementation used
     * one closed-block regex, which leaked tags when a provider persisted an unclosed block and
     * could not recognize the newer <plan> spelling.
     */
    static JSONArray splitHistoricalAssistantContent(String value) throws Exception {
        JSONArray parts = new JSONArray();
        java.util.List<NativePlanContentPart> scanned = NativePlanStreamParser.splitComplete(value);
        for (NativePlanContentPart part : scanned) {
            appendHistoricalContentPart(parts, part.getRole(), part.getText());
        }
        return parts;
    }

    /** Return a completed dedicated Plan item from rollout history, across app-server schemas. */
    private static JSONObject completedHistoricalPlanItem(JSONObject record, JSONObject payload) {
        if (record == null || payload == null) return null;
        String recordType = normalizeItemType(record.optString("type", ""));
        String payloadType = normalizeItemType(payload.optString("type", payload.optString("item_type", "")));
        JSONObject item = payload.optJSONObject("item");
        if (item == null && "responseitem".equals(recordType) && "plan".equals(payloadType)) item = payload;
        if (item == null) return null;
        String itemType = normalizeItemType(item.optString("type", item.optString("item_type", "")));
        if (!"plan".equals(itemType)) return null;

        // response_item/plan is already a terminal persisted item. event_msg histories also
        // contain item_started records, which must not create a second partial card.
        if ("responseitem".equals(recordType)) return item;
        String status = normalizeItemType(item.optString("status", payload.optString("status", "")));
        boolean completed = payloadType.contains("completed") || payloadType.contains("complete")
            || "completed".equals(status) || "complete".equals(status);
        return completed ? item : null;
    }

    private static String normalizedHistoricalPlanText(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return "";
        StringBuilder plan = new StringBuilder();
        for (NativePlanContentPart part : NativePlanStreamParser.splitComplete(text)) {
            if ("plan".equals(part.getRole())) plan.append(part.getText());
        }
        return plan.length() > 0 ? plan.toString().trim() : text;
    }

    private static boolean historicalPlansEquivalent(String left, String right) {
        String a = normalizedHistoricalPlanForComparison(left);
        String b = normalizedHistoricalPlanForComparison(right);
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static boolean historicalPlanMirror(String assistantText, String planText) {
        String assistant = normalizedHistoricalPlanForComparison(assistantText);
        String plan = normalizedHistoricalPlanForComparison(planText);
        return !assistant.isEmpty() && assistant.equals(plan);
    }

    private static String normalizedHistoricalPlanForComparison(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ")
            .toLowerCase(java.util.Locale.ROOT);
    }

    private static void appendHistoricalProposedPlan(JSONArray messages, String text, String itemId) throws Exception {
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

    private static String historicalProtocolItemId(JSONObject item) {
        if (item == null) return "";
        String value = item.optString("id", "").trim();
        if (value.isEmpty()) value = item.optString("itemId", "").trim();
        if (value.isEmpty()) value = item.optString("item_id", "").trim();
        return value;
    }

    private static JSONObject historicalCompactionItem(JSONObject record, JSONObject payload, long recordAtMs) throws Exception {
        if (payload == null) return null;
        JSONObject item = payload.optJSONObject("item");
        if (item == null) item = payload;
        String type = normalizeItemType(item.optString("type", item.optString("item_type", payload.optString("type", ""))));
        String method = normalizeItemType(payload.optString("method", payload.optString("event", record.optString("type", ""))))
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
        String normalizedStatus = normalizeItemType(rawStatus);
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

    private static String encodeHistoricalCompaction(JSONObject item) {
        return "COMPACTION|" + android.util.Base64.encodeToString(
            item.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
    }

    private static void appendHistoricalContentPart(JSONArray parts, String role, String value) throws Exception {
        String text = value == null ? "" : value.trim();
        if (!text.isEmpty()) parts.put(new JSONObject().put("role", role).put("content", text));
    }

    /** Convert pure parsed parts to the transport format consumed by NativeChatState. */
    static void appendHistoricalAssistantContent(JSONArray messages, String value) throws Exception {
        appendHistoricalAssistantContent(messages, value, "", "");
    }

    private static void appendHistoricalAssistantContent(JSONArray messages, String value,
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

    /** Normalize both JSON function arguments and raw custom-tool input into one history shape. */
    static JSONObject historicalToolArguments(Object value) throws Exception {
        if (value instanceof JSONObject) {
            try { return new JSONObject(value.toString()); }
            catch (Exception ignored) { return new JSONObject(); }
        }
        String raw = value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
        try { return new JSONObject(raw); }
        catch (Exception ignored) { return new JSONObject().put("raw", raw); }
    }

    /** Custom tool outputs are persisted as content-part arrays, unlike legacy string outputs. */
    static String historicalToolOutput(Object value) {
        StringBuilder output = new StringBuilder();
        appendHistoricalToolOutput(value, output);
        return output.toString().trim();
    }

    private static void appendHistoricalToolOutput(Object value, StringBuilder output) {
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

    private static JSONObject historyUserMessage(String value, JSONArray skills, JSONArray attachments) throws Exception {
        String text = normalizeHistoricalUserText(value);
        JSONObject message = new JSONObject().put("role", "user").put("content", text);
        if (skills != null && skills.length() > 0) message.put("skills", skills);
        if (attachments != null && attachments.length() > 0) message.put("attachments", attachments);
        return message;
    }

    /** event_msg/user_message is the app-server's authoritative transcript entry. Raw
     * response_item user messages also contain injected environment/goal context, so use
     * them only as a source of attachment and skill metadata. */
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
        if (!text.startsWith(IMPLEMENT_PLAN_PROMPT_PREFIX)) return text;
        String plan = text.substring(IMPLEMENT_PLAN_PROMPT_PREFIX.length()).trim();
        String encoded = NativeBase64.encode(plan.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return IMPLEMENT_PLAN_DISPLAY_PREFIX + encoded;
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

    private static String shortId(String value) {
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

    private void completeVisibleTurnFromIdle(JSONObject params) {
        if (!isPrimaryEvent(params)) return;
        String statusThread = params.optString("threadId", "");
        String turn = activeTurnId;
        if (turn == null || statusThread.isEmpty() || !statusThread.equals(threadId)) return;
        String key = turnKey(statusThread, turn);
        // thread/status/changed=idle is transport-level state, not a turn terminal barrier. It can
        // occur between a primary turn and a dedicated compaction/continuation turn. Closing here
        // races the authoritative turn/completed notification and interrupts otherwise live work.
        if (turnStartedAtMs.containsKey(key)) {
            Log.i(TAG, "Observed thread idle while awaiting authoritative completion turn="
                + shortId(turn));
        }
    }

    private void clearPendingTurn(JSONObject params) {
        String key = turnKey(params);
        if (key == null) return;
        turnStartedAtMs.remove(key);
        emittedTurnStartedTurns.remove(key);
        JSONObject turn = params == null ? null : params.optJSONObject("turn");
        String completedTurn = turn == null ? params.optString("turnId", "") : turn.optString("id", "");
        String completedThread = params == null ? "" : params.optString("threadId", "");
        ProvisionalTurnStart provisional = provisionalTurnStarts.get(completedThread);
        if (provisional != null && completedTurn.equals(provisional.turnId)) {
            provisionalTurnStarts.remove(completedThread, provisional);
        }
        clearActiveTurnIfMatches(completedThread, completedTurn);
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
        String requestKey = serverRequestRouteKey(request.opt("id"));
        if ("turn/start".equals(method) && !requestedThread.isEmpty() && !requestKey.isEmpty()) {
            pendingPrimaryTurnStartRequestThreads.put(requestKey, requestedThread);
        } else if ("thread/compact/start".equals(method) && !requestedThread.isEmpty()) {
            String primaryTurn = "";
            synchronized (nativeRouteDeliveryLock) {
                if (requestedThread.equals(threadId) && activeTurnId != null) {
                    primaryTurn = activeTurnId;
                }
            }
            compactionTurnTracker.requestStarted(
                requestedThread, primaryTurn, System.currentTimeMillis());
            scheduleDeferredTaskCompletionCheck(
                requestedThread, COMPACTION_TURN_BIND_WINDOW_MS + 250L);
            if (!requestKey.isEmpty()) pendingCompactRequestThreads.put(requestKey, requestedThread);
        }
        String title = extractTaskTitle(params);
        if ("turn/start".equals(method) && !requestedThread.isEmpty()) {
            CodexTaskStore.markRunning(appContext, requestedThread, title);
        } else if ("thread/start".equals(method) && desktopBridge != null) {
            int requestId = request.optInt("id", -1);
            if (requestId >= 0) pendingPrimaryThreadTitles.put(requestId, title);
        }
    }

    private void forgetPrimaryRequest(JSONObject request) {
        if (request == null) return;
        String requestKey = serverRequestRouteKey(request.opt("id"));
        if (!requestKey.isEmpty()) {
            String primaryStartThread = pendingPrimaryTurnStartRequestThreads.remove(requestKey);
            if (primaryStartThread != null) maybeFinalizeDeferredTaskCompletion(primaryStartThread);
            String compactThread = pendingCompactRequestThreads.remove(requestKey);
            if (compactThread != null) {
                compactionTurnTracker.requestFailed(compactThread);
                maybeFinalizeDeferredTaskCompletion(compactThread);
            }
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

    private boolean isPrimaryEvent(JSONObject params) {
        return shouldAcceptVisibleProtocolEvent(
            params, visibleRouteReady || canBufferVisibleRouteEvents(), visibleThreadId, activeTurnId);
    }

    private boolean isPrimaryEvent(String method, JSONObject params) {
        return shouldAcceptVisibleProtocolEvent(
            params,
            visibleRouteReady || canBufferVisibleRouteEvents(),
            visibleThreadId,
            activeTurnId,
            isContextCompactionSignal(method, params)
        );
    }

    private boolean shouldAcceptNotificationForRoute(
            String method, JSONObject params, NativeRouteEventGate.RouteToken routeToken) {
        if (routeToken == null) return false;
        boolean mayBufferLoadingRoute = canBufferVisibleRouteEvents(
            webView == null,
            eventListener != null,
            routeToken.getSourceThreadId(),
            routeToken.isReady());
        return shouldAcceptVisibleProtocolEvent(
            params,
            routeToken.isReady() || mayBufferLoadingRoute,
            routeToken.getSourceThreadId(),
            activeTurnId,
            isContextCompactionSignal(method, params));
    }

    private void emitVisibleRouteEventIfAccepted(JSONObject params, String function, String value) {
        NativeRouteEventGate.RouteToken routeToken;
        synchronized (nativeRouteDeliveryLock) {
            routeToken = nativeRouteEventGate.captureRouteToken(protocolThreadId(params));
            if (!shouldAcceptNotificationForRoute("", params, routeToken)) return;
        }
        emitForRoute(routeToken, function, value);
    }

    private void emitVisibleProtocolLifecycleIfAccepted(
            String kind, JSONObject params, JSONObject details) {
        if (webView != null || params == null) return;
        try {
            JSONObject payload = new JSONObject()
                .put("kind", kind)
                .put("threadId", protocolThreadId(params))
                .put("turnId", protocolTurnId(params))
                .put("itemId", protocolItemId(params))
                .put("sequence", nativeProtocolSequence.incrementAndGet())
                .put("timestampMs", System.currentTimeMillis());
            if (details != null) payload.put("details", details);
            emitVisibleRouteEventIfAccepted(params, "onProtocolEvent", payload.toString());
        } catch (Exception ignored) {}
    }

    /** Blocking server requests without a thread id may only bind to a proven active turn. */
    static boolean hasVerifiableRequestRoute(JSONObject params, String activeTurn) {
        if (params == null) return false;
        if (!protocolThreadId(params).isEmpty()) return true;
        String candidateTurn = protocolTurnId(params);
        return !candidateTurn.isEmpty() && activeTurn != null && candidateTurn.equals(activeTurn);
    }

    private static boolean hasAnyRequestRouteIdentity(JSONObject params) {
        return params != null
            && (!protocolThreadId(params).isEmpty() || !protocolTurnId(params).isEmpty());
    }

    private NativeRouteEventGate.RouteToken captureBlockingRequestRoute(JSONObject params) {
        synchronized (nativeRouteDeliveryLock) {
            if (!hasVerifiableRequestRoute(params, activeTurnId)) return null;
            NativeRouteEventGate.RouteToken routeToken =
                nativeRouteEventGate.captureRouteToken(protocolThreadId(params));
            if (!nativeRouteEventGate.isCurrent(routeToken)) return null;
            if (webView != null) {
                return routeToken;
            }
            return shouldAcceptNotificationForRoute("", params, routeToken) ? routeToken : null;
        }
    }

    private void rememberServerRequestRoute(Object requestId,
                                            NativeRouteEventGate.RouteToken routeToken,
                                            JSONObject params, String method) {
        String key = serverRequestRouteKey(requestId);
        if (key.isEmpty()) return;
        synchronized (pendingServerRequestRoutes) {
            if (ambiguousServerRequestRouteKeys.contains(key)) return;
            PendingServerRequestRoute existing = pendingServerRequestRoutes.get(key);
            if (routeToken == null) {
                if (existing != null) {
                    if (!existing.method.equals(method == null ? "" : method)
                            || !existing.matchesResolution(params)) {
                        pendingServerRequestRoutes.remove(key);
                        ambiguousServerRequestRouteKeys.add(key);
                    }
                } else if (!seenServerRequestRouteKeys.add(key)) {
                    ambiguousServerRequestRouteKeys.add(key);
                }
                return;
            }
            PendingServerRequestRoute candidate = new PendingServerRequestRoute(routeToken, params, method);
            if (existing == null && !seenServerRequestRouteKeys.add(key)) {
                ambiguousServerRequestRouteKeys.add(key);
                Log.w(TAG, "Reused server request id; visible resolution will require route identity");
                return;
            }
            if (existing == null) {
                pendingServerRequestRoutes.put(key, candidate);
            } else if (!existing.sameIdentity(candidate)) {
                pendingServerRequestRoutes.remove(key);
                ambiguousServerRequestRouteKeys.add(key);
                Log.w(TAG, "Ambiguous server request id; visible resolution will require route identity");
            }
        }
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

    private NativeRouteEventGate.RouteToken routeForServerRequestResponse(
            Object requestId, JSONObject requestParams) {
        PendingServerRequestRoute pending = pendingServerRequestRoutes.get(
            serverRequestRouteKey(requestId));
        if (pending != null && pending.matchesResolution(requestParams)) {
            synchronized (nativeRouteDeliveryLock) {
                if (nativeRouteEventGate.isCurrent(pending.routeToken)) return pending.routeToken;
            }
            NativeRouteEventGate.RouteToken recaptured = captureBlockingRequestRoute(requestParams);
            return recaptured == null ? pending.routeToken : recaptured;
        }
        return captureBlockingRequestRoute(requestParams);
    }

    private void retireServerRequestRoute(Object requestId) {
        String key = serverRequestRouteKey(requestId);
        if (key.isEmpty()) return;
        PendingServerRequestRoute removed = pendingServerRequestRoutes.remove(key);
        if (removed != null) scheduleDeferredTaskCompletionCheck(removed.sourceThreadId);
    }

    /** Emits with the route captured at source instead of re-binding a late event to the UI now. */
    private void emitForRoute(NativeRouteEventGate.RouteToken routeToken,
                              String function, String value) {
        if (routeToken == null) return;
        NativeRouteEventGate.RouteToken previous = nativeRouteEmission.get();
        nativeRouteEmission.set(routeToken);
        try {
            emit(function, value);
        } finally {
            if (previous == null) nativeRouteEmission.remove();
            else nativeRouteEmission.set(previous);
        }
    }

    private void emitNativeErrorForRoute(NativeRouteEventGate.RouteToken routeToken,
                                         String message) {
        String safeMessage = message == null ? "Native request failed" : message;
        if (routeToken == null) {
            emit("onNativeError", safeMessage);
            return;
        }
        if (webView != null) {
            emitForRoute(routeToken, "onNativeError", safeMessage);
            return;
        }
        synchronized (nativeRouteDeliveryLock) {
            if (!nativeRouteEventGate.isCurrent(routeToken)) return;
            enqueueAdmittedNativeEventLocked("onNativeError", safeMessage, routeToken);
        }
    }

    /**
     * A resumed native conversation has a visible thread identity before its disk history is
     * ready.  Accepting that thread's protocol events during the gap lets emit() queue them for
     * replay instead of silently dropping the live answer.
     */
    private boolean canBufferVisibleRouteEvents() {
        return canBufferVisibleRouteEvents(
            webView == null, eventListener != null, visibleThreadId, visibleRouteReady);
    }

    static boolean canBufferVisibleRouteEvents(boolean nativeUi, boolean listenerAttached,
                                                String visibleThread, boolean routeReady) {
        return nativeUi && listenerAttached && visibleThread != null
            && !visibleThread.isEmpty() && !routeReady;
    }

    private NativeRouteEventGate.RouteToken resetVisibleRoute(String thread, boolean ready) {
        synchronized (nativeRouteDeliveryLock) {
            if (visibleThreadId != null && !visibleThreadId.equals(thread)) {
                provisionalTurnStarts.remove(visibleThreadId);
            }
            threadId = thread;
            visibleThreadId = thread;
            visibleRouteReady = ready;
            activeTurnId = null;
            activeTurnConfirmedPrimary = false;
            nativeRouteEventGate.resetRoute(navigationGeneration.get(), thread, ready);
            // Keep an already queued/running drain as the scheduler owner. Its extracted route-A
            // events fail the token check; discard only queued routed events so a global backend
            // failure remains visible without creating a reset-vs-running-drain missed wakeup.
            nativeStreamBatcher.clearRoutedEvents();
            return nativeRouteEventGate.captureRouteToken(thread);
        }
    }

    private void markVisibleRouteReadyAndReplay(NativeRouteEventGate.RouteToken routeToken,
                                                NativeHistorySnapshot historySnapshot) {
        if (webView != null) {
            synchronized (nativeRouteDeliveryLock) {
                if (nativeRouteEventGate.isCurrent(routeToken)) visibleRouteReady = true;
            }
            return;
        }
        synchronized (nativeRouteDeliveryLock) {
            if (!nativeRouteEventGate.isCurrent(routeToken)) return;
            java.util.List<NativeRouteEventGate.Event> pending = nativeRouteEventGate.markReadyAndReplay(
                routeToken, historySnapshot == null ? java.util.Collections.emptySet()
                    : historySnapshot.getCompletedProtocolItemIds(),
                historyTailAssistantText(historySnapshot));
            visibleRouteReady = true;
            replayVisibleRouteEventsLocked(routeToken, pending);
        }
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

    private void failOpenVisibleRoute(NativeRouteEventGate.RouteToken routeToken) {
        if (webView != null) return;
        synchronized (nativeRouteDeliveryLock) {
            if (!nativeRouteEventGate.isCurrent(routeToken)) return;
            java.util.List<NativeRouteEventGate.Event> pending = nativeRouteEventGate.failOpen(routeToken);
            visibleRouteReady = true;
            replayVisibleRouteEventsLocked(routeToken, pending);
        }
    }

    /** Caller holds nativeRouteDeliveryLock so a newly admitted live event cannot overtake replay. */
    private void replayVisibleRouteEventsLocked(NativeRouteEventGate.RouteToken routeToken,
                                                java.util.List<NativeRouteEventGate.Event> pending) {
        if (!pending.isEmpty()) {
            try {
                NativeChatDiagnostics.record(appContext, "route_events_replayed", new JSONObject()
                    .put("thread", shortId(routeToken.getSourceThreadId()))
                    .put("generation", routeToken.getNavigationGeneration())
                    .put("events", pending.size()));
            } catch (Exception ignored) {}
        }
        for (NativeRouteEventGate.Event event : pending) enqueueAdmittedNativeEventLocked(
            event.function, event.value, routeToken);
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

    /**
     * Context compaction may be represented by a dedicated server turn even while the user's
     * primary turn is still active. It still belongs to the visible thread and must reach the
     * compaction reducer; ordinary reasoning/tool events from another turn remain rejected.
     */
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

    private static boolean isContextCompactionSignal(String method, JSONObject params) {
        if (isContextCompactionItem(params)) return true;
        String normalized = normalizeProtocolName(method);
        return normalized.contains("contextcompaction") || normalized.contains("contextcompacted");
    }

    private boolean isPrimaryTurn(JSONObject params) {
        if (params == null) return false;
        String candidate = params.optString("threadId", params.optString("thread_id", ""));
        if (candidate.isEmpty()) {
            JSONObject turn = params.optJSONObject("turn");
            if (turn != null) candidate = turn.optString("threadId", turn.optString("thread_id", ""));
        }
        if (candidate.isEmpty()) {
            JSONObject item = params.optJSONObject("item");
            if (item != null) candidate = item.optString("threadId", item.optString("thread_id", item.optString("senderThreadId", item.optString("sender_thread_id", ""))));
        }
        return !candidate.isEmpty() && (primaryThreadIds.contains(candidate) || candidate.equals(threadId));
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

    private boolean hasPendingContinuation(String sourceThread) {
        if (sourceThread == null || sourceThread.isEmpty()) return false;
        if (nativeContinuationPendingThreads.contains(sourceThread)
                || compactionTurnTracker.hasPendingOrActiveCompaction(
                    sourceThread, System.currentTimeMillis())) return true;
        for (String pendingThread : pendingPrimaryTurnStartRequestThreads.values()) {
            if (sourceThread.equals(pendingThread)) return true;
        }
        for (NativeRouteEventGate.RouteToken route : pendingSteerRequestRoutes.values()) {
            if (route != null && sourceThread.equals(route.getSourceThreadId())) return true;
        }
        for (PendingServerRequestRoute request : pendingServerRequestRoutes.values()) {
            if (request != null && sourceThread.equals(request.sourceThreadId)) return true;
        }
        return false;
    }

    private void recordOrFinalizeTaskCompletion(
            String completedThread, String completedTurnId, boolean failed,
            boolean hasPendingContinuation) {
        long nowMs = System.currentTimeMillis();
        DeferredTaskCompletion completion = new DeferredTaskCompletion(
            completedThread, completedTurnId, failed, nowMs + TASK_COMPLETION_SETTLE_MS);
        deferredTaskCompletions.put(completedThread, completion);
        scheduleDeferredTaskCompletionCheck(completedThread, TASK_COMPLETION_SETTLE_MS);
        if (hasPendingContinuation) {
            try {
                NativeChatDiagnostics.record(appContext, "turn_completion_deferred", new JSONObject()
                    .put("thread", shortId(completedThread)).put("turn", shortId(completedTurnId))
                    .put("failed", failed));
            } catch (Exception ignored) {}
            scheduleDeferredTaskCompletionCheck(
                completedThread, COMPACTION_TURN_BIND_WINDOW_MS + 250L);
            return;
        }
    }

    private void maybeFinalizeDeferredTaskCompletion(String sourceThread) {
        if (sourceThread == null || sourceThread.isEmpty()) return;
        DeferredTaskCompletion completion = deferredTaskCompletions.get(sourceThread);
        if (completion == null) return;
        long nowMs = System.currentTimeMillis();
        if (nowMs < completion.notBeforeMs) {
            scheduleDeferredTaskCompletionCheck(
                sourceThread, Math.max(1L, completion.notBeforeMs - nowMs));
            return;
        }
        synchronized (nativeRouteDeliveryLock) {
            if (sourceThread.equals(threadId) && activeTurnId != null && !activeTurnId.isEmpty()) {
                if (!activeTurnId.equals(completion.turnId) && activeTurnConfirmedPrimary) {
                    // A newer primary turn now owns task state; its own completion will finalize it.
                    deferredTaskCompletions.remove(sourceThread, completion);
                }
                return;
            }
        }
        if (hasPendingContinuation(sourceThread)
                || !deferredTaskCompletions.remove(sourceThread, completion)) return;
        finalizeTaskCompletion(completion);
    }

    private void scheduleDeferredTaskCompletionCheck(String sourceThread) {
        scheduleDeferredTaskCompletionCheck(sourceThread, 150L);
    }

    private void scheduleDeferredTaskCompletionCheck(String sourceThread, long delayMs) {
        if (sourceThread == null || sourceThread.isEmpty()) return;
        // Let the routed resolution callback update Activity continuation state first. The check
        // then observes the native hint instead of racing a rejected steer being queued locally.
        mainHandler.postDelayed(() -> {
            synchronized (CodexAppServerBridge.this) {
                maybeFinalizeDeferredTaskCompletion(sourceThread);
            }
        }, Math.max(0L, delayMs));
    }

    private void finalizeTaskCompletion(DeferredTaskCompletion completion) {
        CodexTaskStore.markCompleted(appContext, completion.threadId, completion.failed);
        try {
            NativeChatDiagnostics.record(appContext, "turn_complete", new JSONObject()
                .put("thread", shortId(completion.threadId)).put("turn", shortId(completion.turnId))
                .put("failed", completion.failed));
        } catch (Exception ignored) {}
        notifyTaskCompleted(completion.threadId, completion.failed, completion.turnId);
    }

    private void notifyTaskCompleted(String completedThread, boolean failed, String token) {
        NativeTaskNotificationManager.notifyEvent(appContext, completedThread,
            failed ? NativeTaskNotificationPolicy.FAILED : NativeTaskNotificationPolicy.COMPLETED,
            token, "");
        Activity boundActivity = boundActivity();
        if (boundActivity instanceof CodexHomeActivity) {
            mainHandler.post(((CodexHomeActivity) boundActivity)::onCodexTaskCompleted);
        } else {
            mainHandler.post(() -> CodexOverlayService.notifyTaskCompleted(appContext));
        }
    }

    private static boolean isHighFrequencyNotification(String method) {
        return "item/agentMessage/delta".equals(method)
            || "item/plan/delta".equals(method)
            || "item/reasoning/summaryTextDelta".equals(method)
            || "item/reasoning/textDelta".equals(method)
            || "item/commandExecution/outputDelta".equals(method);
    }

    private static boolean isHighFrequencyEmission(String function) {
        return "onDelta".equals(function) || "onPlanDelta".equals(function)
            || "onReasoningDelta".equals(function) || "onCommandDelta".equals(function)
            || "onCommandDeltaV2".equals(function) || "onProtocolDelta".equals(function);
    }

    private void dispatchNativeEvent(String function, String value,
                                     NativeRouteEventGate.RouteToken routeToken) {
        if (routeToken == null) {
            EventListener listener = eventListener;
            if (listener != null) listener.onEvent(function, value);
            return;
        }
        EventListener listener;
        synchronized (nativeRouteDeliveryLock) {
            if (!nativeRouteEventGate.isCurrent(routeToken)) return;
            listener = eventListener;
        }
        // Do not hold the delivery lock across application callbacks; the route check above is
        // the linearization point and avoids lock-order inversions with synchronized RPC methods.
        if (listener != null) listener.onEvent(function, value);
    }

    private void dispatchNativeEvent(String function, String value) {
        dispatchNativeEvent(function, value, null);
    }

    private void dispatchNativeEvents(java.util.List<NativeStreamEventBatcher.Event> events) {
        for (NativeStreamEventBatcher.Event event : events) {
            dispatchNativeEvent(event.function, event.value, event.routeToken);
        }
    }

    private void drainNativeStreamEvents() {
        java.util.List<NativeStreamEventBatcher.Event> events = nativeStreamBatcher.drain();
        try {
            dispatchNativeEvents(events);
        } finally {
            // scheduled stays true for both queued and actively running drains. An event offered
            // during dispatch therefore cannot be stranded: either this recheck schedules it, or
            // its producer wins the same false->true CAS after the flag is cleared.
            nativeStreamDrainScheduled.set(false);
            if (!nativeStreamBatcher.isEmpty()
                    && nativeStreamDrainScheduled.compareAndSet(false, true)) {
                mainHandler.post(nativeStreamDrain);
            }
        }
    }

    private NativeRouteEventGate.RouteToken routeTokenForEmission() {
        NativeRouteEventGate.RouteToken captured = nativeRouteEmission.get();
        return captured != null ? captured : nativeRouteEventGate.captureRouteToken();
    }

    /** Queue an event already admitted by NativeRouteEventGate. */
    private void enqueueAdmittedNativeEventLocked(String function, String value,
                                                  NativeRouteEventGate.RouteToken routeToken) {
        boolean urgent;
        if (isHighFrequencyEmission(function)) {
            urgent = nativeStreamBatcher.offer(function, value, routeToken);
        } else {
            // Lifecycle/completion/error events are FIFO barriers and must never merge with one
            // another or let a later delta cross them.
            nativeStreamBatcher.offerSeparate(function, value, routeToken);
            urgent = true;
        }
        if (nativeStreamDrainScheduled.compareAndSet(false, true)) {
            mainHandler.postDelayed(nativeStreamDrain, urgent ? 0L : NATIVE_STREAM_BATCH_MS);
        }
    }

    private void emit(String function, String value) {
        if (!isHighFrequencyEmission(function)) {
            android.util.Log.d(TAG, "EMIT function=" + function + " length=" + (value == null ? 0 : value.length()));
        }
        final String safeValue = value == null ? "" : value;
        if (webView == null) {
            synchronized (nativeRouteDeliveryLock) {
                if (isVisibleRouteFunction(function)) {
                    NativeRouteEventGate.RouteToken routeToken = routeTokenForEmission();
                    NativeRouteEventGate.Admission admission = nativeRouteEventGate.offer(
                        routeToken, function, safeValue);
                    if (admission != NativeRouteEventGate.Admission.DISPATCH_NOW) return;
                    enqueueAdmittedNativeEventLocked(function, safeValue, routeToken);
                } else {
                    // Backend/history failures are deliberately global and immediate: a closed
                    // route gate must never hide the explanation for an otherwise blank screen.
                    enqueueAdmittedNativeEventLocked(function, safeValue, null);
                }
            }
            return;
        }

        final String quoted = JSONObject.quote(safeValue);
        final NativeRouteEventGate.RouteToken routeToken = nativeRouteEmission.get();
        mainHandler.post(() -> {
            if (routeToken != null && !nativeRouteEventGate.isCurrent(routeToken)) return;
            if (eventListener != null) eventListener.onEvent(function, safeValue);
            webView.evaluateJavascript(
                "window.codex && window.codex." + function + "(" + quoted + ")", null);
        });
    }

    synchronized void stop() {
        // Invalidate a bootstrap thread before tearing down its shared handles. Without this,
        // a slow ProcessBuilder.start() from the previous configuration can publish an old
        // process after a new start() has already completed.
        serverGeneration.incrementAndGet();
        resetVisibleRoute(null, false);
        initializeRequestId = -1;
        mcpStatusRequestId = -1;
        appServerInitialized = false;
        deferredResumeThreadId = null;
        deferredResumeGeneration = -1;
        synchronized (nativeRouteDeliveryLock) {
            mainHandler.removeCallbacks(nativeStreamDrain);
            nativeStreamDrainScheduled.set(false);
            nativeStreamBatcher.clear();
        }
        turnStartedAtMs.clear();
        emittedTurnStartedTurns.clear();
        streamedAgentItemIds.clear();
        pendingGoalGetRequests.clear();
        pendingNavigationGenerations.clear();
        pendingNavigationMethods.clear();
        pendingPrimaryTurnStartRequestThreads.clear();
        pendingCompactRequestThreads.clear();
        pendingSteerRequestRoutes.clear();
        pendingServerRequestRoutes.clear();
        seenServerRequestRouteKeys.clear();
        ambiguousServerRequestRouteKeys.clear();
        autoClearingCompletedGoalThreads.clear();
        nativeContinuationPendingThreads.clear();
        deferredTaskCompletions.clear();
        provisionalTurnStarts.clear();
        compactionTurnTracker.reset();
        primaryCompletionTracker.reset();
        compactRequestId = -1;
        compactRequestThreadId = null;
        compactNativeRequestId = null;
        try { if (writer != null) writer.close(); } catch (Exception ignored) {}
        writer = null;
        if (process != null) process.destroy();
        process = null;
        if (apiProxy != null) apiProxy.stop();
        apiProxy = null;
    }
}

