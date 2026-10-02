package com.termux.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Entry resolution for the npm-installed CLI. The npm layout changed across releases (the JS
 * bundle moved out of `bin/claude`, which became a native launcher with no Android build), so the
 * bridge must find the runnable JS entry rather than assume one location.
 */
class ClaudeInstallerTest {

    private fun tempDir(): File = Files.createTempDirectory("claude-installer").toFile()

    private fun shebangScript(parent: File, name: String): File =
        File(parent, name).apply { parentFile.mkdirs(); writeText("#!/usr/bin/env node\nconsole.log(1)\n") }

    @Test
    fun `prefers cli js in the package root`() {
        val packageDir = tempDir()
        val cli = File(packageDir, "cli.js").apply { writeText("// bundle") }
        File(packageDir, "package.json").writeText("""{"bin":{"claude":"other.js"},"main":"other.js"}""")
        File(packageDir, "other.js").writeText("// other")
        val bin = shebangScript(tempDir(), "claude")

        assertEquals(cli, ClaudeInstaller.resolveNodeEntry(packageDir, bin))
    }

    @Test
    fun `falls back to the entry declared in package json`() {
        val packageDir = tempDir()
        val declared = File(packageDir, "dist/entry.js").apply { parentFile.mkdirs(); writeText("// entry") }
        File(packageDir, "package.json").writeText("""{"bin":{"claude":"dist/entry.js"}}""")

        assertEquals(declared.canonicalFile, ClaudeInstaller.resolveNodeEntry(packageDir, File(packageDir, "missing")))
    }

    @Test
    fun `uses main when bin is absent`() {
        val packageDir = tempDir()
        val main = File(packageDir, "index.js").apply { writeText("// main") }
        File(packageDir, "package.json").writeText("""{"main":"index.js"}""")

        assertEquals(main.canonicalFile, ClaudeInstaller.resolveNodeEntry(packageDir, File(packageDir, "missing")))
    }

    @Test
    fun `falls back to a shebang bin entry from an older npm layout`() {
        val binDir = tempDir()
        val bin = shebangScript(binDir, "claude")

        assertEquals(bin, ClaudeInstaller.resolveNodeEntry(File(binDir, "no-package"), bin))
    }

    @Test
    fun `ignores a stale shell shim left at the bin entry`() {
        val binDir = tempDir()
        // A leftover sh wrapper would be handed to node and die with a syntax error.
        val shim = File(binDir, "claude").apply { writeText("#!/bin/sh\nexec \"\$0.exe\" \"\$@\"\n") }

        assertNull(ClaudeInstaller.resolveNodeEntry(File(binDir, "no-package"), shim))
    }

    @Test
    fun `a native launcher without any js entry resolves to nothing`() {
        val packageDir = tempDir()
        // A native-launcher release: a manifest whose bin points at a binary, and no JS bundle.
        File(packageDir, "package.json").writeText("""{"bin":{"claude":"vendor/claude"}}""")
        val bin = File(tempDir(), "claude").apply { writeBytes(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())) }

        assertNull(ClaudeInstaller.resolveNodeEntry(packageDir, bin))
    }

    @Test
    fun `rejects the windows launcher 2 1 113 declares as bin claude`() {
        val packageDir = tempDir()
        // The real 2.1.113+ layout: bin.claude points at bin/claude.exe, which ships in the tarball
        // on every platform. Accepting it makes node die with ERR_UNKNOWN_FILE_EXTENSION on launch
        // and — worse — hides the missing entry that triggers the fallback install.
        File(packageDir, "package.json").writeText("""{"bin":{"claude":"bin/claude.exe"}}""")
        File(packageDir, "bin/claude.exe").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0, 0)) }
        // cli-wrapper.cjs exists in that tarball but is not declared, so it is never a candidate.
        File(packageDir, "cli-wrapper.cjs").writeText("#!/usr/bin/env node\n// launcher shim\n")

        assertNull(ClaudeInstaller.resolveNodeEntry(packageDir, File(packageDir, "bin/claude.exe")))
    }

    @Test
    fun `ignores any declared entry node cannot load`() {
        val packageDir = tempDir()
        // A second bin key resolving to an existing non-script must not win either.
        File(packageDir, "package.json").writeText("""{"bin":{"claude":"missing.js","claude.exe":"bin/claude.exe"}}""")
        File(packageDir, "bin/claude.exe").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0, 0)) }

        assertNull(ClaudeInstaller.resolveNodeEntry(packageDir, File(packageDir, "absent")))
    }

    @Test
    fun `accepts a declared cjs or mjs bundle`() {
        val cjsDir = tempDir()
        val cjs = File(cjsDir, "dist/cli.cjs").apply { parentFile?.mkdirs(); writeText("// bundle") }
        File(cjsDir, "package.json").writeText("""{"bin":{"claude":"dist/cli.cjs"}}""")
        assertEquals(cjs.canonicalFile, ClaudeInstaller.resolveNodeEntry(cjsDir, File(cjsDir, "absent")))

        val mjsDir = tempDir()
        val mjs = File(mjsDir, "entry.mjs").apply { writeText("// bundle") }
        File(mjsDir, "package.json").writeText("""{"main":"entry.mjs"}""")
        assertEquals(mjs.canonicalFile, ClaudeInstaller.resolveNodeEntry(mjsDir, File(mjsDir, "absent")))
    }

    @Test
    fun `an executable windows launcher does not count as an installed binary`() {
        val dir = tempDir()
        // npm links bin/claude to the package's claude.exe and marks it executable; reporting that
        // as installed would suppress the reinstall prompt that repairs the install.
        val launcher = File(dir, "claude").apply {
            writeBytes(byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0, 0))
            setExecutable(true)
        }
        assertFalse(ClaudeInstaller.isDeviceExecutable(launcher))

        val elf = File(dir, "native").apply {
            writeBytes(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
            setExecutable(true)
        }
        assertTrue(ClaudeInstaller.isDeviceExecutable(elf))

        // The execute-bit half is not asserted here: File.setExecutable(false) is a no-op on
        // Windows and canExecute() then reports true for any readable file.
        assertFalse(ClaudeInstaller.isDeviceExecutable(File(dir, "absent")))
    }

    @Test
    fun `never follows a manifest entry outside the package directory`() {
        val root = tempDir()
        val packageDir = File(root, "package").apply { mkdirs() }
        File(root, "outside.js").writeText("// outside")
        File(packageDir, "package.json").writeText("""{"bin":{"claude":"../outside.js"}}""")

        assertNull(ClaudeInstaller.resolveNodeEntry(packageDir, File(packageDir, "missing")))
    }

    @Test
    fun `missing manifest and missing bin resolve to nothing`() {
        assertNull(ClaudeInstaller.resolveNodeEntry(File(tempDir(), "absent"), File(tempDir(), "absent")))
    }
}
