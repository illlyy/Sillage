package com.termux.app

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.RandomAccessFile

/**
 * The official `claude-linux-arm64-musl` binary is dynamically linked against musl libc:
 * its ELF header carries `PT_INTERP: /lib/ld-musl-aarch64.so.1`. Android's bionic libc has
 * no musl loader, so a direct exec fails with ENOENT ("error=2"). This runtime detects the
 * dynamic binary, locates (or installs) the musl loader, and the bridge spawns the CLI
 * through it as an explicit interpreter.
 */
internal object ClaudeMuslRuntime {
    const val LOADER_FILE = "ld-musl-aarch64.so.1"
    private const val PT_LOAD = 1
    private const val PT_INTERP = 3

    fun loaderFile(): File = File(TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH, LOADER_FILE)

    fun isLoaderPresent(): Boolean = loaderFile().isFile

    /** True when the binary has a PT_INTERP segment (dynamic linking) and thus needs a loader. */
    fun needsMuslLoader(binary: File): Boolean = findInterpreter(binary) != null

    /** Returns the PT_INTERP string (e.g. "/lib/ld-musl-aarch64.so.1") or null when static. */
    fun findInterpreter(binary: File): String? {
        if (!binary.isFile || binary.length() < 64) return null
        return try {
            RandomAccessFile(binary, "r").use { raf ->
                val header = ByteArray(64)
                raf.readFully(header)
                if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() ||
                    header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
                ) return null
                if (header[4] != 2.toByte()) return null // ELF64 only (arm64)
                val phOff = le64(header, 32)
                val phEntSize = le16(header, 54).toInt()
                val phNum = le16(header, 56).toInt()
                if (phEntSize < 56 || phNum <= 0 || phNum > 128) return null
                for (i in 0 until phNum) {
                    raf.seek(phOff + i * phEntSize.toLong())
                    val ph = ByteArray(56)
                    raf.readFully(ph)
                    if (le32(ph, 0) == PT_INTERP) {
                        val offset = le64(ph, 8)
                        val size = le64(ph, 32)
                        if (size > 0L && size < 256L) {
                            raf.seek(offset)
                            val bytes = ByteArray(size.toInt())
                            raf.readFully(bytes)
                            return String(bytes, Charsets.US_ASCII).trimEnd('\u0000', '\u0020')
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Installs the musl runtime via the Termux package manager (blocking). Returns null on success. */
    fun installBlocking(context: Context, progress: (stage: String, detail: String) -> Unit): String? {
        return try {
            val bin = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
            val aptGet = File(bin, "apt-get")
            if (!aptGet.canExecute()) return "Termux 包管理器不可用，请先在开发工具页安装基础环境"
            val env = HashMap<String, String>()
            env["HOME"] = TermuxConstants.TERMUX_HOME_DIR_PATH
            env["PREFIX"] = TermuxConstants.TERMUX_PREFIX_DIR_PATH
            env["TMPDIR"] = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH
            env["PATH"] = "$bin:/system/bin"
            env["DEBIAN_FRONTEND"] = "noninteractive"
            env["APT_LISTCHANGES_FRONTEND"] = "none"
            progress("musl", "正在更新软件源…")
            runApt(listOf(aptGet.absolutePath, "update"), env)
            progress("musl", "正在安装 musl 运行时…")
            runApt(listOf(aptGet.absolutePath, "install", "-y", "musl"), env)
            if (!isLoaderPresent()) return "musl 已安装但未找到加载器"
            null
        } catch (t: Throwable) {
            t.message ?: t.javaClass.simpleName
        }
    }

    /** Installs the musl runtime via the Termux package manager (async). */
    fun installAsync(context: Context, progress: (stage: String, detail: String) -> Unit, done: (Boolean, String?) -> Unit) {
        Thread({
            val error = installBlocking(context, progress)
            done(error == null, error)
        }, "ClaudeMuslInstall").start()
    }

    private fun runApt(command: List<String>, env: Map<String, String>) {
        val builder = ProcessBuilder(command)
        builder.environment().putAll(env)
        builder.redirectErrorStream(true)
        val started = builder.start()
        started.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                android.util.Log.d("ClaudeMuslRuntime", line.takeLast(300))
            }
        }
        val exit = started.waitFor()
        if (exit != 0) throw IllegalStateException("apt 执行失败（code $exit）")
    }

    private fun le16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun le64(data: ByteArray, offset: Int): Long =
        (le32(data, offset).toLong() and 0xFFFFFFFFL) or
            ((le32(data, offset + 4).toLong() and 0xFFFFFFFFL) shl 32)
}
