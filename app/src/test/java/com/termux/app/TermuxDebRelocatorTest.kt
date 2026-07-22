package com.termux.app

import com.termux.shared.termux.TermuxConstants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxDebRelocatorTest {
    @Test
    fun patchesBinaryPrefixAndHomeWithoutChangingLength() {
        val source = byteArrayOf(0, 1, 2) +
            TermuxDebRelocator.LEGACY_PREFIX.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, 3) +
            TermuxDebRelocator.LEGACY_HOME.toByteArray(Charsets.US_ASCII)

        val patched = TermuxDebRelocator.patchBytes(source)
        val text = patched.toString(Charsets.US_ASCII)

        assertTrue(text.contains(TermuxConstants.TERMUX_PREFIX_DIR_PATH))
        assertTrue(text.contains(TermuxConstants.TERMUX_HOME_DIR_PATH))
        assertFalse(text.contains("/data/data/com.termux/files"))
        assertArrayEquals(source.copyOfRange(0, 3), patched.copyOfRange(0, 3))
        assertTrue(source.size == patched.size)
    }
}
