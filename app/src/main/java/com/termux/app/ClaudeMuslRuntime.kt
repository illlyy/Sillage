package com.termux.app

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import java.io.File
import java.io.RandomAccessFile

/**
 * The official `claude-linux-arm64-musl` binary is dynamically linked against musl libc:
 * its ELF header carries `PT_INTERP: /lib/ld-musl-aarch64.so.1`. Android's bionic libc has
 * no musl loader, so a direct exec fails with ENOENT ("error=2"). This runtime detects the
 * dynamic binary and installs the loader (bundled in app assets, extracted from Alpine's
 * musl package) so the bridge can spawn the CLI through it as an explicit interpreter.
 */
internal object ClaudeMuslRuntime {
    const val LOADER_FILE = "ld-musl-aarch64.so.1"
    const val ASSET_PATH = "claude/$LOADER_FILE"
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

    /** Copies the bundled musl loader into the Termux lib dir. Returns null on success. */
    fun installFromAssets(context: Context): String? {
        return try {
            val target = loaderFile()
            if (target.isFile) return null
            val parent = target.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) return "无法创建 lib 目录"
            context.assets.open(ASSET_PATH).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (!target.setExecutable(true, false)) return "无法设置加载器权限"
            null
        } catch (t: Throwable) {
            t.message ?: t.javaClass.simpleName
        }
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
