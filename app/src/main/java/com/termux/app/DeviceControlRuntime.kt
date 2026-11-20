package com.termux.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.app.ActivityManager
import android.app.Application
import android.app.KeyguardManager
import android.os.Process
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.termux.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

internal object DeviceControlRuntime {
    private const val PERMISSION_REQUEST_CODE = 4207
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val OUR_IME_CLASS = "com.termux.app.SillageDeviceImeService"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(DeviceControlSnapshot())
    val state = mutableState.asStateFlow()

    private lateinit var appContext: Context
    private var listenersRegistered = false
    private var binding = false
    private var bindStartedAtMs = 0L
    private var bindTimeoutJob: Job? = null
    private var userService: IDeviceControlService? = null
    private var sessionJob: Job? = null
    private var currentSnapshot: DeviceUiSnapshot? = null
    private val operationActive = AtomicBoolean(false)
    private val endingSession = AtomicBoolean(false)

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, DeviceControlUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("device_control")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refresh()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        userService = null
        binding = false
        currentSnapshot = null
        update {
            it.copy(
                shizukuState = if (isShizukuPackageInstalled()) DeviceShizukuState.NOT_RUNNING else DeviceShizukuState.NOT_INSTALLED,
                detail = "Shizuku 已断开",
                serviceUid = -1,
                sessionActive = false,
                sessionExpiresAtMs = 0L,
                imeReady = false,
            )
        }
        stopSession("shizuku_dead")
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            bindUserService()
        } else {
            update { it.copy(shizukuState = DeviceShizukuState.PERMISSION_REQUIRED, detail = "Shizuku 权限被拒绝") }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            bindTimeoutJob?.cancel()
            bindTimeoutJob = null
            if (binder == null || !binder.pingBinder()) {
                update { it.copy(shizukuState = DeviceShizukuState.ERROR, detail = "Shizuku UserService 返回了无效 Binder") }
                return
            }
            userService = IDeviceControlService.Stub.asInterface(binder)
            val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
            update {
                it.copy(
                    shizukuState = DeviceShizukuState.READY,
                    detail = if (uid == 0) "已通过 Shizuku 获得 root 身份" else "已通过 Shizuku 获得 shell 身份",
                    serviceUid = uid,
                    imeReady = DeviceImeBridge.isReady(),
                )
            }
            scope.launch { restoreImeIfPending() }
            DeviceControlMcpRegistry.ensureStarted(appContext)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            binding = false
            bindTimeoutJob?.cancel()
            bindTimeoutJob = null
            update {
                it.copy(
                    shizukuState = DeviceShizukuState.ERROR,
                    detail = "Shizuku UserService 已断开",
                    serviceUid = -1,
                    sessionActive = false,
                    sessionExpiresAtMs = 0L,
                    imeReady = false,
                )
            }
            stopSession("service_disconnected")
        }
    }

    fun initialize(context: Context) {
        // Shizuku loads the same Application class inside the shell-UID UserService process.
        // Only the real application process may own the MCP server, session and UI bridge.
        if (currentProcessName(context) != context.packageName) return
        appContext = context.applicationContext
        if (!listenersRegistered) {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
            listenersRegistered = true
        }
        update {
            it.copy(
                enabled = DeviceControlStore.enabled(appContext),
                sessionMinutes = DeviceControlStore.sessionMinutes(appContext),
                shizukuVersion = shizukuVersionName(),
                shizukuCompatibilityWarning = hasKnownUserServiceBug(),
            )
        }
        if (DeviceControlStore.enabled(appContext)) {
            refresh()
        } else {
            DeviceMcpProvisioner.removeClaude(appContext)
        }
    }

    fun setEnabled(enabled: Boolean) {
        DeviceControlStore.setEnabled(appContext, enabled)
        val revision = System.currentTimeMillis()
        appContext.getSharedPreferences(DeviceControlStore.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(NativeMcpConfigStore.REVISION_KEY, revision)
            .putLong("native_claude_mcp_revision_v1", revision)
            .apply()
        update { it.copy(enabled = enabled) }
        if (enabled) {
            refresh()
            DeviceControlMcpRegistry.ensureStarted(appContext)
        } else {
            stopSession("disabled")
            DeviceControlMcpRegistry.stop()
            update {
                it.copy(
                    enabled = false,
                    shizukuState = DeviceShizukuState.NOT_RUNNING,
                    detail = "设备控制已关闭",
                    mcpRunning = false,
                    mcpEndpoint = "",
                )
            }
        }
    }

    fun setSessionMinutes(minutes: Int) {
        DeviceControlStore.setSessionMinutes(appContext, minutes)
        update { it.copy(sessionMinutes = DeviceControlStore.sessionMinutes(appContext)) }
    }

    fun refresh() {
        if (!::appContext.isInitialized) return
        if (DeviceControlStore.enabled(appContext)) DeviceControlMcpRegistry.ensureStarted(appContext)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            update { it.copy(shizukuState = DeviceShizukuState.UNSUPPORTED, detail = "本机设备控制需要 Android 11 或更高版本") }
            return
        }
        if (!isShizukuPackageInstalled()) {
            val binderAlive = runCatching { Shizuku.getVersion() >= 0 }.getOrDefault(false)
            if (!binderAlive) {
                    update { it.copy(shizukuState = DeviceShizukuState.NOT_INSTALLED, detail = "未检测到 Shizuku，请先安装并启动", serviceUid = -1, shizukuVersion = "", shizukuCompatibilityWarning = false) }
                return
            }
        }
        val version = runCatching { Shizuku.getVersion() }.getOrElse {
            update { it.copy(shizukuState = DeviceShizukuState.NOT_RUNNING, detail = "Shizuku 尚未运行", serviceUid = -1) }
            return
        }
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(false) || version < 11) {
            update { it.copy(shizukuState = DeviceShizukuState.UNSUPPORTED, detail = "Shizuku 版本过旧") }
            return
        }
        val permission = runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            update { it.copy(shizukuState = DeviceShizukuState.PERMISSION_REQUIRED, detail = "需要授予 Sillage Shizuku 权限") }
            return
        }
        if (userService?.asBinder()?.pingBinder() == true) {
            val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
            update { it.copy(shizukuState = DeviceShizukuState.READY, detail = "设备控制服务已连接", serviceUid = uid, imeReady = DeviceImeBridge.isReady()) }
            scope.launch { restoreImeIfPending() }
            return
        }
        bindUserService()
    }

    fun requestPermission() {
        runCatching {
            if (!Shizuku.isPreV11()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }.onFailure { error ->
            update { it.copy(shizukuState = DeviceShizukuState.ERROR, detail = error.message.orEmpty()) }
        }
    }

    suspend fun startSession(minutes: Int): DeviceControlSnapshot = withContext(Dispatchers.IO) {
        if (!DeviceControlStore.enabled(appContext)) throw DeviceToolException("DEVICE_CONTROL_DISABLED", "设备控制未启用")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw DeviceToolException("UNSUPPORTED", "需要 Android 11 或更高版本")
        if (mutableState.value.sessionActive && mutableState.value.sessionExpiresAtMs > System.currentTimeMillis()) return@withContext mutableState.value
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (power?.isInteractive == false || keyguard?.isKeyguardLocked == true) {
            throw DeviceToolException("DEVICE_LOCKED", "手机当前处于锁屏或熄屏状态", "解锁后再开始设备控制会话")
        }
        requireService()
        if (!Settings.canDrawOverlays(appContext) && !NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            throw DeviceToolException("VISIBILITY_REQUIRED", "至少需要悬浮停止按钮或通知权限", "先在设备控制页面授予悬浮窗或通知权限")
        }
        val effectiveMinutes = minutes.coerceIn(5, 30)
        DeviceControlStore.setSessionMinutes(appContext, effectiveMinutes)
        val now = System.currentTimeMillis()
        val expires = now + effectiveMinutes * 60_000L
        sessionJob?.cancel()
        update {
            it.copy(
                sessionActive = true,
                sessionStartedAtMs = now,
                sessionExpiresAtMs = expires,
                sessionMinutes = effectiveMinutes,
                inputMethodSwitched = false,
                imeReady = DeviceImeBridge.isReady(),
                detail = "AI 设备控制会话进行中",
            )
        }
        try {
            DeviceControlSessionService.start(appContext, effectiveMinutes)
        } catch (error: Throwable) {
            update {
                it.copy(
                    sessionActive = false,
                    sessionStartedAtMs = 0L,
                    sessionExpiresAtMs = 0L,
                    detail = "无法启动前台控制服务",
                )
            }
            throw DeviceToolException("SESSION_START_FAILED", error.message ?: "无法启动控制会话", "确认允许通知或悬浮窗后重试")
        }
        sessionJob = scope.launch {
            delay(effectiveMinutes * 60_000L)
            stopSession("timeout")
        }
        mutableState.value
    }

    fun stopSession(reason: String = "user") {
        if (!::appContext.isInitialized) return
        if (!endingSession.compareAndSet(false, true)) return
        val wasActive = mutableState.value.sessionActive
        sessionJob?.cancel()
        sessionJob = null
        currentSnapshot = null
        update {
            it.copy(
                sessionActive = false,
                sessionStartedAtMs = 0L,
                sessionExpiresAtMs = 0L,
                inputMethodSwitched = false,
                detail = when (reason) {
                    "timeout" -> "控制会话已超时"
                    "screen_off" -> "锁屏已结束控制会话"
                    "disabled" -> "设备控制已关闭"
                    "shizuku_dead", "service_disconnected" -> "Shizuku 断开，控制会话已结束"
                    else -> "控制会话已停止"
                },
            )
        }
        DeviceControlSessionService.stop(appContext)
        DeviceShellApprovalBroker.denyAll()
        scope.launch {
            runCatching { restoreImeIfPending() }
            endingSession.set(false)
        }
        if (wasActive) {
            runCatching { FcodeLog.event(appContext, "device_control_session_end", JSONObject().put("reason", reason)) }
        }
    }

    fun onSessionServiceDestroyed() {
        if (mutableState.value.sessionActive) stopSession("service_destroyed")
    }

    internal fun setMcpStatus(running: Boolean, endpoint: String) {
        update {
            it.copy(
                mcpRunning = running,
                mcpEndpoint = if (endpoint.isBlank()) "" else "127.0.0.1 loopback",
            )
        }
    }

    private suspend fun <T> exclusiveDeviceOperation(block: suspend () -> T): T {
        if (!operationActive.compareAndSet(false, true)) {
            throw DeviceToolException("DEVICE_BUSY", "另一个设备操作仍在进行", "等待当前操作完成后再试")
        }
        return try {
            block()
        } finally {
            operationActive.set(false)
        }
    }

    suspend fun statusJson(): JSONObject = withContext(Dispatchers.IO) {
        val service = userService
        val status = runCatching { service?.getStatus() }.getOrNull()
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = power?.isInteractive == false || keyguard?.isKeyguardLocked == true
        JSONObject()
            .put("ok", true)
            .put("enabled", mutableState.value.enabled)
            .put("shizuku_state", mutableState.value.shizukuState.name.lowercase())
            .put("detail", mutableState.value.detail)
            .put("service_uid", mutableState.value.serviceUid)
            .put("session_active", mutableState.value.sessionActive)
            .put("session_expires_at_ms", mutableState.value.sessionExpiresAtMs)
            .put("session_minutes", mutableState.value.sessionMinutes)
            .put("device_locked", locked)
            .put("can_start_session", mutableState.value.canStartSession && !locked)
            .put("ime_ready", DeviceImeBridge.isReady())
            .put("service", status?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject.NULL)
    }

    suspend fun snapshot(includeScreenshot: Boolean, maxNodes: Int): Pair<DeviceUiSnapshot, ByteArray?> = exclusiveDeviceOperation {
        val service = requireSession()
        val started = System.currentTimeMillis()
        val parsed = captureSnapshotLocked(service, maxNodes)
        val screenshot = if (includeScreenshot) withContext(Dispatchers.IO) { captureScreenshot(service) } else null
        DeviceAuditStore.record("device_snapshot", parsed.packageName, "ok", started)
        parsed to screenshot
    }

    private suspend fun captureSnapshotLocked(service: IDeviceControlService, maxNodes: Int): DeviceUiSnapshot =
        withContext(Dispatchers.IO) {
        val raw = runCatching { service.dumpUiHierarchy(maxNodes.coerceIn(20, 500)) }
            .getOrElse { throw remoteError(it) }
        val envelope = JSONObject(raw)
        if (!envelope.optBoolean("ok")) throw DeviceToolException("SNAPSHOT_FAILED", envelope.optString("message", "无法读取 UI"))
        val parsed = DeviceUiSnapshotParser.parse(envelope.optString("xml"), maxNodes.coerceIn(20, 500))
        currentSnapshot = parsed
        parsed
    }

    suspend fun apps(query: String, limit: Int): JSONObject = exclusiveDeviceOperation {
        withContext(Dispatchers.IO) {
        val service = requireSession()
        val result = JSONObject(service.listApps(query, limit))
        if (!result.optBoolean("ok")) throw DeviceToolException("APP_QUERY_FAILED", result.optString("message", "无法读取应用列表"))
        result
        }
    }

    suspend fun findNodes(
        query: DeviceNodeQuery,
        freshSnapshot: Boolean,
        maxNodes: Int,
        limit: Int,
    ): JSONObject = exclusiveDeviceOperation {
        if (!query.hasSelector) throw DeviceToolException("INVALID_ARGUMENT", "至少提供一个查询条件")
        val service = requireSession()
        val started = System.currentTimeMillis()
        val snapshot = if (freshSnapshot || currentSnapshot == null ||
            System.currentTimeMillis() - (currentSnapshot?.capturedAtMs ?: 0L) > 30_000L
        ) captureSnapshotLocked(service, maxNodes) else currentSnapshot!!
        val matches = snapshot.findNodeMatches(query)
        DeviceAuditStore.record("device_find", query.query.ifBlank { query.text }, "matches=${matches.size}", started)
        matchResultJson(snapshot, matches, limit.coerceIn(1, 100))
    }

    suspend fun clickMatch(
        query: DeviceNodeQuery,
        index: Int?,
        snapshotId: String,
        nodeId: String,
        durationMs: Long,
    ): JSONObject = exclusiveDeviceOperation {
        val service = requireSession()
        val started = System.currentTimeMillis()
        val snapshot = if (nodeId.isBlank()) {
            captureSnapshotLocked(service, 400)
        } else {
            val cached = currentSnapshot ?: throw DeviceToolException(
                "SNAPSHOT_STALE", "还没有 UI 快照", "重新调用 device_layout 或 device_snapshot",
            )
            if (snapshotId.isNotBlank() && cached.snapshotId != snapshotId) {
                throw DeviceToolException("SNAPSHOT_STALE", "UI 快照已过期", "重新观察屏幕后再点击")
            }
            if (System.currentTimeMillis() - cached.capturedAtMs > 30_000L) {
                throw DeviceToolException("SNAPSHOT_STALE", "UI 快照已过期", "重新观察屏幕后再点击")
            }
            cached
        }
        val matches = if (nodeId.isNotBlank()) {
            val node = snapshot.node(nodeId) ?: throw DeviceToolException("NODE_NOT_FOUND", "节点不存在：$nodeId")
            listOf(DeviceNodeMatch(node, snapshot.clickableNode(nodeId)))
        } else {
            if (!query.hasSelector) throw DeviceToolException("INVALID_ARGUMENT", "提供 node_id 或至少一个文本/ID 查询条件")
            snapshot.findNodeMatches(query).filter { it.target != null && !it.target.bounds.isEmpty }
        }
        if (matches.isEmpty()) throw DeviceToolException("NODE_NOT_FOUND", "没有找到可点击的匹配节点")
        val chosen = when {
            index != null -> matches.getOrNull(index) ?: throw DeviceToolException(
                "INVALID_ARGUMENT", "index 超出范围：$index，匹配数量 ${matches.size}",
            )
            matches.size == 1 -> matches.first()
            else -> {
                val hint = matches.take(10).joinToString("；") { match ->
                    "#${match.node.id} text=${match.node.text.take(40)} desc=${match.node.contentDescription.take(40)}"
                }
                throw DeviceToolException("NODE_AMBIGUOUS", "匹配到 ${matches.size} 个节点", "指定 index 或 node_id：$hint")
            }
        }
        val target = chosen.target ?: throw DeviceToolException("NODE_NOT_FOUND", "匹配节点的可点击祖先不存在")
        if (target.bounds.isEmpty) throw DeviceToolException("NODE_NOT_FOUND", "目标节点没有有效坐标")
        val x = target.bounds.centerX()
        val y = target.bounds.centerY()
        if (!service.tap(x, y, durationMs.coerceIn(50L, 5000L))) {
            throw DeviceToolException("ACTION_FAILED", "点击失败")
        }
        currentSnapshot = null
        DeviceAuditStore.record("device_click", "#${target.id} ${target.text}".trim(), "ok", started)
        JSONObject()
            .put("ok", true)
            .put("snapshot_id", snapshot.snapshotId)
            .put("matched_node_id", chosen.node.id)
            .put("target_node_id", target.id)
            .put("x", x)
            .put("y", y)
            .put("duration_ms", durationMs.coerceIn(50L, 5000L))
    }

    suspend fun scroll(
        direction: String,
        amount: Float,
        durationMs: Long,
        snapshotId: String,
        nodeId: String,
    ): JSONObject = exclusiveDeviceOperation {
        val service = requireSession()
        val started = System.currentTimeMillis()
        val snapshot = currentSnapshot?.takeIf {
            (snapshotId.isBlank() || it.snapshotId == snapshotId) &&
                System.currentTimeMillis() - it.capturedAtMs <= 30_000L
        }
        val gesture = scrollGesture(service, snapshot, nodeId, direction, amount)
        if (!service.swipe(gesture.fromX, gesture.fromY, gesture.toX, gesture.toY, durationMs.coerceIn(50L, 5000L))) {
            throw DeviceToolException("ACTION_FAILED", "滚动失败")
        }
        currentSnapshot = null
        DeviceAuditStore.record("device_scroll", direction, "ok", started)
        JSONObject()
            .put("ok", true)
            .put("direction", gesture.direction)
            .put("from_x", gesture.fromX)
            .put("from_y", gesture.fromY)
            .put("to_x", gesture.toX)
            .put("to_y", gesture.toY)
    }

    suspend fun scrollUntil(
        query: DeviceNodeQuery,
        direction: String,
        amount: Float,
        maxScrolls: Int,
        durationMs: Long,
        nodeId: String,
    ): JSONObject = exclusiveDeviceOperation {
        if (!query.hasSelector) throw DeviceToolException("INVALID_ARGUMENT", "至少提供一个查询条件")
        val service = requireSession()
        val started = System.currentTimeMillis()
        var lastSnapshot: DeviceUiSnapshot? = null
        var lastMatches: List<DeviceNodeMatch> = emptyList()
        val cappedScrolls = maxScrolls.coerceIn(1, 12)
        for (attempt in 0..cappedScrolls) {
            val snapshot = captureSnapshotLocked(service, 400)
            lastSnapshot = snapshot
            lastMatches = snapshot.findNodeMatches(query)
            if (lastMatches.isNotEmpty()) {
                DeviceAuditStore.record("device_scroll_until", query.query.ifBlank { query.text }, "found", started)
                return@exclusiveDeviceOperation matchResultJson(snapshot, lastMatches, 50)
                    .put("found", true)
                    .put("scrolls", attempt)
            }
            if (attempt == cappedScrolls) break
            val gesture = scrollGesture(service, snapshot, nodeId, direction, amount)
            if (!service.swipe(gesture.fromX, gesture.fromY, gesture.toX, gesture.toY, durationMs.coerceIn(50L, 5000L))) {
                throw DeviceToolException("ACTION_FAILED", "滚动失败")
            }
            currentSnapshot = null
            delay(280L)
        }
        DeviceAuditStore.record("device_scroll_until", query.query.ifBlank { query.text }, "not_found", started)
        val finalSnapshot = lastSnapshot ?: throw DeviceToolException("SNAPSHOT_FAILED", "无法读取最终 UI")
        matchResultJson(finalSnapshot, lastMatches, 50)
            .put("found", false)
            .put("scrolls", cappedScrolls)
    }

    suspend fun waitForCondition(
        query: DeviceNodeQuery,
        targetPackage: String,
        notPresent: Boolean,
        timeoutMs: Long,
        intervalMs: Long,
    ): JSONObject = exclusiveDeviceOperation {
        if (!query.hasSelector && targetPackage.isBlank()) {
            throw DeviceToolException("INVALID_ARGUMENT", "提供文本/ID 查询条件或 package")
        }
        val service = requireSession()
        val started = System.currentTimeMillis()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceIn(250L, 30_000L)
        var lastSnapshot: DeviceUiSnapshot? = null
        var lastMatches: List<DeviceNodeMatch> = emptyList()
        while (true) {
            val snapshot = captureSnapshotLocked(service, 400)
            lastSnapshot = snapshot
            lastMatches = if (query.hasSelector) snapshot.findNodeMatches(query) else emptyList()
            val selectorPresent = !query.hasSelector || lastMatches.isNotEmpty()
            val packagePresent = targetPackage.isBlank() ||
                snapshot.packageName.equals(targetPackage.trim(), ignoreCase = true)
            val present = selectorPresent && packagePresent
            val matched = if (notPresent) !present else present
            if (matched) {
                DeviceAuditStore.record("device_wait_for", query.query.ifBlank { targetPackage }, "matched", started)
                return@exclusiveDeviceOperation matchResultJson(snapshot, lastMatches, 50)
                    .put("matched", true)
                    .put("not_present", notPresent)
                    .put("elapsed_ms", System.currentTimeMillis() - started)
            }
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0L) break
            delay(minOf(intervalMs.coerceIn(200L, 2_000L), remaining))
        }
        DeviceAuditStore.record("device_wait_for", query.query.ifBlank { targetPackage }, "timeout", started)
        matchResultJson(lastSnapshot, lastMatches, 50)
            .put("matched", false)
            .put("not_present", notPresent)
            .put("elapsed_ms", System.currentTimeMillis() - started)
    }

    suspend fun openUrl(url: String, packageName: String): JSONObject = exclusiveDeviceOperation {
        val service = requireSession()
        val started = System.currentTimeMillis()
        val value = url.trim()
        if (value.length !in 1..4096 || value.any { it.isWhitespace() || it.code < 0x20 } ||
            !Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""").containsMatchIn(value)
        ) {
            throw DeviceToolException("INVALID_ARGUMENT", "URL 必须是有效的 URI scheme，例如 https://、mailto: 或应用 deep link")
        }
        runCatching { URI(value) }.getOrElse { throw DeviceToolException("INVALID_ARGUMENT", "URL 格式无效") }
        val pkg = packageName.trim().takeIf { it.isNotBlank() }
        if (pkg != null && !pkg.matches(Regex("""[A-Za-z0-9_.]+"""))) {
            throw DeviceToolException("INVALID_ARGUMENT", "package_name 格式无效")
        }
        val command = buildString {
            append("am start -W -a android.intent.action.VIEW -d ").append(shellQuote(value))
            if (pkg != null) append(" -p ").append(pkg)
        }
        val result = runSafeShell(service, command, 20)
        if (!result.optBoolean("ok")) {
            throw DeviceToolException("OPEN_URL_FAILED", result.optString("output", "无法打开 URL"))
        }
        currentSnapshot = null
        DeviceAuditStore.record("device_open_url", value.take(80), "ok", started)
        JSONObject().put("ok", true).put("url", value).put("package_name", pkg ?: "")
            .put("output", result.optString("output").trim().takeLast(1200))
    }

    suspend fun openSettings(page: String, packageName: String): JSONObject = exclusiveDeviceOperation {
        val service = requireSession()
        val started = System.currentTimeMillis()
        val normalized = page.trim().lowercase().ifBlank { "settings" }
        val targetPackage = packageName.trim()
        val actions = mapOf(
            "settings" to "android.settings.SETTINGS",
            "wifi" to "android.settings.WIFI_SETTINGS",
            "internet" to "android.settings.WIRELESS_SETTINGS",
            "bluetooth" to "android.settings.BLUETOOTH_SETTINGS",
            "apps" to "android.settings.APPLICATION_SETTINGS",
            "notifications" to "android.settings.NOTIFICATION_SETTINGS",
            "sound" to "android.settings.SOUND_SETTINGS",
            "display" to "android.settings.DISPLAY_SETTINGS",
            "battery" to "android.intent.action.POWER_USAGE_SUMMARY",
            "accessibility" to "android.settings.ACCESSIBILITY_SETTINGS",
            "location" to "android.settings.LOCATION_SOURCE_SETTINGS",
            "privacy" to "android.settings.PRIVACY_SETTINGS",
            "storage" to "android.settings.INTERNAL_STORAGE_SETTINGS",
            "developer" to "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
        )
        val action = if (normalized == "app_info") {
            if (!targetPackage.matches(Regex("""[A-Za-z0-9_.]+"""))) {
                throw DeviceToolException("INVALID_ARGUMENT", "app_info 需要有效的 package_name")
            }
            "android.settings.APPLICATION_DETAILS_SETTINGS"
        } else actions[normalized] ?: throw DeviceToolException(
            "INVALID_ARGUMENT", "不支持的设置页：$normalized",
        )
        val command = buildString {
            append("am start -W -a ").append(action)
            if (normalized == "app_info") append(" -d ").append(shellQuote("package:$targetPackage"))
        }
        val result = runSafeShell(service, command, 20)
        if (!result.optBoolean("ok")) {
            throw DeviceToolException("OPEN_SETTINGS_FAILED", result.optString("output", "无法打开设置页"))
        }
        currentSnapshot = null
        DeviceAuditStore.record("device_open_settings", normalized, "ok", started)
        JSONObject().put("ok", true).put("page", normalized).put("action", action)
            .put("output", result.optString("output").trim().takeLast(1200))
    }

    suspend fun systemPanel(action: String): JSONObject = exclusiveDeviceOperation {
        val service = requireSession()
        val started = System.currentTimeMillis()
        val normalized = action.trim().lowercase()
        val command = when (normalized) {
            "notifications", "expand_notifications" -> "cmd statusbar expand-notifications"
            "quick_settings", "quick", "expand_quick_settings" -> "cmd statusbar expand-settings"
            "collapse", "close" -> "cmd statusbar collapse"
            else -> throw DeviceToolException("INVALID_ARGUMENT", "action 必须是 notifications、quick_settings 或 collapse")
        }
        val result = runSafeShell(service, command, 10)
        if (!result.optBoolean("ok")) throw DeviceToolException("SYSTEM_PANEL_FAILED", "系统面板操作失败")
        currentSnapshot = null
        delay(220L)
        DeviceAuditStore.record("device_system_panel", normalized, "ok", started)
        JSONObject().put("ok", true).put("action", normalized)
    }

    suspend fun appInfo(packageName: String): JSONObject = exclusiveDeviceOperation {
        val service = requireSession()
        val started = System.currentTimeMillis()
        val pkg = packageName.trim()
        if (!pkg.matches(Regex("""[A-Za-z0-9_.]+"""))) {
            throw DeviceToolException("INVALID_ARGUMENT", "package_name 格式无效")
        }
        val details = runSafeShell(
            service,
            "dumpsys package $pkg 2>/dev/null | grep -E 'versionCode=|versionName=|primaryCpuAbi=|User 0:|dataDir=' | head -n 20",
            12,
        ).optString("output")
        if (details.isBlank()) throw DeviceToolException("APP_NOT_FOUND", "没有找到应用：$pkg")
        val mainActivity = runSafeShell(service, "cmd package resolve-activity --brief $pkg", 12)
            .optString("output")
            .lineSequence()
            .lastOrNull { it.contains('/') }
            .orEmpty()
            .trim()
        val app = runCatching {
            val list = JSONObject(service.listApps(pkg, 20)).optJSONArray("apps") ?: JSONArray()
            (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                .firstOrNull { it.optString("package_name") == pkg }
        }.getOrNull()
        fun field(pattern: String): String = Regex(pattern).find(details)?.groupValues?.getOrNull(1).orEmpty()
        DeviceAuditStore.record("device_app_info", pkg, "ok", started)
        JSONObject()
            .put("ok", true)
            .put("package_name", pkg)
            .put("label", app?.optString("label").orEmpty())
            .put("enabled", app?.optBoolean("enabled") ?: JSONObject.NULL)
            .put("system", app?.optBoolean("system") ?: JSONObject.NULL)
            .put("version_name", field("""versionName=(\S+)"""))
            .put("version_code", field("""versionCode=(\d+)"""))
            .put("min_sdk", field("""minSdk=(\d+)"""))
            .put("target_sdk", field("""targetSdk=(\d+)"""))
            .put("primary_cpu_abi", field("""primaryCpuAbi=(\S+)"""))
            .put("data_dir", field("""dataDir=(\S+)"""))
            .put("main_activity", mainActivity)
            .put("user_state", details.lineSequence().firstOrNull { it.contains("User 0:") }?.trim().orEmpty())
    }

    suspend fun mediaControl(action: String): JSONObject = exclusiveDeviceOperation {
        val service = requireSession()
        val started = System.currentTimeMillis()
        val normalized = action.trim().lowercase().replace('-', '_')
        val command = when (normalized) {
            "play", "pause", "play_pause", "stop", "next", "previous" ->
                "cmd media_session dispatch ${normalized.replace('_', '-')}"
            "mute" -> "cmd media_session dispatch mute"
            "volume_up", "volume_down" ->
                "cmd media_session volume --adj ${if (normalized == "volume_up") "raise" else "lower"}"
            "volume_get", "volume" -> "cmd media_session volume --get"
            else -> throw DeviceToolException("INVALID_ARGUMENT", "不支持的媒体动作：$action")
        }
        val result = runSafeShell(service, command, 10)
        if (!result.optBoolean("ok")) throw DeviceToolException("MEDIA_CONTROL_FAILED", "媒体控制失败")
        if (normalized != "volume_get" && normalized != "volume") currentSnapshot = null
        DeviceAuditStore.record("device_media", normalized, "ok", started)
        JSONObject().put("ok", true).put("action", normalized)
            .put("output", result.optString("output").trim().takeLast(1200))
    }

    private fun matchResultJson(
        snapshot: DeviceUiSnapshot,
        matches: List<DeviceNodeMatch>,
        limit: Int,
    ): JSONObject = JSONObject()
        .put("ok", true)
        .put("snapshot_id", snapshot.snapshotId)
        .put("package", snapshot.packageName)
        .put("screen_width", snapshot.screenWidth)
        .put("screen_height", snapshot.screenHeight)
        .put("truncated", snapshot.truncated)
        .put("match_count", matches.size)
        .put("matches", JSONArray().apply {
            matches.take(limit.coerceIn(1, 100)).forEach { put(deviceMatchJson(it)) }
        })

    private fun deviceMatchJson(match: DeviceNodeMatch): JSONObject {
        val node = match.node
        val target = match.target
        return JSONObject()
            .put("node_id", node.id)
            .put("text", node.text)
            .put("description", node.contentDescription)
            .put("resource_id", node.resourceId)
            .put("class", node.className)
            .put("bounds", JSONObject()
                .put("left", node.bounds.left).put("top", node.bounds.top)
                .put("right", node.bounds.right).put("bottom", node.bounds.bottom))
            .put("center_x", node.bounds.centerX())
            .put("center_y", node.bounds.centerY())
            .put("clickable", node.clickable)
            .put("editable", node.editable)
            .put("scrollable", node.scrollable)
            .put("click_target_node_id", target?.id ?: JSONObject.NULL)
            .put("click_target_center_x", target?.bounds?.centerX() ?: JSONObject.NULL)
            .put("click_target_center_y", target?.bounds?.centerY() ?: JSONObject.NULL)
    }

    private suspend fun scrollGesture(
        service: IDeviceControlService,
        snapshot: DeviceUiSnapshot?,
        nodeId: String,
        direction: String,
        amount: Float,
    ): ScrollGesture = withContext(Dispatchers.IO) {
        val normalized = direction.trim().lowercase()
        if (normalized !in setOf("up", "down", "left", "right")) {
            throw DeviceToolException("INVALID_ARGUMENT", "direction 必须是 up、down、left 或 right")
        }
        val bounds = if (nodeId.isNotBlank()) {
            val current = snapshot ?: throw DeviceToolException(
                "SNAPSHOT_STALE", "按 node_id 滚动前需要先观察屏幕", "调用 device_layout 后重试",
            )
            val target = current.clickableNode(nodeId) ?: current.node(nodeId)
                ?: throw DeviceToolException("NODE_NOT_FOUND", "滚动容器不存在：$nodeId")
            if (target.bounds.isEmpty) throw DeviceToolException("NODE_NOT_FOUND", "滚动容器没有有效坐标")
            target.bounds
        } else displayBounds(service, snapshot)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            throw DeviceToolException("DISPLAY_UNAVAILABLE", "无法读取屏幕尺寸")
        }
        val fraction = amount.coerceIn(0.1f, 0.9f)
        val dx = (bounds.width() * fraction / 2f).coerceAtLeast(1f)
        val dy = (bounds.height() * fraction / 2f).coerceAtLeast(1f)
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val (fromX, fromY, toX, toY) = when (normalized) {
            "up" -> listOf(cx, cy + dy, cx, cy - dy)
            "down" -> listOf(cx, cy - dy, cx, cy + dy)
            "left" -> listOf(cx + dx, cy, cx - dx, cy)
            else -> listOf(cx - dx, cy, cx + dx, cy)
        }
        ScrollGesture(normalized, fromX.toInt(), fromY.toInt(), toX.toInt(), toY.toInt())
    }

    private fun displayBounds(service: IDeviceControlService, snapshot: DeviceUiSnapshot?): Rect {
        if (snapshot != null && snapshot.screenWidth > 0 && snapshot.screenHeight > 0) {
            return Rect(0, 0, snapshot.screenWidth, snapshot.screenHeight)
        }
        val display = runCatching { JSONObject(service.getStatus()).optString("display") }.getOrDefault("")
        val override = Regex("""Override size:\s*(\d+)x(\d+)""").find(display)
        val physical = Regex("""Physical size:\s*(\d+)x(\d+)""").find(display)
        val match = override ?: physical
        val width = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val height = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        return Rect(0, 0, width, height)
    }

    private suspend fun runSafeShell(
        service: IDeviceControlService,
        command: String,
        timeoutSeconds: Int,
    ): JSONObject = withContext(Dispatchers.IO) {
        // Used only for fixed, auditable tool implementations whose arguments are validated and
        // shell-quoted above. Raw user commands still go through device_shell's per-call approval.
        runCatching { JSONObject(service.execShell(command, timeoutSeconds.coerceIn(1, 30))) }
            .getOrElse { throw remoteError(it) }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private data class ScrollGesture(
        val direction: String,
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int,
    )

    private suspend fun resolvePackageByName(service: IDeviceControlService, appName: String): String = withContext(Dispatchers.IO) {
        if (appName.isBlank()) throw DeviceToolException("INVALID_ARGUMENT", "应用名为空")
        val result = JSONObject(service.listApps(appName, 50))
        if (!result.optBoolean("ok")) throw DeviceToolException("APP_QUERY_FAILED", result.optString("message", "无法查询应用"))
        val apps = result.optJSONArray("apps") ?: JSONArray()
        if (apps.length() == 0) throw DeviceToolException("APP_NOT_FOUND", "没有找到应用：$appName")
        val all = (0 until apps.length()).mapNotNull { apps.optJSONObject(it) }
        val exact = all.filter { it.optString("label").equals(appName.trim(), ignoreCase = true) }
        val candidates = if (exact.size == 1) exact else all
        if (candidates.size != 1) {
            val names = candidates.take(8).joinToString("；") { "${it.optString("label")} (${it.optString("package_name")})" }
            throw DeviceToolException("APP_AMBIGUOUS", "找到多个应用，请改用 package_name：$names")
        }
        candidates.first().optString("package_name")
    }

    suspend fun tap(snapshotId: String?, nodeId: String?, x: Int?, y: Int?, durationMs: Long = 50L): JSONObject = exclusiveDeviceOperation {
        withContext(Dispatchers.IO) {
        val service = requireSession()
        val point = resolvePoint(snapshotId, nodeId, x, y)
        val target = foregroundPackageOrUnknown()
        val started = System.currentTimeMillis()
        if (!service.tap(point.first, point.second, durationMs.coerceIn(50L, 5000L))) throw DeviceToolException("ACTION_FAILED", "点击失败")
        currentSnapshot = null
        DeviceAuditStore.record("device_tap", target, "ok", started)
        JSONObject().put("ok", true).put("x", point.first).put("y", point.second)
        }
    }

    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): JSONObject = exclusiveDeviceOperation {
        withContext(Dispatchers.IO) {
        val service = requireSession()
        val target = foregroundPackageOrUnknown()
        val started = System.currentTimeMillis()
        if (!service.swipe(fromX, fromY, toX, toY, durationMs)) throw DeviceToolException("ACTION_FAILED", "滑动失败")
        currentSnapshot = null
        DeviceAuditStore.record("device_swipe", target, "ok", started)
        JSONObject().put("ok", true)
        }
    }

    suspend fun key(keyName: String): JSONObject = exclusiveDeviceOperation {
        withContext(Dispatchers.IO) {
        val service = requireSession()
        val target = foregroundPackageOrUnknown()
        val started = System.currentTimeMillis()
        if (!service.key(keyName)) throw DeviceToolException("ACTION_FAILED", "按键失败")
        currentSnapshot = null
        DeviceAuditStore.record("device_key", target, keyName, started)
        JSONObject().put("ok", true).put("key", keyName)
        }
    }

    suspend fun launchApp(packageName: String, appName: String, activityName: String): JSONObject = exclusiveDeviceOperation {
        withContext(Dispatchers.IO) {
        val service = requireSession()
        val started = System.currentTimeMillis()
        val resolvedPackage = packageName.trim().ifBlank { resolvePackageByName(service, appName) }
        val result = JSONObject(service.launchApp(resolvedPackage, activityName))
        if (!result.optBoolean("ok")) throw DeviceToolException("LAUNCH_FAILED", result.optString("output", result.optString("message", "启动失败")))
        currentSnapshot = null
        DeviceAuditStore.record("device_launch_app", resolvedPackage, "ok", started)
        result.put("package_name", resolvedPackage)
        }
    }

    suspend fun inputText(text: String, mode: String, pressEnter: Boolean): JSONObject = exclusiveDeviceOperation {
        val service = requireSession()
        val target = foregroundPackageOrUnknown()
        val started = System.currentTimeMillis()
        val validMode = if (mode == "replace") "replace" else "append"
        try {
            if (!DeviceImeBridge.isReady()) prepareIme(service)
            val ready = withTimeoutOrNull(4_000L) {
                while (!DeviceImeBridge.isReady()) delay(50L)
                true
            } ?: false
            if (!ready) throw DeviceToolException("IME_UNAVAILABLE", "桥接输入法尚未连接到目标输入框", "先点击目标输入框，再重试")
            if (!DeviceImeBridge.commitText(text, validMode, pressEnter)) {
                throw DeviceToolException("IME_UNAVAILABLE", "输入法无法写入当前输入框")
            }
            currentSnapshot = null
            DeviceAuditStore.record("device_input_text", target, "ok", started)
            JSONObject().put("ok", true).put("mode", validMode).put("characters", text.length)
        } finally {
            // Restore the user's normal keyboard immediately after the one AI commit.
            runCatching { restoreImeIfPending() }
        }
    }

    suspend fun waitFor(ms: Long): JSONObject {
        requireSession()
        delay(ms.coerceIn(50L, 5000L))
        return foregroundStatus()
    }

    suspend fun shell(command: String, timeoutSeconds: Int): JSONObject = exclusiveDeviceOperation {
        withContext(Dispatchers.IO) {
        requireSession()
        if (command.isBlank()) throw DeviceToolException("INVALID_ARGUMENT", "命令为空")
        if (!DeviceShellApprovalBroker.notificationsAvailable(appContext)) {
            throw DeviceToolException("NOTIFICATION_REQUIRED", "shell 审批通知不可用", "开启 Sillage 通知权限及“AI 设备命令”通知渠道后重试")
        }
        val target = foregroundPackageOrUnknown()
        val started = System.currentTimeMillis()
        val approved = DeviceShellApprovalBroker.request(appContext, command)
        if (!approved) {
            DeviceAuditStore.record("device_shell", target, "denied", started)
            throw DeviceToolException("APPROVAL_DENIED", "用户拒绝或审批超时")
        }
        val service = requireSession()
        val result = JSONObject(service.execShell(command, timeoutSeconds.coerceIn(1, 60)))
        currentSnapshot = null
        val code = if (result.optBoolean("ok")) "ok" else if (result.optBoolean("timedOut")) "timeout" else "command_failed"
        DeviceAuditStore.record("device_shell", target, code, started)
        result.put("approval", "granted_once")
        }
    }

    suspend fun foregroundStatus(): JSONObject = withContext(Dispatchers.IO) {
        val service = userService ?: throw DeviceToolException("SHIZUKU_NOT_RUNNING", "Shizuku 服务未连接")
        val status = JSONObject(service.getStatus())
        JSONObject().put("ok", true).put("focus", status.optString("focus")).put("display", status.optString("display"))
    }

    private suspend fun prepareIme(service: IDeviceControlService): Boolean = withContext(Dispatchers.IO) {
        val ours = ComponentName(appContext.packageName, OUR_IME_CLASS)
        val encoded = ours.flattenToString()
        val current = shellOutput(service, "settings get secure default_input_method", 5)
        if (isSafeImeComponent(current) && current != encoded) {
            DeviceControlStore.setPreviousIme(appContext, current)
            DeviceControlStore.setImeCleanupPending(appContext, true)
        }
        shellOutput(service, "ime enable $encoded", 5)
        val selected = shellOutput(service, "ime set $encoded", 5)
        val success = !selected.contains("Error", ignoreCase = true) && !selected.contains("Unknown", ignoreCase = true)
        update { it.copy(inputMethodSwitched = success, imeReady = DeviceImeBridge.isReady() || success) }
        success
    }

    private suspend fun restoreImeIfPending() {
        if (!DeviceControlStore.imeCleanupPending(appContext)) {
            update { it.copy(imeReady = DeviceImeBridge.isReady(), inputMethodSwitched = false) }
            return
        }
        val service = userService ?: return
        val previous = DeviceControlStore.previousIme(appContext)
        val ours = ComponentName(appContext.packageName, OUR_IME_CLASS).flattenToString()
        withContext(Dispatchers.IO) {
            if (isSafeImeComponent(previous)) shellOutput(service, "ime set $previous", 5)
            shellOutput(service, "ime disable $ours", 5)
            DeviceControlStore.setImeCleanupPending(appContext, false)
            DeviceControlStore.setPreviousIme(appContext, "")
            update { it.copy(imeReady = DeviceImeBridge.isReady(), inputMethodSwitched = false) }
        }
    }

    private fun isSafeImeComponent(value: String): Boolean =
        value.matches(Regex("""[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+"""))

    private fun shellOutput(service: IDeviceControlService, command: String, timeoutSeconds: Int): String {
        return runCatching {
            val result = JSONObject(service.execShell(command, timeoutSeconds))
            if (result.optBoolean("ok")) result.optString("output").trim() else ""
        }.getOrDefault("")
    }

    private fun requireService(): IDeviceControlService {
        if (!DeviceControlStore.enabled(appContext)) throw DeviceToolException("DEVICE_CONTROL_DISABLED", "设备控制未启用")
        val service = userService
        if (service == null || !service.asBinder().pingBinder()) {
            refresh()
            throw DeviceToolException("SHIZUKU_NOT_RUNNING", "Shizuku UserService 未连接", "在设置中启动 Shizuku 并授权")
        }
        return service
    }

    private fun requireSession(): IDeviceControlService {
        val service = requireService()
        val snapshot = mutableState.value
        if (!snapshot.sessionActive || snapshot.sessionExpiresAtMs <= System.currentTimeMillis()) {
            if (snapshot.sessionActive) stopSession("timeout")
            throw DeviceToolException("SESSION_REQUIRED", "需要先开启设备控制会话", "在 Sillage 的设备控制页面开始一个 5–30 分钟的会话")
        }
        return service
    }

    private fun resolvePoint(snapshotId: String?, nodeId: String?, x: Int?, y: Int?): Pair<Int, Int> {
        if (x != null && y != null) return x to y
        if (snapshotId.isNullOrBlank() || nodeId.isNullOrBlank()) {
            throw DeviceToolException("INVALID_ARGUMENT", "必须提供 snapshot_id + node_id，或 x + y")
        }
        val snapshot = currentSnapshot ?: throw DeviceToolException("SNAPSHOT_STALE", "还没有 UI 快照")
        if (snapshot.snapshotId != snapshotId || System.currentTimeMillis() - snapshot.capturedAtMs > 30_000L) {
            throw DeviceToolException("SNAPSHOT_STALE", "UI 快照已过期", "重新调用 device_snapshot")
        }
        val node = snapshot.clickableNode(nodeId) ?: throw DeviceToolException("NODE_NOT_FOUND", "节点不存在或不可点击")
        if (node.bounds.isEmpty) throw DeviceToolException("NODE_NOT_FOUND", "节点没有有效坐标")
        return node.bounds.centerX() to node.bounds.centerY()
    }

    private fun captureScreenshot(service: IDeviceControlService): ByteArray? {
        val descriptor = service.captureScreenshot() ?: return null
        return runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (total >= 24 * 1024 * 1024) break
                        val allowed = minOf(read, 24 * 1024 * 1024 - total)
                        output.write(buffer, 0, allowed)
                        total += allowed
                    }
                    output.toByteArray().takeIf { it.isNotEmpty() }
                }
            }
        }.getOrNull()
    }

    private fun remoteError(error: Throwable): DeviceToolException =
        DeviceToolException("SHIZUKU_SERVICE_ERROR", error.message ?: error.javaClass.simpleName, "重新连接 Shizuku")

    private fun foregroundPackageOrUnknown(): String = currentSnapshot?.packageName.orEmpty().ifBlank { "unknown" }

    private fun bindUserService() {
        if (!::appContext.isInitialized || binding) return
        binding = true
        val warning = hasKnownUserServiceBug()
        update {
            it.copy(
                shizukuState = DeviceShizukuState.STARTING,
                detail = if (warning) "正在连接；Shizuku 13.6.0 在 MediaTek/HyperOS 上存在已知启动问题" else "正在启动 Shizuku 设备控制服务",
                shizukuVersion = shizukuVersionName(),
                shizukuCompatibilityWarning = warning,
            )
        }
        runCatching { Shizuku.bindUserService(args, serviceConnection) }
            .onFailure { error ->
                binding = false
                update { state -> state.copy(shizukuState = DeviceShizukuState.ERROR, detail = error.message.orEmpty()) }
            }
        bindTimeoutJob?.cancel()
        bindTimeoutJob = scope.launch {
            delay(12_000L)
            if (binding && System.currentTimeMillis() - bindStartedAtMs >= 11_000L) {
                binding = false
                runCatching { Shizuku.unbindUserService(args, serviceConnection, true) }
                val warning = hasKnownUserServiceBug()
                update {
                    it.copy(
                        shizukuState = DeviceShizukuState.ERROR,
                        detail = if (warning) {
                            "Shizuku 13.6.0 的 UserService 在 MediaTek/HyperOS 上启动失败。请安装 Shizuku 13.5.4 后重新启动服务。"
                        } else {
                            "Shizuku UserService 启动超时，请重启 Shizuku 后重试"
                        },
                        shizukuCompatibilityWarning = warning,
                    )
                }
            }
        }
    }

    private fun shizukuVersionName(): String = runCatching {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0).versionName.orEmpty()
    }.getOrDefault("")

    private fun hasKnownUserServiceBug(): Boolean {
        val version = shizukuVersionName()
        val mediaTek = Build.HARDWARE.contains("mt", ignoreCase = true) || Build.BOARD.contains("mt", ignoreCase = true)
        return mediaTek && version.startsWith("13.6.0")
    }

    private fun currentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName().orEmpty()
        }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return activityManager?.runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
            ?.processName
            .orEmpty()
    }

    private fun isShizukuPackageInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    @Synchronized
    private fun update(transform: (DeviceControlSnapshot) -> DeviceControlSnapshot) {
        mutableState.value = transform(mutableState.value)
    }

}
