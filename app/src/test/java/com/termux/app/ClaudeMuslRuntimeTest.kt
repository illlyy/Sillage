package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Verifies ELF PT_INTERP detection for the musl loader routing. */
class ClaudeMuslRuntimeTest {

    private fun writeElf(interp: String?): File {
        val file = File.createTempFile("elf", ".bin")
        val bytes = ByteArray(256)
        bytes[0] = 0x7F; bytes[1] = 'E'.code.toByte(); bytes[2] = 'L'.code.toByte(); bytes[3] = 'F'.code.toByte()
        bytes[4] = 2 // ELF64
        bytes[5] = 1 // little endian
        // e_phoff at 32 (LE64)
        bytes[32] = 64
        // e_phentsize at 54 (LE16)
        bytes[54] = 56
        // e_phnum at 56 (LE16)
        bytes[56] = 1
        if (interp != null) {
            // program header at offset 64: p_type=PT_INTERP(3) at +0, p_offset at +8, p_filesz at +32
            bytes[64] = 3
            val strOff = 128
            bytes[72] = strOff.toByte()
            bytes[73] = (strOff shr 8).toByte()
            bytes[74] = (strOff shr 16).toByte()
            bytes[75] = (strOff shr 24).toByte()
            bytes[96] = interp.length.toByte()
            bytes[97] = (interp.length shr 8).toByte()
            val interpBytes = interp.toByteArray(Charsets.US_ASCII)
            interpBytes.copyInto(bytes, strOff)
        } else {
            // PT_LOAD (no interp)
            bytes[64] = 1
        }
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun `detects dynamic binary with interp`() {
        val file = writeElf("/lib/ld-musl-aarch64.so.1")
        assertTrue(ClaudeMuslRuntime.needsMuslLoader(file))
        assertEquals("/lib/ld-musl-aarch64.so.1", ClaudeMuslRuntime.findInterpreter(file))
        file.delete()
    }

    @Test
    fun `static binary has no interp`() {
        val file = writeElf(null)
        assertFalse(ClaudeMuslRuntime.needsMuslLoader(file))
        assertNull(ClaudeMuslRuntime.findInterpreter(file))
        file.delete()
    }

    @Test
    fun `non-elf or tiny file is not dynamic`() {
        val file = File.createTempFile("plain", ".bin")
        file.writeText("not an elf")
        assertFalse(ClaudeMuslRuntime.needsMuslLoader(file))
        file.delete()
    }
}
