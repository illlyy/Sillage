package com.termux.app

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Installs the official Claude Code CLI into the app-private Termux prefix.
 *
 * Two distributions, picked by what the device has: with Node.js present the CLI is installed from
 * npm and run as a JS entry through Node; without it the official musl arm64 binary is downloaded
 * from GitHub Releases (SHA-256 checked), mirroring CodexInstaller's flow. Node is strongly
 * preferred — the official binary is Bun-based and Android's seccomp filter blocks a syscall it
 * needs during startup (exit 159).
 */
internal object ClaudeInstaller {
    private const val TAG = "ClaudeInstaller"
    private const val RELEASE_BASE = "https://github.com/anthropics/claude-code/releases/download/"
    private const val VERSION = "v2.1.220"
    private const val ARCHIVE = "claude-linux-arm64-musl.tar.gz"
    private const val EXPECTED_SHA256 = "25aa9d57b68b7ea150ecce4a8ecbc2d55f292453f2ae99591cadff42f8181293"

    const val BINARY_NAME = "claude"

    /** npm package carrying the Node.js distribution of the CLI. */
    const val NPM_PACKAGE = "@anthropic-ai/claude-code"

    /**
     * Preferred npm spec — the newest published release. Anthropic's 2.1.x line puts a native
     * launcher at `bin/claude` whose platform optionalDependencies omit Android, so an install is
     * only usable when the package still carries a plain JS entry that Node can execute directly.
     * [installViaNpm] verifies that after installing and retries with [FALLBACK_NPM_SPEC] when the
     * newest release leaves nothing runnable behind, so the device is never left with a stub.
     */
    private const val PREFERRED_NPM_SPEC = "$NPM_PACKAGE@latest"

    /**
     * Last release shipping a plain `cli.js` (a ~14 MB bundle with a `node` shebang); 2.1.113
     * switched `bin.claude` to `bin/claude.exe` and moved the CLI into per-platform packages.
     * The safety net when the newest release leaves no Node-runnable entry behind.
     */
    private const val FALLBACK_NPM_SPEC = "$NPM_PACKAGE@2.1.112"

    interface Progress {
        fun onStage(stage: String, detail: String)
        fun onDownloadProgress(downloaded: Long, total: Long, percent: Int)
        fun onComplete(success: Boolean, error: String?)
    }

    fun installUrl(): String = RELEASE_BASE + VERSION + "/" + ARCHIVE

    /** Directory npm uses for the global package install. */
    fun packageDir(): File =
        File(TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH, "node_modules/$NPM_PACKAGE")

    private fun binEntry(): File = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, BINARY_NAME)

    /** The JS file Node should execute, or null when no Node-runnable install exists. */
    fun nodeEntry(): File? = resolveNodeEntry(packageDir(), binEntry())

    /**
     * Resolves the Node entry of an npm install, newest packaging first:
     * 1. `cli.js` in the package root — the bundled CLI in every JS-entry release.
     * 2. Whatever `package.json` declares (`bin.claude` / `bin` string / `main`), for releases
     *    that rename or move the bundle.
     * 3. `bin/claude` itself when it is a shebang script — an older 2.0.x install, where npm
     *    linked the JS entry directly into `bin`.
     *
     * A native-launcher release matches none of these, which is exactly how the installer tells a
     * usable install from a stub. Takes both paths as parameters so it is unit-testable off-device.
     */
    fun resolveNodeEntry(packageDir: File, binEntry: File): File? {
        val cli = File(packageDir, "cli.js")
        if (cli.isFile) return cli
        val declared = declaredEntry(packageDir)
        if (declared != null) return declared
        if (ClaudeAgentBridge.isNodeScript(binEntry)) return binEntry
        return null
    }

    /** Extensions Node loads as a script. */
    private val NODE_SCRIPT_EXTENSIONS = setOf("js", "cjs", "mjs")

    /**
     * True when Node can execute [file]: a JS extension, or a `node` shebang for the extensionless
     * entries some packages declare.
     *
     * Existence alone is not enough. Since 2.1.113 the manifest declares
     * `bin.claude = "bin/claude.exe"`, and that Windows launcher ships in the tarball on *every*
     * platform, so an existence check accepts it and every launch then dies with
     * `ERR_UNKNOWN_FILE_EXTENSION`. Worse, it makes [resolveNodeEntry] return non-null, which
     * silently defeats the [FALLBACK_NPM_SPEC] retry in [installViaNpm].
     */
    private fun isNodeRunnable(file: File): Boolean =
        file.isFile && (file.extension.lowercase() in NODE_SCRIPT_EXTENSIONS
            || ClaudeAgentBridge.isNodeScript(file))

    /** `bin.claude` / `bin` / `main` from package.json, resolved inside [packageDir]. */
    private fun declaredEntry(packageDir: File): File? {
        val manifest = File(packageDir, "package.json")
        if (!manifest.isFile) return null
        val json = runCatching { org.json.JSONObject(manifest.readText(Charsets.UTF_8)) }.getOrNull() ?: return null
        val candidates = ArrayList<String>(3)
        when (val bin = json.opt("bin")) {
            is org.json.JSONObject -> {
                bin.optString(BINARY_NAME).takeIf { it.isNotBlank() }?.let(candidates::add)
                // A single-binary package may name the key after the package instead of the command.
                bin.keys().forEach { key -> bin.optString(key).takeIf { it.isNotBlank() }?.let(candidates::add) }
            }
            is String -> if (bin.isNotBlank()) candidates.add(bin)
        }
        json.optString("main").takeIf { it.isNotBlank() }?.let(candidates::add)
        for (candidate in candidates) {
            val resolved = runCatching { File(packageDir, candidate).canonicalFile }.getOrNull() ?: continue
            // Never follow a manifest outside its own package directory.
            val root = runCatching { packageDir.canonicalFile }.getOrNull() ?: continue
            if (!resolved.path.startsWith(root.path)) continue
            if (isNodeRunnable(resolved)) return resolved
        }
        return null
    }

    /** Installed npm package version, for diagnostics; empty when it cannot be read. */
    fun installedVersion(): String {
        val manifest = File(packageDir(), "package.json")
        if (!manifest.isFile) return ""
        return runCatching {
            org.json.JSONObject(manifest.readText(Charsets.UTF_8)).optString("version")
        }.getOrDefault("")
    }

    /**
     * The installed package's `bin` field verbatim, for diagnostics; empty when unreadable.
     *
     * A packaging change upstream is what breaks entry resolution, and this is the one field that
     * names it — `{"claude":"cli.js"}` is runnable, `{"claude":"bin/claude.exe"}` is not.
     */
    fun declaredBinSpec(): String {
        val manifest = File(packageDir(), "package.json")
        if (!manifest.isFile) return ""
        return runCatching {
            org.json.JSONObject(manifest.readText(Charsets.UTF_8)).opt("bin")?.toString().orEmpty()
        }.getOrDefault("")
    }

    fun isInstalled(): Boolean = nodeEntry() != null || isDeviceExecutable(binEntry())

    /** Which of the two distributions is actually on the device. */
    enum class InstallKind { NONE, OFFICIAL_BINARY, NPM_PACKAGE }

    /**
     * The distribution the bridge will launch, mirroring its own preference order: a Node-runnable
     * npm entry always wins over the official binary. Two devices on the same app version can be
     * running very different CLIs — the npm path is pinned to a release over a hundred older than
     * the bundled binary — and until settings said so, that was invisible to the user.
     */
    fun installKind(): InstallKind =
        installKind(nodeEntry() != null, isDeviceExecutable(binEntry()))

    internal fun installKind(hasNodeEntry: Boolean, hasOfficialBinary: Boolean): InstallKind = when {
        hasNodeEntry -> InstallKind.NPM_PACKAGE
        hasOfficialBinary -> InstallKind.OFFICIAL_BINARY
        else -> InstallKind.NONE
    }

    /** Version of the bundled official build, without the leading `v`. */
    fun pinnedBinaryVersion(): String = VERSION.removePrefix("v")

    /**
     * Version to show in settings: the installed npm package's own version, else the pinned
     * official build. Empty when nothing recognisable is installed — a guess would be worse than
     * a blank, since the whole point is to let the user trust the number.
     */
    fun displayVersion(kind: InstallKind = installKind()): String = when (kind) {
        InstallKind.NPM_PACKAGE -> installedVersion()
        InstallKind.OFFICIAL_BINARY -> pinnedBinaryVersion()
        InstallKind.NONE -> ""
    }

    /**
     * True when [file] is an ELF image this device can exec — what the official binary install
     * leaves at `bin/claude`.
     *
     * The execute bit alone does not distinguish it: npm links `bin/claude` to the package's
     * `bin/claude.exe` on 2.1.113+ and marks it executable, so an `canExecute()` test reports a
     * Windows launcher as a working install and suppresses the reinstall prompt that would repair
     * it. Internal so [ClaudeInstallerTest] can cover the launcher case.
     */
    internal fun isDeviceExecutable(file: File): Boolean {
        if (!file.isFile || !file.canExecute() || file.length() < 4) return false
        return runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && magic[0] == 0x7F.toByte() && magic[1] == 'E'.code.toByte()
                    && magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)
    }

    /** Removes the Claude CLI (binary and npm package); keeps profiles and transcripts. */
    fun uninstall(): Boolean {
        val binary = binEntry()
        val binaryGone = !binary.exists() || binary.delete()
        val packageGone = packageDir().let { !it.exists() || it.deleteRecursively() }
        return binaryGone && packageGone
    }

    fun installAsync(context: Context, progress: Progress) {
        FcodeLog.event(context, "claude_install_start", org.json.JSONObject()
            .put("nodePresent", File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "node").isFile())
            .put("binaryExists", isInstalled())
            .put("installedVersion", installedVersion()))
        Thread({
            var success = false
            var error: String? = null
            try {
                ensureLayout()
                val node = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "node")
                if (node.isFile()) {
                    // The npm-installed CLI runs through Node.js. The official bun-based musl
                    // binary hangs during initialization on Android, so Node is the reliable path.
                    installViaNpm(context, node, progress)
                } else {
                    installOfficialBinary(context, progress)
                }
                success = true
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
                FcodeLog.event(context, "claude_install_failed", org.json.JSONObject()
                    .put("exceptionClass", t.javaClass.simpleName)
                    .put("message", CodexAppServerBridgeProtocol.redactSensitiveLogLine(
                        error ?: "")))
            }
            FcodeLog.event(context, "claude_install_complete", org.json.JSONObject()
                .put("success", success)
                .put("error", error?.let { CodexAppServerBridgeProtocol.redactSensitiveLogLine(it) }.orEmpty()))
            progress.onComplete(success, error)
        }, "ClaudeInstaller").start()
    }

    /** npm global install of the official CLI package; requires the Node.js dev tool. */
    private fun installViaNpm(context: Context, node: File, progress: Progress) {
        val npm = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "npm")
        if (!npm.isFile()) throw IOException("未找到 npm，请先安装 Node.js 开发工具")
        progress.onStage("npm", "正在通过 npm 安装 Claude Code（首次约 2-3 分钟）…")
        var spec = PREFERRED_NPM_SPEC
        var output = runNpmInstall(context, npm, spec)
        // An install only counts when it left something Node can execute. Since 2.1.113 the
        // package is a launcher whose per-platform binaries have no Android build, and npm exits 0
        // after leaving only `bin/claude.exe` behind, so fall back to the last plain-JS release
        // instead of shipping the user a CLI that cannot start.
        if (nodeEntry() == null) {
            FcodeLog.event(context, "claude_install_npm_no_entry", org.json.JSONObject()
                .put("spec", spec)
                .put("packageVersion", installedVersion())
                .put("declaredBin", declaredBinSpec()))
            progress.onStage("npm", "最新版没有 Node 入口，正在回退到 $FALLBACK_NPM_SPEC …")
            spec = FALLBACK_NPM_SPEC
            output = runNpmInstall(context, npm, spec)
        }
        val entry = nodeEntry()
            ?: throw IOException("npm 安装完成但没有可用的 node 入口（疑似 native stub）：${output.take(300)}")
        val nodeInfo = nodeEnvSummary(node)
        FcodeLog.event(context, "claude_install_npm_ok", org.json.JSONObject()
            .put("spec", spec)
            .put("packageVersion", installedVersion())
            .put("entry", entry.absolutePath)
            .put("entryIsBinLink", entry.absolutePath == binEntry().absolutePath)
            .put("declaredBin", declaredBinSpec())
            .put("nodePlatform", nodeInfo.first)
            .put("nodeVersion", nodeInfo.second)
            .put("outputTail", CodexAppServerBridgeProtocol.redactSensitiveLogLine(output.take(1200))))
    }

    /** Runs one `npm install -g <spec>` and returns its combined output. Throws on a non-zero exit. */
    private fun runNpmInstall(context: Context, npm: File, spec: String): String {
        val home = File(TermuxConstants.TERMUX_HOME_DIR_PATH)
        // --omit=optional: the package's per-platform binaries have no Android build, and the
        // bridge runs the JS entry through Node anyway, so resolving them is pure waste.
        val command = listOf(
            npm.absolutePath, "install", "-g", spec,
            "--no-fund", "--no-audit", "--omit=optional", "--loglevel=error",
        )
        val process = ProcessBuilder(command)
            .directory(home)
            .apply {
                environment()["HOME"] = home.absolutePath
                environment()["TMPDIR"] = File(home, ".tmp").absolutePath
                environment()["npm_config_cache"] = File(home, ".npm").absolutePath
                // npm resolves its interpreter and lifecycle scripts through PATH (shebang
                // `#!/usr/bin/env node`). The app process PATH only lists Android system dirs,
                // so without the Termux bin directory npm fails with `env: 'node': No such
                // file or directory` (exit 127).
                environment()["PATH"] = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin"
            }
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        // Always capture the npm output tail: native-wrapper releases print a postinstall
        // warning yet still exit 0, leaving a non-functional stub behind.
        val safeTail = CodexAppServerBridgeProtocol.redactSensitiveLogLine(output.take(1200))
        if (exit != 0) {
            FcodeLog.event(context, "claude_install_npm_failed", org.json.JSONObject()
                .put("spec", spec)
                .put("exit", exit)
                .put("outputTail", safeTail))
            throw IOException("npm 安装失败（exit $exit）：${output.take(600)}")
        }
        return output
    }

    /** `process.platform` and `process.version` as reported by the device Node, for diagnostics. */
    private fun nodeEnvSummary(node: File): Pair<String, String> {
        val report = runCatching {
            val probe = ProcessBuilder(node.absolutePath, "-p", "process.platform + ' ' + process.version").start()
            probe.inputStream.bufferedReader().use { it.readText().trim() }
        }.getOrDefault("")
        val parts = report.split(" ")
        return if (parts.size >= 2) parts[0] to parts[1] else report to ""
    }

    private fun installOfficialBinary(context: Context, progress: Progress) {
        val archive = File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, ARCHIVE)
        progress.onStage("下载", "正在下载 Claude CLI ($VERSION)…")
        val digest = download(context, archive, progress)
        val shaMatch = digest.equals(EXPECTED_SHA256, ignoreCase = true)
        FcodeLog.event(context, "claude_install_download", org.json.JSONObject()
            .put("version", VERSION)
            .put("sha256Match", shaMatch))
        if (!shaMatch) {
            throw IOException("SHA-256 校验失败（期望 $EXPECTED_SHA256，实际 $digest）")
        }
        progress.onStage("解压", "正在解压可执行文件…")
        val destination = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, BINARY_NAME)
        extractClaude(archive, destination)
        if (!destination.setExecutable(true, false)) throw IOException("无法设置可执行权限")
        archive.delete()
        // The official binary is dynamically linked against musl; install the bundled loader.
        if (ClaudeMuslRuntime.needsMuslLoader(destination) && !ClaudeMuslRuntime.isLoaderPresent()) {
            progress.onStage("musl", "正在安装 musl 运行时…")
            val muslError = ClaudeMuslRuntime.installFromAssets(context)
            if (muslError != null) throw IOException(muslError)
        }
    }

    private fun ensureLayout() {
        File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH).mkdirs()
        File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH).mkdirs()
        File(TermuxConstants.TERMUX_HOME_DIR_PATH).mkdirs()
        File(TermuxConstants.TERMUX_HOME_DIR_PATH, "projects").mkdirs()
    }

    private fun download(context: Context, output: File, progress: Progress): String {
        val mihomo = MihomoManager.get(context)
        val throughMihomo = context.getSharedPreferences("codex_mobile", Context.MODE_PRIVATE)
            .getBoolean("mihomo_route_api", false)
        if (throughMihomo) mihomo.start()
        val target = URL(installUrl())
        val connection = (if (throughMihomo) {
            target.openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", mihomo.mixedPort())))
        } else {
            target.openConnection()
        }) as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "Fcode-Android/0.3.1")
            connection.instanceFollowRedirects = true
            val status = connection.responseCode
            if (status < 200 || status >= 300) throw IOException("下载服务器返回 HTTP $status")
            val total = connection.contentLengthLong
            FcodeLog.event(context, "claude_install_http", org.json.JSONObject()
                .put("status", status)
                .put("totalBytes", total))
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            val buffer = ByteArray(128 * 1024)
            connection.inputStream.use { raw ->
                BufferedInputStream(raw).use { input ->
                    BufferedOutputStream(FileOutputStream(output)).use { out ->
                        var count: Int
                        var lastPercent = -1
                        while (input.read(buffer).also { count = it } != -1) {
                            out.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            received += count
                            if (total > 0) {
                                val percent = (received * 100 / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    progress.onDownloadProgress(received, total, percent)
                                }
                            }
                        }
                    }
                }
            }
            val hex = StringBuilder()
            for (b in digest.digest()) hex.append(String.format(Locale.US, "%02x", b))
            return hex.toString()
        } finally {
            connection.disconnect()
        }
    }

    /** Extracts the single `claude` executable from the release tar.gz (512-byte tar headers). */
    private fun extractClaude(archive: File, destination: File) {
        val temp = File(destination.parentFile, "claude.new")
        if (temp.exists()) temp.delete()
        var found = false
        FileInputStream(archive).use { raw ->
            GZIPInputStream(raw, 128 * 1024).use { tar ->
                BufferedOutputStream(FileOutputStream(temp)).use { out ->
                    val header = ByteArray(512)
                    while (readFully(tar, header)) {
                        if (isZeroBlock(header)) break
                        val name = readString(header, 0, 100)
                        val size = readOctal(header, 124, 12)
                        val isTarget = name == BINARY_NAME || name.endsWith("/" + BINARY_NAME)
                        if (isTarget && header[156].toInt() != '5'.code) {
                            copyExactly(tar, out, size)
                            found = true
                        } else {
                            skipExactly(tar, size)
                        }
                        val padding = (512 - (size % 512)) % 512
                        skipExactly(tar, padding)
                        if (found) break
                    }
                }
            }
        }
        if (!found || temp.length() < 1024 * 1024) {
            temp.delete()
            throw IOException("下载包中没有找到 claude 可执行文件")
        }
        if (destination.exists() && !destination.delete()) throw IOException("无法替换旧版本 Claude")
        if (!temp.renameTo(destination)) throw IOException("无法提交 Claude 安装文件")
    }

    private fun readFully(input: InputStream, data: ByteArray): Boolean {
        var offset = 0
        while (offset < data.size) {
            val count = input.read(data, offset, data.size - offset)
            if (count < 0) return offset > 0
            offset += count
        }
        return true
    }

    private fun isZeroBlock(data: ByteArray): Boolean {
        for (b in data) if (b != 0.toByte()) return false
        return true
    }

    private fun readString(data: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = (offset + length).coerceAtMost(data.size)
        while (end < limit && data[end] != 0.toByte()) end++
        return String(data, offset, end - offset, Charsets.UTF_8)
    }

    private fun readOctal(data: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        val limit = (offset + length).coerceAtMost(data.size)
        for (i in offset until limit) {
            val c = data[i]
            if (c == 0.toByte() || c == ' '.code.toByte()) break
            if (c < '0'.code.toByte() || c > '7'.code.toByte()) break
            value = value * 8 + (c - '0'.code.toByte())
        }
        return value
    }

    private fun copyExactly(input: InputStream, out: BufferedOutputStream, count: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = count
        while (remaining > 0) {
            val read = input.read(buffer, 0, Math.min(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("tar 数据截断")
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipExactly(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) throw IOException("tar 数据截断")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
