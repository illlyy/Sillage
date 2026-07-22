package com.termux.app

import android.app.Activity
import android.system.Os
import android.system.OsConstants
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal enum class DevelopmentToolCategory {
    RUNTIME,
    LANGUAGE,
    BUILD,
    TERMINAL,
    DATA,
    MEDIA,
}

internal data class DevelopmentTool(
    val id: String,
    val category: DevelopmentToolCategory,
    val titleZh: String,
    val titleEn: String,
    val descriptionZh: String,
    val descriptionEn: String,
    val sizeHintZh: String,
    val sizeHintEn: String,
    val aptPackages: List<String>,
    val probeExecutables: List<String>,
    val recommended: Boolean = false,
    val codexCli: Boolean = false,
)

internal object DevelopmentToolCatalog {
    val tools: List<DevelopmentTool> = listOf(
        DevelopmentTool(
            id = "codex",
            category = DevelopmentToolCategory.RUNTIME,
            titleZh = "Codex CLI",
            titleEn = "Codex CLI",
            descriptionZh = "原生聊天、WebUI 与终端 Codex 命令的核心运行组件",
            descriptionEn = "Core runtime for native chat, WebUI and Codex commands in Termux",
            sizeHintZh = "官方下载",
            sizeHintEn = "Official download",
            aptPackages = emptyList(),
            probeExecutables = listOf("codex"),
            recommended = true,
            codexCli = true,
        ),
        DevelopmentTool(
            "git", DevelopmentToolCategory.RUNTIME, "Git", "Git",
            "版本控制、克隆仓库和补丁工作流", "Version control, repository cloning and patch workflows",
            "约 35 MB", "About 35 MB", listOf("git"), listOf("git"), recommended = true,
        ),
        DevelopmentTool(
            "network", DevelopmentToolCategory.RUNTIME, "网络与 JSON 工具", "Network & JSON tools",
            "curl、wget、OpenSSL 与 jq", "curl, wget, OpenSSL and jq",
            "约 30 MB", "About 30 MB", listOf("curl", "wget", "openssl", "jq"), listOf("curl", "wget", "openssl", "jq"), recommended = true,
        ),
        DevelopmentTool(
            "search", DevelopmentToolCategory.RUNTIME, "搜索与文件工具", "Search & file tools",
            "ripgrep、fd、findutils、diff 与 patch", "ripgrep, fd, findutils, diff and patch",
            "约 20 MB", "About 20 MB", listOf("ripgrep", "fd", "findutils", "diffutils", "patch"), listOf("rg", "fd", "find", "patch"), recommended = true,
        ),
        DevelopmentTool(
            "unix-utils", DevelopmentToolCategory.RUNTIME, "基础 Unix 工具", "Core Unix tools",
            "coreutils、procps、sed、grep 与 less", "coreutils, procps, sed, grep and less",
            "约 20 MB", "About 20 MB", listOf("coreutils", "procps", "sed", "grep", "less"), listOf("ls", "ps", "sed", "grep", "less"), recommended = true,
        ),        DevelopmentTool(
            "archives", DevelopmentToolCategory.RUNTIME, "压缩与归档", "Archives & compression",
            "tar、zip、unzip 与 gzip", "tar, zip, unzip and gzip",
            "约 15 MB", "About 15 MB", listOf("tar", "zip", "unzip", "gzip"), listOf("tar", "zip", "unzip", "gzip"), recommended = true,
        ),
        DevelopmentTool(
            "node", DevelopmentToolCategory.LANGUAGE, "Node.js LTS", "Node.js LTS",
            "JavaScript、TypeScript、npm 和前端工具链", "JavaScript, TypeScript, npm and frontend tooling",
            "约 120 MB", "About 120 MB", listOf("nodejs-lts"), listOf("node", "npm"), recommended = true,
        ),
        DevelopmentTool(
            "python", DevelopmentToolCategory.LANGUAGE, "Python", "Python",
            "Python 解释器、pip 和脚本环境", "Python interpreter, pip and scripting environment",
            "约 95 MB", "About 95 MB", listOf("python"), listOf("python", "pip"), recommended = true,
        ),
        DevelopmentTool(
            "rust", DevelopmentToolCategory.LANGUAGE, "Rust", "Rust",
            "rustc、Cargo 和原生 Rust 工具链", "rustc, Cargo and the native Rust toolchain",
            "约 250 MB", "About 250 MB", listOf("rust"), listOf("rustc", "cargo"),
        ),
        DevelopmentTool(
            "golang", DevelopmentToolCategory.LANGUAGE, "Go", "Go",
            "Go 编译器和模块工具", "Go compiler and module tools",
            "约 210 MB", "About 210 MB", listOf("golang"), listOf("go"),
        ),
        DevelopmentTool(
            "java", DevelopmentToolCategory.LANGUAGE, "OpenJDK 21", "OpenJDK 21",
            "Java、Javac 与 JVM 命令行开发环境", "Java, Javac and JVM command-line development",
            "约 330 MB", "About 330 MB", listOf("openjdk-21"), listOf("java", "javac"),
        ),
        DevelopmentTool(
            "native-build", DevelopmentToolCategory.BUILD, "C / C++ 构建工具", "C / C++ build tools",
            "Clang、Make、CMake、Ninja 与 pkg-config", "Clang, Make, CMake, Ninja and pkg-config",
            "约 300 MB", "About 300 MB", listOf("clang", "make", "cmake", "ninja", "pkg-config"), listOf("clang", "make", "cmake", "ninja"),
        ),
        DevelopmentTool(
            "shells", DevelopmentToolCategory.TERMINAL, "扩展 Shell", "Additional shells",
            "Zsh 与 Fish 交互式 Shell", "Zsh and Fish interactive shells",
            "约 45 MB", "About 45 MB", listOf("zsh", "fish"), listOf("zsh", "fish"),
        ),
        DevelopmentTool(
            "editors", DevelopmentToolCategory.TERMINAL, "终端编辑器", "Terminal editors",
            "Vim 与 Nano", "Vim and Nano",
            "约 55 MB", "About 55 MB", listOf("vim", "nano"), listOf("vim", "nano"), recommended = true,
        ),
        DevelopmentTool(
            "tmux", DevelopmentToolCategory.TERMINAL, "Tmux", "Tmux",
            "持久终端会话和窗口分屏", "Persistent terminal sessions and split panes",
            "约 10 MB", "About 10 MB", listOf("tmux"), listOf("tmux"), recommended = true,
        ),
        DevelopmentTool(
            "ssh", DevelopmentToolCategory.TERMINAL, "OpenSSH", "OpenSSH",
            "SSH 客户端、服务端与密钥工具", "SSH client, server and key utilities",
            "约 25 MB", "About 25 MB", listOf("openssh"), listOf("ssh", "sshd", "ssh-keygen"), recommended = true,
        ),
        DevelopmentTool(
            "sqlite", DevelopmentToolCategory.DATA, "SQLite", "SQLite",
            "轻量数据库和 sqlite3 命令行", "Lightweight database and sqlite3 command line",
            "约 15 MB", "About 15 MB", listOf("sqlite"), listOf("sqlite3"),
        ),
        DevelopmentTool(
            "ffmpeg", DevelopmentToolCategory.MEDIA, "FFmpeg", "FFmpeg",
            "音视频检查、转换和编码", "Audio/video inspection, conversion and encoding",
            "约 180 MB", "About 180 MB", listOf("ffmpeg"), listOf("ffmpeg", "ffprobe"),
        ),
        DevelopmentTool(
            "imagemagick", DevelopmentToolCategory.MEDIA, "ImageMagick", "ImageMagick",
            "图片格式转换、缩放与批处理", "Image conversion, resizing and batch processing",
            "约 90 MB", "About 90 MB", listOf("imagemagick"), listOf("magick"),
        ),
    )

    val recommendedIds: Set<String> = tools.filter { it.recommended }.mapTo(linkedSetOf()) { it.id }
    val allIds: Set<String> = tools.mapTo(linkedSetOf()) { it.id }

    fun byId(id: String): DevelopmentTool? = tools.firstOrNull { it.id == id }
}

internal object TermuxDebRelocator {
    internal const val LEGACY_PREFIX = "/data/data/com.termux/files/usr"
    internal const val LEGACY_HOME = "/data/data/com.termux/files/home"

    private val replacements: List<Pair<ByteArray, ByteArray>> = listOf(
        LEGACY_PREFIX.toByteArray(Charsets.US_ASCII) to
            TermuxConstants.TERMUX_PREFIX_DIR_PATH.toByteArray(Charsets.US_ASCII),
        LEGACY_HOME.toByteArray(Charsets.US_ASCII) to
            TermuxConstants.TERMUX_HOME_DIR_PATH.toByteArray(Charsets.US_ASCII),
    ).onEach { (oldValue, newValue) ->
        check(oldValue.size == newValue.size) {
            "Termux package paths must be replaced with equal-length paths"
        }
    }

    internal fun patchBytes(source: ByteArray): ByteArray {
        val result = source.clone()
        replacements.forEach { (oldValue, newValue) ->
            var index = 0
            while (index <= result.size - oldValue.size) {
                var matches = true
                for (offset in oldValue.indices) {
                    if (result[index + offset] != oldValue[offset]) {
                        matches = false
                        break
                    }
                }
                if (matches) {
                    System.arraycopy(newValue, 0, result, index, newValue.size)
                    index += newValue.size
                } else {
                    index++
                }
            }
        }
        return result
    }

    fun relocateExtractedPackage(root: File) {
        val legacyAppRoot = File(root, "data/data/com.termux")
        val legacyPrefix = File(legacyAppRoot, "files/usr")
        val relocatedPrefix = File(root, TermuxConstants.TERMUX_PREFIX_DIR_PATH.removePrefix("/"))
        if (!legacyPrefix.isDirectory) {
            error("Package does not contain the expected Termux prefix")
        }
        relocatedPrefix.parentFile?.mkdirs()
        if (relocatedPrefix.exists()) relocatedPrefix.deleteRecursively()
        if (!legacyPrefix.renameTo(relocatedPrefix)) {
            error("Unable to relocate package prefix")
        }
        legacyAppRoot.deleteRecursively()
        patchTree(root)
        normalizeMaintainerScriptModes(root)
    }

    private fun normalizeMaintainerScriptModes(root: File) {
        val controlDir = File(root, "DEBIAN")
        listOf("preinst", "postinst", "prerm", "postrm", "config").forEach { name ->
            val script = File(controlDir, name)
            if (script.isFile) Os.chmod(script.absolutePath, 0b111101101) // 0755
        }
    }

    private fun patchTree(file: File) {
        val mode = Os.lstat(file.absolutePath).st_mode
        when {
            OsConstants.S_ISLNK(mode) -> patchSymlink(file)
            OsConstants.S_ISDIR(mode) -> file.listFiles()?.forEach(::patchTree)
            OsConstants.S_ISREG(mode) -> patchFile(file)
        }
    }

    private fun patchSymlink(file: File) {
        val target = Os.readlink(file.absolutePath)
        val patched = target
            .replace(LEGACY_PREFIX, TermuxConstants.TERMUX_PREFIX_DIR_PATH)
            .replace(LEGACY_HOME, TermuxConstants.TERMUX_HOME_DIR_PATH)
        if (patched == target) return
        if (!file.delete()) error("Unable to replace package symlink: ${file.absolutePath}")
        Os.symlink(patched, file.absolutePath)
    }

    private fun patchFile(file: File) {
        val maxPatternSize = replacements.maxOf { it.first.size }
        val buffer = ByteArray(1024 * 1024 + maxPatternSize - 1)
        RandomAccessFile(file, "rw").use { random ->
            var position = 0L
            while (position < random.length()) {
                random.seek(position)
                val read = random.read(buffer)
                if (read <= 0) break
                val atEnd = position + read >= random.length()
                val safeAdvance = if (atEnd) read else read - maxPatternSize + 1
                replacements.forEach { (oldValue, newValue) ->
                    var index = 0
                    while (index <= read - oldValue.size && index < safeAdvance) {
                        var matches = true
                        for (offset in oldValue.indices) {
                            if (buffer[index + offset] != oldValue[offset]) {
                                matches = false
                                break
                            }
                        }
                        if (matches) {
                            random.seek(position + index)
                            random.write(newValue)
                            System.arraycopy(newValue, 0, buffer, index, newValue.size)
                            index += newValue.size
                        } else {
                            index++
                        }
                    }
                }
                position += safeAdvance
            }
        }
    }
}

internal object DevelopmentToolInstaller {
    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
    private val running = AtomicBoolean(false)
    @Volatile private var process: Process? = null

    fun isBootstrapInstalled(): Boolean =
        File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "bash").canExecute() &&
            File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "apt-get").canExecute()

    fun isInstalled(tool: DevelopmentTool): Boolean =
        tool.probeExecutables.isNotEmpty() && tool.probeExecutables.all {
            File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, it).canExecute()
        }

    fun installedIds(): Set<String> = DevelopmentToolCatalog.tools
        .filter(::isInstalled)
        .mapTo(linkedSetOf()) { it.id }

    fun logFile(): File = File(TermuxConstants.TERMUX_HOME_DIR_PATH, "fcode-tool-installer.log")

    fun installAptTools(
        activity: Activity,
        tools: List<DevelopmentTool>,
        onProgress: (String, String) -> Unit,
        onComplete: (Set<String>, File) -> Unit,
        onError: (String, File) -> Unit,
    ) {
        val toolsToInstall = tools.filterNot(::isInstalled)
        val packages = toolsToInstall.flatMap { it.aptPackages }.distinct()
        if (packages.isEmpty()) {
            onComplete(installedIds(), logFile())
            return
        }
        if (!isBootstrapInstalled()) {
            onError("The Termux base environment is not installed", logFile())
            return
        }
        if (!running.compareAndSet(false, true)) {
            onError("Another tool installation is already running", logFile())
            return
        }
        Thread({
            val log = logFile()
            try {
                val bin = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
                val workRoot = File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, "fcode-tool-installer")
                val downloadDir = File(workRoot, "downloads")
                val extractedRoot = File(workRoot, "extracted")
                val patchedDir = File(workRoot, "patched")
                workRoot.deleteRecursively()
                File(downloadDir, "partial").mkdirs()
                extractedRoot.mkdirs()
                patchedDir.mkdirs()
                log.parentFile?.mkdirs()
                FileOutputStream(log, true).bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.append("\n\n=== ${timestamp()} ===\nPackages: ${packages.joinToString(" ")}\n")
                    writer.flush()
                    runCommand(activity, writer, onProgress, "更新软件源", listOf("$bin/apt-get", "update"))
                    runCommand(
                        activity, writer, onProgress, "下载软件包",
                        listOf(
                            "$bin/apt-get",
                            "-o", "Dir::Cache::archives=${downloadDir.absolutePath}",
                            "install", "--download-only", "--reinstall", "-y",
                        ) + packages,
                    )
                    val archives = downloadDir.listFiles { file -> file.extension == "deb" }
                        ?.sortedBy { it.name }
                        .orEmpty()
                    if (archives.isEmpty()) error("Package manager did not download any packages")
                    archives.forEachIndexed { index, archive ->
                        activity.runOnUiThread {
                            onProgress("install", "正在适配软件包 ${index + 1}/${archives.size}：${archive.name}")
                        }
                        val extracted = File(extractedRoot, index.toString())
                        val patched = File(patchedDir, archive.name)
                        runCommand(
                            activity, writer, onProgress, "解包 ${archive.name}",
                            listOf("$bin/dpkg-deb", "--raw-extract", archive.absolutePath, extracted.absolutePath),
                            showOutput = false,
                        )
                        TermuxDebRelocator.relocateExtractedPackage(extracted)
                        runCommand(
                            activity, writer, onProgress, "重建 ${archive.name}",
                            listOf("$bin/dpkg-deb", "--build", extracted.absolutePath, patched.absolutePath),
                            showOutput = false,
                        )
                        extracted.deleteRecursively()
                    }
                    val patchedArchives = patchedDir.listFiles { file -> file.extension == "deb" }
                        ?.sortedBy { it.name }
                        .orEmpty()
                    val installCommand = listOf("$bin/dpkg", "--force-confold", "--install") +
                        patchedArchives.map { it.absolutePath }
                    val firstInstall = runCommand(
                        activity, writer, onProgress, "安装软件包", installCommand, allowFailure = true,
                    )
                    runCommand(
                        activity, writer, onProgress, "配置软件包",
                        listOf("$bin/dpkg", "--configure", "--pending"),
                        allowFailure = firstInstall != 0,
                    )
                    if (firstInstall != 0) {
                        runCommand(activity, writer, onProgress, "重试安装软件包", installCommand)
                        runCommand(
                            activity, writer, onProgress, "完成软件包配置",
                            listOf("$bin/dpkg", "--configure", "--pending"),
                        )
                    }
                }
                workRoot.deleteRecursively()
                File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".fcode-tool-environment-v2")
                    .writeText("${timestamp()}\n${tools.joinToString(",") { it.id }}\n", Charsets.UTF_8)
                activity.runOnUiThread { onComplete(installedIds(), log) }
            } catch (error: Exception) {
                val message = "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                runCatching { log.appendText("ERROR: $message\n", Charsets.UTF_8) }
                activity.runOnUiThread { onError(message, log) }
            } finally {
                process = null
                running.set(false)
            }
        }, "FcodeToolInstaller").start()
    }

    fun cancel() {
        process?.destroy()
    }

    private fun runCommand(
        activity: Activity,
        writer: java.io.BufferedWriter,
        onProgress: (String, String) -> Unit,
        label: String,
        command: List<String>,
        showOutput: Boolean = true,
        allowFailure: Boolean = false,
    ): Int {
        activity.runOnUiThread { onProgress("install", label) }
        writer.appendLine("\$ ${command.joinToString(" ")}")
        writer.flush()
        val builder = ProcessBuilder(command)
        builder.environment()["HOME"] = TermuxConstants.TERMUX_HOME_DIR_PATH
        builder.environment()["PREFIX"] = TermuxConstants.TERMUX_PREFIX_DIR_PATH
        builder.environment()["TMPDIR"] = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH
        builder.environment()["PATH"] = "${TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH}:/system/bin"
        builder.environment()["DEBIAN_FRONTEND"] = "noninteractive"
        builder.environment()["APT_LISTCHANGES_FRONTEND"] = "none"
        builder.redirectErrorStream(true)
        val started = builder.start()
        process = started
        var lastUpdate = 0L
        started.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                writer.appendLine(line)
                val now = android.os.SystemClock.uptimeMillis()
                if (showOutput && now - lastUpdate >= 120L) {
                    writer.flush()
                    lastUpdate = now
                    activity.runOnUiThread { onProgress("install", line.takeLast(220)) }
                }
            }
        }
        writer.flush()
        val exitCode = started.waitFor()
        if (exitCode != 0 && !allowFailure) error("$label failed with code $exitCode")
        return exitCode
    }
}
