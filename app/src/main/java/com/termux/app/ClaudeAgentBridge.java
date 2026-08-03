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

    // Readiness for a fresh stream-json session is NOT gated on session_id: the CLI emits none
    // until the first user message. When routed through the loopback proxy, the CLI's health
    // probe (HEAD /api/hello) is the readiness signal; a direct connection falls back to a boot
    // grace timer. A very long notice-only guard nudges a CLI that hangs before reaching the probe.
    private static final long READY_FALLBACK_MS = 4_000L;
    private static final long INIT_NOTICE_MS = 120_000L;
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
    private String modelOverride = "";
    // Process working directory for the spawned CLI. A fresh conversation created from a project
    // folder runs there (so Bash/Read/Grep operate on the project); resume keeps the previous
    // home-based behavior until a project is explicitly selected.
    private volatile String launchCwd = "";
    private final AtomicInteger generation = new AtomicInteger();

    // startup diagnostics: whether a stale resume id was dropped so the runtime never restarts
    // the degraded path twice; the first stderr line is read lazily from claude-stderr.log.
    private volatile boolean resumeDegraded = false;

    // Thread id adopted before the CLI echoes its authoritative session id (which, for a fresh
    // session, only arrives with the first user-message line). Always the --session-id uuid or
    // --resume id the bridge passed, so goals/history route correctly from the start.
    private volatile String provisionalSessionId = "";

    // Exit-reporting policy: a deliberate stop (re-spawn/switch) must never surface as an error;
    // a real exit while a live turn is open seals the partial answer and reports the true cause
    // once. Per-outbound-turn flags reset when a new user message begins.
    private volatile boolean stopRequested = false;
    private volatile boolean turnResultSeen = false;
    private volatile boolean lastResultFailed = false;
    private volatile String activeOutboundTurnId = "";
    private volatile boolean spawnInProgress = false;

    // The musl CLI cannot use Android's system proxy and its own TLS/DNS stack often cannot
    // reach the API directly. A loopback proxy (Java HttpURLConnection) forwards to the real
    // base URL, exactly like the Codex bridge, so Claude reuses Java's network stack.
    private LocalApiProxy claudeProxy;
    private String proxyBaseUrl;

    // conversation state
    private volatile String sessionId;
    private volatile boolean ready;
    private final ClaudeEventDecoder decoder;
    private final AtomicLong nativeSequence = new AtomicLong();

    // control channel correlation
    private final ConcurrentHashMap<String, String> pendingApprovalToolUseIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingUserInputToolUseIds = new ConcurrentHashMap<>();

    private final Runnable readinessFallbackRunnable = this::markReady;
    private final Runnable initNoticeRunnable = () -> {
        if (!ready && running) {
            Log.w(TAG, "Claude CLI still initializing after " + INIT_NOTICE_MS + "ms");
            emit("onHistoryWarning", "Claude CLI 仍在初始化，请检查网络连接。");
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

    /**
     * Marks the bridge ready exactly once: emits onReady with the current thread id and restores
     * the stored goal. Triggered by the CLI's health probe (routed through the proxy), the
     * direct-connection fallback timer, or defensively when the CLI echoes an authoritative
     * session id. A fresh stream-json session emits no session_id until the first user message,
     * so readiness is deliberately NOT gated on session_id.
     */
    private void markReady() {
        if (ready || !running) return;
        // Before the CLI echoes its authoritative session id, fall back to the provisional id
        // (the --session-id/--resume value) so onReady carries a stable thread id from the start.
        String thread = sessionId == null ? provisionalSessionId : sessionId;
        if (thread.isEmpty()) return;
        ready = true;
        mainHandler.removeCallbacks(readinessFallbackRunnable);
        mainHandler.removeCallbacks(initNoticeRunnable);
        emit("onReady", thread);
        restoreClientGoal(thread);
    }

    // ------------------------------------------------------------------ lifecycle

    synchronized void start(
            String claudeBinPath,
            String configDir,
            ClaudeProfile profile,
            String permissionMode,
            String allowedTools,
            String resumeThreadId,
            boolean routeThroughMihomo,
            String modelOverride) {
        this.claudeBinPath = claudeBinPath;
        this.configDir = configDir;
        this.profile = profile;
        this.permissionMode = permissionMode == null ? "default" : permissionMode;
        this.allowedTools = allowedTools == null ? "" : allowedTools;
        this.resumeThreadId = resumeThreadId == null ? "" : resumeThreadId;
        this.routeThroughMihomo = routeThroughMihomo;
        this.modelOverride = modelOverride == null ? "" : modelOverride;
        spawn();
    }

    private void spawn() {
        stopInternal();
        generation.incrementAndGet();
        ready = false;
        sessionId = null;
        provisionalSessionId = "";
        decoder.reset("");
        // Resume hardening: a stale/ghost resume id (failed init, cross-backend residue) makes
        // the CLI print "No conversation found with session ID: ..." and exit immediately. Verify
        // the transcript exists before passing --resume, and fall back to a fresh session instead
        // of hanging for the init timeout with a misleading error.
        if (!resumeThreadId.isEmpty() && !resumeTargetExists()) {
            Log.w(TAG, "Resume target missing, starting a fresh session instead of " + resumeThreadId);
            resumeThreadId = "";
            resumeDegraded = true;
        }
        boolean routedThroughProxy = false;
        proxyBaseUrl = null;
        if (claudeProxy != null) {
            claudeProxy.stop();
            claudeProxy = null;
        }
        try {
            // Route the CLI through the loopback proxy unless it already points at one. The
            // anthropic wire format is passed through untouched (only /responses is adapted).
            if (profile != null && profile.getBaseUrl() != null && !profile.getBaseUrl().isEmpty()) {
                String realBase = profile.getBaseUrl();
                if (!realBase.startsWith("http://127.0.0.1") && !realBase.startsWith("http://localhost")) {
                    LocalApiProxy proxy = new LocalApiProxy(realBase, "anthropic",
                        routeThroughMihomo, MihomoManager.get(appContext).mixedPort());
                    try {
                        int port = proxy.start();
                        claudeProxy = proxy;
                        proxyBaseUrl = "http://127.0.0.1:" + port;
                        routedThroughProxy = true;
                        // A fresh stream-json session emits no session_id until the first user
                        // message, so the CLI's health probe is the readiness signal when routed
                        // through the proxy. The callback fires on the proxy worker thread;
                        // markReady runs on the main thread after spawn() completes (running=true).
                        proxy.setOnHealthProbe(() -> mainHandler.post(ClaudeAgentBridge.this::markReady));
                    } catch (Exception e) {
                        Log.w(TAG, "Claude proxy failed to start, falling back to direct connection", e);
                        proxy.stop();
                    }
                }
            }
        } catch (Throwable ignored) {}
        try {
            // The official arm64 binary is dynamically linked against musl: exec it through the
            // musl loader (`ld-musl-aarch64.so.1 <binary> ...`) when its ELF carries PT_INTERP.
            // An npm-installed CLI is a node script (shebang): run it through node instead —
            // bun-based musl binaries hang on Android during initialization.
            File binary = new File(claudeBinPath);
            java.util.ArrayList<String> command = new java.util.ArrayList<>();
            if (ClaudeAgentBridge.isNodeScript(binary)) {
                File node = new File(com.termux.shared.termux.TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "node");
                if (!node.isFile()) {
                    throw new IllegalStateException(
                        "Claude CLI 需要 Node.js 运行时，请先在设置中安装开发工具 Node.js");
                }
                command.add(node.getAbsolutePath());
            } else if (ClaudeMuslRuntime.INSTANCE.needsMuslLoader(binary)) {
                if (!ClaudeMuslRuntime.INSTANCE.isLoaderPresent()) {
                    // Bundle the loader from assets automatically; no re-download needed.
                    ClaudeMuslRuntime.INSTANCE.installFromAssets(appContext);
                }
                File loader = ClaudeMuslRuntime.INSTANCE.loaderFile();
                if (!loader.isFile()) {
                    throw new IllegalStateException(
                        "Claude CLI 需要 musl 运行时，请在设置中重新下载 Claude CLI");
                }
                command.add(loader.getAbsolutePath());
            }
            command.add(claudeBinPath);
            command.add("--output-format");
            command.add("stream-json");
            command.add("--input-format");
            command.add("stream-json");
            // Emit per-token stream_event/content_block_delta lines during generation. Without it
            // the CLI only writes complete system/assistant messages, so the decoder's whole-block
            // fallback surfaces the entire thinking/body at once (no streaming).
            command.add("--include-partial-messages");
            command.add("--verbose");
            command.add("--permission-prompt-tool");
            command.add("stdio");
            command.add("--permission-mode");
            command.add(permissionMode);            if (!allowedTools.isEmpty()) {
                command.add("--allowedTools");
                command.add(allowedTools);
            }
            // Runtime chat model selection rides on --model. Belt-and-suspenders: the env
            // ANTHROPIC_MODEL override is written into settings.json below too, for CLIs that
            // honor the session's stored model over the flag on resume. Emitted only when the
            // selection differs from the profile primary, so a default spawn never carries it.
            command.addAll(modelArgs(modelOverride, profile == null ? "" : profile.getModel()));
            // The provisional thread id doubles as the CLI's session id: --resume for a retained
            // conversation, else a fresh --session-id uuid the CLI echoes back unchanged on the
            // first user-message line, so goals/history route correctly from the start.
            boolean resuming = !resumeThreadId.isEmpty();
            String freshUuid = resuming ? "" : UUID.randomUUID().toString();
            provisionalSessionId = chooseSessionId(resumeThreadId, freshUuid);
            if (resuming) {
                command.add("--resume=" + resumeThreadId);
            } else {
                command.add("--session-id");
                command.add(freshUuid);
            }
            ProcessBuilder builder = new ProcessBuilder(command);
            // The working directory must exist before fork: Android returns ENOENT from
            // forkAndExec when the cwd is missing (bootstrap may not be extracted yet).
            File home = new File(termuxHome());
            home.mkdirs();
            File processDir = home;
            if (!launchCwd.isEmpty()) {
                File candidate = new File(launchCwd);
                if (candidate.isDirectory() || candidate.mkdirs()) {
                    processDir = candidate;
                }
            }
            builder.directory(processDir);
            // Keep stderr in a file as well as the log pipe so a stalled CLI can be inspected
            // after the fact (e.g. bun runtime initialization hangs on Android).
            try {
                File tmpDir = new File(home, ".tmp");
                if (tmpDir.isDirectory() || tmpDir.mkdirs()) {
                    builder.redirectError(new File(tmpDir, "claude-stderr.log"));
                }
            } catch (Throwable ignored) {}
            // Credentials, base URL and models live in CLAUDE_CONFIG_DIR/settings.json
            // (written by ClaudeSettingsWriter, same shape as desktop cc-switch). Nothing
            // credential-like is passed on the command line.
            if (configDir != null && !configDir.isEmpty()) {
                ClaudeSettingsWriter.INSTANCE.write(new File(configDir), profile, proxyBaseUrl, modelOverride);
            }
            java.util.Map<String, String> env = builder.environment();
            // Bun (the claude runtime) resolves the home dir via $HOME (uv_os_homedir) and
            // fails with ENOENT when it is unset, as Android app processes are.
            env.put("HOME", home.getAbsolutePath());
            env.put("PWD", processDir.getAbsolutePath());
            env.put("TMPDIR", new File(home, ".tmp").getAbsolutePath());
            env.put("CLAUDE_CODE_ENTRYPOINT", "android-fcode");
            env.put("CLAUDE_AGENT_SDK_SKIP_VERSION_CHECK", "1");
            if (configDir != null && !configDir.isEmpty()) env.put("CLAUDE_CONFIG_DIR", configDir);
            // The CLI's child processes (Bash tool, MCP stdio servers) need the Termux prefix on
            // PATH and LD_LIBRARY_PATH; Android app processes inherit a minimal default PATH.
            env.put("PATH", com.termux.shared.termux.TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin:/system/xbin");
            env.put("LD_LIBRARY_PATH", com.termux.shared.termux.TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
            env.put("PREFIX", com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH);
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
            // sessionId stays null until the CLI echoes its authoritative id (the first
            // user-message line on a fresh session; the init message on a resume). Deliberately
            // NOT pre-set to the provisional id: a non-null sessionId would make currentThreadId()
            // return it and trigger a redundant bridge re-spawn on every re-attach.
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
            BufferedReader stdout = new BufferedReader(
                new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            readerThread = new Thread(() -> readLoop(stdout), "ClaudeAgentReader");
            readerThread.setDaemon(true);
            readerThread.start();
            // stderr goes to $HOME/.tmp/claude-stderr.log (redirectError above), so it is NOT read
            // through a pipe here — calling getErrorStream() after redirectError can return
            // null/empty on Android and would NPE the reader. The first stderr line is read lazily
            // from that file when surfacing a startup failure.
            stopRequested = false;
            turnResultSeen = false;
            lastResultFailed = false;
            activeOutboundTurnId = "";
            running = true;
            sendControl("req_init", new JSONObject().put("subtype", "initialize").put("hooks", new JSONObject()));
            mainHandler.removeCallbacks(readinessFallbackRunnable);
            mainHandler.removeCallbacks(initNoticeRunnable);
            if (routedThroughProxy) {
                // Readiness = the CLI's health probe observed by the proxy (precise). A long,
                // notice-only guard nudges a CLI that hangs before reaching the probe, without
                // killing a healthy process that is simply waiting for its first message.
                mainHandler.postDelayed(initNoticeRunnable, INIT_NOTICE_MS);
            } else {
                // Direct connection: the probe isn't observable, so assume ready after the boot grace.
                mainHandler.postDelayed(readinessFallbackRunnable, READY_FALLBACK_MS);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to spawn claude", e);
            emit("onNativeError", "无法启动 Claude CLI: " + e.getMessage());
        }
    }

    /** True when the CLI entry is a text script with a shebang (npm-installed node CLI). */
    static boolean isNodeScript(File binary) {
        if (binary == null || !binary.isFile() || binary.length() < 4) return false;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(binary, "r")) {
            int first = raf.read();
            int second = raf.read();
            return first == '#' && second == '!';
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Chooses the thread id for the upcoming session: the resume id when resuming a retained
     * conversation, else the fresh uuid passed to {@code --session-id}. Unit-tested so the
     * readiness/thread-id policy is documented independently of the process-level bridge.
     */
    static String chooseSessionId(String resumeThreadId, String freshSessionId) {
        return (resumeThreadId == null || resumeThreadId.isBlank()) ? freshSessionId : resumeThreadId;
    }

    /**
     * Command-line arguments that select the model: `--model <id>` when an override is active
     * and differs from the profile's primary model, empty otherwise. Unit-tested so the flag
     * construction is independent of process spawn.
     */
    static java.util.List<String> modelArgs(String modelOverride, String profilePrimaryModel) {
        if (modelOverride == null || modelOverride.isBlank()) {
            return java.util.Collections.emptyList();
        }
        if (profilePrimaryModel != null && modelOverride.equalsIgnoreCase(profilePrimaryModel)) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.asList("--model", modelOverride);
    }

    /**
     * True when a Claude transcript exists for the requested resume id. Claude stores sessions
     * both as `CLAUDE_CONFIG_DIR/sessions/<id>.jsonl` (newer) and
     * `CLAUDE_CONFIG_DIR/projects/<cwd>/<id>.jsonl` (classic layout); a stale id matches neither.
     */
    private boolean resumeTargetExists() {
        if (configDir == null || configDir.isEmpty()) return false;
        return resumeTranscriptExists(new File(configDir), resumeThreadId);
    }

    /** Static transcript lookup so the resume-hardening rule is unit-testable. */
    static boolean resumeTranscriptExists(File configDir, String sessionId) {
        if (configDir == null || sessionId == null || sessionId.isEmpty()) return false;
        if (new File(new File(configDir, "sessions"), sessionId + ".jsonl").isFile()) return true;
        File projects = new File(configDir, "projects");
        File[] projectDirs = projects.listFiles();
        if (projectDirs != null) {
            for (File dir : projectDirs) {
                if (new File(dir, sessionId + ".jsonl").isFile()) return true;
            }
        }
        return false;
    }

    private String termuxHome() {
        try {
            return com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH;
        } catch (Throwable t) {
            return "/data/data/com.ilyop.codex/files/home";
        }
    }

    /** Reads the first non-empty line of the CLI's stderr log (written via redirectError). */
    private String firstStderrLine() {
        try {
            File log = new File(new File(termuxHome(), ".tmp"), "claude-stderr.log");
            if (log.isFile()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(log), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) return line.trim();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    /** What an exited CLI should surface to the host. Null means: nothing to report. */
    static final class ExitReport {
        final boolean reportError;
        final boolean syntheticCompletion;
        ExitReport(boolean reportError, boolean syntheticCompletion) {
            this.reportError = reportError;
            this.syntheticCompletion = syntheticCompletion;
        }
    }

    /**
     * Decides how an exited CLI is surfaced. A deliberate stop (stopRequested), a turn already
     * closed by its result, or an error already surfaced by a failed result must not be re-reported.
     * A startup failure (not ready) reports without a synthetic turn; a live turn cut short gets a
     * synthetic completion so the partial answer seals without re-entering. Unit-tested.
     */
    static ExitReport decideExitReport(boolean wasRunning, boolean stopRequested, boolean ready,
            boolean turnResultSeen, boolean errorAlreadyShown, boolean turnInProgress) {
        if (!wasRunning || stopRequested) return null;
        if (turnResultSeen || errorAlreadyShown) return null;
        if (!ready || !turnInProgress) return new ExitReport(true, false);
        return new ExitReport(true, true);
    }

    private int exitValue() {
        try {
            Process current = process;
            if (current != null) return current.exitValue();
        } catch (Throwable ignored) {}
        return -1;
    }

    /** Thread id a synthetic completion must carry so the host's drain gate accepts it. */
    private String completionThread() {
        String t = sessionId == null ? provisionalSessionId : sessionId;
        return t.isEmpty() ? "claude" : t;
    }

    private String syntheticCompletionPayload() {
        try {
            return new JSONObject()
                .put("threadId", completionThread())
                .put("turnId", activeOutboundTurnId)
                .put("epoch", 0L)
                .put("turn", new JSONObject().put("hasPendingContinuation", false))
                .put("failed", true)
                .put("details", new JSONObject())
                .toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void stopInternal() {
        stopRequested = true;
        mainHandler.removeCallbacks(readinessFallbackRunnable);
        mainHandler.removeCallbacks(initNoticeRunnable);
        // Clear the running flag BEFORE destroying the process: the reader thread's finally
        // observes it to decide whether the close is a deliberate stop or an unexpected exit.
        running = false;
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
                    // Never drop the CLI's own words silently: surface non-JSON stdout too.
                    Log.d(TAG, "cli-out: " + line);
                    continue;
                }
                handleLine(message);
            }
        } catch (Exception e) {
            if (running) Log.w(TAG, "Reader stopped", e);
        } finally {
            boolean wasRunning = running;
            boolean deliberate = stopRequested;
            running = false;
            mainHandler.removeCallbacks(readinessFallbackRunnable);
            mainHandler.removeCallbacks(initNoticeRunnable);
            // A deliberate stop or a turn already closed by its result must not invent a failure.
            // Only an unexpected exit is surfaced, and a live turn gets a synthetic completion so
            // the partial answer seals without re-entering.
            ExitReport report = decideExitReport(wasRunning, deliberate, ready,
                turnResultSeen, lastResultFailed, !activeOutboundTurnId.isEmpty());
            if (report == null || !report.reportError) return;
            String stderrLine = firstStderrLine();
            String detail = stderrLine.isEmpty() ? "" : "（CLI 输出：" + stderrLine + "）";
            int exit = exitValue();
            String exitDetail = exit >= 0 ? "（退出码 " + exit + "）" : "";
            String message = "Claude CLI 进程已退出" + exitDetail + detail;
            if (report.syntheticCompletion) emit("onTurnComplete", syntheticCompletionPayload());
            emit("onNativeError", message);
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
        // A fresh session echoes its authoritative session id only on the first user-message
        // line; readiness is driven by the health probe (or the direct-connection fallback
        // timer), not by this id. Adopt the echoed id defensively — it normally equals the
        // --session-id/--resume value the bridge passed, so nothing changes.
        String observed = decoder.observeSession(message);
        if (observed != null && !observed.isEmpty() && !observed.equals(sessionId)) {
            sessionId = observed;
            // The CLI echoes its session id on the first user-message line of a fresh session.
            // Adopt it WITHOUT losing the armed outbound turn: reset() would wipe activeTurnId,
            // so the result completion would carry the session id instead of the turnStarted turn
            // id, and the host's strict turn match would reject it (stuck "generating" forever).
            decoder.adoptSession(observed);
            if (!ready) markReady();
        }
        // Track whether the current outbound turn closed via a result line: a later unexpected
        // CLI exit must not invent a second completion, and a failed result already surfaced its
        // error so the exit handler stays silent.
        if ("result".equals(type)) {
            turnResultSeen = true;
            lastResultFailed = message.optBoolean("is_error", false)
                || message.optString("subtype", "success").startsWith("error");
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
        if (text == null || text.isBlank()) return;
        if (!running) reconnectForNextTurn();
        if (!running) return;
        beginOutboundTurn(UUID.randomUUID().toString());
        String finalText = goalPrefix() + text;
        String skillHint = skillHintText(skillsJson);
        if (!skillHint.isEmpty()) finalText += "\n\n" + skillHint;
        try {
            JSONObject user = new JSONObject()
                .put("type", "user")
                .put("message", new JSONObject().put("role", "user").put("content", buildContent(finalText, attachmentsJson)));
            writeLine(user.toString());
        } catch (Exception e) {
            emit("onNativeError", "发送消息失败: " + e.getMessage());
        }
    }

    /**
     * Conveys the composer's selected skills to the Claude CLI as a text reference. Claude Code
     * discovers skills from its skills directories and loads SKILL.md on mention, so a plain
     * reference is the version-robust path (skill content blocks depend on the installed CLI).
     */
    private static String skillHintText(String skillsJson) {
        if (skillsJson == null || skillsJson.isBlank()) return "";
        try {
            JSONArray skills = new JSONArray(skillsJson);
            if (skills.length() == 0) return "";
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < skills.length(); i++) {
                JSONObject skill = skills.optJSONObject(i);
                if (skill == null) continue;
                String name = skill.optString("name");
                if (name.isBlank()) continue;
                if (names.length() > 0) names.append("、");
                names.append(name);
            }
            if (names.length() == 0) return "";
            return "请使用以下技能：" + names;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Records a new outbound turn. The same id drives the decoder (its turn completion carries it)
     * and a normalized {@code turnStarted} event, so the host's {@code currentTurnId} is set and
     * turn completion matches precisely instead of falling back to legacy heuristics.
     */
    private void beginOutboundTurn(String turnId) {
        decoder.beginTurn(turnId);
        activeOutboundTurnId = turnId;
        turnResultSeen = false;
        lastResultFailed = false;
        try {
            emit("onProtocolEvent", new JSONObject()
                .put("kind", "turnStarted")
                .put("threadId", completionThread())
                .put("turnId", turnId)
                .put("sequence", nativeSequence.incrementAndGet())
                .toString());
        } catch (Exception ignored) {}
    }

    /** Best-effort recovery after an unexpected CLI exit: inform the host and re-spawn once. */
    private void reconnectForNextTurn() {
        if (spawnInProgress) return;
        spawnInProgress = true;
        try {
            // A non-terminal notice: a full onNativeError would mark the next turn FAILED.
            emit("onHistoryWarning", "Claude CLI 已断开，正在重新连接…");
            spawn();
        } finally {
            spawnInProgress = false;
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
        if (text == null || text.isBlank()) return;
        if (!running) reconnectForNextTurn();
        if (!running) return;
        beginOutboundTurn(UUID.randomUUID().toString());
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
        // The process is already starting (or running) this session: the attach path passes the
        // retained thread at spawn, and a fresh bridge already carries it as the provisional
        // session id (currentThreadId() may expose it before the CLI echoes). Avoid a second spawn.
        if (process != null && (this.resumeThreadId.equals(target)
                || (sessionId != null && sessionId.equals(target))
                || provisionalSessionId.equals(target))) return 0;
        this.resumeThreadId = target;
        // A resume reuses the previous home-based working directory unless the caller explicitly
        // selected a project; keeping the old behavior avoids relocating existing transcripts.
        launchCwd = "";
        spawn();
        return 0;
    }

    @Override
    int restoreRetainedConversation(String resumeThreadId) {
        return resumeConversation(resumeThreadId);
    }

    @Override
    void newConversationAtCwd(String requestedCwd) {
        String cwd = requestedCwd == null ? "" : requestedCwd.trim();
        launchCwd = cwd;
        resumeThreadId = "";
        spawn();
    }

    @Override
    void loadSubagentHistory(String subagentThreadId, int generation) {
        if (subagentThreadId == null || subagentThreadId.trim().isEmpty()) return;
        final String threadId = subagentThreadId.trim();
        new Thread(() -> {
            JSONObject result = new JSONObject();
            try {
                File transcript = findSubagentTranscript(threadId);
                JSONArray messages = new JSONArray();
                if (transcript != null) {
                    StringBuilder buffer = new StringBuilder();
                    for (String line : readTailLines(transcript, 800)) {
                        if (line.isBlank()) continue;
                        JSONObject entry = runCatchingJson(line);
                        if (entry == null) continue;
                        String type = entry.optString("type");
                        if ("user".equals(type)) {
                            flushSubagentText(messages, buffer, "user");
                            String text = subagentText(entry.optJSONObject("message"));
                            if (!text.isBlank()) messages.put(new JSONObject()
                                .put("role", "user").put("content", text));
                        } else if ("assistant".equals(type)) {
                            String text = subagentText(entry.optJSONObject("message"));
                            if (!text.isBlank()) {
                                if (buffer.length() > 0) buffer.append("\n\n");
                                buffer.append(text);
                            }
                        }
                    }
                    flushSubagentText(messages, buffer, "assistant");
                }
                result.put("threadId", threadId);
                result.put("generation", generation);
                result.put("messages", messages);
                result.put("found", transcript != null);
                result.put("status", transcript != null ? "done" : "missing");
            } catch (Exception error) {
                try {
                    result.put("threadId", threadId);
                    result.put("generation", generation);
                    result.put("messages", new JSONArray());
                    result.put("status", "failed");
                    result.put("error", error.getMessage());
                } catch (Exception ignored) {}
            }
            emit("onSubagentHistory", NativeLargePayloadStore.compactSubagentHistoryResult(result));
        }, "ClaudeSubagentHistory").start();
    }

    private static void flushSubagentText(JSONArray messages, StringBuilder buffer, String role) {
        if (buffer.length() == 0) return;
        try {
            messages.put(new JSONObject().put("role", role).put("content", buffer.toString().trim()));
        } catch (Exception ignored) {}
        buffer.setLength(0);
    }

    private static String subagentText(JSONObject message) {
        if (message == null) return "";
        Object content = message.opt("content");
        if (content instanceof String) return (String) content;
        if (content instanceof JSONArray) {
            StringBuilder text = new StringBuilder();
            JSONArray blocks = (JSONArray) content;
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.optJSONObject(i);
                if (block != null && "text".equals(block.optString("type"))) text.append(block.optString("text"));
            }
            return text.toString();
        }
        return "";
    }

    private static JSONObject runCatchingJson(String line) {
        try { return new JSONObject(line); } catch (Exception e) { return null; }
    }

    private static java.util.List<String> readTailLines(File file, int maxLines) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
                if (result.size() > maxLines) result.remove(0);
            }
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Locates a Claude subagent transcript for the capsule key. The capsule is keyed by the Task
     * tool-use id, so match by the transcript's own session id, by file name, or by any line that
     * references the key (parent_tool_use_id / description echo).
     */
    private File findSubagentTranscript(String threadId) {
        try {
            if (configDir == null || configDir.isEmpty()) return null;
            java.util.ArrayList<File> candidates = new java.util.ArrayList<>();
            collectSubagentJsonl(new File(configDir, "projects"), candidates, 0);
            for (File file : candidates) {
                String id = firstSessionId(file);
                if (id != null && id.equalsIgnoreCase(threadId)) return file;
                if (file.getName().equalsIgnoreCase(threadId + ".jsonl")) return file;
            }
            for (File file : candidates) {
                if (transcriptReferences(file, threadId)) return file;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void collectSubagentJsonl(File dir, java.util.List<File> out, int depth) {
        if (depth > 8 || dir == null || !dir.isDirectory()) return;
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                if ("tool-results".equals(entry.getName())) continue;
                collectSubagentJsonl(entry, out, depth + 1);
            } else if (entry.isFile() && entry.getName().endsWith(".jsonl")
                    && entry.getParentFile() != null
                    && "subagents".equals(entry.getParentFile().getName())) {
                out.add(entry);
            }
        }
    }

    private static String firstSessionId(File file) {
        try {
            java.util.List<String> lines = readTailLines(file, 200);
            for (String line : lines) {
                if (line.isBlank()) continue;
                JSONObject entry = runCatchingJson(line);
                if (entry == null) continue;
                String id = entry.optString("sessionId");
                if (id.isEmpty()) id = entry.optString("session_id");
                if (!id.isEmpty()) return id;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean transcriptReferences(File file, String key) {
        try {
            for (String line : readTailLines(file, 400)) {
                if (line.contains(key)) return true;
            }
        } catch (Exception ignored) {}
        return false;
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
        if (claudeProxy != null) {
            claudeProxy.stop();
            claudeProxy = null;
            proxyBaseUrl = null;
        }
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
