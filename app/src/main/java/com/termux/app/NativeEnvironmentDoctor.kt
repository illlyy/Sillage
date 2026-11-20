package com.termux.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Severity of one self-check row. [INFO] is context, not a problem. */
internal enum class NativeEnvState { OK, WARN, FAIL, INFO }

/** What the row's button does. Navigation actions are resolved by the page, not here. */
internal enum class NativeEnvAction {
    NONE,
    OPEN_DEVELOPMENT_TOOLS,
    OPEN_MODEL_CONFIGS,
    OPEN_SKILLS,
    OPEN_MCP,
    OPEN_LOGS,
    OPEN_APP_SETTINGS,
    OPEN_NOTIFICATION_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_OVERLAY_SETTINGS,
    REQUEST_STORAGE,
    RUN_ENVIRONMENT_SETUP,
}

/** Grouping used only as a small label on the row; the page sorts by severity. */
internal enum class NativeEnvGroup { RUNTIME, CLI, PROCESS, NETWORK, STORAGE, PERMISSION, EXTENSIONS }

internal data class NativeEnvCheck(
    val id: String,
    val group: NativeEnvGroup,
    val state: NativeEnvState,
    val titleZh: String,
    val titleEn: String,
    val detailZh: String,
    val detailEn: String,
    val action: NativeEnvAction = NativeEnvAction.NONE,
    val actionZh: String = "",
    val actionEn: String = "",
)

/**
 * Read-only environment diagnosis: what is installed, what the spawned environment actually looks
 * like, whether the network resolves, and which scattered permissions are still missing.
 *
 * Deliberately never repairs anything. The one previous "fix" that rebuilt the runtime directory
 * silently deleted the Claude CLI (see F-CLAUDE-31), so every row either explains itself or routes
 * the user to the single page that owns that component.
 *
 * All members do blocking IO; call from [kotlinx.coroutines.Dispatchers.IO].
 */
internal object NativeEnvironmentDoctor {
    private const val PROBE_NAME = ".fcode-write-probe"

    fun run(context: Context, prefs: SharedPreferences): List<NativeEnvCheck> {
        val checks = mutableListOf<NativeEnvCheck>()
        checks += runtimeChecks()
        checks += cliChecks(context)
        checks += processChecks()
        checks += networkChecks(prefs)
        checks += storageChecks(context, prefs)
        checks += permissionChecks(context)
        checks += extensionChecks()
        return checks
    }

    /** Deterministic ordering: failures first, then warnings, then healthy rows, then context. */
    fun ordered(checks: List<NativeEnvCheck>): List<NativeEnvCheck> = checks.sortedBy {
        when (it.state) {
            NativeEnvState.FAIL -> 0
            NativeEnvState.WARN -> 1
            NativeEnvState.OK -> 2
            NativeEnvState.INFO -> 3
        }
    }

    fun counts(checks: List<NativeEnvCheck>): Map<NativeEnvState, Int> = NativeEnvState.entries
        .associateWith { state -> checks.count { it.state == state } }

    // ---- runtime -----------------------------------------------------------------------------

    private fun runtimeChecks(): List<NativeEnvCheck> {
        val prefix = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)
        val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        val bash = File(prefix, "bin/bash")
        val marker = CodexInstaller.environmentMarker()
        return listOf(
            NativeEnvCheck(
                id = "runtime.prefix",
                group = NativeEnvGroup.RUNTIME,
                state = if (prefix.isDirectory) NativeEnvState.OK else NativeEnvState.FAIL,
                titleZh = "Termux 根目录",
                titleEn = "Termux prefix",
                detailZh = if (prefix.isDirectory) prefix.absolutePath else "缺少 ${prefix.absolutePath}，基础环境还没有解压。",
                detailEn = if (prefix.isDirectory) prefix.absolutePath else "${prefix.absolutePath} is missing; the base environment was never extracted.",
                action = if (prefix.isDirectory) NativeEnvAction.NONE else NativeEnvAction.OPEN_DEVELOPMENT_TOOLS,
                actionZh = "去安装",
                actionEn = "Install",
            ),
            NativeEnvCheck(
                id = "runtime.bash",
                group = NativeEnvGroup.RUNTIME,
                state = if (bash.canExecute()) NativeEnvState.OK else NativeEnvState.FAIL,
                titleZh = "Shell（bash）",
                titleEn = "Shell (bash)",
                detailZh = if (bash.canExecute()) "可执行" else "缺少可执行的 ${bash.absolutePath}，安装脚本与工具都会启动失败。",
                detailEn = if (bash.canExecute()) "Executable" else "${bash.absolutePath} is not executable; every install script and tool spawn will fail.",
                action = if (bash.canExecute()) NativeEnvAction.NONE else NativeEnvAction.OPEN_DEVELOPMENT_TOOLS,
                actionZh = "去安装",
                actionEn = "Install",
            ),
            NativeEnvCheck(
                id = "runtime.marker",
                group = NativeEnvGroup.RUNTIME,
                state = when {
                    marker.isFile -> NativeEnvState.OK
                    bash.canExecute() -> NativeEnvState.WARN
                    else -> NativeEnvState.INFO
                },
                titleZh = "开发环境安装记录",
                titleEn = "Environment setup record",
                detailZh = when {
                    marker.isFile -> "已完成一次性安装"
                    bash.canExecute() -> "基础环境可用，但完整工具集（git、node、python 等）从未装完。只有打开过 WebUI 才会触发补装，原生聊天不会。"
                    else -> "基础环境尚未解压"
                },
                detailEn = when {
                    marker.isFile -> "One-time setup completed"
                    bash.canExecute() -> "Base is usable but the full tool set (git, node, python…) never finished. It is only provisioned when the WebUI is opened, never from native chat."
                    else -> "Base environment not extracted yet"
                },
                action = when {
                    marker.isFile -> NativeEnvAction.NONE
                    bash.canExecute() -> NativeEnvAction.RUN_ENVIRONMENT_SETUP
                    else -> NativeEnvAction.NONE
                },
                actionZh = "补齐工具",
                actionEn = "Install tools",
            ),
            NativeEnvCheck(
                id = "runtime.writable",
                group = NativeEnvGroup.RUNTIME,
                state = when {
                    !writable(prefix) || !writable(home) -> NativeEnvState.FAIL
                    else -> NativeEnvState.OK
                },
                titleZh = "目录可写",
                titleEn = "Writable directories",
                detailZh = if (writable(prefix) && writable(home)) "运行目录与主目录可写" else "运行目录或主目录不可写，工具无法安装。",
                detailEn = if (writable(prefix) && writable(home)) "Prefix and home are writable" else "Prefix or home is read-only; nothing can be installed.",
            ),
        )
    }

    private fun writable(directory: File): Boolean = runCatching {
        if (!directory.isDirectory) return@runCatching false
        val probe = File(directory, PROBE_NAME)
        probe.writeText("ok")
        probe.delete()
    }.getOrDefault(false)

    // ---- CLI ---------------------------------------------------------------------------------

    private fun cliChecks(context: Context): List<NativeEnvCheck> {
        val codex = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "codex")
        val codexVersion = if (codex.canExecute()) runVersion(codex) else ""
        val claudeInfo = runCatching { ClaudeInstallInfo.from(context) }.getOrDefault(ClaudeInstallInfo.Unknown)
        val muslNeeded = claudeInfo.kind == ClaudeInstaller.InstallKind.OFFICIAL_BINARY
        val muslPresent = runCatching { ClaudeMuslRuntime.isLoaderPresent() }.getOrDefault(false)
        return listOfNotNull(
            NativeEnvCheck(
                id = "cli.codex",
                group = NativeEnvGroup.CLI,
                state = if (codex.canExecute()) NativeEnvState.OK else NativeEnvState.FAIL,
                titleZh = "Codex CLI",
                titleEn = "Codex CLI",
                detailZh = when {
                    !codex.canExecute() -> "未安装。原生聊天、WebUI 与 Web 搜索都会不可用。"
                    codexVersion.isNotBlank() -> codexVersion
                    else -> "已安装（版本未知）"
                },
                detailEn = when {
                    !codex.canExecute() -> "Not installed. Native chat, WebUI and web search are unavailable."
                    codexVersion.isNotBlank() -> codexVersion
                    else -> "Installed (version unknown)"
                },
                action = if (codex.canExecute()) NativeEnvAction.NONE else NativeEnvAction.OPEN_DEVELOPMENT_TOOLS,
                actionZh = "去安装",
                actionEn = "Install",
            ),
            NativeEnvCheck(
                id = "cli.claude",
                group = NativeEnvGroup.CLI,
                state = when {
                    claudeInfo.kind == ClaudeInstaller.InstallKind.NONE -> NativeEnvState.WARN
                    claudeInfo.sandboxBlocked -> NativeEnvState.WARN
                    else -> NativeEnvState.OK
                },
                titleZh = "Claude CLI",
                titleEn = "Claude CLI",
                detailZh = when {
                    claudeInfo.kind == ClaudeInstaller.InstallKind.NONE -> "未安装。切到 Claude 后端后会提示安装。"
                    claudeInfo.sandboxBlocked -> "官方二进制被系统沙箱拦截（退出码 ${claudeInfo.sandboxExitCode}），已改用 npm 版 ${claudeInfo.version}。"
                    else -> "官方二进制 ${claudeInfo.version}"
                },
                detailEn = when {
                    claudeInfo.kind == ClaudeInstaller.InstallKind.NONE -> "Not installed. The Claude backend will prompt you."
                    claudeInfo.sandboxBlocked -> "The official binary is blocked by the system sandbox (exit ${claudeInfo.sandboxExitCode}); running the npm build ${claudeInfo.version}."
                    else -> "Official binary ${claudeInfo.version}"
                },
                action = if (claudeInfo.kind == ClaudeInstaller.InstallKind.NONE) NativeEnvAction.OPEN_MODEL_CONFIGS else NativeEnvAction.NONE,
                actionZh = "去安装",
                actionEn = "Install",
            ),
            if (!muslNeeded) null else NativeEnvCheck(
                id = "cli.musl",
                group = NativeEnvGroup.CLI,
                state = if (muslPresent) NativeEnvState.OK else NativeEnvState.FAIL,
                titleZh = "musl 运行时",
                titleEn = "musl runtime",
                detailZh = if (muslPresent) "已就位" else "官方二进制需要它才能启动，当前缺失，Claude 后端会直接失败。",
                detailEn = if (muslPresent) "Present" else "The official binary needs it to start; the Claude backend will fail without it.",
                action = if (muslPresent) NativeEnvAction.NONE else NativeEnvAction.OPEN_MODEL_CONFIGS,
                actionZh = "重新安装",
                actionEn = "Reinstall",
            ),
        )
    }

    /** Reads the first meaningful line of `--version`, bounded so a hung binary cannot stall the page. */
    private fun runVersion(binary: File, timeoutMs: Long = 3500): String = runCatching {
        val process = NativeProcessEnv.apply(ProcessBuilder(binary.absolutePath, "--version"))
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { stream -> output.append(stream.readText()) }
            }
        }.apply { isDaemon = true; start() }
        reader.join(timeoutMs)
        if (reader.isAlive) {
            runCatching { process.destroy() }
            return@runCatching ""
        }
        output.toString().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    }.getOrDefault("")

    // ---- process env -------------------------------------------------------------------------

    private fun processChecks(): List<NativeEnvCheck> {
        val environment = NativeProcessEnv.base()
        val pathEntries = environment["PATH"].orEmpty().split(':').filter { it.isNotBlank() }
        val prefixBin = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
        val tmp = File(environment["TMPDIR"].orEmpty())
        val lib = File(TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH)
        return listOf(
            NativeEnvCheck(
                id = "env.path",
                group = NativeEnvGroup.PROCESS,
                state = if (prefixBin in pathEntries && File(prefixBin).isDirectory) NativeEnvState.OK else NativeEnvState.FAIL,
                titleZh = "PATH",
                titleEn = "PATH",
                detailZh = if (prefixBin in pathEntries) environment["PATH"].orEmpty() else "PATH 缺少 $prefixBin，子进程会找不到已安装的工具。",
                detailEn = if (prefixBin in pathEntries) environment["PATH"].orEmpty() else "PATH is missing $prefixBin; child processes cannot find installed tools.",
            ),
            NativeEnvCheck(
                id = "env.tmpdir",
                group = NativeEnvGroup.PROCESS,
                state = if (tmp.isDirectory && writable(tmp)) NativeEnvState.OK else NativeEnvState.WARN,
                titleZh = "TMPDIR",
                titleEn = "TMPDIR",
                detailZh = if (tmp.isDirectory && writable(tmp)) tmp.absolutePath else "${tmp.absolutePath} 不存在或不可写，下载与解压可能失败。",
                detailEn = if (tmp.isDirectory && writable(tmp)) tmp.absolutePath else "${tmp.absolutePath} is missing or read-only; downloads and extraction may fail.",
            ),
            NativeEnvCheck(
                id = "env.lib",
                group = NativeEnvGroup.PROCESS,
                state = if (lib.isDirectory) NativeEnvState.OK else NativeEnvState.WARN,
                titleZh = "共享库路径",
                titleEn = "Shared libraries",
                detailZh = if (lib.isDirectory) "已注入 LD_LIBRARY_PATH" else "缺少 ${lib.absolutePath}，MCP 与 npm 启动的子进程可能报找不到库。",
                detailEn = if (lib.isDirectory) "LD_LIBRARY_PATH is injected" else "${lib.absolutePath} is missing; MCP and npm children may fail to load libraries.",
            ),
            NativeEnvCheck(
                id = "env.locale",
                group = NativeEnvGroup.PROCESS,
                state = NativeEnvState.OK,
                titleZh = "字符编码",
                titleEn = "Text encoding",
                detailZh = "子进程统一使用 ${NativeProcessEnv.DEFAULT_LANG}，中文与 emoji 不会因 locale 丢失。",
                detailEn = "Child processes use ${NativeProcessEnv.DEFAULT_LANG}; non-ASCII output stays intact.",
            ),
        )
    }

    // ---- network -----------------------------------------------------------------------------

    private fun networkChecks(prefs: SharedPreferences): List<NativeEnvCheck> {
        val github = resolveHost("github.com")
        val npm = resolveHost("registry.npmjs.org")
        val mirrorHost = aptMirrorHost()
        val mirror = mirrorHost.isBlank() || resolveHost(mirrorHost)
        val routeApi = prefs.getBoolean("mihomo_route_api", false)
        return listOf(
            NativeEnvCheck(
                id = "net.dns.github",
                group = NativeEnvGroup.NETWORK,
                state = if (github) NativeEnvState.OK else NativeEnvState.FAIL,
                titleZh = "GitHub 解析",
                titleEn = "GitHub resolution",
                detailZh = if (github) "github.com 可解析" else "无法解析 github.com，Codex / Claude 的下载与更新都会失败。",
                detailEn = if (github) "github.com resolves" else "github.com does not resolve; CLI downloads and updates will fail.",
            ),
            NativeEnvCheck(
                id = "net.dns.npm",
                group = NativeEnvGroup.NETWORK,
                state = if (npm) NativeEnvState.OK else NativeEnvState.WARN,
                titleZh = "npm 源解析",
                titleEn = "npm registry resolution",
                detailZh = if (npm) "registry.npmjs.org 可解析" else "无法解析 registry.npmjs.org，npm 版 Claude CLI 无法安装或更新。",
                detailEn = if (npm) "registry.npmjs.org resolves" else "registry.npmjs.org does not resolve; the npm Claude build cannot install or update.",
            ),
            NativeEnvCheck(
                id = "net.mirror",
                group = NativeEnvGroup.NETWORK,
                state = when {
                    mirrorHost.isBlank() -> NativeEnvState.INFO
                    mirror -> NativeEnvState.OK
                    else -> NativeEnvState.WARN
                },
                titleZh = "软件源可达",
                titleEn = "Package mirror",
                detailZh = when {
                    mirrorHost.isBlank() -> "未检测到 apt 软件源配置"
                    mirror -> "当前源 $mirrorHost 可解析"
                    else -> "当前源 $mirrorHost 无法解析，安装工具会失败。"
                },
                detailEn = when {
                    mirrorHost.isBlank() -> "No apt source configured"
                    mirror -> "Configured source $mirrorHost resolves"
                    else -> "Configured source $mirrorHost does not resolve; installing tools will fail."
                },
                action = if (mirrorHost.isNotBlank() && !mirror) NativeEnvAction.OPEN_DEVELOPMENT_TOOLS else NativeEnvAction.NONE,
                actionZh = "检查工具页",
                actionEn = "Open tools",
            ),
            NativeEnvCheck(
                id = "net.proxy",
                group = NativeEnvGroup.NETWORK,
                state = if (routeApi) NativeEnvState.INFO else NativeEnvState.OK,
                titleZh = "API 代理",
                titleEn = "API proxy",
                detailZh = if (routeApi) "已开启「API 走代理」。若请求超时，先确认代理在运行。" else "API 直连（未走 Mihomo）",
                detailEn = if (routeApi) "API goes through the proxy. If requests time out, confirm the proxy is running." else "API connects directly (Mihomo bypassed)",
            ),
        )
    }

    /** Host of the first `deb <url>` line, so the check follows whatever source is configured. */
    internal fun aptMirrorHost(sourcesList: File = File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "etc/apt/sources.list")): String {
        val line = runCatching {
            if (!sourcesList.isFile) return@runCatching ""
            sourcesList.bufferedReader().useLines { lines ->
                lines.map { it.trim() }.firstOrNull { it.startsWith("deb ") }
            }.orEmpty()
        }.getOrDefault("")
        return runCatching {
            val url = line.split(Regex("\\s+")).getOrNull(1).orEmpty()
            java.net.URI(url).host.orEmpty()
        }.getOrDefault("")
    }

    private fun resolveHost(host: String, timeoutMs: Long = 3000): Boolean {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "EnvDnsProbe").apply { isDaemon = true }
        }
        return try {
            executor.submit<String?> {
                runCatching { java.net.InetAddress.getByName(host).hostAddress }.getOrNull()
            }.get(timeoutMs, TimeUnit.MILLISECONDS) != null
        } catch (error: Throwable) {
            false
        } finally {
            executor.shutdownNow()
        }
    }

    // ---- storage -----------------------------------------------------------------------------

    private fun storageChecks(context: Context, prefs: SharedPreferences): List<NativeEnvCheck> {
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val available = stat?.availableBytes ?: 0L
        val total = stat?.totalBytes ?: 0L
        val logs = directorySize(FcodeLog.logDir(context))
        val cache = directorySize(context.cacheDir)
        val configured = NativeWorkspace.configured(prefs)
        val workspace = NativeWorkspace.resolve(prefs)
        val configuredUsable = NativeWorkspace.isUsable(configured)
        return listOf(
            NativeEnvCheck(
                id = "storage.space",
                group = NativeEnvGroup.STORAGE,
                state = when {
                    available <= 0L -> NativeEnvState.INFO
                    available < 1L * 1024 * 1024 * 1024 -> NativeEnvState.FAIL
                    available < 3L * 1024 * 1024 * 1024 -> NativeEnvState.WARN
                    else -> NativeEnvState.OK
                },
                titleZh = "可用空间",
                titleEn = "Free space",
                detailZh = "可用 ${formatBytes(available)} / 共 ${formatBytes(total)}" +
                    if (available in 1..(3L * 1024 * 1024 * 1024 - 1)) " · 安装或更新可能失败" else "",
                detailEn = "${formatBytes(available)} free of ${formatBytes(total)}" +
                    if (available in 1..(3L * 1024 * 1024 * 1024 - 1)) " · installs may fail" else "",
            ),
            NativeEnvCheck(
                id = "storage.logs",
                group = NativeEnvGroup.STORAGE,
                state = if (logs > 24L * 1024 * 1024) NativeEnvState.WARN else NativeEnvState.OK,
                titleZh = "日志与缓存",
                titleEn = "Logs & cache",
                detailZh = "日志 ${formatBytes(logs)} · 缓存 ${formatBytes(cache)}",
                detailEn = "Logs ${formatBytes(logs)} · cache ${formatBytes(cache)}",
                action = NativeEnvAction.OPEN_LOGS,
                actionZh = "清理",
                actionEn = "Manage",
            ),
            NativeEnvCheck(
                id = "storage.workspace",
                group = NativeEnvGroup.STORAGE,
                state = when {
                    configured == null -> NativeEnvState.OK
                    configuredUsable -> NativeEnvState.OK
                    else -> NativeEnvState.WARN
                },
                titleZh = "当前工作区",
                titleEn = "Current workspace",
                detailZh = when {
                    configured == null -> "使用应用私有目录 ${workspace.absolutePath}"
                    configuredUsable -> workspace.absolutePath
                    else -> "已选择 ${configured.absolutePath}，但应用没有读写权限，已回退到 ${workspace.absolutePath}；AI 只能改动应用私有文件。"
                },
                detailEn = when {
                    configured == null -> "Using the app-private directory ${workspace.absolutePath}"
                    configuredUsable -> workspace.absolutePath
                    else -> "${configured.absolutePath} is selected but not readable/writable; fell back to ${workspace.absolutePath}. The AI can only touch app-private files."
                },
                action = if (configured != null && !configuredUsable) {
                    NativeEnvAction.REQUEST_STORAGE
                } else {
                    NativeEnvAction.NONE
                },
                actionZh = "授予存储权限",
                actionEn = "Grant storage",
            ),
        )
    }

    /** The directory the backends actually use, as resolved by [NativeWorkspace]. */
    internal fun currentWorkspace(prefs: SharedPreferences): File = NativeWorkspace.resolve(prefs)

    private fun directorySize(root: File, depth: Int = 0): Long {
        if (!root.isDirectory || depth > 6) return 0L
        return root.listFiles()?.sumOf { file ->
            if (file.isDirectory) directorySize(file, depth + 1) else file.length()
        } ?: 0L
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
        else -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1073741824.0)
    }

    // ---- permissions -------------------------------------------------------------------------

    private fun permissionChecks(context: Context): List<NativeEnvCheck> {
        val legacyStorage = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        // This app targets SDK 28, so on Android 11+ the legacy READ/WRITE grant is what unlocks
        // shared storage; MANAGE_EXTERNAL_STORAGE cannot be granted to a low-target app at all.
        val storageGranted = legacyStorage || runCatching {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
        }.getOrDefault(false)
        // Trust the grant only if it actually buys access: a granted-but-restricted app looks
        // identical in settings while every file operation still fails.
        val sharedWritable = storageGranted && writable(Environment.getExternalStorageDirectory())
        val allFiles = storageGranted && sharedWritable
        val notifications = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(true)
        val battery = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrNull() ?: false
        val overlay = runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
        return listOf(
            NativeEnvCheck(
                id = "perm.allfiles",
                group = NativeEnvGroup.PERMISSION,
                state = if (allFiles) NativeEnvState.OK else NativeEnvState.FAIL,
                titleZh = "存储权限",
                titleEn = "Storage access",
                detailZh = when {
                    allFiles -> "已授权，可读写共享存储"
                    storageGranted -> "权限已授予，但共享存储仍然写不进去（可能被系统限制）。可在「设置 → 应用 → 存储」里检查。"
                    else -> "未授权。默认工作区在共享存储上，AI 读写项目文件会失败；当前已回退到应用私有目录。"
                },
                detailEn = when {
                    allFiles -> "Granted; shared storage is readable and writable"
                    storageGranted -> "Granted, but shared storage is still not writable (may be restricted by the system). Check Settings → Apps → Storage."
                    else -> "Not granted. The default workspace lives on shared storage, so file access fails; it currently falls back to the app-private directory."
                },
                // The app targets SDK 28, so the grantable permission is the legacy one; opening
                // app settings would only show a toggle that does not exist for this target.
                action = if (storageGranted) NativeEnvAction.NONE else NativeEnvAction.REQUEST_STORAGE,
                actionZh = "授予权限",
                actionEn = "Grant",
            ),
            NativeEnvCheck(
                id = "perm.notifications",
                group = NativeEnvGroup.PERMISSION,
                state = if (notifications) NativeEnvState.OK else NativeEnvState.WARN,
                titleZh = "通知",
                titleEn = "Notifications",
                detailZh = if (notifications) "已开启" else "已关闭。任务完成提醒不会再出现。",
                detailEn = if (notifications) "Enabled" else "Disabled. Completion alerts will not appear.",
                action = if (notifications) NativeEnvAction.NONE else NativeEnvAction.OPEN_NOTIFICATION_SETTINGS,
                actionZh = "去开启",
                actionEn = "Enable",
            ),
            NativeEnvCheck(
                id = "perm.battery",
                group = NativeEnvGroup.PERMISSION,
                state = if (battery) NativeEnvState.OK else NativeEnvState.WARN,
                titleZh = "电池优化",
                titleEn = "Battery optimization",
                detailZh = if (battery) "已忽略优化，长任务不易被杀" else "未忽略优化，后台长任务可能被系统杀掉。",
                detailEn = if (battery) "Ignored; long tasks survive in background" else "Not ignored; long background tasks may be killed.",
                action = if (battery) NativeEnvAction.NONE else NativeEnvAction.OPEN_BATTERY_SETTINGS,
                actionZh = "去设置",
                actionEn = "Settings",
            ),
            NativeEnvCheck(
                id = "perm.overlay",
                group = NativeEnvGroup.PERMISSION,
                state = if (overlay) NativeEnvState.OK else NativeEnvState.INFO,
                titleZh = "悬浮窗",
                titleEn = "Overlay",
                detailZh = if (overlay) "已授权" else "未授权。只影响悬浮球与特效，不影响聊天。",
                detailEn = if (overlay) "Granted" else "Not granted. Only affects the floating bubble and effects, not chat.",
                action = if (overlay) NativeEnvAction.NONE else NativeEnvAction.OPEN_OVERLAY_SETTINGS,
                actionZh = "去授权",
                actionEn = "Grant",
            ),
        )
    }

    // ---- extensions --------------------------------------------------------------------------

    private fun extensionChecks(): List<NativeEnvCheck> {
        val managed = countSkillDirs(File(File(TermuxConstants.TERMUX_HOME_DIR, ".codex"), "skills"))
        val claudeVisible = countSkillDirs(File(File(TermuxConstants.TERMUX_HOME_DIR, ".claude"), "skills"))
        val mcp = runCatching { NativeMcpConfigStore.load() }.getOrDefault(emptyList())
        return listOf(
            NativeEnvCheck(
                id = "ext.skills",
                group = NativeEnvGroup.EXTENSIONS,
                state = when {
                    managed == 0 && claudeVisible == 0 -> NativeEnvState.INFO
                    managed == claudeVisible -> NativeEnvState.OK
                    else -> NativeEnvState.WARN
                },
                titleZh = "Skill 可见性",
                titleEn = "Skill visibility",
                detailZh = when {
                    managed == 0 && claudeVisible == 0 -> "还没有安装 Skill"
                    managed == claudeVisible -> "Codex 与 Claude 各有 $managed 个"
                    else -> "Codex 看到 $managed 个，Claude 只看到 $claudeVisible 个 —— 在 Claude 后端可能用不上。"
                },
                detailEn = when {
                    managed == 0 && claudeVisible == 0 -> "No skills installed yet"
                    managed == claudeVisible -> "$managed visible to each backend"
                    else -> "Codex sees $managed, Claude sees only $claudeVisible; skills may be unusable on Claude."
                },
                action = if (managed == claudeVisible) NativeEnvAction.NONE else NativeEnvAction.OPEN_SKILLS,
                actionZh = "去管理",
                actionEn = "Manage",
            ),
            NativeEnvCheck(
                id = "ext.mcp",
                group = NativeEnvGroup.EXTENSIONS,
                state = NativeEnvState.INFO,
                titleZh = "MCP 服务器",
                titleEn = "MCP servers",
                detailZh = if (mcp.isEmpty()) "未配置" else "已配置 ${mcp.size} 个，其中 ${mcp.count { it.enabled }} 个启用",
                detailEn = if (mcp.isEmpty()) "None configured" else "${mcp.size} configured, ${mcp.count { it.enabled }} enabled",
                action = NativeEnvAction.OPEN_MCP,
                actionZh = "去配置",
                actionEn = "Configure",
            ),
        )
    }

    /** Counts discoverable skills the same way both bridges scan: any directory holding SKILL.md. */
    private fun countSkillDirs(root: File, depth: Int = 0): Int {
        if (!root.isDirectory || depth > 8) return 0
        var count = if (File(root, "SKILL.md").isFile) 1 else 0
        root.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }?.forEach {
            count += countSkillDirs(it, depth + 1)
        }
        return count
    }

    // ---- export ------------------------------------------------------------------------------

    /** Plain-text report for sharing. Contains no keys, tokens or subscription URLs. */
    fun exportText(context: Context, prefs: SharedPreferences, checks: List<NativeEnvCheck>): String {
        val builder = StringBuilder()
        builder.appendLine("Fcode 环境自检 / environment report")
        builder.appendLine("time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        builder.appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
        builder.appendLine("abi: ${Build.SUPPORTED_ABIS?.joinToString(", ").orEmpty()}")
        builder.appendLine("app: ${com.termux.BuildConfig.VERSION_NAME} (${com.termux.BuildConfig.VERSION_CODE}) debug=${com.termux.BuildConfig.DEBUG}")
        builder.appendLine("workspace: ${currentWorkspace(prefs).absolutePath}")
        builder.appendLine("env:")
        NativeProcessEnv.base().forEach { (key, value) -> builder.appendLine("  $key=$value") }
        builder.appendLine("checks:")
        ordered(checks).forEach { check ->
            val flag = when (check.state) {
                NativeEnvState.OK -> "OK  "
                NativeEnvState.WARN -> "WARN"
                NativeEnvState.FAIL -> "FAIL"
                NativeEnvState.INFO -> "INFO"
            }
            builder.appendLine("  [$flag] ${check.id} · ${check.titleEn}: ${check.detailEn}")
        }
        return builder.toString()
    }
}
