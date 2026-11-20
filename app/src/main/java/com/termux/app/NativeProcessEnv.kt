package com.termux.app

import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * The single definition of the environment handed to child processes we spawn ourselves.
 *
 * Every spawn site used to assemble its own map, and they drifted apart: `LD_LIBRARY_PATH` was
 * missing wherever a Termux binary needs a shared library (npx/node, MCP stdio servers), `TMPDIR`
 * pointed at two different directories depending on the component, and no site declared a UTF-8
 * locale, so non-ASCII output could come back mangled. Symptoms looked unrelated ("command not
 * found" inside an MCP server, garbled Chinese in a command card) while the cause was the same.
 *
 * Public (rather than `internal`) so the Java spawn sites can share it too — the whole point is
 * that there is exactly one definition. The two JSONL bridges keep their own maps because they
 * must also inject provider credentials; they should still take these values from here.
 */
object NativeProcessEnv {
    const val DEFAULT_LANG = "en_US.UTF-8"

    /** Values every Termux child process needs; call sites add their own on top. */
    @JvmStatic
    fun base(): MutableMap<String, String> = linkedMapOf(
        "HOME" to TermuxConstants.TERMUX_HOME_DIR_PATH,
        "PREFIX" to TermuxConstants.TERMUX_PREFIX_DIR_PATH,
        // Android app processes inherit a minimal PATH that only lists system directories, so
        // tools resolved through PATH (npm lifecycle scripts, shebangs) are invisible without it.
        "PATH" to TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin:/system/xbin",
        "TMPDIR" to TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH,
        // Without this a Termux-built binary cannot resolve its own shared libraries.
        "LD_LIBRARY_PATH" to TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH,
        "LANG" to DEFAULT_LANG,
    )

    /** Applies [base] then [overrides] to a builder. Overrides always win. */
    @JvmStatic
    @JvmOverloads
    fun apply(builder: ProcessBuilder, overrides: Map<String, String> = emptyMap()): ProcessBuilder {
        val environment = builder.environment()
        environment.putAll(base())
        environment.putAll(overrides)
        return builder
    }

    /** Directories that must exist before fork; Android fails the exec when cwd is missing. */
    @JvmStatic
    fun ensureDirectories() {
        listOf(
            TermuxConstants.TERMUX_HOME_DIR_PATH,
            TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH,
            File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".tmp").absolutePath,
        ).forEach { path -> runCatching { File(path).mkdirs() } }
    }
}
