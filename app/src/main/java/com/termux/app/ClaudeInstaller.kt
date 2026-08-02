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
 * Downloads and installs the official Claude Code CLI (musl arm64 static binary) into the
 * app-private Termux bin directory, mirroring CodexInstaller's GitHub Releases flow.
 */
internal object ClaudeInstaller {
    private const val TAG = "ClaudeInstaller"
    private const val RELEASE_BASE = "https://github.com/anthropics/claude-code/releases/download/"
    private const val VERSION = "v2.1.220"
    private const val ARCHIVE = "claude-linux-arm64-musl.tar.gz"
    private const val EXPECTED_SHA256 = "25aa9d57b68b7ea150ecce4a8ecbc2d55f292453f2ae99591cadff42f8181293"

    const val BINARY_NAME = "claude"

    interface Progress {
        fun onStage(stage: String, detail: String)
        fun onDownloadProgress(downloaded: Long, total: Long, percent: Int)
        fun onComplete(success: Boolean, error: String?)
    }

    fun installUrl(): String = RELEASE_BASE + VERSION + "/" + ARCHIVE

    fun isInstalled(): Boolean =
        File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, BINARY_NAME).canExecute()

    fun installAsync(context: Context, progress: Progress) {
        Thread({
            var success = false
            var error: String? = null
            try {
                ensureLayout()
                val archive = File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, ARCHIVE)
                progress.onStage("下载", "正在下载 Claude CLI ($VERSION)…")
                val digest = download(context, archive, progress)
                if (!digest.equals(EXPECTED_SHA256, ignoreCase = true)) {
                    throw IOException("SHA-256 校验失败（期望 $EXPECTED_SHA256，实际 $digest）")
                }
                progress.onStage("解压", "正在解压可执行文件…")
                val destination = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, BINARY_NAME)
                extractClaude(archive, destination)
                if (!destination.setExecutable(true, false)) throw IOException("无法设置可执行权限")
                archive.delete()
                success = true
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
            }
            progress.onComplete(success, error)
        }, "ClaudeInstaller").start()
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
