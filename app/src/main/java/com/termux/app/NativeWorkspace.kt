package com.termux.app

import android.content.SharedPreferences
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * The one place that decides which directory the backends treat as the workspace.
 *
 * There used to be three copies of this rule (native chat bridge, WebUI bridge, git actions) and
 * all of them accepted a directory that merely existed. `File.isDirectory()` still returns true
 * for `/storage/emulated/0/` when the app holds no storage permission, because the stat only
 * needs the parent's execute bit. The result: the provider was started with a cwd it could not
 * list, so every file tool, git call and project scan failed with EACCES while the UI happily
 * showed that directory as the current project.
 *
 * Requiring `canRead() && canWrite()` makes an unusable workspace fall back to the app home, and
 * the environment self-check page explains why. Public so the Java bridges can share it.
 */
object NativeWorkspace {
    const val PREF_ENABLED = "custom_project_root_enabled"
    const val PREF_PATH = "custom_project_root"
    const val DEFAULT_SHARED_PATH = "/storage/emulated/0/"

    /** True when the app can actually list and modify files in [directory]. */
    @JvmStatic
    fun isUsable(directory: File?): Boolean =
        directory != null && directory.isDirectory && directory.canRead() && directory.canWrite()

    /**
     * The configured shared-storage root, or null when the user disabled it or never set one.
     * Returned even when unusable, so the self-check page can name the exact path.
     */
    @JvmStatic
    fun configured(prefs: SharedPreferences?): File? {
        if (prefs == null || !prefs.getBoolean(PREF_ENABLED, true)) return null
        val path = prefs.getString(PREF_PATH, DEFAULT_SHARED_PATH) ?: DEFAULT_SHARED_PATH
        return path.takeIf { it.isNotBlank() }?.let(::File)
    }

    /** Usable configured root, else the app home. Never returns a path the app cannot write. */
    @JvmStatic
    fun resolve(prefs: SharedPreferences?): File {
        val configured = configured(prefs)
        if (isUsable(configured)) return configured!!
        return File(TermuxConstants.TERMUX_HOME_DIR_PATH)
    }

    @JvmStatic
    fun resolvePath(prefs: SharedPreferences?): String = resolve(prefs).absolutePath
}
