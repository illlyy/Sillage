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

    static final String TAG = "IlyopCodexBridge";
    static final java.util.regex.Pattern RECORD_TIMESTAMP_PATTERN = java.util.regex.Pattern.compile(
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
            this.turnId = CodexAppServerBridgeProtocol.protocolTurnId(params);
            this.method = method == null ? "" : method;
        }

        boolean sameIdentity(PendingServerRequestRoute other) {
            return other != null && routeToken != null && routeToken.belongsToSameRoute(other.routeToken)
                && sourceThreadId.equals(other.sourceThreadId)
                && turnId.equals(other.turnId)
                && method.equals(other.method);
        }

        boolean matchesResolution(JSONObject params) {
            return CodexAppServerBridgeProtocol.requestRouteIdentityMatches(sourceThreadId, turnId, params);
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
            String key = CodexAppServerBridgeProtocol.turnKey(sourceThread, turnId);
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
                && primaryTurnByAuxiliaryTurn.containsKey(CodexAppServerBridgeProtocol.turnKey(sourceThread, turnId));
        }

        synchronized String primaryTurnFor(String sourceThread, String turnId) {
            if (sourceThread == null || turnId == null) return "";
            return primaryTurnByAuxiliaryTurn.getOrDefault(CodexAppServerBridgeProtocol.turnKey(sourceThread, turnId), "");
        }

        synchronized boolean complete(String sourceThread, String turnId) {
            if (sourceThread == null || turnId == null) return false;
            String key = CodexAppServerBridgeProtocol.turnKey(sourceThread, turnId);
            if (!primaryTurnByAuxiliaryTurn.containsKey(key)) return false;
            terminalAuxiliaryTurnDeadlines.remove(key);
            completedAuxiliaryTurns.add(key);
            return true;
        }

        synchronized boolean observeLifecycleTerminal(
                String sourceThread, String turnId, long nowMs) {
            if (sourceThread == null || sourceThread.isEmpty()
                    || turnId == null || turnId.isEmpty()) return false;
            String key = CodexAppServerBridgeProtocol.turnKey(sourceThread, turnId);
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
            CodexAppServerBridgeProtocol.logModelSelection(request);
            rememberPrimaryRequest(request);
            CodexAppServerBridgeProtocol.applyAndroidProviderOverrides(request);
            sendJson(request);
        }
        catch (Exception e) {
            forgetPrimaryRequest(request);
            if (desktopBridge != null) desktopBridge.onAppServerError(request.opt("id"), e.getMessage());
        }
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
                CodexAppServerBridgeProtocol.purgeStaleAgentsMaxThreads(new File(codexHome, "config.toml"));
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
                for (String override : CodexAppServerBridgeProtocol.agentConfigOverrides(
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


    @JavascriptInterface public void newConversation() {
        newConversationAtCwd(null);
    }

    void newConversationAtCwd(String requestedCwd) {
        int generation = navigationGeneration.incrementAndGet();
        clearDeferredResume();
        resetVisibleRoute(null, false);
        NativeChatDiagnostics.record(appContext, "conversation_route", CodexAppServerBridgeProtocol.navigationDetails(generation, "new"));
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
        NativeChatDiagnostics.record(appContext, "conversation_route", CodexAppServerBridgeProtocol.navigationDetails(generation, CodexAppServerBridgeProtocol.shortId(requestedThread)));
        final AtomicBoolean historyDeliveryResolved = new AtomicBoolean(false);
        final NativeHistorySnapshot emptySnapshot = NativeHistoryParser.parse(new JSONArray());
        final Runnable historyTimeout = scheduleHistoryLoadTimeout(
            requestedThread, generation, routeToken, historyDeliveryResolved, emptySnapshot,
            allowMissingRollout);
        new Thread(() -> {
            final long historyStartedAt = android.os.SystemClock.uptimeMillis();
            try {
                File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
                File sessionFile = CodexAppServerBridgeHistory.findSessionFile(sessionsRoot, requestedThread);
                JSONArray history = sessionFile == null ? new JSONArray() : CodexAppServerBridgeHistory.readConversationHistory(sessionFile);
                NativeHistorySnapshot historySnapshot = NativeHistoryParser.parse(history);
                NativeChatDiagnostics.record(appContext, "history_prepared", CodexAppServerBridgeProtocol.navigationDetails(generation, CodexAppServerBridgeProtocol.shortId(requestedThread))
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
                    CodexAppServerBridgeProtocol.navigationDetails(generation, CodexAppServerBridgeProtocol.shortId(requestedThread))
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
                CodexAppServerBridgeProtocol.navigationDetails(generation, CodexAppServerBridgeProtocol.shortId(requestedThread)));
            emitForRoute(routeToken, "onReady", requestedThread);
            return;
        }
        try {
            if (deferResumeUntilInitialized(requestedThread, generation)) return;
            JSONObject resumeParams = new JSONObject().put("threadId", requestedThread);
            CodexAppServerBridgeProtocol.applyNativeMcpConfig(resumeParams);
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
                File sessionFile = CodexAppServerBridgeHistory.findSessionFile(sessionsRoot, subagentThreadId);
                JSONArray messages = sessionFile == null ? new JSONArray() : CodexAppServerBridgeHistory.readConversationHistory(sessionFile);
                result.put("threadId", subagentThreadId);
                result.put("generation", generation);
                result.put("messages", messages);
                result.put("found", sessionFile != null);
                result.put("status", CodexAppServerBridgeProtocol.subagentSessionStatus(sessionFile));
                NativeChatDiagnostics.record(appContext, "subagent_history", new JSONObject()
                    .put("thread", CodexAppServerBridgeProtocol.shortId(subagentThreadId))
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


    void loadSkills() {
        new Thread(() -> {
            JSONArray result = new JSONArray();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            CodexAppServerBridgeProtocol.scanSkillDirectory(new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "skills"), result, seen, 0);
            CodexAppServerBridgeProtocol.scanSkillDirectory(new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".agents"), "skills"), result, seen, 0);
            emit("onSkills", result.toString());
        }, "CodexSkillScanner").start();
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
            pendingCompactRequestThreads.remove(CodexAppServerBridgeProtocol.serverRequestRouteKey(compactRequestId));
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
        if (!CodexAppServerBridgeProtocol.isCompletedGoal(goal)) {
            autoClearingCompletedGoalThreads.remove(goalThread);
        } else if (autoClearingCompletedGoalThreads.add(goalThread)) {
            try {
                sendRequest("thread/goal/clear", new JSONObject().put("threadId", goalThread));
            } catch (Exception error) {
                autoClearingCompletedGoalThreads.remove(goalThread);
                Log.w(TAG, "Unable to clear completed thread goal for " + CodexAppServerBridgeProtocol.shortId(goalThread), error);
            }
        }
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
            if (requestRoute == null && CodexAppServerBridgeProtocol.hasAnyRequestRouteIdentity(requestParams)) {
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
            if (requestRoute == null && CodexAppServerBridgeProtocol.hasAnyRequestRouteIdentity(requestParams)) {
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
        CodexAppServerBridgeProtocol.applyNativeMcpConfig(params);
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
        android.util.Log.d(TAG, "SEND " + CodexAppServerBridgeProtocol.messageSummary(message));
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



    private void readStdout(Process activeProcess, int generation) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!isCurrentServerProcess(activeProcess, generation)) return;
                JSONObject message = new JSONObject(line);
                if (!CodexAppServerBridgeProtocol.isHighFrequencyNotification(message.optString("method", ""))) {
                    android.util.Log.d(TAG, "RECV " + CodexAppServerBridgeProtocol.messageSummary(message));
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
                String safeLine = CodexAppServerBridgeProtocol.redactSensitiveLogLine(line);
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
                String requestedThread = CodexAppServerBridgeProtocol.protocolThreadId(inboundParams);
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
                String requestedThread = CodexAppServerBridgeProtocol.protocolThreadId(inboundParams);
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
                CodexAppServerBridgeProtocol.applyNativeMcpConfig(resumeParams);
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
            String summary = CodexAppServerBridgeProtocol.mcpStatusSummary(message);
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
                Log.w(TAG, "Unable to read thread goal for " + CodexAppServerBridgeProtocol.shortId(goalThread) + ": "
                    + message.optJSONObject("error").optString("message", "unknown error"));
                return;
            }
            if (CodexAppServerBridgeProtocol.isMcpAvailabilityError(message)) {
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
            android.util.Log.i(TAG, "COLLAB_MODES " + CodexAppServerBridgeProtocol.redactSensitiveLogLine(String.valueOf(message.opt("result"))));
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
                        .put("thread", CodexAppServerBridgeProtocol.shortId(resultThreadId)).put("generation", responseGeneration));
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
            nativeRouteEventGate.captureRouteToken(CodexAppServerBridgeProtocol.protocolThreadId(params));
        boolean primaryEvent = shouldAcceptNotificationForRoute(method, params, notificationRoute);
        nativeRouteEmission.remove();
        if (primaryEvent) nativeRouteEmission.set(notificationRoute);
        try {
        CodexAppServerBridgeProtocol.logCollabAgentEvent(method, params);
        if ("thread/goal/updated".equals(method) && params != null) {
            publishNativeGoalState(params.optString("threadId", ""), params.optJSONObject("goal"));
            return;
        }
        if ("thread/goal/cleared".equals(method) && params != null) {
            String clearedThread = params.optString("threadId", "");
            autoClearingCompletedGoalThreads.remove(clearedThread);
            if (desktopBridge == null && CodexAppServerBridgeProtocol.isVisibleThreadEvent(params, visibleThreadId)) {
                emit("onGoalCleared", new JSONObject().put("threadId", clearedThread).toString());
            }
            return;
        }
        if ("serverRequest/resolved".equals(method) && params != null) {
            String requestKey = CodexAppServerBridgeProtocol.serverRequestRouteKey(params.opt("requestId"));
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
                .put("thread", CodexAppServerBridgeProtocol.shortId(ignoredThread))
                .put("visibleThread", CodexAppServerBridgeProtocol.shortId(visibleThreadId))
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
                .put("method", method).put("thread", CodexAppServerBridgeProtocol.shortId(params.optString("threadId", ""))));
            emit("onPlanUpdated", params.toString());
        }
        if (("item/started".equals(method) || "item/completed".equals(method)) && params != null && primaryEvent) {
            emitProtocolItemEvent(method, params);
        }
        if (primaryEvent && params != null && CodexAppServerBridgeProtocol.isContextCompactionLifecycleMethod(method)
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
                    .put("threadId", CodexAppServerBridgeProtocol.protocolThreadId(params))
                    .put("turnId", CodexAppServerBridgeProtocol.protocolTurnId(params))
                    .put("itemId", CodexAppServerBridgeProtocol.protocolItemId(params))
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
                    String normalizedStatus = CodexAppServerBridgeProtocol.normalizeProtocolName(existingStatus);
                    if (existingStatus.isEmpty() || "working".equals(normalizedStatus)
                            || "running".equals(normalizedStatus) || "inprogress".equals(normalizedStatus)) {
                        presentationItem.put("status", "done");
                    }
                }
                emit("onSubagentEvent", NativeLargePayloadStore.compactToolItem(presentationItem));
                NativeChatDiagnostics.record(appContext, "subagent_capsule", new JSONObject()
                    .put("method", method).put("type", liveType)
                    .put("thread", CodexAppServerBridgeProtocol.shortId(params.optString("threadId", ""))));
            }
        }
        if (primaryEvent && "item/plan/delta".equals(method) && params != null) {
            // Plan mode emits its final <proposed_plan> block as a dedicated Plan item,
            // not as an agentMessage. Forward that stream explicitly or the native UI
            // remains blank until it reparses the rollout after a route change.
            emitProtocolDeltaEvent("planDelta", params, params.optString("delta", ""));
            if (webView != null) emit("onPlanDelta", params.toString());
        } else if (primaryEvent && "item/started".equals(method) && CodexAppServerBridgeProtocol.isContextCompactionItem(params)) {
            // The normalized event above owns the native divider. Do not turn compaction into a
            // generic processing label/capsule.
        } else if (primaryEvent && "item/completed".equals(method) && CodexAppServerBridgeProtocol.isContextCompactionItem(params)) {
            // Completion updates the same stable divider via onProtocolEvent.
        } else if (primaryEvent && "item/started".equals(method) && CodexAppServerBridgeProtocol.isPlanItem(params)) {
            if (webView != null) emit("onPlanStarted", params.toString());
        } else if (primaryEvent && "item/completed".equals(method) && CodexAppServerBridgeProtocol.isPlanItem(params)) {
            if (webView != null) emit("onPlanComplete", params.toString());
        } else if (primaryEvent && "item/started".equals(method) && CodexAppServerBridgeProtocol.isCommandItem(params)) {
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
        } else if (primaryEvent && "item/completed".equals(method) && CodexAppServerBridgeProtocol.isReasoningItem(params)) {
            JSONObject item = params.optJSONObject("item");
            String text = CodexAppServerBridgeProtocol.extractReasoningText(item);
            if (webView != null && !text.isEmpty()) emit("onReasoningComplete", text);
        } else if (primaryEvent && "item/completed".equals(method) && CodexAppServerBridgeProtocol.isCommandItem(params)) {
            JSONObject item = params.optJSONObject("item");
            // handleNotification runs on the app-server reader thread. Strip large streams
            // here so the main thread receives only command metadata and an output reference.
            if (webView != null) emit("onCommandComplete", NativeCommandOutputStore.compactCommandItem(item));
        } else if (primaryEvent && "item/completed".equals(method) && CodexAppServerBridgeProtocol.isToolDetailItem(params)) {
            JSONObject item = params.optJSONObject("item");
            if (webView != null) emit("onToolComplete", NativeLargePayloadStore.compactToolItem(item));
        } else if (primaryEvent && "item/completed".equals(method) && CodexAppServerBridgeProtocol.isAgentMessageItem(params)) {
            JSONObject item = params == null ? null : params.optJSONObject("item");
            boolean finalAnswer = CodexAppServerBridgeProtocol.isFinalAgentMessage(params);
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
            String completedText = CodexAppServerBridgeProtocol.extractAgentMessageText(item);
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
                && "threadtokenusageupdated".equals(CodexAppServerBridgeProtocol.normalizeProtocolName(method))) {
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
        } else if ("thread/status/changed".equals(method) && CodexAppServerBridgeProtocol.isIdleThreadStatus(params)) {
            completeVisibleTurnFromIdle(params);
        } else if ("turn/completed".equals(method)) {
            String completedThread = CodexAppServerBridgeProtocol.protocolThreadId(params);
            String completedTurnId = CodexAppServerBridgeProtocol.protocolTurnId(params);
            if (CodexAppServerBridgeProtocol.isAuxiliaryCompactionTurnCompletion(compactionTurnTracker, params)) {
                clearPendingTurn(params);
                compactionTurnTracker.complete(completedThread, completedTurnId);
                NativeChatDiagnostics.record(appContext, "compaction_turn_complete", new JSONObject()
                    .put("thread", CodexAppServerBridgeProtocol.shortId(completedThread)).put("turn", CodexAppServerBridgeProtocol.shortId(completedTurnId)));
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
                emit("onTurnComplete", CodexAppServerBridgeProtocol.turnLifecycleCallbackPayload(
                    params, hasPendingContinuation));
            }
            if (exactPrimaryCompletion) {
                boolean failed = CodexAppServerBridgeProtocol.turnFailed(params);
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











    private void emitProtocolCompactionLifecycle(String method, JSONObject params) {
        if (webView != null || params == null) return;
        try {
            JSONObject copy = new JSONObject(params.toString());
            JSONObject item = copy.optJSONObject("item");
            if (item == null) {
                item = new JSONObject().put("type", "contextCompaction");
                String itemId = CodexAppServerBridgeProtocol.protocolItemId(copy);
                if (!itemId.isEmpty()) item.put("id", itemId);
                copy.put("item", item);
            } else if (item.optString("type").isEmpty()) item.put("type", "contextCompaction");
            String normalized = CodexAppServerBridgeProtocol.normalizeProtocolName(method);
            boolean completed = normalized.endsWith("completed") || normalized.endsWith("complete")
                || normalized.endsWith("failed") || normalized.endsWith("cancelled") || normalized.endsWith("canceled");
            emitProtocolItemEvent(completed ? "item/completed" : "item/started", copy);
        } catch (Exception ignored) {}
    }





    private void emitProtocolItemEvent(String method, JSONObject params) {
        if (webView != null || params == null) return;
        try {
            JSONObject item = params.optJSONObject("item");
            String itemType = item == null ? "" : item.optString("type", item.optString("item_type", ""));
            String normalizedItemType = CodexAppServerBridgeProtocol.normalizeItemType(itemType);
            String lifecycle = "item/started".equals(method) ? "started" : "completed";
            String kind;
            if (CodexAppServerBridgeProtocol.isContextCompactionItem(params)) kind = "contextCompaction" + CodexAppServerBridgeProtocol.capitalize(lifecycle);
            else if (CodexAppServerBridgeProtocol.isCommandItem(params)) kind = "command" + CodexAppServerBridgeProtocol.capitalize(lifecycle);
            else if (CodexAppServerBridgeProtocol.isReasoningItem(params)) kind = "reasoning" + CodexAppServerBridgeProtocol.capitalize(lifecycle);
            else if (CodexAppServerBridgeProtocol.isPlanItem(params)) kind = "plan" + CodexAppServerBridgeProtocol.capitalize(lifecycle);
            else if (CodexAppServerBridgeProtocol.isAgentMessageItem(params)) kind = "assistant" + CodexAppServerBridgeProtocol.capitalize(lifecycle);
            else if (item != null && ("subagentactivity".equals(normalizedItemType) || "collabagenttoolcall".equals(normalizedItemType))) kind = "subagentUpdated";
            else kind = "tool" + CodexAppServerBridgeProtocol.capitalize(lifecycle);

            JSONObject safeItem = item == null ? new JSONObject() : new JSONObject(item.toString());
            if ("commandexecution".equals(normalizedItemType) && "completed".equals(lifecycle)) {
                safeItem = new JSONObject(NativeCommandOutputStore.compactCommandItem(item));
            } else if ("completed".equals(lifecycle) && CodexAppServerBridgeProtocol.isToolDetailItem(params)) {
                safeItem = new JSONObject(NativeLargePayloadStore.compactToolItem(item));
            } else if ("agentmessage".equals(normalizedItemType)) {
                String text = CodexAppServerBridgeProtocol.extractAgentMessageText(item);
                safeItem.remove("content");
                safeItem.put("text", text);
            }
            if ("subagentactivity".equals(normalizedItemType)
                    || "collabagenttoolcall".equals(normalizedItemType)) {
                String status = safeItem.optString("status", "").trim();
                String normalizedStatus = CodexAppServerBridgeProtocol.normalizeProtocolName(status);
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
                .put("threadId", CodexAppServerBridgeProtocol.protocolThreadId(params))
                .put("turnId", CodexAppServerBridgeProtocol.protocolTurnId(params))
                .put("itemId", CodexAppServerBridgeProtocol.protocolItemId(params))
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
                .put("threadId", CodexAppServerBridgeProtocol.protocolThreadId(params))
                .put("turnId", CodexAppServerBridgeProtocol.protocolTurnId(params))
                .put("itemId", CodexAppServerBridgeProtocol.protocolItemId(params))
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
                .put("threadId", CodexAppServerBridgeProtocol.protocolThreadId(params))
                .put("turnId", CodexAppServerBridgeProtocol.protocolTurnId(params))
                .put("itemId", CodexAppServerBridgeProtocol.protocolItemId(params))
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
                .put("threadId", CodexAppServerBridgeProtocol.protocolThreadId(params))
                .put("turnId", CodexAppServerBridgeProtocol.protocolTurnId(params))
                .put("itemId", CodexAppServerBridgeProtocol.protocolItemId(params))
                .put("sequence", nativeProtocolSequence.incrementAndGet())
                .put("timestampMs", System.currentTimeMillis());
            if (details != null) payload.put("details", details);
            emit("onProtocolEvent", payload.toString());
        } catch (Exception ignored) {}
    }





    private void rememberTurnStart(JSONObject message) {
        JSONObject result = message.optJSONObject("result");
        JSONObject params = message.optJSONObject("params");
        JSONObject turn = result == null ? null : result.optJSONObject("turn");
        if (turn == null) {
            if (params != null) turn = params.optJSONObject("turn");
        }
        String method = message.optString("method", "");
        String turnId = turn == null ? CodexAppServerBridgeProtocol.protocolTurnId(params) : turn.optString("id", "");
        if (turnId.isEmpty()) turnId = CodexAppServerBridgeProtocol.protocolTurnId(params);
        String sourceThread = turn == null ? CodexAppServerBridgeProtocol.protocolThreadId(params)
            : turn.optString("threadId", turn.optString("thread_id", ""));
        if (sourceThread.isEmpty()) sourceThread = CodexAppServerBridgeProtocol.protocolThreadId(params);

        long nowMs = System.currentTimeMillis();
        String responseKey = message.has("id") && !message.has("method")
            ? CodexAppServerBridgeProtocol.serverRequestRouteKey(message.opt("id")) : "";
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

        boolean compactionSignal = CodexAppServerBridgeProtocol.isContextCompactionSignal(method, params);
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
            if (CodexAppServerBridgeProtocol.isContextCompactionTerminalSignal(method, params)
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
                + CodexAppServerBridgeProtocol.shortId(turnId));
            return;
        }
        if (auxiliaryTurn) return;

        if (!CodexAppServerBridgeProtocol.isTurnStartObservation(message, method, turn) || sourceThread.isEmpty() || turnId.isEmpty()) {
            return;
        }
        boolean confirmedPrimaryStart = primaryStartRpcResponse
            || hasPendingPrimaryTurnStart(sourceThread);
        boolean postCompletionWeakStart = !confirmedPrimaryStart
            && primaryCompletionTracker.shouldQuarantineWeakStart(
                sourceThread, turnId, nowMs);
        long startedAtSeconds = turn == null ? 0L : turn.optLong("startedAt", 0L);
        if (!rememberActiveTurn(sourceThread, turnId, confirmedPrimaryStart)) return;
        String key = CodexAppServerBridgeProtocol.turnKey(sourceThread, turnId);
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
        String requestKey = CodexAppServerBridgeProtocol.serverRequestRouteKey(message.opt("id"));
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


    private void repairAuxiliaryActiveTurn(String sourceThread, String auxiliaryTurn) {
        synchronized (nativeRouteDeliveryLock) {
            if (sourceThread == null || !sourceThread.equals(threadId)
                    || auxiliaryTurn == null || !auxiliaryTurn.equals(activeTurnId)) return;
            String primaryTurn = compactionTurnTracker.primaryTurnFor(sourceThread, auxiliaryTurn);
            activeTurnId = !primaryTurn.isEmpty()
                    && turnStartedAtMs.containsKey(CodexAppServerBridgeProtocol.turnKey(sourceThread, primaryTurn))
                ? primaryTurn : null;
            activeTurnConfirmedPrimary = activeTurnId != null;
        }
    }

    private void emitMainTurnStarted(String sourceThread, String turnId, JSONObject turn) {
        String key = CodexAppServerBridgeProtocol.turnKey(sourceThread, turnId);
        if (!emittedTurnStartedTurns.add(key)) return;
        JSONObject params = CodexAppServerBridgeProtocol.turnLifecycleParams(sourceThread, turnId, turn);
        emitProtocolLifecycleEvent("turnStarted", params, turn);
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
                        && turnStartedAtMs.containsKey(CodexAppServerBridgeProtocol.turnKey(sourceThread, activeTurnId))) {
                    Log.w(TAG, "Ignoring unverified turn replacement active=" + CodexAppServerBridgeProtocol.shortId(activeTurnId)
                        + " candidate=" + CodexAppServerBridgeProtocol.shortId(turn));
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
        final String key = CodexAppServerBridgeProtocol.turnKey(thread, turn);
        for (long thresholdMs : new long[]{60_000L, 180_000L}) {
            mainHandler.postDelayed(() -> {
                Long started = turnStartedAtMs.get(key);
                if (started == null) return;
                long elapsed = Math.max(0L, System.currentTimeMillis() - started);
                if (elapsed >= thresholdMs) Log.w(TAG, "COLLAB_STALL thread=" + CodexAppServerBridgeProtocol.shortId(thread)
                    + " turn=" + CodexAppServerBridgeProtocol.shortId(turn) + " elapsedMs=" + elapsed
                    + " activeTurns=" + turnStartedAtMs.size());
            }, thresholdMs);
        }
    }

    private void scheduleTurnRuntimeDiagnostics(String thread, String turn) {
        new Thread(() -> {
            File sessionsRoot = new File(new File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "sessions");
            for (int attempt = 0; attempt < 8; attempt++) {
                try {
                    File sessionFile = CodexAppServerBridgeHistory.findSessionFile(sessionsRoot, thread);
                    String diagnostic = sessionFile == null ? null : CodexAppServerBridgeHistory.readTurnRuntimeDiagnostic(sessionFile, turn);
                    if (diagnostic != null) {
                        Log.i(TAG, "RUNTIME thread=" + CodexAppServerBridgeProtocol.shortId(thread) + " turn=" + CodexAppServerBridgeProtocol.shortId(turn) + " " + diagnostic);
                        return;
                    }
                    Thread.sleep(500L);
                } catch (Exception error) {
                    Log.w(TAG, "Unable to read turn runtime diagnostics", error);
                    return;
                }
            }
            Log.w(TAG, "RUNTIME unavailable thread=" + CodexAppServerBridgeProtocol.shortId(thread) + " turn=" + CodexAppServerBridgeProtocol.shortId(turn));
        }, "CodexTurnDiagnostics").start();
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
        CodexAppServerBridgeHistory.findSessionFiles(sessionsRoot, threadIds, files);
        for (java.util.Map.Entry<String, File> entry : files.entrySet()) {
            String project = CodexAppServerBridgeHistory.resolveConversationProject(entry.getValue());
            if (!project.isEmpty()) projects.put(entry.getKey(), project);
        }
        return projects;
    }






    /** Split persisted plan-mode output into normal assistant text and plan Markdown.
     *
     * Keep history on the same stateful scanner as the live stream. The old implementation used
     * one closed-block regex, which leaked tags when a provider persisted an unclosed block and
     * could not recognize the newer <plan> spelling.
     */

    /** Return a completed dedicated Plan item from rollout history, across app-server schemas. */










    /** Convert pure parsed parts to the transport format consumed by NativeChatState. */




    /** Normalize both JSON function arguments and raw custom-tool input into one history shape. */

    /** Custom tool outputs are persisted as content-part arrays, unlike legacy string outputs. */




    /** event_msg/user_message is the app-server's authoritative transcript entry. Raw
     * response_item user messages also contain injected environment/goal context, so use
     * them only as a source of attachment and skill metadata. */









    private void completeVisibleTurnFromIdle(JSONObject params) {
        if (!isPrimaryEvent(params)) return;
        String statusThread = params.optString("threadId", "");
        String turn = activeTurnId;
        if (turn == null || statusThread.isEmpty() || !statusThread.equals(threadId)) return;
        String key = CodexAppServerBridgeProtocol.turnKey(statusThread, turn);
        // thread/status/changed=idle is transport-level state, not a turn terminal barrier. It can
        // occur between a primary turn and a dedicated compaction/continuation turn. Closing here
        // races the authoritative turn/completed notification and interrupts otherwise live work.
        if (turnStartedAtMs.containsKey(key)) {
            Log.i(TAG, "Observed thread idle while awaiting authoritative completion turn="
                + CodexAppServerBridgeProtocol.shortId(turn));
        }
    }

    private void clearPendingTurn(JSONObject params) {
        String key = CodexAppServerBridgeProtocol.turnKey(params);
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





    private void rememberPrimaryRequest(JSONObject request) {
        String method = request.optString("method", "");
        JSONObject params = request.optJSONObject("params");
        if (params == null) return;
        String requestedThread = params.optString("threadId", params.optString("thread_id", ""));
        if (!requestedThread.isEmpty()) primaryThreadIds.add(requestedThread);
        String requestKey = CodexAppServerBridgeProtocol.serverRequestRouteKey(request.opt("id"));
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
        String title = CodexAppServerBridgeProtocol.extractTaskTitle(params);
        if ("turn/start".equals(method) && !requestedThread.isEmpty()) {
            CodexTaskStore.markRunning(appContext, requestedThread, title);
        } else if ("thread/start".equals(method) && desktopBridge != null) {
            int requestId = request.optInt("id", -1);
            if (requestId >= 0) pendingPrimaryThreadTitles.put(requestId, title);
        }
    }

    private void forgetPrimaryRequest(JSONObject request) {
        if (request == null) return;
        String requestKey = CodexAppServerBridgeProtocol.serverRequestRouteKey(request.opt("id"));
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



    private boolean isPrimaryEvent(JSONObject params) {
        return CodexAppServerBridgeProtocol.shouldAcceptVisibleProtocolEvent(
            params, visibleRouteReady || canBufferVisibleRouteEvents(), visibleThreadId, activeTurnId);
    }

    private boolean isPrimaryEvent(String method, JSONObject params) {
        return CodexAppServerBridgeProtocol.shouldAcceptVisibleProtocolEvent(
            params,
            visibleRouteReady || canBufferVisibleRouteEvents(),
            visibleThreadId,
            activeTurnId,
            CodexAppServerBridgeProtocol.isContextCompactionSignal(method, params)
        );
    }

    private boolean shouldAcceptNotificationForRoute(
            String method, JSONObject params, NativeRouteEventGate.RouteToken routeToken) {
        if (routeToken == null) return false;
        boolean mayBufferLoadingRoute = CodexAppServerBridgeProtocol.canBufferVisibleRouteEvents(
            webView == null,
            eventListener != null,
            routeToken.getSourceThreadId(),
            routeToken.isReady());
        return CodexAppServerBridgeProtocol.shouldAcceptVisibleProtocolEvent(
            params,
            routeToken.isReady() || mayBufferLoadingRoute,
            routeToken.getSourceThreadId(),
            activeTurnId,
            CodexAppServerBridgeProtocol.isContextCompactionSignal(method, params));
    }

    private void emitVisibleRouteEventIfAccepted(JSONObject params, String function, String value) {
        NativeRouteEventGate.RouteToken routeToken;
        synchronized (nativeRouteDeliveryLock) {
            routeToken = nativeRouteEventGate.captureRouteToken(CodexAppServerBridgeProtocol.protocolThreadId(params));
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
                .put("threadId", CodexAppServerBridgeProtocol.protocolThreadId(params))
                .put("turnId", CodexAppServerBridgeProtocol.protocolTurnId(params))
                .put("itemId", CodexAppServerBridgeProtocol.protocolItemId(params))
                .put("sequence", nativeProtocolSequence.incrementAndGet())
                .put("timestampMs", System.currentTimeMillis());
            if (details != null) payload.put("details", details);
            emitVisibleRouteEventIfAccepted(params, "onProtocolEvent", payload.toString());
        } catch (Exception ignored) {}
    }

    /** Blocking server requests without a thread id may only bind to a proven active turn. */


    private NativeRouteEventGate.RouteToken captureBlockingRequestRoute(JSONObject params) {
        synchronized (nativeRouteDeliveryLock) {
            if (!CodexAppServerBridgeProtocol.hasVerifiableRequestRoute(params, activeTurnId)) return null;
            NativeRouteEventGate.RouteToken routeToken =
                nativeRouteEventGate.captureRouteToken(CodexAppServerBridgeProtocol.protocolThreadId(params));
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
        String key = CodexAppServerBridgeProtocol.serverRequestRouteKey(requestId);
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



    private NativeRouteEventGate.RouteToken routeForServerRequestResponse(
            Object requestId, JSONObject requestParams) {
        PendingServerRequestRoute pending = pendingServerRequestRoutes.get(
            CodexAppServerBridgeProtocol.serverRequestRouteKey(requestId));
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
        String key = CodexAppServerBridgeProtocol.serverRequestRouteKey(requestId);
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
        return CodexAppServerBridgeProtocol.canBufferVisibleRouteEvents(
            webView == null, eventListener != null, visibleThreadId, visibleRouteReady);
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
                CodexAppServerBridgeHistory.historyTailAssistantText(historySnapshot));
            visibleRouteReady = true;
            replayVisibleRouteEventsLocked(routeToken, pending);
        }
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
                    .put("thread", CodexAppServerBridgeProtocol.shortId(routeToken.getSourceThreadId()))
                    .put("generation", routeToken.getNavigationGeneration())
                    .put("events", pending.size()));
            } catch (Exception ignored) {}
        }
        for (NativeRouteEventGate.Event event : pending) enqueueAdmittedNativeEventLocked(
            event.function, event.value, routeToken);
    }


    /**
     * Context compaction may be represented by a dedicated server turn even while the user's
     * primary turn is still active. It still belongs to the visible thread and must reach the
     * compaction reducer; ordinary reasoning/tool events from another turn remain rejected.
     */



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
                    .put("thread", CodexAppServerBridgeProtocol.shortId(completedThread)).put("turn", CodexAppServerBridgeProtocol.shortId(completedTurnId))
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
                .put("thread", CodexAppServerBridgeProtocol.shortId(completion.threadId)).put("turn", CodexAppServerBridgeProtocol.shortId(completion.turnId))
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
        if (CodexAppServerBridgeProtocol.isHighFrequencyEmission(function)) {
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
        if (!CodexAppServerBridgeProtocol.isHighFrequencyEmission(function)) {
            android.util.Log.d(TAG, "EMIT function=" + function + " length=" + (value == null ? 0 : value.length()));
        }
        final String safeValue = value == null ? "" : value;
        if (webView == null) {
            synchronized (nativeRouteDeliveryLock) {
                if (CodexAppServerBridgeProtocol.isVisibleRouteFunction(function)) {
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

